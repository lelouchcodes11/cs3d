package com.lagradost.desktop.isolate

import android.util.Log
import com.fasterxml.jackson.databind.JsonNode
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.desktop.runtime.web.CloudflareGate
import com.lagradost.desktop.runtime.web.HeadlessBrowser
import com.lagradost.desktop.runtime.web.PrivateNetworkGuard
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A page that the app's own browser fetches for an extension in the sandbox, when a Cloudflare check refused the extension's request although the check was passed in the browser:
 * Cloudflare ties a clearance to the handshake of the browser that earned it, which the HTTP client of the sandbox does not have, so the same cookie is good for the first request
 * (AnimePahe's Home) and refused for the next (its search). The browser has the right handshake and the cookies, so it loads the address and the extension gets what the page shows.
 *
 * Only a GET of a public address, at most a few at a time, for a site whose check the user's rules allow ([CloudflareGate.allowsSolving]); the extension gets the text of the
 * page (a JSON document or the HTML), never the cookies of the browser.
 */
internal object BrowserFetch {
    private const val TAG = "BrowserFetch"
    private const val MAX_CHARS = 8_000_000
    private val permits = Semaphore(3)

    class Page(val code: Int, val type: String, val text: String)

    /** The state of the page as the script below reports it */
    private class State(val ready: String, val challenge: Boolean, val type: String, val length: Int)

    private const val STATE_SCRIPT = "(function(){var c=!!(window._cf_chl_opt||document.querySelector('#challenge-running,#challenge-form,#challenge-stage,.cf-browser-verification'))||/just a moment|un instant|attention required/i.test(document.title||'');" +
        "return JSON.stringify({r:document.readyState,c:c,t:document.contentType||'',n:document.body?document.body.innerText.length:0});})()"

    private const val CONTENT_SCRIPT = "(function(){var t=document.contentType||'';if(t.indexOf('json')>=0||t.indexOf('text/plain')>=0)return document.body?document.body.innerText:'';return document.documentElement.outerHTML;})()"

    /** [url] loaded by the browser: the page when it passed, a 403 page when no clearance came in time (or the rules do not allow a check for this site) */
    suspend fun get(url: String, timeoutMs: Long = CloudflareGate.UNATTENDED_MS + 5_000): Page = permits.withPermit {
        var browser: HeadlessBrowser? = null
        try {
            browser = HeadlessBrowser(userAgent = null, onRequest = { r ->
                // the page may not be used to reach this PC or its network, whatever it redirects to
                if (PrivateNetworkGuard.isAllowed(r.url)) HeadlessBrowser.Decision.CONTINUE else HeadlessBrowser.Decision.BLOCK
            })
            browser.load(url)
            val found = withTimeoutOrNull(timeoutMs) {
                var page: Page? = null
                while (page == null) {
                    delay(450)
                    val s = state(browser) ?: continue
                    if (s.ready == "complete" && !s.challenge && s.length > 0) {
                        val text = evaluate(browser, CONTENT_SCRIPT) ?: continue
                        page = Page(200, s.type.ifBlank { "text/html" }, text.take(MAX_CHARS))
                    }
                }
                page
            }
            found ?: Page(403, "text/html", "")
        } finally {
            // the page is closed on the thread that owns the browser
            val b = browser
            if (b != null) main { runCatching { b.destroy() } }
        }
    }

    private suspend fun evaluate(browser: HeadlessBrowser, script: String): String? {
        val out = CompletableDeferred<String?>()
        runCatching { browser.evaluate(script) { json -> out.complete(runCatching { mapper.readTree(json).takeIf { it.isTextual }?.asText() }.getOrNull()) } }
            .onFailure { out.complete(null) }
        return withTimeoutOrNull(5_000) { out.await() }
    }

    private suspend fun state(browser: HeadlessBrowser): State? {
        val text = evaluate(browser, STATE_SCRIPT) ?: return null
        val n: JsonNode = runCatching { mapper.readTree(text) }.getOrNull() ?: return null
        return State(n.get("r")?.asText().orEmpty(), n.get("c")?.asBoolean() == true, n.get("t")?.asText().orEmpty(), n.get("n")?.asInt() ?: 0)
    }

    /** What the sandbox may ask: the address, as a page or "no" (code 0: the rules do not let the app open a page for that site) */
    suspend fun serve(b: JsonNode): JsonNode {
        val url = b.get("url")?.asText().orEmpty()
        require(url.length <= 8_000 && (url.startsWith("https://") || url.startsWith("http://"))) { "not a web address" }
        require(PrivateNetworkGuard.isAllowed(url)) { "that address is not allowed" }
        val host = runCatching { java.net.URI(url).host }.getOrNull() ?: error("no host")
        val out = mapper.createObjectNode()
        if (!CloudflareGate.allowsSolving(host)) return out.put("code", 0)
        val job = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
        val solve = CloudflareGate.beginSolve(host) { job?.cancel() }
        try {
            val page = get(url)
            if (page.code != 200) CloudflareGate.markFailed(host)
            Log.i(TAG, "${page.code} for $host (${page.text.length} characters)")
            return out.put("code", page.code).put("type", page.type).put("text", page.text)
        } finally {
            CloudflareGate.endSolve(solve)
        }
    }
}
