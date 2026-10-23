package com.lagradost.desktop.player

/**
 * Quality of a live DASH channel. ffmpeg's DASH reader plays one fixed representation and cannot change it in an open stream (switching `vid` froze a live
 * channel for more than 20 s), so a channel whose best quality is more than the connection carries buffers for ever. When that happens the player opens the
 * channel again with a lower cap: the manifest it gets then lacks the representations above it (and it opens fewer streams, which also starts faster).
 */
object DashQuality {
    private val ADAPTATION = Regex("""<AdaptationSet\b[^>]*>.*?</AdaptationSet>""", RegexOption.DOT_MATCHES_ALL)
    private val REPRESENTATION = Regex("""<Representation\b[^>]*?\bbandwidth="(\d+)"[^>]*?(?:/>|>.*?</Representation>)""", RegexOption.DOT_MATCHES_ALL)

    private fun isVideo(set: String): Boolean {
        val open = set.substringBefore('>')
        return open.contains("""video""") || (!open.contains("audio") && !open.contains("text") && !open.contains("image") && set.contains("""width="""))
    }

    /** The bandwidths (bits per second) of the video representations, lowest first */
    fun videoBandwidths(xml: String): List<Long> = ADAPTATION.findAll(xml).filter { isVideo(it.value) }
        .flatMap { set -> REPRESENTATION.findAll(set.value).mapNotNull { it.groupValues[1].toLongOrNull() } }.distinct().sorted().toList()

    /** [xml] without the video representations above [maxBandwidth] (the lowest one stays whatever the cap) */
    fun cap(xml: String, maxBandwidth: Long): String {
        val lowest = videoBandwidths(xml).firstOrNull() ?: return xml
        return ADAPTATION.replace(xml) { set ->
            if (!isVideo(set.value)) return@replace set.value
            REPRESENTATION.replace(set.value) { rep ->
                val bw = rep.groupValues[1].toLongOrNull() ?: return@replace rep.value
                if (bw > maxBandwidth && bw != lowest) "" else rep.value
            }
        }
    }

    /** The next lower bandwidth than [current] in [ladder] (lowest first), or null when [current] is the lowest */
    fun below(ladder: List<Long>, current: Long): Long? = ladder.lastOrNull { it < current }
}
