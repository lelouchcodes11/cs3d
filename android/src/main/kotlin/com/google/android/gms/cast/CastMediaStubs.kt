package com.google.android.gms.cast

import android.net.Uri
import org.json.JSONObject

class MediaTrack internal constructor(
    val id: Long,
    val type: Int,
    val name: String?,
    val subtype: Int,
    val contentId: String?
) {
    companion object {
        const val TYPE_TEXT = 1
        const val SUBTYPE_SUBTITLES = 1
    }

    class Builder(private val id: Long, private val type: Int) {
        private var name: String? = null
        private var subtype: Int = 0
        private var contentId: String? = null

        fun setName(name: String?): Builder { this.name = name; return this }
        fun setSubtype(subtype: Int): Builder { this.subtype = subtype; return this }
        fun setContentId(contentId: String?): Builder { this.contentId = contentId; return this }
        fun build(): MediaTrack = MediaTrack(id, type, name, subtype, contentId)
    }
}

class MediaMetadata(val mediaType: Int) {
    private val data = mutableMapOf<String, String>()
    private val images = mutableListOf<com.google.android.gms.common.images.WebImage>()

    fun putString(key: String, value: String) { data[key] = value }
    fun getString(key: String): String? = data[key]
    fun addImage(image: com.google.android.gms.common.images.WebImage) { images.add(image) }

    companion object {
        const val MEDIA_TYPE_MOVIE = 1
        const val KEY_SUBTITLE = "com.google.android.gms.cast.metadata.SUBTITLE"
        const val KEY_TITLE = "com.google.android.gms.cast.metadata.TITLE"
    }
}

class MediaInfo internal constructor(
    val contentUrl: String?,
    val streamType: Int,
    val contentType: String?,
    val metadata: MediaMetadata?,
    val mediaTracks: List<MediaTrack>?,
    val customData: JSONObject?
) {
    val contentId: String? get() = contentUrl

    companion object {
        const val STREAM_TYPE_BUFFERED = 1
    }

    class Builder(private val contentUrl: String?) {
        private var streamType: Int = STREAM_TYPE_BUFFERED
        private var contentType: String? = null
        private var metadata: MediaMetadata? = null
        private var mediaTracks: List<MediaTrack>? = null
        private var customData: JSONObject? = null

        fun setStreamType(streamType: Int): Builder { this.streamType = streamType; return this }
        fun setContentType(contentType: String?): Builder { this.contentType = contentType; return this }
        fun setMetadata(metadata: MediaMetadata): Builder { this.metadata = metadata; return this }
        fun setMediaTracks(tracks: List<MediaTrack>): Builder { this.mediaTracks = tracks; return this }
        fun setCustomData(data: JSONObject?): Builder { this.customData = data; return this }
        fun build(): MediaInfo = MediaInfo(contentUrl, streamType, contentType, metadata, mediaTracks, customData)
    }
}

class MediaQueueItem internal constructor(val media: MediaInfo) {
    class Builder(private val media: MediaInfo) {
        fun build(): MediaQueueItem = MediaQueueItem(media)
    }
}

class MediaLoadOptions internal constructor(val playPosition: Long, val autoplay: Boolean) {
    class Builder {
        private var playPosition: Long = 0L
        private var autoplay: Boolean = true

        fun setPlayPosition(pos: Long): Builder { this.playPosition = pos; return this }
        fun setAutoplay(auto: Boolean): Builder { this.autoplay = auto; return this }
        fun build(): MediaLoadOptions = MediaLoadOptions(playPosition, autoplay)
    }
}

class MediaLoadRequestData internal constructor(val mediaInfo: MediaInfo?, val currentTime: Long) {
    class Builder {
        private var mediaInfo: MediaInfo? = null
        private var currentTime: Long = 0L

        fun setMediaInfo(info: MediaInfo?): Builder { this.mediaInfo = info; return this }
        fun setCurrentTime(time: Long): Builder { this.currentTime = time; return this }
        fun setAutoplay(auto: Boolean): Builder = this
        fun build(): MediaLoadRequestData = MediaLoadRequestData(mediaInfo, currentTime)
    }
}

class MediaSeekOptions internal constructor(val position: Long) {
    class Builder {
        private var position: Long = 0L
        fun setPosition(pos: Long): Builder { this.position = pos; return this }
        fun build(): MediaSeekOptions = MediaSeekOptions(position)
    }
}

object CastStatusCodes {
    const val SUCCESS = 0
    const val FAILED = 1
}
