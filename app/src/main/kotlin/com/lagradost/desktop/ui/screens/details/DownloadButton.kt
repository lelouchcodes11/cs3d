package com.lagradost.desktop.ui.screens.details

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.ui.result.ACTION_DOWNLOAD_EPISODE
import com.lagradost.cloudstream3.ui.result.ACTION_DOWNLOAD_MIRROR
import com.lagradost.cloudstream3.ui.result.EpisodeClickEvent
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.ResultViewModel2
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.core.Tab
import com.lagradost.desktop.ui.Toasts
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.GlassCircleButton
import com.lagradost.desktop.ui.fluent.IconButton
import com.lagradost.desktop.ui.fluent.MenuFlyout
import com.lagradost.desktop.ui.fluent.MenuItem
import com.lagradost.desktop.ui.fluent.Icons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where the download of an episode or film is: nothing yet, waiting or running (with how far), done, or failed */
internal enum class DlKind { None, Running, Done, Failed }

/** [paused]: a running download that was paused; [done] and [total] bytes when the downloader said so */
internal class DlState(val kind: DlKind, val fraction: Float? = null, val paused: Boolean = false, val done: Long = 0L, val total: Long = 0L)

/**
 * The download state of one episode (the id the downloader keeps it under): what is known at once (this run), the file on the disk from an earlier run,
 * and the downloader's events while the page is open. The state is a state object: it can be set at once when the button is pressed.
 */
@Composable
internal fun rememberDownloadState(id: Int): MutableState<DlState> {
    val ctx = DesktopBootstrap.activity
    val state = remember(id) {
        mutableStateOf(
            when (VideoDownloadManager.downloadStatus[id]) {
                VideoDownloadManager.DownloadType.IsDone -> DlState(DlKind.Done)
                VideoDownloadManager.DownloadType.IsDownloading, VideoDownloadManager.DownloadType.IsPending -> DlState(DlKind.Running)
                VideoDownloadManager.DownloadType.IsPaused -> DlState(DlKind.Running, paused = true)
                VideoDownloadManager.DownloadType.IsFailed -> DlState(DlKind.Failed)
                else -> DlState(DlKind.None)
            },
        )
    }
    // a file left by an earlier run of the app
    LaunchedEffect(id) {
        if (state.value.kind != DlKind.None) return@LaunchedEffect
        val onDisk = withContext(Dispatchers.IO) { runCatching { VideoDownloadManager.getDownloadFileInfo(ctx, id) != null }.getOrDefault(false) }
        if (onDisk && state.value.kind == DlKind.None) state.value = DlState(DlKind.Done)
    }
    DisposableEffect(id) {
        val status: (Pair<Int, VideoDownloadManager.DownloadType>) -> Unit = { (changed, type) ->
            if (changed == id) {
                state.value = when (type) {
                    VideoDownloadManager.DownloadType.IsDone -> DlState(DlKind.Done)
                    VideoDownloadManager.DownloadType.IsDownloading, VideoDownloadManager.DownloadType.IsPending -> state.value.let { DlState(DlKind.Running, it.fraction, false, it.done, it.total) }
                    VideoDownloadManager.DownloadType.IsPaused -> state.value.let { DlState(DlKind.Running, it.fraction, true, it.done, it.total) }
                    VideoDownloadManager.DownloadType.IsFailed -> DlState(DlKind.Failed)
                    VideoDownloadManager.DownloadType.IsStopped -> DlState(DlKind.None)
                }
            }
        }
        val progress: (Triple<Int, Long, Long>) -> Unit = { (changed, done, total) ->
            if (changed == id && total > 0 && state.value.kind != DlKind.Done) state.value = DlState(DlKind.Running, (done.toFloat() / total).coerceIn(0f, 1f), state.value.paused, done, total)
        }
        VideoDownloadManager.downloadStatusEvent += status
        VideoDownloadManager.downloadProgressEvent += progress
        onDispose {
            VideoDownloadManager.downloadStatusEvent -= status
            VideoDownloadManager.downloadProgressEvent -= progress
        }
    }
    return state
}

