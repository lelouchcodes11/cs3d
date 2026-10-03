package android.media.audiofx

import com.lagradost.desktop.runtime.DesktopAudio

/** desktop: the gain is applied as extra player volume through DesktopAudio */
open class LoudnessEnhancer(val audioSession: Int) {
    open var enabled: Boolean = false
        set(value) {
            field = value
            DesktopAudio.boostMilliBel = if (value) gain else 0
            DesktopAudio.changed()
        }

    private var gain = 0

    open fun setTargetGain(gainmB: Int) {
        gain = gainmB
        if (enabled) {
            DesktopAudio.boostMilliBel = gainmB
            DesktopAudio.changed()
        }
    }

    open fun getTargetGain(): Float = gain.toFloat()

    open fun release() {
        if (enabled) {
            DesktopAudio.boostMilliBel = 0
            DesktopAudio.changed()
        }
    }
}
