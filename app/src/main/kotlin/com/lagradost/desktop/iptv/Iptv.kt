package com.lagradost.desktop.iptv

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.APIHolder.allProviders
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.CLEARKEY_UUID
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newDrmExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.desktop.runtime.AndroidRuntime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

/**
 * TV playlists (IPTV) without an extension: an M3U playlist (an address or a file) or an Xtream Codes login becomes a source of its own. Its
 * channel groups are rows on Home, search finds channels by name, a channel's page says what is on now and next (from the playlist's XMLTV guide)
 * and Play opens it in the app's player with the headers and ClearKey keys the playlist gives. Kept in the app preferences; added and removed in
 * Settings > Extensions and sources > Stremio add-ons, TV playlists and torrents.
 */
data class IptvPlaylist(
    val id: String,
    val name: String,
    /** An M3U address or file path; for Xtream the server address */
    val url: String,
    val user: String? = null,
    val pass: String? = null,
    val enabled: Boolean = true,
) {
    val xtream: Boolean get() = user != null && pass != null

    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("url", url).put("enabled", enabled).also { o ->
        user?.let { o.put("user", it) }; pass?.let { o.put("pass", it) }
    }

    companion object {
        fun fromJson(o: JSONObject) = IptvPlaylist(
            o.getString("id"), o.optString("name"), o.getString("url"),
            o.optString("user").takeIf { o.has("user") }, o.optString("pass").takeIf { o.has("pass") }, o.optBoolean("enabled", true),
        )
    }
}

/** One channel of a playlist; [licenseKey]: ClearKey "kid:key" (hex or base64), [licenseUrl]: a ClearKey license server */
data class IptvChannel(
    val name: String,
    val url: String,
    val logo: String?,
    val group: String,
    val tvgId: String?,
    val headers: Map<String, String>,
    val licenseKey: String? = null,
    val licenseUrl: String? = null,
) {
    fun toJson(): String = JSONObject().put("name", name).put("url", url).put("logo", logo ?: "").put("group", group).put("tvg", tvgId ?: "")
        .put("headers", JSONObject(headers)).put("key", licenseKey ?: "").put("license", licenseUrl ?: "").toString()

    companion object {
        fun fromJson(s: String): IptvChannel = JSONObject(s).let { o ->
            val h = o.optJSONObject("headers")
            IptvChannel(
                o.getString("name"), o.getString("url"), o.optString("logo").ifBlank { null }, o.optString("group"), o.optString("tvg").ifBlank { null },
                h?.keys()?.asSequence()?.associateWith { h.getString(it) }.orEmpty(), o.optString("key").ifBlank { null }, o.optString("license").ifBlank { null },
            )
        }
    }
}

object IptvPlaylists {
    private const val TAG = "Iptv"
    private const val PREF = "desktop_iptv_playlists"
    /** Playlist servers often answer only IPTV players */
    const val USER_AGENT = "VLC/3.0.21 LibVLC/3.0.21"

    val playlists = mutableStateListOf<IptvPlaylist>()
    @Volatile private var loaded = false
    private val lock = Mutex()

    private fun prefs() = androidx.preference.PreferenceManager.getDefaultSharedPreferences(AndroidRuntime.context)

