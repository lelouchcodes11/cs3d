@file:JvmName("MaterialMisc2Kt")

package com.google.android.material.textfield

import android.content.Context
import android.util.AttributeSet
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatEditText

open class TextInputLayout : LinearLayout {
    private var hint: CharSequence? = null
    private var error: CharSequence? = null
    private var helperText: CharSequence? = null

    constructor(context: Context?) : super(context) {
        setOrientation(VERTICAL)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        setOrientation(VERTICAL)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setOrientation(VERTICAL)
    }

    open fun getEditText(): EditText? = children.firstOrNull { it is EditText } as? EditText
    open fun setHint(hint: CharSequence?) {
        this.hint = hint
        getEditText()?.setHint(hint)
    }

    open fun getHint(): CharSequence? = hint
    open fun setError(errorText: CharSequence?) {
        error = errorText
        getEditText()?.setError(errorText)
    }

    open fun getError(): CharSequence? = error
    open fun setErrorEnabled(enabled: Boolean) {}
    open fun setHelperText(helperText: CharSequence?) {
        this.helperText = helperText
    }

    open fun setHelperTextEnabled(enabled: Boolean) {}
    open fun setEndIconMode(endIconMode: Int) {}
    open fun setStartIconDrawable(startIconDrawable: android.graphics.drawable.Drawable?) {}
    open fun setBoxBackgroundColor(boxBackgroundColor: Int) {}
    open fun setBoxStrokeColor(boxStrokeColor: Int) {}
    open fun setHintEnabled(enabled: Boolean) {}
    open fun setPasswordVisibilityToggleEnabled(enabled: Boolean) {}

    companion object {
        const val END_ICON_NONE = 0
        const val END_ICON_PASSWORD_TOGGLE = 1
        const val END_ICON_CLEAR_TEXT = 2
        const val END_ICON_DROPDOWN_MENU = 3
        const val BOX_BACKGROUND_NONE = 0
        const val BOX_BACKGROUND_FILLED = 1
        const val BOX_BACKGROUND_OUTLINE = 2
    }

    override fun onViewAdded(child: android.view.View) {
        super.onViewAdded(child)
        if (child is EditText && hint != null && child.getHint() == null) child.setHint(hint)
    }
}

open class TextInputEditText : AppCompatEditText {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
}
