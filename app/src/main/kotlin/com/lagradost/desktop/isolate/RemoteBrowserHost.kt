package com.lagradost.desktop.isolate

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.RemoteBridge
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.mapper
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.runtime.web.CloudflareGate
import com.lagradost.desktop.runtime.web.PrivateNetworkGuard
import com.lagradost.desktop.runtime.web.WebCookie
import com.lagradost.desktop.runtime.web.WebCookies
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * The app's side of the browser of the extension sandbox (see [WorkerBrowser]): for each WebView that an extension builds in the sandbox process the app makes a real
 * page (WebView2), hidden, does what the extension asks of it and sends back what happens in it. When an extension shows a dialog with a page in it (a Cloudflare
 * check that the user must pass) the page is shown in a window of the app, under the same rules as for an extension that runs in the app ([CloudflareGate]).
 *
 * What comes from the sandbox is data of an untrusted program and is checked here: only http(s) pages are opened, scripts, headers, cookies and the number of pages are
 * bounded, cookies are accepted only for the site of the page that is opened, and the page's pipe to the extension (JavascriptInterface) carries text only.
 */
object RemoteBrowserHost {
    private const val TAG = "RemoteBrowserHost"
    private const val MAX_PAGES = 12
    private const val MAX_PER_EXTENSION = 6
    private const val MAX_SCRIPT = 2_000_000
    private const val MAX_DATA = 4_000_000
    private val idPattern = Regex("r[0-9]{1,9}")
    private val namePattern = Regex("[A-Za-z_\$][A-Za-z0-9_\$]{0,63}")

    /** What the extension's own clients answer to (the page only asks the sandbox about what it overrides, as on the phone) */
    private class Flags {
        @Volatile var intercept = false
        @Volatile var override = false
        @Volatile var loadResource = false
        @Volatile var httpError = false
        @Volatile var console = false
        @Volatile var alert = false
        @Volatile var confirm = false
        @Volatile var prompt = false
    }

    private class Page(val id: String, val path: String) {
        lateinit var view: WebView
        val flags = Flags()
        @Volatile var dialog: android.app.Dialog? = null
        @Volatile var closed = false

        /** When the page first showed a Cloudflare check that it has not passed since (0: it does not show one); the window for it opens if the page does not pass by itself */
        @Volatile var challengeSince = 0L
        @Volatile var autoShown = false
        @Volatile var refusedByUser = false
        private val queue = Executors.newSingleThreadExecutor { r -> Thread(r, "wvhost-$id").also { it.isDaemon = true } }

        /** Work on the page's queue, in order with its events */
        fun later(block: () -> Unit) {
            if (!closed) queue.execute { runCatching(block) }
        }

        /** An event for the extension, in the order the page made them */
        fun send(event: String, data: ObjectNode = mapper.createObjectNode()) {
            if (closed) return
            queue.execute { ask(event, data, 15_000) }
        }

        /** An event that asks the extension something and waits for the answer (null: no answer) */
        fun ask(event: String, data: ObjectNode, timeoutMs: Long): JsonNode? {
            val peer = rpcProvider() ?: return null
            val body = mapper.createObjectNode().put("id", id).put("event", event).also { it.set<JsonNode>("data", data) }
            return runCatching { runBlocking { peer.call("wvEvent", body, timeoutMs) } }.getOrNull()?.takeIf { !it.isNull }
        }

        fun shutdown() { queue.shutdown() }
    }

    private val pages = ConcurrentHashMap<String, Page>()
    private val main = Handler(Looper.getMainLooper())

    /** The sandbox's pipe and the extensions it holds: the sandbox host's, and what the tests put in their place */
    @Volatile internal var rpcProvider: () -> RpcPeer? = { IsolatedHost.rpcOrNull() }
    @Volatile internal var owns: (String) -> Boolean = { IsolatedHost.ownsOrLoads(it) }

