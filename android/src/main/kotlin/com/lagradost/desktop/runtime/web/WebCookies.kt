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

    /** Every cookie that has not expired */
    fun all(): List<WebCookie> {
        ensureLoaded()
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
        put(WebCookie(name, v, domain, path, expires, secure, httpOnly))
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
