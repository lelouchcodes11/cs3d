package com.lagradost.cloudstream3.network

import android.util.Log
import android.webkit.CookieManager
import androidx.annotation.AnyThread
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.debugWarning
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.nicehttp.Requests.Companion.await
import com.lagradost.nicehttp.cookies
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.net.URI


@AnyThread
class CloudflareKiller : Interceptor {
    companion object {
        const val TAG = "CloudflareKiller"
        private val ERROR_CODES = listOf(403, 503)
        private val CLOUDFLARE_SERVERS = listOf("cloudflare-nginx", "cloudflare")
        fun parseCookieMap(cookie: String): Map<String, String> {
            return cookie.split(";").associate {
                val split = it.split("=")
                (split.getOrNull(0)?.trim() ?: "") to (split.getOrNull(1)?.trim() ?: "")
            }.filter { it.key.isNotBlank() && it.value.isNotBlank() }
        }
    }

    init {
        // Needs to clear cookies between sessions to generate new cookies.
        safe {
            // This can throw an exception on unsupported devices :(
            CookieManager.getInstance().removeAllCookies(null)
        }
    }

    val savedCookies: MutableMap<String, Map<String, String>> = mutableMapOf()

    /**
     * Gets the headers with cookies, webview user agent included!
     * */
    fun getCookieHeaders(url: String): Headers {
        val userAgentHeaders = WebViewResolver.webViewUserAgent?.let {
            mapOf("user-agent" to it)
        } ?: emptyMap()

        return getHeaders(userAgentHeaders, savedCookies[URI(url).host] ?: emptyMap())
    }

    override fun intercept(chain: Interceptor.Chain): Response = runBlocking {
        val request = chain.request()

        when (val cookies = savedCookies[request.url.host]) {
            null -> {
                val response = chain.proceed(request)
                if(!(response.header("Server") in CLOUDFLARE_SERVERS && response.code in ERROR_CODES)) {
                    return@runBlocking response
                } else if (!com.lagradost.desktop.runtime.web.CloudflareGate.allowsSolving(request.url.host)) {
                    // Settings > General > Pass Cloudflare checks is off, or the user just cancelled this site's: no page is opened, the extension gets the answer as it is
                    return@runBlocking response
                } else {
                    response.close()
                    bypassCloudflare(request)?.let {
                        Log.d(TAG, "Succeeded bypassing cloudflare: ${request.url}")
                        return@runBlocking it
                    }
                }
            }
            else -> {
                return@runBlocking proceed(request, cookies)
            }
        }

        debugWarning({ true }) { "Failed cloudflare at: ${request.url}" }
        return@runBlocking chain.proceed(request)
    }

    private fun getWebViewCookie(url: String): String? {
        return safe {
            CookieManager.getInstance()?.getCookie(url)
        }
    }

    /**
     * Returns true if the cf cookies were successfully fetched from the CookieManager
     * Also saves the cookies.
     * */
    private fun trySolveWithSavedCookies(request: Request): Boolean {
        // Not sure if this takes expiration into account
        return getWebViewCookie(request.url.toString())?.let { cookie ->
            cookie.contains("cf_clearance").also { solved ->
                if (solved) savedCookies[request.url.host] = parseCookieMap(cookie)
            }
        } ?: false
    }

    private suspend fun proceed(request: Request, cookies: Map<String, String>): Response {
        val userAgentMap = WebViewResolver.getWebViewUserAgent()?.let {
            mapOf("user-agent" to it)
        } ?: emptyMap()

        val headers =
            getHeaders(request.headers.toMap() + userAgentMap, cookies + request.cookies)
        return app.baseClient.newCall(
            request.newBuilder()
                .headers(headers)
                .build()
        ).await()
    }

    private suspend fun bypassCloudflare(request: Request): Response? {
        val url = request.url.toString()

        // If no cookies then try to get them
        // Remove this if statement if cookies expire
        if (!trySolveWithSavedCookies(request)) {
            Log.d(TAG, "Loading webview to solve cloudflare for ${request.url}")
            val resolver = WebViewResolver(
                // Never exit based on url
                Regex(".^"),
                // Cloudflare needs default user agent
                userAgent = null,
                // Cannot use okhttp (i think intercepting cookies fails which causes the issues)
                useOkhttp = false,
                // Match every url for the requestCallBack
                additionalUrls = listOf(Regex(".")),
                // desktop: a check nobody sees is not waited for as long as a minute (see CloudflareGate.UNATTENDED_MS)
                timeout = com.lagradost.desktop.runtime.web.CloudflareGate.UNATTENDED_MS,
            )
            // the bar with a Cancel button shows while the page works (see CloudflareGate)
            val solve = com.lagradost.desktop.runtime.web.CloudflareGate.beginSolve(request.url.host) { resolver.cancel() }
            // desktop: the page's own requests are seen on the browser's thread, where the browser's cookies can not be waited for, so the check
            // there never saw the pass: a passed check was waited on for the whole time limit and the site then counted as failed (measured: a
            // check passed after 6 s, given up at 25 s). The cookies are looked at from here instead, and a pass is tried on the request itself
            // before it is believed (a check page hands out a cookie before it is through: such a one is not taken, the page works on).
            var passed: Response? = null
            val watcher = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                var tried: String? = null
                while (true) {
                    kotlinx.coroutines.delay(400)
                    if (!trySolveWithSavedCookies(request)) continue
                    val cookies = savedCookies[request.url.host] ?: continue
                    val clearance = cookies["cf_clearance"]
                    if (clearance == tried) { savedCookies.remove(request.url.host); continue }
                    tried = clearance
                    val answer = runCatching { proceed(request, cookies) }.getOrNull()
                    if (answer != null && !(answer.header("Server") in CLOUDFLARE_SERVERS && answer.code in ERROR_CODES)) {
                        passed = answer
                        resolver.cancel()
                        return@launch
                    }
                    answer?.close()
                    savedCookies.remove(request.url.host)
                }
            }
            try {
                resolver.resolveUsingWebView(
                    url
                ) {
                    false
                }
            } finally {
                watcher.cancel()
                com.lagradost.desktop.runtime.web.CloudflareGate.endSolve(solve)
            }
            watcher.join()
            passed?.let { return it }
            // the last look, with the page gone
            if (savedCookies[request.url.host] == null) trySolveWithSavedCookies(request)
        }

        val cookies = savedCookies[request.url.host] ?: run {
            com.lagradost.desktop.runtime.web.CloudflareGate.markFailed(request.url.host)
            return null
        }
        return proceed(request, cookies)
    }
}