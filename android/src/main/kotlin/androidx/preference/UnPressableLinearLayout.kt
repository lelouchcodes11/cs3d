package androidx.preference

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

/**
 * The preference seekbar row uses this so a press on the row does not press its children.
 * Pressed state is not pushed onto children, which is what this class exists to guarantee.
 */
open class UnPressableLinearLayout : LinearLayout {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
}
