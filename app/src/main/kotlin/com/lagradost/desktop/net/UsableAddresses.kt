package com.lagradost.desktop.net

import okhttp3.Dns
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Hosts behind CDNs (TMDB, Google, Cloudflare, ...) answer with IPv4 and IPv6 addresses. OkHttp tries IPv6 first ("happy eyeballs"), and on
 * a network without IPv6 that attempt fails at once with "Network is unreachable"; on this app's client about every fourth request to
 * such a host failed that way (searches in extensions that ask TMDB came back empty, ...). Browsers and curl fall back quietly.
 * So: when the machine has no globally routable IPv6 address, the IPv6 addresses of a host that also has IPv4 ones are left out.
 */
object UsableAddresses {
    private const val CHECK_EVERY_MS = 15_000L

    @Volatile
    private var checkedAt = 0L

    @Volatile
    private var hasIpv6 = true

    fun wrap(delegate: Dns): Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            var all = delegate.lookup(hostname)
            if (all.any { it is Inet6Address } && all.any { it is Inet4Address } && !ipv6Usable()) all = all.filter { it is Inet4Address }
            // addresses that just failed (see Quarantine) are tried last
            return if (all.size > 1) all.sortedBy { if (Quarantine.isBad(hostname, it)) 1 else 0 } else all
        }
    }

    private fun ipv6Usable(): Boolean {
        val now = System.currentTimeMillis()
        if (now - checkedAt < CHECK_EVERY_MS) return hasIpv6
        hasIpv6 = runCatching {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.asSequence() }
                .any { a -> a is Inet6Address && isGlobal(a) }
        }.getOrDefault(true)
        checkedAt = now
        return hasIpv6
    }

    /** A routable address: not loopback, link-local, unique-local (fc00::/7), Teredo (2001::/32) or 6to4 (2002::/16) */
    private fun isGlobal(a: Inet6Address): Boolean {
        if (a.isLoopbackAddress || a.isLinkLocalAddress || a.isSiteLocalAddress || a.isAnyLocalAddress || a.isMulticastAddress) return false
        val b = a.address
        val first = b[0].toInt() and 0xFF
        if (first and 0xFE == 0xFC) return false
        if (first == 0x20 && b[1].toInt() == 0x02) return false
        if (first == 0x20 && b[1].toInt() == 0x01 && b[2].toInt() == 0 && b[3].toInt() == 0) return false
        return true
    }
}
