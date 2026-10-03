@file:JvmName("ViewSupportKt")

package android.view

import android.animation.TimeInterpolator
import android.graphics.Insets
import android.graphics.Rect
import android.os.Parcel
import android.os.Parcelable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

abstract class AbsSavedState : Parcelable {
    private val mSuperState: Parcelable?

    protected constructor(superState: Parcelable?) {
        mSuperState = superState
    }

    protected constructor(source: Parcel?) {
        mSuperState = null
    }

    protected constructor(source: Parcel?, loader: ClassLoader?) {
        mSuperState = null
    }

    fun getSuperState(): Parcelable? = mSuperState
    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel, flags: Int) {}

    companion object {
        @JvmField
        val EMPTY_STATE: AbsSavedState = object : AbsSavedState(null as Parcelable?) {}
    }
}

class Outline {
    @JvmField
    var mRect: Rect? = null

    @JvmField
    var mRadius = 0f
    private var alpha = 1f

    fun setRect(left: Int, top: Int, right: Int, bottom: Int) {
        mRect = Rect(left, top, right, bottom)
    }

    fun setRect(rect: Rect) {
        mRect = Rect(rect)
    }

    fun setRoundRect(left: Int, top: Int, right: Int, bottom: Int, radius: Float) {
        mRect = Rect(left, top, right, bottom)
        mRadius = radius
    }

    fun setRoundRect(rect: Rect, radius: Float) = setRoundRect(rect.left, rect.top, rect.right, rect.bottom, radius)
    fun setOval(left: Int, top: Int, right: Int, bottom: Int) {
        mRect = Rect(left, top, right, bottom)
        mRadius = (kotlin.math.min(right - left, bottom - top) / 2).toFloat()
    }

    fun setEmpty() {
        mRect = null
    }

    fun setAlpha(alpha: Float) {
        this.alpha = alpha
    }

    fun getAlpha(): Float = alpha
    fun getRadius(): Float = mRadius
}

abstract class ViewOutlineProvider {
    abstract fun getOutline(view: View, outline: Outline)

    companion object {
        @JvmField
        val BACKGROUND: ViewOutlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRect(0, 0, view.getWidth(), view.getHeight())
            }
        }

        @JvmField
        val BOUNDS: ViewOutlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRect(0, 0, view.getWidth(), view.getHeight())
            }
        }

        @JvmField
        val PADDED_BOUNDS: ViewOutlineProvider = BOUNDS
    }
}

class ViewTreeObserver {
    fun interface OnGlobalLayoutListener {
        fun onGlobalLayout()
    }

    fun interface OnPreDrawListener {
        fun onPreDraw(): Boolean
    }

    fun interface OnGlobalFocusChangeListener {
        fun onGlobalFocusChanged(oldFocus: View?, newFocus: View?)
    }

    fun interface OnScrollChangedListener {
        fun onScrollChanged()
    }

    fun interface OnDrawListener {
        fun onDraw()
    }

    fun interface OnWindowFocusChangeListener {
        fun onWindowFocusChanged(hasFocus: Boolean)
    }

    private val globalLayout = ArrayList<OnGlobalLayoutListener>()
    private val preDraw = ArrayList<OnPreDrawListener>()

    fun addOnGlobalLayoutListener(listener: OnGlobalLayoutListener) {
        globalLayout.add(listener)
    }

    fun removeOnGlobalLayoutListener(victim: OnGlobalLayoutListener) {
        globalLayout.remove(victim)
    }

    @Deprecated("")
    fun removeGlobalOnLayoutListener(victim: OnGlobalLayoutListener) = removeOnGlobalLayoutListener(victim)

    fun addOnPreDrawListener(listener: OnPreDrawListener) {
        preDraw.add(listener)
    }

    fun removeOnPreDrawListener(victim: OnPreDrawListener) {
        preDraw.remove(victim)
    }

    fun addOnGlobalFocusChangeListener(listener: OnGlobalFocusChangeListener) {}
    fun removeOnGlobalFocusChangeListener(victim: OnGlobalFocusChangeListener) {}
    fun addOnScrollChangedListener(listener: OnScrollChangedListener) {}
    fun removeOnScrollChangedListener(victim: OnScrollChangedListener) {}
    fun addOnDrawListener(listener: OnDrawListener) {}
    fun removeOnDrawListener(victim: OnDrawListener) {}
    fun addOnWindowFocusChangeListener(listener: OnWindowFocusChangeListener) {}
    fun removeOnWindowFocusChangeListener(victim: OnWindowFocusChangeListener) {}
    fun isAlive(): Boolean = true

