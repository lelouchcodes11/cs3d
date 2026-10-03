@file:JvmName("CoreCompatKt")

package androidx.core.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ScrollView

open class NestedScrollView : ScrollView {
    fun interface OnScrollChangeListener {
        fun onScrollChange(v: NestedScrollView, scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int)
    }

    private var listener: OnScrollChangeListener? = null

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    open fun setOnScrollChangeListener(l: OnScrollChangeListener?) {
        listener = l
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        listener?.onScrollChange(this, l, t, oldl, oldt)
    }
}

open class ContentLoadingProgressBar : android.widget.ProgressBar {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    open fun show() = setVisibility(VISIBLE)
    open fun hide() = setVisibility(GONE)
}

@Suppress("unused")
private val unusedFrameLayout: Class<*> = FrameLayout::class.java