    private suspend fun <T> onMain(timeoutMs: Long = 20_000, block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val done = CompletableDeferred<Result<T>>()
        main.post { done.complete(runCatching(block)) }
        return withTimeout(timeoutMs) { done.await() }.getOrThrow()
    }

    // ---- what the sandbox asks ----------------------------------------------------------------------------------------------------------------------

    suspend fun serve(b: JsonNode): JsonNode? {
        val id = b.get("id")?.asText().orEmpty()
        require(idPattern.matches(id)) { "not a page" }
        val op = b.get("op")?.asText().orEmpty()
        if (op == "open") return open(id, b.get("path")?.asText().orEmpty())
        val page = pages[id] ?: error("no page $id")
        return when (op) {
            "client" -> {
                page.flags.apply {
                    intercept = b.get("intercept")?.asBoolean() == true; override = b.get("override")?.asBoolean() == true
                    loadResource = b.get("loadResource")?.asBoolean() == true; httpError = b.get("httpError")?.asBoolean() == true
                    console = b.get("console")?.asBoolean() == true; alert = b.get("alert")?.asBoolean() == true
                    confirm = b.get("confirm")?.asBoolean() == true; prompt = b.get("prompt")?.asBoolean() == true
                }
                null
            }
            "settings" -> { onMain { applySettings(page.view, b.get("settings")) }; null }
            "load" -> { onMain { load(page, b) }; null }
            "loadData" -> { onMain { loadData(page, b) }; null }
            "reload" -> { onMain { page.view.reload() }; null }
            "stop" -> { onMain { page.view.stopLoading() }; null }
            "back" -> { onMain { page.view.goBack() }; null }
            "forward" -> { onMain { page.view.goForward() }; null }
            "clearCache" -> { onMain { page.view.clearCache(true) }; null }
            "clearStorage" -> null
            "bridge" -> { onMain { bridge(page, b) }; null }
            "unbridge" -> { onMain { page.view.removeJavascriptInterface(b.get("name")?.asText().orEmpty()) }; null }
            "mouse" -> { onMain { mouse(page, b) }; null }
            "eval" -> {
                val script = b.get("script")?.asText().orEmpty()
                require(script.length <= MAX_SCRIPT) { "script too long" }
                // what the page is on now (a name that was public when it was opened may have been turned toward this PC since)
                val current = onMain { page.view.getUrl() }
                require(current == null || PrivateNetworkGuard.isAllowed(current)) { "the page is on an address that is not allowed" }
                val result = CompletableDeferred<String?>()
                onMain { page.view.evaluateJavascript(script) { result.complete(it) } }
                mapper.createObjectNode().put("v", withTimeout(80_000) { result.await() })
            }
            "destroy" -> { close(page); null }
            else -> error("unknown page operation $op")
        }
    }

    private suspend fun open(id: String, path: String): JsonNode {
        val ok = owns(path) && pages.size < MAX_PAGES && pages.values.count { it.path == path } < MAX_PER_EXTENSION && !pages.containsKey(id)
        // the page of a sandboxed extension is only safe on the engine whose requests the app can all look at (WebView2)
        if (!ok || !com.lagradost.desktop.runtime.web.WebRuntime.usesWebView2) return mapper.createObjectNode().put("ok", false)
        val page = Page(id, path)
        val made = runCatching {
            onMain {
                page.view = WebView(DesktopBootstrap.activity).also { v ->
                    v.getSettings().setAllowFileAccess(false)
                    v.networkGuard = PrivateNetworkGuard::isAllowed
                    v.setWebViewClient(forwardClient(page))
                    v.setWebChromeClient(forwardChrome(page))
                }
            }
        }.onFailure { Log.w(TAG, "no page could be made for ${File(path).name}: $it") }.isSuccess
        if (made) pages[id] = page else page.shutdown()
        return mapper.createObjectNode().put("ok", made)
    }

