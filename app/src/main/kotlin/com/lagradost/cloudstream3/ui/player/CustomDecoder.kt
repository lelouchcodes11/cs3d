package com.lagradost.cloudstream3.ui.player

import android.content.Context
import com.lagradost.cloudstream3.ui.subtitles.SaveCaptionStyle
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment

/** SSA/ASS "\an" alignment values and subtitle helper properties */
class CustomDecoder private constructor() {
    companion object {
        const val SSA_ALIGNMENT_BOTTOM_LEFT = 1
        const val SSA_ALIGNMENT_BOTTOM_CENTER = 2
        const val SSA_ALIGNMENT_BOTTOM_RIGHT = 3
        const val SSA_ALIGNMENT_MIDDLE_LEFT = 4
        const val SSA_ALIGNMENT_MIDDLE_CENTER = 5
        const val SSA_ALIGNMENT_MIDDLE_RIGHT = 6
        const val SSA_ALIGNMENT_TOP_LEFT = 7
        const val SSA_ALIGNMENT_TOP_CENTER = 8
        const val SSA_ALIGNMENT_TOP_RIGHT = 9

        var subtitleOffset: Long = 0
        var overrideEncoding: String? = null
        val style: SaveCaptionStyle get() = SubtitlesFragment.getCurrentSavedStyle()

        fun updateForcedEncoding(context: Context?) {}
        fun updateForcedEncoding(encoding: String?) {
            overrideEncoding = encoding
        }

        fun fixSubtitleAlignment(cues: Any?) {}

        fun androidx.media3.common.text.Cue.Builder.setSubtitleAlignment(alignment: Int?): androidx.media3.common.text.Cue.Builder = this
    }
}
