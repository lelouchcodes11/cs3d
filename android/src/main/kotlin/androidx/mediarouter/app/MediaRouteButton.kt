package androidx.mediarouter.app

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/** desktop: no cast support; the button exists for view binding and stays empty */
open class MediaRouteButton : FrameLayout {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
}
