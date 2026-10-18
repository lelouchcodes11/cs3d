package com.lagradost.desktop.runtime

import android.util.Log

/**
 * Keeps a bulk deletion from emptying the user's data (extension list, repositories, library, history, search history, settings).
 *
 * Why: a sync extension (Ultima) mirrors a cloud copy onto the local store and removes the local keys the cloud copy lacks. A copy that is
 * small, stale or marked deleted by another device (a "tombstone" of one device stays in the cloud and is applied by every device that syncs
 * later) therefore empties the store here, and the wipe is then pushed to the other devices. It happened three times (14 Oct on the portable
 * copy, 16 Oct, 18 Oct on an installed copy that had just been updated). The extension's own code runs in this process and uses the same
 * preference calls as the app, so the place to stop it is where the removal is applied to the store.
 *
 * What is stopped, unless the caller is inside [allow] (the user's own "clear history", "delete profile", "restore a backup"):
 *  - the removal of `PLUGINS_KEY` or `REPOSITORIES_KEY` while they hold something;
 *  - removals that add up, within [WINDOW_MS] and in one file, to at least [MIN_KEYS] keys and [PERCENT] % of what the file held at the start of
 *    the window (one `clear()` or a long run of single `remove`s alike). The keys removed earlier in that window are put back.
 * Puts always go through, so the new data of a sync is merged in; only what it would take away is kept.
 */
object WipeGuard {
    private const val TAG = "WipeGuard"
    const val MIN_KEYS = 25
    const val PERCENT = 40
    const val WINDOW_MS = 120_000L

    private val protectedKeys = setOf("PLUGINS_KEY", "REPOSITORIES_KEY")
    private val trusted = ThreadLocal<Int>()
    private val lastReport = HashMap<String, Long>()

    /** Off in tests that need the plain behaviour */
    @Volatile
    @JvmStatic
    var enabled = true

    /** Told when something was kept: the file's name, how many keys, how many the file holds (the app shows a notice) */
    @Volatile
    @JvmStatic
    var onBlocked: ((file: String, kept: Int, total: Int) -> Unit)? = null
        set(value) {
            field = value
            synchronized(lastReport) { lastReport.clear() }
        }

    /** Runs [block] as the user's own bulk change: nothing is held back in it (this thread only) */
    fun <T> allow(block: () -> T): T {
        trusted.set((trusted.get() ?: 0) + 1)
        try {
            return block()
        } finally {
            val n = (trusted.get() ?: 1) - 1
            if (n <= 0) trusted.remove() else trusted.set(n)
        }
    }

    internal fun allowed(): Boolean = (trusted.get() ?: 0) > 0

    internal fun isProtected(key: String): Boolean = key in protectedKeys || protectedKeys.any { key.endsWith("/$it") }

    internal fun hasValue(v: Any?): Boolean = when (v) {
        null -> false
        is String -> v.isNotBlank() && v != "[]" && v != "{}"
        is Collection<*> -> v.isNotEmpty()
        else -> true
    }

    /** What to keep and what to put back for one commit */
    internal class Verdict(val keep: Set<String>, val restore: Map<String, Any?>)

    /** The removals of one file during the last minutes (used under the file's lock) */
    internal class Window {
        private var start = 0L
        private var base: Map<String, Any?>? = null
        private var removed = 0
        private var tripped = false

        fun decide(now: Long, map: Map<String, Any?>, wouldRemove: Set<String>): Verdict {
            if (base == null || now - start > WINDOW_MS) {
                base = HashMap(map); start = now; removed = 0; tripped = false
            }
            val b = base!!
            removed += wouldRemove.size
            if (!tripped && removed >= MIN_KEYS && removed * 100L >= b.size * PERCENT.toLong()) tripped = true
            val keep = HashSet<String>()
            if (tripped) keep.addAll(wouldRemove) else wouldRemove.filterTo(keep) { isProtected(it) && hasValue(map[it]) }
            val restore = if (tripped) b.filter { (k, v) -> v != null && !map.containsKey(k) } else emptyMap()
            return Verdict(keep, restore)
        }
    }

    /** Logs what happened (with who asked, to find the caller) and tells the app, at most every 10 s for a file */
    internal fun report(file: String, kept: Int, total: Int) {
        val now = System.currentTimeMillis()
        val quiet = synchronized(lastReport) { (lastReport[file] ?: 0L).let { now - it < 10_000L }.also { if (!it) lastReport[file] = now } }
        if (quiet) return
        val who = Throwable().stackTrace.asSequence().map { it.toString() }.filter { "SharedPreferencesImpl" !in it && "WipeGuard" !in it }.take(14).joinToString("\n    at ")
        Log.w(TAG, "kept $kept of the $total keys of $file that were about to be deleted; asked from:\n    at $who")
        runCatching { onBlocked?.invoke(file, kept, total) }
    }
}
