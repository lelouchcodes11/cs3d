package com.lagradost.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticsMaskTest {
    @Test
    fun masksQueryAndPathSecrets() {
        val masked = Diagnostics.mask("GET https://example.com/get.php?username=me&password=mySecret123&token=abcXYZ")
        assertFalse(masked.contains("mySecret123"), masked)
        assertFalse(masked.contains("abcXYZ"), masked)
        assertTrue(masked.contains("password=…"), masked)
        assertTrue(masked.contains("token=…"), masked)
    }

    @Test
    fun masksStremioEncodedJsonAndDebridKeys() {
        val encoded = "manifest https://pengu.uk/%7B%22auth_token%22%3A%22W8zK1InQyHdLHpm5Tsk1mFKsyFlwlu-FblWFFSgVFIU%22%7D/manifest.json: timeout"
        val maskedEncoded = Diagnostics.mask(encoded)
        assertFalse(maskedEncoded.contains("W8zK1InQyHdLHpm5"), maskedEncoded)
        assertTrue(maskedEncoded.contains("pengu.uk"), maskedEncoded)

        val debrid = "https://torrentio.strem.fun/providers=yts|realdebrid=SUPERSECRETKEY123/stream/movie/tt1.json"
        val maskedDebrid = Diagnostics.mask(debrid)
        assertFalse(maskedDebrid.contains("SUPERSECRETKEY123"), maskedDebrid)
        assertTrue(maskedDebrid.contains("realdebrid=…/stream/movie/tt1.json"), maskedDebrid)

        val json = """{"auth_token":"secret_val_42","ok":true}"""
        val maskedJson = Diagnostics.mask(json)
        assertFalse(maskedJson.contains("secret_val_42"), maskedJson)
        assertTrue(maskedJson.contains("\"auth_token\":\"…\""), maskedJson)
    }

    @Test
    fun masksBasicAuthUrlsAndBearerHeaders() {
        val iptv = "Failed to open http://myuser:mypass99@tv.example.com:8080/live/123.m3u8"
        val maskedIptv = Diagnostics.mask(iptv)
        assertFalse(maskedIptv.contains("myuser"), maskedIptv)
        assertFalse(maskedIptv.contains("mypass99"), maskedIptv)
        assertTrue(maskedIptv.contains("http://…:…@tv.example.com:8080/live/123.m3u8"), maskedIptv)

        val bearer = "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig"
        val maskedBearer = Diagnostics.mask(bearer)
        assertFalse(maskedBearer.contains("eyJhbGci"), maskedBearer)
        assertEquals("Authorization: Bearer …", maskedBearer)
    }

    @Test
    fun leavesOrdinaryLinesUntouched() {
        val plain = "11-19 17:56:13.215 I MpvPlayer: timing: first frame 2407 ms after loadfile"
        assertEquals(plain, Diagnostics.mask(plain))
    }
}
