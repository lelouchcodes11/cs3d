package com.lagradost.desktop.player

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Rational
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.lagradost.cloudstream3.ui.player.*
import com.lagradost.cloudstream3.ui.subtitles.SaveCaptionStyle
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.videoskip.VideoSkipStamp
import com.sun.jna.Pointer
import java.io.File

/** What the subtitle loader is doing with [subtitle]: fetching it, showing it, or giving up on it */
enum class SubtitleLoadState { Loading, Loaded, Failed }

data class SubtitleLoadEvent(
    val state: SubtitleLoadState,
    val subtitle: SubtitleData,
    override val source: PlayerEventSource = PlayerEventSource.Player,
) : PlayerEvent()

open class MpvPlayer : IPlayer {
    companion object {
        private const val TAG = "MpvPlayer"
        /** Link headers mpv must not send as given: it makes them itself for every request (Range for each seek) */
        private val HOP_HEADERS = listOf("Range", "Host", "Content-Length", "Connection", "Accept-Encoding")
        /** How much earlier CNCVerse whole-file subtitles are shown (see shiftedStreamSubtitle) */
        private const val STREAM_SUB_SHIFT_MS = 850L

        /** A live seek stops this far (s) before the newest buffered picture: a reserve for segments that come late */
        private const val LIVE_MARGIN_S = 6.0

        /** A live channel waiting this long for data is opened again (ms) */
        private const val LIVE_STALL_MS = 12_000L

        /** Client errors that bounded ranges, headers and the app's HTTP client can cure; 404/410 and rate limits (429) they cannot */
        private val RANGE_RETRY_STATUS = setOf(400, 403, 405, 406, 416)

        /** Tools without a window (link tests): no video output, no audio device */
        @Volatile
        var headless = false

        /**
         * Set while a native video window exists (see ui/screens/player/NativeVideo.kt): the window handle mpv draws into, 0 when it is not ready.
         * A core created while this is set draws with mpv's own GPU renderer into that window instead of the software render API.
         */
        @Volatile
        var nativeWindow: (() -> Long)? = null

        /** The player that was started last (dev tools) */
        @Volatile
        var active: MpvPlayer? = null
    }

    private val mpv: Mpv = Mpv.INSTANCE
    private var handle: Pointer? = null

    /** This core draws into a native window (see [nativeWindow]) */
    @Volatile
    var native = false
        private set

    // Video goes through libmpv's software render API into the view's bitmap (vo=libmpv), so the
    // player controls are drawn over it like on Android
    private val renderLock = Any()
    private var renderContext: Pointer? = null
    @Volatile
    private var surface: com.lagradost.desktop.runtime.ui.MpvSurfaceView? = null
    private val renderUpdateCallback = Mpv.RenderUpdateCallback { surface?.requestRender() }
    private val swFormat = com.sun.jna.Memory(8).apply { setString(0, "bgra") }
    private val swSize = com.sun.jna.Memory(8)
    private val swStride = com.sun.jna.Memory(8)
    // mpv_render_frame_info {uint64 flags; int64 target_time}: when mpv wants the frame it renders on screen (mpv's clock, in NANOseconds although mpv_get_time_us reads microseconds)
    private val frameInfo = com.sun.jna.Memory(16)
    private val noBlock = com.sun.jna.Memory(8).apply { setInt(0, 0) }
    @Volatile private var frameInfoWorks = true
    private val frameRenderer = com.lagradost.desktop.runtime.ui.VideoFrameRenderer { w, h, stride, address ->
        var targetNs = 0L
        val rendered = synchronized(renderLock) {
            val rc = renderContext ?: return@synchronized false
            mpv.mpv_render_context_update(rc)
            swSize.setInt(0, w)
            swSize.setInt(4, h)
            swStride.setLong(0, stride.toLong())
            // for the frame the render call below draws: when mpv wants it on screen (mpv's clock -> System.nanoTime(): the same instant read on both)
            if (FramePacer.enabled && frameInfoWorks) {
                frameInfo.clear()
                val got = runCatching { mpv.mpv_render_context_get_info(rc, Mpv.RenderParam().also { it.type = Mpv.MPV_RENDER_PARAM_NEXT_FRAME_INFO; it.data = frameInfo }) }
                val core = handle
                val targetMpvNs = frameInfo.getLong(8)
                if (got.isFailure) { frameInfoWorks = false; Log.w(TAG, "no frame timing from mpv: ${got.exceptionOrNull()?.message}") }
                else if (targetMpvNs > 0 && core != null) {
                    val now = System.nanoTime()
                    val t = now + (targetMpvNs - mpv.mpv_get_time_us(core) * 1000L)
                    // a frame time is within a second or two of now; anything else is a wrong clock or unit: mpv's own blocking is used
                    if (Math.abs(t - now) < 2_000_000_000L) targetNs = t
                }
            }
            val params = renderParams(
                Mpv.MPV_RENDER_PARAM_SW_SIZE to swSize,
                Mpv.MPV_RENDER_PARAM_SW_FORMAT to swFormat,
                Mpv.MPV_RENDER_PARAM_SW_STRIDE to swStride,
                Mpv.MPV_RENDER_PARAM_SW_POINTER to Pointer(address),
                // paced: mpv hands the frame over about a frame interval before its target time and FramePacer holds it for its refresh (mpv would block instead)
                *(if (targetNs != 0L) arrayOf(Mpv.MPV_RENDER_PARAM_BLOCK_FOR_TARGET_TIME to noBlock) else emptyArray()),
            )
            mpv.mpv_render_context_render(rc, params) >= 0
        }
        // shown at the refresh this frame's target time belongs to (an even 2-3 rhythm for 24 fps on 60 Hz), not at whichever one the render jitter lands before
        // smooth motion draws every refresh from the frames and their target times itself, but only while blending is useful (24 / 25 fps on 60 Hz);
        // otherwise a frame waits here for its slot. (A 30 or 60 fps video with smooth motion on used to skip both: mpv hands frames over about a
        // frame early without blocking, so they showed at whichever refresh came next - an uneven 1-3 rhythm and the picture ahead of the sound)
        val smooth = surface?.smoothMotion == true
        surface?.lastTargetNs = if (smooth) targetNs else 0L
        if (rendered && targetNs != 0L && !(smooth && surface?.blendUseful == true)) FramePacer.hold(targetNs)
        rendered
    }

    /** mpv_render_param[]: {int type; void* data} entries, zero terminated */
    private fun renderParams(vararg entries: Pair<Int, Pointer?>): com.sun.jna.Memory {
        val m = com.sun.jna.Memory(16L * (entries.size + 1))
        m.clear()
        entries.forEachIndexed { i, (type, data) ->
            m.setInt(i * 16L, type)
            m.setPointer(i * 16L + 8, data)
        }
        return m
    }

    fun setVideoSurface(newSurface: com.lagradost.desktop.runtime.ui.MpvSurfaceView?) {
        val old = surface
        if (old === newSurface) return
        old?.renderer = null
        surface = newSurface
        newSurface?.present?.grid = VBlankGrid
        newSurface?.renderer = frameRenderer
    }

    private val playerListeners = java.util.concurrent.CopyOnWriteArrayList<Player.Listener>()
    @Volatile
    private var bufferedPositionMs: Long = 0L
    @Volatile
    private var lastPostedPositionMs: Long = 0L
    @Volatile
    private var fileLoaded: Boolean = false
    private var lastPlaybackState = Player.STATE_IDLE
    private var lastIsPlaying = false

    private fun notifyListeners(block: (Player.Listener) -> Unit) {
        mainHandler.post { for (l in playerListeners) block(l) }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var isPlaying: Boolean = false
    @Volatile
    private var isPaused: Boolean = true
    @Volatile
    private var isBuffering: Boolean = false
    private var isSeeking = false

    /** The playlist server address of the playing HLS stream (whole-file subtitle renditions are asked for there), "" otherwise */
    @Volatile private var hlsAddress = ""
    /** mpv tracks of those subtitle files: listed with the subtitles of the stream itself */
    private val streamSubtitleTracks = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Int, Boolean>())
    @Volatile
    private var isEnded: Boolean = false
    @Volatile
    private var currentPositionMs: Long = 0L
    @Volatile
    private var currentDurationMs: Long = 0L
    @Volatile
    private var currentSpeed: Float = 1.0f
    @Volatile
    private var currentSubtitleOffsetMs: Long = 0L
    @Volatile
    private var currentWidth: Int = 0
    @Volatile
    private var currentHeight: Int = 0

    private var activeSubtitles: MutableSet<SubtitleData> = mutableSetOf()
    // a playlist that does not play is tried once more through HlsProxy (repaired playlists)
    private var lastLinkUrl: String? = null
    private var useProxy = false
    // the user agent sent when a link does not name one: Android's, as ExoPlayer does (a link's own one must not stay set for the next source)
    private val defaultUserAgent = com.lagradost.cloudstream3.USER_AGENT
    private var proxyTried = false
    // a plain video file that was refused (403 ...) is tried once more through RangeProxy (bounded ranges, the app's HTTP client)
    private var useRangeProxy = false

    /** DASH manifests read ahead: the files of an on-demand one, or [NO_FILES] for any other kind (see OnDemandDash) */
    private val onDemand = java.util.concurrent.ConcurrentHashMap<String, OnDemandDash.Files>()
    private val NO_FILES = OnDemandDash.Files("", null, 0)

