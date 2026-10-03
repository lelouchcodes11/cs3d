package android.graphics.drawable

import android.content.res.ColorStateList
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect

/**
 * AnimatedVectorDrawable: a VectorDrawable whose named groups and paths are driven by object
 * animators (pathData morphs, alpha, rotation, scale, translation). Frames are computed at draw time
 * and the drawable invalidates itself while running.
 */
open class AnimatedVectorDrawable : Drawable, Animatable {
    /** One animated property of a named target, with its timing inside the whole animation */
    internal class PropertyAnimation(
        val target: String,
        val property: String,
        val from: String?,
        val to: String?,
        val duration: Long,
        val startOffset: Long,
        val repeatCount: Int,
        val reverse: Boolean,
        val interpolator: (Float) -> Float,
    )

    private val vector: VectorDrawable?
    private val animations: List<PropertyAnimation>
    private var running = false
    private var startTime = 0L

    constructor() : super() {
        vector = null
        animations = emptyList()
    }

    internal constructor(vector: VectorDrawable?, animations: List<PropertyAnimation>) : super() {
        this.vector = vector
        this.animations = animations
    }

    private val totalDuration: Long
        get() = animations.maxOfOrNull { if (it.repeatCount < 0) Long.MAX_VALUE else it.startOffset + it.duration * (it.repeatCount + 1) } ?: 0L

    override fun start() {
        if (animations.isEmpty()) return
        running = true
        startTime = System.nanoTime() / 1_000_000
        invalidateSelf()
    }

    override fun stop() {
        running = false
    }

    override fun isRunning(): Boolean = running

    open fun reset() {
        running = false
        apply(0L)
        invalidateSelf()
    }

    private fun apply(elapsed: Long) {
        val v = vector ?: return
        for (a in animations) {
            if (elapsed < a.startOffset) continue
            val local = elapsed - a.startOffset
            val duration = a.duration.coerceAtLeast(1)
            val iteration = local / duration
            var fraction = if (a.repeatCount >= 0 && iteration > a.repeatCount) 1f else (local % duration).toFloat() / duration
            if (a.repeatCount >= 0 && iteration > a.repeatCount && a.reverse && a.repeatCount % 2 == 1) fraction = 0f
            else if (a.reverse && iteration % 2 == 1L && !(a.repeatCount >= 0 && iteration > a.repeatCount)) fraction = 1f - fraction
            val f = a.interpolator(fraction)
            applyProperty(v, a, f)
        }
    }

    private fun applyProperty(v: VectorDrawable, a: PropertyAnimation, f: Float) {
        fun num(s: String?) = s?.toFloatOrNull()
        fun lerp(): Float? {
            val from = num(a.from)
            val to = num(a.to) ?: return null
            return (from ?: to) + (to - (from ?: to)) * f
        }
        when (a.property) {
            "pathData" -> {
                val path = v.findPath(a.target) ?: return
                val to = a.to ?: return
                val data = if (a.from != null) PathParser.interpolatePathData(a.from, to, f) ?: (if (f < 1f) a.from else to) else to
                if (data != path.pathData) {
                    path.pathData = data
                    path.path = PathParser.createPathFromPathData(data)
                }
            }
            "fillAlpha" -> v.findPath(a.target)?.let { p -> lerp()?.let { p.fillAlpha = it } }
            "strokeAlpha" -> v.findPath(a.target)?.let { p -> lerp()?.let { p.strokeAlpha = it } }
            "alpha" -> lerp()?.let { v.rootAlpha = it }
            "rotation", "scaleX", "scaleY", "translateX", "translateY", "pivotX", "pivotY" -> {
                val g = v.findGroup(a.target) ?: return
                val value = lerp() ?: return
                when (a.property) {
                    "rotation" -> g.rotation = value
                    "scaleX" -> g.scaleX = value
                    "scaleY" -> g.scaleY = value
                    "translateX" -> g.translateX = value
                    "translateY" -> g.translateY = value
                    "pivotX" -> g.pivotX = value
                    "pivotY" -> g.pivotY = value
                }
                g.rebuild()
            }
        }
    }

    override fun draw(canvas: Canvas) {
        val v = vector ?: return
        if (running) {
            val elapsed = System.nanoTime() / 1_000_000 - startTime
            apply(elapsed)
            if (elapsed >= totalDuration) running = false
        }
        v.setBounds(getBounds())
        v.setState(getState())
        v.draw(canvas)
        if (running) invalidateSelf()
    }

    override fun getIntrinsicWidth(): Int = vector?.getIntrinsicWidth() ?: -1
    override fun getIntrinsicHeight(): Int = vector?.getIntrinsicHeight() ?: -1
    override fun setAlpha(alpha: Int) {
        vector?.setAlpha(alpha)
    }

    override fun getAlpha(): Int = vector?.getAlpha() ?: 255
    override fun setColorFilter(colorFilter: ColorFilter?) {
        vector?.setColorFilter(colorFilter)
    }

    override fun setTintList(tint: ColorStateList?) {
        vector?.setTintList(tint)
    }

    override fun setTintBlendMode(blendMode: BlendMode?) {
        vector?.setTintBlendMode(blendMode)
    }

    override fun isStateful(): Boolean = true
    override fun onStateChange(state: IntArray): Boolean {
        invalidateSelf()
        return true
    }

    override fun onBoundsChange(bounds: Rect) {
        vector?.setBounds(bounds)
    }

    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    internal companion object {
        /** Interpolators by resource name (framework and androidx ones used in drawables) */
        fun interpolator(ref: String?): (Float) -> Float {
            val name = ref?.substringAfterLast('/') ?: return accelerateDecelerate
            return when (name) {
                "linear" -> { t -> t }
                "accelerate_quad", "accelerate_cubic" -> { t -> t * t }
                "decelerate_quad", "decelerate_cubic" -> { t -> 1 - (1 - t) * (1 - t) }
                "fast_out_slow_in" -> cubicBezier(0.4f, 0f, 0.2f, 1f)
                "fast_out_linear_in" -> cubicBezier(0.4f, 0f, 1f, 1f)
                "linear_out_slow_in" -> cubicBezier(0f, 0f, 0.2f, 1f)
                else -> accelerateDecelerate
            }
        }

        private val accelerateDecelerate: (Float) -> Float = { t -> (Math.cos((t + 1) * Math.PI) / 2.0).toFloat() + 0.5f }

        private fun cubicBezier(x1: Float, y1: Float, x2: Float, y2: Float): (Float) -> Float = { x ->
            // solve the curve's x(t) = x by bisection, then evaluate y(t)
            var lo = 0f
            var hi = 1f
            var t = x
            repeat(20) {
                val cx = 3 * (1 - t) * (1 - t) * t * x1 + 3 * (1 - t) * t * t * x2 + t * t * t
                if (cx < x) lo = t else hi = t
                t = (lo + hi) / 2
            }
            3 * (1 - t) * (1 - t) * t * y1 + 3 * (1 - t) * t * t * y2 + t * t * t
        }
    }
}
