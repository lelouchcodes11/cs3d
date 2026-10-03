package androidx.appcompat.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuImpl
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MenuItemImpl
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.view.CollapsibleActionView
import com.lagradost.desktop.runtime.ui.ViewAttributes

/**
 * AppCompat Toolbar: navigation button, title/subtitle, the XML's own children, then the menu's
 * action items and an overflow button at the end. A collapsible action view (search) expands in place
 * with a collapse button. Geometry follows Widget.AppCompat.Toolbar (56dp, 16dp/72dp content insets,
 * 56dp navigation button, 48dp action buttons).
 */
open class Toolbar : ViewGroup {
    private var mNavButtonView: ImageButton? = null
    private var mCollapseButtonView: ImageButton? = null
    private var mTitleTextView: TextView? = null
    private var mSubtitleTextView: TextView? = null
    private val mMenuViews = ArrayList<View>()
    private var mMenu: MenuImpl? = null
    private var mExpandedItem: MenuItemImpl? = null
    private var mExpandedView: View? = null

    private var mTitleText: CharSequence? = null
    private var mSubtitleText: CharSequence? = null
    private var mTitleTextColor: ColorStateList? = null
    private var mSubtitleTextColor: ColorStateList? = null
    private var mNavIconTint: ColorStateList? = null
    private var mNavContentDescription: CharSequence? = null
    private var mContentInsetStart = dp(16f)
    private var mContentInsetEnd = 0
    private var mContentInsetStartWithNavigation = dp(72f)
    private var mOnMenuItemClickListener: OnMenuItemClickListener? = null
    private var mMenuRebuildPosted = false
    private var mPopupTheme = 0

    fun interface OnMenuItemClickListener {
        fun onMenuItemClick(item: MenuItem): Boolean
    }

    open class LayoutParams : ViewGroup.MarginLayoutParams {
        @JvmField var gravity: Int = Gravity.START

        constructor(c: Context, attrs: AttributeSet?) : super(c, attrs)
        constructor(width: Int, height: Int) : super(width, height)
        constructor(width: Int, height: Int, gravity: Int) : super(width, height) {
            this.gravity = gravity
        }
        constructor(gravity: Int) : this(WRAP_CONTENT, MATCH_PARENT, gravity)
        constructor(source: ViewGroup.LayoutParams) : super(source) {
            if (source is LayoutParams) gravity = source.gravity
        }
    }

    constructor(context: Context?) : super(context) {
        init(null)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(attrs)
    }

    private fun dp(v: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getContext().getResources().getDisplayMetrics()).toInt()

    private fun init(attrs: AttributeSet?) {
        setMinimumHeight(dp(56f))
        if (attrs == null) return
        val r = ViewAttributes.reader(this, attrs)
        r.dim("contentInsetStart")?.let { mContentInsetStart = it }
        r.dim("contentInsetEnd")?.let { mContentInsetEnd = it }
        r.dim("contentInsetStartWithNavigation")?.let { mContentInsetStartWithNavigation = it }
        r.dim("minHeight")?.let { setMinimumHeight(it) }
        mTitleTextColor = r.colorStateList("titleTextColor")
        mSubtitleTextColor = r.colorStateList("subtitleTextColor")
        mNavIconTint = r.colorStateList("navigationIconTint")
        mPopupTheme = r.resourceId("popupTheme")
        r.text("navigationContentDescription")?.let { mNavContentDescription = it }
        val menuRes = r.resourceId("menu")
        val title = r.text("title")
        val subtitle = r.text("subtitle")
        val navIcon = r.drawable("navigationIcon")
        if (!title.isNullOrEmpty()) setTitle(title)
        if (!subtitle.isNullOrEmpty()) setSubtitle(subtitle)
        if (navIcon != null) setNavigationIcon(navIcon)
        if (menuRes != 0) inflateMenu(menuRes)
    }

    // ------------------------------------------------------------------ title

