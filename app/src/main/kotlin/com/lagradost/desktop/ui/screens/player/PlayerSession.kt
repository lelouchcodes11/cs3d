package com.lagradost.desktop.ui.screens.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.isEpisodeBased
import com.lagradost.cloudstream3.isLiveStream
import com.lagradost.cloudstream3.isMovieType
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.player.AudioTrack
import com.lagradost.cloudstream3.ui.player.CS3IPlayer
import com.lagradost.cloudstream3.ui.player.CSPlayerEvent
import com.lagradost.cloudstream3.ui.player.CSPlayerLoading
import com.lagradost.cloudstream3.ui.player.DisplayLink
import com.lagradost.cloudstream3.ui.player.DownloadEvent
import com.lagradost.cloudstream3.ui.player.EmbeddedSubtitlesFetchedEvent
import com.lagradost.cloudstream3.ui.player.EpisodeSeekEvent
import com.lagradost.cloudstream3.ui.player.ErrorEvent
import com.lagradost.cloudstream3.ui.player.NEXT_WATCH_EPISODE_PERCENTAGE
import com.lagradost.cloudstream3.ui.player.PRELOAD_NEXT_EPISODE_PERCENTAGE
import com.lagradost.cloudstream3.ui.player.PlayerEvent
import com.lagradost.cloudstream3.ui.player.PlayerGeneratorViewModel
import com.lagradost.cloudstream3.ui.player.PositionEvent
import com.lagradost.cloudstream3.ui.player.ResizedEvent
import com.lagradost.cloudstream3.ui.player.StatusEvent
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.ui.player.SubtitleOrigin
import com.lagradost.cloudstream3.ui.player.SubtitlesUpdatedEvent
import com.lagradost.cloudstream3.ui.player.TimestampInvokedEvent
import com.lagradost.cloudstream3.ui.player.TimestampSkippedEvent
import com.lagradost.cloudstream3.ui.player.TracksChangedEvent
import com.lagradost.cloudstream3.ui.player.UPDATE_SYNC_PROGRESS_PERCENTAGE
import com.lagradost.cloudstream3.ui.player.GeneratorPlayer
import com.lagradost.cloudstream3.ui.player.PlayerSubtitleHelper.Companion.toSubtitleMimeType
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleEntity
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleSearch
import com.lagradost.cloudstream3.LoadResponse.Companion.getAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.getImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.getMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.getTMDbId
import com.lagradost.cloudstream3.ui.player.VideoEndedEvent
import com.lagradost.cloudstream3.ui.player.VideoGenerator
import com.lagradost.cloudstream3.ui.player.VideoLink
import com.lagradost.cloudstream3.ui.player.source_priority.QualityDataHelper
import com.lagradost.cloudstream3.ui.player.source_priority.QualityDataHelper.getLinkPriority
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.ResultFragment
import com.lagradost.cloudstream3.ui.result.SyncViewModel
import com.lagradost.cloudstream3.ui.subtitles.SUBTITLE_AUTO_SELECT_KEY
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.Companion.getAutoSelectLanguageTagIETF
import com.lagradost.cloudstream3.utils.AppContextUtils.sortSubs
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.ui.player.ExtractorUri
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.videoskip.VideoSkipStamp
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.runtime.AndroidRuntime
import com.lagradost.desktop.runtime.ui.MpvSurfaceView
import com.lagradost.desktop.ui.Toasts
import com.lagradost.desktop.ui.fluent.Icons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Resize(val label: String, val keepAspect: Boolean, val panscan: Double) {
    Fit("Fit", true, 0.0),
    Fill("Stretch", false, 0.0),
    Zoom("Zoom to fill", true, 1.0),
}

/**
 * One playback page: loads the links of an episode through the engine's [PlayerGeneratorViewModel],
 * starts them in mpv in quality order, falls back to the next source on errors, saves the watch
 * position and moves between episodes. The native replacement of GeneratorPlayer's logic.
 */
