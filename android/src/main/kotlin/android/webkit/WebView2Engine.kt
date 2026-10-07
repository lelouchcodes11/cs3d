package android.webkit

import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.MotionEvent
import com.lagradost.desktop.runtime.web.wv2.Com
import com.lagradost.desktop.runtime.web.wv2.WebView2Runtime
import com.lagradost.desktop.runtime.web.wv2.Wv2Browser
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import org.json.JSONArray
import org.json.JSONObject
import java.awt.EventQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * One android.webkit.WebView on Edge WebView2, with Android's threading: page callbacks on the main (UI) thread, shouldInterceptRequest
 * on a background thread. The page is made on first use; a WebView an extension made and never destroyed is closed after a few idle
 * minutes off screen and reopens its page when it is used again.
 */
internal class WebView2Engine(private val view: WebView) : Wv2Browser(), WebViewBackend {
    companion object {
        private const val TAG = "WebView"
        private const val DEFAULT_WIDTH = 1280
        private const val DEFAULT_HEIGHT = 720
        private val serial = AtomicInteger()
        private val bridgeExecutor = Executors.newCachedThreadPool { r -> Thread(r, "JavaBridge").also { it.isDaemon = true } }
        private val interceptExecutor = Executors.newCachedThreadPool { r -> Thread(r, "webview-intercept").also { it.isDaemon = true } }
    }

    private val id = "w" + serial.incrementAndGet()
    private val main = Handler(Looper.getMainLooper())

    @Volatile override var title: String? = null
    @Volatile override var progress: Int = 0
    @Volatile override var originalUrl: String? = null
    @Volatile override var contentHeight: Int = 0
    @Volatile private var lastUrl: String? = null
    /** The last page the extension itself asked for (what a closed page reopens; ads and redirects move [lastUrl] elsewhere) */
    @Volatile private var loadedUrl: String? = null
    @Volatile private var navigating: String? = null
    @Volatile private var canBack = false
    @Volatile private var canForward = false
    @Volatile private var attached = false
    @Volatile private var destroyed = false

    private val programmatic = ConcurrentHashMap.newKeySet<String>()
    private val pendingData = ConcurrentHashMap<String, Triple<String, String, String>>()
    private val jsInterfaces = ConcurrentHashMap<String, Any>()
    private var bridgeScriptId: String? = null

    @Volatile private var overridesIntercept = false
    @Volatile private var overridesLoadResource = false
    @Volatile private var overridesHttpError = false
    @Volatile private var overridesOverrideUrl = false

    /** The page closed while it was not used: its URL, reopened (before anything else) when it is used again */
    @Volatile private var resumeUrl: String? = null
    private val afterResume = ArrayList<(Pointer) -> Unit>()
    private var resuming = false

    // on screen
    @Volatile private var screenParent: WinDef.HWND? = null
    @Volatile private var wasOnScreen = false
    private var canvas: Canvas? = null

    init {
        clientChanged()
    }

    override fun clientChanged() {
        val c = view.client
        fun overridden(name: String, vararg types: Class<*>): Boolean = try {
            c.javaClass.getMethod(name, *types).declaringClass != WebViewClient::class.java
        } catch (e: NoSuchMethodException) {
            false
        }
        overridesIntercept = overridden("shouldInterceptRequest", WebView::class.java, WebResourceRequest::class.java) ||
                overridden("shouldInterceptRequest", WebView::class.java, String::class.java)
        overridesLoadResource = overridden("onLoadResource", WebView::class.java, String::class.java)
        overridesHttpError = overridden("onReceivedHttpError", WebView::class.java, WebResourceRequest::class.java, WebResourceResponse::class.java)
        overridesOverrideUrl = overridden("shouldOverrideUrlLoading", WebView::class.java, WebResourceRequest::class.java) ||
                overridden("shouldOverrideUrlLoading", WebView::class.java, String::class.java)
    }

    // ------------------------------------------------------------------ page life

    override fun hiddenSize(): Pair<Int, Int> {
        val lp = view.getLayoutParams()
        val w = view.getWidth().takeIf { it > 0 } ?: lp?.width?.takeIf { it > 0 } ?: DEFAULT_WIDTH
        val h = view.getHeight().takeIf { it > 0 } ?: lp?.height?.takeIf { it > 0 } ?: DEFAULT_HEIGHT
        return w to h
    }

