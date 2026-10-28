package com.lagradost.desktop.runtime.web

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JcefCookieThreadTest {
    @Test fun nativeInterceptionNeverWaitsForItsOwnCookieThread() {
        assertTrue(JcefRuntime.canWaitForCookieRead)
        JcefRuntime.inNativeCookieCallback {
            assertFalse(JcefRuntime.canWaitForCookieRead)
            JcefRuntime.inNativeCookieCallback { assertFalse(JcefRuntime.canWaitForCookieRead) }
            assertFalse(JcefRuntime.canWaitForCookieRead)
        }
        assertTrue(JcefRuntime.canWaitForCookieRead)
    }

    @Test fun throwingExtensionDoesNotLeakTheNativeCallbackMarker() {
        assertFailsWith<IllegalStateException> {
            JcefRuntime.inNativeCookieCallback { error("synthetic extension failure") }
        }
        assertTrue(JcefRuntime.canWaitForCookieRead)
    }
}
