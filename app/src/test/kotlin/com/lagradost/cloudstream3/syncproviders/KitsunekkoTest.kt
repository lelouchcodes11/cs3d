package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.syncproviders.providers.Kitsunekko
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KitsunekkoTest {
    private val folders = listOf("3-gatsu no Lion", "Shingeki no Kyojin", "Shingeki no Kyojin Season 2", "Sousou no Frieren", "Overlord")

    @Test
    fun findsTheFolderByItsWords() {
        assertEquals("Sousou no Frieren", Kitsunekko.matchFolders(folders, "Sousou no Frieren").first())
        assertEquals("Shingeki no Kyojin", Kitsunekko.matchFolders(folders, "Shingeki no Kyojin").first())
    }

    @Test
    fun aShortTitleNeedsAllItsWords() {
        assertTrue(Kitsunekko.matchFolders(folders, "Overlord").contains("Overlord"))
        assertTrue(Kitsunekko.matchFolders(folders, "Berserk").isEmpty())
    }

    @Test
    fun readsTheEpisodeFromFileNames() {
        assertTrue(Kitsunekko.hasEpisode("3-gatsu no Lion 004.srt", 4))
        assertTrue(Kitsunekko.hasEpisode("Frieren - E05.ass", 5))
        assertFalse(Kitsunekko.hasEpisode("3-gatsu no Lion 014.srt", 4))
        assertFalse(Kitsunekko.hasEpisode("Show 1080p 02.srt", 10))
    }
}