    /** Takes over the listeners of a floating observer (view attached to a window) */
    fun merge(observer: ViewTreeObserver) {
        globalLayout.addAll(observer.globalLayout)
        preDraw.addAll(observer.preDraw)
    }

    fun dispatchOnGlobalLayout() {
        for (l in globalLayout.toList()) l.onGlobalLayout()
    }

    fun dispatchOnPreDraw(): Boolean {
        var cancel = false
        for (l in preDraw.toList()) if (!l.onPreDraw()) cancel = true
        return !cancel
    }
}

/**
 * Animates view properties over time on the main thread; the Compose renderer picks up the
 * intermediate values through the view's snapshot state.
 */
class ViewPropertyAnimator internal constructor(private val view: View) {
    private val targets = LinkedHashMap<String, Pair<Float?, Float>>()
    private var duration = 300L
    private var startDelay = 0L
    private var interpolator: TimeInterpolator? = null
    private var endAction: Runnable? = null
    private var startAction: Runnable? = null
    private var listener: android.animation.Animator.AnimatorListener? = null
    private var updateListener: android.animation.ValueAnimator.AnimatorUpdateListener? = null
    private var job: Job? = null

    private fun target(name: String, value: Float, relative: Boolean = false): ViewPropertyAnimator {
        targets[name] = (if (relative) current(name) else null) to value
        scheduleStart()
        return this
    }

    private fun current(name: String): Float = when (name) {
        "alpha" -> view.getAlpha()
        "translationX" -> view.getTranslationX()
        "translationY" -> view.getTranslationY()
        "translationZ" -> view.getTranslationZ()
        "scaleX" -> view.getScaleX()
        "scaleY" -> view.getScaleY()
        "rotation" -> view.getRotation()
        "x" -> view.getX()
        "y" -> view.getY()
        else -> 0f
    }

    private fun apply(name: String, v: Float) {
        when (name) {
            "alpha" -> view.setAlpha(v)
            "translationX" -> view.setTranslationX(v)
            "translationY" -> view.setTranslationY(v)
            "translationZ" -> view.setTranslationZ(v)
            "scaleX" -> view.setScaleX(v)
            "scaleY" -> view.setScaleY(v)
            "rotation" -> view.setRotation(v)
            "x" -> view.setX(v)
            "y" -> view.setY(v)
        }
    }

    private var scheduled = false
    private fun scheduleStart() {
        if (scheduled) return
        scheduled = true
        view.post { if (scheduled) start() }
    }

    fun setDuration(duration: Long): ViewPropertyAnimator {
        this.duration = duration
        return this
    }

    fun getDuration(): Long = duration
    fun setStartDelay(startDelay: Long): ViewPropertyAnimator {
        this.startDelay = startDelay
        return this
    }

    fun getStartDelay(): Long = startDelay
    fun setInterpolator(interpolator: TimeInterpolator?): ViewPropertyAnimator {
        this.interpolator = interpolator
        return this
    }

    fun getInterpolator(): TimeInterpolator? = interpolator
    fun setListener(listener: android.animation.Animator.AnimatorListener?): ViewPropertyAnimator {
        this.listener = listener
        return this
    }

    fun setUpdateListener(listener: android.animation.ValueAnimator.AnimatorUpdateListener?): ViewPropertyAnimator {
        updateListener = listener
        return this
    }

    fun withEndAction(runnable: Runnable?): ViewPropertyAnimator {
        endAction = runnable
        return this
    }

    fun withStartAction(runnable: Runnable?): ViewPropertyAnimator {
        startAction = runnable
        return this
    }

    fun withLayer(): ViewPropertyAnimator = this
    fun alpha(value: Float) = target("alpha", value)
    fun alphaBy(value: Float) = target("alpha", view.getAlpha() + value)
    fun translationX(value: Float) = target("translationX", value)
    fun translationXBy(value: Float) = target("translationX", view.getTranslationX() + value)
    fun translationY(value: Float) = target("translationY", value)
    fun translationYBy(value: Float) = target("translationY", view.getTranslationY() + value)
    fun translationZ(value: Float) = target("translationZ", value)
    fun scaleX(value: Float) = target("scaleX", value)
    fun scaleXBy(value: Float) = target("scaleX", view.getScaleX() + value)
    fun scaleY(value: Float) = target("scaleY", value)
    fun scaleYBy(value: Float) = target("scaleY", view.getScaleY() + value)
    fun rotation(value: Float) = target("rotation", value)
    fun rotationBy(value: Float) = target("rotation", view.getRotation() + value)
    fun rotationX(value: Float) = this
    fun rotationY(value: Float) = this
    fun x(value: Float) = target("x", value)
    fun y(value: Float) = target("y", value)
    fun z(value: Float) = this

