package android.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.Closeable

open class MediaMetadataRetriever : Closeable, AutoCloseable {
    companion object {
        const val OPTION_PREVIOUS_SYNC = 0
        const val OPTION_NEXT_SYNC = 1
        const val OPTION_CLOSEST_SYNC = 2
        const val OPTION_CLOSEST = 3

        const val METADATA_KEY_CD_TRACK_NUMBER = 0
        const val METADATA_KEY_ALBUM = 1
        const val METADATA_KEY_ARTIST = 2
        const val METADATA_KEY_AUTHOR = 3
        const val METADATA_KEY_COMPOSER = 4
        const val METADATA_KEY_DATE = 5
        const val METADATA_KEY_TITLE = 7
        const val METADATA_KEY_DURATION = 9
        const val METADATA_KEY_NUM_TRACKS = 10
        const val METADATA_KEY_ALBUMARTIST = 13
        const val METADATA_KEY_DISC_NUMBER = 14
        const val METADATA_KEY_VIDEO_WIDTH = 18
        const val METADATA_KEY_VIDEO_HEIGHT = 19
        const val METADATA_KEY_BITRATE = 20
        const val METADATA_KEY_TIMED_TEXT_LANGUAGES = 21
        const val METADATA_KEY_IS_DRM = 22
        const val METADATA_KEY_VIDEO_ROTATION = 24
        const val METADATA_KEY_CAPTURE_FRAMERATE = 25
        const val METADATA_KEY_VIDEO_FRAME_COUNT = 32
    }

    private var duration: Long? = null
    private var width: Int? = null
    private var height: Int? = null

    open fun setDataSource(path: String) {}

    open fun setDataSource(uri: String, headers: Map<String, String>) {}

    open fun setDataSource(context: Context, uri: Uri) {}

    open fun extractMetadata(keyCode: Int): String? {
        return when (keyCode) {
            METADATA_KEY_DURATION -> duration?.toString()
            METADATA_KEY_VIDEO_WIDTH -> width?.toString()
            METADATA_KEY_VIDEO_HEIGHT -> height?.toString()
            else -> null
        }
    }

    open fun getFrameAtTime(timeUs: Long, option: Int = OPTION_CLOSEST_SYNC): Bitmap? = null

    open fun getScaledFrameAtTime(
        timeUs: Long,
        option: Int,
        dstWidth: Int,
        dstHeight: Int
    ): Bitmap? = null

    open fun release() {}

    override fun close() {
        release()
    }
}
