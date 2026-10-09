package com.lagradost.desktop.stremio

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * What a Stremio title is played from: asks every add-on that offers streams (and subtitles) for the item and turns the answers into
 * the player's sources: direct links, HLS/DASH, YouTube, and torrents (a "magnet" source, played through the torrent engine).
 */
object StremioStreams {
    private const val TAG = "StremioStreams"

    /** What a title is: a Stremio type ("movie", "series", "anime", "tv") and id ("tt0111161", "tt0903747:1:2", "kitsu:12:3") */
    class Target(val type: String, val id: String, val inline: JSONArray? = null) {
        fun toData(): String = JSONObject().put("t", type).put("i", id).also { if (inline != null && inline.length() > 0) it.put("s", inline) }.toString()
    }

    fun parseData(data: String): Target? = runCatching {
        val j = JSONObject(data)
        Target(j.getString("t"), j.getString("i"), j.optJSONArray("s"))
    }.getOrNull()

    /** The default trackers a magnet gets when its stream names none */
    private val defaultTrackers = listOf(
        "udp://tracker.opentrackr.org:1337/announce", "udp://open.stealth.si:80/announce", "udp://tracker.torrent.eu.org:451/announce",
        "udp://explodie.org:6969/announce", "udp://open.demonii.com:1337/announce", "udp://exodus.desync.com:6969/announce",
        "udp://tracker.openbittorrent.com:6969/announce", "udp://tracker.tiny-vps.com:6969/announce",
    )

    fun magnetOf(hash: String, name: String?, trackers: List<String>): String = buildString {
        append("magnet:?xt=urn:btih:").append(hash.lowercase())
        if (!name.isNullOrBlank()) append("&dn=").append(StremioClient.enc(name))
        (trackers.ifEmpty { defaultTrackers }).forEach { append("&tr=").append(StremioClient.enc(it)) }
    }

    fun qualityOf(text: String): Int {
        val t = text.lowercase()
        return when {
            Regex("2160p|\\b4k\\b|\\buhd\\b").containsMatchIn(t) -> 2160
            Regex("1440p|\\b2k\\b").containsMatchIn(t) -> 1440
            Regex("1080p|\\bfhd\\b|full ?hd").containsMatchIn(t) -> 1080
            Regex("720p|\\bhd\\b").containsMatchIn(t) -> 720
            Regex("480p|\\bsd\\b").containsMatchIn(t) -> 480
            Regex("360p").containsMatchIn(t) -> 360
            else -> 0
        }
    }

    /** "Torrentio\n4k DV | HDR" -> "4k DV | HDR": what an add-on's stream name says beyond its own name */
    // Torrentio writes the seeders after a person icon (U+1F464); other add-ons write the word
    private val seedersRegex = Regex("""(?:👤|seeders?|seeds?)\s*[:=]?\s*(\d+)""", RegexOption.IGNORE_CASE)

    /** The number of seeders an add-on wrote into the title of a torrent, when it did */
    private fun seedersOf(s: StremioStream): Int? = seedersRegex.find((s.title ?: s.description).orEmpty())?.groupValues?.get(1)?.toIntOrNull()

    private fun displayName(addonName: String, s: StremioStream): String {
        val head = (s.name ?: "").lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ").replace(addonName, "", ignoreCase = true).trim(' ', '-', '|', ':')
        val lines = (s.title ?: s.description ?: "").lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val release = lines.firstOrNull()?.take(90).orEmpty()
        val facts = lines.drop(1).joinToString("  ") { it }.take(70)
        return buildString {
            append('[').append(addonName).append(']')
            if (head.isNotBlank()) append(' ').append(head)
            if (release.isNotBlank()) append("  ·  ").append(release)
            if (facts.isNotBlank()) append("  ·  ").append(facts)
        }
    }

    /** Language of a Stremio subtitle ("eng", "fre", "pt-BR" or a name) as the player's name */
    /** ISO 639-2/B codes (what OpenSubtitles sends) that differ from the 639-2/T codes the language list knows, and the regional ones of the add-ons */
    private val bibliographic = mapOf(
        "alb" to "sqi", "arm" to "hye", "baq" to "eus", "bur" to "mya", "chi" to "zho", "cze" to "ces", "dut" to "nld", "fre" to "fra", "geo" to "kat",
        "ger" to "deu", "gre" to "ell", "ice" to "isl", "mac" to "mkd", "mao" to "mri", "may" to "msa", "per" to "fas", "rum" to "ron", "slo" to "slk", "tib" to "bod", "wel" to "cym",
    )
    private val regional = mapOf("pob" to "Portuguese (Brazil)", "pb" to "Portuguese (Brazil)", "pt-br" to "Portuguese (Brazil)", "zht" to "Chinese (Traditional)", "zhc" to "Chinese (Simplified)", "zhs" to "Chinese (Simplified)", "spl" to "Spanish (Latin America)", "spn" to "Spanish", "ea" to "Spanish (Spain)", "scc" to "Serbian", "sr" to "Serbian")

    private fun languageName(code: String): String {
        val c = code.trim().lowercase()
        regional[c]?.let { return it }
        val t = bibliographic[c] ?: c
        return SubtitleHelper.fromThreeLettersToLanguage(t) ?: SubtitleHelper.fromTwoLettersToLanguage(t) ?: SubtitleHelper.fromTagToLanguageName(code.trim()) ?: code.trim()
    }

    private suspend fun emitSubtitles(list: List<StremioSubtitle>, addonName: String, subtitleCallback: (SubtitleFile) -> Unit) {
        for (s in list) subtitleCallback(newSubtitleFile(languageName(s.lang), s.url))
    }

