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
