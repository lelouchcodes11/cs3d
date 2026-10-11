package com.lagradost.desktop.player

import android.util.Log
import com.lagradost.desktop.runtime.ui.MpvSurfaceView

/**
 * The standard video player draws every frame with the CPU (mpv's software renderer into a bitmap that the window shows). A fast PC does that in a few
 * milliseconds; a weak one (an old dual-core, a very small chip, a video that can only be decoded in software) shows the picture at 10 to 15 frames a
 * second and drops the rest: "the video hangs". Every five seconds the numbers of the surface and the drop counters of mpv are looked at; two slow
 * windows in a row lower the cost by one step:
 *
 *  1. smooth motion off (it makes the window draw on every refresh) and frames may be dropped in the decoder, not only at the end;
 *  2. frames are rendered with at most about 1.1 million pixels (a little more than 1280 x 860, the graphics card enlarges them) and the decoder takes
 *     its fast paths;
 *  3. at most about 0.56 million pixels (960 x 585) and the loop filter is skipped on frames that no other frame refers to.
 *
 * The steps stay for the rest of the run of the app (the PC does not get faster), also for the next videos. When the video is still slow after the last
 * step the viewer is told once that the GPU player does the work on the graphics card.
 */
class PlaybackGovernor(
    private val surface: MpvSurfaceView,
    private val isPlaying: () -> Boolean,
    private val onStep: (Int) -> Unit,
    private val onStillSlow: () -> Unit,
) {
    companion object {
        private const val TAG = "PlaybackGovernor"
        const val MAX_LEVEL = 3

        /** The step reached in this run of the app; a new video starts there */
        @Volatile
        var level = 0
            private set

        @Volatile
        private var suggested = false

        /** Dev: set the step by hand (`/player?perf=2`), 0 undoes them for the next video */
        fun force(value: Int) {
            level = value.coerceIn(0, MAX_LEVEL)
        }
    }

    private var appliedTo: MpvPlayer? = null
    private var appliedLevel = -1
    private var lastSerial = -1
    private var lastDropped = -1L
    private var slowWindows = 0

    /** The last decision, for the diagnostics */
    @Volatile
    var lastNote = "not looked at yet"
        private set

    private fun apply(player: MpvPlayer?) {
        val l = level
        // the surface part first, it needs no core
        surface.smoothAllowed = l < 1
        surface.qualityCapPixels = when {
            l >= 3 -> 560_000L
            l >= 2 -> 1_100_000L
            else -> 0L
        }
        if (player != null) {
            // an unchanged option is not set again: setting a decoder option makes mpv restart the decoder (a hitch in the picture)
            if (l >= 1) player.setMpvProperty("framedrop", "decoder+vo")
            if (l >= 2) player.setMpvProperty("vd-lavc-fast", "yes")
            if (l >= 3) player.setMpvProperty("vd-lavc-skiploopfilter", "nonref")
        }
        appliedLevel = l
    }

    /** Called about every five seconds while a video page is open */
    fun check() {
        val p = MpvPlayer.active ?: return
        // a new core (another source, a reload) or a step set from outside: the state of the steps is put on it
        if (p !== appliedTo || appliedLevel != level) {
            appliedTo = p
            apply(p)
            lastDropped = -1L
            slowWindows = 0
        }
        if (p.native) { lastNote = "GPU player: nothing to do"; return }
        val serial = surface.stats.windowSerial
        if (serial == lastSerial) return
        lastSerial = serial

        val dropped = (p.getMpvPropertyString("frame-drop-count")?.toLongOrNull() ?: 0L) +
            (p.getMpvPropertyString("decoder-frame-drop-count")?.toLongOrNull() ?: 0L)
        val before = lastDropped
        lastDropped = dropped
        val fps = p.getMpvPropertyString("container-fps")?.toDoubleOrNull()?.takeIf { it > 1.0 }
            ?: p.getMpvPropertyString("estimated-vf-fps")?.toDoubleOrNull()?.takeIf { it > 1.0 }
        val cache = p.getMpvPropertyString("paused-for-cache") == "yes"
        val secs = surface.stats.windowSeconds
        // paused, loading, waiting for the network, or nothing to compare with: not a measure of this PC
        if (before < 0 || !isPlaying() || cache || fps == null || secs <= 0.0) {
            slowWindows = 0
            lastNote = "not measured (playing=${isPlaying()} cache=$cache fps=$fps)"
            return
        }
        val interval = 1000.0 / fps
        val dropRatio = (dropped - before).coerceAtLeast(0) / (fps * secs)
        val cpu = surface.stats.windowCpuMs
        val behind = dropRatio > 0.10
        val heavy = cpu > interval * 0.70
        lastNote = "level $level: %.0f fps film, %d dropped in %.1f s (%.0f %%), render %.1f ms of %.1f ms".format(fps, dropped - before, secs, dropRatio * 100, cpu, interval)
        if (!(behind || heavy)) { slowWindows = 0; return }
        if (++slowWindows < 2) return
        slowWindows = 0
        if (level < MAX_LEVEL) {
            level++
            Log.w(TAG, "the video is too heavy for this PC ($lastNote): step $level")
            apply(p)
            lastDropped = -1L
            onStep(level)
        } else if (!suggested) {
            suggested = true
            Log.w(TAG, "still too heavy after the last step ($lastNote)")
            onStillSlow()
        }
    }
}
