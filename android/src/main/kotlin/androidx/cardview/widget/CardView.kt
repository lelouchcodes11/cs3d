package androidx.cardview.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lagradost.desktop.runtime.ui.ViewAttributes

open class CardView : FrameLayout {
    private var mRadius by mutableFloatStateOf(0f)
    private var mCardElevation by mutableFloatStateOf(0f)
    private var mCardBackground by mutableStateOf<ColorStateList?>(null)
    private var mUseCompatPadding = false
    private var mPreventCornerOverlap = true

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        if (attrs != null) ViewAttributes.applyCardAttributes(this, attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs)

    open fun setRadius(radius: Float) {
        mRadius = radius
    }

    open fun getRadius(): Float = mRadius
    open fun setCardElevation(elevation: Float) {
        mCardElevation = elevation
    }

    open fun getCardElevation(): Float = mCardElevation
    open fun setMaxCardElevation(maxElevation: Float) {}
    open fun setCardBackgroundColor(color: Int) {
        mCardBackground = ColorStateList.valueOf(color)
    }

    open fun setCardBackgroundColor(color: ColorStateList?) {
        mCardBackground = color
    }

    open fun getCardBackgroundColor(): ColorStateList? = mCardBackground
    open fun setUseCompatPadding(useCompatPadding: Boolean) {
        mUseCompatPadding = useCompatPadding
    }

    open fun setPreventCornerOverlap(preventCornerOverlap: Boolean) {
        mPreventCornerOverlap = preventCornerOverlap
    }

    open fun setContentPadding(left: Int, top: Int, right: Int, bottom: Int) = setPadding(left, top, right, bottom)
}
