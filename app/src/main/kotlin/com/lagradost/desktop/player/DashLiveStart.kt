package com.lagradost.desktop.player

/**
 * Where a live DASH channel starts for ffmpeg. Its DASH reader begins at the OLDEST segment a live manifest lists: JIO TV lists a minute
 * (`timeShiftBufferDepth` PT1M), so playback starts 56 to 60 s behind the live edge with about 4 s to spare before the CDN drops that segment.
 * The first hiccup (a slow request, the picture decoder catching up) puts the reader's next segment outside the window and it skips ahead
 * by two to six segments: the picture lags and the sound jumps ("Invalid audio PTS: 13.9 -> 27.9", the audio and video drift apart).
 *
 * The manifest the player gets is cut at the front: every segment that ended before [boundSeconds] (fixed when the first manifest is read:
 * [START_BEHIND_S] before the newest segment) is left out. The bound does not move, so later manifests are cut at the same place until the
 * CDN's own window has passed it, and the reader stays [START_BEHIND_S] seconds inside the window instead of at its edge. A manifest that
 * lists less than [MIN_LISTED_S] is left whole ("the last 30 s if the channel keeps more than 30, else whatever there is").
 *
 * Only SegmentTemplate + SegmentTimeline manifests are cut (what JIO TV and most packagers send); everything else passes unchanged.
 */
object DashLiveStart {
    const val START_BEHIND_S = 30.0
    private const val MIN_LISTED_S = 36.0

    private val TEMPLATE = Regex("""(<SegmentTemplate\b[^>]*>)(.*?)(</SegmentTemplate>)""", RegexOption.DOT_MATCHES_ALL)
    private val TIMELINE = Regex("""<SegmentTimeline>(.*?)</SegmentTimeline>""", RegexOption.DOT_MATCHES_ALL)
    private val S = Regex("""<S\b([^>]*?)/>""")

    private class Seg(var t: Long, val d: Long, var r: Long, val explicit: Boolean = false)

    private fun attr(text: String, name: String): Long? = Regex("""\b$name="(-?\d+)"""").find(text)?.groupValues?.get(1)?.toLongOrNull()

    private fun segments(timeline: String): List<Seg>? {
        val out = ArrayList<Seg>()
        var time = 0L
        for (m in S.findAll(timeline)) {
            val a = m.groupValues[1]
            val given = attr(a, "t")
            given?.let { time = it }
            val d = attr(a, "d") ?: return null
            val r = attr(a, "r") ?: 0L
            if (r < 0) return null // open ended: not cut
            out.add(Seg(time, d, r, given != null))
            time += d * (r + 1)
        }
        return out
    }

    /** The end (s) of the newest segment each timeline lists, the longest listed stretch (s) */
    fun measure(xml: String): Pair<Double, Double>? {
        var newest = Double.NEGATIVE_INFINITY
        var longest = 0.0
        var any = false
        for (tm in TEMPLATE.findAll(xml)) {
            val scale = attr(tm.groupValues[1], "timescale")?.takeIf { it > 0 } ?: 1L
            val tl = TIMELINE.find(tm.groupValues[2]) ?: continue
            val segs = segments(tl.groupValues[1])?.takeIf { it.isNotEmpty() } ?: continue
            val last = segs.last()
            val end = (last.t + last.d * (last.r + 1)).toDouble() / scale
            val first = segs.first().t.toDouble() / scale
            newest = maxOf(newest, end)
            longest = maxOf(longest, end - first)
            any = true
        }
        return if (any) newest to longest else null
    }

    /** The bound (s on the manifest's own clock) for a first manifest, or null when it lists too little to cut */
    fun boundFor(xml: String): Double? = measure(xml)?.let { (newest, listed) -> if (listed >= MIN_LISTED_S) newest - START_BEHIND_S else null }

    /** [xml] without the segments that ended before [boundSeconds] */
    fun cut(xml: String, boundSeconds: Double): String = TEMPLATE.replace(xml) { tm ->
        val open = tm.groupValues[1]
        val body = tm.groupValues[2]
        val tl = TIMELINE.find(body) ?: return@replace tm.value
        val scale = attr(open, "timescale")?.takeIf { it > 0 } ?: 1L
        val segs = segments(tl.groupValues[1])?.takeIf { it.isNotEmpty() } ?: return@replace tm.value
        val bound = (boundSeconds * scale).toLong()
        var dropped = 0L
        val kept = ArrayList<Seg>()
        for (s in segs) {
            if (kept.isNotEmpty()) { kept.add(s); continue }
            val end = s.t + s.d * (s.r + 1)
            if (end <= bound) { dropped += s.r + 1; continue }
            // part of a repeated entry ended before the bound
            val skip = if (s.t >= bound) 0L else minOf(s.r, (bound - s.t) / s.d)
            dropped += skip
            kept.add(Seg(s.t + skip * s.d, s.d, s.r - skip, true))
        }
        if (dropped == 0L || kept.isEmpty()) return@replace tm.value
        val rebuilt = buildString {
            append("<SegmentTimeline>")
            for ((i, s) in kept.withIndex()) {
                append("<S")
                if (i == 0 || s.explicit) append(" t=\"").append(s.t).append('"')
                append(" d=\"").append(s.d).append('"')
                if (s.r > 0) append(" r=\"").append(s.r).append('"')
                append("/>")
            }
            append("</SegmentTimeline>")
        }
        // a template that numbers its segments carries on from the first one that is left
        val newOpen = if (body.contains("\$Number") || open.contains("\$Number")) {
            val start = attr(open, "startNumber") ?: 1L
            if (Regex("""\bstartNumber="""").containsMatchIn(open)) open.replace(Regex("""\bstartNumber="-?\d+""""), "startNumber=\"${start + dropped}\"")
            else open.replaceFirst("<SegmentTemplate", "<SegmentTemplate startNumber=\"${start + dropped}\"")
        } else open
        newOpen + body.replaceRange(tl.range, rebuilt) + tm.groupValues[3]
    }
}
