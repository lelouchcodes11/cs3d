package com.google.android.gms.cast.framework.media

import com.google.android.gms.cast.*
import com.google.android.gms.common.api.PendingResult
import com.google.android.gms.common.api.Status
import org.json.JSONObject

class RemoteMediaClient {
    interface MediaChannelResult {
        val status: Status
    }

    class MediaQueue {
        val itemIds: LongArray = longArrayOf()
        val itemCount: Int = 0
    }

    class CurrentItem {
        val itemId: Long = 0L
        val media: MediaInfo? = null
    }

    val mediaQueue: MediaQueue = MediaQueue()
    val currentItem: CurrentItem? = null
    val mediaInfo: MediaInfo? = null
    val mediaStatus: MediaStatus? = null
    val approximateStreamPosition: Long = 0L
    val streamDuration: Long = 0L
    val isLoadingNextItem: Boolean = false

    fun queueNext(data: JSONObject?): PendingResult<MediaChannelResult>? = null
    fun setActiveMediaTracks(ids: LongArray): PendingResult<MediaChannelResult>? = null
    fun setTextTrackStyle(style: TextTrackStyle): PendingResult<MediaChannelResult>? = null
    fun queueInsertAndPlayItem(
        item: MediaQueueItem,
        nextId: Long,
        startAt: Long,
        data: JSONObject?
    ): PendingResult<MediaChannelResult>? = null
    fun queueAppendItem(item: MediaQueueItem, data: JSONObject?): PendingResult<MediaChannelResult>? = null
    fun queueSetRepeatMode(repeatMode: Int, data: JSONObject?): PendingResult<MediaChannelResult>? = null
    fun seek(options: MediaSeekOptions): PendingResult<MediaChannelResult>? = null
    fun load(mediaInfo: MediaInfo, options: MediaLoadOptions): PendingResult<MediaChannelResult>? = null
    fun load(requestData: MediaLoadRequestData): PendingResult<MediaChannelResult>? = null
}
