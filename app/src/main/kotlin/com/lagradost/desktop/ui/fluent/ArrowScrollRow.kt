package com.lagradost.desktop.ui.fluent

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A row that is wider than its place and scrolls sideways: a button at each end moves it by most of its width (dimmed when there is nothing more that way),
 * and a mouse can hold it and drag it (a short movement is still a click on what is under the pointer; the row runs on a little after the button is
 * let go). A finger drags it by itself. The chips of a list of categories, the pictures of a group.
 */
@Composable
fun ArrowScrollRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val state = rememberScrollState()
    val scope = rememberCoroutineScope()
    var width by remember { mutableIntStateOf(1) }
    val dragState = rememberDraggableState { delta -> state.dispatchRawDelta(-delta) }
    val back = state.canScrollBackward
    val forward = state.canScrollForward
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.alpha(if (back) 1f else 0.3f)) { ChevronButton(Icons.ChevronLeft) { if (back) scope.launch { state.animateScrollBy(-width * 0.7f) } } }
        Row(
            Modifier.weight(1f).onSizeChanged { width = it.width }
                .draggable(
                    dragState, Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        scope.launch {
                            var last = 0f
                            AnimationState(0f, -velocity).animateDecay(exponentialDecay(frictionMultiplier = 1.4f)) {
                                val step = value - last
                                last = value
                                if ((step > 0f && !state.canScrollForward) || (step < 0f && !state.canScrollBackward)) cancelAnimation() else state.dispatchRawDelta(step)
                            }
                        }
                    },
                )
                .horizontalScroll(state),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        Box(Modifier.alpha(if (forward) 1f else 0.3f)) { ChevronButton(Icons.ChevronRight) { if (forward) scope.launch { state.animateScrollBy(width * 0.7f) } } }
    }
}