    /** Turns the streams of one add-on into links; returns how many were found */
    private suspend fun emitStreams(
        addon: StremioAddon, streams: List<StremioStream>, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit, target: Target? = null,
    ): Int {
        // the episode asked for: a season pack without a file number is searched for it (see TorrentEngine.pickFile)
        val parts = target?.id?.split(':')
        val season = if (target?.type == "series" && parts != null && parts.size >= 3) parts[parts.size - 2].toIntOrNull() else null
        val episode = if (target?.type == "series" && parts != null && parts.size >= 3) parts.last().toIntOrNull() else null
        var found = 0
        for (s in streams) {
            val label = displayName(addon.name, s)
            val quality = qualityOf((s.name.orEmpty()) + " " + (s.title ?: s.description).orEmpty() + " " + s.filename.orEmpty())
            try {
                val url = s.url
                when {
                    s.infoHash != null -> {
                        val magnet = magnetOf(s.infoHash, s.filename ?: s.title?.lineSequence()?.firstOrNull(), s.trackers)
                        callback(
                            newExtractorLink(addon.name, label, magnet, ExtractorLinkType.MAGNET) {
                                this.quality = quality
                                // which file of the torrent is the video (the add-on knows: season packs hold many)
                                this.extractorData = JSONObject().put("hash", s.infoHash).also { o ->
                                    s.fileIdx?.let { o.put("fileIdx", it) }
                                    s.filename?.let { o.put("filename", it) }
                                seedersOf(s)?.let { o.put("seeders", it) }
                                    season?.let { o.put("season", it) }
                                    episode?.let { o.put("episode", it) }
                                }.toString()
                            },
                        )
                        found++
                    }
                    url != null && url.startsWith("magnet:", ignoreCase = true) -> {
                        callback(newExtractorLink(addon.name, label, url, ExtractorLinkType.MAGNET) { this.quality = quality })
                        found++
                    }
                    url != null && url.startsWith("http", ignoreCase = true) -> {
                        val type = when {
                            url.substringBefore('?').endsWith(".m3u8", true) -> ExtractorLinkType.M3U8
                            url.substringBefore('?').endsWith(".mpd", true) -> ExtractorLinkType.DASH
                            else -> ExtractorLinkType.VIDEO
                        }
                        callback(
                            newExtractorLink(addon.name, label, url, type) {
                                this.quality = quality
                                this.headers = s.headers
                                s.headers.entries.firstOrNull { it.key.equals("Referer", true) }?.let { this.referer = it.value }
                            },
                        )
                        found++
                    }
                    s.ytId != null -> {
                        loadExtractor("https://www.youtube.com/watch?v=${s.ytId}", null, subtitleCallback) { callback(it); found++ }
                    }
                    else -> if (s.externalUrl != null) Log.i(TAG, "${addon.name}: \"${label.take(60)}\" opens a web page (${s.externalUrl.take(80)}), not a video")
                }
                if (s.subtitles.isNotEmpty()) emitSubtitles(s.subtitles, addon.name, subtitleCallback)
            } catch (t: Throwable) {
                Log.w(TAG, "a stream of ${addon.name} was not usable: ${t.message}")
            }
        }
        return found
    }

    /**
     * Streams and subtitles of [target] from every add-on that has them. [skip] leaves out an add-on (the one the title came from does its
     * own streams). Returns whether a source was found.
     */
    suspend fun collect(target: Target, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean = coroutineScope {
        val total = AtomicInteger(0)
        // the title's own streams (a channel lists them in its meta)
        target.inline?.let { a ->
            val own = StremioClient.parseStreamArray(a)
            if (own.isNotEmpty()) total.addAndGet(emitStreams(StremioAddon("inline", null, true), own, subtitleCallback, callback))
        }
        val jobs = ArrayList<kotlinx.coroutines.Deferred<Any?>>()
        for (addon in StremioAddons.streamAddons().filter { it.manifest!!.provides("stream", target.type, target.id) }) {
            jobs.add(async {
                val url = StremioClient.resourceUrl(addon.manifestUrl, "stream", target.type, target.id)
                val started = System.currentTimeMillis()
                // a torrent add-on may need a while to look through its sites
                val json = withTimeoutOrNull(25_000) { StremioClient.getJson(url, ttlMs = 3 * 60_000L, timeoutSec = 22) }
                val streams = json?.let { StremioClient.parseStreams(it) }.orEmpty()
                val n = emitStreams(addon, streams, subtitleCallback, callback, target)
                total.addAndGet(n)
                Log.i(TAG, "${addon.name}: $n source(s) for ${target.type} ${target.id} in ${System.currentTimeMillis() - started} ms")
            })
        }
        for (addon in StremioAddons.subtitleAddons().filter { it.manifest!!.provides("subtitles", target.type, target.id) }) {
            jobs.add(async {
                val url = StremioClient.resourceUrl(addon.manifestUrl, "subtitles", target.type, target.id)
                val json = withTimeoutOrNull(20_000) { StremioClient.getJson(url, ttlMs = 10 * 60_000L, timeoutSec = 18) }
                val subs = json?.let { StremioClient.parseSubtitles(it) }.orEmpty()
                emitSubtitles(subs, addon.name, subtitleCallback)
                Log.i(TAG, "${addon.name}: ${subs.size} subtitle(s) for ${target.type} ${target.id}")
            })
        }
        jobs.forEach { it.await() }
        total.get() > 0
    }
}
