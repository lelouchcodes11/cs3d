package com.lagradost.desktop.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.DataStoreHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kids profiles. A profile marked for kids uses only the sources a grown-up allowed (none at first, never adult ones), and Settings, Extensions
 * and new profiles ask for a grown-up's PIN. The allow-list is the real protection: sites do not label everything they have.
 * Kept beside the profiles (not in them: the profile list is synced with the phone app, which does not know this field).
 */
object Kids {
    private const val KEY = "desktop_kids_profiles"

    /** Changes with every edit: what reads [active] or the lists draws again */
    var version by mutableIntStateOf(0)
        private set

    private fun prefs() = androidx.preference.PreferenceManager.getDefaultSharedPreferences(com.lagradost.desktop.runtime.AndroidRuntime.context)

    /** Read once and kept until a change: the check runs for every source each time Home or search lists them */
    @Volatile private var cached: Pair<Int, JSONObject>? = null

    private fun all(): JSONObject {
        val v = version
        cached?.takeIf { it.first == v }?.let { return it.second }
        val o = runCatching { JSONObject(prefs().getString(KEY, "{}") ?: "{}") }.getOrDefault(JSONObject())
        cached = v to o
        return o
    }

    fun isKids(keyIndex: Int): Boolean { version; return all().has(keyIndex.toString()) }

    /** The source names a kids profile may use */
    fun allowed(keyIndex: Int): Set<String> {
        version
        val a = all().optJSONArray(keyIndex.toString()) ?: return emptySet()
        return (0 until a.length()).map { a.getString(it) }.toSet()
    }

    /** [kids] false: an ordinary profile again */
    fun set(keyIndex: Int, kids: Boolean, allowed: Set<String>) {
        val o = all()
        if (kids) o.put(keyIndex.toString(), JSONArray(allowed.sorted())) else o.remove(keyIndex.toString())
        prefs().edit().putString(KEY, o.toString()).apply()
        version++
    }

    /** The profile that is open is a kids profile */
    val active: Boolean get() = runCatching { isKids(DataStoreHelper.selectedKeyIndex) }.getOrDefault(false)

    /** Whether the open profile may use [api] (always, for a grown-up's profile) */
    fun allows(api: MainAPI): Boolean {
        if (!active) return true
        return api.name in allowed(DataStoreHelper.selectedKeyIndex) && TvType.NSFW !in api.supportedTypes
    }

    /**
     * Runs [action] at once on a grown-up's profile; on a kids profile only after the PIN of a grown-up's profile. Without any grown-up PIN
     * there is nothing to check, so it runs (the profile editor says to set one).
     */
    fun guard(action: () -> Unit) {
        // a grown-up who just gave the PIN is not asked again for a quarter of an hour
        if (!active || System.currentTimeMillis() - unlockedAt < 15 * 60_000L) return action()
        val grownUp = runCatching { DataStoreHelper.getAccounts(com.lagradost.desktop.DesktopBootstrap.activity) }.getOrDefault(emptyList())
            .firstOrNull { !isKids(it.keyIndex) && it.lockPin != null }
        if (grownUp == null) return action()
        com.lagradost.desktop.ui.shell.PinLock.ask("A grown-up's PIN (${grownUp.name})", grownUp.lockPin) { if (it != null) { unlockedAt = System.currentTimeMillis(); action() } }
    }

    @Volatile private var unlockedAt = 0L

    /** Pages a kids profile only opens with a grown-up's PIN */
    fun needsGuard(route: Route): Boolean = route is Route.Settings || route == Route.Extensions
}
