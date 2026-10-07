package com.lagradost.desktop.runtime.web

import com.lagradost.desktop.runtime.web.wv2.WebView2Runtime

/** Which browser engine WebViews use: Edge WebView2 (part of Windows 10/11), or bundled Chromium (JCEF) where it is missing */
object WebRuntime {
    val usesWebView2: Boolean get() = WebView2Runtime.isAvailable

    /** The user agent of the engine's own browser (what a Cloudflare clearance cookie made by it is bound to) */
    val defaultUserAgent: String get() = if (usesWebView2) WebView2Runtime.userAgent else JcefRuntime.defaultUserAgent
}
