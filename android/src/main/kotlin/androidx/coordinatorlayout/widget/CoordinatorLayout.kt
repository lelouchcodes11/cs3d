package androidx.coordinatorlayout.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import com.google.android.material.appbar.AppBarLayout
import kotlin.math.max

/**
 * FrameLayout plus the app-bar scrolling behavior: the scrolling child fills the space under the
 * AppBarLayout, and scroll flags on the bar's children collapse it as that child scrolls.
 */
open class CoordinatorLayout : FrameLayout {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val bar = findAppBar()
        val offset = bar?.getVerticalOffset() ?: 0
        val barH = if (bar != null && bar.getVisibility() != GONE) bar.getMeasuredHeight() else 0
        val contentH = max(0, getMeasuredHeight() - getPaddingTop() - getPaddingBottom() - barH - offset)
        val childW = MeasureSpec.makeMeasureSpec(max(0, getMeasuredWidth() - getPaddingLeft() - getPaddingRight()), MeasureSpec.EXACTLY)
        val childH = MeasureSpec.makeMeasureSpec(contentH, MeasureSpec.EXACTLY)
        for (i in 0 until getChildCount()) {
            val child = getChildAt(i) ?: continue
            if (child is AppBarLayout || child.getVisibility() == GONE || !isScrollingChild(child)) continue
            child.measure(childW, childH)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val bar = findAppBar()
        bar?.applyPendingExpanded()
        val offset = bar?.getVerticalOffset() ?: 0
        var contentTop = getPaddingTop()
        if (bar != null && bar.getVisibility() != GONE) {
            val h = bar.getMeasuredHeight()
            val barTop = getPaddingTop() + offset
            bar.layout(getPaddingLeft(), barTop, right - left - getPaddingRight(), barTop + h)
            contentTop = getPaddingTop() + h + offset
        }
        val parentBottom = bottom - top - getPaddingBottom()
        for (i in 0 until getChildCount()) {
            val child = getChildAt(i) ?: continue
            if (child is AppBarLayout || child.getVisibility() == GONE) continue
            if (!isScrollingChild(child)) continue
            child.layout(child.getLeft(), contentTop, child.getRight(), parentBottom)
        }
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray) {
        val bar = findAppBar()
        var leftY = dy
        if (bar != null && dy != 0) {
            val used = bar.consumePreScroll(dy)
            if (used != 0) {
                consumed[1] += used
                leftY -= used
            }
        }
        if (dx != 0 || leftY != 0) {
            val more = intArrayOf(0, 0)
            (getParent() as? ViewGroup)?.onNestedPreScroll(target, dx, leftY, more)
            consumed[0] += more[0]
            consumed[1] += more[1]
        }
    }

    override fun onNestedScroll(target: View, dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int) {
        val bar = findAppBar()
        var left = dyUnconsumed
        if (bar != null && dyUnconsumed != 0) {
            val used = bar.consumeUnconsumed(dyUnconsumed)
            left -= used
        }
        (getParent() as? ViewGroup)?.onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, left)
    }

    private fun findAppBar(): AppBarLayout? {
        for (i in 0 until getChildCount()) {
            val child = getChildAt(i)
            if (child is AppBarLayout) return child
        }
        return null
    }

    private fun isScrollingChild(child: View): Boolean {
        val lp = child.getLayoutParams()
        val behavior = (lp as? LayoutParams)?.behaviorName ?: return false
        return behavior.endsWith("ScrollingViewBehavior")
    }

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams = LayoutParams(getContext(), attrs)
    override fun generateLayoutParams(p: android.view.ViewGroup.LayoutParams): LayoutParams = LayoutParams(p)
    override fun generateDefaultLayoutParams(): LayoutParams = LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
    override fun checkLayoutParams(p: android.view.ViewGroup.LayoutParams?): Boolean = p is LayoutParams

    class LayoutParams : FrameLayout.LayoutParams {
        var behaviorName: String? = null

        constructor(width: Int, height: Int) : super(width, height)
        constructor(source: android.view.ViewGroup.LayoutParams) : super(source)
        constructor(c: Context?, attrs: AttributeSet?) : super(c, attrs) {
            val raw = attrs?.getAttributeValue(RES_AUTO, "layout_behavior") ?: attrs?.getAttributeValue(null, "layout_behavior")
            behaviorName = resolve(c, raw)
        }

        private fun resolve(c: Context?, raw: String?): String? {
            if (raw.isNullOrEmpty() || c == null) return raw
            if (!raw.startsWith("@")) return raw
            val id = c.resources.getIdentifier(raw.removePrefix("@").removePrefix("+"), null, null)
            if (id == 0) return raw
            return try {
                c.resources.getString(id)
            } catch (_: Exception) {
                raw
            }
        }

        companion object {
            private const val RES_AUTO = "http://schemas.android.com/apk/res-auto"
        }
    }
}
