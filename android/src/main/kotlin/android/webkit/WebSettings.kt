package android.webkit

import android.content.Context
import com.lagradost.desktop.runtime.web.JcefRuntime

/**
 * WebSettings of a JCEF backed WebView. Settings that Chromium can apply per browser are pushed to
 * the engine (user agent, image blocking, zoom), the rest are stored for getters.
 */
abstract class WebSettings {
    enum class LayoutAlgorithm { NORMAL, SINGLE_COLUMN, NARROW_COLUMNS, TEXT_AUTOSIZING }
    enum class ZoomDensity { FAR, MEDIUM, CLOSE }
    enum class RenderPriority { NORMAL, HIGH, LOW }
    enum class PluginState { ON, ON_DEMAND, OFF }
    enum class TextSize(@JvmField val value: Int) { SMALLEST(50), SMALLER(75), NORMAL(100), LARGER(150), LARGEST(200) }

    companion object {
        const val LOAD_DEFAULT = -1
        const val LOAD_NORMAL = 0
        const val LOAD_CACHE_ELSE_NETWORK = 1
        const val LOAD_NO_CACHE = 2
        const val LOAD_CACHE_ONLY = 3
        const val MIXED_CONTENT_ALWAYS_ALLOW = 0
        const val MIXED_CONTENT_NEVER_ALLOW = 1
        const val MIXED_CONTENT_COMPATIBILITY_MODE = 2
        const val FORCE_DARK_OFF = 0
        const val FORCE_DARK_AUTO = 1
        const val FORCE_DARK_ON = 2
        const val MENU_ITEM_NONE = 0

        @JvmStatic
        fun getDefaultUserAgent(context: Context?): String = JcefRuntime.defaultUserAgent
    }

    abstract fun setJavaScriptEnabled(flag: Boolean)
    abstract fun getJavaScriptEnabled(): Boolean
    abstract fun setDomStorageEnabled(flag: Boolean)
    abstract fun getDomStorageEnabled(): Boolean
    abstract fun setUserAgentString(ua: String?)
    abstract fun getUserAgentString(): String
    abstract fun setBlockNetworkImage(flag: Boolean)
    abstract fun getBlockNetworkImage(): Boolean
    abstract fun setBlockNetworkLoads(flag: Boolean)
    abstract fun getBlockNetworkLoads(): Boolean
    abstract fun setLoadsImagesAutomatically(flag: Boolean)
    abstract fun getLoadsImagesAutomatically(): Boolean
    abstract fun setCacheMode(mode: Int)
    abstract fun getCacheMode(): Int
    abstract fun setMixedContentMode(mode: Int)
    abstract fun getMixedContentMode(): Int
    abstract fun setMediaPlaybackRequiresUserGesture(require: Boolean)
    abstract fun getMediaPlaybackRequiresUserGesture(): Boolean
    abstract fun setAllowFileAccess(allow: Boolean)
    abstract fun getAllowFileAccess(): Boolean
    abstract fun setAllowContentAccess(allow: Boolean)
    abstract fun getAllowContentAccess(): Boolean
    abstract fun setAllowFileAccessFromFileURLs(flag: Boolean)
    abstract fun setAllowUniversalAccessFromFileURLs(flag: Boolean)
    abstract fun setDatabaseEnabled(flag: Boolean)
    abstract fun getDatabaseEnabled(): Boolean
    abstract fun setUseWideViewPort(use: Boolean)
    abstract fun getUseWideViewPort(): Boolean
    abstract fun setLoadWithOverviewMode(overview: Boolean)
    abstract fun getLoadWithOverviewMode(): Boolean
    abstract fun setSupportZoom(support: Boolean)
    abstract fun supportZoom(): Boolean
    abstract fun setBuiltInZoomControls(enabled: Boolean)
    abstract fun getBuiltInZoomControls(): Boolean
    abstract fun setDisplayZoomControls(enabled: Boolean)
    abstract fun getDisplayZoomControls(): Boolean
    abstract fun setJavaScriptCanOpenWindowsAutomatically(flag: Boolean)
    abstract fun getJavaScriptCanOpenWindowsAutomatically(): Boolean
    abstract fun setSupportMultipleWindows(support: Boolean)
    abstract fun supportMultipleWindows(): Boolean
    abstract fun setTextZoom(textZoom: Int)
    abstract fun getTextZoom(): Int
    abstract fun setGeolocationEnabled(flag: Boolean)
    abstract fun setSaveFormData(save: Boolean)
    abstract fun setSavePassword(save: Boolean)
    abstract fun setLayoutAlgorithm(l: LayoutAlgorithm?)
    abstract fun getLayoutAlgorithm(): LayoutAlgorithm
    abstract fun setDefaultTextEncodingName(encoding: String?)
    abstract fun getDefaultTextEncodingName(): String
    abstract fun setMinimumFontSize(size: Int)
    abstract fun setDefaultFontSize(size: Int)
    abstract fun setDefaultFixedFontSize(size: Int)
    abstract fun setStandardFontFamily(font: String?)
    abstract fun setOffscreenPreRaster(enabled: Boolean)
    abstract fun setSafeBrowsingEnabled(enabled: Boolean)
    abstract fun setForceDark(forceDark: Int)
    abstract fun setAlgorithmicDarkeningAllowed(allow: Boolean)
    abstract fun setNeedInitialFocus(flag: Boolean)
    abstract fun setRenderPriority(priority: RenderPriority?)
    abstract fun setPluginState(state: PluginState?)
    abstract fun setAppCacheEnabled(flag: Boolean)
    abstract fun setAppCachePath(appCachePath: String?)
    abstract fun setDisabledActionModeMenuItems(menuItems: Int)
    abstract fun setEnableSmoothTransition(enable: Boolean)
    abstract fun setLoadsImagesAutomaticallyCompat(flag: Boolean)
}

