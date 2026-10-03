package androidx.media3.common.text

import android.text.Layout

open class Cue(
    val text: CharSequence? = null,
    val textAlignment: Layout.Alignment? = null,
    val line: Float = -Float.MAX_VALUE,
    val lineType: Int = LINE_TYPE_FRACTION,
    val lineAnchor: Int = ANCHOR_TYPE_START,
    val position: Float = -Float.MAX_VALUE,
    val positionAnchor: Int = ANCHOR_TYPE_START,
    val size: Float = -Float.MAX_VALUE,
    val bitmapHeight: Float = -Float.MAX_VALUE,
    val windowColor: Int = 0,
    val windowColorSet: Boolean = false,
    val verticalType: Int = VERTICAL_TYPE_THUMBNAILS,
    val shearDegrees: Float = 0f
) {
    companion object {
        const val ANCHOR_TYPE_START = 0
        const val ANCHOR_TYPE_MIDDLE = 1
        const val ANCHOR_TYPE_END = 2

        const val LINE_TYPE_FRACTION = 0
        const val LINE_TYPE_NUMBER = 1

        const val TEXT_SIZE_TYPE_FRACTIONAL = 0
        const val TEXT_SIZE_TYPE_FRACTIONAL_IGNORE_PADDING = 1
        const val TEXT_SIZE_TYPE_ABSOLUTE = 2

        const val DIMEN_UNSET = -Float.MAX_VALUE
        const val TYPE_UNSET = Int.MIN_VALUE

        const val VERTICAL_TYPE_THUMBNAILS = 0
    }

    open fun buildUpon(): Builder = Builder(this)

    open class Builder() {
        private var text: CharSequence? = null
        private var textAlignment: Layout.Alignment? = null
        private var line: Float = -Float.MAX_VALUE
        private var lineType: Int = LINE_TYPE_FRACTION
        private var lineAnchor: Int = ANCHOR_TYPE_START
        private var position: Float = -Float.MAX_VALUE
        private var positionAnchor: Int = ANCHOR_TYPE_START
        private var size: Float = -Float.MAX_VALUE
        private var bitmapHeight: Float = -Float.MAX_VALUE
        private var windowColor: Int = 0
        private var windowColorSet: Boolean = false
        private var verticalType: Int = VERTICAL_TYPE_THUMBNAILS
        private var shearDegrees: Float = 0f
        private var textSize: Float = -Float.MAX_VALUE
        private var textSizeType: Int = TEXT_SIZE_TYPE_FRACTIONAL

        constructor(cue: Cue) : this() {
            this.text = cue.text
            this.textAlignment = cue.textAlignment
            this.line = cue.line
            this.lineType = cue.lineType
            this.lineAnchor = cue.lineAnchor
            this.position = cue.position
            this.positionAnchor = cue.positionAnchor
            this.size = cue.size
            this.bitmapHeight = cue.bitmapHeight
            this.windowColor = cue.windowColor
            this.windowColorSet = cue.windowColorSet
            this.verticalType = cue.verticalType
            this.shearDegrees = cue.shearDegrees
        }

        open fun setText(text: CharSequence?): Builder = apply { this.text = text }
        open fun getText(): CharSequence? = text
        open fun setTextAlignment(textAlignment: Layout.Alignment?): Builder = apply { this.textAlignment = textAlignment }
        open fun getTextAlignment(): Layout.Alignment? = textAlignment
        open fun setLine(line: Float, lineType: Int): Builder = apply {
            this.line = line
            this.lineType = lineType
        }
        open fun setLineAnchor(lineAnchor: Int): Builder = apply { this.lineAnchor = lineAnchor }
        open fun setPosition(position: Float): Builder = apply { this.position = position }
        open fun setPositionAnchor(positionAnchor: Int): Builder = apply { this.positionAnchor = positionAnchor }
        open fun setTextSize(textSize: Float, textSizeType: Int): Builder = apply {
            this.textSize = textSize
            this.textSizeType = textSizeType
        }
        open fun setSize(size: Float): Builder = apply { this.size = size }
        open fun setBitmapHeight(bitmapHeight: Float): Builder = apply { this.bitmapHeight = bitmapHeight }
        open fun setWindowColor(windowColor: Int): Builder = apply {
            this.windowColor = windowColor
            this.windowColorSet = true
        }
        open fun setShearDegrees(shearDegrees: Float): Builder = apply { this.shearDegrees = shearDegrees }
        open fun build(): Cue = Cue(
            text, textAlignment, line, lineType, lineAnchor, position, positionAnchor,
            size, bitmapHeight, windowColor, windowColorSet, verticalType, shearDegrees
        )
    }
}
