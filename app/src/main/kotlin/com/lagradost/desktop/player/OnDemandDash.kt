package com.lagradost.desktop.player

import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.w3c.dom.Element
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

/**
 * DASH "on demand" manifests (profile isoff-on-demand: every quality is one fragmented MP4 file, `SegmentBase` with an `indexRange` that points
 * at the file's seek index). ffmpeg's DASH reader takes each file for one endless segment: the video plays but cannot be seeked (IStreamFlare's
 * OK.ru links). ExoPlayer on Android reads the index. Here the files are played directly instead: the best video quality for the screen, with the
 * best audio file as an external track; mpv seeks inside both with HTTP ranges.
 */
object OnDemandDash {
    private const val TAG = "OnDemandDash"

    /** [video] and [audio] (null when the video file has its own sound) to play instead of the manifest */
    data class Files(val video: String, val audio: String?, val height: Int)

    private val client by lazy {
        com.lagradost.cloudstream3.app.baseClient.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()
    }

    /** The highest video the screen can show (at least 1080 lines): a 4K file on a smaller screen only costs bandwidth */
    private fun heightCap(): Int = runCatching {
        java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.maxOf { it.displayMode.height }
    }.getOrDefault(1080).coerceAtLeast(1080)

    /** The files of an on-demand manifest at [url], or null when it is another kind of DASH (live, segment templates or lists) */
    fun resolve(url: String, headers: Map<String, String>): Files? {
        val builder = Request.Builder().url(url)
        for ((k, v) in headers) if (!k.equals("Range", true)) com.lagradost.desktop.net.RawHeaders.set(builder, k, v)
        val (xml, finalUrl) = client.newCall(builder.build()).execute().use { r ->
            if (!r.isSuccessful || r.body.contentLength() > (4L shl 20)) return null
            r.body.string() to r.request.url.toString()
        }
        if (!xml.contains("<MPD") || !xml.contains("SegmentBase")) return null
        val doc = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }.newDocumentBuilder().parse(xml.byteInputStream())
        val mpd = doc.documentElement
        if (mpd.getAttribute("type") == "dynamic") return null
        val period = mpd.children("Period").firstOrNull() ?: return null
        // a manifest with templates or segment lists anywhere is left to the DASH reader
        if (doc.getElementsByTagName("SegmentTemplate").length > 0 || doc.getElementsByTagName("SegmentList").length > 0) return null

        val base = listOf(mpd, period).fold(finalUrl) { acc, e -> e.baseUrl()?.let { resolveUrl(acc, it) } ?: acc }

        data class Rep(val url: String, val bandwidth: Long, val height: Int, val hasAudio: Boolean)

        val videos = ArrayList<Rep>()
        val audios = ArrayList<Rep>()
        for (set in period.children("AdaptationSet")) {
            val setBase = set.baseUrl()?.let { resolveUrl(base, it) } ?: base
            val setType = set.getAttribute("contentType").ifEmpty { set.getAttribute("mimeType").substringBefore('/') }
            for (rep in set.children("Representation")) {
                val repUrl = rep.baseUrl()?.let { resolveUrl(setBase, it) } ?: return null
                val type = setType.ifEmpty { rep.getAttribute("mimeType").substringBefore('/') }
                val codecs = rep.getAttribute("codecs").ifEmpty { set.getAttribute("codecs") }
                val r = Rep(
                    repUrl, rep.getAttribute("bandwidth").toLongOrNull() ?: 0L, rep.getAttribute("height").toIntOrNull() ?: 0,
                    codecs.contains("mp4a") || codecs.contains("opus") || codecs.contains("ac-3") || codecs.contains("ec-3"),
                )
                when (type) {
                    "video" -> videos.add(r)
                    "audio" -> audios.add(r)
                }
            }
        }
        if (videos.isEmpty()) return null
        val cap = heightCap()
        val video = videos.filter { it.height in 1..cap }.maxWithOrNull(compareBy({ it.height }, { it.bandwidth }))
            ?: videos.minByOrNull { it.height } ?: return null
        val audio = if (video.hasAudio) null else audios.maxByOrNull { it.bandwidth }?.url
        Log.i(TAG, "on-demand DASH: ${video.height}p of ${videos.map { it.height }.sorted()} (cap $cap), audio ${if (audio != null) "separate" else "in the video"}")
        return Files(video.url, audio, video.height)
    }

    private fun resolveUrl(base: String, ref: String): String = base.toHttpUrlOrNull()?.resolve(ref.trim())?.toString() ?: ref.trim()

    private fun Element.children(name: String): List<Element> {
        val out = ArrayList<Element>()
        val nodes = childNodes
        for (i in 0 until nodes.length) (nodes.item(i) as? Element)?.takeIf { it.tagName.substringAfter(':') == name }?.let(out::add)
        return out
    }

    private fun Element.baseUrl(): String? = children("BaseURL").firstOrNull()?.textContent?.trim()?.takeIf { it.isNotEmpty() }
}
