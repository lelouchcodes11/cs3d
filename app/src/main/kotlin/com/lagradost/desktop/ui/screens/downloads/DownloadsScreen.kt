package com.lagradost.desktop.ui.screens.downloads

import com.lagradost.desktop.ui.fluent.hairBorder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.lagradost.desktop.ui.fluent.TouchLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import com.lagradost.desktop.ui.fluent.smoothWheel
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lagradost.desktop.ui.fluent.glass
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.isEpisodeBased
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_DELETE_FILE
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_PAUSE_DOWNLOAD
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_PLAY_FILE
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_RESUME_DOWNLOAD
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup
import com.lagradost.cloudstream3.ui.download.DownloadClickEvent
import com.lagradost.cloudstream3.ui.download.DownloadViewModel
import com.lagradost.cloudstream3.ui.download.VisualDownloadCached
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DOWNLOAD_EPISODE_CACHE
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects
import com.lagradost.cloudstream3.utils.downloader.DownloadQueueManager
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.withContext
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.cloudstream3.utils.DataStore.getFolderName
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.core.Route
import com.lagradost.desktop.core.appVm
import com.lagradost.desktop.core.observeAsState
import com.lagradost.desktop.ui.components.RemoteImage
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.ContextMenuArea
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.lightShadow
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.IconButton
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.MenuItem
import com.lagradost.desktop.ui.fluent.ProgressBar
import com.lagradost.desktop.ui.fluent.ProgressRing
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction
import com.lagradost.desktop.ui.shell.TopBarHeight

private fun size(bytes: Long): String = when {
    bytes <= 0 -> "0 MB"
    bytes < 1_000_000_000L -> "%.0f MB".format(bytes / 1_000_000.0)
    else -> "%.2f GB".format(bytes / 1_000_000_000.0)
}

@Composable
fun DownloadsScreen() {
    val c = Fluent.colors
    val ctx = DesktopBootstrap.activity
    val vm = appVm<DownloadViewModel>()
    val headers by vm.headerCards.observeAsState()
    val children by vm.childCards.observeAsState()
    val used by vm.usedBytes.observeAsState()
    val free by vm.availableBytes.observeAsState()
    val downloaded by vm.downloadBytes.observeAsState()
    var folder by remember { mutableStateOf<VisualDownloadCached.Header?>(null) }

    LaunchedEffect(Unit) { vm.updateHeaderList(ctx) }
    val changes = remember { mutableStateOf(0) }
    DisposableEffect(Unit) {
        val status: (Pair<Int, VideoDownloadManager.DownloadType>) -> Unit = { (_, type) ->
            if (type == VideoDownloadManager.DownloadType.IsDone || type == VideoDownloadManager.DownloadType.IsStopped || type == VideoDownloadManager.DownloadType.IsFailed) changes.value++
        }
        val deleted: (Int) -> Unit = { changes.value++ }
        VideoDownloadManager.downloadStatusEvent += status
        VideoDownloadManager.downloadDeleteEvent += deleted
        onDispose { VideoDownloadManager.downloadStatusEvent -= status; VideoDownloadManager.downloadDeleteEvent -= deleted }
    }
    LaunchedEffect(changes.value) {
        if (changes.value == 0) return@LaunchedEffect
        kotlinx.coroutines.delay(700)
        vm.updateHeaderList(ctx)
        folder?.let { vm.updateChildList(ctx, getFolderName(DOWNLOAD_EPISODE_CACHE, it.data.id.toString())) }
    }
    // the sizes of what is downloading grow: the lists are read again every few seconds while one runs
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(4000)
            if (VideoDownloadManager.downloadStatus.values.any { it == VideoDownloadManager.DownloadType.IsDownloading }) {
                vm.updateHeaderList(ctx)
                folder?.let { vm.updateChildList(ctx, getFolderName(DOWNLOAD_EPISODE_CACHE, it.data.id.toString())) }
            }
        }
    }
    LaunchedEffect(folder) { folder?.let { vm.updateChildList(ctx, getFolderName(DOWNLOAD_EPISODE_CACHE, it.data.id.toString())) } }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(top = TopBarHeight)) {
        Column(Modifier.padding(start = 36.dp, end = 36.dp, top = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (folder != null) IconButton(Icons.Back, { folder = null; vm.clearChildren() }, tooltip = "All downloads", kind = ButtonKind.Standard, size = 40.dp)
                com.lagradost.desktop.ui.fluent.PageHeader(folder?.data?.name ?: "Downloads", Modifier.weight(1f), icon = Icons.Download, tint = androidx.compose.ui.graphics.Color(0xFF3FBF7F), subtitle = if (folder == null) "Titles saved on this PC" else "Episodes") {
                    IconButton(Icons.Refresh, { vm.updateHeaderList(ctx) }, tooltip = "Refresh", kind = ButtonKind.Standard, size = 36.dp)
                }
            }
            val total = (used ?: 0L) + (free ?: 0L)
            if (total > 0) {
                Column(Modifier.fillMaxWidth().glass(com.lagradost.desktop.ui.fluent.FluentShapes.card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // nothing downloaded: an empty track, not a dot that looks like progress
                    ProgressBar(if ((downloaded ?: 0L) <= 0L) 0f else ((downloaded ?: 0L).toFloat() / total).coerceIn(0.01f, 1f), height = 8.dp)
                    FText("${size(downloaded ?: 0L)} downloaded  ·  ${size(free ?: 0L)} free on this drive", style = Fluent.type.caption, color = c.textSecondary)
                }
            }
        }
        val res = if (folder == null) headers else children
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        when (res) {
            null, is Resource.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ProgressRing() }
            is Resource.Failure -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { FText(res.errorString, color = c.textSecondary) }
            is Resource.Success -> {
                val items = res.value
                if (items.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    com.lagradost.desktop.ui.fluent.EmptyState(Icons.Download, "No downloads yet", "Use the download action of an episode or movie to keep it offline.")
                } else LazyColumn(Modifier.fillMaxSize().smoothWheel(listState), state = listState, contentPadding = PaddingValues(start = 36.dp, end = 36.dp, top = 8.dp, bottom = 8.dp + com.lagradost.desktop.ui.shell.LocalDockInset.current), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(items, key = { it.data.id }) { item -> DownloadRow(item, onOpenFolder = { if (it is VisualDownloadCached.Header) folder = it }) }
                }
            }
        }
    }
    }
}

