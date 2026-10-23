package com.lagradost.desktop.notify

import com.lagradost.cloudstream3.app
import com.lagradost.desktop.tmdb.Tmdb
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.time.LocalDate

/**
 * What is about to come out, for the slider on the Schedule page: films that open in the viewer's cinemas, films that reach streaming
 * services, new series (TMDB, the viewer's country) and anime that has not started yet (AniList). Every answer is cached for a while.
 */
object ComingSoon {
    enum class Kind(val label: String) { Movie("Movies"), Series("Series"), Anime("Anime") }

    enum class Channel(val label: String) { Cinema("In cinemas"), Streaming("Streaming & TV") }

    class Item(
        val key: String,
        val kind: Kind,
        val channel: Channel,
        val title: String,
        val date: LocalDate,
        /** Only the month is known (AniList often has no day for a season that is far away) */
        val approx: Boolean,
        val poster: String?,
        val backdrop: String?,
        val overview: String?,
        /** 0..10 when the title has a score already */
        val rating: Double?,
        val genres: List<String>,
        /** The network or studio, when it is known */
        val by: String?,
        /** Place in the source's own popularity list: 0 is the most wanted */
        val rank: Int,
    )

    private class Cached(val at: Long, val region: String, val items: List<Item>)

    private var cached: Cached? = null
    private const val CACHE_MS = 30L * 60 * 1000

    /** How far ahead the slider looks */
    const val DAYS_AHEAD = 90L

    private val genreNames: Map<Int, String> = (Tmdb.movieGenres.entries + Tmdb.tvGenres.entries).associate { it.value to it.key }
    private val noAnime = Regex("<[^>]+>")

    /** The upcoming releases, soonest first. Fails only when every source failed. */
    suspend fun upcoming(force: Boolean = false): List<Item> {
        val now = System.currentTimeMillis()
        val region = Tmdb.region
        cached?.let { if (!force && it.region == region && now - it.at < CACHE_MS) return it.items }
        val today = LocalDate.now()
        val until = today.plusDays(DAYS_AHEAD)
        val gate = Semaphore(6)
        val failures = ArrayList<Throwable>()
        val parts = coroutineScope {
            listOf(
                async { runCatching { movies(Channel.Cinema, today, until, gate) } },
                async { runCatching { movies(Channel.Streaming, today, until, gate) } },
                async { runCatching { series(today, until, gate) } },
                async { runCatching { anime(today, until) } },
            ).map { it.await() }
        }
        val out = ArrayList<Item>()
        for (p in parts) p.onSuccess { out += it }.onFailure { failures += it }
        if (out.isEmpty() && failures.size == parts.size) throw failures.first()
        val sorted = out.filter { !it.date.isBefore(today) }.sortedWith(compareBy<Item>({ it.date }, { it.rank }))
        cached = Cached(now, region, sorted)
        return sorted
    }

    // ------------------------------------------------------------------ TMDB

    private fun date(s: String?): LocalDate? = s?.takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

    private fun genres(o: JSONObject): List<String> =
        o.optJSONArray("genre_ids")?.let { a -> (0 until a.length()).mapNotNull { genreNames[a.optInt(it)] } }.orEmpty()

    /** The date the film reaches [types] (TMDB release types: 2 limited, 3 cinemas, 4 digital) in the viewer's country, the first one from [from] on */
    private suspend fun regionalDate(id: Int, types: Set<Int>, from: LocalDate): LocalDate? {
        val results = Tmdb.json("/movie/$id/release_dates")?.optJSONArray("results") ?: return null
        val region = Tmdb.region
        for (i in 0 until results.length()) {
            val r = results.optJSONObject(i) ?: continue
            if (r.optString("iso_3166_1") != region) continue
            val list = r.optJSONArray("release_dates") ?: continue
            return (0 until list.length()).mapNotNull { n ->
                val e = list.optJSONObject(n) ?: return@mapNotNull null
                if (e.optInt("type") !in types) null else date(e.optString("release_date"))
            }.filter { !it.isBefore(from) }.minOrNull()
        }
        return null
    }

    private suspend fun movies(channel: Channel, from: LocalDate, to: LocalDate, gate: Semaphore): List<Item> {
        val types = if (channel == Channel.Cinema) setOf(2, 3) else setOf(4)
        val j = Tmdb.json(
            "/discover/movie", "region" to Tmdb.region, "with_release_type" to types.sorted().joinToString("|"),
            "release_date.gte" to from.toString(), "release_date.lte" to to.toString(), "sort_by" to "popularity.desc", "include_adult" to "false",
        ) ?: return emptyList()
        val arr = j.optJSONArray("results") ?: return emptyList()
        val raw = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.filter { Tmdb.poster(it.optString("poster_path")) != null }.take(24)
        return coroutineScope {
            raw.mapIndexed { rank, o ->
                async {
                    val id = o.optInt("id")
                    // the list shows the primary release date; the one that counts is the date in the viewer's country, for this kind of release
                    val day = gate.withPermit { runCatching { regionalDate(id, types, from) }.getOrNull() } ?: date(o.optString("release_date")) ?: return@async null
                    if (day.isBefore(from) || day.isAfter(to)) return@async null
                    Item(
                        "movie:${channel.name}:$id", Kind.Movie, channel, o.optString("title"), day, false,
                        Tmdb.poster(o.optString("poster_path"), "w500"), Tmdb.poster(o.optString("backdrop_path"), "w1280"),
                        o.optString("overview").takeIf { it.isNotBlank() }, o.optDouble("vote_average").takeIf { !it.isNaN() && it > 0 },
                        genres(o), null, rank,
                    )
                }
            }.mapNotNull { it.await() }.filter { it.title.isNotBlank() }
        }
    }

