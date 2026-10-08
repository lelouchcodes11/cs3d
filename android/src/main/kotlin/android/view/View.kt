package android.view

import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.util.AttributeSet
import android.util.SparseArray
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lagradost.desktop.runtime.ui.ViewAttributes
import java.util.concurrent.atomic.AtomicInteger

/**
 * Desktop android.view.View. UI relevant properties are kept in Compose snapshot state so the
 * Compose renderer (com.lagradost.desktop.runtime.ui) recomposes whenever extension code mutates
 * a view, exactly like invalidate()/requestLayout() would on Android.
 */
open class View : Drawable.Callback {
    fun interface OnClickListener {
        fun onClick(v: View)
    }

    fun interface OnLongClickListener {
        fun onLongClick(v: View): Boolean
    }

    fun interface OnFocusChangeListener {
        fun onFocusChange(v: View, hasFocus: Boolean)
    }

    fun interface OnKeyListener {
        fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean
    }

    fun interface OnTouchListener {
        fun onTouch(v: View, event: MotionEvent): Boolean
    }

    fun interface OnDragListener {
        fun onDrag(v: View, event: DragEvent): Boolean
    }

    /** What is drawn under the pointer while dragging (the desktop drag shows no shadow; the list updates on drop) */
    open class DragShadowBuilder(private val view: View?) {
        constructor() : this(null)

        fun getView(): View? = view
        open fun onProvideShadowMetrics(outShadowSize: android.graphics.Point, outShadowTouchPoint: android.graphics.Point) {
            outShadowSize.set(view?.getWidth() ?: 1, view?.getHeight() ?: 1)
            outShadowTouchPoint.set(outShadowSize.x / 2, outShadowSize.y / 2)
        }
        open fun onDrawShadow(canvas: android.graphics.Canvas) {}
    }

    fun interface OnApplyWindowInsetsListener {
        fun onApplyWindowInsets(v: View, insets: WindowInsets): WindowInsets
    }