@Composable
private fun DownloadRow(item: VisualDownloadCached, onOpenFolder: (VisualDownloadCached) -> Unit) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(FluentShapes.card)
    val (title, poster, subtitle) = when (item) {
        is VisualDownloadCached.Header -> Triple(
            item.data.name, item.data.poster,
            if (item.data.type.isEpisodeBased()) "${item.totalDownloads} episode${if (item.totalDownloads == 1) "" else "s"}  ·  ${size(item.currentBytes)}" else size(item.currentBytes),
        )
        is VisualDownloadCached.Child -> Triple(
            item.data.name ?: "Episode ${item.data.episode}", item.data.poster,
            listOfNotNull(item.data.season?.let { "Season $it" }, "Episode ${item.data.episode}", size(item.currentBytes)).joinToString("  ·  "),
        )
    }
    val episode: DownloadObjects.DownloadEpisodeCached? = when (item) {
        is VisualDownloadCached.Child -> item.data
        is VisualDownloadCached.Header -> item.child
    }
    // the episodes this row stands for (a series: all of the title's), and what each is doing right now
    val episodes = rememberEpisodes(item)
    val live = rememberLive(episodes)
    val running = episodes.filter { it.id in live.running }
    fun open() {
        if (item is VisualDownloadCached.Header && item.data.type.isEpisodeBased()) onOpenFolder(item)
        else episode?.let { DownloadButtonSetup.handleDownloadClick(DownloadClickEvent(DOWNLOAD_ACTION_PLAY_FILE, it)) }
    }
    val menu = {
        buildList {
            add(MenuItem(if (item is VisualDownloadCached.Header && item.data.type.isEpisodeBased()) "Open" else "Play", Icons.Play) { open() })
            if (item is VisualDownloadCached.Header) add(MenuItem("Open title page", Icons.Info) { Navigator.go(Route.Details(item.data.url, item.data.apiName, item.data.name, item.data.poster)) })
            if (running.isNotEmpty()) {
                add(if (live.paused) MenuItem("Resume", Icons.Play) { resumeAll(running) } else MenuItem("Pause", Icons.Pause) { pauseAll(running) })
                add(MenuItem("Cancel the download", Icons.Close, destructive = true) { askCancel(title, running) })
            } else episode?.let { ep ->
                add(MenuItem("Delete", Icons.Delete, destructive = true) { DownloadButtonSetup.handleDownloadClick(DownloadClickEvent(DOWNLOAD_ACTION_DELETE_FILE, ep)) })
            }
        }
    }
    ContextMenuArea(menu) {
        Row(
            Modifier.fillMaxWidth().lightShadow(shape).clip(shape).background(if (hovered) c.cardHover else c.card, shape).hairBorder(c.stroke, shape)
                .fluentClickable(source, true, shape, Role.Button) { open() }.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(56.dp, 84.dp).clip(RoundedCornerShape(6.dp)).background(c.control)) {
                RemoteImage(poster, null, title, Modifier.fillMaxSize(), ContentScale.Crop)
            }
            Column(Modifier.weight(1f).padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FText(title, style = Fluent.type.bodyStrong, maxLines = 2)
                if (running.isEmpty()) FText(subtitle, style = Fluent.type.caption, color = c.textSecondary, maxLines = 1)
                else {
                    // what is going on: how far, and of how much
                    val f = live.fraction
                    ProgressBar(f ?: 0f, Modifier.width(300.dp), color = if (live.paused) c.textTertiary else c.accent)
                    val bytes = if (live.total > 0) "${size(live.done)} of ${size(live.total)}  ·  " else ""
                    FText(
                        (if (live.paused) "Paused" else if (f == null) "Starting…" else "Downloading") + (f?.let { "  ·  $bytes${(it * 100).toInt()}%" } ?: "") +
                            (if (running.size > 1) "  ·  ${running.size} episodes" else ""),
                        style = Fluent.type.caption, color = if (live.paused) c.textSecondary else c.accentText, maxLines = 1,
                    )
                }
                if (live.failed > 0 && running.isEmpty()) FText("${live.failed} failed: press try again", style = Fluent.type.caption, color = c.critical, maxLines = 1)
            }
            // the buttons are always there: pause or resume, cancel; try again; delete
            when {
                running.isNotEmpty() -> {
                    IconButton(if (live.paused) Icons.Play else Icons.Pause, { if (live.paused) resumeAll(running) else pauseAll(running) }, tooltip = if (live.paused) "Resume" else "Pause", kind = ButtonKind.Standard, size = 36.dp)
                    IconButton(Icons.Close, { askCancel(title, running) }, tooltip = "Cancel the download", kind = ButtonKind.Standard, size = 36.dp)
                }
                live.failed > 0 -> {
                    IconButton(Icons.Refresh, { retryAll(episodes) }, tooltip = "Try again", kind = ButtonKind.Standard, size = 36.dp)
                    episode?.let { ep -> IconButton(Icons.Delete, { DownloadButtonSetup.handleDownloadClick(DownloadClickEvent(DOWNLOAD_ACTION_DELETE_FILE, ep)) }, tooltip = "Delete", kind = ButtonKind.Standard, size = 36.dp) }
                }
                else -> {
                    if (item is VisualDownloadCached.Child) IconButton(Icons.Delete, { DownloadButtonSetup.handleDownloadClick(DownloadClickEvent(DOWNLOAD_ACTION_DELETE_FILE, item.data)) }, tooltip = "Delete", kind = ButtonKind.Standard, size = 36.dp)
                    Icon(Icons.ChevronRightSmall, size = 12.dp, tint = c.textSecondary)
                }
            }
        }
    }
}

