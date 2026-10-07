package com.lagradost.desktop.runtime.web.wv2

import android.util.Log
import com.lagradost.desktop.runtime.AndroidRuntime
import com.lagradost.desktop.runtime.web.NativeFiles
import com.lagradost.desktop.runtime.web.WebCookie
import com.lagradost.desktop.runtime.web.WebCookies
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Microsoft Edge WebView2, the browser engine that comes with Windows 10/11, for every WebView of the app (extension WebViews,
 * WebViewResolver, Cloudflare checks). Nothing is bundled but the 200 KB loader, and its processes (msedgewebview2.exe) run only while a
 * page is in use: when no browser has been alive for [IDLE_SHUTDOWN_MS] the environment is released and Windows ends them.
 *
 * WebView2 is single-threaded (an STA): every call happens on the thread "webview2" here, which runs a Win32 message loop; [post] queues work
 * for it. The app's UI thread never waits for this thread (it only posts), so a busy page cannot freeze the window.
 */
object WebView2Runtime {
    private const val TAG = "WebView2"
    private const val WM_RUN = 0x8000 + 0x31
    private const val IDLE_SHUTDOWN_MS = 30_000L
    /** A WebView an extension made and forgot (not on screen, nothing asked of it) is closed after this long; it reopens its page when used again */
    const val PARK_AFTER_MS = 2 * 60_000L

    /** The same for a WebView that was on screen (a dialog) and is not any more */
    const val PARK_AFTER_SHOWN_MS = 20_000L

    private val IID_ENV_DONE = Com.iid("4e8a3389-c9d8-4bd2-b6b5-124fee6cc14d")
    private val IID_ENV2 = Com.iid("41f3632b-5ef4-404f-ad82-2d606c5a9a21")
    private val IID_GET_COOKIES_DONE = Com.iid("5a4f5069-5c15-47c3-8646-f4de1c116670")

    private interface Loader : Library {
        fun CreateCoreWebView2EnvironmentWithOptions(browserFolder: WString?, userDataFolder: WString?, options: Pointer?, handler: Pointer?): Int
        fun GetAvailableCoreWebView2BrowserVersionString(browserFolder: WString?, versionInfo: PointerByReference): Int
    }

    private val loader: Loader? by lazy {
        runCatching {
            val dll = NativeFiles.find("WebView2Loader.dll") ?: return@runCatching null
            Native.load(dll.absolutePath, Loader::class.java)
        }.onFailure { Log.w(TAG, "loader not loaded: ${it.message}") }.getOrNull()
    }

    /** The installed WebView2 runtime's version ("154.0.4258.53"), null when there is none (or WebView2 is switched off) */
    val version: String? by lazy {
        if (System.getProperty("cloudstream.web") == "jcef") return@lazy null
        val l = loader ?: return@lazy null
        runCatching {
            val ref = PointerByReference()
            if (l.GetAvailableCoreWebView2BrowserVersionString(null, ref) < 0) return@runCatching null
            val p = ref.value ?: return@runCatching null
            try {
                p.getWideString(0)
            } finally {
                Ole32.INSTANCE.CoTaskMemFree(p)
            }
        }.getOrNull().also { Log.i(TAG, "WebView2 runtime: ${it ?: "not available"}") }
    }

    val isAvailable: Boolean get() = version != null

    /** The user agent of WebView2's own (Edge) browser: what a cf_clearance cookie made by it is bound to */
    val userAgent: String
        get() {
            val major = version?.substringBefore('.') ?: "154"
            return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Safari/537.36 Edg/$major.0.0.0"
        }

    // ------------------------------------------------------------------ the WebView2 thread

