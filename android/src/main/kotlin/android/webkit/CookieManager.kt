package android.webkit

import android.os.Handler
import android.os.Looper
import com.lagradost.desktop.runtime.web.WebCookies
import java.util.concurrent.Executors

/** Cookie store shared by every WebView */
abstract class CookieManager {
    companion object {
        private val sInstance: CookieManager by lazy { AppCookieManager() }

        @JvmStatic
        fun getInstance(): CookieManager = sInstance

        @JvmStatic
        fun allowFileSchemeCookies(): Boolean = false

        @JvmStatic
        fun setAcceptFileSchemeCookies(accept: Boolean) {}
    }

    abstract fun setAcceptCookie(accept: Boolean)
    abstract fun acceptCookie(): Boolean
    abstract fun setAcceptThirdPartyCookies(webview: WebView?, accept: Boolean)
    abstract fun acceptThirdPartyCookies(webview: WebView?): Boolean
    abstract fun setCookie(url: String?, value: String?)
    abstract fun setCookie(url: String?, value: String?, callback: ValueCallback<Boolean>?)
    abstract fun getCookie(url: String?): String?
    open fun getCookie(url: String?, privateBrowsing: Boolean): String? = getCookie(url)

    @Deprecated("")
    abstract fun removeSessionCookie()
    abstract fun removeSessionCookies(callback: ValueCallback<Boolean>?)

    @Deprecated("")
    abstract fun removeAllCookie()
    abstract fun removeAllCookies(callback: ValueCallback<Boolean>?)
    abstract fun hasCookies(): Boolean
    open fun hasCookies(privateBrowsing: Boolean): Boolean = hasCookies()

    @Deprecated("")
    abstract fun removeExpiredCookie()
    abstract fun flush()
}

/** The app's cookie jar (WebCookies): reading it never starts a browser; the running engine mirrors it */
internal class AppCookieManager : CookieManager() {
    @Volatile
    private var accept = true
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "cookie-manager").also { it.isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())

    private fun <T> callback(cb: ValueCallback<T>?, value: T) {
        if (cb != null) main.post { cb.onReceiveValue(value) }
    }

    override fun setAcceptCookie(accept: Boolean) {
        this.accept = accept
    }

    override fun acceptCookie(): Boolean = accept
    override fun setAcceptThirdPartyCookies(webview: WebView?, accept: Boolean) {}
    override fun acceptThirdPartyCookies(webview: WebView?): Boolean = true

    override fun setCookie(url: String?, value: String?) {
        if (url == null || value == null) return
        WebCookies.set(url, value)
    }

    override fun setCookie(url: String?, value: String?, callback: ValueCallback<Boolean>?) {
        if (url == null || value == null) {
            callback(callback, false)
            return
        }
        worker.execute { callback(callback, WebCookies.set(url, value)) }
    }

    override fun getCookie(url: String?): String? {
        if (url == null) return null
        return try {
            WebCookies.header(url)
        } catch (t: Throwable) {
            null
        }
    }

    @Deprecated("")
    override fun removeSessionCookie() {
        WebCookies.removeSessionCookies()
    }

    override fun removeSessionCookies(callback: ValueCallback<Boolean>?) {
        worker.execute { callback(callback, WebCookies.removeSessionCookies()) }
    }

    @Deprecated("")
    override fun removeAllCookie() {
        WebCookies.removeAll()
    }

    override fun removeAllCookies(callback: ValueCallback<Boolean>?) {
        worker.execute { callback(callback, WebCookies.removeAll()) }
    }

    override fun hasCookies(): Boolean = !WebCookies.isEmpty()

    @Deprecated("")
    override fun removeExpiredCookie() {}
    override fun flush() {}
}

open class WebStorage private constructor() {
    open class Origin(private val origin: String?, private val quota: Long, private val usage: Long) {
        fun getOrigin(): String? = origin
        fun getQuota(): Long = quota
        fun getUsage(): Long = usage
    }

    companion object {
        private val instance = WebStorage()

        @JvmStatic
        fun getInstance(): WebStorage = instance
    }

    /** Clears local/session storage, IndexedDB and caches of every origin known to live WebViews */
    open fun deleteAllData() {
        WebView.forEachLive { it.engine.clearStorage(null) }
    }

    open fun deleteOrigin(origin: String?) {
        WebView.forEachLive { it.engine.clearStorage(origin) }
    }

    open fun getOrigins(callback: ValueCallback<Map<*, *>>?) {
        callback?.onReceiveValue(emptyMap<String, Origin>())
    }

    open fun getUsageForOrigin(origin: String?, callback: ValueCallback<Long>?) {
        callback?.onReceiveValue(0L)
    }

    open fun getQuotaForOrigin(origin: String?, callback: ValueCallback<Long>?) {
        callback?.onReceiveValue(0L)
    }
}

open class WebViewDatabase private constructor() {
    companion object {
        private val instance = WebViewDatabase()

        @JvmStatic
        fun getInstance(context: android.content.Context?): WebViewDatabase = instance
    }

    open fun hasUsernamePassword(): Boolean = false
    open fun clearUsernamePassword() {}
    open fun hasHttpAuthUsernamePassword(): Boolean = false
    open fun clearHttpAuthUsernamePassword() {}
    open fun hasFormData(): Boolean = false
    open fun clearFormData() {}
}

@Deprecated("")
open class CookieSyncManager private constructor() {
    companion object {
        private val instance = CookieSyncManager()

        @JvmStatic
        fun createInstance(context: android.content.Context?): CookieSyncManager = instance

        @JvmStatic
        fun getInstance(): CookieSyncManager = instance
    }

    open fun sync() = CookieManager.getInstance().flush()
    open fun startSync() {}
    open fun stopSync() {}
    open fun resetSync() {}
}
