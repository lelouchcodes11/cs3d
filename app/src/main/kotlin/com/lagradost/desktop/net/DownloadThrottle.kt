package com.lagradost.desktop.net

import kotlinx.coroutines.delay

/**
 * The speed limit of downloads (Settings > General > Downloads > "Download speed limit"), shared by every download and every connection of
 * one: a film downloading at full speed (10 MB/s and more) left a video that played at the same time buffering. A token bucket that fills at
 * [limit] bytes a second and holds at most one second of it; a download takes what it read from it and waits when it is empty.
 */
object DownloadThrottle {
    /** Bytes per second, 0 = no limit */
    @Volatile
    var limit: Long = 0L

    private var allowance = 0.0
    private var last = System.nanoTime()

    /** How long [bytes] more must wait at [limitBps], given the bucket; updates the bucket (tests call it directly) */
    @Synchronized
    internal fun reserve(bytes: Int, limitBps: Long, now: Long = System.nanoTime()): Long {
        allowance = minOf(limitBps.toDouble(), allowance + (now - last).coerceAtLeast(0L) / 1e9 * limitBps)
        last = now
        allowance -= bytes
        return if (allowance >= 0) 0L else ((-allowance) / limitBps * 1000).toLong()
    }

    suspend fun take(bytes: Int) {
        val l = limit
        if (l <= 0L || bytes <= 0) return
        val wait = reserve(bytes, l)
        if (wait > 0) delay(wait)
    }
}
