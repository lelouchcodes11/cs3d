package com.google.android.material.navigation

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.badge.BadgeDrawable
import com.lagradost.desktop.runtime.ui.ThemeBridge
import com.lagradost.desktop.runtime.ui.WidgetDefaults

/**
 * Shared rail and bottom-bar implementation. Each menu item is a child view whose id is the item id,
 * so findViewById(R.id.navigation_home) reaches it.
 */
open class NavigationBarView : FrameLayout {
    fun interface OnItemSelectedListener {
        fun onNavigationItemSelected(item: MenuItem): Boolean
    }

    fun interface OnItemReselectedListener {
        fun onNavigationItemReselected(item: MenuItem)
    }

    val menu: android.view.MenuImpl = android.view.MenuImpl(getContext())
    protected val menuRow: LinearLayout
    private val badges = HashMap<Int, BadgeDrawable>()
    private var selectedListener: OnItemSelectedListener? = null
    private var reselectedListener: OnItemReselectedListener? = null
    private var currentId: Int = 0
    var selectedItemId: Int
        get() = currentId
        set(value) { selectItem(value) }
    var labelVisibilityMode: Int = LABEL_VISIBILITY_AUTO
        set(value) {
            field = value
            rebuild()
        }
    var itemIconTintList: ColorStateList? = null
    var itemTextColor: ColorStateList? = null
    var itemRippleColor: ColorStateList? = null
    var itemActiveIndicatorColor: ColorStateList? = null
    var itemSpacing: Int = 0
    var itemIconSize: Int = WidgetDefaults.dp(24f)
    private var horizontalItems = false

