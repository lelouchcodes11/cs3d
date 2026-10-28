package com.lagradost.desktop.ui.fluent

import androidx.compose.ui.text.TextLayoutResult
import java.util.concurrent.ConcurrentHashMap

/**
 * Dev tool: with `-Dcloudstream.textaudit=1` every [FText] reports when its text did not fit (cut by an ellipsis or clipped), and the dev server's
 * `/textaudit` lists them. A translation that is longer than the English text it replaces shows up here instead of in a screenshot of every page in
 * every language. Off by default: a normal run adds nothing to any text.
 */
object TextAudit {
    val on: Boolean = System.getProperty("cloudstream.textaudit") == "1"

    private val cut = ConcurrentHashMap<String, Int>()

    /** The layout callback to give a text, or null when the audit is off */
    internal fun watcher(shown: String, maxLines: Int): ((TextLayoutResult) -> Unit)? =
        if (!on) null else { r -> if (r.hasVisualOverflow) cut.merge("$shown  [max $maxLines line(s), ${r.lineCount} laid out]", 1, Int::plus) }

    fun clear() = cut.clear()

    fun report(): String = if (!on) "off: start the app with -Dcloudstream.textaudit=1" else
        cut.keys.sorted().joinToString("\n").ifEmpty { "nothing was cut off" }
}