internal class JcefWebSettings(private val view: WebView) : WebSettings() {
    @Volatile var javaScript = false
    @Volatile var domStorage = false
    @Volatile var userAgent: String? = null
    @Volatile var blockImages = false
    @Volatile var blockNetwork = false
    @Volatile var loadImages = true
    private var cacheMode = LOAD_DEFAULT
    private var mixedContent = MIXED_CONTENT_NEVER_ALLOW
    private var mediaGesture = true
    private var fileAccess = false
    private var contentAccess = true
    private var database = false
    private var wideViewPort = false
    private var overview = false
    private var zoom = true
    private var builtInZoom = false
    private var displayZoom = true
    @Volatile var jsOpenWindows = false
    @Volatile var multipleWindows = false
    private var textZoom = 100
    private var layout = LayoutAlgorithm.NARROW_COLUMNS
    private var encoding = "UTF-8"

    override fun setJavaScriptEnabled(flag: Boolean) {
        javaScript = flag
    }

    override fun getJavaScriptEnabled(): Boolean = javaScript
    override fun setDomStorageEnabled(flag: Boolean) {
        domStorage = flag
    }

    override fun getDomStorageEnabled(): Boolean = domStorage
    override fun setUserAgentString(ua: String?) {
        userAgent = ua?.takeIf { it.isNotBlank() }
        view.engine.applyUserAgent(getUserAgentString())
    }

    override fun getUserAgentString(): String = userAgent ?: JcefRuntime.defaultUserAgent
    override fun setBlockNetworkImage(flag: Boolean) {
        blockImages = flag
    }

    override fun getBlockNetworkImage(): Boolean = blockImages
    override fun setBlockNetworkLoads(flag: Boolean) {
        blockNetwork = flag
    }

    override fun getBlockNetworkLoads(): Boolean = blockNetwork
    override fun setLoadsImagesAutomatically(flag: Boolean) {
        loadImages = flag
    }

    override fun getLoadsImagesAutomatically(): Boolean = loadImages
    override fun setCacheMode(mode: Int) {
        cacheMode = mode
    }

