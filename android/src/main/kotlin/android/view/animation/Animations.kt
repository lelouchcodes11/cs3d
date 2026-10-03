@file:JvmName("AnimationsKt")

package android.view.animation

import android.animation.TimeInterpolator
import android.view.View
import kotlin.math.cos
import kotlin.math.pow

interface Interpolator : TimeInterpolator

open class LinearInterpolator : Interpolator {
    override fun getInterpolation(input: Float): Float = input
}

open class AccelerateInterpolator(private val factor: Float = 1f) : Interpolator {

    override fun getInterpolation(input: Float): Float = if (factor == 1f) input * input else input.toDouble().pow(2.0 * factor).toFloat()
}

open class DecelerateInterpolator(private val factor: Float = 1f) : Interpolator {

    override fun getInterpolation(input: Float): Float =
        if (factor == 1f) 1f - (1f - input) * (1f - input) else (1.0 - (1.0 - input).pow(2.0 * factor)).toFloat()
}

open class AccelerateDecelerateInterpolator : Interpolator {
    override fun getInterpolation(input: Float): Float = (cos((input + 1) * Math.PI) / 2.0f).toFloat() + 0.5f
}

open class OvershootInterpolator(private val tension: Float = 2f) : Interpolator {

    override fun getInterpolation(input: Float): Float {
        val t = input - 1.0f
        return t * t * ((tension + 1) * t + tension) + 1.0f
    }
}

open class AnticipateInterpolator(private val tension: Float = 2f) : Interpolator {

    override fun getInterpolation(input: Float): Float = input * input * ((tension + 1) * input - tension)
}

open class BounceInterpolator : Interpolator {
    private fun bounce(t: Float): Float = t * t * 8.0f
    override fun getInterpolation(input: Float): Float {
        val t = input * 1.1226f
        return when {
            t < 0.3535f -> bounce(t)
            t < 0.7408f -> bounce(t - 0.54719f) + 0.7f
            t < 0.9644f -> bounce(t - 0.8526f) + 0.9f
            else -> bounce(t - 1.0435f) + 0.95f
        }
    }
}

open class Transformation {
    protected var mMatrix: android.graphics.Matrix = android.graphics.Matrix()
    protected var mAlpha: Float = 1.0f
    protected var mTransformationType: Int = TYPE_BOTH

    open fun clear() {
        mAlpha = 1.0f
        mMatrix.reset()
        mTransformationType = TYPE_BOTH
    }

    open fun getAlpha(): Float = mAlpha
    open fun setAlpha(alpha: Float) { mAlpha = alpha }
    open fun getMatrix(): android.graphics.Matrix = mMatrix
    open fun getTransformationType(): Int = mTransformationType
    open fun setTransformationType(type: Int) { mTransformationType = type }

    companion object {
        const val TYPE_IDENTITY = 0x0
        const val TYPE_ALPHA = 0x1
        const val TYPE_MATRIX = 0x2
        const val TYPE_BOTH = 0x3
    }
}

open class Animation {
    interface AnimationListener {
        fun onAnimationStart(animation: Animation?)
        fun onAnimationEnd(animation: Animation?)
        fun onAnimationRepeat(animation: Animation?)
    }

    companion object {
        const val INFINITE = -1
        const val RESTART = 1
        const val REVERSE = 2
        const val ABSOLUTE = 0
        const val RELATIVE_TO_SELF = 1
        const val RELATIVE_TO_PARENT = 2
    }

    open var duration: Long = 0L
    val durationMillis: Long get() = duration
    private var listener: AnimationListener? = null
    open var interpolator: Interpolator? = null
    open var fillAfter: Boolean = false
    open var startOffset: Long = 0L

    open fun setRepeatCount(repeatCount: Int) {}
    open fun setRepeatMode(repeatMode: Int) {}
    open fun setAnimationListener(listener: AnimationListener?) {
        this.listener = listener
    }

    open fun start() {}
    open fun cancel() {}
    open fun reset() {}
    open fun hasEnded(): Boolean = true

    protected open fun applyTransformation(interpolatedTime: Float, t: Transformation?) {}

    /** Desktop: apply the animation end state immediately */
    open fun applyTo(view: View) {
        listener?.onAnimationStart(this)
        applyTransformation(1.0f, null)
        applyEnd(view)
        listener?.onAnimationEnd(this)
    }

    protected open fun applyEnd(view: View) {}
}

open class AlphaAnimation(private val fromAlpha: Float, private val toAlpha: Float) : Animation() {
    override fun applyEnd(view: View) {
        view.animate().alpha(toAlpha).setDuration(duration).start()
    }
}

open class TranslateAnimation(fromX: Float, private val toX: Float, fromY: Float, private val toY: Float) : Animation() {
    override fun applyEnd(view: View) {
        view.animate().translationX(toX).translationY(toY).setDuration(duration).start()
    }
}

open class ScaleAnimation(fromX: Float, private val toX: Float, fromY: Float, private val toY: Float) : Animation() {
    constructor(fromX: Float, toX: Float, fromY: Float, toY: Float, pivotX: Float, pivotY: Float) : this(fromX, toX, fromY, toY)
    constructor(fromX: Float, toX: Float, fromY: Float, toY: Float, pivotXType: Int, pivotXValue: Float, pivotYType: Int, pivotYValue: Float) : this(fromX, toX, fromY, toY)

    override fun applyEnd(view: View) {
        view.animate().scaleX(toX).scaleY(toY).setDuration(duration).start()
    }
}

open class RotateAnimation(fromDegrees: Float, private val toDegrees: Float) : Animation() {
    constructor(fromDegrees: Float, toDegrees: Float, pivotXType: Int, pivotXValue: Float, pivotYType: Int, pivotYValue: Float) : this(fromDegrees, toDegrees)

    override fun applyEnd(view: View) {
        view.animate().rotation(toDegrees).setDuration(duration).start()
    }
}

open class AnimationSet(shareInterpolator: Boolean) : Animation() {
    private val animations = ArrayList<Animation>()
    open fun addAnimation(a: Animation) {
        animations.add(a)
    }

    override fun applyTo(view: View) {
        for (a in animations) a.applyTo(view)
    }
}

object AnimationUtils {
    @JvmStatic
    fun loadAnimation(context: android.content.Context?, id: Int): Animation = AlphaAnimation(1f, 1f)

    @JvmStatic
    fun currentAnimationTimeMillis(): Long = android.os.SystemClock.uptimeMillis()
}
