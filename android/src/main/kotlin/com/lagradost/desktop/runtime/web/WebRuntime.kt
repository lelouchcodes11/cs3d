package com.lagradost.desktop.runtime.web

import com.lagradost.desktop.runtime.web.wv2.WebView2Runtime

/** Which browser engine WebViews use: Edge WebView2 (part of Windows 10/11), or bundled Chromium (JCEF) where it is missing */
object WebRuntime {
    val usesWebView2: Boolean get() = WebView2Runtime.isAvailable

    /** The user agent of the engine's own browser (what a Cloudflare clearance cookie made by it is bound to) */
    val defaultUserAgent: String get() = reportedUserAgent ?: if (usesWebView2) WebView2Runtime.userAgent else JcefRuntime.defaultUserAgent

    /** Set in the extension sandbox, which has no browser engine: the user agent of the app's browser, which an extension takes for its own requests (a Cloudflare clearance is bound to it) */
    @Volatile
    var reportedUserAgent: String? = null
}
