package android.view

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.lagradost.desktop.runtime.ui.ViewAttributes

interface ViewParent {
    fun getParent(): ViewParent?
    fun requestLayout()
    fun isLayoutRequested(): Boolean = false
    fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean)
    fun requestChildFocus(child: View?, focused: View?)
    fun clearChildFocus(child: View?) {}
    fun bringChildToFront(child: View?)
    fun focusSearch(focused: View?, direction: Int): View? = null
    fun recomputeViewAttributes(child: View?) {}
    fun childDrawableStateChanged(child: View?) {}
    fun requestFitSystemWindows() {}
    fun getParentForAccessibility(): ViewParent? = getParent()
    fun canResolveLayoutDirection(): Boolean = true
    fun getLayoutDirection(): Int = View.LAYOUT_DIRECTION_LTR
}

interface ViewManager {
    fun addView(view: View, params: ViewGroup.LayoutParams?)
    fun updateViewLayout(view: View, params: ViewGroup.LayoutParams?)
    fun removeView(view: View)
}

abstract class ViewGroup : View, ViewParent, ViewManager {
    open class LayoutParams {
        companion object {
            @Deprecated("") const val FILL_PARENT = -1
            const val MATCH_PARENT = -1
            const val WRAP_CONTENT = -2
        }

        @JvmField
        var width: Int = WRAP_CONTENT

        @JvmField
        var height: Int = WRAP_CONTENT

        constructor(c: Context?, attrs: AttributeSet?) {
            if (attrs != null) ViewAttributes.applyLayoutParams(this, c, attrs)
        }

        constructor(width: Int, height: Int) {
            this.width = width
            this.height = height
        }

        constructor(source: LayoutParams?) {
            width = source?.width ?: WRAP_CONTENT
            height = source?.height ?: WRAP_CONTENT
        }

        open fun resolveLayoutDirection(layoutDirection: Int) {}

        override fun toString(): String = "LayoutParams(w=$width, h=$height)"
    }

    open class MarginLayoutParams : LayoutParams {
        @JvmField
        var leftMargin = 0

        @JvmField
        var topMargin = 0

        @JvmField
        var rightMargin = 0

        @JvmField
        var bottomMargin = 0

        private var startMargin = Int.MIN_VALUE
        private var endMargin = Int.MIN_VALUE

        constructor(c: Context?, attrs: AttributeSet?) : super(c, attrs)
        constructor(width: Int, height: Int) : super(width, height)
        constructor(source: MarginLayoutParams?) : super(source) {
            if (source != null) {
                leftMargin = source.leftMargin
                topMargin = source.topMargin
                rightMargin = source.rightMargin
                bottomMargin = source.bottomMargin
                startMargin = source.startMargin
                endMargin = source.endMargin
            }
        }

        constructor(source: LayoutParams?) : super(source) {
            if (source is MarginLayoutParams) {
                leftMargin = source.leftMargin
                topMargin = source.topMargin
                rightMargin = source.rightMargin
                bottomMargin = source.bottomMargin
            }
        }

        open fun setMargins(left: Int, top: Int, right: Int, bottom: Int) {
            leftMargin = left
            topMargin = top
            rightMargin = right
            bottomMargin = bottom
        }

        open fun setMarginStart(start: Int) {
            startMargin = start
            leftMargin = start
        }

        open fun setMarginEnd(end: Int) {
            endMargin = end
            rightMargin = end
        }

        open fun getMarginStart(): Int = if (startMargin != Int.MIN_VALUE) startMargin else leftMargin
        open fun getMarginEnd(): Int = if (endMargin != Int.MIN_VALUE) endMargin else rightMargin
        open fun isMarginRelative(): Boolean = startMargin != Int.MIN_VALUE || endMargin != Int.MIN_VALUE
        open fun setLayoutDirection(layoutDirection: Int) {}
        open fun getLayoutDirection(): Int = View.LAYOUT_DIRECTION_LTR
    }

