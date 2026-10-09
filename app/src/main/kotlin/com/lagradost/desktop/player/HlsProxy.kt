package com.lagradost.desktop.player

import android.util.Log
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Loopback server that repairs HLS playlists some sources hand out, used when playing the plain URL failed.
 *
 *  - Playlists are fetched with the source's headers (referer, user agent) and every relative address is made
 *    absolute, because mpv reads them from this server and could not resolve them any more.
 *  - Some sources serve playlists through a wrapper service (`.../parse?url=<real playlist>`) while the AES key
 *    named in the playlist (`URI="key.bin"`) lives next to the real playlist; a relative key address is tried
 *    against the playlist address first and then against that embedded address, and the first one that answers
 *    is written into the playlist. Without it the segments cannot be decrypted and look like pictures.
 *
 * Media segments are not touched: mpv fetches them directly.
 */
object HlsProxy {
    private const val TAG = "HlsProxy"

    private val headersById = ConcurrentHashMap<String, Map<String, String>>()
    // the extension's video interceptor of a wrapped stream (getVideoInterceptor): applied to its playlists, keys and segments, which then all come through here
    private val interceptorsById = ConcurrentHashMap<String, okhttp3.Interceptor>()

    /** A subtitle rendition of a master playlist that is one whole file (WebVTT/SRT), handed to the player as a subtitle file instead */
    data class StreamSubtitle(val url: String, val name: String, val language: String?)
    private val streamSubtitlesById = ConcurrentHashMap<String, List<StreamSubtitle>>()

    /** The whole-file subtitles taken out of the master playlist that [address] (a [wrap] address) serves */
    fun streamSubtitles(address: String): List<StreamSubtitle> =
        address.substringAfter("h=", "").substringBefore("&").takeIf { it.isNotEmpty() }?.let { streamSubtitlesById[it] } ?: emptyList()

    /** A variant (quality) of a master playlist, in the order of the master: mpv's HLS "editions" have the same order */
    data class Variant(val width: Int?, val height: Int?, val bandwidth: Long)
    private val variantsById = ConcurrentHashMap<String, List<Variant>>()

    /** The variants of the master playlist that [address] (a [wrap] address) serves */
    fun variants(address: String): List<Variant> =
        address.substringAfter("h=", "").substringBefore("&").takeIf { it.isNotEmpty() }?.let { variantsById[it] } ?: emptyList()

    private class Prefetched(val result: java.util.concurrent.CompletableFuture<Pair<Int, ByteArray>>, val at: Long)
    private val firstSegments = ConcurrentHashMap<String, Prefetched>()

    private class Fetched(val status: Int, val body: ByteArray, val final: Boolean)
    private class CacheEntry(val result: java.util.concurrent.CompletableFuture<Fetched>, val at: Long) {
        /** The player has asked for it already: a playlist that can still change (live) is fetched again next time */
        @Volatile
        var consumed = false
    }
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val activeCalls = ConcurrentHashMap<String, java.util.concurrent.CopyOnWriteArrayList<okhttp3.Call>>()

    /** Cancels any in-flight prefetch futures, active network calls and drops registered headers for [address] */
    fun cancel(address: String?) {
        if (address == null) return
        val id = address.substringAfter("h=", "").substringBefore("&")
        if (id.isNotEmpty()) {
            headersById.remove(id)
            interceptorsById.remove(id)
            streamSubtitlesById.remove(id)
            variantsById.remove(id)
            activeCalls.remove(id)?.forEach { runCatching { it.cancel() } }
            val it = firstSegments.entries.iterator()
            while (it.hasNext()) {
                val entry = it.next()
                if (entry.key.startsWith("$id|")) {
                    runCatching { entry.value.result.cancel(true) }
                    it.remove()
                }
            }
            val cIt = cache.entries.iterator()
            while (cIt.hasNext()) {
                val entry = cIt.next()
                if (entry.key.startsWith("$id|")) {
                    runCatching { entry.value.result.cancel(true) }
                    cIt.remove()
                }
            }
        }
    }

