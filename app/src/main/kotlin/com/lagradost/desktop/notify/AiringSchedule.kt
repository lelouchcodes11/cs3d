package com.lagradost.desktop.notify

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.DataStoreHelper
import org.json.JSONObject

/**
 * What airs in the coming days, from AniList's public airing schedule (GraphQL, no key). Marked "in your list" when the title is one the
 * person subscribed to, saved in the library or is continuing.
 */
object AiringSchedule {
    class Airing(
        val mediaId: Int,
        val title: String,
        val alt: String?,
        val cover: String?,
        val episode: Int,
        /** Unix seconds */
        val airingAt: Long,
        val score: Int?,
        val popularity: Int,
        val format: String?,
        val inList: Boolean,
    )

    private var cached: Pair<Long, List<Airing>>? = null
    private const val CACHE_MS = 20L * 60 * 1000

    private const val QUERY = "query(\$from:Int,\$to:Int,\$page:Int){Page(page:\$page,perPage:50){pageInfo{hasNextPage} " +
        "airingSchedules(airingAt_greater:\$from,airingAt_lesser:\$to,sort:TIME){airingAt episode media{id idMal title{romaji english} " +
        "coverImage{large} format isAdult averageScore popularity}}}}"

    /** Normalised title: lower case letters and digits only, so "Re:Zero" and "Re Zero" are the same */
    private fun norm(s: String?): String = s.orEmpty().lowercase().filter { it.isLetterOrDigit() }

    /** AniList ids and normalised names of everything the person follows */
    private fun followed(): Pair<Set<Int>, Set<String>> {
        val ids = HashSet<Int>()
        val names = HashSet<String>()
        fun add(name: String?, sync: Map<String, String>?) {
            norm(name).takeIf { it.length >= 3 }?.let { names += it }
            sync?.get("anilist")?.toIntOrNull()?.let { ids += it }
        }
        runCatching { DataStoreHelper.getAllSubscriptions().forEach { add(it.name, it.syncData) } }
        runCatching { DataStoreHelper.getAllBookmarkedData().forEach { add(it.name, it.syncData) } }
        runCatching { DataStoreHelper.getAllFavorites().forEach { add(it.name, it.syncData) } }
        return ids to names
    }

    /** Episodes airing between now - 1 h and now + [days] days, soonest first */
    suspend fun upcoming(days: Int = 7, force: Boolean = false): List<Airing> {
        val now = System.currentTimeMillis()
        cached?.let { (at, list) -> if (!force && now - at < CACHE_MS) return list }
        val out = fetch((now / 1000L - 3600L).toInt(), (now / 1000L + days * 86400L).toInt())
        cached = now to out
        return out
    }

    private var timetableCache: Triple<String, Long, List<Airing>>? = null

    /**
     * The timetable: every episode that airs on the [days] days from [first] (the viewer's own days, midnight to midnight, so what aired
     * earlier today is in it too), soonest first.
     */
    suspend fun timetable(first: java.time.LocalDate, days: Int = 7, force: Boolean = false): List<Airing> {
        val now = System.currentTimeMillis()
        val zone = java.time.ZoneId.systemDefault()
        val key = "$first/$days/$zone"
        timetableCache?.let { (k, at, list) -> if (!force && k == key && now - at < CACHE_MS) return list }
        val from = first.atStartOfDay(zone).toEpochSecond().toInt()
        val to = first.plusDays(days.toLong()).atStartOfDay(zone).toEpochSecond().toInt()
        val out = fetch(from, to)
        timetableCache = Triple(key, now, out)
        return out
    }

    // the reading of each page of the answer blocks its thread: on the UI thread (the page asks from a LaunchedEffect) that was a stall of 250 to 300 ms
    // when the timetable was opened for the first time
    private suspend fun fetch(from: Int, to: Int): List<Airing> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val (ids, names) = followed()
        val out = ArrayList<Airing>()
        var page = 1
        // a whole week of episodes is 300 to 500 (AniList gives 50 a page)
        while (page <= 14) {
            val body = mapOf("query" to QUERY, "variables" to mapOf("from" to from, "to" to to, "page" to page))
            val json = JSONObject(app.post("https://graphql.anilist.co", json = body, timeout = 20).text)
            // AniList's own words when it answers without data (it was switched off in October 2026): the page then says so, and no empty timetable is kept as the answer
            com.lagradost.desktop.net.ServiceOutage.requireData("AniList", json)
            val data = json.optJSONObject("data")?.optJSONObject("Page") ?: break
            val arr = data.optJSONArray("airingSchedules") ?: break
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val m = e.optJSONObject("media") ?: continue
                if (m.optBoolean("isAdult")) continue
                val t = m.optJSONObject("title")
                val romaji = t?.optString("romaji")?.takeIf { it.isNotBlank() && it != "null" }
                val english = t?.optString("english")?.takeIf { it.isNotBlank() && it != "null" }
                val title = english ?: romaji ?: continue
                val id = m.optInt("id")
                val mine = id in ids || norm(english) in names || norm(romaji) in names
                out += Airing(
                    id, title, if (english != null && romaji != null && english != romaji) romaji else null,
                    m.optJSONObject("coverImage")?.optString("large")?.takeIf { it.isNotBlank() },
                    e.optInt("episode"), e.optLong("airingAt"), m.optInt("averageScore").takeIf { it > 0 }, m.optInt("popularity"),
                    m.optString("format").takeIf { it.isNotBlank() && it != "null" }, mine,
                )
            }
            if (data.optJSONObject("pageInfo")?.optBoolean("hasNextPage") != true) break
            page++
        }
        out
    }
}
