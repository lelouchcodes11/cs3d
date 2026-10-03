package com.google.android.material.tabs

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.viewpager2.widget.ViewPager2
import com.lagradost.desktop.runtime.ui.DrawsItself
import com.lagradost.desktop.runtime.ui.ThemeBridge
import com.lagradost.desktop.runtime.ui.ViewAttributes
import com.lagradost.desktop.runtime.ui.WidgetDefaults
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Material TabLayout: a horizontally scrolling strip of tab views with a sliding selection indicator.
 * Reads the Material attributes (mode, gravity, indicator drawable/color/height/gravity, text colors
 * and appearance, paddings, min/max width) and selects a tab when its view is clicked, even when the
 * app replaced the view's click listener (TabView.performClick).
 */
open class TabLayout : HorizontalScrollView {
    interface OnTabSelectedListener {
        fun onTabSelected(tab: Tab?)
        fun onTabUnselected(tab: Tab?) {}
        fun onTabReselected(tab: Tab?) {}
    }

    interface BaseOnTabSelectedListener<T : Tab> : OnTabSelectedListener

    /** The view of a tab (Material's TabView): its label, with the icon above or inline */
    class TabView internal constructor(context: Context, internal val tab: Tab) : TextView(context) {
        override fun performClick(): Boolean {
            val handled = super.performClick()
            tab.select()
            return handled || true
        }

        fun getTab(): Tab = tab
    }

    class Tab internal constructor(val parent: TabLayout) {
        var text: CharSequence? = null
            set(value) {
                field = value
                parent.refreshTab(this)
            }
        var icon: Drawable? = null
            set(value) {
                field = value
                parent.refreshTab(this)
            }
        var customView: View? = null
        var tag: Any? = null
        var position: Int = -1
            internal set
        var contentDescription: CharSequence? = null
            set(value) {
                field = value
                view.setContentDescription(value)
            }
        val view: TabView = TabView(parent.getContext(), this)

        fun setText(text: CharSequence?): Tab = apply {
            this.text = text
            parent.refreshTab(this)
        }

        fun setText(resId: Int): Tab = setText(parent.getContext().resources.getText(resId))
        fun setIcon(icon: Drawable?): Tab = apply {
            this.icon = icon
            parent.refreshTab(this)
        }

        fun setIcon(resId: Int): Tab = setIcon(parent.getContext().getDrawable(resId))
        fun setCustomView(view: View?): Tab = apply { customView = view }
        fun setCustomView(layoutResId: Int): Tab = setCustomView(android.view.LayoutInflater.from(parent.getContext()).inflate(layoutResId, null))
        fun setTag(tag: Any?): Tab = apply { this.tag = tag }
        fun setContentDescription(text: CharSequence?): Tab = apply {
            contentDescription = text
            view.setContentDescription(text)
        }

        fun setContentDescription(resId: Int): Tab = setContentDescription(parent.getContext().resources.getText(resId))
        fun select() = parent.selectTab(this)
        fun isSelected(): Boolean = parent.selectedTabPosition == position
    }