    /** Cancels all active prefetch futures, active network calls and clears proxy tables */
    fun cancelAll() {
        headersById.clear()
        interceptorsById.clear()
        streamSubtitlesById.clear()
        variantsById.clear()
        for ((_, calls) in activeCalls) {
            for (call in calls) {
                runCatching { call.cancel() }
            }
        }
        activeCalls.clear()
        for ((_, p) in firstSegments) {
            runCatching { p.result.cancel(true) }
        }
        firstSegments.clear()
        for ((_, c) in cache) {
            runCatching { c.result.cancel(true) }
        }
        cache.clear()
    }

    /**
     * First segments of the variants and renditions of a recorded stream, fetched all at once while the playlists are read: ffmpeg opens
     * the first segment of every one of them one after the other before it plays (a master with 3 qualities and 21 audio languages took
     * ~6 s). Each is handed out once, then dropped.
     */
    private fun prefetchSegment(url: String, id: String, headers: Map<String, String>): String {
        val now = System.currentTimeMillis()
        firstSegments.entries.removeIf { now - it.value.at > 120_000 }
        val key = "$id|$url"
        firstSegments.computeIfAbsent(key) {
            Prefetched(java.util.concurrent.CompletableFuture.supplyAsync({ getFull(url, headers, interceptorsById[id], id).let { it.first to it.second } }, sidePool), now)
        }
        return "http://127.0.0.1:${server.address.port}/seg?h=$id&u=${URLEncoder.encode(url, "UTF-8")}"
    }

    private fun firstSegment(ex: HttpExchange) {
        val url = query(ex, "u") ?: return fail(ex, IllegalArgumentException("no address"))
        val id = query(ex, "h") ?: ""
        val ready = firstSegments.remove("$id|$url")?.let { runCatching { it.result.get(30, java.util.concurrent.TimeUnit.SECONDS) }.getOrNull() }
        // asked for again later (a seek back to the start), or the early fetch failed: fetched now
        val (code, fetched) = ready?.takeIf { it.first in 200..299 } ?: getFull(url, headersById[id] ?: emptyMap(), interceptorsById[id], id).let { it.first to it.second }
        if (code !in 200..299) { ex.sendResponseHeaders(code, -1); ex.close(); return }
        val body = withoutDisguise(fetched)
        ex.sendResponseHeaders(200, body.size.toLong())
        ex.responseBody.use { it.write(body) }
    }

    /** Segment addresses that pretend to be something else (`seg-0.webp`, `chunk-1.js`): the video behind a fake file header */
    private val DISGUISED = Regex("""\.(png|jpe?g|webp|gif|bmp|ico|svg|avif|css|js|html?|txt|json|woff2?|ttf|xml)$""", RegexOption.IGNORE_CASE)

    /**
     * A segment with a fake file header in front (an image or a script, so that the CDN serves it as one): the bytes from the start of
     * the real MPEG-TS (five packets in a row) or MP4 box on. ExoPlayer finds the sync bytes by itself; ffmpeg reads the header,
     * takes the segment for a picture and fails ("png: chunk too big"). A segment that already starts right is returned as it is.
     */
    internal fun withoutDisguise(body: ByteArray): ByteArray {
        fun ts(at: Int) = (0 until 5).all { i -> at + i * 188 < body.size && body[at + i * 188] == 0x47.toByte() }
        if (body.isEmpty() || ts(0)) return body
        fun box(at: Int) = at + 8 <= body.size && MP4_BOXES.any { t -> (0 until 4).all { i -> body[at + 4 + i] == t[i].code.toByte() } }
        if (box(0) || (body.size >= 3 && body[0] == 'I'.code.toByte() && body[1] == 'D'.code.toByte() && body[2] == '3'.code.toByte())) return body
        val limit = minOf(body.size - 1, 256 * 1024)
        for (at in 1..limit) {
            if ((body[at] == 0x47.toByte() && ts(at)) || box(at)) {
                Log.i(TAG, "segment behind a fake $at-byte header: cut off")
                return body.copyOfRange(at, body.size)
            }
        }
        return body
    }

    private val MP4_BOXES = listOf("ftyp", "styp", "moof", "sidx")

    private val sidePool = java.util.concurrent.Executors.newCachedThreadPool { r -> Thread(r, "HlsProxy-side").apply { isDaemon = true } }

