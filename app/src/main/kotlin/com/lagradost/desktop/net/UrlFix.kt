package com.lagradost.desktop.net

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Extensions hand out addresses with raw file names (`.../Breaking Bad S01E01 [Hindi + English] x264.mkv`). ExoPlayer sends them
 * through OkHttp, which percent-encodes what a URL does not allow, so they play on Android; mpv/libcurl rejects the raw address
 * ("loading failed"). This makes the same address mpv can open.
 */
object UrlFix {
    fun encode(url: String): String {
        if (!url.startsWith("http", ignoreCase = true)) return url
        val base = url.toHttpUrlOrNull()?.toString() ?: manual(url)
        // OkHttp leaves [ ] alone in paths and queries; libcurl wants them encoded (the host part may hold an IPv6 literal)
        val schemeEnd = base.indexOf("://")
        if (schemeEnd < 0) return base
        val authorityEnd = base.indexOf('/', schemeEnd + 3).let { if (it < 0) base.length else it }
        return base.substring(0, authorityEnd) + base.substring(authorityEnd).replace("[", "%5B").replace("]", "%5D")
    }

    private fun manual(url: String): String {
        val sb = StringBuilder()
        for (ch in url) {
            if (ch.code <= 32 || ch.code > 126 || ch in "{}|\\^`\"<>") {
                for (b in ch.toString().toByteArray(Charsets.UTF_8)) sb.append('%').append("%02X".format(b))
            } else sb.append(ch)
        }
        return sb.toString()
    }
}
