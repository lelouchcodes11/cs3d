package com.lagradost.desktop.player

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Translated subtitles keep every timing and style; only the words change (no network: the "translation" is upper case) */
class SubtitleTranslateTest {
    private fun upper(text: String, ext: String) = runBlocking {
        SubtitleTranslate.rewrite(text, ext) { words -> words.map { it.uppercase() } }
    }

    @Test
    fun srtKeepsTimingAndLineBreaks() {
        val srt = "1\n00:00:01,000 --> 00:00:02,500\nHello there\nfriend\n\n2\n00:00:03,000 --> 00:00:04,000\n- Hi!\n\n3\n00:00:05,000 --> 00:00:06,000\n♪ 123 ♪\n"
        assertEquals(
            "1\n00:00:01,000 --> 00:00:02,500\nHELLO THERE\nFRIEND\n\n2\n00:00:03,000 --> 00:00:04,000\n- HI!\n\n3\n00:00:05,000 --> 00:00:06,000\n♪ 123 ♪\n",
            upper(srt, ".srt"),
        )
    }

    @Test
    fun vttHeaderStays() {
        val vtt = "WEBVTT\n\n00:01.000 --> 00:02.000 align:start\nGood night\n"
        assertEquals("WEBVTT\n\n00:01.000 --> 00:02.000 align:start\nGOOD NIGHT\n", upper(vtt, ".vtt"))
    }

    @Test
    fun assKeepsStyleAndPositionTag() {
        val ass = "[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n" +
            "Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,{\\an8}Where, are{\\i1} you{\\i0}?\\NHere\n" +
            "Dialogue: 0,0:00:03.00,0:00:04.00,Sign,,0,0,0,,{\\p1}m 0 0 l 10 10{\\p0}\n"
        assertEquals(
            "[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n" +
                "Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,{\\an8}WHERE, ARE YOU?\\NHERE\n" +
                "Dialogue: 0,0:00:03.00,0:00:04.00,Sign,,0,0,0,,{\\p1}m 0 0 l 10 10{\\p0}\n",
            upper(ass, ".ass"),
        )
    }

    @Test
    fun anUnansweredCueStaysAsItWas() {
        val srt = "1\n00:00:01,000 --> 00:00:02,000\nLine one\nline two\n"
        assertEquals(srt, runBlocking { SubtitleTranslate.rewrite(srt, ".srt") { words -> words.map { null } } })
    }

    @Test
    fun cuesWithoutBlankLinesBetweenThemKeepTheirTimings() {
        val srt = "1\n00:00:01,000 --> 00:00:02,000\nHello\nthere\n2\n00:00:03,000 --> 00:00:04,000\nBye\n"
        assertEquals("1\n00:00:01,000 --> 00:00:02,000\nHELLO\nTHERE\n2\n00:00:03,000 --> 00:00:04,000\nBYE\n", upper(srt, ".srt"))
        val vtt = "WEBVTT\n\n00:01.000 --> 00:02.000\nOne\n00:03.000 --> 00:04.000\nTwo\n"
        assertEquals("WEBVTT\n\n00:01.000 --> 00:02.000\nONE\n00:03.000 --> 00:04.000\nTWO\n", upper(vtt, ".vtt"))
    }

    @Test
    fun noCuesGivesNull() {
        assertNull(upper("just some text", ".srt"))
    }
}
