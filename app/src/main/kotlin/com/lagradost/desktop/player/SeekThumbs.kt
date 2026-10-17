package com.lagradost.desktop.player

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.lagradost.desktop.runtime.AndroidRuntime
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The little picture above the seek bar when the pointer is on it: a still of the scene at that time.
 *
 * A second, headless libmpv opens the same address as the player (with the same headers) and jumps to a grid of keyframes, one every
 * `duration / count` seconds, writing each as a small JPEG (`vo=image`, scaled to 320 px, no audio, software decode). It runs on a low
 * priority thread, a frame about every 0.3 s, coarse first (every 8th point, then every 4th ...) so the bar has previews at once and fills in.
 * The decoded frames are kept in memory (about 20 KB each); nothing is left on disk.
 */
class SeekThumbs(private val source: Source, private val durationMs: Long) {
    /** What the player opened: the final address (the local proxy for HLS/DASH) and the headers that go with it */
    class Source(val url: String, val userAgent: String?, val referer: String?, val headers: Map<String, String>)

    val count: Int = (durationMs / 1000L / 8L).toInt().coerceIn(20, 72)
    private val frames = ConcurrentHashMap<Int, ImageBitmap>()
    private val dir = File(AndroidRuntime.dataDir, "cache/seekthumbs/${System.nanoTime()}")

    /** Changes with every new frame: what reads it draws again */
    var ready by mutableIntStateOf(0)
        private set

    // the picture without the black bars of a letterboxed film (left, top, right, bottom of what was seen so far in any frame); null: the whole frame
    @Volatile private var picture: IntArray? = null
    @Volatile private var frameW = 0
    @Volatile private var frameH = 0

    @Volatile private var closed = false
    private var thread: Thread? = null

    fun start() {
        if (thread != null) return
        thread = Thread({ runCatching { run() }.onFailure { Log.w(TAG, "stopped: $it") }; cleanup() }, "seek-thumbs").apply {
            isDaemon = true; priority = Thread.MIN_PRIORITY; start()
        }
    }

    fun close() {
        closed = true
        frames.clear()
    }

    /** The part of a frame worth showing (left, top, right, bottom in pixels of the frame), null while all of it is */
    fun content(): IntArray? {
        @Suppress("UNUSED_EXPRESSION") ready
        val box = picture ?: return null
        val w = frameW; val h = frameH
        if (w <= 0 || h <= 0) return null
        // nearly the whole frame: nothing to cut
        if ((box[2] - box[0]) >= w * 0.94 && (box[3] - box[1]) >= h * 0.94) return null
        return box
    }

