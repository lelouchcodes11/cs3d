package com.lagradost.desktop.ui.screens.explore

import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.desktop.tmdb.Tmdb
import com.lagradost.desktop.tmdb.TmdbCard
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

enum class ExploreKind(val label: String) { Movies("Movies"), Series("TV series"), Anime("Anime") }

/** [ranked]: the list is an order (a top 10), so its posters get big numbers */
class ExploreRow(val title: String, val subtitle: String?, val ranked: Boolean = false, val load: suspend () -> List<TmdbCard>)

/**
 * Catalogs for the Explore page. TMDB for films and series (trending, in cinemas, top rated, by genre and by streaming service),
 * AniList for anime; Cinemeta (the catalog behind Stremio) answers when TMDB cannot. No account is needed for any of them.
 */
object ExploreClient {
    private const val TTL_MS = 30 * 60_000L
    // the last 60 lists: filter combinations the viewer tried keep their answer for a while, but not for ever
    private val cache = object : java.util.LinkedHashMap<String, Pair<Long, List<TmdbCard>>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<TmdbCard>>>?) = size > 60
    }

    val animeGenres = listOf("Action", "Adventure", "Comedy", "Drama", "Fantasy", "Horror", "Mystery", "Romance", "Sci-Fi", "Slice of Life", "Sports", "Supernatural")

    fun genres(kind: ExploreKind): List<String> = when (kind) {
        ExploreKind.Movies -> Tmdb.movieGenres.keys.toList()
        ExploreKind.Series -> Tmdb.tvGenres.keys.toList()
        ExploreKind.Anime -> animeGenres
    }

    fun hasServices(kind: ExploreKind) = kind != ExploreKind.Anime

    fun rows(kind: ExploreKind, genre: String?, service: String?): List<ExploreRow> {
        if (kind == ExploreKind.Anime) return listOf(
            ExploreRow("Trending", "Rising this week", ranked = genre == null) { anilist("TRENDING_DESC", null, genre) },
            ExploreRow("Airing now", "Episodes coming out") { anilist("POPULARITY_DESC", "RELEASING", genre) },
            ExploreRow("Top rated", "Highest scores on AniList") { anilist("SCORE_DESC", null, genre) },
            ExploreRow("Upcoming", "Not out yet") { anilist("POPULARITY_DESC", "NOT_YET_RELEASED", genre) },
        )
        val movie = kind == ExploreKind.Movies
        val path = if (movie) "movie" else "tv"
        val genreId = genre?.let { (if (movie) Tmdb.movieGenres else Tmdb.tvGenres)[it] }
        val serviceId = service?.let { Tmdb.services[it] }
        val where = listOfNotNull(genre, service?.let { "on $it" }).joinToString(" ")
        if (genreId == null && serviceId == null) {
            val fallback = if (movie) "movie" else "series"
            return if (movie) listOf(
                ExploreRow("Trending today", "The most watched films right now", ranked = true) { tmdb("trending", true) { Tmdb.list("/trending/movie/day", true) }.ifEmpty { cinemeta(fallback, "top") } },
                ExploreRow("Popular", "What people are watching") { tmdb("popular", true) { Tmdb.list("/movie/popular", true, "region" to Tmdb.region) }.ifEmpty { cinemeta(fallback, "top") } },
                ExploreRow("In cinemas", "Playing now") { tmdb("nowplaying", true) { Tmdb.list("/movie/now_playing", true, "region" to Tmdb.region) } },
                ExploreRow("Coming soon", "Releasing in the next weeks") { tmdb("upcoming", true) { Tmdb.list("/movie/upcoming", true, "region" to Tmdb.region) } },
                ExploreRow("Top rated", "Best of all time") { tmdb("toprated", true) { Tmdb.list("/movie/top_rated", true) }.ifEmpty { cinemeta(fallback, "imdbRating") } },
            ) else listOf(
                ExploreRow("Trending today", "The most watched series right now", ranked = true) { tmdb("trending", false) { Tmdb.list("/trending/tv/day", false) }.ifEmpty { cinemeta(fallback, "top") } },
                ExploreRow("Popular", "What people are watching") { tmdb("popular", false) { Tmdb.list("/tv/popular", false) }.ifEmpty { cinemeta(fallback, "top") } },
                ExploreRow("Airing today", "New episodes today") { tmdb("airing", false) { Tmdb.list("/tv/airing_today", false) } },
                ExploreRow("On the air", "Running this week") { tmdb("ontheair", false) { Tmdb.list("/tv/on_the_air", false) } },
                ExploreRow("Top rated", "Best of all time") { tmdb("toprated", false) { Tmdb.list("/tv/top_rated", false) }.ifEmpty { cinemeta(fallback, "imdbRating") } },
            )
        }
        val today = LocalDate.now().toString()
        fun discover(sort: String, vararg extra: Pair<String, String>): suspend () -> List<TmdbCard> = {
            val params = buildList {
                add("sort_by" to sort)
                if (genreId != null) add("with_genres" to genreId.toString())
                if (serviceId != null) { add("with_watch_providers" to serviceId.toString()); add("watch_region" to Tmdb.region); add("with_watch_monetization_types" to "flatrate|free|ads") }
                addAll(extra)
            }
            tmdb("discover/$path/$genre/$service/$sort", movie) { Tmdb.list("/discover/$path", movie, *params.toTypedArray()) }
        }
        return listOf(
            ExploreRow("Popular $where".trim(), null, load = discover("popularity.desc")),
            ExploreRow("Top rated $where".trim(), null, load = discover("vote_average.desc", "vote_count.gte" to "300")),
            ExploreRow("New $where".trim(), null, load = discover(if (movie) "primary_release_date.desc" else "first_air_date.desc", (if (movie) "primary_release_date.lte" else "first_air_date.lte") to today, "vote_count.gte" to "20")),
        )
    }

    private suspend fun cached(key: String, load: suspend () -> List<TmdbCard>): List<TmdbCard> {
        synchronized(cache) { cache[key] }?.let { (at, list) -> if (System.currentTimeMillis() - at < TTL_MS) return list }
        val list = load()
        if (list.isNotEmpty()) synchronized(cache) { cache[key] = System.currentTimeMillis() to list }
        return list
    }

    private suspend fun tmdb(key: String, movie: Boolean, load: suspend () -> List<TmdbCard>): List<TmdbCard> = cached("tmdb/${if (movie) "m" else "t"}/$key", load)

    private suspend fun cinemeta(type: String, sort: String): List<TmdbCard> = cached("cm/$type/$sort") {
        val json = JSONObject(app.get("https://cinemeta-catalogs.strem.io/$sort/catalog/$type/$sort.json", timeout = 20).text)
        val metas = json.optJSONArray("metas") ?: return@cached emptyList()
        buildList {
            for (i in 0 until metas.length()) {
                val m = metas.getJSONObject(i)
                val name = m.optString("name").takeIf { it.isNotBlank() } ?: continue
                val poster = m.optString("poster").takeIf { it.isNotBlank() } ?: continue
                val genres = m.optJSONArray("genre")?.let { g -> (0 until g.length()).map { g.getString(it) } }.orEmpty()
                add(
                    TmdbCard(
                        name = name, url = "cinemeta:" + m.optString("imdb_id", name), type = if (type == "movie") TvType.Movie else TvType.TvSeries,
                        posterUrl = poster, year = m.optString("year").take(4).toIntOrNull(), genres = genres,
                        score = Score.from10(m.optString("imdbRating").toDoubleOrNull()), isMovie = type == "movie",
                    ),
                )
            }
        }
    }

    private suspend fun anilist(sort: String, status: String?, genre: String?): List<TmdbCard> = cached("al/$sort/$status/$genre") {
        val filters = buildString {
            append("type: ANIME, isAdult: false, sort: [$sort]")
            if (status != null) append(", status: $status")
            if (genre != null) append(", genre: \"${genre.replace("\"", "")}\"")
        }
        val query = "{ Page(perPage: 30) { media($filters) { id title { romaji english } coverImage { extraLarge } averageScore seasonYear genres format } } }"
        val json = JSONObject(app.post("https://graphql.anilist.co", json = mapOf("query" to query), timeout = 20).text)
        val media = json.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media") ?: return@cached emptyList()
        buildList {
            for (i in 0 until media.length()) {
                val m = media.getJSONObject(i)
                val t = m.optJSONObject("title")
                val name = (t?.optString("english")?.takeIf { it.isNotBlank() && it != "null" } ?: t?.optString("romaji"))?.takeIf { it.isNotBlank() } ?: continue
                val cover = m.optJSONObject("coverImage")?.optString("extraLarge")?.takeIf { it.isNotBlank() } ?: continue
                val genres = m.optJSONArray("genres")?.let { g -> (0 until g.length()).map { g.getString(it) } }.orEmpty()
                add(
                    TmdbCard(
                        name = name, url = "anilist:" + m.optInt("id"), type = if (m.optString("format") == "MOVIE") TvType.AnimeMovie else TvType.Anime,
                        posterUrl = cover, year = m.optInt("seasonYear").takeIf { it > 0 }, genres = genres,
                        score = Score.from100(m.optInt("averageScore").takeIf { it > 0 }), isMovie = false,
                    ),
                )
            }
        }
    }
}
