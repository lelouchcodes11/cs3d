package com.google.android.material.floatingactionbutton

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageButton
import com.google.android.material.button.MaterialButton

open class FloatingActionButton : ImageButton {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private var size = SIZE_AUTO
    private var customSize = 0

    open fun show() = setVisibility(View.VISIBLE)
    open fun hide() = setVisibility(View.GONE)
    open fun isOrWillBeShown(): Boolean = getVisibility() == View.VISIBLE
    open fun isOrWillBeHidden(): Boolean = getVisibility() != View.VISIBLE
    open fun setSize(size: Int) { this.size = size }
    open fun getSize(): Int = size
    open fun setCustomSize(size: Int) { customSize = size }
    open fun getCustomSize(): Int = customSize
    open fun setRippleColor(color: Int) {}
    open fun setRippleColor(color: android.content.res.ColorStateList?) {}
    open fun setCompatElevation(elevation: Float) = setElevation(elevation)

    companion object {
        const val SIZE_MINI = 1
        const val SIZE_NORMAL = 0
        const val SIZE_AUTO = -1
        const val NO_CUSTOM_SIZE = 0
    }
}

open class ExtendedFloatingActionButton : MaterialButton {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private var extended = true
    private var collapsedText: CharSequence? = null

    // desktop: collapsing to the icon-only state is done by dropping the label (no width animation)
    open fun shrink() {
        if (!extended) return
        extended = false
        collapsedText = getText()
        setText("")
    }

    open fun extend() {
        if (extended) return
        extended = true
        setText(collapsedText ?: "")
        collapsedText = null
    }

    open fun isExtended(): Boolean = extended
    open fun show() = setVisibility(View.VISIBLE)
    open fun hide() = setVisibility(View.GONE)
}
