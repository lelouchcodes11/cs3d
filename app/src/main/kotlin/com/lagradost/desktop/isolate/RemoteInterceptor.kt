package com.lagradost.desktop.isolate

import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import java.util.Base64

/**
 * The video interceptor of an extension that runs in the sandbox (`MainAPI.getVideoInterceptor`: code that changes the requests of a video or answers some of
 * them itself). The player's proxies run it like any other interceptor, but it does not go on to the network: the request goes to the sandbox, where the
 * extension's own interceptor runs around a request that the sandbox makes, and the answer (status, headers, then the body piece by piece, on request) comes
 * back as the response. The video never reaches the extension's code in this process.
 */
class RemoteInterceptor(private val api: RemoteMainAPI, private val link: ExtractorLink) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val description = mapper.createObjectNode().put("url", request.url.toString()).put("method", request.method).also { n ->
            n.set<com.fasterxml.jackson.databind.JsonNode>("headers", mapper.valueToTree(request.headers.toMultimap().mapValues { it.value.firstOrNull().orEmpty() }))
            request.body?.let { b ->
                val buffer = Buffer()
                b.writeTo(buffer)
                if (buffer.size <= 1_048_576) n.put("body", Base64.getEncoder().encodeToString(buffer.readByteArray()))
            }
        }
        val reply = runBlocking { api.callHost("interceptOpen", 180_000, "link" to IsolationSerde.write(link, ExtractorLink::class.java), "request" to description) }
        val id = reply.get("id").asLong()
        val headers = Headers.Builder().also { b -> reply.get("headers").forEach { pair -> b.addUnsafeNonAscii(pair.get(0).asText(), pair.get(1).asText()) } }.build()
        val length = reply.get("contentLength")?.asLong(-1) ?: -1
        return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(reply.get("code").asInt()).message(reply.get("message")?.asText().orEmpty())
            .headers(headers).body(RemoteSource(id).buffer().asResponseBody(headers["Content-Type"]?.toMediaTypeOrNull(), length)).build()
    }

    /**
     * The body of the answer: a piece of up to a megabyte is asked for when the player reads on, and handed out as the reader asks. The reader (okio) asks
     * for 8 KB at a time: a call per read made a 2 MB video segment ~250 calls to the sandbox (Kisskh: the first segment took more than 20 s).
     */
    private class RemoteSource(private val id: Long) : Source {
        private var done = false
        private var closed = false
        private val pending = Buffer()

        override fun read(sink: Buffer, byteCount: Long): Long {
            if (closed) return -1
            if (pending.size == 0L) {
                if (done) return -1
                val reply = runBlocking { IsolatedHost.call("interceptRead", mapper.createObjectNode().put("id", id).put("max", PIECE), 120_000) }
                if (reply.get("eof")?.asBoolean() == true) { done = true; return -1 }
                pending.write(Base64.getDecoder().decode(reply.get("data").asText()))
                if (reply.get("last")?.asBoolean() == true) done = true
            }
            return pending.read(sink, minOf(byteCount, pending.size))
        }

        override fun timeout(): Timeout = Timeout.NONE

        override fun close() {
            if (closed) return
            closed = true
            runCatching { runBlocking { IsolatedHost.call("interceptClose", mapper.createObjectNode().put("id", id), 10_000) } }
        }

        private companion object {
            const val PIECE = 1_048_576
        }
    }
}
