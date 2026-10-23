package com.lagradost.desktop.net

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** Links that an extension got out of date on, repaired when they arrive */
class LinkRepairTest {
    private fun link(url: String, referer: String, headers: Map<String, String> = emptyMap()): ExtractorLink = runBlocking {
        newExtractorLink("test", "MovieBox [Hindi]", url) {
            this.referer = referer
            this.headers = headers
        }
    }

    private fun referer(l: ExtractorLink) = l.headers.entries.firstOrNull { it.key.equals("Referer", true) }?.value ?: l.referer

    @Test
    fun anOldMovieBoxRefererIsReplaced() {
        val l = link("https://bcdnw.hakunaymatata.com/vegamovies/abc.mp4?sign=1&t=2", "", mapOf("Referer" to "https://fmoviesunblocked.net/", "Origin" to "https://fmoviesunblocked.net"))
        LinkRepair.apply(l)
        assertEquals("https://movie-box.co/", referer(l))
        assertEquals("https://movie-box.co/", l.referer)
        assertEquals(false, l.headers.keys.any { it.equals("Origin", true) })
    }

    @Test
    fun theRightRefererStaysAsItIs() {
        val l = link("https://bcdnxw.hakunaymatata.com/tran-audio/20250609/abc.mp4", "", mapOf("Referer" to "https://movie-box.co/", "User-Agent" to "x"))
        LinkRepair.apply(l)
        assertEquals("https://movie-box.co/", referer(l))
        assertEquals("x", l.headers["User-Agent"])
    }

    @Test
    fun otherFilesOfThatHostAndOtherHostsAreLeftAlone() {
        val bt = link("https://bcdn.hakunaymatata.com/bt/abc.mp4", "https://vidlink.pro", mapOf("accept" to "*/*"))
        LinkRepair.apply(bt)
        assertEquals("https://vidlink.pro", bt.referer)
        val other = link("https://example.com/vegamovies/abc.mp4", "https://a.example/")
        LinkRepair.apply(other)
        assertEquals("https://a.example/", other.referer)
    }
}
