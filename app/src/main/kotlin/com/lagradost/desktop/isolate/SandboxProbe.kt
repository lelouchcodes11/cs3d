package com.lagradost.desktop.isolate

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.lagradost.cloudstream3.mapper
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/** What code in the sandbox process manages to do, tried one thing at a time (the tests of the sandbox ask for it; a normal run never does) */
internal object SandboxProbe {
    private fun attempt(block: () -> Any?): String = try { "allowed: " + (block() ?: "") } catch (t: Throwable) { "blocked: " + t.javaClass.simpleName }

    fun run(b: JsonNode): JsonNode {
        val out: ObjectNode = mapper.createObjectNode()
        b.get("readFile")?.asText()?.let { path -> out.put("readFile", attempt { File(path).readText().take(20) }) }
        b.get("listFolder")?.asText()?.let { path -> out.put("listFolder", attempt { File(path).list()?.size ?: throw SecurityException("no listing") }) }
        b.get("writeFile")?.asText()?.let { path -> out.put("writeFile", attempt { File(path).writeText("x"); "written" }) }
        b.get("ownFile")?.asText()?.let { path -> out.put("ownFile", attempt { File(path).writeText("x"); "written" }) }
        // what an extension meets in the program: the first TLS provider (the app's is Conscrypt) and what its load(context) is given (the app gives an activity)
        out.put("tlsProvider", java.security.Security.getProviders().first().name)
        out.put("pluginContext", ExtensionWorker.pluginContext?.let { it.javaClass.simpleName + ", activity=" + (it is androidx.appcompat.app.AppCompatActivity) } ?: "none")
        out.put("startProgram", attempt { ProcessBuilder("cmd.exe", "/c", "echo", "hi").start().waitFor() })
        b.get("loopbackPort")?.asInt()?.let { port -> out.put("loopback", attempt { Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 3000); "connected" } }) }
        b.get("internetHost")?.asText()?.let { host -> out.put("internet", attempt { Socket().use { it.connect(InetSocketAddress(host, 443), 8000); "connected" } }) }
        // what a site sees of this process's own requests (Cloudflare's trace: the address, TLS, HTTP version and the user agent)
        b.get("trace")?.asText()?.let { url -> out.put("trace", attempt { kotlinx.coroutines.runBlocking { com.lagradost.cloudstream3.app.get(url).text } }) }
        // many requests at once from a client of OkHttp's own defaults (fast fallback races two connections and closes the loser): the program must live through it
        b.get("hammer")?.asInt()?.let { n ->
            out.put("hammer", attempt {
                val client = okhttp3.OkHttpClient()
                val done = java.util.concurrent.atomic.AtomicInteger()
                val pool = java.util.concurrent.Executors.newFixedThreadPool(24)
                repeat(n) { i ->
                    pool.execute { runCatching { client.newCall(okhttp3.Request.Builder().url(if (i % 2 == 0) "https://raw.githubusercontent.com/robots.txt" else "https://www.cloudflare.com/cdn-cgi/trace").build()).execute().use { done.incrementAndGet() } } }
                }
                pool.shutdown()
                pool.awaitTermination(90, java.util.concurrent.TimeUnit.SECONDS)
                "${done.get()} of $n answered"
            })
        }
        b.get("webUrl")?.asText()?.let { url -> out.put("web", attempt { webPage(url, b.get("webAs")?.asText().orEmpty(), b.get("webDialogSeconds")?.asInt() ?: 0) }) }
        return out
    }

    /** A browser view as an extension builds it: a page with a script, a console line and an object the page calls; what happened, in order */
    private fun webPage(url: String, asExtension: String, dialogSeconds: Int): String {
        ExtensionWorker.probeExtension = asExtension
        val log = java.util.concurrent.CopyOnWriteArrayList<String>()
        val done = java.util.concurrent.CountDownLatch(1)
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val view = android.webkit.WebView(com.lagradost.desktop.runtime.AndroidRuntime.context)
            view.getSettings().setJavaScriptEnabled(true)
            view.addJavascriptInterface(object {
                @android.webkit.JavascriptInterface
                fun ping(s: String): String { log += "bridge:$s"; return "pong-$s" }
            }, "Bridge")
            view.setWebChromeClient(object : android.webkit.WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean { log += "console:" + consoleMessage.message(); return true }
            })
            view.setWebViewClient(object : android.webkit.WebViewClient() {
                override fun shouldInterceptRequest(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?): android.webkit.WebResourceResponse? {
                    log += "request:" + request?.url?.path
                    return null
                }

                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                    log += "finished"
                    view?.evaluateJavascript("document.title + '|' + Bridge.ping('hi')") { result -> log += "eval:$result"; done.countDown() }
                }
            })
            view.loadUrl(url)
            // an extension's challenge dialog: the page in a dialog; the app shows it in a window of its own
            if (dialogSeconds > 0) {
                val ctx = com.lagradost.desktop.runtime.AndroidRuntime.context
                val dialog = android.app.Dialog(ctx)
                val root = android.widget.FrameLayout(ctx)
                root.addView(view, android.widget.FrameLayout.LayoutParams(600, 400))
                dialog.setContentView(root)
                dialog.setOnCancelListener { log += "cancelled" }
                dialog.show()
                log += "dialog shown"
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ log += "dialog dismissed"; dialog.dismiss() }, dialogSeconds * 1000L)
            }
        }
        check(done.await(60, java.util.concurrent.TimeUnit.SECONDS)) { "no answer from the page: $log" }
        if (dialogSeconds > 0) Thread.sleep(dialogSeconds * 1000L + 1500)
        return log.joinToString(";")
    }
}
