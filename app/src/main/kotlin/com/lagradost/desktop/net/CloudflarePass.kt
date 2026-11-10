package com.lagradost.desktop.net

import com.lagradost.desktop.runtime.web.WebCookies
import com.lagradost.desktop.runtime.web.WebRuntime
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import com.fasterxml.jackson.databind.JsonNode
import com.lagradost.cloudstream3.mapper

/**
 * A Cloudflare pass (cf_clearance) is only accepted together with the User-Agent of the browser that earned it, and here that browser is the app's (Edge
 * WebView2), not a phone's. AnimePahe sends its saved pass with a fixed Android phone User-Agent ("Linux; Android 10; K"), so Cloudflare refused it at once
 * ("CF challenge persists": the check window again), and every poster on i.animepahe.pw failed with 403 (56 in one evening of the owner's log; on Android,
 * where the browser is a phone, they show). Measured with the browser's pass: 403 with the phone User-Agent, 200 with the browser's.
 *
 * When the browser holds a pass for the request's site, the request is sent with the browser's Cloudflare cookies for that site (over the extension's ones
 * of the same name; its other cookies stay) and the browser's User-Agent, as the browser's own request would be: at once when the request carries a pass of
 * its own (or, for [pictures], always); otherwise only as a second try when Cloudflare refused the request with its check, so a request that works is never
 * changed. Nothing changes for a site the browser holds no pass for.
 *
 * In the extension sandbox Cloudflare refuses the connection even with the browser's pass, User-Agent, headers and address (measured: 403 there, 200 from
 * the app for the same request), so AnimePahe reopened its check at every start and its pages came through the app's browser one by one. There a request
 * that Cloudflare refused is made by the app instead ([sandbox], [serve]): only for a site whose check the app's browser passed, never for an address of
 * the private network.
 */
