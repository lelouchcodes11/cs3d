package com.lagradost.desktop.ui.fluent

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop

@Composable
private fun fluentScrollbarStyle(): ScrollbarStyle {
    val c = Fluent.colors
    return ScrollbarStyle(
        minimalHeight = 36.dp, thickness = 6.dp, shape = RoundedCornerShape(3.dp), hoverDurationMillis = 200,
        unhoverColor = c.textSecondary.copy(alpha = 0.32f), hoverColor = c.textSecondary.copy(alpha = 0.75f),
    )
}

/**
 * The bar is there while the page moves, for a moment after, and while the pointer is on it or drags it; at rest it fades away, so a page is not
 * edged with a grey line all the time (it comes back as soon as the pointer goes to the right edge).
 */
@Composable
private fun BoxScope.FadingScrollbar(adapter: ScrollbarAdapter, state: ScrollableState, top: Dp, position: () -> Long) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val dragged by source.collectIsDraggedAsState()
    val moving = state.isScrollInProgress
    // the mouse wheel moves a list in raw steps that [isScrollInProgress] does not report: the position itself is watched too. Only a change of
    // [recent] recomposes the bar (it used to count every move in a state: the bar recomposed and restarted its timer in every frame of a scroll)
    var recent by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        snapshotFlow(position).drop(1).collectLatest { recent = true; delay(1100); recent = false }
    }
    val shown = moving || recent || hovered || dragged
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(if (shown) 120 else 420), label = "scrollbarAlpha")
    VerticalScrollbar(
        adapter, Modifier.zIndex(10f).align(Alignment.CenterEnd).fillMaxHeight().padding(top = top, end = 2.dp).graphicsLayer { this.alpha = alpha },
        style = fluentScrollbarStyle(), interactionSource = source,
    )
}

/** Thin overlay scrollbar at the right edge of a lazy list; [top] keeps it below a see-through top bar */
@Composable
fun BoxScope.FluentScrollbar(state: LazyListState, top: Dp = 0.dp) = FadingScrollbar(rememberScrollbarAdapter(state), state, top) { state.firstVisibleItemIndex * 100_000L + state.firstVisibleItemScrollOffset }

@Composable
fun BoxScope.FluentScrollbar(state: LazyGridState, top: Dp = 0.dp) = FadingScrollbar(rememberScrollbarAdapter(state), state, top) { state.firstVisibleItemIndex * 100_000L + state.firstVisibleItemScrollOffset }

@Composable
fun BoxScope.FluentScrollbar(state: ScrollState, top: Dp = 0.dp) = FadingScrollbar(rememberScrollbarAdapter(state), state, top) { state.value.toLong() }