    fun interface OnLayoutChangeListener {
        fun onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int)
    }

    fun interface OnScrollChangeListener {
        fun onScrollChange(v: View, scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int)
    }

    fun interface OnGenericMotionListener {
        fun onGenericMotion(v: View, event: MotionEvent): Boolean
    }

    interface OnAttachStateChangeListener {
        fun onViewAttachedToWindow(v: View)
        fun onViewDetachedFromWindow(v: View)
    }

    fun interface OnSystemUiVisibilityChangeListener {
        fun onSystemUiVisibilityChange(visibility: Int)
    }

    open class BaseSavedState(superState: Parcelable?) : android.view.AbsSavedState(superState)

    object MeasureSpec {
        const val MODE_SHIFT = 30
        const val MODE_MASK = 0x3 shl MODE_SHIFT
        const val UNSPECIFIED = 0
        const val EXACTLY = 1 shl MODE_SHIFT
        const val AT_MOST = 2 shl MODE_SHIFT

        @JvmStatic
        fun makeMeasureSpec(size: Int, mode: Int): Int = (size and MODE_MASK.inv()) or (mode and MODE_MASK)

        @JvmStatic
        fun getMode(measureSpec: Int): Int = measureSpec and MODE_MASK

        @JvmStatic
        fun getSize(measureSpec: Int): Int = measureSpec and MODE_MASK.inv()

        @JvmStatic
        fun toString(measureSpec: Int): String = "MeasureSpec: ${getMode(measureSpec)} ${getSize(measureSpec)}"
    }

    companion object {
        const val NO_ID = -1
        const val VISIBLE = 0x00000000
        const val INVISIBLE = 0x00000004
        const val GONE = 0x00000008
        const val FOCUSABLE_AUTO = 0x00000010
        const val NOT_FOCUSABLE = 0x00000000
        const val FOCUSABLE = 0x00000001
        const val FOCUS_BACKWARD = 0x00000001
        const val FOCUS_FORWARD = 0x00000002
        const val FOCUS_LEFT = 0x00000011
        const val FOCUS_UP = 0x00000021
        const val FOCUS_RIGHT = 0x00000042
        const val FOCUS_DOWN = 0x00000082
        const val LAYOUT_DIRECTION_LTR = 0
        const val LAYOUT_DIRECTION_RTL = 1
        const val LAYOUT_DIRECTION_INHERIT = 2
        const val LAYOUT_DIRECTION_LOCALE = 3
        const val TEXT_ALIGNMENT_INHERIT = 0
        const val TEXT_ALIGNMENT_GRAVITY = 1
        const val TEXT_ALIGNMENT_TEXT_START = 2
        const val TEXT_ALIGNMENT_TEXT_END = 3
        const val TEXT_ALIGNMENT_CENTER = 4
        const val TEXT_ALIGNMENT_VIEW_START = 5
        const val TEXT_ALIGNMENT_VIEW_END = 6
        const val IMPORTANT_FOR_ACCESSIBILITY_AUTO = 0
        const val IMPORTANT_FOR_ACCESSIBILITY_YES = 1
        const val IMPORTANT_FOR_ACCESSIBILITY_NO = 2
        const val IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS = 4
        const val SYSTEM_UI_FLAG_VISIBLE = 0
        const val SYSTEM_UI_FLAG_LOW_PROFILE = 0x00000001
        const val SYSTEM_UI_FLAG_HIDE_NAVIGATION = 0x00000002
        const val SYSTEM_UI_FLAG_FULLSCREEN = 0x00000004
        const val SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR = 0x00000010
        const val SYSTEM_UI_FLAG_LAYOUT_STABLE = 0x00000100
        const val SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION = 0x00000200
        const val SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN = 0x00000400
        const val SYSTEM_UI_FLAG_IMMERSIVE = 0x00000800
        const val SYSTEM_UI_FLAG_IMMERSIVE_STICKY = 0x00001000
        const val SYSTEM_UI_FLAG_LIGHT_STATUS_BAR = 0x00002000
        const val OVER_SCROLL_ALWAYS = 0
        const val OVER_SCROLL_IF_CONTENT_SCROLLS = 1
        const val OVER_SCROLL_NEVER = 2
        const val SCROLLBARS_INSIDE_OVERLAY = 0
        const val SCROLLBARS_OUTSIDE_OVERLAY = 0x02000000
        const val LAYER_TYPE_NONE = 0
        const val LAYER_TYPE_SOFTWARE = 1
        const val LAYER_TYPE_HARDWARE = 2
        const val DRAWING_CACHE_QUALITY_AUTO = 0
        const val HAPTIC_FEEDBACK_ENABLED = 0x10000000
        const val SOUND_EFFECTS_ENABLED = 0x08000000
        const val AUTOFILL_TYPE_NONE = 0
        const val MEASURED_STATE_MASK = 0xff000000.toInt()
        const val MEASURED_SIZE_MASK = 0x00ffffff
        const val MEASURED_STATE_TOO_SMALL = 0x01000000
        const val MEASURED_HEIGHT_STATE_SHIFT = 16

        @JvmStatic
        fun combineMeasuredStates(curState: Int, newState: Int): Int = curState or newState

        // android.R.attr.state_* values used in drawable state sets
        const val STATE_PRESSED = 16842919
        const val STATE_FOCUSED = 16842908

        @JvmField
        val ALPHA: android.util.Property<View, Float> = object : android.util.Property<View, Float>(Float::class.javaObjectType, "alpha") {
            override fun get(obj: View): Float = obj.getAlpha()
            override fun set(obj: View, value: Float) { obj.setAlpha(value) }
        }

        @JvmField
        val TRANSLATION_X: android.util.Property<View, Float> = object : android.util.Property<View, Float>(Float::class.javaObjectType, "translationX") {
            override fun get(obj: View): Float = obj.getTranslationX()
            override fun set(obj: View, value: Float) { obj.setTranslationX(value) }
        }

        @JvmField
        val TRANSLATION_Y: android.util.Property<View, Float> = object : android.util.Property<View, Float>(Float::class.javaObjectType, "translationY") {
            override fun get(obj: View): Float = obj.getTranslationY()
            override fun set(obj: View, value: Float) { obj.setTranslationY(value) }
        }

        @JvmField
        val ROTATION: android.util.Property<View, Float> = object : android.util.Property<View, Float>(Float::class.javaObjectType, "rotation") {
            override fun get(obj: View): Float = obj.getRotation()
            override fun set(obj: View, value: Float) { obj.setRotation(value) }
        }

        @JvmField
        val SCALE_X: android.util.Property<View, Float> = object : android.util.Property<View, Float>(Float::class.javaObjectType, "scaleX") {
            override fun get(obj: View): Float = obj.getScaleX()
            override fun set(obj: View, value: Float) { obj.setScaleX(value) }
        }

        @JvmField
        val SCALE_Y: android.util.Property<View, Float> = object : android.util.Property<View, Float>(Float::class.javaObjectType, "scaleY") {
            override fun get(obj: View): Float = obj.getScaleY()
            override fun set(obj: View, value: Float) { obj.setScaleY(value) }
        }

        @JvmField
        val X: android.util.Property<View, Float> = object : android.util.Property<View, Float>(Float::class.javaObjectType, "x") {
            override fun get(obj: View): Float = obj.getX()
            override fun set(obj: View, value: Float) { obj.setX(value) }
        }

        @JvmField
        val Y: android.util.Property<View, Float> = object : android.util.Property<View, Float>(Float::class.javaObjectType, "y") {
            override fun get(obj: View): Float = obj.getY()
            override fun set(obj: View, value: Float) { obj.setY(value) }
        }
        const val STATE_SELECTED = 16842913
        const val STATE_CHECKED = 16842912
        const val STATE_ENABLED = 16842910
        const val STATE_ACTIVATED = 16843518
        const val STATE_WINDOW_FOCUSED = 16842909
        const val STATE_CHECKABLE = 16842911
        const val STATE_HOVERED = 16843623

        @JvmField
        val EMPTY_STATE_SET = IntArray(0)

        @JvmField
        val ENABLED_STATE_SET = intArrayOf(STATE_ENABLED)

        @JvmField
        val FOCUSED_STATE_SET = intArrayOf(STATE_FOCUSED)

        @JvmField
        val SELECTED_STATE_SET = intArrayOf(STATE_SELECTED)

        @JvmField
        val PRESSED_STATE_SET = intArrayOf(STATE_PRESSED)

        @JvmField
        val ENABLED_FOCUSED_STATE_SET = intArrayOf(STATE_ENABLED, STATE_FOCUSED)

        private val sNextGeneratedId = AtomicInteger(1)

        @JvmStatic
        fun generateViewId(): Int {
            while (true) {
                val result = sNextGeneratedId.get()
                var newValue = result + 1
                if (newValue > 0x00FFFFFF) newValue = 1
                if (sNextGeneratedId.compareAndSet(result, newValue)) return result
            }
        }

        @JvmStatic
        fun inflate(context: Context, resource: Int, root: ViewGroup?): View =
            LayoutInflater.from(context).inflate(resource, root)

        @JvmStatic
        fun resolveSize(size: Int, measureSpec: Int): Int = resolveSizeAndState(size, measureSpec, 0) and MEASURED_SIZE_MASK

        @JvmStatic
        fun resolveSizeAndState(size: Int, measureSpec: Int, childMeasuredState: Int): Int {
            val specMode = MeasureSpec.getMode(measureSpec)
            val specSize = MeasureSpec.getSize(measureSpec)
            return when (specMode) {
                MeasureSpec.AT_MOST -> if (specSize < size) specSize or MEASURED_STATE_TOO_SMALL else size
                MeasureSpec.EXACTLY -> specSize
                else -> size
            } or (childMeasuredState and MEASURED_STATE_MASK)
        }

        @JvmStatic
        fun getDefaultSize(size: Int, measureSpec: Int): Int {
            val specMode = MeasureSpec.getMode(measureSpec)
            val specSize = MeasureSpec.getSize(measureSpec)
            return if (specMode == MeasureSpec.UNSPECIFIED) size else specSize
        }
    }

    private val mContext: Context

    constructor(context: Context?) {
        mContext = context ?: com.lagradost.desktop.runtime.AndroidRuntime.applicationContext
                ?: throw IllegalStateException("View created without context")
    }

    constructor(context: Context?, attrs: AttributeSet?) : this(context) {
        if (attrs != null) ViewAttributes.applyViewAttributes(this, attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : this(context, attrs)

    // ---------------------------------------------------------------- reactive state
    /** Incremented whenever the view needs to be redrawn/re-laid out */
    val renderVersion = mutableIntStateOf(0)
    private var mId = NO_ID
    internal var mParent: ViewParent? = null
    private var mLayoutParams by mutableStateOf<ViewGroup.LayoutParams?>(null)
    private var mVisibility by mutableIntStateOf(VISIBLE)
    private var mEnabled by mutableStateOf(true)
    private var mAlpha by mutableFloatStateOf(1f)
    private var mTranslationX by mutableFloatStateOf(0f)
    private var mTranslationY by mutableFloatStateOf(0f)
    private var mTranslationZ by mutableFloatStateOf(0f)
    private var mRotation by mutableFloatStateOf(0f)
    private var mScaleX by mutableFloatStateOf(1f)
    private var mScaleY by mutableFloatStateOf(1f)
    private var mElevation by mutableFloatStateOf(0f)
    private var mBackground by mutableStateOf<Drawable?>(null)
    private var mForeground by mutableStateOf<Drawable?>(null)
    private var mBackgroundTint by mutableStateOf<ColorStateList?>(null)
    private var mPaddingLeft by mutableIntStateOf(0)
    private var mPaddingTop by mutableIntStateOf(0)
    private var mPaddingRight by mutableIntStateOf(0)
    private var mPaddingBottom by mutableIntStateOf(0)
    private var mMinWidth by mutableIntStateOf(0)
    private var mMinHeight by mutableIntStateOf(0)
    private var mSelected by mutableStateOf(false)
    private var mActivated by mutableStateOf(false)
    private var mPressed by mutableStateOf(false)
    private var mFocused by mutableStateOf(false)
    private var mClipToOutline by mutableStateOf(false)
    private var mContentDescription by mutableStateOf<CharSequence?>(null)
    private var mClickable = false
    private var mLongClickable = false
    private var mFocusable = FOCUSABLE_AUTO
    private var mFocusableInTouchMode = false
    private var mTag: Any? = null
    private var mKeyedTags: SparseArray<Any?>? = null
    private var mNextFocusDownId = NO_ID
    private var mNextFocusUpId = NO_ID
    private var mNextFocusLeftId = NO_ID
    private var mNextFocusRightId = NO_ID
    private var mNextFocusForwardId = NO_ID
    private var mLayoutDirection = LAYOUT_DIRECTION_LTR
    private var mTextAlignment = TEXT_ALIGNMENT_GRAVITY
    private var mSystemUiVisibility = 0
    private var mScrollX by mutableIntStateOf(0)
    private var mScrollY by mutableIntStateOf(0)
    private var mImportantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_AUTO

    // Frame (px, relative to the parent) set by layout(), like Android
    private var mLeft = 0
    private var mTop = 0
    private var mRight = 0
    private var mBottom = 0
    private var mMeasuredWidth = 0
    private var mMeasuredHeight = 0
    private var mAttached = false

    /** The Compose node rendering this view (renderer), told about frame changes */
    @JvmField
    var desktopNode: com.lagradost.desktop.runtime.ui.ViewNodeHandle? = null

    /** Set by the view host for the root it lays out: bumped to request a traversal */
    @JvmField
    var hostLayoutRequest: androidx.compose.runtime.MutableIntState? = null

    /** View.post() actions waiting for the view to be attached (View.getRunQueue) */
    private var mRunQueue: ArrayList<Pair<Runnable, Long>>? = null

    // Listeners
    internal var mOnClickListener: OnClickListener? = null
    internal var mOnLongClickListener: OnLongClickListener? = null
    internal var mOnFocusChangeListener: OnFocusChangeListener? = null
    internal var mOnKeyListener: OnKeyListener? = null
    internal var mOnTouchListener: OnTouchListener? = null
    internal var mOnDragListener: OnDragListener? = null
    internal var mOnApplyWindowInsetsListener: OnApplyWindowInsetsListener? = null
    internal var mOnGenericMotionListener: OnGenericMotionListener? = null
    private var mOnScrollChangeListener: OnScrollChangeListener? = null
    private val mLayoutChangeListeners = ArrayList<OnLayoutChangeListener>()
    private val mAttachStateListeners = ArrayList<OnAttachStateChangeListener>()
    private var mViewTreeObserver: ViewTreeObserver? = null
    private var mAnimator: ViewPropertyAnimator? = null
    private var mAnimation: android.view.animation.Animation? = null

    // ---------------------------------------------------------------- basic properties
    fun getContext(): Context = mContext
    open fun getResources(): Resources = mContext.resources
    open fun getId(): Int = mId
    open fun setId(id: Int) {
        mId = id
    }

    /**
     * Sets the id without calling an override. ConstraintLayout.setId uses a map that is still
     * null while its super constructor is applying attributes.
     */
    internal fun assignId(id: Int) {
        mId = id
    }

    open fun getParent(): ViewParent? = mParent
    open fun getRootView(): View {
        var v: View = this
        while (true) {
            val p = v.mParent as? View ?: return v
            v = p
        }
    }

    open fun getHandler(): Handler = Handler(Looper.getMainLooper())

    open fun getTag(): Any? = mTag
    open fun setTag(tag: Any?) {
        mTag = tag
    }

    open fun getTag(key: Int): Any? = mKeyedTags?.get(key)
    open fun setTag(key: Int, tag: Any?) {
        val tags = mKeyedTags ?: SparseArray<Any?>().also { mKeyedTags = it }
        tags.put(key, tag)
    }

    // desktop: Android returns null before params are set; callers here get a detached WRAP_CONTENT value
    // that is not stored, so ViewGroup.addView still applies the parent's generateDefaultLayoutParams
    open fun getLayoutParams(): ViewGroup.LayoutParams = mLayoutParams ?: ViewGroup.LayoutParams(-2, -2)

    internal fun layoutParamsOrNull(): ViewGroup.LayoutParams? = mLayoutParams

    private var mOverlay: ViewOverlay? = null
    open fun getOverlay(): ViewOverlay = mOverlay ?: ViewOverlay(this).also { mOverlay = it }

    /** ViewGroup internal: assigns layout params without requesting a layout */
    internal fun setLayoutParamsSilently(params: ViewGroup.LayoutParams) {
        mLayoutParams = params
    }

    open fun setLayoutParams(params: ViewGroup.LayoutParams?) {
        requireNotNull(params) { "Layout parameters cannot be null" }
        mLayoutParams = params
        resolveLayoutParams()
        requestLayout()
    }

    /** AOSP resolveLayoutParams: ConstraintLayout turns start/end constraints into left/right here */
    private fun resolveLayoutParams() {
        mLayoutParams?.resolveLayoutDirection(if (mLayoutDirection == LAYOUT_DIRECTION_RTL) LAYOUT_DIRECTION_RTL else LAYOUT_DIRECTION_LTR)
    }

    open fun getVisibility(): Int = mVisibility
    open fun setVisibility(visibility: Int) {
        val old = mVisibility
        if (old == visibility) return
        mVisibility = visibility
        // Android: entering or leaving GONE changes the layout, INVISIBLE only the drawing
        if ((old == GONE) != (visibility == GONE)) requestLayout()
        invalidate()
    }

    open fun isShown(): Boolean {
        var v: View? = this
        while (v != null) {
            if (v.mVisibility != VISIBLE) return false
            v = v.mParent as? View
        }
        return mAttached
    }

    open fun isEnabled(): Boolean = mEnabled
    open fun setEnabled(enabled: Boolean) {
        mEnabled = enabled
        refreshDrawableState()
    }

    open fun getAlpha(): Float = mAlpha
    open fun setAlpha(alpha: Float) {
        mAlpha = alpha
    }

    open fun getTranslationX(): Float = mTranslationX
    open fun setTranslationX(translationX: Float) {
        mTranslationX = translationX
    }

    open fun getTranslationY(): Float = mTranslationY
    open fun setTranslationY(translationY: Float) {
        mTranslationY = translationY
    }

    open fun getTranslationZ(): Float = mTranslationZ
    open fun setTranslationZ(translationZ: Float) {
        mTranslationZ = translationZ
    }

    open fun getX(): Float = mLeft + mTranslationX
    open fun setX(x: Float) = setTranslationX(x - mLeft)
    open fun getY(): Float = mTop + mTranslationY
    open fun setY(y: Float) = setTranslationY(y - mTop)
    open fun getZ(): Float = mElevation + mTranslationZ
    open fun getRotation(): Float = mRotation
    open fun setRotation(rotation: Float) {
        mRotation = rotation
    }

    open fun getRotationX(): Float = 0f
    open fun setRotationX(rotationX: Float) {}
    open fun getRotationY(): Float = 0f
    open fun setRotationY(rotationY: Float) {}
    open fun getScaleX(): Float = mScaleX
    open fun setScaleX(scaleX: Float) {
        mScaleX = scaleX
    }

    open fun getScaleY(): Float = mScaleY
    open fun setScaleY(scaleY: Float) {
        mScaleY = scaleY
    }

    open fun setPivotX(pivotX: Float) {}
    open fun setPivotY(pivotY: Float) {}
    open fun getPivotX(): Float = getWidth() / 2f
    open fun getPivotY(): Float = getHeight() / 2f
    open fun getElevation(): Float = mElevation
    open fun setElevation(elevation: Float) {
        mElevation = elevation
    }

    open fun setCameraDistance(distance: Float) {}

    // ---------------------------------------------------------------- background / drawing
    open fun getBackground(): Drawable? = mBackground
    open fun setBackground(background: Drawable?) {
        mBackground?.setCallback(null)
        mBackground = background
        background?.setCallback(this)
        background?.setState(getDrawableState())
        // Android: a background with padding (shape <padding>, inset, 9-patch) becomes the view padding
        if (background != null) {
            val p = Rect()
            if (background.getPadding(p)) {
                mPaddingLeft = p.left
                mPaddingTop = p.top
                mPaddingRight = p.right
                mPaddingBottom = p.bottom
            }
        }
        requestLayout()
        invalidate()
    }

    @Deprecated("")
    open fun setBackgroundDrawable(background: Drawable?) = setBackground(background)
    open fun setBackgroundColor(color: Int) {
        val bg = mBackground
        if (bg is ColorDrawable) {
            bg.mutate()
            bg.setColor(color)
            invalidate()
        } else setBackground(ColorDrawable(color))
    }

    open fun setBackgroundResource(resid: Int) {
        setBackground(if (resid == 0) null else mContext.getDrawable(resid))
    }

    open fun getBackgroundTintList(): ColorStateList? = mBackgroundTint
    open fun setBackgroundTintList(tint: ColorStateList?) {
        mBackgroundTint = tint
        mBackground?.setTintList(tint)
        invalidate()
    }

    open fun setBackgroundTintMode(tintMode: android.graphics.PorterDuff.Mode?) {
        mBackground?.setTintMode(tintMode)
    }

    open fun getForeground(): Drawable? = mForeground
    open fun setForeground(foreground: Drawable?) {
        mForeground = foreground
        foreground?.setCallback(this)
    }

    open fun setClipToOutline(clipToOutline: Boolean) {
        mClipToOutline = clipToOutline
    }

    open fun getClipToOutline(): Boolean = mClipToOutline
    open fun setOutlineProvider(provider: ViewOutlineProvider?) {}
    open fun getOutlineProvider(): ViewOutlineProvider? = ViewOutlineProvider.BACKGROUND

    open fun invalidate() {
        renderVersion.intValue++
    }

    open fun invalidate(dirty: Rect?) = invalidate()
    open fun invalidate(l: Int, t: Int, r: Int, b: Int) = invalidate()
    open fun postInvalidate() = invalidate()
    open fun postInvalidateDelayed(delayMilliseconds: Long) {
        postDelayed({ invalidate() }, delayMilliseconds)
    }

    open fun postInvalidateOnAnimation() = invalidate()

    /**
     * Android: marks this view and its ancestors for a new measure/layout pass; the root schedules
     * the traversal (LayoutScheduler).
     */
    open fun requestLayout() {
        mMeasureCache?.clear()
        val scheduler = com.lagradost.desktop.runtime.ui.LayoutScheduler
        var requester = false
        if (scheduler.viewRequestingLayout == null) {
            // ViewRootImpl.requestLayoutDuringLayout: handled by a second pass after the current one
            if (scheduler.isInLayout && !scheduler.requestLayoutDuringLayout(this)) return
            scheduler.viewRequestingLayout = this
            requester = true
        }
        mForceLayout = true
        val p = mParent
        if (p != null) {
            if (!p.isLayoutRequested()) p.requestLayout()
        } else {
            // a root: its host schedules the traversal (detached trees are laid out when shown)
            hostLayoutRequest?.let { scheduler.scheduleTraversal(it) }
        }
        if (requester) scheduler.viewRequestingLayout = null
    }

    open fun isLayoutRequested(): Boolean = mForceLayout

    /** Measure again at the next layout pass without requesting one from the parent */
    open fun forceLayout() {
        mMeasureCache?.clear()
        mForceLayout = true
    }

    /** Draw the background and content into a canvas (used by the renderer for custom views) */
    open fun draw(canvas: Canvas) {
        val bg = mBackground
        if (bg != null) {
            bg.setBounds(0, 0, getWidth(), getHeight())
            bg.draw(canvas)
        }
        onDraw(canvas)
        dispatchDraw(canvas)
        mForeground?.let {
            it.setBounds(0, 0, getWidth(), getHeight())
            it.draw(canvas)
        }
    }

    protected open fun onDraw(canvas: Canvas) {}
    protected open fun dispatchDraw(canvas: Canvas) {}

    /** true if a subclass overrides onDraw, meaning the renderer must call draw() */
    val hasCustomDraw: Boolean by lazy {
        if (this is com.lagradost.desktop.runtime.ui.DrawsItself) return@lazy true
        var c: Class<*>? = javaClass
        var custom = false
        while (c != null && c != View::class.java) {
            if (!c.name.startsWith("android.") && !c.name.startsWith("androidx.") && !c.name.startsWith("com.google.android.material.")) {
                if (c.declaredMethods.any { it.name == "onDraw" && it.parameterCount == 1 }) {
                    custom = true
                    break
                }
            }
            c = c.superclass
        }
        custom
    }

    override fun invalidateDrawable(who: Drawable) = invalidate()
    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
        getHandler().postAtTime(what, who, `when`)
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        getHandler().removeCallbacks(what, who)
    }

    open fun unscheduleDrawable(who: Drawable?) {}

    protected open fun verifyDrawable(who: Drawable): Boolean = who === mBackground || who === mForeground

    // ---------------------------------------------------------------- padding
    open fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        if (mPaddingLeft == left && mPaddingTop == top && mPaddingRight == right && mPaddingBottom == bottom) return
        mPaddingLeft = left
        mPaddingTop = top
        mPaddingRight = right
        mPaddingBottom = bottom
        requestLayout()
        invalidate()
    }

    open fun setPaddingRelative(start: Int, top: Int, end: Int, bottom: Int) = setPadding(start, top, end, bottom)
    open fun getPaddingLeft(): Int = mPaddingLeft
    open fun getPaddingTop(): Int = mPaddingTop
    open fun getPaddingRight(): Int = mPaddingRight
    open fun getPaddingBottom(): Int = mPaddingBottom
    open fun getPaddingStart(): Int = mPaddingLeft
    open fun getPaddingEnd(): Int = mPaddingRight
    open fun getMinimumWidth(): Int = mMinWidth
    open fun getMinimumHeight(): Int = mMinHeight
    open fun setMinimumWidth(minWidth: Int) {
        if (mMinWidth == minWidth) return
        mMinWidth = minWidth
        requestLayout()
    }

    open fun setMinimumHeight(minHeight: Int) {
        if (mMinHeight == minHeight) return
        mMinHeight = minHeight
        requestLayout()
    }

    // ---------------------------------------------------------------- geometry
    fun getWidth(): Int = mRight - mLeft
    fun getHeight(): Int = mBottom - mTop
    fun getLeft(): Int = mLeft
    fun getTop(): Int = mTop
    fun getRight(): Int = mRight
    fun getBottom(): Int = mBottom
    fun getMeasuredWidth(): Int = mMeasuredWidth and MEASURED_SIZE_MASK
    fun getMeasuredHeight(): Int = mMeasuredHeight and MEASURED_SIZE_MASK
    fun getMeasuredWidthAndState(): Int = mMeasuredWidth
    fun getMeasuredHeightAndState(): Int = mMeasuredHeight

    fun getMeasuredState(): Int = (mMeasuredWidth and MEASURED_STATE_MASK) or
        ((mMeasuredHeight shr MEASURED_HEIGHT_STATE_SHIFT) and (MEASURED_STATE_MASK shr MEASURED_HEIGHT_STATE_SHIFT))

    protected fun setMeasuredDimension(measuredWidth: Int, measuredHeight: Int) {
        mMeasuredWidth = measuredWidth
        mMeasuredHeight = measuredHeight
        mMeasuredDimensionSet = true
    }

    // Android measure/layout protocol (PFLAG_FORCE_LAYOUT, PFLAG_LAYOUT_REQUIRED, measure cache)
    private var mForceLayout = true
    private var mLayoutRequired = true
    private var mMeasuredDimensionSet = false
    private var mMeasureNeededBeforeLayout = false
    private var mIsLaidOut = false
    private var mOldWidthMeasureSpec = Int.MIN_VALUE
    private var mOldHeightMeasureSpec = Int.MIN_VALUE
    private var mMeasureCache: HashMap<Long, Long>? = null

    /**
     * View.measure: calls onMeasure when a layout was requested or the specs changed in a way that can
     * change the size, like Android (measuring again with the same specs is free, specs measured
     * before come from the measure cache).
     */
    fun measure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        resolveLayoutParams()
        val key = (widthMeasureSpec.toLong() shl 32) or (heightMeasureSpec.toLong() and 0xffffffffL)
        val cache = mMeasureCache ?: HashMap<Long, Long>(4).also { mMeasureCache = it }
        val forceLayout = mForceLayout
        val specChanged = widthMeasureSpec != mOldWidthMeasureSpec || heightMeasureSpec != mOldHeightMeasureSpec
        val isSpecExactly = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY &&
            MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY
        val matchesSpecSize = getMeasuredWidth() == MeasureSpec.getSize(widthMeasureSpec) &&
            getMeasuredHeight() == MeasureSpec.getSize(heightMeasureSpec)
        val needsLayout = specChanged && (!isSpecExactly || !matchesSpecSize)
        if (forceLayout || needsLayout) {
            mMeasuredDimensionSet = false
            val cached = if (forceLayout) null else cache[key]
            if (cached == null) {
                onMeasure(widthMeasureSpec, heightMeasureSpec)
                mMeasureNeededBeforeLayout = false
            } else {
                mMeasuredWidth = (cached shr 32).toInt()
                mMeasuredHeight = cached.toInt()
                mMeasuredDimensionSet = true
                mMeasureNeededBeforeLayout = true
            }
            if (!mMeasuredDimensionSet) {
                // Android throws here; stay lenient with extension views
                setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec), getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec))
            }
            mLayoutRequired = true
        }
        mOldWidthMeasureSpec = widthMeasureSpec
        mOldHeightMeasureSpec = heightMeasureSpec
        cache[key] = (mMeasuredWidth.toLong() shl 32) or (mMeasuredHeight.toLong() and 0xffffffffL)
    }

    protected open fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
            getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec)
        )
    }

    protected open fun getSuggestedMinimumWidth(): Int = kotlin.math.max(mMinWidth, mBackground?.getMinimumWidth() ?: 0)
    protected open fun getSuggestedMinimumHeight(): Int = kotlin.math.max(mMinHeight, mBackground?.getMinimumHeight() ?: 0)

    /** View.getBaseline: -1 when the view has no text baseline */
    open fun getBaseline(): Int = -1

    open fun layout(l: Int, t: Int, r: Int, b: Int) {
        if (mMeasureNeededBeforeLayout) {
            onMeasure(mOldWidthMeasureSpec, mOldHeightMeasureSpec)
            mMeasureNeededBeforeLayout = false
        }
        val oldL = mLeft
        val oldT = mTop
        val oldR = mRight
        val oldB = mBottom
        val changed = setFrame(l, t, r, b)
        if (changed || mLayoutRequired) {
            onLayout(changed, l, t, r, b)
            mLayoutRequired = false
            if (mLayoutChangeListeners.isNotEmpty()) {
                for (listener in mLayoutChangeListeners.toList()) listener.onLayoutChange(this, l, t, r, b, oldL, oldT, oldR, oldB)
            }
        }
        mForceLayout = false
        mIsLaidOut = true
    }

    /** Assigns the frame; true when it changed. Calls onSizeChanged for a new size, like Android */
    protected open fun setFrame(left: Int, top: Int, right: Int, bottom: Int): Boolean {
        if (mLeft == left && mRight == right && mTop == top && mBottom == bottom) return false
        val oldWidth = mRight - mLeft
        val oldHeight = mBottom - mTop
        mLeft = left
        mTop = top
        mRight = right
        mBottom = bottom
        val newWidth = right - left
        val newHeight = bottom - top
        val sizeChanged = newWidth != oldWidth || newHeight != oldHeight
        frameChanged(sizeChanged)
        if (sizeChanged) onSizeChanged(newWidth, newHeight, oldWidth, oldHeight)
        return true
    }

    /** Tells the renderer that this view moved or was resized */
    private fun frameChanged(sizeChanged: Boolean) {
        if (sizeChanged) desktopNode?.invalidateNodeMeasurement()
        (mParent as? View)?.desktopNode?.invalidateNodeMeasurement()
    }

    fun setLeft(left: Int) {
        setFrame(left, mTop, mRight, mBottom)
    }

    fun setTop(top: Int) {
        setFrame(mLeft, top, mRight, mBottom)
    }

    fun setRight(right: Int) {
        setFrame(mLeft, mTop, right, mBottom)
    }

    fun setBottom(bottom: Int) {
        setFrame(mLeft, mTop, mRight, bottom)
    }

    fun setLeftTopRightBottom(left: Int, top: Int, right: Int, bottom: Int) {
        setFrame(left, top, right, bottom)
    }

    open fun offsetTopAndBottom(offset: Int) {
        if (offset == 0) return
        mTop += offset
        mBottom += offset
        frameChanged(false)
    }

    open fun offsetLeftAndRight(offset: Int) {
        if (offset == 0) return
        mLeft += offset
        mRight += offset
        frameChanged(false)
    }

    protected open fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {}
    protected open fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {}

    open fun getLocationOnScreen(outLocation: IntArray) {
        getLocationInWindow(outLocation)
    }

    /** Position in the window: frames, translations and parent scrolling, plus the host's position */
    open fun getLocationInWindow(outLocation: IntArray) {
        var x = mLeft + mTranslationX
        var y = mTop + mTranslationY
        var v: View = this
        while (true) {
            val p = v.mParent as? View ?: break
            x += p.mLeft + p.mTranslationX - p.mScrollX
            y += p.mTop + p.mTranslationY - p.mScrollY
            v = p
        }
        com.lagradost.desktop.runtime.ui.HostPositions.positions[v]?.let {
            x += it.x
            y += it.y
        }
        outLocation[0] = (x + 0.5f).toInt()
        outLocation[1] = (y + 0.5f).toInt()
    }

    open fun getGlobalVisibleRect(r: Rect): Boolean {
        val loc = IntArray(2)
        getLocationInWindow(loc)
        r.set(loc[0], loc[1], loc[0] + getWidth(), loc[1] + getHeight())
        return isShown()
    }

    open fun getLocalVisibleRect(r: Rect): Boolean {
        r.set(0, 0, getWidth(), getHeight())
        return isShown()
    }

    open fun getHitRect(outRect: Rect) = outRect.set(mLeft, mTop, mRight, mBottom)
    open fun getDrawingRect(outRect: Rect) = outRect.set(mScrollX, mScrollY, mScrollX + getWidth(), mScrollY + getHeight())
    open fun requestRectangleOnScreen(rectangle: Rect?, immediate: Boolean): Boolean = false

    open fun addOnLayoutChangeListener(listener: OnLayoutChangeListener) {
        mLayoutChangeListeners.add(listener)
    }

    open fun removeOnLayoutChangeListener(listener: OnLayoutChangeListener) {
        mLayoutChangeListeners.remove(listener)
    }

    // ---------------------------------------------------------------- scrolling
    open fun getScrollX(): Int = mScrollX
    open fun getScrollY(): Int = mScrollY
    open fun setScrollX(value: Int) = scrollTo(value, mScrollY)
    open fun setScrollY(value: Int) = scrollTo(mScrollX, value)
    open fun scrollTo(x: Int, y: Int) {
        val oldX = mScrollX
        val oldY = mScrollY
        if (oldX != x || oldY != y) {
            mScrollX = x
            mScrollY = y
            onScrollChanged(x, y, oldX, oldY)
            mOnScrollChangeListener?.onScrollChange(this, x, y, oldX, oldY)
        }
    }

    open fun scrollBy(x: Int, y: Int) = scrollTo(mScrollX + x, mScrollY + y)

    /** NestedScrollingChild: parents (CoordinatorLayout) may consume part of a scroll before the child does. */
    open fun dispatchNestedPreScroll(dx: Int, dy: Int, consumed: IntArray?): Boolean {
        val out = consumed ?: return false
        val parent = getParent() as? ViewGroup ?: return false
        parent.onNestedPreScroll(this, dx, dy, out)
        return out[0] != 0 || out[1] != 0
    }

    open fun dispatchNestedScroll(dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int, offsetInWindow: IntArray?): Boolean {
        val parent = getParent() as? ViewGroup ?: return false
        parent.onNestedScroll(this, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed)
        return dyUnconsumed != 0 || dxUnconsumed != 0
    }
    protected open fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {}
    open fun setOnScrollChangeListener(l: OnScrollChangeListener?) {
        mOnScrollChangeListener = l
    }

    protected open fun computeHorizontalScrollRange(): Int = getWidth()
    protected open fun computeHorizontalScrollOffset(): Int = mScrollX
    protected open fun computeHorizontalScrollExtent(): Int = getWidth()
    protected open fun computeVerticalScrollRange(): Int = getHeight()
    protected open fun computeVerticalScrollOffset(): Int = mScrollY
    protected open fun computeVerticalScrollExtent(): Int = getHeight()

    /** View.canScrollVertically: from the scroll range, offset and extent */
    open fun canScrollVertically(direction: Int): Boolean {
        val offset = computeVerticalScrollOffset()
        val range = computeVerticalScrollRange() - computeVerticalScrollExtent()
        if (range == 0) return false
        return if (direction < 0) offset > 0 else offset < range - 1
    }

    open fun canScrollHorizontally(direction: Int): Boolean {
        val offset = computeHorizontalScrollOffset()
        val range = computeHorizontalScrollRange() - computeHorizontalScrollExtent()
        if (range == 0) return false
        return if (direction < 0) offset > 0 else offset < range - 1
    }
    open fun setVerticalScrollBarEnabled(enabled: Boolean) {}
    open fun setHorizontalScrollBarEnabled(enabled: Boolean) {}
    open fun isVerticalScrollBarEnabled(): Boolean = true
    open fun isHorizontalScrollBarEnabled(): Boolean = true
    open fun setScrollbarFadingEnabled(fadeScrollbars: Boolean) {}
    open fun setScrollBarStyle(style: Int) {}
    open fun setOverScrollMode(overScrollMode: Int) {}
    open fun getOverScrollMode(): Int = OVER_SCROLL_IF_CONTENT_SCROLLS
    open fun setNestedScrollingEnabled(enabled: Boolean) {}
    open fun isNestedScrollingEnabled(): Boolean = true
    open fun setFadingEdgeLength(length: Int) {}
    open fun setVerticalFadingEdgeEnabled(enabled: Boolean) {}
    open fun setHorizontalFadingEdgeEnabled(enabled: Boolean) {}

    // ---------------------------------------------------------------- state
    open fun isSelected(): Boolean = mSelected
    open fun setSelected(selected: Boolean) {
        mSelected = selected
        refreshDrawableState()
    }

    open fun isActivated(): Boolean = mActivated
    open fun setActivated(activated: Boolean) {
        mActivated = activated
        refreshDrawableState()
    }

    open fun isPressed(): Boolean = mPressed
    open fun setPressed(pressed: Boolean) {
        mPressed = pressed
        refreshDrawableState()
    }

    open fun getDrawableState(): IntArray {
        val states = ArrayList<Int>(6)
        if (mEnabled) states.add(STATE_ENABLED)
        if (mFocused) states.add(STATE_FOCUSED)
        if (mSelected) states.add(STATE_SELECTED)
        if (mPressed) states.add(STATE_PRESSED)
        if (mActivated) states.add(STATE_ACTIVATED)
        states.add(STATE_WINDOW_FOCUSED)
        onCreateDrawableState(states)
        return states.toIntArray()
    }

    /** Subclasses add extra drawable states (checked etc) */
    internal open fun onCreateDrawableState(states: MutableList<Int>) {}

    protected open fun onCreateDrawableState(extraSpace: Int): IntArray = getDrawableState()

    open fun refreshDrawableState() {
        drawableStateChanged()
        invalidate()
    }

    protected open fun drawableStateChanged() {
        val state = getDrawableState()
        mBackground?.setState(state)
        mForeground?.setState(state)
    }

    open fun jumpDrawablesToCurrentState() {}

    // ---------------------------------------------------------------- click / focus
    open fun setOnClickListener(l: OnClickListener?) {
        if (!isClickable()) setClickable(true)
        mOnClickListener = l
    }

    open fun hasOnClickListeners(): Boolean = mOnClickListener != null

    open fun setOnLongClickListener(l: OnLongClickListener?) {
        if (!isLongClickable()) setLongClickable(true)
        mOnLongClickListener = l
    }

    open fun setOnFocusChangeListener(l: OnFocusChangeListener?) {
        mOnFocusChangeListener = l
    }

    open fun getOnFocusChangeListener(): OnFocusChangeListener? = mOnFocusChangeListener

    open fun setOnKeyListener(l: OnKeyListener?) {
        mOnKeyListener = l
    }

    open fun setOnTouchListener(l: OnTouchListener?) {
        mOnTouchListener = l
    }

    open fun setOnGenericMotionListener(l: OnGenericMotionListener?) {
        mOnGenericMotionListener = l
    }

    open fun setOnApplyWindowInsetsListener(listener: OnApplyWindowInsetsListener?) {
        mOnApplyWindowInsetsListener = listener
        listener?.onApplyWindowInsets(this, WindowInsets.CONSUMED)
    }

    open fun onApplyWindowInsets(insets: WindowInsets): WindowInsets = insets
    open fun dispatchApplyWindowInsets(insets: WindowInsets): WindowInsets =
        mOnApplyWindowInsetsListener?.onApplyWindowInsets(this, insets) ?: onApplyWindowInsets(insets)

    open fun requestApplyInsets() {}
    open fun getRootWindowInsets(): WindowInsets = WindowInsets.CONSUMED

    open fun performClick(): Boolean {
        val l = mOnClickListener ?: return false
        l.onClick(this)
        return true
    }

    open fun callOnClick(): Boolean = performClick()
    open fun performLongClick(): Boolean = mOnLongClickListener?.onLongClick(this) ?: false
    open fun performLongClick(x: Float, y: Float): Boolean = performLongClick()
    open fun performHapticFeedback(feedbackConstant: Int): Boolean = false
    open fun performHapticFeedback(feedbackConstant: Int, flags: Int): Boolean = false
    open fun playSoundEffect(soundConstant: Int) {}

    open fun isClickable(): Boolean = mClickable
    open fun setClickable(clickable: Boolean) {
        mClickable = clickable
    }

    open fun isLongClickable(): Boolean = mLongClickable
    open fun setLongClickable(longClickable: Boolean) {
        mLongClickable = longClickable
    }

    open fun setContextClickable(contextClickable: Boolean) {}
    open fun isFocusable(): Boolean = mFocusable == FOCUSABLE || (mFocusable == FOCUSABLE_AUTO && (mClickable || this is android.widget.EditText))
    open fun setFocusable(focusable: Boolean) {
        mFocusable = if (focusable) FOCUSABLE else NOT_FOCUSABLE
    }

    open fun setFocusable(focusable: Int) {
        mFocusable = focusable
    }

    open fun getFocusable(): Int = mFocusable
    open fun isFocusableInTouchMode(): Boolean = mFocusableInTouchMode
    open fun setFocusableInTouchMode(focusableInTouchMode: Boolean) {
        mFocusableInTouchMode = focusableInTouchMode
        if (focusableInTouchMode) mFocusable = FOCUSABLE
    }

    open fun isFocused(): Boolean = mFocused
    open fun hasFocus(): Boolean = mFocused
    open fun hasFocusable(): Boolean = isFocusable()

    /** Called by the renderer when compose focus changes */
    fun dispatchFocusChanged(focused: Boolean) {
        if (mFocused == focused) return
        mFocused = focused
        refreshDrawableState()
        onFocusChanged(focused, if (focused) FOCUS_DOWN else 0, null)
        mOnFocusChangeListener?.onFocusChange(this, focused)
        if (focused) (mParent as? ViewGroup)?.requestChildFocus(this, this)
    }

    protected open fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {}

    /** Focus requests are routed to the renderer through this counter */
    val focusRequest = mutableIntStateOf(0)

    fun requestFocus(): Boolean = requestFocus(FOCUS_DOWN)
    fun requestFocus(direction: Int): Boolean = requestFocus(direction, null)
    open fun isInTouchMode(): Boolean = com.lagradost.desktop.runtime.TouchMode.inTouchMode

    open fun requestFocus(direction: Int, previouslyFocusedRect: Rect?): Boolean {
        if (!isFocusable() && !mFocusableInTouchMode && this !is ViewGroup) return false
        // Android: in touch mode only focusableInTouchMode views can take focus
        if (this !is ViewGroup && isInTouchMode() && !mFocusableInTouchMode) return false
        focusRequest.intValue++
        return true
    }

    fun requestFocusFromTouch(): Boolean = requestFocus()
    open fun clearFocus() {
        dispatchFocusChanged(false)
    }

    open fun findFocus(): View? = if (mFocused) this else null
    open fun setDefaultFocusHighlightEnabled(defaultFocusHighlightEnabled: Boolean) {}

    open fun getNextFocusDownId(): Int = mNextFocusDownId
    open fun setNextFocusDownId(id: Int) {
        mNextFocusDownId = id
    }

    open fun getNextFocusUpId(): Int = mNextFocusUpId
    open fun setNextFocusUpId(id: Int) {
        mNextFocusUpId = id
    }

    open fun getNextFocusLeftId(): Int = mNextFocusLeftId
    open fun setNextFocusLeftId(id: Int) {
        mNextFocusLeftId = id
    }

    open fun getNextFocusRightId(): Int = mNextFocusRightId
    open fun setNextFocusRightId(id: Int) {
        mNextFocusRightId = id
    }

    open fun getNextFocusForwardId(): Int = mNextFocusForwardId
    open fun setNextFocusForwardId(id: Int) {
        mNextFocusForwardId = id
    }

    open fun focusSearch(direction: Int): View? = null

    // ---------------------------------------------------------------- key / touch dispatch
    open fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (mOnKeyListener?.onKey(this, event.keyCode, event) == true) return true
        return if (event.action == KeyEvent.ACTION_DOWN) onKeyDown(event.keyCode, event) else onKeyUp(event.keyCode, event)
    }

    open fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = false
    open fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (KeyEvent.isConfirmKey(keyCode) && isClickable()) return performClick()
        return false
    }

    open fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean = false
    open fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean = false

    open fun setOnDragListener(l: OnDragListener?) {
        mOnDragListener = l
    }

    open fun onDragEvent(event: DragEvent): Boolean = false

    open fun dispatchDragEvent(event: DragEvent): Boolean = mOnDragListener?.onDrag(this, event) == true || onDragEvent(event)

    /** Drag and drop within this view's window (extensions reorder lists this way), see DragAndDrop */
    fun startDragAndDrop(data: android.content.ClipData?, shadowBuilder: DragShadowBuilder?, myLocalState: Any?, flags: Int): Boolean =
        DragAndDrop.start(this, data, myLocalState)

    @Deprecated("startDragAndDrop")
    fun startDrag(data: android.content.ClipData?, shadowBuilder: DragShadowBuilder?, myLocalState: Any?, flags: Int): Boolean =
        startDragAndDrop(data, shadowBuilder, myLocalState, flags)

    fun cancelDragAndDrop() = DragAndDrop.cancel()

    fun updateDragShadow(shadowBuilder: DragShadowBuilder?) {}

    open fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (mOnTouchListener?.onTouch(this, event) == true) return true
        return onTouchEvent(event)
    }

    open fun onTouchEvent(event: MotionEvent): Boolean {
        // like Android: a view that only reacts to a long press (a drag handle) takes the press too, or the long press never comes
        if (!mEnabled) return isClickable() || isLongClickable()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> setPressed(true)
            MotionEvent.ACTION_UP -> {
                setPressed(false)
                if (isClickable()) performClick()
            }
            MotionEvent.ACTION_CANCEL -> setPressed(false)
        }
        return isClickable() || isLongClickable()
    }

    open fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        mOnGenericMotionListener?.onGenericMotion(this, event) ?: onGenericMotionEvent(event)

    open fun onGenericMotionEvent(event: MotionEvent): Boolean = false
    open fun onInterceptTouchEvent(ev: MotionEvent): Boolean = false

    // ---------------------------------------------------------------- misc
    open fun getContentDescription(): CharSequence? = mContentDescription
    open fun setContentDescription(contentDescription: CharSequence?) {
        mContentDescription = contentDescription
    }

    open fun setTooltipText(tooltipText: CharSequence?) {}
    open fun getLayoutDirection(): Int = mLayoutDirection
    open fun setLayoutDirection(layoutDirection: Int) {
        mLayoutDirection = layoutDirection
    }

    open fun setTextDirection(textDirection: Int) {}
    open fun getTextAlignment(): Int = mTextAlignment
    open fun setTextAlignment(textAlignment: Int) {
        mTextAlignment = textAlignment
        invalidate()
    }

    open fun getSystemUiVisibility(): Int = mSystemUiVisibility
    open fun setSystemUiVisibility(visibility: Int) {
        mSystemUiVisibility = visibility
    }

    open fun setOnSystemUiVisibilityChangeListener(l: OnSystemUiVisibilityChangeListener?) {}
    open fun getWindowSystemUiVisibility(): Int = mSystemUiVisibility
    open fun setKeepScreenOn(keepScreenOn: Boolean) {}
    open fun getKeepScreenOn(): Boolean = false
    open fun setImportantForAccessibility(mode: Int) {
        mImportantForAccessibility = mode
    }

    open fun getImportantForAccessibility(): Int = mImportantForAccessibility
    open fun setImportantForAutofill(mode: Int) {}
    open fun setAccessibilityDelegate(delegate: Any?) {}
    open fun announceForAccessibility(text: CharSequence?) {}
    open fun sendAccessibilityEvent(eventType: Int) {}
    open fun setLayerType(layerType: Int, paint: android.graphics.Paint?) {}
    open fun getLayerType(): Int = LAYER_TYPE_NONE
    open fun setWillNotDraw(willNotDraw: Boolean) {}
    open fun setDrawingCacheEnabled(enabled: Boolean) {}
    open fun setSaveEnabled(enabled: Boolean) {}
    open fun setSoundEffectsEnabled(soundEffectsEnabled: Boolean) {}
    open fun setHapticFeedbackEnabled(hapticFeedbackEnabled: Boolean) {}
    open fun isInEditMode(): Boolean = false
    open fun isHardwareAccelerated(): Boolean = true
    open fun isAttachedToWindow(): Boolean = mAttached
    open fun isLaidOut(): Boolean = mIsLaidOut
    open fun getWindowToken(): android.os.IBinder? = null
    open fun getApplicationWindowToken(): android.os.IBinder? = null
    open fun getDisplay(): Display? = null
    open fun getWindowVisibility(): Int = if (mAttached) VISIBLE else GONE
    open fun hasWindowFocus(): Boolean = mAttached
    open fun onWindowFocusChanged(hasWindowFocus: Boolean) {}
    open fun onSaveInstanceState(): Parcelable? = null
    open fun onRestoreInstanceState(state: Parcelable?) {}
    open fun saveHierarchyState(container: SparseArray<Parcelable>) {}
    open fun restoreHierarchyState(container: SparseArray<Parcelable>) {}
    open fun startAnimation(animation: android.view.animation.Animation?) {
        animation?.applyTo(this)
    }

    open fun clearAnimation() {
        mAnimation = null
    }

    open fun getAnimation(): android.view.animation.Animation? = mAnimation
    open fun setAnimation(animation: android.view.animation.Animation?) {
        mAnimation = animation
    }

    /**
     * Android shares one observer per window: attached views use their root's, detached ones a
     * floating observer merged into the root's when they are attached.
     */
    open fun getViewTreeObserver(): ViewTreeObserver {
        val owner = if (mAttached) getRootView() else this
        return owner.mViewTreeObserver ?: ViewTreeObserver().also { owner.mViewTreeObserver = it }
    }

    open fun animate(): ViewPropertyAnimator = mAnimator ?: ViewPropertyAnimator(this).also { mAnimator = it }

    open fun bringToFront() {
        (mParent as? ViewGroup)?.bringChildToFront(this)
    }

    open fun addOnAttachStateChangeListener(listener: OnAttachStateChangeListener) {
        mAttachStateListeners.add(listener)
    }

    open fun removeOnAttachStateChangeListener(listener: OnAttachStateChangeListener) {
        mAttachStateListeners.remove(listener)
    }

    /**
     * Attaches the view to the window: called by the view host for its root and by ViewGroup for
     * views added to an attached parent, like Android.
     */
    open fun dispatchAttachedToWindow() {
        if (mAttached) return
        mAttached = true
        // merge a floating tree observer into the window's one
        val floating = mViewTreeObserver
        if (floating != null && mParent != null) {
            mViewTreeObserver = null
            getViewTreeObserver().merge(floating)
        }
        mRunQueue?.let { queue ->
            mRunQueue = null
            for ((action, delay) in queue) if (delay > 0) postDelayed(action, delay) else post(action)
        }
        onAttachedToWindow()
        for (l in mAttachStateListeners.toList()) l.onViewAttachedToWindow(this)
    }

    open fun dispatchDetachedFromWindow() {
        if (!mAttached) return
        onDetachedFromWindow()
        for (l in mAttachStateListeners.toList()) l.onViewDetachedFromWindow(this)
        mAttached = false
    }

    protected open fun onAttachedToWindow() {}
    protected open fun onDetachedFromWindow() {}
    protected open fun onFinishInflate() {}

    /** Called by LayoutInflater after children were added */
    fun finishInflate() = onFinishInflate()

    /**
     * Android: actions posted to a detached view wait until it is attached; posts to an attached
     * view run after a pending layout pass (so sizes are known), like the traversal barrier.
     */
    open fun post(action: Runnable?): Boolean {
        if (action == null) return false
        if (!mAttached) {
            (mRunQueue ?: ArrayList<Pair<Runnable, Long>>().also { mRunQueue = it }).add(action to 0L)
            return true
        }
        if (com.lagradost.desktop.runtime.ui.LayoutScheduler.postAfterLayout(getRootView(), action)) return true
        return getHandler().post(action)
    }

    open fun postDelayed(action: Runnable?, delayMillis: Long): Boolean {
        if (action == null) return false
        if (!mAttached) {
            (mRunQueue ?: ArrayList<Pair<Runnable, Long>>().also { mRunQueue = it }).add(action to delayMillis)
            return true
        }
        return getHandler().postDelayed(action, delayMillis)
    }

    open fun postOnAnimation(action: Runnable) {
        post(action)
    }

    open fun postOnAnimationDelayed(action: Runnable, delayMillis: Long) {
        postDelayed(action, delayMillis)
    }

    open fun removeCallbacks(action: Runnable?): Boolean {
        if (action != null) {
            mRunQueue?.removeAll { it.first === action }
            com.lagradost.desktop.runtime.ui.LayoutScheduler.removeCallbacks(action)
            getHandler().removeCallbacks(action)
        }
        return true
    }

    /** T may be View? so upstream `findViewById<View?>(id)` compiles. The value is still nullable. */
    open fun <T : View?> findViewById(id: Int): T {
        if (id == NO_ID) return null as T
        @Suppress("UNCHECKED_CAST")
        return findViewTraversal(id) as T
    }

    open fun <T : View> requireViewById(id: Int): T =
        findViewById<T?>(id) ?: throw IllegalArgumentException("ID does not reference a View inside this View")

    open fun findViewTraversal(id: Int): View? = if (id == mId) this else null

    open fun <T : View> findViewWithTag(tag: Any?): T? {
        @Suppress("UNCHECKED_CAST")
        return findViewWithTagTraversal(tag) as T?
    }

    open fun findViewWithTagTraversal(tag: Any?): View? = if (tag != null && tag == mTag) this else null

    /** Walk up the context wrappers to find the hosting activity */
    fun findActivity(): android.app.Activity? {
        var c: Context? = mContext
        while (c != null) {
            if (c is android.app.Activity) return c
            c = (c as? ContextWrapper)?.baseContext
        }
        return null
    }

    override fun toString(): String = "${javaClass.name}{${Integer.toHexString(System.identityHashCode(this))} id=$mId}"
}
