package android.text

import android.graphics.Paint

open class TextPaint : Paint {
    @JvmField
    var bgColor = 0

    @JvmField
    var baselineShift = 0

    @JvmField
    var linkColor = 0

    @JvmField
    var drawableState: IntArray? = null

    @JvmField
    var density = 1f

    @JvmField
    var underlineColor = 0

    constructor() : super()
    constructor(flags: Int) : super(flags)
    constructor(p: Paint?) : super(p)

    open fun set(tp: TextPaint) {
        super.set(tp)
        bgColor = tp.bgColor
        baselineShift = tp.baselineShift
        linkColor = tp.linkColor
        drawableState = tp.drawableState
        density = tp.density
    }
}
