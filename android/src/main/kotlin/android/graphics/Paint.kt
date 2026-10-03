package android.graphics

import org.jetbrains.skia.Font
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.PaintStrokeJoin

/** android.graphics.Paint backed by a Skia paint and font. */
open class Paint {
    enum class Style(@JvmField val nativeInt: Int) { FILL(0), STROKE(1), FILL_AND_STROKE(2) }
    enum class Cap(@JvmField val nativeInt: Int) { BUTT(0), ROUND(1), SQUARE(2) }
    enum class Join(@JvmField val nativeInt: Int) { MITER(0), ROUND(1), BEVEL(2) }
    enum class Align(@JvmField val nativeInt: Int) { LEFT(0), CENTER(1), RIGHT(2) }

    class FontMetrics {
        @JvmField var top = 0f
        @JvmField var ascent = 0f
        @JvmField var descent = 0f
        @JvmField var bottom = 0f
        @JvmField var leading = 0f
    }

    class FontMetricsInt {
        @JvmField var top = 0
        @JvmField var ascent = 0
        @JvmField var descent = 0
        @JvmField var bottom = 0
        @JvmField var leading = 0
    }

    companion object {
        const val ANTI_ALIAS_FLAG = 0x01
        const val FILTER_BITMAP_FLAG = 0x02
        const val DITHER_FLAG = 0x04
        const val UNDERLINE_TEXT_FLAG = 0x08
        const val STRIKE_THRU_TEXT_FLAG = 0x10
        const val FAKE_BOLD_TEXT_FLAG = 0x20
        const val LINEAR_TEXT_FLAG = 0x40
        const val SUBPIXEL_TEXT_FLAG = 0x80
        const val EMBEDDED_BITMAP_TEXT_FLAG = 0x400
    }

    /** The native skia paint, exposed for the desktop Canvas implementation */
    @JvmField
    val skia = org.jetbrains.skia.Paint()

    private var flags = 0
    private var mStyle = Style.FILL
    private var cap = Cap.BUTT
    private var join = Join.MITER
    private var align = Align.LEFT
    private var colorFilter: ColorFilter? = null
    private var shader: Shader? = null
    private var typeface: Typeface? = Typeface.DEFAULT
    private var textSize = 12f
    private var textScaleX = 1f
    private var letterSpacing = 0f
    private var xfermode: Xfermode? = null

    constructor() {
        setFlags(0)
    }

    constructor(flags: Int) {
        setFlags(flags)
    }

    constructor(paint: Paint?) {
        if (paint != null) set(paint)
    }

    open fun set(src: Paint) {
        setFlags(src.flags)
        setColor(src.getColor())
        setStyle(src.mStyle)
        setStrokeWidth(src.getStrokeWidth())
        setStrokeCap(src.cap)
        setStrokeJoin(src.join)
        setStrokeMiter(src.getStrokeMiter())
        setColorFilter(src.colorFilter)
        setShader(src.shader)
        setTypeface(src.typeface)
        setTextSize(src.textSize)
        setTextAlign(src.align)
        setTextScaleX(src.textScaleX)
        letterSpacing = src.letterSpacing
    }

    open fun reset() {
        skia.reset()
        flags = 0
        mStyle = Style.FILL
        cap = Cap.BUTT
        join = Join.MITER
        align = Align.LEFT
        colorFilter = null
        shader = null
        typeface = Typeface.DEFAULT
        textSize = 12f
    }

    open fun getFlags(): Int = flags
    open fun setFlags(flags: Int) {
        this.flags = flags
        skia.isAntiAlias = (flags and ANTI_ALIAS_FLAG) != 0
        skia.isDither = (flags and DITHER_FLAG) != 0
    }

    open fun isAntiAlias(): Boolean = (flags and ANTI_ALIAS_FLAG) != 0
    open fun setAntiAlias(aa: Boolean) = setFlags(if (aa) flags or ANTI_ALIAS_FLAG else flags and ANTI_ALIAS_FLAG.inv())
    open fun isDither(): Boolean = (flags and DITHER_FLAG) != 0
    open fun setDither(dither: Boolean) = setFlags(if (dither) flags or DITHER_FLAG else flags and DITHER_FLAG.inv())
    open fun isFilterBitmap(): Boolean = (flags and FILTER_BITMAP_FLAG) != 0
    open fun setFilterBitmap(filter: Boolean) = setFlags(if (filter) flags or FILTER_BITMAP_FLAG else flags and FILTER_BITMAP_FLAG.inv())
    open fun isFakeBoldText(): Boolean = (flags and FAKE_BOLD_TEXT_FLAG) != 0
    open fun setFakeBoldText(b: Boolean) = setFlags(if (b) flags or FAKE_BOLD_TEXT_FLAG else flags and FAKE_BOLD_TEXT_FLAG.inv())
    open fun isUnderlineText(): Boolean = (flags and UNDERLINE_TEXT_FLAG) != 0
    open fun setUnderlineText(b: Boolean) = setFlags(if (b) flags or UNDERLINE_TEXT_FLAG else flags and UNDERLINE_TEXT_FLAG.inv())
    open fun isStrikeThruText(): Boolean = (flags and STRIKE_THRU_TEXT_FLAG) != 0
    open fun setStrikeThruText(b: Boolean) = setFlags(if (b) flags or STRIKE_THRU_TEXT_FLAG else flags and STRIKE_THRU_TEXT_FLAG.inv())

    open fun getStyle(): Style = mStyle
    open fun setStyle(style: Style?) {
        this.mStyle = style ?: Style.FILL
        skia.mode = when (this.mStyle) {
            Style.FILL -> PaintMode.FILL
            Style.STROKE -> PaintMode.STROKE
            Style.FILL_AND_STROKE -> PaintMode.STROKE_AND_FILL
        }
    }

