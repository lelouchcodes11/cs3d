package com.google.android.material.progressindicator

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.ProgressBar
import com.lagradost.desktop.runtime.ui.ViewAttributes

/**
 * Material progress indicators. indicatorColor, trackColor, trackThickness and indicatorSize come
 * from the element and its style. A circular indicator stays circular when progress is set
 * (ProgressBar.setMax otherwise forces the horizontal style).
 */
open class BaseProgressIndicator : ProgressBar {
    @JvmField var trackThicknessPx: Int = 0
    @JvmField var indicatorInsetPx: Int = 0
    @JvmField var indicatorSizePx: Int = 0
    @JvmField var trackGapPx: Int = 0

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        readIndicator(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        readIndicator(attrs)
    }

    protected fun readIndicator(attrs: AttributeSet?) {
        if (attrs == null) return
        val r = ViewAttributes.reader(this, attrs)
        r.colorStateList("indicatorColor")?.let {
            setProgressTintList(it)
            setIndeterminateTintList(it)
        }
        r.colorStateList("trackColor")?.let { setProgressBackgroundTintList(it) }
        r.dim("trackThickness")?.let { trackThicknessPx = it }
        r.dim("indicatorSize")?.let { indicatorSizePx = it }
        r.dim("indicatorInset")?.let { indicatorInsetPx = it }
        r.dim("indicatorTrackGapSize")?.let { trackGapPx = it }
        r.dim("trackCornerRadius")
    }

    protected fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getContext().getResources().getDisplayMetrics()).toInt()
}

open class CircularProgressIndicator : BaseProgressIndicator {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun setMax(max: Int) {
        super.setMax(max)
        setHorizontalStyle(false)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (indicatorSizePx <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val w = indicatorSizePx + getPaddingLeft() + getPaddingRight()
        val h = indicatorSizePx + getPaddingTop() + getPaddingBottom()
        setMeasuredDimension(View.resolveSize(w, widthMeasureSpec), View.resolveSize(h, heightMeasureSpec))
    }
}

open class LinearProgressIndicator : BaseProgressIndicator {
    constructor(context: Context?) : super(context) {
        setHorizontalStyle(true)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        finishLinear()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        finishLinear()
    }

    private fun finishLinear() {
        setHorizontalStyle(true)
        if (trackThicknessPx <= 0) trackThicknessPx = dp(4f)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (trackThicknessPx <= 0) return
        if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.EXACTLY) return
        val h = trackThicknessPx + getPaddingTop() + getPaddingBottom()
        setMeasuredDimension(getMeasuredWidth(), View.resolveSize(h, heightMeasureSpec))
    }
}
