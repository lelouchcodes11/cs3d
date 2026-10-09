package com.lagradost.cloudstream3.utils.videoskip

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.syncproviders.providers.MALApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VideoSkipIntegrationTest {

    @Test
    fun introDbSkipSupportsAnimeAndMovies() {
        val introDb = IntroDbSkip()
        assertTrue(introDb.supportedTypes.contains(TvType.Anime))
        assertTrue(introDb.supportedTypes.contains(TvType.OVA))
        assertTrue(introDb.supportedTypes.contains(TvType.AnimeMovie))
        assertTrue(introDb.supportedTypes.contains(TvType.Movie))
        assertTrue(introDb.supportedTypes.contains(TvType.TvSeries))
    }

    @Test
    fun theIntroDbSkipSupportsAnimeAndMovies() {
        val theIntroDb = TheIntroDBSkip()
        assertTrue(theIntroDb.supportedTypes.contains(TvType.Anime))
        assertTrue(theIntroDb.supportedTypes.contains(TvType.OVA))
        assertTrue(theIntroDb.supportedTypes.contains(TvType.AnimeMovie))
        assertTrue(theIntroDb.supportedTypes.contains(TvType.Movie))
        assertTrue(theIntroDb.supportedTypes.contains(TvType.TvSeries))
    }

    @Test
    fun introDbSegmentParsesMillisecondsOrSeconds() {
        val segWithMs = IntroDbSkip.Segment(
            startSec = null,
            endSec = null,
            startMs = 120_000L,
            endMs = 210_000L,
            confidence = 1.0,
            submissionCount = 5,
            updatedAt = null
        )
        val startFromMs = segWithMs.startMs ?: segWithMs.startSec?.let { (it * 1000.0).toLong() }
        val endFromMs = segWithMs.endMs ?: segWithMs.endSec?.let { (it * 1000.0).toLong() }
        assertEquals(120_000L, startFromMs)
        assertEquals(210_000L, endFromMs)

        val segWithSec = IntroDbSkip.Segment(
            startSec = 85.5,
            endSec = 175.5,
            startMs = null,
            endMs = null,
            confidence = 1.0,
            submissionCount = 3,
            updatedAt = null
        )
        val startFromSec = segWithSec.startMs ?: segWithSec.startSec?.let { (it * 1000.0).toLong() }
        val endFromSec = segWithSec.endMs ?: segWithSec.endSec?.let { (it * 1000.0).toLong() }
        assertEquals(85500L, startFromSec)
        assertEquals(175500L, endFromSec)
    }

    @Test
    fun malOAuthPayloadIncludesRedirectUri() {
        val payload = MALApi.Payload(
            requestId = 42,
            codeVerifier = "verifier123",
            redirectUri = "http://localhost:52526/mallogin"
        )
        assertEquals(42, payload.requestId)
        assertEquals("verifier123", payload.codeVerifier)
        assertEquals("http://localhost:52526/mallogin", payload.redirectUri)
    }

    @Test
    fun aniSkipSupportedTypes() {
        val aniSkip = AniSkip()
        assertTrue(aniSkip.supportedTypes.contains(TvType.Anime))
        assertTrue(aniSkip.supportedTypes.contains(TvType.OVA))
        assertTrue(aniSkip.supportedTypes.contains(TvType.AnimeMovie))
        assertTrue(aniSkip.supportedTypes.contains(TvType.TvSeries))
        assertTrue(aniSkip.supportedTypes.contains(TvType.Movie))
    }

    @Test
    fun aniSkipCleanTitle() {
        assertEquals("Sousou no Frieren", AniSkip.cleanTitle("Sousou no Frieren (Dub)"))
        assertEquals("Sousou no Frieren", AniSkip.cleanTitle("Sousou no Frieren [Sub]"))
        assertEquals("Jujutsu Kaisen", AniSkip.cleanTitle("Jujutsu Kaisen (Dubbed)"))
        assertEquals("Attack on Titan", AniSkip.cleanTitle("Attack on Titan [Uncensored]"))
        assertEquals("Solo Leveling", AniSkip.cleanTitle("Solo Leveling (English)"))
        assertEquals("Chainsaw Man", AniSkip.cleanTitle("Chainsaw Man"))
        assertEquals("One Piece", AniSkip.cleanTitle("One Piece [1080p]"))
    }

    @Test
    fun aniSkipSeasonNumberExtraction() {
        assertEquals(2, AniSkip.extractSeasonNumber("Attack on Titan Season 2"))
        assertEquals(2, AniSkip.extractSeasonNumber("Jujutsu Kaisen 2nd Season"))
        assertEquals(3, AniSkip.extractSeasonNumber("My Hero Academia 3rd Season"))
        assertEquals(4, AniSkip.extractSeasonNumber("Haikyuu!! 4th Season"))
        assertEquals(2, AniSkip.extractSeasonNumber("Bleach Part 2"))
        assertEquals(3, AniSkip.extractSeasonNumber("Mob Psycho 100 Season III"))
        assertEquals(null, AniSkip.extractSeasonNumber("Chainsaw Man"))
    }
}
