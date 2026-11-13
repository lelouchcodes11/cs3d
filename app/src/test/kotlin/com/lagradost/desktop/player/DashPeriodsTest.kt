package com.lagradost.desktop.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DashPeriodsTest {
    private fun period(id: Int, start: String, duration: String?, startNumber: Int, timeline: String) = """
  <Period start="$start" id="$id"${duration?.let { " duration=\"$it\"" } ?: ""}>
    <AdaptationSet mimeType="video/mp4"><Representation id="v" bandwidth="8200000">
      <SegmentTemplate timescale="25000" media="cenc_video_6_0_${'$'}Number${'$'}.mp4" initialization="v_init.mp4" startNumber="$startNumber" presentationTimeOffset="0">
        <SegmentTimeline>$timeline</SegmentTimeline>
      </SegmentTemplate></Representation></AdaptationSet>
    <AdaptationSet mimeType="audio/mp4"><Representation id="a" bandwidth="128000">
      <SegmentTemplate timescale="48000" media="cenc_audio_7_0_${'$'}Number${'$'}.mp4" initialization="a_init.mp4" startNumber="$startNumber" presentationTimeOffset="0">
        <SegmentTimeline>$timeline</SegmentTimeline>
      </SegmentTemplate></Representation></AdaptationSet>
  </Period>"""

    private fun mpd(vararg periods: String) =
        """<?xml version="1.0"?><MPD type="dynamic" minimumUpdatePeriod="PT5S">${periods.joinToString("")}
</MPD>"""

    // the shape of WILLOW's manifest during an ad break (2026-11-13): 2 + 140 + 10 segments, numbers going on from Period to Period
    private val willow = mpd(
        period(6183, "PT12297.900S", "PT33.000S", 6200, """<S t="100" d="50"/><S t="150" d="50"/>"""),
        period(6201, "PT12330.900S", "PT279.120S", 6202, """<S t="200" d="50" r="139"/>"""),
        period(6341, "PT12610.020S", null, 6342, """<S t="7200" d="50" r="9"/>"""),
    )

    @Test
    fun periodsOfOneTimelineBecomeOne() {
        val merged = DashPeriods.merge(willow)
        assertEquals(1, Regex("<Period\\b").findAll(merged).count())
        // the first Period's start and numbering stay, its length goes (it ended at the first ad marker)
        assertTrue(merged.contains("""<Period start="PT12297.900S" id="6183">"""))
        assertTrue(merged.contains("""startNumber="6200""""))
        // every Period's segments, in order, for both streams
        assertEquals(2, Regex("""<S t="200" d="50" r="139"/>""").findAll(merged).count())
        assertEquals(2, Regex("""<S t="7200" d="50" r="9"/>""").findAll(merged).count())
        assertTrue(merged.indexOf("""t="150"""") < merged.indexOf("""t="200"""") && merged.indexOf("""t="200"""") < merged.indexOf("""t="7200""""))
        assertTrue(merged.trimEnd().endsWith("</MPD>"))
    }

    @Test
    fun aGapInTheNumbersLeavesTheManifestAlone() {
        val gap = mpd(
            period(1, "PT0S", "PT10S", 100, """<S t="0" d="50" r="1"/>"""),
            period(2, "PT10S", null, 105, """<S t="100" d="50"/>"""),
        )
        assertSame(gap, DashPeriods.merge(gap))
    }

    @Test
    fun onePeriodAndRecordingsAreLeftAlone() {
        val one = mpd(period(1, "PT0S", null, 1, """<S t="0" d="50" r="3"/>"""))
        assertSame(one, DashPeriods.merge(one))
        val vod = willow.replace("type=\"dynamic\"", "type=\"static\"")
        assertSame(vod, DashPeriods.merge(vod))
        assertFalse(DashPeriods.merge(one).contains("duration=\"PT33"))
    }
}
