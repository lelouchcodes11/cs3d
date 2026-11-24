package com.lagradost.cloudstream3.utils.downloader


import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.util.Log
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKeys
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.CloudStreamApp.Companion.removeKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.IDownloadableMinimum
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.launchSafe
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.services.VideoDownloadService
import com.lagradost.cloudstream3.sortUrls
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_NAVIGATE_TO
import com.lagradost.cloudstream3.ui.player.LOADTYPE_INAPP_DOWNLOAD
import com.lagradost.cloudstream3.ui.player.RepoLinkGenerator
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.ui.player.source_priority.QualityDataHelper
import com.lagradost.cloudstream3.ui.player.source_priority.QualityDataHelper.getLinkPriority
import com.lagradost.cloudstream3.ui.result.ExtractorSubtitleLink
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment
import com.lagradost.cloudstream3.utils.AppContextUtils.createNotificationChannel
import com.lagradost.cloudstream3.utils.AppContextUtils.sortSubs
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DOWNLOAD_EPISODE_CACHE
import com.lagradost.cloudstream3.utils.DOWNLOAD_HEADER_CACHE
import com.lagradost.cloudstream3.utils.DataStore.getFolderName
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.Event
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.M3u8Helper2
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromTagToEnglishLanguageName
import com.lagradost.cloudstream3.utils.SubtitleUtils.deleteMatchingSubtitles
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.downloader.DownloadFileManagement.getBasePath
import com.lagradost.cloudstream3.utils.downloader.DownloadFileManagement.getDefaultDir
import com.lagradost.cloudstream3.utils.downloader.DownloadFileManagement.getFileName
import com.lagradost.cloudstream3.utils.downloader.DownloadFileManagement.getFolder
import com.lagradost.cloudstream3.utils.downloader.DownloadFileManagement.sanitizeFilename
import com.lagradost.cloudstream3.utils.downloader.DownloadFileManagement.toFile
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects.CreateNotificationMetadata
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects.DownloadEpisodeMetadata
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects.DownloadItem
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects.DownloadResumePackage
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects.DownloadStatus
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects.DownloadedFileInfo
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects.DownloadedFileInfoResult
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects.LazyStreamDownloadResponse
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects.StreamData
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects.DownloadQueueWrapper
import com.lagradost.cloudstream3.utils.downloader.DownloadUtils.appendAndDontOverride
import com.lagradost.cloudstream3.utils.downloader.DownloadUtils.cancel
import com.lagradost.cloudstream3.utils.downloader.DownloadUtils.downloadSubtitle
import com.lagradost.cloudstream3.utils.downloader.DownloadUtils.getEstimatedTimeLeft
import com.lagradost.cloudstream3.utils.downloader.DownloadUtils.getImageBitmapFromUrl
import com.lagradost.cloudstream3.utils.downloader.DownloadUtils.join
import com.lagradost.cloudstream3.utils.txt
import com.lagradost.safefile.SafeFile
import com.lagradost.safefile.closeQuietly
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.io.IOException
import java.io.OutputStream

const val DOWNLOAD_CHANNEL_ID = "cloudstream3.general"
const val DOWNLOAD_CHANNEL_NAME = "Downloads"
const val DOWNLOAD_CHANNEL_DESCRIPT = "The download notification channel"

object VideoDownloadManager {
    fun maxConcurrentDownloads(context: Context): Int =
        PreferenceManager.getDefaultSharedPreferences(context)
            ?.getInt(context.getString(R.string.download_parallel_key), 3) ?: 3

    private fun maxConcurrentConnections(context: Context): Int =
        PreferenceManager.getDefaultSharedPreferences(context)
            ?.getInt(context.getString(R.string.download_concurrent_key), 3) ?: 3

    private val _currentDownloads: MutableStateFlow<Set<Int>> = MutableStateFlow(emptySet())
    val currentDownloads: StateFlow<Set<Int>> = _currentDownloads

    const val TAG = "VDM"
    private const val DOWNLOAD_NOTIFICATION_TAG = "FROM_DOWNLOADER"
    /** desktop: how long a download waits for more sources after the first one came (see downloadEpisodeWithoutLinks) */
    private const val DOWNLOAD_LINK_GRACE_MS = 25_000L

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36"

    @get:DrawableRes
    val imgDone get() = R.drawable.rddone

    @get:DrawableRes
    val imgDownloading get() = R.drawable.rdload

    @get:DrawableRes
    val imgPaused get() = R.drawable.rdpause

    @get:DrawableRes
    val imgStopped get() = R.drawable.rderror

    @get:DrawableRes
    val imgError get() = R.drawable.rderror

    @get:DrawableRes
    val pressToPauseIcon get() = R.drawable.ic_baseline_pause_24

    @get:DrawableRes
    val pressToResumeIcon get() = R.drawable.ic_baseline_play_arrow_24

    @get:DrawableRes
    val pressToStopIcon get() = R.drawable.baseline_stop_24

    enum class DownloadType {
        IsPaused,
        IsDownloading,
        IsDone,
        IsFailed,
        IsStopped,
        IsPending
    }

    enum class DownloadActionType {
        Pause,
        Resume,
        Stop,
    }


    /** Invalid input, just skip to the next one as the same args will give the same error */
    private val DOWNLOAD_INVALID_INPUT =
        DownloadStatus(retrySame = false, tryNext = true, success = false)

    /** no need to try any other mirror as we have downloaded the file */
    private val DOWNLOAD_SUCCESS =
        DownloadStatus(retrySame = false, tryNext = false, success = true)

    /** the user pressed stop, so no need to download anything else */
    private val DOWNLOAD_STOPPED =
        DownloadStatus(retrySame = false, tryNext = false, success = true)

    /** the process failed due to some reason, so we retry and also try the next mirror */
    private val DOWNLOAD_FAILED = DownloadStatus(retrySame = true, tryNext = true, success = false)

    /** The download only downloaded partial. desktop: not a success. It was counted as one, so a film that broke off after 50 MB (and once
     * more on the retry) was taken as downloaded: it was never marked failed, "Try again" and the retry by itself never came, and a cut-off
     * file stayed. Now it fails, keeps its file and resumes from there; no other source is tried (it would start from the beginning) */
    private val DOWNLOAD_PARTIAL_SUCCESS =
        DownloadStatus(retrySame = true, tryNext = false, success = false)

    /** 50MB minimum size */
    const val DOWNLOAD_PARTIAL_MIN_SIZE = 1_048_576L * 50L

    /** bad config, skip all mirrors as every call to download will have the same bad config */
    private val DOWNLOAD_BAD_CONFIG =
        DownloadStatus(retrySame = false, tryNext = false, success = false)

    const val KEY_RESUME_PACKAGES = "download_resume_2"
    const val KEY_DOWNLOAD_INFO = "download_info"

    /** A key to save all the downloads which have not yet started and those currently running, using [DownloadQueueWrapper]
     * [KEY_RESUME_PACKAGES] can store keys which should not be automatically queued, unlike this key.
     */
    const val KEY_RESUME_IN_QUEUE = "download_resume_queue_key"
//    private const val KEY_RESUME_QUEUE_PACKAGES = "download_q_resume"

    val downloadStatus = HashMap<Int, DownloadType>()
    val downloadStatusEvent = Event<Pair<Int, DownloadType>>()
    val downloadDeleteEvent = Event<Int>()
    val downloadEvent = Event<Pair<Int, DownloadActionType>>()
    val downloadProgressEvent = Event<Triple<Int, Long, Long>>()
//    val downloadQueue = LinkedList<DownloadResumePackage>()

    private var hasCreatedNotChannel = false

    private fun Context.createNotificationChannel() {
        hasCreatedNotChannel = true

        this.createNotificationChannel(
            DOWNLOAD_CHANNEL_ID,
            DOWNLOAD_CHANNEL_NAME,
            DOWNLOAD_CHANNEL_DESCRIPT
        )
    }

    fun cancelAllDownloadNotifications(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.activeNotifications.forEach { notification ->
            if (notification.tag == DOWNLOAD_NOTIFICATION_TAG) {
                manager.cancel(DOWNLOAD_NOTIFICATION_TAG, notification.id)
            }
        }
    }