    /** The row of tab views; draws the selection indicator under the labels */
    private inner class SlidingTabIndicator(context: Context) : LinearLayout(context), DrawsItself {
        var indicatorLeft = -1f
        var indicatorRight = -1f

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (indicatorLeft < 0f || indicatorRight <= indicatorLeft) return
            val d = indicator
            val h = when {
                tabIndicatorHeight >= 0 -> tabIndicatorHeight
                d.getIntrinsicHeight() > 0 -> d.getIntrinsicHeight()
                else -> WidgetDefaults.dp(2f)
            }
            if (h <= 0) return
            val height = getHeight()
            val (top, bottom) = when (tabIndicatorGravity) {
                INDICATOR_GRAVITY_CENTER -> (height - h) / 2 to (height + h) / 2
                INDICATOR_GRAVITY_TOP -> 0 to h
                INDICATOR_GRAVITY_STRETCH -> 0 to height
                else -> height - h to height
            }
            d.setBounds(indicatorLeft.roundToInt(), top, indicatorRight.roundToInt(), bottom)
            tabIndicatorColor?.let { d.setTint(it) }
            d.draw(canvas)
        }
    }

    private val strip = SlidingTabIndicator(getContext())
    private val tabs = ArrayList<Tab>()
    private val listeners = ArrayList<OnTabSelectedListener>()
    private var indicatorAnimator: ValueAnimator? = null

    var selectedTabPosition: Int = -1
        private set
    var tabMode: Int = MODE_FIXED
        set(value) {
            field = value
            applyModeAndGravity()
        }
    var tabGravity: Int = GRAVITY_FILL
        set(value) {
            field = value
            applyModeAndGravity()
        }
    private var tabIndicatorColor: Int? = null
    private var tabIndicatorHeight = -1
    private var tabIndicatorGravity = INDICATOR_GRAVITY_BOTTOM
    private var tabIndicatorFullWidth = true
    private var indicator: Drawable = GradientDrawable().apply { setColor(ThemeBridge.colorPrimary) }
    private var tabTextColors: ColorStateList? = null
    private var tabTextSize = 0f
    private var tabTextAllCaps = true
    private var tabPaddingStart = WidgetDefaults.dp(12f)
    private var tabPaddingEnd = WidgetDefaults.dp(12f)
    private var tabPaddingTop = 0
    private var tabPaddingBottom = 0
    private var tabMinWidth = -1
    private var tabMaxWidth = WidgetDefaults.dp(264f)
    private var tabBackground: Int = 0

    constructor(context: Context?) : super(context) {
        initTabs(null)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        initTabs(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs) {
        initTabs(attrs)
    }

    private fun initTabs(attrs: AttributeSet?) {
        setHorizontalScrollBarEnabled(false)
        setFillViewport(true)
        strip.setOrientation(LinearLayout.HORIZONTAL)
        addView(strip, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))

        val r = ViewAttributes.reader(this, attrs ?: ViewAttributes.emptyAttributes)
        r.text("tabMode")?.toString()?.let { tabModeValue(it) }?.let { tabMode = it }
        r.text("tabGravity")?.toString()?.let { tabGravityValue(it) }?.let { tabGravity = it }
        r.drawable("tabIndicator")?.let { indicator = it.mutate() }
        r.colorStateList("tabIndicatorColor")?.let { tabIndicatorColor = it.defaultColor }
        r.dim("tabIndicatorHeight")?.let { tabIndicatorHeight = it }
        r.text("tabIndicatorGravity")?.toString()?.let { g ->
            tabIndicatorGravity = when (g) {
                "bottom", "0" -> INDICATOR_GRAVITY_BOTTOM
                "center", "1" -> INDICATOR_GRAVITY_CENTER
                "top", "2" -> INDICATOR_GRAVITY_TOP
                "stretch", "3" -> INDICATOR_GRAVITY_STRETCH
                else -> INDICATOR_GRAVITY_BOTTOM
            }
        }
        r.bool("tabIndicatorFullWidth")?.let { tabIndicatorFullWidth = it }
        r.dim("tabPadding")?.let {
            tabPaddingStart = it
            tabPaddingEnd = it
            tabPaddingTop = it
            tabPaddingBottom = it
        }
        r.dim("tabPaddingStart")?.let { tabPaddingStart = it }
        r.dim("tabPaddingEnd")?.let { tabPaddingEnd = it }
        r.dim("tabPaddingTop")?.let { tabPaddingTop = it }
        r.dim("tabPaddingBottom")?.let { tabPaddingBottom = it }
        r.dim("tabMinWidth")?.let { tabMinWidth = it }
        r.dim("tabMaxWidth")?.let { tabMaxWidth = it }
        tabBackground = r.resourceId("tabBackground")
        val appearance = r.resourceId("tabTextAppearance")
        val base = r.colorStateList("tabTextColor")
        val selected = r.colorStateList("tabSelectedTextColor")
        if (appearance != 0) {
            val a = ViewAttributes.styleReader(this, appearance)
            a.dim("textSize")?.let { tabTextSize = it.toFloat() }
            a.bool("textAllCaps")?.let { tabTextAllCaps = it }
            if (base == null) a.colorStateList("textColor")?.let { tabTextColors = it }
        }
        if (base != null) tabTextColors = base
        if (selected != null) {
            val normal = tabTextColors?.defaultColor ?: ThemeBridge.textColorSecondary
            tabTextColors = ColorStateList(
                arrayOf(intArrayOf(View.STATE_SELECTED), intArrayOf()),
                intArrayOf(selected.defaultColor, normal),
            )
        }
        applyModeAndGravity()
    }

    private fun tabModeValue(v: String): Int? = when (v) {
        "scrollable", "0" -> MODE_SCROLLABLE
        "fixed", "1" -> MODE_FIXED
        "auto", "2" -> MODE_AUTO
        else -> null
    }

    private fun tabGravityValue(v: String): Int? = when (v) {
        "fill", "0" -> GRAVITY_FILL
        "center", "1" -> GRAVITY_CENTER
        "start", "2" -> GRAVITY_START
        else -> null
    }

    private fun scrollable() = tabMode == MODE_SCROLLABLE || tabMode == MODE_AUTO

    private fun applyModeAndGravity() {
        // before the fields exist (setters run from the constructor)
        @Suppress("SENSELESS_COMPARISON")
        if (strip == null || tabs == null) return
        strip.setGravity(
            when {
                scrollable() && tabGravity == GRAVITY_START -> Gravity.START
                tabGravity == GRAVITY_FILL && !scrollable() -> Gravity.START
                else -> Gravity.CENTER_HORIZONTAL
            }
        )
        tabs.forEach { updateTabLayoutParams(it) }
    }

    private fun updateTabLayoutParams(tab: Tab) {
        val fill = !scrollable() && tabGravity == GRAVITY_FILL
        val lp = LinearLayout.LayoutParams(if (fill) 0 else LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
        if (fill) lp.weight = 1f
        tab.view.setLayoutParams(lp)
        val min = if (tabMinWidth >= 0) tabMinWidth else if (scrollable()) WidgetDefaults.dp(72f) else 0
        tab.view.setMinWidth(min)
        tab.view.setMaxWidth(tabMaxWidth)
    }

    val tabCount: Int get() = tabs.size

    fun newTab(): Tab = Tab(this).also { setupTabView(it) }

    private fun setupTabView(tab: Tab) {
        tab.view.apply {
            setGravity(Gravity.CENTER)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, if (tabTextSize > 0f) tabTextSize else 14f * getResources().getDisplayMetrics().scaledDensity)
            setAllCaps(tabTextAllCaps)
            setMaxLines(2)
            setEllipsize(TextUtils.TruncateAt.END)
            setPaddingRelative(tabPaddingStart, tabPaddingTop, tabPaddingEnd, tabPaddingBottom)
            setTextColor(tabTextColors ?: ColorStateList(
                arrayOf(intArrayOf(View.STATE_SELECTED), intArrayOf()),
                intArrayOf(ThemeBridge.colorPrimary, ThemeBridge.textColorSecondary),
            ))
            if (tabBackground != 0) runCatching { setBackgroundResource(tabBackground) }
            setClickable(true)
            setFocusable(true)
        }
    }

    fun addTab(tab: Tab) = addTab(tab, tabs.isEmpty())
    fun addTab(tab: Tab, position: Int) = addTab(tab, position, tabs.isEmpty())

    fun addTab(tab: Tab, selected: Boolean) = addTab(tab, tabs.size, selected)

    fun addTab(tab: Tab, position: Int, selected: Boolean) {
        tabs.add(position, tab)
        tabs.forEachIndexed { i, t -> t.position = i }
        updateTabLayoutParams(tab)
        strip.addView(tab.view, position)
        refreshTab(tab)
        if (selected) selectTab(tab)
    }

    fun removeTab(tab: Tab) = removeTabAt(tab.position)

    fun removeTabAt(position: Int) {
        val tab = tabs.getOrNull(position) ?: return
        tabs.removeAt(position)
        strip.removeView(tab.view)
        tabs.forEachIndexed { i, t -> t.position = i }
        if (selectedTabPosition == position) {
            selectedTabPosition = -1
            tabs.getOrNull(max(0, position - 1))?.let { selectTab(it) }
        } else if (selectedTabPosition > position) selectedTabPosition--
    }

    fun removeAllTabs() {
        tabs.clear()
        strip.removeAllViews()
        selectedTabPosition = -1
        strip.indicatorLeft = -1f
        strip.indicatorRight = -1f
    }

    fun getTabAt(index: Int): Tab? = tabs.getOrNull(index)

    @JvmOverloads
    fun selectTab(tab: Tab?, updateIndicator: Boolean = true) {
        if (tab == null) {
            getTabAt(selectedTabPosition)?.let { previous -> listeners.toList().forEach { it.onTabUnselected(previous) } }
            selectedTabPosition = -1
            tabs.forEach { refreshTab(it) }
            return
        }
        val previous = getTabAt(selectedTabPosition)
        if (previous === tab) {
            listeners.toList().forEach { it.onTabReselected(tab) }
            return
        }
        if (previous != null) listeners.toList().forEach { it.onTabUnselected(previous) }
        selectedTabPosition = tab.position
        tabs.forEach { refreshTab(it) }
        if (updateIndicator) animateIndicatorTo(tab)
        listeners.toList().forEach { it.onTabSelected(tab) }
    }

    /** Scroll position (0..1 between tabs) while a pager is dragged */
    @JvmOverloads
    fun setScrollPosition(position: Int, positionOffset: Float, updateSelectedText: Boolean, updateIndicatorPosition: Boolean = true) {
        val tab = getTabAt(position) ?: return
        if (updateIndicatorPosition) {
            val (l, r) = indicatorBounds(tab)
            val next = getTabAt(position + 1)
            if (next != null && positionOffset > 0f) {
                val (nl, nr) = indicatorBounds(next)
                setIndicator(l + (nl - l) * positionOffset, r + (nr - r) * positionOffset)
            } else setIndicator(l, r)
        }
        if (updateSelectedText && selectedTabPosition != position) {
            selectedTabPosition = position
            tabs.forEach { refreshTab(it) }
        }
    }

    fun addOnTabSelectedListener(listener: OnTabSelectedListener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    @Deprecated("Use addOnTabSelectedListener")
    fun setOnTabSelectedListener(listener: OnTabSelectedListener?) {
        listeners.clear()
        if (listener != null) listeners.add(listener)
    }

    fun removeOnTabSelectedListener(listener: OnTabSelectedListener) {
        listeners.remove(listener)
    }

    fun clearOnTabSelectedListeners() = listeners.clear()

    fun setSelectedTabIndicatorColor(color: Int) {
        tabIndicatorColor = color
        strip.invalidate()
    }

    fun setSelectedTabIndicatorHeight(height: Int) {
        tabIndicatorHeight = height
        strip.invalidate()
    }

    fun setSelectedTabIndicator(drawable: Drawable?) {
        indicator = drawable?.mutate() ?: GradientDrawable()
        strip.invalidate()
    }

    fun setSelectedTabIndicatorGravity(gravity: Int) {
        tabIndicatorGravity = gravity
        strip.invalidate()
    }

    fun setTabIndicatorFullWidth(full: Boolean) {
        tabIndicatorFullWidth = full
        getTabAt(selectedTabPosition)?.let { moveIndicatorTo(it) }
    }

    fun setTabTextColors(colors: ColorStateList?) {
        tabTextColors = colors
        tabs.forEach { t -> colors?.let { t.view.setTextColor(it) } }
    }

    fun setTabTextColors(normalColor: Int, selectedColor: Int) = setTabTextColors(
        ColorStateList(arrayOf(intArrayOf(View.STATE_SELECTED), intArrayOf()), intArrayOf(selectedColor, normalColor))
    )

    fun getTabTextColors(): ColorStateList? = tabTextColors

    internal fun refreshTab(tab: Tab) {
        val label = tab.view
        label.setText(tab.text)
        label.setContentDescription(tab.contentDescription ?: tab.text)
        tab.icon?.let { icon ->
            icon.setBounds(0, 0, WidgetDefaults.dp(24f), WidgetDefaults.dp(24f))
            label.setCompoundDrawables(null, icon, null, null)
        } ?: label.setCompoundDrawables(null, null, null, null)
        label.setSelected(tab.position == selectedTabPosition)
    }

    // ---- indicator

    private fun indicatorBounds(tab: Tab): Pair<Float, Float> {
        val v = tab.view
        var left = v.getLeft().toFloat()
        var right = v.getRight().toFloat()
        if (!tabIndicatorFullWidth) {
            val text = v.getText()?.toString().orEmpty().let { if (tabTextAllCaps) it.uppercase() else it }
            val content = max(WidgetDefaults.dp(24f).toFloat(), v.getPaint().measureText(text))
            val center = (left + right) / 2
            left = center - content / 2
            right = center + content / 2
        }
        return left to right
    }

    private fun setIndicator(left: Float, right: Float) {
        strip.indicatorLeft = left
        strip.indicatorRight = right
        strip.invalidate()
    }

    private fun moveIndicatorTo(tab: Tab) {
        val (l, r) = indicatorBounds(tab)
        setIndicator(l, r)
    }

    private fun animateIndicatorTo(tab: Tab) {
        indicatorAnimator?.cancel()
        if (tab.view.getWidth() == 0 || strip.indicatorLeft < 0f) {
            // not laid out yet: placed on the next layout
            post { moveIndicatorTo(tab); scrollToTab(tab) }
            return
        }
        val fromL = strip.indicatorLeft
        val fromR = strip.indicatorRight
        val (toL, toR) = indicatorBounds(tab)
        indicatorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            setDuration(250)
            addUpdateListener {
                val f = it.getAnimatedValue() as Float
                setIndicator(fromL + (toL - fromL) * f, fromR + (toR - fromR) * f)
            }
            start()
        }
        scrollToTab(tab)
    }

    private fun scrollToTab(tab: Tab) {
        if (!scrollable()) return
        val v = tab.view
        val target = v.getLeft() + v.getWidth() / 2 - (getWidth() - getPaddingLeft() - getPaddingRight()) / 2
        smoothScrollTo(max(0, target), 0)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (indicatorAnimator?.isRunning() != true) getTabAt(selectedTabPosition)?.let { moveIndicatorTo(it) }
    }

    companion object {
        const val MODE_SCROLLABLE = 0
        const val MODE_FIXED = 1
        const val MODE_AUTO = 2
        const val GRAVITY_FILL = 0
        const val GRAVITY_CENTER = 1
        const val GRAVITY_START = 2
        const val INDICATOR_GRAVITY_BOTTOM = 0
        const val INDICATOR_GRAVITY_CENTER = 1
        const val INDICATOR_GRAVITY_TOP = 2
        const val INDICATOR_GRAVITY_STRETCH = 3
        const val TAB_LABEL_VISIBILITY_UNLABELED = 0
        const val TAB_LABEL_VISIBILITY_LABELED = 1
    }
}

