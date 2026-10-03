package androidx.media3.common

import android.net.Uri

object MimeTypes {
    const val APPLICATION_M3U8 = "application/x-mpegURL"
    const val APPLICATION_MPD = "application/dash+xml"
    const val VIDEO_MP4 = "video/mp4"
    const val TEXT_VTT = "text/vtt"
    const val APPLICATION_SUBRIP = "application/x-subrip"
    const val APPLICATION_MEDIA3_CUES = "application/x-media3-cues"
    const val TEXT_UNKNOWN = "text/unknown"
    const val TEXT_SSA = "text/x-ssa"
    const val APPLICATION_TTML = "application/ttml+xml"
    const val APPLICATION_MP4VTT = "application/x-mp4-vtt"
}

object C {
    const val TIME_UNSET = -Long.MAX_VALUE
    const val INDEX_UNSET = -1
    const val TRACK_TYPE_AUDIO = 1
    const val TRACK_TYPE_VIDEO = 2
    const val TRACK_TYPE_TEXT = 3
}

open class PlaybackException(
    message: String? = null,
    cause: Throwable? = null,
    val errorCode: Int = 0
) : Exception(message, cause) {
    val errorCodeName: String get() = "ERROR_CODE_$errorCode"
    companion object {
        const val ERROR_CODE_UNSPECIFIED = 0
        const val ERROR_CODE_REMOTE_ERROR = 1001
        const val ERROR_CODE_BEHIND_LIVE_WINDOW = 1002
        const val ERROR_CODE_TIMEOUT = 1003
        const val ERROR_CODE_IO_UNSPECIFIED = 2000
        const val ERROR_CODE_DRM_UNSPECIFIED = 6000
        const val ERROR_CODE_DRM_SCHEME_UNSUPPORTED = 6001
        const val ERROR_CODE_DRM_PROVISIONING_FAILED = 6002
        const val ERROR_CODE_DRM_CONTENT_ERROR = 6003
        const val ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED = 6004
        const val ERROR_CODE_IO_NETWORK_CONNECTION_FAILED = 2001
        const val ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT = 2002
        const val ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE = 2003
        const val ERROR_CODE_IO_BAD_HTTP_STATUS = 2004
        const val ERROR_CODE_IO_FILE_NOT_FOUND = 2005
        const val ERROR_CODE_IO_NO_PERMISSION = 2006
        const val ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED = 2007
        const val ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE = 2008
        const val ERROR_CODE_PARSING_CONTAINER_MALFORMED = 3001
        const val ERROR_CODE_PARSING_MANIFEST_MALFORMED = 3002
        const val ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED = 3003
        const val ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED = 3004
        const val ERROR_CODE_DECODER_INIT_FAILED = 4001
        const val ERROR_CODE_DECODER_QUERY_FAILED = 4002
        const val ERROR_CODE_DECODING_FAILED = 4003
        const val ERROR_CODE_DECODING_FORMAT_UNSUPPORTED = 4004
        const val ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES = 4005
        const val ERROR_CODE_AUDIO_TRACK_INIT_FAILED = 5001
        const val ERROR_CODE_AUDIO_TRACK_WRITE_FAILED = 5002
    }
}

class VideoSize(
    val width: Int = 0,
    val height: Int = 0,
    val unappliedRotationDegrees: Int = 0,
    val pixelWidthHeightRatio: Float = 1f,
) {
    companion object {
        @JvmField
        val UNKNOWN = VideoSize(0, 0)
    }
}

class Timeline {
    class Window {
        var durationMs: Long = 0L
        var isDynamic: Boolean = false
    }

    fun getWindow(windowIndex: Int, window: Window): Window = window
}

class MediaItem private constructor(val uri: Uri?) {
    class Builder {
        private var uri: Uri? = null

        fun setUri(uri: Uri?): Builder {
            this.uri = uri
            return this
        }

        fun setUri(uriString: String?): Builder {
            this.uri = uriString?.let { Uri.parse(it) }
            return this
        }

        fun build(): MediaItem = MediaItem(uri)
    }

    companion object {
        fun fromUri(uri: Uri): MediaItem = Builder().setUri(uri).build()
        fun fromUri(uriString: String): MediaItem = Builder().setUri(uriString).build()
    }
}

class TrackGroup(val length: Int = 0)
class TrackSelectionOverride(val trackGroup: TrackGroup, val trackIndices: List<Int>)
class Tracks(val groups: List<Any> = emptyList())

interface Player {
    val duration: Long
    val currentPosition: Long
    val isPlaying: Boolean
    val playWhenReady: Boolean
    val currentMediaItemIndex: Int
    val isCurrentMediaItemDynamic: Boolean get() = false
    val currentLiveOffset: Long get() = C.TIME_UNSET
    val bufferedPosition: Long get() = currentPosition
    val playbackState: Int get() = STATE_READY
    val videoSize: VideoSize get() = VideoSize.UNKNOWN
    val isLoading: Boolean get() = playbackState == STATE_BUFFERING
    fun seekForward() = seekTo(currentPosition + 10_000)
    fun seekBack() = seekTo(maxOf(0, currentPosition - 10_000))
    fun seekToNext() {}
    fun seekToPrevious() {}
    fun setPlayWhenReady(playWhenReady: Boolean) = if (playWhenReady) play() else pause()

    companion object {
        const val STATE_IDLE = 1
        const val STATE_BUFFERING = 2
        const val STATE_READY = 3
        const val STATE_ENDED = 4
    }

    fun seekTo(positionMs: Long)
    fun play()
    fun pause()
    fun addListener(listener: Listener)
    fun removeListener(listener: Listener)

    class PositionInfo(
        val windowIndex: Int = 0,
        val mediaItemIndex: Int = 0,
        val positionMs: Long = 0L,
        val contentPositionMs: Long = 0L,
        val adGroupIndex: Int = C.INDEX_UNSET,
        val adIndexInAdGroup: Int = C.INDEX_UNSET
    )

    class Commands

    interface Listener {
        fun onTimelineChanged(timeline: Timeline, reason: Int) {}
        fun onPositionDiscontinuity(oldPosition: PositionInfo, newPosition: PositionInfo, reason: Int) {}
        fun onIsPlayingChanged(isPlaying: Boolean) {}
        fun onPlaybackStateChanged(playbackState: Int) {}
        fun onPlayerError(error: PlaybackException) {}
        fun onVideoSizeChanged(videoSize: VideoSize) {}
        fun onTracksChanged(tracks: Tracks) {}
    }
}
