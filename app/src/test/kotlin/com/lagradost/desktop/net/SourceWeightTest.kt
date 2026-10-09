package com.lagradost.desktop.net

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The sources the automatic choice leaves for later: 4K, REMUX and very large files (names as StreamPlay and CineStream write them) */
class SourceWeightTest {
    private fun link(name: String, quality: Int): ExtractorLink = runBlocking { newExtractorLink("test", name, "https://example.invalid/v.mkv") { this.quality = quality } }

    @Test
    fun fourKIsHeavy() {
        assertTrue(SourceWeight.heavy(link("MoviesDrive [Hub-Cloud] BluRay | HEVC | 10bit | 7.07 GB", 2160)))
    }

    @Test
    fun aRemuxIsHeavyAtAnyPictureSize() {
        assertTrue(SourceWeight.heavy(link("DahmerMovies DTS-HD MA 5 1 HEVC REMUX-FraMeSToR", 1080)))
    }

    @Test
    fun aHugeFileIsHeavy() {
        assertTrue(SourceWeight.heavy(link("4Khdhub [Hub-Cloud] BluRay | 29.35 GB", 1080)))
        assertFalse(SourceWeight.heavy(link("Bollyflix [GDFlix] BluRay | 17.77GB", 1080)))
    }

    @Test
    fun anOrdinaryFullHdSourceIsNot() {
        assertFalse(SourceWeight.heavy(link("Allmovieland [Tamil]", 1080)))
        assertFalse(SourceWeight.heavy(link("Skymovies [Pixeldrain] | H.264 | 3.05 GB", 1080)))
        assertFalse(SourceWeight.heavy(link("Rtally [FSLv2] | 474.15 MB", 480)))
    }

    @Test
    fun noLinkIsNotHeavy() {
        assertEquals(false, SourceWeight.heavy(null))
    }
}
