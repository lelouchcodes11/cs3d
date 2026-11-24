package com.lagradost.desktop.torrent

import android.util.Log
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import java.net.URLEncoder

/**
 * Searches public anime torrent indexes for a title (the torrent picker of the Details page, like Seanime's "Select a torrent to stream"):
 * Nyaa's RSS feed and AnimeTosho's JSON feed. Every result is read for what the picker shows and filters on: release group, episode,
 * resolution, batch, dual audio / multi subs, seeders, size, age. Nothing is downloaded here; a chosen result is played as a magnet link
 * by the torrent engine (with the viewer's consent, like any torrent source).
 */
object TorrentSearch {
    private const val TAG = "TorrentSearch"

    enum class Provider(val label: String) { Nyaa("Nyaa"), AnimeTosho("AnimeTosho") }

    class Result(
        val provider: Provider,
        val name: String,
        val hash: String,
        val seeders: Int,
        val leechers: Int,
        val bytes: Long,
        /** Unix milliseconds */
        val time: Long,
        val trusted: Boolean,
        val page: String?,
        /** Trackers the source names for this torrent (AnimeTosho's magnet: nekoBT releases are announced on its own tracker) */
        val ownTrackers: List<String> = emptyList(),
    ) {
        val group: String? = Regex("""^\s*\[([^\]]{1,40})]""").find(name)?.groupValues?.get(1)
        val resolution: Int? = Regex("""(?<!\d)(2160|1080|720|576|480|360)[pP]""").find(name)?.groupValues?.get(1)?.toIntOrNull()
            ?: if (Regex("""\b4K\b|\bUHD\b""", RegexOption.IGNORE_CASE).containsMatchIn(name)) 2160 else null
        /** A range of episodes or a whole season in one torrent */
        val batch: Boolean = Regex("""\bbatch\b|\bcomplete\b|(?<!\d)\d{1,3}\s*[-~]\s*\d{1,3}(?!\d)(?![pP])|\bS\d{1,2}(?!\s*E\d)\b""", RegexOption.IGNORE_CASE).containsMatchIn(name.substringBefore(".mkv"))
            && episode == null
        val episode: Int? get() = episodeOf(name)
        /** The seasons a release says it holds ("S2", "Season 02", "2nd Season", "S01-S05"), null when it does not say */
        val seasons: IntRange? get() = seasonsOf(name)
        val dualAudio: Boolean = Regex("""dual[ -]?audio""", RegexOption.IGNORE_CASE).containsMatchIn(name)
        val multiSubs: Boolean = Regex("""multi[ -]?subs?|multiple subtitle""", RegexOption.IGNORE_CASE).containsMatchIn(name)
        val magnet: String get() = "magnet:?xt=urn:btih:$hash&dn=" + URLEncoder.encode(name, "UTF-8") + (ownTrackers + TRACKERS).distinct().joinToString("") { "&tr=" + URLEncoder.encode(it, "UTF-8") }
    }

    private val TRACKERS = listOf(
        "http://nyaa.tracker.wf:7777/announce", "udp://open.stealth.si:80/announce", "udp://tracker.opentrackr.org:1337/announce",
        "udp://exodus.desync.com:6969/announce", "udp://tracker.torrent.eu.org:451/announce",
    )

    /** The seasons a release name states: "S02", "S2E05", "Season 2", "2nd Season", a range "S01-S05" / "Season 1-3"; null when it states none */
    fun seasonsOf(name: String): IntRange? {
        val clean = name.replace(Regex("""\[[^\]]*]"""), " ")
        Regex("""\bS(\d{1,2})\s*[-~]\s*S?(\d{1,2})\b|\bSeasons?\s*(\d{1,2})\s*[-~]\s*(\d{1,2})\b""", RegexOption.IGNORE_CASE).find(clean)?.let { m ->
            val a = m.groupValues[1].ifEmpty { m.groupValues[3] }.toInt()
            val b = m.groupValues[2].ifEmpty { m.groupValues[4] }.toInt()
            if (a <= b) return a..b
        }
        Regex("""\bS(\d{1,2})(?=E\d|\b)|\bSeason\s*(\d{1,2})\b|\b(\d{1,2})(?:st|nd|rd|th)\s+Season\b""", RegexOption.IGNORE_CASE).find(clean)?.let { m ->
            val n = m.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.toIntOrNull() ?: return null
            return n..n
        }
        return null
    }

    /** The episode a release name is about: "S01E06", "- 06", "EP06", "E06 ", "#06" (a version suffix v2 is fine); null for none or a range */
    fun episodeOf(name: String): Int? {
        val clean = name.replace(Regex("""\[[^\]]*]|\([^)]*\)"""), " ")
        Regex("""S\d{1,2}\s*E(\d{1,4})(?!\d)""", RegexOption.IGNORE_CASE).find(clean)?.let { return it.groupValues[1].toIntOrNull() }
        Regex("""(?:\bEP?|Episode|#)\s*\.?\s*(\d{1,4})(?:v\d)?(?!\d)""", RegexOption.IGNORE_CASE).find(clean)?.let { return it.groupValues[1].toIntOrNull() }
        Regex("""\s-\s(\d{1,4})(?:v\d)?(?:\s|$|\.)""").find(clean)?.let { m ->
            // "- 01 - 12" is a range
            if (Regex("""\s-\s\d{1,4}\s*[-~]\s*\d""").containsMatchIn(clean)) return null
            return m.groupValues[1].toIntOrNull()
        }
        return null
    }

