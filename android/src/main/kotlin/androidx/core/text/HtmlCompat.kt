package androidx.core.text

import android.text.Html
import android.text.Spanned

object HtmlCompat {
    const val FROM_HTML_MODE_LEGACY = 0
    const val FROM_HTML_MODE_COMPACT = 63
    const val FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH = 1
    const val TO_HTML_PARAGRAPH_LINES_CONSECUTIVE = 0

    @JvmStatic
    fun fromHtml(source: String, flags: Int): Spanned = Html.fromHtml(source, flags)

    @JvmStatic
    fun fromHtml(source: String, flags: Int, imageGetter: Html.ImageGetter?, tagHandler: Html.TagHandler?): Spanned =
        Html.fromHtml(source, flags, imageGetter, tagHandler)
}