    fun load() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            runCatching {
                val a = JSONArray(prefs().getString(PREF, "[]"))
                playlists.addAll((0 until a.length()).map { IptvPlaylist.fromJson(a.getJSONObject(it)) })
            }.onFailure { Log.w(TAG, "playlists not read: ${it.message}") }
            loaded = true
        }
    }

    private fun save() {
        prefs().edit().putString(PREF, JSONArray(playlists.map { it.toJson() }).toString()).apply()
    }

    fun find(id: String): IptvPlaylist? = playlists.firstOrNull { it.id == id }

    /**
     * Adds what was pasted: an M3U address or file, or an Xtream link (`http://host:port/get.php?username=..&password=..`, the form IPTV services
     * hand out). The channels are read once first, so a wrong address says so at once. Returns the playlist and its channel count.
     */
    suspend fun add(input: String, wantedName: String? = null): Result<Pair<IptvPlaylist, Int>> {
        load()
        val text = input.trim()
        if (text.isBlank()) return Result.failure(IllegalArgumentException("Paste a playlist address"))
        val xtream = Regex("""^(https?://[^/?#]+)/(?:get|player_api)\.php\?(.*)$""", RegexOption.IGNORE_CASE).find(text)?.let { m ->
            val q = m.groupValues[2].split('&').associate { it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('=', ""), "UTF-8") }
            val u = q["username"]; val p = q["password"]
            if (u.isNullOrBlank() || p.isNullOrBlank()) null else Triple(m.groupValues[1], u, p)
        }
        val url = xtream?.first ?: text
        if (playlists.any { it.url == url && it.user == xtream?.second }) return Result.failure(IllegalStateException("This playlist is already added"))
        val host = runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull() ?: java.io.File(url).nameWithoutExtension
        var name = wantedName?.trim()?.ifBlank { null } ?: host ?: "TV playlist"
        var n = 2
        val base = name
        while (playlists.any { it.name == name } || apis.any { it.name == name }) name = "$base ($n)".also { n++ }
        val p = IptvPlaylist(java.util.UUID.randomUUID().toString().take(8), name, url, xtream?.second, xtream?.third)
        val channels = runCatching { Channels.fetch(p) }.getOrElse { return Result.failure(IllegalStateException("That address did not give a playlist: ${it.message}")) }
        if (channels.isEmpty()) return Result.failure(IllegalStateException("That playlist has no channels"))
        playlists.add(p)
        save()
        syncProviders()
        return Result.success(p to channels.size)
    }

    fun remove(id: String) {
        playlists.removeAll { it.id == id }
        Channels.forget(id)
        save()
        ioSafe { syncProviders() }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val i = playlists.indexOfFirst { it.id == id }
        if (i < 0) return
        playlists[i] = playlists[i].copy(enabled = enabled)
        save()
        ioSafe { syncProviders() }
    }

    /** The playlists that are on, as providers in the engine (again after every change and every load of the extensions) */
    suspend fun syncProviders() {
        load()
        lock.withLock {
            allProviders.withLock {
                try {
                    val wanted = playlists.filter { it.enabled }
                    for (p in wanted) {
                        val existing = allProviders.firstOrNull { it is IptvApi && it.playlistId == p.id }
                        if (existing != null && existing.name == p.name) continue
                        if (existing != null) allProviders.remove(existing)
                        allProviders.add(IptvApi(p.id, p.name))
                    }
                    val keep = wanted.map { it.id }.toSet()
                    allProviders.filter { it is IptvApi && it.playlistId !in keep }.forEach { allProviders.remove(it) }
                    apis = allProviders.distinctBy { it.lang + it.name + it.mainUrl + it::class.qualifiedName }
                    APIHolder.apiMap = null
                } catch (t: Throwable) {
                    Log.w(TAG, "providers not updated: ${t.message}")
                }
            }
        }
    }

    fun start() = ioSafe { syncProviders() }
}

/** The channels of each playlist (read once per run, again after 6 hours) and their guides */
internal object Channels {
    private const val TTL = 6 * 3_600_000L
    private class Cached(val at: Long, val channels: List<IptvChannel>, val guides: List<String>)
    private val cache = ConcurrentHashMap<String, Cached>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    fun forget(id: String) { cache.remove(id); Guide.forget(id) }

    suspend fun of(p: IptvPlaylist): List<IptvChannel> = cached(p).channels
    suspend fun guides(p: IptvPlaylist): List<String> = cached(p).guides

    private suspend fun cached(p: IptvPlaylist): Cached = locks.getOrPut(p.id) { Mutex() }.withLock {
        cache[p.id]?.takeIf { System.currentTimeMillis() - it.at < TTL }?.let { return it }
        val read = if (p.xtream) xtream(p) else m3u(p)
        Cached(System.currentTimeMillis(), read.first, read.second).also { cache[p.id] = it }
    }

    /** Reads the channels (the check when a playlist is added); kept, so Home does not read them again */
    suspend fun fetch(p: IptvPlaylist): List<IptvChannel> {
        val read = if (p.xtream) xtream(p) else m3u(p)
        cache[p.id] = Cached(System.currentTimeMillis(), read.first, read.second)
        return read.first
    }

    private suspend fun text(address: String): String {
        val bytes = if (address.startsWith("http", true)) {
            app.get(address, headers = mapOf("User-Agent" to IptvPlaylists.USER_AGENT), timeout = 60).okhttpResponse.body.bytes()
        } else java.io.File(if (address.startsWith("file:", true)) java.net.URI(address).path else address).readBytes()
        val plain = if (bytes.size > 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) GZIPInputStream(ByteArrayInputStream(bytes)).readBytes() else bytes
        return String(plain, Charsets.UTF_8).removePrefix("﻿")
    }