    /** What each provider answered: the results, or null when it could not be reached */
    class Answer(val results: List<Result>, val failed: Set<Provider>)

    suspend fun search(query: String, providers: Set<Provider>): Answer = coroutineScope {
        val jobs = providers.map { p -> p to async(Dispatchers.IO) { withTimeoutOrNull(25_000) { runCatching { fetch(p, query) }.onFailure { Log.w(TAG, "${p.label}: $it") }.getOrNull() } } }
        val failed = HashSet<Provider>()
        val all = ArrayList<Result>()
        for ((p, job) in jobs) { val r = job.await(); if (r == null) failed += p else all += r }
        // the same release on both sites: keep the one with more seeders
        Answer(all.groupBy { it.hash.lowercase() }.values.map { same -> same.maxBy { it.seeders } }, failed)
    }

    /** Like [search], but [onUpdate] gets the merged answer each time one provider is done (a slow or blocked site does not hold the others back) */
    suspend fun searchEach(query: String, providers: Set<Provider>, onUpdate: (Answer, Boolean) -> Unit) = coroutineScope {
        val lock = Any()
        val failed = HashSet<Provider>()
        val all = ArrayList<Result>()
        var left = providers.size
        providers.map { p ->
            async(Dispatchers.IO) {
                val r = withTimeoutOrNull(25_000) { runCatching { fetch(p, query) }.onFailure { Log.w(TAG, "${p.label}: $it") }.getOrNull() }
                val snapshot = synchronized(lock) {
                    if (r == null) failed += p else all += r
                    left--
                    Answer(all.groupBy { it.hash.lowercase() }.values.map { same -> same.maxBy { it.seeders } }, failed.toSet()) to (left == 0)
                }
                withContext(Dispatchers.Main) { onUpdate(snapshot.first, snapshot.second) }
            }
        }.awaitAll()
    }

    private suspend fun fetch(p: Provider, query: String): List<Result> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query, "UTF-8")
        when (p) {
            Provider.Nyaa -> parseNyaa(app.get("https://nyaa.si/?page=rss&q=$q&c=1_0&f=0", timeout = 20).text)
            // feed.animetosho.org stopped resolving (2026: the .org site announced its shutdown); the feed goes on at .net
            Provider.AnimeTosho -> parseTosho(app.get("https://feed.animetosho.net/json?q=$q", timeout = 20).text)
        }
    }

    private fun tag(item: String, name: String): String? =
        Regex("""<$name>(?:<!\[CDATA\[)?(.*?)(?:]]>)?</$name>""", RegexOption.DOT_MATCHES_ALL).find(item)?.groupValues?.get(1)?.trim()

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")

    private fun parseNyaa(xml: String): List<Result> = Regex("""<item>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).mapNotNull { m ->
        val it = m.groupValues[1]
        val hash = tag(it, "nyaa:infoHash") ?: return@mapNotNull null
        Result(
            Provider.Nyaa, unescape(tag(it, "title") ?: return@mapNotNull null), hash,
            tag(it, "nyaa:seeders")?.toIntOrNull() ?: 0, tag(it, "nyaa:leechers")?.toIntOrNull() ?: 0,
            sizeBytes(tag(it, "nyaa:size")), runCatching { java.time.ZonedDateTime.parse(tag(it, "pubDate")!!.replace("-0000", "+0000"), java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrDefault(0L),
            tag(it, "nyaa:trusted") == "Yes", tag(it, "guid"),
        )
    }.toList()

    private fun parseTosho(json: String): List<Result> {
        val arr = JSONArray(json)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val hash = o.optString("info_hash").takeIf { it.length >= 32 } ?: return@mapNotNull null
            Result(
                Provider.AnimeTosho, o.optString("title"), hash, o.optInt("seeders", 0), o.optInt("leechers", 0), o.optLong("total_size", 0L),
                o.optLong("timestamp", 0L) * 1000L, false, o.optString("link").takeIf { it.isNotBlank() },
                o.optString("magnet_uri").split('&').filter { it.startsWith("tr=") }.map { java.net.URLDecoder.decode(it.removePrefix("tr="), "UTF-8") },
            )
        }
    }

    /** "641.2 MiB", "1.4 GiB" */
    private fun sizeBytes(s: String?): Long {
        val m = Regex("""([\d.]+)\s*([KMGT])i?B""", RegexOption.IGNORE_CASE).find(s ?: return 0) ?: return 0
        val n = m.groupValues[1].toDoubleOrNull() ?: return 0
        val mult = when (m.groupValues[2].uppercase()) { "K" -> 1L shl 10; "M" -> 1L shl 20; "G" -> 1L shl 30; else -> 1L shl 40 }
        return (n * mult).toLong()
    }
}
