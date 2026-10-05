package com.lagradost.desktop.tools

import androidx.compose.runtime.withFrameNanos

/**
 * Dev tool (-Dcloudstream.framestats=1): asks for a frame on every refresh and records how long each took to arrive, so a screen that
 * costs more than a refresh (16.7 ms on 60 Hz) shows up as long intervals; read with the dev server's /framestats?s=<seconds>.
 */
object FrameStats {
    val enabled = System.getProperty("cloudstream.framestats") != null
    private val times = LongArray(1 shl 16)
    private val stamps = LongArray(1 shl 16)
    @Volatile private var count = 0L

    suspend fun run() {
        if (!enabled) return
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val i = (count and (times.size - 1).toLong()).toInt()
            times[i] = now - last
            stamps[i] = System.nanoTime()
            count++
            last = now
        }
    }

    /** The wall-clock times (HH:mm:ss.SSS) and lengths of the [n] longest frames of the last [seconds] seconds, in time order */
    fun worst(seconds: Int, n: Int): String {
        val until = System.nanoTime() - seconds * 1_000_000_000L
        val total = minOf(count, times.size.toLong()).toInt()
        val rows = ArrayList<Pair<Long, Long>>() // end of the frame (nanoTime), length
        for (k in 1..total) { val i = ((count - k) and (times.size - 1).toLong()).toInt(); if (stamps[i] < until) break; rows.add(stamps[i] to times[i]) }
        val now = System.nanoTime(); val wall = System.currentTimeMillis()
        val fmt = java.text.SimpleDateFormat("HH:mm:ss.SSS")
        return rows.sortedByDescending { it.second }.take(n).sortedBy { it.first }.joinToString("\n") { (end, len) -> fmt.format(java.util.Date(wall - (now - end) / 1_000_000)) + "  " + len / 1_000_000 + " ms" }
    }

    /** Frame intervals of the last [seconds] seconds: count, average, percentiles, and how many were over 20, 33 and 50 ms */
    fun report(seconds: Int): String {
        val until = System.nanoTime() - seconds * 1_000_000_000L
        val n = minOf(count, times.size.toLong()).toInt()
        val list = ArrayList<Long>()
        for (k in 1..n) {
            val i = ((count - k) and (times.size - 1).toLong()).toInt()
            if (stamps[i] < until) break
            list.add(times[i])
        }
        if (list.isEmpty()) return "no frames"
        list.sort()
        fun p(q: Double) = list[((list.size - 1) * q).toInt()] / 1e6
        fun over(ms: Int) = list.count { it > ms * 1_000_000L }
        return "frames=${list.size} avg=%.1f ms p50=%.1f p95=%.1f p99=%.1f max=%.1f | >20 ms: ${over(20)} >33 ms: ${over(33)} >50 ms: ${over(50)}".format(list.average() / 1e6, p(0.5), p(0.95), p(0.99), list.last() / 1e6)
    }
}
