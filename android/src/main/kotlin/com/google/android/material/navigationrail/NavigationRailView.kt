package com.google.android.material.navigationrail

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.navigation.NavigationBarView

open class NavigationRailView : NavigationBarView {
    var menuGravity: Int = android.view.Gravity.CENTER

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        setHorizontalItems(false)
    }

    companion object {
        const val LABEL_VISIBILITY_AUTO = NavigationBarView.LABEL_VISIBILITY_AUTO
        const val LABEL_VISIBILITY_SELECTED = NavigationBarView.LABEL_VISIBILITY_SELECTED
        const val LABEL_VISIBILITY_LABELED = NavigationBarView.LABEL_VISIBILITY_LABELED
        const val LABEL_VISIBILITY_UNLABELED = NavigationBarView.LABEL_VISIBILITY_UNLABELED
    }
}
