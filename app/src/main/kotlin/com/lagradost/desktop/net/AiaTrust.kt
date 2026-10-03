package com.lagradost.desktop.net

import android.util.Log
import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Completes certificate chains the way browsers do. Plenty of small streaming sites send their own certificate and the
 * intermediate but not the cross-signed link up to a root the device knows (Let's Encrypt's 2026 "Root YE" hierarchy is the
 * current example: the server sends `leaf, YE2`, and the cross-signed `Root YE` that leads to ISRG Root X2 only exists behind the
 * intermediate's "CA Issuers" address). Windows and browsers fetch that link; the JVM does not, so the site fails here and
 * works in a browser.
 *
 * Only a failed validation is retried, and the fetched certificate is no trust anchor: the extended chain still has to end in a
 * root of the normal trust store (the merged JVM + OS roots, see [com.lagradost.desktop.TrustStore]) with every signature valid.
 */
object AiaTrust {
    private const val TAG = "AiaTrust"
    private const val CA_ISSUERS_OID = "1.3.6.1.5.5.7.48.2"
    private const val NEGATIVE_TTL_MS = 30_000L

    private class Fetched(val certs: List<X509Certificate>, val at: Long)

    private val fetched = ConcurrentHashMap<String, Fetched>()
    private val factory = CertificateFactory.getInstance("X.509")

    /** The client trusts what the platform trusts plus what [complete] can add */
    fun install(builder: OkHttpClient.Builder) {
        try {
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(null as KeyStore?)
            val base = tmf.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull() ?: return
            val manager = wrap(base)
            val context = runCatching { SSLContext.getInstance("TLS", "Conscrypt") }.getOrElse { SSLContext.getInstance("TLS") }
            context.init(null, arrayOf(manager), null)
            builder.sslSocketFactory(context.socketFactory, manager)
        } catch (t: Throwable) {
            Log.w(TAG, "chain completion not installed: $t")
        }
    }

    private fun wrap(base: X509TrustManager) = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = base.checkClientTrusted(chain, authType)
        override fun getAcceptedIssuers(): Array<X509Certificate> = base.acceptedIssuers
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            try {
                base.checkServerTrusted(chain, authType)
            } catch (first: CertificateException) {
                val extended = complete(chain) ?: throw first
                try {
                    base.checkServerTrusted(extended, authType)
                    Log.i(TAG, "${chain.firstOrNull()?.subjectX500Principal?.name}: trusted after fetching ${extended.size - chain.size} issuer certificate(s)")
                } catch (_: CertificateException) {
                    throw first
                }
            }
        }
    }

    /** [chain] followed by the issuers found behind the last certificate's "CA Issuers" address; null when nothing was found */
    private fun complete(chain: Array<X509Certificate>): Array<X509Certificate>? {
        val result = chain.toMutableList()
        repeat(3) {
            val last = result.last()
            if (last.subjectX500Principal == last.issuerX500Principal) return if (result.size > chain.size) result.toTypedArray() else null
            val issuer = findIssuer(last) ?: return if (result.size > chain.size) result.toTypedArray() else null
            result.add(issuer)
        }
        return if (result.size > chain.size) result.toTypedArray() else null
    }

    private fun findIssuer(cert: X509Certificate): X509Certificate? {
        for (url in caIssuerUrls(cert)) {
            for (candidate in download(url)) {
                if (candidate.subjectX500Principal != cert.issuerX500Principal) continue
                if (runCatching { cert.verify(candidate.publicKey) }.isSuccess) return candidate
            }
        }
        return null
    }

    private fun download(url: String): List<X509Certificate> {
        val now = System.currentTimeMillis()
        fetched[url]?.let { if (it.certs.isNotEmpty() || now - it.at < NEGATIVE_TTL_MS) return it.certs }
        // a few tries: on a network that resets some connections one failure must not decide for the next minutes
        var certs: List<X509Certificate> = emptyList()
        for (attempt in 1..3) {
            certs = try {
                val uri = URI(url)
                if (uri.scheme != "http" && uri.scheme != "https") emptyList() else {
                    val connection = uri.toURL().openConnection() as HttpURLConnection
                    connection.connectTimeout = 5_000
                    connection.readTimeout = 5_000
                    connection.instanceFollowRedirects = true
                    val bytes = connection.inputStream.use { it.readNBytes(256 * 1024) }
                    factory.generateCertificates(ByteArrayInputStream(bytes)).filterIsInstance<X509Certificate>()
                }
            } catch (t: Throwable) {
                Log.d(TAG, "$url (attempt $attempt): $t")
                emptyList()
            }
            if (certs.isNotEmpty()) break
        }
        fetched[url] = Fetched(certs, now)
        return certs
    }

    // ---- the Authority Information Access extension (RFC 5280 4.2.2.1), read by hand: the JDK classes for it are internal ----

    private class Tlv(val tag: Int, val start: Int, val end: Int)

    private fun tlv(data: ByteArray, at: Int, limit: Int): Tlv? {
        if (at + 2 > limit) return null
        val tag = data[at].toInt() and 0xff
        val first = data[at + 1].toInt() and 0xff
        var length = first
        var header = 2
        if (first and 0x80 != 0) {
            val count = first and 0x7f
            if (count == 0 || count > 3 || at + 2 + count > limit) return null
            length = 0
            for (i in 0 until count) length = (length shl 8) or (data[at + 2 + i].toInt() and 0xff)
            header = 2 + count
        }
        val end = at + header + length
        return if (end > limit) null else Tlv(tag, at + header, end)
    }

    private fun caIssuerUrls(cert: X509Certificate): List<String> {
        val value = cert.getExtensionValue("1.3.6.1.5.5.7.1.1") ?: return emptyList() // OCTET STRING { SEQUENCE OF AccessDescription }
        val urls = ArrayList<String>()
        val octets = tlv(value, 0, value.size) ?: return urls
        val sequence = tlv(value, octets.start, octets.end) ?: return urls
        var position = sequence.start
        while (position < sequence.end) {
            val description = tlv(value, position, sequence.end) ?: break
            position = description.end
            val oid = tlv(value, description.start, description.end) ?: continue
            val name = tlv(value, oid.end, description.end) ?: continue
            if (oid.tag != 0x06 || name.tag != 0x86) continue // 0x86 = [6] uniformResourceIdentifier
            if (decodeOid(value, oid.start, oid.end) == CA_ISSUERS_OID) urls.add(String(value, name.start, name.end - name.start, Charsets.ISO_8859_1))
        }
        return urls
    }

    private fun decodeOid(data: ByteArray, start: Int, end: Int): String {
        if (end <= start) return ""
        val sb = StringBuilder()
        val firstByte = data[start].toInt() and 0xff
        sb.append(firstByte / 40).append('.').append(firstByte % 40)
        var value = 0L
        for (i in start + 1 until end) {
            val b = data[i].toInt() and 0xff
            value = (value shl 7) or (b and 0x7f).toLong()
            if (b and 0x80 == 0) { sb.append('.').append(value); value = 0 }
        }
        return sb.toString()
    }
}
