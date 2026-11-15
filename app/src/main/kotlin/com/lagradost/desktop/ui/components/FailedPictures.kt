package com.lagradost.desktop.ui.components

import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import java.util.concurrent.ConcurrentHashMap

/**
 * Pictures that cannot load are not fetched again and again. Coil keeps no memory of a failure, so a picture whose address is not a picture (a
 * repository's icon given as a share link: an HTML page) or is gone (404) was downloaded and failed on every redraw of the card that shows it (67
 * times in one session of the owner's log, each a download and a failed decode). Such an address now answers "broken" at once for ten minutes, and
 * the card shows its placeholder without waiting. A failure that may pass (no network, a timeout, 429/5xx) is not remembered.
 */
object FailedPictures : Interceptor {
    private const val FORGET_MS = 10 * 60_000L
    private val failed = ConcurrentHashMap<String, Long>()

    /** The answer for an address remembered as broken (not logged again) */
    class KnownBroken(address: String) : Exception("a picture that did not load a moment ago: $address")

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val key = chain.request.data.let { if (it is String || it is okhttp3.HttpUrl || it is java.net.URI) it.toString() else null }
        if (key != null) {
            val at = failed[key]
            if (at != null) {
                if (System.currentTimeMillis() - at < FORGET_MS) return ErrorResult(null, chain.request, KnownBroken(key))
                failed.remove(key)
            }
        }
        val result = chain.proceed()
        if (key != null && result is ErrorResult && isLasting(result.throwable)) {
            if (failed.size > 2_000) failed.clear()
            failed[key] = System.currentTimeMillis()
        }
        return result
    }

    private fun isLasting(t: Throwable): Boolean {
        if (t is kotlinx.coroutines.CancellationException) return false
        // not a picture at all (an HTML page, an unknown format)
        if (t.message?.contains("makeFromEncoded") == true) return true
        val code = (t as? coil3.network.HttpException)?.response?.code ?: return false
        return code in 400..499 && code != 408 && code != 429
    }
}