    /** Counts loads: a manifest read ahead for an older load does not start it any more */
    private var loadGeneration = 0
    private var rangeTried = false
    @Volatile
    private var lastHttpStatus = 0
    @Volatile
    private var preferredSubtitle: SubtitleData? = null
    // External subtitles are added to the file that plays once it is open: SubtitleData id -> mpv track id
    private val externalTracks = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private var subtitlesDisabled = false
    private var lastEmbedded: List<SubtitleData> = emptyList()
    // sub-add may download the file: never on the UI or event threads
    private val subExecutor = java.util.concurrent.Executors.newFixedThreadPool(4) { r -> Thread(r, "MpvPlayer-Subtitles").apply { isDaemon = true } }
    @Volatile
    private var fileGeneration = 0
    private var currentLink: ExtractorLink? = null
    private var currentUri: ExtractorUri? = null
    private var currentContext: Context? = null
    private var startPositionMs: Long? = null
    // set when mpv refused the `start` option: the seek is done once the file is open
    @Volatile
    private var pendingSeekMs = 0L
    @Volatile
    private var loadStartedAt = 0L
    // false from the moment a file is asked for until its first picture is on screen (PLAYBACK_RESTART)
    @Volatile
    private var firstFrameLogged = true
    // the user (or a dialog) paused on purpose; everything else - opening, a reload, a source switch - plays
    @Volatile
    private var userPaused = false

    private var eventHandler: ((PlayerEvent) -> Unit)? = null
    private var requestedListeningPercentages: List<Int>? = null
    private val reportedPercentages = mutableSetOf<Int>()
    private var timeStamps: List<VideoSkipStamp> = emptyList()
    private var currentActiveStamp: VideoSkipStamp? = null

    private var eventThread: Thread? = null
    @Volatile
    private var isReleased: Boolean = false

    val exoPlayer: ExoPlayer = object : ExoPlayer, com.lagradost.desktop.runtime.ui.DesktopVideoOutput {
        override val duration: Long get() = this@MpvPlayer.getDuration() ?: 0L
        override val currentPosition: Long get() = this@MpvPlayer.getPosition() ?: 0L
        override val bufferedPosition: Long get() = maxOf(bufferedPositionMs, currentPositionMs)
        override val isPlaying: Boolean get() = this@MpvPlayer.getIsPlaying()
        override val playWhenReady: Boolean get() = !isPaused
        override val playbackState: Int get() = currentPlaybackState()
        override val videoSize: androidx.media3.common.VideoSize
            get() = androidx.media3.common.VideoSize(currentWidth, currentHeight)
        override val currentMediaItemIndex: Int get() = 0

        override fun setVideoSurface(surface: com.lagradost.desktop.runtime.ui.MpvSurfaceView?) =
            this@MpvPlayer.setVideoSurface(surface)

        override fun seekTo(positionMs: Long) {
            this@MpvPlayer.seekTo(positionMs)
        }

        override fun play() {
            this@MpvPlayer.handleEvent(CSPlayerEvent.Play)
        }

        override fun pause() {
            this@MpvPlayer.handleEvent(CSPlayerEvent.Pause)
        }

        override fun addListener(listener: Player.Listener) {
            playerListeners.addIfAbsent(listener)
        }

        override fun removeListener(listener: Player.Listener) {
            playerListeners.remove(listener)
        }
    }

    private fun currentPlaybackState(): Int = when {
        handle == null || !fileLoaded -> if (currentLink != null || currentUri != null) Player.STATE_BUFFERING else Player.STATE_IDLE
        isEnded -> Player.STATE_ENDED
        isBuffering -> Player.STATE_BUFFERING
        else -> Player.STATE_READY
    }

    private fun postEvent(event: PlayerEvent) {
        mainHandler.post {
            eventHandler?.invoke(event)
        }
    }

    @Synchronized
    private fun initMpvIfNeeded() {
        // like CS3IPlayer, a released player makes a new core on the next load
        if (handle != null) return
        active = this
        isReleased = false
        fileLoaded = false
        val ctx = mpv.mpv_create() ?: throw RuntimeException("Failed to create mpv context")
        handle = ctx

        val wid = if (headless) 0L else runCatching { nativeWindow?.invoke() ?: 0L }.getOrDefault(0L)
        native = wid != 0L
        if (!headless && !native && nativeWindow != null) {
            // the page asked for the native player but its window is not there: the standard player takes over (the page switches to it)
            Log.w(TAG, "native video window not ready: using the standard player")
            com.lagradost.desktop.ui.screens.player.NativeVideo.broken = true
        }
        if (native) {
            // mpv's own renderer in a window of the app: Direct3D 11, decoding stays on the GPU, frames timed to the audio clock (mpv's default).
            // display-resample + interpolation timed frames to the screen's refresh instead, but the refresh a child window sees is jittery
            // (mpv measured ~54 Hz, jitter 0.46 on a 60 Hz screen): 66 dropped and 155 mistimed frames in 40 s of 24p; audio timing: none
            Log.i(TAG, "native video window 0x${java.lang.Long.toHexString(wid)}")
            for ((k, v) in listOf("wid" to wid.toString(), "vo" to "gpu", "gpu-api" to "d3d11", "hwdec" to "auto-safe",
                "osc" to "no", "osd-level" to "0", "input-default-bindings" to "no", "input-vo-keyboard" to "no", "input-cursor" to "no", "cursor-autohide" to "no", "keepaspect" to "yes", "background-color" to "#000000"))
                mpv.mpv_set_option_string(ctx, k, v)
            if (com.lagradost.desktop.ui.fluent.Appearance.anime4k) Anime4K.option()?.let { mpv.mpv_set_option_string(ctx, "glsl-shaders", it) }
        } else {
            mpv.mpv_set_option_string(ctx, "vo", if (headless) "null" else "libmpv")
            // decoded frames are read back for the software renderer
            mpv.mpv_set_option_string(ctx, "hwdec", "auto-copy-safe")
        }
        mpv.mpv_set_option_string(ctx, "audio-spdif", com.lagradost.desktop.ui.fluent.Appearance.audioDecoder.spdif)
        if (headless) mpv.mpv_set_option_string(ctx, "ao", "null")
        mpv.mpv_set_option_string(ctx, "keep-open", "yes")
        mpv.mpv_set_option_string(ctx, "idle", "yes")
        mpv.mpv_set_option_string(ctx, "force-window", "no")
        mpv.mpv_set_option_string(ctx, "ytdl", "no")
        // embedded HLS WebVTT: every seek re-reads the cached cues and libass would draw each one again (the same line stacked 3, 5, 9 times)
        mpv.mpv_set_option_string(ctx, "sub-clear-on-seek", "yes")
        // the fonts of the subtitle style settings, found by libass by their family names
        runCatching { SubtitleStyler.prepareFonts(); mpv.mpv_set_option_string(ctx, "sub-fonts-dir", SubtitleStyler.fontsDir.absolutePath) }
        // AudioManager STREAM_MUSIC + LoudnessEnhancer boost (up to 200%)
        mpv.mpv_set_option_string(ctx, "volume-max", "200")
        // a longer audio buffer (mpv's default is 0.2 s) rides out a busy moment of the PC without a crackle
        mpv.mpv_set_option_string(ctx, "audio-buffer", "0.4")
        mpv.mpv_set_option_string(ctx, "volume", DesktopAudioVolume.format())
        // a stalled connection ends in an error (and the next source) instead of waiting for ever; plenty of read-ahead
        mpv.mpv_set_option_string(ctx, "network-timeout", "15")
        // names are resolved with the app's DNS setting (DNS over HTTPS ...), like Android's player does; -Dcloudstream.noproxy=true switches it off
        if (System.getProperty("cloudstream.noproxy") != "true") (System.getProperty("cloudstream.httpproxy") ?: com.lagradost.desktop.net.NetProxy.address)?.let { mpv.mpv_set_option_string(ctx, "http-proxy", it) }
        // like ExoPlayer: Android's default user agent unless the link names its own
        mpv.mpv_set_option_string(ctx, "user-agent", com.lagradost.cloudstream3.USER_AGENT)
        mpv.mpv_set_option_string(ctx, "cache", "yes")
        // read ahead one minute, not more (mpv's default cache-secs is unlimited, which preloaded ~10 minutes); keep little behind
        mpv.mpv_set_option_string(ctx, "cache-secs", "60")
        mpv.mpv_set_option_string(ctx, "demuxer-readahead-secs", "60")
        mpv.mpv_set_option_string(ctx, "demuxer-max-bytes", "150MiB")
        mpv.mpv_set_option_string(ctx, "demuxer-max-back-bytes", "32MiB")

        // debugging: ffmpeg demuxer messages only show with a module level, e.g. -Dcloudstream.mpvmsglevel=all=no,ffmpeg/demuxer=v,cplayer=v
        System.getProperty("cloudstream.mpvmsglevel")?.let { mpv.mpv_set_option_string(ctx, "msg-level", it) }
        val res = mpv.mpv_initialize(ctx)
        if (res < 0) {
            Log.e(TAG, "Failed to initialize mpv: ${mpv.mpv_error_string(res)}")
        }
        if (!headless && !native) createRenderContext(ctx)
        com.lagradost.desktop.runtime.DesktopAudio.addListener(volumeListener)
        mpv.mpv_request_log_messages(ctx, System.getProperty("cloudstream.mpvlog") ?: "warn")

        mpv.mpv_observe_property(ctx, 1, "time-pos", Mpv.MPV_FORMAT_DOUBLE)
        mpv.mpv_observe_property(ctx, 2, "duration", Mpv.MPV_FORMAT_DOUBLE)
        mpv.mpv_observe_property(ctx, 3, "pause", Mpv.MPV_FORMAT_FLAG)
        mpv.mpv_observe_property(ctx, 4, "paused-for-cache", Mpv.MPV_FORMAT_FLAG)
        mpv.mpv_observe_property(ctx, 5, "eof-reached", Mpv.MPV_FORMAT_FLAG)
        // display size (after aspect correction), what ExoPlayer reports as the video size
        mpv.mpv_observe_property(ctx, 6, "dwidth", Mpv.MPV_FORMAT_INT64)
        mpv.mpv_observe_property(ctx, 7, "dheight", Mpv.MPV_FORMAT_INT64)
        mpv.mpv_observe_property(ctx, 8, "track-list/count", Mpv.MPV_FORMAT_INT64)
        mpv.mpv_observe_property(ctx, 9, "demuxer-cache-time", Mpv.MPV_FORMAT_DOUBLE)
        // selected tracks, so track menus show what really plays
        mpv.mpv_observe_property(ctx, 10, "sid", Mpv.MPV_FORMAT_STRING)
        mpv.mpv_observe_property(ctx, 11, "aid", Mpv.MPV_FORMAT_STRING)
        mpv.mpv_observe_property(ctx, 12, "vid", Mpv.MPV_FORMAT_STRING)
        // a seek in progress shows the loading ring at once (paused-for-cache only comes once the buffer has run dry, often seconds later)
        mpv.mpv_observe_property(ctx, 13, "seeking", Mpv.MPV_FORMAT_FLAG)

        startEventLoop(ctx)
        postEvent(PlayerAttachedEvent(exoPlayer))
        // the saved subtitle look (settings, Player, Subtitles), and later changes of it
        runCatching { updateSubtitleStyle(com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.getCurrentSavedStyle()) }
        com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.applyStyleEvent += styleListener
    }

