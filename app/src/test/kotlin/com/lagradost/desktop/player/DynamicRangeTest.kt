package com.lagradost.desktop.player

import com.lagradost.desktop.ui.fluent.DynamicRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The compressor presets (the phone's Light, Dialogue Boost, Heavy) and the ffmpeg filter that mpv gets for them */
class DynamicRangeTest {
    @Test
    fun offLeavesTheSoundAlone() {
        assertEquals("", DynamicRange.Off.filter)
    }

    @Test
    fun presetsAreTheOnesOfThePhone() {
        assertEquals(-18 to 4, DynamicRange.Light.thresholdDb to DynamicRange.Light.makeupDb)
        assertEquals(-24 to 12, DynamicRange.Dialogue.thresholdDb to DynamicRange.Dialogue.makeupDb)
        assertEquals(-30 to 16, DynamicRange.Heavy.thresholdDb to DynamicRange.Heavy.makeupDb)
    }

    @Test
    fun theFilterTakesLinearValuesNotDecibels() {
        // 10 ^ (-24 / 20) = 0.0631, 10 ^ (12 / 20) = 3.9811; a dot, whatever the language of the PC
        assertEquals(
            "lavfi=[acompressor=threshold=0.06310:ratio=8:attack=5:release=400:makeup=3.98107:detection=peak]",
            DynamicRange.Dialogue.filter,
        )
    }

    @Test
    fun everyPresetStaysInsideWhatAcompressorAccepts() {
        for (d in DynamicRange.entries.filter { it != DynamicRange.Off }) {
            val threshold = Regex("threshold=([0-9.]+)").find(d.filter)!!.groupValues[1].toDouble()
            val makeup = Regex("makeup=([0-9.]+)").find(d.filter)!!.groupValues[1].toDouble()
            // ffmpeg: threshold 0.000976563 .. 1, makeup 1 .. 64
            assertTrue(threshold in 0.000976563..1.0, "$d threshold $threshold")
            assertTrue(makeup in 1.0..64.0, "$d makeup $makeup")
        }
    }
}
