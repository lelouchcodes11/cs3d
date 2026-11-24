package com.lagradost.desktop.tmdb

import com.lagradost.desktop.notify.UpdateNotices
import com.lagradost.desktop.tmdb.Tmdb.SearchHit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Which TMDB search result is the title an extension showed */
class TmdbMatchTest {
    private fun hit(name: String, year: Int?, animated: Boolean = false, original: String? = null) = SearchHit(name, original, year, animated)

    @Test
    fun onlyAResultOfThatNameIsTaken() {
        // a search narrowed by a later season's year: another series of that year is no match
        assertNull(Tmdb.bestNamed(listOf(hit("Talking Bad", 2013)), "Breaking Bad", 2013, anime = false))
        assertEquals(1, Tmdb.bestNamed(listOf(hit("Talking Bad", 2013), hit("Breaking Bad", 2008)), "Breaking Bad", 2013, anime = false))
        // case and punctuation do not matter
        assertEquals(0, Tmdb.bestNamed(listOf(hit("Avengers: Endgame", 2019)), "avengers endgame", null, anime = false))
        // nor whether the extension shows the original title
        assertEquals(0, Tmdb.bestNamed(listOf(hit("Money Heist", 2017, original = "La Casa de Papel")), "La casa de papel", null, anime = false))
    }

    @Test
    fun ofTwoWithTheSameNameTheRightOneIsTaken() {
        val dune = listOf(hit("Dune", 2021), hit("Dune", 1984))
        assertEquals(1, Tmdb.bestNamed(dune, "Dune", 1984, anime = false))
        assertEquals(0, Tmdb.bestNamed(dune, "Dune", 2021, anime = false))
        assertEquals(0, Tmdb.bestNamed(dune, "Dune", null, anime = false))
        // an anime page: the animated series, not the live-action one listed first
        val onePiece = listOf(hit("One Piece", 2023), hit("One Piece", 1999, animated = true))
        assertEquals(1, Tmdb.bestNamed(onePiece, "One Piece", null, anime = true))
        assertEquals(0, Tmdb.bestNamed(onePiece, "One Piece", null, anime = false))
    }

    @Test
    fun episodesSeenAreTheOnesBeforeTheResumePoint() {
        // resume at episode 6 (episode 5 finished): 5 seen, a new episode 6 is not "caught up"
        assertEquals(5, UpdateNotices.seenBefore(6))
        assertNull(UpdateNotices.seenBefore(1))
    }
}
