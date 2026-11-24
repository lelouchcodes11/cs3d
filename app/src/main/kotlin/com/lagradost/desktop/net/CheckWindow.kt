package com.lagradost.desktop.net

import android.os.Handler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.isolate.CheckDialog
import com.lagradost.desktop.runtime.web.CloudflareGate
import com.lagradost.desktop.runtime.web.PrivateNetworkGuard
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The window of a Cloudflare check that a hidden page could not pass by itself (a "Verify you are human" box that wants a click).
 *
 * Every solver of the app works in a page nobody sees: the CloudflareKiller of an extension in the app, the same for an extension in the sandbox
 * (IsolatedHost.webview), and the browser that answers the sandbox's refused requests (BrowserFetch). Most checks pass there by themselves within a
 * few seconds. One that is still on after [SHOW_AFTER_MS] got nowhere before: it was given up after 25 s and the site was left alone for 10 minutes,
 * so a site whose check needs a click never worked. Now the same address is opened in a page the user can see, in the check window of the app
 * (CheckDialog, drawn by AndroidDialogHost), when the site is one of the extension in use (CloudflareGate.allowsWindowFor) and the page really shows a
 * check. The page shares the browser's cookies, so the pass the user earns there is the solver's pass; the window closes by itself once the page is
 * through. One window at a time; a second solve of the same site waits for the first one's window.
 */
object CheckWindow {
    private const val TAG = "CheckWindow"

    /** How long a hidden check may take before its site is looked at in a page that can be shown (`-Dcloudstream.cfwindowafter=` in ms for a test) */
    val SHOW_AFTER_MS: Long = System.getProperty("cloudstream.cfwindowafter")?.toLongOrNull() ?: 8_000L

    /** How long a window waits for the user */
    const val ATTENDED_MS = 180_000L

    /** How long the page may take to show a check (or the site) before it is given up */
    private const val LOOK_MS = 15_000L

    private val challengeTitle = Regex("just a moment|attention required|verify you are human|checking your browser|one more step|security check|performing security verification|human verification|ddos-guard|un instant|einen moment|un momento", RegexOption.IGNORE_CASE)

    fun isChallengeTitle(title: String?): Boolean = !title.isNullOrBlank() && challengeTitle.containsMatchIn(title)

    /** The colour of the page area of the window before the page has drawn (and of the page where it draws nothing) */
    fun pageColor(): Int = if (com.lagradost.desktop.runtime.ui.ThemeBridge.isDark) 0xFF1C1C1E.toInt() else 0xFFF2F2F7.toInt()

    /** How long a loaded check page is given to draw before it is shown (the title comes long before the first picture) */
    private const val DRAW_MS = 400L

    private val oneAtATime = Mutex()
    private val openFor = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val main = Handler(Looper.getMainLooper())

    /** True while a check window is up (the Cloudflare chip stays away then: the window says it all) */
    var showing by androidx.compose.runtime.mutableStateOf(false)
        private set

    /** Checks waiting for the window that is up (a search in all extensions can bring several): the window shows how many, and Skip all */
    private val queued = java.util.concurrent.atomic.AtomicInteger()

    /** Skip all: the window that is up and every one waiting end, and none comes up for a minute (the rest of that search goes on hidden) */
    @Volatile private var skipGeneration = 0
    @Volatile private var skippedUntil = 0L
    @Volatile private var current: Page? = null

    fun skipAll() {
        skipGeneration++
        skippedUntil = System.currentTimeMillis() + 60_000
        current?.cancelled = true
        Log.i(TAG, "skip all: the check window and ${queued.get()} waiting are closed")
    }

