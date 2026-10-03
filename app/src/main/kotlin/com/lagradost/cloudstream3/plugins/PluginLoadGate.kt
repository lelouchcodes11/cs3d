package com.lagradost.cloudstream3.plugins

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Desktop: keeps plugin loading from starving the first screen. Loading converts and links every extension, and
 * started all at once (16+ threads) it took the CPU from the UI thread while the window was still being built.
 */
object PluginLoadGate {
    /** Plugins that are loaded (or converted) at the same time */
    val limit = Semaphore(3)

    /** Completed once the first frame is on screen */
    val firstFrame = CompletableDeferred<Unit>()

    /** Waits for the first frame, but never longer than [maxMs] */
    suspend fun awaitFirstFrame(maxMs: Long = 6_000) {
        withTimeoutOrNull(maxMs) { firstFrame.await() }
    }
}
