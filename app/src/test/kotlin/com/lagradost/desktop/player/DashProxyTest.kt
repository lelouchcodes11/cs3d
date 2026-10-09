package com.lagradost.desktop.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DashProxyTest {
    @Test
    fun testDashProxyWrapAndCancel() {
        val originalUrl = "https://example.com/live/manifest.mpd"
        val headers = mapOf("User-Agent" to "TestAgent")

        val wrapped = DashProxy.wrap(originalUrl, headers)
        assertNotNull(wrapped, "Wrapped URL should not be null")
        assertTrue(wrapped.startsWith("http://127.0.0.1:"), "Wrapped URL should be on loopback")
        assertTrue(wrapped.contains("/d/"), "Wrapped URL should contain /d/ path")

        // Test cancel
        DashProxy.cancel(wrapped)

        // Cancel with null or unknown should not throw
        DashProxy.cancel(null)
        DashProxy.cancel("http://127.0.0.1:1234/d/unknown-id/manifest.mpd")
    }

    @Test
    fun testDashProxyCancelAll() {
        val wrapped1 = DashProxy.wrap("https://example.com/live1.mpd", emptyMap())
        val wrapped2 = DashProxy.wrap("https://example.com/live2.mpd", emptyMap())

        DashProxy.cancelAll()

        // Cancel already cancelled entries should be no-op
        DashProxy.cancel(wrapped1)
        DashProxy.cancel(wrapped2)
    }

    @Test
    fun testDashProxyInFlightCancellation() {
        val mockServer = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(java.net.InetAddress.getByName("127.0.0.1"), 0), 0)
        val startedLatch = java.util.concurrent.CountDownLatch(1)
        mockServer.createContext("/live.mpd") { ex ->
            val manifest = """<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="dynamic"></MPD>"""
            val bytes = manifest.toByteArray()
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.write(bytes)
            ex.close()
        }
        mockServer.createContext("/segment.m4s") { ex ->
            ex.sendResponseHeaders(200, 1_000_000L)
            startedLatch.countDown()
            val buf = ByteArray(1024)
            try {
                for (i in 0 until 500) {
                    ex.responseBody.write(buf)
                    Thread.sleep(50)
                }
            } catch (_: Throwable) {}
            ex.close()
        }
        mockServer.start()
        try {
            val streamUrl = "http://127.0.0.1:${mockServer.address.port}/live.mpd"
            val wrapped = DashProxy.wrap(streamUrl, emptyMap())

            val client = okhttp3.OkHttpClient()
            val manifestReq = okhttp3.Request.Builder().url(wrapped).build()
            client.newCall(manifestReq).execute().use { r ->
                assertTrue(r.isSuccessful)
            }

            val segmentWrapped = wrapped.substringBeforeLast('/') + "/segment.m4s"
            val streamThread = Thread {
                val segReq = okhttp3.Request.Builder().url(segmentWrapped).build()
                runCatching {
                    client.newCall(segReq).execute().use { resp ->
                        val bytes = resp.body.byteStream()
                        val b = ByteArray(1024)
                        while (bytes.read(b) >= 0) {
                            // streaming
                        }
                    }
                }
            }
            streamThread.start()

            assertTrue(startedLatch.await(4, java.util.concurrent.TimeUnit.SECONDS), "Mock segment streaming should have started")

            val beganCancel = System.currentTimeMillis()
            DashProxy.cancel(wrapped)
            streamThread.join(2000)
            val cancelDuration = System.currentTimeMillis() - beganCancel

            assertFalse(streamThread.isAlive, "Streaming thread must terminate quickly when DashProxy is cancelled")
            assertTrue(cancelDuration < 2000, "Cancellation should complete well within 2 seconds")
        } finally {
            mockServer.stop(0)
        }
    }

    @Test
    fun testRangeProxyAndHlsProxyCancel() {
        val rangeWrapped = RangeProxy.wrap("https://example.com/video.mp4", emptyMap())
        RangeProxy.cancel(rangeWrapped)
        RangeProxy.cancel(null)
        RangeProxy.cancelAll()

        HlsProxy.cancel("http://127.0.0.1:1234/pl?h=test&u=test")
        HlsProxy.cancel(null)
        HlsProxy.cancelAll()
    }

    @Test
    fun testRangeProxyInFlightCancellation() {
        val mockServer = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(java.net.InetAddress.getByName("127.0.0.1"), 0), 0)
        val startedLatch = java.util.concurrent.CountDownLatch(1)
        mockServer.createContext("/video.mp4") { ex ->
            val rangeHeader = ex.requestHeaders.getFirst("Range")
            if (rangeHeader != null && rangeHeader.startsWith("bytes=0-0")) {
                ex.responseHeaders.add("Content-Range", "bytes 0-0/1000000")
                ex.sendResponseHeaders(206, 1)
                ex.responseBody.write(byteArrayOf(0))
                ex.close()
                return@createContext
            }
            ex.responseHeaders.add("Content-Range", "bytes 0-999999/1000000")
            ex.sendResponseHeaders(206, 1_000_000L)
            startedLatch.countDown()
            val buf = ByteArray(1024)
            try {
                for (i in 0 until 500) {
                    ex.responseBody.write(buf)
                    Thread.sleep(50)
                }
            } catch (_: Throwable) {}
            ex.close()
        }
        mockServer.start()
        try {
            val streamUrl = "http://127.0.0.1:${mockServer.address.port}/video.mp4"
            val wrapped = RangeProxy.wrap(streamUrl, emptyMap())

            val client = okhttp3.OkHttpClient()
            val streamThread = Thread {
                val req = okhttp3.Request.Builder().url(wrapped).build()
                runCatching {
                    client.newCall(req).execute().use { resp ->
                        val bytes = resp.body.byteStream()
                        val b = ByteArray(1024)
                        while (bytes.read(b) >= 0) {
                            // streaming
                        }
                    }
                }
            }
            streamThread.start()

            assertTrue(startedLatch.await(4, java.util.concurrent.TimeUnit.SECONDS), "Mock video streaming should have started")

            val beganCancel = System.currentTimeMillis()
            RangeProxy.cancel(wrapped)
            streamThread.join(2000)
            val cancelDuration = System.currentTimeMillis() - beganCancel

            assertFalse(streamThread.isAlive, "Streaming thread must terminate quickly when RangeProxy is cancelled")
            assertTrue(cancelDuration < 2000, "Cancellation should complete well within 2 seconds")
        } finally {
            mockServer.stop(0)
        }
    }
}