    private val styleListener: (SaveCaptionStyle) -> Unit = { style -> updateSubtitleStyle(style) }

    private val volumeListener: (Double) -> Unit = { v -> if (handle != null) setMpvProperty("volume", String.format(java.util.Locale.ROOT, "%.1f", v)) }

    private object DesktopAudioVolume {
        fun format(): String = String.format(java.util.Locale.ROOT, "%.1f", com.lagradost.desktop.runtime.DesktopAudio.volumePercent)
    }

    private fun createRenderContext(ctx: Pointer) {
        synchronized(renderLock) {
            val apiType = com.sun.jna.Memory(8).apply { setString(0, "sw") }
            val out = com.sun.jna.ptr.PointerByReference()
            val res = mpv.mpv_render_context_create(out, ctx, renderParams(Mpv.MPV_RENDER_PARAM_API_TYPE to apiType))
            if (res < 0) {
                Log.e(TAG, "Failed to create the render context: ${mpv.mpv_error_string(res)}")
                return
            }
            renderContext = out.value
            mpv.mpv_render_context_set_update_callback(out.value, renderUpdateCallback, null)
        }
        surface?.requestRender()
    }

    private fun startEventLoop(ctx: Pointer) {
        eventThread = Thread({
            while (!isReleased && handle == ctx) {
                val eventPtr = mpv.mpv_wait_event(ctx, 0.05) ?: continue
                val event = Mpv.MpvEvent(eventPtr)
                when (event.event_id) {
                    Mpv.MPV_EVENT_NONE -> {}
                    Mpv.MPV_EVENT_PROPERTY_CHANGE -> {
                        val prop = event.getProperty() ?: continue
                        val propName = prop.name ?: continue
                        handlePropertyChange(propName, prop.format, prop.data)
                    }
                    Mpv.MPV_EVENT_START_FILE -> {
                        fileLoaded = false
                        isSeeking = false
                        firstFrameLogged = false
                        isEnded = false
                        fileGeneration++
                        externalTracks.clear()
                        streamSubtitleTracks.clear()
                        lastEmbedded = emptyList()
                        tracksSnapshot = null
                        sidSnapshot = null
                        updateStatus()
                    }
                    Mpv.MPV_EVENT_PLAYBACK_RESTART -> {
                        if (!firstFrameLogged) {
                            firstFrameLogged = true
                            Log.i(TAG, "timing: first frame ${System.currentTimeMillis() - loadStartedAt} ms after loadfile")
                        }
                        updateStatus()
                        notifyListeners { it.onPositionDiscontinuity(Player.PositionInfo(), Player.PositionInfo(positionMs = currentPositionMs), 1) }
                    }
                    Mpv.MPV_EVENT_FILE_LOADED -> {
                        fileLoaded = true
                        liveStream = detectLive()
                        // the pause mpv puts on itself at the end of the failed source (keep-open) can land after our "play" for this one
                        ensurePlaying()
                        mainHandler.postDelayed({ ensurePlaying() }, 600)
                        mainHandler.postDelayed({ ensurePlaying() }, 2000)
                        if (liveStream) Log.i(TAG, "live stream: seeks stay inside the buffered part")
                        if (native) mainHandler.postDelayed({ checkNativeOutput() }, 6_000)
                        isBuffering = false
                        isEnded = false
                        Log.i(TAG, "timing: file opened ${System.currentTimeMillis() - loadStartedAt} ms after loadfile")
                        pendingSeekMs.takeIf { it > 0 }?.let { pendingSeekMs = 0; mpvCommand("seek", (it / 1000.0).toString(), "absolute") }
                        chooseBestEdition()
                        refreshTracks()
                        updateStatus()
                        subtitleTask { addStreamSubtitles(); syncSubtitles() }
                    }
                    Mpv.MPV_EVENT_LOG_MESSAGE -> {
                        event.getLogMessage()?.let {
                            val text = it.text?.trimEnd()
                            Log.w("mpv", "[${it.prefix}] $text")
                            // "[curl] HTTP error 403" / "[ffmpeg] http: HTTP error 403 Forbidden": why a plain file did not open
                            if (text != null && text.contains("HTTP error")) {
                                Regex("HTTP error (\\d{3})").find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { code -> lastHttpStatus = code }
                            }
                        }
                    }
                    Mpv.MPV_EVENT_END_FILE -> {
                        val end = event.getEndFile()
                        if (end != null && end.reason == Mpv.MPV_END_FILE_REASON_ERROR) {
                            // ExoPlayer reports a failed source as a PlaybackException; the player UI then tries the next link
                            val message = mpv.mpv_error_string(end.error) ?: "error ${end.error}"
                            val failed = currentLink
                            val wrapper = failed?.url?.contains("enc-dec.app/api/parse-", ignoreCase = true) == true
                            if (failed != null && onDemand[failed.url]?.let { it !== NO_FILES } == true) {
                                // the files of an on-demand manifest did not open: the manifest itself (playable, not seekable)
                                onDemand[failed.url] = NO_FILES
                                Log.i(TAG, "the files of the on-demand DASH manifest did not play ($message), trying the manifest")
                                mainHandler.post {
                                    currentContext?.let { ctx -> loadPlayer(ctx, true, currentLink, currentUri, startPositionMs, activeSubtitles, preferredSubtitle, true, false) }
                                }
                            } else if (failed != null && !proxyTried && (isHls(failed) || isDash(failed)) && !(wrapper && useProxy)) {
                                // once more the other way: plain when the playlist server failed, through it when the plain address did
                                proxyTried = true
                                useProxy = !useProxy
                                Log.i(TAG, "playback failed ($message), trying again ${if (useProxy) "through" else "without"} the playlist server")
                                mainHandler.post {
                                    currentContext?.let { ctx -> loadPlayer(ctx, true, currentLink, currentUri, startPositionMs, activeSubtitles, preferredSubtitle, true, false) }
                                }
                            } else if (failed != null && !rangeTried && !isHls(failed) && !useRangeProxy && isPlainFile(failed) && lastHttpStatus in RANGE_RETRY_STATUS) {
                                rangeTried = true
                                useRangeProxy = true
                                Log.i(TAG, "opening failed (HTTP $lastHttpStatus), trying again through the range server")
                                mainHandler.post {
                                    currentContext?.let { ctx -> loadPlayer(ctx, true, currentLink, currentUri, startPositionMs, activeSubtitles, preferredSubtitle, true, false) }
                                }
                            } else {
                                postEvent(ErrorEvent(androidx.media3.common.PlaybackException(message, null, androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED)))
                            }
                        }
                        if (end != null && end.reason == Mpv.MPV_END_FILE_REASON_EOF) {
                            isEnded = true
                            isPlaying = false
                            postEvent(VideoEndedEvent())
                            updateStatus()
                        }
                    }
                    Mpv.MPV_EVENT_SHUTDOWN -> break
                    else -> {}
                }
            }
        }, "MpvPlayer-EventLoop").apply {
            isDaemon = true
            start()
        }
    }