    private suspend fun m3u(p: IptvPlaylist): Pair<List<IptvChannel>, List<String>> = parseM3u(text(p.url))

    private val attr = Regex("""([\w-]+)="([^"]*)"""")

    internal fun parseM3u(body: String): Pair<List<IptvChannel>, List<String>> {
        val out = ArrayList<IptvChannel>()
        val guides = ArrayList<String>()
        var info: String? = null
        val headers = LinkedHashMap<String, String>()
        var key: String? = null
        var licenseUrl: String? = null
        fun reset() { info = null; headers.clear(); key = null; licenseUrl = null }
        for (raw in body.lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> {}
                line.startsWith("#EXTM3U", true) -> attr.findAll(line).filter { it.groupValues[1].lowercase() in setOf("url-tvg", "x-tvg-url", "tvg-url") }
                    .forEach { m -> guides += m.groupValues[2].split(',').map { it.trim() }.filter { it.startsWith("http") } }
                line.startsWith("#EXTINF", true) -> { reset(); info = line }
                line.startsWith("#EXTVLCOPT", true) -> {
                    val (k, v) = line.substringAfter(':').let { it.substringBefore('=').lowercase() to it.substringAfter('=', "") }
                    when (k) {
                        "http-user-agent" -> headers["User-Agent"] = v
                        "http-referrer", "http-referer" -> headers["Referer"] = v
                        "http-origin" -> headers["Origin"] = v
                        "http-cookie" -> headers["Cookie"] = v
                    }
                }
                line.startsWith("#EXTHTTP", true) -> runCatching {
                    val o = JSONObject(line.substringAfter(':'))
                    for (k in o.keys()) headers[if (k.equals("cookie", true)) "Cookie" else if (k.equals("user-agent", true)) "User-Agent" else k] = o.getString(k)
                }
                line.startsWith("#KODIPROP", true) -> {
                    val (k, v) = line.substringAfter(':').let { it.substringBefore('=').lowercase() to it.substringAfter('=', "") }
                    if (k == "inputstream.adaptive.license_key" && v.isNotBlank()) {
                        if (v.startsWith("http", true)) licenseUrl = v.substringBefore('|') else key = v
                    }
                }
                line.startsWith("#") -> {}
                else -> {
                    val i = info
                    if (i != null) {
                        // the name follows the first comma after the attributes (a name may hold commas, an attribute value too)
                        val comma = i.indexOf(',', (i.lastIndexOf('"') + 1).coerceAtLeast(0)).let { if (it < 0) i.indexOf(',') else it }
                        val a = attr.findAll(if (comma < 0) i else i.substring(0, comma)).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                        val title = if (comma < 0) "" else i.substring(comma + 1).trim()
                        // "url|User-Agent=..&Referer=.." (the form Kodi and many players read)
                        val url = line.substringBefore('|')
                        if ('|' in line) line.substringAfter('|').split('&').forEach { kv ->
                            val k = kv.substringBefore('='); val v = java.net.URLDecoder.decode(kv.substringAfter('=', ""), "UTF-8")
                            if (k.isNotBlank() && v.isNotBlank()) headers[if (k.equals("user-agent", true)) "User-Agent" else if (k.equals("referer", true) || k.equals("referrer", true)) "Referer" else k] = v
                        }
                        val name = title.ifBlank { a["tvg-name"].orEmpty() }.ifBlank { "Channel ${out.size + 1}" }
                        out += IptvChannel(name, url, a["tvg-logo"]?.ifBlank { null }, a["group-title"]?.ifBlank { null } ?: "Channels", a["tvg-id"]?.ifBlank { null }, headers.toMap(), key, licenseUrl)
                    }
                    reset()
                }
            }
        }
        return out to guides.distinct()
    }

