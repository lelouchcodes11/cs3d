@file:JvmName("MaterialMisc3Kt")

package com.google.android.material.switchmaterial

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.SwitchCompat

open class SwitchMaterial : SwitchCompat {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    open fun setUseMaterialThemeColors(useMaterialThemeColors: Boolean) {}
}
