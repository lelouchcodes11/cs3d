package com.lagradost.desktop.ui.screens.downloads

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.lagradost.cloudstream3.utils.DOWNLOAD_EPISODE_CACHE
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
    LaunchedEffect(folder) { folder?.let { vm.updateChildList(ctx, getFolderName(DOWNLOAD_EPISODE_CACHE, it.data.id.toString())) } }

    Column(Modifier.fillMaxSize().padding(top = TopBarHeight)) {
        Column(Modifier.padding(horizontal = 36.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (folder != null) IconButton(Icons.Back, { folder = null; vm.clearChildren() }, tooltip = "All downloads", kind = ButtonKind.Standard, size = 40.dp)
                com.lagradost.desktop.ui.fluent.PageHeader(folder?.data?.name ?: "Downloads", Modifier.weight(1f), subtitle = if (folder == null) "Titles saved on this PC" else "Episodes") {
                    IconButton(Icons.Refresh, { vm.updateHeaderList(ctx) }, tooltip = "Refresh", kind = ButtonKind.Standard, size = 36.dp)
                }
            }
            val total = (used ?: 0L) + (free ?: 0L)
            if (total > 0) {
                Column(Modifier.fillMaxWidth().glass(com.lagradost.desktop.ui.fluent.FluentShapes.card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProgressBar(((downloaded ?: 0L).toFloat() / total).coerceIn(0.01f, 1f), height = 8.dp)
                    FText("${size(downloaded ?: 0L)} downloaded  ·  ${size(free ?: 0L)} free on this drive", style = Fluent.type.caption, color = c.textSecondary)
                }
            }
        }
        val res = if (folder == null) headers else children
        when (res) {
            null, is Resource.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ProgressRing() }
            is Resource.Failure -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { FText(res.errorString, color = c.textSecondary) }
            is Resource.Success -> {
                val items = res.value
                if (items.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    com.lagradost.desktop.ui.fluent.EmptyState(Icons.Download, "No downloads yet", "Use the download action of an episode or movie to keep it offline.")
                } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 36.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(items, key = { it.data.id }) { item -> DownloadRow(item, onOpenFolder = { if (it is VisualDownloadCached.Header) folder = it }) }
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
    val progress = if (item.totalBytes > 0) (item.currentBytes.toFloat() / item.totalBytes).coerceIn(0f, 1f) else 1f
    val episode: com.lagradost.cloudstream3.utils.downloader.DownloadObjects.DownloadEpisodeCached? = when (item) {
        is VisualDownloadCached.Child -> item.data
        is VisualDownloadCached.Header -> item.child
    }
    fun open() {
        if (item is VisualDownloadCached.Header && item.data.type.isEpisodeBased()) onOpenFolder(item)
        else episode?.let { DownloadButtonSetup.handleDownloadClick(DownloadClickEvent(DOWNLOAD_ACTION_PLAY_FILE, it)) }
    }
    val menu = {
        buildList {
            add(MenuItem(if (item is VisualDownloadCached.Header && item.data.type.isEpisodeBased()) "Open" else "Play", Icons.Play) { open() })
            if (item is VisualDownloadCached.Header) add(MenuItem("Open title page", Icons.Info) { Navigator.go(Route.Details(item.data.url, item.data.apiName, item.data.name, item.data.poster)) })
            episode?.let { ep ->
                add(MenuItem("Pause", Icons.Pause) { DownloadButtonSetup.handleDownloadClick(DownloadClickEvent(DOWNLOAD_ACTION_PAUSE_DOWNLOAD, ep)) })
                add(MenuItem("Resume", Icons.Play) { DownloadButtonSetup.handleDownloadClick(DownloadClickEvent(DOWNLOAD_ACTION_RESUME_DOWNLOAD, ep)) })
                add(MenuItem("Delete", Icons.Delete, destructive = true) { DownloadButtonSetup.handleDownloadClick(DownloadClickEvent(DOWNLOAD_ACTION_DELETE_FILE, ep)) })
            }
        }
    }
    ContextMenuArea(menu) {
        Row(
            Modifier.fillMaxWidth().clip(shape).background(if (hovered) c.cardHover else c.card, shape).border(androidx.compose.ui.unit.Dp.Hairline, c.stroke, shape)
                .fluentClickable(source, true, shape, Role.Button) { open() }.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(56.dp, 84.dp).clip(RoundedCornerShape(6.dp)).background(c.control)) {
                RemoteImage(poster, null, title, Modifier.fillMaxSize(), ContentScale.Crop)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FText(title, style = Fluent.type.bodyStrong, maxLines = 2)
                FText(subtitle, style = Fluent.type.caption, color = c.textSecondary, maxLines = 1)
                if (progress < 1f) {
                    ProgressBar(progress, Modifier.width(260.dp))
                    FText("Downloading… ${(progress * 100).toInt()}%", style = Fluent.type.caption, color = c.accentText)
                }
            }
            Icon(Icons.ChevronRightSmall, size = 12.dp, tint = c.textSecondary)
        }
    }
}
