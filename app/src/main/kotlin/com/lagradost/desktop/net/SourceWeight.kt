package com.lagradost.desktop.net

import com.lagradost.cloudstream3.utils.ExtractorLink

/**
 * Which sources are heavy: the ones that stall on a slow host or drop frames on a weak graphics card. StreamPlay and CineStream list their 4K
 * REMUX and 30 to 70 GB files first (quality order), which then buffer for ever or stutter; every other extension mostly offers 1080p and less.
 * Used by the automatic choice of a source (`PlayerGeneratorViewModel.sortLinks`, setting "Prefer smooth sources").
 */
object SourceWeight {
    private val remux = Regex("""\bremux\b""", RegexOption.IGNORE_CASE)
    private val size = Regex("""(\d+(?:[.,]\d+)?)\s*(GB|MB)\b""", RegexOption.IGNORE_CASE)

    /** Larger than this many GB is heavy whatever the picture size says */
    private const val HEAVY_GB = 25.0

    fun heavy(link: ExtractorLink?): Boolean {
        link ?: return false
        if (link.quality > 1200) return true
        val text = link.name + " " + link.source
        if (remux.containsMatchIn(text)) return true
        val match = size.find(text) ?: return false
        val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return false
        val gb = if (match.groupValues[2].equals("MB", true)) value / 1024.0 else value
        return gb >= HEAVY_GB
    }
}
