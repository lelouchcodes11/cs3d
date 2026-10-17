package com.lagradost.desktop.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The pure parts of "Translate on-screen text": the OCR answer, the clean-up of Japanese lines, lines becoming sentences, the translation answer */
class ScreenTextTest {
    @Test
    fun theOcrAnswerIsRead() {
        val ocr = ScreenText.parse("""{"lang":"en-US","w":1280,"h":720,"lines":[{"t":"Durability: 1090 / 1090","x":149,"y":272,"w":349,"h":34}]}""")
        assertNull(ocr.error)
        assertEquals(1280f, ocr.w)
        assertEquals(720f, ocr.h)
        assertEquals(1, ocr.lines.size)
        assertEquals("Durability: 1090 / 1090", ocr.lines[0].t)
        assertEquals(272f, ocr.lines[0].y)
    }

    @Test
    fun aMissingLanguagePackIsReportedWithWhatIsThere() {
        val ocr = ScreenText.parse("""{"error":"no-engine","wanted":"ja","available":"en-GB,en-US"}""")
        assertEquals("no-engine", ocr.error)
        assertEquals("en-GB,en-US", ocr.available)
        assertTrue(ocr.lines.isEmpty())
    }

    @Test
    fun windowsSpacesBetweenJapaneseLettersAreTakenOut() {
        assertEquals("製作アイテムの適用", ScreenText.clean("製作 アイテム の 適用"))
        // between Latin words and between Latin and Japanese a space stays
        assertEquals("Lv 12 の敵", ScreenText.clean("Lv 12 の敵"))
        assertEquals("Hello  world".replace("  ", " "), ScreenText.clean("Hello  world"))
    }

    @Test
    fun linesUnderEachOtherAreOneSentenceAndFarOnesAreNot() {
        val lines = listOf(
            ScreenText.OcrLine("Chance to trigger", 100f, 100f, 300f, 30f),
            ScreenText.OcrLine("the skill Five Strikes", 100f, 134f, 280f, 30f),
            ScreenText.OcrLine("Durability: 1090", 100f, 400f, 250f, 30f),
            ScreenText.OcrLine("Status window", 700f, 100f, 200f, 30f),
        )
        val groups = ScreenText.merge(lines)
        assertEquals(3, groups.size)
        val sentence = groups.first { it.text.startsWith("Chance") }
        assertEquals("Chance to trigger the skill Five Strikes", sentence.text)
        assertEquals(2, sentence.lines)
        assertEquals(100f, sentence.x)
        assertEquals(64f, sentence.h)
    }

    @Test
    fun japaneseLinesAreJoinedWithoutASpace() {
        val groups = ScreenText.merge(listOf(ScreenText.OcrLine("製作アイテムの", 50f, 50f, 300f, 40f), ScreenText.OcrLine("適用される等級", 50f, 94f, 300f, 40f)))
        assertEquals(1, groups.size)
        assertEquals("製作アイテムの適用される等級", groups[0].text)
    }

    @Test
    fun theTranslationAnswerIsJoinedFromItsSegments() {
        val body = """[[["Durabilidad: ","Durability: ",null,null,3],["1090 / 1090","1090 / 1090",null,null,3]],null,"en"]"""
        assertEquals("Durabilidad: 1090 / 1090", ScreenText.parseTranslation(body))
    }
}
