package androidx.media3.session

import android.content.Context
import androidx.media3.common.Player

class MediaSession private constructor(
    val player: Player? = null,
    val id: String = ""
) {
    val platformToken: Any? = null

    fun release() {}

    class Builder(val context: Context, val player: Player) {
        private var id: String = ""

        fun setId(id: String): Builder {
            this.id = id
            return this
        }

        fun build(): MediaSession = MediaSession(player, id)
    }
}
