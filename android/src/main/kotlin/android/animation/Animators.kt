@file:JvmName("AnimatorsKt")

package android.animation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun interface TimeInterpolator {
    fun getInterpolation(input: Float): Float
}

fun interface TypeEvaluator<T> {
    fun evaluate(fraction: Float, startValue: T, endValue: T): T
}

abstract class Animator : Cloneable {
    interface AnimatorListener {
        fun onAnimationStart(animation: Animator)
        fun onAnimationEnd(animation: Animator)
        fun onAnimationCancel(animation: Animator)
        fun onAnimationRepeat(animation: Animator)
    }

    interface AnimatorPauseListener {
        fun onAnimationPause(animation: Animator)
        fun onAnimationResume(animation: Animator)
    }

    protected val mListeners = ArrayList<AnimatorListener>()

    open fun start() {}
    open fun cancel() {}
    open fun end() {}
    open fun pause() {}
    open fun resume() {}
    open fun isPaused(): Boolean = false
    abstract fun getStartDelay(): Long
    abstract fun setStartDelay(startDelay: Long)
    abstract fun setDuration(duration: Long): Animator
    abstract fun getDuration(): Long
    open var interpolator: TimeInterpolator? = null
    abstract fun isRunning(): Boolean
    open fun isStarted(): Boolean = isRunning()
    open fun addListener(listener: AnimatorListener) {
        mListeners.add(listener)
    }

    open fun removeListener(listener: AnimatorListener) {
        mListeners.remove(listener)
    }

    open fun getListeners(): ArrayList<AnimatorListener> = mListeners
    open fun removeAllListeners() = mListeners.clear()
    open fun setTarget(target: Any?) {}
    public override fun clone(): Animator = super.clone() as Animator
}

open class AnimatorListenerAdapter : Animator.AnimatorListener, Animator.AnimatorPauseListener {
    override fun onAnimationStart(animation: Animator) {}
    override fun onAnimationEnd(animation: Animator) {}
    override fun onAnimationCancel(animation: Animator) {}
    override fun onAnimationRepeat(animation: Animator) {}
    override fun onAnimationPause(animation: Animator) {}
    override fun onAnimationResume(animation: Animator) {}
}

open class ValueAnimator : Animator() {
    fun interface AnimatorUpdateListener {
        fun onAnimationUpdate(animation: ValueAnimator)
    }

    companion object {
        const val RESTART = 1
        const val REVERSE = 2
        const val INFINITE = -1

        @JvmStatic
        fun ofFloat(vararg values: Float): ValueAnimator = ValueAnimator().also { it.setFloatValues(*values) }

        @JvmStatic
        fun ofInt(vararg values: Int): ValueAnimator = ValueAnimator().also { it.setIntValues(*values) }

        @JvmStatic
        fun ofArgb(vararg values: Int): ValueAnimator = ValueAnimator().also { it.setIntValues(*values); it.argb = true }
    }

    private var floatValues: FloatArray? = null
    private var intValues: IntArray? = null
    internal var argb = false
    private var mDuration = 300L
    private var mStartDelay = 0L
    private var mInterpolator: TimeInterpolator? = null
    override var interpolator: TimeInterpolator?
        get() = mInterpolator
        set(value) { mInterpolator = value }
    private var repeatCount = 0
    private var repeatMode = RESTART
    private val updateListeners = ArrayList<AnimatorUpdateListener>()
    private var job: Job? = null
    private var fraction = 0f
    private var value: Any? = null

    open fun setFloatValues(vararg values: Float) {
        floatValues = values
        intValues = null
    }

    open fun setIntValues(vararg values: Int) {
        intValues = values
        floatValues = null
    }

    open fun setObjectValues(vararg values: Any?) {}
    open fun setEvaluator(value: TypeEvaluator<*>?) {}
    override fun setDuration(duration: Long): ValueAnimator {
        mDuration = duration
        return this
    }

    override fun getDuration(): Long = mDuration
    override fun getStartDelay(): Long = mStartDelay
    override fun setStartDelay(startDelay: Long) {
        mStartDelay = startDelay
    }
    open fun setRepeatCount(value: Int) {
        repeatCount = value
    }

    open fun getRepeatCount(): Int = repeatCount
    open fun setRepeatMode(value: Int) {
        repeatMode = value
    }

    open fun addUpdateListener(listener: AnimatorUpdateListener) {
        updateListeners.add(listener)
    }

    open fun removeUpdateListener(listener: AnimatorUpdateListener) {
        updateListeners.remove(listener)
    }

    open fun removeAllUpdateListeners() = updateListeners.clear()
    open fun getAnimatedValue(): Any? = value
    open fun getAnimatedFraction(): Float = fraction
    open fun setCurrentPlayTime(playTime: Long) {
        update(if (mDuration <= 0) 1f else (playTime.toFloat() / mDuration).coerceIn(0f, 1f))
    }

    private fun interpolate(f: Float): Any? {
        floatValues?.let { v ->
            if (v.size == 1) return v[0]
            val seg = (f * (v.size - 1)).coerceAtMost(v.size - 1 - 1e-6f)
            val i = seg.toInt()
            return v[i] + (v[i + 1] - v[i]) * (seg - i)
        }
        intValues?.let { v ->
            if (v.size == 1) return v[0]
            val seg = (f * (v.size - 1)).coerceAtMost(v.size - 1 - 1e-6f)
            val i = seg.toInt()
            val t = seg - i
            if (argb) {
                val a = v[i]
                val b = v[i + 1]
                fun ch(s: Int) = (((a shr s) and 0xff) + ((((b shr s) and 0xff) - ((a shr s) and 0xff)) * t)).toInt() and 0xff
                return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
            }
            return (v[i] + (v[i + 1] - v[i]) * t).toInt()
        }
        return null
    }

