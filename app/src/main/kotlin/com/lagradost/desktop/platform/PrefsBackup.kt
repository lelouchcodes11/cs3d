package com.lagradost.desktop.platform

import android.util.Log
import com.lagradost.desktop.runtime.AndroidRuntime
import com.lagradost.desktop.runtime.SharedPreferencesImpl
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rolling copies of the app's preference files (`<data>/shared_prefs`: watch history, bookmarks, extension list, settings) in
 * `<data>/backups/prefs-<time>`. Sync extensions (Ultima) mirror a cloud copy onto the local store and delete what the cloud copy
 * lacks, so there is always a recent copy to go back to: taken when the app starts and when the window comes back to the front
 * after 30 minutes; the latest [KEEP] are kept.
 */
object PrefsBackup {
    private const val TAG = "PrefsBackup"
    private const val MIN_GAP_MS = 30 * 60 * 1000L
    private const val KEEP = 12

    @Volatile
    private var last = 0L

    fun maybeBackup(reason: String) {
        val now = System.currentTimeMillis()
        if (now - last < MIN_GAP_MS) return
        last = now
        Thread({ copyNow(reason, now) }, "prefs-backup").apply { isDaemon = true }.start()
    }

    /** Copies right now, on the calling thread, whatever the gap: before something replaces the data (restoring a backup file) */
    fun snapshotNow(reason: String) {
        val now = System.currentTimeMillis()
        last = now
        copyNow(reason, now)
    }

    private fun copyNow(reason: String, now: Long) {
        runCatching {
            // what is still waiting for its delayed write belongs in the copy
            SharedPreferencesImpl.flushAll()
            val data = AndroidRuntime.dataDir
            val source = File(data, "shared_prefs")
            val files = source.listFiles { f -> f.isFile && f.name.endsWith(".xml") } ?: return@runCatching
            if (files.isEmpty()) return@runCatching
            val root = File(data, "backups").apply { mkdirs() }
            val folder = File(root, "prefs-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date(now)))
            folder.mkdirs()
            for (f in files) f.copyTo(File(folder, f.name), overwrite = true)
            // oldest first (the names sort by time)
            root.listFiles { f -> f.isDirectory && f.name.startsWith("prefs-") }?.sortedBy { it.name }?.dropLast(KEEP)?.forEach { it.deleteRecursively() }
            Log.i(TAG, "copied ${files.size} preference files to ${folder.name} ($reason)")
        }.onFailure { Log.w(TAG, "backup failed: ${it.message}") }
    }
}