    /**
     * Runs beside a hidden solve of [url] (whose site is [host]); the caller cancels it as soon as its solve passed. It ends the solve with [stop]
     * when the solve has to end without a pass: at the unattended limit when no window may be shown or the page shows no check, when the user closes
     * the window, and after [ATTENDED_MS]. [onPassed] is told when the page in the window got through (its clearance is in the browser's cookies now).
     */
    suspend fun supervise(url: String, host: String, userAgent: String?, stop: () -> Unit, onPassed: () -> Unit = {}) {
        val started = System.currentTimeMillis()
        suspend fun unattendedEnd() {
            delay((CloudflareGate.UNATTENDED_MS - (System.currentTimeMillis() - started)).coerceAtLeast(0))
            Log.i(TAG, "the check of $host did not pass in ${CloudflareGate.UNATTENDED_MS / 1000} s")
            stop()
        }
        delay(SHOW_AFTER_MS)
        if (!CloudflareGate.allowsWindowFor(host) || System.currentTimeMillis() < skippedUntil) return unattendedEnd()
        // another solve of this site has its window up: that pass is this one's too
        openFor[host]?.let { other ->
            Log.i(TAG, "the check of $host waits for the window that is up for it")
            withTimeoutOrNull(ATTENDED_MS) { other.await() }
            delay(4_000)
            stop()
            return
        }
        val waitedFrom = System.currentTimeMillis()
        val generation = skipGeneration
        queued.incrementAndGet()
        var counted = true
        try { oneAtATime.withLock {
            queued.decrementAndGet(); counted = false
            if (System.currentTimeMillis() - waitedFrom > 500) Log.i(TAG, "the check of $host waited ${(System.currentTimeMillis() - waitedFrom) / 1000} s for another check window")
            if (generation != skipGeneration) { CloudflareGate.cancelledByUser(host); stop(); return }
            if (!CloudflareGate.allowsWindowFor(host)) { stop(); return }
            val done = CompletableDeferred<Unit>()
            openFor[host] = done
            var page: Page? = null
            try {
                // made whatever happens to this call: a check that passed while the page was being made (this call cancelled) left a page nobody closed,
                // running the check page for the rest of the session. Now it is kept in [page] and the finally below closes it.
                page = runCatching { withContext(NonCancellable) { onMain { Page(url, host, userAgent) } } }.onFailure { Log.w(TAG, "no page could be made for the check of $host: $it") }.getOrNull()
                    ?: return unattendedEnd()
                kotlinx.coroutines.yield() // throws when this call was cancelled meanwhile (the finally closes the page)
                run(page, url, host, onPassed)
                stop()
            } finally {
                openFor.remove(host, done)
                done.complete(Unit)
                page?.let { p -> withContext(NonCancellable) { runCatching { onMain { p.close() } } } }
                showing = false
                current = null
            }
        } } finally { if (counted) queued.decrementAndGet() }
    }

    /** The page's life: looked at hidden, shown once it shows a check, closed when it is through, cancelled or out of time */
    private suspend fun run(page: Page, url: String, host: String, onPassed: () -> Unit) {
        val opened = System.currentTimeMillis()
        // 1. hidden: does the site still show a check? It is shown once the check page has loaded and had a moment to draw: shown at its title
        // it was a white box for seconds, and on the owner's PC for a whole minute
        while (true) {
            delay(250)
            val title = page.title
            if (isChallengeTitle(title) && page.finished && System.currentTimeMillis() - page.finishedAt > DRAW_MS) break
            if (page.finished && !title.isNullOrBlank() && System.currentTimeMillis() - page.finishedAt > 1_500) {
                Log.i(TAG, "the site of $host showed no check in a fresh page (\"${title.take(60)}\"): nothing to show")
                onPassed()
                // the solver's own look at the cookies ends it now; if it does not, it ends here
                delay(4_000)
                return
            }
            if (System.currentTimeMillis() - opened > LOOK_MS) {
                Log.i(TAG, if (isChallengeTitle(title)) "the check page of $host did not finish loading within ${LOOK_MS / 1000} s: not shown" else "the page of $host showed no check within ${LOOK_MS / 1000} s")
                return
            }
        }
        // 2. shown until it is through, the user closes it or the time is up
        // the extension the site belongs to, or the site itself (a video host while sources load)
        val name = runCatching { CloudflareGate.apisOfHost(host).firstOrNull() }.getOrNull() ?: host.removePrefix("www.")
        val shown = onMain { page.show(name) }
        if (!shown) { Log.i(TAG, "the check window of $host could not be shown"); return }
        showing = true
        current = page
        Log.i(TAG, "the check of $host is shown to the user (for $name)")
        attention()
        val shownAt = System.currentTimeMillis()
        var blankRecovered = false
        var throughSince = 0L
        val until = shownAt + ATTENDED_MS
        while (System.currentTimeMillis() < until) {
            delay(250)
            page.dialog?.more = queued.get()
            if (page.cancelled) {
                CloudflareGate.cancelledByUser(host)
                return
            }
            if (!blankRecovered && System.currentTimeMillis() - shownAt > 8_000) {
                blankRecovered = true
                if (page.isVisiblyBlank()) {
                    val fallbackUrl = runCatching {
                        val u = java.net.URI(url)
                        val isRoot = (u.rawPath.isNullOrEmpty() || u.rawPath == "/") && u.rawQuery.isNullOrEmpty()
                        if (isRoot) null else "${u.scheme ?: "https"}://$host/"
                    }.getOrNull()
                    Log.i(TAG, "the check page of $host is still blank after 8 s: ${if (fallbackUrl != null) "loading $fallbackUrl" else "reloading"}")
                    runCatching { com.lagradost.desktop.runtime.web.WebCookies.dropCloudflareCookies(host) }
                    onMain {
                        if (fallbackUrl != null) page.view.loadUrl(fallbackUrl) else page.view.reload()
                    }
                    continue
                }
            }
            val title = page.title
            val cleared = page.finished && !isChallengeTitle(title) && runCatching { com.lagradost.desktop.runtime.web.WebCookies.hasClearance(host) }.getOrDefault(false)
            if ((!title.isNullOrBlank() && !isChallengeTitle(title)) || cleared) {
                if (throughSince == 0L) throughSince = System.currentTimeMillis()
                if (System.currentTimeMillis() - throughSince > 800) {
                    Log.i(TAG, "the check of $host was passed in the window")
                    onPassed()
                    // a moment of "Done" before it goes: the user sees that the click worked
                    page.dialog?.passed = true
                    delay(700)
                    onMain { page.hide() }
                    showing = false
                    delay(5_000)
                    return
                }
            } else throughSince = 0L
        }
        Log.i(TAG, "the check window of $host was not answered in ${ATTENDED_MS / 1000} s")
    }

