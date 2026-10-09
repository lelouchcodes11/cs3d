package com.lagradost.desktop.stremio

import org.json.JSONArray
import org.json.JSONObject

/**
 * Stremio add-ons are web services that describe themselves in a `manifest.json` and answer `/catalog`, `/meta`, `/stream` and
 * `/subtitles` requests with JSON (the open add-on protocol: https://github.com/Stremio/stremio-addon-sdk). This app is not made by or
 * connected to Stremio; it only speaks that protocol.
 */

/** One extra a catalog takes ("search", "genre", "skip"): [options] are the allowed values of a "genre" like extra */
class StremioExtra(val name: String, val required: Boolean, val options: List<String>)

class StremioCatalog(val type: String, val id: String, val name: String, val extras: List<StremioExtra>) {
    val supportsSearch: Boolean get() = extras.any { it.name == "search" }
    val supportsSkip: Boolean get() = extras.any { it.name == "skip" }

    /** A catalog that cannot be shown on its own (it needs a search text or a genre first) */
    val needsExtra: Boolean get() = extras.any { it.required }
    val genres: List<String> get() = extras.firstOrNull { it.name == "genre" }?.options.orEmpty()
}

/** A resource an add-on offers ("catalog", "meta", "stream", "subtitles"), possibly for fewer types or id prefixes than the add-on as a whole */
class StremioResource(val name: String, val types: List<String>?, val idPrefixes: List<String>?)

class StremioManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val logo: String?,
    val background: String?,
    val resources: List<StremioResource>,
    val types: List<String>,
    val idPrefixes: List<String>,
    val catalogs: List<StremioCatalog>,
    val configurable: Boolean,
    val configurationRequired: Boolean,
    val p2p: Boolean,
    val adult: Boolean,
    val raw: String,
) {
    val providesCatalogs: Boolean get() = catalogs.isNotEmpty()
    val providesMeta: Boolean get() = resources.any { it.name == "meta" }
    val providesStreams: Boolean get() = resources.any { it.name == "stream" }
    val providesSubtitles: Boolean get() = resources.any { it.name == "subtitles" }

    /** The add-on says it streams torrents (or its text does): its sources need the torrent engine */
    val torrents: Boolean get() = p2p || description.contains("torrent", ignoreCase = true) || name.contains("torrent", ignoreCase = true)

    /** Does this add-on answer [resource] requests for an item of [type] with [itemId] (a "tt..." IMDb id, "kitsu:12" ...)? */
    fun provides(resource: String, type: String, itemId: String): Boolean {
        val r = resources.firstOrNull { it.name == resource } ?: return false
        val types = r.types ?: this.types
        if (types.isNotEmpty() && type !in types) return false
        val prefixes = r.idPrefixes ?: idPrefixes
        return prefixes.isEmpty() || prefixes.any { itemId.startsWith(it) }
    }

    companion object {
        private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } }

        fun parse(text: String): StremioManifest {
            val j = JSONObject(text)
            val topTypes = strings(j.optJSONArray("types"))
            val topPrefixes = strings(j.optJSONArray("idPrefixes"))
            // resources are plain names or objects with their own types and id prefixes
            val resources = buildList {
                val a = j.optJSONArray("resources")
                if (a != null) for (i in 0 until a.length()) {
                    val v = a.get(i)
                    if (v is String) add(StremioResource(v, null, null))
                    else if (v is JSONObject) add(
                        StremioResource(
                            v.optString("name"),
                            v.optJSONArray("types")?.let { strings(it) },
                            v.optJSONArray("idPrefixes")?.let { strings(it) },
                        ),
                    )
                }
            }
            val catalogs = buildList {
                val a = j.optJSONArray("catalogs")
                if (a != null) for (i in 0 until a.length()) {
                    val c = a.optJSONObject(i) ?: continue
                    val extras = ArrayList<StremioExtra>()
                    c.optJSONArray("extra")?.let { ex ->
                        for (k in 0 until ex.length()) {
                            val e = ex.optJSONObject(k) ?: continue
                            extras += StremioExtra(e.optString("name"), e.optBoolean("isRequired"), strings(e.optJSONArray("options")))
                        }
                    }
                    // the older way of saying the same: extraSupported / extraRequired
                    val required = strings(c.optJSONArray("extraRequired"))
                    strings(c.optJSONArray("extraSupported")).forEach { n -> if (extras.none { it.name == n }) extras += StremioExtra(n, n in required, emptyList()) }
                    required.forEach { n -> if (extras.none { it.name == n }) extras += StremioExtra(n, true, emptyList()) }
                    val id = c.optString("id")
                    val type = c.optString("type")
                    if (id.isNotBlank() && type.isNotBlank()) add(StremioCatalog(type, id, c.optString("name").ifBlank { id }, extras))
                }
            }
            val hints = j.optJSONObject("behaviorHints")
            return StremioManifest(
                id = j.optString("id"), name = j.optString("name").ifBlank { "Add-on" }, version = j.optString("version"),
                description = j.optString("description"), logo = j.optString("logo").takeIf { it.startsWith("http") },
                background = j.optString("background").takeIf { it.startsWith("http") },
                resources = resources, types = topTypes, idPrefixes = topPrefixes, catalogs = catalogs,
                configurable = hints?.optBoolean("configurable") == true, configurationRequired = hints?.optBoolean("configurationRequired") == true,
                p2p = hints?.optBoolean("p2p") == true, adult = hints?.optBoolean("adult") == true, raw = text,
            )
        }
    }
}

/** An item of a catalog or a full meta of an item (the meta of a series lists its [videos]) */
class StremioMeta(
    val id: String,
    val type: String,
    val name: String,
    val poster: String?,
    val background: String?,
    val logo: String?,
    val description: String?,
    val releaseInfo: String?,
    val year: Int?,
    val rating: Double?,
    val genres: List<String>,
    val cast: List<String>,
    val directors: List<String>,
    val runtime: String?,
    val trailerIds: List<String>,
    val imdbId: String?,
    val videos: List<StremioVideo>,
    val links: List<Pair<String, String>>,
)

/** An episode (or the video of a channel); [streams] are streams the meta itself carries */
class StremioVideo(
    val id: String,
    val title: String?,
    val season: Int?,
    val episode: Int?,
    val released: String?,
    val thumbnail: String?,
    val overview: String?,
    val streams: JSONArray?,
)

class StremioStream(
    val url: String?,
    val ytId: String?,
    val infoHash: String?,
    val fileIdx: Int?,
    val externalUrl: String?,
    val name: String?,
    val title: String?,
    val description: String?,
    val trackers: List<String>,
    val headers: Map<String, String>,
    val notWebReady: Boolean,
    val bingeGroup: String?,
    val filename: String?,
    val subtitles: List<StremioSubtitle>,
)

class StremioSubtitle(val id: String?, val url: String, val lang: String)