// ---- what the downloads are doing, live -----------------------------------------------------------------------------------------------

/** What a row's episodes are doing: [running] are the ids that download or wait (paused or not), [failed] how many failed */
private class Live(val running: List<Int>, val paused: Boolean, val fraction: Float?, val done: Long, val total: Long, val failed: Int)

private val NoLive = Live(emptyList(), false, null, 0L, 0L, 0)

/** One episode as the downloader's events last described it */
private data class Ev(val id: Int, val running: Boolean, val paused: Boolean, val failed: Boolean, val fraction: Float?, val done: Long, val total: Long)

private fun evOf(id: Int, type: VideoDownloadManager.DownloadType? = VideoDownloadManager.downloadStatus[id], prev: Ev? = null) = Ev(
    id,
    running = type == VideoDownloadManager.DownloadType.IsDownloading || type == VideoDownloadManager.DownloadType.IsPending || type == VideoDownloadManager.DownloadType.IsPaused,
    paused = type == VideoDownloadManager.DownloadType.IsPaused,
    failed = type == VideoDownloadManager.DownloadType.IsFailed,
    fraction = prev?.fraction, done = prev?.done ?: 0L, total = prev?.total ?: 0L,
)

/** The episodes of a row: the one of an episode row, those of the title (read from the saved episode list) for a title row */
@Composable
private fun rememberEpisodes(item: VisualDownloadCached): List<DownloadObjects.DownloadEpisodeCached> {
    val ctx = DesktopBootstrap.activity
    val start = when (item) {
        is VisualDownloadCached.Child -> listOf(item.data)
        is VisualDownloadCached.Header -> listOfNotNull(item.child)
    }
    val list = produceState(start, item.data.id, item.currentBytes) {
        if (item is VisualDownloadCached.Header && item.data.type.isEpisodeBased()) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.getKeys(getFolderName(DOWNLOAD_EPISODE_CACHE, item.data.id.toString())).mapNotNull { k -> ctx.getKey<DownloadObjects.DownloadEpisodeCached>(k) }.distinctBy { it.id }
                }.getOrDefault(start)
            }
        }
    }
    return list.value
}

