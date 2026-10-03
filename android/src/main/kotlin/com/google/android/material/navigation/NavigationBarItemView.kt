package com.google.android.material.navigation

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

open class NavigationBarItemView : LinearLayout {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
}