    private fun requireWeb(url: String) {
        require(url.length <= 8_000 && (url.startsWith("https://") || url.startsWith("http://") || url == "about:blank")) { "not a web address" }
        // not this PC, not the local network: the sandbox may not use the app's browser to get there
        require(PrivateNetworkGuard.isAllowed(url)) { "that address is not allowed" }
    }

    private fun applySettings(view: WebView, s: JsonNode?) {
        if (s == null || !s.isObject) return
        view.getSettings().apply {
            setJavaScriptEnabled(s.get("js")?.asBoolean() == true)
            setDomStorageEnabled(s.get("dom")?.asBoolean() == true)
            s.get("ua")?.takeIf { it.isTextual && it.asText().length <= 600 }?.let { setUserAgentString(it.asText()) }
            setBlockNetworkImage(s.get("blockImages")?.asBoolean() == true)
            setLoadsImagesAutomatically(s.get("blockImages")?.asBoolean() != true)
            setBlockNetworkLoads(s.get("blockNetwork")?.asBoolean() == true)
        }
    }

    private fun host(url: String): String? = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull()

    private fun load(page: Page, b: JsonNode) {
        val url = b.get("url")?.asText().orEmpty()
        requireWeb(url)
        applySettings(page.view, b.get("settings"))
        // the cookies the extension holds for this site go into the app's jar, for this site only
        val site = host(url)
        b.get("cookies")?.takeIf { it.isArray }?.take(200)?.forEach { c ->
            runCatching {
                val domain = c.get("domain").asText()
                val d = domain.trimStart('.').lowercase()
                if (site != null && (site == d || site.endsWith(".$d"))) {
                    WebCookies.put(
                        WebCookie(c.get("name").asText().take(256), c.get("value").asText().take(4096), domain, c.get("path")?.asText() ?: "/", c.get("expires")?.takeIf { !it.isNull }?.asLong(), c.get("secure")?.asBoolean() == true, c.get("httpOnly")?.asBoolean() == true),
                    )
                }
            }
        }
        val headers = HashMap<String, String>()
        b.get("headers")?.fields()?.asSequence()?.take(40)?.forEach { (k, v) -> if (k.length <= 100 && v.asText().length <= 4_000) headers[k] = v.asText() }
        val post = b.get("post")?.asText()?.takeIf { it.isNotEmpty() }?.let { Base64.getDecoder().decode(it) }
        if (post != null) {
            require(post.size <= MAX_DATA) { "too much data" }
            page.view.postUrl(url, post)
        } else page.view.loadUrl(url, headers)
    }

    private fun loadData(page: Page, b: JsonNode) {
        val base = b.get("base")?.asText().orEmpty()
        requireWeb(base)
        val data = b.get("data")?.asText().orEmpty()
        require(data.length <= MAX_DATA) { "too much data" }
        applySettings(page.view, b.get("settings"))
        page.view.loadDataWithBaseURL(base, data, b.get("mime")?.asText()?.take(100) ?: "text/html", b.get("encoding")?.asText()?.take(40) ?: "utf-8", null)
    }

    private fun bridge(page: Page, b: JsonNode) {
        val name = b.get("name")?.asText().orEmpty()
        require(namePattern.matches(name)) { "not a name" }
        val methods = HashMap<String, Set<Int>>()
        b.get("methods")?.takeIf { it.isArray }?.take(64)?.forEach { m ->
            val n = m.get("name")?.asText().orEmpty()
            if (namePattern.matches(n)) methods[n] = m.get("counts")?.map { it.asInt() }?.toSet().orEmpty()
        }
        page.view.addJavascriptInterface(
            RemoteBridge(methods) { method, args ->
                // the page's call runs in the extension; its answer is the page's ({"v": result} or {"e": error})
                page.ask("bridge", mapper.createObjectNode().put("name", name).put("method", method).put("args", args.take(1_000_000)), 60_000)?.toString() ?: "{\"e\":\"no answer\"}"
            },
            name,
        )
    }

