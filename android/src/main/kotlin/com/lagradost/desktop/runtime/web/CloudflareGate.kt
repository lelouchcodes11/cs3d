package com.lagradost.desktop.runtime.web

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.lagradost.desktop.runtime.AndroidRuntime

/**
 * Settings > General > Cloudflare solvers. Off: no page is opened to pass a Cloudflare check. The extension's request just fails, as it
 * would on a site that cannot be passed, and nothing flashes up on the screen. Read on every use (a switch in Settings applies at once).
 */
object CloudflareGate {
    private const val TAG = "CloudflareGate"
    const val KEY = "cloudflare_solvers_key"

    val solversOn: Boolean
        get() {
            val context = AndroidRuntime.applicationContext ?: return true
            return runCatching { PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY, true) }.getOrDefault(true)
        }

    fun hasWebView(v: View?): Boolean {
        if (v is WebView) return true
        if (v is ViewGroup) for (i in 0 until v.getChildCount()) if (hasWebView(v.getChildAt(i))) return true
        return false
    }

    fun findCancel(v: View?): View? {
        if (v is TextView && v.getText().toString().trim().equals("cancel", ignoreCase = true)) return v
        if (v is ViewGroup) for (i in 0 until v.getChildCount()) findCancel(v.getChildAt(i))?.let { return it }
        return null
    }

    /** A solve in progress that the user can stop: a hidden browser loading a page to get the clearance cookie of [host] */
    class Solve(val host: String, internal val cancel: () -> Unit) {
        val startedAt = System.currentTimeMillis()
    }

    /** The solves in progress (the bar with the Cancel button follows this list) */
    val solves = androidx.compose.runtime.mutableStateListOf<Solve>()
    private val cancelledHosts = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private const val CANCELLED_MS = 2 * 60_000L

    /** A check that was tried and did not end in a clearance (a page that needs a click, a site that blocks this PC): not tried again for a while, so the next title is not held up by it again */
    private val failedHosts = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private const val FAILED_MS = 10 * 60_000L

    /** How long a check that nobody sees may take: a clearance that comes by itself comes in a few seconds; a page that waits for a click is not waited for (the extension goes on without that site) */
    const val UNATTENDED_MS = 25_000L

    fun markFailed(host: String) {
        failedHosts[host] = System.currentTimeMillis()
        Log.i(TAG, "no clearance came for $host: it is left alone for ${FAILED_MS / 60_000} minutes")
    }

    private fun recentlyFailed(host: String): Boolean = failedHosts[host]?.let { System.currentTimeMillis() - it < FAILED_MS } == true

    fun beginSolve(host: String, cancel: () -> Unit): Solve = Solve(host, cancel).also { solves.add(it) }

    fun endSolve(solve: Solve) {
        solves.remove(solve)
    }

    /** The user pressed Cancel: every solve in progress is stopped, and those sites are left alone for two minutes (their next request is not held up again) */
    fun cancelSolves() {
        val now = System.currentTimeMillis()
        for (s in solves.toList()) {
            cancelledHosts[s.host] = now
            Log.i(TAG, "the solve for ${s.host} was cancelled by the user")
            runCatching { s.cancel() }
        }
    }

    // ---- only the extension in use: a plugin that checks its site while it loads (AnimePahe does, at every start of the app) must not put a
    // Cloudflare window on the screen of someone who is on another extension. The app tells which extensions are in use and which plugin / site belongs to whom.

    /** Set by the app: the names of the extensions the user is working with now (the one on Home, the title pages that are open); null: all of them (a search in all extensions) */
    @Volatile var activeApis: () -> Set<String>? = { null }

    /** Set by the app: the same as [activeApis] for a window, which is never put up for a search in all extensions (that is null above: every extension) */
    @Volatile var activeApisForWindows: () -> Set<String> = { emptySet() }

    /** Set by the app: the names of the extensions of the plugin whose classes [loader] loaded */
    @Volatile var apisOfLoader: (ClassLoader?) -> Set<String> = { emptySet() }

    /** Set by the app: the names of the extensions whose site is [host] */
    @Volatile var apisOfHost: (String) -> Set<String> = { emptySet() }

    /** Set by the app: true while the user is on the Extensions or Settings page (a plugin's own settings window is wanted there) */
    @Volatile var managingExtensions: () -> Boolean = { false }

    /** The first class of a plugin on the call stack: who asked for a window (null: the app itself did) */
    fun callingPluginClass(): Class<*>? = runCatching {
        StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk { s ->
            s.map { it.declaringClass }.filter { runCatching { apisOfLoader(it.classLoader).isNotEmpty() }.getOrDefault(false) }.findFirst().orElse(null)
        }
    }.getOrNull()

    /**
     * An extension's own window that nobody asked for (a donation or news popup it opens when it loads, or when it is first used): shown only when that
     * extension is the one in use, or when the user is on the Extensions or Settings page. True when the window must not be shown.
     */
    fun refuseIdlePopup(owner: Class<*>?): Boolean {
        if (owner == null) return false
        val apis = runCatching { apisOfLoader(owner.classLoader) }.getOrDefault(emptySet())
        Log.d(TAG, "a window of ${owner.name} (loader ${owner.classLoader?.javaClass?.simpleName}) belongs to ${apis.joinToString()}")
        if (apis.isEmpty()) return false
        if (inUse(apis, owner.name)) return false
        if (runCatching { managingExtensions() }.getOrDefault(false)) return false
        Log.i(TAG, "a window of ${owner.name} is not shown: ${apis.joinToString()} is not the extension in use")
        return true
    }

    private var lastNotInUseLog = 0L
    private var lastNoOwnerLog = 0L

    private fun inUse(apis: Set<String>, what: String, window: Boolean = false): Boolean {
        // not an extension's own request (a video host, an extractor, a class of the app): nothing to judge it by
        if (apis.isEmpty()) {
            val now = System.currentTimeMillis()
            if (now - lastNoOwnerLog > 30_000) { lastNoOwnerLog = now; Log.i(TAG, "the Cloudflare check of $what belongs to no extension: shown") }
            return true
        }
        // null = every extension (a search in all of them). A failed lookup (the app is still starting) is "none": a window nobody asked for is worse than a failed request
        val active = try { if (window) activeApisForWindows() else activeApis() } catch (e: Throwable) { Log.w(TAG, "which extensions are in use is not known yet: $e"); emptySet() } ?: return true
        if (apis.any { it in active }) return true
        val now = System.currentTimeMillis()
        if (now - lastNotInUseLog > 10_000) { lastNotInUseLog = now; Log.i(TAG, "the Cloudflare check of $what is not shown: ${apis.joinToString()} is not the extension in use (${active.joinToString()})") }
        return false
    }

    /** [inUse] without a log line: for polling */
    private fun inUseQuiet(owner: Class<*>): Boolean {
        val apis = runCatching { apisOfLoader(owner.classLoader) }.getOrDefault(emptySet())
        if (apis.isEmpty()) return true
        // a window: see activeApisForWindows
        val active = runCatching { activeApisForWindows() }.getOrDefault(emptySet())
        return apis.any { it in active }
    }

    private val held = java.util.concurrent.ConcurrentHashMap.newKeySet<Class<*>>()

    /**
     * A Cloudflare window that an extension asked for before the user was using it (AnimePahe checks its site as it loads, with another extension on Home) is not
     * dropped: dropping it told the extension "dismissed", and the extension then refused to ask again for a while, so the window never opened when the user did
     * switch to that extension. It is held until the extension is in use ([then] gets true, on the main thread) or [maxMs] have passed ([then] gets false).
     * False when a window of that extension is already held (the caller refuses this one as before).
     */
    fun holdUntilInUse(owner: Class<*>, maxMs: Long, then: (Boolean) -> Unit): Boolean {
        if (!held.add(owner)) return false
        val started = System.currentTimeMillis()
        Thread({
            var ok = false
            while (System.currentTimeMillis() - started < maxMs) {
                if (inUseQuiet(owner)) { ok = true; break }
                try { Thread.sleep(400) } catch (_: InterruptedException) { break }
            }
            held.remove(owner)
            android.os.Handler(android.os.Looper.getMainLooper()).post { then(ok) }
        }, "cloudflare-hold").apply { isDaemon = true; start() }
        return true
    }

    /** Whether the extension that owns [host] is one the user is using */
    fun inUseHost(host: String): Boolean = inUse(runCatching { apisOfHost(host) }.getOrDefault(emptySet()), host)

    /** Whether the extension that owns the plugin class [owner] is one the user is using */
    fun inUseOwner(owner: Class<*>?): Boolean = inUse(runCatching { apisOfLoader(owner?.classLoader) }.getOrDefault(emptySet()), owner?.name ?: "a dialog", window = true)

    /** Whether a page may be opened to pass the check of [host]: the solvers are on, the extension is in use and the user did not just cancel that site's */
    fun allowsSolving(host: String): Boolean {
        if (!solversOn) return false
        if (!inUseHost(host)) return false
        if (recentlyFailed(host)) return false
        val at = cancelledHosts[host] ?: return true
        return System.currentTimeMillis() - at > CANCELLED_MS
    }

    /**
     * A page of an extension in the sandbox that asks to be shown in a window (its challenge dialog): the same rules as for one that runs in the app. The solvers are on,
     * one of [apis] (the extension's sources) is in use. (That such windows are not coming over and over is checked when the window is shown, see [refuseBrowserDialog].)
     */
    fun allowsDialogFor(apis: Set<String>, what: String): Boolean = solversOn && inUse(apis, what, window = true)

    /** The Cancel of a page dialog: its own Cancel button is pressed (the extension is told), or the dialog is cancelled */
    fun cancelBrowserDialog(content: View?, cancel: () -> Unit) {
        val button = findCancel(content)
        if (button != null) runCatching { button.performClick() }.onFailure { cancel() } else cancel()
    }

    private val shown = ArrayDeque<Long>()
    private var pausedUntil = 0L
    private const val WINDOW_MS = 120_000L
    private const val MAX_IN_WINDOW = 2
    private const val PAUSE_MS = 20 * 60_000L

    /** Another page in a dialog while a few were just shown and closed (a check that never ends in a cookie): paused for a while, not shown again and again */
    private fun keepsOpening(): Boolean = synchronized(this) {
        val now = System.currentTimeMillis()
        if (now < pausedUntil) return true
        shown.addLast(now)
        while (shown.isNotEmpty() && now - shown.first() > WINDOW_MS) shown.removeFirst()
        if (shown.size > MAX_IN_WINDOW) {
            pausedUntil = now + PAUSE_MS
            shown.clear()
            Log.w(TAG, "pages in dialogs were asked for ${MAX_IN_WINDOW + 1} times within ${WINDOW_MS / 1000} s: none is shown for the next ${PAUSE_MS / 60_000} minutes (Settings > General > Pass Cloudflare checks turns them off for good)")
            runCatching { AndroidRuntime.host.showToast("A site's Cloudflare check keeps coming back, so no more check windows for ${PAUSE_MS / 60_000} minutes. Settings > General > Pass Cloudflare checks turns them off.", true) }
            true
        } else false
    }

    /**
     * An extension's own challenge dialog (a page in a dialog with a Cancel button, like Nepu's) that must not be shown: the Cancel button is pressed
     * for the user, so the extension gets its "not solved" answer at once instead of waiting two minutes behind a window nobody sees. That is when
     * the solvers are off, and when such dialogs keep coming (a check that cannot be passed opens, closes and opens again on every request).
     * True when the dialog was refused (it must not be shown).
     */
    fun refuseBrowserDialog(content: View?, owner: Class<*>? = null, cancel: () -> Unit): Boolean {
        if (!hasWebView(content)) return false
        if (solversOn && inUseOwner(owner) && !keepsOpening()) return false
        Log.i(TAG, "a page in a dialog was asked for and is not shown")
        cancelBrowserDialog(content, cancel)
        return true
    }
}
