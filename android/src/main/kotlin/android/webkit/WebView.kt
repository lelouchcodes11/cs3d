package android.webkit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Picture
import android.net.http.SslCertificate
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.AbsoluteLayout
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * android.webkit.WebView implemented with Chromium (JCEF). Each instance owns an off-screen
 * browser; when the view is shown by the Compose renderer the browser surface is displayed.
 */
@Suppress("DEPRECATION")
open class WebView : AbsoluteLayout {
    companion object {
        const val SCHEME_TEL = "tel:"
        const val SCHEME_MAILTO = "mailto:"
        const val SCHEME_GEO = "geo:0,0?q="
        const val RENDERER_PRIORITY_WAIVED = 0
        const val RENDERER_PRIORITY_BOUND = 1
        const val RENDERER_PRIORITY_IMPORTANT = 2

        private val live = CopyOnWriteArrayList<WeakReference<WebView>>()

        @Volatile
        private var debuggingEnabled = false

        @JvmStatic
        fun setWebContentsDebuggingEnabled(enabled: Boolean) {
            debuggingEnabled = enabled
        }

        @JvmStatic
        fun clearClientCertPreferences(onCleared: Runnable?) {
            onCleared?.run()
        }

        @JvmStatic
        fun setDataDirectorySuffix(suffix: String?) {}

        @JvmStatic
        fun disableWebView() {}

        @JvmStatic
        fun getCurrentWebViewPackage(): android.content.pm.PackageInfo? = null

        @JvmStatic
        fun setSafeBrowsingWhitelist(hosts: List<String>?, callback: ValueCallback<Boolean>?) {
            callback?.onReceiveValue(true)
        }

        @JvmStatic
        fun startSafeBrowsing(context: Context?, callback: ValueCallback<Boolean>?) {
            callback?.onReceiveValue(true)
        }

        @JvmStatic
        fun getSafeBrowsingPrivacyPolicyUrl(): android.net.Uri = android.net.Uri.parse("https://www.google.com/chrome/privacy/")

        internal fun forEachLive(block: (WebView) -> Unit) {
            for (ref in live) {
                val v = ref.get()
                if (v == null) live.remove(ref) else block(v)
            }
        }
    }

    inner class WebViewTransport {
        private var mWebview: WebView? = null

        @Synchronized
        fun setWebView(webview: WebView?) {
            mWebview = webview
        }

        @Synchronized
        fun getWebView(): WebView? = mWebview
    }

    class HitTestResult internal constructor(private val type: Int, private val extra: String?) {
        companion object {
            const val UNKNOWN_TYPE = 0
            const val ANCHOR_TYPE = 1
            const val PHONE_TYPE = 2
            const val GEO_TYPE = 3
            const val EMAIL_TYPE = 4
            const val IMAGE_TYPE = 5
            const val IMAGE_ANCHOR_TYPE = 6
            const val SRC_ANCHOR_TYPE = 7
            const val SRC_IMAGE_ANCHOR_TYPE = 8
            const val EDIT_TEXT_TYPE = 9
        }

        fun getType(): Int = type
        fun getExtra(): String? = extra
    }

    fun interface FindListener {
        fun onFindResultReceived(activeMatchOrdinal: Int, numberOfMatches: Int, isDoneCounting: Boolean)
    }

    fun interface PictureListener {
        fun onNewPicture(view: WebView?, picture: Picture?)
    }

    abstract class VisualStateCallback {
        abstract fun onComplete(requestId: Long)
    }

    internal val engine: JcefWebViewEngine
    private val mSettings: JcefWebSettings
    @Volatile
    internal var client: WebViewClient = WebViewClient()
    @Volatile
    internal var chromeClient: WebChromeClient? = null
    @Volatile
    internal var downloadListener: DownloadListener? = null
    private var findListener: FindListener? = null

