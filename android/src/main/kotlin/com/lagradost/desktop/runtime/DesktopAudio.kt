package com.lagradost.desktop.runtime

import java.util.concurrent.CopyOnWriteArrayList

/**
 * The app's output volume: Android's STREAM_MUSIC level (AudioManager) times the LoudnessEnhancer
 * boost, as a percentage (100 = unchanged, up to 200). Players apply it to their audio output.
 */
object DesktopAudio {
    const val MAX_STEPS = 15

    @Volatile
    var musicSteps: Int = MAX_STEPS
        internal set

    @Volatile
    var boostMilliBel: Int = 0
        internal set

    private val listeners = CopyOnWriteArrayList<(Double) -> Unit>()

    /** STREAM_MUSIC level plus the loudness boost (1000 mB = +100%) */
    val volumePercent: Double
        get() = (musicSteps.toDouble() / MAX_STEPS * 100.0 + boostMilliBel / 10.0).coerceIn(0.0, 200.0)

    fun addListener(listener: (Double) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (Double) -> Unit) {
        listeners.remove(listener)
    }

    internal fun changed() {
        val v = volumePercent
        for (l in listeners) l(v)
    }
}
