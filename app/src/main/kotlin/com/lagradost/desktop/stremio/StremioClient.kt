package com.lagradost.desktop.stremio

import android.util.Log
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/** The HTTP side of the add-on protocol: addresses, requests, a short memory of answers and the reading of the JSON. */
object StremioClient {
    private const val TAG = "Stremio"

    // ------------------------------------------------------------------ addresses

    /** "stremio://host/path", "host/path", "https://host/path?x=y" -> the address of the manifest */
    fun normalizeManifestUrl(input: String): String {
        var url = input.trim()
        if (url.startsWith("stremio://", ignoreCase = true)) url = "https://" + url.substring(10)
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) url = "https://$url"
        val query = if (url.contains('?')) "?" + url.substringAfter('?') else ""
        // "…/manifest.json/" got a second "/manifest.json"; the add-on's own settings page ("…/cfg/configure", where its install link is made)
        // became "…/configure/manifest.json", which no add-on answers
        var path = url.substringBefore('?').trimEnd('/')
        if (path.endsWith("/configure", ignoreCase = true)) path = path.dropLast("/configure".length)
        if (!path.endsWith("manifest.json", ignoreCase = true)) path = "$path/manifest.json"
        return path + query
    }

    /** The address resources hang from: the manifest address without "/manifest.json" (the configuration of an add-on is part of the path) */
    fun baseOf(manifestUrl: String): String = manifestUrl.substringBefore('?').removeSuffix("/manifest.json").removeSuffix("/")

    private fun queryOf(manifestUrl: String): String = manifestUrl.substringAfter('?', "").takeIf { it.isNotBlank() }?.let { "?$it" } ?: ""

    /** JavaScript's encodeURIComponent: what the SDK's clients send for ids and extras */
    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20").replace("*", "%2A").replace("%7E", "~")

    fun resourceUrl(manifestUrl: String, resource: String, type: String, id: String, extra: Map<String, String> = emptyMap()): String {
        val tail = if (extra.isEmpty()) "" else "/" + extra.entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
        return "${baseOf(manifestUrl)}/$resource/${enc(type)}/${enc(id)}$tail.json${queryOf(manifestUrl)}"
    }

    /**
     * Address safe to write to logs: drops credentials, query parameters and any configuration segment that Stremio add-ons put in the path
     * (debrid API keys, auth tokens, base64/JSON configs before `/manifest.json`, `/catalog/`, `/meta/`, `/stream/` or `/subtitles/`).
     */
    internal fun safeUrl(url: String): String {
        val noQuery = url.substringBefore('?').substringBefore('#')
        val schemeEnd = noQuery.indexOf("://").takeIf { it >= 0 }?.let { it + 3 } ?: return noQuery.take(80)
        val pathStart = noQuery.indexOf('/', schemeEnd)
        if (pathStart < 0) return noQuery.substring(0, schemeEnd) + noQuery.substring(schemeEnd).substringAfter('@')
        val host = noQuery.substring(schemeEnd, pathStart).substringAfter('@')
        val path = noQuery.substring(pathStart)
        val resource = Regex("""/(manifest\.json|(?:catalog|meta|stream|subtitles)/.*)$""", RegexOption.IGNORE_CASE).find(path)
        val cleanPath = when {
            resource == null -> if (path == "/") "/" else "/…"
            resource.range.first > 0 -> "/…${resource.value}"
            else -> resource.value
        }
        return (noQuery.substring(0, schemeEnd) + host + cleanPath).take(120)
    }

    // ------------------------------------------------------------------ requests

    private class Cached(val at: Long, val body: String)

    private val cache = ConcurrentHashMap<String, Cached>()

    /** The JSON at [url]; null when the add-on did not answer in time or sent something else. Answers are kept for [ttlMs]. */
    suspend fun getJson(url: String, ttlMs: Long = 0L, timeoutSec: Long = 15): JSONObject? {
        val now = System.currentTimeMillis()
        if (ttlMs > 0) cache[url]?.takeIf { now - it.at < ttlMs }?.let { return runCatching { JSONObject(it.body) }.getOrNull() }
        return try {
            val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.get(url, timeout = timeoutSec, headers = mapOf("Accept" to "application/json")).also { runCatching { it.text } } }
            if (!r.isSuccessful) {
                Log.w(TAG, "HTTP ${r.code} from ${safeUrl(url)}")
                return null
            }
            val text = r.text
            val json = JSONObject(text)
            if (ttlMs > 0) {
                if (cache.size > 300) cache.clear()
                cache[url] = Cached(now, text)
            }
            json
        } catch (t: Throwable) {
            // a cancelled request (the page was left, a time limit ran out) is not "no answer": swallowed, a left title page went on to ask
            // every other add-on and then failed with "No add-on has details"
            if (t is CancellationException) currentCoroutineContext().ensureActive()
            Log.w(TAG, "${safeUrl(url)}: ${t.javaClass.simpleName} ${t.message}")
            null
        }
    }

    suspend fun manifest(manifestUrl: String): StremioManifest? {
        val url = manifestUrl
        return try {
            val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.get(url, timeout = 15, headers = mapOf("Accept" to "application/json")).also { runCatching { it.text } } }
            if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}")
            StremioManifest.parse(r.text).also { if (it.id.isBlank() && it.name == "Add-on") throw IllegalStateException("not an add-on manifest") }
        } catch (t: Throwable) {
            if (t is CancellationException) currentCoroutineContext().ensureActive()
            Log.w(TAG, "manifest ${safeUrl(url)}: ${t.message}")
            null
        }
    }

    fun clearCache() = cache.clear()

    // ------------------------------------------------------------------ reading

    private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } }

    private fun JSONObject.str(name: String): String? = optString(name).takeIf { it.isNotBlank() && it != "null" }

    /** "142 min", "2h22min" (the TMDB add-on), "1h 5m", "PT2H22M" -> minutes; the first number alone made "2h22min" 2 minutes */
    internal fun runtimeMinutes(runtime: String?): Int? {
        val t = runtime?.lowercase() ?: return null
        val h = Regex("""([0-9]+)\s*h""").find(t)?.groupValues?.get(1)?.toIntOrNull()
        val m = Regex("""([0-9]+)\s*m""").find(t)?.groupValues?.get(1)?.toIntOrNull()
        if (h != null) return h * 60 + (m ?: 0)
        return m ?: Regex("[0-9]+").find(t)?.value?.toIntOrNull()
    }

    private fun yearOf(vararg texts: String?): Int? = texts.firstNotNullOfOrNull { t -> t?.let { Regex("(19|20)\\d{2}").find(it)?.value?.toIntOrNull() } }

    fun parseMeta(o: JSONObject, defaultType: String = ""): StremioMeta {
        val genres = strings(o.optJSONArray("genres")).ifEmpty { strings(o.optJSONArray("genre")) }
        val trailers = buildList {
            o.optJSONArray("trailers")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.str("source")?.let { add(it) } }
            o.optJSONArray("trailerStreams")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.str("ytId")?.let { add(it) } }
        }.distinct()
        val videos = buildList {
            val a = o.optJSONArray("videos")
            if (a != null) for (i in 0 until a.length()) {
                val v = a.optJSONObject(i) ?: continue
                val id = v.str("id") ?: continue
                add(
                    StremioVideo(
                        id, v.str("title") ?: v.str("name"), v.optInt("season", -1).takeIf { v.has("season") && it >= 0 },
                        v.optInt("episode", -1).takeIf { v.has("episode") && it >= 0 } ?: v.optInt("number", -1).takeIf { v.has("number") && it >= 0 },
                        v.str("released"), v.str("thumbnail"), v.str("overview") ?: v.str("description"), v.optJSONArray("streams"),
                    ),
                )
            }
        }
        val links = buildList {
            o.optJSONArray("links")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { l -> l.str("url")?.let { u -> add((l.str("name") ?: u) to u) } } }
        }
        val id = o.str("id") ?: o.str("imdb_id").orEmpty()
        return StremioMeta(
            id = id, type = o.str("type") ?: defaultType, name = o.str("name") ?: o.str("title") ?: id,
            poster = o.str("poster"), background = o.str("background") ?: o.str("fanart"), logo = o.str("logo"),
            description = o.str("description") ?: o.str("overview"), releaseInfo = o.str("releaseInfo"),
            year = yearOf(o.str("year"), o.str("releaseInfo"), o.str("released")), rating = o.str("imdbRating")?.toDoubleOrNull(),
            genres = genres, cast = strings(o.optJSONArray("cast")), directors = strings(o.optJSONArray("director")).ifEmpty { listOfNotNull(o.str("director")) },
            runtime = o.str("runtime"), trailerIds = trailers, imdbId = o.str("imdb_id") ?: id.takeIf { it.startsWith("tt") }, videos = videos, links = links,
        )
    }

    fun parseMetas(root: JSONObject, defaultType: String): List<StremioMeta> {
        val a = root.optJSONArray("metas") ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let { m -> parseMeta(m, defaultType) } }.filter { it.id.isNotBlank() }
    }

    fun parseStreams(root: JSONObject): List<StremioStream> {
        val a = root.optJSONArray("streams") ?: return emptyList()
        return parseStreamArray(a)
    }

    fun parseStreamArray(a: JSONArray): List<StremioStream> = (0 until a.length()).mapNotNull { i ->
        val s = a.optJSONObject(i) ?: return@mapNotNull null
        val hints = s.optJSONObject("behaviorHints")
        // request headers a stream needs: proxyHeaders.request (current) or headers (older)
        val headers = LinkedHashMap<String, String>()
        (hints?.optJSONObject("proxyHeaders")?.optJSONObject("request") ?: hints?.optJSONObject("headers"))?.let { h -> h.keys().forEach { k -> headers[k] = h.optString(k) } }
        val subs = s.optJSONArray("subtitles")?.let { parseSubtitleArray(it) }.orEmpty()
        StremioStream(
            url = s.str("url"), ytId = s.str("ytId"), infoHash = s.str("infoHash")?.lowercase(), fileIdx = if (s.has("fileIdx")) s.optInt("fileIdx", -1).takeIf { it >= 0 } else null,
            externalUrl = s.str("externalUrl"), name = s.str("name"), title = s.str("title"), description = s.str("description"),
            trackers = strings(s.optJSONArray("sources")).map { it.removePrefix("tracker:") }.filter { it.contains("://") },
            headers = headers, notWebReady = hints?.optBoolean("notWebReady") == true, bingeGroup = hints?.str("bingeGroup"), filename = hints?.str("filename"), subtitles = subs,
        )
    }

    fun parseSubtitles(root: JSONObject): List<StremioSubtitle> = root.optJSONArray("subtitles")?.let { parseSubtitleArray(it) }.orEmpty()

    private fun parseSubtitleArray(a: JSONArray): List<StremioSubtitle> = (0 until a.length()).mapNotNull { i ->
        val s = a.optJSONObject(i) ?: return@mapNotNull null
        val url = s.str("url") ?: return@mapNotNull null
        StremioSubtitle(s.str("id"), url, s.str("lang") ?: "und")
    }
}