private fun startDownload(vm: ResultViewModel2, ep: ResultEpisode, state: MutableState<DlState>, what: String) {
    state.value = DlState(DlKind.Running)
    vm.handleAction(EpisodeClickEvent(ACTION_DOWNLOAD_EPISODE, ep))
    Toasts.show("Downloading $what. The progress is under Downloads.", false)
}

/** The sources are listed and one is picked by hand (the downloader otherwise takes the best one by itself) */
private fun chooseSource(vm: ResultViewModel2, ep: ResultEpisode) {
    vm.handleAction(EpisodeClickEvent(ACTION_DOWNLOAD_MIRROR, ep))
}

/** What the Download button offers when nothing is downloading: the best source at once, or a source of your choice */
private fun choices(vm: ResultViewModel2, ep: ResultEpisode, state: MutableState<DlState>, what: String, retry: Boolean) = listOf(
    MenuItem(if (retry) "Try again" else "Download", Icons.Download) { startDownload(vm, ep, state, what) },
    MenuItem("Choose a source…", Icons.Link) { chooseSource(vm, ep) },
)

private fun glyphOf(s: DlState) = when (s.kind) {
    DlKind.Done -> Icons.Check
    DlKind.Failed -> Icons.Refresh
    else -> Icons.Download
}

private fun tipOf(s: DlState, what: String) = when (s.kind) {
    DlKind.None -> "Download $what"
    DlKind.Running -> (if (s.paused) "Paused" else "Downloading") + (s.fraction?.let { " " + (it * 100).toInt() + " %" } ?: "") + ": open Downloads"
    DlKind.Done -> "Downloaded: open Downloads"
    DlKind.Failed -> "The download failed: try again"
}

/** The round Download button of the title's header (a film, or the episode Play opens): its state, and a ring along its edge while it downloads */
@Composable
internal fun HeaderDownloadButton(vm: ResultViewModel2, ep: ResultEpisode, what: String, size: Dp = 50.dp) {
    val state = rememberDownloadState(ep.id)
    val s = state.value
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.size(size)) {
        GlassCircleButton(
            glyphOf(s), tipOf(s, what),
            {
                when (s.kind) {
                    DlKind.None, DlKind.Failed -> menu = true
                    else -> Navigator.goTab(Tab.Downloads)
                }
            },
            // while it downloads the progress ring is the button's only edge (a second, still ring beside it looked doubled)
            active = s.kind == DlKind.Done, size = size, edge = s.kind != DlKind.Running,
        )
        if (s.kind == DlKind.Running) {
            Canvas(Modifier.size(size)) {
                val sw = 2.5.dp.toPx()
                val topLeft = Offset(sw / 2, sw / 2)
                val ringSize = Size(this.size.width - sw, this.size.height - sw)
                drawArc(Color.White.copy(alpha = 0.28f), 0f, 360f, false, topLeft = topLeft, size = ringSize, style = Stroke(sw))
                drawArc(Color.White, -90f, 360f * (s.fraction ?: 0.08f), false, topLeft = topLeft, size = ringSize, style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        if (menu) MenuFlyout(choices(vm, ep, state, what, s.kind == DlKind.Failed), { menu = false })
    }
}

/** The small Download button of an episode card, beside its menu */
@Composable
internal fun EpisodeDownloadButton(vm: ResultViewModel2, ep: ResultEpisode, what: String) {
    val state = rememberDownloadState(ep.id)
    val s = state.value
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(
            glyphOf(s),
            {
                when (s.kind) {
                    DlKind.None, DlKind.Failed -> menu = true
                    else -> Navigator.goTab(Tab.Downloads)
                }
            },
            tooltip = tipOf(s, what), size = 28.dp, iconSize = 14.dp,
        )
        if (menu) MenuFlyout(choices(vm, ep, state, what, s.kind == DlKind.Failed), { menu = false })
    }
}
