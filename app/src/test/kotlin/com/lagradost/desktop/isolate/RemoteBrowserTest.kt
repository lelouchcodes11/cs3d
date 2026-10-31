package com.lagradost.desktop.isolate

import com.lagradost.cloudstream3.mapper
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.runtime.web.wv2.WebView2Runtime
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The browser of an extension in the sandbox: the extension's process (a real AppContainer) builds a WebView, the app makes the page, and what the page does reaches the
 * extension the way it would reach one on the phone: the request, the console line, the page finishing, a script's answer and a call of the page to the extension's own object.
 */
class RemoteBrowserTest {
    private lateinit var root: File
    private var child: AppContainerLauncher.Isolated? = null
    private var server: HttpServer? = null

    @BeforeTest
    fun setUp() {
        assumeTrue("needs Windows AppContainers", AppContainerLauncher.available)
        assumeTrue("needs the Windows WebView2 runtime", WebView2Runtime.isAvailable)
        System.setProperty("cloudstream.workerprobe", "1")
        root = File.createTempFile("remotebrowser", "").also { it.delete(); it.mkdirs() }
    }

    @AfterTest
    fun tearDown() {
        RemoteBrowserHost.closeFor(null)
        com.lagradost.desktop.runtime.web.PrivateNetworkGuard.testAllowedHosts = emptySet()
        RemoteBrowserHost.rpcProvider = { IsolatedHost.rpcOrNull() }
        RemoteBrowserHost.owns = { IsolatedHost.owns(it) }
        child?.destroy()
        server?.stop(0)
        System.clearProperty("cloudstream.workerprobe")
        if (::root.isInitialized) root.deleteRecursively()
    }

    @Test
    fun aBrowserViewInTheSandboxIsAPageOfTheApp() = runBlocking {
        // the app's side: its engine, and a page on this PC's loopback (the sandbox cannot reach it, the app's page can)
        val activity = DesktopBootstrap.init(File(root, "app"))
        DesktopBootstrap.onCreate(activity, loadPlugins = false, activityLifecycle = false)
        val http = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
        http.createContext("/page") { ex ->
            val html = "<html><head><title>hello</title></head><body><script>console.log('from-page');</script></body></html>".toByteArray()
            ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            ex.sendResponseHeaders(200, html.size.toLong())
            ex.responseBody.use { it.write(html) }
        }
        http.start()
        server = http

        val (process, rpc) = IsolatedHost.launch(File(root, "box"))
        child = process
        RemoteBrowserHost.rpcProvider = { rpc }
        RemoteBrowserHost.owns = { it == "probe-extension" }
        com.lagradost.desktop.runtime.web.PrivateNetworkGuard.testAllowedHosts = setOf("127.0.0.1")
        rpc.call("ping", null, 90_000)
        val body = mapper.createObjectNode().put("webUrl", "http://127.0.0.1:${http.address.port}/page").put("webAs", "probe-extension")
        val r = rpc.call("probe", body, 150_000)
        val web = r.get("web").asText()
        println("remote browser: $web")
        assertTrue(web.startsWith("allowed"), "the page was made: $web")
        assertTrue("request:/page" in web, "the extension saw the page's request: $web")
        assertTrue("console:from-page" in web, "the extension got the page's console line: $web")
        assertTrue("finished" in web, "the page finished: $web")
        assertTrue("bridge:hi" in web, "the page called the extension's object: $web")
        assertTrue("eval:\"hello|pong-hi\"" in web, "the extension's script ran in the page and got the object's answer: $web")
    }
}
