package com.lagradost.desktop.net

import android.util.Log
import com.lagradost.cloudstream3.utils.ExtractorLink

/**
 * Fixes in a link that an extension got out of date on, applied once when the link arrives (see APIRepository.loadLinks), so the player, the
 * downloads and the external players all use the repaired one.
 *
 * MovieBox's CDN servers `bcdnw`, `bcdnxw` and `sbcdnw` (hakunaymatata.com) only serve a request that names `https://movie-box.co/` as its referer and
 * answer every other one with "429 Too Many Requests". The MovieBox extensions send exactly that; the MovieBox sources inside CineStream still send
 * the old `fmoviesunblocked.net` (curl: 429 with it, 206 with movie-box.co) and none of their links play. The links of the plain `bcdn` server
 * (StreamPlay's Vidlink) are signed for no referer at all and are left alone.
 */
object LinkRepair {
    private const val MOVIEBOX_REFERER = "https://movie-box.co/"
    private val MOVIEBOX_NODES = Regex("^s?bcdnx?w\\.hakunaymatata\\.com$")

    fun apply(link: ExtractorLink) {
        runCatching {
            val host = java.net.URI(link.url).host?.lowercase() ?: return
            if (MOVIEBOX_NODES.matches(host)) setReferer(link, MOVIEBOX_REFERER, "MovieBox CDN")
        }
    }

    private fun setReferer(link: ExtractorLink, referer: String, why: String) {
        val current = link.headers.entries.firstOrNull { it.key.equals("Referer", true) }?.value ?: link.referer
        if (current.startsWith(referer.trimEnd('/'))) return
        // the Origin of the old site goes with its referer
        link.headers = link.headers.filterKeys { !it.equals("Referer", true) && !it.equals("Origin", true) } + ("Referer" to referer)
        link.referer = referer
        Log.i("LinkRepair", "${link.name}: referer $current -> $referer ($why)")
    }
}
