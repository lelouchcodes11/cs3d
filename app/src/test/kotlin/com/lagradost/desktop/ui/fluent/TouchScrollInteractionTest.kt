package com.lagradost.desktop.ui.fluent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.After
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Uses the same mouse-style stream Windows delivers for a finger, not an Android-only touch stream. */
class TouchScrollInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val oldMode = Appearance.touchSwipes
    @After fun restoreSettings() { Appearance.touchSwipes = oldMode }

    private fun list(state: LazyListState, clicks: androidx.compose.runtime.MutableIntState) {
        compose.setContent {
            TouchLazyColumn(Modifier.size(300.dp).testTag("page"), state = state) {
                items(30) { Box(Modifier.size(280.dp, 100.dp).clickable { clicks.intValue++ }.testTag("card-$it")) }
            }
        }
    }

    private fun swipe() = compose.onNodeWithTag("page").performMouseInput {
        moveTo(Offset(100f, 220f))
        press()
        repeat(10) { advanceEventTime(20); moveBy(Offset(0f, -15f)) }
        release()
    }

    @Test fun promotedFingerSwipeScrollsWithoutActivatingCard() {
        Appearance.touchSwipes = TouchSwipes.On
        val state = LazyListState()
        val clicks = mutableIntStateOf(0)
        list(state, clicks)
        swipe()
        compose.runOnIdle {
            assertTrue(state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0)
            assertEquals(0, clicks.intValue)
        }
    }

    @Test fun tapStillActivatesOnce() {
        Appearance.touchSwipes = TouchSwipes.On
        val clicks = mutableIntStateOf(0)
        list(LazyListState(), clicks)
        compose.onNodeWithTag("card-0").performMouseInput { click() }
        compose.runOnIdle { assertEquals(1, clicks.intValue) }
    }

    @Test fun offDoesNotTurnMouseDragsIntoScrolls() {
        Appearance.touchSwipes = TouchSwipes.Off
        val state = LazyListState()
        list(state, mutableIntStateOf(0))
        swipe()
        compose.runOnIdle {
            assertEquals(0, state.firstVisibleItemIndex)
            assertEquals(0, state.firstVisibleItemScrollOffset)
        }
    }

    @Test fun realTouchContinuesToUseComposeScrolling() {
        Appearance.touchSwipes = TouchSwipes.Auto
        val state = LazyListState()
        val clicks = mutableIntStateOf(0)
        list(state, clicks)
        compose.onNodeWithTag("page").performTouchInput { swipeUp() }
        compose.runOnIdle {
            assertTrue(state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0)
            assertEquals(0, clicks.intValue)
        }
    }

    private fun nested(page: LazyListState, row: LazyListState, clicks: androidx.compose.runtime.MutableIntState) {
        compose.setContent {
            TouchLazyColumn(Modifier.size(300.dp).testTag("page"), state = page) {
                item {
                    TouchLazyRow(Modifier.height(150.dp).testTag("shelf"), state = row) {
                        items(20) { Box(Modifier.size(100.dp, 150.dp).clickable { clicks.intValue++ }) }
                    }
                }
                items(30) { Box(Modifier.size(280.dp, 100.dp)) }
            }
        }
    }

    @Test fun horizontalShelfSwipeDoesNotScrollPageOrClick() {
        Appearance.touchSwipes = TouchSwipes.On
        val page = LazyListState()
        val row = LazyListState()
        val clicks = mutableIntStateOf(0)
        nested(page, row, clicks)
        compose.onNodeWithTag("shelf").performMouseInput {
            moveTo(Offset(230f, 70f)); press()
            repeat(10) { advanceEventTime(20); moveBy(Offset(-15f, 0f)) }
            release()
        }
        compose.runOnIdle {
            assertTrue(row.firstVisibleItemIndex > 0 || row.firstVisibleItemScrollOffset > 0)
            assertEquals(0, page.firstVisibleItemScrollOffset)
            assertEquals(0, page.firstVisibleItemIndex)
            assertEquals(0, clicks.intValue)
        }
    }

    @Test fun verticalSwipeOverShelfScrollsPageNotShelf() {
        Appearance.touchSwipes = TouchSwipes.On
        val page = LazyListState()
        val row = LazyListState()
        val clicks = mutableIntStateOf(0)
        nested(page, row, clicks)
        compose.onNodeWithTag("shelf").performMouseInput {
            moveTo(Offset(100f, 120f)); press()
            repeat(6) { advanceEventTime(20); moveBy(Offset(0f, -15f)) }
            release()
        }
        compose.runOnIdle {
            assertTrue(page.firstVisibleItemIndex > 0 || page.firstVisibleItemScrollOffset > 0)
            assertEquals(0, row.firstVisibleItemScrollOffset)
            assertEquals(0, row.firstVisibleItemIndex)
            assertEquals(0, clicks.intValue)
        }
    }
}
