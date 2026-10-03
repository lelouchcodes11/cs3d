package com.google.android.material.divider

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.lagradost.desktop.runtime.ui.DrawsItself
import com.lagradost.desktop.runtime.ui.ThemeBridge
import com.lagradost.desktop.runtime.ui.ViewAttributes

/** A hairline. Color and thickness come from materialDividerStyle when the element does not set them. */
open class MaterialDivider : View, DrawsItself {
    private var color = (ThemeBridge.textColorSecondary and 0x00FFFFFF) or 0x61000000
    private var thicknessPx = 1
    private var insetStart = 0
    private var insetEnd = 0
    private val paint = Paint()

    constructor(context: Context?) : super(context) {
        thicknessPx = dp(1f)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        read(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        read(attrs)
    }

    private fun read(attrs: AttributeSet?) {
        thicknessPx = dp(1f)
        if (attrs == null) return
        val r = ViewAttributes.reader(this, attrs)
        r.colorStateList("dividerColor")?.let { color = it.defaultColor }
        r.dim("dividerThickness")?.let { if (it > 0) thicknessPx = it }
        r.dim("dividerInsetStart")?.let { insetStart = it }
        r.dim("dividerInsetEnd")?.let { insetEnd = it }
    }

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getContext().getResources().getDisplayMetrics()).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = thicknessPx + getPaddingTop() + getPaddingBottom()
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(heightMeasureSpec)
        else resolveSize(desired, heightMeasureSpec)
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec), h)
    }

    override fun onDraw(canvas: Canvas) {
        paint.setColor(color)
        paint.setStyle(Paint.Style.FILL)
        val top = getPaddingTop() + ((getHeight() - getPaddingTop() - getPaddingBottom() - thicknessPx) / 2).coerceAtLeast(0)
        canvas.drawRect(
            (getPaddingLeft() + insetStart).toFloat(),
            top.toFloat(),
            (getWidth() - getPaddingRight() - insetEnd).toFloat(),
            (top + thicknessPx).toFloat(),
            paint,
        )
    }
}
