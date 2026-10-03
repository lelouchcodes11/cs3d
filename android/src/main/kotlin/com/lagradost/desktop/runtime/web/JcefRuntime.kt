package com.lagradost.desktop.runtime.web

import android.util.Log
import com.lagradost.desktop.runtime.AndroidRuntime
import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.EnumProgress
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter
import org.cef.CefApp
import org.cef.CefClient
import org.cef.CefSettings
import org.cef.callback.CefCookieVisitor
import org.cef.misc.BoolRef
import org.cef.network.CefCookie
import org.cef.network.CefCookieManager
import java.awt.EventQueue
import java.io.File
import java.net.URI
import java.util.Date
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Owns the Chromium Embedded Framework instance used for every WebView (extension WebViews,
 * WebViewResolver, Cloudflare bypass and the in-app browser).
 */
object JcefRuntime {
    private const val TAG = "JcefRuntime"
    private const val SENTINEL_URL = "https://cloudstream-cookie-sentinel.invalid/"
    const val BRIDGE_ORIGIN = "https://cloudstream-bridge.invalid"

    /** Chrome version shipped by the bundled CEF, used for the default user agent */
    const val CHROME_VERSION = "146.0.7680.179"

    /** Desktop Chrome user agent used by all browsers (and reported by WebSettings) */
    val defaultUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${CHROME_VERSION.substringBefore('.')}.0.0.0 Safari/537.36"

    @Volatile
    private var app: CefApp? = null
    private val initFuture = CompletableFuture<CefApp>()

    @Volatile
    var progressListener: ((state: String, percent: Float) -> Unit)? = null

    @Volatile
    var initError: Throwable? = null
        private set

    val isReady: Boolean get() = app != null

    private var started = false

    /** Start initialisation in the background (idempotent) */
    fun startAsync() {
        synchronized(this) {
            if (started) return
            started = true
        }
        Thread({
            try {
                initFuture.complete(init())
            } catch (t: Throwable) {
                initError = t
                Log.e(TAG, "Failed to initialise JCEF: ${Log.getStackTraceString(t)}")
                initFuture.completeExceptionally(t)
            }
        }, "jcef-init").apply { isDaemon = true }.start()
    }

    /** Blocks until CEF is ready. Must not be called from the CEF UI thread. */
    fun await(timeoutMs: Long = 120_000): CefApp {
        app?.let { return it }
        startAsync()
        return initFuture.get(timeoutMs, TimeUnit.MILLISECONDS)
    }

    fun whenReady(block: (CefApp) -> Unit) {
        startAsync()
        initFuture.thenAccept(block)
    }

    private fun init(): CefApp {
        val installDir = File(AndroidRuntime.dataDir, "jcef")
        val cacheDir = File(AndroidRuntime.dataDir, "webview")
        cacheDir.mkdirs()
        val builder = CefAppBuilder()
        builder.setInstallDir(installDir)
        builder.setProgressHandler { state, percent ->
            val name = when (state) {
                EnumProgress.LOCATING -> "locating"
                EnumProgress.DOWNLOADING -> "downloading"
                EnumProgress.EXTRACTING -> "extracting"
                EnumProgress.INSTALL -> "installing"
                EnumProgress.INITIALIZING -> "initializing"
                EnumProgress.INITIALIZED -> "initialized"
                else -> state.name.lowercase()
            }
            progressListener?.invoke(name, percent)
        }
        val settings = builder.cefSettings
        settings.windowless_rendering_enabled = true
        settings.root_cache_path = cacheDir.absolutePath
        settings.cache_path = File(cacheDir, "Default").absolutePath
        settings.persist_session_cookies = true
        settings.user_agent = defaultUserAgent
        settings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_DISABLE
        settings.locale = java.util.Locale.getDefault().toLanguageTag()
        // Headless and background browsers must run at full speed (Cloudflare challenges rely on timers)
        builder.addJcefArgs(
            "--disable-background-timer-throttling",
            "--disable-renderer-backgrounding",
            "--disable-backgrounding-occluded-windows",
            "--disable-features=CalculateNativeWinOcclusion,HardwareMediaKeyHandling,MediaSessionService",
            "--autoplay-policy=no-user-gesture-required",
            "--disable-blink-features=AutomationControlled",
            "--no-first-run",
            "--no-default-browser-check",
        )
        builder.setAppHandler(object : MavenCefAppHandlerAdapter() {
            override fun stateHasChanged(state: CefApp.CefAppState) {
                if (state == CefApp.CefAppState.TERMINATED) app = null
            }
        })
        val cefApp = builder.build()
        app = cefApp
        Log.i(TAG, "JCEF ${cefApp.version} initialised")
        // Guarantees that cookie visitors are called at least once, see visitCookies
        CefCookieManager.getGlobalManager()?.setCookie(
            SENTINEL_URL, CefCookie("cs_sentinel", "1", "cloudstream-cookie-sentinel.invalid", "/", false, false, Date(), Date(), false, Date())
        )
        return cefApp
    }

    fun createClient(): CefClient = await().createClient()

    fun shutdown() {
        try {
            app?.dispose()
        } catch (_: Throwable) {
        }
        app = null
    }

    // ------------------------------------------------------------------ cookies

