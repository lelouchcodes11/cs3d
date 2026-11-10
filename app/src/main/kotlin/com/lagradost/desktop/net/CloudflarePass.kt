package com.lagradost.desktop.net

import com.lagradost.desktop.runtime.web.WebCookies
import com.lagradost.desktop.runtime.web.WebRuntime
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * A Cloudflare pass (cf_clearance) is only accepted together with the User-Agent of the browser that earned it, and here that browser is the app's (Edge
 * WebView2), not a phone's. AnimePahe sends its saved pass with a fixed Android phone User-Agent ("Linux; Android 10; K"), so Cloudflare refused it at once
 * ("CF challenge persists": the check window again), and every poster on i.animepahe.pw failed with 403 (56 in one evening of the owner's log; on Android,
 * where the browser is a phone, they show). Measured with the browser's pass: 403 with the phone User-Agent, 200 with the browser's.
 *
 * When the browser holds a pass for the request's site, the request is sent with the browser's Cloudflare cookies for that site (over the extension's ones
 * of the same name; its other cookies stay) and the browser's User-Agent, as the browser's own request would be: at once when the request carries a pass of
 * its own (or, for [pictures], always); otherwise only as a second try when Cloudflare refused the request with its check, so a request that works is never
 * changed. Nothing changes for a site the browser holds no pass for. Not used in the extension sandbox, whose connection Cloudflare refuses even then (its
 * requests go through the app's browser instead, see isolate/BrowserFetchInterceptor.kt).
 */
class CloudflarePass private constructor(private val evenWithoutCookies: Boolean) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val own = request.header("Cookie")
        if (evenWithoutCookies || own?.contains("cf_clearance=") == true) return chain.proceed(withPass(request) ?: request)
        val response = chain.proceed(request)
        if (!challenged(response)) return response
        val again = withPass(request) ?: return response
        response.close()
        return chain.proceed(again)
    }

    /** [request] with the browser's Cloudflare cookies and User-Agent, or null when the browser holds no pass for its site */
    private fun withPass(request: Request): Request? {
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
