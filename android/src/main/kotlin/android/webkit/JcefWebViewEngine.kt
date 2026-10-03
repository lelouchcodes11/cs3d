package android.webkit

import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.lagradost.desktop.runtime.web.JcefRuntime
import org.cef.CefClient
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefBeforeDownloadCallback
import org.cef.callback.CefCallback
import org.cef.callback.CefDownloadItem
import org.cef.callback.CefJSDialogCallback
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefDownloadHandlerAdapter
import org.cef.handler.CefJSDialogHandler
import org.cef.handler.CefJSDialogHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceHandler
import org.cef.handler.CefResourceHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefPostData
import org.cef.network.CefPostDataElement
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import org.json.JSONArray
import org.json.JSONObject
import java.awt.EventQueue
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Lets a few browsers start at a time. A browser holds its place until it is destroyed; one that waits longer than
 * [MAX_WAIT_MS] starts anyway, so an extension that never destroys its WebView cannot block every later one.
 */
internal object BrowserGate {
    private const val LIMIT = 5
    private const val MAX_WAIT_MS = 20_000L
    private val lock = Any()
    private var active = 0
    private val waiting = ArrayDeque<Ticket>()
    private val timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "webview-gate").also { it.isDaemon = true } }

    private class Ticket(val start: () -> Unit) {
        val started = AtomicBoolean(false)
    }

    fun enter(start: () -> Unit) {
        val ticket = Ticket(start)
        val now = synchronized(lock) {
            if (active < LIMIT) { active++; true } else { waiting.addLast(ticket); false }
        }
        if (now) {
            ticket.started.set(true)
            start()
        } else {
            timer.schedule({ force(ticket) }, MAX_WAIT_MS, TimeUnit.MILLISECONDS)
        }
    }

    private fun force(ticket: Ticket) {
        val go = synchronized(lock) {
            if (ticket.started.compareAndSet(false, true)) { waiting.remove(ticket); active++; true } else false
        }
        if (go) ticket.start()
    }

    fun leave() {
        var next: Ticket? = null
        synchronized(lock) {
            while (waiting.isNotEmpty()) {
                val candidate = waiting.removeFirst()
                if (candidate.started.compareAndSet(false, true)) { next = candidate; break }
            }
            if (next == null) active--
        }
        next?.start?.invoke()
    }
}

/**
 * Bridges one android.webkit.WebView to a JCEF off-screen browser, translating Chromium events to
 * WebViewClient/WebChromeClient callbacks with Android threading semantics: page callbacks on the
 * main (UI) thread, shouldInterceptRequest on a background thread.
 */
internal class JcefWebViewEngine(private val view: WebView) {
    companion object {
        private const val TAG = "WebView"
        private const val DEFAULT_WIDTH = 1280
        private const val DEFAULT_HEIGHT = 720
        private val bridgeExecutor = Executors.newCachedThreadPool { r -> Thread(r, "JavaBridge").also { it.isDaemon = true } }
        private val setupExecutor = Executors.newCachedThreadPool { r -> Thread(r, "webview-setup").also { it.isDaemon = true } }
        private val blockedImageExt = Regex("\\.(png|jpe?g|gif|webp|bmp|ico|svg)(\\?|$)", RegexOption.IGNORE_CASE)
    }

    private val main = Handler(Looper.getMainLooper())
    private val browserLock = Object()
    @Volatile private var client: CefClient? = null
    @Volatile private var browser: CefBrowser? = null
    @Volatile private var created = false
    @Volatile private var destroyed = false
    private val pending = CopyOnWriteArrayList<(CefBrowser) -> Unit>()
    private var creating = AtomicBoolean(false)

    @Volatile var title: String? = null
    @Volatile var progress: Int = 0
    @Volatile var originalUrl: String? = null
    @Volatile var contentHeight: Int = 0
    @Volatile private var lastUrl: String? = null
    @Volatile private var attached = false

    /** URLs whose next main frame navigation is initiated by loadUrl (no shouldOverrideUrlLoading) */
    private val programmatic = ConcurrentHashMap.newKeySet<String>()
    private val pendingHeaders = ConcurrentHashMap<String, Map<String, String>>()
    private val pendingPost = ConcurrentHashMap<String, ByteArray>()
    private val pendingData = ConcurrentHashMap<String, Triple<String, String, String>>()
    private val jsInterfaces = ConcurrentHashMap<String, Any>()
    private var bridgeScriptId: String? = null

