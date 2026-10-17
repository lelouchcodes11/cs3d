package com.lagradost.desktop.platform

import android.util.Log
import com.lagradost.desktop.runtime.AndroidRuntime
import java.io.File

/**
 * Starting the app again while it runs opens a second, independent window (a process of its own, so two videos can play side by side)
 * instead of only bringing the first one to the front.
 *
 * All windows work on the SAME data folder: the library, history, resume positions, accounts, settings and extensions are one set. The
 * saved lists are shared safely by the preferences store (`SharedPreferencesImpl`: a write is merged into what another window saved, and
 * a change made in another window is picked up within a second). Each window has its own IPC port (the first 52525, the others 52527 and
 * up: 52526 is the page the browser returns to after a sign-in, see `OAuthCallback`) and its own log file. A link of the shell (a
 * `cloudstreamapp://` address, an OAuth redirect) always goes to the first window, which also keeps the tray icon and the checks for new
 * episodes, and so does a start while the switch is off: Settings > Appearance > "Open a new window when the app is started again" (a file
 * `single-instance.txt` in the data folder).
 */
object MultiInstance {
    private const val TAG = "MultiInstance"
    private const val FIRST_PORT = 52525
    private const val OTHER_PORT_BASE = 52527

    /** 1 for the first window, 2.. for the others */
    @JvmStatic
    var number: Int = 1
        private set

    private fun portOf(n: Int) = if (n <= 1) FIRST_PORT else OTHER_PORT_BASE + n - 2

    private fun baseDir(): File = System.getProperty("cloudstream.data")?.let { File(it) } ?: AndroidRuntime.defaultDataDir()

    /** The switch (on by default) */
    fun enabled(): Boolean = !File(baseDir(), "single-instance.txt").exists()

    fun setEnabled(on: Boolean) {
        val f = File(baseDir(), "single-instance.txt")
        runCatching { if (on) f.delete() else { f.parentFile?.mkdirs(); f.writeText("A second start of the app only brings the first window to the front. Delete this file to open new windows.") } }
    }

    private fun isDeepLink(args: Array<String>) = args.any { it.contains("://") || it.startsWith("cloudstream", ignoreCase = true) }

    /**
     * Called by the start-up when a first window answers on the IPC port. True: this process is another window now (its IPC port system
     * property is set, the data folder is the same) and must go on starting; false: the arguments belong to the first window.
     */
    fun becomeSecondWindow(args: Array<String>): Boolean {
        if (isDeepLink(args) || !enabled()) return false
        return runCatching {
            // the lowest number whose window is not running
            var n = 2
            while (n < 12 && SingleInstanceIpc.isRunning(portOf(n))) n++
            if (n >= 12) return false
            System.setProperty("cloudstream.ipcport", portOf(n).toString())
            number = n
            Log.i(TAG, "window $n on the shared data folder, IPC port ${portOf(n)}")
            true
        }.getOrElse { Log.w(TAG, "could not start another window: $it"); false }
    }
}
