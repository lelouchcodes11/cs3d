package com.lagradost.desktop.player

import com.sun.net.httpserver.HttpServer
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClearKeyLicenseTest {
    private val kidHex = "c94a85da7e44563f8f3a687b5bef641c"
    private val keyHex = "445450834250887830adbde7c75caa2b"
    private fun b64(hex: String) = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(16) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() })

    @Test
    fun aJwkSetGivesHexPairs() {
        val json = """{"keys":[{"kty":"oct","k":"${b64(keyHex)}","kid":"${b64(kidHex)}"}],"type":"temporary"}"""
        assertEquals(listOf(kidHex to keyHex), ClearKeyLicense.parse(json))
    }

    @Test
    fun aPlainMapAndAnArrayAreRead() {
        assertEquals(listOf(kidHex to keyHex), ClearKeyLicense.parse("""{"$kidHex":"$keyHex"}"""))
        assertEquals(listOf(kidHex to keyHex), ClearKeyLicense.parse("""[{"kid":"$kidHex","key":"$keyHex"}]"""))
        assertEquals(listOf("" to keyHex), ClearKeyLicense.parse("""{"key":"$keyHex"}"""))
    }

    @Test
    fun anAnswerWithoutKeysGivesNone() {
        assertTrue(ClearKeyLicense.parse("Access denied.").isEmpty())
        assertTrue(ClearKeyLicense.parse("""{"error":"no"}""").isEmpty())
        assertTrue(ClearKeyLicense.parse("""{"keys":[]}""").isEmpty())
    }

    @Test
    fun theManifestNamesItsKeyIds() {
        val mpd = """<ContentProtection schemeIdUri="urn:mpeg:dash:mp4protection:2011" value="cenc" cenc:default_KID="C94A85DA-7E44-563F-8F3A-687B5BEF641C"/><ContentProtection cenc:default_KID="c94a85da-7e44-563f-8f3a-687b5bef641c"/>"""
        assertEquals(listOf(kidHex), ClearKeyLicense.manifestKids(mpd))
        assertEquals("""{"kids":["${b64(kidHex)}"],"type":"temporary"}""", ClearKeyLicense.requestBody(listOf(kidHex)))
    }

    @Test
    fun fetchAsksTheWayThePhoneDoes() {
        var posted: String? = null
        var referer: String? = null
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/m.mpd") { ex ->
            val body = """<MPD><ContentProtection cenc:default_KID="C94A85DA-7E44-563F-8F3A-687B5BEF641C"/></MPD>""".toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.use { it.write(body) }
        }
        server.createContext("/license") { ex ->
            posted = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            referer = ex.requestHeaders.getFirst("Referer")
            val body = """{"keys":[{"kty":"oct","k":"${b64(keyHex)}","kid":"${b64(kidHex)}"}],"type":"temporary"}""".toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val keys = ClearKeyLicense.fetch("$base/license?id=1", "$base/m.mpd", mapOf("Referer" to "https://watch.example/"), OkHttpClient())
            assertEquals(listOf(kidHex to keyHex), keys)
            assertEquals("""{"kids":["${b64(kidHex)}"],"type":"temporary"}""", posted)
            assertEquals("https://watch.example/", referer)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun aRefusedServerGivesNone() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/license") { ex -> ex.sendResponseHeaders(403, -1); ex.close() }
        server.start()
        try {
            assertTrue(ClearKeyLicense.fetch("http://127.0.0.1:${server.address.port}/license", null, emptyMap(), OkHttpClient()).isEmpty())
        } finally {
            server.stop(0)
        }
    }
}
