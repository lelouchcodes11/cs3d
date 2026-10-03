package com.facebook.shimmer

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.FrameLayout
import com.lagradost.desktop.runtime.ui.ViewAttributes

/**
 * Facebook shimmer: a highlight band sweeps across the children.
 * The layouts use shimmer_auto_start, shimmer_duration and the two alphas.
 */
open class ShimmerFrameLayout : FrameLayout {
    private var durationMs = 1000
    private var highlightAlpha = 0.3f
    private var autoStart = false
    private var running = false
    private var generation = 0
    private val overlay = ShimmerDrawable()

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        read(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        read(attrs)
    }

    private fun read(attrs: AttributeSet?) {
        if (attrs == null) return
        val r = ViewAttributes.reader(this, attrs)
        r.int("shimmer_duration")?.let { if (it > 0) durationMs = it }
        r.float("shimmer_highlight_alpha")?.let { highlightAlpha = it.coerceIn(0f, 1f) }
        r.bool("shimmer_auto_start")?.let { autoStart = it }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (autoStart) startShimmer()
    }

    override fun onDetachedFromWindow() {
        stopShimmer()
        super.onDetachedFromWindow()
    }

    open fun startShimmer() {
        if (running) return
        running = true
        val gen = ++generation
        if (getForeground() == null || getForeground() === overlay) setForeground(overlay)
        val tick = object : Runnable {
            override fun run() {
                if (gen != generation || !running) return
                invalidate()
                postDelayed(this, 16)
            }
        }
        post(tick)
    }

    open fun stopShimmer() {
        if (!running && getForeground() !== overlay) return
        running = false
        generation++
        if (getForeground() === overlay) setForeground(null)
        invalidate()
    }

    open fun isShimmerStarted(): Boolean = running

    private inner class ShimmerDrawable : Drawable() {
        private val paint = Paint()

        override fun draw(canvas: Canvas) {
            val b = getBounds()
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 1f || h <= 1f) return
            val duration = durationMs.coerceAtLeast(1)
            val phase = (System.nanoTime() / 1_000_000L % duration) / duration.toFloat()
            val band = w * 0.35f
            val x = -band + phase * (w + band)
            val alpha = (highlightAlpha.coerceIn(0f, 1f) * 255f).toInt()
            val color = (alpha shl 24) or 0x00FFFFFF
            paint.setShader(
                LinearGradient(
                    x, 0f, x + band, h * 0.15f,
                    intArrayOf(0, color, 0),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP,
                )
            )
            canvas.drawRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), paint)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}

        @Deprecated("Deprecated in Android")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }
}
