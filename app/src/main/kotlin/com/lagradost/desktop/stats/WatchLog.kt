package com.lagradost.desktop.stats

import android.util.Log
import com.lagradost.desktop.runtime.AndroidRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * A private diary of what is watched, for the Stats page: how many milliseconds of which title were played on which day, plus what is known
 * of the title (type, genres, people, studios, languages). It is a file of its own in the data folder (`watchlog.json`): it is not part of the
 * saved lists, so nothing in it is synced or backed up with them, and it can be deleted at any time (Stats > Reset).
 */
object WatchLog {
    private const val TAG = "WatchLog"

    class Meta(
        val key: String,
        val name: String,
        val type: String,
        val api: String,
        val poster: String?,
        val genres: List<String> = emptyList(),
        val people: List<String> = emptyList(),
        val studios: List<String> = emptyList(),
        val subLang: String? = null,
        val audioLang: String? = null,
    )

    class Title(
        val key: String,
        var name: String,
        var type: String,
        var api: String,
        var poster: String?,
        var genres: List<String>,
        var people: List<String>,
        var studios: List<String>,
        var subLang: String?,
        var audioLang: String?,
        var starts: Int,
        var rewatches: Int,
        var firstDay: String,
        /** Episode ids that were watched to the end (a second watch of one is a rewatch) */
        val finished: MutableSet<Int> = HashSet(),
    )

    private val lock = Any()
    private val titles = LinkedHashMap<String, Title>()
    private val days = LinkedHashMap<String, MutableMap<String, Long>>()
    private var loaded = false
    private var dirty = false
    private var lastSave = 0L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val file: File get() = File(AndroidRuntime.dataDir, "watchlog.json")

    private fun today(): String = LocalDate.now(ZoneId.systemDefault()).toString()

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            if (!file.isFile) return
            val root = JSONObject(file.readText())
            root.optJSONObject("titles")?.let { t ->
                for (k in t.keys()) {
                    val o = t.optJSONObject(k) ?: continue
                    fun list(name: String) = o.optJSONArray(name)?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
                    titles[k] = Title(
                        k, o.optString("name"), o.optString("type"), o.optString("api"), o.optString("poster").takeIf { it.isNotBlank() },
                        list("genres"), list("people"), list("studios"), o.optString("sub").takeIf { it.isNotBlank() }, o.optString("audio").takeIf { it.isNotBlank() },
                        o.optInt("starts"), o.optInt("rewatches"), o.optString("first"),
                        o.optJSONArray("finished")?.let { a -> (0 until a.length()).map { a.optInt(it) }.toMutableSet() } ?: HashSet(),
                    )
                }
            }
            root.optJSONObject("days")?.let { d ->
                for (day in d.keys()) {
                    val o = d.optJSONObject(day) ?: continue
                    days[day] = o.keys().asSequence().associateWith { k -> o.optLong(k) }.toMutableMap()
                }
            }
        }.onFailure { Log.w(TAG, "load: $it") }
    }

    /** [ms] more of the title were played now */
    fun add(meta: Meta, ms: Long) {
        if (ms <= 0) return
        synchronized(lock) {
            ensureLoaded()
            val t = titles.getOrPut(meta.key) {
                Title(meta.key, meta.name, meta.type, meta.api, meta.poster, meta.genres, meta.people, meta.studios, meta.subLang, meta.audioLang, 0, 0, today())
            }
            // what became known later (genres from the page, studios from TMDB) fills in
            if (meta.genres.isNotEmpty()) t.genres = meta.genres
            if (meta.people.isNotEmpty()) t.people = meta.people
            if (meta.studios.isNotEmpty()) t.studios = meta.studios
            if (meta.poster != null) t.poster = meta.poster
            if (meta.subLang != null) t.subLang = meta.subLang
            if (meta.audioLang != null) t.audioLang = meta.audioLang
            val day = days.getOrPut(today()) { HashMap() }
            day[meta.key] = (day[meta.key] ?: 0L) + ms
            dirty = true
        }
        maybeSave()
    }

    /** An episode (or film) was opened: counted once per title and start */
    fun started(meta: Meta) {
        synchronized(lock) {
            ensureLoaded()
            val t = titles.getOrPut(meta.key) {
                Title(meta.key, meta.name, meta.type, meta.api, meta.poster, meta.genres, meta.people, meta.studios, meta.subLang, meta.audioLang, 0, 0, today())
            }
            t.starts++
            dirty = true
        }
    }

    /** An episode reached [fraction] of its length: the end of one is remembered, a second go at it counts as a rewatch (once) */
    fun progress(key: String, episodeId: Int, fraction: Float, rewatchNoted: Boolean): Boolean {
        synchronized(lock) {
            ensureLoaded()
            val t = titles[key] ?: return rewatchNoted
            var noted = rewatchNoted
            if (fraction >= 0.9f) {
                if (t.finished.add(episodeId)) dirty = true
            } else if (fraction in 0.1f..0.5f && !noted && episodeId in t.finished) {
                t.rewatches++
                noted = true
                dirty = true
            }
            return noted
        }
    }

    /** Studios found for a title (TMDB) after the first play */
    fun setStudios(key: String, studios: List<String>, genres: List<String>) {
        synchronized(lock) {
            ensureLoaded()
            val t = titles[key] ?: return
            if (studios.isNotEmpty()) t.studios = studios
            if (t.genres.isEmpty() && genres.isNotEmpty()) t.genres = genres
            dirty = true
        }
        maybeSave()
    }

    fun hasStudios(key: String): Boolean = synchronized(lock) { ensureLoaded(); titles[key]?.studios?.isNotEmpty() == true }

    private fun maybeSave() {
        val now = System.currentTimeMillis()
        if (!dirty || now - lastSave < 30_000L) return
        lastSave = now
        scope.launch { flush() }
    }

    /** Writes what is not written yet (at the end of playback) */
    fun flush() {
        val text = synchronized(lock) {
            if (!dirty) return
            dirty = false
            val root = JSONObject()
            val t = JSONObject()
            for ((k, v) in titles) t.put(k, JSONObject().apply {
                put("name", v.name); put("type", v.type); put("api", v.api); put("poster", v.poster ?: ""); put("genres", JSONArray(v.genres)); put("people", JSONArray(v.people))
                put("studios", JSONArray(v.studios)); put("sub", v.subLang ?: ""); put("audio", v.audioLang ?: ""); put("starts", v.starts); put("rewatches", v.rewatches)
                put("first", v.firstDay); put("finished", JSONArray(v.finished.toList()))
            })
            root.put("titles", t)
            val d = JSONObject()
            // a year of days is plenty for the heat map
            val keep = LocalDate.now(ZoneId.systemDefault()).minusDays(400).toString()
            for ((day, m) in days) if (day >= keep) d.put(day, JSONObject(m as Map<*, *>))
            root.put("days", d)
            root.toString()
        }
        runCatching { file.writeText(text) }.onFailure { Log.w(TAG, "save: $it") }
        lastSave = System.currentTimeMillis()
    }

    fun reset() {
        synchronized(lock) {
            titles.clear(); days.clear(); dirty = false; loaded = true
        }
        runCatching { file.delete() }
    }

    // ------------------------------------------------------------------ reading

    class Snapshot(val titles: Map<String, Title>, val days: Map<String, Map<String, Long>>)

    fun snapshot(): Snapshot = synchronized(lock) {
        ensureLoaded()
        Snapshot(titles.toMap(), days.mapValues { it.value.toMap() })
    }
}