    private fun update(f: Float) {
        fraction = f
        value = interpolate(interpolator?.getInterpolation(f) ?: f)
        for (l in updateListeners.toList()) l.onAnimationUpdate(this)
        onUpdate()
    }

    protected open fun onUpdate() {}

    override fun start() {
        job?.cancel()
        job = CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            if (mStartDelay > 0) delay(mStartDelay)
            for (l in mListeners.toList()) l.onAnimationStart(this@ValueAnimator)
            var iteration = 0
            while (true) {
                val t0 = System.nanoTime()
                while (true) {
                    val f = if (mDuration <= 0) 1f else ((System.nanoTime() - t0) / 1_000_000f / mDuration).coerceIn(0f, 1f)
                    update(if (repeatMode == REVERSE && iteration % 2 == 1) 1f - f else f)
                    if (f >= 1f) break
                    delay(16)
                }
                iteration++
                if (repeatCount != INFINITE && iteration > repeatCount) break
                for (l in mListeners.toList()) l.onAnimationRepeat(this@ValueAnimator)
            }
            job = null
            for (l in mListeners.toList()) l.onAnimationEnd(this@ValueAnimator)
        }
    }

    override fun cancel() {
        if (job != null) {
            job?.cancel()
            job = null
            for (l in mListeners.toList()) l.onAnimationCancel(this)
            for (l in mListeners.toList()) l.onAnimationEnd(this)
        }
    }

    override fun end() {
        job?.cancel()
        job = null
        update(1f)
        for (l in mListeners.toList()) l.onAnimationEnd(this)
    }

    override fun isRunning(): Boolean = job != null
    override fun clone(): ValueAnimator = super.clone() as ValueAnimator
}

open class ObjectAnimator : ValueAnimator() {
    private var autoCancel = false

    /** Cancels other running ObjectAnimators on the same target and property when started */
    open fun setAutoCancel(cancel: Boolean) {
        autoCancel = cancel
    }

    private var target: Any? = null
    private var propertyName: String? = null
    private var property: android.util.Property<*, *>? = null

    companion object {
        @JvmStatic
        fun ofFloat(target: Any?, propertyName: String, vararg values: Float): ObjectAnimator =
            ObjectAnimator().also { it.target = target; it.propertyName = propertyName; it.setFloatValues(*values) }

        @JvmStatic
        fun <T> ofFloat(target: T, property: android.util.Property<T, Float>, vararg values: Float): ObjectAnimator =
            ObjectAnimator().also { it.target = target; it.property = property; it.propertyName = property.name; it.setFloatValues(*values) }

        @JvmStatic
        fun ofInt(target: Any?, propertyName: String, vararg values: Int): ObjectAnimator =
            ObjectAnimator().also { it.target = target; it.propertyName = propertyName; it.setIntValues(*values) }

        @JvmStatic
        fun <T> ofInt(target: T, property: android.util.Property<T, Int>, vararg values: Int): ObjectAnimator =
            ObjectAnimator().also { it.target = target; it.property = property; it.propertyName = property.name; it.setIntValues(*values) }

        @JvmStatic
        fun ofArgb(target: Any?, propertyName: String, vararg values: Int): ObjectAnimator =
            ObjectAnimator().also { it.target = target; it.propertyName = propertyName; it.setIntValues(*values); it.argb = true }
    }

    override fun setTarget(target: Any?) {
        this.target = target
    }

    open fun setPropertyName(propertyName: String) {
        this.propertyName = propertyName
    }

    override fun onUpdate() {
        val t = target ?: return
        val v = getAnimatedValue() ?: return
        val p = property
        if (p != null) {
            try {
                @Suppress("UNCHECKED_CAST")
                (p as android.util.Property<Any, Any?>).set(t, v)
            } catch (_: Throwable) {
            }
            return
        }
        val name = propertyName ?: return
        val setter = "set" + name.replaceFirstChar { it.uppercaseChar() }
        try {
            val m = t.javaClass.methods.firstOrNull { it.name == setter && it.parameterCount == 1 } ?: return
            m.invoke(t, v)
        } catch (_: Throwable) {
        }
    }
}

open class AnimatorSet : Animator() {
    private val items = ArrayList<Animator>()
    private var duration = -1L
    private var startDelay = 0L

    inner class Builder(private val anim: Animator) {
        fun with(anim: Animator): Builder = this.also { items.add(anim) }
        fun before(anim: Animator): Builder = this.also { items.add(anim) }
        fun after(anim: Animator): Builder = this.also { items.add(anim) }
    }

    open fun playTogether(vararg items: Animator) {
        this.items.addAll(items)
    }

    open fun playTogether(items: Collection<Animator>) {
        this.items.addAll(items)
    }

    open fun playSequentially(vararg items: Animator) {
        this.items.addAll(items)
    }

    open fun play(anim: Animator): Builder {
        items.add(anim)
        return Builder(anim)
    }

    override fun start() {
        for (l in mListeners.toList()) l.onAnimationStart(this)
        for (a in items) {
            if (duration >= 0) a.setDuration(duration)
            a.start()
        }
        for (l in mListeners.toList()) l.onAnimationEnd(this)
    }

    override fun cancel() = items.forEach { it.cancel() }
    override fun getStartDelay(): Long = startDelay
    override fun setStartDelay(startDelay: Long) {
        this.startDelay = startDelay
    }

    override fun setDuration(duration: Long): AnimatorSet {
        this.duration = duration
        return this
    }

    override fun getDuration(): Long = duration
    override var interpolator: TimeInterpolator?
        get() = null
        set(value) { items.forEach { it?.interpolator = value } }
    override fun isRunning(): Boolean = items.any { it.isRunning() }
}
