package android.webkit

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.lagradost.desktop.runtime.web.WebCookie
import com.lagradost.desktop.runtime.web.WebCookies
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * The browser of the extension sandbox. An extension that builds an android.webkit.WebView in the sandbox process gets a view whose page lives in the app: the app opens a
 * real page (WebView2) of its own for it, does what the view is asked to do (load, run a script, click) and sends back what happens (page started, finished, a request,
 * a console line, a call from the page to the extension's JavascriptInterface). The sandbox process itself has no browser engine and no window.
 */
interface RemoteBrowserFactory {
    /** The page for [view], or null when none can be made (the view then cannot be used: see [WebBrowserGate.unavailable]) */
    fun create(id: String, view: WebView): RemoteBrowser?
}

/** The page in the app, as the sandbox process sees it. Calls are made in the order they come. */
interface RemoteBrowser {
    /** Something the page is asked to do ([op] is "load", "reload", "mouse" ...); nothing comes back */
    fun op(op: String, args: JSONObject)

    /** Runs [script] in the page and gives its result (JSON text, null when the page is gone) to [callback], on any thread */
    fun evaluate(script: String, callback: (String?) -> Unit)
}

/**
 * An object that an extension in the sandbox gave to addJavascriptInterface, as the app's page sees it: the names of its methods (and how many parameters each has),
 * and [call], which runs one in the sandbox and returns the JSON answer for the page ({"v": result} or {"e": error}).
 */
class RemoteBridge(val methods: Map<String, Set<Int>>, val call: (method: String, argsJson: String) -> String)

/** What the app tells a page's view in the sandbox: see [RemoteEngine.event] */
object RemoteBrowsers {
    @Volatile
    var factory: RemoteBrowserFactory? = null

    internal val engines = ConcurrentHashMap<String, WeakReference<RemoteEngine>>()
    internal val serial = AtomicInteger()

    /** An event of the page [id] (from the app); the answer when the event asks for one (a request to look at, a question from the page) */
    @JvmStatic
    fun deliver(id: String, event: String, data: JSONObject): JSONObject? = engines[id]?.get()?.event(event, data)

    /** The remote pages that are in the view tree below [root] (a dialog's window): the app shows these to the user */
    @JvmStatic
    fun idsIn(root: View?): List<String> {
        val found = ArrayList<String>()
        fun walk(v: View?) {
            if (v is WebView) (v.engine as? RemoteEngine)?.let { found += it.id }
            if (v is ViewGroup) for (i in 0 until v.getChildCount()) walk(v.getChildAt(i))
        }
        walk(root)
        return found
    }
}

internal class RemoteEngine(private val view: WebView, private val remote: RemoteBrowser, val id: String) : WebViewBackend {
    private val main = Handler(Looper.getMainLooper())

    @Volatile override var title: String? = null
    @Volatile override var progress: Int = 0
    @Volatile override var originalUrl: String? = null
    @Volatile override var contentHeight: Int = 0
    @Volatile private var url: String? = null
    @Volatile private var canBack = false
    @Volatile private var canForward = false
    private val interfaces = ConcurrentHashMap<String, Any>()

    init {
        RemoteBrowsers.engines[id] = WeakReference(this)
        clientChanged()
    }

    // ---- what the view is asked to do: sent to the app's page ---------------------------------------------------------------------------------------

    /** The settings the page needs to know, sent with every load (the extension sets them on the view after it was made) */
    private fun settingsJson(): JSONObject {
        val s = view.jcefSettings()
        return JSONObject().put("js", s.javaScript).put("dom", s.domStorage).put("ua", s.userAgent ?: JSONObject.NULL)
            .put("blockImages", s.blockImages || !s.loadImages).put("blockNetwork", s.blockNetwork)
    }

    /** The cookies this process holds for the site of [url] (an extension that set one before the page was opened): the app's page starts with them */
    private fun cookiesFor(url: String): JSONArray {
        val out = JSONArray()
        val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull() ?: return out
        for (c in WebCookies.all()) {
            val d = c.domain.trimStart('.').lowercase()
            if (host == d || host.endsWith(".$d")) {
                out.put(JSONObject().put("name", c.name).put("value", c.value).put("domain", c.domain).put("path", c.path)
                    .put("expires", c.expires ?: JSONObject.NULL).put("secure", c.secure).put("httpOnly", c.httpOnly))
            }
        }
        return out
    }

    override fun clientChanged() {
        val c = view.client
        fun overridden(name: String, vararg types: Class<*>): Boolean = try {
            c.javaClass.getMethod(name, *types).declaringClass != WebViewClient::class.java
        } catch (e: NoSuchMethodException) {
            false
        }
        val chrome = view.chromeClient
        fun chromeOverridden(name: String, vararg types: Class<*>): Boolean = chrome != null && try {
            chrome.javaClass.getMethod(name, *types).declaringClass != WebChromeClient::class.java
        } catch (e: NoSuchMethodException) {
            false
        }
        remote.op(
            "client",
            JSONObject()
                .put("intercept", overridden("shouldInterceptRequest", WebView::class.java, WebResourceRequest::class.java) || overridden("shouldInterceptRequest", WebView::class.java, String::class.java))
                .put("override", overridden("shouldOverrideUrlLoading", WebView::class.java, WebResourceRequest::class.java) || overridden("shouldOverrideUrlLoading", WebView::class.java, String::class.java))
                .put("loadResource", overridden("onLoadResource", WebView::class.java, String::class.java))
                .put("httpError", overridden("onReceivedHttpError", WebView::class.java, WebResourceRequest::class.java, WebResourceResponse::class.java))
                .put("console", chromeOverridden("onConsoleMessage", ConsoleMessage::class.java))
                .put("alert", chromeOverridden("onJsAlert", WebView::class.java, String::class.java, String::class.java, JsResult::class.java))
                .put("confirm", chromeOverridden("onJsConfirm", WebView::class.java, String::class.java, String::class.java, JsResult::class.java))
                .put("prompt", chromeOverridden("onJsPrompt", WebView::class.java, String::class.java, String::class.java, String::class.java, JsPromptResult::class.java)),
        )
    }

    override fun load(url: String, headers: Map<String, String>, postData: ByteArray?) {
        originalUrl = url
        val o = JSONObject().put("url", url).put("headers", JSONObject(headers)).put("settings", settingsJson()).put("cookies", cookiesFor(url))
        if (postData != null) o.put("post", Base64.getEncoder().encodeToString(postData))
        remote.op("load", o)
    }

    override fun loadDataAt(baseUrl: String, data: String, mime: String, encoding: String) {
        originalUrl = baseUrl
        remote.op("loadData", JSONObject().put("base", baseUrl).put("data", data).put("mime", mime).put("encoding", encoding).put("settings", settingsJson()))
    }

    override fun reload() = remote.op("reload", JSONObject())
    override fun stopLoading() = remote.op("stop", JSONObject())
    override fun canGoBack(): Boolean = canBack
    override fun canGoForward(): Boolean = canForward
    override fun goBack() = remote.op("back", JSONObject())
    override fun goForward() = remote.op("forward", JSONObject())
    override fun clearCache() = remote.op("clearCache", JSONObject())
    override fun clearStorage(origin: String?) = remote.op("clearStorage", JSONObject().put("origin", origin ?: JSONObject.NULL))
    override fun find(text: String) {}

    override fun evaluate(script: String, callback: ValueCallback<String>?) {
        remote.op("settings", JSONObject().put("settings", settingsJson()))
        remote.evaluate(script) { result -> if (callback != null) main.post { callback.onReceiveValue(result ?: "null") } }
    }

    override fun addJavascriptInterface(obj: Any, name: String) {
        interfaces[name] = obj
        val methods = obj.javaClass.methods.filter { it.isAnnotationPresent(JavascriptInterface::class.java) }
            .groupBy { it.name }.map { (n, ms) -> JSONObject().put("name", n).put("counts", JSONArray(ms.map { it.parameterCount }.distinct())) }
        remote.op("bridge", JSONObject().put("name", name).put("methods", JSONArray(methods)))
    }

    override fun removeJavascriptInterface(name: String) {
        interfaces.remove(name)
        remote.op("unbridge", JSONObject().put("name", name))
    }

    override fun dispatchMouse(event: MotionEvent) {
        remote.op(
            "mouse",
            JSONObject().put("action", event.getActionMasked()).put("x", event.getX().toDouble()).put("y", event.getY().toDouble())
                .put("downTime", event.getDownTime()).put("eventTime", event.getEventTime())
                .put("vscroll", event.getAxisValue(MotionEvent.AXIS_VSCROLL).toDouble()),
        )
    }

    override fun applyUserAgent(ua: String) = remote.op("settings", JSONObject().put("settings", settingsJson()))

    // the size and the place on screen are the app's: it shows the page in a window of its own (see RemoteBrowserHost) and tells this view its size
    override fun layoutChanged() {}
    override fun visibilityChanged() {}
    override fun attachedChanged(isAttached: Boolean) {}
    override fun uiComponent(): java.awt.Component? = null

    override fun destroy() {
        RemoteBrowsers.engines.remove(id)
        remote.op("destroy", JSONObject())
    }

    override fun currentUrl(): String? = url

    // ---- what the app's page tells this view --------------------------------------------------------------------------------------------------------

    private fun request(d: JSONObject): WebResourceRequest {
        val headers = HashMap<String, String>()
        d.optJSONObject("headers")?.let { h -> for (k in h.keys()) headers[k] = h.optString(k) }
        return WebResourceRequestImpl(android.net.Uri.parse(d.optString("url")), d.optString("method", "GET"), headers, d.optBoolean("main"), d.optBoolean("redirect"), d.optBoolean("gesture"))
    }

    private fun readState(d: JSONObject) {
        if (d.has("url")) url = d.optString("url")
        canBack = d.optBoolean("back", canBack)
        canForward = d.optBoolean("forward", canForward)
        if (d.has("title") && !d.isNull("title")) title = d.optString("title")
        if (d.has("height")) contentHeight = d.optInt("height")
        // the user agent the app's page really has: the extension takes it for its own requests (a Cloudflare clearance is bound to it)
        d.optString("ua").takeIf { it.isNotBlank() }?.let {
            if (com.lagradost.desktop.runtime.web.WebRuntime.reportedUserAgent != it) android.util.Log.i("RemoteBrowser", "the page's user agent: $it")
            com.lagradost.desktop.runtime.web.WebRuntime.reportedUserAgent = it
        }
    }

    /** The answer for an event that asks one is a JSON object (null: nothing to say); the others are handled on the main thread, as in the phone's WebView */
    fun event(name: String, d: JSONObject): JSONObject? {
        when (name) {
            "started" -> {
                readState(d)
                progress = 10
                main.post { view.client.onPageStarted(view, d.optString("url"), null); view.chromeClient?.onProgressChanged(view, 10) }
            }
            "finished" -> {
                readState(d)
                // the cookies the page has just set are in this process's jar before onPageFinished (a login looks for its cookie there)
                d.optJSONArray("cookies")?.let { a ->
                    for (i in 0 until a.length()) runCatching {
                        val c = a.getJSONObject(i)
                        WebCookies.put(
                            WebCookie(
                                c.getString("name"), c.getString("value"), c.getString("domain"), c.optString("path", "/"),
                                if (c.isNull("expires")) null else c.getLong("expires"), c.optBoolean("secure"), c.optBoolean("httpOnly"),
                            ),
                        )
                    }
                }
                progress = 100
                main.post {
                    view.chromeClient?.onProgressChanged(view, 100)
                    view.client.onPageCommitVisible(view, d.optString("url"))
                    view.client.onPageFinished(view, d.optString("url"))
                }
            }
            "progress" -> {
                progress = d.optInt("p")
                main.post { view.chromeClient?.onProgressChanged(view, d.optInt("p")) }
            }
            "title" -> {
                title = d.optString("t")
                main.post { view.chromeClient?.onReceivedTitle(view, d.optString("t")) }
            }
            "history" -> {
                readState(d)
                main.post { view.client.doUpdateVisitedHistory(view, d.optString("url"), false) }
            }
            "error" -> main.post {
                view.client.onReceivedError(view, request(d), WebResourceErrorImpl(d.optInt("code", -1), d.optString("description")))
            }
            "httpError" -> {
                val response = WebResourceResponse(d.optString("mime", "text/html"), "utf-8", null)
                response.setStatusCodeAndReasonPhrase(d.optInt("status"), d.optString("reason"))
                main.post { view.client.onReceivedHttpError(view, request(d), response) }
            }
            "loadResource" -> main.post { view.client.onLoadResource(view, d.optString("url")) }
            "size" -> main.post { view.layout(0, 0, d.optInt("w"), d.optInt("h")) }
            "console" -> main.post {
                val level = runCatching { ConsoleMessage.MessageLevel.valueOf(d.optString("level", "LOG")) }.getOrDefault(ConsoleMessage.MessageLevel.LOG)
                view.chromeClient?.onConsoleMessage(ConsoleMessage(d.optString("message"), d.optString("source"), d.optInt("line"), level))
            }
            "intercept" -> {
                val response = runCatching { view.client.shouldInterceptRequest(view, request(d)) }.getOrNull() ?: return null
                val body = runCatching { response.getData()?.use { it.readNBytes(MAX_BODY) } }.getOrNull() ?: ByteArray(0)
                val headers = JSONObject()
                response.getResponseHeaders()?.forEach { (k, v) -> headers.put(k, v) }
                return JSONObject().put("mime", response.getMimeType() ?: "text/plain").put("enc", response.getEncoding() ?: "utf-8")
                    .put("status", response.getStatusCode()).put("reason", response.getReasonPhrase() ?: "OK").put("headers", headers)
                    .put("body", Base64.getEncoder().encodeToString(body))
            }
            "override" -> {
                val stop = onMain(5000, false) { view.client.shouldOverrideUrlLoading(view, request(d)) }
                return JSONObject().put("v", stop)
            }
            "alert" -> return dialog(d) { c, r -> c.onJsAlert(view, d.optString("url"), d.optString("message"), r) }
            "confirm" -> return dialog(d) { c, r -> c.onJsConfirm(view, d.optString("url"), d.optString("message"), r) }
            "prompt" -> return dialog(d, prompt = true) { c, r -> c.onJsPrompt(view, d.optString("url"), d.optString("message"), d.optString("default"), r as JsPromptResult) }
            // the page calls a method of an object the extension gave to addJavascriptInterface
            "bridge" -> {
                val json = JsBridge.invoke(interfaces, d.optString("name"), d.optString("method"), d.optString("args", "[]"))
                return JSONObject(json)
            }
        }
        return null
    }

    private fun <T> onMain(timeoutMs: Long, fallback: T, block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return runCatching(block).getOrDefault(fallback)
        val result = java.util.concurrent.atomic.AtomicReference<Any?>(fallback)
        val done = java.util.concurrent.CountDownLatch(1)
        main.post { try { result.set(block()) } catch (_: Throwable) {} finally { done.countDown() } }
        done.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        @Suppress("UNCHECKED_CAST")
        return result.get() as T
    }

    /** A script dialog of the page (alert, confirm, prompt): the extension's WebChromeClient answers it, or the dialog is dismissed */
    private fun dialog(d: JSONObject, prompt: Boolean = false, ask: (WebChromeClient, JsResult) -> Boolean): JSONObject {
        val chrome = view.chromeClient ?: return JSONObject().put("handled", false)
        val answer = java.util.concurrent.atomic.AtomicReference<Pair<Boolean, String?>>()
        val done = java.util.concurrent.CountDownLatch(1)
        val resolve: (Boolean, String?) -> Unit = { ok, text -> answer.compareAndSet(null, ok to text); done.countDown() }
        val handled = onMain(5000, false) {
            ask(chrome, if (prompt) JsPromptResult(resolve) else JsResult(resolve))
        }
        if (!handled) return JSONObject().put("handled", false)
        done.await(60, java.util.concurrent.TimeUnit.SECONDS)
        val (ok, text) = answer.get() ?: (false to null)
        return JSONObject().put("handled", true).put("ok", ok).put("text", text ?: JSONObject.NULL)
    }

    companion object {
        /** A replacement for a request is read up to this much (an extension that answers a request with a page of its own) */
        private const val MAX_BODY = 4 * 1024 * 1024
    }
}
