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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * A page that the app's own browser fetches for an extension in the sandbox, when a Cloudflare check refused the extension's request although the check was passed in the browser:
 * Cloudflare ties a clearance to the handshake of the browser that earned it, which the HTTP client of the sandbox does not have, so the same cookie is good for the first request
 * (AnimePahe's Home) and refused for the next (its search). The browser has the right handshake and the cookies.
 *
 * The first request of a site loads its address in a hidden page (the check is passed there, if it can be passed without the user) and the page stays open, idle for a few minutes at
 * most: every later request of that site is one `fetch` inside that page, a fraction of a second, instead of a page of its own (AnimePahe asks for ten addresses at once when its
 * Home opens: ten page loads queued behind each other kept a bar on the screen until the user pressed Cancel). A site that is asked several times while its page is being made waits
 * for that page.
 *
 * Only a GET of a public address, for a site whose check the user's rules allow ([CloudflareGate.allowsSolving]); the extension gets the text of the answer (a JSON document or
 * the HTML), never the cookies of the browser.
 */
internal object BrowserFetch {
    private const val TAG = "BrowserFetch"
    private const val MAX_CHARS = 8_000_000
    private const val IDLE_MS = 5 * 60_000L
    private val pageLoads = Semaphore(3)
    private val inPage = Semaphore(8)

    class Page(val code: Int, val type: String, val text: String)

    /** A hidden page that is open on [origin] with the clearance in it */
    private class Session(val origin: String, val browser: HeadlessBrowser) {
        @Volatile var lastUsed = System.currentTimeMillis()
    }

    private val sessions = ConcurrentHashMap<String, Session>()
    private val making = ConcurrentHashMap<String, Mutex>()

    /** The state of the page as the script below reports it */
    private class State(val ready: String, val challenge: Boolean, val type: String, val length: Int)

    private const val CHALLENGE = "!!(window._cf_chl_opt||document.querySelector('#challenge-running,#challenge-form,#challenge-stage,.cf-browser-verification'))||/just a moment|un instant|attention required/i.test(document.title||'')"
    private const val STATE_SCRIPT = "(function(){return JSON.stringify({r:document.readyState,c:$CHALLENGE,t:document.contentType||'',n:document.body?document.body.innerText.length:0});})()"
    private const val CONTENT_SCRIPT = "(function(){var t=document.contentType||'';if(t.indexOf('json')>=0||t.indexOf('text/plain')>=0)return document.body?document.body.innerText:'';return document.documentElement.outerHTML;})()"

    private fun originOf(url: String): String? = runCatching { java.net.URI(url).let { "${it.scheme}://${it.host}" + (if (it.port > 0) ":${it.port}" else "") } }.getOrNull()

    private val janitor by lazy {
        Thread({
            while (true) {
                try { Thread.sleep(60_000) } catch (_: InterruptedException) { return@Thread }
                val now = System.currentTimeMillis()
                for ((host, s) in sessions) if (now - s.lastUsed > IDLE_MS && sessions.remove(host, s)) close(s.browser)
            }
        }, "browser-fetch-idle").apply { isDaemon = true; start() }
    }

    private fun close(browser: HeadlessBrowser) = main { runCatching { browser.destroy() } }

    /** [url] answered by the browser: a page of the site that is open, or a new page; a 403 when no clearance came in time */
    suspend fun get(url: String, host: String, timeoutMs: Long = CloudflareGate.UNATTENDED_MS + 5_000): Page {
        janitor
        sessions[host]?.let { s -> inOpenPage(s, host, url)?.let { return it } }
        // one page per site at a time: the others wait for it and ask it (see the description)
        val gate = making.getOrPut(host) { Mutex() }
        return gate.withLock {
            sessions[host]?.let { s -> inOpenPage(s, host, url)?.let { return@withLock it } }
            val job = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
            // the bar with Cancel is for a page that has to be made, which takes seconds; a request in an open page takes none
            val solve = CloudflareGate.beginSolve(host) { job?.cancel() }
            try { loadPage(url, host, timeoutMs) } finally { CloudflareGate.endSolve(solve) }
        }
    }

    /** One `fetch` in the page that is open (null: the page does not serve this address, or the answer was a Cloudflare check: a new page is made) */
    private suspend fun inOpenPage(s: Session, host: String, url: String): Page? = inPage.withPermit {
        if (originOf(url) != s.origin || s.browser.destroyed) { sessions.remove(host, s); return@withPermit null }
        s.lastUsed = System.currentTimeMillis()
        val literal = mapper.writeValueAsString(url)
        val id = evaluate(s.browser, "(function(){var i='f'+Math.random().toString(36).slice(2);window.__bf=window.__bf||{};window.__bf[i]=null;" +
            "fetch($literal,{credentials:'include',headers:{'Accept':'application/json, text/plain, */*'}}).then(function(r){return r.text().then(function(t){window.__bf[i]={s:r.status,t:r.headers.get('content-type')||'',b:t};});})" +
            ".catch(function(){window.__bf[i]={s:0,t:'',b:''};});return i;})()") ?: return@withPermit null
        val answer = withTimeoutOrNull(20_000) {
            var json: JsonNode? = null
            while (json == null) {
                delay(120)
                val text = evaluate(s.browser, "JSON.stringify(window.__bf['$id']||null)") ?: continue
                json = runCatching { mapper.readTree(text) }.getOrNull()?.takeIf { it.isObject }
            }
            json
        }
        runCatching { s.browser.evaluate("delete window.__bf['$id']", null) }
        if (answer == null) return@withPermit null
        val code = answer.get("s")?.asInt() ?: 0
        val body = answer.get("b")?.asText().orEmpty()
        val refused = code == 0 || ((code == 403 || code == 503) && (body.contains("Just a moment", true) || body.contains("challenge-platform", true) || body.isBlank()))
        if (refused) { sessions.remove(host, s); close(s.browser); return@withPermit null }
        Page(code, answer.get("t")?.asText().orEmpty().ifBlank { "text/html" }, body.take(MAX_CHARS))
    }

    /** [url] loaded in a new hidden page; the page is kept (as the page of the site) when it passed */
    private suspend fun loadPage(url: String, host: String, timeoutMs: Long): Page = pageLoads.withPermit {
        var browser: HeadlessBrowser? = null
        var kept = false
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
            if (found != null) {
                val origin = originOf(url)
                if (origin != null) {
                    sessions.put(host, Session(origin, browser))?.let { close(it.browser) }
                    kept = true
                }
            }
            found ?: Page(403, "text/html", "")
        } finally {
            // a page that was not kept is closed on the thread that owns the browser
            val b = browser
            if (b != null && !kept) close(b)
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

    /** What the sandbox may ask: the address, as the answer of the site (code 0: the rules do not let the app open a page for that site) */
    suspend fun serve(b: JsonNode): JsonNode {
        val url = b.get("url")?.asText().orEmpty()
        require(url.length <= 8_000 && (url.startsWith("https://") || url.startsWith("http://"))) { "not a web address" }
        require(PrivateNetworkGuard.isAllowed(url)) { "that address is not allowed" }
        val host = runCatching { java.net.URI(url).host }.getOrNull() ?: error("no host")
        val out = mapper.createObjectNode()
        if (!CloudflareGate.allowsSolving(host)) return out.put("code", 0)
        val started = System.currentTimeMillis()
        val page = get(url, host)
        if (page.code == 403) CloudflareGate.markFailed(host)
        Log.i(TAG, "${page.code} for $host (${page.text.length} characters, ${System.currentTimeMillis() - started} ms)")
        return out.put("code", page.code).put("type", page.type).put("text", page.text)
    }
}
