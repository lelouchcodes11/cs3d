package com.lagradost.desktop

import android.util.Log
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.desktop.runtime.AndroidRuntime
import com.lagradost.desktop.runtime.web.JcefRuntime

/**
 * Work that the window's own thread used to do the first time a page needed it, done at the start on a thread of its own instead (measured in a
 * start-up of the packaged app with a real set of extensions: freezes of 0.6 s (resource files), 0.5-2.7 s (Jackson's first use) and 2-5 s
 * (Chromium starting) with the window on screen).
 */
object Warmups {
    private const val TAG = "Warmups"
    private const val KEY_JCEF_USED = "jcef_used_last_run"

    /** Called once the application object exists */
    fun start() {
        JcefRuntime.onStarted = ::noteJcefUsed
        // Chromium (WebView) starts when an extension first needs a page or cookie (a site behind Cloudflare, for example), and the window pays for it
        // (its message pump runs on the window's thread). Starting it before the window shows when the last run needed it was tried: measured worse
        // than the lazy start (frames over 50 ms: 66 against 32 in the first 25 s), so it is off; -Dcloudstream.jcefearly=true brings it back.
        runCatching {
            val prefs = PreferenceManager.getDefaultSharedPreferences(AndroidRuntime.context)
            val usedLastRun = prefs.getBoolean(KEY_JCEF_USED, false)
            // JcefRuntime sets it again when it starts in this run
            prefs.edit().putBoolean(KEY_JCEF_USED, false).apply()
            if (usedLastRun && System.getProperty("cloudstream.jcefearly") == "true" && !com.lagradost.desktop.runtime.web.WebRuntime.usesWebView2) {
                Log.i(TAG, "Chromium was needed in the last run: starting it now")
                JcefRuntime.startAsync()
            }
        }
        Thread({ runCatching { work() } }, "warm-up-start").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1; start() }
    }

    /** Remembers that Chromium was needed in this run (called by JcefRuntime when it starts) */
    fun noteJcefUsed() {
        runCatching { PreferenceManager.getDefaultSharedPreferences(AndroidRuntime.context).edit().putBoolean(KEY_JCEF_USED, true).apply() }
    }

    private inline fun timed(what: String, block: () -> Unit) {
        val t = System.currentTimeMillis()
        runCatching { block() }.onFailure { Log.w(TAG, "$what: ${it.message}") }
        Log.i(TAG, "$what warmed in ${System.currentTimeMillis() - t} ms")
    }

    private fun work() {
        // the bundled Chromium of older versions left about 560 MB in the data folder (its files and its profile); with WebView2 they are unused
        if (com.lagradost.desktop.runtime.web.WebRuntime.usesWebView2) Thread({
            Thread.sleep(20_000)
            for (name in listOf("jcef", "webview")) {
                val dir = java.io.File(AndroidRuntime.dataDir, name)
                if (dir.isDirectory) {
                    val ok = dir.deleteRecursively()
                    Log.i(TAG, "old Chromium folder $name removed: $ok")
                }
            }
        }, "old-chromium-cleanup").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; start() }
        // the GPU player's video window class gets its black background before the first video (else that opens with a white flash)
        Thread({ runCatching { Thread.sleep(2_500); com.lagradost.desktop.ui.screens.player.NativeVideo.warmUp() } }, "video-window-warm-up").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; start() }
        // the XML files of the app's strings (a page's first string waited for them)
        timed("resources") { DesktopBootstrap.resourceIndex().warm(AndroidRuntime.context.resources.configuration) }
        // the saved lists the first pages read: parsing their JSON the first time includes setting up Jackson for the classes
        timed("saved data") {
            val reads: List<() -> Any?> = listOf(
                { DataStoreHelper.searchPreferenceTags },
                { DataStoreHelper.getAllResumeStateIds()?.take(8)?.forEach { DataStoreHelper.getLastWatched(it) } },
                { DataStoreHelper.getAllWatchStateIds() },
                { DataStoreHelper.getAllBookmarkedData() },
                { DataStoreHelper.getAllSubscriptions() },
                { DataStoreHelper.getAllFavorites() },
                { DataStoreHelper.getCurrentAccount() },
            )
            for (read in reads) runCatching { read() }
        }
    }
}
