package com.lagradost.desktop.player

import android.util.Log
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.sun.jna.Pointer
import java.io.File

/**
 * Saves an HLS video whose sound is a stream of its own (every CNCVerse title: the picture playlist has no sound, the sound comes from an
 * EXT-X-MEDIA playlist). Joining the segments of one playlist, as the downloader does, gave a film without sound, so it refused them
 * ("can not be saved yet"). Here a hidden, silent mpv plays the link as fast as it downloads (no clock, no picture decoded but the key frames)
 * and records what it reads, the picture and the chosen sound, into one Matroska file: mpv's own muxer does the joining.
 */
object MpvRecorder {
    private const val TAG = "MpvRecorder"

    /** [ok]: the whole video was saved; otherwise [reason] says why not ([stopped]: the user stopped it) */
    class Result(val ok: Boolean, val stopped: Boolean = false, val reason: String? = null)

    /** No progress for this long while the network gives nothing: given up */
    private const val STALL_MS = 120_000L

    /**
     * Blocks until the video is saved into [out], the download is stopped ([control] answers "stop") or it fails. [progress] gets the seconds
     * saved and the length of the video. [control]: "run", "pause" or "stop", asked every half second.
     */
    fun record(link: ExtractorLink, out: File, progress: (done: Double, total: Double) -> Unit, control: () -> String): Result {
        val mpv = runCatching { Mpv.INSTANCE }.getOrElse { return Result(false, reason = "the video library is missing: ${it.message}") }
        val ctx = mpv.mpv_create() ?: return Result(false, reason = "the video library could not start")
        var proxied: String? = null
        try {
            fun opt(name: String, value: String) { mpv.mpv_set_option_string(ctx, name, value) }
            // nothing on screen, nothing heard, no user settings or scripts
            listOf(
                "config" to "no", "terminal" to "no", "osc" to "no", "load-scripts" to "no", "ytdl" to "no", "input-default-bindings" to "no",
                "vo" to "null", "ao" to "null", "ao-null-untimed" to "yes", "untimed" to "yes", "audio-display" to "no", "sid" to "no",
                // only the key frames are decoded: what is recorded is read from the stream, the decoder only keeps playback moving
                "hwdec" to "no", "vd-lavc-skipframe" to "nonkey", "vd-lavc-skiploopfilter" to "all", "vd-lavc-fast" to "yes", "framedrop" to "no",
                "network-timeout" to "30", "keep-open" to "no",
                // idle: with "no" the core quit before the file was given to it (an empty playlist at start)
                "idle" to "yes",
                "stream-record" to out.absolutePath,
            ).forEach { (k, v) -> opt(k, v) }
            if (System.getProperty("cloudstream.noproxy") != "true") com.lagradost.desktop.net.NetProxy.address?.let { opt("http-proxy", it) }
            // test runs: -Dcloudstream.recordlimit=<s> saves only the first seconds (the end of a whole film is then checked in a minute)
            val limit = System.getProperty("cloudstream.recordlimit")?.toDoubleOrNull()
            limit?.let { opt("end", it.toString()) }
            val headers = buildMap {
                put("User-Agent", link.headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value ?: com.lagradost.cloudstream3.USER_AGENT)
                link.headers.forEach { (k, v) -> if (!k.equals("Range", true)) put(k, v) }
                if (link.referer.isNotBlank() && link.headers.keys.none { it.equals("Referer", true) }) put("Referer", link.referer)
            }
            opt("user-agent", headers.getValue("User-Agent"))
            headers.entries.firstOrNull { it.key.equals("Referer", true) }?.let { opt("referrer", it.value) }
            if (mpv.mpv_initialize(ctx) < 0) return Result(false, reason = "the video library could not start")
            for ((k, v) in headers) if (!k.equals("User-Agent", true) && !k.equals("Referer", true)) mpv.mpv_command(ctx, arrayOf("change-list", "http-header-fields", "append", "$k: $v", null))
            // the playlists come through the app (its DNS, the headers, one sound language), as when the video plays
            val address = com.lagradost.desktop.net.UrlFix.encode(link.url)
            // an extension that fetches its videos its own way (getVideoInterceptor) does so here too, as in the player
            val interceptor = runCatching { com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(link.source)?.getVideoInterceptor(link) }.getOrNull()
            val dash = link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.DASH
            // the sound language the viewer plays in (the playlist server gives mpv one of them): a title watched in Hindi was saved with the default English
            val language = com.lagradost.cloudstream3.ui.player.CS3IPlayer.preferredAudioTrackLanguage ?: runCatching {
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(com.lagradost.desktop.runtime.AndroidRuntime.context).getString(com.lagradost.desktop.ui.screens.player.PlayerSession.AUDIO_LANG_KEY, null)
            }.getOrNull()
            proxied = runCatching { if (dash) DashProxy.wrap(address, headers) else HlsProxy.wrap(address, headers, interceptor, audioLanguage = language) }.getOrNull()
            val target = proxied ?: address
            Log.i(TAG, "recording ${link.name} into ${out.name}")
            if (mpv.mpv_command(ctx, arrayOf("loadfile", target, null)) < 0) return Result(false, reason = "the video could not be opened")

            var lastPos = -1.0
            // at the end of the file mpv no longer reports the position and length: the last ones seen are what counts
            var lastTotal = 0.0
            var lastMove = System.currentTimeMillis()
            var paused = false
            while (true) {
                val ev = mpv.mpv_wait_event(ctx, 0.5)
                val id = ev?.getInt(0) ?: Mpv.MPV_EVENT_NONE
                if (id == Mpv.MPV_EVENT_END_FILE) {
                    val data: Pointer? = ev?.getPointer(16)
                    val reason = data?.getInt(0) ?: -1
                    val error = data?.getInt(4) ?: 0
                    val pos = number(mpv, ctx, "time-pos") ?: lastPos
                    val total = number(mpv, ctx, "duration") ?: lastTotal
                    // 0 = the end of the file; the last seconds may not be reported by time-pos, the file is the proof
                    return if (reason == 0 && out.isFile && out.length() > 1_000_000 && (total <= 0 || pos >= minOf(total, limit ?: total) - 60)) {
                        progress(total, total)
                        Log.i(TAG, "saved ${out.name}: ${out.length() / 1_048_576} MB, ${total.toLong()} s")
                        Result(true)
                    } else Result(false, reason = "the video stopped at ${pos.toLong()} of ${total.toLong()} s (${mpv.mpv_error_string(error) ?: "end $reason"})")
                }
                if (id == Mpv.MPV_EVENT_SHUTDOWN) return Result(false, reason = "the video library stopped")
                // a live channel has no end: it is not saved
                if (proxied != null && (HlsProxy.isLive(proxied) || DashProxy.isLive(proxied))) { mpv.mpv_command(ctx, arrayOf("stop", null)); return Result(false, reason = "a live channel can not be saved") }
                when (control()) {
                    "stop" -> { mpv.mpv_command(ctx, arrayOf("stop", null)); return Result(false, stopped = true) }
                    "pause" -> if (!paused) { paused = true; mpv.mpv_set_property_string(ctx, "pause", "yes") }
                    else -> if (paused) { paused = false; mpv.mpv_set_property_string(ctx, "pause", "no"); lastMove = System.currentTimeMillis() }
                }
                if (paused) continue
                val pos = number(mpv, ctx, "time-pos") ?: continue
                val total = number(mpv, ctx, "duration") ?: 0.0
                if (total > 0) lastTotal = total
                val now = System.currentTimeMillis()
                if (pos > lastPos + 0.5) { lastPos = pos; lastMove = now; progress(pos, total) }
                else if (now - lastMove > STALL_MS) return Result(false, reason = "nothing came for ${STALL_MS / 1000} s")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "recording failed: $t")
            return Result(false, reason = t.message ?: t.toString())
        } finally {
            // the recording is finished and the file closed when the core goes
            runCatching { mpv.mpv_terminate_destroy(ctx) }
            proxied?.let { runCatching { HlsProxy.cancel(it) }; runCatching { DashProxy.cancel(it) } }
        }
    }

    private fun number(mpv: Mpv, ctx: Pointer, name: String): Double? {
        val p = mpv.mpv_get_property_string(ctx, name) ?: return null
        return try { p.getString(0, "UTF-8").toDoubleOrNull() } finally { mpv.mpv_free(p) }
    }
}
