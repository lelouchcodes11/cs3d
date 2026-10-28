package com.lagradost.desktop.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GraphicsTest {
    @Test
    fun aStateFileIsReadWhateverIsInIt() {
        val s = Graphics.parseState("level=1\npending=2\n")
        assertEquals(1, s.level)
        assertEquals(2, s.pending)
        val none = Graphics.parseState(null)
        assertEquals(0, none.level)
        assertEquals(0, none.pending)
        val odd = Graphics.parseState("level=99\npending=-4\nnonsense")
        assertEquals(2, odd.level, "the level can not go past the last renderer")
        assertEquals(0, odd.pending)
    }

    @Test
    fun theThirdUnhealthyStartInARowStepsOneRendererDown() {
        var s = Graphics.State(0, 0)
        val levels = ArrayList<Int>()
        // six starts that never became healthy: Direct3D twice, OpenGL twice, Software for the rest
        repeat(7) { s = Graphics.next(s); levels += s.level }
        assertEquals(listOf(0, 0, 1, 1, 2, 2, 2), levels)
    }

    @Test
    fun aHealthyStartKeepsTheLevelAndClearsTheCount() {
        // the health timer writes State(level, 0) after 20 s: the next start counts from one again and stays on the level it reached
        val s = Graphics.next(Graphics.State(1, 0))
        assertEquals(1, s.level)
        assertEquals(1, s.pending)
    }

    @Test
    fun theSafeGraphicsArgumentsPickARenderer() {
        assertEquals(RenderChoice.Software, Graphics.choiceFromArgs(arrayOf("--safe-graphics")))
        assertEquals(RenderChoice.Software, Graphics.choiceFromArgs(arrayOf("cloudstreamapp://x", "--Software")))
        assertEquals(RenderChoice.OpenGL, Graphics.choiceFromArgs(arrayOf("--opengl")))
        assertEquals(RenderChoice.Direct3D, Graphics.choiceFromArgs(arrayOf("--direct3d")))
        assertEquals(RenderChoice.Auto, Graphics.choiceFromArgs(arrayOf("--auto-graphics")))
        assertEquals(null, Graphics.choiceFromArgs(arrayOf("cloudstreamapp://open", "--other")))
        assertEquals(null, Graphics.choiceFromArgs(emptyArray()))
    }

    @Test
    fun aWeakPcIsOneWithLittleMemoryOrFewThreads() {
        assertTrue(LowEnd.isLowEnd(LowEnd.Facts(3.9, 4)))
        assertTrue(LowEnd.isLowEnd(LowEnd.Facts(16.0, 2)))
        assertTrue(LowEnd.isLowEnd(LowEnd.Facts(5.8, 8)))
        assertFalse(LowEnd.isLowEnd(LowEnd.Facts(7.8, 4)))
        assertFalse(LowEnd.isLowEnd(LowEnd.Facts(16.0, 8)))
    }

    @Test
    fun theModeDecidesWhenTheLightLookIsOn() {
        assertTrue(LowEnd.active(PerformanceMode.Light))
        assertFalse(LowEnd.active(PerformanceMode.Full))
    }
}
