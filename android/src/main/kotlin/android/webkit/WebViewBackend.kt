package android.webkit

import android.view.MotionEvent
import com.lagradost.desktop.runtime.web.wv2.WebView2Runtime

/** The browser engine behind one android.webkit.WebView: WebView2 (Windows' own Edge engine), or bundled Chromium (JCEF) where it is missing */
internal interface WebViewBackend {
    val title: String?
    val progress: Int
    val originalUrl: String?
    val contentHeight: Int

    fun clientChanged()
    fun load(url: String, headers: Map<String, String>, postData: ByteArray?)
    fun loadDataAt(baseUrl: String, data: String, mime: String, encoding: String)
    fun reload()
    fun stopLoading()
    fun canGoBack(): Boolean
    fun canGoForward(): Boolean
    fun goBack()
    fun goForward()
    fun clearCache()
    fun clearStorage(origin: String?)
    fun find(text: String)
    fun evaluate(script: String, callback: ValueCallback<String>?)
    fun addJavascriptInterface(obj: Any, name: String)
    fun removeJavascriptInterface(name: String)
    fun dispatchMouse(event: MotionEvent)
    fun applyUserAgent(ua: String)
    fun layoutChanged()
    fun visibilityChanged()
    fun attachedChanged(isAttached: Boolean)
    fun uiComponent(): java.awt.Component?
    fun destroy()
    fun currentUrl(): String?

    companion object {
        fun create(view: WebView): WebViewBackend = if (WebView2Runtime.isAvailable) WebView2Engine(view) else JcefWebViewEngine(view)
    }
}