    private fun mouse(page: Page, b: JsonNode) {
        val action = b.get("action")?.asInt() ?: return
        val x = (b.get("x")?.asDouble() ?: 0.0).toFloat()
        val y = (b.get("y")?.asDouble() ?: 0.0).toFloat()
        val time = b.get("eventTime")?.asLong() ?: System.currentTimeMillis()
        val event = if (action == MotionEvent.ACTION_SCROLL) MotionEvent.obtainScroll(time, x, y, (b.get("vscroll")?.asDouble() ?: 0.0).toFloat())
        else MotionEvent.obtain(b.get("downTime")?.asLong() ?: time, time, action, x, y, 0)
        try { page.view.dispatchTouchEvent(event) } finally { event.recycle() }
    }

    // ---- what the page does: sent to the extension ---------------------------------------------------------------------------------------------------

    private fun state(view: WebView, url: String?): ObjectNode =
        mapper.createObjectNode().put("url", view.getUrl() ?: url).put("back", view.canGoBack()).put("forward", view.canGoForward()).put("title", view.getTitle()).put("ua", view.getSettings().getUserAgentString())

    private fun requestNode(r: WebResourceRequest): ObjectNode =
        mapper.createObjectNode().put("url", r.url.toString()).put("method", r.method).put("main", r.isForMainFrame).put("redirect", r.isRedirect).put("gesture", r.hasGesture())
            .also { n -> n.set<JsonNode>("headers", mapper.valueToTree(r.requestHeaders.entries.take(60).associate { it.key to it.value.take(2_000) })) }

    private fun cookiesFor(url: String?): JsonNode {
        val site = url?.let { host(it) } ?: return mapper.createArrayNode()
        return mapper.valueToTree(WebCookies.all().filter { c -> val d = c.domain.trimStart('.').lowercase(); site == d || site.endsWith(".$d") })
    }

