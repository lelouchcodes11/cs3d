package com.lagradost.desktop.torrent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TorrentSearchTest {
    @Test
    fun theSeasonsAReleaseNameStates() {
        assertEquals(2..2, TorrentSearch.seasonsOf("[Judas] Sousou no Frieren (Frieren Beyond Journey`s End) (Season 02) [1080p][HEVC x265 10bit] (Batch)"))
        assertEquals(2..2, TorrentSearch.seasonsOf("[SubsPlease] Sousou no Frieren S2 (01-10) (1080p) [Batch]"))
        assertEquals(2..2, TorrentSearch.seasonsOf("Sousou no Frieren 2nd Season - 03 (1080p)"))
        assertEquals(1..1, TorrentSearch.seasonsOf("Sousou.No.Frieren.S01.MULTi.1080p.BluRay.x265-KAF"))
        assertEquals(1..5, TorrentSearch.seasonsOf("Breaking Bad (2008) Season 1-5 S01-S05 (1080p BluRay x265 HEVC 10bit AAC 5.1 Silence)"))
        assertEquals(3..3, TorrentSearch.seasonsOf("Show.S03E05.1080p.WEB.h264"))
        // nothing said: every season stays in the list
        assertNull(TorrentSearch.seasonsOf("[SubsPlease] Sousou no Frieren - 03 (1080p) [ABCD1234].mkv"))
        // a group or a hash in brackets is not a season
        assertNull(TorrentSearch.seasonsOf("[S2Group] Title - 05 [S3A9]"))
    }
}
