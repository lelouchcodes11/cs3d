package com.lagradost.desktop.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerTouchGesturesTest {
    @get:Rule val compose = createComposeRule()
    private var singles = 0
    private val seeks = mutableListOf<Long>()

    private fun surface(promoted: Boolean = false, enabled: Boolean = true, button: Boolean = false, suppress: Boolean = false) {
        compose.setContent {
            Box(Modifier.size(400.dp, 300.dp).testTag("video").playerTouchTaps(
                enabled = { enabled }, suppressTap = { suppress },
                onSingleTap = { singles++ }, onSeek = { seeks += it }, promotedTouch = { promoted },
            )) {
                if (button) Box(Modifier.fillMaxSize().clickable { }.testTag("control"))
            }
        }
    }

    private fun finishSingles() { compose.mainClock.advanceTimeBy(600); compose.waitForIdle() }

    @Test fun leftDoubleTouchRewindsWithoutPausing() {
        surface()
        compose.onNodeWithTag("video").performTouchInput {
            click(Offset(width * .2f, height * .5f)); advanceEventTime(100)
            click(Offset(width * .2f, height * .5f))
        }
        finishSingles()
        assertEquals(listOf(-10_000L), seeks)
        assertEquals(0, singles)
    }

    @Test fun rightDoubleTouchSkipsWithoutPausing() {
        surface()
        compose.onNodeWithTag("video").performTouchInput {
            click(Offset(width * .8f, height * .5f)); advanceEventTime(100)
            click(Offset(width * .8f, height * .5f))
        }
        finishSingles()
        assertEquals(listOf(10_000L), seeks)
        assertEquals(0, singles)
    }

    @Test fun promotedWindowsFingerUsesTheSameSeekingPath() {
        surface(promoted = true)
        compose.onNodeWithTag("video").performMouseInput {
            moveTo(Offset(width * .8f, height * .5f)); click(); advanceEventTime(100); click()
        }
        finishSingles()
        assertEquals(listOf(10_000L), seeks)
        assertEquals(0, singles)
    }

    @Test fun realMouseIsLeftToExistingDesktopControls() {
        surface()
        compose.onNodeWithTag("video").performMouseInput { click(); advanceEventTime(100); click() }
        finishSingles()
        assertTrue(seeks.isEmpty())
        assertEquals(0, singles)
    }

    @Test fun singleTouchStillTogglesPlaybackOnce() {
        surface()
        compose.onNodeWithTag("video").performTouchInput { click() }
        finishSingles()
        assertEquals(1, singles)
        assertTrue(seeks.isEmpty())
    }

    @Test fun differentSidesAreNotASeek() {
        surface()
        compose.onNodeWithTag("video").performTouchInput {
            click(Offset(width * .2f, height * .5f)); advanceEventTime(100)
            click(Offset(width * .8f, height * .5f))
        }
        finishSingles()
        assertTrue(seeks.isEmpty())
        assertEquals(2, singles)
    }

    @Test fun swipeAndLongPressAreNotTaps() {
        surface()
        compose.onNodeWithTag("video").performTouchInput {
            swipe(Offset(width * .2f, height * .5f), Offset(width * .8f, height * .5f), 200)
            advanceEventTime(500)
            down(center); advanceEventTime(500); up()
        }
        finishSingles()
        assertTrue(seeks.isEmpty())
        assertEquals(0, singles)
    }

    @Test fun controlsDoNotSeek() {
        surface(button = true)
        compose.onNodeWithTag("control").performTouchInput { click(); advanceEventTime(100); click() }
        finishSingles()
        assertTrue(seeks.isEmpty())
        assertEquals(0, singles)
    }

    @Test fun disabledPipSurfaceDoesNotSeek() {
        surface(enabled = false)
        compose.onNodeWithTag("video").performTouchInput { click(); advanceEventTime(100); click() }
        finishSingles()
        assertTrue(seeks.isEmpty())
        assertEquals(0, singles)
    }

    @Test fun finishingBoostDoesNotPauseOrSeek() {
        surface(suppress = true)
        compose.onNodeWithTag("video").performTouchInput { click(); advanceEventTime(100); click() }
        finishSingles()
        assertTrue(seeks.isEmpty())
        assertEquals(0, singles)
    }

    @Test fun repeatedDoubleTapsKeepSeekingInTenSecondSteps() {
        surface()
        compose.onNodeWithTag("video").performTouchInput {
            repeat(4) { click(Offset(width * .8f, height * .5f)); advanceEventTime(100) }
        }
        finishSingles()
        assertEquals(listOf(10_000L, 10_000L), seeks)
        assertEquals(0, singles)
    }

    @Test fun twoFingerGestureCannotBecomeASeekOrPause() {
        surface()
        compose.onNodeWithTag("video").performTouchInput {
            down(0, Offset(width * .2f, height * .4f))
            down(1, Offset(width * .2f, height * .6f))
            advanceEventTime(100); up(0); up(1)
        }
        finishSingles()
        assertTrue(seeks.isEmpty())
        assertEquals(0, singles)
    }

    @Test fun onlyFingersAndTheirPromotedMouseEventsAreTouch() {
        assertTrue(isPlayerTouch(PointerType.Touch, false))
        assertTrue(isPlayerTouch(PointerType.Mouse, true))
        assertFalse(isPlayerTouch(PointerType.Mouse, false))
        assertFalse(isPlayerTouch(PointerType.Stylus, true))
    }
}
