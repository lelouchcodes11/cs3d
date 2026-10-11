package com.lagradost.desktop.net

import com.sun.net.httpserver.HttpServer
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Web pages are never written to the disk cache; data still is (that is what makes repeated requests quick) */
class NoPageCacheTest {
    private lateinit var server: HttpServer
    private lateinit var dir: File
    private val pageHits = AtomicInteger()
    private val dataHits = AtomicInteger()

    @BeforeTest
    fun setUp() {
        dir = File.createTempFile("nopagecache", "").also { it.delete(); it.mkdirs() }
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        // both answers say "keep me for an hour": the cache would store both without the interceptor
        server.createContext("/page") { ex ->
            pageHits.incrementAndGet()
            val body = "<html><script>location.href='https://example.invalid'</script></html>".toByteArray()
            ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            ex.responseHeaders.add("Cache-Control", "max-age=3600")
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.createContext("/data") { ex ->
            dataHits.incrementAndGet()
            val body = """{"ok":true}""".toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.responseHeaders.add("Cache-Control", "max-age=3600")
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.stop(0)
        dir.deleteRecursively()
    }

    private fun client(withInterceptor: Boolean): OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(dir, 10L * 1024 * 1024))
        .apply { if (withInterceptor) addNetworkInterceptor(NoPageCache) }
        .build()

    private fun get(c: OkHttpClient, path: String): String =
        c.newCall(Request.Builder().url("http://127.0.0.1:${server.address.port}$path").build()).execute().use { it.body.string() }

    @Test
    fun aPageIsAskedForEveryTimeAndNotKept() {
        val c = client(true)
        get(c, "/page"); get(c, "/page")
        assertEquals(2, pageHits.get())
        // nothing of it is in the cache folder
        val stored = dir.walkTopDown().filter { it.isFile && it.name != "journal" && !it.name.startsWith("journal.") }.map { it.readText() }.toList()
        assertTrue(stored.none { it.contains("location.href") }, "a web page was written to the cache folder: $stored")
    }

    @Test
    fun dataIsStillCached() {
        val c = client(true)
        get(c, "/data"); get(c, "/data")
        assertEquals(1, dataHits.get())
    }

    @Test
    fun withoutTheInterceptorThePageWouldBeKept() {
        // the test's own check: the same server and cache keep the page when the interceptor is not there
        val c = client(false)
        get(c, "/page"); get(c, "/page")
        assertEquals(1, pageHits.get())
    }
}
