package com.lagradost.desktop.net

import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.desktop.runtime.AndroidRuntime

/**
 * The source that played last for a title, so the next episode can start on the same one ("Last used source" in the sources list).
 * What is remembered is the provider and the name of the source without what changes from one episode to the next (file sizes, SxxEyy).
 */
object LastSource {
    private const val PREFIX = "desktop_last_source:"
    private const val KEY_ENABLED = "desktop_prefer_last_source"

    /** The title the player is on now (set by the player session once the title page is loaded) */
    @Volatile var titleKey: String? = null

    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(AndroidRuntime.context)

    var enabled: Boolean
        get() = runCatching { prefs.getBoolean(KEY_ENABLED, true) }.getOrDefault(true)
        set(v) { runCatching { prefs.edit().putBoolean(KEY_ENABLED, v).apply() } }

    enum class Sort(val label: String) { Best("Best"), Quality("Quality"), Size("Size"), Seeders("Seeders") }

    /** How the sources list is ordered (the automatic choice always uses the profile order) */
    var sort: Sort
        get() = runCatching { Sort.valueOf(prefs.getString("desktop_source_sort", null) ?: "Best") }.getOrDefault(Sort.Best)
        set(v) { runCatching { prefs.edit().putString("desktop_source_sort", v.name).apply() } }

    private val noise = Regex("""(\d+(?:[.,]\d+)?\s*(?:gb|mb)|s\d{1,2}\s*e\d{1,3}|\be\d{1,3}\b|episode\s*\d+|\d{3,4}p|[\[\](){}|•·:_-])""", RegexOption.IGNORE_CASE)

    /** The same for the same kind of source in every episode */
    fun key(link: ExtractorLink?): String {
        link ?: return ""
        val name = noise.replace(link.name, " ").lowercase().replace(Regex("""\s+"""), " ").trim()
        return link.source.lowercase().trim() + "|" + name
    }

    /** The key remembered for the title now (null when nothing is) */
    fun remembered(): String? {
        val t = titleKey ?: return null
        return runCatching { prefs.getString(PREFIX + t, null) }.getOrNull()
    }

    fun remember(link: ExtractorLink?) {
        val t = titleKey ?: return
        val k = key(link).takeIf { it.isNotBlank() } ?: return
        runCatching { if (prefs.getString(PREFIX + t, null) != k) prefs.edit().putString(PREFIX + t, k).apply() }
    }

    fun isPreferred(link: ExtractorLink?): Boolean {
        if (!enabled) return false
        val want = remembered() ?: return false
        return key(link) == want
    }
}
