package com.lagradost.desktop.player

import android.util.Log
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Loopback server between the player and a DASH stream. It exists for live channels (JIO TV and other ClearKey DASH channels),
 * which looped the last two seconds after a while.
 *
 * Why they looped: ffmpeg's DASH demuxer works through the segment list of the manifest by position. At the live edge it asks for
 * the segment after the last listed one, which a CDN already has (the manifest is cached and runs one or two segments behind) and the
 * demuxer gets it. It then asks for the next position, which still maps to the end of the same stale list, so it fetches that segment
 * *again*, until the manifest has caught up (4 to 7 times in a row, every copy valid media). The player plays each copy, so the sound
 * repeats the same two seconds while the picture goes on, and the playback speed drops.
 *
 * A related fault: the demuxer works out the numbers of live segments from the clock and asks for the next one before the CDN
 * has published it. ffmpeg skips a segment that answers 404 and later comes back to ones it played already (sound jumps back two
 * seconds, the picture slows down to meet it: "out of sync, slow motion" after a seek or a while of playing). Such a request is held
 * here until the segment exists.
 *
 * The fix for the loop is to never answer the same live segment twice in a row. A repeat is answered `404` (after a short pause) and the demuxer
 * then does what it does for a segment that is not there yet: it reads the manifest again and tries the next position, until the
 * manifest has caught up and the real next segment is asked for.
 *
 * Everything else is passed through unchanged (headers of the link, Range requests, status codes), so on-demand manifests play like
 * before; the manifest and the segments (addressed relative to it) come from this server, fetched with the app's HTTP client.
 */
object DashProxy {
    private const val TAG = "DashProxy"
    private const val RETRIES = 3

    /** How long a request for a live segment that the CDN does not have yet is held (a segment is 2 to 6 s; the player times out after 15 s) */
    private const val PUBLISH_WAIT_MS = 6_000L

    /** A segment of a live stream is told by the long number (time or sequence number) before its extension; the init segment has none */
    private val TIMED = Regex("""\d{6,}(\.[A-Za-z0-9]+)$""")

    private class Entry(val url: String, val headers: Map<String, String>, val name: String) {
        /** The address the relative segment addresses of the manifest are resolved against */
        val baseDir = url.substringBefore('?').substringBeforeLast('/') + "/"

        /** The last segment sent completely, by stream (its address without the number) */
        val last = ConcurrentHashMap<String, String>()

        @Volatile
        var live = false

        @Volatile
        var repeats = 0

        @Volatile
        var waits = 0

        /** The segments the newest manifest lists (live manifests with segment templates) */
        @Volatile
        var timeline: DashTimeline? = null

        /** server clock minus this PC's clock (the manifest response's Date), for templates whose segments follow the clock */
        @Volatile
        var clockOffsetMs = 0L

        @Volatile
        var manifestAt = 0L
        val manifestLock = Any()

        fun serverNow() = System.currentTimeMillis() + clockOffsetMs
    }

