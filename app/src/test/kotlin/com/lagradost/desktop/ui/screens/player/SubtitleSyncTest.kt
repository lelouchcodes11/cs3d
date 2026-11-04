package com.lagradost.desktop.ui.screens.player

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubtitleSyncTest {
    /** 2 minutes in 100 ms bins: talking in bursts of 1-4 s with pauses; the loudness is noisy, music-like noise in the pauses */
    private fun talk(seed: Int): Pair<Map<Int, Float>, BooleanArray> {
        val r = Random(seed)
        val speech = BooleanArray(1200)
        var i = 0
        while (i < speech.size) {
            val on = 10 + r.nextInt(30)
            for (k in i until minOf(speech.size, i + on)) speech[k] = true
            i += on + 5 + r.nextInt(25)
        }
        val loud = speech.indices.associateWith { if (speech[it]) -22f + r.nextFloat() * 8f else -38f + r.nextFloat() * 14f }
        return loud to speech
    }

    private fun shown(speech: BooleanArray, lateBins: Int): Map<Int, Boolean> = speech.indices.associateWith { speech.getOrElse(it - lateBins) { false } }

    @Test
    fun findsLateSubtitles() {
        val (loud, speech) = talk(1)
        // lines show 1.5 s after the voice: the lines must move 15 bins earlier
        assertEquals(15, SubtitleSync.bestShift(loud, shown(speech, 15)))
    }

    @Test
    fun findsEarlySubtitles() {
        val (loud, speech) = talk(2)
        assertEquals(-32, SubtitleSync.bestShift(loud, shown(speech, -32)))
    }

    @Test
    fun inTimeStaysPut() {
        val (loud, speech) = talk(3)
        assertEquals(0, SubtitleSync.bestShift(loud, shown(speech, 1)))
    }

    @Test
    fun noiseChangesNothing() {
        val r = Random(4)
        val loud = (0 until 1200).associateWith { -30f + r.nextFloat() * 10f }
        val text = (0 until 1200).associateWith { r.nextInt(3) == 0 }
        assertNull(SubtitleSync.bestShift(loud, text))
    }
}

class SubtitleSyncEarlyTest {
    @Test
    fun clearMatchIn30Seconds() {
        val r = Random(5)
        val speech = BooleanArray(300)
        var i = 0
        while (i < speech.size) { val on = 10 + r.nextInt(30); for (k in i until minOf(speech.size, i + on)) speech[k] = true; i += on + 5 + r.nextInt(25) }
        val loud = speech.indices.associateWith { if (speech[it]) -22f + r.nextFloat() * 8f else -38f + r.nextFloat() * 14f }
        val text = speech.indices.associateWith { speech.getOrElse(it - 20) { false } }
        assertEquals(20, SubtitleSync.bestShift(loud, text, minPeak = 3f, minCorrelation = 0.3f, minLead = 1.6f))
    }

    @Test
    fun earlyRulesIgnoreNoise() {
        for (seed in 10..19) {
            val r = Random(seed)
            val loud = (0 until 300).associateWith { -30f + r.nextFloat() * 10f }
            val text = (0 until 300).associateWith { r.nextInt(3) == 0 }
            assertNull(SubtitleSync.bestShift(loud, text, minPeak = 3f, minCorrelation = 0.3f, minLead = 1.6f), "seed $seed")
        }
    }
}

class SubtitleCueTest {
    @Test
    fun srtAndVtt() {
        val srt = "1\n00:00:01,500 --> 00:00:03,250\nHi\n\n2\n01:02:03,040 --> 01:02:05,000\nThere\n"
        assertEquals(listOf(1.5 to 3.25, 3723.04 to 3725.0), SubtitleSync.parseCues(srt))
        val vtt = "WEBVTT\n\n00:01.000 --> 00:02.500\nShort\n"
        assertEquals(listOf(1.0 to 2.5), SubtitleSync.parseCues(vtt))
    }

    @Test
    fun ass() {
        val ass = "[Events]\nFormat: Layer, Start, End, Style, Text\nDialogue: 0,0:00:04.12,0:00:06.50,Default,,0,0,0,,Hello\n"
        assertEquals(listOf(4.12 to 6.5), SubtitleSync.parseCues(ass))
    }
}

class DelayInputTest {
    @Test
    fun typedDelays() {
        assertEquals(1500L, parseDelay("1500"))
        assertEquals(-250L, parseDelay(" -250 ms"))
        assertEquals(1500L, parseDelay("1.5 s"))
        assertEquals(-400L, parseDelay("-0,4s"))
        assertEquals(300L, parseDelay("+300"))
        assertNull(parseDelay("abc"))
    }
}
