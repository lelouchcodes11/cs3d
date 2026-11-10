package com.lagradost.desktop.ui.fluent

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.composed
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.onFocusedBoundsChanged
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * A page of shelves composes a row only when it comes into view (Compose for Desktop has no look-ahead for lazy lists): the first scroll over a page
 * composed, measured and started the pictures of every row that entered, 8 or more cards each, in the frames of the scroll itself (a frame of 70 to
 * 110 ms had the window's thread about half busy in the card composition of the entering row). The list is made taller than the window by [extra]
 * and cut off at the window's edge, so the rows of the next screenful are composed, laid out and loading their pictures while the page is idle; the
 * same [extra] is added to the list's bottom padding ([lazyPadding]), so the end of the list stays where it was. Nothing of it is drawn or reachable
 * by the pointer. The value is read while measuring, so it changing does not recompose the page. -Dcloudstream.composeahead=false turns it off.
 */
fun Modifier.composeAhead(extra: State<Dp>?): Modifier = if (extra == null) this else this.clipToBounds().layout { measurable, constraints ->
    val e = if (constraints.hasBoundedHeight) extra.value.roundToPx() else 0
    val taller = Constraints(constraints.minWidth, constraints.maxWidth, constraints.minHeight + e, if (e > 0) constraints.maxHeight + e else constraints.maxHeight)
    val placeable = measurable.measure(taller)
    layout(placeable.width, (placeable.height - e).coerceAtLeast(0)) { placeable.place(0, 0) }
}

/** The list's padding with the look-ahead added to its bottom (read while measuring) */
fun lazyPadding(extra: State<Dp>?, top: Dp = 0.dp, bottom: Dp = 0.dp): PaddingValues = object : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 0.dp
    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 0.dp
    override fun calculateTopPadding(): Dp = top
    override fun calculateBottomPadding(): Dp = bottom + (extra?.value ?: 0.dp)
}

/** How far below the window a page composes ahead: nothing at first, [step] and then twice [step] once the page has been open for a moment. Null when off (weak PC, the switch) */
@Composable
fun rememberComposeAhead(step: Dp = 450.dp): State<Dp>? {
    if (System.getProperty("cloudstream.composeahead") == "false" || com.lagradost.desktop.platform.LowEnd.detected) return null
    val ahead = remember { mutableStateOf(0.dp) }
    LaunchedEffect(Unit) {
        // after the page's first frames and its pictures, in two steps so that the extra composition does not come as one long frame
        delay(1500)
        ahead.value = step
        delay(600)
        ahead.value = step * 2
    }
    return ahead
}

/**
 * The keyboard focus in a list made taller by [composeAhead]: the list takes a card in the look-ahead part below the window for one in view and does not
 * scroll to it (the Down key on Home moved the focus to rows under the window's edge). A focused element outside the part in view is scrolled in, clear
 * of the top bar ([top]) and the bottom edge ([bottom]). Only for the keyboard: a click focuses what is already in view.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.followKeyboardFocus(state: androidx.compose.foundation.lazy.LazyListState, top: Dp = 120.dp, bottom: Dp = 32.dp): Modifier = composed {
    val list = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    Modifier.onGloballyPositioned { list[0] = it }.onFocusedBoundsChanged { focused ->
        val l = list[0] ?: return@onFocusedBoundsChanged
        if (focused == null || !focused.isAttached || !l.isAttached || !InputMode.keyboard) return@onFocusedBoundsChanged
        val r = l.localBoundingBoxOf(focused, clipBounds = false)
        val t = with(density) { top.toPx() }
        val b = l.size.height - with(density) { bottom.toPx() }
        val delta = when {
            r.bottom > b -> (r.bottom - b).coerceAtMost(r.top - t)
            // within the first screenful of the page: the page goes back to its start (the art at the top of Home is shown whole)
            state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset > 0 && r.top + state.firstVisibleItemScrollOffset < l.size.height * 0.8f -> -state.firstVisibleItemScrollOffset.toFloat()
            // near the top: the element goes to the upper middle (at the start of the page that is the top: the art at the top of Home fades out as it scrolls away)
            r.top < maxOf(t, l.size.height * 0.3f) -> r.top - l.size.height * 0.45f
            else -> 0f
        }
        if (delta != 0f) scope.launch { state.animateScrollBy(delta) }
    }
}