    constructor(context: Context?) : super(context) {
        menuRow = createRow()
    }
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        menuRow = createRow()
        readMenu(attrs)
    }
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        menuRow = createRow()
        readMenu(attrs)
    }

    protected fun setHorizontalItems(horizontal: Boolean) {
        horizontalItems = horizontal
        menuRow.setOrientation(if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL)
        val lp = menuRow.getLayoutParams() as LayoutParams
        lp.gravity = if (horizontal) Gravity.CENTER else Gravity.CENTER
        menuRow.setLayoutParams(lp)
        rebuild()
    }

    private fun createRow(): LinearLayout {
        val row = LinearLayout(getContext())
        row.setOrientation(LinearLayout.VERTICAL)
        row.setGravity(Gravity.CENTER)
        val lp = LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.CENTER
        addView(row, lp)
        return row
    }

    private fun readMenu(attrs: AttributeSet?) {
        if (attrs == null) return
        val menuId = resourceAttr(attrs, "menu")
        val header = resourceAttr(attrs, "headerLayout")
        when (attrs.getAttributeValue(RES_AUTO, "labelVisibilityMode") ?: attrs.getAttributeValue(null, "labelVisibilityMode")) {
            "labeled", "1" -> labelVisibilityMode = LABEL_VISIBILITY_LABELED
            "unlabeled", "0" -> labelVisibilityMode = LABEL_VISIBILITY_UNLABELED
            "selected", "2" -> labelVisibilityMode = LABEL_VISIBILITY_SELECTED
            "auto", "-1" -> labelVisibilityMode = LABEL_VISIBILITY_AUTO
        }
        if (header != 0) {
            val headerView = android.view.LayoutInflater.from(getContext()).inflate(header, this, false)
            addView(headerView, 0)
        }
        if (menuId != 0) inflateMenu(menuId)
    }

    private fun resourceAttr(attrs: AttributeSet, name: String): Int {
        val fromApp = attrs.getAttributeResourceValue(RES_AUTO, name, 0)
        if (fromApp != 0) return fromApp
        return attrs.getAttributeResourceValue(null, name, 0)
    }

    fun inflateMenu(resId: Int) {
        menu.clear()
        android.view.MenuInflater(getContext()).inflate(resId, menu)
        if (currentId == 0) currentId = menu.visibleItems().firstOrNull()?.getItemId() ?: 0
        rebuild()
    }

    fun setOnItemSelectedListener(listener: OnItemSelectedListener?) {
        selectedListener = listener
    }

    fun setOnItemReselectedListener(listener: OnItemReselectedListener?) {
        reselectedListener = listener
    }

    /** Updates the highlight without calling the selection listener. Used by navigation setup. */
    fun setCheckedItem(itemId: Int) {
        if (menu.findItem(itemId) == null) return
        currentId = itemId
        menu.visibleItems().forEach { it.setChecked(it.getItemId() == itemId) }
        refreshStyles()
    }

    private fun selectItem(itemId: Int) {
        val item = menu.findItem(itemId) ?: return
        if (itemId == currentId) {
            reselectedListener?.onNavigationItemReselected(item)
            return
        }
        if (selectedListener?.onNavigationItemSelected(item) == false) return
        currentId = itemId
        menu.visibleItems().forEach { it.setChecked(it.getItemId() == itemId) }
        refreshStyles()
    }

    fun getOrCreateBadge(menuItemId: Int): BadgeDrawable = badges.getOrPut(menuItemId) { BadgeDrawable() }

    fun removeBadge(menuItemId: Int) {
        badges.remove(menuItemId)
    }

    fun getBadge(menuItemId: Int): BadgeDrawable? = badges[menuItemId]

    private fun labelsVisible(selected: Boolean): Boolean = when (labelVisibilityMode) {
        LABEL_VISIBILITY_LABELED -> true
        LABEL_VISIBILITY_SELECTED -> selected
        LABEL_VISIBILITY_UNLABELED -> false
        else -> !horizontalItems
    }

    private fun rebuild() {
        menuRow.removeAllViews()
        for (item in menu.visibleItems()) {
            val selected = item.getItemId() == currentId
            val cell = NavigationBarItemView(getContext())
            cell.setId(item.getItemId())
            cell.setOrientation(LinearLayout.VERTICAL)
            cell.setGravity(Gravity.CENTER)
            val pad = WidgetDefaults.dp(8f)
            cell.setPadding(pad, pad, pad, pad)
            cell.setClickable(true)
            cell.setFocusable(true)
            val icon = ImageView(getContext())
            item.getIcon()?.let { icon.setImageDrawable(it) }
            cell.addView(icon, LinearLayout.LayoutParams(itemIconSize, itemIconSize))
            val label = TextView(getContext())
            label.setText(item.getTitle())
            label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
            label.setGravity(Gravity.CENTER)
            label.setVisibility(if (labelsVisible(selected)) View.VISIBLE else View.GONE)
            cell.addView(label, LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
            cell.setOnClickListener { selectedItemId = item.getItemId() }
            val lp = if (horizontalItems) {
                LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            } else {
                LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            if (!horizontalItems && itemSpacing > 0) lp.bottomMargin = itemSpacing
            menuRow.addView(cell, lp)
        }
        refreshStyles()
    }

    private fun refreshStyles() {
        for (i in 0 until menuRow.getChildCount()) {
            val cell = menuRow.getChildAt(i) as? LinearLayout ?: continue
            val selected = cell.getId() == currentId
            cell.setSelected(selected)
            val icon = cell.getChildAt(0) as? ImageView
            val label = cell.getChildAt(1) as? TextView
            val color = if (selected) ThemeBridge.colorPrimary else ThemeBridge.textColorSecondary
            icon?.setColorFilter(color)
            label?.setTextColor(color)
            label?.setVisibility(if (labelsVisible(selected)) View.VISIBLE else View.GONE)
            if (selected && itemActiveIndicatorColor != null) {
                cell.setBackgroundColor(itemActiveIndicatorColor!!.getDefaultColor())
            } else {
                cell.setBackground(null)
            }
        }
    }

    fun expand() {
        labelVisibilityMode = LABEL_VISIBILITY_LABELED
    }

    fun collapse() {
        labelVisibilityMode = LABEL_VISIBILITY_UNLABELED
    }

    companion object {
        const val LABEL_VISIBILITY_AUTO = -1
        const val LABEL_VISIBILITY_SELECTED = 0
        const val LABEL_VISIBILITY_LABELED = 1
        const val LABEL_VISIBILITY_UNLABELED = 2
        private const val RES_AUTO = "http://schemas.android.com/apk/res-auto"
    }
}
