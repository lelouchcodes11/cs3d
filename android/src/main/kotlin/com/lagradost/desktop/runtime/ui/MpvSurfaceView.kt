package com.lagradost.desktop.runtime.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.compose.runtime.mutableIntStateOf
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.awt.EventQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Renders the current video frame into memory: 32-bit BGRX rows of [stride] bytes at [address] */
fun interface VideoFrameRenderer {
    fun renderFrame(width: Int, height: Int, stride: Int, address: Long): Boolean
}

/** A player whose video can be shown in a [MpvSurfaceView] (the desktop side of Player.setVideoSurface) */
interface DesktopVideoOutput {
    fun setVideoSurface(surface: MpvSurfaceView?)
}

/**
 * Video surface drawn by Compose like any other view, so player controls draw above it (as a
 * SurfaceView's overlay does on Android). The player renders frames at the view's pixel size on a
 * dedicated thread; each finished frame is published as an image for the next Compose frame.
 */
open class MpvSurfaceView @JvmOverloads constructor(
    context: Context? = null,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    @Volatile
    var renderer: VideoFrameRenderer? = null
        set(value) {
            field = value
            if (value == null) clearFrame() else requestRender()
        }

    val stats = VideoFrameStats()
    val frameVersion = mutableIntStateOf(0)
    var frame: Image? = null
        private set

    @Volatile
    private var targetWidth = 0

    @Volatile
    private var targetHeight = 0
    private val pending = AtomicBoolean(false)
    private var bitmap: Bitmap? = null
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "VideoSurface-Render").apply { isDaemon = true; priority = Thread.NORM_PRIORITY + 3 }
    }

    /** A new frame is available (called from any thread); rendering is coalesced */
    fun requestRender() {
        if (!pending.compareAndSet(false, true)) return
        executor.execute {
            pending.set(false)
            try {
                renderNow()
            } catch (t: Throwable) {
                android.util.Log.e("MpvSurfaceView", "render failed", t)
            }
        }
    }

    private fun renderNow() {
        val r = renderer ?: return
        val w = targetWidth
        val h = targetHeight
        if (w <= 0 || h <= 0) return
        val started = System.nanoTime()
        val cpu = threadBean.currentThreadCpuTime
        val bmp = bitmap?.takeIf { it.width == w && it.height == h } ?: Bitmap().also {
            it.allocPixels(ImageInfo.makeN32(w, h, ColorAlphaType.OPAQUE))
            bitmap?.close()
            bitmap = it
        }
        val pixels = bmp.peekPixels() ?: return
        if (!r.renderFrame(w, h, bmp.rowBytes, pixels.addr)) return
        bmp.notifyPixelsChanged()
        val image = Image.makeFromBitmap(bmp)
        stats.frame(System.nanoTime() - started, threadBean.currentThreadCpuTime - cpu, w, h)
        EventQueue.invokeLater {
            val old = frame
            frame = image
            old?.close()
            frameVersion.intValue++
        }
    }

    /** Native UI: the size (pixels) frames are rendered at, as the Android layout would set it */
    fun setRenderSize(w: Int, h: Int) {
        if (w == targetWidth && h == targetHeight) return
        targetWidth = w
        targetHeight = h
        requestRender()
    }

    fun clearFrame() {
        EventQueue.invokeLater {
            frame?.close()
            frame = null
            frameVersion.intValue++
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        targetWidth = w
        targetHeight = h
        requestRender()
    }
}

private val threadBean = java.lang.management.ManagementFactory.getThreadMXBean()

/** How fast frames are produced (wall time includes mpv waiting for the frame's display time; cpu time is this thread's own work) */
class VideoFrameStats {
    private var frames = 0
    private var wallNs = 0L
    private var cpuNs = 0L
    private var maxNs = 0L
    private var since = System.nanoTime()
    private var lastEnd = 0L
    private var late = 0
    private var maxGapMs = 0L
    private val lateAt = StringBuilder()

    @Volatile
    var last: String = "no frames"
        private set

    @Synchronized
    fun frame(wall: Long, cpu: Long, w: Int, h: Int) {
        run {
            val now0 = System.nanoTime()
            if (lastEnd != 0L) {
                val gapMs = (now0 - lastEnd) / 1_000_000
                if (gapMs > maxGapMs) maxGapMs = gapMs
                if (gapMs > 56) { late++; if (lateAt.length < 200) lateAt.append(((now0 - since) / 100_000_000L) / 10.0).append("s:").append(gapMs).append("ms ") }
            }
            lastEnd = now0
        }
        frames++
        wallNs += wall
        cpuNs += cpu
        maxNs = maxOf(maxNs, wall)
        val now = System.nanoTime()
        if (now - since >= 5_000_000_000L) {
            val secs = (now - since) / 1e9
            last = "%dx%d %.1f fps, render %.1f ms avg (cpu %.1f ms), max %.1f ms".format(w, h, frames / secs, wallNs / 1e6 / frames, cpuNs / 1e6 / frames, maxNs / 1e6)
            last += ", gaps: late(>56 ms)=$late max=${maxGapMs} ms ${lateAt}"
            late = 0; maxGapMs = 0; lateAt.setLength(0)
            if (System.getProperty("cloudstream.videostats") != null) android.util.Log.i("VideoSurface", last)
            frames = 0; wallNs = 0; cpuNs = 0; maxNs = 0; since = now
        }
    }
}
