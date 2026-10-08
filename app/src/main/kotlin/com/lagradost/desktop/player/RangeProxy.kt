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
 * Loopback server for plain video files (mkv/mp4 ...) that the player's own HTTP stack cannot open. It is the fallback used after
 * a "client error" (400, 403, 405, 406, 416) when opening such a link, see MpvPlayer.
 *
 * Why it helps: file hosts behind Cloudflare workers (the "Instant Download" links of HubCloud / 4KHDHub, ...) answer 403 to a plain
 * GET, to `Range: bytes=0-` and to HEAD, and serve only bounded ranges (`bytes=0-1023`); mpv asks for the file as a whole. Here the
 * player gets the file from this server, which fetches it from the host in bounded ranges with the app's own HTTP client (DNS setting,
 * TLS, headers of the link, quarantine of bad addresses), so
 *  - hosts that only talk in bounded ranges work, and the file's size is learned from the first answer (`Content-Range`);
 *  - a connection that dies in the middle of a long file is continued where it stopped, the player never sees it;
 *  - seeking is answered with ranges like any server would.
 */
object RangeProxy {
    private const val TAG = "RangeProxy"
    private const val CHUNK = 4L * 1024 * 1024
    private const val RETRIES = 6

    private class Entry(val url: String, val headers: Map<String, String>, val interceptor: okhttp3.Interceptor? = null) {
        @Volatile
        var total = -1L

        @Volatile
        var contentType: String? = null

        @Volatile
        var ranges = true
    }

    private val entries = ConcurrentHashMap<String, Entry>()

    private val sharedClient by lazy {
        com.lagradost.cloudstream3.app.baseClient.newBuilder().callTimeout(0, TimeUnit.SECONDS).connectTimeout(8, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    }
    private val freshClient by lazy {
        sharedClient.newBuilder().connectionPool(okhttp3.ConnectionPool(0, 1, TimeUnit.SECONDS)).protocols(listOf(okhttp3.Protocol.HTTP_1_1)).build()
    }

    private val server: HttpServer by lazy {
        HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 32).apply {
            executor = Executors.newCachedThreadPool { r -> Thread(r, "RangeProxy").apply { isDaemon = true } }
            createContext("/m") { ex -> runCatching { handle(ex) }.onFailure { Log.w(TAG, "request failed: ${it.message}") }; ex.close() }
            start()
        }
    }

    /** Address to give the player instead of [url]; the file name (and with it the extension) is kept, players guess the format from it */
    fun wrap(url: String, headers: Map<String, String>, interceptor: okhttp3.Interceptor? = null): String {
        if (entries.size > 50) entries.clear()
        val id = Integer.toHexString(url.hashCode()) + "-" + System.nanoTime().toString(16)
        entries[id] = Entry(url, headers, interceptor)
        val name = url.substringBefore('?').substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(80).ifEmpty { "video" }
        return "http://127.0.0.1:${server.address.port}/m/$id/$name"
    }

    /** [fresh]: on a new connection; some hosts decide per connection (edge server) whether they serve a request, a retry on the same one fails again */
    private fun upstream(entry: Entry, range: String?, fresh: Boolean = false): okhttp3.Response {
        val builder = Request.Builder().url(entry.url.toHttpUrlOrNull() ?: throw IOException("bad address ${entry.url}"))
        var userAgent = false
        for ((k, v) in entry.headers) {
            if (k.equals("Host", true) || k.equals("Content-Length", true) || k.equals("Connection", true) || k.equals("Range", true)) continue
            if (k.equals("User-Agent", true)) userAgent = true
            com.lagradost.desktop.net.RawHeaders.set(builder, k, v)
        }
        if (!userAgent) builder.header("User-Agent", com.lagradost.cloudstream3.USER_AGENT)
        if (range != null) builder.header("Range", range)
        if (fresh) builder.header("Connection", "close")
        // the extension's video interceptor (getVideoInterceptor) sees every request, as with ExoPlayer on Android
        val client = (if (fresh) freshClient else sharedClient).let { c -> entry.interceptor?.let { c.newBuilder().addInterceptor(it).build() } ?: c }
        return client.newCall(builder.build()).execute()
    }