/** Keeps a TabLayout and a ViewPager2 on the same page. */
class TabLayoutMediator(
    private val tabLayout: TabLayout,
    private val viewPager: ViewPager2,
    private val autoRefresh: Boolean = true,
    private val smoothScroll: Boolean = true,
    private val tabConfigurationStrategy: TabConfigurationStrategy,
) {
    fun interface TabConfigurationStrategy {
        fun onConfigureTab(tab: TabLayout.Tab, position: Int)
    }

    private var attached = false
    private val pageCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            if (tabLayout.selectedTabPosition != position) tabLayout.getTabAt(position)?.let { tabLayout.selectTab(it) }
        }
    }
    private val tabListener = object : TabLayout.OnTabSelectedListener {
        override fun onTabSelected(tab: TabLayout.Tab?) {
            if (tab != null && viewPager.currentItem != tab.position) viewPager.setCurrentItem(tab.position, smoothScroll)
        }
    }

    constructor(tabLayout: TabLayout, viewPager: ViewPager2, strategy: TabConfigurationStrategy) : this(tabLayout, viewPager, true, true, strategy)
    constructor(tabLayout: TabLayout, viewPager: ViewPager2, autoRefresh: Boolean, strategy: TabConfigurationStrategy) :
        this(tabLayout, viewPager, autoRefresh, true, strategy)

    fun isAttached(): Boolean = attached

    fun attach() {
        if (attached) return
        attached = true
        populate()
        viewPager.registerOnPageChangeCallback(pageCallback)
        tabLayout.addOnTabSelectedListener(tabListener)
    }

    fun detach() {
        if (!attached) return
        attached = false
        viewPager.unregisterOnPageChangeCallback(pageCallback)
        tabLayout.removeOnTabSelectedListener(tabListener)
    }

    private fun populate() {
        tabLayout.removeAllTabs()
        val count = viewPager.getAdapter()?.getItemCount() ?: 0
        for (i in 0 until count) {
            val tab = tabLayout.newTab()
            tabConfigurationStrategy.onConfigureTab(tab, i)
            tabLayout.addTab(tab, false)
        }
        if (count > 0) {
            val current = viewPager.currentItem.coerceIn(0, count - 1)
            tabLayout.getTabAt(current)?.let { tabLayout.selectTab(it) }
        }
    }
}