    /** Work that needs the page as it was: a closed page is reopened first and the work waits for it to load */
    private fun withPage(action: (Pointer) -> Unit) {
        if (destroyed) return
        withCore { core ->
            val url = resumeUrl
            if (url != null) {
                resumeUrl = null
                resuming = true
                afterResume.add(action)
                Log.i(TAG, "reopening $url")
                navigate(core, url, emptyMap(), null)
            } else if (resuming) {
                afterResume.add(action)
            } else {
                action(core)
            }
        }
    }

    override fun parkIfForgotten(now: Long) {
        if (!isReady || destroyed || screenParent != null || (attached && !wasOnScreen) || resuming) return
        if (now - lastActivity < (if (wasOnScreen) WebView2Runtime.PARK_AFTER_SHOWN_MS else WebView2Runtime.PARK_AFTER_MS)) return
        Log.i(TAG, "closing an unused page (${lastUrl ?: "blank"}), it reopens when used")
        resumeUrl = loadedUrl ?: lastUrl
        bridgeScriptId = null
        park()
    }

    override fun describe(now: Long): String =
        super.describe(now) + " url=${lastUrl ?: "-"} attached=$attached onScreen=${screenParent != null} wasOnScreen=$wasOnScreen destroyed=$destroyed"

    override fun dropAfterCrash() {
        if (state == State.READY) resumeUrl = loadedUrl ?: lastUrl
        bridgeScriptId = null
        super.dropAfterCrash()
    }

    override fun setup(core: Pointer) {
        Com.getPtr(core, 3)?.let { settings ->
            Com.putBool(settings, 8, false) // script dialogs come to WebChromeClient
            Com.putBool(settings, 10, false) // no status bar
            Com.putBool(settings, 18, false) // no Ctrl+wheel zoom
            Com.release(settings)
        }
        view.jcefSettings().userAgent?.let { applyUserAgentNow(core, it) }
        Com.call(core, 57, WString("*"), CONTEXT_ALL)
        on(core, 7, IID_NAVIGATION_STARTING) { _, a -> if (a != null) onNavigationStarting(a) }
        on(core, 9, IID_CONTENT_LOADING) { _, _ -> onContentLoading(core) }
        on(core, 11, IID_SOURCE_CHANGED) { _, _ ->
            val url = source(core) ?: return@on
            if (url == "about:blank") return@on
            lastUrl = url
            main.post { view.client.doUpdateVisitedHistory(view, url, false) }
        }
        on(core, 15, IID_NAVIGATION_COMPLETED) { _, a -> if (a != null) onNavigationCompleted(core, a) }
        on(core, 21, IID_SCRIPT_DIALOG) { _, a -> if (a != null) onScriptDialog(a) }
        on(core, 25, IID_PROCESS_FAILED) { _, a ->
            val kind = a?.let { Com.getInt(it, 3) } ?: -1
            if (kind == 0) rt.post { rt.onBrowserProcessExited() }
            if (kind == 0 || kind == 1) main.post { view.client.onRenderProcessGone(view, RenderProcessGoneDetail()) }
        }
        on(core, 44, IID_NEW_WINDOW) { _, a ->
            if (a == null) return@on
            val url = Com.getString(a, 3)
            Com.putBool(a, 6, true)
            if (!url.isNullOrEmpty()) main.post { handlePopup(url) }
        }
        on(core, 46, IID_TITLE_CHANGED) { _, _ ->
            val t = Com.getString(core, 48)
            title = t
            main.post { view.chromeClient?.onReceivedTitle(view, t) }
        }
        on(core, 55, IID_RESOURCE_REQUESTED) { _, a -> if (a != null) onResourceRequested(a) }
        core2?.let { c2 -> on(c2, 61, IID_RESPONSE_RECEIVED) { _, a -> if (a != null && overridesHttpError) onResponseReceived(a) } }
        Com.query(core, IID_CORE4)?.let { c4 ->
            on(c4, 75, IID_DOWNLOAD_STARTING) { _, a -> if (a != null) onDownloadStarting(a) }
            Com.release(c4)
        }
        Com.query(core, IID_CORE14)?.let { c14 ->
            on(c14, 106, IID_CERTIFICATE_ERROR) { _, a -> if (a != null) onCertificateError(a) }
            Com.release(c14)
        }
        if (overridesConsole()) listenToConsole(core)
        installBridgeScript(core)
        devTools(core, "Emulation.setFocusEmulationEnabled", "{\"enabled\":true}")
        val ua = view.jcefSettings().getUserAgentString()
        if (ua.contains("Mobile") || ua.contains("Android")) {
            val (w, h) = hiddenSize()
            val scale = screenScale()
            devTools(
                core, "Emulation.setDeviceMetricsOverride",
                JSONObject().put("width", max(1, (w / scale).roundToInt())).put("height", max(1, (h / scale).roundToInt()))
                    .put("deviceScaleFactor", scale).put("mobile", true).toString(),
            )
        }
    }

