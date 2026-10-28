package com.lagradost.desktop.runtime.web

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Coalesces synchronous Android cookie reads without accepting late or incomplete browser answers. */
internal class BrowserCookieRefresh {
    private val lock = Any()
    private var pending: CompletableFuture<Boolean>? = null

    fun refresh(
        timeoutMs: Long,
        canWait: Boolean,
        request: ((List<WebCookie>?) -> Unit) -> Unit,
        apply: (List<WebCookie>) -> Boolean,
    ): Boolean {
        val future = synchronized(lock) {
            pending ?: run {
                val future = CompletableFuture<Boolean>()
                pending = future
                val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
                fun finish(cookies: List<WebCookie>?) {
                    synchronized(lock) {
                        if (future.isDone) return
                        val ok = cookies != null && System.nanoTime() < deadline && runCatching { apply(cookies) }.getOrDefault(false)
                        future.complete(ok)
                        if (pending === future) pending = null
                    }
                }
                timer.schedule({ finish(null) }, timeoutMs, TimeUnit.MILLISECONDS)
                try {
                    request(::finish)
                } catch (_: Exception) {
                    finish(null)
                }
                future
            }
        }
        // In particular, never wait on the WebView2 COM callback/message-pump thread.
        if (!canWait) return future.getNow(false)
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private val timer = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "cookie-refresh-timeout").also { it.isDaemon = true }
        }
    }
}
