package com.lagradost.desktop.ui.screens.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import com.lagradost.desktop.platform.WindowsTouch
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun isPlayerTouch(type: PointerType, promoted: Boolean): Boolean =
    type == PointerType.Touch || (type == PointerType.Mouse && promoted)

/** Touch singles retain play/pause, but wait for a second tap so seeking never briefly pauses playback. */
@Composable
internal fun Modifier.playerTouchTaps(
    enabled: () -> Boolean,
    suppressTap: () -> Boolean,
    onSingleTap: () -> Unit,
    onSeek: (Long) -> Unit,
    promotedTouch: () -> Boolean = { WindowsTouch.lastPrimaryWasTouch },
): Modifier {
    val active by rememberUpdatedState(enabled)
    val suppress by rememberUpdatedState(suppressTap)
    val single by rememberUpdatedState(onSingleTap)
    val seek by rememberUpdatedState(onSeek)
    val promoted by rememberUpdatedState(promotedTouch)
    return pointerInput(Unit) {
        coroutineScope {
            var pending: Job? = null
            var lastTapAt: Long? = null
            var lastLeft = false
            try {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    if (!active() || !isPlayerTouch(down.type, promoted())) {
                        pending?.cancel()
                        lastTapAt = null
                        return@awaitEachGesture
                    }
                    val left = down.position.x < size.width / 2f
                    val previous = lastTapAt
                    // Opposite-side taps are two singles, not an accidental seek across the screen.
                    if (pending?.isActive == true && previous != null &&
                        (left != lastLeft || down.uptimeMillis - previous > viewConfiguration.doubleTapTimeoutMillis)) {
                        single()
                        lastTapAt = null
                    }
                    // A second press may become a hold/swipe: do not fire the first single under it.
                    pending?.cancel()
                    pending = null
                    var valid = true
                    var releasedAt = down.uptimeMillis
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || event.changes.size != 1) { valid = false; break }
                        if (change.isConsumed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) valid = false
                        if (!change.pressed) {
                            releasedAt = change.uptimeMillis
                            if (valid) change.consume()
                            break
                        }
                    }
                    if (suppress() || !valid || !active() || releasedAt - down.uptimeMillis >= HOLD_BOOST_MS) {
                        lastTapAt = null
                        return@awaitEachGesture
                    }
                    val elapsed = lastTapAt?.let { releasedAt - it }
                    if (elapsed != null && elapsed in viewConfiguration.doubleTapMinTimeMillis..viewConfiguration.doubleTapTimeoutMillis && left == lastLeft) {
                        lastTapAt = null
                        seek(if (left) -10_000L else 10_000L)
                    } else {
                        lastTapAt = releasedAt
                        lastLeft = left
                        pending = launch {
                            delay(viewConfiguration.doubleTapTimeoutMillis)
                            lastTapAt = null
                            if (active()) single()
                        }
                    }
                }
            } finally {
                pending?.cancel()
            }
        }
    }
}
