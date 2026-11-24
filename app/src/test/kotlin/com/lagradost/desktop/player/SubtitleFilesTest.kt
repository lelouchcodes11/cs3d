package com.lagradost.desktop.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a downloaded "subtitle" is, and where its last line ends (a file for another cut of the film ends far from the film's end) */
class SubtitleFilesTest {
    private fun srt(vararg times: String) = times.mapIndexed { i, t -> "${i + 1}\n$t\nLine ${i + 1}\n" }.joinToString("\n").toByteArray()

    @Test
    fun anSrtKnowsWhereItsLastCueEnds() {
        val clean = SubtitleFiles.normalize(srt("00:00:01,000 --> 00:00:03,000", "01:02:03,400 --> 01:02:05,250"))
        assertNotNull(clean)
        assertEquals(".srt", clean.extension)
        assertEquals(2, clean.cues)
        assertEquals(((1 * 60 + 2) * 60 + 5) * 1000L + 250, clean.lastCueMs)
    }

    @Test
    fun anAssKnowsWhereItsLastCueEnds() {
        val ass = "[Script Info]\nTitle: x\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n" +
            "Dialogue: 0,0:00:01.00,0:00:02.50,Default,,0,0,0,,Hi\nDialogue: 0,0:10:00.00,0:10:30.20,Default,,0,0,0,,Bye\n"
        val clean = SubtitleFiles.normalize(ass.toByteArray())
        assertNotNull(clean)
        assertEquals(".ass", clean.extension)
        assertEquals(10 * 60_000L + 30_200, clean.lastCueMs)
    }

    @Test
    fun aChosenCharacterSetIsUsedAndAutomaticGuessesTheRest() {
        if (!com.lagradost.desktop.runtime.AndroidRuntime.isInitialized) com.lagradost.desktop.runtime.AndroidRuntime.init(java.nio.file.Files.createTempDirectory("subenc-test").toFile())
        if (runCatching { com.lagradost.desktop.runtime.AndroidRuntime.context }.isFailure) com.lagradost.desktop.runtime.AndroidRuntime.startApplication(android.app.Application())
        val text = "こんにちは世界"
        val sjis = "1\n00:00:01,000 --> 00:00:02,000\n$text\n".toByteArray(java.nio.charset.Charset.forName("Shift_JIS"))
        try {
            SubtitleEncoding.set("")
            // not valid UTF-8, so Automatic falls back to the Windows code page: letters, but not the Japanese
            assertTrue(!String(SubtitleFiles.normalize(sjis)!!.bytes, Charsets.UTF_8).contains(text))
            SubtitleEncoding.set("Shift_JIS")
            assertTrue(String(SubtitleFiles.normalize(sjis)!!.bytes, Charsets.UTF_8).contains(text))
            // a UTF-8 file with a byte order mark is still read right when UTF-8 is chosen
            SubtitleEncoding.set("UTF-8")
            val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "1\n00:00:01,000 --> 00:00:02,000\n$text\n".toByteArray(Charsets.UTF_8)
            assertTrue(String(SubtitleFiles.normalize(bom)!!.bytes, Charsets.UTF_8).let { it.contains(text) && !it.startsWith("﻿") })
        } finally {
            SubtitleEncoding.set("")
        }
    }

    @Test
    fun theTimingShapeTellsTheSameTalkFromAnotherCut() {
        fun film(offsetSec: Int) = srt(*(0 until 40).map { i ->
            val s = offsetSec + i * 90
            "%02d:%02d:%02d,000 --> %02d:%02d:%02d,500".format(s / 3600, s / 60 % 60, s % 60, (s + 2) / 3600, (s + 2) / 60 % 60, (s + 2) % 60)
        }.toTypedArray())
        val a = SubtitleFiles.normalize(film(30))!!
        val b = SubtitleFiles.normalize(film(32))!!
        val shifted = SubtitleFiles.normalize(film(30 + 20 * 60))!!
        assertEquals(30_000L, a.firstCueMs)
        assertEquals(40, a.profile.sum())
        // the same talk two seconds later is nearly the same shape, twenty minutes later is not
        assertTrue(SubtitleFiles.timingDistance(a.profile, b.profile) < 0.05)
        assertTrue(SubtitleFiles.timingDistance(a.profile, shifted.profile) > 0.3)
        assertEquals(0.0, SubtitleFiles.timingDistance(a.profile, a.profile))
        assertEquals(1.0, SubtitleFiles.timingDistance(a.profile, IntArray(0)))
    }

    @Test
    fun aBlockPageIsNoSubtitle() {
        assertNull(SubtitleFiles.normalize("<!DOCTYPE html><html><body>Blocked</body></html>".toByteArray()))
        assertNull(SubtitleFiles.normalize(ByteArray(0)))
        assertTrue(SubtitleFiles.normalize(srt("00:00:01,000 --> 00:00:02,000"))!!.cues == 1)
    }

    @Test
    fun anOldFileIsReadInItsLanguagesCodePage() {
        val arabic = "مرحبا بكم"
        val file = "1\n00:00:01,000 --> 00:00:02,000\n$arabic\n".toByteArray(charset("windows-1256"))
        assertTrue(String(SubtitleFiles.normalize(file, "ar")!!.bytes, Charsets.UTF_8).contains(arabic))
        assertTrue(String(SubtitleFiles.normalize(file, "Arabic")!!.bytes, Charsets.UTF_8).contains(arabic))
        assertEquals("windows-1251", SubtitleFiles.legacyCharset("ru").name().lowercase())
        assertEquals("Big5", SubtitleFiles.legacyCharset("zh-TW").name())
        assertEquals("GBK", SubtitleFiles.legacyCharset("Chinese (Simplified)").name())
        assertEquals("windows-1252", SubtitleFiles.legacyCharset("en").name().lowercase())
        assertEquals("windows-1252", SubtitleFiles.legacyCharset(null).name().lowercase())
        // names and OpenSubtitles' older codes for traditional Chinese
        assertEquals("Big5", SubtitleFiles.legacyCharset("Chinese (Taiwan)").name())
        assertEquals("Big5", SubtitleFiles.legacyCharset("zht").name())
        assertEquals("GBK", SubtitleFiles.legacyCharset("zh-CN").name())
    }

    @Test
    fun srtTimingsWithoutHoursAreMadeWhole() {
        val clean = SubtitleFiles.normalize("1\n00:01,000 --> 00:02,500\nHello\n\n2\n1:02,000 --> 1:04,000\nBye\n".toByteArray())
        assertNotNull(clean)
        assertEquals(".srt", clean.extension)
        assertEquals(2, clean.cues)
        val text = String(clean.bytes, Charsets.UTF_8)
        assertTrue(text.contains("00:00:01,000 --> 00:00:02,500") && text.contains("00:01:02,000 --> 00:01:04,000"))
        assertEquals(64_000L, clean.lastCueMs)
        // a file whose timings have hours is left as it is
        val whole = "1\n00:00:01,000 --> 00:00:02,000\nHi\n"
        assertEquals(whole, SubtitleFiles.padSrtHours(whole))
    }

    @Test
    fun webVttWithoutItsHeaderGetsOne() {
        val clean = SubtitleFiles.normalize("00:01.000 --> 00:02.000\nHello\n\n00:03.000 --> 00:04.000\nBye\n".toByteArray())
        assertNotNull(clean)
        assertEquals(".vtt", clean.extension)
        assertTrue(String(clean.bytes, Charsets.UTF_8).startsWith("WEBVTT\n\n00:01.000"))
        assertEquals(2, clean.cues)
    }

    @Test
    fun anAssSortedByStyleKnowsItsRealFirstAndLastCue() {
        val ass = "[Script Info]\nTitle: x\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n" +
            "Dialogue: 0,0:20:00.00,0:20:02.00,Default,,0,0,0,,Middle, of the film\nDialogue: 0,1:30:00.00,1:30:05.00,Default,,0,0,0,,End\n" +
            "Dialogue: 0,0:00:10.00,0:00:12.00,Sign,,0,0,0,,Title card\nDialogue: 0,0:01:00.00,0:01:30.00,Song,,0,0,0,,Opening\n"
        val clean = SubtitleFiles.normalize(ass.toByteArray())!!
        assertEquals(10_000L, clean.firstCueMs)
        assertEquals(90 * 60_000L + 5_000, clean.lastCueMs)
    }

    @Test
    fun anSrtWithACreditAtTheEndStillEndsWhereTheFilmDoes() {
        val clean = SubtitleFiles.normalize(srt("00:00:05,000 --> 00:00:06,000", "01:40:00,000 --> 01:40:02,000", "00:00:00,000 --> 00:00:03,000"))!!
        assertEquals(100 * 60_000L + 2_000, clean.lastCueMs)
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z -> for ((name, bytes) in entries) { z.putNextEntry(java.util.zip.ZipEntry(name)); z.write(bytes); z.closeEntry() } }
        return out.toByteArray()
    }

    @Test
    fun aZipGivesItsRealSubtitleInTheWantedLanguage() {
        val macJunk = byteArrayOf(0, 5, 22, 7, 0, 2, 0, 0) + ByteArray(80)
        val french = "1\n00:00:01,000 --> 00:00:02,000\nBonjour\n".toByteArray()
        val english = "1\n00:00:01,000 --> 00:00:02,000\nHello\n".toByteArray()
        // a Mac's file details come first: the subtitle after them is taken
        assertTrue(String(SubtitleFiles.normalize(zip("__MACOSX/._Film.srt" to macJunk, "Film.srt" to english))!!.bytes, Charsets.UTF_8).contains("Hello"))
        // two languages: the one named for the wanted language
        val both = zip("Film.fr.srt" to french, "Film.en.srt" to english)
        assertTrue(String(SubtitleFiles.normalize(both, "en")!!.bytes, Charsets.UTF_8).contains("Hello"))
        assertTrue(String(SubtitleFiles.normalize(both, "English")!!.bytes, Charsets.UTF_8).contains("Hello"))
        assertTrue(String(SubtitleFiles.normalize(both, "fr")!!.bytes, Charsets.UTF_8).contains("Bonjour"))
        // a readme before a subtitle that is not in the usual formats
        assertTrue(String(SubtitleFiles.normalize(zip("readme.txt" to "Downloaded from somewhere".toByteArray(), "Film.sub" to "{10}{20}Hello\n".toByteArray()))!!.bytes, Charsets.UTF_8).contains("Hello"))
    }

    @Test
    fun theFilesOfADownloadAreOfferedEpisodeFirst() {
        val names = listOf("Show.S01/", "Show.nfo", "Show.S01E01.srt", "Show.S01E05.srt", "Show.S01E06.srt")
        val order = SubtitleFiles.downloadOrder(names, "en", 1, 5)
        assertEquals(listOf(3, 2, 4), order)
        assertEquals(2, SubtitleFiles.episodeFit("show.1x05.srt", 1, 5))
        assertEquals(-2, SubtitleFiles.episodeFit("Show.S02E05.srt", 1, 5))
        assertEquals(1, SubtitleFiles.episodeFit("Anime - Episode 5.ass", null, 5))
        assertEquals(0, SubtitleFiles.episodeFit("Film.1080p.x264.srt", 1, 5))
        // languages: the wanted one first, nothing left out
        assertEquals(listOf(1, 0), SubtitleFiles.downloadOrder(listOf("Film.fr.srt", "Film.en.srt"), "en", null, null))
        // only junk: kept as it is
        assertEquals(listOf(0, 1), SubtitleFiles.downloadOrder(listOf("a.nfo", "b.nfo"), "en", null, null))
    }

    @Test
    fun aByteOrderMarkWinsOverAChosenCodePage() {
        if (!com.lagradost.desktop.runtime.AndroidRuntime.isInitialized) com.lagradost.desktop.runtime.AndroidRuntime.init(java.nio.file.Files.createTempDirectory("subenc-test").toFile())
        if (runCatching { com.lagradost.desktop.runtime.AndroidRuntime.context }.isFailure) com.lagradost.desktop.runtime.AndroidRuntime.startApplication(android.app.Application())
        val text = "Привет мир"
        val body = "1\n00:00:01,000 --> 00:00:02,000\n$text\n"
        try {
            SubtitleEncoding.set("windows-1256")
            val utf8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + body.toByteArray(Charsets.UTF_8)
            assertTrue(String(SubtitleFiles.normalize(utf8)!!.bytes, Charsets.UTF_8).contains(text))
            val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + body.toByteArray(Charsets.UTF_16LE)
            assertTrue(String(SubtitleFiles.normalize(utf16)!!.bytes, Charsets.UTF_8).contains(text))
        } finally {
            SubtitleEncoding.set("")
        }
    }
}
