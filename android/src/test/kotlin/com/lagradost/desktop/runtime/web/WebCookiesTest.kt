package com.lagradost.desktop.runtime.web

import com.lagradost.desktop.runtime.AndroidRuntime
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Only synthetic cookies in a fresh temporary data folder; never reads the user's app data. */
class WebCookiesTest {
    private val url = "https://example.invalid/account"
    private val old = WebCookie("session", "synthetic-old", "example.invalid", httpOnly = true, secure = true)
    private val fresh = old.copy(value = "synthetic-new")

    @BeforeTest fun setup() {
        AndroidRuntime.init(Files.createTempDirectory("web-cookies-test").toFile())
        WebCookies.backend = null
        WebCookies.removeAll()
    }

    @AfterTest fun teardown() { WebCookies.backend = null }

    private fun browser(answer: List<WebCookie>?) = object : WebCookies.BrowserBackend {
        override fun push(cookie: WebCookie) {}
        override fun delete(cookie: WebCookie) {}
        override fun deleteAll() {}
        override fun readCookies(complete: (List<WebCookie>?) -> Unit) { complete(answer) }
    }

    @Test fun ajaxLoginReadUsesLiveHttpOnlyCookies() {
        WebCookies.put(old)
        WebCookies.backend = browser(listOf(fresh))
        assertEquals("session=synthetic-new", WebCookies.freshHeader(url))
        assertTrue(WebCookies.all().single().httpOnly)
    }

    @Test fun failedReadIsNotReportedAsASuccessfulStaleLogin() {
        WebCookies.put(old)
        WebCookies.backend = browser(null)
        assertNull(WebCookies.freshHeader(url))
        assertEquals(listOf(old), WebCookies.all())
    }

    @Test fun flushOnTheUiThreadDoesNotWaitForABusyBrowserButStillSaves() {
        WebCookies.put(old)
        // a browser busy with a page: it never answers the cookie read
        WebCookies.backend = object : WebCookies.BrowserBackend {
            override fun push(cookie: WebCookie) {}
            override fun delete(cookie: WebCookie) {}
            override fun deleteAll() {}
            override fun readCookies(complete: (List<WebCookie>?) -> Unit) {}
        }
        var took = 0L
        java.awt.EventQueue.invokeAndWait {
            val t0 = System.nanoTime()
            WebCookies.flush()
            took = (System.nanoTime() - t0) / 1_000_000
        }
        assertTrue(took < 50, "the UI thread waited $took ms")
        val file = java.io.File(AndroidRuntime.dataDir, "web/cookies.bin")
        val deadline = System.currentTimeMillis() + 5_000
        while (!file.isFile && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertTrue(file.isFile, "the jar was saved by the writer thread")
    }

    @Test fun noBrowserUsesPersistedJarWithoutStartingOne() {
        WebCookies.put(old)
        assertEquals("session=synthetic-old", WebCookies.freshHeader(url))
        assertNull(WebCookies.backend)
    }

    @Test fun olderSnapshotCannotUndoLocalRenewalOrDeletion() {
        val beforeRenewal = WebCookies.beginBrowserRead()
        WebCookies.put(fresh)
        assertFalse(WebCookies.replaceAll(listOf(old), beforeRenewal))
        val beforeDeletion = WebCookies.beginBrowserRead()
        WebCookies.removeAll()
        assertFalse(WebCookies.replaceAll(listOf(old), beforeDeletion))
        assertTrue(WebCookies.all().isEmpty())
    }

    @Test fun outOfOrderAndReplacedBrowserAnswersAreRejected() {
        val first = WebCookies.beginBrowserRead()
        val second = WebCookies.beginBrowserRead()
        assertTrue(WebCookies.replaceAll(listOf(fresh), second))
        assertFalse(WebCookies.replaceAll(listOf(old), first))
        val disconnected = WebCookies.beginBrowserRead()
        WebCookies.backend = browser(emptyList())
        assertFalse(WebCookies.replaceAll(emptyList(), disconnected))
        assertEquals(listOf(fresh), WebCookies.all())
    }

    @Test fun pathDomainSecureAndExpiryScopeRemainIntact() {
        WebCookies.put(old.copy(path = "/account"))
        WebCookies.put(WebCookie("expired", "synthetic", "example.invalid", expires = 1))
        assertEquals("session=synthetic-old", WebCookies.header(url))
        assertEquals("session=synthetic-old", WebCookies.header("$url/settings"))
        assertNull(WebCookies.header("https://example.invalid/accounting"))
        assertNull(WebCookies.header("https://other.invalid/account"))
        assertNull(WebCookies.header("http://example.invalid/account"))
    }

    @Test fun flushWritesEncryptedJarInTheTemporaryFolder() {
        if (!System.getProperty("os.name").startsWith("Windows")) return
        WebCookies.backend = browser(listOf(fresh))
        WebCookies.flush()
        val bytes = java.io.File(AndroidRuntime.dataDir, "web/cookies.bin").readBytes()
        val plain = String(com.sun.jna.platform.win32.Crypt32Util.cryptUnprotectData(bytes), Charsets.UTF_8)
        assertTrue(plain.contains("synthetic-new"))
        assertFalse(String(bytes, Charsets.UTF_8).contains("synthetic-new"))
    }
}
