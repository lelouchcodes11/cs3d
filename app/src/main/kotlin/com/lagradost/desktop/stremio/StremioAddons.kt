package com.lagradost.desktop.stremio

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.APIHolder.allProviders
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.desktop.runtime.AndroidRuntime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** One add-on the user has: its address, whether it is on, and the manifest last read from it ([manifest] null until it could be read) */
class StremioAddon(
    val manifestUrl: String,
    val manifest: StremioManifest?,
    val enabled: Boolean,
    val error: String? = null,
) {
    val name: String get() = manifest?.name ?: manifestUrl.substringAfter("://").substringBefore('/')

    fun copy(manifest: StremioManifest? = this.manifest, enabled: Boolean = this.enabled, error: String? = this.error) = StremioAddon(manifestUrl, manifest, enabled, error)
}

/**
 * The user's Stremio add-ons (Settings > Stremio & torrents). Add-ons with catalogs or meta become providers of their own (their catalogs
 * are rows on Home, their search is a search, their items open the title page); add-ons with streams or subtitles add sources and
 * subtitles to what the player finds (see [StremioStreams]). The list is kept in the app preferences, the first start has Cinemeta.
 */
object StremioAddons {
    private const val TAG = "Stremio"
    private const val PREF = "desktop_stremio_addons_v1"
    const val CINEMETA = "https://v3-cinemeta.strem.io/manifest.json"

    /** Add-ons people commonly use: offered in the add dialog ([needsP2p]: its streams are torrents) */
    class Suggestion(val name: String, val about: String, val url: String, val needsP2p: Boolean = false)

    val suggestions = listOf(
        Suggestion("Cinemeta", "Movie and series catalogs and details (the official one)", CINEMETA),
        Suggestion("Torrentio", "Torrent streams for movies, series and anime. Needs the torrent engine, or a debrid service in its settings.", "https://torrentio.strem.fun/manifest.json", needsP2p = true),
        Suggestion("OpenSubtitles v3", "Subtitles in many languages for movies and series", "https://opensubtitles-v3.strem.io/manifest.json"),
        Suggestion("Anime Kitsu", "Anime catalogs and episodes from Kitsu", "https://anime-kitsu.strem.fun/manifest.json"),
    )

    /** The add-ons in the user's order; replaced as a whole item when one changes, so lists in the UI redraw */
    val addons = mutableStateListOf<StremioAddon>()

    @Volatile private var loaded = false
    private val lock = Mutex()

    private fun prefs() = androidx.preference.PreferenceManager.getDefaultSharedPreferences(AndroidRuntime.context)

    fun enabled(): List<StremioAddon> = addons.filter { it.enabled && it.manifest != null }
    fun streamAddons(): List<StremioAddon> = enabled().filter { it.manifest!!.providesStreams }
    fun subtitleAddons(): List<StremioAddon> = enabled().filter { it.manifest!!.providesSubtitles }
    fun metaAddons(): List<StremioAddon> = enabled().filter { it.manifest!!.providesMeta }
    fun find(manifestUrl: String): StremioAddon? = addons.firstOrNull { it.manifestUrl == manifestUrl }

    // ------------------------------------------------------------------ storage

