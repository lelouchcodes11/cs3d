package com.lagradost.desktop.ui.screens.details

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.VideoWatchState
import com.lagradost.desktop.tmdb.Tmdb.TmdbEpisode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The episode list of a title page: season packs are told from episodes, and TMDB fills what an extension left out (only when it is the same season) */
class EpisodeDataTest {
    private fun ep(n: Int, name: String? = null, poster: String? = null, description: String? = null, season: Int? = 1) = ResultEpisode(
        headerName = "", name = name, poster = poster, episode = n, seasonIndex = null, season = season, data = "d$n", apiName = "t", id = n, index = n - 1,
        position = 0, duration = 0, score = null, description = description, isFiller = null, tvType = TvType.TvSeries, parentId = 1, videoWatchState = VideoWatchState.None,
    )

    private fun tm(vararg e: TmdbEpisode) = e.associateBy { it.number }

    private fun t(n: Int, name: String = "Title $n") = TmdbEpisode(n, name, "Synopsis $n", "https://image.tmdb.org/t/p/original/$n.jpg", 8.5, 55)

    @Test
    fun archiveEntriesAreSeasonPacks() {
        assertTrue(isSeasonPack(ep(17, "S01 – When.Life.Gives.You.Tangerines.S01.1080p.NF.WEB-DL.Multi.DDP5.1.H.264-4kHdHub.Com.zip [1080p, 48.46 GB]")))
        assertTrue(isSeasonPack(ep(1, "Show S02 Complete Pack 720p")))
        assertFalse(isSeasonPack(ep(3, "Episode 3")))
        assertFalse(isSeasonPack(ep(4, "The Zip Line")))
        assertFalse(isSeasonPack(ep(5, null)))
    }

    @Test
    fun packNameIsCleanedAndSplit() {
        val (title, meta) = packParts("S01 – When.Life.Gives.You.Tangerines.S01.1080p.NF.WEB-DL.Multi.DDP5.1.HDR-DV.H.265-4kHdHub.Com.zip [1080p, 36.56 GB]")
        assertEquals("1080p NF WEB-DL Multi DDP5.1 HDR-DV H.265", title)
        assertEquals(listOf("1080p", "36.56 GB"), meta)
        assertEquals("1080p NF WEB-DL Multi AAC5.1 AV1", packParts("S01 – Show.S01.1080p.NF.WEB-DL.Multi.AAC5.1.AV1-4kHdHub.Com.zip [1080p, 11.35 GB]").first)
    }

    @Test
    fun missingStillsAndSynopsesAreFilledForTheSameSeason() {
        val sent = listOf(ep(1, "Episode 1"), ep(2, "Episode 2", poster = "https://site/own2.jpg"), ep(3, null))
        val out = withTmdbEpisodes(sent, mapOf(1 to tm(t(1), t(2), t(3))), anime = false)
        assertEquals("Title 1", out[0].name)
        assertEquals("https://image.tmdb.org/t/p/original/1.jpg", out[0].poster)
        assertEquals("Synopsis 1", out[0].description)
        // what the extension sent stays
        assertEquals("https://site/own2.jpg", out[1].poster)
        assertEquals("Synopsis 2", out[1].description)
        assertEquals(55, out[2].runTime)
    }

    @Test
    fun anotherSeasonIsNotTaken() {
        // the extension names its episodes and the names are not TMDB's: its own data stays, nothing is mixed in
        val sent = listOf(ep(1, "Pilot"), ep(2, "Second light"), ep(3, "Home"))
        val out = withTmdbEpisodes(sent, mapOf(1 to tm(t(1, "A"), t(2, "B"), t(3, "C"))), anime = false)
        assertEquals(sent, out)
        // numbers that do not fit
        val few = withTmdbEpisodes(listOf(ep(1), ep(2), ep(30), ep(31), ep(32)), mapOf(1 to tm(t(1), t(2))), anime = false)
        assertNull(few[0].description)
        // an anime without names is never matched by number alone
        assertEquals(listOf(ep(1)), withTmdbEpisodes(listOf(ep(1)), mapOf(1 to tm(t(1))), anime = true))
    }

    @Test
    fun packsAreLeftAlone() {
        val pack = ep(17, "S01 – Show.S01.1080p.zip [1080p, 1 GB]")
        val out = withTmdbEpisodes(listOf(ep(1), pack), mapOf(1 to tm(t(1), t(17))), anime = false)
        assertEquals(pack, out[1])
    }
}