    companion object {
        const val FOCUS_BEFORE_DESCENDANTS = 0x20000
        const val FOCUS_AFTER_DESCENDANTS = 0x40000
        const val FOCUS_BLOCK_DESCENDANTS = 0x60000
        const val PERSISTENT_NO_CACHE = 0x0
        const val LAYOUT_MODE_CLIP_BOUNDS = 0
        const val LAYOUT_MODE_OPTICAL_BOUNDS = 1

        /** ViewGroup.getChildMeasureSpec (AOSP) */
        @JvmStatic
        fun getChildMeasureSpec(spec: Int, padding: Int, childDimension: Int): Int {
            val specMode = MeasureSpec.getMode(spec)
            val specSize = MeasureSpec.getSize(spec)
            val size = kotlin.math.max(0, specSize - padding)
            var resultSize = 0
            var resultMode = MeasureSpec.UNSPECIFIED
            if (childDimension >= 0) {
                resultSize = childDimension
                resultMode = MeasureSpec.EXACTLY
            } else if (childDimension == LayoutParams.MATCH_PARENT || childDimension == LayoutParams.WRAP_CONTENT) {
                resultSize = size
                resultMode = when (specMode) {
                    MeasureSpec.EXACTLY -> if (childDimension == LayoutParams.MATCH_PARENT) MeasureSpec.EXACTLY else MeasureSpec.AT_MOST
                    MeasureSpec.AT_MOST -> MeasureSpec.AT_MOST
                    else -> MeasureSpec.UNSPECIFIED
                }
            }
            return MeasureSpec.makeMeasureSpec(resultSize, resultMode)
        }
    }

    /** Children, observed by the renderer */
    val children = mutableStateListOf<View>()
    private var mDescendantFocusability = FOCUS_BEFORE_DESCENDANTS
    private var mClipChildren by androidx.compose.runtime.mutableStateOf(true)
    private var mClipToPadding by androidx.compose.runtime.mutableStateOf(true)
    private var mOnHierarchyChangeListener: OnHierarchyChangeListener? = null
    private var mFocusedChild: View? = null
    private var mLayoutTransition: Any? = null

