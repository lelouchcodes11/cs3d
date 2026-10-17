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

    /** Whether a page may be opened to pass the check of [host]: the solvers are on and the user did not just cancel that site's */
    fun allowsSolving(host: String): Boolean {
        if (!solversOn) return false
        val at = cancelledHosts[host] ?: return true
        return System.currentTimeMillis() - at > CANCELLED_MS
    }

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
    fun refuseBrowserDialog(content: View?, cancel: () -> Unit): Boolean {
        if (!hasWebView(content)) return false
        if (solversOn && !keepsOpening()) return false
        Log.i(TAG, "a page in a dialog was asked for and is not shown")
        cancelBrowserDialog(content, cancel)
        return true
    }
}