    constructor(context: Context?) : this(context, null)
    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr) {
        mSettings = JcefWebSettings(this)
        engine = JcefWebViewEngine(this)
        live.add(WeakReference(this))
        setFocusable(true)
        setFocusableInTouchMode(true)
    }

    @Deprecated("")
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, privateBrowsing: Boolean) : this(context, attrs, defStyleAttr, 0)

    internal fun jcefSettings(): JcefWebSettings = mSettings

    open fun getSettings(): WebSettings = mSettings
    open fun setWebViewClient(client: WebViewClient?) {
        this.client = client ?: WebViewClient()
        engine.clientChanged()
    }

    open fun getWebViewClient(): WebViewClient = client
    open fun setWebChromeClient(client: WebChromeClient?) {
        chromeClient = client
    }

    open fun getWebChromeClient(): WebChromeClient? = chromeClient
    open fun setDownloadListener(listener: DownloadListener?) {
        downloadListener = listener
    }

    open fun setFindListener(listener: FindListener?) {
        findListener = listener
    }

    @Deprecated("")
    open fun setPictureListener(listener: PictureListener?) {}

    open fun loadUrl(url: String) = loadUrl(url, emptyMap())
    open fun loadUrl(url: String, additionalHttpHeaders: Map<String, String>?) {
        if (url.startsWith("javascript:", ignoreCase = true)) {
            engine.evaluate(url.substring("javascript:".length), null)
            return
        }
        engine.load(url, additionalHttpHeaders ?: emptyMap(), null)
    }

    open fun postUrl(url: String, postData: ByteArray?) = engine.load(url, emptyMap(), postData ?: ByteArray(0))

    open fun loadData(data: String, mimeType: String?, encoding: String?) {
        val mime = mimeType ?: "text/html"
        val url = if ("base64".equals(encoding, ignoreCase = true)) "data:$mime;base64,$data"
        else "data:$mime;charset=utf-8;base64," + java.util.Base64.getEncoder().encodeToString(data.toByteArray(Charsets.UTF_8))
        engine.load(url, emptyMap(), null)
    }

    open fun loadDataWithBaseURL(baseUrl: String?, data: String, mimeType: String?, encoding: String?, historyUrl: String?) {
        if (baseUrl == null || baseUrl.startsWith("data:") || !(baseUrl.startsWith("http://") || baseUrl.startsWith("https://"))) {
            loadData(data, mimeType, null)
            return
        }
        engine.loadDataAt(baseUrl, data, mimeType ?: "text/html", encoding ?: "utf-8")
    }

    open fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) = engine.evaluate(script, resultCallback)

    open fun addJavascriptInterface(obj: Any, name: String) = engine.addJavascriptInterface(obj, name)
    open fun removeJavascriptInterface(name: String) = engine.removeJavascriptInterface(name)

    open fun reload() = engine.reload()
    open fun stopLoading() = engine.stopLoading()
    open fun canGoBack(): Boolean = engine.canGoBack()
    open fun goBack() = engine.goBack()
    open fun canGoForward(): Boolean = engine.canGoForward()
    open fun goForward() = engine.goForward()
    open fun canGoBackOrForward(steps: Int): Boolean = if (steps < 0) canGoBack() else if (steps > 0) canGoForward() else true
    open fun goBackOrForward(steps: Int) {
        if (steps < 0) repeat(-steps) { goBack() } else repeat(steps) { goForward() }
    }

    open fun getUrl(): String? = engine.currentUrl()
    open fun getOriginalUrl(): String? = engine.originalUrl
    open fun getTitle(): String? = engine.title
    open fun getFavicon(): Bitmap? = null
    open fun getProgress(): Int = engine.progress
    open fun getContentHeight(): Int = engine.contentHeight

    @Deprecated("")
    open fun getScale(): Float = 1f
    open fun getCertificate(): SslCertificate? = null
    open fun getHitTestResult(): HitTestResult = HitTestResult(HitTestResult.UNKNOWN_TYPE, null)
    open fun copyBackForwardList(): WebBackForwardList = WebBackForwardList(engine.currentUrl())
    open fun saveState(outState: Bundle): WebBackForwardList? = null
    open fun restoreState(inState: Bundle): WebBackForwardList? = null

    open fun destroy() = engine.destroy()
    open fun onPause() {}
    open fun onResume() {}
    open fun pauseTimers() {}
    open fun resumeTimers() {}
    open fun clearCache(includeDiskFiles: Boolean) = engine.clearCache()
    open fun clearHistory() {}
    open fun clearFormData() {}
    open fun clearMatches() {}
    open fun clearSslPreferences() {}
    open fun freeMemory() {}
    open fun findAllAsync(find: String?) {
        if (!find.isNullOrEmpty()) engine.find(find)
    }

    open fun findNext(forward: Boolean) {}
    open fun setInitialScale(scaleInPercent: Int) {}
    open fun zoomIn(): Boolean = false
    open fun zoomOut(): Boolean = false
    open fun zoomBy(zoomFactor: Float) {}
    open fun pageUp(top: Boolean): Boolean {
        engine.evaluate(if (top) "window.scrollTo(0,0)" else "window.scrollBy(0,-window.innerHeight)", null)
        return true
    }

    open fun pageDown(bottom: Boolean): Boolean {
        engine.evaluate(if (bottom) "window.scrollTo(0,document.body.scrollHeight)" else "window.scrollBy(0,window.innerHeight)", null)
        return true
    }

    open fun flingScroll(vx: Int, vy: Int) {}
    override fun scrollBy(x: Int, y: Int) {
        engine.evaluate("window.scrollBy($x,$y)", null)
    }

    override fun scrollTo(x: Int, y: Int) {
        super.scrollTo(x, y)
        engine.evaluate("window.scrollTo($x,$y)", null)
    }

    open fun setNetworkAvailable(networkUp: Boolean) {}
    open fun setMapTrackballToArrowKeys(setMap: Boolean) {}
    open fun setRendererPriorityPolicy(rendererRequestedPriority: Int, waivedWhenNotVisible: Boolean) {}
    open fun getRendererRequestedPriority(): Int = RENDERER_PRIORITY_IMPORTANT
    open fun postVisualStateCallback(requestId: Long, callback: VisualStateCallback) {
        post { callback.onComplete(requestId) }
    }

    open fun postWebMessage(message: Any?, targetOrigin: android.net.Uri?) {}
    open fun documentHasImages(response: Message?) {
        response?.sendToTarget()
    }

    open fun requestFocusNodeHref(hrefMsg: Message?) {}
    open fun requestImageRef(msg: Message?) {}
    open fun capturePicture(): Picture? = null
    open fun createPrintDocumentAdapter(documentName: String?): Any? = null
    open fun setTextClassifier(textClassifier: Any?) {}
    open fun getWebViewLooper(): Looper = Looper.getMainLooper()
    override fun getHandler(): Handler = Handler(Looper.getMainLooper())

    /** Synthetic touches (used by extensions to click elements) are forwarded as mouse input */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (mOnTouchListener?.onTouch(this, event) == true) return true
        engine.dispatchMouse(event)
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        engine.dispatchMouse(event)
        return true
    }

    override fun setLayoutParams(params: ViewGroup.LayoutParams?) {
        super.setLayoutParams(params)
        engine.layoutChanged()
    }

    override fun layout(l: Int, t: Int, r: Int, b: Int) {
        super.layout(l, t, r, b)
        engine.layoutChanged()
    }

    /**
     * AbsoluteLayout with no children measures to 0 under AT_MOST, so a match_parent WebView in a
     * wrap-content dialog collapses and the page is clipped. Fill the space the parent offered.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        var w = getMeasuredWidth()
        var h = getMeasuredHeight()
        val wMode = MeasureSpec.getMode(widthMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val wSize = MeasureSpec.getSize(widthMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)
        if (w == 0 && wMode != MeasureSpec.UNSPECIFIED && wSize > 0) w = wSize
        if (h == 0 && hMode != MeasureSpec.UNSPECIFIED && hSize > 0) h = hSize
        if (w != getMeasuredWidth() || h != getMeasuredHeight()) setMeasuredDimension(w, h)
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        // also called by View(context, attrs) before the engine exists
        if (constructed) engine.visibilityChanged()
    }

    override fun dispatchAttachedToWindow() {
        super.dispatchAttachedToWindow()
        engine.attachedChanged(true)
    }

    override fun dispatchDetachedFromWindow() {
        super.dispatchDetachedFromWindow()
        engine.attachedChanged(false)
    }

    /** Desktop: the AWT component showing the page (used by the Compose renderer) */
    fun uiComponent(): java.awt.Component? = engine.uiComponent()

    internal fun reportFind(active: Int, count: Int) {
        findListener?.onFindResultReceived(active, count, true)
    }

    private var constructed = false

    init {
        constructed = true
    }
}

open class WebBackForwardList internal constructor(private val url: String?) : Cloneable {
    open fun getCurrentItem(): WebHistoryItem? = url?.let { WebHistoryItem(it) }
    open fun getCurrentIndex(): Int = if (url == null) -1 else 0
    open fun getItemAtIndex(index: Int): WebHistoryItem? = if (index == 0) getCurrentItem() else null
    open fun getSize(): Int = if (url == null) 0 else 1
    public override fun clone(): WebBackForwardList = WebBackForwardList(url)
}

open class WebHistoryItem internal constructor(private val url: String) : Cloneable {
    open fun getUrl(): String = url
    open fun getOriginalUrl(): String = url
    open fun getTitle(): String? = null
    open fun getFavicon(): Bitmap? = null
    public override fun clone(): WebHistoryItem = WebHistoryItem(url)
}