    override fun getCacheMode(): Int = cacheMode
    override fun setMixedContentMode(mode: Int) {
        mixedContent = mode
    }

    override fun getMixedContentMode(): Int = mixedContent
    override fun setMediaPlaybackRequiresUserGesture(require: Boolean) {
        mediaGesture = require
    }

    override fun getMediaPlaybackRequiresUserGesture(): Boolean = mediaGesture
    override fun setAllowFileAccess(allow: Boolean) {
        fileAccess = allow
    }

    override fun getAllowFileAccess(): Boolean = fileAccess
    override fun setAllowContentAccess(allow: Boolean) {
        contentAccess = allow
    }

    override fun getAllowContentAccess(): Boolean = contentAccess
    override fun setAllowFileAccessFromFileURLs(flag: Boolean) {}
    override fun setAllowUniversalAccessFromFileURLs(flag: Boolean) {}
    override fun setDatabaseEnabled(flag: Boolean) {
        database = flag
    }

    override fun getDatabaseEnabled(): Boolean = database
    override fun setUseWideViewPort(use: Boolean) {
        wideViewPort = use
    }

    override fun getUseWideViewPort(): Boolean = wideViewPort
    override fun setLoadWithOverviewMode(overview: Boolean) {
        this.overview = overview
    }

    override fun getLoadWithOverviewMode(): Boolean = overview
    override fun setSupportZoom(support: Boolean) {
        zoom = support
    }

    override fun supportZoom(): Boolean = zoom
    override fun setBuiltInZoomControls(enabled: Boolean) {
        builtInZoom = enabled
    }

    override fun getBuiltInZoomControls(): Boolean = builtInZoom
    override fun setDisplayZoomControls(enabled: Boolean) {
        displayZoom = enabled
    }

    override fun getDisplayZoomControls(): Boolean = displayZoom
    override fun setJavaScriptCanOpenWindowsAutomatically(flag: Boolean) {
        jsOpenWindows = flag
    }

    override fun getJavaScriptCanOpenWindowsAutomatically(): Boolean = jsOpenWindows
    override fun setSupportMultipleWindows(support: Boolean) {
        multipleWindows = support
    }

    override fun supportMultipleWindows(): Boolean = multipleWindows
    override fun setTextZoom(textZoom: Int) {
        this.textZoom = textZoom
    }

    override fun getTextZoom(): Int = textZoom
    override fun setGeolocationEnabled(flag: Boolean) {}
    override fun setSaveFormData(save: Boolean) {}
    override fun setSavePassword(save: Boolean) {}
    override fun setLayoutAlgorithm(l: LayoutAlgorithm?) {
        layout = l ?: LayoutAlgorithm.NORMAL
    }

    override fun getLayoutAlgorithm(): LayoutAlgorithm = layout
    override fun setDefaultTextEncodingName(encoding: String?) {
        this.encoding = encoding ?: "UTF-8"
    }

    override fun getDefaultTextEncodingName(): String = encoding
    override fun setMinimumFontSize(size: Int) {}
    override fun setDefaultFontSize(size: Int) {}
    override fun setDefaultFixedFontSize(size: Int) {}
    override fun setStandardFontFamily(font: String?) {}
    override fun setOffscreenPreRaster(enabled: Boolean) {}
    override fun setSafeBrowsingEnabled(enabled: Boolean) {}
    override fun setForceDark(forceDark: Int) {}
    override fun setAlgorithmicDarkeningAllowed(allow: Boolean) {}
    override fun setNeedInitialFocus(flag: Boolean) {}
    override fun setRenderPriority(priority: RenderPriority?) {}
    override fun setPluginState(state: PluginState?) {}
    override fun setAppCacheEnabled(flag: Boolean) {}
    override fun setAppCachePath(appCachePath: String?) {}
    override fun setDisabledActionModeMenuItems(menuItems: Int) {}
    override fun setEnableSmoothTransition(enable: Boolean) {}
    override fun setLoadsImagesAutomaticallyCompat(flag: Boolean) = setLoadsImagesAutomatically(flag)
}
