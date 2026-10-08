package com.lagradost.desktop.tmdb

import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** A film, series or person's credit from TMDB as a poster; extensions do not own it, so opening it searches them for the title (see openCard) */
class TmdbCard(
    override val name: String,
    override val url: String,
    override var type: TvType?,
    override var posterUrl: String?,
    val year: Int?,
    val genres: List<String>,
    override var score: Score?,
    val tmdbId: Int = 0,
    val isMovie: Boolean = true,
    val backdrop: String? = null,
    val overview: String? = null,
) : SearchResponse {
    override val apiName: String = "TMDB"
    override var posterHeaders: Map<String, String>? = null
    override var id: Int? = null
    override var quality: SearchQuality? = null
}

class TmdbPerson(val id: Int, val name: String, val role: String?, val image: String?)
class TmdbReview(val author: String, val rating: Double?, val text: String, val date: String?)
class TmdbProvider(val name: String, val logo: String?)

class TmdbInfo(
    val id: Int, val isMovie: Boolean, val title: String, val tagline: String?, val overview: String?,
    val rating: Double?, val votes: Int, val runtime: Int?, val status: String?, val date: String?, val certification: String?,
    val genres: List<String>, val studios: List<Pair<Int, String>>, val logo: String?, val backdrop: String?, val poster: String?,
    val backdrops: List<String>, val cast: List<TmdbPerson>, val directors: List<Pair<Int, String>>, val writers: List<Pair<Int, String>>,
    val trailerKey: String?, val reviews: List<TmdbReview>, val providers: List<TmdbProvider>, val similar: List<TmdbCard>,
    val collection: Pair<Int, String>?, val imdbId: String?, val seasons: Int?, val episodes: Int?, val networks: List<Pair<Int, String>>,
)

class TmdbPersonInfo(
    val id: Int, val name: String, val bio: String?, val birthday: String?, val deathday: String?, val place: String?,
    val department: String?, val image: String?, val known: List<TmdbCard>, val credits: List<TmdbCard>,
)

/**
 * The Movie Database (themoviedb.org): artwork, cast with photos, ratings, reviews, collections and where a title streams.
 * This product uses the TMDB API but is not endorsed or certified by TMDB. Answers are kept for six hours.
 */
object Tmdb {
    /** A free key for the app; -Dcloudstream.tmdbkey=<key> uses another one */
    private val key: String = System.getProperty("cloudstream.tmdbkey") ?: "167005118451cfe63da18e461dacd1d1"
    private const val BASE = "https://api.themoviedb.org/3"
    private const val IMG = "https://image.tmdb.org/t/p/"
    private const val TTL_MS = 6 * 3_600_000L

    /** The viewer's country, which decides the streaming services and age ratings shown */
    val region: String get() = com.lagradost.desktop.ui.fluent.Appearance.tmdbRegion.ifBlank { Locale.getDefault().country.ifBlank { "US" } }

    /** Settings > Appearance > Title information: when off no request is made and every call comes back empty */
    val enabled: Boolean get() = com.lagradost.desktop.ui.fluent.Appearance.tmdbEnabled

    /** Countries offered in Settings (TMDB has streaming data for all of them) */
    val regions = linkedMapOf(
        "IN" to "India", "US" to "United States", "GB" to "United Kingdom", "CA" to "Canada", "AU" to "Australia", "DE" to "Germany", "FR" to "France",
        "ES" to "Spain", "IT" to "Italy", "NL" to "Netherlands", "BR" to "Brazil", "MX" to "Mexico", "JP" to "Japan", "KR" to "South Korea",
        "AE" to "United Arab Emirates", "SA" to "Saudi Arabia", "SG" to "Singapore", "ZA" to "South Africa", "TR" to "Turkey", "PH" to "Philippines",
    )