    // ------------------------------------------------------------------ navigation

    override fun currentUrl(): String? = lastUrl

    override fun load(url: String, headers: Map<String, String>, postData: ByteArray?) {
        if (destroyed) return
        if (debug) Log.i(TAG, "$id load $url (state $state)")
        if (originalUrl == null) originalUrl = url
        loadedUrl = url
        programmatic.add(url)
        withCore { core ->
            // a new page replaces the one a closed page would have reopened
            resumeUrl = null
            navigate(core, url, headers, postData)
        }
    }

    override fun loadDataAt(baseUrl: String, data: String, mime: String, encoding: String) {
        pendingData[baseUrl] = Triple(data, mime, encoding)
        load(baseUrl, emptyMap(), null)
    }

    override fun reload() = withPage { Com.call(it, 31) }
    override fun stopLoading() {
        rt.post { if (isReady) core?.let { Com.call(it, 43) } }
    }

    override fun canGoBack(): Boolean = canBack
    override fun canGoForward(): Boolean = canForward
    override fun goBack() = withPage { Com.call(it, 40) }
    override fun goForward() = withPage { Com.call(it, 41) }

    override fun clearCache() = withCore { devTools(it, "Network.clearBrowserCache") }

    override fun clearStorage(origin: String?) = withCore { core ->
        val o = origin ?: currentUrl()?.let { u -> runCatching { val uri = java.net.URI(u); "${uri.scheme}://${uri.authority}" }.getOrNull() }
        if (o != null) devTools(core, "Storage.clearDataForOrigin", JSONObject().put("origin", o).put("storageTypes", "all").toString())
    }

    override fun find(text: String) {}

    private fun onNavigationStarting(a: Pointer) {
        val url = Com.getString(a, 3) ?: return
        if (debug) Log.i(TAG, "$id navigation starting $url")
        if (url == "about:blank") return
        navigating = url
        val redirect = Com.getBool(a, 5)
        if (!redirect && programmatic.remove(url)) return
        if (!overridesOverrideUrl) return
        val req = WebResourceRequestImpl(Uri.parse(url), "GET", emptyMap(), true, redirect, Com.getBool(a, 4))
        if (onMainSync(5000, false) { view.client.shouldOverrideUrlLoading(view, req) }) Com.putBool(a, 8, true)
    }

    private fun onContentLoading(core: Pointer) {
        val url = source(core) ?: return
        if (debug) Log.i(TAG, "$id content loading $url")
        if (url == "about:blank" && lastUrl == null) return
        lastUrl = url
        progress = 10
        main.post {
            view.client.onPageStarted(view, url, null)
            view.chromeClient?.onProgressChanged(view, 10)
        }
    }

