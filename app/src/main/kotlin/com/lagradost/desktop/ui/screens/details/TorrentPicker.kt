package com.lagradost.desktop.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.ui.player.ExtractorLinkGenerator
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.core.Route
import com.lagradost.desktop.DesktopPlatform
import com.lagradost.desktop.torrent.TorrentSearch
import com.lagradost.desktop.ui.components.SoftImage
import com.lagradost.desktop.ui.fluent.Chip
import com.lagradost.desktop.ui.fluent.ComboBox
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.IconButton
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.fluent.ProgressRing
import com.lagradost.desktop.ui.fluent.TextBox
import com.lagradost.desktop.ui.fluent.ToggleSwitch
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction
import com.lagradost.desktop.ui.fluent.smoothWheel
import org.json.JSONObject

/**
 * "Select a torrent to stream" for one episode, like Seanime's: the anime torrent sites are searched for the title, the left side has the
 * episode, smart search (only releases of this episode, and batches when asked), the providers, the resolution; the right side the releases
 * with group, episode, resolution, audio and subtitle badges, seeders, size and age, sortable. A click plays the release (the torrent engine
 * asks for its consent the first time, like any torrent source).
 */
object TorrentPicker {
    fun show(title: String, episode: Int?, poster: String?, posterHeaders: Map<String, String>?) {
        Overlays.show(
            Overlays.Dialog(title = "Select a torrent to stream", close = "Cancel", width = 1080.dp, bodyMax = 660.dp, dismissOnOutside = true) { dismiss ->
                Body(title, episode, poster, posterHeaders) { r, ep ->
                    dismiss()
                    play(title, ep, r)
                }
            },
        )
    }

    private fun play(title: String, episode: Int?, r: TorrentSearch.Result) {
        com.lagradost.desktop.core.ioTask {
            val data = JSONObject().put("hash", r.hash).put("seeders", r.seeders).also { o -> if (r.batch && episode != null) o.put("episode", episode) }
            val link = newExtractorLink(r.provider.label, "${r.group?.let { "[$it] " }.orEmpty()}$title${episode?.let { " · Episode $it" }.orEmpty()}", r.magnet, ExtractorLinkType.MAGNET) {
                quality = r.resolution ?: com.lagradost.cloudstream3.utils.Qualities.Unknown.value
                extractorData = data.toString()
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                Navigator.go(Route.Player(ExtractorLinkGenerator(listOf(link), emptyList()), 0, null))
            }
        }
    }

    private enum class Sort(val label: String) { Seeders("Seeders"), Size("Size"), Date("Date"), Resolution("Resolution") }

    private fun sizeText(b: Long) = when {
        b <= 0 -> null
        b >= 1L shl 30 -> "%.2f GB".format(b / (1L shl 30).toDouble())
        else -> "%d MB".format(b / (1L shl 20))
    }

    private fun ago(t: Long): String {
        if (t <= 0) return ""
        val d = (System.currentTimeMillis() - t) / 86_400_000L
        return when { d < 1 -> "today"; d < 2 -> "yesterday"; d < 60 -> "$d days ago"; d < 730 -> "${d / 30} months ago"; else -> "${d / 365} years ago" }
    }

