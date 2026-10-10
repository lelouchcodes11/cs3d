package com.lagradost.desktop.ui.fluent

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp

/**
 * Wheel scrolling that glides. A mouse wheel notch moves a list by a fixed jump (the page jerks one step per notch); here each notch only adds
 * to a distance still to travel and the list follows it, frame by frame, covering a part of what is left each time (an exponential ease of about
 * 90 ms), so a flick of the wheel is a smooth run and notches in quick succession add up. Sideways wheels, Shift and Ctrl+wheel are left to whoever
 * handles them (a shelf scrolls sideways, Ctrl zooms). Off with Settings > Look > Animations: Off.
 */
@Composable
fun Modifier.smoothWheel(state: ScrollableState, step: Dp = 72.dp): Modifier {
    if (Appearance.motion == Motion.Off) return this
    val scope = rememberCoroutineScope()
    val stepPx = with(LocalDensity.current) { step.toPx() }
    val glide = remember(state) { WheelGlide(state, scope) }
    return this.pointerInput(state, stepPx) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Scroll) continue
                val change = event.changes.firstOrNull() ?: continue
                val delta = change.scrollDelta
                if (change.isConsumed || delta.y == 0f || delta.x != 0f) continue
                if (event.keyboardModifiers.isShiftPressed || event.keyboardModifiers.isCtrlPressed) continue
                change.consume()
                glide.push(delta.y * stepPx)
            }
        }
    }
}

private class WheelGlide(private val state: ScrollableState, private val scope: CoroutineScope) {
    private var remaining = 0f
    private var job: Job? = null

    fun push(px: Float) {
        // turning the wheel the other way takes back what was still to come
        remaining = if (remaining != 0f && (remaining > 0f) != (px > 0f)) px else remaining + px
        if (job?.isActive != true) job = scope.launch { run() }
    }

    private suspend fun run() {
        var last = withFrameNanos { it }
        while (abs(remaining) > 0.5f) {
            val now = withFrameNanos { it }
            val seconds = ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.08f)
            last = now
            val part = remaining * (1f - exp(-seconds / TAU))
            val moved = state.scrollBy(part)
            remaining -= part
            // the end of the list: nothing moved, nothing left to travel
            if (abs(part) > 1f && abs(moved) < abs(part) * 0.25f) remaining = 0f
        }
        remaining = 0f
    }

    private companion object {
        const val TAU = 0.09f
    }
}