    /**
     * The single file of a subtitle playlist that lists exactly one (`#EXTINF:99999` over the whole film), or null. ffmpeg's HLS reader
     * never delivered the cues of such a playlist (CNCVerse's Netflix / Prime Video / Hotstar); as a plain subtitle file it works.
     */
    private fun singleSubtitleFile(url: String, headers: Map<String, String>): String? = runCatching {
        val (code, bytes, finalUrl) = getFull(url, headers)
        if (code !in 200..299) return null
        val text = String(bytes, Charsets.UTF_8)
        if (!text.contains("#EXT-X-ENDLIST")) return null
        val segments = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
        segments.singleOrNull()?.let { resolve(finalUrl, it) }
    }.getOrNull()
    private val keyCache = ConcurrentHashMap<String, String>()

    // wrapped streams (by id) with a media playlist that has no end: live
    private val liveIds = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    /** Whether a [wrap] address plays a live playlist (known once a media playlist was read) */
    fun isLive(address: String): Boolean = address.substringAfter("h=", "").substringBefore("&").let { it.isNotEmpty() && it in liveIds }

    private val server: HttpServer by lazy {
        HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 32).apply {
            executor = Executors.newCachedThreadPool { r -> Thread(r, "HlsProxy").apply { isDaemon = true } }
            createContext("/pl") { ex -> runCatching { playlist(ex) }.onFailure { fail(ex, it) } }
            createContext("/gen") { ex -> runCatching { generated(ex) }.onFailure { fail(ex, it) } }
            createContext("/seg") { ex -> runCatching { firstSegment(ex) }.onFailure { fail(ex, it) } }
            start()
        }
    }

    /** Address to give mpv instead of [url] */
    fun wrap(url: String, headers: Map<String, String>, interceptor: okhttp3.Interceptor? = null): String {
        val id = Integer.toHexString(url.hashCode()) + "-" + System.nanoTime().toString(16)
        headersById[id] = headers
        if (interceptor != null) interceptorsById[id] = interceptor
        return playlistAddress(url, id)
    }

    // playlists made by the app (OnDemandDash: DASH segment templates as HLS), by id; the newest few are kept
    private val generatedById = java.util.Collections.synchronizedMap(object : LinkedHashMap<String, String>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 16
    })

    /** Serves [playlist] (an HLS playlist with absolute addresses) at a loopback address for mpv */
    fun serve(playlist: String): String {
        val id = java.util.UUID.randomUUID().toString().take(12)
        generatedById[id] = playlist
        return "http://127.0.0.1:${server.address.port}/gen/$id.m3u8"
    }

    private fun generated(ex: HttpExchange) {
        val body = generatedById[ex.requestURI.path.substringAfterLast('/').removeSuffix(".m3u8")]?.toByteArray()
        if (body == null) { ex.sendResponseHeaders(404, -1); ex.close(); return }
        ex.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
        ex.sendResponseHeaders(200, body.size.toLong())
        ex.responseBody.use { it.write(body) }
    }

    private fun playlistAddress(url: String, id: String) =
        "http://127.0.0.1:${server.address.port}/pl?h=$id&u=${URLEncoder.encode(url, "UTF-8")}"

    private fun fail(ex: HttpExchange, t: Throwable) {
        Log.w(TAG, "playlist request failed: ${t.message}")
        runCatching { ex.sendResponseHeaders(502, -1) }
        ex.close()
    }

    private fun query(ex: HttpExchange, name: String): String? =
        ex.requestURI.rawQuery?.split('&')?.firstNotNullOfOrNull {
            val i = it.indexOf('=')
            if (i > 0 && it.substring(0, i) == name) URLDecoder.decode(it.substring(i + 1), "UTF-8") else null
        }

    /**
     * GET with the app's own HTTP client (the one the extensions and, on Android, ExoPlayer use: DNS setting incl. DNS over HTTPS,
     * TLS provider, redirects), the headers of the link and Android's default user agent. Status and body.
     */
    private fun get(url: String, headers: Map<String, String>): Pair<Int, ByteArray> = getFull(url, headers).let { it.first to it.second }

    /** [get] and the address the answer came from after the redirects: relative addresses in a playlist are meant against that one */
    private fun getFull(url: String, headers: Map<String, String>, interceptor: okhttp3.Interceptor? = null, id: String? = null): Triple<Int, ByteArray, String> {
        val builder = Request.Builder().url(url.toHttpUrlOrNull() ?: toUri(url).toString().toHttpUrl())
        var userAgent = false
        for ((k, v) in headers) {
            // the client sets these itself
            if (k.equals("Host", true) || k.equals("Content-Length", true) || k.equals("Connection", true) || k.equals("Accept-Encoding", true)) continue
            if (k.equals("User-Agent", true)) userAgent = true
            com.lagradost.desktop.net.RawHeaders.set(builder, k, v)
        }
        if (!userAgent) builder.header("User-Agent", com.lagradost.cloudstream3.USER_AGENT)
        val client = com.lagradost.cloudstream3.app.baseClient.newBuilder().callTimeout(if (interceptor != null) 60 else 20, java.util.concurrent.TimeUnit.SECONDS).apply { interceptor?.let { addInterceptor(it) } }.build()
        val call = client.newCall(builder.build())
        if (id != null) {
            activeCalls.computeIfAbsent(id) { java.util.concurrent.CopyOnWriteArrayList() }.add(call)
        }
        try {
            call.execute().use { r -> return Triple(r.code, r.body.bytes(), r.request.url.toString()) }
        } finally {
            if (id != null) {
                activeCalls[id]?.remove(call)
            }
        }
    }

    // eight at a time, in the order they were asked for
    private val pool = java.util.concurrent.ThreadPoolExecutor(8, 8, 30, java.util.concurrent.TimeUnit.SECONDS, java.util.concurrent.LinkedBlockingQueue()) { r ->
        Thread(r, "HlsProxy-fetch").apply { isDaemon = true }
    }.apply { allowCoreThreadTimeOut(true) }

    private fun fetch(url: String, id: String, headers: Map<String, String>, forPlayer: Boolean): java.util.concurrent.CompletableFuture<Fetched> {
        if (cache.size > 300) cache.clear()
        val key = "$id|$url"
        val now = System.currentTimeMillis()
        cache[key]?.let { e ->
            val done = e.result.getNow(null)
            val reusable = when {
                !e.result.isDone -> true // on its way
                done == null || done.status !in 200..299 -> false
                done.final -> true // a finished (VOD) playlist never changes
                else -> !e.consumed && now - e.at < 20_000 // fetched ahead of time and not asked for yet
            }
            if (reusable) {
                if (forPlayer) e.consumed = true
                return e.result
            }
        }
        val future = java.util.concurrent.CompletableFuture<Fetched>()
        val entry = CacheEntry(future, now).also { it.consumed = forPlayer }
        cache[key] = entry
        pool.execute {
            try {
                val (code, bytes, finalUrl) = getFull(url, headers, interceptorsById[id], id)
                if (code !in 200..299) {
                    Log.w(TAG, "playlist $code: ${url.take(120)}")
                    future.complete(Fetched(code, ByteArray(0), false))
                    cache.remove(key, entry)
                } else {
                    val text = String(bytes, Charsets.UTF_8)
                    if (finalUrl.substringBefore('?') != url.substringBefore('?')) Log.i(TAG, "playlist redirected: segments come from ${finalUrl.substringBefore('?').take(100)}")
                    val body = rewrite(text, finalUrl, id, headers).toByteArray(Charsets.UTF_8)
                    if (text.contains("#EXTINF") && !text.contains("#EXT-X-ENDLIST")) liveIds.add(id)
                    future.complete(Fetched(200, body, text.contains("#EXT-X-ENDLIST")))
                }
            } catch (t: Throwable) {
                future.completeExceptionally(t)
                cache.remove(key, entry)
            }
        }
        return future
    }

    private fun playlist(ex: HttpExchange) {
        val url = query(ex, "u") ?: return fail(ex, IllegalArgumentException("no address"))
        val id = query(ex, "h") ?: ""
        val headers = headersById[id] ?: emptyMap()
        val started = System.currentTimeMillis()
        val result = fetch(url, id, headers, forPlayer = true).get(30, java.util.concurrent.TimeUnit.SECONDS)
        if (result.status !in 200..299) {
            ex.sendResponseHeaders(result.status, -1)
            ex.close()
            return
        }
        Log.i(TAG, "playlist ${System.currentTimeMillis() - started} ms: ${url.take(100)}")
        val body = result.body
        ex.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
        ex.sendResponseHeaders(200, body.size.toLong())
        ex.responseBody.use { it.write(body) }
    }

    /** Addresses in playlists may hold characters a URI does not allow (`en-us.[CC].m3u8`, spaces): those are percent-encoded */
    private fun toUri(url: String): URI = runCatching { URI.create(url) }.getOrElse {
        val sb = StringBuilder()
        for (ch in url) {
            if (ch.code <= 32 || ch.code > 126 || ch in "[]{}|\\^`\"<>") {
                for (b in ch.toString().toByteArray(Charsets.UTF_8)) sb.append('%').append("%02X".format(b))
            } else sb.append(ch)
        }
        URI.create(sb.toString())
    }

    private fun resolve(base: String, ref: String): String = runCatching { toUri(base).resolve(toUri(ref)).toString() }.getOrDefault(ref)

    /** `https://wrapper/api/parse?url=<real playlist>&...` -> the real playlist address */
    private fun embeddedAddress(playlistUrl: String): String? =
        runCatching {
            URI.create(playlistUrl).rawQuery?.split('&')?.firstNotNullOfOrNull {
                val i = it.indexOf('=')
                if (i > 0 && it.substring(0, i) == "url") URLDecoder.decode(it.substring(i + 1), "UTF-8").takeIf { v -> v.startsWith("http") } else null
            }
        }.getOrNull()

    private fun rewrite(text: String, playlistUrl: String, id: String, headers: Map<String, String>): String {
        val master = text.contains("#EXT-X-STREAM-INF")
        // subtitle renditions that are one whole file: left out of the master, the player adds them as subtitle files (streamSubtitles)
        val wholeFile = HashMap<String, StreamSubtitle>()
        if (master) {
            val attr = { line: String, name: String -> Regex("""$name="([^"]*)"""").find(line)?.groupValues?.get(1) }
            val checks = text.lineSequence().map { it.trim() }.filter { it.startsWith("#EXT-X-MEDIA") && it.contains("TYPE=SUBTITLES") }
                .mapNotNull { line -> attr(line, "URI")?.let { uri -> line to java.util.concurrent.CompletableFuture.supplyAsync({ singleSubtitleFile(resolve(playlistUrl, uri), headers) }, sidePool) } }
                .toList()
            val deadline = System.currentTimeMillis() + 6_000
            for ((line, check) in checks) {
                val file = runCatching { check.get((deadline - System.currentTimeMillis()).coerceAtLeast(1), java.util.concurrent.TimeUnit.MILLISECONDS) }.getOrNull() ?: continue
                // the language as a plain code (Prime Video writes "en-us.[CC]"): the automatic choice compares codes
                val language = attr(line, "LANGUAGE")?.let { Regex("""^[A-Za-z]{2,3}""").find(it.trim())?.value?.lowercase() }
                wholeFile[line] = StreamSubtitle(file, attr(line, "NAME") ?: attr(line, "LANGUAGE") ?: "Subtitles", language)
            }
            if (wholeFile.isNotEmpty()) {
                streamSubtitlesById[id] = wholeFile.values.toList()
                Log.i(TAG, "${wholeFile.size} subtitle playlist(s) of one whole file: added as subtitle files")
            }
        }
        if (master) {
            variantsById[id] = text.lineSequence().filter { it.startsWith("#EXT-X-STREAM-INF") }.map { line ->
                val res = Regex("""RESOLUTION=(\d+)x(\d+)""").find(line)
                Variant(res?.groupValues?.get(1)?.toIntOrNull(), res?.groupValues?.get(2)?.toIntOrNull(), Regex("""[^-]BANDWIDTH=(\d+)""").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L)
            }.toList()
        }
        // the variants name the subtitle group; with all of its renditions left out the reference goes too
        val allSubtitlesOut = wholeFile.isNotEmpty() && text.lineSequence().count { it.startsWith("#EXT-X-MEDIA") && it.contains("TYPE=SUBTITLES") } == wholeFile.size
        val embedded = embeddedAddress(playlistUrl)
        val uriAttribute = Regex("""URI="([^"]*)"""")
        val out = StringBuilder(text.length + 256)
        var nextIsVariant = false
        // a recorded media playlist: its first segment is fetched now (see firstSegments); byte ranges are left alone
        var prefetchFirst = System.getProperty("cloudstream.noprefetch") == null && !master && text.contains("#EXT-X-ENDLIST") && text.contains("#EXTINF") && !text.contains("#EXT-X-BYTERANGE")
        // segments dressed up as pictures or scripts: all of them come through here, without the fake header (see withoutDisguise)
        val disguised = !master && !text.contains("#EXT-X-BYTERANGE") && text.lineSequence().firstOrNull { it.isNotBlank() && !it.startsWith("#") }
            ?.let { DISGUISED.containsMatchIn(it.trim().substringBefore('?').substringBefore('#')) } == true
        if (disguised) Log.i(TAG, "segments are disguised as other files: served through the proxy")
        // with the extension's interceptor every address goes through it (it decodes segments, answers key requests)
        val intercepted = interceptorsById.containsKey(id)
        val viaProxy = { absolute: String -> "http://127.0.0.1:${server.address.port}/seg?h=$id&u=${URLEncoder.encode(absolute, "UTF-8")}" }
        // what the player will ask for next, in the order it is needed: video variants, audio, subtitles
        val variants = ArrayList<String>()
        val audio = ArrayList<String>()
        val subtitles = ArrayList<String>()
        for (raw in text.lineSequence()) {
            val line = raw.trimEnd('\r')
            if (line in wholeFile) continue
            when {
                line.isBlank() -> out.append(line)
                line.startsWith("#") -> {
                    if (line.startsWith("#EXT-X-STREAM-INF")) nextIsVariant = true
                    out.append(
                        when {
                            line.startsWith("#EXT-X-STREAM-INF") && allSubtitlesOut -> line.replace(Regex(""",SUBTITLES="[^"]*""""), "")
                            (line.startsWith("#EXT-X-KEY") || line.startsWith("#EXT-X-MAP")) && intercepted ->
                                uriAttribute.replace(line) { m -> if (m.groupValues[1].startsWith("data:", true)) m.value else "URI=\"" + viaProxy(resolve(playlistUrl, m.groupValues[1])) + "\"" }
                            line.startsWith("#EXT-X-KEY") || line.startsWith("#EXT-X-SESSION-KEY") ->
                                uriAttribute.replace(line) { m -> "URI=\"" + keyAddress(m.groupValues[1], playlistUrl, embedded, headers) + "\"" }
                            // renditions (audio, subtitles) are playlists of their own: through here, keys get repaired there
                            line.startsWith("#EXT-X-MEDIA") && master ->
                                uriAttribute.replace(line) { m ->
                                    val absolute = resolve(playlistUrl, m.groupValues[1])
                                    (if (line.contains("TYPE=SUBTITLES") || line.contains("TYPE=CLOSED-CAPTIONS")) subtitles else audio).add(absolute)
                                    "URI=\"" + playlistAddress(absolute, id) + "\""
                                }
                            // every other address in a tag (init segments, parts, hints, reports) must not resolve against this server
                            else -> uriAttribute.replace(line) { m -> "URI=\"" + resolve(playlistUrl, m.groupValues[1]) + "\"" }
                        },
                    )
                }
                else -> {
                    val absolute = resolve(playlistUrl, line.trim())
                    // variants are playlists (through here); media segments stay on their server
                    if (master && nextIsVariant) variants.add(absolute)
                    out.append(
                        when {
                            master && nextIsVariant -> playlistAddress(absolute, id)
                            prefetchFirst -> { prefetchFirst = false; prefetchSegment(absolute, id, headers) }
                            disguised || intercepted -> viaProxy(absolute)
                            else -> absolute
                        },
                    )
                    nextIsVariant = false
                }
            }
            out.append('\n')
        }
        // The player opens every variant and rendition one after the other (a master of a big service lists dozens of
        // subtitle playlists, which took ~10 s). They all load now, at the same time.
        for (url in variants + audio + subtitles) fetch(url, id, headers, forPlayer = false)
        return out.toString()
    }

    /** The address of a key: where the playlist says, or next to the real playlist behind a wrapper service */
    private fun keyAddress(ref: String, playlistUrl: String, embedded: String?, headers: Map<String, String>): String {
        if (ref.isBlank() || ref.startsWith("data:", true)) return ref
        val standard = resolve(playlistUrl, ref)
        if (embedded == null) return standard
        return keyCache.getOrPut(standard + "|" + embedded) {
            val candidates = listOf(standard, resolve(embedded, ref)).distinct()
            candidates.firstOrNull { candidate ->
                runCatching {
                    val (code, bytes) = get(candidate, headers)
                    code == 200 && bytes.size in 16..64
                }.getOrDefault(false)
            } ?: standard
        }
    }
}
