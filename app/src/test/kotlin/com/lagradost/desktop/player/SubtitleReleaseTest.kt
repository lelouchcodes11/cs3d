package com.lagradost.desktop.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubtitleReleaseTest {
    private val video = "Interstellar.2014.IMAX.1080p.BluRay.x264-SPARKS.mkv"

    @Test
    fun sameGroupKindAndCutScoresHighest() = assertEquals(3, SubtitleRelease.match("Interstellar.2014.IMAX.1080p.BluRay.x264-SPARKS", video))

    @Test
    fun sameKindOnlyScoresOne() = assertEquals(1, SubtitleRelease.match("Interstellar.2014.1080p.BluRay.x265-RARBG", "Interstellar.2014.1080p.BluRay.x264-FGT.mkv"))

    @Test
    fun anotherKindScoresNothing() = assertEquals(0, SubtitleRelease.match("Interstellar.2014.720p.WEB-DL.x264-GROUP", "Interstellar.2014.1080p.BluRay.x264-FGT.mkv"))

    @Test
    fun webDlAndWebRipAreTheSameKind() = assertEquals(1, SubtitleRelease.match("Show.S01E01.1080p.WEB-DL", "Show.S01E01.720p.WEBRip.x265.mkv"))

    @Test
    fun codecIsNotAGroup() = assertEquals(null, SubtitleRelease.groupOf("Movie.2020.1080p.BluRay-x264"))

    @Test
    fun plainNamesScoreNothing() {
        assertEquals(0, SubtitleRelease.match("English", "FibWatch"))
        assertTrue(SubtitleRelease.match("English 3", "[Fibwatch.Com]Interstellar.2014.Dual.1080P.mkv") == 0)
    }
}
