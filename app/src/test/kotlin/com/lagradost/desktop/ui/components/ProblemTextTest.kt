package com.lagradost.desktop.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProblemTextTest {
    @Test
    fun rawParserErrorBecomesPlainWords() {
        val raw = "Unrecognized token 'https': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')\n at [Source: REDACTED; line: 1, column: 1]JsonParser.java 2675"
        val p = ProblemText.of(raw)
        assertTrue(p.text.startsWith("The extension got an answer it could not read"))
        assertEquals(raw.take(400), p.detail)
    }

    @Test
    fun networkProblemsAreNamed() {
        assertTrue(ProblemText.of("java.net.UnknownHostException: Unable to resolve host \"x.example\"").text.startsWith("The site could not be found"))
        assertTrue(ProblemText.of("java.net.SocketTimeoutException: timeout").text.startsWith("The site took too long"))
        assertTrue(ProblemText.of("HTTP 403 Forbidden").text.startsWith("The site refused"))
    }

    @Test
    fun plainMessagesStayAsTheyAre() {
        val p = ProblemText.of("No episodes found for this title")
        assertEquals("No episodes found for this title", p.text)
        assertNull(p.detail)
    }

    @Test
    fun setupMessagesAreRecognised() {
        assertTrue(ProblemText.asksForSettings("Select sections from the extension's settings page to show here."))
        assertFalse(ProblemText.asksForSettings("timeout"))
        assertFalse(ProblemText.asksForSettings(null))
    }
}
