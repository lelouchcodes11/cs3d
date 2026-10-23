package com.lagradost.desktop.net

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.LinkedBlockingQueue

/**
 * A second opinion for names the machine's own DNS refuses. A filtering resolver (Cloudflare's 1.1.1.2 "malware" service, family and school
 * DNS, some ISPs) answers 0.0.0.0 / `::` or "no such host" for hosts that stream sites use (the MegaPlay proxy `fetch.nexabloom.top` behind AniKoto,
 * Anikage and Animex, MovieBlast's CDN); the same names resolve everywhere else. DNS over HTTPS (Settings > Network) is the usual way round and
 * is often blocked on those very networks, so this asks public resolvers over plain UDP, the way `nslookup name 1.1.1.1` does.
 *
 * Only called after the normal lookup failed or answered with an "unspecified" address; the answers are cached. A Settings switch turns it off.
 */
object PublicDns {
    private const val TAG = "PublicDns"
    private val SERVERS = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9")
    private const val TIMEOUT_MS = 2_000L
    private const val MIN_TTL_S = 30
    private const val MAX_TTL_S = 300
    private const val NEGATIVE_TTL_MS = 60_000L
    private const val SWITCH_KEY = "public_dns_fallback_key"
    private const val SWITCH_CACHE_MS = 5_000L

    private class Entry(val until: Long, val addresses: List<InetAddress>)

    private val cache = ConcurrentHashMap<String, Entry>()

    @Volatile
    private var switchReadAt = 0L

    @Volatile
    private var switchOn = true

    /** Settings > Network > "Ask public DNS about blocked names" (on unless the user turned it off) */
    val enabled: Boolean
        get() {
            val now = System.currentTimeMillis()
            if (now - switchReadAt > SWITCH_CACHE_MS) {
                switchOn = runCatching {
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(com.lagradost.desktop.runtime.AndroidRuntime.context).getBoolean(SWITCH_KEY, true)
                }.getOrDefault(true)
                switchReadAt = now
            }
            return switchOn
        }

    /** The addresses public resolvers give for [host]; empty when they do not know it (or cannot be reached) */
    fun lookup(host: String): List<InetAddress> {
        val now = System.currentTimeMillis()
        cache[host]?.let { if (it.until > now) return it.addresses }
        var reply = ask(host, TYPE_A)
        if (reply != null && reply.addresses.isEmpty() && !reply.nxdomain) reply = ask(host, TYPE_AAAA) ?: reply
        val addresses = reply?.addresses.orEmpty().filterNot { it.isAnyLocalAddress }
        val ttlMs = if (addresses.isEmpty()) NEGATIVE_TTL_MS else (reply!!.ttlSeconds.coerceIn(MIN_TTL_S, MAX_TTL_S) * 1000L)
        cache[host] = Entry(System.currentTimeMillis() + ttlMs, addresses)
        if (addresses.isNotEmpty()) Log.i(TAG, "$host: the system DNS refuses it, public DNS says ${addresses.joinToString { it.hostAddress ?: "?" }}")
        return addresses
    }

    private class Reply(val addresses: List<InetAddress>, val ttlSeconds: Int, val nxdomain: Boolean)

    /** What a resolver that did not answer leaves in the queue (which cannot hold null) */
    private val NO_ANSWER = Reply(emptyList(), 0, false)

    /** All resolvers at once, the first usable answer wins (a resolver that cannot be reached must not make the others wait) */
    private fun ask(host: String, type: Int): Reply? {
        val queue = LinkedBlockingQueue<Reply>()
        for (server in SERVERS) Thread.ofVirtual().name("public-dns").start { queue.put(runCatching { query(server, host, type) }.getOrNull() ?: NO_ANSWER) }
        val end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MS + 500)
        var waiting = SERVERS.size
        var nx: Reply? = null
        while (waiting > 0) {
            val left = end - System.nanoTime()
            if (left <= 0) break
            val r = queue.poll(left, TimeUnit.NANOSECONDS) ?: break
            waiting--
            if (r.addresses.isNotEmpty()) return r
            if (r.nxdomain) nx = r
        }
        return nx
    }

    private fun query(server: String, host: String, type: Int): Reply? {
        val id = ThreadLocalRandom.current().nextInt(0x10000)
        val packet = buildQuery(id, host, type) ?: return null
        DatagramSocket().use { socket ->
            socket.soTimeout = TIMEOUT_MS.toInt()
            socket.send(DatagramPacket(packet, packet.size, InetAddress.getByName(server), 53))
            val buffer = ByteArray(1500)
            val end = System.currentTimeMillis() + TIMEOUT_MS
            while (System.currentTimeMillis() < end) {
                val received = DatagramPacket(buffer, buffer.size)
                socket.receive(received)
                if (received.length >= 12 && u16(buffer, 0) == id) return parse(buffer, received.length, host)
            }
        }
        return null
    }

    private fun buildQuery(id: Int, host: String, type: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        fun w16(v: Int) { out.write(v shr 8 and 0xFF); out.write(v and 0xFF) }
        w16(id); w16(0x0100); w16(1); w16(0); w16(0); w16(0)
        for (label in host.trimEnd('.').split('.')) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            if (bytes.isEmpty() || bytes.size > 63) return null
            out.write(bytes.size); out.write(bytes)
        }
        out.write(0)
        w16(type); w16(1)
        return out.toByteArray()
    }

    private fun parse(b: ByteArray, length: Int, host: String): Reply? {
        val flags = u16(b, 2)
        if (flags and 0x8000 == 0) return null
        val rcode = flags and 0xF
        if (rcode != 0 && rcode != 3) return null // SERVFAIL / REFUSED: not an answer
        var pos = 12
        repeat(u16(b, 4)) { pos = skipName(b, pos, length) + 4 }
        val found = ArrayList<InetAddress>()
        var ttl = Int.MAX_VALUE
        for (i in 0 until u16(b, 6)) {
            pos = skipName(b, pos, length)
            if (pos + 10 > length) break
            val type = u16(b, pos)
            val recordTtl = (u16(b, pos + 4) shl 16) or u16(b, pos + 6)
            val rdlength = u16(b, pos + 8)
            pos += 10
            if (pos + rdlength > length) break
            if ((type == TYPE_A && rdlength == 4) || (type == TYPE_AAAA && rdlength == 16)) {
                found += InetAddress.getByAddress(host, b.copyOfRange(pos, pos + rdlength))
                if (recordTtl in 0 until ttl) ttl = recordTtl
            }
            pos += rdlength
        }
        return Reply(found, if (ttl == Int.MAX_VALUE) MIN_TTL_S else ttl, rcode == 3)
    }

    /** The position after a (possibly compressed) name */
    private fun skipName(b: ByteArray, start: Int, length: Int): Int {
        var pos = start
        while (pos < length) {
            val l = b[pos].toInt() and 0xFF
            if (l == 0) return pos + 1
            if (l and 0xC0 == 0xC0) return pos + 2
            pos += l + 1
        }
        return length
    }

    private fun u16(b: ByteArray, at: Int) = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

    private const val TYPE_A = 1
    private const val TYPE_AAAA = 28
}
