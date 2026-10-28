package com.lagradost.desktop.ui.screens.player

import android.util.Log
import com.lagradost.desktop.platform.WinProbe
import java.util.concurrent.atomic.AtomicInteger

/**
 * The safety net of the player's controls. A few users reported a video with no controls at all. The controls only ever came from pointer events
 * that Compose delivered, and in the native (GPU) player from a second, transparent window above the video; when either fails the viewer is left with
 * a picture and nothing to click. Three fail-safes, none of which depends on the thing that failed:
 *
 *  1. [PointerWatch]: Windows is asked where the pointer is (10 times a second, a plain GetCursorPos); a pointer that moves over the app's own window
 *     shows the controls even when no pointer event reached Compose.
 *  2. [OverlayHealth]: in the native player the controls window must be visible and must be the window under the pointer when the pointer is over the
 *     video. A window that never appears, or that lets the pointer through to the video below (click-through, behind the video), is first put on top
 *     again, and when that does not help the standard player (controls drawn in the app's own window) takes over for good.
 *  3. The C key shows the controls and keeps them on the picture until it is pressed again (PlayerScreen), whatever else is wrong.
 *
 * Settings > Player > Controls > "Controls safety net" switches all of it off.
 */
object ControlsGuard {
    private const val TAG = "ControlsGuard"

    /** Counters for the diagnostics: what the net saw in this run of the app */
    val pointerAssists = AtomicInteger()
    val healed = AtomicInteger()
    val fellBack = AtomicInteger()

    @Volatile
    var lastNote = "not started"

    fun note(text: String) { lastNote = text }

    /** The line of "Copy diagnostics" */
    fun summary(): String = "safety net ${if (com.lagradost.desktop.ui.fluent.Appearance.controlsGuard) "on" else "off"}; pointer assists ${pointerAssists.get()}, " +
        "controls window re-raised ${healed.get()}, GPU player given up ${fellBack.get()}; last: $lastNote"

    /** Where the pointer is, by Windows, and which window of ours (if any) is under it */
    class Sample(val x: Int, val y: Int, val hit: Hit)

    enum class Hit {
        /** the controls window (or a child of it) */
        Overlay,

        /** the app's window, with the video window in it: whatever the controls window is, it is not on top here */
        Main,

        /** another program's window (or none): says nothing about our windows */
        Other,
    }

    fun sample(main: Long, overlay: Long): Sample? {
        val p = WinProbe.cursor() ?: return null
        val root = WinProbe.rootAt(p[0], p[1])
        val hit = when {
            overlay != 0L && root == overlay -> Hit.Overlay
            main != 0L && root == main -> Hit.Main
            else -> Hit.Other
        }
        return Sample(p[0], p[1], hit)
    }

    /** Remembers the last pointer position and says when the pointer moved */
    class PointerWatch(private val slackPx: Int = 3) {
        private var lastX = Int.MIN_VALUE
        private var lastY = Int.MIN_VALUE

        /** True when the pointer is at least [slackPx] pixels from where it was at the last call (the first call never counts: there is nothing to compare with) */
        fun moved(x: Int, y: Int): Boolean {
            val first = lastX == Int.MIN_VALUE
            val away = !first && (Math.abs(x - lastX) >= slackPx || Math.abs(y - lastY) >= slackPx)
            if (first || away) { lastX = x; lastY = y }
            return away
        }
    }

    enum class Verdict { Fine, Heal, GiveUp }

    /**
     * Judges the controls window of the native player from samples. [now] is injected for the tests.
     *
     *  - the window is not visible for [HIDDEN_HEAL_MS] (it never came up, or went away): [Verdict.Heal]; for [HIDDEN_GIVE_UP_MS]: [Verdict.GiveUp];
     *  - the pointer moves over the controls window's place and Windows says the window under it is the app's own (the controls window is behind the video or lets
     *    the pointer through) in [BLIND_SAMPLES] samples in a row spread over at least [BLIND_SPAN_MS]: Heal, and the same again after a heal: GiveUp.
     *  - a sample that finds the controls window under the pointer clears the blind count; a window of another program says nothing.
     */
    class OverlayHealth(private val now: () -> Long = System::currentTimeMillis) {
        private var hiddenSince = 0L
        private var blind = 0
        private var blindSince = 0L
        private var healedOnce = false

        /** Counting starts again: after a heal, or when the app's window was minimized (the controls window goes with it, which says nothing) */
        fun clear() { hiddenSince = 0L; blind = 0; blindSince = 0L }

        fun sample(overlayVisible: Boolean, pointerMovedOverVideo: Boolean, hit: Hit): Verdict {
            val t = now()
            if (!overlayVisible) {
                if (hiddenSince == 0L) hiddenSince = t
                val hidden = t - hiddenSince
                if (hidden >= HIDDEN_GIVE_UP_MS && healedOnce) return Verdict.GiveUp
                if (hidden >= HIDDEN_HEAL_MS && !healedOnce) { healedOnce = true; clear(); return Verdict.Heal }
                return Verdict.Fine
            }
            hiddenSince = 0L
            if (!pointerMovedOverVideo) return Verdict.Fine
            when (hit) {
                Hit.Overlay -> { blind = 0; blindSince = 0L }
                Hit.Other -> {}
                Hit.Main -> {
                    if (blind == 0) blindSince = t
                    blind++
                    if (blind >= BLIND_SAMPLES && t - blindSince >= BLIND_SPAN_MS) {
                        clear()
                        if (!healedOnce) { healedOnce = true; return Verdict.Heal }
                        return Verdict.GiveUp
                    }
                }
            }
            return Verdict.Fine
        }

        companion object {
            const val HIDDEN_HEAL_MS = 6_000L
            const val HIDDEN_GIVE_UP_MS = 12_000L
            const val BLIND_SAMPLES = 6
            const val BLIND_SPAN_MS = 1_200L
        }
    }

    /** What to do when the net gives up on the native player's controls window (called once per video page) */
    fun giveUpNative(reason: String) {
        if (NativeVideo.broken) return
        fellBack.incrementAndGet()
        note("gave up on the GPU player: $reason")
        Log.w(TAG, "the GPU player's controls window can not be used ($reason): the standard player takes over")
        NativeVideo.broken = true
        // the choice was the user's, and it does not work on this PC: it is not tried again on the next video or the next start
        com.lagradost.desktop.ui.fluent.Appearance.nativePlayer = false
        com.lagradost.desktop.ui.fluent.Appearance.save()
        com.lagradost.desktop.ui.Toasts.show("The GPU player's controls could not be shown on this PC. Using the standard player (Settings > Player > Video).", true)
        PlayerSession.active?.restartPlayback()
    }

    fun heal(overlayHandle: Long, reason: String) {
        healed.incrementAndGet()
        note("controls window re-raised: $reason")
        Log.w(TAG, "the controls window of the GPU player is not usable ($reason): putting it on top again")
        WinProbe.raise(overlayHandle)
    }
}
