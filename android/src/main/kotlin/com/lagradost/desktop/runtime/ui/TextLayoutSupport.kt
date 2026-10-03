package com.lagradost.desktop.runtime.ui

import android.text.Spanned
import android.text.TextUtils
import android.text.method.PasswordTransformationMethod
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import com.lagradost.desktop.runtime.AndroidRuntime
import java.util.WeakHashMap
import kotlin.math.ceil

/**
 * Text of TextViews: the Compose text style, spans and transformations of a view, and its text
 * layout. The same functions serve TextView.onMeasure (native layout) and the renderer, so text is
 * measured and drawn identically.
 */
object TextLayoutSupport {
    /** Font of TextViews without a typeface, set by the app to its theme font (Google Sans) */
    @Volatile
    var defaultFontFamily: FontFamily = FontFamily.Default

    private val fontFamilyResolver by lazy { createFontFamilyResolver() }
    private val measurer by lazy { TextMeasurer(fontFamilyResolver, density(), LayoutDirection.Ltr, 512) }

    fun density(): Density {
        val dm = AndroidRuntime.displayMetrics
        return Density(dm.density, if (dm.density > 0f) dm.scaledDensity / dm.density else 1f)
    }

    // ------------------------------------------------------------------------------------ fonts

    private val familyCache = WeakHashMap<android.graphics.Typeface, FontFamily>()

    private val defaultTypefaces by lazy {
        setOf(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.SANS_SERIF)
    }

    /** Font family of an Android typeface; null for the default one (the app font, like on Android) */
    fun familyOf(tf: android.graphics.Typeface?): FontFamily? {
        if (tf == null || tf in defaultTypefaces) return null
        return synchronized(familyCache) {
            familyCache.getOrPut(tf) { FontFamily(androidx.compose.ui.text.platform.Typeface(tf.skia)) }
        }
    }

    // ------------------------------------------------------------------------------------ style

    fun textAlignFor(gravity: Int): TextAlign = when (gravity and 0x07) {
        Gravity.CENTER_HORIZONTAL -> TextAlign.Center
        Gravity.RIGHT -> TextAlign.End
        else -> TextAlign.Start
    }

    /** The Compose text style of a TextView (size, color, font, spacing, decorations, alignment) */
    fun styleOf(view: TextView, color: Int = view.getCurrentTextColor(), density: Density = density()): TextStyle {
        val sizePx = view.getTextSize()
        val tf = view.getTypeface()
        val style = view.getTypefaceStyle() or (tf?.getStyle() ?: 0)
        val weight = when {
            tf != null && tf.getWeight() != 400 && tf.getWeight() != 700 -> FontWeight(tf.getWeight())
            (style and android.graphics.Typeface.BOLD) != 0 -> FontWeight.Bold
            else -> FontWeight.Normal
        }
        val flags = view.getPaintFlags()
        val decorations = buildList {
            if ((flags and android.graphics.Paint.UNDERLINE_TEXT_FLAG) != 0) add(TextDecoration.Underline)
            if ((flags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG) != 0) add(TextDecoration.LineThrough)
        }
        val mult = view.getLineSpacingMultiplier()
        val extra = view.getLineSpacingExtra()
        val lineHeight = if (mult != 1f || extra != 0f) with(density) { (sizePx * 1.17f * mult + extra).toSp() } else TextUnit.Unspecified
        val shadow = if (view.getShadowRadius() > 0f) Shadow(Color(view.getShadowColor()), Offset(1f, 1f), view.getShadowRadius()) else null
        return TextStyle(
            color = Color(color),
            fontSize = with(density) { sizePx.toSp() },
            fontWeight = weight,
            fontStyle = if ((style and android.graphics.Typeface.ITALIC) != 0) FontStyle.Italic else FontStyle.Normal,
            fontFamily = familyOf(tf) ?: defaultFontFamily,
            letterSpacing = if (view.getLetterSpacing() != 0f) view.getLetterSpacing().em else TextUnit.Unspecified,
            textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
            shadow = shadow,
            textAlign = textAlignFor(view.getGravity()),
            lineHeight = lineHeight,
        )
    }

    // ------------------------------------------------------------------------------------ text

