package com.lagradost.cloudstream3.ui.player

import android.widget.FrameLayout
import androidx.media3.ui.SubtitleView
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.ui.subtitles.SaveCaptionStyle
import com.lagradost.cloudstream3.utils.DataStoreHelper.currentAccount
import com.lagradost.desktop.player.MpvPlayer

const val TAG = "CS3ExoPlayer"
const val PREFERRED_AUDIO_LANGUAGE_KEY = "preferred_audio_language"

/**
 * Desktop implementation of CS3IPlayer backed by MpvPlayer.
 */
open class CS3IPlayer : MpvPlayer() {
    var cacheSize = 0L
    var simpleCacheSize = 0L
    var videoBufferMs = 0L

    val imageGenerator = IPreviewGenerator.new()

    fun initSubtitles(
        subView: SubtitleView?,
        holder: FrameLayout?,
        style: SaveCaptionStyle?
    ) {
        if (style != null) {
            updateSubtitleStyle(style)
        }
    }

    companion object {
        var preferredAudioTrackLanguage: String? = null
            get() {
                return field ?: getKey<String>(
                    "$currentAccount/$PREFERRED_AUDIO_LANGUAGE_KEY",
                    field
                )?.also {
                    field = it
                }
            }
            set(value) {
                setKey("$currentAccount/$PREFERRED_AUDIO_LANGUAGE_KEY", value)
                field = value
            }
    }
}
