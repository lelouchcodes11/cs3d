package com.lagradost.desktop.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DashQualityTest {
    private val mpd = """<MPD type="dynamic"><Period>
<AdaptationSet mimeType="video/mp4"><SegmentTemplate media="v-${'$'}RepresentationID${'$'}-${'$'}Number${'$'}.m4s"/>
<Representation id="1" width="640" height="360" bandwidth="800000" codecs="avc1"/>
<Representation id="2" width="1280" height="720" bandwidth="2000000" codecs="avc1"><ContentProtection value="cenc"/></Representation>
<Representation id="3" width="1920" height="1080" bandwidth="6000000" codecs="avc1"/>
</AdaptationSet>
<AdaptationSet mimeType="audio/mp4"><Representation id="9" bandwidth="128000"/></AdaptationSet>
</Period></MPD>"""

    @Test
    fun theLadderIsTheVideoBandwidthsLowestFirst() {
        assertEquals(listOf(800_000L, 2_000_000L, 6_000_000L), DashQuality.videoBandwidths(mpd))
    }

    @Test
    fun aCapRemovesTheRepresentationsAboveItButNeverTheAudio() {
        val capped = DashQuality.cap(mpd, 2_000_000L)
        assertFalse(capped.contains("""id="3""""))
        assertTrue(capped.contains("""id="2""""))
        assertTrue(capped.contains("""id="1""""))
        assertTrue(capped.contains("""id="9""""))
        assertEquals(listOf(800_000L, 2_000_000L), DashQuality.videoBandwidths(capped))
    }

    @Test
    fun theLowestRepresentationStaysWhateverTheCap() {
        val capped = DashQuality.cap(mpd, 100_000L)
        assertEquals(listOf(800_000L), DashQuality.videoBandwidths(capped))
        assertTrue(capped.contains("""id="9""""))
    }

    @Test
    fun nextLowerStepsDownTheLadder() {
        val ladder = DashQuality.videoBandwidths(mpd)
        assertEquals(2_000_000L, DashQuality.below(ladder, 6_000_000L))
        assertEquals(800_000L, DashQuality.below(ladder, 2_000_000L))
        assertNull(DashQuality.below(ladder, 800_000L))
    }
}