/** The downloader's events for [episodes], as one state: the row follows them at once (pause, resume, progress, the end) */
@Composable
private fun rememberLive(episodes: List<DownloadObjects.DownloadEpisodeCached>): Live {
    val ids = episodes.map { it.id }
    val states = remember(ids) { mutableStateMapOf<Int, Ev>().also { m -> for (id in ids) m[id] = evOf(id) } }
    DisposableEffect(ids) {
        val status: (Pair<Int, VideoDownloadManager.DownloadType>) -> Unit = { (id, type) -> if (id in ids) states[id] = evOf(id, type, states[id]) }
        val progress: (Triple<Int, Long, Long>) -> Unit = { (id, done, total) ->
            if (id in ids && total > 0) states[id]?.let { states[id] = it.copy(fraction = (done.toFloat() / total).coerceIn(0f, 1f), done = done, total = total) }
        }
        VideoDownloadManager.downloadStatusEvent += status
        VideoDownloadManager.downloadProgressEvent += progress
        onDispose {
            VideoDownloadManager.downloadStatusEvent -= status
            VideoDownloadManager.downloadProgressEvent -= progress
        }
    }
    if (ids.isEmpty()) return NoLive
    val all = ids.mapNotNull { states[it] }
    val running = all.filter { it.running }
    val fractions = running.mapNotNull { it.fraction }
    return Live(
        running.map { it.id }, running.isNotEmpty() && running.all { it.paused },
        if (fractions.isEmpty()) null else fractions.average().toFloat(),
        running.sumOf { it.done }, running.sumOf { it.total }, all.count { it.failed },
    )
}

private fun pauseAll(episodes: List<DownloadObjects.DownloadEpisodeCached>) {
    for (e in episodes) VideoDownloadManager.downloadEvent.invoke(e.id to VideoDownloadManager.DownloadActionType.Pause)
}

/** A paused one carries on where it was; one that waits, or was left by an earlier run, starts again from its saved place */
private fun resumeAll(episodes: List<DownloadObjects.DownloadEpisodeCached>) {
    for (e in episodes) DownloadButtonSetup.handleDownloadClick(DownloadClickEvent(DOWNLOAD_ACTION_RESUME_DOWNLOAD, e))
}

private fun retryAll(episodes: List<DownloadObjects.DownloadEpisodeCached>) {
    for (e in episodes) if (VideoDownloadManager.downloadStatus[e.id] == VideoDownloadManager.DownloadType.IsFailed) DownloadButtonSetup.handleDownloadClick(DownloadClickEvent(DOWNLOAD_ACTION_RESUME_DOWNLOAD, e))
}

/** Cancel asks first: the part that is downloaded is deleted with it */
private fun askCancel(title: String, episodes: List<DownloadObjects.DownloadEpisodeCached>) {
    val ctx = DesktopBootstrap.activity
    val ids = episodes.map { it.id }
    Overlays.show(
        Overlays.Dialog(
            title = "Cancel this download?", primary = "Cancel the download", close = "Keep it", width = 420.dp,
            onPrimary = {
                for (id in ids) DownloadQueueManager.cancelDownload(id)
                VideoDownloadManager.deleteFilesAndUpdateSettings(ctx, ids.toSet(), MainScope())
            },
        ) { FText("“$title”: what has been downloaded of it is deleted.", color = Fluent.colors.textSecondary) },
    )
}
