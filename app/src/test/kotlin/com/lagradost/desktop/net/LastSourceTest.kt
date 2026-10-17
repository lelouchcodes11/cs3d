package com.lagradost.desktop.net

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/** The key that tells "the same source" in the next episode, and the sizes read from source names */
class LastSourceTest {
    private fun link(source: String, name: String): ExtractorLink = runBlocking { newExtractorLink(source, name, "https://example.invalid/v.mkv") { quality = 1080 } }

    @Test
    fun sameSourceInTwoEpisodesHasOneKey() {
        val a = link("AnimePahe", "AnimePahe - Vodes [SUB] S01E03 1080p [412 MB]")
        val b = link("AnimePahe", "AnimePahe - Vodes [SUB] S01E04 1080p [398 MB]")
        assertEquals(LastSource.key(a), LastSource.key(b))
    }

    @Test
    fun anotherServerIsAnotherKey() {
        val a = link("HiAnime", "HiAnime MegaPlay [SUB]")
        val b = link("HiAnime", "HiAnime VidStreaming [SUB]")
        assertNotEquals(LastSource.key(a), LastSource.key(b))
    }

    @Test
    fun sizeIsReadFromTheName() {
        assertEquals(2.1, SourceWeight.sizeGb(link("x", "Torrentio 1080p 💾 2.1 GB 👤 120"))!!, 0.001)
        assertEquals(0.5, SourceWeight.sizeGb(link("x", "Mirror [512 MB]"))!!, 0.001)
        assertNull(SourceWeight.sizeGb(link("x", "MegaPlay 1080p")))
    }
}
