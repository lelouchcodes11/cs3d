package com.lagradost.desktop.net

import com.lagradost.cloudstream3.utils.ExtractorLink
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Which kinds of source failed lately. Among sources of the same quality, the kinds that did not play recently go last, so a video does
 * not start with the link that is known not to work. A "kind" is the extractor plus the label in brackets ("HubCloud [Instant Download]"):
 * the Cloudflare workers behind HubCloud's "Instant Download" links refuse (HTTP 403) or take 10-25 s to start more often than not, and each
 * link has a worker (a host) of its own, so the host cannot be what is remembered. Failures are kept for six hours (also across runs).
 *
 * [PRIOR]: kinds that were measured to be slow or flaky start with a small penalty (4KHDHub on 2026-10-08: "10Gbps [Download]" and
 * "[Pixeldrain]" started in 4-6 s every time, "[Instant Download]" in 10-25 s or not at all).
 */
object HostHealth {
    private const val MEMORY_MS = 6 * 3_600_000L
    private const val PREF = "desktop_source_health_v1"
    private val PRIOR = mapOf("instant download" to 1)

    private class Entry(var count: Int, var at: Long)

    private val entries = ConcurrentHashMap<String, Entry>()
    @Volatile private var loaded = false

    private fun prefs() = androidx.preference.PreferenceManager.getDefaultSharedPreferences(com.lagradost.desktop.runtime.AndroidRuntime.context)

    private fun kind(link: ExtractorLink): String {
        val label = Regex("\\[([^\\]]+)\\]").find(link.name)?.groupValues?.get(1)?.trim()?.lowercase().orEmpty()
        return (link.source.trim().lowercase() + " " + label).trim()
    }

    private fun labelOf(link: ExtractorLink): String = Regex("\\[([^\\]]+)\\]").find(link.name)?.groupValues?.get(1)?.trim()?.lowercase().orEmpty()

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            runCatching {
                val json = JSONObject(prefs().getString(PREF, "{}") ?: "{}")
                for (key in json.keys()) {
                    val o = json.getJSONObject(key)
                    entries[key] = Entry(o.getInt("c"), o.getLong("t"))
                }
            }
            loaded = true
        }
    }

    private fun save() {
        runCatching {
            val json = JSONObject()
            val now = System.currentTimeMillis()
            for ((k, e) in entries) if (e.count > 0 && now - e.at < MEMORY_MS) json.put(k, JSONObject().put("c", e.count).put("t", e.at))
            prefs().edit().putString(PREF, json.toString()).apply()
        }
    }

    fun failed(link: ExtractorLink?) {
        link ?: return
        ensureLoaded()
        val e = entries.getOrPut(kind(link)) { Entry(0, 0) }
        e.count = minOf(e.count + 1, 5)
        e.at = System.currentTimeMillis()
        save()
    }

    fun worked(link: ExtractorLink?) {
        link ?: return
        ensureLoaded()
        val e = entries[kind(link)] ?: return
        if (e.count > 0) {
            e.count--
            save()
        }
    }

    /** Added to a link's place among sources of the same quality: 0 is first */
    fun penalty(link: ExtractorLink?): Int {
        link ?: return 0
        ensureLoaded()
        val learned = entries[kind(link)]?.takeIf { System.currentTimeMillis() - it.at < MEMORY_MS }?.count ?: 0
        return learned + (PRIOR[labelOf(link)] ?: 0)
    }
}
