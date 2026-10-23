package com.lagradost.desktop.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DashLiveStartTest {
    /** 31 segments of 2 s on a 48 kHz clock (audio) and 31 of 2 s on a 600 Hz clock (video), like JIO TV's one-minute window */
    private fun manifest(count: Int = 31, number: Boolean = false, repeat: Boolean = false): String {
        fun timeline(scale: Long, step: Long, start: Long): String =
            if (repeat) "<SegmentTimeline><S t=\"$start\" d=\"$step\" r=\"${count - 1}\"/></SegmentTimeline>"
            else buildString {
                append("<SegmentTimeline>")
                for (i in 0 until count) append(if (i == 0) "<S t=\"$start\" d=\"$step\"/>" else "<S d=\"$step\"/>")
                append("</SegmentTimeline>")
            }
        val media = if (number) "m-\$RepresentationID\$-\$Number\$.dash" else "m-\$RepresentationID\$-\$Time\$.dash"
        return """<MPD type="dynamic"><Period><AdaptationSet contentType="audio"><SegmentTemplate timescale="48000" media="$media">${timeline(48000, 96000, 4_800_000_000L)}</SegmentTemplate><Representation id="a"/></AdaptationSet>
<AdaptationSet contentType="video"><SegmentTemplate timescale="600" media="$media" startNumber="7">${timeline(600, 1200, 60_000_000L)}</SegmentTemplate><Representation id="v"/></AdaptationSet></Period></MPD>"""
    }

    @Test
    fun theBoundIs30SecondsBeforeTheNewestSegment() {
        val xml = manifest()
        val (newest, listed) = assertNotNull(DashLiveStart.measure(xml))
        assertEquals(100_000.0 + 62.0, newest, 0.01) // audio: 4.8e9 / 48000 = 100000 s, 31 x 2 s
        assertEquals(62.0, listed, 0.01)
        assertEquals(newest - 30.0, DashLiveStart.boundFor(xml)!!, 0.01)
    }

    @Test
    fun aShortWindowIsLeftWhole() {
        assertNull(DashLiveStart.boundFor(manifest(count = 15)))
    }

    @Test
    fun theFrontIsCutAndTheTimesKeepTheirPlace() {
        val xml = manifest()
        val cut = DashLiveStart.cut(xml, DashLiveStart.boundFor(xml)!!)
        // 16 of the 31 segments end before the bound (30 s of 62 s are left = 15 segments), so the first one left starts 32 s in
        val audio = Regex("""<S t="(\d+)" d="96000"/>""").find(cut)!!
        assertEquals((4_800_000_000L + 16L * 96000L).toString(), audio.groupValues[1])
        assertEquals(14, Regex("""<S d="96000"/>""").findAll(cut).count())
        val video = Regex("""<S t="(\d+)" d="1200"/>""").find(cut)!!
        assertEquals((60_000_000L + 16L * 1200L).toString(), video.groupValues[1])
        // the video template numbers nothing: nothing else changes
        assertTrue(cut.contains("startNumber=\"7\""))
    }

    @Test
    fun aRepeatedEntryIsShortenedAndNumbersCarryOn() {
        val xml = manifest(repeat = true, number = true)
        val cut = DashLiveStart.cut(xml, DashLiveStart.boundFor(xml)!!)
        // 16 segments dropped: the repeat count falls from 30 to 14, the start time moves on, the video's startNumber 7 becomes 23
        assertTrue(cut.contains("r=\"14\""), cut)
        assertTrue(cut.contains("t=\"${4_800_000_000L + 16L * 96000L}\""), cut)
        assertTrue(cut.contains("startNumber=\"23\""), cut)
        assertTrue(cut.contains("<SegmentTemplate startNumber=\"17\" timescale=\"48000\""), cut)
    }

    @Test
    fun theBoundStaysWhereItWasWhileTheWindowSlides() {
        val first = manifest()
        val bound = DashLiveStart.boundFor(first)!!
        // 5 segments later: the CDN dropped the 5 oldest and added 5 new ones, the cut still starts at the same place
        val later = manifest().replace("t=\"4800000000\"", "t=\"${4_800_000_000L + 5L * 96000L}\"").replace("t=\"60000000\"", "t=\"${60_000_000L + 5L * 1200L}\"")
        val cut = DashLiveStart.cut(later, bound)
        val audio = Regex("""<S t="(\d+)" d="96000"/>""").find(cut)!!
        assertEquals((4_800_000_000L + 16L * 96000L).toString(), audio.groupValues[1])
        assertEquals(19, Regex("""<S d="96000"/>""").findAll(cut).count())
    }
}