    /** The app is behind other windows or minimised: its taskbar button flashes until it is brought up (a check waits for a click) */
    private fun attention() {
        if (!System.getProperty("os.name").orEmpty().lowercase().contains("win")) return
        java.awt.EventQueue.invokeLater {
            runCatching {
                val w = com.lagradost.desktop.ui.DesktopUiHost.window ?: return@invokeLater
                if (w.isActive) return@invokeLater
                val info = com.sun.jna.platform.win32.WinUser.FLASHWINFO()
                info.cbSize = info.size()
                info.hWnd = com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Pointer(com.sun.jna.Native.getComponentID(w)))
                // FLASHW_ALL | FLASHW_TIMERNOFG: caption and taskbar button, until the window comes to the front
                info.dwFlags = 0x3 or 0xC
                com.sun.jna.platform.win32.User32.INSTANCE.FlashWindowEx(info)
            }.onFailure { Log.w(TAG, "no taskbar flash: $it") }
        }
    }

    private suspend fun <T> onMain(block: () -> T): T =
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else suspendCancellableCoroutine { c ->
            main.post {
                try { c.resume(block()) } catch (t: Throwable) { c.resumeWithException(t) }
            }
        }

    /** The page of one check (made, shown and closed on the main thread; its title is read from anywhere) */
    private class Page(url: String, private val host: String, userAgent: String?) {
        @Volatile var title: String? = null
        @Volatile var finished = false
        @Volatile var finishedAt = 0L
        @Volatile var cancelled = false
        @Volatile var dialog: CheckDialog? = null
            private set
        val view: WebView = WebView(DesktopBootstrap.activity)

        init {
            // (no pass is deleted here: this page opens 8 s into a hidden solve, often just as that solve earned a new pass, and deleting "the pass of
            // the site" here deleted the new one; a pass Cloudflare refused is dropped where it was refused, see CloudflarePass.dropRejected)
            view.getSettings().apply {
                setJavaScriptEnabled(true)
                setDomStorageEnabled(true)
                setAllowFileAccess(false)
                if (userAgent != null) setUserAgentString(userAgent)
            }
            // the check page may not lead to this PC or its network
            view.networkGuard = PrivateNetworkGuard::isAllowed
            // a page that has not drawn yet is the colour of the window around it, not a white box (seen on the owner's PC for a whole minute)
            view.setBackgroundColor(pageColor())
            view.setWebChromeClient(object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView, title: String?) { this@Page.title = title }
            })
            view.setWebViewClient(object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) { finished = false }
                override fun onPageFinished(view: WebView?, url: String?) {
                    title = view?.getTitle() ?: title
                    finished = true
                    finishedAt = System.currentTimeMillis()
                }
            })
            view.loadUrl(url)
        }

        suspend fun isVisiblyBlank(): Boolean {
            val result = CompletableDeferred<String?>()
            runCatching {
                onMain {
                    view.evaluateJavascript(
                        "(function(){var t=(document.body&&document.body.innerText||'').trim().length;var f=document.querySelectorAll('iframe').length;return t+','+f;})()",
                    ) { result.complete(it) }
                }
            }.onFailure { return false }
            val raw = withTimeoutOrNull(2_000) { result.await() }?.trim()?.trim('"') ?: return false
            val parts = raw.split(',')
            val textLen = parts.getOrNull(0)?.toIntOrNull() ?: return false
            val iframes = parts.getOrNull(1)?.toIntOrNull() ?: return false
            return textLen == 0 && iframes == 0
        }

        fun show(extension: String?): Boolean {
            val d = CheckDialog(
                DesktopBootstrap.activity,
                extension,
                url = { runCatching { view.getUrl() }.getOrNull() },
                reload = {
                    main.post {
                        runCatching { com.lagradost.desktop.runtime.web.WebCookies.dropCloudflareCookies(host) }
                        runCatching { view.reload() }
                    }
                },
                ownWindow = true,
            )
            (view.getParent() as? ViewGroup)?.removeView(view)
            d.setContentView(view)
            d.setOnCancelListener { cancelled = true }
            dialog = d
            d.show()
            if (!d.isShowing()) { dialog = null; (view.getParent() as? ViewGroup)?.removeView(view); return false }
            return true
        }

        fun hide() {
            val d = dialog ?: return
            dialog = null
            runCatching { d.setOnCancelListener(null) }
            runCatching { d.dismiss() }
            (view.getParent() as? ViewGroup)?.removeView(view)
        }

        fun close() {
            hide()
            runCatching { view.destroy() }
        }
    }
}
