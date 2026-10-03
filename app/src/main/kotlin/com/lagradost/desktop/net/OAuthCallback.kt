package com.lagradost.desktop.net

import android.util.Log
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.awt.EventQueue
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * Where the browser comes back to after "Authorise" on AniList / MyAnimeList / Simkl: a page of the app itself on
 * `http://localhost:52526/<service>`. The old way, a `cloudstreamapp://` link that the browser hands to Windows and Windows to a second
 * copy of the app, depends on the browser asking, the registry entry, the second copy reaching the first and the window coming forward,
 * and for the user it simply looked like "nothing happens". Here the browser opens an ordinary address, the page reads the code or token
 * out of the address (AniList puts it after the `#`, which a server never sees, hence a little script) and posts it back to this server,
 * and the same login code as for the old link takes over. The `cloudstreamapp://` link keeps working for clients registered that way.
 */
object OAuthCallback {
    private const val TAG = "OAuthCallback"
    const val PORT = 52526
    private const val WINDOW_MS = 15 * 60_000L

    /** The redirect address to register for [identifier] ("anilistlogin", "mallogin", "simkl") */
    fun redirectUrl(identifier: String) = "http://localhost:$PORT/$identifier"

    @Volatile private var servers: List<HttpServer> = emptyList()
    @Volatile private var armedUntil = 0L
    @Volatile private var onLink: ((String) -> Unit)? = null

    /**
     * A sign-in is about to start: the callback page is accepted for the next 15 minutes and what it reports goes to [handler]
     * as a `cloudstreamapp://<service>/#...` style link. Returns false when the port could not be taken.
     */
    @Synchronized
    fun arm(handler: (String) -> Unit): Boolean {
        onLink = handler
        armedUntil = System.currentTimeMillis() + WINDOW_MS
        if (servers.isNotEmpty()) return true
        val started = ArrayList<HttpServer>()
        for (host in listOf("127.0.0.1", "::1")) {
            try {
                val server = HttpServer.create(InetSocketAddress(InetAddress.getByName(host), PORT), 0)
                server.createContext("/") { ex -> runCatching { handle(ex) }.onFailure { Log.w(TAG, "request failed: $it"); runCatching { ex.close() } } }
                server.executor = Executors.newCachedThreadPool { r -> Thread(r, "oauth-callback").apply { isDaemon = true } }
                server.start()
                started += server
            } catch (t: Throwable) {
                // ::1 is not there on every machine; only a failure of both is a problem
                Log.i(TAG, "no listener on $host:$PORT: $t")
            }
        }
        servers = started
        if (started.isEmpty()) Log.w(TAG, "port $PORT is taken: the sign-in cannot come back to the app through the browser address") else Log.i(TAG, "listening on localhost:$PORT")
        return started.isNotEmpty()
    }

    private val hostOk = Regex("^(localhost|127\\.0\\.0\\.1|\\[::1\\]):$PORT$", RegexOption.IGNORE_CASE)

    private fun handle(ex: HttpExchange) {
        val host = ex.requestHeaders.getFirst("Host").orEmpty()
        // a page of another site (DNS rebinding) is not talking to us
        if (!hostOk.matches(host)) return reply(ex, 421, "text/plain", "wrong host")
        val path = ex.requestURI.path.trim('/')
        if (ex.requestMethod == "POST" && path == "_cb") {
            val origin = ex.requestHeaders.getFirst("Origin")
            if (origin != null && !hostOk.matches(origin.removePrefix("http://"))) return reply(ex, 403, "text/plain", "foreign origin")
            if (System.currentTimeMillis() > armedUntil) return reply(ex, 409, "text/plain", "no sign-in is waiting")
            val body = ex.requestBody.readNBytes(16_384).toString(Charsets.UTF_8)
            val (id, search, hash) = body.split("\n").let { Triple(it.getOrElse(0) { "" }, it.getOrElse(1) { "" }, it.getOrElse(2) { "" }) }
            if (!id.matches(Regex("[a-z]{3,20}"))) return reply(ex, 400, "text/plain", "bad service")
            // the shape of the real redirect: ".../#access_token=..." (AniList) or "...?code=...&state=..." (MyAnimeList, Simkl)
            val link = if (hash.isNotEmpty()) "cloudstreamapp://$id/$hash" else "cloudstreamapp://$id$search"
            armedUntil = 0L
            val handler = onLink
            EventQueue.invokeLater { handler?.invoke(link) }
            return reply(ex, 200, "text/plain", "ok")
        }
        if (ex.requestMethod == "GET" && path.matches(Regex("[a-z]{3,20}"))) return reply(ex, 200, "text/html; charset=utf-8", PAGE)
        reply(ex, 404, "text/plain", "not found")
    }

    private fun reply(ex: HttpExchange, code: Int, type: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        ex.responseHeaders.add("Content-Type", type)
        ex.responseHeaders.add("Cache-Control", "no-store")
        ex.responseHeaders.add("Referrer-Policy", "no-referrer")
        ex.sendResponseHeaders(code, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    private val PAGE = """
        <!doctype html><html><head><meta charset="utf-8"><title>CloudStream</title><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#111114;color:#f2f2f4;font:16px Segoe UI,system-ui,sans-serif}
        main{max-width:420px;padding:32px;text-align:center}h1{font-size:22px;margin:0 0 8px}p{color:#a8a8b3;margin:0;line-height:1.5}</style></head>
        <body><main><h1 id="t">Signing you in…</h1><p id="m">Sending the answer to CloudStream.</p></main>
        <script>
        (function () {
          var id = location.pathname.replace(/^\/+/, "").split("/")[0];
          function show(t, m) { document.getElementById("t").textContent = t; document.getElementById("m").textContent = m; }
          fetch("/_cb", { method: "POST", headers: { "Content-Type": "text/plain" }, body: id + "\n" + location.search + "\n" + location.hash })
            .then(function (r) {
              if (r.ok) { history.replaceState(null, "", "/" + id); show("You are signed in", "You can close this tab and go back to CloudStream."); }
              else show("Nothing is waiting for a sign-in", "Start it again from CloudStream: Settings, Accounts & security.");
            })
            .catch(function () { show("CloudStream did not answer", "Is CloudStream still open? Start the sign-in again from Settings, Accounts & security."); });
        })();
        </script></body></html>
    """.trimIndent()
}