    private fun handlePropertyChange(name: String, format: Int, data: Pointer?) {
        when (name) {
            "time-pos" -> {
                if (data != null && format == Mpv.MPV_FORMAT_DOUBLE) {
                    val posSec = data.getDouble(0)
                    val posMs = (posSec * 1000).toLong()
                    currentPositionMs = posMs
                    // time-pos changes every frame; the UI gets position updates about every 250 ms or on jumps
                    if (kotlin.math.abs(posMs - lastPostedPositionMs) >= 250) {
                        postEvent(PositionEvent(PlayerEventSource.Player, lastPostedPositionMs, posMs, currentDurationMs))
                        lastPostedPositionMs = posMs
                    }

                    // Check timestamps
                    val matchingStamp = timeStamps.firstOrNull { posMs >= it.timestamp.startMs && posMs <= it.timestamp.endMs }
                    if (matchingStamp != currentActiveStamp) {
                        currentActiveStamp = matchingStamp
                        matchingStamp?.let { postEvent(TimestampInvokedEvent(it)) }
                    }

                    // Check listening percentages
                    if (currentDurationMs > 0) {
                        val pct = ((posMs.toDouble() / currentDurationMs) * 100).toInt()
                        val req = requestedListeningPercentages
                        if (req != null && pct in req && pct !in reportedPercentages) {
                            reportedPercentages.add(pct)
                        }
                    }
                }
            }
            "duration" -> {
                if (data != null && format == Mpv.MPV_FORMAT_DOUBLE) {
                    val durSec = data.getDouble(0)
                    currentDurationMs = (durSec * 1000).toLong()
                }
            }
            "pause" -> {
                if (data != null && format == Mpv.MPV_FORMAT_FLAG) {
                    val pausedFlag = data.getInt(0)
                    val wasPaused = isPaused
                    isPaused = (pausedFlag != 0)
                    isPlaying = !isPaused && !isBuffering && !isEnded
                    if (wasPaused != isPaused) {
                        postEvent(if (isPaused) PauseEvent() else PlayEvent())
                        updateStatus()
                    }
                    // a pause nobody asked for soon after a source change (the end of the failed source arriving late): undone,
                    // at whatever moment it lands (the checks at file-loaded time can be over by then)
                    if (isPaused && !userPaused && !isEnded && System.currentTimeMillis() - loadStartedAt < 20_000) mainHandler.post { ensurePlaying() }
                }
            }
            "seeking" -> {
                if (data != null && format == Mpv.MPV_FORMAT_FLAG) {
                    val seeking = data.getInt(0) != 0
                    if (seeking != isSeeking) { isSeeking = seeking; updateStatus() }
                }
            }
            "paused-for-cache" -> {
                if (data != null && format == Mpv.MPV_FORMAT_FLAG) {
                    val bufferingFlag = data.getInt(0)
                    isBuffering = (bufferingFlag != 0)
                    mainHandler.removeCallbacks(liveStallCheck)
                    if (isBuffering) { bufferingSince = System.currentTimeMillis(); mainHandler.postDelayed(liveStallCheck, LIVE_STALL_MS) } else bufferingSince = 0L
                    isPlaying = !isPaused && !isBuffering && !isEnded
                    updateStatus()
                }
            }
            "eof-reached" -> {
                if (data != null && format == Mpv.MPV_FORMAT_FLAG) {
                    val eof = data.getInt(0) != 0
                    if (eof && !isEnded) {
                        isEnded = true
                        isPlaying = false
                        postEvent(VideoEndedEvent())
                        updateStatus()
                    }
                }
            }
            "dwidth" -> {
                if (data != null && format == Mpv.MPV_FORMAT_INT64) {
                    currentWidth = data.getLong(0).toInt()
                    postEvent(ResizedEvent(currentHeight, currentWidth))
                    notifyListeners { it.onVideoSizeChanged(androidx.media3.common.VideoSize(currentWidth, currentHeight)) }
                }
            }
            "dheight" -> {
                if (data != null && format == Mpv.MPV_FORMAT_INT64) {
                    currentHeight = data.getLong(0).toInt()
                    postEvent(ResizedEvent(currentHeight, currentWidth))
                    notifyListeners { it.onVideoSizeChanged(androidx.media3.common.VideoSize(currentWidth, currentHeight)) }
                }
            }
            "track-list/count" -> {
                refreshTracks()
                subtitleTask { publishEmbeddedSubtitles() }
            }
            "sid", "aid", "vid" -> refreshTracks()
            "demuxer-cache-time" -> {
                if (data != null && format == Mpv.MPV_FORMAT_DOUBLE) bufferedPositionMs = (data.getDouble(0) * 1000).toLong()
            }
        }
    }

    fun debugState(): String = "fileLoaded=$fileLoaded firstFrame=$firstFrameLogged paused=$isPaused buffering=$isBuffering ended=$isEnded startMs=$startPositionMs mpvStart=${getMpvPropertyString("start")} timePos=${getMpvPropertyString("time-pos")} eof=${getMpvPropertyString("eof-reached")} cacheEnd=${getMpvPropertyString("demuxer-cache-time")} url=${currentLink?.url?.take(50)}"

    private var lastStatus: CSPlayerLoading = CSPlayerLoading.IsBuffering
    private fun updateStatus() {
        // a file that is still opening is "loading", not "playing": the UI keeps its loading overlay up until the first frame
        val opening = (!fileLoaded || !firstFrameLogged) && (currentLink != null || currentUri != null)
        val currentStatus = when {
            isEnded -> CSPlayerLoading.IsEnded
            isBuffering || isSeeking || opening -> CSPlayerLoading.IsBuffering
            isPaused -> CSPlayerLoading.IsPaused
            else -> CSPlayerLoading.IsPlaying
        }
        val prev = lastStatus
        lastStatus = currentStatus
        postEvent(StatusEvent(prev, currentStatus))
        val state = currentPlaybackState()
        val playing = isPlaying
        if (state != lastPlaybackState) {
            lastPlaybackState = state
            notifyListeners { it.onPlaybackStateChanged(state) }
        }
        if (playing != lastIsPlaying) {
            lastIsPlaying = playing
            notifyListeners { it.onIsPlayingChanged(playing) }
        }
    }

    fun getMpvPropertyString(name: String): String? {
        val ctx = handle ?: return null
        val ptr = mpv.mpv_get_property_string(ctx, name) ?: return null
        return try {
            ptr.getString(0, "UTF-8")
        } finally {
            mpv.mpv_free(ptr)
        }
    }

    fun setMpvProperty(name: String, value: String) {
        val ctx = handle ?: return
        mpv.mpv_set_property_string(ctx, name, value)
    }

    /** A new audio decoder choice (SW / HW / HW+) while a video plays: mpv reopens the sound in place, the position stays */
    fun applyAudioDecoder() = setMpvProperty("audio-spdif", com.lagradost.desktop.ui.fluent.Appearance.audioDecoder.spdif)

    /** How the sound of the playing track is decoded: its codec, and whether it goes undecoded to the receiver ("spdif-ac3") */
    fun audioDecodingNow(): String? {
        val codec = getMpvPropertyString("audio-codec-name")?.takeIf { it.isNotBlank() } ?: return null
        val format = getMpvPropertyString("audio-params/format").orEmpty()
        return if (format.startsWith("spdif")) "$codec sent to the receiver / TV" else "$codec decoded by the app"
    }

    fun mpvCommandResult(vararg args: String): Int {
        val ctx = handle ?: return -1
        val arr = arrayOfNulls<String>(args.size + 1)
        for (i in args.indices) arr[i] = args[i]
        arr[args.size] = null
        return mpv.mpv_command(ctx, arr)
    }

    fun mpvCommand(vararg args: String) {
        val ctx = handle ?: return
        val arr = arrayOfNulls<String>(args.size + 1)
        for (i in args.indices) {
            arr[i] = args[i]
        }
        arr[args.size] = null
        mpv.mpv_command(ctx, arr)
    }

    override fun getPlaybackSpeed(): Float = currentSpeed

    override fun setPlaybackSpeed(speed: Float) {
        currentSpeed = speed
        setMpvProperty("speed", speed.toString())
    }

    override fun getIsPlaying(): Boolean = isPlaying

    override fun getDuration(): Long? = if (currentDurationMs > 0) currentDurationMs else null

    override fun getPosition(): Long? = currentPositionMs

    override fun seekTime(time: Long, source: PlayerEventSource) {
        if (liveStream) return seekTo(currentPositionMs + time, source)
        mpvCommand("seek", (time / 1000.0).toString(), "relative")
    }

    override fun seekTo(time: Long, source: PlayerEventSource) {
        val target = if (liveStream) liveTarget(time / 1000.0) else time / 1000.0
        mpvCommand("seek", String.format(java.util.Locale.ROOT, "%.3f", target), "absolute")
    }

    // ---- live streams ------------------------------------------------------------------------------------------------

    /** The address mpv plays (a proxy address for HLS / DASH) */
    @Volatile
    private var playingAddress: String? = null

    /** A live channel: a dynamic DASH manifest, an HLS playlist without an end, or a timeline that counts from the wall clock */
    @Volatile
    var liveStream = false
        private set

    private fun detectLive(): Boolean {
        val a = playingAddress ?: return false
        if (DashProxy.isLive(a) || HlsProxy.isLive(a)) return true
        return (getMpvPropertyString("demuxer-start-time")?.toDoubleOrNull() ?: 0.0) > 1.0e9
    }

    /** The buffered stretch (time-pos seconds) that playback can move in; null when mpv does not say */
    fun liveRange(): Pair<Double, Double>? {
        val state = getMpvPropertyString("demuxer-cache-state") ?: return null
        val ranges = Regex("""\{"start":(-?[\d.eE+-]+),"end":(-?[\d.eE+-]+)\}""").findAll(state.substringAfter("seekable-ranges", ""))
            .mapNotNull { m -> val s = m.groupValues[1].toDoubleOrNull(); val e = m.groupValues[2].toDoubleOrNull(); if (s != null && e != null && e > s) s to e else null }.toList()
        if (ranges.isEmpty()) return null
        val pos = currentPositionMs / 1000.0
        return ranges.firstOrNull { pos >= it.first - 1 && pos <= it.second + 1 } ?: ranges.last()
    }

    /**
     * Where a seek in a live stream may go. ffmpeg cannot seek in live DASH/HLS (its seek answers "not supported"), so mpv can
     * only move inside what it has buffered; a target past that (+1 min on a channel whose buffer is 20 s) left the demuxer
     * stuck for good, and a target right at the end of the buffer played at the live edge with nothing in reserve (stalls,
     * picture and sound drifting apart). The target stays inside the buffer and [LIVE_MARGIN_S] short of its end.
     */
    private fun liveTarget(wanted: Double): Double {
        val (start, end) = liveRange() ?: return currentPositionMs / 1000.0
        val low = start + 0.3
        val high = maxOf(low, if (end - LIVE_MARGIN_S > low) end - LIVE_MARGIN_S else (start + end) / 2)
        val target = wanted.coerceIn(low, high)
        if (target != wanted) Log.i(TAG, "live seek to ${"%.1f".format(wanted)} kept inside the buffer: ${"%.1f".format(target)} (buffer ${"%.1f".format(start)}..${"%.1f".format(end)})")
        return target
    }

    /** Live: back to the newest picture (as close to the live point as the buffer allows) */
    fun goLive() {
        if (!liveStream) return
        val range = liveRange() ?: return
        seekTo((range.second * 1000).toLong(), PlayerEventSource.UI)
    }

    /** How far (s) playback is behind the newest buffered picture of a live stream */
    fun liveLatency(): Double? = if (!liveStream) null else liveRange()?.let { (it.second - currentPositionMs / 1000.0).coerceAtLeast(0.0) }