    open fun getColor(): Int = skia.color
    open fun setColor(color: Int) {
        skia.color = color
    }

    open fun setColor(color: Long) {
        skia.color = color.toInt()
    }

    open fun getAlpha(): Int = skia.alpha
    open fun setAlpha(a: Int) {
        skia.alpha = a.coerceIn(0, 255)
    }

    open fun setARGB(a: Int, r: Int, g: Int, b: Int) = setColor(Color.argb(a, r, g, b))

    open fun getStrokeWidth(): Float = skia.strokeWidth
    open fun setStrokeWidth(width: Float) {
        skia.strokeWidth = width
    }

    open fun getStrokeMiter(): Float = skia.strokeMiter
    open fun setStrokeMiter(miter: Float) {
        skia.strokeMiter = miter
    }

    open fun getStrokeCap(): Cap = cap
    open fun setStrokeCap(cap: Cap?) {
        this.cap = cap ?: Cap.BUTT
        skia.strokeCap = when (this.cap) {
            Cap.BUTT -> PaintStrokeCap.BUTT
            Cap.ROUND -> PaintStrokeCap.ROUND
            Cap.SQUARE -> PaintStrokeCap.SQUARE
        }
    }

    open fun getStrokeJoin(): Join = join
    open fun setStrokeJoin(join: Join?) {
        this.join = join ?: Join.MITER
        skia.strokeJoin = when (this.join) {
            Join.MITER -> PaintStrokeJoin.MITER
            Join.ROUND -> PaintStrokeJoin.ROUND
            Join.BEVEL -> PaintStrokeJoin.BEVEL
        }
    }

    open fun getColorFilter(): ColorFilter? = colorFilter
    open fun setColorFilter(filter: ColorFilter?): ColorFilter? {
        colorFilter = filter
        skia.colorFilter = filter?.toSkia()
        return filter
    }

    open fun getShader(): Shader? = shader
    open fun setShader(shader: Shader?): Shader? {
        this.shader = shader
        skia.shader = shader?.toSkia()
        return shader
    }

    open fun getXfermode(): Xfermode? = xfermode
    open fun setXfermode(xfermode: Xfermode?): Xfermode? {
        this.xfermode = xfermode
        (xfermode as? PorterDuffXfermode)?.let { skia.blendMode = it.mode.toSkia() }
        return xfermode
    }

    open fun setShadowLayer(radius: Float, dx: Float, dy: Float, shadowColor: Int) {}
    open fun clearShadowLayer() {}

    open fun getTypeface(): Typeface? = typeface
    open fun setTypeface(typeface: Typeface?): Typeface? {
        this.typeface = typeface
        return typeface
    }

    open fun getTextSize(): Float = textSize
    open fun setTextSize(textSize: Float) {
        this.textSize = textSize
    }

    open fun getTextScaleX(): Float = textScaleX
    open fun setTextScaleX(scaleX: Float) {
        textScaleX = scaleX
    }

    open fun getLetterSpacing(): Float = letterSpacing
    open fun setLetterSpacing(letterSpacing: Float) {
        this.letterSpacing = letterSpacing
    }

    open fun getTextAlign(): Align = align
    open fun setTextAlign(align: Align?) {
        this.align = align ?: Align.LEFT
    }

    /** Skia font for this paint's typeface and text size */
    fun font(): Font {
        val tf = (typeface ?: Typeface.DEFAULT).skia
        return Font(tf, textSize).also {
            it.isEmboldened = isFakeBoldText()
            it.scaleX = textScaleX
        }
    }

    open fun measureText(text: String?): Float = if (text.isNullOrEmpty()) 0f else font().measureTextWidth(text)
    open fun measureText(text: String?, start: Int, end: Int): Float = measureText(text?.substring(start, end))
    open fun measureText(text: CharSequence?, start: Int, end: Int): Float = measureText(text?.subSequence(start, end)?.toString())
    open fun measureText(text: CharArray?, index: Int, count: Int): Float = measureText(text?.let { String(it, index, count) })

    open fun getTextBounds(text: String?, start: Int, end: Int, bounds: Rect) {
        val s = text?.substring(start, end) ?: ""
        val r = font().measureText(s, skia)
        bounds.set(r.left.toInt(), r.top.toInt(), kotlin.math.ceil(r.right).toInt(), kotlin.math.ceil(r.bottom).toInt())
    }

    open fun getFontMetrics(metrics: FontMetrics?): Float {
        val m = font().metrics
        metrics?.apply {
            top = m.top
            ascent = m.ascent
            descent = m.descent
            bottom = m.bottom
            leading = m.leading
        }
        return m.descent - m.ascent + m.leading
    }

    open fun getFontMetrics(): FontMetrics = FontMetrics().also { getFontMetrics(it) }

    open fun getFontMetricsInt(fmi: FontMetricsInt?): Int {
        val m = font().metrics
        fmi?.apply {
            top = kotlin.math.floor(m.top).toInt()
            ascent = kotlin.math.round(m.ascent).toInt()
            descent = kotlin.math.round(m.descent).toInt()
            bottom = kotlin.math.ceil(m.bottom).toInt()
            leading = kotlin.math.round(m.leading).toInt()
        }
        return kotlin.math.round(m.descent - m.ascent + m.leading).toInt()
    }

    open fun getFontMetricsInt(): FontMetricsInt = FontMetricsInt().also { getFontMetricsInt(it) }
    open fun ascent(): Float = font().metrics.ascent
    open fun descent(): Float = font().metrics.descent
    open fun getFontSpacing(): Float = getFontMetrics(null)
}