class PlayerSession(
    private val vm: PlayerGeneratorViewModel,
    generator: VideoGenerator<*>,
    index: Int,
    private val sync: SyncViewModel,
    private val exit: () -> Unit,
) {
    private val ctx = DesktopBootstrap.activity
    private val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val player = CS3IPlayer()
    val surface = MpvSurfaceView(ctx)

    // ---- state read by the UI ----
    var status by mutableStateOf(CSPlayerLoading.IsBuffering); private set
    var positionMs by mutableStateOf(0L); private set
    var durationMs by mutableStateOf(0L); private set
    var bufferedMs by mutableStateOf(0L); private set
    var title by mutableStateOf(""); private set
    var episodeLabel by mutableStateOf<String?>(null); private set
    var sourceName by mutableStateOf<String?>(null); private set
    var resolution by mutableStateOf<String?>(null); private set

    /** Shown over the video until playback starts; null once a source plays */
    var loadingText by mutableStateOf<String?>("Looking for sources…"); private set

    /** When the source that is being started was asked for, so that the loading screen can say how long it has been waiting */
    var sourceStartedAt by mutableLongStateOf(0L); private set

    /** "Source 3 of 82" while a source is being started */
    var sourcePosition by mutableStateOf<String?>(null); private set

    /** A source is being opened (as opposed to sources still being looked for): the loading screen offers to skip it */
    val startingSource: Boolean get() = playerActive && loadingText != null && !waitingForMore

    /** The source in use failed, there is no other one yet and more are still being collected: continues when one arrives */
    private var waitingForMore = false
    val waitingForMoreSources: Boolean get() = waitingForMore
    var linksFound by mutableStateOf(0); private set
    var canSkipLoading by mutableStateOf(false); private set
    var failure by mutableStateOf<String?>(null); private set

    /** Sources are still being collected in the background (after "Play now", or while the first one plays) */
    var loadingMore by mutableStateOf(false); private set
    /** Bumped when the list of sources changed, so an open sources dialog shows new ones */
    var sourcesVersion by mutableStateOf(0); private set
    var activeStamp by mutableStateOf<VideoSkipStamp?>(null); private set

    var volume by mutableStateOf(prefs.getInt(VOLUME_KEY, 100)); private set
    var muted by mutableStateOf(false); private set
    var speed by mutableStateOf(1f); private set
    var resize by mutableStateOf(Resize.Fit); private set
    var subtitleDelayMs by mutableStateOf(0L); private set

    /** Short on-screen feedback for keyboard actions (volume, seek, speed ...), drawn like a macOS HUD */
    var hud by mutableStateOf<Hud?>(null); private set

    fun showHud(glyph: String, text: String, fraction: Float? = null) {
        hud = Hud(glyph, text, fraction)
    }

    fun clearHud(h: Hud) {
        if (hud === h) hud = null
    }

    /** Bumped when subtitle lists or track lists changed, so menus recompose */
    var listsVersion by mutableStateOf(0); private set
    var episodeVersion by mutableStateOf(0); private set

    // ---- internal ----
    private var selectedLink: VideoLink? = null
    private var selectedSubtitle: SubtitleData? = null
    /** The viewer chose a subtitle (or none) in this episode: the language based automatic choice must not take it back */
    private var subtitleChosenByUser = false
    private var preferredSubLang: String? = getAutoSelectLanguageTagIETF()
    private var playerActive = false
    private var isNextEpisode = false
    private var qualityProfile = 1
    private var maxEpisodeSet: Int? = null
    private var requestedStamps = false
    private var progressSavedAt = 0L
    private var progressLastPosition = -1L
    private var pendingProgress: (() -> Unit)? = null
    private var verifyJob: Job? = null
    // where the next source of this episode picks up: the saved position at first, then wherever the video was
    private var resumeMs = 0L
    private var released = false
    private var audioApplied = false
    private val removers = mutableListOf<() -> Unit>()

    private val currentMeta: Any? get() = vm.state.generatorState?.meta
    private val nextMeta: Any? get() = vm.state.generatorState?.nextMeta
    val hasNext: Boolean get() = vm.hasNextEpisode() == true
    val hasPrev: Boolean get() = vm.hasPrevEpisode() == true

    init {
        active = this
    }

    init {
        if (CS3IPlayer.preferredAudioTrackLanguage == null) CS3IPlayer.preferredAudioTrackLanguage = prefs.getString(AUDIO_LANG_KEY, null)
    }

    init {
        runCatching {
            val profiles = QualityDataHelper.getProfiles()
            qualityProfile = profiles.firstOrNull { it.types.contains(QualityDataHelper.QualityProfileType.WiFi) }?.id
                ?: profiles.firstOrNull()?.id ?: qualityProfile
        }
        player.setVideoSurface(surface)
        player.initCallbacks(::onEvent)
        updateTitle()
        applyVolume()
        vm.attachGenerator(generator, index)
        sync.updateUserData()

        fun <T> LiveData<T>.watch(block: (T?) -> Unit) {
            val observer = Observer<T> { block(it) }
            observeForever(observer)
            removers += { removeObserver(observer) }
        }
        vm.currentStamps.watch { live ->
            if (live == null || live.instance != vm.state.instance) return@watch
            player.addTimeStamps(live.value)
        }
        vm.currentSubtitles.watch { live ->
            if (live == null || live.instance != vm.state.instance) return@watch
            player.setActiveSubtitles(live.value)
            listsVersion++
            // downloaded subtitles can not be selected before the player loaded them
            if (live.value.lastOrNull()?.origin != SubtitleOrigin.DOWNLOADED_FILE) autoSelectSubtitles()
        }
        vm.loadingLinks.watch { live ->
            if (live == null || live.instance != vm.state.instance) return@watch
            when (val loading = live.value) {
                is Resource.Loading -> releaseForReload()
                is Resource.Success -> {
                    if (waitingForMore) {
                        waitingForMore = false
                        val another = firstUsableLink()
                        if (another != null) loadLink(another.link, true, "Trying another source…") else noMoreSources()
                    } else startPlayer()
                }
                is Resource.Failure -> {
                    Toasts.show(loading.errorString, true)
                    startPlayer()
                }
            }
        }
        vm.currentLinks.watch { live ->
            if (live == null || live.instance != vm.state.instance) return@watch
            val usable = vm.state.sortLinks(qualityProfile).count { it.shouldUseLink }
            linksFound = usable
            sourcesVersion++
            canSkipLoading = usable > 0 && vm.generator?.canSkipLoading == true
            if (title.isBlank()) updateTitle()
            if (waitingForMore) firstUsableLink()?.let {
                waitingForMore = false
                loadLink(it.link, true, "Trying another source…")
            }
            if (!playerActive && vm.state.links.any { link ->
                    getLinkPriority(qualityProfile, link.first) >=
                        QualityDataHelper.AUTO_SKIP_PRIORITY
                }
            ) startPlayer()
        }
        scope.launch {
            while (true) {
                val more = playerActive && vm.isLoadingLinks
                if (more != loadingMore) loadingMore = more
                if (playerActive) {
                    player.getPosition()?.let { positionMs = it }
                    if (loadingText == null && positionMs > 0) resumeMs = positionMs
                    player.getDuration()?.let { if (it > 0) durationMs = it }
                    bufferedMs = runCatching { (player.exoPlayer.bufferedPosition) }.getOrDefault(0L)
                    val pos = positionMs
                    val stamp = vm.state.stamps.firstOrNull { pos >= it.timestamp.startMs && pos < it.timestamp.endMs }
                    if (stamp != activeStamp) activeStamp = stamp
                }
                delay(250)
            }
        }
        watchStalls()
        vm.loadLinks()
    }

    // ---- a source that stops delivering data: retry once, then move on (instead of buffering for ever)
    private var stallPosition = -1L
    private var stallBuffered = -1L
    private var stallSince = 0L
    private var stallRetries = 0

    private fun watchStalls() {
        scope.launch {
            while (true) {
                delay(2000)
                val waiting = playerActive && failure == null && !waitingForMore && (startingSource || status == CSPlayerLoading.IsBuffering)
                val now = System.currentTimeMillis()
                if (!waiting || positionMs != stallPosition || bufferedMs != stallBuffered) {
                    stallPosition = positionMs
                    stallBuffered = bufferedMs
                    stallSince = now
                    continue
                }
                // a source that has not shown a picture after 20 s is not worth waiting for (it used to be 35 s); one that stops half way gets 25 s
                val limit = if (startingSource) 20_000 else 25_000
                if (now - stallSince > limit) {
                    stallSince = now
                    onStalled()
                }
            }
        }
    }

    private fun onStalled() {
        // a source that never opened (not one that stopped half way) fails the same way again: on to the next source at once
        val neverStarted = loadingText != null
        if (neverStarted && backToFallback("That source is not responding.")) return
        if (stallRetries == 0 && !(neverStarted && nextLink() != null)) {
            stallRetries++
            Toasts.show("The connection is slow, trying again…", false)
            player.reloadPlayer(ctx)
        } else {
            stallRetries = 0
            if (nextLink() != null) {
                Toasts.show("This source is not responding, trying the next one", false)
                nextMirror("That source is not responding. Trying the next one…")
            } else if (vm.isLoadingLinks) {
                waitForMoreSources("That source is not responding. Looking for another one…")
            } else {
                loadingText = null
                failure = "The source stopped responding."
            }
        }
    }

    /** The source in use failed and there is no other one yet: sources are still being collected, one of them is started when it arrives */
    /** The best source that has not failed (a source that arrived later may rank above the one that did) */
    private fun firstUsableLink(): DisplayLink? = vm.state.sortLinks(qualityProfile).firstOrNull { it.shouldUseLink }

    private fun waitForMoreSources(text: String) {
        selectedLink?.let { bad -> vm.modifyState { addError(bad) } }
        waitingForMore = true
        sourcePosition = null
        loadingText = text
    }

    private fun noMoreSources() {
        vm.forceClearCache = true
        loadingText = null
        failure = "None of the sources could be played."
    }

    // ------------------------------------------------------------------ playback

    private fun getPos(): Long {
        val durPos = getViewPos(vm.state.generatorState?.id) ?: return 0L
        if (durPos.duration == 0L) return 0L
        if (durPos.position * 100L / durPos.duration > 95L) return 0L
        return durPos.position
    }

    private fun startPlayer() {
        if (playerActive || released) return
        val first = vm.state.sortLinks(qualityProfile).firstOrNull { it.shouldUseLink }?.link
        if (first == null) {
            noLinksFound()
            return
        }
        resumeMs = if (isNextEpisode) 0L else getPos()
        positionMs = resumeMs
        loadLink(first, false)
        updateTitle()
    }

    private fun noLinksFound() {
        vm.forceClearCache = true
        failure = "No playable sources were found."
        loadingText = null
        Toasts.show("No links found", true)
    }

    /**
     * [reason] is what the loading screen says (a failed source: "trying the next one"); [resumeAt] carries on at that position instead
     * of where the playing video is (a stream that ended early is continued where it broke off).
     */
    private fun loadLink(link: VideoLink, sameEpisode: Boolean, reason: String? = null, resumeAt: Long? = null) {
        stallRetries = 0
        if (link != selectedLink) prematureRetries = 0
        // another source of the same episode carries on where the video was (not at 0:00)
        if (resumeAt != null) resumeMs = resumeAt
        else if (sameEpisode && playerActive && loadingText == null) player.getPosition()?.let { if (it > 0) resumeMs = it }
        playerActive = true
        waitingForMore = false
        selectedLink = link
        audioApplied = false
        failure = null
        loadingText = reason ?: "Starting playback…"
        playingSince = 0L
        loadStartMs = resumeMs
        sourceStartedAt = System.currentTimeMillis()
        val usable = vm.state.sortLinks(qualityProfile).filter { it.shouldUseLink }
        val at = usable.indexOfFirst { it.link == link }
        sourcePosition = if (at >= 0 && usable.size > 1) "Source ${at + 1} of ${usable.size}" else null
        sourceName = link.first?.name ?: link.second?.name
        resolution = null
        if (!sameEpisode) {
            requestedStamps = false
            activeStamp = null
        }
        updateTitle()
        verifyJob?.cancel()
        link.first?.let { l ->
            verifyJob = scope.launch(Dispatchers.IO) {
                runCatching {
                    if (l.extractorData != null) com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(l.source)?.extractorVerifierJob(l.extractorData)
                }
            }
        }
        val subtitles = vm.state.subtitles
        val (url, uri) = link
        player.loadPlayer(
            ctx, sameEpisode, url, uri,
            startPosition = resumeMs.takeIf { it > 0L },
            subtitles = subtitles,
            subtitle = (if (sameEpisode) selectedSubtitle else null) ?: autoSubtitle(subtitles, settings = true, downloads = true),
            preview = true,
        )
        if (!sameEpisode) {
            player.addTimeStamps(emptyList())
            player.setSubtitleOffset(0)
            subtitleDelayMs = 0
        }
        applyVolume()
        applyResize()
        player.setPlaybackSpeed(speed)
    }

    private fun releaseForReload() {
        flushProgress()
        player.release()
        selectedSubtitle = null
        subtitleChosenByUser = false
        selectedLink = null
        playerActive = false
        loadingText = "Looking for sources…"
        canSkipLoading = false
        vm.modifyState { setError(emptyList()) }
    }

    private fun nextLink(): DisplayLink? {
        val links = vm.state.sortLinks(qualityProfile)
        val current = links.indexOfFirst { it.link == selectedLink }
        return links.withIndex().firstOrNull { it.index > current && it.value.shouldUseLink }?.value
    }

    val hasNextMirror: Boolean get() = nextLink() != null

    fun nextMirror(reason: String? = null) {
        val next = nextLink()
        if (next == null) noLinksFound() else loadLink(next.link, true, reason)
    }

    // a stream that ends long before its end: tried again where it broke off once, then the next source
    private var prematureRetries = 0
    // when the picture of the current source appeared and from where it was started
    private var playingSince = 0L
    private var loadStartMs = 0L

    /**
     * mpv says "end of file". That is only true near the end of a long video. A stream that the server (or a dropped connection) cut
     * off earlier, or one that ends within seconds after it started, used to leave the player paused on a frame without any sign.
     */
    private fun endedTooEarly(): Boolean {
        val duration = durationMs
        if (duration < 120_000L) return false
        if ((currentMeta as? ResultEpisode)?.tvType?.isLiveStream() == true) return false
        val position = positionMs
        if (position < duration - 25_000L) return true
        val played = if (playingSince > 0) System.currentTimeMillis() - playingSince else 0L
        return played < 20_000L && loadStartMs < duration - 120_000L
    }

    private fun onPrematureEnd() {
        val link = selectedLink ?: return
        val duration = durationMs
        // where it broke off; a stream that "ended" right away carries on from where this load started
        val at = if (positionMs >= duration - 1_500L) loadStartMs else positionMs
        android.util.Log.w("NativePlayer", "ended too early at $positionMs of $duration, source ${link.first?.name?.lineSequence()?.firstOrNull()}")
        if (prematureRetries == 0) {
            prematureRetries++
            Toasts.show("The connection was lost, reconnecting…", false)
            loadLink(link, true, "The connection was lost. Reconnecting…", resumeAt = at)
            return
        }
        vm.modifyState { addError(link) }
        if (nextLink() != null) {
            Toasts.show("This source keeps stopping, trying the next one", false)
            nextMirror("That source keeps stopping. Trying the next one…")
        } else if (vm.isLoadingLinks) {
            waitForMoreSources("That source keeps stopping. Looking for another one…")
        } else {
            loadingText = null
            failure = "The video stopped before its end and no other source is left."
        }
    }

    private fun onPlayerError(error: Throwable) {
        if (backToFallback("That source could not be played.")) return
        selectedLink?.let { link -> vm.modifyState { addError(link) } }
        android.util.Log.e("NativePlayer", "playerError ${selectedLink?.first?.name}: ${error.message}", error)
        val message = error.message ?: error.toString()
        if (nextLink() != null) {
            Toasts.show("Source failed, trying the next one.\n$message", false)
            nextMirror("That source did not work. Trying the next one…")
        } else if (vm.isLoadingLinks) {
            // more sources are still coming in: not an error yet
            waitForMoreSources("That source did not work. Looking for another one…")
        } else {
            vm.forceClearCache = true
            loadingText = null
            val live = (currentMeta as? ResultEpisode)?.tvType?.isLiveStream() == true
            failure = "Playback failed: $message" + if (live) "\nLive events only have a stream while they are on; if this one has not started yet (or its link expired), try again closer to the start time." else ""
        }
    }

    // ------------------------------------------------------------------ events

    private fun onEvent(event: PlayerEvent) {
        when (event) {
            is StatusEvent -> {
                status = event.isPlaying
                if (event.isPlaying != CSPlayerLoading.IsPlaying) flushProgress()
                if (event.isPlaying == CSPlayerLoading.IsPlaying) {
                    if (loadingText != null || playingSince == 0L) playingSince = System.currentTimeMillis()
                    loadingText = null
                    sourcePosition = null
                    waitingForMore = false
                    fallbackLink = null
                    vm.forceClearCache = false
                }
            }
            is PositionEvent -> positionChanged(event.toMs, event.durationMs)
            is ErrorEvent -> onPlayerError(event.error)
            is VideoEndedEvent -> {
                // the end of a file that has been replaced already (a source that is just starting) is not news
                if (loadingText != null) return
                if (endedTooEarly()) {
                    onPrematureEnd()
                } else {
                    flushProgress()
                    if (prefs.getBoolean(ctx.getString(R.string.autoplay_next_key), true)) nextEpisode()
                }
            }
            is EpisodeSeekEvent -> if (event.offset < 0) prevEpisode() else nextEpisode()
            is ResizedEvent -> if (event.width > 0 && event.height > 0) resolution = "${event.width}×${event.height}"
            is TracksChangedEvent -> {
                listsVersion++
                applyPreferredAudio()
            }
            is EmbeddedSubtitlesFetchedEvent -> vm.addSubtitles(event.tracks.toSet())
            is SubtitlesUpdatedEvent -> listsVersion++
            is TimestampInvokedEvent -> activeStamp = event.timestamp
            is TimestampSkippedEvent -> activeStamp = null
            is DownloadEvent -> {}
            else -> {}
        }
    }

    /**
     * The player reports the position every 250 ms; every save is a disk write and a change that sync extensions (Ultima) upload.
     * The position is stored about every 5 s and at once after a jump; what is newer is stored by [flushProgress] when playback
     * stops, pauses or changes source.
     */
    private fun saveProgress(position: Long, duration: Long) {
        val id = vm.state.generatorState?.id
        val meta = currentMeta
        val next = nextMeta
        val save = { DataStoreHelper.setViewPosAndResume(id, position, duration, meta, next) }
        val now = System.currentTimeMillis()
        // consecutive ticks are 250 ms apart (a little more at a higher speed); a bigger step is a seek
        val jumped = progressLastPosition >= 0 && kotlin.math.abs(position - progressLastPosition) > 2_000L
        progressLastPosition = position
        if (jumped || now - progressSavedAt >= 5_000L) {
            progressSavedAt = now
            pendingProgress = null
            save()
        } else {
            pendingProgress = save
        }
    }

    private fun flushProgress() {
        val save = pendingProgress ?: return
        pendingProgress = null
        progressSavedAt = System.currentTimeMillis()
        save()
    }

    private fun positionChanged(position: Long, duration: Long) {
        val meta = currentMeta as? ResultEpisode
        if (meta?.tvType?.isLiveStream() == true || meta?.tvType == TvType.NSFW) return
        if (duration <= 0L) return
        if (!requestedStamps) {
            requestedStamps = true
            if (prefs.getBoolean(ctx.getString(R.string.enable_skip_op_from_database), true)) vm.loadStamps(duration)
        }
        val percentage = position * 100L / duration
        saveProgress(position, duration)
        if (meta != null && percentage >= UPDATE_SYNC_PROGRESS_PERCENTAGE && (maxEpisodeSet ?: -1) < meta.episode) {
            if (prefs.getBoolean(ctx.getString(R.string.episode_sync_enabled_key), true)) {
                maxEpisodeSet = meta.episode
                sync.modifyMaxEpisode(meta.totalEpisodeIndex ?: meta.episode)
            }
        }
        if (percentage >= PRELOAD_NEXT_EPISODE_PERCENTAGE) vm.preLoadNextLinks()
    }

    // ------------------------------------------------------------------ titles

    private fun updateTitle() {
        val meta = currentMeta
        var header: String? = null
        var sub: String? = null
        var episode: Int? = null
        var season: Int? = null
        var type: TvType? = null
        when (meta) {
            is ResultEpisode -> { header = meta.headerName; sub = meta.name; episode = meta.episode; season = meta.season; type = meta.tvType }
            is ExtractorUri -> { header = meta.headerName; sub = meta.name; episode = meta.episode; season = meta.season; type = meta.tvType }
        }
        title = header ?: vm.state.generatorState?.response?.name ?: ""
        episodeLabel = if (type.isEpisodeBased() && episode != null) {
            val ep = if (season == null) "Episode $episode" else "S$season · E$episode"
            if (sub.isNullOrBlank() || sub == header) ep else "$ep · $sub"
        } else sub?.takeIf { it.isNotBlank() && it != header }
    }

    // ------------------------------------------------------------------ subtitles

    private fun autoSubtitle(subtitles: Set<SubtitleData>, settings: Boolean, downloads: Boolean): SubtitleData? {
        val lang = preferredSubLang ?: return null
        if (downloads) {
            sortSubs(subtitles).firstOrNull { it.origin == SubtitleOrigin.DOWNLOADED_FILE && it.matchesLanguageCode(lang) }?.let { return it }
        }
        if (!settings) return null
        return sortSubs(subtitles).firstOrNull { it.matchesLanguageCode(lang) }
    }

    private fun applySubtitle(sub: SubtitleData?, userInitiated: Boolean): Boolean {
        if (sub != selectedSubtitle && userInitiated) {
            val tag = if (sub == null) "" else sub.getIETF_tag()
            if (tag != null) {
                setKey(SUBTITLE_AUTO_SELECT_KEY, tag)
                preferredSubLang = tag
            }
        }
        selectedSubtitle = sub
        return player.setPreferredSubtitles(sub)
    }

    private fun autoSelectSubtitles() {
        if (subtitleChosenByUser) return
        runCatching {
            val lang = preferredSubLang
            val current = player.getCurrentPreferredSubtitle()
            val pick = if (current != null && (lang == null || current.matchesLanguageCode(lang))) current
            else if (!lang.isNullOrEmpty()) autoSubtitle(vm.state.subtitles, settings = true, downloads = false) else null
            if (pick != null && applySubtitle(pick, false)) {
                player.saveData()
                player.reloadPlayer(ctx)
                player.handleEvent(CSPlayerEvent.Play)
            }
        }
    }

    /** All subtitle choices, sorted by name */
    fun subtitles(): List<SubtitleData> = sortSubs(vm.state.subtitles)
    /** What mpv really shows (the list and the picture agree) */
    fun currentSubtitle(): SubtitleData? = player.getCurrentPreferredSubtitle()

    fun selectSubtitle(sub: SubtitleData?) {
        subtitleChosenByUser = true
        android.util.Log.i("PlayerSession", "subtitle chosen: ${sub?.let { "${it.originalName} ${it.nameSuffix} [${it.origin}]" } ?: "none"}")
        if (applySubtitle(sub, true)) {
            player.saveData()
            player.reloadPlayer(ctx)
            player.handleEvent(CSPlayerEvent.Play)
        }
        listsVersion++
    }

    fun cycleSubtitle() {
        val list = subtitles()
        if (list.isEmpty()) return
        val current = currentSubtitle()
        val next = if (current == null) list.first() else list.getOrNull(list.indexOf(current) + 1)
        selectSubtitle(next)
        Toasts.show(next?.name?.trim() ?: "Subtitles off", false)
    }

    /** Adds subtitle tracks, reloads the player at the same position and selects the first one */
    fun addAndSelectSubtitles(vararg subtitleData: SubtitleData) {
        if (subtitleData.isEmpty()) return
        val selected = subtitleData.first()
        subtitleChosenByUser = true
        vm.addSubtitles(subtitleData.toSet())
        // added to the running file and switched to, no reload
        player.setActiveSubtitles(vm.state.subtitles)
        applySubtitle(selected, false)
        listsVersion++
        Toasts.show("Loaded subtitles: ${selected.name.trim()}", false)
    }

    /** Native file dialog for a local .srt/.vtt/.ass file */
    fun pickSubtitleFile() {
        val dialog = java.awt.FileDialog(com.lagradost.desktop.ui.DesktopUiHost.window, "Choose a subtitle file", java.awt.FileDialog.LOAD)
        dialog.setFilenameFilter { _, n -> n.lowercase().let { it.endsWith(".srt") || it.endsWith(".vtt") || it.endsWith(".ass") || it.endsWith(".ssa") || it.endsWith(".sub") || it.endsWith(".ttml") } }
        dialog.isVisible = true
        val file = dialog.file?.let { java.io.File(dialog.directory, it) } ?: return
        addSubtitleFile(file)
    }

    /** Adds a local subtitle file to the playing video and selects it */
    fun addSubtitleFile(file: java.io.File) {
        addAndSelectSubtitles(SubtitleData(file.name, "", file.toURI().toString(), SubtitleOrigin.DOWNLOADED_FILE, file.name.toSubtitleMimeType(), emptyMap(), null))
    }

    /** Title and ids the online subtitle search starts from */
    fun subtitleSearchOf(query: String, lang: String?): SubtitleSearch {
        val response = vm.state.generatorState?.response
        val meta = currentMeta as? ResultEpisode
        return SubtitleSearch(
            query = query,
            imdbId = response?.getImdbId(), tmdbId = response?.getTMDbId()?.toInt(), malId = response?.getMalId()?.toInt(),
            aniListId = response?.getAniListId()?.toInt(),
            epNumber = meta?.takeIf { !it.tvType.isMovieType() }?.episode, seasonNumber = meta?.takeIf { !it.tvType.isMovieType() }?.season,
            lang = lang?.ifBlank { null }, year = vm.currentSubtitleYear.value,
        )
    }

    val defaultSubtitleQuery: String get() = (currentMeta as? ResultEpisode)?.headerName ?: title

    /** Searches every subtitle provider, results interleaved so each provider is represented */
    suspend fun searchSubtitles(query: String, lang: String?): List<SubtitleEntity> {
        val search = subtitleSearchOf(query, lang)
        // every provider is asked at the same time: a slow or dead one must not hold the others back
        val results = kotlinx.coroutines.coroutineScope {
            GeneratorPlayer.subsProviders.toList().map { provider ->
                async(Dispatchers.IO) {
                    when (val r = Resource.fromResult(provider.search(search))) {
                        is Resource.Success -> r.value.also { android.util.Log.i("PlayerSession", "subtitle search ${provider.idPrefix}: ${it.size} result(s)") }
                        is Resource.Failure -> { android.util.Log.w("PlayerSession", "subtitle search ${provider.idPrefix}: ${r.errorString}"); Toasts.show("${provider.name}: ${r.errorString}", false); emptyList() }
                        else -> emptyList()
                    }
                }
            }.awaitAll()
        }
        val max = results.maxOfOrNull { it.size } ?: return emptyList()
        val items = ArrayList<SubtitleEntity>()
        for (index in 0 until max) for (list in results) list.getOrNull(index)?.let { items.add(it) }
        return items
    }

    /** Downloads one chosen search result and selects it */
    suspend fun applyOnlineSubtitle(entity: SubtitleEntity): String {
        val provider = GeneratorPlayer.subsProviders.firstOrNull { it.idPrefix == entity.idPrefix }
        if (provider == null) { Toasts.show("Subtitle provider ${entity.idPrefix} is not available", true); return "no provider ${entity.idPrefix}" }
        Toasts.show("Downloading subtitles…", false)
        return when (val r = Resource.fromResult(provider.resource(entity))) {
            is Resource.Success -> {
                val subs = r.value.getSubtitles().map { res ->
                    SubtitleData(res.name ?: entity.name, "", res.url, res.origin, res.url.toSubtitleMimeType(), entity.headers, entity.lang)
                }
                android.util.Log.i("PlayerSession", "online subtitle ${entity.idPrefix} '${entity.name}': ${subs.size} file(s) ${subs.map { it.url.take(200) }}")
                if (subs.isEmpty()) {
                    Toasts.show("${provider.name} could not provide this subtitle file. Try another result, or check your connection.", true)
                    "no files"
                } else {
                    kotlinx.coroutines.withContext(Dispatchers.Main) { addAndSelectSubtitles(*subs.toTypedArray()) }
                    "ok ${subs.size}"
                }
            }
            is Resource.Failure -> { android.util.Log.w("PlayerSession", "online subtitle failed: ${r.errorString}"); Toasts.show(r.errorString, true); "failed ${r.errorString}" }
            else -> "loading"
        }
    }

    private var subtitleSizeJob: kotlinx.coroutines.Job? = null

    /**
     * The subtitle size is the saved style's (the same setting as Settings > Subtitles), so it stays for every video:
     * a size of only this video (mpv sub-scale) was lost when the next one opened. Saved and sent to mpv a moment
     * after the last change, a slider drag is many changes.
     */
    fun changeSubtitleSize(size: Float) {
        val style = com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.subtitleStyleState.value.copy(fixedTextSize = Math.round(size).coerceIn(5, 60).toFloat())
        com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.subtitleStyleState.value = style
        subtitleSizeJob?.cancel()
        subtitleSizeJob = scope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(100)
            with(com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.Companion) {
                com.lagradost.desktop.runtime.AndroidRuntime.context.saveStyle(style)
                applyStyleEvent.invoke(style)
            }
        }
    }

    /** Searches the subtitle providers for the title and loads the first result */
    fun addFirstOnlineSubtitle() {
        scope.launch(Dispatchers.IO) {
            Toasts.show("Loading subtitles…", false)
            val first = searchSubtitles(defaultSubtitleQuery, getAutoSelectLanguageTagIETF()).firstOrNull()
            if (first == null) Toasts.show("No subtitles found", false) else applyOnlineSubtitle(first)
        }
    }

    fun setSubtitleDelay(ms: Long) {
        subtitleDelayMs = ms
        player.setSubtitleOffset(ms)
    }

    // ------------------------------------------------------------------ tracks

    fun audioTracks(): List<AudioTrack> = runCatching { player.getVideoTracks().allAudioTracks }.getOrDefault(emptyList())
    fun currentAudio(): AudioTrack? = runCatching { player.getVideoTracks().currentAudioTrack }.getOrNull()

    fun selectAudio(track: AudioTrack) {
        CS3IPlayer.preferredAudioTrackLanguage = track.language
        prefs.edit().putString(AUDIO_LANG_KEY, track.language).apply()
        player.setPreferredAudioTrack(track.language, track.id, track.formatIndex)
        listsVersion++
    }

    private fun langKey(code: String?): String? {
        val c = code?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return com.lagradost.cloudstream3.utils.SubtitleHelper.fromCodeToLangTagIETF(c)?.substringBefore('-')?.lowercase() ?: c.substringBefore('-').lowercase()
    }

    /** Once per file: the audio language chosen before (any video) is selected when this file has it */
    private fun applyPreferredAudio() {
        if (audioApplied) return
        // a channel keeps its own default audio ("Pogo Hindi" must not start in the Telugu of the last film)
        if ((currentMeta as? ResultEpisode)?.tvType?.isLiveStream() == true) return
        val tracks = runCatching { player.getVideoTracks() }.getOrNull() ?: return
        if (tracks.allAudioTracks.isEmpty()) return
        audioApplied = true
        val wanted = langKey(CS3IPlayer.preferredAudioTrackLanguage) ?: return
        val match = tracks.allAudioTracks.firstOrNull { langKey(it.language) == wanted } ?: return
        if (match.id != tracks.currentAudioTrack?.id) player.setPreferredAudioTrack(match.language, match.id, match.formatIndex)
    }

    fun cycleAudio() {
        val list = audioTracks()
        if (list.size < 2) return
        val next = list[(list.indexOfFirst { it.id == currentAudio()?.id } + 1) % list.size]
        selectAudio(next)
        Toasts.show(next.label ?: next.language ?: "Audio track", false)
    }

    fun videoTracks() = runCatching { player.getVideoTracks().allVideoTracks }.getOrDefault(emptyList())
    fun currentVideo() = runCatching { player.getVideoTracks().currentVideoTrack }.getOrNull()

    fun selectVideo(track: com.lagradost.cloudstream3.ui.player.VideoTrack?) {
        if (track == null) player.setMaxVideoSize() else player.setMaxVideoSize(track.width ?: Int.MAX_VALUE, track.height ?: Int.MAX_VALUE, track.id)
        listsVersion++
    }

    // ------------------------------------------------------------------ sources

    class SourceItem(val link: VideoLink, val name: String, val quality: String, val usable: Boolean, val current: Boolean)

    /** Reads [sourcesVersion], so a composable that lists sources updates when more arrive */
    private fun sortedLinksNow(): List<DisplayLink> {
        sourcesVersion
        return vm.state.sortLinks(qualityProfile)
    }

    fun sources(): List<SourceItem> = sortedLinksNow().map {
        val l = it.link
        val q = l.first?.quality?.let { q -> Qualities.getStringByInt(q) }.orEmpty()
        SourceItem(l, l.first?.name ?: l.second?.name ?: "Source", q, it.shouldUseLink, l == selectedLink)
    }

    // the source that was playing before the user picked another one: when the new one does not work, back to it
    private var fallbackLink: VideoLink? = null

    fun selectSource(link: VideoLink) {
        if (link == selectedLink) return
        fallbackLink = selectedLink.takeIf { loadingText == null }
        loadLink(link, true)
    }

    private fun backToFallback(why: String): Boolean {
        val back = fallbackLink ?: return false
        fallbackLink = null
        selectedLink?.let { bad -> vm.modifyState { addError(bad) } }
        Toasts.show("$why Going back to the source that was playing.", false)
        loadLink(back, true)
        return true
    }

    fun skipLoading() {
        vm.modifyState { copy(loading = Resource.Success(Unit)) }
    }

    fun reloadSources() {
        vm.forceClearCache = true
        releaseForReload()
        vm.loadLinks()
    }

    // ------------------------------------------------------------------ episodes

    class EpisodeItem(val episode: ResultEpisode, val index: Int, val current: Boolean, val fraction: Float)

    fun episodes(): List<EpisodeItem> {
        episodeVersion
        val all = vm.state.generatorState?.allMeta?.filterIsInstance<ResultEpisode>().orEmpty()
        val currentId = vm.state.generatorState?.id
        return all.mapIndexed { i, ep ->
            val pos = getViewPos(ep.id)
            val frac = if (pos != null && pos.duration > 0) (pos.position.toFloat() / pos.duration).coerceIn(0f, 1f) else 0f
            EpisodeItem(ep, i, ep.id == currentId, frac)
        }
    }

    fun playEpisode(item: EpisodeItem) {
        if (item.current) return
        isNextEpisode = true
        releaseForReload()
        vm.loadThisEpisode(item.index)
        episodeVersion++
    }

    fun nextEpisode() {
        if (!hasNext) return
        isNextEpisode = true
        releaseForReload()
        vm.loadLinksNext()
        episodeVersion++
    }

    fun prevEpisode() {
        if (!hasPrev) return
        isNextEpisode = true
        releaseForReload()
        vm.loadLinksPrev()
        episodeVersion++
    }

    // ------------------------------------------------------------------ transport

    fun togglePlay() = player.handleEvent(CSPlayerEvent.PlayPauseToggle)
    fun play() = player.handleEvent(CSPlayerEvent.Play)
    fun pause() {
        flushProgress()
        player.handleEvent(CSPlayerEvent.Pause)
    }

    fun seekBy(ms: Long) {
        val d = durationMs
        val target = (positionMs + ms).coerceIn(0L, if (d > 0) d else Long.MAX_VALUE)
        player.seekTo(target)
        positionMs = target
        val secs = kotlin.math.abs(ms) / 1000
        showHud(if (ms < 0) Icons.Rewind else Icons.FastForward, (if (ms < 0) "−" else "+") + secs + " s")
    }

    fun seekTo(ms: Long) {
        player.seekTo(ms)
        positionMs = ms
    }

    fun stampRanges(): List<Pair<Long, Long>> = vm.state.stamps.map { it.timestamp.startMs to it.timestamp.endMs }

    fun skipStamp() {
        val stamp = activeStamp ?: return
        if (stamp.skipToNextEpisode) nextEpisode() else {
            player.seekTo(stamp.timestamp.endMs)
            activeStamp = null
        }
    }

    fun changeSpeed(value: Float) {
        speed = value
        player.setPlaybackSpeed(value)
        showHud(Icons.Speed, if (value == 1f) "Normal speed" else "$value×")
    }

    fun changeVolume(percent: Int) {
        volume = percent.coerceIn(0, 200)
        if (volume > 0) muted = false
        applyVolume()
        prefs.edit().putInt(VOLUME_KEY, volume).apply()
    }

    /** Up/down to the next multiple of 5 (up to 200 %), with the on-screen level */
    fun stepVolume(direction: Int) {
        val next = if (direction > 0) (volume / 5 + 1) * 5 else ((volume + 4) / 5 - 1) * 5
        changeVolume(next)
        showHud(if (volume == 0) Icons.Mute else Icons.Volume, "$volume%", volume / 200f)
    }

    fun toggleMute() {
        muted = !muted
        applyVolume()
        showHud(if (muted || volume == 0) Icons.Mute else Icons.Volume, if (muted) "Muted" else "$volume%", volume / 200f)
    }

    private fun applyVolume() {
        player.setMpvProperty("volume", volume.toString())
        player.setMpvProperty("mute", if (muted) "yes" else "no")
    }

    fun changeResize(value: Resize) {
        resize = value
        applyResize()
    }

    fun cycleResize() = changeResize(Resize.entries[(resize.ordinal + 1) % Resize.entries.size])

    private fun applyResize() {
        player.setMpvProperty("keepaspect", if (resize.keepAspect) "yes" else "no")
        player.setMpvProperty("panscan", resize.panscan.toString())
    }

    // ------------------------------------------------------------------ lifecycle

    fun release() {
        if (released) return
        released = true
        if (active === this) active = null
        flushProgress()
        removers.forEach { it() }
        removers.clear()
        verifyJob?.cancel()
        scope.cancel()
        runCatching { player.release() }
        runCatching { player.releaseCallbacks() }
        runCatching { player.setVideoSurface(null) }
        // the title page shows the new watch position
        runCatching { ResultFragment.updateUI() }
    }

    fun exitFullscreen() {
        val host = AndroidRuntime.host
        if (host.isFullscreen()) host.setFullscreen(false)
    }

    // ---- picture in picture: the window becomes a small one above all others
    val pip: Boolean get() = com.lagradost.desktop.platform.WinChrome.pip

    fun togglePip() = setPip(!pip)

    fun setPip(on: Boolean) {
        val window = com.lagradost.desktop.ui.DesktopUiHost.window ?: return
        val size = resolution?.split('×')?.mapNotNull { it.trim().toFloatOrNull() }
        val aspect = if (size != null && size.size == 2 && size[1] > 0f) size[0] / size[1] else 16f / 9f
        java.awt.EventQueue.invokeLater { com.lagradost.desktop.platform.WinChrome.setPip(window, on, aspect) }
    }

    /** Dev server: behave as if the player reported an error for the current source */
    fun debugFail() = onPlayerError(RuntimeException("simulated source error"))

    /** Dev server: behave as if mpv reported the end of the file now */
    fun debugEnd() = onEvent(VideoEndedEvent())

    /** One line for the dev server: what the page and the player think right now */
    fun debugLine(): String = "loading=${loadingText} status=$status failure=${failure?.take(60)} source=${sourceName?.lineSequence()?.firstOrNull()} links=$linksFound more=$loadingMore pos=$positionMs dur=$durationMs mpv[${player.debugState()}]"

    companion object {
        @Volatile
        var active: PlayerSession? = null
        const val VOLUME_KEY = "desktop_player_volume"
        const val AUDIO_LANG_KEY = "desktop_player_audio_lang"
    }
}

/** One on-screen feedback bubble */
class Hud(val glyph: String, val text: String, val fraction: Float? = null)
