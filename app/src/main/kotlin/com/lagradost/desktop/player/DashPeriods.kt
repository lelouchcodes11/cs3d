package com.lagradost.desktop.player

/**
 * A live manifest split into several Periods, served to the player as ONE Period.
 *
 * Channels with ad markers (SCTE-35: Amazon's live channels such as WILLOW, FANCODE, PREMIER SPORTS) start a new Period at every marker, so during an
 * ad break the manifest lists two or three. ffmpeg's DASH reader reads only the first one: it played that piece (often 30-60 s), ran out of
 * segments while the channel went on in the next Period, stalled, and the app opened the channel again on the same piece, over and over (owner,
 * 2026-11-13: "willow links are playing the same part again and again").
 *
 * The markers only split the timeline: every Period has the same segment templates, and the numbers and times go on from one to the next. Then the
 * timelines of the later Periods are appended to the first one's and the first one's length is dropped. Anything else is left as the site sent it.
 */
object DashPeriods {
    private val DYNAMIC = Regex("""<MPD\b[^>]*\btype\s*=\s*["']dynamic["']""")
    private val PERIOD = Regex("""<Period\b[^>]*>.*?</Period>""", RegexOption.DOT_MATCHES_ALL)
    private val PERIOD_TAG = Regex("""<Period\b[^>]*>""")
    private val PERIOD_DURATION = Regex("""\s+duration\s*=\s*["'][^"']*["']""")
    private val TEMPLATE = Regex("""<SegmentTemplate\b([^>]*)>(.*?)</SegmentTemplate>""", RegexOption.DOT_MATCHES_ALL)
    private val TIMELINE = Regex("""(<SegmentTimeline\b[^>]*>)(.*?)(</SegmentTimeline>)""", RegexOption.DOT_MATCHES_ALL)
    private val S = Regex("""<S\b[^>]*?/>""")
    private val MEDIA = Regex("""\bmedia\s*=\s*["']([^"']*)["']""")
    private val START_NUMBER = Regex("""\bstartNumber\s*=\s*["'](\d+)["']""")
    private val REPEAT = Regex("""\br\s*=\s*["'](-?\d+)["']""")

    private class Template(val media: String, val startNumber: Long, val segments: List<String>, val count: Long)

    private fun templates(period: String): List<Template>? {
        val out = ArrayList<Template>()
        for (m in TEMPLATE.findAll(period)) {
            val attrs = m.groupValues[1]
            val media = MEDIA.find(attrs)?.groupValues?.get(1) ?: return null
            val timeline = TIMELINE.find(m.groupValues[2]) ?: return null
            val segments = S.findAll(timeline.groupValues[2]).map { it.value }.toList()
            if (segments.isEmpty()) return null
            var count = 0L
            for (s in segments) {
                val r = REPEAT.find(s)?.groupValues?.get(1)?.toLong() ?: 0L
                if (r < 0) return null // "until the end of the Period": no fixed count to go on from
                count += 1 + r
            }
            out += Template(media, START_NUMBER.find(attrs)?.groupValues?.get(1)?.toLong() ?: 1L, segments, count)
        }
        return out.takeIf { it.isNotEmpty() }
    }

    /** [xml] with its Periods made one when they only split one timeline (see the class notes); otherwise [xml] itself */
    fun merge(xml: String): String {
        if (!DYNAMIC.containsMatchIn(xml.take(4096))) return xml
        val periods = PERIOD.findAll(xml).toList()
        if (periods.size < 2) return xml
        val all = periods.map { templates(it.value) ?: return xml }
        val first = all.first()
        for (i in 1 until all.size) {
            val prev = all[i - 1]
            val next = all[i]
            if (next.map { it.media } != first.map { it.media }) return xml
            // the numbers go on from one Period to the next, for every stream
            for (k in next.indices) if (next[k].startNumber != prev[k].startNumber + prev[k].count) return xml
        }
        // the first Period, each timeline holding the segments of every Period, with no length of its own (it ended at the first marker)
        var k = 0
        val body = TEMPLATE.replace(periods.first().value) { tm ->
            val index = k++
            val segments = all.joinToString("") { p -> p[index].segments.joinToString("") }
            tm.value.replace(TIMELINE) { tl -> tl.groupValues[1] + segments + tl.groupValues[3] }
        }
        val tag = PERIOD_TAG.find(body) ?: return xml
        val merged = body.replaceRange(tag.range, tag.value.replace(PERIOD_DURATION, ""))
        return xml.substring(0, periods.first().range.first) + merged + xml.substring(periods.last().range.last + 1)
    }
}