    /** A live manifest was read (by the player or by [refreshManifest]): what it lists, and the server's clock */
    private fun learn(entry: Entry, bytes: ByteArray, date: String?) {
        entry.manifestAt = System.currentTimeMillis()
        date?.let { d -> runCatching { java.time.ZonedDateTime.parse(d, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull() }
            ?.let { entry.clockOffsetMs = it - System.currentTimeMillis() }
        val parsed = DashTimeline.parse(String(bytes, Charsets.UTF_8))
        if (parsed != null) {
            if (entry.timeline == null) Log.i(TAG, "live timeline: ${parsed.tracks.size} streams, segments ${parsed.tracks.first().segmentMs} ms")
            entry.timeline = parsed
        }
    }

    /** Reads the manifest again (at most once a second, shared by all waiting requests) */
    private fun refreshManifest(entry: Entry) {
        synchronized(entry.manifestLock) {
            if (System.currentTimeMillis() - entry.manifestAt < 1000) return
            runCatching {
                val builder = Request.Builder().url(entry.url.toHttpUrlOrNull() ?: return)
                for ((k, v) in entry.headers) if (!k.equals("Host", true) && !k.equals("Range", true) && !k.equals("Content-Length", true)) runCatching { builder.header(k, v) }
                if (entry.headers.keys.none { it.equals("User-Agent", true) }) builder.header("User-Agent", com.lagradost.cloudstream3.USER_AGENT)
                builder.cacheControl(okhttp3.CacheControl.FORCE_NETWORK)
                client.newCall(builder.build()).execute().use { r -> if (r.isSuccessful) learn(entry, r.body.bytes(), r.header("Date")) else entry.manifestAt = System.currentTimeMillis() }
            }.onFailure { entry.manifestAt = System.currentTimeMillis() }
        }
    }

    /**
     * The player asks for live segments from its own idea of the clock, often before the CDN has them. Asking the CDN then is
     * worse than waiting: the 404 is cached at the edge for seconds and ffmpeg skips the segment, later jumping back to it. The
     * request is held until the manifest lists the segment (or the template's clock says it exists), up to a few segment lengths.
     */
    private fun waitForListing(entry: Entry, trackId: String, value: Long, address: String) {
        fun track() = entry.timeline?.tracks?.firstOrNull { it.id == trackId }
        val first = track() ?: return
        if (value <= first.newest(entry.serverNow())) return
        val started = System.currentTimeMillis()
        val deadline = started + (3 * first.segmentMs + 4000).coerceIn(6000, 12_000)
        while (System.currentTimeMillis() < deadline) {
            refreshManifest(entry)
            val t = track() ?: return
            if (value <= t.newest(entry.serverNow())) break
            Thread.sleep(250)
        }
        if (entry.waits++ % 50 == 0) Log.i(TAG, "live segment held until listed (${entry.waits}): ${System.currentTimeMillis() - started} ms for ${address.takeLast(60)}")
    }

    private val entries = ConcurrentHashMap<String, Entry>()

    private val client by lazy {
        com.lagradost.cloudstream3.app.baseClient.newBuilder().callTimeout(0, TimeUnit.SECONDS).connectTimeout(5, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).build()
    }
    private val freshClient by lazy {
        client.newBuilder().connectionPool(okhttp3.ConnectionPool(0, 1, TimeUnit.SECONDS)).protocols(listOf(okhttp3.Protocol.HTTP_1_1)).build()
    }

    private val server: HttpServer by lazy {
        HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 32).apply {
            executor = Executors.newCachedThreadPool { r -> Thread(r, "DashProxy").apply { isDaemon = true } }
            createContext("/d") { ex -> runCatching { handle(ex) }.onFailure { Log.w(TAG, "request failed: ${it.message}") }; ex.close() }
            start()
        }
    }

    /** Whether a [wrap] address plays a live (dynamic) manifest */
    fun isLive(address: String): Boolean = address.substringAfter("/d/", "").substringBefore("/").let { entries[it]?.live == true }

    /** Address to give the player instead of [url] (the manifest); segments are asked for next to it */
    fun wrap(url: String, headers: Map<String, String>): String {
        if (entries.size > 50) entries.clear()
        val id = Integer.toHexString(url.hashCode()) + "-" + System.nanoTime().toString(16)
        val name = url.substringBefore('?').substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(80).ifEmpty { "manifest.mpd" }
        entries[id] = Entry(url, headers, name)
        return "http://127.0.0.1:${server.address.port}/d/$id/$name"
    }

    private fun upstream(entry: Entry, url: String, ex: HttpExchange, fresh: Boolean): okhttp3.Response {
        val builder = Request.Builder().url(url.toHttpUrlOrNull() ?: throw IOException("bad address $url"))
        var userAgent = false
        for ((k, v) in entry.headers) {
            if (k.equals("Host", true) || k.equals("Content-Length", true) || k.equals("Connection", true) || k.equals("Range", true)) continue
            if (k.equals("User-Agent", true)) userAgent = true
            runCatching { builder.header(k, v) }
        }
        if (!userAgent) builder.header("User-Agent", com.lagradost.cloudstream3.USER_AGENT)
        ex.requestHeaders.getFirst("Range")?.let { builder.header("Range", it) }
        if (fresh) builder.header("Connection", "close")
        if (ex.requestMethod.equals("HEAD", true)) builder.head()
        return (if (fresh) freshClient else client).newCall(builder.build()).execute()
    }

    private fun handle(ex: HttpExchange) {
        val path = ex.requestURI.rawPath.removePrefix("/d/")
        val entry = entries[path.substringBefore('/')] ?: run { ex.sendResponseHeaders(404, -1); return }
        val rest = path.substringAfter('/', "")
        val isManifest = rest == entry.name
        val url = if (isManifest) entry.url else entry.baseDir + rest + (ex.requestURI.rawQuery?.let { "?$it" } ?: "")
        // the segment's place in the live manifest: which stream, which number (or time)
        val matched = if (!isManifest && entry.live) entry.timeline?.match(rest + (ex.requestURI.rawQuery?.let { "?$it" } ?: "")) else null
        if (matched != null) waitForListing(entry, matched.first.id, matched.second, rest + (ex.requestURI.rawQuery?.let { "?$it" } ?: ""))
        // a segment of a live stream that was sent just now is not sent again, see the notes above
        val key = if (matched != null) "track:" + matched.first.id else if (!isManifest && entry.live) rest.replace(TIMED, "$1").takeIf { it != rest } else null
        if (key != null && entry.last[key] == rest) {
            if (entry.repeats++ % 25 == 0) Log.i(TAG, "live segment asked for again, refused (${entry.repeats}): ${rest.takeLast(60)}")
            Thread.sleep(250)
            ex.sendResponseHeaders(404, -1)
            return
        }
        var failure: IOException? = null
        var started = false
        // a live segment that is listed but not on this CDN edge yet is waited for (it usually is there within a moment)
        val waitUntil = System.currentTimeMillis() + PUBLISH_WAIT_MS
        var attempt = 0
        while (attempt < RETRIES) {
            attempt++
            try {
                upstream(entry, url, ex, fresh = attempt > 1).use { r ->
                    if (r.code == 404 && key != null && System.currentTimeMillis() < waitUntil) {
                        // ffmpeg's answer to a 404 at the live edge is to skip to the next number and later come back to
                        // segments it has played already: the sound jumped back two seconds and the picture slowed down to
                        // meet it again. Holding the request until the segment exists keeps the demuxer in order.
                        attempt--
                        if (entry.waits++ % 50 == 0) Log.i(TAG, "live segment not published yet, waiting (${entry.waits}): ${rest.takeLast(60)}")
                        Thread.sleep(400)
                        return@use
                    }
                    if (r.code !in 200..299) {
                        ex.sendResponseHeaders(r.code, -1)
                        return
                    }
                    val body = r.body
                    if (isManifest) {
                        val bytes = body.bytes()
                        if (!entry.live && Regex("""<MPD\b[^>]*\btype\s*=\s*["']dynamic["']""").containsMatchIn(String(bytes, 0, minOf(bytes.size, 4096), Charsets.UTF_8))) {
                            entry.live = true
                            Log.i(TAG, "live manifest: repeated segments are refused")
                        }
                        if (entry.live) learn(entry, bytes, r.header("Date"))
                        ex.responseHeaders.add("Content-Type", r.header("Content-Type") ?: "application/dash+xml")
                        ex.sendResponseHeaders(200, bytes.size.toLong())
                        if (!ex.requestMethod.equals("HEAD", true)) ex.responseBody.write(bytes)
                        return
                    }
                    r.header("Content-Type")?.let { ex.responseHeaders.add("Content-Type", it) }
                    r.header("Content-Range")?.let { ex.responseHeaders.add("Content-Range", it) }
                    r.header("Accept-Ranges")?.let { ex.responseHeaders.add("Accept-Ranges", it) }
                    val length = body.contentLength()
                    if (ex.requestMethod.equals("HEAD", true)) {
                        ex.responseHeaders.add("Content-Length", maxOf(length, 0).toString())
                        ex.sendResponseHeaders(r.code, -1)
                        return
                    }
                    started = true
                    ex.sendResponseHeaders(r.code, if (length >= 0) length else 0)
                    val input = body.byteStream()
                    val out = ex.responseBody
                    val buffer = ByteArray(64 * 1024)
                    var sent = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        try { out.write(buffer, 0, n) } catch (e: IOException) { return } // the player went away
                        sent += n
                    }
                    if (key != null && (length < 0 || sent == length)) entry.last[key] = rest
                    return
                }
            } catch (e: IOException) {
                failure = e
                Log.w(TAG, "attempt $attempt for ${rest.takeLast(60)}: ${e.javaClass.simpleName} ${e.message}")
                if (started) return // the body broke off in the middle: closing the connection tells the player
            }
        }
        // the player sees a failed open, like for a server that is down
        runCatching { ex.sendResponseHeaders(502, -1) }
        Log.w(TAG, "giving up on ${rest.takeLast(60)}: ${failure?.message}")
    }
}
