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

    /** A chosen DNS-over-HTTPS service that failed this many names in a row (and the system DNS knew them) is taken for down */
    private const val DOH_MISSES_TO_DOWN = 2
    private const val DOH_DOWN_MS = 5 * 60_000L

    @Volatile
    private var dohDownUntil = 0L
    private val dohMisses = java.util.concurrent.atomic.AtomicInteger()
    private val IPV4_LITERAL = Regex("""\d{1,3}(\.\d{1,3}){3}""")

    /**
     * The names go to the chosen DNS-over-HTTPS service (Settings > Providers) and, when it does not know one, to the system DNS: a host of an operator's own
     * network (JIO TV's CDN) only resolves there, and the phone, which has no such setting, plays it. Some networks block the service altogether (the owner's: every
     * lookup, TMDB and GitHub too, failed after the service's timeouts, so everything waited for them first: pictures did not load, JIO TV did not start). After
     * [DOH_MISSES_TO_DOWN] misses in a row the system DNS goes first for [DOH_DOWN_MS] (and the service is asked when the system does not know a name).
     */
    private fun resolve(delegate: Dns, hostname: String): List<InetAddress> {
        if (delegate === Dns.SYSTEM || hostname == "localhost" || ':' in hostname || IPV4_LITERAL.matches(hostname)) return Dns.SYSTEM.lookup(hostname)
        if (System.currentTimeMillis() < dohDownUntil) {
            return try { Dns.SYSTEM.lookup(hostname) } catch (e: java.net.UnknownHostException) { delegate.lookup(hostname) }
        }
        return try {
            delegate.lookup(hostname).also { dohMisses.set(0) }
        } catch (e: java.net.UnknownHostException) {
            val system = try { Dns.SYSTEM.lookup(hostname) } catch (_: java.net.UnknownHostException) { throw e }
            val misses = dohMisses.incrementAndGet()
            android.util.Log.i("UsableAddresses", "$hostname: not known to the chosen DNS service (${e.message?.take(80)}), the system DNS says ${system.joinToString { it.hostAddress ?: "?" }}")
            if (misses >= DOH_MISSES_TO_DOWN) {
                dohDownUntil = System.currentTimeMillis() + DOH_DOWN_MS
                dohMisses.set(0)
                android.util.Log.w("UsableAddresses", "the chosen DNS service failed $misses names in a row: the system DNS is used first for ${DOH_DOWN_MS / 60_000} minutes")
            }
            system
        }
    }

    /**
     * [resolve], and when the resolvers say nothing useful (no such host, or the "unspecified" address 0.0.0.0 / `::` that filtering DNS services give for
     * a host they block) a second opinion from public resolvers, see [PublicDns]. A name that nobody knows still fails with the first error.
     */
    private fun resolveOrAskPublic(delegate: Dns, hostname: String): List<InetAddress> {
        val plainName = hostname != "localhost" && ':' !in hostname && !IPV4_LITERAL.matches(hostname) && '.' in hostname
        val found = try {
            resolve(delegate, hostname)
        } catch (e: java.net.UnknownHostException) {
            if (!plainName || !PublicDns.enabled) throw e
            return PublicDns.lookup(hostname).ifEmpty { throw e }
        }
        if (plainName && found.isNotEmpty() && found.all { it.isAnyLocalAddress } && PublicDns.enabled) {
            PublicDns.lookup(hostname).let { if (it.isNotEmpty()) return it }
        }
        return found
    }

    fun wrap(delegate: Dns): Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            var all = resolveOrAskPublic(delegate, hostname)
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
