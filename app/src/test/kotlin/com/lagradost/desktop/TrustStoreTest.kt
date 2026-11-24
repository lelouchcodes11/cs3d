package com.lagradost.desktop

import java.io.File
import java.security.KeyStore
import kotlin.test.Test
import kotlin.test.assertTrue

class TrustStoreTest {
    private fun count(file: File) = KeyStore.getInstance("PKCS12").apply { file.inputStream().use { load(it, "changeit".toCharArray()) } }.size()

    @Test
    fun aDayOldCopyIsUsedAtOnceAndReplacedAtTheNextStart() {
        val dir = kotlin.io.path.createTempDirectory("truststore").toFile()
        val saved = listOf("javax.net.ssl.trustStore", "javax.net.ssl.trustStoreType", "javax.net.ssl.trustStorePassword").associateWith { System.getProperty(it) }
        try {
            val file = File(dir, "cacerts-merged.p12")
            val next = File(dir, "cacerts-merged.next.p12")
            TrustStore.install(dir) // first start: built now
            assertTrue(file.isFile)
            val roots = count(file)
            assertTrue(roots > 100, "only $roots roots")

            file.setLastModified(System.currentTimeMillis() - 2L * 24 * 60 * 60 * 1000)
            val started = System.currentTimeMillis()
            TrustStore.install(dir) // a day old: used as it is, the new one comes in the background
            assertTrue(System.currentTimeMillis() - started < 200, "waited for the build")
            val until = System.currentTimeMillis() + 20_000
            while (!next.isFile && System.currentTimeMillis() < until) Thread.sleep(50)
            assertTrue(next.isFile, "no background copy")
            // the background copy has the same roots (the property naming the merged copy did not leak into it)
            assertTrue(count(next) == roots, "${count(next)} vs $roots")

            TrustStore.install(dir) // next start: the new copy takes over
            assertTrue(!next.isFile && file.isFile && System.currentTimeMillis() - file.lastModified() < 60_000)
        } finally {
            saved.forEach { (k, v) -> if (v == null) System.clearProperty(k) else System.setProperty(k, v) }
            dir.deleteRecursively()
        }
    }
}
