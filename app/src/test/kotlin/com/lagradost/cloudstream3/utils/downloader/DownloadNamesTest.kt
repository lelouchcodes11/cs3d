package com.lagradost.cloudstream3.utils.downloader

import com.lagradost.cloudstream3.utils.SubtitleUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** File names of downloads: what Windows accepts, and which subtitles belong to which video */
class DownloadNamesTest {
    @Test
    fun aVeryLongNameIsCutToWhatWindowsTakes() {
        val name = DownloadFileManagement.sanitizeFilename("Episode 1 - " + "a long plot. ".repeat(40))
        assertTrue(name.length <= 200, "length ${name.length}")
        assertFalse(name.endsWith(".") || name.endsWith(" "))
        assertTrue(name.startsWith("Episode 1 - a long plot"))
    }

    @Test
    fun aCutDoesNotSplitACharacterInTwo() {
        val name = DownloadFileManagement.sanitizeFilename("a".repeat(199) + "😀" + "b".repeat(20))
        assertFalse(name.last().isHighSurrogate())
        assertEquals("a".repeat(199), name)
    }

    @Test
    fun shortNamesAreKeptAsTheyWere() {
        assertEquals("Season 1 Episode 2 - Pilot", DownloadFileManagement.sanitizeFilename("Season 1 Episode 2 - Pilot"))
        assertEquals("_CON", DownloadFileManagement.sanitizeFilename("CON"))
    }

    @Test
    fun aSubtitleNameIsMadeSafeForWindows() {
        assertEquals("Episode 1 English SDH", DownloadUtils.subtitleFileName("Episode 1", "English: SDH"))
        assertFalse(DownloadUtils.subtitleFileName("Episode 1", "Português/BR").contains('/'))
        val long = DownloadUtils.subtitleFileName("x".repeat(214), "English (Forced) from somewhere far away")
        assertTrue(long.length + ".vtt".length <= 255)
        assertTrue(long.startsWith("x".repeat(214) + " "))
    }

    @Test
    fun episodeTenSubtitlesAreNotEpisodeOnes() {
        val display = "Season 1 Episode 1.mp4"
        val clean = SubtitleUtils.cleanDisplayName(display)
        assertFalse(SubtitleUtils.isMatchingSubtitle("Season 1 Episode 10 English.vtt", display, clean))
        assertFalse(SubtitleUtils.isMatchingSubtitle("Season 1 Episode 12.srt", display, clean))
        assertTrue(SubtitleUtils.isMatchingSubtitle("Season 1 Episode 1 English.vtt", display, clean))
        assertTrue(SubtitleUtils.isMatchingSubtitle("Season 1 Episode 1.en.srt", display, clean))
        assertTrue(SubtitleUtils.isMatchingSubtitle("Season 1 Episode 1.vtt", display, clean))
        assertFalse(SubtitleUtils.isMatchingSubtitle("Season 1 Episode 1.mp4", display, clean))
    }
}