    @Volatile private var overridesIntercept = false
    @Volatile private var overridesLoadResource = false
    @Volatile private var overridesHttpError = false
    @Volatile private var overridesOverrideUrl = false

    init {
        clientChanged()
    }

    fun clientChanged() {
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

    // ------------------------------------------------------------------ browser lifecycle

    private fun withBrowser(action: (CefBrowser) -> Unit) {
        if (destroyed) return
        val b = browser
        if (b != null && created) {
            action(b)
            return
        }
        pending.add(action)
        ensureCreated()
    }

    private fun ensureCreated() {
        if (!creating.compareAndSet(false, true)) return
        // Chromium browsers are heavy: an extension (or a search over many providers) that opens dozens at once leaves every one
        // of them starved (DevTools calls time out, pages stay black). They start in turns.
        BrowserGate.enter {
            hasPermit.set(true)
            if (destroyed) {
                releasePermit()
                return@enter
            }
            setupExecutor.execute {
                try {
                    val c = JcefRuntime.createClient()
                    client = c
                    installHandlers(c)
                    val b = c.createBrowser("about:blank", true, false)
                    synchronized(browserLock) { browser = b }
                    b.createImmediately()
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to create browser: ${Log.getStackTraceString(t)}")
                    pending.clear()
                    releasePermit()
                }
            }
        }
    }

    private val hasPermit = AtomicBoolean(false)

    private fun releasePermit() {
        if (hasPermit.compareAndSet(true, false)) BrowserGate.leave()
    }

    private fun onBrowserCreated(b: CefBrowser) {
        setupExecutor.execute {
            try {
                applyViewport(b)
                applyUserAgentInternal(b, view.jcefSettings().getUserAgentString())
                installBridgeScript(b)
            } catch (t: Throwable) {
                Log.w(TAG, "Browser setup failed: ${t.message}")
            }
            created = true
            val actions = pending.toList()
            pending.clear()
            for (a in actions) {
                try {
                    a(b)
                } catch (t: Throwable) {
                    Log.e(TAG, Log.getStackTraceString(t))
                }
            }
        }
    }

    private fun devTools(b: CefBrowser, method: String, params: JSONObject? = null): String? = try {
        b.devToolsClient.executeDevToolsMethod(method, params?.toString()).get(15, TimeUnit.SECONDS)
    } catch (t: Throwable) {
        Log.w(TAG, "DevTools $method failed: ${t.message}")
        null
    }

    private fun viewportSize(): Pair<Int, Int> {
        val lp = view.getLayoutParams()
        val w = view.getWidth().takeIf { it > 0 } ?: lp?.width?.takeIf { it > 0 } ?: DEFAULT_WIDTH
        val h = view.getHeight().takeIf { it > 0 } ?: lp?.height?.takeIf { it > 0 } ?: DEFAULT_HEIGHT
        return w to h
    }

    /**
     * Windows screen scale (1.5 on a 150% display). Compose and Android views are in physical px;
     * the Swing surface JCEF paints into is that size divided by this scale.
     */
    private fun screenScale(): Double {
        val scale = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX
        return if (scale.isFinite() && scale > 0.0) scale else 1.0
    }

    private fun applyViewport(b: CefBrowser) {
        val (pxW, pxH) = viewportSize()
        val scale = screenScale()
        // CSS pixels must match the Swing component. Forcing the raw px size with scale 1 makes the
        // page 1.5× the visible surface, so a dialog clips the Cloudflare check.
        val w = max(1, (pxW / scale).roundToInt())
        val h = max(1, (pxH / scale).roundToInt())
        val ua = view.jcefSettings().getUserAgentString()
        val mobile = ua.contains("Mobile") || ua.contains("Android")
        devTools(b, "Emulation.setDeviceMetricsOverride", JSONObject().put("width", w).put("height", h).put("deviceScaleFactor", scale).put("mobile", mobile))
        devTools(b, "Emulation.setFocusEmulationEnabled", JSONObject().put("enabled", true))
        // A detached WebView still behaves like a laid out view for extensions
        if (!attached && view.getWidth() == 0) main.post { view.layout(0, 0, pxW, pxH) }
    }

    fun applyUserAgent(ua: String) {
        withBrowser { b -> setupExecutor.execute { applyUserAgentInternal(b, ua) } }
    }

    private fun applyUserAgentInternal(b: CefBrowser, ua: String) {
        devTools(b, "Emulation.setUserAgentOverride", JSONObject().put("userAgent", ua))
    }

    fun layoutChanged() {
        val b = browser ?: return
        if (created) setupExecutor.execute { applyViewport(b) }
    }

    fun visibilityChanged() {}

    fun attachedChanged(isAttached: Boolean) {
        attached = isAttached
    }

    fun uiComponent(): java.awt.Component? = browser?.uiComponent

    fun destroy() {
        if (destroyed) return
        destroyed = true
        pending.clear()
        val b = browser
        val c = client
        browser = null
        client = null
        setupExecutor.execute {
            try {
                b?.stopLoad()
                b?.close(true)
            } catch (_: Throwable) {
            }
            try {
                c?.dispose()
            } catch (_: Throwable) {
            }
            releasePermit()
        }
    }

    // ------------------------------------------------------------------ navigation

    fun currentUrl(): String? = lastUrl ?: browser?.url?.takeIf { it != "about:blank" }

    fun load(url: String, headers: Map<String, String>, postData: ByteArray?) {
        if (originalUrl == null) originalUrl = url
        programmatic.add(url)
        if (headers.isNotEmpty()) pendingHeaders[url] = headers
        if (postData != null) pendingPost[url] = postData
        withBrowser { it.loadURL(url) }
    }

    fun loadDataAt(baseUrl: String, data: String, mime: String, encoding: String) {
        pendingData[baseUrl] = Triple(data, mime, encoding)
        load(baseUrl, emptyMap(), null)
    }

    fun reload() = withBrowser { it.reload() }
    fun stopLoading() {
        browser?.takeIf { created }?.stopLoad()
    }

    fun canGoBack(): Boolean = browser?.canGoBack() ?: false
    fun canGoForward(): Boolean = browser?.canGoForward() ?: false
    fun goBack() = withBrowser { it.goBack() }
    fun goForward() = withBrowser { it.goForward() }

    fun clearCache() = withBrowser { b -> setupExecutor.execute { devTools(b, "Network.clearBrowserCache") } }

    fun clearStorage(origin: String?) = withBrowser { b ->
        setupExecutor.execute {
            val o = origin ?: currentUrl()?.let { u -> try { val uri = java.net.URI(u); "${uri.scheme}://${uri.authority}" } catch (_: Exception) { null } }
            if (o != null) devTools(b, "Storage.clearDataForOrigin", JSONObject().put("origin", o).put("storageTypes", "all"))
        }
    }

    fun find(text: String) = withBrowser { b -> b.find(text, true, false, false) }

    // ------------------------------------------------------------------ javascript

    fun evaluate(script: String, callback: ValueCallback<String>?) {
        withBrowser { b ->
            setupExecutor.execute {
                val response = devTools(
                    b, "Runtime.evaluate", JSONObject()
                        .put("expression", script)
                        .put("returnByValue", true)
                        .put("awaitPromise", false)
                        .put("userGesture", true)
                        .put("allowUnsafeEvalBlockedByCSP", true)
                )
                if (callback != null) {
                    val value = try {
                        val result = JSONObject(response ?: "{}").optJSONObject("result")
                        if (result == null || result.optString("type") == "undefined" || !result.has("value")) "null"
                        else JSONObject.valueToString(result.opt("value"))
                    } catch (t: Throwable) {
                        "null"
                    }
                    main.post { callback.onReceiveValue(value) }
                }
            }
        }
    }

    fun addJavascriptInterface(obj: Any, name: String) {
        jsInterfaces[name] = obj
        withBrowser { b -> setupExecutor.execute { installBridgeScript(b) } }
    }

    fun removeJavascriptInterface(name: String) {
        jsInterfaces.remove(name)
        withBrowser { b -> setupExecutor.execute { installBridgeScript(b) } }
    }

    private fun exposedMethods(obj: Any): List<String> =
        obj.javaClass.methods.filter { it.isAnnotationPresent(JavascriptInterface::class.java) }.map { it.name }.distinct()

    @Synchronized
    private fun installBridgeScript(b: CefBrowser) {
        bridgeScriptId?.let { devTools(b, "Page.removeScriptToEvaluateOnNewDocument", JSONObject().put("identifier", it)) }
        bridgeScriptId = null
        if (jsInterfaces.isEmpty()) return
        val id = b.identifier
        val sb = StringBuilder("(function(){function __csCall(n,m,a){var x=new XMLHttpRequest();")
        sb.append("x.open('POST','${JcefRuntime.BRIDGE_ORIGIN}/$id/'+encodeURIComponent(n)+'/'+encodeURIComponent(m),false);")
        sb.append("x.send(JSON.stringify(Array.prototype.slice.call(a)));var r=JSON.parse(x.responseText||'{}');")
        sb.append("if(r.e)throw new Error(r.e);return r.v;}")
        for ((name, obj) in jsInterfaces) {
            sb.append("window[").append(JSONObject.quote(name)).append("]={")
            sb.append(exposedMethods(obj).joinToString(",") { m -> "${JSONObject.quote(m)}:function(){return __csCall(${JSONObject.quote(name)},${JSONObject.quote(m)},arguments);}" })
            sb.append("};")
        }
        sb.append("})();")
        devTools(b, "Page.enable")
        val resp = devTools(b, "Page.addScriptToEvaluateOnNewDocument", JSONObject().put("source", sb.toString()))
        bridgeScriptId = try {
            JSONObject(resp ?: "{}").optString("identifier").ifEmpty { null }
        } catch (_: Throwable) {
            null
        }
    }

    /** Invoke a @JavascriptInterface method, returns the JSON response for the page */
    private fun invokeBridge(name: String, method: String, argsJson: String): String {
        val obj = jsInterfaces[name] ?: return JSONObject().put("e", "No interface $name").toString()
        return try {
            val args = JSONArray(argsJson.ifBlank { "[]" })
            val candidates = obj.javaClass.methods.filter {
                it.name == method && it.isAnnotationPresent(JavascriptInterface::class.java) && it.parameterCount == args.length()
            }
            val m = candidates.firstOrNull() ?: return JSONObject().put("e", "Method not found").toString()
            val params = m.parameterTypes.mapIndexed { i, t -> convertArg(args.opt(i), t) }.toTypedArray()
            val result = m.invoke(obj, *params)
            JSONObject().put("v", if (result == null || m.returnType == Void.TYPE) JSONObject.NULL else result).toString()
        } catch (t: Throwable) {
            val cause = (t as? java.lang.reflect.InvocationTargetException)?.targetException ?: t
            Log.e(TAG, "JavascriptInterface $name.$method threw: ${Log.getStackTraceString(cause)}")
            JSONObject().put("e", cause.toString()).toString()
        }
    }

    private fun convertArg(v: Any?, t: Class<*>): Any? {
        if (v == null || v == JSONObject.NULL) {
            return when (t) {
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                java.lang.Double.TYPE -> 0.0
                java.lang.Float.TYPE -> 0f
                java.lang.Boolean.TYPE -> false
                java.lang.Short.TYPE -> 0.toShort()
                java.lang.Byte.TYPE -> 0.toByte()
                java.lang.Character.TYPE -> 0.toChar()
                String::class.java -> if (v == JSONObject.NULL) null else "undefined"
                else -> null
            }
        }
        return when (t) {
            String::class.java -> if (v is String) v else v.toString()
            java.lang.Integer.TYPE, java.lang.Integer::class.java -> (v as? Number)?.toInt() ?: v.toString().toIntOrNull() ?: 0
            java.lang.Long.TYPE, java.lang.Long::class.java -> (v as? Number)?.toLong() ?: v.toString().toLongOrNull() ?: 0L
            java.lang.Double.TYPE, java.lang.Double::class.java -> (v as? Number)?.toDouble() ?: v.toString().toDoubleOrNull() ?: 0.0
            java.lang.Float.TYPE, java.lang.Float::class.java -> (v as? Number)?.toFloat() ?: v.toString().toFloatOrNull() ?: 0f
            java.lang.Boolean.TYPE, java.lang.Boolean::class.java -> (v as? Boolean) ?: v.toString().toBoolean()
            else -> v
        }
    }

    // ------------------------------------------------------------------ input

    fun dispatchMouse(event: MotionEvent) {
        val type = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> "mousePressed"
            MotionEvent.ACTION_UP -> "mouseReleased"
            MotionEvent.ACTION_MOVE -> "mouseMoved"
            MotionEvent.ACTION_SCROLL -> "mouseWheel"
            else -> return
        }
        // DevTools mouse coordinates are CSS pixels, which are view px divided by the screen scale
        val scale = screenScale()
        val x = event.x / scale
        val y = event.y / scale
        val wheel = event.getAxisValue(MotionEvent.AXIS_VSCROLL).toDouble()
        withBrowser { b ->
            setupExecutor.execute {
                val params = JSONObject().put("type", type).put("x", x).put("y", y)
                if (type == "mouseWheel") params.put("deltaX", 0).put("deltaY", -wheel * 100)
                else params.put("button", if (type == "mouseMoved") "none" else "left").put("clickCount", if (type == "mouseMoved") 0 else 1)
                devTools(b, "Input.dispatchMouseEvent", params)
            }
        }
    }

