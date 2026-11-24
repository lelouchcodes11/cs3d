package com.lagradost.desktop.player

import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HlsSegmentStreamTest {
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(62) { 1 }

    private fun ts(packets: Int) = ByteArray(packets * 188).also { b -> for (p in 0 until packets) { b[p * 188] = 0x47; b[p * 188 + 1] = (p and 0x1F).toByte() } }

    @Test
    fun fakeHeaderIsFoundFromTheFirstBytes() {
        val body = png + ts(20)
        assertNull(HlsProxy.videoStart(body, 300, false), "too few bytes to tell")
        assertEquals(png.size, HlsProxy.videoStart(body, png.size + 5 * 188, false))
        assertEquals(0, HlsProxy.videoStart(ts(20), 5 * 188, false))
        assertEquals(body.size - png.size, HlsProxy.withoutDisguise(body).size)
        assertEquals(0x47.toByte(), HlsProxy.withoutDisguise(body)[0])
    }

    @Test
    fun aShortBodyIsNeverCut() {
        // a 16-byte key with 0x47 in it: before, it lost the bytes in front of that one
        val key = ByteArray(16) { (it * 7).toByte() }.also { it[5] = 0x47 }
        assertEquals(0, HlsProxy.videoStart(key, key.size, true))
        assertTrue(HlsProxy.withoutDisguise(key).contentEquals(key))
    }

    @Test
    fun thePlayersFromTheStartRequestUsesTheSegmentFetchedAhead() {
        val segment = png + ts(50)
        val gets = java.util.concurrent.atomic.AtomicInteger(0)
        val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 8).apply {
            executor = java.util.concurrent.Executors.newCachedThreadPool()
            createContext("/v.m3u8") { ex ->
                val text = "#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXT-X-PLAYLIST-TYPE:VOD\n#EXTINF:10.0,\nseg-0.png\n#EXT-X-ENDLIST\n".toByteArray()
                ex.sendResponseHeaders(200, text.size.toLong()); ex.responseBody.use { it.write(text) }
            }
            createContext("/seg-0.png") { ex ->
                gets.incrementAndGet()
                ex.sendResponseHeaders(200, segment.size.toLong()); ex.responseBody.use { it.write(segment) }
            }
            start()
        }
        try {
            val playlist = URI(HlsProxy.wrap("http://127.0.0.1:${server.address.port}/v.m3u8", emptyMap())).toURL().readText()
            val seg = playlist.lineSequence().first { it.contains("/seg?") }
            Thread.sleep(300) // the segment fetched ahead is on its way or in
            // mpv asks for every segment like this: before, that went to the range path (a second download, no header cut)
            val conn = URI(seg).toURL().openConnection().apply { setRequestProperty("Range", "bytes=0-") }
            val body = conn.getInputStream().use { it.readBytes() }
            assertEquals(segment.size - png.size, body.size)
            assertEquals(0x47.toByte(), body[0])
            assertEquals(1, gets.get(), "downloaded ${gets.get()} times")
        } finally {
            server.stop(0)
        }
    }

    private fun serveMaster(text: String, block: (String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 8).apply {
            executor = java.util.concurrent.Executors.newCachedThreadPool()
            createContext("/") { ex ->
                val body = (if (ex.requestURI.path == "/master.m3u8") text else "#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXTINF:10.0,\nseg-0.ts\n#EXT-X-ENDLIST\n").toByteArray()
                ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.use { it.write(body) }
            }
            start()
        }
        try { block("http://127.0.0.1:${server.address.port}/master.m3u8") } finally { server.stop(0) }
    }

    @Test
    fun aStreamWithSeveralAudioLanguagesGetsTheOneChosenBefore() {
        val master = "#EXTM3U\n" +
            "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"aud\",NAME=\"English\",LANGUAGE=\"en\",DEFAULT=YES,AUTOSELECT=YES,URI=\"en.m3u8\"\n" +
            "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"aud\",NAME=\"Hindi\",LANGUAGE=\"hin\",DEFAULT=NO,AUTOSELECT=YES,URI=\"hi.m3u8\"\n" +
            "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"aud\",NAME=\"Tamil\",LANGUAGE=\"ta\",DEFAULT=NO,AUTOSELECT=YES,URI=\"ta.m3u8\"\n" +
            "#EXT-X-STREAM-INF:BANDWIDTH=2800000,RESOLUTION=1280x720,AUDIO=\"aud\"\nv.m3u8\n"
        serveMaster(master) { url ->
            val address = HlsProxy.wrap(url, emptyMap(), audioLanguage = "hi")
            val text = URI(address).toURL().readText()
            val audio = text.lineSequence().filter { it.contains("TYPE=AUDIO") }.toList()
            assertEquals(1, audio.size, text)
            assertTrue(audio[0].contains("NAME=\"Hindi\"") && audio[0].contains("DEFAULT=YES"), audio[0])
            val (all, picked) = HlsProxy.audioRenditions(address)!!
            assertEquals(listOf("English", "Hindi", "Tamil"), all.map { it.name })
            assertEquals(1, picked)
            // picked in the menu: the next open of the same master gets that one, whatever was chosen before
            HlsProxy.chooseAudio(address, 2)
            val again = URI(HlsProxy.wrap(url, emptyMap(), audioLanguage = "hi")).toURL().readText()
            assertTrue(again.lineSequence().single { it.contains("TYPE=AUDIO") }.contains("NAME=\"Tamil\""), again)
        }
    }

    @Test
    fun audioInSeveralGroupsIsLeftAsItIs() {
        val master = "#EXTM3U\n" +
            "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"lo\",NAME=\"English\",LANGUAGE=\"en\",DEFAULT=YES,URI=\"en-lo.m3u8\"\n" +
            "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"hi\",NAME=\"English\",LANGUAGE=\"en\",DEFAULT=YES,URI=\"en-hi.m3u8\"\n" +
            "#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,AUDIO=\"lo\"\nlow.m3u8\n" +
            "#EXT-X-STREAM-INF:BANDWIDTH=2800000,RESOLUTION=1280x720,AUDIO=\"hi\"\nhigh.m3u8\n"
        serveMaster(master) { url ->
            val address = HlsProxy.wrap(url, emptyMap(), audioLanguage = "en")
            val text = URI(address).toURL().readText()
            assertEquals(2, text.lineSequence().count { it.contains("TYPE=AUDIO") }, text)
            assertNull(HlsProxy.audioRenditions(address))
        }
    }

    @Test
    fun aStalledPlaylistIsAskedForAgain() {
        val asked = java.util.concurrent.atomic.AtomicInteger(0)
        val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 8).apply {
            executor = java.util.concurrent.Executors.newCachedThreadPool()
            createContext("/a.m3u8") { ex ->
                // the first request is never answered (a stalled stream), the second one is
                if (asked.incrementAndGet() == 1) { Thread.sleep(15_000); ex.close(); return@createContext }
                val text = "#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXTINF:10.0,\nseg-0.ts\n#EXT-X-ENDLIST\n".toByteArray()
                ex.sendResponseHeaders(200, text.size.toLong()); ex.responseBody.use { it.write(text) }
            }
            start()
        }
        try {
            val started = System.currentTimeMillis()
            val text = URI(HlsProxy.wrap("http://127.0.0.1:${server.address.port}/a.m3u8", emptyMap())).toURL().readText()
            val ms = System.currentTimeMillis() - started
            assertTrue(text.startsWith("#EXTM3U"), text)
            assertTrue(ms < 6_000, "playlist after $ms ms")
            assertEquals(2, asked.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun theFirstBytesReachThePlayerBeforeTheWholeSegment() {
        val segment = png + ts(4000) // ~750 KB
        val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 8).apply {
            executor = java.util.concurrent.Executors.newCachedThreadPool()
            createContext("/v.m3u8") { ex ->
                val text = "#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXT-X-PLAYLIST-TYPE:VOD\n#EXTINF:10.0,\nseg-0.png\n#EXT-X-ENDLIST\n".toByteArray()
                ex.responseHeaders.set("Content-Type", "application/vnd.apple.mpegurl")
                ex.sendResponseHeaders(200, text.size.toLong()); ex.responseBody.use { it.write(text) }
            }
            createContext("/seg-0.png") { ex ->
                ex.sendResponseHeaders(200, segment.size.toLong())
                ex.responseBody.use { out ->
                    out.write(segment, 0, 64 * 1024); out.flush()
                    Thread.sleep(2500) // a slow server: the rest comes later
                    out.write(segment, 64 * 1024, segment.size - 64 * 1024)
                }
            }
            start()
        }
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val playlist = URI(HlsProxy.wrap("$base/v.m3u8", emptyMap())).toURL().readText()
            val seg = playlist.lineSequence().first { it.startsWith("http://127.0.0.1") && it.contains("/seg?") }
            val started = System.currentTimeMillis()
            val conn = URI(seg).toURL().openConnection()
            conn.getInputStream().use { input ->
                val first = ByteArray(5 * 188)
                var got = 0
                while (got < first.size) { val n = input.read(first, got, first.size - got); if (n < 0) break; got += n }
                val firstMs = System.currentTimeMillis() - started
                assertTrue(firstMs < 1500, "first bytes after $firstMs ms")
                assertEquals(0x47.toByte(), first[0], "the fake header is cut off")
                val rest = input.readBytes()
                assertEquals(segment.size - png.size, got + rest.size)
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun upstreamHttpErrorIsRecordedAndClearedOnCancel() {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 8).apply {
            executor = java.util.concurrent.Executors.newCachedThreadPool()
            createContext("/bad.m3u8") { ex ->
                ex.sendResponseHeaders(502, -1)
                ex.close()
            }
            start()
        }
        try {
            val address = HlsProxy.wrap("http://127.0.0.1:${server.address.port}/bad.m3u8", emptyMap())
            val conn = URI(address).toURL().openConnection() as java.net.HttpURLConnection
            assertEquals(502, conn.responseCode)
            assertEquals(502, HlsProxy.upstreamStatus(address))
            HlsProxy.cancel(address)
            assertNull(HlsProxy.upstreamStatus(address))
        } finally {
            server.stop(0)
        }
    }
}
