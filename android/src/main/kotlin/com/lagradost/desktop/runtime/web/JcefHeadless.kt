package com.lagradost.desktop.runtime.web

import android.util.Log
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefCallback
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.network.CefRequest
import org.json.JSONObject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * HeadlessBrowser on bundled Chromium (JCEF), where WebView2 is missing.
 * Requests are observed natively (no blocking interception), so pages load at full speed.
 */
internal class JcefHeadless(
    private val userAgent: String?,
    private val onRequest: (HeadlessBrowser.Request) -> HeadlessBrowser.Decision,
    private val onPageFinished: ((String) -> Unit)? = null,
) : HeadlessBrowser.Impl {
    companion object {
        private const val TAG = "HeadlessBrowser"
        private val executor = Executors.newCachedThreadPool { r -> Thread(r, "headless-browser").also { it.isDaemon = true } }
    }

    @Volatile private var client: CefClient? = null
    @Volatile private var browser: CefBrowser? = null
    private val created = CompletableFuture<CefBrowser>()
    @Volatile override var destroyed = false
        private set
    private val pendingHeaders = ConcurrentHashMap<String, Map<String, String>>()

    init {
        executor.execute {
            try {
                val c = JcefRuntime.createClient()
                client = c
                c.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
                    override fun onAfterCreated(b: CefBrowser) {
                        if (b !== browser) return
                        executor.execute {
                            devTools(b, "Emulation.setDeviceMetricsOverride", JSONObject().put("width", 1280).put("height", 720).put("deviceScaleFactor", 1).put("mobile", false))
                            if (userAgent != null) devTools(b, "Emulation.setUserAgentOverride", JSONObject().put("userAgent", userAgent))
                            created.complete(b)
                        }
                    }

                    override fun onBeforePopup(b: CefBrowser?, frame: CefFrame?, targetUrl: String?, targetFrameName: String?): Boolean = true
                })
                c.addLoadHandler(object : CefLoadHandlerAdapter() {
                    override fun onLoadEnd(b: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                        if (b === browser && frame.isMain) {
                            val url = frame.url ?: return
                            if (url != "about:blank") onPageFinished?.invoke(url)
                        }
                    }

                    override fun onLoadError(b: CefBrowser?, frame: CefFrame?, errorCode: CefLoadHandler.ErrorCode?, errorText: String?, failedUrl: String?) {
                        if (b === browser && frame?.isMain == true && errorCode != CefLoadHandler.ErrorCode.ERR_ABORTED) {
                            Log.w(TAG, "Load error $errorCode for $failedUrl")
                        }
                    }
                })
                c.addRequestHandler(object : CefRequestHandlerAdapter() {
                    override fun getResourceRequestHandler(
                        b: CefBrowser?, frame: CefFrame?, request: CefRequest, isNavigation: Boolean, isDownload: Boolean,
                        requestInitiator: String?, disableDefaultHandling: BoolRef?
                    ): CefResourceRequestHandler? {
                        if (b !== browser) return null
                        val main = frame?.isMain == true
                        return object : CefResourceRequestHandlerAdapter() {
                            override fun onBeforeResourceLoad(b: CefBrowser?, frame: CefFrame?, req: CefRequest): Boolean {
                                val url = req.url ?: return false
                                if (url == "about:blank") return false
                                if (isNavigation && main) {
                                    pendingHeaders.remove(url)?.let { hs ->
                                        for ((k, v) in hs) {
                                            if (k.equals("Referer", true)) req.setReferrer(v, CefRequest.ReferrerPolicy.REFERRER_POLICY_DEFAULT)
                                            else req.setHeaderByName(k, v, true)
                                        }
                                    }
                                }
                                val headers = HashMap<String, String>()
                                req.getHeaderMap(headers)
                                req.referrerURL?.takeIf { it.isNotEmpty() && headers.keys.none { k -> k.equals("Referer", true) } }?.let { headers["Referer"] = it }
                                val decision = try {
                                    onRequest(HeadlessBrowser.Request(url, req.method ?: "GET", headers, main, isNavigation, req.resourceType?.name))
                                } catch (t: Throwable) {
                                    HeadlessBrowser.Decision.CONTINUE
                                }
                                return decision == HeadlessBrowser.Decision.BLOCK
                            }
                        }
                    }

                    override fun onCertificateError(b: CefBrowser?, certError: CefLoadHandler.ErrorCode?, requestUrl: String?, callback: CefCallback): Boolean {
                        // Same behaviour as the Android WebViewResolver: ignore ssl issues
                        callback.Continue()
                        return true
                    }
                })
                val b = c.createBrowser("about:blank", true, false)
                browser = b
                b.createImmediately()
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to create headless browser: ${Log.getStackTraceString(t)}")
                created.completeExceptionally(t)
            }
        }
    }

    private fun devTools(b: CefBrowser, method: String, params: JSONObject? = null): String? = try {
        b.devToolsClient.executeDevToolsMethod(method, params?.toString()).get(15, TimeUnit.SECONDS)
    } catch (t: Throwable) {
        null
    }

    private fun withBrowser(block: (CefBrowser) -> Unit) {
        created.thenAcceptAsync({ b -> if (!destroyed) block(b) }, executor)
    }

    override fun load(url: String, headers: Map<String, String>) {
        if (headers.isNotEmpty()) pendingHeaders[url] = headers
        withBrowser { it.loadURL(url) }
    }

    /** Evaluate JavaScript in the page, [callback] gets the JSON encoded result */
    override fun evaluate(script: String, callback: ((String) -> Unit)?) {
        withBrowser { b ->
            val r = devTools(
                b, "Runtime.evaluate",
                JSONObject().put("expression", script).put("returnByValue", true).put("allowUnsafeEvalBlockedByCSP", true).put("userGesture", true)
            )
            if (callback != null) {
                val value = try {
                    val result = JSONObject(r ?: "{}").optJSONObject("result")
                    if (result == null || result.optString("type") == "undefined" || !result.has("value")) "null"
                    else JSONObject.valueToString(result.opt("value"))
                } catch (t: Throwable) {
                    "null"
                }
                callback(value)
            }
        }
    }

    override fun currentUrl(): String? = browser?.url

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        val b = browser
        val c = client
        browser = null
        client = null
        executor.execute {
            try {
                b?.stopLoad()
                b?.close(true)
            } catch (_: Throwable) {
            }
            try {
                c?.dispose()
            } catch (_: Throwable) {
            }
        }
    }
}
