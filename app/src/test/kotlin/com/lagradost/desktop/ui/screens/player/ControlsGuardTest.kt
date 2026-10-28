package com.lagradost.desktop.ui.screens.player

import com.lagradost.desktop.ui.screens.player.ControlsGuard.Hit
import com.lagradost.desktop.ui.screens.player.ControlsGuard.OverlayHealth
import com.lagradost.desktop.ui.screens.player.ControlsGuard.PointerWatch
import com.lagradost.desktop.ui.screens.player.ControlsGuard.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControlsGuardTest {
    @Test
    fun pointerWatchSeesOnlyRealMovement() {
        val w = PointerWatch(3)
        assertFalse(w.moved(100, 100), "the first sample has nothing to compare with")
        assertFalse(w.moved(101, 100), "one pixel of jitter is not a movement")
        assertTrue(w.moved(104, 100))
        // the position the last movement ended at is the reference, jitter around it does not add up
        assertFalse(w.moved(105, 101))
        assertFalse(w.moved(104, 102))
        assertTrue(w.moved(104, 106))
    }

    private class Clock { var t = 0L; fun now() = t }

    private fun health(c: Clock) = OverlayHealth(c::now)

    @Test
    fun aWindowThatIsThereAndUnderThePointerIsFine() {
        val c = Clock()
        val h = health(c)
        repeat(100) { c.t += 100; assertEquals(Verdict.Fine, h.sample(true, true, Hit.Overlay)) }
    }

    @Test
    fun aWindowThatNeverAppearsIsRepairedAndThenGivenUp() {
        val c = Clock()
        val h = health(c)
        var heals = 0
        var gaveUp = false
        for (i in 1..400) {
            c.t += 100
            when (h.sample(false, false, Hit.Other)) {
                Verdict.Heal -> { heals++; assertTrue(c.t in 6_000..6_200, "healed at ${c.t}") }
                Verdict.GiveUp -> { gaveUp = true; break }
                Verdict.Fine -> {}
            }
        }
        assertEquals(1, heals)
        assertTrue(gaveUp)
        // 6 s to the repair, then 12 s more of the same
        assertTrue(c.t in 18_000..18_300, "gave up at ${c.t}")
    }

    @Test
    fun aWindowThatComesBackAfterARepairIsFine() {
        val c = Clock()
        val h = health(c)
        var healed = false
        while (!healed) { c.t += 100; healed = h.sample(false, false, Hit.Other) == Verdict.Heal }
        repeat(200) { c.t += 100; assertEquals(Verdict.Fine, h.sample(true, true, Hit.Overlay)) }
    }

    @Test
    fun aWindowThatLetsThePointerThroughIsRepairedThenGivenUp() {
        val c = Clock()
        val h = health(c)
        val verdicts = ArrayList<Verdict>()
        for (i in 1..200) {
            c.t += 100
            val v = h.sample(true, true, Hit.Main)
            if (v != Verdict.Fine) verdicts += v
            if (v == Verdict.GiveUp) break
        }
        assertEquals(listOf(Verdict.Heal, Verdict.GiveUp), verdicts)
    }

    @Test
    fun aFewBlindSamplesBetweenGoodOnesAreNotEnough() {
        val c = Clock()
        val h = health(c)
        repeat(50) {
            repeat(4) { c.t += 100; assertEquals(Verdict.Fine, h.sample(true, true, Hit.Main)) }
            c.t += 100
            assertEquals(Verdict.Fine, h.sample(true, true, Hit.Overlay))
        }
    }

    @Test
    fun anotherProgramsWindowAndAStillPointerSayNothing() {
        val c = Clock()
        val h = health(c)
        repeat(100) { c.t += 100; assertEquals(Verdict.Fine, h.sample(true, true, Hit.Other)) }
        repeat(100) { c.t += 100; assertEquals(Verdict.Fine, h.sample(true, false, Hit.Main)) }
    }

    @Test
    fun aMinimizedWindowStartsTheCountAgain() {
        val c = Clock()
        val h = health(c)
        repeat(50) { c.t += 100; h.sample(false, false, Hit.Other) } // 5 s without the window
        h.clear()
        repeat(50) { c.t += 100; assertEquals(Verdict.Fine, h.sample(false, false, Hit.Other)) } // 5 s more: not 10
    }
}
