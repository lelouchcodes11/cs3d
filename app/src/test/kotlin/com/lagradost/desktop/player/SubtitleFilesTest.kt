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
}