    /** Learns the size (and type) of the file with a one byte range; false when the host refused */
    private fun probe(entry: Entry): Int {
        if (entry.total >= 0) return 200
        var refused = 0
        for (attempt in 1..RETRIES) {
            try {
                upstream(entry, "bytes=0-0", fresh = attempt > 1).use { r ->
                    if (r.code == 206) {
                        entry.total = r.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: -1L
                        entry.contentType = r.header("Content-Type")
                        return if (entry.total >= 0) 206 else 502
                    }
                    if (r.code == 200) {
                        // no range support: the whole file comes on one connection
                        entry.ranges = false
                        entry.total = r.header("Content-Length")?.toLongOrNull() ?: -1L
                        entry.contentType = r.header("Content-Type")
                        return 200
                    }
                    if (r.code == 404 || r.code == 410) return r.code
                    if (r.code in 400..499) refused = r.code // may be this connection only: try again on a new one
                }
            } catch (e: IOException) {
                Log.w(TAG, "probe attempt $attempt: ${e.javaClass.simpleName} ${e.message}")
            }
        }
        return if (refused != 0) refused else 502
    }

    private fun handle(ex: HttpExchange) {
        val id = ex.requestURI.path.removePrefix("/m/").substringBefore('/')
        val entry = entries[id] ?: run { ex.sendResponseHeaders(404, -1); return }
        val status = probe(entry)
        if (status !in 200..299) {
            ex.sendResponseHeaders(if (status in 400..499) status else 502, -1)
            return
        }
        val total = entry.total
        val type = entry.contentType ?: "application/octet-stream"
        ex.responseHeaders.add("Content-Type", type)
        ex.responseHeaders.add("Accept-Ranges", if (entry.ranges) "bytes" else "none")
        if (ex.requestMethod.equals("HEAD", true)) {
            ex.responseHeaders.add("Content-Length", total.toString())
            ex.sendResponseHeaders(200, -1)
            return
        }
        // the range the player wants
        var start = 0L
        var end = if (total >= 0) total - 1 else Long.MAX_VALUE
        var partial = false
        ex.requestHeaders.getFirst("Range")?.let { header ->
            val m = Regex("bytes=(\\d*)-(\\d*)").find(header)
            if (m != null && entry.ranges) {
                val a = m.groupValues[1]
                val b = m.groupValues[2]
                when {
                    a.isNotEmpty() -> { start = a.toLong(); if (b.isNotEmpty()) end = minOf(end, b.toLong()); partial = true }
                    b.isNotEmpty() && total >= 0 -> { start = maxOf(0, total - b.toLong()); partial = true } // the last n bytes
                }
            }
        }
        if (total >= 0 && start >= total) {
            ex.responseHeaders.add("Content-Range", "bytes */$total")
            ex.sendResponseHeaders(416, -1)
            return
        }
        val length = if (total >= 0) end - start + 1 else -1L
        if (partial) {
            ex.responseHeaders.add("Content-Range", "bytes $start-$end/${if (total >= 0) total else "*"}")
            ex.sendResponseHeaders(206, if (length >= 0) length else 0)
        } else {
            ex.sendResponseHeaders(200, if (length >= 0) length else 0)
        }
        val out = ex.responseBody
        var position = start
        var failures = 0
        var chunkStart = start
        while (position <= end) {
            // after a failure in the middle of a chunk the whole chunk is asked for again, from its own start, and what the player
            // has got already is skipped: some hosts accept only the ranges they saw before, not "bytes=<odd position>-"
            val from = if (failures > 0) chunkStart else position
            if (failures == 0) chunkStart = position
            val last = if (entry.ranges) minOf(end, from + CHUNK - 1) else end
            try {
                upstream(entry, if (entry.ranges) "bytes=$from-$last" else null, fresh = failures > 0).use { r ->
                    if (r.code != 206 && r.code != 200) throw IOException("HTTP ${r.code}")
                    val body = r.body
                    val input = body.byteStream()
                    // a host that ignored the range sends the file from its start
                    if (r.code == 200 && from > 0) input.skipNBytes(from)
                    if (position > from) input.skipNBytes(position - from)
                    val buffer = ByteArray(64 * 1024)
                    var left = last - position + 1
                    while (left > 0) {
                        val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                        if (n < 0) break
                        try { out.write(buffer, 0, n) } catch (e: IOException) { return } // the player went away (seek, stop)
                        position += n
                        left -= n
                        failures = 0
                    }
                    if (left > 0 && total >= 0 && position <= last) throw IOException("chunk ended early")
                    if (total < 0 && left > 0) { position = end + 1 } // size unknown: the end of the file
                }
            } catch (e: IOException) {
                failures++
                Log.w(TAG, "continuing at $position after ${e.javaClass.simpleName} ${e.message} (attempt $failures)")
                if (failures >= RETRIES) return // closing the connection early tells the player
            }
        }
    }
}
