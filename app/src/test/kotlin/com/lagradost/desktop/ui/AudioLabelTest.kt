package com.lagradost.desktop.ui

import com.lagradost.cloudstream3.ui.player.AudioTrack
import com.lagradost.desktop.ui.screens.player.audioLabel
import kotlin.test.Test
import kotlin.test.assertEquals

class AudioLabelTest {
    @Test
    fun twoTracksOfOneLanguageAreToldApart() {
        val all = listOf(
            AudioTrack("hlsaudio:0", "3. Spanish", "spa", null, null, null),
            AudioTrack("hlsaudio:1", "4. Spanish (Latin America)", "spa", null, null, null),
            AudioTrack("hlsaudio:2", "8. Hindi", "hin", null, null, null),
        )
        assertEquals(listOf("Spanish", "Spanish (Latin America)", "Hindi"), all.mapIndexed { i, t -> audioLabel(t, i, all) })
        // without the list (the summary line) only the language
        assertEquals("Spanish", audioLabel(all[1], 1))
    }
}
