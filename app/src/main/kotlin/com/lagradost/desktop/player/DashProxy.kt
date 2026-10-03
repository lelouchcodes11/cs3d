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
 * The fix is to never answer the same live segment twice in a row. A repeat is answered `404` (after a short pause) and the demuxer
 * then does what it does for a segment that is not there yet: it reads the manifest again and tries the next position, until the
 * manifest has caught up and the real next segment is asked for.
 *
 * Everything else is passed through unchanged (headers of the link, Range requests, status codes), so on-demand manifests play like
 * before; the manifest and the segments (addressed relative to it) come from this server, fetched with the app's HTTP client.
 */
object DashProxy {
    private const val TAG = "DashProxy"
    private const val RETRIES = 3

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
    }

    private val entries = ConcurrentHashMap<String, Entry>()

    private val client by lazy {
        com.lagradost.cloudstream3.app.baseClient.newBuilder().callTimeout(0, TimeUnit.SECONDS).connectTimeout(8, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
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
        // a segment of a live stream that was sent just now is not sent again, see the notes above
        val key = if (!isManifest && entry.live) rest.replace(TIMED, "$1").takeIf { it != rest } else null
        if (key != null && entry.last[key] == rest) {
            if (entry.repeats++ % 25 == 0) Log.i(TAG, "live segment asked for again, refused (${entry.repeats}): ${rest.takeLast(60)}")
            Thread.sleep(250)
            ex.sendResponseHeaders(404, -1)
            return
        }
        var failure: IOException? = null
        var started = false
        for (attempt in 1..RETRIES) {
            try {
                upstream(entry, url, ex, fresh = attempt > 1).use { r ->
                    if (r.code !in 200..299) {
                        // not published yet: the demuxer asks again at once, do not let it spin
                        if (r.code == 404 && key != null) Thread.sleep(150)
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
