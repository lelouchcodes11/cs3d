package com.lagradost.desktop.player

import android.util.Log
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer

/**
 * When the monitor refreshes, in [System.nanoTime] units: a helper thread waits for the graphics kernel's vertical-blank event and stamps each
 * one (D3DKMTWaitForVerticalBlankEvent). It runs only while frames ask for the grid and ends itself a few seconds after the last question.
 *
 * Why: a 24 fps film on a 60 Hz screen has to alternate 2 and 3 refreshes per frame. Frames used to be shown at the first refresh after they were
 * rendered, and render time / thread wake-ups jitter by a few milliseconds, so frames that lay near a refresh flipped between two slots at random
 * (measured: 38 % of the frame pairs repeated the gap before them, an even rhythm has none) and the motion looked uneven ("slow motion", "rusty").
 * With the refresh grid known, [FramePacer] puts each frame on a slot that follows from mpv's exact target time, not from the jitter.
 */
internal object VBlankClock {
    private const val TAG = "VBlankClock"

    private interface Gdi : Library {
        fun D3DKMTOpenAdapterFromHdc(data: Pointer): Int
        fun D3DKMTWaitForVerticalBlankEvent(data: Pointer): Int
        fun D3DKMTCloseAdapter(data: Pointer): Int
    }

    private interface User : Library {
        fun GetDC(hwnd: Pointer?): Pointer?
        fun ReleaseDC(hwnd: Pointer?, hdc: Pointer?): Int
        fun MonitorFromWindow(hwnd: Pointer?, flags: Int): Pointer?
        fun GetMonitorInfoW(monitor: Pointer?, info: Pointer): Int
    }

    private interface GdiDc : Library {
        fun CreateDCW(driver: com.sun.jna.WString, device: com.sun.jna.WString, port: com.sun.jna.WString?, mode: Pointer?): Pointer?
        fun DeleteDC(hdc: Pointer?): Int
    }

    private val gdi: Gdi? by lazy { runCatching { Native.load("gdi32", Gdi::class.java) }.onFailure { Log.w(TAG, "no gdi32: ${it.message}") }.getOrNull() }
    private val user: User? by lazy { runCatching { Native.load("user32", User::class.java) }.getOrNull() }
    private val gdiDc: GdiDc? by lazy { runCatching { Native.load("gdi32", GdiDc::class.java) }.getOrNull() }

    /** The display ("\\.\DISPLAY2") the player window is on now; null when it cannot be told (the primary display is used then) */
    private fun windowDisplay(): String? = runCatching {
        val u = user ?: return null
        val window = com.lagradost.desktop.ui.DesktopUiHost.window ?: return null
        val hwnd = Native.getComponentPointer(window) ?: return null
        val monitor = u.MonitorFromWindow(hwnd, 2) ?: return null // MONITOR_DEFAULTTONEAREST
        val info = Memory(104).also { it.clear(); it.setInt(0, 104) } // MONITORINFOEXW: 40 bytes of MONITORINFO, then the 32 characters of szDevice
        if (u.GetMonitorInfoW(monitor, info) == 0) null else info.getWideString(40).takeIf { it.isNotBlank() }
    }.getOrNull()

    /** The latest vblank and the refresh period (nanoTime units); 0 while unknown */
    @Volatile private var lastVBlankNs = 0L
    @Volatile var periodNs = 0L
        private set
    @Volatile private var lastAskedAt = 0L
    @Volatile private var failed = false
    private var thread: Thread? = null

    /** True when the refresh grid is known and fresh; starts the helper thread when it is not running */
    val available: Boolean
        get() {
            if (failed) return false
            val now = System.nanoTime()
            lastAskedAt = now
            ensureRunning()
            return periodNs > 0 && now - lastVBlankNs < 250_000_000L
        }

    @Synchronized
    private fun ensureRunning() {
        if (thread?.isAlive == true) return
        thread = Thread(::run, "VBlankClock").apply { isDaemon = true; priority = Thread.NORM_PRIORITY + 2; start() }
    }

