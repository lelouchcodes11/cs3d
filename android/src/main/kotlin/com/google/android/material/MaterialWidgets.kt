@file:JvmName("MaterialWidgetsKt")

package com.google.android.material.card

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import androidx.cardview.widget.CardView

open class MaterialCardView : CardView {
    private var strokeColor: ColorStateList? = null
    private var strokeWidth = 0
    private var checkable = false
    private var checked = false

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    open fun setStrokeColor(strokeColor: Int) = setStrokeColor(ColorStateList.valueOf(strokeColor))
    open fun setStrokeColor(strokeColor: ColorStateList?) {
        this.strokeColor = strokeColor
        invalidate()
    }

    open fun getStrokeColorStateList(): ColorStateList? = strokeColor
    open fun setStrokeWidth(strokeWidth: Int) {
        this.strokeWidth = strokeWidth
        invalidate()
    }

    open fun getStrokeWidth(): Int = strokeWidth
    open fun setCheckable(checkable: Boolean) {
        this.checkable = checkable
    }

    open fun isCheckable(): Boolean = checkable
    open fun setChecked(checked: Boolean) {
        this.checked = checked
        invalidate()
    }

    open fun isChecked(): Boolean = checked
    open fun toggle() = setChecked(!checked)
    open fun setRippleColor(rippleColor: ColorStateList?) {}
}
