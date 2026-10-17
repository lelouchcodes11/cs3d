package com.lagradost.desktop.stats

import android.util.Log
import com.lagradost.desktop.runtime.AndroidRuntime
import org.json.JSONObject
import java.io.File

/**
 * What Continue watching shows besides the saved progress: the still of the episode and the source it was watched with. The saved resume
 * entry only knows the title's poster; these are kept apart in `resume_extras.json` (not in the synced lists).
 */
object ResumeExtras {
    private const val TAG = "ResumeExtras"
    private val lock = Any()
    private val thumbs = LinkedHashMap<Int, String>()
    private val sources = LinkedHashMap<Int, String>()
    private var loaded = false
    private val file: File get() = File(AndroidRuntime.dataDir, "resume_extras.json")

    private fun ensure() {
        if (loaded) return
        loaded = true
        runCatching {
            if (!file.isFile) return
            val root = JSONObject(file.readText())
            root.optJSONObject("thumbs")?.let { o -> o.keys().forEach { k -> k.toIntOrNull()?.let { id -> thumbs[id] = o.optString(k) } } }
            root.optJSONObject("sources")?.let { o -> o.keys().forEach { k -> k.toIntOrNull()?.let { id -> sources[id] = o.optString(k) } } }
        }.onFailure { Log.w(TAG, "load: $it") }
    }

    /** The still of an episode, by the id of the episode */
    fun thumbOf(episodeId: Int?): String? = synchronized(lock) { ensure(); episodeId?.let { thumbs[it] } }

    /** The source a title was last watched with, by the id of the title (parent) */
    fun sourceOf(parentId: Int?): String? = synchronized(lock) { ensure(); parentId?.let { sources[it] } }

    fun setThumb(episodeId: Int?, url: String?) {
        if (episodeId == null || url.isNullOrBlank()) return
        synchronized(lock) {
            ensure()
            if (thumbs[episodeId] == url) return
            thumbs[episodeId] = url
            while (thumbs.size > 300) thumbs.remove(thumbs.keys.first())
            save()
        }
    }

    fun setSource(parentId: Int?, name: String?) {
        if (parentId == null || name.isNullOrBlank()) return
        synchronized(lock) {
            ensure()
            if (sources[parentId] == name) return
            sources.remove(parentId)
            sources[parentId] = name
            while (sources.size > 300) sources.remove(sources.keys.first())
            save()
        }
    }

    private fun save() {
        runCatching {
            val root = JSONObject()
            root.put("thumbs", JSONObject().also { o -> thumbs.forEach { (k, v) -> o.put(k.toString(), v) } })
            root.put("sources", JSONObject().also { o -> sources.forEach { (k, v) -> o.put(k.toString(), v) } })
            file.writeText(root.toString())
        }.onFailure { Log.w(TAG, "save: $it") }
    }
}
