package androidx.media3.exoplayer

import androidx.media3.common.Player

interface ExoPlayer : Player {
    val audioSessionId: Int get() = 0
}
