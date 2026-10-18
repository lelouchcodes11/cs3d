package com.lagradost.desktop.runtime

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.StringWriter
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * SharedPreferences persisted in the exact XML format Android uses (shared_prefs/<name>.xml),
 * with in-memory reads, coalesced asynchronous writes for apply() and synchronous commit().
 *
 * Several processes (the windows of the app started more than once, see MultiInstance) may use the same folder: a write does not put the
 * whole map of this process on the disk but reads the file again first (under a lock on a file shared by the processes) and puts only what
 * this process changed on top of it, and a timer looks at the file once a second and takes over what another process wrote (the listeners
 * are told which keys changed). What one window saves is therefore seen by the others and nobody's changes are wiped.
 */
class SharedPreferencesImpl(private val file: File) : SharedPreferences {
    private val lock = ReentrantReadWriteLock()
    private var map: MutableMap<String, Any?> = HashMap()
    private val listeners = java.util.WeakHashMap<SharedPreferences.OnSharedPreferenceChangeListener, Any>()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var loaded = false

    // what this process changed and has not written yet (guarded by `lock`)
    private val pendingPut = HashMap<String, Any>()
    private val pendingDel = HashSet<String>()
    private var pendingClear = false

    // the deletions of the last minutes, for the guard against a bulk wipe
    private val removals = WipeGuard.Window()

    // the file as this process last read or wrote it: another process writing it shows as a difference
    @Volatile
    private var diskStamp = NO_FILE
    private var pendingWrite: ScheduledFuture<*>? = null

    private class Delta(val clear: Boolean, val del: Set<String>, val put: Map<String, Any>)

    private data class Stamp(val modified: Long, val length: Long)

