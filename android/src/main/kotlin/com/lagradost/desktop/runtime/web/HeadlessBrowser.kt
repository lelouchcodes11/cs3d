package com.lagradost.desktop.runtime.web

import android.util.Log
import com.lagradost.desktop.runtime.web.wv2.Com
import com.lagradost.desktop.runtime.web.wv2.WebView2Runtime
import com.lagradost.desktop.runtime.web.wv2.Wv2Browser
import com.sun.jna.Pointer
import com.sun.jna.WString

/**
 * Minimal hidden browser used by WebViewResolver and CloudflareKiller: every request of the page goes through [onRequest], which can block it.
 * Edge WebView2 when Windows has it, bundled Chromium (JCEF) otherwise.
 */
class HeadlessBrowser(
    userAgent: String?,
    onRequest: (Request) -> Decision,
    onPageFinished: ((String) -> Unit)? = null,
) {
    data class Request(
        val url: String,
        val method: String,
        val headers: Map<String, String>,
        val isMainFrame: Boolean,
        val isNavigation: Boolean,
        val resourceType: String?,
    )

    enum class Decision { CONTINUE, BLOCK }

    internal interface Impl {
        val destroyed: Boolean
        fun load(url: String, headers: Map<String, String>)
        fun evaluate(script: String, callback: ((String) -> Unit)?)
        fun currentUrl(): String?
        fun destroy()
    }

    private val impl: Impl =
        if (WebView2Runtime.isAvailable) Wv2Headless(userAgent, onRequest, onPageFinished) else JcefHeadless(userAgent, onRequest, onPageFinished)

    val destroyed: Boolean get() = impl.destroyed

    fun load(url: String, headers: Map<String, String> = emptyMap()) = impl.load(url, headers)

    /** Evaluate JavaScript in the page, [callback] gets the JSON encoded result */
    fun evaluate(script: String, callback: ((String) -> Unit)?) = impl.evaluate(script, callback)

    fun currentUrl(): String? = impl.currentUrl()

    fun destroy() = impl.destroy()
}

/** HeadlessBrowser on WebView2: [onRequest] runs on the WebView2 thread for every request, a blocked one gets an empty 403 */
internal class Wv2Headless(
    private val userAgent: String?,
    private val onRequest: (HeadlessBrowser.Request) -> HeadlessBrowser.Decision,
    private val onPageFinished: ((String) -> Unit)?,
) : Wv2Browser(), HeadlessBrowser.Impl {
    companion object {
        private const val TAG = "HeadlessBrowser"
        private val contexts = arrayOf(
            "All", "Document", "Stylesheet", "Image", "Media", "Font", "Script", "XHR", "Fetch", "TextTrack", "EventSource", "WebSocket",
            "Manifest", "SignedExchange", "Ping", "CSPViolationReport", "Other",
        )
    }

    @Volatile override var destroyed = false
        private set
    @Volatile private var url: String? = null

    override fun setup(core: Pointer) {
        Com.getPtr(core, 3)?.let { settings ->
            Com.putBool(settings, 8, false)
            if (userAgent != null && userAgent != WebView2Runtime.userAgent) {
                Com.query(settings, IID_SETTINGS2)?.let { s2 ->
                    Com.call(s2, 22, WString(userAgent))
                    Com.release(s2)
                }
            }
            Com.release(settings)
        }
        Com.call(core, 57, WString("*"), CONTEXT_ALL)
        on(core, 55, IID_RESOURCE_REQUESTED) { _, a ->
            if (a == null) return@on
            val request = Com.getPtr(a, 3) ?: return@on
            try {
                val u = Com.getString(request, 3) ?: return@on
                if (u == "about:blank") return@on
                val context = Com.getInt(a, 7) ?: 0
                val r = HeadlessBrowser.Request(
                    u, Com.getString(request, 5) ?: "GET", requestHeaders(request), context == CONTEXT_DOCUMENT, context == CONTEXT_DOCUMENT,
                    contexts.getOrNull(context),
                )
                val decision = try {
                    onRequest(r)
                } catch (t: Throwable) {
                    HeadlessBrowser.Decision.CONTINUE
                }
                if (decision == HeadlessBrowser.Decision.BLOCK) respond(a, 403, "Blocked", emptyMap(), null)
            } finally {
                Com.release(request)
            }
        }
        on(core, 11, IID_SOURCE_CHANGED) { _, _ -> url = source(core) }
        on(core, 15, IID_NAVIGATION_COMPLETED) { _, _ ->
            val u = source(core) ?: return@on
            url = u
            if (u != "about:blank") runCatching { onPageFinished?.invoke(u) }
        }
        // alerts would wait for an answer for ever: accepted (confirm and prompt are cancelled)
        on(core, 21, IID_SCRIPT_DIALOG) { _, a -> if (a != null && (Com.getInt(a, 4) ?: 0).let { it == 0 || it == 3 }) Com.call(a, 6) }
        on(core, 44, IID_NEW_WINDOW) { _, a -> if (a != null) Com.putBool(a, 6, true) }
        Com.query(core, IID_CORE4)?.let { c4 ->
            on(c4, 75, IID_DOWNLOAD_STARTING) { _, a -> if (a != null) Com.putBool(a, 5, true) }
            Com.release(c4)
        }
        // Same behaviour as the Android WebViewResolver: ignore ssl issues
        Com.query(core, IID_CORE14)?.let { c14 ->
            on(c14, 106, IID_CERTIFICATE_ERROR) { _, a -> if (a != null) Com.call(a, 7, 0) }
            Com.release(c14)
        }
        devTools(core, "Emulation.setFocusEmulationEnabled", "{\"enabled\":true}")
    }

    override fun load(url: String, headers: Map<String, String>) {
        if (destroyed) return
        withCore { navigate(it, url, headers, null) }
    }

    override fun evaluate(script: String, callback: ((String) -> Unit)?) {
        if (destroyed) return
        withCore { core -> execute(core, script) { v -> runCatching { callback?.invoke(v) }.onFailure { Log.w(TAG, "callback: ${it.message}") } } }
    }

    override fun currentUrl(): String? = url

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        close()
    }
}