    private val cookieSnapshot = AtomicReference<List<CefCookie>>(emptyList())
    private val cookieRefreshPosted = AtomicBoolean(false)
    private val cookieReads = Executors.newSingleThreadExecutor { r ->
        Thread(r, "jcef-cookies").apply { isDaemon = true }
    }

    private fun cookieManager(): CefCookieManager? {
        await()
        return CefCookieManager.getGlobalManager()
    }

    /**
     * All cookies in the store.
     * Plugins poll this from the UI thread (Cloudflare dialogs). An empty jar never calls the
     * visitor, so a blocking wait there freezes the window for the whole timeout. The UI thread
     * gets the last snapshot and a refresh runs beside it.
     */
    fun allCookies(timeoutMs: Long = 5000): List<CefCookie> {
        if (EventQueue.isDispatchThread()) {
            if (cookieRefreshPosted.compareAndSet(false, true)) {
                cookieReads.execute {
                    try {
                        cookieSnapshot.set(readCookies(timeoutMs))
                    } finally {
                        cookieRefreshPosted.set(false)
                    }
                }
            }
            return cookieSnapshot.get()
        }
        val fresh = readCookies(timeoutMs)
        cookieSnapshot.set(fresh)
        return fresh
    }

    private fun readCookies(timeoutMs: Long): List<CefCookie> {
        val cm = cookieManager() ?: return emptyList()
        val result = ArrayList<CefCookie>()
        val latch = CountDownLatch(1)
        val visitor = CefCookieVisitor { cookie: CefCookie, count: Int, total: Int, _: BoolRef ->
            synchronized(result) { result.add(cookie) }
            if (total <= 0 || count + 1 >= total) latch.countDown()
            true
        }
        if (!cm.visitAllCookies(visitor)) return emptyList()
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        return synchronized(result) { result.filter { it.domain != "cloudstream-cookie-sentinel.invalid" } }
    }

    private fun domainMatches(host: String, domain: String): Boolean {
        val d = domain.removePrefix(".").lowercase()
        val h = host.lowercase()
        return h == d || h.endsWith(".$d")
    }

    /** Cookies that would be sent to [url], in "name=value; name2=value2" form (Android CookieManager.getCookie) */
    fun cookieHeader(url: String): String? {
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return null
        }
        val host = uri.host ?: return null
        val path = (uri.rawPath ?: "/").ifEmpty { "/" }
        val secure = uri.scheme.equals("https", true) || uri.scheme.equals("wss", true)
        val now = Date()
        val matching = allCookies().filter { c ->
            domainMatches(host, c.domain ?: "") &&
                    (path.startsWith(c.path ?: "/")) &&
                    (!c.secure || secure) &&
                    (!c.hasExpires || c.expires == null || c.expires.after(now))
        }.sortedByDescending { it.path?.length ?: 0 }
        if (matching.isEmpty()) return null
        return matching.joinToString("; ") { "${it.name}=${it.value}" }
    }

    /** Parse a Set-Cookie style string and store it (Android CookieManager.setCookie) */
    fun setCookie(url: String, value: String): Boolean {
        val cm = cookieManager() ?: return false
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return false
        }
        val parts = value.split(';').map { it.trim() }
        val nv = parts.firstOrNull() ?: return false
        val eq = nv.indexOf('=')
        val name = if (eq >= 0) nv.substring(0, eq).trim() else nv
        val v = if (eq >= 0) nv.substring(eq + 1).trim() else ""
        var domain = ""
        var path = "/"
        var secure = false
        var httpOnly = false
        var expires: Date? = null
        for (attr in parts.drop(1)) {
            val i = attr.indexOf('=')
            val k = (if (i >= 0) attr.substring(0, i) else attr).trim().lowercase()
            val av = if (i >= 0) attr.substring(i + 1).trim() else ""
            when (k) {
                "domain" -> domain = av
                "path" -> path = av.ifEmpty { "/" }
                "secure" -> secure = true
                "httponly" -> httpOnly = true
                "max-age" -> av.toLongOrNull()?.let { expires = Date(System.currentTimeMillis() + it * 1000) }
                "expires" -> if (expires == null) expires = parseHttpDate(av)
            }
        }
        val cookie = CefCookie(name, v, domain, path, secure, httpOnly, Date(), Date(), expires != null, expires ?: Date())
        val target = if (uri.scheme == null) "https://$url" else url
        return cm.setCookie(target, cookie)
    }

    private fun parseHttpDate(s: String): Date? {
        val formats = arrayOf("EEE, dd MMM yyyy HH:mm:ss zzz", "EEE, dd-MMM-yyyy HH:mm:ss zzz", "EEE, dd-MMM-yy HH:mm:ss zzz", "EEE MMM d HH:mm:ss yyyy")
        for (f in formats) {
            try {
                return java.text.SimpleDateFormat(f, java.util.Locale.US).parse(s)
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun deleteCookies(url: String?, name: String?): Boolean {
        val cm = cookieManager() ?: return false
        val ok = cm.deleteCookies(url ?: "", name ?: "")
        if (url.isNullOrEmpty()) {
            cm.setCookie(
                SENTINEL_URL, CefCookie("cs_sentinel", "1", "cloudstream-cookie-sentinel.invalid", "/", false, false, Date(), Date(), false, Date())
            )
        }
        return ok
    }

    fun flushCookies() {
        cookieManager()?.flushStore(null)
    }
}
