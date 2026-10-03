@file:JvmName("MaterialMiscKt")

package com.google.android.material.button

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatButton

open class MaterialButton : AppCompatButton {
    private var mIcon: Drawable? = null
    private var mIconTint: ColorStateList? = null
    private var mIconSize = 0
    private var mIconGravity = ICON_GRAVITY_START
    private var cornerRadius = 0
    private var strokeWidth = 0
    private var strokeColor: ColorStateList? = null
    private var checkable = false

    constructor(context: Context?) : super(context) {
        initButton(null)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        initButton(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initButton(attrs)
    }

    /** Material's button attributes (element, style and the theme's materialButtonStyle) */
    private fun initButton(attrs: AttributeSet?) {
        val r = com.lagradost.desktop.runtime.ui.ViewAttributes.reader(this, attrs ?: com.lagradost.desktop.runtime.ui.ViewAttributes.emptyAttributes)
        r.colorStateList("iconTint")?.let { mIconTint = it }
        r.dim("iconSize")?.let { mIconSize = it }
        r.text("iconGravity")?.toString()?.let { g ->
            mIconGravity = when (g) {
                "start" -> ICON_GRAVITY_START
                "textStart" -> ICON_GRAVITY_TEXT_START
                "end" -> ICON_GRAVITY_END
                "textEnd" -> ICON_GRAVITY_TEXT_END
                "top" -> ICON_GRAVITY_TOP
                "textTop" -> ICON_GRAVITY_TEXT_TOP
                else -> g.toIntOrNull() ?: ICON_GRAVITY_START
            }
        }
        r.dim("cornerRadius")?.let { cornerRadius = it }
        r.dim("strokeWidth")?.let { strokeWidth = it }
        r.colorStateList("strokeColor")?.let { strokeColor = it }
        r.bool("checkable")?.let { checkable = it }
        val icon = r.drawable("icon")
        if (icon != null) {
            setCompoundDrawablePadding(r.dim("iconPadding") ?: com.lagradost.desktop.runtime.ui.WidgetDefaults.dp(8f))
            setIcon(icon)
        } else {
            r.dim("iconPadding")?.let { setCompoundDrawablePadding(it) }
        }
    }

    /**
     * Widget.MaterialComponents.Button: 16dp x 4dp padding plus the 6dp vertical insets, 48dp tall
     * (36dp visible); the shape is drawn by the renderer
     */
    override fun applyWidgetDefaults() {
        val d = com.lagradost.desktop.runtime.ui.WidgetDefaults
        setBackground(null)
        setPadding(d.dp(16f), d.dp(10f), d.dp(16f), d.dp(10f))
        setMinimumHeight(d.dp(48f))
        setMinimumWidth(d.dp(64f))
        setAllCaps(true)
        setGravity(android.view.Gravity.CENTER)
    }

    /** The icon as a compound drawable on its gravity's side, sized by iconSize and tinted like Material */
    private fun updateIcon() {
        val icon = mIcon
        if (icon != null) {
            icon.setTintList(mIconTint ?: getTextColors())
            val w = if (mIconSize > 0) mIconSize else icon.getIntrinsicWidth().coerceAtLeast(0)
            val h = if (mIconSize > 0) mIconSize else icon.getIntrinsicHeight().coerceAtLeast(0)
            icon.setBounds(0, 0, w, h)
        }
        when (mIconGravity) {
            ICON_GRAVITY_TOP, ICON_GRAVITY_TEXT_TOP -> setCompoundDrawables(null, icon, null, null)
            ICON_GRAVITY_END, ICON_GRAVITY_TEXT_END -> setCompoundDrawables(null, null, icon, null)
            else -> setCompoundDrawables(icon, null, null, null)
        }
    }

    /**
     * Horizontal shift of the icon for textStart / textEnd gravity: the icon sits next to the centered
     * text (MaterialButton.updateIconPosition), or in the middle of an icon-only button
     */
    internal fun iconOffsetX(width: Int): Int {
        if (mIcon == null || (mIconGravity != ICON_GRAVITY_TEXT_START && mIconGravity != ICON_GRAVITY_TEXT_END)) return 0
        val raw = getText()?.toString() ?: ""
        val text = if (isAllCaps()) raw.uppercase() else raw
        val textWidth = if (text.isEmpty()) 0 else kotlin.math.ceil(getPaint().measureText(text)).toInt()
        val end = mIconGravity == ICON_GRAVITY_TEXT_END
        val iconWidth = compoundSize(if (end) 2 else 0)[0]
        val padding = if (text.isEmpty()) 0 else getCompoundDrawablePadding()
        val available = (width - textWidth - getPaddingLeft() - getPaddingRight() - iconWidth - padding) / 2
        return kotlin.math.max(0, available).let { if (end) -it else it }
    }

    open fun setIcon(icon: Drawable?) {
        this.mIcon = icon?.mutate()
        updateIcon()
    }

    open fun setIconResource(iconResourceId: Int) = setIcon(if (iconResourceId == 0) null else getContext().getDrawable(iconResourceId))
    open fun getIcon(): Drawable? = mIcon
    open fun setIconTint(iconTint: ColorStateList?) {
        mIconTint = iconTint
        updateIcon()
    }

    open fun setIconTintResource(id: Int) = setIconTint(androidx.core.content.ContextCompat.getColorStateList(getContext(), id))
    open fun getIconTint(): ColorStateList = mIconTint ?: getTextColors()

    override fun setTextColor(colors: ColorStateList?) {
        super.setTextColor(colors)
        // the default icon tint follows the text color
        if (mIcon != null && mIconTint == null) updateIcon()
    }

    open fun setIconSize(iconSize: Int) {
        mIconSize = iconSize
        updateIcon()
    }

    open fun getIconSize(): Int = mIconSize
    open fun setIconGravity(iconGravity: Int) {
        mIconGravity = iconGravity
        updateIcon()
    }

    open fun getIconGravity(): Int = mIconGravity
    open fun setIconPadding(iconPadding: Int) = setCompoundDrawablePadding(iconPadding)
    open fun getIconPadding(): Int = getCompoundDrawablePadding()
    open fun setCornerRadius(cornerRadius: Int) {
        this.cornerRadius = cornerRadius
        invalidate()
    }

    open fun setCornerRadiusResource(id: Int) = setCornerRadius(getResources().getDimensionPixelSize(id))
    open fun getCornerRadius(): Int = cornerRadius
    open fun setStrokeWidth(strokeWidth: Int) {
        this.strokeWidth = strokeWidth
        invalidate()
    }

    open fun getStrokeWidth(): Int = strokeWidth
    open fun setStrokeColor(strokeColor: ColorStateList?) {
        this.strokeColor = strokeColor
        invalidate()
    }

    open fun getStrokeColor(): ColorStateList? = strokeColor
    open fun setRippleColor(rippleColor: ColorStateList?) {}
    open fun setCheckable(checkable: Boolean) {
        this.checkable = checkable
    }

    open fun isCheckable(): Boolean = checkable
    open fun setChecked(checked: Boolean) = setSelected(checked)
    open fun isChecked(): Boolean = isSelected()

    companion object {
        const val ICON_GRAVITY_START = 1
        const val ICON_GRAVITY_TEXT_START = 2
        const val ICON_GRAVITY_END = 3
        const val ICON_GRAVITY_TEXT_END = 4
        const val ICON_GRAVITY_TOP = 0x10
        const val ICON_GRAVITY_TEXT_TOP = 0x20
    }
}

open class MaterialButtonToggleGroup : android.widget.LinearLayout {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    fun interface OnButtonCheckedListener {
        fun onButtonChecked(group: MaterialButtonToggleGroup, checkedId: Int, isChecked: Boolean)
    }

    private val listeners = ArrayList<OnButtonCheckedListener>()
    private var checkedId = android.view.View.NO_ID

    open fun addOnButtonCheckedListener(listener: OnButtonCheckedListener) {
        listeners.add(listener)
    }

    open fun check(id: Int) {
        checkedId = id
        listeners.forEach { it.onButtonChecked(this, id, true) }
    }

    open fun getCheckedButtonId(): Int = checkedId
    open fun setSingleSelection(singleSelection: Boolean) {}
    open fun setSelectionRequired(selectionRequired: Boolean) {}
}