    fun load() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            loaded = true
            runCatching {
                val p = prefs()
                val text = p.getString(PREF, null)
                val array = if (text == null) JSONArray().put(JSONObject().put("url", CINEMETA).put("enabled", true)) else JSONArray(text)
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    val url = o.optString("url").takeIf { it.isNotBlank() } ?: continue
                    val manifest = o.optString("manifest").takeIf { it.isNotBlank() }?.let { runCatching { StremioManifest.parse(it) }.getOrNull() }
                    addons.add(StremioAddon(url, manifest, o.optBoolean("enabled", true)))
                }
            }.onFailure { Log.w(TAG, "add-ons not read: ${it.message}") }
        }
    }

    private fun save() {
        runCatching {
            val array = JSONArray()
            for (a in addons) array.put(JSONObject().put("url", a.manifestUrl).put("enabled", a.enabled).also { o -> a.manifest?.let { o.put("manifest", it.raw) } })
            prefs().edit().putString(PREF, array.toString()).remove(PREF + "_enrich").apply()
        }
    }

    // ------------------------------------------------------------------ changes

    /** Reads the manifest at [input] (a "stremio://" link, a page address or the manifest itself) and adds the add-on. */
    suspend fun add(input: String): Result<StremioAddon> {
        load()
        val url = StremioClient.normalizeManifestUrl(input)
        addons.firstOrNull { it.manifestUrl == url }?.let { return Result.failure(IllegalStateException("${it.name} is already added")) }
        val manifest = StremioClient.manifest(url) ?: return Result.failure(IllegalStateException("That address did not answer with an add-on manifest"))
        val addon = StremioAddon(url, manifest, true)
        addons.add(addon)
        save()
        syncProviders()
        return Result.success(addon)
    }

    fun remove(manifestUrl: String) {
        addons.removeAll { it.manifestUrl == manifestUrl }
        save()
        ioSafe { syncProviders() }
    }

    fun setEnabled(manifestUrl: String, enabled: Boolean) {
        val i = addons.indexOfFirst { it.manifestUrl == manifestUrl }
        if (i < 0) return
        addons[i] = addons[i].copy(enabled = enabled)
        save()
        ioSafe { syncProviders() }
    }

    fun move(manifestUrl: String, delta: Int) {
        val i = addons.indexOfFirst { it.manifestUrl == manifestUrl }
        val j = i + delta
        if (i < 0 || j !in addons.indices) return
        val a = addons.removeAt(i)
        addons.add(j, a)
        save()
    }

    /** Reads the manifest again (an add-on changes its catalogs and its version) */
    suspend fun refresh(manifestUrl: String) {
        val i = addons.indexOfFirst { it.manifestUrl == manifestUrl }
        if (i < 0) return
        val manifest = StremioClient.manifest(manifestUrl)
        val i2 = addons.indexOfFirst { it.manifestUrl == manifestUrl }
        if (i2 < 0) return
        addons[i2] = if (manifest != null) addons[i2].copy(manifest = manifest, error = null) else addons[i2].copy(error = "Did not answer")
        save()
        syncProviders()
    }

    suspend fun refreshAll() {
        load()
        for (a in addons.toList()) refresh(a.manifestUrl)
    }

    // ------------------------------------------------------------------ providers

    /** The add-ons with catalogs or meta that are on, as providers in the engine (made again after every change and every load of the extensions) */
    suspend fun syncProviders() {
        load()
        lock.withLock {
            allProviders.withLock {
                try {
                    val wanted = addons.filter { it.enabled && it.manifest != null && (it.manifest.providesCatalogs || it.manifest.providesMeta) }
                    val names = HashSet<String>()
                    val keep = HashSet<String>()
                    for (a in wanted) {
                        var name = a.manifest!!.name
                        var n = 2
                        while (!names.add(name)) name = "${a.manifest.name} ($n)".also { n++ }
                        keep += a.manifestUrl
                        val existing = allProviders.firstOrNull { it is StremioApi && it.addonUrl == a.manifestUrl } as StremioApi?
                        if (existing != null && existing.name == name) continue
                        if (existing != null) allProviders.remove(existing)
                        allProviders.add(StremioApi(a.manifestUrl, name))
                        Log.i(TAG, "provider ${name} (${a.manifest.catalogs.size} catalogs)")
                    }
                    allProviders.filter { it is StremioApi && it.addonUrl !in keep }.forEach { allProviders.remove(it) }
                    apis = allProviders.distinctBy { it.lang + it.name + it.mainUrl + it::class.qualifiedName }
                    APIHolder.apiMap = null
                } catch (t: Throwable) {
                    Log.w(TAG, "providers not updated: ${t.message}")
                }
            }
        }
    }

    /** Start-up: the saved list, the providers of it, then the manifests are read again in the background (once a day) */
    fun start() {
        load()
        ioSafe {
            syncProviders()
            val last = prefs().getLong(PREF + "_checked", 0L)
            if (System.currentTimeMillis() - last > 24 * 3_600_000L || addons.any { it.manifest == null }) {
                prefs().edit().putLong(PREF + "_checked", System.currentTimeMillis()).apply()
                refreshAll()
            }
        }
    }
}