    @Composable
    private fun Body(title: String, startEpisode: Int?, poster: String?, posterHeaders: Map<String, String>?, onPick: (TorrentSearch.Result, Int?) -> Unit) {
        val c = Fluent.colors
        var query by remember { mutableStateOf(title) }
        var submitted by remember { mutableStateOf(title) }
        var episode by remember { mutableStateOf(startEpisode) }
        var smart by remember { mutableStateOf(startEpisode != null) }
        var batches by remember { mutableStateOf(true) }
        var trustedOnly by remember { mutableStateOf(false) }
        var resolution by remember { mutableStateOf(0) }
        var providers by remember { mutableStateOf(TorrentSearch.Provider.entries.toSet()) }
        var sort by remember { mutableStateOf(Sort.Seeders) }
        var loading by remember { mutableStateOf(true) }
        var answer by remember { mutableStateOf<TorrentSearch.Answer?>(null) }
        LaunchedEffect(submitted, providers) {
            loading = true
            answer = null
            TorrentSearch.searchEach(submitted, providers) { a, done -> answer = a; if (done) loading = false }
        }
        val all = answer?.results.orEmpty()
        val shown = all.filter { r ->
            (!smart || episode == null || r.episode == episode || (batches && r.batch)) &&
                (resolution == 0 || r.resolution == resolution) && (!trustedOnly || r.trusted)
        }.let { list ->
            when (sort) {
                Sort.Seeders -> list.sortedByDescending { it.seeders }
                Sort.Size -> list.sortedByDescending { it.bytes }
                Sort.Date -> list.sortedByDescending { it.time }
                Sort.Resolution -> list.sortedWith(compareByDescending<TorrentSearch.Result> { it.resolution ?: 0 }.thenByDescending { it.seeders })
            }
        }
        Row(Modifier.fillMaxWidth().fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            // ---------------------------------------------------------------- the episode and the filters
            Column(Modifier.width(300.dp).fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(end = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val shape = RoundedCornerShape(12.dp)
                Box(Modifier.fillMaxWidth().height(160.dp).clip(shape).background(c.control, shape)) {
                    SoftImage(poster, posterHeaders, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))))
                    Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                        FText(title, style = Fluent.type.bodyStrong, color = Color.White, maxLines = 2)
                        episode?.let { FText("Episode $it", style = Fluent.type.caption, color = Color.White.copy(alpha = 0.8f), maxLines = 1) }
                    }
                }
                TextBox(query, { query = it }, Modifier.fillMaxWidth(), placeholder = "Search", leadingIcon = Icons.Search, height = 34.dp, onSubmit = { submitted = query.trim().ifEmpty { title } })
                Setting("Smart search", "Only this episode (and batches)") { ToggleSwitch(smart, { smart = it }) }
                if (episode != null) Setting("Episode number", null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(Icons.ChevronLeftSmall, { episode = ((episode ?: 1) - 1).coerceAtLeast(1) }, size = 28.dp, iconSize = 10.dp, tooltip = "Previous episode")
                        FText("${episode ?: "-"}", style = Fluent.type.bodyStrong, maxLines = 1)
                        IconButton(Icons.ChevronRightSmall, { episode = (episode ?: 0) + 1 }, size = 28.dp, iconSize = 10.dp, tooltip = "Next episode")
                    }
                }
                Setting("Batches", "Releases with many episodes") { ToggleSwitch(batches, { batches = it }) }
                Setting("Trusted only", "Releases from trusted uploaders") { ToggleSwitch(trustedOnly, { trustedOnly = it }) }
                Setting("Resolution", null) {
                    ComboBox(listOf(0, 2160, 1080, 720, 480), resolution, { if (it == 0) "Any" else "${it}p" }, { resolution = it }, minWidth = 100.dp)
                }
                FText("Providers", style = Fluent.type.caption, color = c.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TorrentSearch.Provider.entries.forEach { p ->
                        val on = p in providers
                        Chip(p.label + if (answer?.failed?.contains(p) == true) " (no answer)" else "", on, {
                            providers = if (on) (providers - p).ifEmpty { providers } else providers + p
                        })
                    }
                }
            }
            // ---------------------------------------------------------------- the releases
            Column(Modifier.weight(1f).fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FText(if (loading && answer == null) "Searching…" else "${shown.size} results", style = Fluent.type.bodyStrong, maxLines = 1)
                    if (loading) ProgressRing(size = 16.dp, strokeWidth = 2.dp)
                    Box(Modifier.weight(1f))
                    Sort.entries.forEach { s -> Chip(s.label, sort == s, { sort = s }) }
                }
                val state = rememberLazyListState()
                LazyColumn(Modifier.fillMaxWidth().weight(1f).smoothWheel(state), state = state, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(shown, key = { it.provider.name + it.hash }) { r -> ReleaseRow(r) { onPick(r, episode) } }
                    if (!loading && shown.isEmpty()) item {
                        FText(
                            when {
                                answer != null && answer!!.failed.size == providers.size -> "The torrent sites did not answer. They may be blocked on your network."
                                all.isNotEmpty() -> "Nothing matches the filters. Turn off Smart search or pick another resolution."
                                else -> "No torrents found for \"$submitted\". Try a shorter or other name in the search box."
                            },
                            Modifier.padding(16.dp), color = c.textSecondary,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun Setting(label: String, detail: String?, control: @Composable () -> Unit) {
        val c = Fluent.colors
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                FText(label, maxLines = 1)
                detail?.let { FText(it, style = Fluent.type.caption, color = c.textSecondary, maxLines = 1) }
            }
            control()
        }
    }

    @Composable
    private fun Badge(text: String, bg: Color, fg: Color) {
        Box(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
            FText(text, style = Fluent.type.caption.copy(fontWeight = FontWeight.SemiBold), color = fg, maxLines = 1, softWrap = false)
        }
    }

    @Composable
    private fun ReleaseRow(r: TorrentSearch.Result, onClick: () -> Unit) {
        val c = Fluent.colors
        val source = rememberInteraction()
        val hovered by source.collectIsHoveredAsState()
        val shape = RoundedCornerShape(12.dp)
        Row(
            Modifier.fillMaxWidth().clip(shape).background(if (hovered) c.cardHover else c.card, shape)
                .fluentClickable(source, true, shape, Role.Button, onClick).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // the release group as the row's "thumbnail"
            Box(Modifier.width(86.dp).height(54.dp).clip(RoundedCornerShape(8.dp)).background(c.control), contentAlignment = Alignment.Center) {
                FText(r.group ?: r.provider.label, Modifier.padding(4.dp), style = Fluent.type.caption.copy(fontWeight = FontWeight.Bold), color = c.text, maxLines = 2)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    FText(when { r.episode != null -> "Episode ${r.episode}"; r.batch -> "Batch"; else -> "Release" }, style = Fluent.type.bodyStrong, maxLines = 1)
                    r.resolution?.let { Badge("${it}p", c.control, c.text) }
                    if (r.batch) Badge("Batch", Color(0x335B8DEF), Color(0xFF8FB2FF))
                    if (r.dualAudio) Badge("Dual audio", Color(0x33B07CFF), Color(0xFFC9A6FF))
                    if (r.multiSubs) Badge("Multi subs", Color(0x33FFB84D), Color(0xFFFFC870))
                    if (r.trusted) Badge("Trusted", Color(0x3337C871), Color(0xFF4CD787))
                }
                FText(r.name, style = Fluent.type.caption, color = c.textSecondary, maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FText("▲ ${r.seeders} seeders", style = Fluent.type.caption.copy(fontWeight = FontWeight.SemiBold), color = if (r.seeders >= 10) Color(0xFF4CD787) else if (r.seeders >= 3) Color(0xFFFFC870) else Color(0xFFFF8A8A), maxLines = 1)
                    sizeText(r.bytes)?.let { FText(it, style = Fluent.type.caption, color = c.textSecondary, maxLines = 1) }
                    FText(ago(r.time), style = Fluent.type.caption, color = c.textSecondary, maxLines = 1)
                    FText(r.provider.label, style = Fluent.type.caption, color = c.textSecondary, maxLines = 1)
                }
            }
            r.page?.let { page -> IconButton(Icons.OpenInNewWindow, { DesktopPlatform.openExternalBrowser(page) }, tooltip = "Open the release page", size = 30.dp, iconSize = 12.dp) }
        }
    }
}
