package com.lagradost.desktop.stremio

import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.ProviderType
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

/**
 * A Stremio add-on that has catalogs or meta, as a provider of the app: its catalogs are rows on Home, its search is a search, its items
 * open the title page and Play asks the add-ons that have streams (see [StremioStreams]). One instance per add-on, made by [StremioAddons].
 */
class StremioApi(val addonUrl: String, displayName: String) : MainAPI() {
    override var name: String = displayName
    override var mainUrl: String = StremioClient.baseOf(addonUrl)
    override var lang: String = "en"
    override var canBeOverridden: Boolean = false
    override val hasMainPage: Boolean get() = manifest?.catalogs?.any { !it.needsExtra } == true
    override val hasQuickSearch: Boolean = false
    override val hasDownloadSupport: Boolean = false
    override val providerType: ProviderType = ProviderType.MetaProvider
    override val loadLinksTimeoutMs: Long = 40_000L

    private val manifest: StremioManifest? get() = StremioAddons.find(addonUrl)?.manifest

    override val supportedTypes: Set<TvType>
        get() {
            val m = manifest ?: return setOf(TvType.Movie, TvType.TvSeries)
            val types = (m.types + m.catalogs.map { it.type }).distinct().map { typeOf(it) }.toSet()
            return types.ifEmpty { setOf(TvType.Movie, TvType.TvSeries) }
        }

    private fun typeOf(t: String): TvType = when (t.lowercase()) {
        "movie" -> TvType.Movie
        "series" -> TvType.TvSeries
        "anime" -> TvType.Anime
        "tv", "channel" -> TvType.Live
        else -> TvType.Others
    }

    /** Anime catalogs say "series" or "movie" like any other: the id tells ("kitsu:12", "mal:5", "anilist:9", "anidb:3") */
    private fun kindOf(type: String, id: String): TvType {
        val k = typeOf(type)
        val anime = Regex("^(kitsu|mal|anilist|anidb|anime)[:-]", RegexOption.IGNORE_CASE).containsMatchIn(id)
        return if (anime && k == TvType.Movie) TvType.AnimeMovie else if (anime && k == TvType.TvSeries) TvType.Anime else k
    }

    private fun typeLabel(t: String): String = when (t.lowercase()) {
        "movie" -> "Movies"; "series" -> "Series"; "anime" -> "Anime"; "tv" -> "TV"; "channel" -> "Channels"
        else -> t.replaceFirstChar { it.uppercase() }
    }

    // ------------------------------------------------------------------ home

    override val mainPage: List<MainPageData>
        get() {
            val catalogs = manifest?.catalogs?.filter { !it.needsExtra }.orEmpty()
            if (catalogs.isEmpty()) return listOf(MainPageData("", "", false))
            return catalogs.map { c ->
                val dup = catalogs.count { it.name.equals(c.name, true) } > 1
                MainPageData(if (dup) "${c.name} · ${typeLabel(c.type)}" else c.name, "${c.type}|${c.id}", false)
            }
        }

    /** How many items a page of a catalog has (the first answer says; the next page skips that many) */
    private val pageSize = ConcurrentHashMap<String, Int>()

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val (type, id) = request.data.split('|', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val catalog = manifest?.catalogs?.firstOrNull { it.type == type && it.id == id } ?: return null
        val key = "$addonUrl|$type|$id"
        val skip = if (page <= 1 || !catalog.supportsSkip) 0 else (pageSize[key] ?: 100) * (page - 1)
        if (page > 1 && !catalog.supportsSkip) return null
        val extra = if (skip > 0) mapOf("skip" to skip.toString()) else emptyMap()
        val json = StremioClient.getJson(StremioClient.resourceUrl(addonUrl, "catalog", type, id, extra), ttlMs = 10 * 60_000L) ?: throw ErrorLoadingException("$name did not answer")
        val metas = StremioClient.parseMetas(json, type)
        if (page <= 1 && metas.isNotEmpty()) pageSize[key] = metas.size
        return newHomePageResponse(HomePageList(request.name, metas.map { toSearch(it) }, false), hasNext = catalog.supportsSkip && metas.isNotEmpty())
    }

    // ------------------------------------------------------------------ search

