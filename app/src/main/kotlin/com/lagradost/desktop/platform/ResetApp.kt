package com.lagradost.desktop.platform

import android.util.Log
import com.lagradost.desktop.runtime.AndroidRuntime
import com.lagradost.desktop.runtime.SharedPreferencesImpl
import java.io.File

/**
 * "Reset app" (Settings > Updates & backup > Reset). A running app has its files open (preferences waiting to be written, the log, the web
 * view's profile), so the reset is only asked for here and carried out by the next start, before any setting, log, extension or sync is opened:
 * [request] writes a marker into the data folder and restarts the app, [applyPending] (first thing in `main`) does the deleting.
 *
 *  - [Scope.Settings]: the look, player, subtitle, performance and security settings go back to their defaults. Accounts, profiles, library, watch
 *    history and extensions stay. A copy of the preferences is taken first (data folder, `backups`).
 *  - [Scope.Everything]: the app starts like a first install: every profile, sign-in, library entry, watch position, extension, repository, cache,
 *    log and the second copy of the settings that survives updates ([DataKeep]) are deleted. Downloaded videos are in the download folder the user
 *    chose and are not touched.
 *
 * The extension list goes with [Scope.Everything], so a sync extension (Ultima) is not loaded after it and cannot push the empty state to the
 * user's other devices.
 */
object ResetApp {
    private const val TAG = "ResetApp"
    private const val MARKER = "reset.pending"
    private const val SETTINGS_FILE = "com.lagradost.cloudstream3_preferences.xml"

    enum class Scope { Settings, Everything }

    private fun dataDirNow(): File = System.getProperty("cloudstream.data")?.let { File(it) } ?: AndroidRuntime.defaultDataDir()

    /** A folder that is this app's data folder for certain, and not a drive, the user's home or a folder of the system */
    internal fun isSafeToWipe(dir: File): Boolean {
        val d = dir.absoluteFile.normalize()
        if (!d.isDirectory || d.parentFile == null || d.toPath().nameCount < 2) return false
        val home = File(System.getProperty("user.home") ?: return false).absoluteFile.normalize()
        if (d == home || home.startsWith(d)) return false
        return File(d, "shared_prefs").isDirectory || File(d, "files").isDirectory
    }

    /** True while no other window of the app uses the data folder (a reset needs it to itself) */
    fun canRequest(): Boolean = !MultiInstance.otherWindowsRunning()

    /** Asks for the reset and restarts the app; false when it cannot be done now (another window is open, or this is not a data folder) */
    fun request(scope: Scope): Boolean {
        val dir = AndroidRuntime.dataDir
        if (!isSafeToWipe(dir) || !canRequest()) return false
        runCatching { SharedPreferencesImpl.flushAll() }
        if (scope == Scope.Settings) runCatching { PrefsBackup.snapshotNow("before resetting the settings") }
        runCatching { File(dir, MARKER).writeText(scope.name) }.onFailure { Log.w(TAG, "could not write the marker: $it"); return false }
        Log.i(TAG, "reset asked for: $scope")
        AppRestart.request()
        return true
    }

    /** First thing at start-up: carries out a reset that the last run asked for */
    fun applyPending() {
        val dir = runCatching { dataDirNow().absoluteFile }.getOrNull() ?: return
        val marker = File(dir, MARKER)
        if (!marker.isFile) return
        val scope = Scope.entries.firstOrNull { it.name == runCatching { marker.readText().trim() }.getOrDefault("") }
        // one attempt only: a marker that stayed would erase what the user builds up again after the reset
        marker.delete()
        if (scope == null || !isSafeToWipe(dir)) {
            System.err.println("ResetApp: ignored (scope=$scope, folder=$dir)")
            return
        }
        val left = when (scope) {
            Scope.Settings -> resetSettings(dir)
            Scope.Everything -> eraseEverything(dir)
        }
        System.err.println("ResetApp: $scope done" + if (left > 0) ", $left items could not be deleted (in use)" else "")
    }

    private fun resetSettings(dir: File): Int {
        var left = 0
        for (f in listOf(File(dir, "shared_prefs/$SETTINGS_FILE"), File(dir, "window.properties"), File(dir, "single-instance.txt"), File(dir, "files/graphics.txt"), File(dir, "shaders"))) {
            if (f.exists() && !f.deleteRecursively()) left++
        }
        return left
    }

    private fun eraseEverything(dir: File): Int {
        var left = 0
        // the web view's profile may still be shutting down: a few tries
        for (attempt in 1..10) {
            left = 0
            dir.listFiles()?.forEach { if (!it.deleteRecursively()) left++ }
            if (left == 0) break
            Thread.sleep(500)
        }
        DataKeep.forget()
        // the extensions' sandbox has a profile of its own in Windows: nothing of the app is to be left on the PC
        if (com.lagradost.desktop.isolate.AppContainerLauncher.available) com.lagradost.desktop.isolate.AppContainerLauncher.deleteProfile(com.lagradost.desktop.isolate.IsolatedHost.CONTAINER)
        return left
    }
}
