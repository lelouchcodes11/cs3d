package com.lagradost.desktop.net

import android.util.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newAudioFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * YouTube streams for a trailer, taken from YouTube's own player service the way its iPhone app asks for them. The extractor of the library
 * (NewPipe) is tried first; when YouTube has changed something it fails and the trailer used to open in the browser. Video and sound are
 * separate files (the player opens the sound beside the video, see MpvPlayer `audioTracks`).
 */
object YouTubeStreams {
    private const val TAG = "YouTubeStreams"
    private const val CLIENT_VERSION = "20.10.4"
    private const val USER_AGENT = "com.google.ios.youtube/$CLIENT_VERSION (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"
    private val idPattern = Regex("(?:youtu\\.be/|youtube(?:-nocookie)?\\.com/(?:.*v=|v/|u/\\w/|embed/|shorts/|live/))([\\w-]{11})")

    fun isYouTube(url: String): Boolean = idPattern.containsMatchIn(url)

    suspend fun links(url: String): List<ExtractorLink> {
        val id = idPattern.find(url)?.groupValues?.get(1) ?: return emptyList()
        return runCatching { fetch(id) }.onFailure { Log.w(TAG, "$id: $it") }.getOrDefault(emptyList())
    }

    private suspend fun fetch(id: String): List<ExtractorLink> {
        val body = JSONObject()
            .put("context", JSONObject().put("client", JSONObject()
                .put("clientName", "IOS").put("clientVersion", CLIENT_VERSION).put("hl", "en")
                .put("deviceMake", "Apple").put("deviceModel", "iPhone16,2").put("osName", "iPhone").put("osVersion", "18.3.2.22D82")))
            .put("videoId", id).put("contentCheckOk", true).put("racyCheckOk", true)
        val text = app.post(
            "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            headers = mapOf("User-Agent" to USER_AGENT, "X-YouTube-Client-Name" to "5", "X-YouTube-Client-Version" to CLIENT_VERSION),
            requestBody = body.toString().toRequestBody("application/json".toMediaType()),
        ).text
        val json = JSONObject(text)
        val status = json.optJSONObject("playabilityStatus")?.optString("status")
        val streaming = json.optJSONObject("streamingData")
        if (streaming == null) { Log.w(TAG, "$id: no streams (status $status)"); return emptyList() }

        // a live trailer (a premiere): the playlist plays as it is
        streaming.optString("hlsManifestUrl").takeIf { it.isNotBlank() }?.let { hls ->
            return listOf(newExtractorLink("YouTube", "YouTube Live", hls, ExtractorLinkType.M3U8) { headers = mapOf("User-Agent" to USER_AGENT) })
        }

        class Format(val url: String, val mime: String, val height: Int, val bitrate: Int, val drc: Boolean)
        val formats = streaming.optJSONArray("adaptiveFormats")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val f = a.optJSONObject(i) ?: return@mapNotNull null
                val u = f.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Format(u, f.optString("mimeType"), f.optInt("height"), f.optInt("bitrate"), u.contains("xtags=drc"))
            }
        }.orEmpty()

        // the best sound first: the player opens the first one beside the video (the compressed "drc" variant last)
        val audio = formats.filter { it.mime.startsWith("audio/") }.sortedWith(compareBy<Format> { it.drc }.thenByDescending { it.mime.startsWith("audio/mp4") }.thenByDescending { it.bitrate })
        if (audio.isEmpty()) return emptyList()
        val sound = audio.take(2).map { newAudioFile(it.url) }

        // up to 1080p (a 4K trailer asks a lot of the PC); H.264 decodes on every graphics card, VP9 and AV1 are the fallback
        val video = formats.filter { it.mime.startsWith("video/") && it.height in 1..1080 }
        fun codec(m: String) = when { m.contains("avc1") -> "H264"; m.contains("vp9") || m.contains("vp09") -> "VP9"; m.contains("av01") -> "AV1"; else -> "" }
        return video.sortedWith(compareByDescending<Format> { it.height }.thenBy { if (codec(it.mime) == "H264") 0 else 1 }).take(8).map { v ->
            newExtractorLink("YouTube", "YouTube ${codec(v.mime)}", v.url) {
                quality = v.height
                headers = mapOf("User-Agent" to USER_AGENT)
                audioTracks = sound
            }
        }
    }
}
