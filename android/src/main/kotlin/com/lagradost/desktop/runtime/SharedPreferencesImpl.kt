package com.lagradost.desktop.runtime

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.StringWriter
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * SharedPreferences persisted in the exact XML format Android uses (shared_prefs/<name>.xml),
 * with in-memory reads, coalesced asynchronous writes for apply() and synchronous commit().
 */
class SharedPreferencesImpl(private val file: File) : SharedPreferences {
    private val lock = ReentrantReadWriteLock()
    private var map: MutableMap<String, Any?> = HashMap()
    private val listeners = java.util.WeakHashMap<SharedPreferences.OnSharedPreferenceChangeListener, Any>()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var loaded = false

    @Volatile
    private var dirtyGeneration = 0L
    private var writtenGeneration = 0L
    private var pendingWrite: ScheduledFuture<*>? = null

    companion object {
        private val writer = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "shared-prefs-writer").also { it.isDaemon = false }
        }
        private val instances = ArrayList<SharedPreferencesImpl>()

        init {
            Runtime.getRuntime().addShutdownHook(Thread { flushAll() })
        }

        /** Write every pending change to disk (called on exit) */
        @JvmStatic
        fun flushAll() {
            val list = synchronized(instances) { instances.toList() }
            for (p in list) p.writeToDiskIfDirty()
        }
    }

    init {
        synchronized(instances) { instances.add(this) }
    }

    private fun ensureLoaded() {
        if (loaded) return
        lock.write {
            if (loaded) return
            map = load()
            loaded = true
        }
    }

    private fun load(): MutableMap<String, Any?> {
        val backup = File(file.path + ".bak")
        if (backup.exists()) {
            // A crash happened while writing, the backup is the last good state
            file.delete()
            backup.renameTo(file)
        }
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

    @Synchronized
    private fun writeToDiskIfDirty() {
        val gen: Long
        val snapshot: Map<String, Any?>
        lock.read {
            gen = dirtyGeneration
            if (gen == writtenGeneration) return
            snapshot = HashMap(map)
        }
        try {
            file.parentFile?.mkdirs()
            val backup = File(file.path + ".bak")
            val tmp = File(file.path + ".tmp")
            tmp.writeText(serialize(snapshot), Charsets.UTF_8)
            if (file.exists()) {
                backup.delete()
                file.renameTo(backup)
            }
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
            backup.delete()
            writtenGeneration = gen
        } catch (t: Throwable) {
            android.util.Log.e("SharedPreferences", "Failed to write ${file.name}: ${t.message}")
        }
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
            lock.write {
                synchronized(this) {
                    if (clear) {
                        if (map.isNotEmpty()) {
                            map.clear()
                            changed.add(null)
                        }
                        clear = false
                    }
                    for (k in removed) {
                        if (map.containsKey(k)) {
                            map.remove(k)
                            changed.add(k)
                        }
                    }
                    for ((k, v) in modified) {
                        // Like Android, putting null removes the key
                        if (v == null) {
                            if (map.containsKey(k)) {
                                map.remove(k)
                                changed.add(k)
                            }
                        } else if (map[k] != v || !map.containsKey(k)) {
                            map[k] = v
                            changed.add(k)
                        }
                    }
                    modified.clear()
                    removed.clear()
                }
                if (changed.isNotEmpty()) dirtyGeneration++
            }
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
