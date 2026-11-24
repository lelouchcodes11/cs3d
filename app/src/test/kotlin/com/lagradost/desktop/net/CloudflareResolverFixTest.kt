package com.lagradost.desktop.net

import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.runtime.AndroidRuntime
import com.lagradost.desktop.runtime.web.CaptchaQuiet
import com.lagradost.desktop.runtime.web.CloudflareGate
import com.lagradost.desktop.runtime.web.WebCookie
import com.lagradost.desktop.runtime.web.WebCookies
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloudflareResolverFixTest {
    private fun ensureInit() {
        if (!AndroidRuntime.isInitialized) {
            val dir = Files.createTempDirectory("cs3d-cf-test").toFile()
            dir.deleteOnExit()
            DesktopBootstrap.init(dir)
        }
    }

    @Test
    fun dropCloudflareCookiesClearsOnlyTargetHostCfCookies() {
        ensureInit()
        val prevBackend = WebCookies.backend
        try {
            WebCookies.backend = null
            WebCookies.removeAll()

            WebCookies.put(WebCookie("cf_clearance", "stale-token", ".animepahe.pw", "/"))
            WebCookies.put(WebCookie("__cf_bm", "bot-mgmt", ".animepahe.pw", "/"))
            WebCookies.put(WebCookie("cf_chl_rc_ni", "1", "animepahe.pw", "/"))
            WebCookies.put(WebCookie("session_id", "keep-me", "animepahe.pw", "/"))
            WebCookies.put(WebCookie("cf_clearance", "other-site", ".example.com", "/"))

            assertTrue(WebCookies.hasClearance("animepahe.pw"))
            assertTrue(WebCookies.hasClearance("i.animepahe.pw"))
            assertTrue(WebCookies.hasClearance("example.com"))

            val dropped = WebCookies.dropCloudflareCookies("animepahe.pw")
            assertEquals(3, dropped)

            assertFalse(WebCookies.hasClearance("animepahe.pw"))
            assertFalse(WebCookies.hasClearance("i.animepahe.pw"))
            assertTrue(WebCookies.hasClearance("example.com"))
            assertEquals("session_id=keep-me", WebCookies.header("https://animepahe.pw/"))
        } finally {
            WebCookies.removeAll()
            WebCookies.backend = prevBackend
        }
    }

    @Test
    fun aRefusedOldPassDoesNotDeleteTheNewOne() {
        ensureInit()
        val prevBackend = WebCookies.backend
        try {
            WebCookies.backend = null
            WebCookies.removeAll()
            WebCookies.put(WebCookie("cf_clearance", "new-pass", ".animepahe.pw", "/"))
            // a request sent with the previous pass is refused after the browser earned the new one
            val refused = okhttp3.Request.Builder().url("https://animepahe.pw/api").header("Cookie", "a=b; cf_clearance=old-pass").build()
            CloudflarePass.dropRejected(refused)
            assertTrue(WebCookies.hasClearance("animepahe.pw"))
            // refused with the pass the jar holds: that one goes
            CloudflarePass.dropRejected(refused.newBuilder().header("Cookie", "cf_clearance=new-pass").build())
            assertFalse(WebCookies.hasClearance("animepahe.pw"))
        } finally {
            WebCookies.removeAll()
            WebCookies.backend = prevBackend
        }
    }

    @Test
    fun captchaQuietAndRetryCooldownsCanBeClearedOnReload() {
        ensureInit()
        try {
            CaptchaQuiet.clearAll()
            CloudflareGate.clearRetryCooldowns()

            CaptchaQuiet.markShown("AnimePahe.-1169875678.cs3")
            assertTrue(CaptchaQuiet.recentlyShown("AnimePahe.-1169875678.cs3"))
            CaptchaQuiet.clear("AnimePahe.-1169875678.cs3")
            assertFalse(CaptchaQuiet.recentlyShown("AnimePahe.-1169875678.cs3"))

            CaptchaQuiet.markShown("TestExt.123.cs3")
            assertTrue(CaptchaQuiet.recentlyShown("TestExt.123.cs3"))
            CaptchaQuiet.clearAll()
            assertFalse(CaptchaQuiet.recentlyShown("TestExt.123.cs3"))

            CloudflareGate.markFailed("animepahe.pw")
            CloudflareGate.cancelledByUser("animepahe.pw")
            assertFalse(CloudflareGate.allowsSolving("animepahe.pw"))
            CloudflareGate.clearRetryCooldowns()
            assertTrue(CloudflareGate.allowsSolving("animepahe.pw"))
        } finally {
            CaptchaQuiet.clearAll()
            CloudflareGate.clearRetryCooldowns()
        }
    }

    @Test
    fun challengeTitleMatchesCloudflareAndDdosGuardTitles() {
        assertTrue(CheckWindow.isChallengeTitle("Just a moment..."))
        assertTrue(CheckWindow.isChallengeTitle("Performing security verification"))
        assertTrue(CheckWindow.isChallengeTitle("DDoS-Guard"))
        assertTrue(CheckWindow.isChallengeTitle("Attention Required! | Cloudflare"))
        assertFalse(CheckWindow.isChallengeTitle("animepahe :: anime"))
        assertFalse(CheckWindow.isChallengeTitle(""))
        assertFalse(CheckWindow.isChallengeTitle(null))
    }
}
