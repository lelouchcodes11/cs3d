package com.lagradost.desktop.net

import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Proxy
import java.net.UnknownHostException
import java.security.cert.CertificateException
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Some addresses of a host do not work from a given network although the host does: with DNS over HTTPS the answer can contain CDN
 * edges that reset every connection ("Connection reset" while the TLS handshake starts; on this machine 3.175.86.67 of TMDB resets
 * all of them, .37 three of four) while others answer every time. OkHttp then does not move on to the next address, so about half of
 * all requests of such a host fail, and extensions swallow the error and show "no results".
 *
 * - [listenerFactory] remembers addresses whose connection attempt failed that way for a few minutes ("quarantine");
 * - [UsableAddresses] puts them last when resolving, so the next attempt goes to another address of the host;
 * - [RetryQuickFailures] repeats a GET/HEAD that failed that way within a moment (up to 6 attempts), which then lands on a working one.
 */
object Quarantine {
    private const val TTL_MS = 10 * 60 * 1000L
    private val bad = ConcurrentHashMap<String, Long>()

    private fun key(host: String, address: InetAddress) = host.lowercase() + "|" + address.hostAddress

    fun isBad(host: String, address: InetAddress): Boolean {
        val k = key(host, address)
        val until = bad[k] ?: return false
        if (until < System.currentTimeMillis()) {
            bad.remove(k)
            return false
        }
        return true
    }

    fun mark(host: String, address: InetAddress) {
        if (bad.size > 2000) bad.clear()
        bad[key(host, address)] = System.currentTimeMillis() + TTL_MS
    }

    fun clear(host: String, address: InetAddress) {
        bad.remove(key(host, address))
    }

    /** Failures that say "this address does not work (now)", as opposed to a slow or refusing server */
    fun addressProblem(e: IOException): Boolean {
        if (e is SSLPeerUnverifiedException || (e is SSLHandshakeException && e.cause is CertificateException)) return false
        if (e is UnknownHostException) return false
        if (e is ConnectException || e is NoRouteToHostException) return true
        val m = e.message.orEmpty().lowercase()
        return "reset" in m || "unreachable" in m || "refused" in m || "broken pipe" in m || "unexpected end of stream" in m ||
            "connection closed" in m || "software caused" in m
    }

    val listenerFactory = EventListener.Factory {
        object : EventListener() {
            override fun connectFailed(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?, ioe: IOException) {
                val address = inetSocketAddress.address ?: return
                if (addressProblem(ioe)) mark(call.request().url.host, address)
            }

            override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) {
                val address = inetSocketAddress.address ?: return
                clear(call.request().url.host, address)
            }
        }
    }
}

/** Repeats an idempotent request that failed quickly because the address it used does not work, see [Quarantine] */
object RetryQuickFailures : Interceptor {
    private const val MAX_ATTEMPTS = 6
    private const val QUICK_NS = 4_000_000_000L

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET" && request.method != "HEAD") return chain.proceed(request)
        var attempt = 0
        while (true) {
            val started = System.nanoTime()
            try {
                return chain.proceed(request)
            } catch (e: IOException) {
                attempt++
                if (attempt >= MAX_ATTEMPTS || chain.call().isCanceled() || System.nanoTime() - started > QUICK_NS || !Quarantine.addressProblem(e)) throw e
            }
        }
    }
}