    // a live channel that waits for data for this long is opened again at the live point (a stuck demuxer never recovers)
    @Volatile
    private var bufferingSince = 0L
    private var lastLiveReopen = 0L
    private val liveStallCheck = Runnable {
        val since = bufferingSince
        if (liveStream && isBuffering && !isPaused && fileLoaded && since > 0 && System.currentTimeMillis() - since >= LIVE_STALL_MS - 500 && System.currentTimeMillis() - lastLiveReopen > 30_000) {
            val link = currentLink
            val ctx = currentContext
            if (link != null && ctx != null) {
                lastLiveReopen = System.currentTimeMillis()
                Log.i(TAG, "live stream waited ${LIVE_STALL_MS / 1000} s for data: opening it again at the live point")
                loadPlayer(ctx, true, link, currentUri, null, activeSubtitles, preferredSubtitle, true, false)
            }
        }
    }

    /** Not paused by the user, the file is open and mpv is paused anyway: play */
    private fun ensurePlaying() {
        if (userPaused || isEnded || !fileLoaded) return
        if (getMpvPropertyString("pause") == "yes") {
            Log.i(TAG, "paused by mpv itself after a source change: playing")
            setMpvProperty("pause", "no")
        }
    }

    override fun getSubtitleOffset(): Long = currentSubtitleOffsetMs

    override fun setSubtitleOffset(offset: Long) {
        currentSubtitleOffsetMs = offset
        setMpvProperty("sub-delay", (offset / 1000.0).toString())
    }

    @AnyThread
    override fun initCallbacks(
        @MainThread eventHandler: (PlayerEvent) -> Unit,
        requestedListeningPercentages: List<Int>?
    ) {
        this.eventHandler = eventHandler
        this.requestedListeningPercentages = requestedListeningPercentages
        this.reportedPercentages.clear()
        if (handle != null) {
            postEvent(PlayerAttachedEvent(exoPlayer))
        }
    }

    override fun releaseCallbacks() {
        this.eventHandler = null
    }

    override fun updateSubtitleStyle(style: SaveCaptionStyle) {
        if (handle == null) return
        SubtitleStyler.apply(this, style)
    }

    override fun saveData() {}

    override fun addTimeStamps(timeStamps: List<VideoSkipStamp>) {
        this.timeStamps = timeStamps
    }

    override fun loadPlayer(
        context: Context,
        sameEpisode: Boolean,
        link: ExtractorLink?,
        data: ExtractorUri?,
        startPosition: Long?,
        subtitles: Set<SubtitleData>,
        subtitle: SubtitleData?,
        autoPlay: Boolean?,
        preview: Boolean
    ) {
        initMpvIfNeeded()
        currentContext = context
        // a DASH manifest is looked at first, off this thread: an on-demand one (one file per quality) is played as its files, which
        // can be seeked; ffmpeg's DASH reader cannot seek in them (OnDemandDash)
        val generation = ++loadGeneration
        if (link != null && isDash(link) && link !is com.lagradost.cloudstream3.utils.DrmExtractorLink && link.url.startsWith("http", ignoreCase = true) &&
            !link.url.contains("127.0.0.1") && !onDemand.containsKey(link.url)
        ) {
            val headers = buildMap {
                if (link.headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) put("User-Agent", defaultUserAgent)
                putAll(link.headers)
                if (link.referer.isNotBlank() && link.headers.keys.none { it.equals("Referer", ignoreCase = true) }) put("Referer", link.referer)
            }
            Thread({
                val files = runCatching { OnDemandDash.resolve(com.lagradost.desktop.net.UrlFix.encode(link.url), headers) }
                    .onFailure { Log.w(TAG, "DASH manifest not read ahead: ${it.message}") }.getOrNull()
                onDemand[link.url] = files ?: NO_FILES
                mainHandler.post {
                    if (generation == loadGeneration && !isReleased) loadPlayer(context, sameEpisode, link, data, startPosition, subtitles, subtitle, autoPlay, preview)
                }
            }, "dash-probe").apply { isDaemon = true }.start()
            return
        }
        val onDemandFiles = link?.let { onDemand[it.url] }?.takeIf { it !== NO_FILES }
        if (link != null && link.url != lastLinkUrl) {
            lastLinkUrl = link.url
            // HLS goes through the local playlist server: it loads the many playlists of a master at the same time (ffmpeg asks
            // for them one after the other, ~10 s for a service that lists dozens of subtitle playlists) and repairs wrappers;
            // DASH goes through DashProxy, which keeps live channels from repeating a segment (the "2 second loop")
            useProxy = (isHls(link) || isDash(link)) && link.url.startsWith("http", ignoreCase = true) && !link.url.contains("127.0.0.1")
            proxyTried = false
            useRangeProxy = false
            // the extension decodes or answers some requests itself (getVideoInterceptor, which ExoPlayer applies on Android): files go through the range server with it
            if (videoInterceptor(link) != null && !isHls(link) && !isDash(link) && link.url.startsWith("http", ignoreCase = true)) useRangeProxy = true
            rangeTried = false
        }
        lastHttpStatus = 0
        currentLink = link
        currentUri = data
        startPositionMs = startPosition
        activeSubtitles = subtitles.toMutableSet()
        preferredSubtitle = subtitle

        // DRM: ClearKey keys go to ffmpeg's CENC decryption; other schemes need a CDM a desktop player
        // does not have, so they fail like an unsupported scheme and the UI moves to the next link
        val drm = link as? com.lagradost.cloudstream3.utils.DrmExtractorLink
        Log.i(TAG, "load ${link?.type} ${link?.name}: drm=${drm?.uuid} key=${drm?.key != null} license=${drm?.licenseUrl != null} url=${link?.url?.take(1500)} referer=${link?.referer} headers=${link?.headers}")
        // a source that answers with a web page instead of a video address (JIO TV's playlist service sends a decoy video page when it refuses
        // a request): mpv would take the page for a file name and sit there for 30 s, so the player says what happened at once
        if (link != null && link.url.trimStart().let { it.startsWith("<") || it.contains('\n') }) {
            Log.w(TAG, "the source gave a web page instead of a video address (${link.name}): ${link.url.take(80).replace('\n', ' ')}")
            postEvent(ErrorEvent(androidx.media3.common.PlaybackException("The source returned a web page instead of a video address", null, androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED)))
            return
        }
        if (drm != null && drm.uuid != com.lagradost.cloudstream3.utils.CLEARKEY_DRM_UUID) {
            postEvent(ErrorEvent(androidx.media3.common.PlaybackException("Unsupported DRM scheme ${drm.uuid}", null, androidx.media3.common.PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED)))
            return
        }
        val keyHex = drm?.key?.let { clearKeyHex(it) }
        if (drm != null && keyHex == null) {
            postEvent(ErrorEvent(androidx.media3.common.PlaybackException("ClearKey link without a key", null, androidx.media3.common.PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED)))
            return
        }
        val kidHex = drm?.kid?.let { clearKeyHex(it) }
        setMpvProperty(
            "demuxer-lavf-o",
            buildString {
                // dropped connections (also of HLS segments) are retried
                append("reconnect=1,reconnect_streamed=1,reconnect_delay_max=5")
                if (keyHex != null) {
                    append(",decryption_key=").append(keyHex)
                    append(",cenc_decryption_key=").append(keyHex)
                    if (kidHex != null) append(",decryption_keys=").append(kidHex).append(':').append(keyHex)
                }
            }
        )

        // http-header-fields is a string list: one entry per header (commas in values stay intact with append)
        // nothing shows until the player logic selects a track: the list and the picture must agree
        setMpvProperty("sid", "no")
        subtitlesDisabled = false
        mpvCommand("change-list", "http-header-fields", "clr", "")
        setMpvProperty("referrer", "")
        hlsAddress = ""
        // another stream: the best quality is chosen again (the same one reloaded keeps the viewer's choice)
        if (link?.url != lastQualityUrl) { videoChosen = false; lastQualityUrl = link?.url }
        val url = when {
            link != null -> {
                setMpvProperty("user-agent", defaultUserAgent)
                for ((key, value) in link.headers) {
                    when {
                        key.equals("User-Agent", ignoreCase = true) -> setMpvProperty("user-agent", value)
                        key.equals("Referer", ignoreCase = true) -> setMpvProperty("referrer", value)
                        // ExoPlayer sets these per request itself; a fixed "Range: bytes=0-" (SuperStream) sent beside mpv's own Range made
                        // servers answer every seek from the start, or refuse the request
                        HOP_HEADERS.any { key.equals(it, ignoreCase = true) } -> {}
                        else -> mpvCommand("change-list", "http-header-fields", "append", "$key: $value")
                    }
                }
                if (link.referer.isNotBlank() && link.headers.keys.none { it.equals("Referer", ignoreCase = true) }) {
                    setMpvProperty("referrer", link.referer)
                }
                val address = com.lagradost.desktop.net.UrlFix.encode(link.url)
                // mpv fetches the files itself, with the headers set above
                if (onDemandFiles != null) onDemandFiles.video
                else if (useRangeProxy) RangeProxy.wrap(address, buildMap {
                    if (link.headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) put("User-Agent", defaultUserAgent)
                    putAll(link.headers)
                    if (link.referer.isNotBlank() && link.headers.keys.none { it.equals("Referer", ignoreCase = true) }) put("Referer", link.referer)
                }, videoInterceptor(link)) else if (useProxy) {
                    // the playlists (DASH: the manifest and the segments) are fetched with what mpv would have sent
                    val sent = buildMap {
                        if (link.headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) put("User-Agent", defaultUserAgent)
                        putAll(link.headers)
                        if (link.referer.isNotBlank() && link.headers.keys.none { it.equals("Referer", ignoreCase = true) }) put("Referer", link.referer)
                    }
                    if (isDash(link)) DashProxy.wrap(address, sent) else HlsProxy.wrap(address, sent, videoInterceptor(link)).also { hlsAddress = it }
                } else address
            }
            data != null -> data.uri.toString()
            else -> null
        }
        playingAddress = url
        liveStream = false

        if (url != null) {
            // the start position is an option of the file that opens next: a seek sent right after loadfile is lost
            // while the (slow) HLS playlist is still opening, which made every resume start at 0:00
            pendingSeekMs = 0L
            if (startPosition != null && startPosition > 0) {
                val rc = mpv.mpv_set_property_string(handle!!, "start", String.format(java.util.Locale.ROOT, "%.3f", startPosition / 1000.0))
                if (rc < 0) pendingSeekMs = startPosition
            } else {
                mpv.mpv_set_property_string(handle!!, "start", "none")
            }
            loadStartedAt = System.currentTimeMillis()
            // from now on this is "opening", not the state of the file that played before
            fileLoaded = false
            firstFrameLogged = false
            isEnded = false
            userPaused = autoPlay != true
            // the audio track of a DASH stream is chosen here, see setPreferredAudioTrack
            val audioId = dashAudio?.takeIf { link != null && it.first == link.url && onDemandFiles == null }?.second
            // the sound of an on-demand manifest is a file of its own, opened beside the video (%n% quotes the address for mpv's option list)
            // the app's own HLS playlists (OnDemandDash) are read as HLS only: when one does not open (a segment server refusing TLS), mpv tried
            // the other readers on the stream it could not rewind and crashed (IStreamFlare "Mandaadi")
            // (only the reader is chosen: demuxer-lavf-format=hls also applied to the subtitle files added later and every SRT failed to open, MovieBox)
            val forceHls = onDemandFiles?.video?.contains("/gen/") == true
            // a source with video only (YouTube's adaptive streams) names its sound as a separate file: opened beside the video
            val soundFile = onDemandFiles?.audio ?: link?.audioTracks?.firstOrNull()?.url?.takeIf { it.startsWith("http", ignoreCase = true) }
            val options = listOfNotNull(audioId?.let { "aid=$it" }, soundFile?.let { "audio-files-append=%${it.toByteArray().size}%$it" }, if (forceHls) "demuxer=lavf" else null)
            if (options.isNotEmpty()) mpvCommand("loadfile", url, "replace", "-1", options.joinToString(",")) else mpvCommand("loadfile", url, "replace")
            setMpvProperty("pause", if (autoPlay == true) "no" else "yes")
            isPaused = autoPlay != true
            isPlaying = autoPlay == true

            // subtitles are added and selected once the file is open (FILE_LOADED), see syncSubtitles
        }
    }