    /**
     * @param hlsProgress will together with hlsTotal display another notification if used, to lessen the confusion about estimated size.
     * */
    @SuppressLint("StringFormatInvalid")
    private suspend fun createDownloadNotification(
        context: Context,
        source: String?,
        linkName: String?,
        ep: DownloadEpisodeMetadata,
        state: DownloadType,
        progress: Long,
        total: Long,
        notificationCallback: (Int, Notification) -> Unit,
        hlsProgress: Long? = null,
        hlsTotal: Long? = null,
        bytesPerSecond: Long
    ): Notification? {
        try {
            if (total <= 0) return null// crash, invalid data

            val builder = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
                .setAutoCancel(true)
                .setColorized(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setColor(context.colorFromAttribute(R.attr.colorPrimary))
                .setContentTitle(ep.mainName)
                .setSmallIcon(
                    when (state) {
                        DownloadType.IsDone -> imgDone
                        DownloadType.IsDownloading -> imgDownloading
                        DownloadType.IsPaused -> imgPaused
                        DownloadType.IsFailed -> imgError
                        DownloadType.IsStopped -> imgStopped
                        DownloadType.IsPending -> imgDownloading
                    }
                )

            if (ep.sourceApiName != null) {
                builder.setSubText(ep.sourceApiName)
            }

            if (source != null) {
                val intent = Intent(context, MainActivity::class.java).apply {
                    data = source.toUri()
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val pendingIntent =
                    PendingIntentCompat.getActivity(context, 0, intent, 0, false)
                builder.setContentIntent(pendingIntent)
            }

            if (state == DownloadType.IsDownloading || state == DownloadType.IsPaused) {
                builder.setProgress((total / 1000).toInt(), (progress / 1000).toInt(), false)
            } else if (state == DownloadType.IsPending) {
                builder.setProgress(0, 0, true)
            }

            val rowTwoExtra = if (ep.name != null) " - ${ep.name}\n" else ""
            val rowTwo = if (ep.season != null && ep.episode != null) {
                "${context.getString(R.string.season_short)}${ep.season}:${context.getString(R.string.episode_short)}${ep.episode}" + rowTwoExtra
            } else if (ep.episode != null) {
                "${context.getString(R.string.episode)} ${ep.episode}" + rowTwoExtra
            } else {
                (ep.name ?: "") + ""
            }
            val downloadFormat = context.getString(R.string.download_format)

            if (SDK_INT >= Build.VERSION_CODES.O) {
                if (ep.poster != null) {
                    val poster = withContext(Dispatchers.IO) {
                        context.getImageBitmapFromUrl(ep.poster)
                    }
                    if (poster != null)
                        builder.setLargeIcon(poster)
                }

                val progressPercentage: Long
                val progressMbString: String
                val totalMbString: String
                val suffix: String

                val mbFormat = "%.1f MB"

                if (hlsProgress != null && hlsTotal != null) {
                    progressPercentage = hlsProgress * 100 / hlsTotal
                    progressMbString = hlsProgress.toString()
                    totalMbString = hlsTotal.toString()
                    suffix = " - $mbFormat".format(progress / 1000000f)
                } else {
                    progressPercentage = progress * 100 / total
                    progressMbString = mbFormat.format(progress / 1000000f)
                    totalMbString = mbFormat.format(total / 1000000f)
                    suffix = ""
                }

                val mbPerSecondString =
                    if (state == DownloadType.IsDownloading) {
                        " ($mbFormat/s)".format(bytesPerSecond.toFloat() / 1000000f)
                    } else ""

                val remainingTime =
                    if (state == DownloadType.IsDownloading) {
                        getEstimatedTimeLeft(context, bytesPerSecond, progress, total)
                    } else ""

                val bigText =
                    when (state) {
                        DownloadType.IsDownloading, DownloadType.IsPaused -> {
                            (if (linkName == null) "" else "$linkName\n") + "$rowTwo\n$progressPercentage % ($progressMbString/$totalMbString)$suffix$mbPerSecondString $remainingTime"
                        }

                        DownloadType.IsPending -> {
                            (if (linkName == null) "" else "$linkName\n") + rowTwo
                        }

                        DownloadType.IsFailed -> {
                            downloadFormat.format(
                                context.getString(R.string.download_failed),
                                rowTwo
                            )
                        }

                        DownloadType.IsDone -> {
                            downloadFormat.format(context.getString(R.string.download_done), rowTwo)
                        }

                        DownloadType.IsStopped -> {
                            downloadFormat.format(
                                context.getString(R.string.download_canceled),
                                rowTwo
                            )
                        }
                    }

                val bodyStyle = NotificationCompat.BigTextStyle()
                bodyStyle.bigText(bigText)
                builder.setStyle(bodyStyle)
            } else {
                val txt =
                    when (state) {
                        DownloadType.IsDownloading, DownloadType.IsPaused, DownloadType.IsPending -> {
                            rowTwo
                        }

                        DownloadType.IsFailed -> {
                            downloadFormat.format(
                                context.getString(R.string.download_failed),
                                rowTwo
                            )
                        }

                        DownloadType.IsDone -> {
                            downloadFormat.format(context.getString(R.string.download_done), rowTwo)
                        }

                        DownloadType.IsStopped -> {
                            downloadFormat.format(
                                context.getString(R.string.download_canceled),
                                rowTwo
                            )
                        }
                    }

                builder.setContentText(txt)
            }

            if ((state == DownloadType.IsDownloading || state == DownloadType.IsPaused || state == DownloadType.IsPending) && SDK_INT >= Build.VERSION_CODES.O) {
                val actionTypes: MutableList<DownloadActionType> = ArrayList()
                // INIT
                if (state == DownloadType.IsDownloading) {
                    actionTypes.add(DownloadActionType.Pause)
                    actionTypes.add(DownloadActionType.Stop)
                }

                if (state == DownloadType.IsPaused) {
                    actionTypes.add(DownloadActionType.Resume)
                    actionTypes.add(DownloadActionType.Stop)
                }
                if (state == DownloadType.IsPending) {
                    actionTypes.add(DownloadActionType.Stop)
                }

                // ADD ACTIONS
                for ((index, i) in actionTypes.withIndex()) {
                    val actionResultIntent = Intent(context, VideoDownloadService::class.java)

                    actionResultIntent.putExtra(
                        "type", when (i) {
                            DownloadActionType.Resume -> "resume"
                            DownloadActionType.Pause -> "pause"
                            DownloadActionType.Stop -> "stop"
                        }
                    )

                    actionResultIntent.putExtra("id", ep.id)

                    val pending: PendingIntent = PendingIntent.getService(
                        // BECAUSE episodes lying near will have the same id +1, index will give the same requested as the previous episode, *100000 fixes this
                        context, (4337 + index * 1000000 + ep.id),
                        actionResultIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    builder.addAction(
                        NotificationCompat.Action(
                            when (i) {
                                DownloadActionType.Resume -> pressToResumeIcon
                                DownloadActionType.Pause -> pressToPauseIcon
                                DownloadActionType.Stop -> pressToStopIcon
                            }, when (i) {
                                DownloadActionType.Resume -> context.getString(R.string.resume)
                                DownloadActionType.Pause -> context.getString(R.string.pause)
                                DownloadActionType.Stop -> context.getString(R.string.cancel)
                            }, pending
                        )
                    )
                }
            }

            if (!hasCreatedNotChannel) {
                context.createNotificationChannel()
            }

            val notification = builder.build()
            notificationCallback(ep.id, notification)
            with(NotificationManagerCompat.from(context)) {
                // notificationId is a unique int for each notification that you must define
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return null
                }
                notify(DOWNLOAD_NOTIFICATION_TAG, ep.id, notification)
            }
            return notification
        } catch (e: Exception) {
            logError(e)
            return null
        }
    }


    @Throws(IOException::class)
    fun setupStream(
        context: Context,
        name: String,
        folder: String?,
        extension: String,
        tryResume: Boolean,
    ): StreamData {
        return setupStream(
            context.getBasePath().first ?: getDefaultDir(context)
            ?: throw IOException("Bad config"),
            name,
            folder,
            extension,
            tryResume
        )
    }

    /**
     * Sets up the appropriate file and creates a data stream from the file.
     * Used for initializing downloads and backups.
     * */
    @Throws(IOException::class)
    fun setupStream(
        baseFile: SafeFile,
        name: String,
        folder: String?,
        extension: String,
        tryResume: Boolean,
    ): StreamData {
        val displayName = getDisplayName(name, extension)

        val subDir = baseFile.gotoDirectory(folder, createMissingDirectories = true)
            ?: throw IOException("Cant create directory")
        val foundFile = subDir.findFile(displayName)

        val (file, fileLength) = if (foundFile == null || foundFile.exists() != true) {
            subDir.createFileOrThrow(displayName) to 0L
        } else {
            if (tryResume) {
                foundFile to foundFile.lengthOrThrow()
            } else {
                foundFile.deleteOrThrow()
                subDir.createFileOrThrow(displayName) to 0L
            }
        }

        return StreamData(fileLength, file)
    }

    /** This class handles the notifications, as well as the relevant key */
    data class DownloadMetaData(
        private val id: Int?,
        private val linkHash : Int,
        var bytesDownloaded: Long = 0,
        var bytesWritten: Long = 0,

        var totalBytes: Long? = null,

        // notification metadata
        private var lastUpdatedMs: Long = 0,
        private var lastDownloadedBytes: Long = 0,
        private val createNotificationCallback: (CreateNotificationMetadata) -> Unit,

        private var internalType: DownloadType = DownloadType.IsPending,
        val isHLS : Boolean,
        // how many segments that we have downloaded
        var hlsProgress: Int = 0,
        // how many segments that exist
        var hlsTotal: Int? = null,
        // this is how many segments that has been written to the file
        // will always be <= hlsProgress as we may keep some in a buffer
        var hlsWrittenProgress: Int = 0,

        // this is used for copy with metadata on how much we have downloaded for setKey
        private var downloadFileInfoTemplate: DownloadedFileInfo? = null
    ) : Closeable {
        fun setResumeLength(length: Long) {
            bytesDownloaded = length
            bytesWritten = length
            lastDownloadedBytes = length
        }

        /** Returns the appropriate failed status based on download progress */
        fun failedStatus() = if (this.bytesWritten > DOWNLOAD_PARTIAL_MIN_SIZE)
            DOWNLOAD_PARTIAL_SUCCESS
        else
            DOWNLOAD_FAILED

        val approxTotalBytes: Long
            get() = totalBytes ?: hlsTotal?.let { total ->
                (bytesDownloaded * (total / hlsProgress.toFloat())).toLong()
            } ?: bytesDownloaded

        private var stopListener: (() -> Unit)? = null

        /** on cancel button pressed or failed invoke this once and only once */
        fun setOnStop(callback: (() -> Unit)) {
            stopListener = callback
        }

        fun removeStopListener() {
            stopListener = null
        }

        private val downloadEventListener = { event: Pair<Int, DownloadActionType> ->
            if (event.first == id) {
                when (event.second) {
                    DownloadActionType.Pause -> {
                        type = DownloadType.IsPaused
                    }

                    DownloadActionType.Stop -> {
                        type = DownloadType.IsStopped
                        stopListener?.invoke()
                        stopListener = null
                    }

                    DownloadActionType.Resume -> {
                        type = DownloadType.IsDownloading
                    }
                }
            }
        }

        private fun updateFileInfo() {
            if (id == null) return
            downloadFileInfoTemplate?.let { template ->
                /** This looks strange, but fixes an issue where we do an instant retry, and it fails immediately,
                 * eg. by turning off wifi */
                val totalBytesValue = if (approxTotalBytes <= bytesDownloaded) {
                    val prevInfo = getKey<DownloadedFileInfo>(
                        KEY_DOWNLOAD_INFO,
                        id.toString()
                    )

                    /** If this link is the same as the last cached video link metadata */
                    if (prevInfo != null && prevInfo.linkHash == linkHash) {
                        /** Try to use totalBytes if it exists, otherwise the max of the prev data,
                         * and download size to ensure total >= downloaded */
                        totalBytes ?: maxOf(prevInfo.totalBytes, bytesDownloaded)
                    } else {
                        approxTotalBytes
                    }
                } else {
                    approxTotalBytes
                }

                setKey(
                    KEY_DOWNLOAD_INFO,
                    id.toString(),
                    template.copy(
                        linkHash = linkHash,
                        totalBytes = totalBytesValue,
                        extraInfo = if (isHLS) hlsWrittenProgress.toString() else null
                    )
                )
            }
        }

        fun setDownloadFileInfoTemplate(template: DownloadedFileInfo) {
            downloadFileInfoTemplate = template
            updateFileInfo()
        }

        init {
            if (id != null) {
                downloadEvent += downloadEventListener
            }
        }

        override fun close() {
            // as we may need to resume hls downloads, we save the current written index
            if (isHLS || totalBytes == null) {
                updateFileInfo()
            }
            if (id != null) {
                downloadEvent -= downloadEventListener
                downloadStatus -= id
            }
            stopListener = null
        }

        var type
            get() = internalType
            set(value) {
                internalType = value
                notify()
            }

        fun onDelete() {
            bytesDownloaded = 0
            hlsWrittenProgress = 0
            hlsProgress = 0
            if (id != null)
                downloadDeleteEvent(id)

            //internalType = DownloadType.IsStopped
            notify()
        }

        companion object {
            const val UPDATE_RATE_MS: Long = 1000L
        }

        @JvmName("DownloadMetaDataNotify")
        private fun notify() {
            // max 10 sec between notifications, min 0.1s, this is to stop div by zero
            val dt = (System.currentTimeMillis() - lastUpdatedMs).coerceIn(100, 10000)

            val bytesPerSecond =
                ((bytesDownloaded - lastDownloadedBytes) * 1000L) / dt

            lastDownloadedBytes = bytesDownloaded
            lastUpdatedMs = System.currentTimeMillis()
            try {
                val bytes = approxTotalBytes

                // notification creation
                if (isHLS) {
                    createNotificationCallback(
                        CreateNotificationMetadata(
                            internalType,
                            bytesDownloaded,
                            bytes,
                            hlsTotal = hlsTotal?.toLong(),
                            hlsProgress = hlsProgress.toLong(),
                            bytesPerSecond = bytesPerSecond
                        )
                    )
                } else {
                    createNotificationCallback(
                        CreateNotificationMetadata(
                            internalType,
                            bytesDownloaded,
                            bytes,
                            bytesPerSecond = bytesPerSecond
                        )
                    )
                }

                // as hls has an approx file size we want to update this metadata
                if (isHLS) {
                    updateFileInfo()
                }

                if (internalType == DownloadType.IsStopped || internalType == DownloadType.IsFailed) {
                    stopListener?.invoke()
                    stopListener = null
                }

                // push all events, this *should* not crash, TODO MUTEX?
                if (id != null) {
                    downloadStatus[id] = type
                    downloadProgressEvent(Triple(id, bytesDownloaded, bytes))
                    downloadStatusEvent(id to type)
                }
            } catch (t: Throwable) {
                logError(t)
                if (BuildConfig.DEBUG) {
                    throw t
                }
            }
        }

        private fun checkNotification() {
            if (lastUpdatedMs + UPDATE_RATE_MS > System.currentTimeMillis()) return
            notify()
        }


        /** adds the length and pushes a notification if necessary */
        fun addBytes(length: Long) {
            bytesDownloaded += length
            // we don't want to update the notification after it is paused,
            // download progress may not stop directly when we "pause" it
            if (type == DownloadType.IsDownloading) checkNotification()
        }

        fun addBytesWritten(length: Long) {
            bytesWritten += length
        }

        /** adds the length + hsl progress and pushes a notification if necessary */
        fun addSegment(length: Long) {
            hlsProgress += 1
            addBytes(length)
        }

        fun setWrittenSegment(segmentIndex: Int) {
            hlsWrittenProgress = segmentIndex + 1
            // in case of abort we need to save every written progress
            updateFileInfo()
        }
    }


    data class LazyStreamDownloadData(
        private val url: String,
        private val headers: Map<String, String>,
        private val referer: String,
        /** This specifies where chunk i starts and ends,
         * bytes=${chuckStartByte[ i ]}-${chuckStartByte[ i+1 ] -1}
         * where out of bounds => bytes=${chuckStartByte[ i ]}- */
        private val chuckStartByte: LongArray,
        val totalLength: Long?,
        val downloadLength: Long?,
        val chuckSize: Long,
        val bufferSize: Int,
        val isResumed: Boolean,
    ) {
        val size get() = chuckStartByte.size

        /** desktop: why the last piece could not be fetched (an HTTP status), for the reason the download failed */
        @Volatile
        var lastError: String? = null

        /** returns what byte it has downloaded,
         * so start at 10 and download 4 bytes = return 14
         *
         * the range is [startByte, endByte) to be able to do [a, b) [b, c) ect
         *
         * [a, null) will return inclusive to eof = [a, eof]
         *
         * throws an error if initial get request fails, can be specified as return startByte
         * */
        @Throws
        private suspend fun resolve(
            startByte: Long,
            endByte: Long?,
            buffer: ByteArray,
            callback: (suspend CoroutineScope.(LazyStreamDownloadResponse) -> Unit)
        ): Long = withContext(Dispatchers.IO) {
            var currentByte: Long = startByte
            val stopAt = endByte ?: Long.MAX_VALUE
            if (currentByte >= stopAt) return@withContext currentByte

            val request = app.get(
                url,
                headers = headers + mapOf(
                    // range header is inclusive so [startByte, endByte-1] = [startByte, endByte)
                    // if nothing at end the server will continue until eof
                    "Range" to "bytes=$startByte-" // ${endByte?.minus(1)?.toString() ?: "" }
                ),
                referer = referer,
                verify = false
            )
            // desktop: what the server answered was written as the video whatever it was: an expired link's error page (403, 404) went into the
            // middle of the file, and a server that sends the whole file (200) to a request from the middle had its first bytes written where
            // the middle belongs. 416: nothing is left after startByte (a whole file being resumed)
            val code = request.code
            if (code == 416) {
                request.body.closeQuietly()
                return@withContext currentByte
            }
            if (code !in 200..299) {
                request.body.closeQuietly()
                throw IOException("HTTP $code")
            }
            val requestStream = request.body.byteStream()

            var read = 0

            try {
                if (code != 206 && startByte > 0) {
                    var skip = startByte
                    while (skip > 0 && isActive) {
                        val n = requestStream.read(buffer, 0, minOf(bufferSize.toLong(), skip).toInt())
                        if (n < 0) break
                        skip -= n
                    }
                    if (skip > 0) return@withContext currentByte
                }
                // desktop: isActive: a stopped download read on to the end of the file (the read does not notice the stop), so a stopped film went
                // on downloading in the background and its place in the queue stayed taken until it had all come
                while (isActive && requestStream.read(buffer, 0, bufferSize).also { read = it } >= 0) {
                    // desktop: Settings > Downloads > "Download speed limit" (shared by every download)
                    com.lagradost.desktop.net.DownloadThrottle.take(read)
                    val start = currentByte
                    currentByte += read.toLong()

                    // this stops overflow
                    if (currentByte >= stopAt) {
                        callback(LazyStreamDownloadResponse(buffer, start, stopAt))
                        break
                    } else {
                        callback(LazyStreamDownloadResponse(buffer, start, currentByte))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                logError(t)
            } finally {
                requestStream.closeQuietly()
            }

            return@withContext currentByte
        }

        /** retries the resolve n times and returns true if successful */
        suspend fun resolveSafe(
            index: Int,
            retries: Int = 3,
            buffer: ByteArray,
            callback: (suspend CoroutineScope.(LazyStreamDownloadResponse) -> Unit)
        ): Boolean {
            var start = chuckStartByte.getOrNull(index) ?: return false
            val end = chuckStartByte.getOrNull(index + 1)

            for (i in 0 until retries) {
                try {
                    // in case
                    start = resolve(start, end, buffer, callback)
                    // no end defined, so we don't care exactly where it ended
                    if (end == null) return true
                    // we have download more or exactly what we needed
                    if (start >= end) return true
                } catch (_: IllegalStateException) {
                    return false
                } catch (_: CancellationException) {
                    return false
                } catch (t: Throwable) {
                    lastError = t.message ?: t.javaClass.simpleName
                    continue
                }
            }
            return false
        }
    }

    @Throws
    suspend fun streamLazy(
        url: String,
        headers: Map<String, String>,
        referer: String,
        startByte: Long,
        /** how many bytes every connection should be, by default it is 10 MiB */
        chuckSize: Long = (1 shl 20) * 10,
        /** maximum bytes in the buffer that responds */
        bufferSize: Int = DEFAULT_BUFFER_SIZE,
        /** how many bytes bytes it should require to use the parallel downloader instead,
         * if we download a very small file we don't want it parallel */
        maximumSmallSize: Long = chuckSize * 2
    ): LazyStreamDownloadData {
        // we don't want to make a separate connection for every 1kb
        require(chuckSize > 1000)

        val headRequest = app.head(url = url, headers = headers, referer = referer, verify = false)
        var contentLength = headRequest.size
        if (contentLength != null && contentLength <= 0) contentLength = null

        val hasRangeSupport = when (headRequest.headers["Accept-Ranges"]?.lowercase()?.trim()) {
            // server has stated it has no support
            "none" -> false
            // server has stated it has support
            "bytes" -> true
            // if null or undefined (as bytes is the only range unit formally defined)
            // If the get request returns partial content we support range
            else -> {
                headRequest.headers["Accept-Ranges"]?.let { range ->
                    Log.v(TAG, "Unknown Accept-Ranges tag: $range")
                }
                // as we don't poll the body this should be fine
                val getRequest = app.get(
                    url,
                    headers = headers + mapOf(
                        "Range" to "bytes=0-${
                            // we don't want to request more than the actual file
                            // but also more than 0 bytes
                            contentLength?.let { max ->
                                minOf(maxOf(max - 1L, 3L), 1023L)
                            } ?: 1023L
                        }"
                    ),
                    referer = referer,
                    verify = false
                )
                // if head request did not work then we can just look for the size here too
                // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Range
                if (contentLength == null) {
                    contentLength =
                        getRequest.headers["Content-Range"]?.trim()?.lowercase()?.let { range ->
                            // we only support "bytes" unit
                            if (range.startsWith("bytes")) {
                                // may be '*' if unknown
                                range.substringAfter("/").toLongOrNull()
                            } else {
                                Log.v(TAG, "Unknown Content-Range unit: $range")
                                null
                            }
                        }
                }

                // supports range if status is partial content https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/206
                getRequest.code == 206
            }
        }

        Log.d(
            TAG,
            "Starting stream with url=$url, startByte=$startByte, contentLength=$contentLength, hasRangeSupport=$hasRangeSupport"
        )

        var downloadLength: Long? = null

        val ranges = if (!hasRangeSupport) {
            // is the equivalent of [0..EOF] as we cant resume, nor can parallelize it
            downloadLength = contentLength
            LongArray(1) { 0 }
        } else if (contentLength == null || contentLength < maximumSmallSize) {
            if (contentLength != null) {
                downloadLength = contentLength - startByte
            }
            // is the equivalent of [startByte..EOF] as we don't know the size we can only do one
            // connection
            LongArray(1) { startByte }
        } else {
            downloadLength = contentLength - startByte
            // div with ceiling as
            // this makes the last part "unknown ending" and it will break at EOF
            // so eg startByte = 0, downloadLength = 13, chuckSize = 10
            // = LongArray(2) { 0, 10 } = [0,10) + [10..EOF]
            LongArray(((downloadLength + chuckSize - 1) / chuckSize).toInt()) { idx ->
                startByte + idx * chuckSize
            }
        }

        return LazyStreamDownloadData(
            url = url,
            headers = headers,
            referer = referer,
            chuckStartByte = ranges,
            downloadLength = downloadLength,
            totalLength = contentLength,
            chuckSize = chuckSize,
            bufferSize = bufferSize,
            // we have only resumed if we had a downloaded file and we can resume
            isResumed = startByte > 0 && hasRangeSupport
        )
    }


    /** download a file that consist of a single stream of data*/
    suspend fun downloadThing(
        context: Context,
        link: IDownloadableMinimum,
        name: String,
        folder: String,
        extension: String,
        tryResume: Boolean,
        parentId: Int?,
        createNotificationCallback: (CreateNotificationMetadata) -> Unit,
        parallelConnections: Int = 3,
        /** how many bytes a valid file must be in bytes,
         * this should be different for subtitles and video */
        minimumSize: Long = 100
    ): DownloadStatus = withContext(Dispatchers.IO) {
        if (parallelConnections < 1) {
            return@withContext DOWNLOAD_INVALID_INPUT
        }

        var fileStream: OutputStream? = null
        //var requestStream: InputStream? = null
        val metadata = DownloadMetaData(
            totalBytes = 0,
            bytesDownloaded = 0,
            createNotificationCallback = createNotificationCallback,
            id = parentId,
            linkHash = link.url.hashCode(),
            isHLS = false
        )
        try {
            // get the file path
            val (baseFile, basePath) = context.getBasePath()
            val displayName = getDisplayName(name, extension)
            if (baseFile == null) return@withContext DOWNLOAD_BAD_CONFIG

            // set up the download file
            var stream = setupStream(baseFile, name, folder, extension, tryResume)

            fileStream = stream.open()

            metadata.setResumeLength(stream.startAt)
            metadata.type = DownloadType.IsPending

            val items = streamLazy(
                url = link.url.replace(" ", "%20"),
                referer = link.referer,
                startByte = stream.startAt,
                headers = link.headers.appendAndDontOverride(
                    mapOf(
                        "user-agent" to USER_AGENT,
                    )
                )
            )

            // too short file, treat it as a invalid link
            if (items.totalLength != null && items.totalLength < minimumSize) {
                fileStream.closeQuietly()
                metadata.onDelete()
                stream.delete()
                return@withContext DOWNLOAD_INVALID_INPUT
            }

            // if we have an output stream that cant be resumed then we delete the entire file
            // and set up the stream again
            if (!items.isResumed && stream.startAt > 0) {
                fileStream.closeQuietly()
                stream.delete()
                metadata.setResumeLength(0)
                stream = setupStream(baseFile, name, folder, extension, false)
                fileStream = stream.open()
            }

            metadata.totalBytes = items.totalLength

            // desktop: a film bigger than the room left on the disk downloaded for an hour and then failed with the disk full (and left
            // other programs without room). Its size is known now: refused at once, with how much is needed (a little room is kept free)
            val total = items.totalLength ?: 0L
            val need = total - (if (items.isResumed && stream.startAt in 1 until total) stream.startAt else 0L)
            val free = runCatching { stream.file.filePath()?.let { java.io.File(it).absoluteFile.parentFile?.usableSpace } }.getOrNull()
            if (need > 0 && free != null && free > 0 && free < need + DISK_SPARE) {
                lastFailureReason = "not enough space: ${gb(need)} needed, ${gb(free)} free"
                Log.w(TAG, lastFailureReason!!)
                // the empty file it made is not left behind (a resumed one keeps what it has)
                if (stream.startAt == 0L) {
                    fileStream.closeQuietly()
                    stream.delete()
                }
                metadata.type = DownloadType.IsFailed
                return@withContext metadata.failedStatus()
            }

            metadata.type = DownloadType.IsDownloading
            metadata.setDownloadFileInfoTemplate(
                DownloadedFileInfo(
                    totalBytes = metadata.approxTotalBytes,
                    relativePath = folder,
                    displayName = displayName,
                    basePath = basePath
                )
            )

            val currentMutex = Mutex()
            val current = (0 until items.size).iterator()

            val fileMutex = Mutex()
            // start to data
            val pendingData: HashMap<Long, LazyStreamDownloadResponse> =
                hashMapOf()

            val fileChecker = launch(Dispatchers.IO) {
                while (isActive) {
                    if (stream.exists) {
                        delay(5000)
                        continue
                    }
                    fileMutex.withLock {
                        metadata.type = DownloadType.IsStopped
                    }
                    break
                }
            }

            val jobs = (0 until parallelConnections).map {
                launch(Dispatchers.IO) {

                    // @downloadexplanation
                    // this may seem a bit complex but it more or less acts as a queue system
                    // imagine we do the downloading [0,3] and it response in the order 0,2,3,1
                    // file: [_,_,_,_] queue: [_,_,_,_] Initial condition
                    // file: [X,_,_,_] queue: [_,_,_,_] + added 0 directly to file
                    // file: [X,_,_,_] queue: [_,_,X,_] + added 2 to queue
                    // file: [X,_,_,_] queue: [_,_,X,X] + added 3 to queue
                    // file: [X,X,_,_] queue: [_,_,X,X] + added 1 directly to file
                    // file: [X,X,X,X] queue: [_,_,_,_] write the queue and remove from it

                    // note that this is a bit more complex compared to hsl as ever segment
                    // will return several bytearrays, and is therefore chained by the byte
                    // so every request has a front and back byte instead of an index
                    // this *requires* that no gap exist due because of resolve
                    val callback: (suspend CoroutineScope.(LazyStreamDownloadResponse) -> Unit) =
                        callback@{ response ->
                            if (!isActive) return@callback
                            fileMutex.withLock {
                                // wait until not paused
                                while (metadata.type == DownloadType.IsPaused) delay(100)
                                // if stopped then throw
                                if (metadata.type == DownloadType.IsStopped || metadata.type == DownloadType.IsFailed) {
                                    this.cancel()
                                    return@callback
                                }

                                val responseSize = response.size
                                metadata.addBytes(response.size)

                                // desktop: a write that failed (the disk full) was taken for a network error and the bytes were skipped: every
                                // later piece then waited for them, and the download hung at "Downloading" for ever (or, in one connection, a
                                // cut-off file was marked done). Now it fails and says why
                                try {
                                    if (response.startByte == metadata.bytesWritten) {
                                        // if we are first in the queue then write it directly
                                        fileStream.write(
                                            response.bytes,
                                            0,
                                            responseSize.toInt()
                                        )
                                        metadata.addBytesWritten(responseSize)
                                    } else {
                                        // otherwise append to queue, we need to clone the bytes as they will be overridden otherwise
                                        pendingData[response.startByte] =
                                            response.copy(bytes = response.bytes.clone())
                                    }

                                    while (true) {
                                        // remove the current queue start, so no possibility of
                                        // while(true) { continue } in case size = 0, and removed extra
                                        // garbage
                                        val pending = pendingData.remove(metadata.bytesWritten) ?: break

                                        val size = pending.size

                                        fileStream.write(
                                            pending.bytes,
                                            0,
                                            size.toInt()
                                        )
                                        metadata.addBytesWritten(size)
                                    }
                                } catch (e: IOException) {
                                    logError(e)
                                    lastFailureReason = e.message
                                    metadata.type = DownloadType.IsFailed
                                    this.cancel()
                                    return@callback
                                }
                            }
                        }

                    // Reuse a download buffer to decrease unnecessary alloc
                    val buffer = ByteArray(items.bufferSize)

                    // This will take up the first available job and resolve
                    while (true) {
                        if (!isActive) return@launch

                        var isTooFarAhead = false
                        fileMutex.withLock {
                            if (metadata.type == DownloadType.IsStopped
                                || metadata.type == DownloadType.IsFailed
                            ) return@launch

                            // Limit RAM usage by throttling if too much data is downloaded but not yet written to disk
                            // 50MB limit
                            if (metadata.bytesDownloaded - metadata.bytesWritten > 50_000_000) {
                                isTooFarAhead = true
                            }
                        }

                        if (isTooFarAhead) {
                            delay(500)
                            continue
                        }

                        // mutex just in case, we never want this to fail due to multithreading
                        val index = currentMutex.withLock {
                            if (!current.hasNext()) return@launch
                            current.nextInt()
                        }

                        // in case something has gone wrong set to failed if the fail is not caused by
                        // user cancellation
                        if (!items.resolveSafe(index, buffer = buffer, callback = callback)) {
                            fileMutex.withLock {
                                if (metadata.type != DownloadType.IsStopped) {
                                    if (lastFailureReason == null) lastFailureReason = items.lastError ?: "a piece could not be fetched"
                                    metadata.type = DownloadType.IsFailed
                                }
                            }
                            return@launch
                        }
                    }
                }
            }

            // fast stop as the jobs may be in a slow request
            metadata.setOnStop {
                jobs.cancel()
            }

            jobs.join()
            fileChecker.cancel()

            // jobs are finished so we don't want to stop them anymore
            metadata.removeStopListener()
            if (!stream.exists) metadata.type = DownloadType.IsStopped

            if (metadata.type == DownloadType.IsFailed) {
                return@withContext metadata.failedStatus()
            }

            if (metadata.type == DownloadType.IsStopped) {
                // we need to close before delete
                fileStream.closeQuietly()
                metadata.onDelete()
                stream.delete()
                return@withContext DOWNLOAD_STOPPED
            }

            // in case the head request lies about content-size,
            // then we don't want shit output
            if (metadata.bytesDownloaded < minimumSize) {
                // we need to close before delete
                fileStream.closeQuietly()
                metadata.onDelete()
                stream.delete()
                return@withContext DOWNLOAD_INVALID_INPUT
            }

            // desktop: a connection that broke off in the last (open-ended) piece ended the read like the end of the file did, and a cut-off film was
            // marked done. Short of the size the server announced (a little slack for servers that round it): failed, and resume carries on from here.
            val announced = metadata.totalBytes
            if (announced != null && announced > 0 && metadata.bytesWritten < announced - maxOf(announced / 100, 1_000_000L)) {
                Log.w(TAG, "download ended at ${metadata.bytesWritten} of $announced bytes: kept as failed, not done")
                lastFailureReason = "cut off"
                metadata.type = DownloadType.IsFailed
                return@withContext metadata.failedStatus()
            }

            metadata.type = DownloadType.IsDone
            return@withContext DOWNLOAD_SUCCESS
        } catch (e: IOException) {
            // some sort of IO error, this should not happened
            // we just rethrow it
            logError(e)
            throw e
        } catch (t: Throwable) {
            // some sort of network error, will error
            logError(t)
            // note that when failing we don't want to delete the file,
            // only user interaction has that power
            lastFailureReason = t.message ?: t.javaClass.simpleName
            metadata.type = DownloadType.IsFailed
            return@withContext metadata.failedStatus()
        } finally {
            fileStream?.closeQuietly()
            //requestStream?.closeQuietly()
            metadata.close()
        }
    }

    private suspend fun downloadHLS(
        context: Context,
        link: ExtractorLink,
        name: String,
        folder: String,
        parentId: Int?,
        startIndex: Int?,
        createNotificationCallback: (CreateNotificationMetadata) -> Unit,
        parallelConnections: Int = 3
    ): DownloadStatus = withContext(Dispatchers.IO) {
        if (parallelConnections < 1) return@withContext DOWNLOAD_INVALID_INPUT

        val metadata = DownloadMetaData(
            createNotificationCallback = createNotificationCallback,
            id = parentId,
            linkHash = link.url.hashCode(),
            isHLS = true
        )
        var fileStream: OutputStream? = null
        // desktop: DASH (picture and sound are separate files by design) is recorded by mpv into one Matroska file
        if (link.type == ExtractorLinkType.DASH) {
            return@withContext try {
                recordSeparateAudio(context, link, name, folder, context.getBasePath().second, metadata)
            } catch (t: Throwable) {
                logError(t)
                lastFailureReason = t.message
                metadata.type = DownloadType.IsFailed
                DOWNLOAD_FAILED
            } finally {
                metadata.close()
            }
        }
        try {
            val extension = "mp4"

            // the start .ts index
            var startAt = startIndex ?: 0

            // set up the file data
            val (baseFile, basePath) = context.getBasePath()
            if (baseFile == null) return@withContext DOWNLOAD_BAD_CONFIG

            val displayName = getDisplayName(name, extension)
            val stream =
                setupStream(baseFile, name, folder, extension, startAt > 0)

            if (!stream.resume) startAt = 0
            fileStream = stream.open()

            // push the metadata
            metadata.setResumeLength(stream.startAt)
            metadata.hlsProgress = startAt
            metadata.hlsWrittenProgress = startAt
            metadata.type = DownloadType.IsPending
            metadata.setDownloadFileInfoTemplate(
                DownloadedFileInfo(
                    totalBytes = 0,
                    relativePath = folder,
                    displayName = displayName,
                    basePath = basePath
                )
            )

            // do the initial get request to fetch the segments
            val m3u8 = M3u8Helper.M3u8Stream(
                link.url, link.quality, link.headers.appendAndDontOverride(
                    mapOf(
                        "user-agent" to USER_AGENT,
                    ) + if (link.referer.isNotBlank()) mapOf("referer" to link.referer) else emptyMap()
                )
            )

            val items = try {
                M3u8Helper2.hslLazy(m3u8, selectBest = true, requireAudio = true)
            } catch (t: Throwable) {
                // desktop: the sound is a stream of its own (CNCVerse titles): joining the picture segments would give a film without sound.
                // mpv records picture and sound together into a Matroska file instead
                if (t.message?.contains("no video with audio", ignoreCase = true) != true) throw t
                fileStream.closeQuietly()
                stream.delete()
                fileStream = null
                return@withContext recordSeparateAudio(context, link, name, folder, basePath, metadata)
            }

            metadata.hlsTotal = items.size
            metadata.type = DownloadType.IsDownloading

            val currentMutex = Mutex()
            val current = (startAt until items.size).iterator()

            val fileMutex = Mutex()
            val pendingData: HashMap<Int, ByteArray> = hashMapOf()

            val fileChecker = launch(Dispatchers.IO) {
                while (isActive) {
                    if (stream.exists) {
                        delay(5000)
                        continue
                    }
                    fileMutex.withLock {
                        metadata.type = DownloadType.IsStopped
                    }
                    break
                }
            }

            // see @downloadexplanation for explanation of this download strategy,
            // this keeps all jobs working at all times,
            // does several connections in parallel instead of a regular for loop to improve
            // download speed
            val jobs = (0 until parallelConnections).map {
                launch(Dispatchers.IO) {
                    while (true) {
                        if (!isActive) return@launch

                        var isTooFarAhead = false
                        fileMutex.withLock {
                            if (metadata.type == DownloadType.IsStopped
                                || metadata.type == DownloadType.IsFailed
                            ) return@launch

                            // Limit RAM usage by throttling if too much data is downloaded but not yet written to disk
                            // 50MB limit
                            if (metadata.bytesDownloaded - metadata.bytesWritten > 50_000_000) {
                                isTooFarAhead = true
                            }
                        }

                        if (isTooFarAhead) {
                            delay(500)
                            continue
                        }

                        // mutex just in case, we never want this to fail due to multithreading
                        val index = currentMutex.withLock {
                            if (!current.hasNext()) return@launch
                            current.nextInt()
                        }

                        // in case something has gone wrong set to failed if the fail is not caused by
                        // user cancellation
                        val bytes = items.resolveLinkSafe(index) ?: run {
                            fileMutex.withLock {
                                if (metadata.type != DownloadType.IsStopped) {
                                    if (lastFailureReason == null) lastFailureReason = "a piece could not be fetched"
                                    metadata.type = DownloadType.IsFailed
                                }
                            }
                            return@launch
                        }
                        // desktop: the speed limit counts the segments of a stream too (outside the lock: the other segments go on meanwhile)
                        com.lagradost.desktop.net.DownloadThrottle.take(bytes.size)

                        fileMutex.withLock {
                            try {
                                // user pause
                                while (metadata.type == DownloadType.IsPaused) delay(100)
                                // if stopped then break to delete
                                if (metadata.type == DownloadType.IsStopped || metadata.type == DownloadType.IsFailed || !isActive) return@launch

                                val segmentLength = bytes.size.toLong()
                                // send notification, no matter the actual write order
                                metadata.addSegment(segmentLength)

                                // directly write the bytes if you are first
                                if (metadata.hlsWrittenProgress == index) {
                                    fileStream.write(bytes)

                                    metadata.addBytesWritten(segmentLength)
                                    metadata.setWrittenSegment(index)
                                } else {
                                    // no need to clone as there will be no modification of this bytearray
                                    pendingData[index] = bytes
                                }

                                // write the cached bytes submitted by other threads
                                while (true) {
                                    val cache =
                                        pendingData.remove(metadata.hlsWrittenProgress) ?: break
                                    val cacheLength = cache.size.toLong()

                                    fileStream.write(cache)

                                    metadata.addBytesWritten(cacheLength)
                                    metadata.setWrittenSegment(metadata.hlsWrittenProgress)
                                }
                            } catch (t: Throwable) {
                                // this is in case of write fail
                                logError(t)
                                lastFailureReason = t.message
                                if (metadata.type != DownloadType.IsStopped) {
                                    metadata.type = DownloadType.IsFailed
                                }
                            }
                        }
                    }
                }
            }

            // fast stop as the jobs may be in a slow request
            metadata.setOnStop {
                jobs.cancel()
            }

            jobs.join()
            fileChecker.cancel()

            metadata.removeStopListener()

            if (!stream.exists) metadata.type = DownloadType.IsStopped

            if (metadata.type == DownloadType.IsFailed) {
                return@withContext metadata.failedStatus()
            }

            if (metadata.type == DownloadType.IsStopped) {
                // we need to close before delete
                fileStream.closeQuietly()
                metadata.onDelete()
                stream.delete()
                return@withContext DOWNLOAD_STOPPED
            }

            metadata.type = DownloadType.IsDone
            return@withContext DOWNLOAD_SUCCESS
        } catch (t: Throwable) {
            logError(t)
            lastFailureReason = t.message
            metadata.type = DownloadType.IsFailed
            return@withContext metadata.failedStatus()
        } finally {
            fileStream?.closeQuietly()
            metadata.close()
        }
    }

    /**
     * desktop: an HLS video whose sound is a separate stream, saved by [com.lagradost.desktop.player.MpvRecorder] into "name.mkv". Its progress goes
     * through the same counters as the segments of an HLS download (1000 steps of the length), so the Downloads page shows it like any other;
     * it starts from the beginning when it is resumed.
     */
    private suspend fun recordSeparateAudio(
        context: Context,
        link: ExtractorLink,
        name: String,
        folder: String,
        basePath: String?,
        metadata: DownloadMetaData,
    ): DownloadStatus = withContext(Dispatchers.IO) {
        val (baseFile, _) = context.getBasePath()
        if (baseFile == null) return@withContext DOWNLOAD_BAD_CONFIG
        val stream = setupStream(baseFile, name, folder, "mkv", false)
        val path = stream.file.filePath() ?: return@withContext DOWNLOAD_BAD_CONFIG
        val out = java.io.File(path)
        val steps = 1000
        metadata.hlsTotal = steps
        metadata.hlsProgress = 0
        metadata.hlsWrittenProgress = 0
        metadata.setDownloadFileInfoTemplate(DownloadedFileInfo(totalBytes = 0, relativePath = folder, displayName = getDisplayName(name, "mkv"), basePath = basePath))
        metadata.type = DownloadType.IsDownloading
        var stopAsked = false
        metadata.setOnStop { stopAsked = true }
        var lastBytes = 0L
        val result = com.lagradost.desktop.player.MpvRecorder.record(link, out, progress = { done, total ->
            if (total > 0) {
                val step = ((done / total) * steps).toInt().coerceIn(0, steps)
                while (metadata.hlsProgress < step) {
                    val now = out.length()
                    val grew = (now - lastBytes).coerceAtLeast(0)
                    lastBytes = now
                    metadata.addSegment(grew)
                    metadata.addBytesWritten(grew)
                    metadata.setWrittenSegment(metadata.hlsProgress - 1)
                }
            }
        }, control = {
            when {
                stopAsked || metadata.type == DownloadType.IsStopped -> "stop"
                metadata.type == DownloadType.IsPaused -> "pause"
                else -> "run"
            }
        })
        metadata.removeStopListener()
        when {
            result.ok -> { metadata.type = DownloadType.IsDone; DOWNLOAD_SUCCESS }
            result.stopped -> { metadata.onDelete(); runCatching { stream.delete() }; DOWNLOAD_STOPPED }
            else -> {
                android.util.Log.w("DownloadManager", "separate-audio download failed: ${result.reason}")
                lastFailureReason = result.reason
                runCatching { stream.delete() }
                metadata.type = DownloadType.IsFailed
                DOWNLOAD_FAILED
            }
        }
    }

    /** desktop: why the last stream download gave up (read once by the clean-up of the failed download, for the message) */
    @Volatile
    private var lastFailureReason: String? = null

    /** desktop: why each download failed, in plain words, for its row on the Downloads page (it only said "failed: press try again") */
    val failureReasons = java.util.concurrent.ConcurrentHashMap<Int, String>()

    /** desktop: how often each download was tried again by itself after its connection broke off (this run) */
    private val autoRetries = java.util.concurrent.ConcurrentHashMap<Int, Int>()
    private const val AUTO_RETRIES = 2

    /** desktop: room left free on the disk by a download (Windows and other programs need some) */
    private const val DISK_SPARE = 500L * 1024 * 1024

    private fun gb(bytes: Long) = if (bytes >= 1024L * 1024 * 1024) "%.1f GB".format(java.util.Locale.ROOT, bytes / 1073741824.0) else "${bytes / 1048576} MB"

    internal fun isConnectionDrop(reason: String?) = failureText(reason).startsWith("the connection kept")

    /** desktop: the downloader's own reason (an exception message, a note) in plain words */
    internal fun failureText(reason: String?): String {
        val r = reason?.lowercase() ?: return "no source could be saved"
        return when {
            "no video with audio" in r -> "this source sends picture and sound as separate streams, which can not be saved yet"
            r.startsWith("not enough space:") -> "there is not enough room on the disk (" + reason.substringAfter(':').trim() + ")"
            "not enough space" in r || "no space left" in r || "disk is full" in r -> "the disk is full"
            "access is denied" in r || "permission denied" in r -> "the download folder can not be written to"
            "no links" in r -> "no source was found for it"
            Regex("""\b40[13]\b""").containsMatchIn(r) || "forbidden" in r || "unauthorized" in r -> "the source refused it (its link may have expired)"
            Regex("""\b404\b""").containsMatchIn(r) || "not found" in r -> "the file is no longer on the source"
            Regex("""\b5\d\d\b""").containsMatchIn(r) -> "the source's server had an error"
            "cut off" in r || "timeout" in r || "timed out" in r || "reset" in r || "closed" in r || "unexpected end" in r || "piece could not" in r ->
                "the connection kept breaking off (try again carries on where it stopped)"
            "unknownhost" in r || "unable to resolve" in r || "no address" in r -> "the source's address could not be found (offline?)"
            else -> "no source could be saved"
        }
    }

    private fun getDisplayName(name: String, extension: String): String {
        return "$name.$extension"
    }

    private suspend fun downloadSingleEpisode(
        context: Context,
        source: String?,
        folder: String?,
        ep: DownloadEpisodeMetadata,
        link: ExtractorLink,
        notificationCallback: (Int, Notification) -> Unit,
        tryResume: Boolean = false,
    ): DownloadStatus {
        // no support for these file formats
        // desktop: a DASH link without DRM is saved by mpv (see recordSeparateAudio), through downloadHLS
        // desktop: a torrent is saved from the torrent engine's address on this PC (the episode asked for, when it is a season pack), read like a
        // file on the web; the torrent is let go of when the download ends
        if (link.type == ExtractorLinkType.MAGNET || link.type == ExtractorLinkType.TORRENT) {
            // desktop: the engine can wait up to two minutes for peers. A download cancelled in that time started anyway once they answered
            // (or went on to the next source when they did not), with nothing on screen to stop it
            var stopped = false
            val stopListener = { event: Pair<Int, DownloadActionType> ->
                if (event.first == ep.id && event.second == DownloadActionType.Stop) stopped = true
            }
            downloadEvent += stopListener
            val local = try {
                com.lagradost.desktop.torrent.TorrentEngine.downloadLink(link)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                android.util.Log.w("DownloadManager", "torrent download not started: ${t.message}")
                return if (stopped) DOWNLOAD_STOPPED else DOWNLOAD_INVALID_INPUT
            } finally {
                downloadEvent -= stopListener
            }
            if (stopped) {
                com.lagradost.desktop.torrent.TorrentEngine.downloadDone(local)
                return DOWNLOAD_STOPPED
            }
            return try {
                downloadSingleEpisode(context, source, folder, ep, local, notificationCallback, tryResume)
            } finally {
                com.lagradost.desktop.torrent.TorrentEngine.downloadDone(local)
            }
        }
        if (link.type == ExtractorLinkType.DASH && link is com.lagradost.cloudstream3.utils.DrmExtractorLink) {
            return DOWNLOAD_INVALID_INPUT
        }

        val name = claimFileName(context, ep.id, folder ?: "", getFileName(context, ep))

        // Make sure this is cancelled when download is done or cancelled.
        val extractorJob = ioSafe {
            if (link.extractorData != null) {
                getApiFromNameNull(link.source)?.extractorVerifierJob(link.extractorData)
            }
        }

        val callback: (CreateNotificationMetadata) -> Unit = { meta ->
            main {
                createDownloadNotification(
                    context,
                    source,
                    link.name,
                    ep,
                    meta.type,
                    meta.bytesDownloaded,
                    meta.bytesTotal,
                    notificationCallback,
                    meta.hlsProgress,
                    meta.hlsTotal,
                    meta.bytesPerSecond
                )
            }
        }

        try {
            when (link.type) {
                ExtractorLinkType.M3U8, ExtractorLinkType.DASH -> {
                    val startIndex = if (tryResume) {
                        context.getKey<DownloadedFileInfo>(
                            KEY_DOWNLOAD_INFO,
                            ep.id.toString(),
                            null
                        )?.extraInfo?.toIntOrNull()
                    } else null

                    return downloadHLS(
                        context,
                        link,
                        name,
                        folder ?: "",
                        ep.id,
                        startIndex,
                        callback, parallelConnections = maxConcurrentConnections(context)
                    )
                }

                ExtractorLinkType.VIDEO -> {
                    return downloadThing(
                        context,
                        link,
                        name,
                        folder ?: "",
                        "mp4",
                        tryResume,
                        ep.id,
                        callback,
                        parallelConnections = maxConcurrentConnections(context),
                        /** We require at least 10 MB video files */
                        minimumSize = (1 shl 20) * 10
                    )
                }

                else -> throw IllegalArgumentException("Unsupported download type")
            }
        } catch (_: Throwable) {
            return DOWNLOAD_FAILED
        } finally {
            extractorJob.cancel()
            releaseFileName(ep.id)
        }
    }

    /** desktop: "folder/name" (in lower case, as Windows ignores case in file names) -> the download that saves under it in this run */
    private val fileNamesInUse = HashMap<String, Int>()

    /**
     * desktop: the file name (without its extension) download [id] saves under in [folder]. Two titles with the same name (a film and its
     * remake, or one film from two sources) were given the same file: the second download deleted the first one's finished file and wrote
     * its own in its place, or both wrote into one file at the same time. The name this download used before is kept (a resume finds its part
     * file); a name another download has, finished or running, gets this download's id after it.
     */
    internal fun claimFileName(context: Context, id: Int, folder: String, name: String): String {
        val withId = "$name ($id)"
        fun base(info: DownloadedFileInfo) = info.displayName.substringBeforeLast('.')
        val own = safe { context.getKey<DownloadedFileInfo>(KEY_DOWNLOAD_INFO, id.toString()) }
        // other downloads, saved or partly saved, whose file is still there under one of the two names
        val others = safe {
            getKeys(KEY_DOWNLOAD_INFO)?.mapNotNull { key ->
                val otherId = key.substringAfterLast('/').toIntOrNull() ?: return@mapNotNull null
                if (otherId == id) return@mapNotNull null
                val info = getKey<DownloadedFileInfo>(key) ?: return@mapNotNull null
                if (info.relativePath != folder) return@mapNotNull null
                val other = base(info)
                if (!other.equals(name, true) && !other.equals(withId, true)) return@mapNotNull null
                if (safe { info.toFile(context)?.exists() } != true) return@mapNotNull null
                other.lowercase()
            }
        }.orEmpty()
        synchronized(fileNamesInUse) {
            fun running(candidate: String): Boolean {
                val user = fileNamesInUse["$folder/$candidate".lowercase()]
                return user != null && user != id
            }
            val ownName = own?.takeIf { it.relativePath == folder }?.let { base(it) }?.takeIf { it.equals(name, true) || it.equals(withId, true) }
            val chosen = when {
                ownName != null && !running(ownName) -> ownName
                !running(name) && name.lowercase() !in others -> name
                else -> withId
            }
            fileNamesInUse["$folder/$chosen".lowercase()] = id
            return chosen
        }
    }

    /** desktop: the names [claimFileName] gave download [id] are free again (its saved file info keeps the one it used) */
    private fun releaseFileName(id: Int) {
        synchronized(fileNamesInUse) { fileNamesInUse.values.removeAll { it == id } }
    }


    fun getDownloadFileInfo(
        context: Context,
        id: Int,
    ): DownloadedFileInfoResult? {
        try {
            val info =
                context.getKey<DownloadedFileInfo>(KEY_DOWNLOAD_INFO, id.toString()) ?: return null
            val file = info.toFile(context)

            // only delete the key if the file is not found
            if (file == null || file.exists() == false) {
                return null
            }

            return DownloadedFileInfoResult(
                file.lengthOrThrow(),
                info.totalBytes,
                file.uriOrThrow()
            )
        } catch (e: Exception) {
            logError(e)
            return null
        }
    }

    fun deleteFilesAndUpdateSettings(
        context: Context,
        ids: Set<Int>,
        scope: CoroutineScope,
        onComplete: (Set<Int>) -> Unit = {}
    ) {
        scope.launchSafe(Dispatchers.IO) {
            val deleteJobs = ids.map { id ->
                async {
                    id to deleteFileAndUpdateSettings(context, id)
                }
            }
            val results = deleteJobs.awaitAll()

            val (successfulResults, failedResults) = results.partition { it.second }
            val successfulIds = successfulResults.map { it.first }.toSet()

            if (failedResults.isNotEmpty()) {
                failedResults.forEach { (id, _) ->
                    // TODO show a toast if some failed?
                    Log.e("FileDeletion", "Failed to delete file with ID: $id")
                }
            } else {
                Log.i("FileDeletion", "All files deleted successfully")
            }

            onComplete.invoke(successfulIds)
        }
    }

    private fun deleteFileAndUpdateSettings(context: Context, id: Int): Boolean {
        val success = deleteFile(context, id)
        if (success) context.removeKey(KEY_DOWNLOAD_INFO, id.toString())
        return success
    }

    private fun deleteFile(context: Context, id: Int): Boolean {
        val info =
            context.getKey<DownloadedFileInfo>(KEY_DOWNLOAD_INFO, id.toString()) ?: return false
        val file = info.toFile(context)

        val isFileDeleted = file?.delete() == true || file?.exists() == false

        if (isFileDeleted) {
            deleteMatchingSubtitles(context, info)
            downloadEvent.invoke(id to DownloadActionType.Stop)
            downloadProgressEvent.invoke(Triple(id, 0, 0))
            downloadStatusEvent.invoke(id to DownloadType.IsStopped)
            downloadDeleteEvent.invoke(id)
        }

        return isFileDeleted
    }

    fun getDownloadResumePackage(context: Context, id: Int): DownloadResumePackage? {
        return context.getKey<DownloadResumePackage>(KEY_RESUME_PACKAGES, id.toString())
    }

    fun getDownloadQueuePackage(context: Context, id: Int): DownloadQueueWrapper? {
        return context.getKey<DownloadQueueWrapper>(KEY_RESUME_IN_QUEUE, id.toString())
    }

    fun getDownloadEpisodeMetadata(
        episode: ResultEpisode,
        titleName: String,
        apiName: String,
        currentPoster: String?,
        currentIsMovie: Boolean,
        tvType: TvType,
    ): DownloadEpisodeMetadata {
        return DownloadEpisodeMetadata(
            episode.id,
            episode.parentId,
            sanitizeFilename(titleName),
            apiName,
            episode.poster ?: currentPoster,
            episode.name,
            if (currentIsMovie) null else episode.season,
            if (currentIsMovie) null else episode.episode,
            tvType,
        )
    }

    class EpisodeDownloadInstance(
        val context: Context,
        val downloadQueueWrapper: DownloadQueueWrapper
    ) {
        private val TAG = "EpisodeDownloadInstance"
        private var subtitleDownloadJob: Job? = null
        private var downloadJob: Job? = null
        private var linkLoadingJob: Job? = null

        /** isCompleted just means the download should not be retried.
         * It includes stopped by user AND completion of file download.
         * */
        var isCompleted = false
            set(value) {
                field = value
                if (value) {
                    removeKey(KEY_RESUME_IN_QUEUE, downloadQueueWrapper.id.toString())
                    // Do not emit events when completed as it may also trigger on cancellation.


                    // Force refresh the queue when completed.
                    // May lead to some redundant calls, but ensures that the queue is always up to date.
                    DownloadQueueManager.forceRefreshQueue()
                }
            }

        /** Cancels all active jobs and sets instance to failed. */
        fun cancelDownload() {
            val cause = "Cancel call from cancelDownload"
            this.subtitleDownloadJob?.cancel(cause)
            this.linkLoadingJob?.cancel(cause)

            // Should not cancel the download job, it may need to clean up itself.
            // Better to send a status event using isStopped and let it cancel itself.
            isCancelled = true
        }

        // Run to cancel ongoing work, delete partial work and refresh queue
        private fun cleanup(status: DownloadType) {
            removeKey(KEY_RESUME_IN_QUEUE, downloadQueueWrapper.id.toString())
            val id = downloadQueueWrapper.id

            // Delete subtitles on cancel
            safe {
                val info = context.getKey<DownloadedFileInfo>(KEY_DOWNLOAD_INFO, id.toString())
                if (info != null) {
                    deleteMatchingSubtitles(context, info)
                }
            }

            // desktop: a download that failed before a single byte came leaves no empty file behind, and says why it failed (it ended without a word)
            var retryIn = 0L
            if (status == DownloadType.IsFailed && !isCancelled) {
                safe {
                    val file = context.getKey<DownloadedFileInfo>(KEY_DOWNLOAD_INFO, id.toString())?.toFile(context)
                    if (file != null && file.exists() == true && file.length() == 0L) file.delete()
                }
                safe {
                    val reason = lastFailureReason
                    lastFailureReason = null
                    val what = downloadQueueWrapper.downloadItem?.let { item -> item.resultName + (if (item.isMovie) "" else " E${item.episode.episode}") } ?: "The download"
                    val why = failureText(reason)
                    // a connection that broke off is mostly gone a moment later, and a new try carries on where it stopped: tried again by itself
                    // (twice, 20 s and then 60 s later) instead of waiting for "Try again" (a long download overnight ended at "failed")
                    val tries = autoRetries[id] ?: 0
                    if (isConnectionDrop(reason) && tries < AUTO_RETRIES) {
                        autoRetries[id] = tries + 1
                        retryIn = if (tries == 0) 20_000L else 60_000L
                        failureReasons[id] = "the connection kept breaking off: trying again by itself in ${retryIn / 1000} s"
                    } else {
                        failureReasons[id] = why
                        com.lagradost.desktop.ui.Toasts.show("$what was not downloaded: $why. Try Download > Choose a source…", true)
                    }
                }
            }

            // desktop: a failed download keeps what it needs to be tried again. The "stop" below makes the queue drop the saved package, so
            // "Try again" on the Downloads page found nothing to start and did nothing. Kept from the first source (it is not queued by
            // itself at the next start: only the "in queue" list is)
            val keep = if (status == DownloadType.IsFailed && !isCancelled) safe { context.getKey<DownloadResumePackage>(KEY_RESUME_PACKAGES, id.toString()) } else null

            downloadStatusEvent.invoke(Pair(id, status))
            downloadStatus[id] = status
            downloadEvent.invoke(Pair(id, DownloadActionType.Stop))
            // a download that got part of the file carries on with the same source from where it stopped (it started over: a film that broke
            // off at 1.5 GB of 2 GB was fetched again from the first byte); one that got nothing tries every source again
            val partial = keep?.linkIndex != null && safe {
                val file = context.getKey<DownloadedFileInfo>(KEY_DOWNLOAD_INFO, id.toString())?.toFile(context)
                file != null && file.exists() == true && (file.length() ?: 0L) > 0L
            } == true
            keep?.let { safe { setKey(KEY_RESUME_PACKAGES, id.toString(), DownloadResumePackage(it.item, if (partial) it.linkIndex else null)) } }
            if (retryIn > 0L && keep != null) {
                val wait = retryIn
                ioSafe {
                    delay(wait)
                    // not if it was tried again, cancelled or deleted in the meantime
                    if (downloadStatus[id] != DownloadType.IsFailed) return@ioSafe
                    val pkg = getDownloadResumePackage(context, id) ?: return@ioSafe
                    Log.i(TAG, "download $id: connection dropped, trying again by itself")
                    DownloadQueueManager.addToQueue(pkg.toWrapper())
                }
            }

            // Force refresh the queue when failed.
            // May lead to some redundant calls, but ensures that the queue is always up to date.
            DownloadQueueManager.forceRefreshQueue()
        }

        var isCancelled = false
            set(value) {
                val oldField = field
                field = value

                // Clean up cancelled work, but only once
                // desktop: not for a download that has just failed. Its own clean-up sends "stop", the queue answers by cancelling the instance, and that
                // second clean-up relabelled the failure as "stopped": the button went back to plain Download and nothing said that it had failed.
                if (value && !oldField && !isFailed) {
                    cleanup(DownloadType.IsStopped)
                }
            }


        /** This failure can be both downloader and user initiated.
         * Do not automatically retry in case of failure. */
        var isFailed = false
            set(value) {
                val oldField = field
                field = value

                // Clean up failed work, but only once
                if (value && !oldField) {
                    cleanup(DownloadType.IsFailed)
                }
            }

        companion object {
            private fun displayNotification(context: Context, id: Int, notification: Notification) {
                safe {
                    if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED
                    ) return@safe

                    NotificationManagerCompat.from(context)
                        .notify(DOWNLOAD_NOTIFICATION_TAG, id, notification)
                }
            }
        }

        private suspend fun downloadFromResume(
            downloadResumePackage: DownloadResumePackage,
            notificationCallback: (Int, Notification) -> Unit,
        ) {
            val item = downloadResumePackage.item
            val id = item.ep.id
            if (currentDownloads.value.contains(id)) { // IF IT IS ALREADY DOWNLOADING, RESUME IT
                // desktop: only an earlier download of this episode that was cancelled (or has just ended) is still here: a new instance for an
                // episode exists only once the old one has left the queue. Its "resume" turned the cancelled download back on, and this instance
                // returned without ever ending, so it held a place in the queue until the app closed (with one download at a time, the
                // queue stopped). It now waits for the old one to finish and then downloads
                val gone = withTimeoutOrNull(120_000L) { currentDownloads.first { !it.contains(id) } } != null
                if (!gone) {
                    isFailed = true
                    return
                }
            }

            _currentDownloads.update { downloads ->
                downloads + id
            }

            try {
                for (index in (downloadResumePackage.linkIndex ?: 0) until item.links.size) {
                    val link = item.links[index]
                    val resume = downloadResumePackage.linkIndex == index

                    setKey(
                        KEY_RESUME_PACKAGES,
                        id.toString(),
                        DownloadResumePackage(item, index)
                    )

                    var connectionResult =
                        downloadSingleEpisode(
                            context,
                            item.source,
                            item.folder,
                            item.ep,
                            link,
                            notificationCallback,
                            resume
                        )

                    if (connectionResult.retrySame) {
                        connectionResult = downloadSingleEpisode(
                            context,
                            item.source,
                            item.folder,
                            item.ep,
                            link,
                            notificationCallback,
                            true
                        )
                    }

                    if (connectionResult.success) { // SUCCESS
                        isCompleted = true
                        break
                    } else if (!connectionResult.tryNext || index >= item.links.lastIndex) {
                        isFailed = true
                        break
                    }
                }
            } catch (e: Exception) {
                isFailed = true
                logError(e)
            } finally {
                isFailed = !isCompleted
                _currentDownloads.update { downloads ->
                    downloads - id
                }
            }
        }

        private suspend fun startDownload(
            info: DownloadItem?,
            pkg: DownloadResumePackage?
        ) {
            try {
                if (info != null) {
                    getDownloadResumePackage(context, info.ep.id)?.let { dpkg ->
                        downloadFromResume(dpkg) { id, notification ->
                            displayNotification(context, id, notification)
                        }
                    } ?: run {
                        if (info.links.isEmpty()) return
                        downloadFromResume(
                            DownloadResumePackage(info, null)
                        ) { id, notification ->
                            displayNotification(context, id, notification)
                        }
                    }
                } else if (pkg != null) {
                    downloadFromResume(pkg) { id, notification ->
                        displayNotification(context, id, notification)
                    }
                }
                return
            } catch (e: Exception) {
                isFailed = true
                logError(e)
                return
            }
        }

        private suspend fun downloadFromResume() {
            val resumePackage = downloadQueueWrapper.resumePackage ?: return
            downloadFromResume(resumePackage) { id, notification ->
                displayNotification(context, id, notification)
            }
        }

        fun startDownload() {
            Log.d(TAG, "Starting download ${downloadQueueWrapper.id}")
            setKey(KEY_RESUME_IN_QUEUE, downloadQueueWrapper.id.toString(), downloadQueueWrapper)

            ioSafe {
                if (downloadQueueWrapper.resumePackage != null) {
                    downloadFromResume()
                    // Load links if they are not already loaded
                } else if (downloadQueueWrapper.downloadItem != null && downloadQueueWrapper.downloadItem.links.isNullOrEmpty()) {
                    downloadEpisodeWithoutLinks()
                } else if (downloadQueueWrapper.downloadItem?.links != null) {
                    downloadEpisodeWithLinks(
                        sortUrls(downloadQueueWrapper.downloadItem.links.toSet()),
                        downloadQueueWrapper.downloadItem.subs
                    )
                }
            }
        }

        private fun downloadEpisodeWithLinks(
            links: List<ExtractorLink>,
            subs: List<SubtitleData>?
        ) {
            val downloadItem = downloadQueueWrapper.downloadItem ?: return
            try {
                // Prepare visual keys
                setKey(
                    DOWNLOAD_HEADER_CACHE,
                    downloadItem.resultId.toString(),
                    DownloadObjects.DownloadHeaderCached(
                        apiName = downloadItem.apiName,
                        url = downloadItem.resultUrl,
                        type = downloadItem.resultType,
                        name = downloadItem.resultName,
                        poster = downloadItem.resultPoster,
                        id = downloadItem.resultId,
                        cacheTime = System.currentTimeMillis(),
                    )
                )
                setKey(
                    getFolderName(
                        DOWNLOAD_EPISODE_CACHE,
                        downloadItem.resultId.toString()
                    ), // 3 deep folder for faster access
                    downloadItem.episode.id.toString(),
                    DownloadObjects.DownloadEpisodeCached(
                        name = downloadItem.episode.name,
                        poster = downloadItem.episode.poster,
                        episode = downloadItem.episode.episode,
                        season = downloadItem.episode.season,
                        id = downloadItem.episode.id,
                        parentId = downloadItem.resultId,
                        score = downloadItem.episode.score,
                        description = downloadItem.episode.description,
                        cacheTime = System.currentTimeMillis(),
                    )
                )

                val meta =
                    getDownloadEpisodeMetadata(
                        downloadItem.episode,
                        downloadItem.resultName,
                        downloadItem.apiName,
                        downloadItem.resultPoster,
                        downloadItem.isMovie,
                        downloadItem.resultType
                    )

                val folder =
                    getFolder(downloadItem.resultType, downloadItem.resultName)
                val src = "$DOWNLOAD_NAVIGATE_TO/${downloadItem.resultId}"

                // DOWNLOAD VIDEO
                val info = DownloadItem(src, folder, meta, links)

                this.downloadJob = ioSafe {
                    startDownload(info, null)
                }

                // 1. Checks if the lang should be downloaded
                // 2. Makes it into the download format
                // 3. Downloads it as a .vtt file
                this.subtitleDownloadJob = ioSafe {
                    try {
                        val downloadList = SubtitlesFragment.getDownloadSubsLanguageTagIETF()

                        subs?.filter { subtitle ->
                            downloadList.any { langTagIETF ->
                                subtitle.languageCode == langTagIETF ||
                                        subtitle.originalName.contains(
                                            fromTagToEnglishLanguageName(
                                                langTagIETF
                                            ) ?: langTagIETF
                                        )
                            }
                        }
                            ?.map { ExtractorSubtitleLink(it.name, it.url, "", it.headers) }
                            ?.take(3) // max subtitles download hardcoded (?_?)
                            ?.forEach { link ->
                                // desktop: the name the video is saved under (see claimFileName), so its subtitles go with it
                                val fileName = claimFileName(context, meta.id, folder, getFileName(context, meta))
                                downloadSubtitle(context, link, fileName, folder)
                            }

                    } catch (_: CancellationException) {
                        val fileName = getFileName(context, meta)

                        val info = DownloadedFileInfo(
                            totalBytes = 0,
                            relativePath = folder,
                            displayName = fileName,
                            basePath = context.getBasePath().second
                        )

                        deleteMatchingSubtitles(context, info)
                    }
                }
            } catch (e: Exception) {
                // The work is only failed if the job did not get started
                if (this.downloadJob == null) {
                    isFailed = true
                }
                logError(e)
            }
        }

        private suspend fun downloadEpisodeWithoutLinks() {
            val downloadItem = downloadQueueWrapper.downloadItem ?: return

            val generator = RepoLinkGenerator(listOf(downloadItem.episode))
            val currentLinks = mutableSetOf<ExtractorLink>()
            val currentSubs = mutableSetOf<SubtitleData>()
            val meta =
                getDownloadEpisodeMetadata(
                    downloadItem.episode,
                    downloadItem.resultName,
                    downloadItem.apiName,
                    downloadItem.resultPoster,
                    downloadItem.isMovie,
                    downloadItem.resultType
                )

            createDownloadNotification(
                context,
                downloadItem.apiName,
                txt(R.string.loading).asString(context),
                meta,
                DownloadType.IsPending,
                0,
                1,
                { _, _ -> },
                null,
                null,
                0
            )?.let { linkLoadingNotification ->
                displayNotification(context, downloadItem.episode.id, linkLoadingNotification)
            }

            // desktop: when the first source came, for the grace below
            val firstLinkAt = java.util.concurrent.atomic.AtomicLong(0L)
            linkLoadingJob = ioSafe {
                generator.generateLinks(
                    offset = 0,
                    isCasting = false,
                    clearCache = false,
                    sourceTypes = LOADTYPE_INAPP_DOWNLOAD,
                    callback = {
                        it.first?.let { link ->
                            synchronized(currentLinks) { currentLinks.add(link) }
                            firstLinkAt.compareAndSet(0L, System.currentTimeMillis())
                        }
                    },
                    subtitleCallback = { sub ->
                        synchronized(currentSubs) { currentSubs.add(sub) }
                    })
            }

            // Wait for link loading completion.
            // desktop: not for the very last source. The slowest extractors of a title run to their time limits: a download of a film waited
            // ~2 minutes "pending" (CineStream, 56 sources) while the player of the same title started in 15 s. Once a source is in, the others
            // get DOWNLOAD_LINK_GRACE_MS more (most of them come within it), then the best of what came is downloaded (the rest still follow it
            // if it fails).
            var stoppedEarly = false
            while (linkLoadingJob?.isActive == true) {
                val first = firstLinkAt.get()
                if (first != 0L && System.currentTimeMillis() - first > DOWNLOAD_LINK_GRACE_MS) {
                    stoppedEarly = true
                    linkLoadingJob?.cancel()
                    break
                }
                delay(250)
            }
            if (!stoppedEarly) linkLoadingJob?.join()

            // Remove link loading notification
            NotificationManagerCompat.from(context)
                .cancel(DOWNLOAD_NOTIFICATION_TAG, downloadItem.episode.id)

            if (linkLoadingJob?.isCancelled == true && !stoppedEarly) {
                // Same as if no links, but no toast.
                // Cancelled link loading is presumed to be user initiated
                isCancelled = true
                return
            } else if (currentLinks.isEmpty()) {
                main {
                    showToast(
                        R.string.no_links_found_toast,
                        Toast.LENGTH_SHORT
                    )
                }
                lastFailureReason = "no links"
                isFailed = true
                return
            } else {
                main {
                    showToast(
                        R.string.download_started,
                        Toast.LENGTH_SHORT
                    )
                }
            }

            // Profiles should always contain a download type
            val profile = QualityDataHelper.getProfiles().first {
                it.types.contains(
                    QualityDataHelper.QualityProfileType.Download
                )
            }

            // desktop: torrents after every direct source (they need the engine and peers), the best shared of them first
            val sortedLinks = synchronized(currentLinks) { currentLinks.toList() }.sortedWith(
                compareBy<ExtractorLink> { if (com.lagradost.desktop.torrent.TorrentEngine.isTorrent(it)) 1 else 0 }
                    .thenBy { -com.lagradost.desktop.torrent.TorrentEngine.seedTier(it) }
                    // desktop: Settings > Downloads > "Download quality" (up to 1080p and not huge by default; before, an 11.4 GB file was taken
                    // when a 2 GB 1080p one was in the list)
                    .then(com.lagradost.desktop.net.DownloadChoice.comparator(com.lagradost.desktop.ui.fluent.Appearance.downloadQuality))
                    // Negative, because the highest priority should be first
                    .thenBy { -getLinkPriority(profile.id, it) }
            )

            downloadEpisodeWithLinks(
                sortedLinks,
                sortSubs(synchronized(currentSubs) { currentSubs.toSet() }),
            )
        }
    }
}
