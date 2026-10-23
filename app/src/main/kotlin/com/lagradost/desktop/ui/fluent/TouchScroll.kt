package com.lagradost.desktop.ui.fluent

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.lagradost.desktop.platform.WindowsTouch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class TouchSwipes(val label: String) { Auto("Auto"), On("On"), Off("Off") }

internal fun useMouseSwipe(mode: TouchSwipes, promotedTouch: Boolean): Boolean =
    mode == TouchSwipes.On || (mode == TouchSwipes.Auto && promotedTouch)

/** Null below slop; otherwise lock to the dominant axis so a shelf does not steal vertical page swipes. */
internal fun swipeAxis(x: Float, y: Float, slop: Float): Orientation? = when {
    maxOf(abs(x), abs(y)) <= slop -> null
    abs(x) > abs(y) -> Orientation.Horizontal
    else -> Orientation.Vertical
}

/** Compose already scrolls real touch pointers. This adds only Windows' mouse-promoted finger drags. */
@Composable
fun Modifier.touchScroll(state: ScrollableState, orientation: Orientation = Orientation.Vertical): Modifier {
    val mode = Appearance.touchSwipes
    if (mode == TouchSwipes.Off) return this
    val fling = ScrollableDefaults.flingBehavior()
    return pointerInput(state, orientation, mode, fling) {
        coroutineScope {
            val jobs = this
            var scrolling: kotlinx.coroutines.Job? = null
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (down.type != PointerType.Mouse || !currentEvent.buttons.isPrimaryPressed ||
                    !useMouseSwipe(mode, WindowsTouch.lastPrimaryWasTouch)) return@awaitEachGesture
                scrolling?.cancel()
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                var total = Offset.Zero
                var claimed = false
                var released = false
                var velocity = 0f
                val deltas = Channel<Float>(Channel.UNLIMITED)
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.size != 1) break
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (change.isConsumed) break
                        if (!change.pressed) {
                            released = true
                            if (claimed) {
                                change.consume() // A swipe must never activate the card underneath on release.
                                val v = tracker.calculateVelocity()
                                velocity = if (orientation == Orientation.Vertical) v.y else v.x
                            }
                            break
                        }
                        val delta = change.position - change.previousPosition
                        total += delta
                        var amount = if (orientation == Orientation.Vertical) delta.y else delta.x
                        if (!claimed) {
                            val axis = swipeAxis(total.x, total.y, viewConfiguration.touchSlop) ?: continue
                            if (axis != orientation) break
                            claimed = true
                            val distance = if (orientation == Orientation.Vertical) total.y else total.x
                            amount = distance - kotlin.math.sign(distance) * viewConfiguration.touchSlop
                            scrolling = jobs.launch {
                                state.scroll(MutatePriority.UserInput) {
                                    for (pixels in deltas) scrollBy(-pixels)
                                    with(fling) { performFling(-velocity) }
                                }
                            }
                        }
                        change.consume()
                        deltas.trySend(amount)
                    }
                } finally {
                    deltas.close()
                    if (!released) scrolling?.cancel()
                }
            }
        }
    }
}

/** Shared wrapper also used by dialogs and extension settings. */
@Composable
fun Modifier.tabletVerticalScroll(state: ScrollState): Modifier = touchScroll(state).verticalScroll(state)

/** Give ordinary dialog/episode lists the same swipe behaviour as the main pages. */
@Composable
fun TouchLazyColumn(
    modifier: Modifier = Modifier,
    state: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(),
    verticalArrangement: androidx.compose.foundation.layout.Arrangement.Vertical = androidx.compose.foundation.layout.Arrangement.Top,
    horizontalAlignment: androidx.compose.ui.Alignment.Horizontal = androidx.compose.ui.Alignment.Start,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) = androidx.compose.foundation.lazy.LazyColumn(
    modifier = modifier.touchScroll(state), state = state, contentPadding = contentPadding,
    verticalArrangement = verticalArrangement, horizontalAlignment = horizontalAlignment, content = content,
)

@Composable
fun TouchLazyRow(
    modifier: Modifier = Modifier,
    state: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    horizontalArrangement: androidx.compose.foundation.layout.Arrangement.Horizontal = androidx.compose.foundation.layout.Arrangement.Start,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) = androidx.compose.foundation.lazy.LazyRow(
    modifier = modifier.touchScroll(state, Orientation.Horizontal), state = state,
    horizontalArrangement = horizontalArrangement, content = content,
)
