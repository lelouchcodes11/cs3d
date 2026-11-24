package com.lagradost.cloudstream3.ui.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A subtitle is in the wanted language when the language is the same, with or without a region on one side */
class SubtitleLanguageMatchTest {
    private fun sub(code: String) = SubtitleData("x", "", "https://a.example/x.srt", SubtitleOrigin.URL, "text/x-subrip", emptyMap(), code)

    @Test
    fun aRegionOnOneSideStillMatches() {
        assertTrue(sub("pt").matchesLanguageCode("pt-BR"))
        assertTrue(sub("pt-BR").matchesLanguageCode("pt"))
        assertTrue(sub("en").matchesLanguageCode("en"))
        assertFalse(sub("pt-PT").matchesLanguageCode("pt-BR"))
        assertFalse(sub("zh").matchesLanguageCode("zh-TW"))
        assertFalse(sub("es").matchesLanguageCode("en"))
    }
}
