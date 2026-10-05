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
    private val keyCache = ConcurrentHashMap<String, String>()

    // wrapped streams (by id) with a media playlist that has no end: live
    private val liveIds = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    /** Whether a [wrap] address plays a live playlist (known once a media playlist was read) */
    fun isLive(address: String): Boolean = address.substringAfter("h=", "").substringBefore("&").let { it.isNotEmpty() && it in liveIds }

    private val server: HttpServer by lazy {
        HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 32).apply {
            executor = Executors.newCachedThreadPool { r -> Thread(r, "HlsProxy").apply { isDaemon = true } }
            createContext("/pl") { ex -> runCatching { playlist(ex) }.onFailure { fail(ex, it) } }
            start()
        }
    }

    /** Address to give mpv instead of [url] */
    fun wrap(url: String, headers: Map<String, String>): String {
        val id = Integer.toHexString(url.hashCode()) + "-" + System.nanoTime().toString(16)
        headersById[id] = headers
        return playlistAddress(url, id)
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
    private fun getFull(url: String, headers: Map<String, String>): Triple<Int, ByteArray, String> {
        val builder = Request.Builder().url(url.toHttpUrlOrNull() ?: toUri(url).toString().toHttpUrl())
        var userAgent = false
        for ((k, v) in headers) {
            // the client sets these itself
            if (k.equals("Host", true) || k.equals("Content-Length", true) || k.equals("Connection", true)) continue
            if (k.equals("User-Agent", true)) userAgent = true
            runCatching { builder.header(k, v) }
        }
        if (!userAgent) builder.header("User-Agent", com.lagradost.cloudstream3.USER_AGENT)
        val client = com.lagradost.cloudstream3.app.baseClient.newBuilder().callTimeout(20, java.util.concurrent.TimeUnit.SECONDS).build()
        client.newCall(builder.build()).execute().use { r -> return Triple(r.code, r.body.bytes(), r.request.url.toString()) }
    }

    private class Fetched(val status: Int, val body: ByteArray, val final: Boolean)
    private class CacheEntry(val result: java.util.concurrent.CompletableFuture<Fetched>, val at: Long) {
        /** The player has asked for it already: a playlist that can still change (live) is fetched again next time */
        @Volatile
        var consumed = false
    }

    // playlists that are fetched (and repaired) already or are on their way; the player asks for them one after the other,
    // which with a slow wrapper service meant seconds of waiting for each of the variant and audio playlists
    private val cache = ConcurrentHashMap<String, CacheEntry>()
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
                val (code, bytes, finalUrl) = getFull(url, headers)
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
        val embedded = embeddedAddress(playlistUrl)
        val uriAttribute = Regex("""URI="([^"]*)"""")
        val out = StringBuilder(text.length + 256)
        var nextIsVariant = false
        // what the player will ask for next, in the order it is needed: video variants, audio, subtitles
        val variants = ArrayList<String>()
        val audio = ArrayList<String>()
        val subtitles = ArrayList<String>()
        for (raw in text.lineSequence()) {
            val line = raw.trimEnd('\r')
            when {
                line.isBlank() -> out.append(line)
                line.startsWith("#") -> {
                    if (line.startsWith("#EXT-X-STREAM-INF")) nextIsVariant = true
                    out.append(
                        when {
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
                    out.append(if (master && nextIsVariant) playlistAddress(absolute, id) else absolute)
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
