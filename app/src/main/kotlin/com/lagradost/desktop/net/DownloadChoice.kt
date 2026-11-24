package com.lagradost.desktop.net

import com.lagradost.cloudstream3.utils.ExtractorLink

/**
 * Which source a download takes when it chooses by itself (Settings > General > Downloads > "Download quality"). Before, it took the best
 * quality there was: for a film on CineStream an 11.4 GB BluRay file, when a 2 GB 1080p one was in the same list.
 */
enum class DownloadQuality(val label: String, val maxHeight: Int?, val maxGb: Double?) {
    Best("Best available", null, null),
    Fhd("Up to 1080p", 1080, 6.0),
    Hd("Up to 720p", 720, 2.5),
    Smallest("Smallest file", null, null),
}

object DownloadChoice {
    private val remux = Regex("""\bremux\b""", RegexOption.IGNORE_CASE)

    /** The picture height a link says, null when it says none (Auto, Unknown) */
    private fun height(link: ExtractorLink): Int? = link.quality.takeIf { it in 100..4320 && it != 400 }

    /**
     * The place of [link] for [mode], lower first; equal places keep the order that follows (the download quality profile). With a limit: the
     * sources within it first, the best of them first; then the ones over it, the lightest first (a download still happens when nothing fits).
     */
    fun key(link: ExtractorLink, mode: DownloadQuality): LongArray {
        val h = height(link)
        val gb = SourceWeight.sizeGb(link)
        return when (mode) {
            DownloadQuality.Best -> longArrayOf()
            DownloadQuality.Smallest -> if (gb != null) longArrayOf(0, (gb * 1024).toLong()) else longArrayOf(1, (h ?: 9_999).toLong())
            else -> {
                val maxH = mode.maxHeight ?: Int.MAX_VALUE
                val maxGb = mode.maxGb ?: Double.MAX_VALUE
                val text = link.name + " " + link.source
                val fits = (h == null || h <= maxH) && (gb == null || gb <= maxGb) && !remux.containsMatchIn(text)
                if (fits) longArrayOf(0, -(h ?: 0).toLong()) else longArrayOf(1, (h ?: 9_999).toLong(), ((gb ?: 999.0) * 1024).toLong())
            }
        }
    }

    fun comparator(mode: DownloadQuality): Comparator<ExtractorLink> = Comparator { a, b -> java.util.Arrays.compare(key(a, mode), key(b, mode)) }
}
