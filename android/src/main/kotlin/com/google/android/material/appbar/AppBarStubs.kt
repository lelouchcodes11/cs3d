package com.google.android.material.appbar

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import com.lagradost.desktop.runtime.res.ResourceSupport
import kotlin.math.max

open class AppBarLayout : LinearLayout {
    fun interface OnOffsetChangedListener {
        fun onOffsetChanged(appBarLayout: AppBarLayout, verticalOffset: Int)
    }

    private val offsetListeners = ArrayList<OnOffsetChangedListener>()

    /** 0 expanded, down to -totalScrollRange collapsed. */
    private var offset = 0
    private var pendingExpanded: Boolean? = null
    private var animToken = 0

    constructor(context: Context?) : super(context) { setOrientation(VERTICAL) }
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) { setOrientation(VERTICAL) }
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { setOrientation(VERTICAL) }

    fun setExpanded(expanded: Boolean) = setExpanded(expanded, false)

    fun setExpanded(expanded: Boolean, animate: Boolean) {
        val range = getTotalScrollRange()
        if (getHeight() == 0 && getMeasuredHeight() == 0) {
            pendingExpanded = expanded
            return
        }
        pendingExpanded = null
        val target = if (expanded) 0 else -range
        if (animate && target != offset) animateOffset(target) else applyOffset(target)
    }

    /** Called from CoordinatorLayout.onLayout once children have heights. */
    internal fun applyPendingExpanded() {
        val want = pendingExpanded ?: return
        if (getTotalScrollRange() == 0 && getHeight() == 0 && getMeasuredHeight() == 0) return
        pendingExpanded = null
        applyOffset(if (want) 0 else -getTotalScrollRange(), request = false)
    }

    fun getVerticalOffset(): Int = offset

    /**
     * Downward content scroll collapses children flagged SCROLL. Upward scroll expands immediately
     * when a child is also enterAlways; otherwise the coordinator expands from unconsumed scroll.
     * Returns the dy consumed (positive means the content should scroll less downward).
     */
    fun consumePreScroll(dy: Int): Int {
        if (dy == 0 || !hasFlag(LayoutParams.SCROLL_FLAG_SCROLL)) return 0
        if (dy < 0 && !hasFlag(LayoutParams.SCROLL_FLAG_ENTER_ALWAYS)) return 0
        return moveOffset(offset - dy)
    }

    /** Upward scroll the child could not consume expands a bar that is not enterAlways. */
    fun consumeUnconsumed(dyUnconsumed: Int): Int {
        if (dyUnconsumed >= 0 || !hasFlag(LayoutParams.SCROLL_FLAG_SCROLL)) return 0
        if (hasFlag(LayoutParams.SCROLL_FLAG_ENTER_ALWAYS)) return 0
        return moveOffset(offset - dyUnconsumed)
    }

    private fun moveOffset(target: Int): Int {
        val before = offset
        applyOffset(target)
        return before - offset
    }

    private fun applyOffset(target: Int, request: Boolean = true) {
        val range = getTotalScrollRange()
        val next = target.coerceIn(-range, 0)
        if (next == offset) return
        offset = next
        animToken++
        for (l in offsetListeners.toList()) l.onOffsetChanged(this, offset)
        if (request) (getParent() as? View)?.requestLayout()
    }

    private fun animateOffset(target: Int) {
        val token = ++animToken
        val start = offset
        val t0 = System.nanoTime()
        val duration = 180_000_000L
        val tick = object : Runnable {
            override fun run() {
                if (token != animToken) return
                val t = ((System.nanoTime() - t0).toFloat() / duration).coerceIn(0f, 1f)
                val eased = t * t * (3f - 2f * t)
                applyOffset(start + ((target - start) * eased).toInt())
                if (t < 1f) postDelayed(this, 16) else applyOffset(target)
            }
        }
        post(tick)
    }

    private fun hasFlag(flag: Int): Boolean {
        for (i in 0 until getChildCount()) {
            val child = getChildAt(i) ?: continue
            val flags = (child.getLayoutParams() as? LayoutParams)?.scrollFlags ?: 0
            if (flags and flag != 0) return true
        }
        return false
    }

    fun addOnOffsetChangedListener(listener: OnOffsetChangedListener) {
        offsetListeners.add(listener)
    }

    fun removeOnOffsetChangedListener(listener: OnOffsetChangedListener) {
        offsetListeners.remove(listener)
    }

    /** Height of children with the scroll flag (exitUntilCollapsed keeps the minimum height). */
    fun getTotalScrollRange(): Int {
        var range = 0
        for (i in 0 until getChildCount()) {
            val child = getChildAt(i) ?: continue
            if (child.getVisibility() == GONE) continue
            val flags = (child.getLayoutParams() as? LayoutParams)?.scrollFlags ?: 0
            if (flags and LayoutParams.SCROLL_FLAG_SCROLL == 0) continue
            val h = when {
                child.getHeight() > 0 -> child.getHeight()
                child.getMeasuredHeight() > 0 -> child.getMeasuredHeight()
                else -> 0
            }
            val lp = child.getLayoutParams() as? MarginLayoutParams
            range += h + (lp?.topMargin ?: 0) + (lp?.bottomMargin ?: 0)
            if (flags and LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED != 0) range -= child.getMinimumHeight()
        }
        return max(0, range)
    }

    open class LayoutParams : LinearLayout.LayoutParams {
        companion object {
            const val SCROLL_FLAG_NO_SCROLL = 0
            const val SCROLL_FLAG_SCROLL = 1
            const val SCROLL_FLAG_EXIT_UNTIL_COLLAPSED = 2
            const val SCROLL_FLAG_ENTER_ALWAYS = 4
            const val SCROLL_FLAG_ENTER_ALWAYS_COLLAPSED = 8
            const val SCROLL_FLAG_SNAP = 16
            const val SCROLL_FLAG_SNAP_MARGINS = 32
        }

        var scrollFlags: Int = 0

        constructor(c: Context?, attrs: AttributeSet?) : super(c, attrs) {
            scrollFlags = readFlags(c, attrs)
        }

        constructor(width: Int, height: Int) : super(width, height)
        constructor(width: Int, height: Int, weight: Float) : super(width, height, weight)
        constructor(p: android.view.ViewGroup.LayoutParams) : super(p) {
            if (p is LayoutParams) scrollFlags = p.scrollFlags
        }

        constructor(source: android.view.ViewGroup.MarginLayoutParams) : super(source)
        constructor(source: LinearLayout.LayoutParams) : super(source) {
            if (source is LayoutParams) scrollFlags = source.scrollFlags
        }

        private fun readFlags(c: Context?, attrs: AttributeSet?): Int {
            if (c == null || attrs == null) return 0
            val tv = ResourceSupport.attr(c.resources, attrs, "layout_scrollFlags") ?: return 0
            if (tv.type == TypedValue.TYPE_INT_DEC || tv.type == TypedValue.TYPE_INT_HEX) return tv.data
            val text = tv.string?.toString() ?: return 0
            var flags = 0
            for (part in text.split('|')) {
                flags = flags or when (part.trim()) {
                    "scroll" -> SCROLL_FLAG_SCROLL
                    "exitUntilCollapsed" -> SCROLL_FLAG_EXIT_UNTIL_COLLAPSED
                    "enterAlways" -> SCROLL_FLAG_ENTER_ALWAYS
                    "enterAlwaysCollapsed" -> SCROLL_FLAG_ENTER_ALWAYS_COLLAPSED
                    "snap" -> SCROLL_FLAG_SNAP
                    "snapMargins" -> SCROLL_FLAG_SNAP_MARGINS
                    "noScroll" -> SCROLL_FLAG_NO_SCROLL
                    else -> 0
                }
            }
            return flags
        }
    }

    override fun generateDefaultLayoutParams(): LayoutParams = LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams = LayoutParams(getContext(), attrs)
    override fun generateLayoutParams(p: android.view.ViewGroup.LayoutParams): LayoutParams = LayoutParams(p)

    class ScrollingViewBehavior {
        companion object
    }
}

/** Toolbar with the Material type. Layout is the compat Toolbar until WP4's full toolbar pass. */
open class MaterialToolbar : Toolbar {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
}

open class CollapsingToolbarLayout : FrameLayout {
    private var titleView: TextView? = null
    private var expandedTitleColor = 0
    private var collapsedTitleColor = 0

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    fun setTitle(title: CharSequence?) {
        val view = titleView ?: TextView(getContext()).also {
            it.setLayoutParams(LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.BOTTOM))
            addView(it)
            titleView = it
        }
        view.setText(title)
        if (expandedTitleColor != 0) view.setTextColor(expandedTitleColor)
    }

    fun setExpandedTitleColor(color: Int) {
        expandedTitleColor = color
        titleView?.setTextColor(color)
    }

    fun setCollapsedTitleTextColor(color: Int) {
        collapsedTitleColor = color
    }
}