    interface OnHierarchyChangeListener {
        fun onChildViewAdded(parent: View, child: View)
        fun onChildViewRemoved(parent: View, child: View)
    }

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        if (attrs != null) com.lagradost.desktop.runtime.ui.ViewAttributes.applyViewGroupAttributes(this, attrs)
    }
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        if (attrs != null) com.lagradost.desktop.runtime.ui.ViewAttributes.applyViewGroupAttributes(this, attrs)
    }
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        if (attrs != null) com.lagradost.desktop.runtime.ui.ViewAttributes.applyViewGroupAttributes(this, attrs)
    }

    override fun isLayoutRequested(): Boolean = super<View>.isLayoutRequested()
    override fun getLayoutDirection(): Int = super<View>.getLayoutDirection()

    open fun getChildCount(): Int = children.size
    open fun getChildAt(index: Int): View? = children.getOrNull(index)
    open fun indexOfChild(child: View?): Int = children.indexOf(child)

    open fun addView(child: View?) {
        if (child != null) addView(child, -1)
    }
    open fun addView(child: View, index: Int) {
        val params = child.layoutParamsOrNull() ?: generateDefaultLayoutParams()
        addView(child, index, params)
    }

    open fun addView(child: View, width: Int, height: Int) {
        val params = generateDefaultLayoutParams()
        params.width = width
        params.height = height
        addView(child, -1, params)
    }

    override fun addView(view: View, params: LayoutParams?) = addView(view, -1, params)

    open fun addView(child: View, index: Int, params: LayoutParams?) {
        requestLayout()
        invalidate()
        addViewInner(child, index, params, false)
    }

    /** ViewGroup.addViewInner: adds, attaches when this group is attached, notifies */
    private fun addViewInner(child: View, index: Int, params: LayoutParams?, preventRequestLayout: Boolean) {
        if (child.getParent() != null) {
            throw IllegalStateException("The specified child already has a parent. You must call removeView() on the child's parent first.")
        }
        var lp = params ?: generateDefaultLayoutParams()
        if (!checkLayoutParams(lp)) lp = generateLayoutParams(lp)
        if (preventRequestLayout) child.setLayoutParamsSilently(lp) else child.setLayoutParams(lp)
        if (index < 0 || index > children.size) children.add(child) else children.add(index, child)
        child.mParent = this
        if (isAttachedToWindow()) child.dispatchAttachedToWindow()
        onViewAdded(child)
        mOnHierarchyChangeListener?.onChildViewAdded(this, child)
    }

    protected open fun addViewInLayout(child: View, index: Int, params: LayoutParams?): Boolean =
        addViewInLayout(child, index, params, false)

    protected open fun addViewInLayout(child: View, index: Int, params: LayoutParams?, preventRequestLayout: Boolean): Boolean {
        child.mParent = null
        addViewInner(child, index, params, preventRequestLayout)
        return true
    }

    /** Re-adds a detached child without attaching it again or requesting a layout */
    protected open fun attachViewToParent(child: View, index: Int, params: LayoutParams?) {
        var lp = params ?: generateDefaultLayoutParams()
        if (!checkLayoutParams(lp)) lp = generateLayoutParams(lp)
        child.setLayoutParamsSilently(lp)
        if (index < 0 || index > children.size) children.add(child) else children.add(index, child)
        child.mParent = this
    }

    /** Removes a child from the list only: it stays attached (RecyclerView scrap) */
    protected open fun detachViewFromParent(child: View) {
        val idx = children.indexOf(child)
        if (idx >= 0) detachViewFromParent(idx)
    }

    protected open fun detachViewFromParent(index: Int) {
        val child = children.removeAt(index)
        child.mParent = null
    }

    protected open fun detachViewsFromParent(start: Int, count: Int) {
        repeat(count) { if (start < children.size) detachViewFromParent(start) }
    }

    protected open fun detachAllViewsFromParent() {
        while (children.isNotEmpty()) detachViewFromParent(children.size - 1)
    }

    /** Finishes removing a detached child: detaches it from the window */
    protected open fun removeDetachedView(child: View, animate: Boolean) {
        if (mFocusedChild === child) mFocusedChild = null
        child.dispatchDetachedFromWindow()
        onViewRemoved(child)
        mOnHierarchyChangeListener?.onChildViewRemoved(this, child)
    }

    protected open fun cleanupLayoutState(child: View) = child.forceLayout()

    override fun updateViewLayout(view: View, params: LayoutParams?) {
        if (params != null) view.setLayoutParams(params)
    }

    override fun removeView(view: View) {
        val idx = children.indexOf(view)
        if (idx >= 0) removeViewAt(idx)
    }

    open fun removeViewInLayout(view: View) {
        val idx = children.indexOf(view)
        if (idx >= 0) removeViewInternal(idx)
    }

    open fun removeViewAt(index: Int) {
        removeViewInternal(index)
        requestLayout()
        invalidate()
    }

    private fun removeViewInternal(index: Int) {
        val child = children.removeAt(index)
        if (mFocusedChild === child) mFocusedChild = null
        child.dispatchDetachedFromWindow()
        child.mParent = null
        onViewRemoved(child)
        mOnHierarchyChangeListener?.onChildViewRemoved(this, child)
    }

    open fun removeViews(start: Int, count: Int) {
        repeat(count) { if (start < children.size) removeViewInternal(start) }
        requestLayout()
        invalidate()
    }

    open fun removeViewsInLayout(start: Int, count: Int) {
        repeat(count) { if (start < children.size) removeViewInternal(start) }
    }

    open fun removeAllViews() {
        removeAllViewsInLayout()
        requestLayout()
        invalidate()
    }

    open fun removeAllViewsInLayout() {
        while (children.isNotEmpty()) removeViewInternal(children.size - 1)
    }

    override fun dispatchAttachedToWindow() {
        if (isAttachedToWindow()) return
        super.dispatchAttachedToWindow()
        for (c in children.toList()) c.dispatchAttachedToWindow()
    }

    open fun onViewAdded(child: View) {}
    open fun onViewRemoved(child: View) {}
    open fun setOnHierarchyChangeListener(listener: OnHierarchyChangeListener?) {
        mOnHierarchyChangeListener = listener
    }

    protected open fun generateDefaultLayoutParams(): LayoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    open fun generateLayoutParams(attrs: AttributeSet?): LayoutParams = LayoutParams(getContext(), attrs)
    protected open fun generateLayoutParams(p: LayoutParams): LayoutParams = p
    protected open fun checkLayoutParams(p: LayoutParams?): Boolean = p != null

    override fun bringChildToFront(child: View?) {
        if (child == null) return
        if (children.remove(child)) {
            children.add(child)
            requestLayout()
            invalidate()
        }
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}

    override fun requestChildFocus(child: View?, focused: View?) {
        mFocusedChild = child
        (getParent() as? ViewGroup)?.requestChildFocus(this, focused)
    }

    override fun clearChildFocus(child: View?) {
        if (mFocusedChild === child) mFocusedChild = null
    }

    open fun getFocusedChild(): View? = mFocusedChild
    override fun findFocus(): View? {
        if (isFocused()) return this
        for (c in children) c.findFocus()?.let { return it }
        return null
    }

    override fun hasFocus(): Boolean = isFocused() || children.any { it.hasFocus() }

    override fun requestFocus(direction: Int, previouslyFocusedRect: android.graphics.Rect?): Boolean {
        return when (mDescendantFocusability) {
            FOCUS_BLOCK_DESCENDANTS -> super.requestFocus(direction, previouslyFocusedRect)
            FOCUS_BEFORE_DESCENDANTS -> if (isFocusable() && super.requestFocus(direction, previouslyFocusedRect)) true
            else children.any { it.getVisibility() == VISIBLE && it.requestFocus(direction, previouslyFocusedRect) }
            else -> children.any { it.getVisibility() == VISIBLE && it.requestFocus(direction, previouslyFocusedRect) }
                    || super.requestFocus(direction, previouslyFocusedRect)
        }
    }

    open fun getDescendantFocusability(): Int = mDescendantFocusability
    open fun setDescendantFocusability(focusability: Int) {
        mDescendantFocusability = focusability
    }

    open fun setClipChildren(clipChildren: Boolean) {
        mClipChildren = clipChildren
        invalidate()
    }

    open fun getClipChildren(): Boolean = mClipChildren
    open fun setClipToPadding(clipToPadding: Boolean) {
        mClipToPadding = clipToPadding
        invalidate()
    }

    open fun getClipToPadding(): Boolean = mClipToPadding

    /** NestedScrollingParent: the default forwards to the next parent. CoordinatorLayout consumes app-bar scroll. */
    open fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray) {
        (getParent() as? ViewGroup)?.onNestedPreScroll(target, dx, dy, consumed)
    }

    open fun onNestedScroll(target: View, dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int) {
        (getParent() as? ViewGroup)?.onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed)
    }

    open fun setMotionEventSplittingEnabled(split: Boolean) {}
    open fun setAddStatesFromChildren(addsStates: Boolean) {}
    open fun setLayoutTransition(transition: Any?) {
        mLayoutTransition = transition
    }

    open fun getLayoutTransition(): Any? = mLayoutTransition
    open fun setLayoutAnimation(controller: Any?) {}
    open fun setTouchscreenBlocksFocus(touchscreenBlocksFocus: Boolean) {}
    open fun setTransitionGroup(isTransitionGroup: Boolean) {}
    open fun shouldDelayChildPressedState(): Boolean = false
    open fun getLayoutMode(): Int = LAYOUT_MODE_CLIP_BOUNDS
    open fun setLayoutMode(layoutMode: Int) {}

    override fun findViewTraversal(id: Int): View? {
        if (id == getId()) return this
        for (c in children.toList()) {
            c.findViewTraversal(id)?.let { return it }
        }
        return null
    }

    override fun findViewWithTagTraversal(tag: Any?): View? {
        if (tag != null && tag == getTag()) return this
        for (c in children.toList()) {
            c.findViewWithTagTraversal(tag)?.let { return it }
        }
        return null
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val focused = mFocusedChild
        if (focused != null && focused.dispatchKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchDetachedFromWindow() {
        for (c in children.toList()) c.dispatchDetachedFromWindow()
        super.dispatchDetachedFromWindow()
    }

    override fun refreshDrawableState() {
        super.refreshDrawableState()
    }

    protected open fun measureChildren(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        for (c in children) if (c.getVisibility() != GONE) measureChild(c, widthMeasureSpec, heightMeasureSpec)
    }

    protected open fun measureChild(child: View, parentWidthMeasureSpec: Int, parentHeightMeasureSpec: Int) {
        val lp = child.layoutParamsOrNull() ?: generateDefaultLayoutParams()
        child.measure(
            getChildMeasureSpec(parentWidthMeasureSpec, getPaddingLeft() + getPaddingRight(), lp.width),
            getChildMeasureSpec(parentHeightMeasureSpec, getPaddingTop() + getPaddingBottom(), lp.height)
        )
    }

    protected open fun measureChildWithMargins(child: View, parentWidthMeasureSpec: Int, widthUsed: Int, parentHeightMeasureSpec: Int, heightUsed: Int) {
        val lp = child.layoutParamsOrNull() ?: generateDefaultLayoutParams()
        val m = lp as? MarginLayoutParams
        child.measure(
            getChildMeasureSpec(
                parentWidthMeasureSpec,
                getPaddingLeft() + getPaddingRight() + (m?.leftMargin ?: 0) + (m?.rightMargin ?: 0) + widthUsed, lp.width
            ),
            getChildMeasureSpec(
                parentHeightMeasureSpec,
                getPaddingTop() + getPaddingBottom() + (m?.topMargin ?: 0) + (m?.bottomMargin ?: 0) + heightUsed, lp.height
            )
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {}
}