    // ------------------------------------------------------------------ handlers

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

    private fun isOurs(b: CefBrowser?): Boolean = b != null && b === browser

    private fun headersOf(request: CefRequest): Map<String, String> {
        val map = HashMap<String, String>()
        request.getHeaderMap(map)
        request.referrerURL?.takeIf { it.isNotEmpty() && !map.keys.any { k -> k.equals("Referer", true) } }?.let { map["Referer"] = it }
        return map
    }

    private fun toRequest(request: CefRequest, mainFrame: Boolean, redirect: Boolean = false, gesture: Boolean = false): WebResourceRequest =
        WebResourceRequestImpl(Uri.parse(request.url), request.method ?: "GET", headersOf(request), mainFrame, redirect, gesture)

    private fun installHandlers(c: CefClient) {
        c.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
            override fun onAfterCreated(b: CefBrowser) {
                if (b === browser) onBrowserCreated(b)
            }

            override fun onBeforePopup(b: CefBrowser, frame: CefFrame?, targetUrl: String?, targetFrameName: String?): Boolean {
                if (!isOurs(b) || targetUrl.isNullOrEmpty()) return true
                main.post { handlePopup(targetUrl) }
                return true
            }
        })

        c.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadStart(b: CefBrowser, frame: CefFrame, transitionType: CefRequest.TransitionType?) {
                if (!isOurs(b) || !frame.isMain) return
                val url = frame.url ?: return
                if (url == "about:blank" && lastUrl == null) return
                lastUrl = url
                progress = 10
                main.post {
                    view.client.onPageStarted(view, url, null)
                    view.chromeClient?.onProgressChanged(view, 10)
                }
            }

            override fun onLoadEnd(b: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (!isOurs(b) || !frame.isMain) return
                val url = frame.url ?: return
                if (url == "about:blank" && lastUrl == null) return
                lastUrl = url
                progress = 100
                main.post {
                    view.chromeClient?.onProgressChanged(view, 100)
                    view.client.onPageCommitVisible(view, url)
                    view.client.onPageFinished(view, url)
                }
                setupExecutor.execute {
                    try {
                        val r = devTools(b, "Runtime.evaluate", JSONObject().put("expression", "document.documentElement.scrollHeight").put("returnByValue", true))
                        contentHeight = JSONObject(r ?: "{}").optJSONObject("result")?.optInt("value") ?: 0
                    } catch (_: Throwable) {
                    }
                }
            }

            override fun onLoadError(b: CefBrowser, frame: CefFrame, errorCode: CefLoadHandler.ErrorCode, errorText: String?, failedUrl: String?) {
                if (!isOurs(b) || errorCode == CefLoadHandler.ErrorCode.ERR_ABORTED) return
                val url = failedUrl ?: return
                val req = WebResourceRequestImpl(Uri.parse(url), "GET", emptyMap(), frame.isMain, false, false)
                val err = WebResourceErrorImpl(mapError(errorCode), errorText ?: errorCode.name)
                main.post { view.client.onReceivedError(view, req, err) }
            }
        })

        c.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            override fun onTitleChange(b: CefBrowser, newTitle: String?) {
                if (!isOurs(b)) return
                title = newTitle
                main.post { view.chromeClient?.onReceivedTitle(view, newTitle) }
            }

            override fun onAddressChange(b: CefBrowser, frame: CefFrame?, url: String?) {
                if (!isOurs(b) || frame?.isMain != true || url == null || url == "about:blank") return
                lastUrl = url
                main.post { view.client.doUpdateVisitedHistory(view, url, false) }
            }

            override fun onConsoleMessage(b: CefBrowser, level: CefSettings.LogSeverity?, message: String?, source: String?, line: Int): Boolean {
                if (!isOurs(b)) return false
                val lvl = when (level) {
                    CefSettings.LogSeverity.LOGSEVERITY_ERROR, CefSettings.LogSeverity.LOGSEVERITY_FATAL -> ConsoleMessage.MessageLevel.ERROR
                    CefSettings.LogSeverity.LOGSEVERITY_WARNING -> ConsoleMessage.MessageLevel.WARNING
                    CefSettings.LogSeverity.LOGSEVERITY_VERBOSE -> ConsoleMessage.MessageLevel.DEBUG
                    else -> ConsoleMessage.MessageLevel.LOG
                }
                val chrome = view.chromeClient ?: return true
                main.post { chrome.onConsoleMessage(ConsoleMessage(message, source, line, lvl)) }
                return true
            }
        })

        c.addJSDialogHandler(object : CefJSDialogHandlerAdapter() {
            override fun onJSDialog(
                b: CefBrowser, originUrl: String?, dialogType: CefJSDialogHandler.JSDialogType?, messageText: String?,
                defaultPromptText: String?, callback: CefJSDialogCallback, suppressMessage: BoolRef?
            ): Boolean {
                val chrome = view.chromeClient
                if (chrome == null) {
                    callback.Continue(dialogType == CefJSDialogHandler.JSDialogType.JSDIALOGTYPE_ALERT, "")
                    return true
                }
                main.post {
                    val handled = when (dialogType) {
                        CefJSDialogHandler.JSDialogType.JSDIALOGTYPE_CONFIRM ->
                            chrome.onJsConfirm(view, originUrl, messageText, JsResult { ok, _ -> callback.Continue(ok, "") })
                        CefJSDialogHandler.JSDialogType.JSDIALOGTYPE_PROMPT ->
                            chrome.onJsPrompt(view, originUrl, messageText, defaultPromptText, JsPromptResult { ok, v -> callback.Continue(ok, v ?: "") })
                        else -> chrome.onJsAlert(view, originUrl, messageText, JsResult { ok, _ -> callback.Continue(ok, "") })
                    }
                    if (!handled) callback.Continue(dialogType == CefJSDialogHandler.JSDialogType.JSDIALOGTYPE_ALERT, defaultPromptText ?: "")
                }
                return true
            }

            override fun onBeforeUnloadDialog(b: CefBrowser?, messageText: String?, isReload: Boolean, callback: CefJSDialogCallback): Boolean {
                callback.Continue(true, "")
                return true
            }
        })

        c.addDownloadHandler(object : CefDownloadHandlerAdapter() {
            override fun onBeforeDownload(b: CefBrowser, item: CefDownloadItem, suggestedName: String?, callback: CefBeforeDownloadCallback?): Boolean {
                val listener = view.downloadListener ?: return true
                val url = item.url
                val mime = item.mimeType
                val len = item.totalBytes
                val disposition = item.contentDisposition
                main.post { listener.onDownloadStart(url, view.jcefSettings().getUserAgentString(), disposition, mime, len) }
                return true
            }
        })

        c.addRequestHandler(object : CefRequestHandlerAdapter() {
            override fun onBeforeBrowse(b: CefBrowser, frame: CefFrame, request: CefRequest, userGesture: Boolean, isRedirect: Boolean): Boolean {
                if (!isOurs(b)) return false
                val url = request.url ?: return false
                if (url == "about:blank") return false
                if (!isRedirect && programmatic.remove(url)) return false
                if (!overridesOverrideUrl || !frame.isMain) return false
                val req = toRequest(request, frame.isMain, isRedirect, userGesture)
                return onMainSync(5000, false) { view.client.shouldOverrideUrlLoading(view, req) }
            }

            override fun getResourceRequestHandler(
                b: CefBrowser?, frame: CefFrame?, request: CefRequest, isNavigation: Boolean, isDownload: Boolean,
                requestInitiator: String?, disableDefaultHandling: BoolRef?
            ): CefResourceRequestHandler? {
                if (!isOurs(b)) return null
                return ResourceHandlerForRequest(frame?.isMain == true && isNavigation)
            }

            override fun onCertificateError(b: CefBrowser?, certError: CefLoadHandler.ErrorCode?, requestUrl: String?, callback: CefCallback): Boolean {
                if (!isOurs(b)) return false
                val error = SslError(SslError.SSL_UNTRUSTED, null, requestUrl)
                main.post {
                    view.client.onReceivedSslError(view, SslErrorHandler({ callback.Continue() }, { callback.cancel() }), error)
                }
                return true
            }

            override fun onRenderProcessTerminated(b: CefBrowser?, status: org.cef.handler.CefRequestHandler.TerminationStatus?, errorCode: Int, errorString: String?) {
                if (!isOurs(b)) return
                main.post { view.client.onRenderProcessGone(view, RenderProcessGoneDetail()) }
            }
        })
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
        view.loadUrl(targetUrl)
    }

    private fun mapError(code: CefLoadHandler.ErrorCode): Int = when (code) {
        CefLoadHandler.ErrorCode.ERR_NAME_NOT_RESOLVED, CefLoadHandler.ErrorCode.ERR_NAME_RESOLUTION_FAILED -> WebViewClient.ERROR_HOST_LOOKUP
        CefLoadHandler.ErrorCode.ERR_CONNECTION_REFUSED, CefLoadHandler.ErrorCode.ERR_CONNECTION_FAILED,
        CefLoadHandler.ErrorCode.ERR_CONNECTION_RESET, CefLoadHandler.ErrorCode.ERR_INTERNET_DISCONNECTED -> WebViewClient.ERROR_CONNECT
        CefLoadHandler.ErrorCode.ERR_TIMED_OUT, CefLoadHandler.ErrorCode.ERR_CONNECTION_TIMED_OUT -> WebViewClient.ERROR_TIMEOUT
        CefLoadHandler.ErrorCode.ERR_TOO_MANY_REDIRECTS -> WebViewClient.ERROR_REDIRECT_LOOP
        CefLoadHandler.ErrorCode.ERR_UNKNOWN_URL_SCHEME, CefLoadHandler.ErrorCode.ERR_DISALLOWED_URL_SCHEME -> WebViewClient.ERROR_UNSUPPORTED_SCHEME
        CefLoadHandler.ErrorCode.ERR_SSL_PROTOCOL_ERROR, CefLoadHandler.ErrorCode.ERR_CERT_AUTHORITY_INVALID,
        CefLoadHandler.ErrorCode.ERR_CERT_COMMON_NAME_INVALID, CefLoadHandler.ErrorCode.ERR_CERT_DATE_INVALID -> WebViewClient.ERROR_FAILED_SSL_HANDSHAKE
        CefLoadHandler.ErrorCode.ERR_INVALID_URL -> WebViewClient.ERROR_BAD_URL
        CefLoadHandler.ErrorCode.ERR_FILE_NOT_FOUND -> WebViewClient.ERROR_FILE_NOT_FOUND
        else -> WebViewClient.ERROR_UNKNOWN
    }

    /** Per request handler: headers for programmatic loads, bridge calls and shouldInterceptRequest */
    private inner class ResourceHandlerForRequest(private val mainNavigation: Boolean) : CefResourceRequestHandlerAdapter() {
        override fun onBeforeResourceLoad(b: CefBrowser?, frame: CefFrame?, request: CefRequest): Boolean {
            val url = request.url ?: return false
            val settings = view.jcefSettings()
            if (settings.blockNetwork && !url.startsWith(JcefRuntime.BRIDGE_ORIGIN)) return true
            if ((settings.blockImages || !settings.loadImages) && request.resourceType == CefRequest.ResourceType.RT_IMAGE) return true
            if (mainNavigation) {
                pendingHeaders.remove(url)?.let { headers ->
                    for ((k, v) in headers) {
                        if (k.equals("Referer", true)) request.setReferrer(v, CefRequest.ReferrerPolicy.REFERRER_POLICY_DEFAULT)
                        else request.setHeaderByName(k, v, true)
                    }
                }
                pendingPost.remove(url)?.let { body ->
                    request.method = "POST"
                    val pd = CefPostData.create()
                    val el = CefPostDataElement.create()
                    el.setToBytes(body.size, body)
                    pd.addElement(el)
                    request.postData = pd
                    if (request.getHeaderByName("Content-Type").isNullOrEmpty()) {
                        request.setHeaderByName("Content-Type", "application/x-www-form-urlencoded", true)
                    }
                }
            }
            if (overridesLoadResource) {
                main.post { view.client.onLoadResource(view, url) }
            }
            return false
        }

        override fun getResourceHandler(b: CefBrowser?, frame: CefFrame?, request: CefRequest): CefResourceHandler? {
            val url = request.url ?: return null
            if (url.startsWith(JcefRuntime.BRIDGE_ORIGIN)) return BridgeResourceHandler()
            if (mainNavigation) {
                pendingData.remove(url)?.let { (data, mime, enc) ->
                    return StaticResourceHandler(200, "OK", mime, enc, emptyMap(), ByteArrayInputStream(data.toByteArray(charset(enc))))
                }
            }
            if (!overridesIntercept) return null
            val req = toRequest(request, frame?.isMain == true && mainNavigation)
            val response = try {
                view.client.shouldInterceptRequest(view, req)
            } catch (t: Throwable) {
                Log.e(TAG, "shouldInterceptRequest threw: ${Log.getStackTraceString(t)}")
                null
            } ?: return null
            return StaticResourceHandler(
                response.getStatusCode(), response.getReasonPhrase() ?: "OK", response.getMimeType() ?: "text/plain",
                response.getEncoding(), response.getResponseHeaders() ?: emptyMap(), response.getData()
            )
        }

        override fun onResourceResponse(b: CefBrowser?, frame: CefFrame?, request: CefRequest, response: CefResponse): Boolean {
            if (overridesHttpError && response.status >= 400) {
                val headers = HashMap<String, String>()
                response.getHeaderMap(headers)
                val req = toRequest(request, mainNavigation)
                val resp = WebResourceResponse(response.mimeType, null, response.status, response.statusText, headers, null)
                main.post { view.client.onReceivedHttpError(view, req, resp) }
            }
            return false
        }
    }

    /** Serves a response produced by Java code */
    private class StaticResourceHandler(
        private val status: Int,
        private val reason: String,
        private val mime: String,
        private val encoding: String?,
        private val headers: Map<String, String>,
        private val data: InputStream?,
    ) : CefResourceHandlerAdapter() {
        override fun processRequest(request: CefRequest?, callback: CefCallback): Boolean {
            callback.Continue()
            return true
        }

        override fun getResponseHeaders(response: CefResponse, responseLength: IntRef, redirectUrl: StringRef?) {
            response.status = status
            response.statusText = reason
            response.mimeType = mime
            val map = HashMap(headers)
            if (encoding != null && !map.keys.any { it.equals("Content-Type", true) }) map["Content-Type"] = "$mime; charset=$encoding"
            if (!map.keys.any { it.equals("Access-Control-Allow-Origin", true) }) map["Access-Control-Allow-Origin"] = "*"
            response.setHeaderMap(map)
            responseLength.set(-1)
        }

        override fun readResponse(dataOut: ByteArray, bytesToRead: Int, bytesRead: IntRef, callback: CefCallback?): Boolean {
            val input = data ?: run {
                bytesRead.set(0)
                return false
            }
            val n = try {
                input.read(dataOut, 0, bytesToRead)
            } catch (e: Exception) {
                -1
            }
            if (n <= 0) {
                bytesRead.set(0)
                try {
                    input.close()
                } catch (_: Exception) {
                }
                return false
            }
            bytesRead.set(n)
            return true
        }

        override fun cancel() {
            try {
                data?.close()
            } catch (_: Exception) {
            }
        }
    }

    /** Runs a @JavascriptInterface call on the JavaBridge thread and returns its JSON result */
    private inner class BridgeResourceHandler : CefResourceHandlerAdapter() {
        @Volatile private var body: ByteArray = ByteArray(0)
        private var offset = 0

        override fun processRequest(request: CefRequest, callback: CefCallback): Boolean {
            val path = request.url.removePrefix(JcefRuntime.BRIDGE_ORIGIN).trim('/').split('/')
            val name = java.net.URLDecoder.decode(path.getOrNull(1) ?: "", "UTF-8")
            val method = java.net.URLDecoder.decode(path.getOrNull(2) ?: "", "UTF-8")
            val sb = StringBuilder()
            request.postData?.let { pd ->
                val elements = java.util.Vector<CefPostDataElement>()
                pd.getElements(elements)
                for (e in elements) {
                    val bytes = ByteArray(e.bytesCount)
                    e.getBytes(bytes.size, bytes)
                    sb.append(String(bytes, Charsets.UTF_8))
                }
            }
            bridgeExecutor.execute {
                body = invokeBridge(name, method, sb.toString()).toByteArray(Charsets.UTF_8)
                callback.Continue()
            }
            return true
        }

        override fun getResponseHeaders(response: CefResponse, responseLength: IntRef, redirectUrl: StringRef?) {
            response.status = 200
            response.statusText = "OK"
            response.mimeType = "application/json"
            response.setHeaderMap(mapOf("Access-Control-Allow-Origin" to "*", "Content-Type" to "application/json; charset=utf-8"))
            responseLength.set(body.size)
        }

        override fun readResponse(dataOut: ByteArray, bytesToRead: Int, bytesRead: IntRef, callback: CefCallback?): Boolean {
            val remaining = body.size - offset
            if (remaining <= 0) {
                bytesRead.set(0)
                return false
            }
            val n = minOf(remaining, bytesToRead)
            System.arraycopy(body, offset, dataOut, 0, n)
            offset += n
            bytesRead.set(n)
            return true
        }
    }

    @Suppress("unused")
    private fun viewOrNull(): View = view
}
