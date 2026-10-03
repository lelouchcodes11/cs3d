@file:JvmName("SpansKt")

package android.text.style

import android.graphics.Typeface
import android.text.TextPaint
import android.view.View

abstract class MetricAffectingSpan : CharacterStyle(), UpdateLayout {
    abstract fun updateMeasureState(textPaint: TextPaint)
}

interface UpdateAppearance
interface UpdateLayout : UpdateAppearance
interface ParagraphStyle
interface LineBackgroundSpan : ParagraphStyle {
    fun drawBackground(
        c: android.graphics.Canvas,
        p: android.graphics.Paint,
        left: Int,
        right: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        lineNumber: Int
    )
}
interface LeadingMarginSpan : ParagraphStyle
interface AlignmentSpan : ParagraphStyle {
    fun getAlignment(): android.text.Layout.Alignment

    open class Standard(private val align: android.text.Layout.Alignment) : AlignmentSpan {
        override fun getAlignment(): android.text.Layout.Alignment = align
    }
}

open class ForegroundColorSpan(private val color: Int) : CharacterStyle(), UpdateAppearance {
    fun getForegroundColor(): Int = color
    override fun updateDrawState(tp: TextPaint) {
        tp.setColor(color)
    }
}

open class BackgroundColorSpan(private val color: Int) : CharacterStyle(), UpdateAppearance {
    fun getBackgroundColor(): Int = color
    override fun updateDrawState(tp: TextPaint) {
        tp.bgColor = color
    }
}

open class StyleSpan(private val style: Int) : MetricAffectingSpan() {
    constructor(style: Int, fontWeightAdjustment: Int) : this(style)

    fun getStyle(): Int = style
    override fun updateDrawState(tp: TextPaint) {
        tp.setFakeBoldText((style and Typeface.BOLD) != 0)
    }

    override fun updateMeasureState(textPaint: TextPaint) = updateDrawState(textPaint)
}

open class UnderlineSpan : CharacterStyle(), UpdateAppearance {
    override fun updateDrawState(tp: TextPaint) {
        tp.setUnderlineText(true)
    }
}

open class StrikethroughSpan : CharacterStyle(), UpdateAppearance {
    override fun updateDrawState(tp: TextPaint) {
        tp.setStrikeThruText(true)
    }
}

open class RelativeSizeSpan(private val proportion: Float) : MetricAffectingSpan() {
    fun getSizeChange(): Float = proportion
    override fun updateDrawState(tp: TextPaint) {
        tp.setTextSize(tp.getTextSize() * proportion)
    }

    override fun updateMeasureState(textPaint: TextPaint) = updateDrawState(textPaint)
}

open class AbsoluteSizeSpan(private val size: Int, private val dip: Boolean) : MetricAffectingSpan() {
    constructor(size: Int) : this(size, false)

    fun getSize(): Int = size
    fun getDip(): Boolean = dip
    override fun updateDrawState(tp: TextPaint) {
        tp.setTextSize(if (dip) size * tp.density else size.toFloat())
    }

    override fun updateMeasureState(textPaint: TextPaint) = updateDrawState(textPaint)
}

open class TypefaceSpan : MetricAffectingSpan {
    private val family: String?
    private val typeface: Typeface?

    constructor(family: String?) {
        this.family = family
        typeface = null
    }

    constructor(typeface: Typeface) {
        family = null
        this.typeface = typeface
    }

    fun getFamily(): String? = family
    fun getTypeface(): Typeface? = typeface
    override fun updateDrawState(tp: TextPaint) {
        tp.setTypeface(typeface ?: Typeface.create(family, Typeface.NORMAL))
    }

    override fun updateMeasureState(textPaint: TextPaint) = updateDrawState(textPaint)
}

abstract class ClickableSpan : CharacterStyle(), UpdateAppearance {
    abstract fun onClick(widget: View)
    override fun updateDrawState(ds: TextPaint) {
        ds.setColor(ds.linkColor)
        ds.setUnderlineText(true)
    }
}

open class URLSpan(private val url: String?) : ClickableSpan() {
    fun getURL(): String? = url
    override fun onClick(widget: View) {
        val u = url ?: return
        com.lagradost.desktop.runtime.AndroidRuntime.host.openUrl(u)
    }
}

open class ImageSpan(private val drawable: android.graphics.drawable.Drawable?) : CharacterStyle() {
    fun getDrawable(): android.graphics.drawable.Drawable? = drawable
    override fun updateDrawState(tp: TextPaint) {}
}

open class SuperscriptSpan : MetricAffectingSpan() {
    override fun updateDrawState(tp: TextPaint) {}
    override fun updateMeasureState(textPaint: TextPaint) {}
}

open class SubscriptSpan : MetricAffectingSpan() {
    override fun updateDrawState(tp: TextPaint) {}
    override fun updateMeasureState(textPaint: TextPaint) {}
}

open class QuoteSpan : ParagraphStyle
open class BulletSpan : ParagraphStyle {
    constructor()
    constructor(gapWidth: Int)
    constructor(gapWidth: Int, color: Int)
}
