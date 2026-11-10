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

    /** A video representation of a manifest: its bandwidth and its height (when the manifest says) */
    data class Rep(val bandwidth: Long, val height: Int?)

    private val HEIGHT = Regex("""\bheight="(\d+)"""")

    /** The video representations, lowest bandwidth first (a height on the AdaptationSet stands for its representations that name none) */
    fun videoReps(xml: String): List<Rep> = ADAPTATION.findAll(xml).filter { isVideo(it.value) }.flatMap { set ->
        val setHeight = HEIGHT.find(set.value.substringBefore('>'))?.groupValues?.get(1)?.toIntOrNull()
        REPRESENTATION.findAll(set.value).mapNotNull { rep ->
            val bw = rep.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            Rep(bw, HEIGHT.find(rep.value.substringBefore('>'))?.groupValues?.get(1)?.toIntOrNull() ?: setHeight)
        }
    }.distinctBy { it.bandwidth }.sortedBy { it.bandwidth }.toList()

    /** The one to play: [exact] when it is there (and within [cap]), else the best up to [screenHeight] lines within [cap] (0 = no cap) */
    fun pick(reps: List<Rep>, screenHeight: Int, cap: Long, exact: Long): Rep? {
        val allowed = reps.filter { cap <= 0L || it.bandwidth <= cap }.ifEmpty { reps.take(1) }
        allowed.firstOrNull { it.bandwidth == exact }?.let { return it }
        return allowed.filter { (it.height ?: 0) in 1..screenHeight }.maxWithOrNull(compareBy({ it.height ?: 0 }, { it.bandwidth }))
            ?: allowed.filter { it.height == null }.maxByOrNull { it.bandwidth } ?: allowed.minByOrNull { it.height ?: 0 }
    }

    /** [xml] with only the video representation of [bandwidth] (audio and the rest stay) */
    fun only(xml: String, bandwidth: Long): String = ADAPTATION.replace(xml) { set ->
        if (!isVideo(set.value)) return@replace set.value
        val kept = REPRESENTATION.replace(set.value) { rep -> if (rep.groupValues[1].toLongOrNull() == bandwidth) rep.value else "" }
        // a video set left without a representation (another codec's set) goes: an empty one is not a valid manifest
        if (REPRESENTATION.containsMatchIn(kept)) kept else ""
    }
}