class CloudflarePass private constructor(private val evenWithoutCookies: Boolean, private val inSandbox: Boolean = false) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (inSandbox) return chain.proceed(request).let { r -> if (challenged(r)) byApp(request)?.also { r.close() } ?: r else r }
        val own = request.header("Cookie")
        if (evenWithoutCookies || own?.contains("cf_clearance=") == true) return chain.proceed(withPass(request) ?: request)
        val response = chain.proceed(request)
        if (!challenged(response)) return response
        val again = withPass(request) ?: return response
        response.close()
        return chain.proceed(again)
    }

    /** [request] with the browser's Cloudflare cookies and User-Agent, or null when the browser holds no pass for its site */
    internal fun withPass(request: Request): Request? {
        val jar = runCatching { WebCookies.header(request.url.toString()) }.getOrNull() ?: return null
        val cf = jar.split("; ").filter { isCloudflareCookie(it) }
        if (cf.none { it.startsWith("cf_clearance=") }) return null
        val ua = runCatching { WebRuntime.defaultUserAgent }.getOrNull() ?: return null
        return request.newBuilder().header("Cookie", merge(request.header("Cookie"), cf.joinToString("; "))).header("User-Agent", ua).build()
    }

    companion object {
        /** For the requests of extensions: a request that carries a pass of its own, or that Cloudflare refused */
        val requests = CloudflarePass(false)

        /** For pictures (posters): every request */
        val pictures = CloudflarePass(true)

        /** In the sandbox: a request that Cloudflare refused is made by the app ([serve]) */
        val sandbox = CloudflarePass(false, inSandbox = true)

        private const val MAX_BODY = 32L * 1024 * 1024

        /** The sandbox's side: the app's answer to [request], or null when it has none (no pass for the site, not allowed, too big) */
        private fun byApp(request: Request): Response? {
            val body = request.body?.let { b -> runCatching { okio.Buffer().also { b.writeTo(it) } }.getOrNull()?.takeIf { it.size <= 1_048_576 } ?: return null }
            val node = mapper.createObjectNode().put("url", request.url.toString()).put("method", request.method)
            node.set<JsonNode>("headers", mapper.createArrayNode().also { a -> request.headers.forEach { (k, v) -> a.add(mapper.createArrayNode().add(k).add(v)) } })
            body?.let { node.put("body", java.util.Base64.getEncoder().encodeToString(it.readByteArray())).put("type", request.body?.contentType()?.toString()) }
            val reply = com.lagradost.desktop.isolate.ExtensionWorker.askApp("cfFetch", node, 60_000) ?: return null
            val code = reply.get("code")?.asInt() ?: 0
            if (code <= 0) return null
            val headers = okhttp3.Headers.Builder().also { b -> reply.get("headers")?.forEach { p -> runCatching { b.addUnsafeNonAscii(p.get(0).asText(), p.get(1).asText()) } } }.build()
            val bytes = java.util.Base64.getDecoder().decode(reply.get("body")?.asText().orEmpty())
            return Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1).code(code).message(reply.get("message")?.asText().orEmpty())
                .headers(headers).body(bytes.toResponseBody(headers["Content-Type"]?.toMediaTypeOrNull())).build()
        }

        /**
         * The app's side of a sandbox request that Cloudflare refused: made with the app's client and the pass of the app's browser. Only for a site the browser
         * holds a pass for (a page whose check was passed), never for an address of the private network; the answer comes back whole (32 MB at most).
         */
        suspend fun serve(b: JsonNode): JsonNode = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val out = mapper.createObjectNode().put("code", 0)
            val url = b.get("url")?.asText().orEmpty()
            if (url.length > 8_000 || !(url.startsWith("https://") || url.startsWith("http://"))) return@withContext out
            if (!com.lagradost.desktop.runtime.web.PrivateNetworkGuard.isAllowed(url)) return@withContext out
            val method = b.get("method")?.asText()?.uppercase()?.takeIf { it in setOf("GET", "POST", "HEAD", "PUT", "PATCH", "DELETE", "OPTIONS") } ?: return@withContext out
            val builder = Request.Builder().url(url)
            b.get("headers")?.forEach { p -> val k = p.get(0).asText(); if (!k.equals("Host", true) && !k.equals("Content-Length", true) && !k.equals("Accept-Encoding", true) && !k.equals("Connection", true)) runCatching { builder.addHeader(k, p.get(1).asText()) } }
            val body = b.get("body")?.asText()?.let { java.util.Base64.getDecoder().decode(it).toRequestBody(b.get("type")?.asText()?.toMediaTypeOrNull()) }
            builder.method(method, body ?: if (method == "POST" || method == "PUT" || method == "PATCH") ByteArray(0).toRequestBody(null) else null)
            val request = sandboxRequests.withPass(builder.build()) ?: return@withContext out
            runCatching {
                com.lagradost.cloudstream3.app.baseClient.newCall(request).execute().use { r ->
                    val length = r.body.contentLength()
                    if (length > MAX_BODY) return@use
                    val bytes = r.body.source().let { s -> s.request(MAX_BODY + 1); if (s.buffer.size > MAX_BODY) return@use; s.readByteArray() }
                    out.put("code", r.code).put("message", r.message).put("body", java.util.Base64.getEncoder().encodeToString(bytes))
                    out.set<JsonNode>("headers", mapper.createArrayNode().also { a -> r.headers.forEach { (k, v) -> if (!k.equals("Content-Encoding", true) && !k.equals("Content-Length", true) && !k.equals("Transfer-Encoding", true)) a.add(mapper.createArrayNode().add(k).add(v)) } })
                    android.util.Log.i("CloudflarePass", "${r.code} for ${request.url.host} made by the app for the sandbox (${bytes.size} bytes)")
                }
            }.onFailure { android.util.Log.w("CloudflarePass", "the app's request for the sandbox failed: $it") }
            out
        }

        // the app's side uses the browser's pass as [requests] does
        private val sandboxRequests = CloudflarePass(false)

        private fun isCloudflareCookie(nameValue: String): Boolean {
            val name = nameValue.substringBefore('=').trim()
            return name == "cf_clearance" || name == "__cf_bm" || name == "_cfuvid" || name == "__cflb" || name.startsWith("cf_chl")
        }

        /** Cloudflare's check page instead of an answer */
        internal fun challenged(response: Response): Boolean {
            if (response.code != 403 && response.code != 503) return false
            if (response.header("Server")?.contains("cloudflare", ignoreCase = true) != true) return false
            return response.header("cf-mitigated").equals("challenge", ignoreCase = true) ||
                runCatching { response.peekBody(4096).string().let { it.contains("Just a moment", true) || it.contains("challenge-platform", true) || it.contains("cf-chl", true) } }.getOrDefault(false)
        }

        /** [own] cookies ("a=1; b=2") with the [browser]'s ones put over those of the same name */
        internal fun merge(own: String?, browser: String): String {
            val all = LinkedHashMap<String, String>()
            for (part in sequenceOf(own.orEmpty(), browser).flatMap { it.split(';').asSequence() }) {
                val p = part.trim()
                if (p.isEmpty()) continue
                all[p.substringBefore('=').trim()] = p
            }
            return all.values.joinToString("; ")
        }
    }
}