    open fun setTitle(title: CharSequence?) {
        mTitleText = title
        if (!title.isNullOrEmpty()) {
            val tv = mTitleTextView ?: TextView(getContext()).also {
                it.setSingleLine(true)
                it.setEllipsize(android.text.TextUtils.TruncateAt.END)
                it.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                it.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL))
                mTitleTextColor?.let { c -> it.setTextColor(c) }
                mTitleTextView = it
                addSystemView(it)
            }
            tv.setText(title)
        } else mTitleTextView?.let {
            removeView(it)
            mTitleTextView = null
        }
    }

    open fun setTitle(resId: Int) = setTitle(getContext().getText(resId))
    open fun getTitle(): CharSequence? = mTitleText

    open fun setSubtitle(subtitle: CharSequence?) {
        mSubtitleText = subtitle
        if (!subtitle.isNullOrEmpty()) {
            val tv = mSubtitleTextView ?: TextView(getContext()).also {
                it.setSingleLine(true)
                it.setEllipsize(android.text.TextUtils.TruncateAt.END)
                it.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                mSubtitleTextColor?.let { c -> it.setTextColor(c) }
                mSubtitleTextView = it
                addSystemView(it)
            }
            tv.setText(subtitle)
        } else mSubtitleTextView?.let {
            removeView(it)
            mSubtitleTextView = null
        }
    }

    open fun setSubtitle(resId: Int) = setSubtitle(getContext().getText(resId))
    open fun getSubtitle(): CharSequence? = mSubtitleText

    open fun setTitleTextColor(color: Int) = setTitleTextColor(ColorStateList.valueOf(color))
    open fun setTitleTextColor(color: ColorStateList) {
        mTitleTextColor = color
        mTitleTextView?.setTextColor(color)
    }

    open fun setSubtitleTextColor(color: Int) = setSubtitleTextColor(ColorStateList.valueOf(color))
    open fun setSubtitleTextColor(color: ColorStateList) {
        mSubtitleTextColor = color
        mSubtitleTextView?.setTextColor(color)
    }

    open fun setTitleTextAppearance(context: Context, resId: Int) {
        mTitleTextView?.setTextAppearance(resId)
    }

    open fun setSubtitleTextAppearance(context: Context, resId: Int) {
        mSubtitleTextView?.setTextAppearance(resId)
    }

    // ------------------------------------------------------------------ navigation

    private fun ensureNavButton(): ImageButton = mNavButtonView ?: ImageButton(getContext()).also {
        it.setBackground(null)
        it.setScaleType(ImageView.ScaleType.CENTER)
        it.setContentDescription(mNavContentDescription)
        mNavButtonView = it
        addSystemView(it)
    }

    open fun setNavigationIcon(icon: Drawable?) {
        if (icon != null) {
            val button = ensureNavButton()
            val d = icon.mutate()
            mNavIconTint?.let { d.setTintList(it) }
            button.setImageDrawable(d)
            button.setVisibility(if (mExpandedItem == null) VISIBLE else GONE)
        } else mNavButtonView?.let {
            it.setImageDrawable(null)
            it.setVisibility(GONE)
        }
        requestLayout()
    }

    open fun setNavigationIcon(resId: Int) = setNavigationIcon(if (resId != 0) getContext().getDrawable(resId) else null)
    open fun getNavigationIcon(): Drawable? = mNavButtonView?.getDrawable()

    open fun setNavigationIconTint(tint: Int) {
        mNavIconTint = ColorStateList.valueOf(tint)
        getNavigationIcon()?.setTintList(mNavIconTint)
    }

    open fun setNavigationOnClickListener(listener: OnClickListener?) {
        ensureNavButton().setOnClickListener(listener)
    }

    open fun setNavigationContentDescription(description: CharSequence?) {
        mNavContentDescription = description
        mNavButtonView?.setContentDescription(description)
    }

    open fun setNavigationContentDescription(resId: Int) = setNavigationContentDescription(if (resId != 0) getContext().getText(resId) else null)
    open fun getNavigationContentDescription(): CharSequence? = mNavContentDescription

    open fun setLogo(drawable: Drawable?) {}
    open fun setLogo(resId: Int) {}

    open fun setContentInsetsRelative(contentInsetStart: Int, contentInsetEnd: Int) {
        mContentInsetStart = contentInsetStart
        mContentInsetEnd = contentInsetEnd
        requestLayout()
    }

    open fun setContentInsetsAbsolute(contentInsetLeft: Int, contentInsetRight: Int) = setContentInsetsRelative(contentInsetLeft, contentInsetRight)
    open fun getContentInsetStart(): Int = mContentInsetStart
    open fun getContentInsetEnd(): Int = mContentInsetEnd
    open fun setContentInsetStartWithNavigation(insetStartWithNavigation: Int) {
        mContentInsetStartWithNavigation = insetStartWithNavigation
        requestLayout()
    }

    open fun getContentInsetStartWithNavigation(): Int = mContentInsetStartWithNavigation
    open fun setPopupTheme(resId: Int) {
        mPopupTheme = resId
    }

    open fun getPopupTheme(): Int = mPopupTheme

    // ------------------------------------------------------------------ menu

    open fun getMenu(): Menu = mMenu ?: MenuImpl(getContext()).also { menu ->
        mMenu = menu
        menu.onChanged = { scheduleMenuRebuild() }
    }

    open fun inflateMenu(resId: Int) {
        MenuInflater(getContext()).inflate(resId, getMenu())
        rebuildMenuViews()
    }

    open fun setOnMenuItemClickListener(listener: OnMenuItemClickListener?) {
        mOnMenuItemClickListener = listener
    }

    open fun hasExpandedActionView(): Boolean = mExpandedItem != null
    open fun collapseActionView() {
        mExpandedItem?.collapseActionView()
    }

    open fun showOverflowMenu(): Boolean = false
    open fun hideOverflowMenu(): Boolean = false
    open fun isOverflowMenuShowing(): Boolean = false
    open fun dismissPopupMenus() {}

    private fun scheduleMenuRebuild() {
        if (mMenuRebuildPosted) return
        mMenuRebuildPosted = true
        post {
            mMenuRebuildPosted = false
            rebuildMenuViews()
        }
    }

    /** Menu presenter: action buttons for shown items, the expanded action view, the overflow button */
    private fun rebuildMenuViews() {
        val menu = mMenu ?: return
        for (v in mMenuViews) removeView(v)
        mMenuViews.clear()

        val items = menu.visibleItems()
        val expanded = items.firstOrNull { it.isActionViewExpanded() }
        if (expanded !== mExpandedItem) onExpandedChanged(mExpandedItem, expanded)

        val overflow = ArrayList<MenuItemImpl>()
        for (item in items) {
            if (item === expanded) continue
            val flags = item.getShowAsAction()
            val shown = flags and (MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_IF_ROOM) != 0
            if (!shown) {
                overflow.add(item)
                continue
            }
            val actionView = item.getActionView()
            val view = if (actionView != null && flags and MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW == 0) {
                (actionView.getParent() as? ViewGroup)?.removeView(actionView)
                actionView
            } else actionButton(item)
            mMenuViews.add(view)
        }
        if (overflow.isNotEmpty()) mMenuViews.add(overflowButton(overflow))
        for (v in mMenuViews) addSystemView(v)
        requestLayout()
    }

    private fun actionButton(item: MenuItemImpl): View {
        val icon = item.getIcon()
        val view: View = if (icon != null) ImageButton(getContext()).also {
            it.setBackground(null)
            it.setScaleType(ImageView.ScaleType.CENTER)
            it.setImageDrawable(icon)
            it.setContentDescription(item.getTitle())
            it.setMinimumWidth(dp(48f))
            it.setPadding(dp(12f), dp(12f), dp(12f), dp(12f))
        } else TextView(getContext()).also {
            it.setText(item.getTitle())
            it.setAllCaps(true)
            it.setGravity(Gravity.CENTER)
            it.setPadding(dp(12f), 0, dp(12f), 0)
        }
        view.setEnabled(item.isEnabled())
        view.setOnClickListener { performItemAction(item) }
        return view
    }

    private fun overflowButton(items: List<MenuItemImpl>): View {
        val id = getContext().getResources().getIdentifier("abc_ic_menu_overflow_material", "drawable", null)
        val button = ImageButton(getContext())
        button.setBackground(null)
        button.setScaleType(ImageView.ScaleType.CENTER)
        if (id != 0) button.setImageDrawable(getContext().getDrawable(id)?.mutate()?.also { d -> mNavIconTint?.let { d.setTintList(it) } })
        button.setMinimumWidth(dp(36f))
        button.setContentDescription("More options")
        button.setOnClickListener {
            val popup = PopupMenu(getContext(), button)
            for (item in items) {
                popup.menu.add(item.getGroupId(), item.getItemId(), item.getOrder(), item.getTitle())
                    .setEnabled(item.isEnabled())
                    .setCheckable(item.isCheckable())
                    .setChecked(item.isChecked())
            }
            popup.setOnMenuItemClickListener { clicked ->
                items.firstOrNull { it.getItemId() == clicked.getItemId() && it.getTitle() == clicked.getTitle() }?.let { performItemAction(it) }
                true
            }
            popup.show()
        }
        return button
    }

    /** MenuBuilder.performItemAction: the item's listener, the toolbar's listener, the intent, then expand */
    private fun performItemAction(item: MenuItemImpl) {
        if (!item.isEnabled()) return
        var handled = item.invokeAction()
        if (!handled) handled = mOnMenuItemClickListener?.onMenuItemClick(item) == true
        if (item.getActionView() != null && item.getShowAsAction() and MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW != 0) {
            item.expandActionView()
        }
    }

    private fun onExpandedChanged(old: MenuItemImpl?, new: MenuItemImpl?) {
        mExpandedView?.let { view ->
            removeView(view)
            (view as? CollapsibleActionView)?.onActionViewCollapsed()
        }
        mExpandedView = null
        mExpandedItem = new
        val collapsed = new == null
        mTitleTextView?.setVisibility(if (collapsed) VISIBLE else GONE)
        mSubtitleTextView?.setVisibility(if (collapsed) VISIBLE else GONE)
        mNavButtonView?.let { it.setVisibility(if (collapsed && it.getDrawable() != null) VISIBLE else GONE) }
        for (i in 0 until getChildCount()) {
            val c = getChildAt(i) ?: continue
            if (!isSystemView(c)) c.setVisibility(if (collapsed) VISIBLE else GONE)
        }
        if (new == null) {
            mCollapseButtonView?.setVisibility(GONE)
            requestLayout()
            return
        }
        val collapse = mCollapseButtonView ?: ImageButton(getContext()).also { button ->
            button.setBackground(null)
            button.setScaleType(ImageView.ScaleType.CENTER)
            val id = getContext().getResources().getIdentifier("abc_ic_ab_back_material", "drawable", null)
            if (id != 0) button.setImageDrawable(getContext().getDrawable(id)?.mutate()?.also { d -> mNavIconTint?.let { d.setTintList(it) } })
            button.setContentDescription("Collapse")
            button.setOnClickListener { collapseActionView() }
            mCollapseButtonView = button
            addSystemView(button)
        }
        collapse.setVisibility(VISIBLE)
        val view = new.getActionView() ?: return
        (view.getParent() as? ViewGroup)?.removeView(view)
        mExpandedView = view
        addSystemView(view)
        (view as? CollapsibleActionView)?.onActionViewExpanded()
        requestLayout()
    }

    // ------------------------------------------------------------------ layout

    private val systemViews = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<View, Boolean>())

    private fun addSystemView(v: View) {
        systemViews.add(v)
        addView(v, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    override fun removeView(view: View) {
        systemViews.remove(view)
        super.removeView(view)
    }

    private fun isSystemView(v: View): Boolean = v in systemViews

    private fun shown(v: View?): Boolean = v != null && v.getParent() === this && v.getVisibility() != GONE

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val minH = getMinimumHeight()
        val contentH = maxOf(0, when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec) - getPaddingTop() - getPaddingBottom()
            else -> minH
        })
        val squareSpec = { w: Int -> MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY) }
        val fullH = MeasureSpec.makeMeasureSpec(contentH, MeasureSpec.EXACTLY)
        val totalW = MeasureSpec.getSize(widthMeasureSpec)
        var used = getPaddingLeft() + getPaddingRight()

        val startButton = if (shown(mCollapseButtonView)) mCollapseButtonView else if (shown(mNavButtonView)) mNavButtonView else null
        if (shown(mCollapseButtonView) && startButton !== mCollapseButtonView) mCollapseButtonView?.measure(squareSpec(0), squareSpec(0))
        startButton?.let {
            it.measure(squareSpec(dp(56f)), fullH)
            used += it.getMeasuredWidth()
        }
        used = maxOf(used, getPaddingLeft() + getPaddingRight() + currentInsetStart(startButton != null))

        var maxChildH = 0
        for (v in mMenuViews) {
            if (!shown(v)) continue
            v.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), fullH)
            used += v.getMeasuredWidth()
        }
        used += mContentInsetEnd

        val remaining = { maxOf(0, totalW - used) }
        mExpandedView?.takeIf { shown(it) }?.let {
            it.measure(MeasureSpec.makeMeasureSpec(remaining(), MeasureSpec.EXACTLY), fullH)
            used += it.getMeasuredWidth()
        }
        var titleW = 0
        var titleH = 0
        for (tv in listOf(mTitleTextView, mSubtitleTextView)) {
            if (!shown(tv)) continue
            tv!!.measure(MeasureSpec.makeMeasureSpec(remaining(), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(contentH, MeasureSpec.AT_MOST))
            titleW = maxOf(titleW, tv.getMeasuredWidth())
            titleH += tv.getMeasuredHeight()
        }
        used += titleW
        for (i in 0 until getChildCount()) {
            val c = getChildAt(i) ?: continue
            if (isSystemView(c) || c.getVisibility() == GONE) continue
            measureChildWithMargins(c, widthMeasureSpec, used, heightMeasureSpec, 0)
            val lp = c.getLayoutParams() as MarginLayoutParams
            used += c.getMeasuredWidth() + lp.leftMargin + lp.rightMargin
            maxChildH = maxOf(maxChildH, c.getMeasuredHeight() + lp.topMargin + lp.bottomMargin)
        }
        val h = maxOf(minH, maxOf(contentH, maxOf(titleH, maxChildH)) + getPaddingTop() + getPaddingBottom())
        setMeasuredDimension(
            resolveSizeAndState(maxOf(used, getSuggestedMinimumWidth()), widthMeasureSpec, 0),
            resolveSizeAndState(h, heightMeasureSpec, 0),
        )
    }

    private fun currentInsetStart(hasNav: Boolean): Int =
        if (hasNav) maxOf(mContentInsetStart, mContentInsetStartWithNavigation) else mContentInsetStart

    private fun layoutVertCentered(v: View, left: Int, top: Int, bottom: Int): Int {
        val y = top + (bottom - top - v.getMeasuredHeight()) / 2
        v.layout(left, y, left + v.getMeasuredWidth(), y + v.getMeasuredHeight())
        return left + v.getMeasuredWidth()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val top = getPaddingTop()
        val bottom = b - t - getPaddingBottom()
        var left = getPaddingLeft()
        var right = r - l - getPaddingRight() - mContentInsetEnd

        val startButton = if (shown(mCollapseButtonView)) mCollapseButtonView else if (shown(mNavButtonView)) mNavButtonView else null
        startButton?.let { left = layoutVertCentered(it, left, top, bottom) }
        left = maxOf(left, getPaddingLeft() + currentInsetStart(startButton != null))

        for (v in mMenuViews.asReversed()) {
            if (!shown(v)) continue
            right -= v.getMeasuredWidth()
            layoutVertCentered(v, right, top, bottom)
        }

        mExpandedView?.takeIf { shown(it) }?.let {
            layoutVertCentered(it, left, top, bottom)
            left += it.getMeasuredWidth()
        }

        val title = mTitleTextView?.takeIf { shown(it) }
        val subtitle = mSubtitleTextView?.takeIf { shown(it) }
        if (title != null || subtitle != null) {
            val total = (title?.getMeasuredHeight() ?: 0) + (subtitle?.getMeasuredHeight() ?: 0)
            var y = top + (bottom - top - total) / 2
            var widest = 0
            for (tv in listOfNotNull(title, subtitle)) {
                val w = minOf(tv.getMeasuredWidth(), maxOf(0, right - left))
                tv.layout(left, y, left + w, y + tv.getMeasuredHeight())
                y += tv.getMeasuredHeight()
                widest = maxOf(widest, w)
            }
            left += widest
        }

        for (i in 0 until getChildCount()) {
            val c = getChildAt(i) ?: continue
            if (isSystemView(c) || c.getVisibility() == GONE) continue
            val lp = c.getLayoutParams() as MarginLayoutParams
            val gravity = (lp as? LayoutParams)?.gravity ?: Gravity.START
            val w = c.getMeasuredWidth()
            val x = if (gravity and Gravity.HORIZONTAL_GRAVITY_MASK == Gravity.CENTER_HORIZONTAL) {
                maxOf(left + lp.leftMargin, (r - l - w) / 2)
            } else left + lp.leftMargin
            layoutVertCentered(c, x, top + lp.topMargin, bottom - lp.bottomMargin)
            left = x + w + lp.rightMargin
        }
    }

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    override fun generateLayoutParams(attrs: AttributeSet?): ViewGroup.LayoutParams = LayoutParams(getContext(), attrs)
    override fun generateLayoutParams(p: ViewGroup.LayoutParams): ViewGroup.LayoutParams = if (p is LayoutParams) p else LayoutParams(p)
    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean = p is LayoutParams
}
