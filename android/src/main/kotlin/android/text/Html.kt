package android.text

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** android.text.Html implemented on top of jsoup */
object Html {
    const val FROM_HTML_MODE_LEGACY = 0x00000000
    const val FROM_HTML_MODE_COMPACT = 0x0000003f
    const val FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH = 0x00000001
    const val TO_HTML_PARAGRAPH_LINES_CONSECUTIVE = 0x00000000
    const val TO_HTML_PARAGRAPH_LINES_INDIVIDUAL = 0x00000001

    fun interface ImageGetter {
        fun getDrawable(source: String?): Drawable?
    }

    fun interface TagHandler {
        fun handleTag(opening: Boolean, tag: String?, output: Editable?, xmlReader: org.xml.sax.XMLReader?)
    }

    @JvmStatic
    @Deprecated("")
    fun fromHtml(source: String?): Spanned = fromHtml(source, FROM_HTML_MODE_LEGACY, null, null)

    @JvmStatic
    fun fromHtml(source: String?, flags: Int): Spanned = fromHtml(source, flags, null, null)

    @JvmStatic
    @Deprecated("")
    fun fromHtml(source: String?, imageGetter: ImageGetter?, tagHandler: TagHandler?): Spanned =
        fromHtml(source, FROM_HTML_MODE_LEGACY, imageGetter, tagHandler)

    @JvmStatic
    fun fromHtml(source: String?, flags: Int, imageGetter: ImageGetter?, tagHandler: TagHandler?): Spanned {
        val out = SpannableStringBuilder()
        if (source.isNullOrEmpty()) return out
        val body = Jsoup.parseBodyFragment(source).body()
        val compact = (flags and FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH) != 0
        for (child in body.childNodes()) append(child, out, compact)
        // Html.fromHtml trims trailing newlines produced by block elements
        while (out.isNotEmpty() && out[out.length - 1] == '\n') out.delete(out.length - 1, out.length)
        return out
    }

    private fun ensureNewlines(out: SpannableStringBuilder, count: Int) {
        if (out.isEmpty()) return
        var existing = 0
        var i = out.length - 1
        while (i >= 0 && out[i] == '\n' && existing < count) {
            existing++
            i--
        }
        repeat(count - existing) { out.append('\n') }
    }

    private fun append(node: Node, out: SpannableStringBuilder, compact: Boolean) {
        when (node) {
            is TextNode -> out.append(node.text())
            is Element -> {
                val tag = node.tagName().lowercase()
                val start = out.length
                when (tag) {
                    "br" -> {
                        out.append('\n')
                        return
                    }
                    "p", "div" -> ensureNewlines(out, if (compact) 1 else 2)
                    "h1", "h2", "h3", "h4", "h5", "h6" -> ensureNewlines(out, if (compact) 1 else 2)
                    "li" -> {
                        ensureNewlines(out, 1)
                        out.append("• ")
                    }
                    "ul", "ol", "blockquote" -> ensureNewlines(out, 1)
                }
                for (c in node.childNodes()) append(c, out, compact)
                val end = out.length
                if (end > start) {
                    val flag = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    when (tag) {
                        "b", "strong" -> out.setSpan(StyleSpan(Typeface.BOLD), start, end, flag)
                        "i", "em", "cite", "dfn" -> out.setSpan(StyleSpan(Typeface.ITALIC), start, end, flag)
                        "u", "ins" -> out.setSpan(UnderlineSpan(), start, end, flag)
                        "s", "strike", "del" -> out.setSpan(StrikethroughSpan(), start, end, flag)
                        "big" -> out.setSpan(RelativeSizeSpan(1.25f), start, end, flag)
                        "small" -> out.setSpan(RelativeSizeSpan(0.8f), start, end, flag)
                        "a" -> node.attr("href").takeIf { it.isNotEmpty() }?.let { out.setSpan(URLSpan(it), start, end, flag) }
                        "font" -> node.attr("color").takeIf { it.isNotEmpty() }?.let { c ->
                            try {
                                out.setSpan(ForegroundColorSpan(Color.parseColor(c)), start, end, flag)
                            } catch (_: Exception) {
                            }
                        }
                        "h1" -> {
                            out.setSpan(RelativeSizeSpan(1.5f), start, end, flag); out.setSpan(StyleSpan(Typeface.BOLD), start, end, flag)
                        }
                        "h2" -> {
                            out.setSpan(RelativeSizeSpan(1.4f), start, end, flag); out.setSpan(StyleSpan(Typeface.BOLD), start, end, flag)
                        }
                        "h3", "h4", "h5", "h6" -> out.setSpan(StyleSpan(Typeface.BOLD), start, end, flag)
                    }
                }
                when (tag) {
                    "p", "div", "h1", "h2", "h3", "h4", "h5", "h6" -> ensureNewlines(out, if (compact) 1 else 2)
                    "ul", "ol", "li", "blockquote" -> ensureNewlines(out, 1)
                }
            }
        }
    }

    @JvmStatic
    fun toHtml(text: Spanned?): String = toHtml(text, TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)

    @JvmStatic
    fun toHtml(text: Spanned?, option: Int): String = TextUtils.htmlEncode(text?.toString() ?: "").replace("\n", "<br>\n")

    @JvmStatic
    fun escapeHtml(text: CharSequence?): String = TextUtils.htmlEncode(text?.toString() ?: "")
}