    private val queue = ConcurrentLinkedQueue<() -> Unit>()
    @Volatile private var thread: Thread? = null
    private var parking: WinDef.HWND? = null
    private val started = CountDownLatch(1)
    private val hosts = ConcurrentHashMap<Pointer, Wv2Browser>()
    private val timer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "webview2-timer").also { it.isDaemon = true } }

    private val windowProc = object : WinUser.WindowProc {
        override fun callback(hwnd: WinDef.HWND?, uMsg: Int, wParam: WinDef.WPARAM?, lParam: WinDef.LPARAM?): WinDef.LRESULT {
            when (uMsg) {
                WM_RUN -> {
                    drain()
                    return WinDef.LRESULT(0)
                }
                WinUser.WM_SIZE -> hwnd?.pointer?.let { hosts[it] }?.let { b -> runCatching { b.onHostResized() } }
            }
            return User32.INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam)
        }
    }

    private fun drain() {
        while (true) {
            val task = queue.poll() ?: break
            try {
                task()
            } catch (t: Throwable) {
                Log.e(TAG, "task failed: ${Log.getStackTraceString(t)}")
            }
        }
    }

    private fun ensureThread() {
        if (thread != null) return
        synchronized(this) {
            if (thread != null) return
            thread = Thread({ loop() }, "webview2").apply { isDaemon = true; start() }
        }
        startTimers()
    }

    private fun loop() {
        Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED)
        val instance = Kernel32.INSTANCE.GetModuleHandle(null)
        val cls = WinUser.WNDCLASSEX().apply {
            hInstance = instance
            lpfnWndProc = windowProc
            lpszClassName = "CloudStreamWebView2"
        }
        User32.INSTANCE.RegisterClassEx(cls)
        // the hidden pages' windows live in this one: shown (pages that think they are hidden stop their timers, a Cloudflare check never ends),
        // but far outside every screen, not in the task bar or Alt+Tab, never activated
        parking = User32.INSTANCE.CreateWindowEx(
            0x80 or 0x08000000, "CloudStreamWebView2", "CloudStream web", 0x80000000.toInt() or 0x02000000,
            -32000, -32000, 1280, 720, null, null, instance, null,
        )
        User32.INSTANCE.ShowWindow(parking, 4)
        started.countDown()
        val msg = WinUser.MSG()
        while (User32.INSTANCE.GetMessage(msg, null, 0, 0) > 0) {
            User32.INSTANCE.TranslateMessage(msg)
            User32.INSTANCE.DispatchMessage(msg)
        }
    }

    /** Runs [block] on the WebView2 thread */
    fun post(block: () -> Unit) {
        ensureThread()
        queue.add(block)
        started.await(10, TimeUnit.SECONDS)
        User32.INSTANCE.PostMessage(parking, WM_RUN, null, null)
    }

    val isWebViewThread: Boolean get() = Thread.currentThread() === thread

    /** A window for one browser (WebView2 thread): a child of the hidden parking window until a WebView is shown on screen */
    internal fun createHost(browser: Wv2Browser, width: Int, height: Int): WinDef.HWND {
        val hwnd = User32.INSTANCE.CreateWindowEx(
            0, "CloudStreamWebView2", null, 0x40000000 or 0x10000000 or 0x02000000,
            0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1), parking, null, Kernel32.INSTANCE.GetModuleHandle(null), null,
        )
        hosts[hwnd.pointer] = browser
        return hwnd
    }

    internal fun destroyHost(hwnd: WinDef.HWND) {
        hosts.remove(hwnd.pointer)
        User32.INSTANCE.DestroyWindow(hwnd)
    }

    /** Moves a browser's window back into the hidden parking window (any thread: the WebView left the screen) */
    internal fun park(hwnd: WinDef.HWND) {
        parking?.let { User32.INSTANCE.SetParent(hwnd, it) }
    }

    // ------------------------------------------------------------------ the environment (browser processes)

    private var env: Pointer? = null
    private var env2: Pointer? = null
    private var creatingEnv = false
    private val envWaiters = ArrayList<(Pointer?) -> Unit>()
    private val users = ConcurrentHashMap.newKeySet<Wv2Browser>()
    private val browsers = ConcurrentHashMap.newKeySet<Wv2Browser>()
    @Volatile private var lastUserLeft = 0L

    /** Calls [block] with the environment (started when needed) on the WebView2 thread; null when WebView2 failed to start */
    internal fun withEnvironment(block: (Pointer?) -> Unit) {
        env?.let { block(it); return }
        envWaiters.add(block)
        if (creatingEnv) return
        creatingEnv = true
        val l = loader
        if (l == null) {
            finishEnv(null)
            return
        }
        // Background pages must run at full speed (Cloudflare checks use timers); no Edge extras the app does not use
        Kernel32.INSTANCE.SetEnvironmentVariable(
            "WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS",
            listOf(
                "--disable-background-timer-throttling",
                "--disable-renderer-backgrounding",
                "--disable-backgrounding-occluded-windows",
                "--disable-features=CalculateNativeWinOcclusion,msSmartScreenProtection,msEdgeShoppingAssistant,msWebOOUI,msPdfOOUI,HardwareMediaKeyHandling,MediaSessionService",
                "--autoplay-policy=no-user-gesture-required",
                "--disable-blink-features=AutomationControlled",
                "--renderer-process-limit=4",
                "--no-first-run",
                "--disable-component-update",
            ).joinToString(" "),
        )
        val dataDir = File(AndroidRuntime.dataDir, "webview2").also { it.mkdirs() }
        val started = System.currentTimeMillis()
        val handler = ComObject.completed(IID_ENV_DONE) { hr, e ->
            if (Com.ok(hr) && e != null) {
                Log.i(TAG, "WebView2 $version started in ${System.currentTimeMillis() - started} ms")
                finishEnv(Com.addRef(e))
            } else {
                Log.e(TAG, "WebView2 did not start: HRESULT 0x${Integer.toHexString(hr)}")
                finishEnv(null)
            }
        }
        val hr = l.CreateCoreWebView2EnvironmentWithOptions(null, WString(dataDir.absolutePath), null, handler.pointer)
        handler.releaseOwn()
        if (hr < 0) {
            Log.e(TAG, "WebView2 did not start: HRESULT 0x${Integer.toHexString(hr)}")
            finishEnv(null)
        }
    }

    private fun finishEnv(e: Pointer?) {
        creatingEnv = false
        env = e
        env2 = e?.let { Com.query(it, IID_ENV2) }
        val waiting = envWaiters.toList()
        envWaiters.clear()
        waiting.forEach { runCatching { it(e) } }
    }

    internal val environment2: Pointer? get() = env2
    internal val environment: Pointer? get() = env

    /** The pages and the environment, for the dev server */
    fun describe(): String {
        val now = System.currentTimeMillis()
        val head = "environment=${if (env != null) "running" else "stopped"} users=${users.size} idleFor=${if (users.isEmpty() && lastUserLeft > 0) (now - lastUserLeft) / 1000 else 0}s"
        return (listOf(head) + browsers.map { it.describe(now) }).joinToString(System.lineSeparator())
    }

    internal fun register(b: Wv2Browser) {
        browsers.add(b)
    }

    internal fun unregister(b: Wv2Browser) {
        browsers.remove(b)
    }

    /** [b] holds the environment (it has or is making a page) */
    internal fun acquire(b: Wv2Browser) {
        users.add(b)
    }

    internal fun release(b: Wv2Browser) {
        if (users.remove(b) && users.isEmpty()) lastUserLeft = System.currentTimeMillis()
    }

    /** No page for [IDLE_SHUTDOWN_MS]: the environment is released, which ends every WebView2 process of the app */
    private fun shutdownIfIdle() {
        if (env == null || creatingEnv || users.isNotEmpty()) return
        if (System.currentTimeMillis() - lastUserLeft < IDLE_SHUTDOWN_MS) return
        Log.i(TAG, "no page in use: WebView2 stopped")
        cores.clear()
        cookiesGiven = false
        if (WebCookies.backend === cookieBackend) WebCookies.backend = null
        Com.release(env2)
        Com.release(env)
        env2 = null
        env = null
    }

    /** The browser process ended (crash or update): every page starts again on its next use */
    internal fun onBrowserProcessExited() {
        Log.w(TAG, "WebView2 browser process exited")
        for (b in browsers.toList()) runCatching { b.dropAfterCrash() }
        cores.clear()
        cookiesGiven = false
        if (WebCookies.backend === cookieBackend) WebCookies.backend = null
        Com.release(env2)
        Com.release(env)
        env2 = null
        env = null
    }

    private fun startTimers() {
        timer.scheduleWithFixedDelay({ post { shutdownIfIdle() } }, 10, 10, TimeUnit.SECONDS)
        // a page sets cookies without a new navigation (a Cloudflare check's answer): they reach the jar within a second and a half
        timer.scheduleWithFixedDelay({ if (browsers.any { it.isReady }) post { pullCookies {} } }, 1500, 1500, TimeUnit.MILLISECONDS)
        timer.scheduleWithFixedDelay({
            post {
                val now = System.currentTimeMillis()
                for (b in browsers.toList()) runCatching { b.parkIfForgotten(now) }
            }
        }, 10, 10, TimeUnit.SECONDS)
    }

    // ------------------------------------------------------------------ cookies (the app's jar is the one that counts, see WebCookies)

    // a cookie manager belongs to one page and stops working when that page closes: it is taken from a live page each time
    private val cores = LinkedHashSet<Pointer>()
    private var cookiesGiven = false

    /** The cookie manager of a live page (the caller releases it), null when no page is open */
    private fun cookieManager(): Pointer? = cores.firstOrNull()?.let { Com.getPtr(it, 66) }

    private inline fun withCookieManager(block: (Pointer) -> Unit) {
        val cm = cookieManager() ?: return
        try {
            block(cm)
        } finally {
            Com.release(cm)
        }
    }

    /** The last page closed: changes to the jar from now on cannot reach the browser, the next page gets the whole jar again */
    internal fun onCoreRemoved(core2: Pointer?) {
        if (core2 != null) cores.remove(core2)
        if (cores.isEmpty()) cookiesGiven = false
    }

    private val debugCookies = System.getProperty("cloudstream.webdebug") == "true"

    private val cookieBackend = object : WebCookies.BrowserBackend {
        override fun push(cookie: WebCookie) = post { withCookieManager { addCookie(it, cookie) } }
        override fun delete(cookie: WebCookie) = post {
            withCookieManager { Com.call(it, 9, WString(cookie.name), WString(cookie.domain), WString(cookie.path)) }
        }

        override fun deleteAll() = post { withCookieManager { Com.call(it, 10) } }
    }

    /** A page was made: the first one of an environment gives the browser the app's jar */
    internal fun onCoreCreated(core2: Pointer?) {
        if (core2 != null) cores.add(core2)
        if (cookiesGiven) return
        if (core2 == null) {
            Log.w(TAG, "no ICoreWebView2_2: cookies are not shared")
            return
        }
        val cm = Com.getPtr(core2, 66) ?: run {
            Log.w(TAG, "no cookie manager: cookies are not shared")
            return
        }
        cookiesGiven = true
        Com.call(cm, 10)
        for (c in WebCookies.all()) addCookie(cm, c)
        Com.release(cm)
        WebCookies.backend = cookieBackend
        Log.i(TAG, "cookie jar given to WebView2 (${WebCookies.all().size} cookies)")
    }

    private fun addCookie(cm: Pointer, c: WebCookie) {
        val cookie = Com.getPtr(cm, 3, WString(c.name), WString(c.value), WString(c.domain), WString(c.path)) ?: return
        try {
            if (c.expires != null) Com.call(cookie, 9, c.expires / 1000.0)
            Com.putBool(cookie, 11, c.httpOnly)
            Com.putBool(cookie, 15, c.secure)
            Com.call(cm, 6, cookie)
        } finally {
            Com.release(cookie)
        }
    }

    /** Copies the browser's jar into the app's (WebView2 thread), then [then] */
    internal fun pullCookies(then: () -> Unit) {
        val cm = cookieManager() ?: run { then(); return }
        val handler = ComObject.completed(IID_GET_COOKIES_DONE) { hr, list ->
            if (!Com.ok(hr) || list == null) Log.w(TAG, "cookies not read: HRESULT 0x${Integer.toHexString(hr)}")
            if (Com.ok(hr) && list != null) {
                val count = IntByReference()
                Com.call(list, 3, count)
                if (debugCookies) Log.d(TAG, "browser jar: ${count.value} cookies")
                val out = ArrayList<WebCookie>(count.value)
                for (i in 0 until count.value) {
                    val c = Com.getPtr(list, 4, i) ?: continue
                    try {
                        val session = Com.getBool(c, 16)
                        val expires = if (session) null else Com.getDouble(c, 8)?.let { (it * 1000).toLong() }
                        out.add(
                            WebCookie(
                                Com.getString(c, 3) ?: continue, Com.getString(c, 4) ?: "", Com.getString(c, 6) ?: continue,
                                Com.getString(c, 7) ?: "/", expires, Com.getBool(c, 14), Com.getBool(c, 10),
                            )
                        )
                    } finally {
                        Com.release(c)
                    }
                }
                WebCookies.replaceAll(out)
            }
            then()
        }
        val hr = Com.call(cm, 5, WString(""), handler.pointer)
        if (debugCookies) Log.d(TAG, "GetCookies: 0x${Integer.toHexString(hr)}")
        Com.release(cm)
        if (hr < 0) then()
        handler.releaseOwn()
    }
}
