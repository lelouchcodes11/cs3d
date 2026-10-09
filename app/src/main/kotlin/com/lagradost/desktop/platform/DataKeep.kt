package com.lagradost.desktop.platform

import android.util.Log
import com.lagradost.desktop.AppInfo
import com.lagradost.desktop.runtime.AndroidRuntime
import com.lagradost.desktop.runtime.SharedPreferencesImpl
import java.io.File
import java.util.Locale

/**
 * A second copy of the settings (the preference files: repositories, the list of extensions, accounts, watch history, bookmarks, the look)
 * outside the data folder, in `%LOCALAPPDATA%\CloudStream-keep`, so that an update never starts from nothing.
 *
 * Why: the data folder of a portable copy sits next to the program (unpacking a new version into a new folder starts an empty one), and an
 * installer that replaces the install folder can take a data folder inside it along. When the app starts with a data folder that has no settings at
 * all and the copy was made by another version, the copy is put back; the extension files themselves are downloaded again from the addresses the
 * list holds (`PluginManager.downloadMissingPluginFiles`). The same version never restores: a data folder that was deleted on purpose stays empty.
 * Runs with an explicit `-Dcloudstream.data` (development, tests) neither read nor write the copy.
 */
object DataKeep {
    private const val TAG = "DataKeep"
    private const val MARKER = "rebuild_preference.xml"
    private const val MAIN_PREFS = "com.lagradost.cloudstream3_preferences.xml"

    private val explicit: Boolean get() = System.getProperty("cloudstream.data") != null

    /** `-Dcloudstream.keep=<folder>` names another place (the tests) */
    private val folder: File?
        get() {
            System.getProperty("cloudstream.keep")?.let { return File(it) }
            val base = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }?.let { File(it) }
                ?: File(System.getProperty("user.home"), if (System.getProperty("os.name").lowercase(Locale.ROOT).contains("win")) "AppData/Local" else ".local/share")
            return File(base, "CloudStream-keep")
        }

    private fun versionOf(keep: File): String? = runCatching { File(keep, "version.txt").readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }

    /** Called before anything reads the settings */
    fun restoreIfFresh(dataDir: File) {
        runCatching {
            if (explicit) return
            val prefs = File(dataDir, "shared_prefs")
            if (File(prefs, MARKER).exists() || File(prefs, MAIN_PREFS).exists()) return
            val keep = folder ?: return
            val saved = File(keep, "shared_prefs").listFiles { f -> f.isFile && f.name.endsWith(".xml") } ?: return
            if (saved.isEmpty()) return
            val from = versionOf(keep)
            if (from == null || from == AppInfo.version) return
            prefs.mkdirs()
            for (f in saved) f.copyTo(File(prefs, f.name), overwrite = false)
            Log.i(TAG, "a new data folder: ${saved.size} settings files of version $from put back from ${keep.absolutePath}")
        }.onFailure { Log.w(TAG, "restore failed: ${it.message}") }
    }

    /** Called after a snapshot of the settings (start, every 30 minutes) and when the app ends */
    fun mirror() {
        runCatching {
            if (explicit || !AndroidRuntime.isInitialized) return
            val source = File(AndroidRuntime.dataDir, "shared_prefs")
            val files = source.listFiles { f -> f.isFile && f.name.endsWith(".xml") } ?: return
            // nothing worth keeping yet (a first start that has not been set up): the copy of an earlier run stays
            val main = File(source, MARKER)
            if (!main.exists() || runCatching { main.readText().let { "REPOSITORIES_KEY" in it || "PLUGINS_KEY" in it } }.getOrDefault(false).not()) return
            val keep = folder ?: return
            val target = File(keep, "shared_prefs").apply { mkdirs() }
            for (f in files) f.copyTo(File(target, f.name), overwrite = true)
            File(keep, "version.txt").writeText(AppInfo.version)
        }.onFailure { Log.w(TAG, "copy failed: ${it.message}") }
    }

    private var hooked = false

    /** The last state is copied when the app ends (waiting settings are written first) */
    fun start() {
        if (explicit || hooked) return
        hooked = true
        Runtime.getRuntime().addShutdownHook(Thread({
            runCatching { SharedPreferencesImpl.flushAll() }
            mirror()
        }, "data-keep"))
    }
}