    private fun run() {
        val g = gdi
        val u = user
        if (g == null || u == null) { failed = true; return }
        // the display the window is on (refresh rate and phase differ between monitors), the primary one when that cannot be told
        val display = windowDisplay()
        val open = Memory(24).also { it.clear() } // D3DKMT_OPENADAPTERFROMHDC {HDC; handle; LUID; sourceId}
        var status = -1
        if (display != null) {
            val dc = gdiDc?.CreateDCW(com.sun.jna.WString("DISPLAY"), com.sun.jna.WString(display), null, null)
            if (dc != null) {
                open.setPointer(0, dc)
                status = g.D3DKMTOpenAdapterFromHdc(open)
                gdiDc?.DeleteDC(dc)
            }
        }
        if (status != 0) {
            val hdc = u.GetDC(null)
            open.clear(); open.setPointer(0, hdc)
            status = g.D3DKMTOpenAdapterFromHdc(open)
            u.ReleaseDC(null, hdc)
        }
        if (status != 0) { Log.w(TAG, "D3DKMTOpenAdapterFromHdc: 0x${Integer.toHexString(status)}"); failed = true; return }
        Log.i(TAG, "watching the refresh of ${display ?: "the primary display"}")
        var checkedAt = System.nanoTime()
        val wait = Memory(12).also { it.clear(); it.setInt(0, open.getInt(8)); it.setInt(8, open.getInt(20)) } // D3DKMT_WAITFORVERTICALBLANKEVENT {adapter; device; sourceId}
        val stamps = LongArray(48)
        var count = 0
        try {
            while (System.nanoTime() - lastAskedAt < 4_000_000_000L) {
                val r = g.D3DKMTWaitForVerticalBlankEvent(wait)
                val now = System.nanoTime()
                if (r != 0) { Log.w(TAG, "D3DKMTWaitForVerticalBlankEvent: 0x${Integer.toHexString(r)}"); failed = periodNs == 0L; return }
                stamps[count % stamps.size] = now
                count++
                // the window was moved to another monitor: its refresh is what counts, so start over on that one (the thread ends, the next question starts a new one)
                if (now - checkedAt > 500_000_000L) {
                    checkedAt = now
                    val current = windowDisplay()
                    if (current != display) { Log.i(TAG, "window moved to display $current (was $display)"); return }
                }
                if (count >= 8) {
                    val n = minOf(count, stamps.size)
                    val first = stamps[(count - n) % stamps.size]
                    val diffs = LongArray(n - 1) { i -> stamps[(count - n + i + 1) % stamps.size] - stamps[(count - n + i) % stamps.size] }.also { it.sort() }
                    val median = diffs[diffs.size / 2]
                    // the whole span divided by the number of refreshes in it: the average, without the jitter of single stamps
                    val refreshes = Math.round((now - first).toDouble() / median)
                    val period = if (refreshes > 0) (now - first) / refreshes else median
                    if (period in 2_700_000L..41_700_000L) {
                        if (periodNs == 0L) Log.i(TAG, "refresh period %.3f ms (%.2f Hz)".format(period / 1e6, 1e9 / period))
                        periodNs = period
                    }
                }
                lastVBlankNs = now
            }
        } finally {
            g.D3DKMTCloseAdapter(Memory(4).also { it.setInt(0, open.getInt(8)) })
            periodNs = 0L
            lastVBlankNs = 0L
        }
    }

    /** The first refresh at or after [t] (nanoTime units) */
    fun atOrAfter(t: Long): Long {
        val base = lastVBlankNs
        val period = periodNs
        val n = Math.floorDiv(t - base + period - 1, period)
        return base + n * period
    }
}

/**
 * Holds a rendered frame until the moment it has to be handed to the window so that it is shown at the refresh it belongs to.
 *
 * mpv is asked not to block in its render call: it then hands over each frame about one frame interval before its target time (measured 35-39 ms
 * ahead for 24 fps), so there is plenty of time to hold it. The refresh of each frame follows from the spacing of mpv's target times (exact),
 * carried forward from the previous frame's refresh: the next refresh that is not earlier than the frame's wanted time. That is the even 2-3-2-3
 * rhythm of 24 fps on 60 Hz, and nothing in it depends on the few milliseconds of jitter of the render thread or of the refresh stamps (the plain
 * "first refresh after the render" lets frames that lie close to a refresh flip between two refreshes for a few seconds in every cycle of the
 * beat between the film's rate and the monitor's). The schedule is nudged towards the real refresh grid by an eighth of the difference per frame.
 */
internal object FramePacer {
    /** The frame is wanted on screen this long after its target time (the window's own draw and hand-over need a few milliseconds) */
    private const val SLOT_PHASE_NS = 6_000_000L

    /** A frame may be shown this much earlier than wanted (that keeps the choice between two refreshes from flipping on the smallest differences) */
    private const val EARLY_NS = 4_000_000L

    /** The frame is handed over this long after the refresh before its slot (the window's draw has to start after the previous present returned) */
    private const val AFTER_VBLANK_NS = 2_000_000L

    /** A target further away than this is a clock jump, not a frame to wait for: show at once */
    private const val MAX_HOLD_NS = 1_000_000_000L

    /** Target times further apart than this start the schedule again (seek, pause, new file) */
    private const val RELOCK_NS = 300_000_000L

    /** -Dcloudstream.nopacing=true goes back to mpv's own blocking (frames shown at the first refresh after they are rendered) */
    val enabled = System.getProperty("cloudstream.nopacing") != "true"

    /** mpv's target further than this from the predicted one is a real change of timing, not jitter */
    private const val SMOOTH_MAX_NS = 8_000_000L