    private suspend fun series(from: LocalDate, to: LocalDate, gate: Semaphore): List<Item> {
        val j = Tmdb.json(
            "/discover/tv", "first_air_date.gte" to from.toString(), "first_air_date.lte" to to.toString(), "sort_by" to "popularity.desc",
            // talk shows and news are not what this page is for
            "without_genres" to "10767,10763", "include_adult" to "false",
        ) ?: return emptyList()
        val arr = j.optJSONArray("results") ?: return emptyList()
        // anime comes from AniList (with its own season dates)
        val raw = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.filter { o ->
            Tmdb.poster(o.optString("poster_path")) != null &&
                !(o.optString("original_language") == "ja" && o.optJSONArray("genre_ids")?.let { g -> (0 until g.length()).any { g.optInt(it) == 16 } } == true)
        }.take(24)
        return coroutineScope {
            raw.mapIndexed { rank, o ->
                async {
                    val day = date(o.optString("first_air_date")) ?: return@async null
                    val id = o.optInt("id")
                    // the network or service it comes on
                    val network = gate.withPermit {
                        runCatching { Tmdb.json("/tv/$id")?.optJSONArray("networks")?.optJSONObject(0)?.optString("name")?.takeIf { it.isNotBlank() } }.getOrNull()
                    }
                    Item(
                        "tv:$id", Kind.Series, Channel.Streaming, o.optString("name"), day, false,
                        Tmdb.poster(o.optString("poster_path"), "w500"), Tmdb.poster(o.optString("backdrop_path"), "w1280"),
                        o.optString("overview").takeIf { it.isNotBlank() }, o.optDouble("vote_average").takeIf { !it.isNaN() && it > 0 },
                        genres(o), network, rank,
                    )
                }
            }.mapNotNull { it.await() }.filter { it.title.isNotBlank() }
        }
    }

    // ------------------------------------------------------------------ AniList

    private const val ANIME_QUERY = "query(\$from:FuzzyDateInt,\$to:FuzzyDateInt){Page(page:1,perPage:30){media(type:ANIME,status:NOT_YET_RELEASED," +
        "startDate_greater:\$from,startDate_lesser:\$to,sort:POPULARITY_DESC,isAdult:false){id title{romaji english} coverImage{extraLarge large} bannerImage " +
        "description(asHtml:false) genres averageScore format startDate{year month day} studios(isMain:true){nodes{name}}}}}"

    private fun fuzzy(d: LocalDate): Int = d.year * 10000 + d.monthValue * 100 + d.dayOfMonth

    private suspend fun anime(from: LocalDate, to: LocalDate): List<Item> {
        val body = mapOf("query" to ANIME_QUERY, "variables" to mapOf("from" to fuzzy(from), "to" to fuzzy(to)))
        val answer = JSONObject(kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.post("https://graphql.anilist.co", json = body, timeout = 20).text })
        com.lagradost.desktop.net.ServiceOutage.requireData("AniList", answer)
        val data = answer.optJSONObject("data")?.optJSONObject("Page") ?: return emptyList()
        val arr = data.optJSONArray("media") ?: return emptyList()
        val out = ArrayList<Item>()
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            val t = m.optJSONObject("title")
            val title = listOf("english", "romaji").firstNotNullOfOrNull { t?.optString(it)?.takeIf { s -> s.isNotBlank() && s != "null" } } ?: continue
            val sd = m.optJSONObject("startDate") ?: continue
            val year = sd.optInt("year").takeIf { it > 0 } ?: continue
            val month = sd.optInt("month").takeIf { it in 1..12 } ?: continue
            val day = sd.optInt("day").takeIf { it in 1..31 }
            val when_ = runCatching { LocalDate.of(year, month, day ?: 1) }.getOrNull() ?: continue
            val cover = m.optJSONObject("coverImage")?.let { c -> c.optString("extraLarge").ifBlank { c.optString("large") } }?.takeIf { it.isNotBlank() && it != "null" } ?: continue
            val banner = m.optString("bannerImage").takeIf { it.isNotBlank() && it != "null" }
            out += Item(
                "anime:${m.optInt("id")}", Kind.Anime,
                // a film opens in the cinemas, a series airs on TV and streams
                if (m.optString("format") == "MOVIE") Channel.Cinema else Channel.Streaming,
                title, when_, day == null, cover, banner,
                m.optString("description").takeIf { it.isNotBlank() && it != "null" }?.replace(noAnime, " ")?.replace(Regex("\\s+"), " ")?.trim(),
                m.optInt("averageScore").takeIf { it > 0 }?.let { it / 10.0 },
                m.optJSONArray("genres")?.let { g -> (0 until g.length()).map { g.optString(it) } }.orEmpty().take(3),
                m.optJSONObject("studios")?.optJSONArray("nodes")?.optJSONObject(0)?.optString("name")?.takeIf { it.isNotBlank() }, i,
            )
        }
        return out
    }
}
