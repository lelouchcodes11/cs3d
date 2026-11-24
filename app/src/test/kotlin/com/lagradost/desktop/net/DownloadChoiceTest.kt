package com.lagradost.desktop.net

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** The source a download takes by itself under each "Download quality" choice */
class DownloadChoiceTest {
    private fun link(name: String, q: Int): ExtractorLink = runBlocking { newExtractorLink("CineStream", name, "https://example.invalid/$name.mkv") { quality = q } }

    // what a CineStream film offers (the 11.4 GB one was taken before)
    private val bluray = link("HubCloud BluRay H.264 10.6 GB", 1080)
    private val remux = link("4KHDHub 2160p REMUX 66.68 GB", 2160)
    private val web1080 = link("V-Cloud 1080p WEB-DL [2.3 GB]", 1080)
    private val web720 = link("V-Cloud 720p [1.1 GB]", 720)
    private val web480 = link("V-Cloud 480p [450 MB]", 480)
    private val auto = link("FibWatch Auto", 0)
    private val all = listOf(remux, bluray, auto, web1080, web480, web720)

    private fun first(mode: DownloadQuality) = all.sortedWith(DownloadChoice.comparator(mode)).first()

    @Test
    fun upTo1080pTakesTheBest1080pThatIsNotHuge() = assertEquals(web1080, first(DownloadQuality.Fhd))

    @Test
    fun upTo720pTakes720p() = assertEquals(web720, first(DownloadQuality.Hd))

    @Test
    fun smallestTakesTheSmallestKnownFile() = assertEquals(web480, first(DownloadQuality.Smallest))

    @Test
    fun bestKeepsTheOrderItWasGiven() = assertEquals(all, all.sortedWith(DownloadChoice.comparator(DownloadQuality.Best)))

    @Test
    fun withNothingWithinTheLimitTheLightestOfTheRestComesFirst() {
        val big = listOf(remux, bluray).sortedWith(DownloadChoice.comparator(DownloadQuality.Hd))
        assertEquals(bluray, big.first())
    }
}