    companion object {
        private val NO_FILE = Stamp(0L, -1L)

        private val writer = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "shared-prefs-writer").also { it.isDaemon = false }
        }
        private val poller = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "shared-prefs-poll").also { it.isDaemon = true }
        }
        private val instances = ArrayList<SharedPreferencesImpl>()

        // one at a time inside this process (a file lock is per process) and, with the lock file, across processes
        private val processLock = ReentrantLock()

        init {
            Runtime.getRuntime().addShutdownHook(Thread { flushAll() })
            poller.scheduleWithFixedDelay({
                val list = synchronized(instances) { instances.toList() }
                for (p in list) runCatching { p.refreshFromDisk() }
            }, 1500, 1000, TimeUnit.MILLISECONDS)
        }

        /** Write every pending change to disk (called on exit) */
        @JvmStatic
        fun flushAll() {
            val list = synchronized(instances) { instances.toList() }
            for (p in list) p.writeToDiskIfDirty()
        }

        private fun stampOf(f: File): Stamp = if (f.isFile) Stamp(f.lastModified(), f.length()) else NO_FILE
    }

    init {
        synchronized(instances) { instances.add(this) }
    }

    /** Runs [block] with the file shared by all processes of the app locked (when the lock cannot be had it runs without) */
    private fun <T> exclusive(block: () -> T): T {
        processLock.lock()
        var channel: FileChannel? = null
        var held: java.nio.channels.FileLock? = null
        try {
            try {
                val dir = file.parentFile?.parentFile ?: file.parentFile
                dir?.mkdirs()
                channel = FileChannel.open(File(dir, ".prefs.lock").toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
                held = channel.lock()
            } catch (_: Throwable) {
            }
            return block()
        } finally {
            runCatching { held?.release() }
            runCatching { channel?.close() }
            processLock.unlock()
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        val (loadedMap, stamp) = exclusive { load() to stampOf(file) }
        lock.write {
            if (loaded) return
            map = loadedMap
            diskStamp = stamp
            loaded = true
        }
    }

    /** A `.bak` is what older versions left when they were stopped while writing: it is only worth something when the file itself is gone */
    private fun settleOldBackup() {
        val backup = File(file.path + ".bak")
        if (!backup.exists()) return
        if (!file.exists()) backup.renameTo(file) else backup.delete()
    }

    private fun load(): MutableMap<String, Any?> {
        settleOldBackup()
        if (!file.exists()) return HashMap()
        return try {
            FileInputStream(file).use { stream -> parse(stream) }
        } catch (t: Throwable) {
            android.util.Log.e("SharedPreferences", "Failed to read ${file.name}: ${t.message}")
            // Keep the unreadable file, it would otherwise be overwritten by the next commit
            try {
                file.copyTo(File(file.path + ".corrupt-" + System.currentTimeMillis()), overwrite = false)
            } catch (_: Throwable) {
            }
            HashMap()
        }
    }

    /** The file as it is now, null when it is not there or cannot be read (nothing is changed on the disk) */
    private fun readIfReadable(): MutableMap<String, Any?>? {
        settleOldBackup()
        if (!file.exists()) return null
        return try {
            FileInputStream(file).use { stream -> parse(stream) }
        } catch (t: Throwable) {
            null
        }
    }

    private fun parse(stream: java.io.InputStream): MutableMap<String, Any?> {
        val parser = android.util.Xml.newPullParser()
        parser.setInput(stream, "UTF-8")
        val result = HashMap<String, Any?>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.depth == 2) {
                val tag = parser.name
                val name = parser.getAttributeValue(null, "name")
                when (tag) {
                    "string" -> result[name] = parser.nextText()
                    "boolean" -> result[name] = parser.getAttributeValue(null, "value").toBoolean()
                    "int" -> result[name] = parser.getAttributeValue(null, "value").toInt()
                    "long" -> result[name] = parser.getAttributeValue(null, "value").toLong()
                    "float" -> result[name] = parser.getAttributeValue(null, "value").toFloat()
                    "null" -> result[name] = null
                    "set" -> {
                        val set = HashSet<String>()
                        val depth = parser.depth
                        while (true) {
                            val e = parser.next()
                            if (e == XmlPullParser.END_TAG && parser.depth == depth) break
                            if (e == XmlPullParser.START_TAG && parser.name == "string") set.add(parser.nextText())
                            if (e == XmlPullParser.END_DOCUMENT) break
                        }
                        result[name] = set
                    }
                }
            }
            event = parser.next()
        }
        return result
    }

    private fun escape(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (c in s) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> {
                    if (c.code < 0x20 && c != '\n' && c != '\t' && c != '\r') sb.append("&#").append(c.code).append(';')
                    else if (c == '\r') sb.append("&#13;")
                    else sb.append(c)
                }
            }
        }
        return sb.toString()
    }

    private fun serialize(snapshot: Map<String, Any?>): String {
        val w = StringWriter()
        w.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n")
        for (key in snapshot.keys.sorted()) {
            val v = snapshot[key]
            val n = escape(key)
            when (v) {
                null -> w.append("    <null name=\"").append(n).append("\" />\n")
                is String -> w.append("    <string name=\"").append(n).append("\">").append(escape(v)).append("</string>\n")
                is Boolean -> w.append("    <boolean name=\"").append(n).append("\" value=\"").append(v.toString()).append("\" />\n")
                is Int -> w.append("    <int name=\"").append(n).append("\" value=\"").append(v.toString()).append("\" />\n")
                is Long -> w.append("    <long name=\"").append(n).append("\" value=\"").append(v.toString()).append("\" />\n")
                is Float -> w.append("    <float name=\"").append(n).append("\" value=\"").append(v.toString()).append("\" />\n")
                is Set<*> -> {
                    w.append("    <set name=\"").append(n).append("\">\n")
                    for (s in v) w.append("        <string>").append(escape(s.toString())).append("</string>\n")
                    w.append("    </set>\n")
                }
                else -> w.append("    <string name=\"").append(n).append("\">").append(escape(v.toString())).append("</string>\n")
            }
        }
        w.append("</map>\n")
        return w.toString()
    }

    private fun writeFile(snapshot: Map<String, Any?>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(serialize(snapshot), Charsets.UTF_8)
        // The file is never missing, not even for an instant. It used to be renamed away and the new one renamed in; a sync extension (Ultima)
        // that listed shared_prefs in between found no profile `rebuild_preference`, took it for deleted, recorded a tombstone and wiped the
        // extension list, the accounts and the library here and on the phone. Replacing is one step (or, when Windows refuses it because
        // another program has the file open, an overwrite in place).
        try {
            java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            file.outputStream().use { out -> tmp.inputStream().use { it.copyTo(out) } }
            tmp.delete()
        }
    }

    private fun applyDelta(target: MutableMap<String, Any?>, clear: Boolean, del: Set<String>, put: Map<String, Any>) {
        if (clear) target.clear()
        for (k in del) target.remove(k)
        target.putAll(put)
    }

    /** The keys whose value differs between the two maps */
    private fun differing(old: Map<String, Any?>, new: Map<String, Any?>): List<String?> {
        val keys = HashSet<String>(old.keys).also { it.addAll(new.keys) }
        return keys.filter { old.containsKey(it) != new.containsKey(it) || old[it] != new[it] }
    }

    @Synchronized
    private fun writeToDiskIfDirty() {
        val delta = lock.write {
            if (pendingPut.isEmpty() && pendingDel.isEmpty() && !pendingClear) null
            else Delta(pendingClear, HashSet(pendingDel), HashMap(pendingPut)).also {
                pendingPut.clear()
                pendingDel.clear()
                pendingClear = false
            }
        } ?: return
        var written = false
        var changedByOthers: List<String?> = emptyList()
        try {
            exclusive {
                file.parentFile?.mkdirs()
                if (stampOf(file) != diskStamp) {
                    // another process wrote the file since this one looked: what it has is the base, this process's changes go on top
                    val theirs = readIfReadable()
                    if (theirs != null) {
                        changedByOthers = lock.write {
                            val merged: MutableMap<String, Any?> = HashMap(theirs)
                            applyDelta(merged, delta.clear, delta.del, delta.put)
                            // changes made while this write was prepared stay pending and are applied again at the next one
                            applyDelta(merged, pendingClear, pendingDel, pendingPut)
                            val changed = differing(map, merged)
                            map = merged
                            changed
                        }
                    }
                }
                writeFile(lock.read { HashMap(map) })
                diskStamp = stampOf(file)
                written = true
            }
        } catch (t: Throwable) {
            android.util.Log.e("SharedPreferences", "Failed to write ${file.name}: ${t.message}")
        }
        if (!written) {
            // keep the changes for the next try
            lock.write {
                if (delta.clear) pendingClear = true
                for ((k, v) in delta.put) if (!pendingDel.contains(k) && !pendingPut.containsKey(k)) pendingPut[k] = v
                for (k in delta.del) if (!pendingPut.containsKey(k)) pendingDel.add(k)
            }
        }
        notifyListeners(changedByOthers)
    }

    /** Takes over what another process wrote to the file (the timer calls this every second) */
    private fun refreshFromDisk() {
        if (!loaded || stampOf(file) == diskStamp) return
        var changed: List<String?> = emptyList()
        synchronized(this) {
            exclusive {
                if (stampOf(file) == diskStamp) return@exclusive
                val theirs = readIfReadable()
                if (theirs != null) {
                    changed = lock.write {
                        val merged: MutableMap<String, Any?> = HashMap(theirs)
                        // what this process changed and has not written yet is still its own
                        applyDelta(merged, pendingClear, pendingDel, pendingPut)
                        val diff = differing(map, merged)
                        map = merged
                        diff
                    }
                }
                diskStamp = stampOf(file)
            }
        }
        notifyListeners(changed)
    }

    private fun scheduleWrite() {
        synchronized(this) {
            pendingWrite?.cancel(false)
            pendingWrite = writer.schedule({ writeToDiskIfDirty() }, 250, TimeUnit.MILLISECONDS)
        }
    }

    override fun getAll(): Map<String, *> {
        ensureLoaded()
        return lock.read { HashMap(map) }
    }

    override fun getString(key: String?, defValue: String?): String? {
        ensureLoaded()
        return lock.read { (map[key] as? String) ?: defValue }
    }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? {
        ensureLoaded()
        return lock.read { (map[key] as? Set<String>)?.let { HashSet(it) } ?: defValues }
    }

    private inline fun <reified T> typed(key: String?, def: T): T {
        ensureLoaded()
        return lock.read {
            val v = map[key]
            if (v == null) def
            else if (v is T) v
            else throw ClassCastException("${v.javaClass.name} cannot be cast to ${T::class.java.name}")
        }
    }

    override fun getInt(key: String?, defValue: Int): Int = typed(key, defValue)
    override fun getLong(key: String?, defValue: Long): Long = typed(key, defValue)
    override fun getFloat(key: String?, defValue: Float): Float = typed(key, defValue)
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = typed(key, defValue)

    override fun contains(key: String?): Boolean {
        ensureLoaded()
        return lock.read { map.containsKey(key) }
    }

    override fun edit(): SharedPreferences.Editor = EditorImpl()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(listeners) { listeners[listener] = Any() }
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    private fun notifyListeners(keys: List<String?>) {
        if (keys.isEmpty()) return
        val ls = synchronized(listeners) { listeners.keys.toList() }
        if (ls.isEmpty()) return
        val run = Runnable {
            for (k in keys.asReversed()) for (l in ls) l.onSharedPreferenceChanged(this, k)
        }
        if (Looper.getMainLooper().isCurrentThread) run.run() else mainHandler.post(run)
    }

    private inner class EditorImpl : SharedPreferences.Editor {
        private val modified = LinkedHashMap<String, Any?>()
        private val removed = HashSet<String>()
        private var clear = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor = synchronized(this) {
            modified[key] = value; removed.remove(key); this
        }

        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor = synchronized(this) {
            modified[key] = values?.let { HashSet(it) }; removed.remove(key); this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor = synchronized(this) {
            modified[key] = value; removed.remove(key); this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor = synchronized(this) {
            modified[key] = value; removed.remove(key); this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = synchronized(this) {
            modified[key] = value; removed.remove(key); this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = synchronized(this) {
            modified[key] = value; removed.remove(key); this
        }

        override fun remove(key: String): SharedPreferences.Editor = synchronized(this) {
            modified.remove(key); removed.add(key); this
        }

        override fun clear(): SharedPreferences.Editor = synchronized(this) {
            clear = true; this
        }

        private fun commitToMemory(): List<String?> {
            ensureLoaded()
            val changed = ArrayList<String?>()
            var kept = 0
            var total = 0
            lock.write {
                synchronized(this) {
                    // what this change would take away; the guard (WipeGuard) holds back a bulk deletion that is not the user's own
                    var keep: Set<String> = emptySet()
                    var restore: Map<String, Any?> = emptyMap()
                    if (WipeGuard.enabled && !WipeGuard.allowed()) {
                        val wouldRemove = HashSet<String>()
                        if (clear) wouldRemove.addAll(map.keys)
                        for (k in removed) if (map.containsKey(k)) wouldRemove.add(k)
                        for ((k, v) in modified) if (v == null && map.containsKey(k)) wouldRemove.add(k)
                        // a key that is put again is replaced, not removed
                        for ((k, v) in modified) if (v != null) wouldRemove.remove(k)
                        if (wouldRemove.isNotEmpty()) {
                            total = map.size
                            val verdict = removals.decide(System.currentTimeMillis(), map, wouldRemove)
                            keep = verdict.keep
                            restore = verdict.restore
                        }
                    }
                    if (clear) {
                        val survivors = if (keep.isEmpty()) emptyMap() else map.filterKeys { it in keep }
                        if (map.isNotEmpty()) {
                            map.clear()
                            changed.add(null)
                        }
                        pendingClear = true
                        pendingPut.clear()
                        pendingDel.clear()
                        for ((k, v) in survivors) if (v != null) { map[k] = v; pendingPut[k] = v }
                        kept += survivors.size
                        clear = false
                    }
                    for (k in removed) {
                        if (k in keep) { if (map.containsKey(k)) kept++; continue }
                        if (map.containsKey(k)) {
                            map.remove(k)
                            changed.add(k)
                            pendingDel.add(k)
                            pendingPut.remove(k)
                        }
                    }
                    for ((k, v) in modified) {
                        // Like Android, putting null removes the key
                        if (v == null) {
                            if (k in keep) { kept++; continue }
                            if (map.containsKey(k)) {
                                map.remove(k)
                                changed.add(k)
                                pendingDel.add(k)
                                pendingPut.remove(k)
                            }
                        } else if (map[k] != v || !map.containsKey(k)) {
                            map[k] = v
                            changed.add(k)
                            pendingPut[k] = v
                            pendingDel.remove(k)
                        }
                    }
                    // what the same bulk deletion took out a moment ago comes back
                    for ((k, v) in restore) {
                        if (v != null && !map.containsKey(k)) {
                            map[k] = v
                            changed.add(k)
                            pendingPut[k] = v
                            pendingDel.remove(k)
                            kept++
                        }
                    }
                    modified.clear()
                    removed.clear()
                }
            }
            if (kept > 0) WipeGuard.report(file.nameWithoutExtension, kept, total.coerceAtLeast(kept))
            return changed
        }

        override fun commit(): Boolean {
            val changed = commitToMemory()
            writeToDiskIfDirty()
            notifyListeners(changed)
            return true
        }

        override fun apply() {
            val changed = commitToMemory()
            if (changed.isNotEmpty()) scheduleWrite()
            notifyListeners(changed)
        }
    }
}