    private var interceptorFor: Pair<String, okhttp3.Interceptor?>? = null

    /** The video interceptor of the extension that made [link] (MainAPI.getVideoInterceptor), asked once per link like CS3IPlayer does */
    private fun videoInterceptor(link: ExtractorLink): okhttp3.Interceptor? {
        interceptorFor?.let { (url, interceptor) -> if (url == link.url) return interceptor }
        val interceptor = runCatching { com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(link.source)?.getVideoInterceptor(link) }
            .onFailure { Log.w(TAG, "getVideoInterceptor of ${link.source} failed: ${it.message}") }.getOrNull()
        if (interceptor != null) Log.i(TAG, "${link.source} has a video interceptor: its requests go through it")
        interceptorFor = link.url to interceptor
        return interceptor
    }

    /** A native core whose video output did not start (no Direct3D 11, a driver problem): the standard player is used from now on, and this video restarts in it */
    private fun checkNativeOutput() {
        if (!native || handle == null || isReleased) return
        val hasVideo = getMpvPropertyString("vid")?.let { it != "no" && it != "false" } == true
        if (hasVideo && getMpvPropertyString("current-vo").isNullOrBlank()) {
            Log.w(TAG, "native video output did not start: switching to the standard player")
            com.lagradost.desktop.ui.screens.player.NativeVideo.broken = true
            com.lagradost.desktop.ui.Toasts.show("The native video player could not start, using the standard one", true)
            com.lagradost.desktop.ui.screens.player.PlayerSession.active?.restartPlayback()
        }
    }

    private fun isHls(link: ExtractorLink): Boolean =
        link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8 || link.url.contains(".m3u8", ignoreCase = true)

    private fun isDash(link: ExtractorLink): Boolean =
        link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.DASH || link.url.substringBefore('?').endsWith(".mpd", ignoreCase = true)

    /** A single http(s) file (not a playlist, DASH manifest, torrent or local server address) */
    private fun isPlainFile(link: ExtractorLink): Boolean =
        link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.VIDEO && link.url.startsWith("http", ignoreCase = true) &&
            !link.url.contains("127.0.0.1") && link.url.substringBefore('?').substringAfterLast('.', "").lowercase() !in setOf("mpd", "m3u8")

