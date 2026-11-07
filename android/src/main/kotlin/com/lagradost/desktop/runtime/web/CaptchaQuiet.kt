package com.lagradost.desktop.runtime.web

import com.lagradost.desktop.runtime.AndroidRuntime
import java.io.File
import java.util.Properties

/**
 * A check window ("verify you are human", a captcha) of an extension is put up at most once an hour, also across restarts: opened again within the
 * hour, the app does not ask again (the extension is told the window was closed). Kept per extension in the data folder. Nothing else waits on it:
 * extension updates and the app's own update check go on as usual.
 */
object CaptchaQuiet {
    const val QUIET_MS = 60L * 60 * 1000

    private val file: File get() = File(AndroidRuntime.dataDir, "captcha_shown.properties")
    private var props: Properties? = null

    private fun props(): Properties = props ?: Properties().also { p -> runCatching { if (file.isFile) file.inputStream().use { p.load(it) } }; props = p }

    /** "GoTaku.2137779647.cs3" and a class name of that extension's window both come down to a stable name */
    fun keyOf(name: String): String = name.substringAfterLast('/').substringAfterLast('\\').substringBefore('.').ifBlank { name }

    @Synchronized
    fun recentlyShown(name: String): Boolean {
        val at = props().getProperty(keyOf(name))?.toLongOrNull() ?: return false
        return System.currentTimeMillis() - at < QUIET_MS
    }

    @Synchronized
    fun markShown(name: String) {
        val p = props()
        p.setProperty(keyOf(name), System.currentTimeMillis().toString())
        runCatching { file.outputStream().use { p.store(it, "check windows shown (milliseconds)") } }
    }
}
