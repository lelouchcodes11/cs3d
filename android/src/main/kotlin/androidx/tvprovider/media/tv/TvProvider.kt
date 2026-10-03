package androidx.tvprovider.media.tv

import android.content.Context
import android.database.Cursor
import android.net.Uri

// desktop: there is no Android TV home screen; programs are built but never published

object TvContractCompat {
    const val AUTHORITY = "android.media.tv"

    object WatchNextPrograms {
        @JvmField
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/watch_next_program")
        const val TYPE_MOVIE = 0
        const val TYPE_TV_SERIES = 1
        const val TYPE_TV_SEASON = 2
        const val TYPE_TV_EPISODE = 3
        const val TYPE_CLIP = 4
        const val WATCH_NEXT_TYPE_CONTINUE = 0
        const val WATCH_NEXT_TYPE_NEXT = 1
        const val WATCH_NEXT_TYPE_NEW = 2
        const val WATCH_NEXT_TYPE_WATCHLIST = 3
    }

    @JvmStatic
    fun buildWatchNextProgramUri(watchNextProgramId: Long): Uri =
        Uri.parse("content://$AUTHORITY/watch_next_program/$watchNextProgramId")
}

class PreviewChannelHelper(private val context: Context) {
    fun publishWatchNextProgram(program: WatchNextProgram): Long = -1L
    fun updateWatchNextProgram(upgradedProgram: WatchNextProgram, programId: Long) {}
    fun deleteWatchNextProgram(programId: Long) {}
}