    /** ClearKey JWK values are base64url; some extensions give hex. Returns 32 hex digits or null. */
    private fun clearKeyHex(value: String): String? {
        val v = value.trim()
        if (v.length == 32 && v.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return v.lowercase()
        return runCatching {
            val bytes = java.util.Base64.getUrlDecoder().decode(v.replace('+', '-').replace('/', '_').trimEnd('='))
            if (bytes.size != 16) null else bytes.joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    override fun reloadPlayer(context: Context) {
        loadPlayer(
            context = context,
            sameEpisode = true,
            link = currentLink,
            data = currentUri,
            // before the file opened there is no position yet: carry on from where this load was meant to start
            startPosition = if (fileLoaded && currentPositionMs > 0) currentPositionMs else startPositionMs,
            subtitles = activeSubtitles,
            subtitle = preferredSubtitle,
            autoPlay = !userPaused,
            preview = false
        )
    }

    override fun getPreview(fraction: Float): Bitmap? = null
    override fun hasPreview(): Boolean = false

    // ------------------------------------------------------------------------------------------
    // Subtitles. mpv's track list is the truth: what is listed, what is selected and what is drawn
    // always agree. Embedded tracks are published as SubtitleData (origin EMBEDDED_IN_VIDEO, url = the
    // mpv track id); external ones are added to the playing file with sub-add and remembered by id.

    private fun subtitleTask(block: () -> Unit) {
        subExecutor.execute { try { block() } catch (t: Throwable) { Log.w(TAG, "subtitles: ${t.message}", t) } }
    }

    class MpvTrack(
        val type: String,
        val id: Int,
        val title: String?,
        val lang: String?,
        val codec: String?,
        val selected: Boolean,
        val external: Boolean,
        val externalFile: String?,
    )

    fun tracks(): List<MpvTrack> {
        val count = getMpvPropertyString("track-list/count")?.toIntOrNull() ?: return emptyList()
        return (0 until count).mapNotNull { i ->
            val p = "track-list/$i/"
            val type = getMpvPropertyString(p + "type") ?: return@mapNotNull null
            val id = getMpvPropertyString(p + "id")?.toIntOrNull() ?: return@mapNotNull null
            MpvTrack(
                type, id, getMpvPropertyString(p + "title"), getMpvPropertyString(p + "lang"), getMpvPropertyString(p + "codec"),
                getMpvPropertyString(p + "selected") == "yes", getMpvPropertyString(p + "external") == "yes", getMpvPropertyString(p + "external-filename"),
            )
        }
    }

    /**
     * Subtitle renditions of the HLS master that are one whole file (the playlist server left them out, see HlsProxy.streamSubtitles):
     * added to mpv as subtitle files and listed with the stream's own subtitles
     */
    private fun addStreamSubtitles() {
        val address = hlsAddress.takeIf { it.isNotEmpty() } ?: return
        val generation = fileGeneration
        for (sub in HlsProxy.streamSubtitles(address)) {
            if (handle == null || generation != fileGeneration) return
            val file = shiftedStreamSubtitle(sub.url) ?: sub.url
            if (tracks().any { it.type == "sub" && it.externalFile == file }) continue
            val rc = mpvCommandResult("sub-add", file, "auto", sub.name, sub.language ?: "")
            val track = tracks().lastOrNull { it.type == "sub" && it.externalFile == file }
            if (rc >= 0 && track != null) streamSubtitleTracks.add(track.id) else Log.w(TAG, "stream subtitle ${sub.name} not added ($rc)")
        }
    }

    /**
     * CNCVerse's whole-file subtitles (no X-TIMESTAMP-MAP) are timed ~0.85 s later than their streams: measured against the speech onsets of the
     * audio (Netflix Squid Game: 0.71-0.93 s over 5 lines, Prime Sardar 2: 0.75-1.0 s; the same with the previous libmpv). The file is fetched once
     * and added with its cues moved earlier by that much; null = could not be fetched (the link is added as it is).
     */
    private fun shiftedStreamSubtitle(url: String): String? = shiftedStreamSubs.getOrPut(url) {
        runCatching {
            val text = kotlinx.coroutines.runBlocking { com.lagradost.cloudstream3.app.get(url, timeout = 15).text }
            if (!text.trimStart().startsWith("WEBVTT") || text.contains("X-TIMESTAMP-MAP")) return@runCatching ""
            val time = Regex("(?:(\\d+):)?(\\d{2}):(\\d{2})\\.(\\d{3})")
            val shifted = text.lineSequence().joinToString("\n") { line ->
                if (!line.contains("-->")) line else time.replace(line) { m ->
                    val (h, mi, s, ms) = m.destructured
                    val t = ((h.ifEmpty { "0" }.toLong() * 3600 + mi.toLong() * 60 + s.toLong()) * 1000 + ms.toLong() - STREAM_SUB_SHIFT_MS).coerceAtLeast(0)
                    "%02d:%02d:%02d.%03d".format(t / 3_600_000, t / 60_000 % 60, t / 1000 % 60, t % 1000)
                }
            }
            java.io.File.createTempFile("streamsub-", ".vtt", java.io.File(com.lagradost.desktop.runtime.AndroidRuntime.dataDir, "cache").also { it.mkdirs() }).apply { deleteOnExit(); writeText(shifted) }.absolutePath
        }.onFailure { Log.w(TAG, "stream subtitle not fetched: ${it.message}") }.getOrDefault("")
    }.takeIf { it.isNotEmpty() }

    private val shiftedStreamSubs = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Called once the file is open: adds the external subtitles, lists the embedded ones, selects the preferred one */
    private fun syncSubtitles() {
        if (handle == null || !fileLoaded) return
        publishEmbeddedSubtitles()
        // only the wanted one is fetched: a source can list hundreds of subtitles, and fetching them all kept the workers busy for minutes
        // so that the one the viewer picked waited in the queue (the other files are fetched when they are picked)
        applySubtitleSelection()
    }

    private val pendingAdds = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.FutureTask<Int?>>()
    private val trackMapLock = Any()

    /** The mpv track of an external subtitle, added to the playing file when it is not there yet (one add per file) */
    private val externalFailedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    /** Downloaded copies of online subtitles by link: a link that works once (a signed one) must not be needed again after a reload */
    private val downloadedSubtitles = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun ensureExternal(sub: SubtitleData): Int? {
        externalTracks[sub.getId()]?.let { known ->
            if (known >= 0) {
                // the track must still be in mpv's list (it is not after a reload or when mpv dropped it)
                if (tracks().any { it.type == "sub" && it.external && it.id == known }) return known
                externalTracks.remove(sub.getId())
            } else if (System.currentTimeMillis() - (externalFailedAt[sub.getId()] ?: 0L) < 15_000L) {
                return null
            } else {
                // it failed a while ago (a slow server, a dropped connection): once more, instead of never
                externalTracks.remove(sub.getId())
            }
        }
        val task = java.util.concurrent.FutureTask<Int?> { addExternal(sub) }
        val running = pendingAdds.putIfAbsent(sub.getId(), task)
        if (running != null) return runCatching { running.get(70, java.util.concurrent.TimeUnit.SECONDS) }.getOrNull()
        try {
            task.run()
        } finally {
            pendingAdds.remove(sub.getId())
        }
        return runCatching { task.get() }.getOrNull()
    }

    private fun addExternal(sub: SubtitleData): Int? {
        val generation = fileGeneration
        var url = com.lagradost.desktop.net.UrlFix.encode(sub.getFixedUrl())
        if (url.isBlank()) return null
        // file: URIs (Java writes file:/C:/...) become plain paths, which is what mpv opens
        if (url.startsWith("file:", ignoreCase = true)) url = runCatching { java.io.File(java.net.URI(url)).path }.getOrDefault(url)
        fun addArgs(file: String) = arrayListOf("sub-add", file, "auto", sub.name.trim().ifBlank { "Subtitle" }).also { a -> sub.languageCode?.takeIf { it.isNotBlank() }?.let { a += it } }
        val link = url
        var webPage = false
        if (url.startsWith("http", ignoreCase = true)) {
            // the app's client (DNS over HTTPS, the link's headers) fetches it once and mpv opens the file: the subtitle then does not depend on the link
            // answering again when the track is switched away from and back to, or the video is reloaded
            val cached = downloadedSubtitles[link]?.takeIf { java.io.File(it).isFile }
            val got = if (cached != null) DownloadedSubtitle(cached, false) else downloadSubtitle(sub, link)
            got.path?.let { downloadedSubtitles[link] = it; url = it }
            webPage = got.webPage
        }
        var result = if (webPage) -1 else mpvCommandResult(*addArgs(url).toTypedArray())
        Log.i(TAG, "sub-add ${sub.name} (${url.take(160)}): ${if (webPage) "a web page, not a subtitle" else mpv.mpv_error_string(result)}")
        if (generation != fileGeneration) return null // another file is playing now
        synchronized(trackMapLock) {
            if (result < 0) { externalTracks[sub.getId()] = -1; externalFailedAt[sub.getId()] = System.currentTimeMillis(); return null }
            val taken = externalTracks.values.toSet()
            val track = tracks().lastOrNull { it.type == "sub" && it.external && it.externalFile == url && it.id !in taken } ?: run {
                externalTracks[sub.getId()] = -1
                externalFailedAt[sub.getId()] = System.currentTimeMillis()
                return null
            }
            externalTracks[sub.getId()] = track.id
            return track.id
        }
    }

    /** Fetches a subtitle file with the app's HTTP client into the cache folder; null when it is not a subtitle (a block page, an error) */
    private class DownloadedSubtitle(val path: String?, val webPage: Boolean)

    private fun downloadSubtitle(sub: SubtitleData, url: String): DownloadedSubtitle = try {
        kotlinx.coroutines.runBlocking {
            var response = com.lagradost.cloudstream3.app.get(url, headers = sub.headers, timeout = 15)
            // a subtitle on the video's own site that is refused (Movy: 403) is asked for again the way the video is (its headers, referer)
            val link = currentLink
            fun site(u: String?) = runCatching { java.net.URI(u!!.trim()).host?.lowercase()?.split('.')?.takeLast(2)?.joinToString(".") }.getOrNull()
            if (!response.isSuccessful && link != null && site(url) != null && site(url) == site(link.url)) {
                val sent = buildMap { putAll(link.headers); if (link.referer.isNotBlank() && link.headers.keys.none { it.equals("Referer", true) }) put("Referer", link.referer); putAll(sub.headers) }
                Log.i(TAG, "subtitle refused (${response.code}): again with the video's headers")
                response = com.lagradost.cloudstream3.app.get(url, headers = sent, timeout = 15)
            }
            val bytes = response.okhttpResponse.body.bytes()
            val head = String(bytes, 0, minOf(bytes.size, 400), Charsets.UTF_8).trimStart()
            val html = response.okhttpResponse.header("Content-Type")?.contains("html", true) == true || head.startsWith("<!") || head.startsWith("<html", true) || head.startsWith("<meta", true)
            if (!response.isSuccessful || bytes.isEmpty() || html) {
                Log.w(TAG, "subtitle download ${response.code} ${bytes.size} bytes, ${if (html) "an HTML page, not a subtitle" else "no subtitle"}: ${head.take(100)}")
                DownloadedSubtitle(null, html)
            } else {
                val ext = sub.mimeType.let { m -> when { m.contains("vtt") -> ".vtt"; m.contains("ass") || m.contains("ssa") -> ".ass"; else -> ".srt" } }
                DownloadedSubtitle(java.io.File.createTempFile("subtitle-", ext, java.io.File(com.lagradost.desktop.runtime.AndroidRuntime.dataDir, "cache").also { it.mkdirs() }).apply { deleteOnExit(); writeBytes(bytes) }.absolutePath, false)
            }
        }
    } catch (t: Throwable) {
        Log.w(TAG, "subtitle download failed: ${t.message}")
        DownloadedSubtitle(null, false)
    }

    private fun publishEmbeddedSubtitles() {
        if (handle == null) return
        val list = tracks().filter { it.type == "sub" && (!it.external || it.id in streamSubtitleTracks) }.map { t ->
            val language = com.lagradost.cloudstream3.utils.SubtitleHelper.fromTagToLanguageName(t.lang) ?: t.lang?.takeIf { it.isNotBlank() }
            val name = language ?: t.title?.takeIf { it.isNotBlank() } ?: "Subtitle ${t.id}"
            val suffix = t.title?.takeIf { it.isNotBlank() && !it.equals(name, true) } ?: ""
            SubtitleData(name, suffix, t.id.toString(), SubtitleOrigin.EMBEDDED_IN_VIDEO, "application/x-subrip", emptyMap(), t.lang)
        }
        if (list == lastEmbedded) return
        lastEmbedded = list
        if (list.isNotEmpty()) postEvent(EmbeddedSubtitlesFetchedEvent(list))
    }

    /** Makes mpv show the preferred subtitle (or none after the user chose "none") */
    private fun applySubtitleSelection() {
        if (handle == null || !fileLoaded) return
        val sub = preferredSubtitle
        if (sub == null) {
            if (subtitlesDisabled) setMpvProperty("sid", "no")
            return
        }
        val generation = fileGeneration
        val embedded = sub.origin == SubtitleOrigin.EMBEDDED_IN_VIDEO
        // already on screen: nothing to do. Every batch of sources or subtitles that arrived (a source collects them for minutes) used to
        // select it again and report "loading" / "on", which popped the subtitle pill up over and over
        val known = if (embedded) sub.url.toIntOrNull() else externalTracks[sub.getId()]?.takeIf { it >= 0 }
        if (known != null && getMpvPropertyString("sid") == known.toString()) return
        if (!embedded) postEvent(SubtitleLoadEvent(SubtitleLoadState.Loading, sub))
        val id = if (embedded) sub.url.toIntOrNull() else ensureExternal(sub)
        // another file plays or another subtitle was chosen while this one was fetched: that choice has its own task
        if (handle == null || generation != fileGeneration || preferredSubtitle != sub) return
        Log.i(TAG, "subtitle selection: ${sub.name} -> track $id")
        if (id != null) {
            setMpvProperty("sid", id.toString())
            if (!embedded) postEvent(SubtitleLoadEvent(SubtitleLoadState.Loaded, sub))
        } else {
            Log.w(TAG, "could not load the subtitles \"${sub.name.trim()}\"")
            postEvent(SubtitleLoadEvent(SubtitleLoadState.Failed, sub))
        }
    }

    override fun setActiveSubtitles(subtitles: Set<SubtitleData>) {
        activeSubtitles = subtitles.toMutableSet()
        if (fileLoaded) subtitleTask { syncSubtitles() }
    }

    override fun setPreferredSubtitles(subtitle: SubtitleData?): Boolean {
        preferredSubtitle = subtitle
        subtitlesDisabled = subtitle == null
        // no reload: the track is switched in the running file
        if (fileLoaded) subtitleTask { applySubtitleSelection() }
        return false
    }

    override fun getCurrentPreferredSubtitle(): SubtitleData? {
        if (handle == null || !fileLoaded) return preferredSubtitle
        val sid = sidSnapshot?.toIntOrNull() ?: return null
        externalTracks.entries.firstOrNull { it.value == sid }?.key?.let { key ->
            activeSubtitles.firstOrNull { it.origin != SubtitleOrigin.EMBEDDED_IN_VIDEO && it.getId() == key }?.let { return it }
        }
        return activeSubtitles.firstOrNull { it.origin == SubtitleOrigin.EMBEDDED_IN_VIDEO && it.url == sid.toString() }
            ?: lastEmbedded.firstOrNull { it.url == sid.toString() }
    }

    override fun handleEvent(event: CSPlayerEvent, source: PlayerEventSource) {
        when (event) {
            // the "pause" property observer updates the state and emits Play/Pause and Status events
            CSPlayerEvent.Play -> { userPaused = false; setMpvProperty("pause", "no") }
            CSPlayerEvent.Pause -> { userPaused = true; setMpvProperty("pause", "yes") }
            CSPlayerEvent.PlayPauseToggle -> { userPaused = !isPaused; setMpvProperty("pause", if (isPaused) "no" else "yes") }
            CSPlayerEvent.SeekForward -> seekTime(10000L, source)
            CSPlayerEvent.SeekBack -> seekTime(-10000L, source)
            CSPlayerEvent.Restart -> seekTo(0L, source)
            CSPlayerEvent.ToggleMute -> {
                val mute = getMpvPropertyString("mute") == "yes"
                setMpvProperty("mute", if (mute) "no" else "yes")
            }
            CSPlayerEvent.NextEpisode -> postEvent(EpisodeSeekEvent(1, source))
            CSPlayerEvent.PrevEpisode -> postEvent(EpisodeSeekEvent(-1, source))
            CSPlayerEvent.SkipCurrentChapter -> {
                currentActiveStamp?.let {
                    seekTo(it.timestamp.endMs, source)
                    postEvent(TimestampSkippedEvent(it, source))
                }
            }
            else -> {}
        }
    }

    override fun onStop() {
        handleEvent(CSPlayerEvent.Pause)
    }

    override fun onPause() {
        handleEvent(CSPlayerEvent.Pause)
    }

    override fun onResume(context: Context) {}

    override fun release() {
        isReleased = true
        com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.applyStyleEvent -= styleListener
        com.lagradost.desktop.runtime.DesktopAudio.removeListener(volumeListener)
        // the surface stays attached (the PlayerView keeps this player) and shows nothing until the next load
        surface?.clearFrame()
        // Detach everything from the caller's thread at once; tearing the core down can block for seconds on a
        // stuck network read, which froze the whole window when leaving a video.
        val render = synchronized(renderLock) {
            val r = renderContext
            renderContext = null
            r
        }
        val ctx = handle
        handle = null
        fileLoaded = false
        val loop = eventThread
        eventThread = null
        if (render != null) runCatching { mpv.mpv_render_context_set_update_callback(render, null, null) }
        Thread({
            try {
                // the event loop polls this core; it must be gone before the core is destroyed
                loop?.let { if (it !== Thread.currentThread()) it.join(2000) }
                // the render context goes before the core (render.h)
                if (render != null) mpv.mpv_render_context_free(render)
                if (ctx != null) mpv.mpv_terminate_destroy(ctx)
            } catch (t: Throwable) {
                Log.w(TAG, "Error terminating mpv: ${t.message}")
            }
        }, "MpvPlayer-Destroy").apply { isDaemon = true; start() }
    }

    override fun isActive(): Boolean = handle != null && !isReleased

    // The track list is read by one background worker when mpv reports a change: asked on the UI thread, mpv blocks the call for as long as its core is busy
    @Volatile private var tracksSnapshot: CurrentTracks? = null
    @Volatile private var sidSnapshot: String? = null
    private val tracksExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "MpvPlayer-Tracks").apply { isDaemon = true } }

    private fun refreshTracks() {
        tracksExecutor.execute {
            try {
                if (handle == null) return@execute
                tracksSnapshot = readTracks()
                sidSnapshot = getMpvPropertyString("sid")
                postEvent(TracksChangedEvent())
            } catch (t: Throwable) { Log.w(TAG, "tracks: ${t.message}") }
        }
    }

    override fun getVideoTracks(): CurrentTracks = tracksSnapshot ?: CurrentTracks(null, null, emptyList(), emptyList(), emptyList(), emptyList())

    private fun readTracks(): CurrentTracks {
        val count = getMpvPropertyString("track-list/count")?.toIntOrNull() ?: 0

        val videos = mutableListOf<VideoTrack>()
        val audios = mutableListOf<AudioTrack>()
        val texts = mutableListOf<TextTrack>()
        var curVideo: VideoTrack? = null
        var curAudio: AudioTrack? = null
        val curTexts = mutableListOf<TextTrack>()

        for (i in 0 until count) {
            val type = getMpvPropertyString("track-list/$i/type") ?: continue
            val id = getMpvPropertyString("track-list/$i/id") ?: i.toString()
            val title = getMpvPropertyString("track-list/$i/title") ?: getMpvPropertyString("track-list/$i/lang")
            val lang = getMpvPropertyString("track-list/$i/lang")
            val selected = getMpvPropertyString("track-list/$i/selected") == "yes"
            val codec = getMpvPropertyString("track-list/$i/codec")

            when (type) {
                "video" -> {
                    val w = getMpvPropertyString("track-list/$i/demux-w")?.toIntOrNull()
                    val h = getMpvPropertyString("track-list/$i/demux-h")?.toIntOrNull()
                    val track = VideoTrack(id, title, lang, w, h, codec)
                    videos.add(track)
                    if (selected) curVideo = track
                }
                "audio" -> {
                    val channels = getMpvPropertyString("track-list/$i/demux-channel-count")?.toIntOrNull()
                    val track = AudioTrack(id, title, lang, codec, channels, i)
                    audios.add(track)
                    if (selected) curAudio = track
                }
                "sub" -> {
                    val track = TextTrack(id, title, lang, null)
                    texts.add(track)
                    if (selected) curTexts.add(track)
                }
            }
        }

        // an HLS master: each quality is an mpv "edition" (the track list only has the tracks of the current one), listed as video tracks
        if (getMpvPropertyString("file-format") == "hls") {
            val editions = getMpvPropertyString("edition-list/count")?.toIntOrNull() ?: 0
            if (editions > 1) {
                val current = getMpvPropertyString("current-edition")?.toIntOrNull()
                val variants = HlsProxy.variants(hlsAddress).takeIf { it.size == editions }
                val qualities = (0 until editions).map { n ->
                    val v = variants?.get(n)
                    val title = getMpvPropertyString("edition-list/$n/title")
                    VideoTrack("edition:$n", v?.height?.let { "${it}p" } ?: title, null, v?.width, v?.height, curVideo?.sampleMimeType)
                }.sortedByDescending { (it.height ?: 0) * 10_000L + (variants?.getOrNull(it.id!!.substringAfter(':').toInt())?.bandwidth ?: 0L) / 1000 }
                val currentQuality = qualities.firstOrNull { it.id == "edition:$current" }
                return CurrentTracks(currentQuality ?: curVideo, curAudio, curTexts, qualities, audios, texts)
            }
        }
        return CurrentTracks(curVideo, curAudio, curTexts, videos, audios, texts)
    }

    override fun getAspectRatio(): Rational? {
        if (currentWidth > 0 && currentHeight > 0) {
            return Rational(currentWidth, currentHeight)
        }
        return null
    }

    override fun setMaxVideoSize(width: Int, height: Int, id: String?) {
        if (id == null) return
        // a quality of an HLS master (see readTracks): mpv switches to that variant's streams in place
        if (id.startsWith("edition:")) {
            videoChosen = true
            setMpvProperty("edition", id.substringAfter(':'))
            mainHandler.postDelayed({ refreshTracks() }, 500)
        } else setMpvProperty("vid", id)
    }

    /** The viewer picked a quality of this stream: the automatic best choice no longer applies */
    @Volatile private var videoChosen = false
    private var lastQualityUrl: String? = null

    /** An HLS master opens on the variant mpv picks; the best one for the screen is chosen instead (as before: the highest up to the screen) */
    private fun chooseBestEdition() {
        if (videoChosen || getMpvPropertyString("file-format") != "hls") return
        val variants = HlsProxy.variants(hlsAddress)
        val editions = getMpvPropertyString("edition-list/count")?.toIntOrNull() ?: 0
        if (editions <= 1 || variants.size != editions) return
        val cap = runCatching { java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.maxOf { it.displayMode.height } }.getOrDefault(1080).coerceAtLeast(1080)
        val best = variants.withIndex().filter { (it.value.height ?: 0) in 1..cap }.maxWithOrNull(compareBy({ it.value.height ?: 0 }, { it.value.bandwidth }))
            ?: variants.withIndex().maxByOrNull { it.value.bandwidth } ?: return
        val current = getMpvPropertyString("current-edition")?.toIntOrNull()
        if (current != best.index) {
            Log.i(TAG, "HLS quality: ${best.value.height}p (variant ${best.index + 1} of ${variants.size}) instead of variant ${current?.plus(1)}")
            setMpvProperty("edition", best.index.toString())
        }
    }

    /** The audio track chosen for a DASH stream (its address, track id): it is part of the next open of that stream */
    @Volatile
    private var dashAudio: Pair<String, String>? = null

    override fun setPreferredAudioTrack(trackLanguage: String?, id: String?, formatIndex: Int?) {
        val link = currentLink
        val ctx = currentContext
        if (id != null && link != null && ctx != null && fileLoaded && link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.DASH) {
            // ffmpeg's dash demuxer takes ~45 s to recover from the seek mpv does when a track is switched in an open file (it
            // reads the manifest again for every representation, 15 for a JIO TV channel with six audio languages), and the
            // picture stands still meanwhile. Opening the stream with the track chosen takes ~6 s and needs no seek.
            dashAudio = link.url to id
            // a live manifest counts its time from the wall clock (a start time in the billions of seconds): carry on live, not at a position
            val live = liveStream
            loadPlayer(ctx, true, link, currentUri, if (live) null else currentPositionMs.takeIf { it > 0 }, activeSubtitles, preferredSubtitle, !userPaused, false)
        } else if (id != null) {
            setMpvProperty("aid", id)
        } else if (trackLanguage != null) {
            setMpvProperty("alang", trackLanguage)
        }
    }

    override fun getSubtitleCues(): List<SubtitleCue> = emptyList()
}
