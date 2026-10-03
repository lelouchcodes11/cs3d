package com.lagradost.desktop.net

import android.util.Log
import com.lagradost.cloudstream3.app
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * A small local HTTP proxy for the video player (mpv). Its only job is the network settings the app already has:
 * names are resolved with the app's DNS (Settings -> DNS over HTTPS), exactly like the extensions' requests and, on Android,
 * ExoPlayer do. Without it mpv uses the system resolver, so a streaming host that a filtering DNS (1.1.1.2, ISP, school) blocks
 * plays on Android and not here.
 *
 * `CONNECT host:port` (https) becomes a plain byte tunnel, so TLS stays between mpv and the server; plain http requests are
 * forwarded with `Connection: close`.
 */
object NetProxy {
    private const val TAG = "NetProxy"
    private const val CONNECT_TIMEOUT_MS = 9_000
    private const val IDLE_TIMEOUT_MS = 120_000
    private const val DNS_TTL_MS = 60_000L
    private const val FIRST_FLIGHT_WAIT_MS = 1_500
    private const val FIRST_BYTE_TIMEOUT_MS = 8_000
    private const val MAX_TUNNEL_ATTEMPTS = 5

    private val dnsCache = ConcurrentHashMap<String, Pair<Long, List<InetAddress>>>()

    private val server: ServerSocket? by lazy {
        runCatching {
            ServerSocket(0, 64, InetAddress.getByName("127.0.0.1")).also { s ->
                Thread({
                    while (!s.isClosed) {
                        val client = try { s.accept() } catch (_: IOException) { break }
                        Thread.ofVirtual().name("net-proxy").start { handle(client) }
                    }
                }, "net-proxy-accept").apply { isDaemon = true }.start()
                Log.i(TAG, "listening on 127.0.0.1:${s.localPort}")
            }
        }.onFailure { Log.w(TAG, "could not start: ${it.message}") }.getOrNull()
    }

    /** `http://127.0.0.1:<port>`, or null when it could not start (the player then connects directly) */
    val address: String? get() = server?.let { "http://127.0.0.1:${it.localPort}" }

    // ---------------------------------------------------------------------------------------------

    private fun resolve(host: String): List<InetAddress> {
        val now = System.currentTimeMillis()
        dnsCache[host]?.let { (at, list) -> if (now - at < DNS_TTL_MS) return list }
        val found = try {
            app.baseClient.dns.lookup(host)
        } catch (t: Throwable) {
            // the chosen resolver does not know it: the system's may
            runCatching { InetAddress.getAllByName(host).toList() }.getOrElse { throw t }
        }
        // IPv4 first: an IPv6 address on a machine without IPv6 routes costs seconds
        val ordered = found.sortedBy { if (it is Inet4Address) 0 else 1 }
        if (ordered.isEmpty()) throw java.net.UnknownHostException(host)
        dnsCache[host] = now to ordered
        return ordered
    }

    /** The first address of [host] that accepts a connection, not one of [skip]; addresses that recently failed (Quarantine) come last */
    private fun connect(host: String, port: Int, skip: Set<InetAddress> = emptySet()): Socket {
        var last: Throwable? = null
        val addresses = resolve(host).let { l -> if (l.size > 1) l.sortedBy { if (Quarantine.isBad(host, it)) 1 else 0 } else l }
        for (address in addresses) {
            if (address in skip) continue
            val socket = Socket()
            try {
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = IDLE_TIMEOUT_MS
                return socket
            } catch (t: Throwable) {
                last = t
                if (t is IOException && Quarantine.addressProblem(t)) Quarantine.mark(host, address)
                runCatching { socket.close() }
            }
        }
        throw last ?: java.net.UnknownHostException(host)
    }

    /**
     * What the client sends first on a tunnel (the TLS ClientHello); empty when it sends nothing soon (the server speaks first).
     * It is kept so that it can be sent again to another address of the host, see [tunnel].
     */
    private fun readFirstFlight(client: Socket, input: InputStream): ByteArray {
        client.soTimeout = FIRST_FLIGHT_WAIT_MS
        return try {
            val buffer = ByteArray(16_384)
            val n = input.read(buffer)
            if (n > 0) buffer.copyOf(n) else ByteArray(0)
        } catch (_: java.net.SocketTimeoutException) {
            ByteArray(0)
        } finally {
            client.soTimeout = IDLE_TIMEOUT_MS
        }
    }

