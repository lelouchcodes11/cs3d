package com.lagradost.desktop.isolate

import android.util.Log
import com.fasterxml.jackson.databind.JsonNode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.ProviderType
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.VPNStatus
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.utils.ExtractorLink

/**
 * A provider of an extension that runs in the sandbox, as the app sees it: the same [MainAPI] as any other, so the Home page, search, the title page and
 * the player use it without knowing. Every call goes over the pipe to the sandbox ([IsolatedHost]); no code of the extension is in this process.
 */
class RemoteMainAPI(private val meta: Meta) : MainAPI() {
    /** What the sandbox tells about a provider when its extension is loaded */
    class Meta(
        val path: String, val name: String, val mainUrl: String, val lang: String, val types: Set<TvType>, val hasMainPage: Boolean, val hasQuickSearch: Boolean,
        val usesWebView: Boolean, val instantLinkLoading: Boolean, val chromecast: Boolean, val download: Boolean, val vpn: VPNStatus, val providerType: ProviderType,
        val syncNames: Set<SyncIdName>, val mainPage: List<MainPageData>, val hasInterceptor: Boolean = false,
    ) {
        companion object {
            private inline fun <reified E : Enum<E>> enumOf(node: JsonNode?, default: E): E = enumValues<E>().firstOrNull { it.name == node?.asText() } ?: default

            fun from(path: String, n: JsonNode) = Meta(
                path, n.get("name").asText(), n.get("mainUrl").asText(), n.get("lang").asText("en"),
                n.get("types").mapNotNull { t -> TvType.values().firstOrNull { it.name == t.asText() } }.toSet().ifEmpty { setOf(TvType.Movie, TvType.TvSeries) },
                n.get("hasMainPage").asBoolean(), n.get("hasQuickSearch").asBoolean(), n.get("usesWebView").asBoolean(), n.get("instantLinkLoading").asBoolean(),
                n.get("hasChromecastSupport").asBoolean(true), n.get("hasDownloadSupport").asBoolean(true),
                enumOf(n.get("vpn"), VPNStatus.None), enumOf(n.get("providerType"), ProviderType.DirectProvider),
                n.get("syncNames").mapNotNull { s -> SyncIdName.values().firstOrNull { it.name == s.asText() } }.toSet(),
                n.get("mainPage").map { MainPageData(it.get("name").asText(), it.get("data").asText(), it.get("horizontal").asBoolean()) },
                n.get("hasInterceptor")?.asBoolean() == true,
            )
        }
    }

    init {
        name = meta.name
        mainUrl = meta.mainUrl
        lang = meta.lang
        sourcePlugin = meta.path
    }

    override val supportedTypes: Set<TvType> = meta.types
    override val hasMainPage: Boolean = meta.hasMainPage
    override val hasQuickSearch: Boolean = meta.hasQuickSearch
    override val usesWebView: Boolean = meta.usesWebView
    override val instantLinkLoading: Boolean = meta.instantLinkLoading
    override val hasChromecastSupport: Boolean = meta.chromecast
    override val hasDownloadSupport: Boolean = meta.download
    override val vpnStatus: VPNStatus = meta.vpn
    override val providerType: ProviderType = meta.providerType
    override val supportedSyncNames: Set<SyncIdName> = meta.syncNames
    override val mainPage: List<MainPageData> = meta.mainPage.ifEmpty { listOf(MainPageData("", "", false)) }

    /** Set when the extension was moved into the app while this provider was in use: the calls that follow (and the one that was running) go to it */
    @Volatile var moved: MainAPI? = null

    /** A call for this provider's extension in the sandbox (used by [RemoteInterceptor]) */
    internal suspend fun callHost(op: String, timeoutMs: Long, vararg pairs: Pair<String, Any?>): JsonNode = IsolatedHost.call(op, body(*pairs), timeoutMs)

    /**
     * The extension's video interceptor runs in the sandbox, around a request the sandbox makes (see [RemoteInterceptor]); asked once per link, as the player does.
     * Null when the extension has none for this link.
     */
    override fun getVideoInterceptor(extractorLink: ExtractorLink): okhttp3.Interceptor? {
        moved?.let { return it.getVideoInterceptor(extractorLink) }
        if (!meta.hasInterceptor) return null
        val has = runCatching { kotlinx.coroutines.runBlocking { callHost("hasInterceptor", 20_000, "link" to IsolationSerde.write(extractorLink, ExtractorLink::class.java)).asBoolean() } }.getOrDefault(false)
        return if (has) RemoteInterceptor(this, extractorLink) else null
    }

    private fun body(vararg pairs: Pair<String, Any?>) =
        mapper.createObjectNode().put("path", meta.path).put("api", meta.name).also { n -> pairs.forEach { (k, v) -> n.set<JsonNode>(k, mapper.valueToTree(v)) } }

    private fun items(node: JsonNode?): List<SearchResponse> =
        node?.mapNotNull { IsolationSerde.read(it, SearchResponse::class.java) }.orEmpty()

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        moved?.let { return it.getMainPage(page, request) }
        val reply = IsolatedHost.call("mainPage", body("page" to page, "name" to request.name, "data" to request.data, "horizontal" to request.horizontalImages), 90_000)
        if (reply.isNull) return null
        val lists = reply.get("lists").map { l -> HomePageList(l.get("name").asText(), items(l.get("list")), l.get("horizontal").asBoolean()) }
        return moved?.getMainPage(page, request) ?: newHomePageResponse(lists, hasNext = reply.get("hasNext").asBoolean())
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        moved?.let { return it.search(query, page) }
        val reply = IsolatedHost.call("search", body("q" to query, "page" to page), 90_000)
        if (reply.isNull) return null
        return moved?.search(query, page) ?: newSearchResponseList(items(reply.get("items")), hasNext = reply.get("hasNext").asBoolean())
    }

    override suspend fun search(query: String): List<SearchResponse>? = search(query, 1)?.items

    override suspend fun quickSearch(query: String): List<SearchResponse>? {
        moved?.let { return it.quickSearch(query) }
        val reply = IsolatedHost.call("quickSearch", body("q" to query), 60_000)
        return if (reply.isNull) null else items(reply.get("items"))
    }

    override suspend fun load(url: String): LoadResponse? {
        moved?.let { return it.load(url) }
        val reply = IsolatedHost.call("load", body("url" to url), 120_000)
        return moved?.load(url) ?: if (reply.isNull) null else IsolationSerde.read(reply, LoadResponse::class.java)
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        moved?.let { return it.loadLinks(data, isCasting, subtitleCallback, callback) }
        val reply = IsolatedHost.call("loadLinks", body("data" to data, "casting" to isCasting), 300_000) { op, node ->
            when (op) {
                "link" -> IsolationSerde.read(node, ExtractorLink::class.java)?.let(callback) ?: Log.w("RemoteMainAPI", "a link of $name could not be read")
                "sub" -> IsolationSerde.plain(node, SubtitleFile::class.java)?.let(subtitleCallback)
            }
        }
        // moved into the app while this was running: what the sandbox could not do (it had no browser) is done again by the extension in the app
        moved?.let { return it.loadLinks(data, isCasting, subtitleCallback, callback) }
        return reply.get("ok")?.asBoolean() == true
    }
}
