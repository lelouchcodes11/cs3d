package com.lagradost.desktop

import android.util.Log
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Android trusts the operating system's certificate store; the JVM only trusts its bundled cacerts.
 * Some sites (e.g. DoFlix) chain to roots that only the OS knows, so the default trust store is
 * replaced with the union of the JVM roots and the Windows (or macOS) roots. Must run before any
 * TLS connection is made.
 */
object TrustStore {
    private const val TAG = "TrustStore"
    private const val PASSWORD = "changeit"

    fun install(dataDir: File) {
        try {
            val file = File(dataDir, "cacerts-merged.p12")
            // the one built in the background last time (see below) takes over, before anything has read the store
            val next = File(dataDir, "cacerts-merged.next.p12")
            if (next.isFile && next.length() > 0) { file.delete(); if (!next.renameTo(file)) next.delete() }
            val fresh = file.isFile && System.currentTimeMillis() - file.lastModified() < 24L * 60 * 60 * 1000
            // Building takes 0.6-0.8 s (the app and the extension sandbox each have one) and ran on the start-up path whenever the copy was a day
            // old: on nearly every start of someone who opens the app once a day. A day-old copy is used as it is and the new one is built
            // in the background, for the next start; only a first start (no copy at all) waits for it.
            if (!file.isFile) build(file)
            else if (!fresh) Thread({ runCatching { build(next) } }, "trust-store-refresh").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
            if (file.isFile) {
                System.setProperty("javax.net.ssl.trustStore", file.absolutePath)
                System.setProperty("javax.net.ssl.trustStoreType", "PKCS12")
                System.setProperty("javax.net.ssl.trustStorePassword", PASSWORD)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Could not install the merged trust store: $t")
        }
    }

    private fun build(out: File) {
        val start = System.currentTimeMillis()
        val merged = KeyStore.getInstance("PKCS12")
        merged.load(null, null)
        var count = 0
        val seen = HashSet<String>()

        fun add(prefix: String, cert: X509Certificate) {
            val key = cert.subjectX500Principal.name + "|" + cert.serialNumber
            if (!seen.add(key)) return
            merged.setCertificateEntry("$prefix-${count++}", cert)
        }

        // JVM bundled roots: its own cacerts file (the default trust manager reads the trustStore property, which may already name the merged copy
        // when this runs in the background, and old entries would then never leave)
        val cacerts = File(System.getProperty("java.home"), "lib/security/cacerts")
        val fromFile = runCatching {
            val ks = KeyStore.getInstance(KeyStore.getDefaultType())
            cacerts.inputStream().use { ks.load(it, null) }
            for (alias in ks.aliases()) (ks.getCertificate(alias) as? X509Certificate)?.let { add("jvm", it) }
        }.isSuccess
        if (!fromFile) {
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(null as KeyStore?)
            tmf.trustManagers.filterIsInstance<X509TrustManager>().forEach { tm ->
                tm.acceptedIssuers.forEach { add("jvm", it) }
            }
        }

        val osStores = when {
            DesktopPlatform.isWindows -> listOf("Windows-ROOT", "Windows-ROOT-LOCALMACHINE", "Windows-ROOT-CURRENTUSER")
            DesktopPlatform.isMac -> listOf("KeychainStore-ROOT", "KeychainStore")
            else -> emptyList()
        }
        for (type in osStores) {
            try {
                val ks = KeyStore.getInstance(type)
                ks.load(null, null)
                for (alias in ks.aliases()) {
                    val cert = ks.getCertificate(alias) as? X509Certificate ?: continue
                    add("os", cert)
                }
            } catch (t: Throwable) {
                Log.d(TAG, "Trust store $type unavailable: $t")
            }
        }

        val tmp = File(out.path + ".tmp")
        tmp.outputStream().use { merged.store(it, PASSWORD.toCharArray()) }
        tmp.renameTo(out) || run {
            out.delete()
            tmp.renameTo(out)
        }
        Log.i(TAG, "Merged trust store with $count certificates in ${System.currentTimeMillis() - start} ms")
    }
}
