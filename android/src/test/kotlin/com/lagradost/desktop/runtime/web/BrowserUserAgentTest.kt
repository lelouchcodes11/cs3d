package com.lagradost.desktop.runtime.web

import com.lagradost.desktop.runtime.web.wv2.WebView2Runtime
import kotlin.test.Test
import kotlin.test.assertEquals

class BrowserUserAgentTest {
    @Test fun observedWebView2DefaultReplacesTheVersionBasedFallback() {
        val original = WebView2Runtime.userAgent
        try {
            WebView2Runtime.observeDefaultUserAgent("Synthetic live Edge UA")
            assertEquals("Synthetic live Edge UA", WebView2Runtime.userAgent)
            WebView2Runtime.observeDefaultUserAgent("")
            assertEquals("Synthetic live Edge UA", WebView2Runtime.userAgent)
        } finally {
            WebView2Runtime.observeDefaultUserAgent(original)
        }
    }

    @Test fun observedJcefDefaultIsRetainedWhenAQueryReturnsNoValue() {
        val original = JcefRuntime.defaultUserAgent
        try {
            JcefRuntime.observeDefaultUserAgent("Synthetic live Chromium UA")
            assertEquals("Synthetic live Chromium UA", JcefRuntime.defaultUserAgent)
            JcefRuntime.observeDefaultUserAgent(null)
            assertEquals("Synthetic live Chromium UA", JcefRuntime.defaultUserAgent)
        } finally {
            JcefRuntime.observeDefaultUserAgent(original)
        }
    }
}