    // the answers of the last 80 requests (a title's page is ~100 KB of JSON: the cache must not grow for as long as the app runs)
    private val cache = object : java.util.LinkedHashMap<String, Pair<Long, JSONObject>>(96, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, JSONObject>>?) = size > 80
    }

    fun poster(path: String?, size: String = "w342") = path?.takeIf { it.isNotBlank() && it != "null" }?.let { IMG + size + it }

    suspend fun json(path: String, vararg params: Pair<String, String>): JSONObject? {
        if (!enabled) return null
        val query = (listOf("api_key" to key, "language" to "en-US") + params).joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }
        val url = "$BASE$path?$query"
        synchronized(cache) { cache[url] }?.let { (at, j) -> if (System.currentTimeMillis() - at < TTL_MS) return j }
        val text = runCatching { app.get(url, timeout = 20).text }.getOrNull() ?: return null
        val j = runCatching { JSONObject(text) }.getOrNull() ?: return null
        if (j.has("status_code") && !j.has("id") && !j.has("results")) return null
        synchronized(cache) { cache[url] = System.currentTimeMillis() to j }
        return j
    }

    // ------------------------------------------------------------------ lists (Explore, collections)

    private fun year(date: String?) = date?.take(4)?.toIntOrNull()

    private fun card(o: JSONObject, forceMovie: Boolean?): TmdbCard? {
        val movie = forceMovie ?: (o.optString("media_type", if (o.has("title")) "movie" else "tv") == "movie")
        val name = (if (movie) o.optString("title") else o.optString("name")).takeIf { it.isNotBlank() } ?: return null
        val poster = poster(o.optString("poster_path")) ?: return null
        val id = o.optInt("id")
        val anime = !movie && o.optString("original_language") == "ja" && o.optJSONArray("genre_ids")?.let { a -> (0 until a.length()).any { a.getInt(it) == 16 } } == true
        return TmdbCard(
            name = name, url = "tmdb:${if (movie) "movie" else "tv"}:$id",
            type = if (movie) TvType.Movie else if (anime) TvType.Anime else TvType.TvSeries, posterUrl = poster,
            year = year(if (movie) o.optString("release_date") else o.optString("first_air_date")),
            genres = o.optJSONArray("genre_ids")?.let { a -> (0 until a.length()).mapNotNull { genreNames[a.getInt(it)] } }.orEmpty(),
            score = Score.from10(o.optDouble("vote_average").takeIf { !it.isNaN() && it > 0 }), tmdbId = id, isMovie = movie,
            backdrop = poster(o.optString("backdrop_path"), "w780"), overview = o.optString("overview").takeIf { it.isNotBlank() },
        )
    }

    private fun cards(arr: JSONArray?, forceMovie: Boolean?): List<TmdbCard> =
        if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { card(arr.getJSONObject(it), forceMovie) }

    /** A TMDB list such as "/movie/popular" or "/discover/tv"; [forceMovie] null for lists that mix both (trending/all) */
    suspend fun list(path: String, forceMovie: Boolean?, vararg params: Pair<String, String>): List<TmdbCard> =
        cards(json(path, *params)?.optJSONArray("results"), forceMovie)

    suspend fun collection(id: Int): Pair<String, List<TmdbCard>>? {
        val j = json("/collection/$id") ?: return null
        return j.optString("name") to cards(j.optJSONArray("parts"), true).sortedBy { it.year ?: 9999 }
    }

    // ------------------------------------------------------------------ one title

    private val imdbRegex = Regex("tt\\d{6,10}")
    private val tmdbRegex = Regex("tmdb\\W{1,6}(\\d{1,9})", RegexOption.IGNORE_CASE)

    /** "Raw (Hindi) [Dub]" -> "Raw", "Naruto - Season 2" -> "Naruto" */
    private fun cleanTitle(t: String): String = t
        .replace(Regex("[\\[(][^\\])]*[\\])]"), " ")
        .replace(Regex("(?i)\\b(hindi|tamil|telugu|english|dubbed|dual audio|multi audio|sub|dub|uncut)\\b"), " ")
        .replace(Regex("(?i)\\s*[-:]?\\s*season\\s*\\d+.*$"), "")
        .replace(Regex("\\s+"), " ").trim()

    /**
     * What TMDB knows about a title an extension showed. The ids the extension gave (IMDb, TMDB) are used first, then a search by
     * name and year; null when nothing fits.
     */
    suspend fun info(title: String, year: Int?, isMovie: Boolean, hints: Collection<String>): TmdbInfo? {
        val joined = hints.joinToString(" ")
        var id = tmdbRegex.find(joined)?.groupValues?.get(1)?.toIntOrNull()
        var movie = isMovie
        if (id == null) {
            val imdb = imdbRegex.find(joined)?.value
            if (imdb != null) {
                val f = json("/find/$imdb", "external_source" to "imdb_id")
                f?.optJSONArray(if (isMovie) "movie_results" else "tv_results")?.optJSONObject(0)?.let { id = it.optInt("id") }
                if (id == null) {
                    // the extension may call a series a film or the other way round
                    f?.optJSONArray(if (isMovie) "tv_results" else "movie_results")?.optJSONObject(0)?.let { id = it.optInt("id"); movie = !isMovie }
                }
            }
        }
        if (id == null) {
            val names = listOf(title, cleanTitle(title)).filter { it.isNotBlank() }.distinct()
            search@ for (n in names) for (y in listOf(year, null).distinct()) {
                val res = json(
                    if (isMovie) "/search/movie" else "/search/tv", "query" to n,
                    *(if (y != null) arrayOf((if (isMovie) "year" else "first_air_date_year") to y.toString()) else emptyArray()),
                )?.optJSONArray("results") ?: continue
                val best = (0 until res.length()).map { res.getJSONObject(it) }.let { all ->
                    all.firstOrNull { (if (isMovie) it.optString("title") else it.optString("name")).equals(n, true) } ?: all.firstOrNull()
                } ?: continue
                id = best.optInt("id"); break@search
            }
        }
        val tid = id?.takeIf { it > 0 } ?: return null
        return load(tid, movie)
    }

    private fun names(arr: JSONArray?, field: String = "name"): List<String> =
        if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString(field)?.takeIf { s -> s.isNotBlank() } }

    private fun idNames(arr: JSONArray?): List<Pair<Int, String>> =
        if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { it.optInt("id") to it.optString("name") }?.takeIf { (id, n) -> id > 0 && n.isNotBlank() } }

    /** Everything a studio ([company]) or a TV network made, most popular first */
    suspend fun byMaker(id: Int, company: Boolean, movie: Boolean): List<TmdbCard> =
        list("/discover/${if (movie) "movie" else "tv"}", movie, (if (company) "with_companies" else "with_networks") to id.toString(), "sort_by" to "popularity.desc")

    private suspend fun load(id: Int, movie: Boolean): TmdbInfo? {
        val kind = if (movie) "movie" else "tv"
        val append = (if (movie) "credits" else "aggregate_credits") + ",images,videos,reviews,recommendations,external_ids,watch/providers," + if (movie) "release_dates" else "content_ratings"
        val j = json("/$kind/$id", "append_to_response" to append, "include_image_language" to "en,null", "include_video_language" to "en,null") ?: return null
        // a series lists only a few people under credits; aggregate_credits has everyone who played in it, with their roles
        val credits = j.optJSONObject(if (movie) "credits" else "aggregate_credits")
        val crew = credits?.optJSONArray("crew")
        fun crewBy(vararg jobs: String): List<Pair<Int, String>> = if (crew == null) emptyList() else
            (0 until crew.length()).map { crew.getJSONObject(it) }.filter { it.optString("job") in jobs }.map { it.optInt("id") to it.optString("name") }.distinctBy { it.first }.take(4)
        val cast = credits?.optJSONArray("cast")?.let { a ->
            (0 until a.length()).map { a.getJSONObject(it) }.take(40).map { TmdbPerson(it.optInt("id"), it.optString("name"), (it.optString("character").ifBlank { it.optJSONArray("roles")?.optJSONObject(0)?.optString("character").orEmpty() }).takeIf { s -> s.isNotBlank() }, poster(it.optString("profile_path"), "w185")) }
        }.orEmpty()
        val images = j.optJSONObject("images")
        val logo = images?.optJSONArray("logos")?.let { a ->
            val all = (0 until a.length()).map { a.getJSONObject(it) }
            (all.filter { it.optString("iso_639_1") == "en" }.ifEmpty { all }).maxByOrNull { it.optDouble("vote_average", 0.0) }
        }?.let { poster(it.optString("file_path"), "w500") }
        val backdrops = images?.optJSONArray("backdrops")?.let { a ->
            (0 until a.length()).mapNotNull { poster(a.getJSONObject(it).optString("file_path"), "w780") }.take(18)
        }.orEmpty()
        val trailer = j.optJSONObject("videos")?.optJSONArray("results")?.let { a ->
            val all = (0 until a.length()).map { a.getJSONObject(it) }.filter { it.optString("site") == "YouTube" }
            (all.firstOrNull { it.optString("type") == "Trailer" && it.optBoolean("official") } ?: all.firstOrNull { it.optString("type") == "Trailer" } ?: all.firstOrNull())?.optString("key")
        }
        val reviews = j.optJSONObject("reviews")?.optJSONArray("results")?.let { a ->
            (0 until a.length()).map { a.getJSONObject(it) }.take(8).mapNotNull {
                val text = it.optString("content").takeIf { s -> s.isNotBlank() } ?: return@mapNotNull null
                TmdbReview(it.optString("author").ifBlank { it.optJSONObject("author_details")?.optString("username").orEmpty() }.ifBlank { "Anonymous" },
                    it.optJSONObject("author_details")?.optDouble("rating")?.takeIf { r -> !r.isNaN() && r > 0 }, text.replace("\r\n", "\n"), it.optString("created_at").take(10).takeIf { s -> s.isNotBlank() })
            }
        }.orEmpty()
        val regionProviders = j.optJSONObject("watch/providers")?.optJSONObject("results")?.optJSONObject(region)
        val providers = listOf("flatrate", "free", "ads").flatMap { t -> regionProviders?.optJSONArray(t)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty() }
            .distinctBy { it.optInt("provider_id") }.take(10).map { TmdbProvider(it.optString("provider_name"), poster(it.optString("logo_path"), "w92")) }
        val certification = if (movie) {
            j.optJSONObject("release_dates")?.optJSONArray("results")?.let { a ->
                val all = (0 until a.length()).map { a.getJSONObject(it) }
                (all.firstOrNull { it.optString("iso_3166_1") == this.region } ?: all.firstOrNull { it.optString("iso_3166_1") == "US" })?.optJSONArray("release_dates")
                    ?.let { r -> (0 until r.length()).map { r.getJSONObject(it).optString("certification") }.firstOrNull { it.isNotBlank() } }
            }
        } else {
            j.optJSONObject("content_ratings")?.optJSONArray("results")?.let { a ->
                val all = (0 until a.length()).map { a.getJSONObject(it) }
                (all.firstOrNull { it.optString("iso_3166_1") == this.region } ?: all.firstOrNull { it.optString("iso_3166_1") == "US" })?.optString("rating")?.takeIf { it.isNotBlank() }
            }
        }
        val col = j.optJSONObject("belongs_to_collection")?.let { it.optInt("id") to it.optString("name") }?.takeIf { it.first > 0 }
        return TmdbInfo(
            id = id, isMovie = movie, title = (if (movie) j.optString("title") else j.optString("name")),
            tagline = j.optString("tagline").takeIf { it.isNotBlank() }, overview = j.optString("overview").takeIf { it.isNotBlank() },
            rating = j.optDouble("vote_average").takeIf { !it.isNaN() && it > 0 }, votes = j.optInt("vote_count"),
            runtime = if (movie) j.optInt("runtime").takeIf { it > 0 } else j.optJSONArray("episode_run_time")?.optInt(0)?.takeIf { it > 0 },
            status = j.optString("status").takeIf { it.isNotBlank() }, date = (if (movie) j.optString("release_date") else j.optString("first_air_date")).takeIf { it.isNotBlank() },
            certification = certification, genres = names(j.optJSONArray("genres")), studios = idNames(j.optJSONArray("production_companies")).take(5),
            logo = logo, backdrop = poster(j.optString("backdrop_path"), "w1280"), poster = poster(j.optString("poster_path"), "w500"),
            backdrops = backdrops, cast = cast,
            directors = if (movie) crewBy("Director") else idNames(j.optJSONArray("created_by")),
            writers = crewBy("Writer", "Screenplay", "Story", "Novel"),
            trailerKey = trailer, reviews = reviews, providers = providers,
            similar = cards(j.optJSONObject("recommendations")?.optJSONArray("results"), movie),
            collection = col, imdbId = j.optJSONObject("external_ids")?.optString("imdb_id")?.takeIf { it.startsWith("tt") } ?: j.optString("imdb_id").takeIf { it.startsWith("tt") },
            seasons = j.optInt("number_of_seasons").takeIf { it > 0 }, episodes = j.optInt("number_of_episodes").takeIf { it > 0 },
            networks = idNames(j.optJSONArray("networks")),
        )
    }

    // ------------------------------------------------------------------ people

    suspend fun person(id: Int): TmdbPersonInfo? {
        val j = json("/person/$id", "append_to_response" to "combined_credits") ?: return null
        val credits = j.optJSONObject("combined_credits")?.optJSONArray("cast")
        val all = if (credits == null) emptyList() else (0 until credits.length()).map { credits.getJSONObject(it) }
            .filter { it.optString("poster_path").isNotBlank() && it.optString("media_type") in listOf("movie", "tv") }
            .filter { it.optJSONArray("genre_ids")?.let { g -> (0 until g.length()).none { i -> g.getInt(i) in listOf(10767, 10763, 10764) } } != false }
        val known = all.sortedByDescending { it.optDouble("popularity", 0.0) }.mapNotNull { card(it, null) }.distinctBy { it.url }.take(20)
        val filmography = all.sortedByDescending { it.optString("release_date").ifBlank { it.optString("first_air_date") } }.mapNotNull { card(it, null) }.distinctBy { it.url }
        return TmdbPersonInfo(
            id = id, name = j.optString("name"), bio = j.optString("biography").takeIf { it.isNotBlank() }, birthday = j.optString("birthday").takeIf { it.isNotBlank() && it != "null" },
            deathday = j.optString("deathday").takeIf { it.isNotBlank() && it != "null" }, place = j.optString("place_of_birth").takeIf { it.isNotBlank() && it != "null" },
            department = j.optString("known_for_department").takeIf { it.isNotBlank() }, image = poster(j.optString("profile_path"), "w500"),
            known = known, credits = filmography,
        )
    }

    // ------------------------------------------------------------------ constants

    val movieGenres = linkedMapOf(
        "Action" to 28, "Adventure" to 12, "Animation" to 16, "Comedy" to 35, "Crime" to 80, "Documentary" to 99, "Drama" to 18, "Family" to 10751,
        "Fantasy" to 14, "History" to 36, "Horror" to 27, "Mystery" to 9648, "Romance" to 10749, "Sci-Fi" to 878, "Thriller" to 53, "War" to 10752, "Western" to 37,
    )
    val tvGenres = linkedMapOf(
        "Action & Adventure" to 10759, "Animation" to 16, "Comedy" to 35, "Crime" to 80, "Documentary" to 99, "Drama" to 18, "Family" to 10751, "Kids" to 10762,
        "Mystery" to 9648, "Reality" to 10764, "Sci-Fi & Fantasy" to 10765, "War & Politics" to 10768, "Western" to 37,
    )
    private val genreNames: Map<Int, String> = (movieGenres.entries + tvGenres.entries).associate { it.value to it.key }

    /** Streaming services (TMDB provider ids) offered as filters on Explore */
    val services = linkedMapOf(
        "Netflix" to 8, "Prime Video" to 119, "Disney+" to 337, "Hotstar" to 122, "Apple TV+" to 350, "Max" to 1899,
        "Hulu" to 15, "Paramount+" to 531, "Crunchyroll" to 283, "Zee5" to 232, "SonyLIV" to 237, "JioCinema" to 220,
    )
}
