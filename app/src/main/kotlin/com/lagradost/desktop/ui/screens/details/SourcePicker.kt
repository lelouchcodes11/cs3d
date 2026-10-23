package com.lagradost.desktop.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.ui.result.SelectPopup
import com.lagradost.cloudstream3.ui.result.callback
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.desktop.net.LastSource
import com.lagradost.desktop.net.SourceWeight
import com.lagradost.desktop.torrent.TorrentEngine
import com.lagradost.desktop.ui.fluent.Appearance
import com.lagradost.desktop.ui.fluent.Chip
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.fluent.TextBox
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction
import com.lagradost.desktop.ui.fluent.smoothWheel

/** One source of the list: what the extension called it and everything that can be read from the link, ready to show */
private class LinkRow(val index: Int, val link: ExtractorLink) {
    val quality: String = Qualities.getStringByInt(link.quality).takeIf { it != "Auto" }.orEmpty()
    val type: String = when (link.type) {
        ExtractorLinkType.M3U8 -> "HLS stream"
        ExtractorLinkType.DASH -> "DASH stream"
        ExtractorLinkType.VIDEO -> "Direct file"
        ExtractorLinkType.TORRENT, ExtractorLinkType.MAGNET -> "Torrent"
    }
    val sizeGb: Double? = SourceWeight.sizeGb(link)
    val seeders: Int? = TorrentEngine.seeders(link)
    val heavy: Boolean = SourceWeight.heavy(link)
    val host: String? = runCatching { java.net.URI(link.url).host?.removePrefix("www.") }.getOrNull()?.takeIf { it.isNotBlank() && link.type != ExtractorLinkType.MAGNET }
    val sizeText: String? = sizeGb?.let { if (it >= 1.0) "%.1f GB".format(it) else "%d MB".format((it * 1024).toInt()) }

    /** The text the search looks in */
    val haystack: String = (link.name + " " + link.source + " " + quality + " " + type + " " + (host ?: "")).lowercase()

    /** Under the name: the site it comes from (when the name does not say it), the kind of file, the size, the seeders, the host */
    val detail: String = listOfNotNull(
        link.source.takeIf { it.isNotBlank() && !link.name.contains(it, ignoreCase = true) },
        type,
        sizeText?.takeIf { !link.name.contains(it, ignoreCase = true) },
        seeders?.let { "$it seeders" },
        host,
    ).joinToString("  ·  ")
}

/**
 * The list of sources that "Play in mpv", "Play in VLC", "Copy link" ... ask for. The old one was a 420 dp wide list of one line per source cut
 * to the width ("StreamPlay [Hub…"): this one is wide, shows the whole name (two lines), the site, the picture size, kind of file, size and
 * seeders of each source, can be searched and sorted like the player's own list.
 */
internal fun showLinkPicker(p: SelectPopup.SelectLinks, title: String) {
    var done = false
    Overlays.show(
        Overlays.Dialog(
            title = title, close = "Cancel", width = 780.dp, bodyMax = 680.dp, dismissOnOutside = true,
            onClose = { if (!done) { done = true; p.callback(null) } },
        ) { dismiss ->
            SourcePickerBody(p.links) { index -> if (!done) { done = true; dismiss(); p.callback(index) } }
        },
    )
}

@Composable
private fun SourcePickerBody(links: List<ExtractorLink>, onPick: (Int) -> Unit) {
    val c = Fluent.colors
    val rows = remember(links) { links.mapIndexed { i, l -> LinkRow(i, l) } }
    var sort by remember { mutableStateOf(LastSource.sort) }
    var query by remember { mutableStateOf("") }
    val hasSizes = rows.any { it.sizeGb != null }
    val hasSeeders = rows.any { it.seeders != null }
    val ordered = when (sort) {
        // the picture size first; the huge files (remux, 4K) after the others when "prefer smooth sources" is on, like the player's automatic choice
        LastSource.Sort.Best -> rows.sortedWith(compareBy<LinkRow>({ if (Appearance.smoothSources && it.heavy) 1 else 0 }, { -it.link.quality }))
        LastSource.Sort.Quality -> rows.sortedByDescending { it.link.quality }
        LastSource.Sort.Size -> rows.sortedByDescending { it.sizeGb ?: -1.0 }
        LastSource.Sort.Seeders -> rows.sortedByDescending { it.seeders ?: -1 }
    }
    val words = query.trim().lowercase().split(Regex("""\s+""")).filter { it.isNotEmpty() }
    val shown = if (words.isEmpty()) ordered else ordered.filter { r -> words.all { it in r.haystack } }
    val state = rememberLazyListState()
    Column {
        if (rows.size > 5) TextBox(
            query, { query = it }, Modifier.fillMaxWidth().padding(bottom = 10.dp),
            placeholder = "Search ${rows.size} sources", leadingIcon = Icons.Search, height = 36.dp,
        )
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            LastSource.Sort.entries.forEach { m ->
                if (m == LastSource.Sort.Size && !hasSizes) return@forEach
                if (m == LastSource.Sort.Seeders && !hasSeeders) return@forEach
                Chip(m.label, sort == m, { sort = m; LastSource.sort = m })
            }
            Box(Modifier.weight(1f))
            FText("${shown.size} source${if (shown.size == 1) "" else "s"}", style = Fluent.type.caption, color = c.textTertiary, maxLines = 1)
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).smoothWheel(state), state = state, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(shown, key = { it.index }) { row -> SourceRow(row) { onPick(row.index) } }
            if (shown.isEmpty()) item {
                FText(if (words.isNotEmpty()) "No source matches \"${query.trim()}\"" else "No sources", Modifier.padding(12.dp), color = c.textSecondary)
            }
        }
    }
}

@Composable
private fun SourceRow(row: LinkRow, onClick: () -> Unit) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(com.lagradost.desktop.ui.fluent.FluentShapes.row)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (hovered) c.subtleHover else Color.Transparent, shape)
            .fluentClickable(source, true, shape, Role.Button, onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FText(row.link.name.ifBlank { row.link.source }, style = Fluent.type.bodyStrong, maxLines = 2)
            if (row.detail.isNotBlank()) FText(row.detail, style = Fluent.type.caption, color = c.textSecondary, maxLines = 2)
        }
        if (row.quality.isNotBlank()) {
            Box(Modifier.background(c.control, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp)) {
                FText(row.quality, style = Fluent.type.caption, color = c.text, maxLines = 1, softWrap = false)
            }
        }
    }
}