    private fun onNavigationCompleted(core: Pointer, a: Pointer) {
        val success = Com.getBool(a, 3)
        val status = Com.getInt(a, 4) ?: 0
        if (debug) Log.i(TAG, "$id navigation completed success=$success status=$status url=${source(core)}")
        canBack = Com.getBool(core, 38)
        canForward = Com.getBool(core, 39)
        val url = source(core) ?: lastUrl ?: return
        if (!success && status != ERROR_OPERATION_CANCELED) {
            val req = WebResourceRequestImpl(Uri.parse(navigating ?: url), "GET", emptyMap(), true, false, false)
            val err = WebResourceErrorImpl(mapError(status), "net::error $status")
            main.post { view.client.onReceivedError(view, req, err) }
        }
        if (resuming) {
            resuming = false
            val work = afterResume.toList()
            afterResume.clear()
            work.forEach { w -> runCatching { w(core) } }
        }
        if (url == "about:blank" && lastUrl == null) return
        lastUrl = url
        progress = 100
        // the cookies the page has just set are in the jar before onPageFinished: logins look for their cookie there (FebBox `ui` in
        // StreamPlay / CineStream)
        rt.pullCookies {
            main.post {
                view.chromeClient?.onProgressChanged(view, 100)
                view.client.onPageCommitVisible(view, url)
                view.client.onPageFinished(view, url)
            }
        }
        execute(core, "document.documentElement.scrollHeight") { v -> contentHeight = v.toDoubleOrNull()?.toInt() ?: 0 }
    }

