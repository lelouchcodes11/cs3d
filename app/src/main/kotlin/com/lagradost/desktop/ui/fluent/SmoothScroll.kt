package com.lagradost.desktop.ui.fluent

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * to a distance still to travel and the list follows it, frame by frame, pulled by a spring that speeds up and slows down gently (about
 * 170 ms a notch), so a flick of the wheel is a smooth run and notches in quick succession add up. Sideways wheels, Shift and Ctrl+wheel are left to whoever
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
                WheelLog.add(delta.y)
                // a touchpad (and a free-spinning or high-resolution wheel) sends many small, fractional steps that are already smooth, and a slow finger
                // often adds tiny steps the other way: those go straight to the page, added up, never eased or treated as a turn of the wheel (that
                // took back what was still to travel and tugged the page backwards: felt as resistance when scrolling slowly)
                val precise = abs(delta.y - kotlin.math.round(delta.y)) > 0.01f
                glide.push(delta.y * stepPx, precise)
            }
        }
    }
}

/** The last wheel steps (time since the one before, size), for Settings > About > Copy diagnostics: shows what a mouse or touchpad really sends */
object WheelLog {
    private val steps = ArrayDeque<String>()
    private var last = 0L

    @Synchronized fun add(delta: Float) {
        val now = System.currentTimeMillis()
        val gap = if (last == 0L) 0 else (now - last).coerceAtMost(9999)
        last = now
        steps.addLast(String.format(java.util.Locale.ROOT, "%+.2f@%d", delta, gap))
        while (steps.size > 60) steps.removeFirst()
    }

    @Synchronized fun text(): String = if (steps.isEmpty()) "none yet" else steps.joinToString(" ")
}

/**
 * True while a wheel glide is under way. A page that moves under a resting pointer hands the pointer to a new card every few frames; each one
 * would lift, throw its shadow and compose its panel (title, buttons, facts) for a card that is gone a moment later. The cards leave those
 * effects out until the page has stopped (they come back for the card under the pointer then).
 */
object ScrollActivity {
    var active by mutableStateOf(false)
}

private class WheelGlide(private val state: ScrollableState, private val scope: CoroutineScope) {
    private var remaining = 0f
    /** How fast the page moves now, in pixels a second (the spring of [glide]) */
    private var velocity = 0f
    private var job: Job? = null
    private var lastPushAt = 0L

    /** A fine step of a touchpad: moved within the next frame, no ease and no reversal rule */
    private var direct = false

    /** The direction of the last notch (1 down, -1 up) and a notch held as a possible bounce */
    private var lastDir = 0
    private var held = 0f
    private var heldAt = 0L

    fun push(px: Float, precise: Boolean = false) {
        val now = System.nanoTime()
        if (precise) {
            direct = true
            lastPushAt = now
            remaining += px
            if (job?.isActive != true) job = scope.launch { run() }
            return
        }
        direct = false
        val dir = if (px > 0f) 1 else -1
        // A wheel often sends one notch the wrong way right after a real one (the wheel's contact bouncing as it settles in a notch; a worn wheel does it
        // more). When scrolling slowly every notch is a run of its own, so that stray notch moved the page back each time: felt as the page being
        // dragged the opposite way. A notch against the last one within 150 ms is held: a second one in the same new direction (within 300 ms)
        // means the wheel really turned back and both apply; otherwise it was the bounce and is dropped.
        if ((lastDir != 0 && dir != lastDir && now - lastPushAt < 150_000_000L) || (held != 0f && (held > 0f) == (px > 0f) && now - heldAt < 300_000_000L)) {
            if (held != 0f && (held > 0f) == (px > 0f) && now - heldAt < 300_000_000L) {
                val back = held + px
                held = 0f
                lastDir = dir
                lastPushAt = now
                remaining = back
                velocity = 0f
                if (job?.isActive != true) job = scope.launch { run() }
            } else {
                held = px
                heldAt = now
            }
            return
        }
        held = 0f
        val reverse = remaining != 0f && (remaining > 0f) != (px > 0f)
        lastDir = dir
        lastPushAt = now
        // turning the wheel the other way (after a pause) takes back what was still to come
        remaining = if (reverse) px else remaining + px
        if (reverse) velocity = 0f
        if (job?.isActive != true) job = scope.launch { run() }
    }

    private suspend fun run() {
        // -Dcloudstream.hoverscroll=keep: cards keep their hover effects while the page moves (to measure what they cost)
        ScrollActivity.active = System.getProperty("cloudstream.hoverscroll") != "keep"
        try { glide() } finally { ScrollActivity.active = false }
    }

    private suspend fun glide() {
        var last = withFrameNanos { it }
        while (abs(remaining) > 0.5f || abs(velocity) > 20f) {
            val now = withFrameNanos { it }
            val seconds = ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            last = now
            val part: Float
            if (direct) {
                // A touchpad sends its steps every 8-16 ms, unevenly. Applied whole on the next frame they moved the page in a frame and not in the
                // next on a 144 Hz screen (a frame every 6.9 ms): uneven jumps, felt as stutter (on 60 Hz nearly every frame gets one, so it looked
                // fine). Each step is now spread over the next ~20 ms by time, not by frames: the same path at any refresh rate, 20 ms behind.
                part = if (abs(remaining) < 1f) remaining else remaining * (1f - exp(-seconds / DIRECT_TAU))
                velocity = 0f
            } else {
                // A critically damped spring pulls the page toward where the wheel wants it: it speeds up gently, slows down gently and never
                // overshoots, and a notch that comes while the page still moves carries the speed on (one long glide instead of a stop per
                // notch). The old curve started each notch at full speed and only slowed down: every notch began with a jolt (felt as "rusty").
                var left = remaining
                var v = velocity
                var t = seconds
                var moved = 0f
                while (t > 0f) {
                    val dt = minOf(t, 0.004f)
                    v += (OMEGA * OMEGA * left - 2f * OMEGA * v) * dt
                    val d = v * dt
                    left -= d
                    moved += d
                    t -= dt
                }
                velocity = v
                part = moved
            }
            val moved = state.scrollBy(part)
            remaining -= part
            // the end of the list: nothing moved, nothing left to travel
            if (abs(part) > 1f && abs(moved) < abs(part) * 0.25f) { remaining = 0f; velocity = 0f }
            if (abs(remaining) <= 0.5f && abs(velocity) <= 20f) break
        }
        remaining = 0f
        velocity = 0f
    }

    private companion object {
        // how stiff the spring is: about 60 ms to half a notch and 170 ms to all of it (the old curve's 65 ms ease felt like a lag behind the wheel;
        // this one starts softer but keeps up with quick notches because the speed carries on)
        const val OMEGA = 28f
        // how quickly a touchpad step is followed: 63 % of it in 20 ms, 95 % in 60 ms
        const val DIRECT_TAU = 0.020f
    }
}
