package com.lagradost.desktop.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The shared download speed limit: a second's worth goes at once, more waits in proportion */
class DownloadThrottleTest {
    private val mb = 1024L * 1024L

    @Test
    fun overTheLimitWaitsInProportion() {
        val t0 = 1_000_000_000L
        // the bucket fills to one second (1 MB) after a long pause, then 3 MB more wait about 2 s
        DownloadThrottle.reserve(0, mb, t0)
        val first = DownloadThrottle.reserve(mb.toInt(), mb, t0 + 5_000_000_000L)
        assertEquals(0L, first)
        val wait = DownloadThrottle.reserve((2 * mb).toInt(), mb, t0 + 5_000_000_000L)
        assertTrue(wait in 1_900L..2_100L, "waited $wait ms")
    }
}
