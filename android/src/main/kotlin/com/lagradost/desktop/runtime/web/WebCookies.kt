package com.lagradost.desktop.runtime.web

import android.util.Log
import com.lagradost.desktop.runtime.AndroidRuntime
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** One cookie. [domain] starts with a dot for a domain cookie, is the bare host for a host-only one; [expires] in ms, null for a session cookie. */
data class WebCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String = "/",
    val expires: Long? = null,
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
) {
    val key: String get() = "$name\u0000${domain.lowercase()}\u0000$path"
    fun expired(now: Long = System.currentTimeMillis()) = expires != null && expires <= now
}

/**
 * The cookie jar behind android.webkit.CookieManager. It lives in the app (encrypted on disk with the Windows user's key), so reading a
 * cookie never starts a browser: extensions and the Cloudflare helpers ask for cookies on every request. A browser engine, while it
 * runs, is a mirror: [backend] receives every change made here, and the engine hands its whole jar back after page loads ([replaceAll]).
 * Live CookieManager reads request a bounded fresh snapshot; without a browser the persisted jar is used.
 */
object WebCookies {
    private const val TAG = "WebCookies"

    /** The browser engine that mirrors the jar while it runs */
    interface BrowserBackend {
        fun push(cookie: WebCookie)
        fun delete(cookie: WebCookie)
        fun deleteAll()
        /** Null means unavailable/incomplete; an empty list means a successfully read empty jar. */
        fun readCookies(complete: (List<WebCookie>?) -> Unit)
        fun canWaitForCookies(): Boolean = true
    }

    @Volatile var backend: BrowserBackend? = null

    private val lock = Any()
    private var cookies = LinkedHashMap<String, WebCookie>()
    private var revision = 0L
    private var nextRead = 0L
    private var acceptedRead = 0L
    private val refresh = BrowserCookieRefresh()

    internal data class ReadTicket(val revision: Long, val sequence: Long, val backend: BrowserBackend?)

    internal fun beginBrowserRead(): ReadTicket {
        ensureLoaded()
        return synchronized(lock) { ReadTicket(revision, ++nextRead, backend) }
    }

    /** No browser is started for cookie reads. A failed live read must not masquerade as fresh login cookies. */
    fun freshHeader(url: String): String? {
        if (!refreshFromBrowser()) return null
        return header(url)
    }

