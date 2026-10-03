package com.google.android.material.progressindicator

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue

abstract class BaseProgressIndicatorSpec(context: Context) {
    @JvmField var trackThickness: Int = dp(context, 4f)
    @JvmField var trackCornerRadius: Int = 0
    @JvmField var indicatorColors: IntArray = intArrayOf(0xFF3D50FA.toInt())
    @JvmField var trackColor: Int = 0
    @JvmField var showAnimationBehavior: Int = 0
    @JvmField var hideAnimationBehavior: Int = 0

    protected companion object {
        fun dp(context: Context, v: Float): Int =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.getResources().getDisplayMetrics()).toInt()
    }
}

/** Material's circular spec; the ExtraSmall style is a 20dp circle with a 2.5dp track */
class CircularProgressIndicatorSpec(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
    BaseProgressIndicatorSpec(context) {
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)

    @JvmField var indicatorSize: Int
    @JvmField var indicatorInset: Int = 0
    @JvmField var indicatorDirection: Int = 0

    init {
        val extraSmall = defStyleRes != 0 && runCatching {
            context.getResources().getResourceEntryName(defStyleRes).contains("ExtraSmall")
        }.getOrDefault(false)
        indicatorSize = dp(context, if (extraSmall) 20f else 40f)
        trackThickness = dp(context, if (extraSmall) 2.5f else 4f)
    }
}

// desktop: a rotating arc drawn with the first indicator color, animated by self-invalidation
class IndeterminateDrawable<S : BaseProgressIndicatorSpec> private constructor(private val spec: S) : Drawable(), Animatable {
    private val paint = Paint().apply {
        setAntiAlias(true)
        setStyle(Paint.Style.STROKE)
        setStrokeCap(Paint.Cap.ROUND)
    }
    private val oval = RectF()
    private var running = true
    private val start = System.nanoTime()
    private var alpha = 255

    override fun draw(canvas: Canvas) {
        val b = getBounds()
        val size = ((spec as? CircularProgressIndicatorSpec)?.indicatorSize ?: minOf(b.width(), b.height()))
            .coerceAtMost(minOf(b.width(), b.height()).takeIf { it > 0 } ?: Int.MAX_VALUE)
        val stroke = spec.trackThickness.toFloat()
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val r = (size - stroke) / 2f
        oval.set(cx - r, cy - r, cx + r, cy + r)
        paint.setStrokeWidth(stroke)
        val color = spec.indicatorColors.firstOrNull() ?: 0xFFFFFFFF.toInt()
        paint.setColor((color and 0x00FFFFFF) or (((color ushr 24) * alpha / 255) shl 24))
        val t = (System.nanoTime() - start) / 1_000_000_000.0
        val rotation = ((t * 360.0 / 1.568) % 360.0).toFloat()
        val phase = (t % 1.333) / 1.333
        val sweep = (20f + 250f * (if (phase < 0.5) phase * 2 else 2 - phase * 2)).toFloat()
        canvas.drawArc(oval, rotation - 90f, sweep, false, paint)
        if (running) invalidateSelf()
    }

    override fun setAlpha(alpha: Int) {
        this.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.setColorFilter(colorFilter)
    }

    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = (spec as? CircularProgressIndicatorSpec)?.indicatorSize ?: -1
    override fun getIntrinsicHeight(): Int = (spec as? CircularProgressIndicatorSpec)?.indicatorSize ?: -1

    override fun start() {
        running = true
        invalidateSelf()
    }

    override fun stop() {
        running = false
    }

    override fun isRunning(): Boolean = running

    companion object {
        @JvmStatic
        fun createCircularDrawable(context: Context, spec: CircularProgressIndicatorSpec): IndeterminateDrawable<CircularProgressIndicatorSpec> =
            IndeterminateDrawable(spec)
    }
}
