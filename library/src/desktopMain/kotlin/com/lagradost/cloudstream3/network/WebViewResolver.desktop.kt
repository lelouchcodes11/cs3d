@file:JvmName("WebViewResolver_androidKt")

package com.lagradost.cloudstream3.network

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.lagradost.api.Log
import com.lagradost.cloudstream3.mvvm.debugException
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.utils.Coroutines.atomicListOf
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.desktop.runtime.web.HeadlessBrowser
import com.lagradost.desktop.runtime.web.WebRuntime
import com.lagradost.nicehttp.requestCreator
import io.ktor.http.Url
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Desktop implementation of WebViewResolver on a hidden browser (Edge WebView2, or bundled Chromium where it is missing).
 *
 * Same contract as the Android implementation: the page is loaded in a hidden browser, every
 * request is matched against [interceptUrl] / [additionalUrls], [script] is evaluated on each
 * request and the browser is destroyed on match or after [timeout]. Requests are observed natively
 * instead of being proxied through OkHttp, so [useOkhttp] only affects nothing but is kept for API
 * compatibility; the browser always performs the requests.
 *
 * @param interceptUrl will stop the WebView when reaching this url.
 * @param additionalUrls this will make resolveUsingWebView also return all other requests matching the list of Regex.
 * @param userAgent if null then will use the default user agent
 * @param useOkhttp kept for compatibility, see above
 * @param script pass custom js to execute
 * @param scriptCallback will be called with the result from custom js
 * @param timeout close webview after timeout
 * */
actual class WebViewResolver actual constructor(
    val interceptUrl: Regex,
    val additionalUrls: List<Regex>,
    val userAgent: String?,
    val useOkhttp: Boolean,
    val script: String?,
    val scriptCallback: ((String) -> Unit)?,
    val timeout: Long
) : Interceptor {

    actual companion object {
        actual var webViewUserAgent: String? = null
        actual val DEFAULT_TIMEOUT = 60_000L
        private const val TAG = "WebViewResolver"

        @JvmName("getWebViewUserAgent1")
        fun getWebViewUserAgent(): String? {
            return webViewUserAgent ?: WebRuntime.defaultUserAgent.also { webViewUserAgent = it }
        }

        // Suppress media and asset requests as we don't display them anywhere
        private val blacklistedFiles = listOf(
            ".jpg", ".png", ".webp", ".mpg", ".mpeg", ".jpeg", ".webm", ".mp4", ".mp3", ".gifv", ".flv", ".asf",
            ".mov", ".mng", ".mkv", ".ogg", ".avi", ".wav", ".woff2", ".woff", ".ttf", ".css", ".vtt", ".srt", ".ts",
            ".gif",
            // Warning, this might fuck some future sites, but it's used to make Sflix work.
            "wss://"
        )
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return runBlocking {
            val fixedRequest = resolveUsingWebView(request).first
            return@runBlocking chain.proceed(fixedRequest ?: request)
        }
    }

    actual suspend fun resolveUsingWebView(
        url: String,
        referer: String?,
        method: String,
        requestCallBack: (Request) -> Boolean,
    ): Pair<Request?, List<Request>> =
        resolveUsingWebView(url, referer, emptyMap(), method, requestCallBack)

    actual suspend fun resolveUsingWebView(
        url: String,
        referer: String?,
        headers: Map<String, String>,
        method: String,
        requestCallBack: (Request) -> Boolean,
    ): Pair<Request?, List<Request>> {
        return try {
            resolveUsingWebView(
                requestCreator(method, url, referer = referer, headers = headers), requestCallBack
            )
        } catch (e: java.lang.IllegalArgumentException) {
            logError(e)
            debugException { "ILLEGAL URL IN resolveUsingWebView!" }
            return null to emptyList()
        }
    }

    actual suspend fun resolveUsingWebView(
        request: Request,
        requestCallBack: (Request) -> Boolean
    ): Pair<Request?, List<Request>> {
        val url = request.url.toString()
        val headers = request.headers
        Log.i(TAG, "Initial web-view request: $url")
        // Extra assurance it exits as it should.
        var shouldExit = false
        var fixedRequest: Request? = null
        val extraRequestList = atomicListOf<Request>()
        var browser: HeadlessBrowser? = null

        fun destroyWebView() {
            main {
                browser?.destroy()
                browser = null
                shouldExit = true
                Log.i(TAG, "Destroyed webview")
            }
        }

        webViewUserAgent = userAgent ?: getWebViewUserAgent()
        try {
            browser = HeadlessBrowser(userAgent, onRequest = { r ->
                val webViewUrl = r.url
                Log.i(TAG, "Loading WebView URL: $webViewUrl")
                if (script != null) {
                    browser?.evaluate(script) { result -> scriptCallback?.invoke(result) }
                }
                if (interceptUrl.containsMatchIn(webViewUrl)) {
                    fixedRequest = r.toOkHttpRequest()?.also { requestCallBack(it) }
                    Log.i(TAG, "Web-view request finished: $webViewUrl")
                    destroyWebView()
                    return@HeadlessBrowser HeadlessBrowser.Decision.BLOCK
                }
                if (additionalUrls.any { it.containsMatchIn(webViewUrl) }) {
                    r.toOkHttpRequest()?.also {
                        if (requestCallBack(it)) destroyWebView()
                    }?.let(extraRequestList::add)
                }
                val blocked = try {
                    blacklistedFiles.any { Url(webViewUrl).encodedPath.decodeURLPart().contains(it) } || webViewUrl.endsWith("/favicon.ico")
                } catch (_: Exception) {
                    false
                }
                if (blocked && !webViewUrl.contains("recaptcha") && !webViewUrl.contains("/cdn-cgi/")) HeadlessBrowser.Decision.BLOCK
                else HeadlessBrowser.Decision.CONTINUE
            })
            browser?.load(url, headers.toMap())
        } catch (e: Exception) {
            logError(e)
        }

        var loop = 0
        // Timeouts after this amount, 60s
        val totalTime = timeout
        val delayTime = 100L

        // A bit sloppy, but couldn't find a better way
        while (loop < totalTime / delayTime && !shouldExit) {
            if (fixedRequest != null) return fixedRequest to extraRequestList
            delay(delayTime)
            loop += 1
        }

        Log.i(TAG, "Web-view timeout after ${totalTime / 1000}s")
        destroyWebView()
        return fixedRequest to extraRequestList
    }

    private fun HeadlessBrowser.Request.toOkHttpRequest(): Request? = safe {
        requestCreator(method, url, headers)
    }
}

fun WebResourceRequest.toRequest(): Request? {
    val webViewUrl = this.url.toString()

    // If invalid url then it can crash with
    // java.lang.IllegalArgumentException: Expected URL scheme 'http' or 'https' but was 'data'
    // At Request.Builder().url(addParamsToUrl(url, params))
    return safe {
        requestCreator(
            this.method,
            webViewUrl,
            this.requestHeaders,
        )
    }
}

fun Response.toWebResourceResponse(): WebResourceResponse {
    val contentTypeValue = this.header("Content-Type")
    // 1. contentType. 2. charset
    val typeRegex = Regex("""(.*);(?:.*charset=(.*)(?:|;)|)""")
    return if (contentTypeValue != null) {
        val found = typeRegex.find(contentTypeValue)
        val contentType = found?.groupValues?.getOrNull(1)?.ifBlank { null } ?: contentTypeValue
        val charset = found?.groupValues?.getOrNull(2)?.ifBlank { null }
        WebResourceResponse(contentType, charset, this.body.byteStream())
    } else {
        WebResourceResponse("application/octet-stream", null, this.body.byteStream())
    }
}
