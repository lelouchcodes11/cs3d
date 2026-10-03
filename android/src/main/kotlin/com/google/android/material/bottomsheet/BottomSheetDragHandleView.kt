package com.google.android.material.bottomsheet

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView

/** Widget.Material3.BottomSheet.DragHandle: the drag handle drawable centered with 22dp vertical padding */
open class BottomSheetDragHandleView : AppCompatImageView {
    constructor(context: Context?) : super(context) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        if (getDrawable() == null) {
            val id = getContext().getResources().getIdentifier("mtrl_bottomsheet_drag_handle", "drawable", null)
            if (id != 0) runCatching { setImageDrawable(getContext().getDrawable(id)) }
        }
        setScaleType(ImageView.ScaleType.CENTER)
        val pad = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 22f, getContext().getResources().getDisplayMetrics()).toInt()
        if (getPaddingTop() == 0 && getPaddingBottom() == 0) setPadding(getPaddingLeft(), pad, getPaddingRight(), pad)
    }
}