    /** Looks for black bars in [bytes] (a small JPEG) and widens the known picture area by what this frame shows */
    private fun noteContent(bytes: ByteArray) {
        val img = runCatching { javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes)) }.getOrNull() ?: return
        val w = img.width; val h = img.height
        if (w < 32 || h < 18) return
        fun lum(px: Int, py: Int): Int { val c = img.getRGB(px, py); return (((c shr 16) and 255) * 3 + ((c shr 8) and 255) * 6 + (c and 255)) / 10 }
        val sx = maxOf(1, w / 40); val sy = maxOf(1, h / 24)
        val dark = 26
        fun rowDark(py: Int): Boolean { var xx = 0; while (xx < w) { if (lum(xx, py) > dark) return false; xx += sx }; return true }
        fun colDark(px: Int): Boolean { var yy = 0; while (yy < h) { if (lum(px, yy) > dark) return false; yy += sy }; return true }
        var top = 0; while (top < h - 1 && rowDark(top)) top++
        var bottom = h - 1; while (bottom > top && rowDark(bottom)) bottom--
        var left = 0; while (left < w - 1 && colDark(left)) left++
        var right = w - 1; while (right > left && colDark(right)) right--
        // a dark scene shows almost nothing: it says nothing about the bars
        if ((right - left + 1) < w * 0.3 || (bottom - top + 1) < h * 0.3) return
        frameW = w; frameH = h
        val cur = picture
        picture = if (cur == null) intArrayOf(left, top, right + 1, bottom + 1)
        else intArrayOf(minOf(cur[0], left), minOf(cur[1], top), maxOf(cur[2], right + 1), maxOf(cur[3], bottom + 1))
    }

    /** The nearest frame that exists for [fraction] of the video (null while there is none) */
    fun frameAt(fraction: Float): ImageBitmap? {
        @Suppress("UNUSED_EXPRESSION") ready
        if (frames.isEmpty()) return null
        val want = (fraction.coerceIn(0f, 1f) * (count - 1)).let { Math.round(it) }
        for (d in 0 until count) {
            frames[want - d]?.let { return it }
            frames[want + d]?.let { return it }
        }
        return null
    }

    private fun cleanup() {
        runCatching { dir.deleteRecursively() }
    }

    private fun run() {
        val mpv = Mpv.INSTANCE
        dir.mkdirs()
        Log.i(TAG, "start: $count previews of ${durationMs / 1000} s, ${source.url.take(80)}")
        val ctx = mpv.mpv_create() ?: return
        try {
            fun opt(k: String, v: String) { mpv.mpv_set_option_string(ctx, k, v) }
            opt("config", "no"); opt("load-scripts", "no"); opt("terminal", "no"); opt("osc", "no"); opt("input-default-bindings", "no"); opt("ytdl", "no")
            opt("vo", "image"); opt("vo-image-format", "jpg"); opt("vo-image-jpeg-quality", "70"); opt("vo-image-outdir", dir.absolutePath)
            opt("ao", "null"); opt("aid", "no"); opt("sid", "no"); opt("audio-display", "no")
            opt("pause", "yes"); opt("keep-open", "yes"); opt("idle", "yes"); opt("force-window", "no")
            // a cheap decode of one frame at a time
            opt("hwdec", "no"); opt("vf", "scale=w=320:h=-2"); opt("hr-seek", "no"); opt("vd-lavc-threads", "2"); opt("vd-lavc-skiploopfilter", "all")
            opt("cache", "no"); opt("demuxer-readahead-secs", "1"); opt("demuxer-max-bytes", "8MiB"); opt("demuxer-max-back-bytes", "1MiB"); opt("network-timeout", "12")
            opt("user-agent", source.userAgent ?: com.lagradost.cloudstream3.USER_AGENT)
            source.referer?.takeIf { it.isNotBlank() }?.let { opt("referrer", it) }
            // like the player: the names are resolved the app's way
            if (System.getProperty("cloudstream.noproxy") != "true") (System.getProperty("cloudstream.httpproxy") ?: com.lagradost.desktop.net.NetProxy.address)?.let { opt("http-proxy", it) }
            if (mpv.mpv_initialize(ctx) < 0) return
            fun command(vararg args: String): Int {
                val arr = arrayOfNulls<String>(args.size + 1)
                for (i in args.indices) arr[i] = args[i]
                return mpv.mpv_command(ctx, arr)
            }
            for ((k, v) in source.headers) command("change-list", "http-header-fields", "append", "$k: $v")
            command("loadfile", source.url, "replace")
            if (!waitFor(mpv, ctx, Mpv.MPV_EVENT_FILE_LOADED, 30_000)) {
                Log.i(TAG, "the video did not open for previews")
                return
            }
            // the first frame of the file is written when it opens
            Thread.sleep(150)
            dir.listFiles()?.forEach { it.delete() }
            // coarse to fine: the points in bit-reversed order, so the bar has previews spread over the whole video from the first seconds
            val order = (0 until count).sortedBy { Integer.reverse(it) }
            var failures = 0
            for (i in order) {
                if (closed) return
                if (frames.containsKey(i)) continue
                val t = durationMs / 1000.0 * i / (count - 1).coerceAtLeast(1)
                // not at the very end: the last second has no keyframe of its own
                val at = minOf(t, durationMs / 1000.0 - 1.0).coerceAtLeast(0.0)
                command("seek", String.format(java.util.Locale.ROOT, "%.2f", at), "absolute+keyframes")
                if (!waitFor(mpv, ctx, Mpv.MPV_EVENT_PLAYBACK_RESTART, 15_000)) {
                    if (++failures >= 4) return
                    continue
                }
                // the frame is written a moment after the restart
                var file: File? = null
                val deadline = System.currentTimeMillis() + 1500
                while (System.currentTimeMillis() < deadline && !closed) {
                    file = dir.listFiles()?.filter { it.name.endsWith(".jpg") }?.maxByOrNull { it.name }
                    if (file != null && file.length() > 0) break
                    Thread.sleep(40)
                }
                if (file == null) { if (++failures >= 6) return; continue }
                Thread.sleep(30)
                val bytes = runCatching { file.readBytes() }.getOrNull()
                dir.listFiles()?.forEach { it.delete() }
                if (bytes != null && bytes.isNotEmpty()) {
                    runCatching { noteContent(bytes) }
                    runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()?.let {
                        frames[i] = it
                        ready++
                        if (frames.size % 8 == 1) Log.i(TAG, "${frames.size} of $count previews")
                    }
                    failures = 0
                }
                // easy on the network and the CPU: the film is being watched at the same time
                Thread.sleep(250)
            }
            Log.i(TAG, "previews done: ${frames.size} of $count")
        } finally {
            runCatching { mpv.mpv_terminate_destroy(ctx) }
        }
    }

    /** Waits for an event of the instance; false when the file ended or failed first, or the time ran out */
    private fun waitFor(mpv: Mpv, ctx: com.sun.jna.Pointer, wanted: Int, timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end && !closed) {
            val p = mpv.mpv_wait_event(ctx, 0.2) ?: continue
            when (Mpv.MpvEvent(p).event_id) {
                wanted -> return true
                Mpv.MPV_EVENT_END_FILE, Mpv.MPV_EVENT_SHUTDOWN -> return false
            }
        }
        return false
    }

    private companion object {
        const val TAG = "SeekThumbs"
    }
}
