package com.lagradost.desktop.ui.components

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.isolate.Isolation
import com.lagradost.desktop.ui.Toasts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * What went wrong, said for a person. Extensions and the network hand the pages raw exception text ("Unrecognized token 'https': was expecting (JSON
 * String, ...) JsonParser.java 2675"); a page shows [Problem.text] in plain words and keeps the original as [Problem.detail] in small print, for a
 * report to the extension's author.
 */
object ProblemText {
    class Problem(val text: String, val detail: String?)

    private val technical = Regex(
        """Exception|Error:|\.java\b|\.kt\b|JsonParser|Unrecognized token|MismatchedInput|was expecting|at \[Source|NullPointer|kotlin\.|java\.|Expected BEGIN_|Unexpected (character|token|end)""",
    )

    fun of(raw: String?): Problem {
        val message = raw?.trim().orEmpty()
        if (message.isEmpty()) return Problem("Something went wrong. Try again in a moment.", null)
        val lower = message.lowercase()
        val plain = when {
            "unknownhost" in lower || "unable to resolve host" in lower || "no address associated" in lower ->
                "The site could not be found. Check the internet connection; the site may also be down or blocked on this network."
            "timeout" in lower || "timed out" in lower -> "The site took too long to answer. Try again in a moment."
            "ssl" in lower || "handshake" in lower || "certificate" in lower -> "A secure connection to the site could not be made."
            "connection refused" in lower || "failed to connect" in lower || "connection reset" in lower -> "The site did not accept the connection. It may be down."
            Regex("""\b403\b|cloudflare|just a moment""").containsMatchIn(lower) -> "The site refused the request (it may be checking for bots)."
            Regex("""\b404\b|not found""").containsMatchIn(lower) -> "The site no longer has this page."
            Regex("""\b5\d\d\b""").containsMatchIn(lower) && "http" in lower -> "The site has a problem of its own right now. Try again later."
            technical.containsMatchIn(message) ->
                "The extension got an answer it could not read. The site may have changed; an update of the extension usually fixes it."
            else -> return Problem(message, null)
        }
        return Problem(plain, message.take(400))
    }

    /** A source's message that asks the user to set it up (Ultima: "Select sections from the extension's settings page to show here.") */
    fun asksForSettings(raw: String?): Boolean = raw != null && Regex("settings page|select sections|configure|set up the", RegexOption.IGNORE_CASE).containsMatchIn(raw)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The settings page of the extension that [apiName] comes from, wherever it runs; false when it has none */
    fun openProviderSettings(apiName: String?): Boolean {
        val path = APIHolder.getApiFromNameNull(apiName)?.sourcePlugin ?: return false
        val ctx = DesktopBootstrap.activity
        val instance = (PluginManager.urlPlugins[path] ?: PluginManager.plugins[path]) as? Plugin
        instance?.openSettings?.let { open ->
            runCatching { open(ctx) }.onFailure { Toasts.show("Could not open settings: ${it.message}", true) }
            return true
        }
        val sandboxed = Isolation.sandboxedPathFor(path) ?: return false
        if (!Isolation.hasSettings(sandboxed)) return false
        scope.launch { if (!Isolation.openSettings(ctx, sandboxed)) Toasts.show("Could not open the settings of $apiName", true) }
        return true
    }
}