    private fun forwardClient(page: Page) = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            if (view != null) page.send("started", state(view, url))
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            if (view == null) return
            noteTitle(page, view.getTitle())
            val node = state(view, url).put("height", view.getContentHeight())
            val target = view.getUrl() ?: url
            // the cookies are read from the browser itself on the page's queue (not on the main thread): the clearance a check has just given is what the extension must see
            page.later {
                if (target != null) runCatching { WebCookies.freshHeader(target) }
                node.set<JsonNode>("cookies", cookiesFor(target))
                page.ask("finished", node, 15_000)
            }
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            page.send("history", state(view, url))
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            page.send("error", requestNode(request).put("code", error.getErrorCode()).put("description", error.getDescription().toString().take(300)))
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
            if (page.flags.httpError) page.send("httpError", requestNode(request).put("status", errorResponse.getStatusCode()).put("reason", errorResponse.getReasonPhrase()).put("mime", errorResponse.getMimeType()))
        }

        override fun onLoadResource(view: WebView?, url: String?) {
            if (page.flags.loadResource) page.send("loadResource", mapper.createObjectNode().put("url", url))
        }

        // called by the page's engine on its own threads: the answer is waited for, briefly
        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
            if (request == null || !page.flags.intercept) return null
            val r = page.ask("intercept", requestNode(request), 4_000) ?: return null
            val body = runCatching { Base64.getDecoder().decode(r.get("body")?.asText().orEmpty()) }.getOrDefault(ByteArray(0))
            val headers = HashMap<String, String>()
            r.get("headers")?.fields()?.forEach { (k, v) -> headers[k] = v.asText() }
            return WebResourceResponse(r.get("mime")?.asText() ?: "text/plain", r.get("enc")?.asText() ?: "utf-8", r.get("status")?.asInt() ?: 200, r.get("reason")?.asText() ?: "OK", headers, ByteArrayInputStream(body))
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            if (request == null || !page.flags.override) return false
            return page.ask("override", requestNode(request), 3_000)?.get("v")?.asBoolean() == true
        }
    }

    private fun forwardChrome(page: Page) = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            page.send("progress", mapper.createObjectNode().put("p", newProgress))
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            noteTitle(page, title)
            page.send("title", mapper.createObjectNode().put("t", title))
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            if (page.flags.console) {
                page.send(
                    "console",
                    mapper.createObjectNode().put("message", consoleMessage.message()?.take(20_000)).put("source", consoleMessage.sourceId()?.take(500)).put("line", consoleMessage.lineNumber())
                        .put("level", consoleMessage.messageLevel().name),
                )
            }
            return false
        }

        private fun answer(flag: Boolean, event: String, url: String?, message: String?, default: String? = null, result: JsResult): Boolean {
            if (!flag) return false
            val r = page.ask(event, mapper.createObjectNode().put("url", url).put("message", message?.take(5_000)).put("default", default), 65_000) ?: return false
            if (r.get("handled")?.asBoolean() != true) return false
            if (r.get("ok")?.asBoolean() == true) { if (result is JsPromptResult && r.get("text")?.isNull == false) result.confirm(r.get("text").asText()) else result.confirm() } else result.cancel()
            return true
        }

        override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult) = answer(page.flags.alert, "alert", url, message, null, result)
        override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult) = answer(page.flags.confirm, "confirm", url, message, null, result)
        override fun onJsPrompt(view: WebView, url: String?, message: String?, defaultValue: String?, result: JsPromptResult) = answer(page.flags.prompt, "prompt", url, message, defaultValue, result)
    }

    // ---- the window ------------------------------------------------------------------------------------------------------------------------------

    /** An extension showed a dialog with a page in it: the page is shown here, when the rules for such windows allow it (false: not shown) */
    suspend fun dialogShow(b: JsonNode): Boolean {
        val page = pages[b.get("id")?.asText().orEmpty()] ?: return false
        val names = APIHolder.allProviders.toList().filter { it.sourcePlugin == page.path }.map { it.name }.toSet()
        val allowed = CloudflareGate.allowsDialogFor(names, File(page.path).name)
        Log.i(TAG, "a window for a page of ${File(page.path).name} (${names.joinToString()}): " + if (allowed) "shown" else "refused (solvers off or the extension is not the one in use)")
        if (!allowed) return false
        return onMain { showWindow(page, names.firstOrNull()) }
    }

    suspend fun dialogHide(b: JsonNode) {
        val page = pages[b.get("id")?.asText().orEmpty()] ?: return
        onMain { hideWindow(page) }
    }

    private val challengeTitle = Regex("just a moment|attention required|verify you are human|checking your browser|one more step", RegexOption.IGNORE_CASE)

    /**
     * What the page's title says. An extension's check often runs in a dialog that the sandbox cannot show (the dialog is a fragment of an activity that has no screen
     * there), so the page runs hidden; most checks pass by themselves within seconds. One that is still on screen after [CHALLENGE_SHOW_MS] is shown to the user in a
     * window of the app (under the same rules as any such window), and the window goes when the page has passed. Called on the main thread.
     */
    private fun noteTitle(page: Page, title: String?) {
        if (page.closed || title.isNullOrBlank()) return
        if (challengeTitle.containsMatchIn(title)) {
            if (page.challengeSince != 0L) return
            page.challengeSince = System.currentTimeMillis()
            main.postDelayed({ showChallenge(page) }, CHALLENGE_SHOW_MS)
        } else {
            page.challengeSince = 0L
            if (page.autoShown) { page.autoShown = false; hideWindow(page) }
        }
    }

    private fun showChallenge(page: Page) {
        if (page.closed || page.challengeSince == 0L || page.dialog != null || page.refusedByUser) return
        if (System.currentTimeMillis() - page.challengeSince < CHALLENGE_SHOW_MS - 500) return
        val names = APIHolder.allProviders.toList().filter { it.sourcePlugin == page.path }.map { it.name }.toSet()
        val allowed = CloudflareGate.allowsDialogFor(names, File(page.path).name)
        Log.i(TAG, "the check of ${names.joinToString()} is still on after ${CHALLENGE_SHOW_MS / 1000} s: " + if (allowed) "its page is shown" else "not shown (solvers off or the extension is not the one in use)")
        if (!allowed) {
            // looked at again later: the user may switch to that extension
            main.postDelayed({ showChallenge(page) }, 3_000)
            return
        }
        page.autoShown = true
        showWindow(page, names.firstOrNull())
    }

    private fun showWindow(page: Page, extension: String?): Boolean {
        if (page.dialog != null) return true
        val ctx = DesktopBootstrap.activity
        val root = android.widget.LinearLayout(ctx)
        root.setOrientation(android.widget.LinearLayout.VERTICAL)
        val title = android.widget.TextView(ctx)
        title.setText(if (extension != null) com.lagradost.desktop.i18n.Tr.f("{0} needs you to pass a check", extension) else com.lagradost.desktop.i18n.Tr.t("An extension needs you to pass a check"))
        title.setPadding(24, 16, 24, 16)
        title.setTextSize(16f)
        root.addView(title)
        (page.view.getParent() as? ViewGroup)?.removeView(page.view)
        root.addView(page.view, android.widget.LinearLayout.LayoutParams(WINDOW_WIDTH, WINDOW_HEIGHT))
        val cancel = android.widget.Button(ctx)
        cancel.setText(com.lagradost.desktop.i18n.Tr.t("Cancel"))
        cancel.setOnClickListener { userCancelled(page) }
        root.addView(cancel)
        val dialog = android.app.Dialog(ctx)
        dialog.setContentView(root)
        dialog.setOnCancelListener { userCancelled(page) }
        page.dialog = dialog
        dialog.show()
        // the app's own rules for such windows (not over and over) may have refused it: then there is no window to keep
        if (!dialog.isShowing()) { page.dialog = null; (page.view.getParent() as? ViewGroup)?.removeView(page.view); return false }
        // the extension lays its own clicks out by the size of its view (Turnstile's box): it is told the size the page really has
        for (delay in longArrayOf(600, 1_800)) main.postDelayed({
            val v = page.view
            if (!page.closed && page.dialog != null && v.getWidth() > 0 && v.getHeight() > 0) page.send("size", mapper.createObjectNode().put("w", v.getWidth()).put("h", v.getHeight()))
        }, delay)
        return true
    }

    private fun hideWindow(page: Page) {
        val dialog = page.dialog ?: return
        page.dialog = null
        runCatching { dialog.setOnCancelListener(null) }
        runCatching { dialog.dismiss() }
        (page.view.getParent() as? ViewGroup)?.removeView(page.view)
    }

    private fun userCancelled(page: Page) {
        if (page.dialog == null) return
        page.refusedByUser = true // the window is not put up again for this page
        hideWindow(page)
        val peer = rpcProvider() ?: return
        Thread({ runCatching { runBlocking { peer.call("dialogCancel", mapper.createObjectNode().put("id", page.id), 10_000) } } }, "wvhost-cancel").apply { isDaemon = true }.start()
    }

    // ---- ending ---------------------------------------------------------------------------------------------------------------------------------

    private fun close(page: Page) {
        if (pages.remove(page.id, page).not()) return
        page.closed = true
        main.post {
            hideWindow(page)
            runCatching { page.view.destroy() }
            page.shutdown()
        }
    }

    /** The sandbox ended, or the extension was unloaded: its pages go with it */
    fun closeFor(path: String?) {
        for (p in pages.values.toList()) if (path == null || p.path == path) close(p)
    }

    /** A check that is still on this long after it appeared is shown to the user */
    private const val CHALLENGE_SHOW_MS = 6_000L

    private const val WINDOW_WIDTH = 760
    private const val WINDOW_HEIGHT = 560
}