    private fun refreshFromBrowser(): Boolean {
        val browser = backend ?: return true
        val ticket = beginBrowserRead()
        val ui = java.awt.EventQueue.isDispatchThread()
        return refresh.refresh(if (ui) 200 else 1500, browser.canWaitForCookies(), browser::readCookies) {
            replaceAll(it, ticket)
        }
    }
    private val loaded = AtomicBoolean(false)
    private val saver = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "cookie-store").also { it.isDaemon = true } }
    private val saveQueued = AtomicBoolean(false)

    private val file: File get() = File(AndroidRuntime.dataDir, "web/cookies.bin")

    private fun ensureLoaded() {
        synchronized(lock) {
            if (loaded.get()) return
            try {
                runCatching {
                    val f = file
                    if (!f.isFile) return
                    val json = String(com.sun.jna.platform.win32.Crypt32Util.cryptUnprotectData(f.readBytes()), Charsets.UTF_8)
                    val array = JSONArray(json)
                    val map = LinkedHashMap<String, WebCookie>()
                    for (i in 0 until array.length()) {
                        val o = array.getJSONObject(i)
                        val c = WebCookie(
                            o.getString("n"), o.optString("v"), o.getString("d"), o.optString("p", "/"),
                            if (o.has("e")) o.getLong("e") else null, o.optBoolean("s"), o.optBoolean("h"),
                        )
                        if (!c.expired()) map[c.key] = c
                    }
                    cookies = map
                }.onFailure { Log.w(TAG, "cookie store not read") }
            } finally {
                loaded.set(true)
            }
        }
    }

    private fun scheduleSave() {
        if (!saveQueued.compareAndSet(false, true)) return
        saver.schedule({
            saveQueued.set(false)
            saveNow()
        }, 1, TimeUnit.SECONDS)
    }

    private fun saveNow(): Boolean = runCatching {
        val array = JSONArray()
        for (c in all()) {
            val o = JSONObject().put("n", c.name).put("v", c.value).put("d", c.domain).put("p", c.path)
            if (c.expires != null) o.put("e", c.expires)
            if (c.secure) o.put("s", true)
            if (c.httpOnly) o.put("h", true)
            array.put(o)
        }
        val bytes = com.sun.jna.platform.win32.Crypt32Util.cryptProtectData(array.toString().toByteArray(Charsets.UTF_8))
        val f = file
        f.parentFile.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeBytes(bytes)
        java.nio.file.Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        true
    }.getOrElse { Log.w(TAG, "cookie store not saved"); false }

    /** Persists on the same writer as delayed saves, so a delayed old write cannot undo a flush. */
    fun flush() {
        val fresh = refreshFromBrowser()
        val saved = try {
            saver.submit<Boolean> { saveNow() }.get(3, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } catch (_: Exception) {
            false
        }
        if (!fresh || !saved) Log.w(TAG, "Cookie flush incomplete (browser=$fresh, disk=$saved)")
    }

    // Cloudflare ties a clearance cookie to the connection it was issued to (the address it came from). After the network changed (another Wi-Fi, a VPN or Cloudflare
    // WARP switched on or off) the stored one is refused, and an extension's solver that looks in the jar finds it at once, takes it for a solved check, retries and is
    // blocked again, for ever (AnimePahe: "Clearance cookie found" and 403 straight after, 2026-10-22). So the clearance cookies go when the network is not the one they
    // were issued on; the check is a local one (no request) and runs at most every 20 s.
    private val networkFile: File get() = File(AndroidRuntime.dataDir, "web/network.txt")
    private var network: String? = null
    private var networkCheckedAt = 0L

    /** The interface and subnet the default route uses (a UDP "connect" sends nothing), or null when there is none */
    private fun currentNetwork(): String? = runCatching {
        java.net.DatagramSocket().use { s ->
            s.connect(java.net.InetAddress.getByName("1.1.1.1"), 53)
            val local = s.localAddress
            val ni = java.net.NetworkInterface.getByInetAddress(local) ?: return@use null
            val prefix = ni.interfaceAddresses.firstOrNull { it.address == local }?.networkPrefixLength?.toInt() ?: return@use null
            val bytes = local.address
            val masked = bytes.indices.joinToString(".") { i -> ((bytes[i].toInt() and 0xFF) and (if (i * 8 + 8 <= prefix) 0xFF else if (i * 8 >= prefix) 0 else (0xFF shl (8 - (prefix - i * 8))) and 0xFF)).toString() }
            "${ni.displayName}|$masked/$prefix"
        }
    }.getOrNull()

    private fun checkNetwork() {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (now - networkCheckedAt < 20_000) return
            networkCheckedAt = now
        }
        val current = currentNetwork() ?: return
        val before = network ?: runCatching { networkFile.takeIf { it.isFile }?.readText()?.trim() }.getOrNull()
        network = current
        if (before == current) return
        runCatching { networkFile.parentFile.mkdirs(); networkFile.writeText(current) }
        if (before == null) return // the first run: nothing to compare with
        val clearance = synchronized(lock) { cookies.values.filter { it.name == "cf_clearance" } }
        if (clearance.isEmpty()) return
        Log.i(TAG, "the network changed ($before -> $current): ${clearance.size} Cloudflare clearance cookie(s) dropped")
        for (c in clearance) put(c.copy(expires = 0L))
    }

    /** Every cookie that has not expired */
    fun all(): List<WebCookie> {
        ensureLoaded()
        checkNetwork()
        val now = System.currentTimeMillis()
        return synchronized(lock) { cookies.values.filter { !it.expired(now) } }
    }

    fun isEmpty(): Boolean = all().isEmpty()

    private fun domainMatches(host: String, domain: String): Boolean {
        val h = host.lowercase()
        if (!domain.startsWith(".")) return h == domain.lowercase()
        val d = domain.removePrefix(".").lowercase()
        return h == d || h.endsWith(".$d")
    }

    /** Cookies that would be sent to [url], "name=value; name2=value2" (Android CookieManager.getCookie) */
    fun header(url: String): String? {
        val uri = runCatching { URI(if (url.contains("://")) url else "https://$url") }.getOrNull() ?: return null
        val host = uri.host ?: return null
        val path = (uri.rawPath ?: "/").ifEmpty { "/" }
        val secure = uri.scheme.equals("https", true) || uri.scheme.equals("wss", true)
        val matching = all().filter { c ->
            domainMatches(host, c.domain) && pathMatches(path, c.path) && (!c.secure || secure)
        }.sortedByDescending { it.path.length }
        if (matching.isEmpty()) return null
        return matching.joinToString("; ") { "${it.name}=${it.value}" }
    }

    private fun pathMatches(path: String, cookiePath: String): Boolean =
        path == cookiePath || (path.startsWith(cookiePath) && (cookiePath.endsWith('/') || path.getOrNull(cookiePath.length) == '/'))

    /** Parses a Set-Cookie style [value] for [url] and stores it (Android CookieManager.setCookie) */
    fun set(url: String, value: String): Boolean {
        val uri = runCatching { URI(if (url.contains("://")) url else "https://$url") }.getOrNull() ?: return false
        val host = uri.host ?: return false
        val parts = value.split(';').map { it.trim() }
        val nv = parts.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return false
        val eq = nv.indexOf('=')
        val name = if (eq >= 0) nv.substring(0, eq).trim() else nv
        val v = if (eq >= 0) nv.substring(eq + 1).trim() else ""
        var domain = host
        var path = "/"
        var secure = false
        var httpOnly = false
        var expires: Long? = null
        var maxAge: Long? = null
        for (attr in parts.drop(1)) {
            val i = attr.indexOf('=')
            val k = (if (i >= 0) attr.substring(0, i) else attr).trim().lowercase()
            val av = if (i >= 0) attr.substring(i + 1).trim() else ""
            when (k) {
                "domain" -> if (av.isNotEmpty()) domain = "." + av.removePrefix(".")
                "path" -> path = av.ifEmpty { "/" }
                "secure" -> secure = true
                "httponly" -> httpOnly = true
                "max-age" -> maxAge = av.toLongOrNull()
                "expires" -> expires = parseHttpDate(av)
            }
        }
        maxAge?.let { expires = System.currentTimeMillis() + it * 1000 }
        val cookie = WebCookie(name, v, domain, path, expires, secure, httpOnly)
        if (cookie.expired() && domain == host) {
            // "name=; Max-Age=0" for a page's address, as extensions write it to wipe a cookie (Nepu's cf_clearance): it names no domain, but a site's clearance
            // cookie is a domain cookie (".host"), a different key. Every cookie of that name the page would be sent is deleted, from the engine's jar as well,
            // or the "wiped" clearance stays, the next check "passes" in a second on the old cookie and the request is blocked again.
            all().filter { it.name == name && domainMatches(host, it.domain) && path.startsWith(it.path) }.forEach { put(it.copy(expires = 0L)) }
        }
        put(cookie)
        return true
    }

    /** Stores [cookie] (an expired one deletes) and gives it to the running engine */
    fun put(cookie: WebCookie) {
        ensureLoaded()
        synchronized(lock) {
            revision++
            if (cookie.expired()) cookies.remove(cookie.key) else cookies[cookie.key] = cookie
            // Enqueue the native write before a later reader can obtain its revision ticket.
            backend?.let { b -> runCatching { if (cookie.expired()) b.delete(cookie) else b.push(cookie) } }
        }
        scheduleSave()
    }

    /** The running engine's whole jar after a page load: it becomes this jar (the engine got this jar when it started) */
    internal fun replaceAll(fromBrowser: List<WebCookie>, ticket: ReadTicket): Boolean {
        ensureLoaded()
        val map = LinkedHashMap<String, WebCookie>()
        for (c in fromBrowser) if (!c.expired()) map[c.key] = c
        val changed = synchronized(lock) {
            if (ticket.backend !== backend || ticket.revision != revision || ticket.sequence < acceptedRead) return false
            acceptedRead = ticket.sequence
            val same = map.keys == cookies.keys && map.all { (k, c) -> cookies[k] == c }
            if (!same) cookies = map
            !same
        }
        if (changed) scheduleSave()
        return true
    }

    fun removeSessionCookies(): Boolean {
        ensureLoaded()
        val removed = synchronized(lock) {
            revision++
            val session = cookies.values.filter { it.expires == null }
            session.forEach { cookies.remove(it.key) }
            backend?.let { b -> session.forEach { runCatching { b.delete(it) } } }
            session
        }
        if (removed.isNotEmpty()) scheduleSave()
        return removed.isNotEmpty()
    }

    fun removeAll(): Boolean {
        ensureLoaded()
        val had = synchronized(lock) {
            revision++
            val any = cookies.isNotEmpty()
            cookies = LinkedHashMap()
            backend?.let { runCatching { it.deleteAll() } }
            any
        }
        scheduleSave()
        return had
    }

    private fun parseHttpDate(s: String): Long? {
        val formats = arrayOf("EEE, dd MMM yyyy HH:mm:ss zzz", "EEE, dd-MMM-yyyy HH:mm:ss zzz", "EEE, dd-MMM-yy HH:mm:ss zzz", "EEE MMM d HH:mm:ss yyyy")
        for (f in formats) {
            try {
                return java.text.SimpleDateFormat(f, java.util.Locale.US).parse(s).time
            } catch (_: Exception) {
            }
        }
        return null
    }
}
