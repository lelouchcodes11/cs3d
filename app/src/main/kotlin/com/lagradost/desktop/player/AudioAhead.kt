package com.lagradost.desktop.player

import android.util.Log

/**
 * The loudness of the voices in a stretch of a video's sound, read ahead of where it plays (for the automatic subtitle timing).
 *
 * A second mpv, with no picture, no subtitles and no sound out, opens the same address at [from] and decodes [seconds] of the sound as fast as
 * the connection allows. Its filter cuts the sound into 0.1 s pieces of the voice band and reports each one's loudness with the piece's own time
 * in the video (mpv starts every file at 0, as the playing one does), so the result needs no correction for buffers. It costs that stretch of the
 * stream once more (a muxed stream brings its picture data along) and a little decoding; nothing is kept.
 */
object AudioAhead {
    private const val TAG = "AudioAhead"

    /** Loudness in dB per 100 ms of video time (key = time in tenths of a second), or null when the stream could not be read */
    fun read(main: MpvPlayer, from: Double, seconds: Double, timeoutMs: Long, cancelled: () -> Boolean): Map<Int, Float>? {
        val url = main.getMpvPropertyString("path")?.takeIf { it.isNotBlank() } ?: return null
        val mpv = Mpv.INSTANCE
        val ctx = mpv.mpv_create() ?: return null
        val loud = HashMap<Int, Float>()
        // FFmpeg has one log for the whole program and mpv hands it to the first player (the visible one): the filter writes to this file instead
        val out = java.io.File.createTempFile("cs-subsync", ".txt")
        var destroyed = false
        try {
            fun opt(name: String, value: String) = mpv.mpv_set_option_string(ctx, name, value)
            opt("config", "no"); opt("terminal", "no"); opt("input-default-bindings", "no"); opt("ytdl", "no")
            opt("vid", "no"); opt("sid", "no"); opt("vo", "null"); opt("audio-display", "no")
            // no speakers, played 100x as fast (pitch left alone: nothing is heard, and the filter needs the samples unchanged). "untimed" output
            // was tried first: the null output reported an underrun at once and mpv took it for the end of the file, with nothing measured
            opt("ao", "null"); opt("speed", System.getProperty("cloudstream.aheadspeed") ?: "100"); opt("audio-pitch-correction", "no")
            opt("start", "%.3f".format(java.util.Locale.ROOT, from.coerceAtLeast(0.0)))
            opt("length", "%.3f".format(java.util.Locale.ROOT, seconds))
            main.getMpvPropertyString("aid")?.takeIf { it != "no" && it != "auto" && it.isNotBlank() }?.let { opt("aid", it) }
            // the playing video's way into the stream: its headers, cookies and demuxer options
            for (name in listOf("user-agent", "referrer", "http-header-fields", "demuxer-lavf-o", "stream-lavf-o", "tls-verify", "network-timeout")) {
                main.getMpvPropertyString(name)?.takeIf { it.isNotBlank() }?.let { opt(name, it) }
            }
            opt(
                "af",
                "lavfi=[aresample=8000,highpass=f=300,lowpass=f=3000,asetnsamples=n=800:p=0," +
                    "astats=metadata=1:reset=1:measure_perchannel=none:measure_overall=RMS_level," +
                    "ametadata=mode=print:key=lavfi.astats.Overall.RMS_level:file='" + out.absolutePath.replace('\\', '/').replace(":", "\\:") + "']",
            )
            val debug = System.getProperty("cloudstream.aheadlog") != null
            if (debug) mpv.mpv_request_log_messages(ctx, "v")
            if (mpv.mpv_initialize(ctx) < 0) return null
            mpv.mpv_command(ctx, arrayOf("loadfile", url, null))
            val giveUp = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < giveUp && !cancelled()) {
                val ptr = mpv.mpv_wait_event(ctx, 0.1) ?: continue
                val event = Mpv.MpvEvent(ptr)
                when (event.event_id) {
                    Mpv.MPV_EVENT_LOG_MESSAGE -> {
                        val msg = event.getLogMessage() ?: continue
                        val text = msg.text ?: continue
                        // -Dcloudstream.aheadlog=1: the hidden player's own log, for when nothing is measured
                        Log.i(TAG, "[${msg.prefix}] ${text.trimEnd()}")
                    }
                    Mpv.MPV_EVENT_END_FILE, Mpv.MPV_EVENT_SHUTDOWN -> break
                }
            }
            mpv.mpv_terminate_destroy(ctx).also { destroyed = true }
            // "frame:12   pts:9600   pts_time:1.2" then "lavfi.astats.Overall.RMS_level=-23.1"
            var pts: Double? = null
            out.forEachLine { line ->
                if (line.startsWith("frame:")) pts = line.substringAfter("pts_time:").trim().toDoubleOrNull()
                else if (line.contains("RMS_level=")) {
                    val t = pts ?: return@forEachLine
                    // a reading covers the 0.1 s from its time on: it belongs to the middle of that slice
                    loud[((t + 0.05) * 10).toInt()] = (line.substringAfter('=').trim().toFloatOrNull() ?: -90f).coerceAtLeast(-90f)
                    pts = null
                }
            }
            Log.i(TAG, "read ${loud.size / 10} s of sound ahead from ${"%.1f".format(from)} s")
            return loud.takeIf { it.isNotEmpty() }
        } catch (t: Throwable) {
            Log.w(TAG, "could not read ahead: ${t.message}")
            return loud.takeIf { it.isNotEmpty() }
        } finally {
            if (!destroyed) runCatching { mpv.mpv_terminate_destroy(ctx) }
            out.delete()
        }
    }
}
