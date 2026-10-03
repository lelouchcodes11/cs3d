package android.text

/** android.text.Layout: the positioned lines of a text (TextView.getLayout) */
abstract class Layout protected constructor() {
    enum class Alignment { ALIGN_NORMAL, ALIGN_OPPOSITE, ALIGN_CENTER }

    companion object {
        const val BREAK_STRATEGY_SIMPLE = 0
        const val BREAK_STRATEGY_HIGH_QUALITY = 1
        const val BREAK_STRATEGY_BALANCED = 2
        const val HYPHENATION_FREQUENCY_NONE = 0
        const val HYPHENATION_FREQUENCY_NORMAL = 1
        const val HYPHENATION_FREQUENCY_FULL = 2
        const val JUSTIFICATION_MODE_NONE = 0
        const val JUSTIFICATION_MODE_INTER_WORD = 1
        const val DIR_LEFT_TO_RIGHT = 1
        const val DIR_RIGHT_TO_LEFT = -1

        @JvmStatic
        fun getDesiredWidth(source: CharSequence, paint: TextPaint): Float = paint.measureText(source.toString())

        @JvmStatic
        fun getDesiredWidth(source: CharSequence, start: Int, end: Int, paint: TextPaint): Float = paint.measureText(source.subSequence(start, end).toString())
    }

    open fun getLineCount(): Int = 1
    open fun getHeight(): Int = 0
    open fun getWidth(): Int = 0
    open fun getText(): CharSequence = ""
    open fun getLineTop(line: Int): Int = 0
    open fun getLineBottom(line: Int): Int = getHeight()
    open fun getLineBaseline(line: Int): Int = 0
    open fun getLineAscent(line: Int): Int = 0
    open fun getLineDescent(line: Int): Int = 0
    open fun getLineStart(line: Int): Int = 0
    open fun getLineEnd(line: Int): Int = getText().length
    open fun getLineVisibleEnd(line: Int): Int = getLineEnd(line)
    open fun getLineLeft(line: Int): Float = 0f
    open fun getLineRight(line: Int): Float = getWidth().toFloat()
    open fun getLineWidth(line: Int): Float = getLineRight(line) - getLineLeft(line)
    open fun getLineMax(line: Int): Float = getLineWidth(line)
    open fun getLineForOffset(offset: Int): Int = 0
    open fun getLineForVertical(vertical: Int): Int = 0
    open fun getOffsetForHorizontal(line: Int, horiz: Float): Int = 0
    open fun getPrimaryHorizontal(offset: Int): Float = 0f
    open fun getSecondaryHorizontal(offset: Int): Float = getPrimaryHorizontal(offset)
    open fun getEllipsisCount(line: Int): Int = 0
    open fun getEllipsisStart(line: Int): Int = 0
    open fun getEllipsizedWidth(): Int = getWidth()
    open fun getParagraphDirection(line: Int): Int = DIR_LEFT_TO_RIGHT
    open fun getSpacingMultiplier(): Float = 1f
    open fun getSpacingAdd(): Float = 0f
    open fun getPaint(): TextPaint = TextPaint()
    open fun getAlignment(): Alignment = Alignment.ALIGN_NORMAL
    open fun getLineBounds(line: Int, bounds: android.graphics.Rect?): Int {
        bounds?.set(getLineLeft(line).toInt(), getLineTop(line), getLineRight(line).toInt(), getLineBottom(line))
        return getLineBaseline(line)
    }

    open fun draw(canvas: android.graphics.Canvas) {}
}

open class StaticLayout(
    val source: CharSequence,
    paint: TextPaint = TextPaint(),
    width: Int = 0,
    alignment: Alignment = Alignment.ALIGN_NORMAL,
    val spacingmult: Float = 1f,
    val spacingadd: Float = 0f,
    val includepad: Boolean = true
) : Layout() {
    private val mPaint: TextPaint = paint
    private val mWidth: Int = width
    private val mAlignment: Alignment = alignment

    override fun getPaint(): TextPaint = mPaint
    override fun getWidth(): Int = mWidth
    override fun getAlignment(): Alignment = mAlignment
    override fun getText(): CharSequence = source
    override fun getSpacingMultiplier(): Float = spacingmult
    override fun getSpacingAdd(): Float = spacingadd
    class Builder private constructor(
        val source: CharSequence,
        val start: Int,
        val end: Int,
        val paint: TextPaint,
        val width: Int
    ) {
        companion object {
            @JvmStatic
            fun obtain(source: CharSequence, start: Int, end: Int, paint: TextPaint, width: Int): Builder =
                Builder(source, start, end, paint, width)
        }
        private var alignment = Alignment.ALIGN_NORMAL
        private var spacingAdd = 0f
        private var spacingMult = 1f
        private var includePad = true

        fun setAlignment(alignment: Alignment): Builder = apply { this.alignment = alignment }
        fun setLineSpacing(spacingAdd: Float, spacingMult: Float): Builder = apply {
            this.spacingAdd = spacingAdd
            this.spacingMult = spacingMult
        }
        fun setIncludePad(includePad: Boolean): Builder = apply { this.includePad = includePad }
        fun build(): StaticLayout = StaticLayout(source, paint, width, alignment, spacingMult, spacingAdd, includePad)
    }
}

