package com.lagradost.desktop.core

import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.ui.SyncWatchType
import com.lagradost.desktop.core.SyncRetry.Op
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SyncRetryTest {
    private fun status(type: SyncWatchType, watched: Int?, max: Int? = null) = SyncAPI.SyncStatus(type, null, watched, maxEpisodes = max)

    @Test fun watchedEpisodesOnlyGoUp() {
        assertEquals(7, SyncRetry.change(Op("a", "1", SyncRetry.MAX_EPISODE, 7), status(SyncWatchType.WATCHING, 5))?.watchedEpisodes)
        // the user (or another device) is already further: nothing is sent back
        assertNull(SyncRetry.change(Op("a", "1", SyncRetry.MAX_EPISODE, 7), status(SyncWatchType.WATCHING, 9)))
    }

    @Test fun watchedEpisodesNeverPassTheTitlesCount() {
        // S2 E1 counted over all seasons (13) on a tracker entry of one 12-episode season
        assertEquals(12, SyncRetry.change(Op("a", "1", SyncRetry.MAX_EPISODE, 13), status(SyncWatchType.WATCHING, 5, max = 12))?.watchedEpisodes)
        assertNull(SyncRetry.change(Op("a", "1", SyncRetry.MAX_EPISODE, 13), status(SyncWatchType.WATCHING, 12, max = 12)))
        // the count from the title's details when the tracker does not give one; 0 means not known
        assertEquals(10, SyncRetry.change(Op("a", "1", SyncRetry.MAX_EPISODE, 14, total = 10), status(SyncWatchType.WATCHING, 5))?.watchedEpisodes)
        assertEquals(14, SyncRetry.change(Op("a", "1", SyncRetry.MAX_EPISODE, 14), status(SyncWatchType.WATCHING, 5, max = 0))?.watchedEpisodes)
    }

    @Test fun watchedEpisodesLeaveUnlistedTitlesAlone() {
        assertNull(SyncRetry.change(Op("a", "1", SyncRetry.MAX_EPISODE, 3), status(SyncWatchType.NONE, 0)))
        val added = SyncRetry.change(Op("a", "1", SyncRetry.MAX_EPISODE, 3, includeUnlisted = true), status(SyncWatchType.NONE, 0))
        assertEquals(SyncWatchType.WATCHING, added?.status)
        assertEquals(3, added?.watchedEpisodes)
    }

    @Test fun startedMovesListedTitlesOnly() {
        assertEquals(SyncWatchType.WATCHING, SyncRetry.change(Op("a", "1", SyncRetry.STARTED), status(SyncWatchType.PLANTOWATCH, 0))?.status)
        assertNull(SyncRetry.change(Op("a", "1", SyncRetry.STARTED), status(SyncWatchType.NONE, 0)))
        assertNull(SyncRetry.change(Op("a", "1", SyncRetry.STARTED), status(SyncWatchType.COMPLETED, 12)))
    }

    @Test fun finishedNeedsTheLastEpisode() {
        assertNull(SyncRetry.change(Op("a", "1", SyncRetry.FINISHED, 11, total = 12), status(SyncWatchType.WATCHING, 10)))
        val done = SyncRetry.change(Op("a", "1", SyncRetry.FINISHED, 12, total = 12), status(SyncWatchType.WATCHING, 11))
        assertEquals(SyncWatchType.COMPLETED, done?.status)
        assertEquals(12, done?.watchedEpisodes)
    }
}
