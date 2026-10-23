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
 * When an engine starts it is given this jar ([BrowserBackend.pushAll]), so this store is the one that counts.
 */
object WebCookies {
    private const val TAG = "WebCookies"

    /** The browser engine that mirrors the jar while it runs */
    interface BrowserBackend {
        fun push(cookie: WebCookie)
        fun delete(cookie: WebCookie)
        fun deleteAll()
    }

    @Volatile var backend: BrowserBackend? = null

    private val lock = Any()
    private var cookies = LinkedHashMap<String, WebCookie>()
    private val loaded = AtomicBoolean(false)
    private val saver = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "cookie-store").also { it.isDaemon = true } }
    private val saveQueued = AtomicBoolean(false)

    private val file: File get() = File(AndroidRuntime.dataDir, "web/cookies.bin")

    private fun ensureLoaded() {
        if (!loaded.compareAndSet(false, true)) return
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
            synchronized(lock) { cookies = map }
        }.onFailure { Log.w(TAG, "cookie store not read: ${it.message}") }
    }

    private fun scheduleSave() {
        if (!saveQueued.compareAndSet(false, true)) return
        saver.schedule({
            saveQueued.set(false)
            runCatching {
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
                if (!tmp.renameTo(f)) {
                    f.delete()
                    tmp.renameTo(f)
                }
            }.onFailure { Log.w(TAG, "cookie store not saved: ${it.message}") }
        }, 1, TimeUnit.SECONDS)
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
            domainMatches(host, c.domain) && path.startsWith(c.path) && (!c.secure || secure)
        }.sortedByDescending { it.path.length }
        if (matching.isEmpty()) return null
        return matching.joinToString("; ") { "${it.name}=${it.value}" }
    }

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
            if (cookie.expired()) cookies.remove(cookie.key) else cookies[cookie.key] = cookie
        }
        scheduleSave()
        backend?.let { b -> runCatching { if (cookie.expired()) b.delete(cookie) else b.push(cookie) } }
    }

    /** The running engine's whole jar after a page load: it becomes this jar (the engine got this jar when it started) */
    fun replaceAll(fromBrowser: List<WebCookie>) {
        ensureLoaded()
        val map = LinkedHashMap<String, WebCookie>()
        for (c in fromBrowser) if (!c.expired()) map[c.key] = c
        val changed = synchronized(lock) {
            val same = map.keys == cookies.keys && map.all { (k, c) -> cookies[k] == c }
            if (!same) cookies = map
            !same
        }
        if (changed) scheduleSave()
    }

    fun removeSessionCookies(): Boolean {
        ensureLoaded()
        val removed = synchronized(lock) {
            val session = cookies.values.filter { it.expires == null }
            session.forEach { cookies.remove(it.key) }
            session
        }
        if (removed.isNotEmpty()) scheduleSave()
        backend?.let { b -> removed.forEach { runCatching { b.delete(it) } } }
        return removed.isNotEmpty()
    }

    fun removeAll(): Boolean {
        ensureLoaded()
        val had = synchronized(lock) {
            val any = cookies.isNotEmpty()
            cookies = LinkedHashMap()
            any
        }
        scheduleSave()
        backend?.let { runCatching { it.deleteAll() } }
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