    fun cancel() {
        scheduled = false
        job?.cancel()
        job = null
        targets.clear()
    }

    fun start() {
        scheduled = false
        if (targets.isEmpty()) {
            endAction?.let { view.post(it) }
            endAction = null
            return
        }
        val anims = targets.map { (k, v) -> Triple(k, v.first ?: current(k), v.second) }
        targets.clear()
        val end = endAction
        endAction = null
        val start = startAction
        startAction = null
        val interp = interpolator
        val dur = duration
        val delayMs = startDelay
        job?.cancel()
        job = CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            if (delayMs > 0) delay(delayMs)
            start?.run()
            val t0 = System.nanoTime()
            while (true) {
                val elapsed = (System.nanoTime() - t0) / 1_000_000f
                val fraction = if (dur <= 0) 1f else (elapsed / dur).coerceIn(0f, 1f)
                val eased = interp?.getInterpolation(fraction) ?: android.view.animation.AccelerateDecelerateInterpolator().getInterpolation(fraction)
                for ((name, from, to) in anims) apply(name, from + (to - from) * eased)
                if (fraction >= 1f) break
                delay(16)
            }
            end?.run()
        }
    }
}

class Display {
    companion object {
        const val DEFAULT_DISPLAY = 0
        const val STATE_ON = 2
    }

    class Mode(private val w: Int, private val h: Int, private val rate: Float) {
        fun getPhysicalWidth(): Int = w
        fun getPhysicalHeight(): Int = h
        fun getRefreshRate(): Float = rate
        fun getModeId(): Int = 0
    }

    fun getDisplayId(): Int = DEFAULT_DISPLAY
    fun getName(): String = "Desktop"
    fun getWidth(): Int = com.lagradost.desktop.runtime.AndroidRuntime.displayMetrics.widthPixels
    fun getHeight(): Int = com.lagradost.desktop.runtime.AndroidRuntime.displayMetrics.heightPixels
    fun getRotation(): Int = 0
    fun getRefreshRate(): Float = 60f
    fun getState(): Int = STATE_ON
    fun getMode(): Mode = Mode(getWidth(), getHeight(), 60f)
    fun getSupportedModes(): Array<Mode> = arrayOf(getMode())
    fun getMetrics(outMetrics: android.util.DisplayMetrics) = outMetrics.setTo(com.lagradost.desktop.runtime.AndroidRuntime.displayMetrics)
    fun getRealMetrics(outMetrics: android.util.DisplayMetrics) = getMetrics(outMetrics)
    fun getSize(outSize: android.graphics.Point) = outSize.set(getWidth(), getHeight())
    fun getRealSize(outSize: android.graphics.Point) = getSize(outSize)
}

class WindowInsets private constructor(private val insets: Insets) {
    constructor() : this(Insets.NONE)
    object Type {
        @JvmStatic fun statusBars(): Int = 1
        @JvmStatic fun navigationBars(): Int = 1 shl 1
        @JvmStatic fun captionBar(): Int = 1 shl 2
        @JvmStatic fun ime(): Int = 1 shl 3
        @JvmStatic fun systemGestures(): Int = 1 shl 4
        @JvmStatic fun mandatorySystemGestures(): Int = 1 shl 5
        @JvmStatic fun tappableElement(): Int = 1 shl 6
        @JvmStatic fun displayCutout(): Int = 1 shl 7
        @JvmStatic fun systemBars(): Int = statusBars() or navigationBars() or captionBar()
    }

    companion object {
        @JvmField
        val CONSUMED = WindowInsets(Insets.NONE)
    }

    fun getInsets(typeMask: Int): Insets = insets
    fun getInsetsIgnoringVisibility(typeMask: Int): Insets = insets
    fun isVisible(typeMask: Int): Boolean = false
    fun getSystemWindowInsetLeft(): Int = insets.left
    fun getSystemWindowInsetTop(): Int = insets.top
    fun getSystemWindowInsetRight(): Int = insets.right
    fun getSystemWindowInsetBottom(): Int = insets.bottom
    fun getStableInsetTop(): Int = 0
    fun getStableInsetBottom(): Int = 0
    fun hasSystemWindowInsets(): Boolean = false
    fun isConsumed(): Boolean = true
    fun consumeSystemWindowInsets(): WindowInsets = this
    fun consumeStableInsets(): WindowInsets = this
    fun replaceSystemWindowInsets(left: Int, top: Int, right: Int, bottom: Int): WindowInsets = WindowInsets(Insets.of(left, top, right, bottom))
    fun getDisplayCutout(): Any? = null
}

class SurfaceHolder
