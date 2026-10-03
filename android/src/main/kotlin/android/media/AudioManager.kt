package android.media

import android.content.Context

open class AudioManager(private val context: Context? = null) {
    companion object {
        const val STREAM_VOICE_CALL = 0
        const val STREAM_SYSTEM = 1
        const val STREAM_RING = 2
        const val STREAM_MUSIC = 3
        const val STREAM_ALARM = 4
        const val STREAM_NOTIFICATION = 5
        const val ERROR = -1
        const val FLAG_SHOW_UI = 1
        const val AUDIOFOCUS_REQUEST_FAILED = 0
        const val AUDIOFOCUS_REQUEST_GRANTED = 1
        const val AUDIOFOCUS_NONE = 0
        const val AUDIOFOCUS_GAIN = 1
        const val AUDIOFOCUS_GAIN_TRANSIENT = 2
        const val AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK = 3
        const val AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE = 4
        const val AUDIOFOCUS_LOSS = -1
        const val AUDIOFOCUS_LOSS_TRANSIENT = -2
        const val AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK = -3
        const val AUDIOFOCUS_REQUEST_DELAYED = 2
    }

    fun interface OnAudioFocusChangeListener {
        fun onAudioFocusChange(focusChange: Int)
    }

    // desktop: STREAM_MUSIC is the app's player volume (DesktopAudio); other streams have no output
    open fun getStreamVolume(streamType: Int): Int = com.lagradost.desktop.runtime.DesktopAudio.musicSteps
    open fun getStreamMaxVolume(streamType: Int): Int = com.lagradost.desktop.runtime.DesktopAudio.MAX_STEPS
    open fun setStreamVolume(streamType: Int, index: Int, flags: Int) {
        if (streamType != STREAM_MUSIC) return
        com.lagradost.desktop.runtime.DesktopAudio.musicSteps = index.coerceIn(0, com.lagradost.desktop.runtime.DesktopAudio.MAX_STEPS)
        com.lagradost.desktop.runtime.DesktopAudio.changed()
    }

    open fun adjustStreamVolume(streamType: Int, direction: Int, flags: Int) =
        setStreamVolume(streamType, getStreamVolume(streamType) + direction, flags)

    open fun requestAudioFocus(listener: Any?, streamType: Int, durationHint: Int): Int = AUDIOFOCUS_REQUEST_GRANTED
    open fun requestAudioFocus(request: AudioFocusRequest): Int = AUDIOFOCUS_REQUEST_GRANTED
    open fun abandonAudioFocus(listener: Any?): Int = AUDIOFOCUS_REQUEST_GRANTED
    open fun abandonAudioFocusRequest(request: AudioFocusRequest): Int = AUDIOFOCUS_REQUEST_GRANTED
}

open class AudioFocusRequest {
    class Builder(focusGain: Int) {
        fun setAudioAttributes(attributes: AudioAttributes): Builder = this
        fun setAcceptsDelayedFocusGain(accepts: Boolean): Builder = this
        fun setWillPauseWhenDucked(pauseOnDuck: Boolean): Builder = this
        fun setOnAudioFocusChangeListener(listener: AudioManager.OnAudioFocusChangeListener): Builder = this
        fun setOnAudioFocusChangeListener(listener: AudioManager.OnAudioFocusChangeListener, handler: android.os.Handler): Builder = this
        fun build(): AudioFocusRequest = AudioFocusRequest()
    }
}

