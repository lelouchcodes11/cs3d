package com.lagradost.desktop.net

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Whether this PC reaches the internet at all. With no connection every extension fails at once, and the pages said what they say when one
 * extension has nothing ("Nothing to show from X yet", "No results, check the spelling", "the site did not accept the connection"): the person
 * looked for the fault in the wrong place. Pages that show an empty or failed state ask [check] and say "No internet connection" instead.
 *
 * The check asks two well-known addresses with the app's own client (its DNS and proxy settings): offline only when neither answers at all. An
 * answer of any kind, an error page too, means the internet is there. While offline it looks again every few seconds, so the pages can carry on
 * by themselves when the connection is back ([offline] is a state: what reads it is redrawn).
 */
object Online {
    var offline by mutableStateOf(false)
        private set

    private const val FRESH_MS = 10_000L
    @Volatile private var checkedAt = 0L
    private val probes = listOf("https://www.gstatic.com/generate_204", "https://cloudflare.com/cdn-cgi/trace")

    private val client: OkHttpClient by lazy {
        com.lagradost.cloudstream3.app.baseClient.newBuilder()
            .connectTimeout(4, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS).callTimeout(6, TimeUnit.SECONDS)
            .cache(null).build()
    }

    /** True when the internet answers (a recent answer is reused for a few seconds) */
    suspend fun check(force: Boolean = false): Boolean {
        if (!force && System.currentTimeMillis() - checkedAt < FRESH_MS) return !offline
        val reached = withContext(Dispatchers.IO) {
            coroutineScope {
                probes.map { url ->
                    async {
                        runCatching { client.newCall(Request.Builder().url(url).head().build()).execute().use { true } }.getOrDefault(false)
                    }
                }.awaitAll().any { it }
            }
        }
        checkedAt = System.currentTimeMillis()
        if (offline == reached) android.util.Log.i("Online", if (reached) "the internet answers again" else "no internet connection")
        withContext(Dispatchers.Main) { offline = !reached }
        return reached
    }

    /**
     * For a page in an empty or failed state: checks now, and while there is no connection checks again every few seconds. [whenBack] runs once
     * when the connection returns (a page loads itself again).
     */
    @Composable
    fun Watch(whenBack: () -> Unit = {}) {
        LaunchedEffect(Unit) {
            if (check()) return@LaunchedEffect
            while (true) {
                delay(4_000)
                if (check(force = true)) { whenBack(); return@LaunchedEffect }
            }
        }
    }

    const val TITLE = "No internet connection"
    const val TEXT = "This PC is not connected to the internet. Check the network; the page loads again by itself when the connection is back."
}
