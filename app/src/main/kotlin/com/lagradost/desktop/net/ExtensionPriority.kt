package com.lagradost.desktop.net

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lagradost.desktop.runtime.AndroidRuntime
import org.json.JSONArray

/**
 * The extensions the viewer wants on top, in the order they were put (Settings > General > Extension priority; a star in the search's
 * extension list). Search results come in that order, the list of the Home page shows them first, and the search's own list as well.
 * Saved with the other settings of this PC (not synced).
 */
object ExtensionPriority {
    private const val KEY = "extension_priority"

    private fun prefs() = androidx.preference.PreferenceManager.getDefaultSharedPreferences(AndroidRuntime.context)

    private fun load(): List<String> = runCatching {
        val a = JSONArray(prefs().getString(KEY, "[]"))
        (0 until a.length()).map { a.getString(it) }.distinct()
    }.getOrDefault(emptyList())

    /** The pinned names, first = most wanted (a state: what reads it draws again when it changes) */
    var names: List<String> by mutableStateOf(load())
        private set

    private fun save(list: List<String>) {
        names = list
        runCatching { prefs().edit().putString(KEY, JSONArray(list).toString()).apply() }
    }

    fun isPinned(name: String) = name in names

    /** The place of an extension in the order: 0 is the first, a name that is not pinned comes after every pinned one */
    fun rank(name: String): Int = names.indexOf(name).let { if (it < 0) Int.MAX_VALUE else it }

    fun pin(name: String) { if (name !in names) save(names + name) }
    fun unpin(name: String) { save(names - name) }
    fun toggle(name: String) { if (isPinned(name)) unpin(name) else pin(name) }

    fun move(name: String, by: Int) {
        val i = names.indexOf(name)
        if (i < 0) return
        val j = (i + by).coerceIn(0, names.size - 1)
        if (i == j) return
        save(names.toMutableList().also { it.removeAt(i); it.add(j, name) })
    }

    /** Pinned names first in their order, the others keep the order they have */
    fun <T> ordered(items: List<T>, nameOf: (T) -> String): List<T> = items.sortedBy { rank(nameOf(it)) }
}
