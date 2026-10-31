package com.lagradost.desktop.runtime.web

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * What a page that an extension in the sandbox asks the app to open may reach. The sandbox itself cannot touch this PC's own services or the home network (Windows keeps
 * it out), so a page the app opens for it must not be a way in: an address that is this PC (the app's own local servers, anything listening on localhost), the home or
 * company network, a link-local or carrier-grade address (cloud metadata, a router) is refused, for the page and for everything it loads or is redirected to. A name is
 * looked up and refused when any of its addresses is such an address.
 */
object PrivateNetworkGuard {
    private class Verdict(val allowed: Boolean, val at: Long)

    private val cache = ConcurrentHashMap<String, Verdict>()
    private const val CACHE_MS = 30_000L

    private val localSuffixes = listOf(".localhost", ".local", ".internal", ".lan", ".home", ".corp", ".intranet", ".home.arpa", ".localdomain")

    /** Whether [url] may be loaded: a public web address, or a page without a network (about:blank, data:, blob:) */
    @JvmStatic
    fun isAllowed(url: String): Boolean {
        val lower = url.trim().lowercase()
        if (lower == "about:blank" || lower.startsWith("data:") || lower.startsWith("blob:")) return true
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() !in setOf("http", "https", "ws", "wss")) return false
        val host = uri.host?.lowercase()?.trim('[', ']', '.')?.takeIf { it.isNotEmpty() } ?: return false
        return hostAllowed(host)
    }

    /** Hosts that a test of this app lets through (a page on this PC's loopback address that the test itself serves); nothing sets it in a normal run */
    @Volatile
    var testAllowedHosts: Set<String> = emptySet()

    fun hostAllowed(host: String): Boolean {
        if (host in testAllowedHosts) return true
        val now = System.currentTimeMillis()
        cache[host]?.let { if (now - it.at < CACHE_MS) return it.allowed }
        val allowed = compute(host)
        if (cache.size > 2_000) cache.clear()
        cache[host] = Verdict(allowed, now)
        return allowed
    }

    private fun compute(host: String): Boolean {
        if (host == "localhost" || localSuffixes.any { host.endsWith(it) }) return false
        val addresses = try {
            InetAddress.getAllByName(host)
        } catch (e: Exception) {
            // a name that does not resolve cannot be loaded either; a single word is a name of the local network
            return host.contains('.')
        }
        return addresses.none { isPrivate(it) }
    }

    /** An address that is this PC, the local network or not routed on the internet */
    @JvmStatic
    fun isPrivate(a: InetAddress): Boolean {
        if (a.isAnyLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress || a.isSiteLocalAddress || a.isMulticastAddress) return true
        val b = a.address
        return when (a) {
            is Inet4Address -> {
                val x = b[0].toInt() and 0xFF
                val y = b[1].toInt() and 0xFF
                x == 0 || (x == 100 && y in 64..127) || (x == 192 && y == 0 && (b[2].toInt() and 0xFF) == 0) || (x == 198 && y in 18..19) || x >= 240
            }
            is Inet6Address -> (b[0].toInt() and 0xFE) == 0xFC // unique local addresses (fc00::/7)
            else -> true
        }
    }
}