    private fun toSearch(m: StremioMeta): SearchResponse {
        val url = itemUrl(m.type, m.id)
        val type = kindOf(m.type, m.id)
        return when (type) {
            TvType.Movie, TvType.AnimeMovie, TvType.Live -> newMovieSearchResponse(m.name, url, type, false) {
                posterUrl = m.poster; year = m.year
                m.rating?.let { score = com.lagradost.cloudstream3.Score.from10(it) }
            }
            else -> newTvSeriesSearchResponse(m.name, url, type, false) {
                posterUrl = m.poster; year = m.year
                m.rating?.let { score = com.lagradost.cloudstream3.Score.from10(it) }
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val catalogs = manifest?.catalogs?.filter { it.supportsSearch }?.take(6).orEmpty()
        if (catalogs.isEmpty()) return null
        val lists = coroutineScope {
            catalogs.map { c ->
                async {
                    val url = StremioClient.resourceUrl(addonUrl, "catalog", c.type, c.id, mapOf("search" to query))
                    StremioClient.getJson(url, ttlMs = 5 * 60_000L)?.let { StremioClient.parseMetas(it, c.type) }.orEmpty()
                }
            }.map { it.await() }
        }
        return lists.flatten().distinctBy { it.type + it.id }.map { toSearch(it) }
    }

    // ------------------------------------------------------------------ title

    private fun itemUrl(type: String, id: String) = "stremio://item/${StremioClient.enc(type)}/${StremioClient.enc(id)}"

    private fun parseUrl(url: String): Pair<String, String>? {
        val rest = url.substringAfter("stremio://item/", "").takeIf { it.isNotEmpty() } ?: return null
        val parts = rest.split('/', limit = 2)
        if (parts.size != 2) return null
        return java.net.URLDecoder.decode(parts[0], "UTF-8") to java.net.URLDecoder.decode(parts[1], "UTF-8")
    }

    /** The meta of an item from this add-on, or from another that has one (a catalog-only add-on shows titles Cinemeta knows) */
    private suspend fun fetchMeta(type: String, id: String): StremioMeta? {
        val own = StremioAddons.find(addonUrl)?.takeIf { it.manifest?.provides("meta", type, id) == true }
        val candidates = listOfNotNull(own) + StremioAddons.metaAddons().filter { it.manifestUrl != addonUrl && it.manifest!!.provides("meta", type, id) }
        for (a in candidates) {
            val json = StremioClient.getJson(StremioClient.resourceUrl(a.manifestUrl, "meta", type, id), ttlMs = 30 * 60_000L) ?: continue
            val meta = json.optJSONObject("meta") ?: continue
            return StremioClient.parseMeta(meta, type)
        }
        return null
    }

    private fun minutes(runtime: String?): Int? = runtime?.let { Regex("(\\d+)").find(it)?.value?.toIntOrNull() }

    override suspend fun load(url: String): LoadResponse? {
        val (type, id) = parseUrl(url) ?: throw ErrorLoadingException("Not a Stremio title")
        val meta = fetchMeta(type, id) ?: throw ErrorLoadingException("No add-on has details for this title")
        val kind = kindOf(type, id)
        val episodes = meta.videos.filter { it.id.isNotBlank() }
        val hasEpisodes = episodes.isNotEmpty() && kind != TvType.Movie && kind != TvType.AnimeMovie && kind != TvType.Live
        val response: LoadResponse = when {
            kind == TvType.Live -> newLiveStreamLoadResponse(meta.name, url, StremioStreams.Target(type, id, meta.videos.firstOrNull()?.streams).toData())
            hasEpisodes || kind == TvType.TvSeries || kind == TvType.Anime -> newTvSeriesLoadResponse(
                meta.name, url, if (kind == TvType.Movie) TvType.TvSeries else kind,
                episodes.map { v ->
                    newEpisode(StremioStreams.Target(type, v.id, v.streams).toData()) {
                        this.name = v.title
                        this.season = v.season ?: if (v.episode != null) 1 else null
                        this.episode = v.episode
                        this.posterUrl = v.thumbnail
                        this.description = v.overview
                        addDate(v.released)
                    }
                },
            )
            else -> newMovieLoadResponse(meta.name, url, kind, StremioStreams.Target(type, id).toData())
        }
        response.apply {
            posterUrl = meta.poster
            backgroundPosterUrl = meta.background
            logoUrl = meta.logo
            plot = meta.description
            year = meta.year
            tags = meta.genres.ifEmpty { null }
            duration = minutes(meta.runtime)
            meta.rating?.let { addScore(it.toString()) }
            meta.imdbId?.let { addImdbId(it) }
            // an anime of MyAnimeList: its id lets the title page find the prequels and sequels
            if (id.startsWith("mal:")) id.substringAfter(':').substringBefore(':').takeIf { it.all { c -> c.isDigit() } }?.let { syncData["mal"] = it }
            if (meta.cast.isNotEmpty()) addActors(meta.cast.take(30).map { Actor(it) })
        }
        return response
    }

    // ------------------------------------------------------------------ sources

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val target = StremioStreams.parseData(data) ?: return false
        return StremioStreams.collect(target, subtitleCallback, callback)
    }
}
