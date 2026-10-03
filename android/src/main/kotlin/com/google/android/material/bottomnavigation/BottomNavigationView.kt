package com.google.android.material.bottomnavigation

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.navigation.NavigationBarView

open class BottomNavigationView : NavigationBarView {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        setHorizontalItems(true)
    }
}