    /** Xtream Codes: the live categories and streams from player_api.php; the guide is its xmltv.php */
    private suspend fun xtream(p: IptvPlaylist): Pair<List<IptvChannel>, List<String>> {
        val base = p.url.trimEnd('/')
        val auth = "username=${java.net.URLEncoder.encode(p.user!!, "UTF-8")}&password=${java.net.URLEncoder.encode(p.pass!!, "UTF-8")}"
        val ua = mapOf("User-Agent" to IptvPlaylists.USER_AGENT)
        val cats = runCatching { JSONArray(app.get("$base/player_api.php?$auth&action=get_live_categories", headers = ua, timeout = 60).text) }.getOrNull()
        val catName = cats?.let { a -> (0 until a.length()).associate { a.getJSONObject(it).optString("category_id") to a.getJSONObject(it).optString("category_name") } }.orEmpty()
        val body = app.get("$base/player_api.php?$auth&action=get_live_streams", headers = ua, timeout = 90).text
        if (!body.trimStart().startsWith("[")) throw IllegalStateException("the login was refused")
        val streams = JSONArray(body)
        val user = java.net.URLEncoder.encode(p.user!!, "UTF-8"); val pass = java.net.URLEncoder.encode(p.pass!!, "UTF-8")
        val list = (0 until streams.length()).map { streams.getJSONObject(it) }.map { o ->
            IptvChannel(
                o.optString("name").trim().ifBlank { "Channel" }, "$base/live/$user/$pass/${o.optString("stream_id")}.m3u8",
                o.optString("stream_icon").takeIf { it.startsWith("http") }, catName[o.optString("category_id")]?.ifBlank { null } ?: "Channels",
                o.optString("epg_channel_id").ifBlank { null }, ua,
            )
        }
        return list to listOf("$base/xmltv.php?$auth")
    }
}

/** What is on: the XMLTV guides of a playlist, read when a channel's page is first opened (not for Home), from 2 hours ago to a day ahead */
internal object Guide {
    class Show(val start: Long, val stop: Long, val title: String, val desc: String?)
    private class Loaded(val at: Long, val byId: Map<String, List<Show>>, val idByName: Map<String, String>)
    private val cache = ConcurrentHashMap<String, Loaded>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    fun forget(id: String) { cache.remove(id) }

    private fun norm(s: String) = s.lowercase().replace(Regex("""\b(hd|fhd|uhd|4k|sd|hevc)\b"""), "").filter { it.isLetterOrDigit() }

    suspend fun shows(p: IptvPlaylist, c: IptvChannel): List<Show> {
        val g = locks.getOrPut(p.id) { Mutex() }.withLock {
            cache[p.id]?.takeIf { System.currentTimeMillis() - it.at < 6 * 3_600_000L } ?: read(p).also { cache[p.id] = it }
        }
        val id = c.tvgId?.takeIf { g.byId.containsKey(it) } ?: g.idByName[norm(c.name)] ?: return emptyList()
        return g.byId[id].orEmpty()
    }

