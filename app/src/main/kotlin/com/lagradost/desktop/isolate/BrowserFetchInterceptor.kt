package com.lagradost.desktop.isolate

import com.lagradost.cloudstream3.mapper
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * In the sandbox: a Cloudflare check that refuses a GET is passed on to the app's browser (see [BrowserFetch]), which has the handshake that the clearance belongs to, and the
 * request is answered with what the browser's page shows. Cloudflare accepts the clearance of a check passed in a browser for the first request of this client and refuses the
 * next ones, so an extension that did pass the check (AnimePahe) still got a 403 for its search. Any other answer is left as it is.
 */
internal object BrowserFetchInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (request.method != "GET" || (response.code != 403 && response.code != 503)) return response
        if (response.header("Server")?.contains("cloudflare", ignoreCase = true) != true) return response
        val challenged = response.header("cf-mitigated").equals("challenge", ignoreCase = true) ||
            runCatching { response.peekBody(4096).string().let { it.contains("Just a moment", true) || it.contains("challenge-platform", true) || it.contains("cf-chl", true) } }.getOrDefault(false)
        if (!challenged) return response
        val reply = ExtensionWorker.askApp("fetch", mapper.createObjectNode().put("url", request.url.toString()), 60_000) ?: return response
        // 0: the rules do not let the app open a page for this site; 403: no clearance came (the extension's own answer to that is better than an empty one)
        val code = reply.get("code")?.asInt() ?: 0
        if (code == 0 || code == 403 || code == 503) return response
        val type = reply.get("type")?.asText().orEmpty().ifBlank { "text/html" }
        val text = reply.get("text")?.asText().orEmpty()
        response.close()
        return Response.Builder()
            .request(request).protocol(Protocol.HTTP_1_1).code(code).message(if (code == 200) "OK" else "from the browser")
            .header("Content-Type", type)
            .body(text.toResponseBody(type.toMediaTypeOrNull()))
            .build()
    }
}
