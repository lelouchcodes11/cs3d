package com.lagradost.desktop.isolate

import com.fasterxml.jackson.databind.JsonNode
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.utils.ExtractorLink
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * The sandbox's side of a remote video interceptor (see [RemoteInterceptor]): runs the extension's `getVideoInterceptor` around a request that this process
 * makes, keeps the answer open, and hands its body to the app piece by piece.
 */
internal object WorkerInterceptors {
    private class Open(val response: Response, val source: BufferedSource, @Volatile var used: Long)

    private val open = ConcurrentHashMap<Long, Open>()
    private val ids = AtomicLong()
    private class Cached(val interceptor: Interceptor?)

    private val cache = HashMap<String, Cached>()

    /** The extension's interceptor for this link (asked once per link, like the player does) */
    fun interceptorOf(api: MainAPI, link: ExtractorLink): Interceptor? = synchronized(cache) {
        cache.getOrPut(api.name + "\u0000" + link.url) { Cached(runCatching { api.getVideoInterceptor(link) }.getOrNull()) }.interceptor
    }

    fun open(api: MainAPI, b: JsonNode): JsonNode {
        reap()
        val link = IsolationSerde.read(b.get("link"), ExtractorLink::class.java) ?: error("not a link")
        val interceptor = interceptorOf(api, link)
        val r = b.get("request")
        val method = r.get("method")?.asText() ?: "GET"
        val builder = Request.Builder().url(r.get("url").asText())
        r.get("headers")?.fields()?.forEach { (k, v) -> builder.header(k, v.asText()) }
        val body = r.get("body")?.asText()?.let { Base64.getDecoder().decode(it).toRequestBody(null) }
        builder.method(method, body ?: if (method == "POST" || method == "PUT" || method == "PATCH") ByteArray(0).toRequestBody("application/octet-stream".toMediaTypeOrNull()) else null)
        val client = app.baseClient.newBuilder().callTimeout(0, TimeUnit.SECONDS).apply { interceptor?.let { addInterceptor(it) } }.build()
        val response = client.newCall(builder.build()).execute()
        val id = ids.incrementAndGet()
        open[id] = Open(response, response.body.source(), System.currentTimeMillis())
        return mapper.createObjectNode().put("id", id).put("code", response.code).put("message", response.message).put("contentLength", response.body.contentLength()).also { n ->
            n.set<JsonNode>("headers", mapper.createArrayNode().also { a -> response.headers.forEach { (k, v) -> a.add(mapper.createArrayNode().add(k).add(v)) } })
        }
    }

    fun read(b: JsonNode): JsonNode {
        val o = open[b.get("id").asLong()] ?: error("this answer is closed")
        o.used = System.currentTimeMillis()
        val buffer = ByteArray(b.get("max").asInt(1_048_576).coerceIn(1, 4_194_304))
        // the piece is filled (a read hands out only what has arrived, often 8 KB): every call is a round trip for the app. "last": the answer ended with it.
        var n = 0
        var ended = false
        while (n < buffer.size) {
            val got = o.source.read(buffer, n, buffer.size - n)
            if (got < 0) { ended = true; break }
            n += got
        }
        if (n == 0) return mapper.createObjectNode().put("eof", true)
        return mapper.createObjectNode().put("data", Base64.getEncoder().encodeToString(if (n == buffer.size) buffer else buffer.copyOf(n))).put("last", ended)
    }

    fun close(id: Long) {
        open.remove(id)?.response?.close()
    }

    /** An answer that the app stopped reading (the player moved on) is closed after a few minutes */
    private fun reap() {
        val limit = System.currentTimeMillis() - 3 * 60_000
        for ((id, o) in open.entries.toList()) if (o.used < limit) close(id)
    }
}