    // render thread only
    private var slot = 0L
    private var lastTarget = 0L
    private var smoothTarget = 0L
    private var avgStep = 0L
    private var lastReal = 0L
    private val decided = IntArray(5)
    private var maxJitterNs = 0L
    private var held = 0
    private var relocks = 0
    private var reportedAt = 0L
    private var parks = 0
    private var oversleepNs = 0L
    private var maxOversleepNs = 0L

    /** The last 5 s of pacing, for the dev server's /videostats */
    @Volatile var last = ""
        private set

    private fun report() {
        val now = System.nanoTime()
        if (reportedAt == 0L) reportedAt = now
        if (now - reportedAt < 5_000_000_000L) return
        last = "held=$held relocks=$relocks, sleep overshoot %.2f ms avg / %.2f ms max over $parks sleeps, decided 0/1/2/3/4+ = ${decided.joinToString("/")}, target jitter max %.2f ms".format(oversleepNs / 1e6 / parks.coerceAtLeast(1), maxOversleepNs / 1e6, maxJitterNs / 1e6)
        if (System.getProperty("cloudstream.videostats") != null) Log.i("FramePacer", "pacing: $last")
        decided.fill(0); maxJitterNs = 0L
        held = 0; relocks = 0; parks = 0; oversleepNs = 0; maxOversleepNs = 0; reportedAt = now
    }

    private fun waitUntil(deadline: Long) {
        val wait = deadline - System.nanoTime()
        if (wait <= 0L || wait > MAX_HOLD_NS) return
        // park for the bulk, spin for the last millisecond (parking wakes up late by up to a millisecond or two)
        if (wait > 2_500_000L) {
            val asked = wait - 1_500_000L
            val t0 = System.nanoTime()
            java.util.concurrent.locks.LockSupport.parkNanos(asked)
            val over = System.nanoTime() - t0 - asked
            parks++; oversleepNs += over.coerceAtLeast(0); if (over > maxOversleepNs) maxOversleepNs = over
        }
        while (System.nanoTime() < deadline) Thread.onSpinWait()
    }

    /** Waits (on the calling render thread) until [targetNs] (nanoTime units, 0 = unknown: a redraw, show at once) has its slot */
    fun hold(targetNs: Long) {
        if (!enabled) return
        if (targetNs <= 0L) { slot = 0L; return }
        if (!VBlankClock.available) {
            // the refresh grid is not known (yet): what mpv's blocking did, wait for the target time
            slot = 0L
            waitUntil(targetNs)
            return
        }
        val period = VBlankClock.periodNs
        val relock = slot == 0L || Math.abs(targetNs - lastTarget) > RELOCK_NS
        // mpv's target times jitter by about a millisecond (audio clock corrections, reading two clocks). A film whose rate nearly divides the
        // refresh rate (29.97 fps on 60 Hz) drifts across a refresh boundary every ~17 s, and while it is near it the jitter made frames flip
        // between one and three refreshes for a second or two (measured: 58 such pairs a minute, a steady rhythm has one 3 per crossing).
        // So the frame clock is predicted from the average frame length and pulled only 1/16 of the way to mpv's time: jitter / 16, drift kept.
        val step = targetNs - lastTarget
        if (relock) { smoothTarget = targetNs; avgStep = 0L } else {
            if (step > 0) avgStep = if (avgStep == 0L) step else avgStep + (step - avgStep) / 32
            val predicted = smoothTarget + avgStep
            // a jump (a dropped or repeated frame, a speed change): follow mpv at once
            smoothTarget = if (avgStep == 0L || Math.abs(targetNs - predicted) > SMOOTH_MAX_NS) targetNs else predicted + (targetNs - predicted) / 16
        }
        val wanted = smoothTarget + SLOT_PHASE_NS - EARLY_NS
        var s = if (relock) {
            relocks++
            VBlankClock.atOrAfter(wanted)
        } else {
            // the next refresh of the carried schedule that is not earlier than wanted (none = the frame shares the previous refresh)
            val n = Math.max(0L, Math.floorDiv(wanted - slot + period - 1, period))
            slot + n * period
        }
        // the real refresh nearest to the schedule, and a step of the schedule towards it
        val real = VBlankClock.atOrAfter(s - period / 2)
        s += (real - s) / 8
        slot = s
        lastTarget = targetNs
        if (!relock && lastReal != 0L) decided[Math.round((real - lastReal).toDouble() / period).toInt().coerceIn(0, 4)]++
        lastReal = real
        maxJitterNs = maxOf(maxJitterNs, Math.abs(targetNs - smoothTarget))
        report()
        held++
        waitUntil(real - period + AFTER_VBLANK_NS)
    }
}

/** The refresh grid as the window's frame statistics see it */
internal object VBlankGrid : com.lagradost.desktop.runtime.ui.RefreshGrid {
    override fun refreshAtOrAfter(t: Long): Long = if (VBlankClock.available) VBlankClock.atOrAfter(t) else 0L
    override val periodNs: Long get() = VBlankClock.periodNs
}