    private suspend fun read(p: IptvPlaylist): Loaded {
        val byId = HashMap<String, MutableList<Show>>()
        val idByName = HashMap<String, String>()
        val from = System.currentTimeMillis() - 2 * 3_600_000L
        val to = System.currentTimeMillis() + 24 * 3_600_000L
        for (url in Channels.guides(p).take(3)) runCatching {
            val bytes = app.get(url, headers = mapOf("User-Agent" to IptvPlaylists.USER_AGENT), timeout = 90).okhttpResponse.body.bytes()
            val input = if (bytes.size > 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) GZIPInputStream(ByteArrayInputStream(bytes)) else ByteArrayInputStream(bytes)
            val f = javax.xml.stream.XMLInputFactory.newFactory().apply {
                setProperty(javax.xml.stream.XMLInputFactory.SUPPORT_DTD, false)
                setProperty(javax.xml.stream.XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
            }
            val r = f.createXMLStreamReader(input)
            var channelId: String? = null
            var start = 0L; var stop = 0L; var title: String? = null; var desc: String? = null
            var inProgramme = false
            while (r.hasNext()) {
                when (r.next()) {
                    javax.xml.stream.XMLStreamConstants.START_ELEMENT -> when (r.localName) {
                        "channel" -> channelId = r.getAttributeValue(null, "id")
                        "display-name" -> if (!inProgramme) channelId?.let { id -> runCatching { r.elementText }.getOrNull()?.let { idByName.putIfAbsent(norm(it), id) } }
                        "programme" -> {
                            inProgramme = true
                            channelId = r.getAttributeValue(null, "channel")
                            start = time(r.getAttributeValue(null, "start")); stop = time(r.getAttributeValue(null, "stop"))
                            title = null; desc = null
                        }
                        "title" -> if (inProgramme && title == null) title = runCatching { r.elementText }.getOrNull()
                        "desc" -> if (inProgramme && desc == null) desc = runCatching { r.elementText }.getOrNull()
                    }
                    javax.xml.stream.XMLStreamConstants.END_ELEMENT -> if (r.localName == "programme") {
                        inProgramme = false
                        val id = channelId
                        if (id != null && title != null && stop > from && start < to) byId.getOrPut(id) { ArrayList() } += Show(start, stop, title!!, desc)
                    }
                }
            }
            r.close()
        }
        byId.values.forEach { it.sortBy { s -> s.start } }
        return Loaded(System.currentTimeMillis(), byId, idByName)
    }

    /** "20261029190000 +0100" -> epoch ms */
    private fun time(s: String?): Long = runCatching {
        val t = s!!.trim()
        val local = java.time.LocalDateTime.parse(t.take(14), java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        val zone = t.drop(14).trim().takeIf { it.isNotEmpty() }?.let { java.time.ZoneOffset.of(it.take(3) + ":" + it.drop(3).take(2)) } ?: java.time.ZoneOffset.UTC
        local.toInstant(zone).toEpochMilli()
    }.getOrDefault(0L)
}

/** One playlist as a provider of the app */
class IptvApi(val playlistId: String, displayName: String) : MainAPI() {
    override var name: String = displayName
    override var mainUrl: String = "iptv://$playlistId"
    override var lang: String = "en"
    override var canBeOverridden: Boolean = false
    override val hasMainPage: Boolean = true
    override val hasQuickSearch: Boolean = false
    override val hasDownloadSupport: Boolean = false
    override val supportedTypes: Set<TvType> = setOf(TvType.Live)
    override val mainPage: List<MainPageData> = listOf(MainPageData("Channels", "all", false))

    private fun playlist() = IptvPlaylists.find(playlistId) ?: throw com.lagradost.cloudstream3.ErrorLoadingException("This playlist was removed")

    private fun card(c: IptvChannel): SearchResponse = newLiveSearchResponse(c.name, c.url, TvType.Live, false) { posterUrl = c.logo }

    /** Every group of the playlist is a row (in the playlist's order); Customise Home hides or moves them like any rows */
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        if (page > 1) return null
        val groups = Channels.of(playlist()).groupBy { it.group }
        return newHomePageResponse(groups.map { (g, list) -> HomePageList(g, list.map { card(it) }, isHorizontalImages = true) }, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return Channels.of(playlist()).filter { it.name.lowercase().contains(q) || it.group.lowercase().contains(q) }.take(200).map { card(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val p = playlist()
        val c = Channels.of(p).firstOrNull { it.url == url } ?: IptvChannel(url.substringAfterLast('/').substringBefore('?'), url, null, "", null, emptyMap())
        val now = System.currentTimeMillis()
        val shows = runCatching { Guide.shows(p, c) }.getOrDefault(emptyList()).filter { it.stop > now }
        val clock = java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.systemDefault())
        fun at(ms: Long) = clock.format(java.time.Instant.ofEpochMilli(ms))
        val plot = shows.take(4).mapIndexed { i, s ->
            (if (i == 0 && s.start <= now) "Now" else at(s.start)) + " · " + s.title + (if (i == 0 && s.start <= now) " (until ${at(s.stop)})" else "") +
                (if (i == 0) s.desc?.takeIf { it.isNotBlank() }?.let { "\n" + it.take(300) }.orEmpty() else "")
        }.joinToString("\n").ifBlank { null }
        return newLiveStreamLoadResponse(c.name, c.url, c.toJson()) {
            posterUrl = c.logo
            this.plot = plot ?: c.group.takeIf { it.isNotBlank() }
            tags = listOfNotNull(c.group.takeIf { it.isNotBlank() })
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val c = runCatching { IptvChannel.fromJson(data) }.getOrNull() ?: return false
        val lower = c.url.lowercase().substringBefore('?')
        val type = when {
            lower.endsWith(".mpd") || "/dash/" in lower || "format=mpd" in c.url -> ExtractorLinkType.DASH
            lower.endsWith(".m3u8") || ".m3u8" in lower || "/hls/" in lower -> ExtractorLinkType.M3U8
            else -> ExtractorLinkType.VIDEO
        }
        val referer = c.headers.entries.firstOrNull { it.key.equals("Referer", true) }?.value.orEmpty()
        val keyParts = c.licenseKey?.takeIf { ':' in it && !it.trimStart().startsWith("{") }?.split(':')
        if (keyParts != null || c.licenseUrl != null) {
            callback(newDrmExtractorLink(name, c.name, c.url, type, CLEARKEY_UUID) {
                this.referer = referer; headers = c.headers
                if (keyParts != null) { kid = keyParts[0].trim(); key = keyParts[1].trim() }
                licenseUrl = c.licenseUrl
            })
        } else {
            callback(newExtractorLink(name, c.name, c.url, type) { this.referer = referer; headers = c.headers })
        }
        return true
    }
}
