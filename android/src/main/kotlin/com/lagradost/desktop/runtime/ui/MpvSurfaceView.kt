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
        Thread(r, "VideoSurface-Render").apply { isDaemon = true }
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
        val bmp = bitmap?.takeIf { it.width == w && it.height == h } ?: Bitmap().also {
            it.allocPixels(ImageInfo.makeN32(w, h, ColorAlphaType.OPAQUE))
            bitmap?.close()
            bitmap = it
        }
        val pixels = bmp.peekPixels() ?: return
        if (!r.renderFrame(w, h, bmp.rowBytes, pixels.addr)) return
        bmp.notifyPixelsChanged()
        val image = Image.makeFromBitmap(bmp)
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