    /**
     * Sends the client's first flight to [upstream] and waits for the first byte of the answer. Some addresses of a CDN host reset the
     * connection at that point from some networks (and answer for another address of the same host): then the connection is
     * made to the next address and the same bytes are sent again, which the player does not notice. Returns the server's connection and
     * its first byte (-1: nothing to add), or null when no address of the host answered.
     */
    private fun tunnel(host: String, port: Int, first: ByteArray, connected: Socket): Pair<Socket, Int>? {
        if (first.isEmpty()) return connected to -1
        var up = connected
        val tried = HashSet<InetAddress>()
        for (attempt in 1..MAX_TUNNEL_ATTEMPTS) {
            up.inetAddress?.let { tried += it }
            val answer = try {
                up.getOutputStream().write(first)
                up.getOutputStream().flush()
                up.soTimeout = FIRST_BYTE_TIMEOUT_MS
                up.getInputStream().read()
            } catch (_: IOException) {
                -1
            }
            if (answer >= 0) {
                up.soTimeout = IDLE_TIMEOUT_MS
                return up to answer
            }
            up.inetAddress?.let { Quarantine.mark(host, it) }
            Log.w(TAG, "$host:$port: no answer from ${up.inetAddress?.hostAddress} (attempt $attempt)")
            runCatching { up.close() }
            if (attempt == MAX_TUNNEL_ATTEMPTS) break
            up = try { connect(host, port, tried) } catch (_: Throwable) { return null }
        }
        return null
    }

    private fun readHead(input: InputStream): String? {
        val sb = StringBuilder()
        while (sb.length < 32_768) {
            val b = input.read()
            if (b < 0) return null
            sb.append(b.toChar())
            val n = sb.length
            if (n >= 4 && sb[n - 4] == '\r' && sb[n - 3] == '\n' && sb[n - 2] == '\r' && sb[n - 1] == '\n') return sb.substring(0, n - 4)
        }
        return null
    }

    private fun handle(client: Socket) {
        client.use {
            try {
                client.tcpNoDelay = true
                client.soTimeout = IDLE_TIMEOUT_MS
                val input = BufferedInputStream(client.getInputStream())
                val out = client.getOutputStream()
                val head = readHead(input) ?: return
                val lines = head.split("\r\n")
                val request = lines[0].split(" ")
                if (request.size < 3) return
                val method = request[0]
                val target = request[1]
                val upstream: Socket
                if (method.equals("CONNECT", true)) {
                    val host = target.substringBeforeLast(':').removePrefix("[").removeSuffix("]")
                    val port = target.substringAfterLast(':').toIntOrNull() ?: 443
                    upstream = try { connect(host, port) } catch (t: Throwable) {
                        Log.w(TAG, "$host:$port: ${t.javaClass.simpleName} ${t.message}")
                        out.write("HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n".toByteArray()); return
                    }
                    out.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                    out.flush()
                    val first = readFirstFlight(client, input)
                    val (up, firstByte) = tunnel(host, port, first, upstream) ?: return
                    if (firstByte >= 0) { out.write(firstByte); out.flush() }
                    up.use { pipe(client, input, out, it) }
                    return
                } else {
                    val uri = URI(target)
                    val host = uri.host ?: return
                    val port = if (uri.port > 0) uri.port else 80
                    upstream = try { connect(host, port) } catch (t: Throwable) {
                        Log.w(TAG, "$host:$port: ${t.javaClass.simpleName} ${t.message}")
                        out.write("HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n".toByteArray()); return
                    }
                    val path = (uri.rawPath?.ifEmpty { "/" } ?: "/") + (uri.rawQuery?.let { "?$it" } ?: "")
                    val rewritten = StringBuilder("$method $path ${request[2]}\r\n")
                    for (line in lines.drop(1)) {
                        val name = line.substringBefore(':').trim()
                        if (name.equals("Proxy-Connection", true) || name.equals("Connection", true) || name.isEmpty()) continue
                        rewritten.append(line).append("\r\n")
                    }
                    rewritten.append("Connection: close\r\n\r\n")
                    upstream.getOutputStream().write(rewritten.toString().toByteArray(Charsets.ISO_8859_1))
                }
                upstream.use { up -> pipe(client, input, out, up) }
            } catch (_: IOException) {
                // the player closed the connection (seeking, stopping): nothing to report
            } catch (t: Throwable) {
                Log.w(TAG, "request failed: ${t.message}")
            }
        }
    }

    private fun pipe(client: Socket, clientIn: InputStream, clientOut: OutputStream, up: Socket) {
        val toServer = Thread.ofVirtual().start {
            try { clientIn.transferTo(up.getOutputStream()) } catch (_: IOException) { }
            runCatching { up.shutdownOutput() }
        }
        try { up.getInputStream().transferTo(clientOut) } catch (_: IOException) { }
        runCatching { client.shutdownOutput() }
        // the other direction ends with the connection; do not wait for a client that keeps its side open
        runCatching { toServer.join(1_000) }
    }
}
