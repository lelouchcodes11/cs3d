package com.lagradost.desktop.runtime.web

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserCookieRefreshTest {
    private val login = listOf(WebCookie("session", "synthetic-new", "example.invalid", httpOnly = true))

    @Test fun ajaxCookieIsAppliedBeforeReadReturns() {
        var saved = emptyList<WebCookie>()
        assertTrue(BrowserCookieRefresh().refresh(1000, true, { it(login) }) { saved = it; true })
        assertEquals(login, saved)
        assertTrue(saved.single().httpOnly)
    }

    @Test fun completedEmptyJarIsNotConfusedWithFailure() {
        var applied = false
        val reader = BrowserCookieRefresh()
        assertTrue(reader.refresh(1000, true, { it(emptyList()) }) { applied = true; true })
        assertTrue(applied)
        applied = false
        assertFalse(reader.refresh(1000, true, { it(null) }) { applied = true; true })
        assertFalse(applied)
    }

    @Test fun timedOutAndLateCookiesNeverReplaceTheJar() {
        lateinit var complete: (List<WebCookie>?) -> Unit
        var applied = false
        val reader = BrowserCookieRefresh()
        assertFalse(reader.refresh(30, true, { complete = it }) { applied = true; true })
        complete(login)
        assertFalse(applied)
        assertTrue(reader.refresh(1000, true, { it(login) }) { true })
    }

    @Test fun browserCallbackThreadDoesNotWaitForItself() {
        lateinit var complete: (List<WebCookie>?) -> Unit
        var applied = false
        assertFalse(BrowserCookieRefresh().refresh(1000, false, { complete = it }) { applied = true; true })
        assertFalse(applied)
        complete(login)
        assertTrue(applied)
    }

    @Test fun concurrentReadsShareOneBrowserEnumeration() {
        val requests = AtomicInteger()
        val started = CountDownLatch(1)
        lateinit var complete: (List<WebCookie>?) -> Unit
        val reader = BrowserCookieRefresh()
        val first = CompletableFuture.supplyAsync {
            reader.refresh(2000, true, { requests.incrementAndGet(); complete = it; started.countDown() }) { true }
        }
        assertTrue(started.await(1, TimeUnit.SECONDS))
        assertFalse(reader.refresh(2000, false, { requests.incrementAndGet() }) { true })
        assertEquals(1, requests.get())
        complete(login)
        assertTrue(first.get(1, TimeUnit.SECONDS))
    }

    @Test fun rejectedSnapshotAndRequestFailureAreNotSuccess() {
        val reader = BrowserCookieRefresh()
        assertFalse(reader.refresh(1000, true, { it(login) }) { false })
        assertFalse(reader.refresh(1000, true, { error("synthetic failure") }) { true })
        assertTrue(reader.refresh(1000, true, { it(login) }) { true })
    }

    @Test fun duplicateCallbacksApplyOnlyOnce() {
        var applies = 0
        assertTrue(BrowserCookieRefresh().refresh(1000, true, { it(login); it(emptyList()) }) { applies++; true })
        assertEquals(1, applies)
    }
}