    private fun handlePopup(targetUrl: String) {
        val settings = view.jcefSettings()
        val chrome = view.chromeClient
        if (settings.multipleWindows && chrome != null) {
            val transport = view.WebViewTransport()
            val handler = object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(msg: Message) {
                    val newView = transport.getWebView() ?: return
                    newView.loadUrl(targetUrl)
                }
            }
            val msg = handler.obtainMessage(0, transport)
            if (chrome.onCreateWindow(view, false, true, msg)) return
        }
        // the page opened it (window.open): it replaces the page, as on Android (shouldOverrideUrlLoading is asked), but is not a page the
        // extension asked for
        withCore { core -> navigate(core, targetUrl, emptyMap(), null) }
    }

    private fun mapError(status: Int): Int = when (status) {
        13 -> WebViewClient.ERROR_HOST_LOOKUP
        6, 9, 10, 11, 12 -> WebViewClient.ERROR_CONNECT
        7 -> WebViewClient.ERROR_TIMEOUT
        15 -> WebViewClient.ERROR_REDIRECT_LOOP
        1, 2, 3, 4, 5 -> WebViewClient.ERROR_FAILED_SSL_HANDSHAKE
        else -> WebViewClient.ERROR_UNKNOWN
    }

    // ------------------------------------------------------------------ requests

    private fun onResourceRequested(a: Pointer) {
        val request = Com.getPtr(a, 3) ?: return
        try {
            val url = Com.getString(request, 3) ?: return
            if (url.startsWith(JsBridge.ORIGIN)) {
                bridgeCall(a, request, url)
                return
            }
            val context = Com.getInt(a, 7) ?: CONTEXT_ALL
            val settings = view.jcefSettings()
            if (settings.blockNetwork || ((settings.blockImages || !settings.loadImages) && context == CONTEXT_IMAGE)) {
                respond(a, 403, "Blocked", emptyMap(), null)
                return
            }
            if (context == CONTEXT_DOCUMENT) {
                pendingData.remove(url)?.let { (data, mime, enc) ->
                    respond(a, 200, "OK", mapOf("Content-Type" to "$mime; charset=$enc"), data.toByteArray(charset(enc)))
                    return
                }
            }
            if (overridesLoadResource) main.post { view.client.onLoadResource(view, url) }
            if (!overridesIntercept) return
            val req = WebResourceRequestImpl(
                Uri.parse(url), Com.getString(request, 5) ?: "GET", requestHeaders(request), context == CONTEXT_DOCUMENT && url == navigating, false, false,
            )
            val deferral = Com.getPtr(a, 6) ?: return
            Com.addRef(a)
            interceptExecutor.execute {
                val response = try {
                    view.client.shouldInterceptRequest(view, req)
                } catch (t: Throwable) {
                    Log.e(TAG, "shouldInterceptRequest threw: ${Log.getStackTraceString(t)}")
                    null
                }
                val body = response?.let { r -> runCatching { r.getData()?.use { it.readBytes() } }.getOrNull() }
                rt.post {
                    try {
                        if (response != null) {
                            val headers = LinkedHashMap(response.getResponseHeaders() ?: emptyMap())
                            val mime = response.getMimeType() ?: "text/plain"
                            if (headers.keys.none { it.equals("Content-Type", true) }) {
                                headers["Content-Type"] = response.getEncoding()?.let { "$mime; charset=$it" } ?: mime
                            }
                            if (headers.keys.none { it.equals("Access-Control-Allow-Origin", true) }) headers["Access-Control-Allow-Origin"] = "*"
                            val status = response.getStatusCode().takeIf { it in 100..599 } ?: 200
                            respond(a, status, response.getReasonPhrase() ?: "OK", headers, body ?: ByteArray(0))
                        }
                    } finally {
                        Com.call(deferral, 3)
                        Com.release(deferral)
                        Com.release(a)
                    }
                }
            }
        } finally {
            Com.release(request)
        }
    }

    private fun onResponseReceived(a: Pointer) {
        val response = Com.getPtr(a, 4) ?: return
        val status = Com.getInt(response, 4) ?: 0
        val reason = Com.getString(response, 5)
        Com.release(response)
        if (status < 400) return
        val request = Com.getPtr(a, 3) ?: return
        val url = Com.getString(request, 3)
        val method = Com.getString(request, 5) ?: "GET"
        Com.release(request)
        if (url == null) return
        val req = WebResourceRequestImpl(Uri.parse(url), method, emptyMap(), url == navigating, false, false)
        val resp = WebResourceResponse(null, null, status, reason ?: "Error", emptyMap(), null)
        main.post { view.client.onReceivedHttpError(view, req, resp) }
    }

    private fun onDownloadStarting(a: Pointer) {
        Com.putBool(a, 5, true)
        val listener = view.downloadListener ?: return
        val op = Com.getPtr(a, 3) ?: return
        val url = Com.getString(op, 9)
        val disposition = Com.getString(op, 10)
        val mime = Com.getString(op, 11)
        val total = Com.getLong(op, 12) ?: -1L
        Com.release(op)
        main.post { listener.onDownloadStart(url, view.jcefSettings().getUserAgentString(), disposition, mime, total) }
    }

    private fun onCertificateError(a: Pointer) {
        val url = Com.getString(a, 4)
        val deferral = Com.getPtr(a, 8) ?: return
        Com.addRef(a)
        val done = AtomicBoolean(false)
        fun finish(action: Int) {
            if (!done.compareAndSet(false, true)) return
            rt.post {
                Com.call(a, 7, action)
                Com.call(deferral, 3)
                Com.release(deferral)
                Com.release(a)
            }
        }
        main.post {
            runCatching {
                view.client.onReceivedSslError(view, SslErrorHandler({ finish(0) }, { finish(1) }), SslError(SslError.SSL_UNTRUSTED, null, url))
            }.onFailure { finish(1) }
        }
    }

    private fun onScriptDialog(a: Pointer) {
        val kind = Com.getInt(a, 4) ?: 0
        if (kind == 3) {
            Com.call(a, 6) // beforeunload: leave
            return
        }
        val chrome = view.chromeClient
        if (chrome == null) {
            if (kind == 0) Com.call(a, 6)
            return
        }
        val url = Com.getString(a, 3)
        val message = Com.getString(a, 5)
        val defaultText = Com.getString(a, 7)
        val deferral = Com.getPtr(a, 10) ?: return
        Com.addRef(a)
        val done = AtomicBoolean(false)
        fun finish(ok: Boolean, text: String?) {
            if (!done.compareAndSet(false, true)) return
            rt.post {
                if (ok) {
                    if (kind == 2 && text != null) Com.call(a, 9, WString(text))
                    Com.call(a, 6)
                }
                Com.call(deferral, 3)
                Com.release(deferral)
                Com.release(a)
            }
        }
        main.post {
            val handled = runCatching {
                when (kind) {
                    1 -> chrome.onJsConfirm(view, url, message, JsResult { ok, _ -> finish(ok, null) })
                    2 -> chrome.onJsPrompt(view, url, message, defaultText, JsPromptResult { ok, v -> finish(ok, v) })
                    else -> chrome.onJsAlert(view, url, message, JsResult { ok, _ -> finish(ok, null) })
                }
            }.getOrDefault(false)
            if (!handled) finish(kind == 0, defaultText)
        }
    }

    private fun overridesConsole(): Boolean {
        val c = view.chromeClient ?: return false
        return try {
            c.javaClass.getMethod("onConsoleMessage", ConsoleMessage::class.java).declaringClass != WebChromeClient::class.java
        } catch (_: NoSuchMethodException) {
            false
        }
    }

    private fun listenToConsole(core: Pointer) {
        devTools(core, "Runtime.enable")
        val receiver = Com.getPtr(core, 42, WString("Runtime.consoleAPICalled")) ?: return
        on(receiver, 3, IID_DEVTOOLS_EVENT) { _, a ->
            val json = a?.let { Com.getString(it, 3) } ?: return@on
            val chrome = view.chromeClient ?: return@on
            runCatching {
                val o = JSONObject(json)
                val level = when (o.optString("type")) {
                    "error", "assert" -> ConsoleMessage.MessageLevel.ERROR
                    "warning" -> ConsoleMessage.MessageLevel.WARNING
                    "debug" -> ConsoleMessage.MessageLevel.DEBUG
                    else -> ConsoleMessage.MessageLevel.LOG
                }
                val args = o.optJSONArray("args") ?: JSONArray()
                val text = (0 until args.length()).joinToString(" ") { i ->
                    val arg = args.optJSONObject(i)
                    arg?.opt("value")?.toString() ?: arg?.optString("description") ?: ""
                }
                val frame = o.optJSONObject("stackTrace")?.optJSONArray("callFrames")?.optJSONObject(0)
                val message = ConsoleMessage(text, frame?.optString("url") ?: "", frame?.optInt("lineNumber") ?: 0, level)
                main.post { chrome.onConsoleMessage(message) }
            }
        }
        Com.release(receiver)
    }

    // ------------------------------------------------------------------ javascript

    override fun evaluate(script: String, callback: ValueCallback<String>?) {
        withPage { core ->
            execute(core, script) { v -> if (callback != null) main.post { callback.onReceiveValue(v) } }
        }
    }

    override fun addJavascriptInterface(obj: Any, name: String) {
        jsInterfaces[name] = obj
        if (state != State.NONE) withCore { installBridgeScript(it) }
    }

    override fun removeJavascriptInterface(name: String) {
        jsInterfaces.remove(name)
        if (state != State.NONE) withCore { installBridgeScript(it) }
    }

    private fun installBridgeScript(core: Pointer) {
        bridgeScriptId?.let { Com.call(core, 28, WString(it)) }
        bridgeScriptId = null
        val script = JsBridge.script(id, jsInterfaces) ?: return
        val handler = com.lagradost.desktop.runtime.web.wv2.ComObject.completed(IID_ADD_SCRIPT_DONE) { hr, result ->
            if (Com.ok(hr)) bridgeScriptId = result?.getWideString(0)
        }
        Com.call(core, 27, WString(script), handler.pointer)
        handler.releaseOwn()
    }

    private fun bridgeCall(a: Pointer, request: Pointer, url: String) {
        lastActivity = System.currentTimeMillis()
        val (name, method) = JsBridge.target(url)
        val body = Com.getPtr(request, 7)?.let { stream ->
            try {
                String(Com.readStream(stream), Charsets.UTF_8)
            } finally {
                Com.release(stream)
            }
        } ?: ""
        val deferral = Com.getPtr(a, 6) ?: return
        Com.addRef(a)
        bridgeExecutor.execute {
            val answer = JsBridge.invoke(jsInterfaces, name, method, body).toByteArray(Charsets.UTF_8)
            rt.post {
                respond(a, 200, "OK", mapOf("Content-Type" to "application/json; charset=utf-8", "Access-Control-Allow-Origin" to "*"), answer)
                Com.call(deferral, 3)
                Com.release(deferral)
                Com.release(a)
            }
        }
    }

    // ------------------------------------------------------------------ input, size, user agent

    private fun screenScale(): Double {
        val scale = runCatching {
            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX
        }.getOrDefault(1.0)
        return if (scale.isFinite() && scale > 0.0) scale else 1.0
    }

    /** Synthetic touches (extensions click elements this way) become DevTools mouse input; real input on screen goes to the page itself */
    override fun dispatchMouse(event: MotionEvent) {
        val type = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> "mousePressed"
            MotionEvent.ACTION_UP -> "mouseReleased"
            MotionEvent.ACTION_MOVE -> "mouseMoved"
            MotionEvent.ACTION_SCROLL -> "mouseWheel"
            else -> return
        }
        val scale = screenScale()
        val x = event.x / scale
        val y = event.y / scale
        val wheel = event.getAxisValue(MotionEvent.AXIS_VSCROLL).toDouble()
        withPage { core ->
            val params = JSONObject().put("type", type).put("x", x).put("y", y)
            if (type == "mouseWheel") params.put("deltaX", 0).put("deltaY", -wheel * 100)
            else params.put("button", if (type == "mouseMoved") "none" else "left").put("clickCount", if (type == "mouseMoved") 0 else 1)
            devTools(core, "Input.dispatchMouseEvent", params.toString())
        }
    }

    override fun applyUserAgent(ua: String) {
        rt.post { if (isReady) core?.let { applyUserAgentNow(it, ua) } }
    }

    private fun applyUserAgentNow(core: Pointer, ua: String) {
        val settings = Com.getPtr(core, 3) ?: return
        Com.query(settings, IID_SETTINGS2)?.let { s2 ->
            Com.call(s2, 22, WString(ua))
            Com.release(s2)
        }
        Com.release(settings)
    }

    override fun layoutChanged() {
        if (screenParent != null) return
        rt.post {
            val window = host ?: return@post
            val (w, h) = hiddenSize()
            User32.INSTANCE.MoveWindow(window, 0, 0, max(1, w), max(1, h), false)
        }
    }

    override fun visibilityChanged() {}

    override fun attachedChanged(isAttached: Boolean) {
        attached = isAttached
        lastActivity = System.currentTimeMillis()
    }

    override fun uiComponent(): java.awt.Component? {
        if (destroyed) return null
        return canvas ?: Canvas().also { canvas = it }
    }

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        screenParent = null
        host?.let { rt.park(it) }
        close()
    }

    /** The page's window inside [parent] (a Canvas on screen), filling it */
    private fun fitInto(parent: WinDef.HWND) {
        val window = host ?: return
        val r = WinDef.RECT()
        User32.INSTANCE.GetClientRect(parent, r)
        User32.INSTANCE.MoveWindow(window, 0, 0, max(1, r.right - r.left), max(1, r.bottom - r.top), true)
        controller?.let { Com.call(it, 23) }
    }

    /** The AWT component that shows the page: a heavyweight canvas whose window becomes the parent of the page's window */
    private inner class Canvas : java.awt.Canvas() {
        init {
            background = java.awt.Color(0x20, 0x20, 0x20)
        }

        override fun addNotify() {
            super.addNotify()
            val parent = WinDef.HWND(Native.getComponentPointer(this) ?: return)
            screenParent = parent
            wasOnScreen = true
            lastActivity = System.currentTimeMillis()
            withPage { _ ->
                val window = host ?: return@withPage
                if (screenParent != parent) return@withPage
                User32.INSTANCE.SetParent(window, parent)
                fitInto(parent)
            }
        }

        override fun removeNotify() {
            screenParent = null
            // back into the hidden window before this canvas' window is destroyed (that would destroy the page's window with it)
            host?.let { rt.park(it) }
            layoutChanged()
            super.removeNotify()
        }

        @Suppress("DEPRECATION")
        override fun reshape(x: Int, y: Int, width: Int, height: Int) {
            super.reshape(x, y, width, height)
            val parent = screenParent ?: return
            rt.post { if (screenParent == parent) fitInto(parent) }
        }

        override fun paint(g: java.awt.Graphics?) {}
        override fun update(g: java.awt.Graphics?) {}
    }

    // ------------------------------------------------------------------ threading

    private fun <T> onMainSync(timeoutMs: Long, default: T, block: () -> T): T {
        if (EventQueue.isDispatchThread()) return block()
        val task = FutureTask(block)
        main.post(task)
        return try {
            task.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (t: Throwable) {
            default
        }
    }
}