    /** The text a TextView displays (all caps, password and single line transformations) */
    fun displayText(view: TextView, raw: CharSequence): CharSequence {
        var t = raw
        if (view.getTransformationMethod() is PasswordTransformationMethod) return "•".repeat(t.length)
        if (view.isAllCaps()) t = t.toString().uppercase()
        if (view.isSingleLine() && t.contains('\n')) t = t.toString().replace('\n', ' ')
        return t
    }

    /** Android spans -> AnnotatedString (colors, styles, sizes, links) */
    fun toAnnotated(view: View, text: CharSequence, linkColor: Color, density: Density, baseSizePx: Float): AnnotatedString {
        if (text !is Spanned) return AnnotatedString(text.toString())
        val spans = try {
            text.getSpans(0, text.length, Any::class.java)
        } catch (_: Throwable) {
            emptyArray()
        }
        if (spans.isEmpty()) return AnnotatedString(text.toString())
        return buildAnnotatedString {
            append(text.toString())
            for (span in spans) {
                val start = text.getSpanStart(span).coerceIn(0, text.length)
                val end = text.getSpanEnd(span).coerceIn(start, text.length)
                if (start >= end) continue
                when (span) {
                    is ForegroundColorSpan -> addStyle(SpanStyle(color = Color(span.getForegroundColor())), start, end)
                    is BackgroundColorSpan -> addStyle(SpanStyle(background = Color(span.getBackgroundColor())), start, end)
                    is StyleSpan -> {
                        val s = span.getStyle()
                        addStyle(
                            SpanStyle(
                                fontWeight = if ((s and android.graphics.Typeface.BOLD) != 0) FontWeight.Bold else null,
                                fontStyle = if ((s and android.graphics.Typeface.ITALIC) != 0) FontStyle.Italic else null,
                            ), start, end
                        )
                    }
                    is UnderlineSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
                    is StrikethroughSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
                    is RelativeSizeSpan -> addStyle(SpanStyle(fontSize = with(density) { (baseSizePx * span.getSizeChange()).toSp() }), start, end)
                    is AbsoluteSizeSpan -> {
                        val px = if (span.getDip()) span.getSize() * density.density else span.getSize().toFloat()
                        addStyle(SpanStyle(fontSize = with(density) { px.toSp() }), start, end)
                    }
                    is TypefaceSpan -> {
                        val family = familyOf(span.getTypeface()) ?: when (span.getFamily()) {
                            "monospace" -> FontFamily.Monospace
                            "serif" -> FontFamily.Serif
                            else -> null
                        }
                        if (family != null) addStyle(SpanStyle(fontFamily = family), start, end)
                    }
                    is SuperscriptSpan -> addStyle(SpanStyle(baselineShift = BaselineShift.Superscript), start, end)
                    is SubscriptSpan -> addStyle(SpanStyle(baselineShift = BaselineShift.Subscript), start, end)
                    is ClickableSpan -> {
                        val styles = androidx.compose.ui.text.TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                        val tag = if (span is URLSpan) span.getURL() ?: "span$start" else "span$start"
                        addLink(LinkAnnotation.Clickable(tag, styles) { span.onClick(view) }, start, end)
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------ layout

    /** How a TextView lays out its text: max lines, wrapping and ellipsis */
    class Params(val maxLines: Int, val softWrap: Boolean, val overflow: TextOverflow)

    fun paramsOf(view: TextView): Params {
        val maxLines = if (view.isSingleLine()) 1 else view.getMaxLines().coerceAtLeast(1)
        val ellipsize = view.getEllipsize()
        val overflow = if (ellipsize != null && ellipsize != TextUtils.TruncateAt.MARQUEE) TextOverflow.Ellipsis else TextOverflow.Clip
        val softWrap = !view.isSingleLine() && !(maxLines == 1 && ellipsize != null)
        return Params(maxLines, softWrap, overflow)
    }

    /** Lays out [text] of [view] within [maxWidth] px (Constraints.Infinity = unbounded) */
    fun layout(view: TextView, text: AnnotatedString, style: TextStyle, maxWidth: Int, params: Params = paramsOf(view)): TextLayoutResult {
        val d = density()
        return measurer.measure(
            text = text,
            style = style,
            overflow = params.overflow,
            softWrap = params.softWrap,
            maxLines = params.maxLines,
            // the paragraph is as wide as the text area so gravity aligns lines inside it (Layout width)
            constraints = if (maxWidth == Constraints.Infinity) Constraints() else Constraints.fixedWidth(maxWidth.coerceAtLeast(0)),
            density = d,
        )
    }

    /** Width of the widest line of [text] when nothing wraps */
    fun unboundedWidth(view: TextView, text: AnnotatedString, style: TextStyle): Int =
        ceil(layout(view, text, style, Constraints.Infinity, Params(Int.MAX_VALUE, false, TextOverflow.Clip)).multiParagraph.width).toInt()

    /** The text ([hint] = the hint) of a TextView as laid out: transformed, with its spans */
    fun annotated(view: TextView, hint: Boolean): AnnotatedString {
        val raw = (if (hint) view.getHint() else view.textState()) ?: ""
        val shown = if (hint) raw else displayText(view, raw)
        val link = view.getLinkTextColors()?.getColorForState(view.getDrawableState(), ThemeBridge.colorPrimary) ?: ThemeBridge.colorPrimary
        return toAnnotated(view, shown, Color(link), density(), view.getTextSize())
    }

    /** Height of one line of the view's font (TextView.getLineHeight) */
    fun lineHeight(view: TextView): Int {
        val style = styleOf(view)
        val r = measurer.measure(AnnotatedString("X"), style, maxLines = 1, density = density())
        return r.size.height
    }
}

/** android.text.Layout over a Compose text layout (TextView.getLayout) */
class DesktopTextLayout(val result: TextLayoutResult, private val text: CharSequence) : android.text.Layout() {
    override fun getLineCount(): Int = result.lineCount
    override fun getHeight(): Int = result.size.height
    override fun getWidth(): Int = result.size.width
    override fun getText(): CharSequence = text
    override fun getLineTop(line: Int): Int = if (line >= result.lineCount) result.size.height else result.getLineTop(line).toInt()
    override fun getLineBottom(line: Int): Int = result.getLineBottom(line.coerceIn(0, result.lineCount - 1)).toInt()
    override fun getLineBaseline(line: Int): Int = result.getLineBaseline(line.coerceIn(0, result.lineCount - 1)).toInt()
    override fun getLineAscent(line: Int): Int = getLineTop(line) - getLineBaseline(line)
    override fun getLineDescent(line: Int): Int = getLineBottom(line) - getLineBaseline(line)
    override fun getLineStart(line: Int): Int = result.getLineStart(line.coerceIn(0, result.lineCount - 1))
    override fun getLineEnd(line: Int): Int = result.getLineEnd(line.coerceIn(0, result.lineCount - 1))
    override fun getLineVisibleEnd(line: Int): Int = result.getLineEnd(line.coerceIn(0, result.lineCount - 1), visibleEnd = true)
    override fun getLineLeft(line: Int): Float = result.getLineLeft(line.coerceIn(0, result.lineCount - 1))
    override fun getLineRight(line: Int): Float = result.getLineRight(line.coerceIn(0, result.lineCount - 1))
    override fun getLineForOffset(offset: Int): Int = result.getLineForOffset(offset.coerceIn(0, result.layoutInput.text.length))
    override fun getLineForVertical(vertical: Int): Int = result.getLineForVerticalPosition(vertical.toFloat())
    override fun getOffsetForHorizontal(line: Int, horiz: Float): Int =
        result.getOffsetForPosition(Offset(horiz, (result.getLineTop(line) + result.getLineBottom(line)) / 2f))

    override fun getPrimaryHorizontal(offset: Int): Float = result.getHorizontalPosition(offset.coerceIn(0, result.layoutInput.text.length), true)
    override fun getEllipsisCount(line: Int): Int {
        val l = line.coerceIn(0, result.lineCount - 1)
        if (!result.isLineEllipsized(l)) return 0
        return (result.getLineEnd(l) - result.getLineEnd(l, visibleEnd = true)).coerceAtLeast(1)
    }

    override fun getEllipsisStart(line: Int): Int {
        val l = line.coerceIn(0, result.lineCount - 1)
        return if (result.isLineEllipsized(l)) result.getLineEnd(l, visibleEnd = true) - result.getLineStart(l) else 0
    }
}
