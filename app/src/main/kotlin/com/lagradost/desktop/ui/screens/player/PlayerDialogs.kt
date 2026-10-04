package com.lagradost.desktop.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.ui.player.CSPlayerLoading
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.ui.player.SubtitleOrigin
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.desktop.core.ioTask
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.IconButton
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.fluent.ProgressRing
import com.lagradost.desktop.ui.fluent.Slider
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction

// The two dialogs of the CloudStream player, laid out like the Android ones: audio and video tracks side by
// side; sources and subtitles side by side. Apply commits the choice, Cancel leaves everything as it was. The
// video pauses while a dialog is open and continues when it is closed.

/** One row of a single-choice list */
@Composable
private fun ChoiceRow(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(4.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(shape)
            .background(if (hovered && enabled) c.subtleHover else Color.Transparent, shape)
            .fluentClickable(source, enabled, shape, Role.RadioButton, onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(16.dp).border(androidx.compose.ui.unit.Dp.Hairline, if (selected) c.accent else c.textSecondary, CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(8.dp).background(c.accent, CircleShape))
        }
        FText(text, Modifier.weight(1f), maxLines = 2, color = if (enabled) c.text else c.textTertiary)
    }
}

/** A line below a list that does something ("Add subtitle file…") */
@Composable
private fun ActionRow(text: String, glyph: String, onClick: () -> Unit) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(4.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(shape)
            .background(if (hovered) c.subtleHover else Color.Transparent, shape)
            .fluentClickable(source, true, shape, Role.Button, onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(glyph, size = 14.dp, tint = c.accent)
        FText(text, Modifier.weight(1f), maxLines = 2, color = c.accent)
    }
}

@Composable
private fun ColumnHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().height(40.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        FText(text, Modifier.weight(1f), style = Fluent.type.subtitle, maxLines = 1)
        trailing()
    }
}

// ------------------------------------------------------------------------------------------------

private fun audioLabel(t: com.lagradost.cloudstream3.ui.player.AudioTrack, index: Int): String {
    val language = (t.language?.trim()?.takeIf { it.isNotBlank() }?.let { raw ->
        SubtitleHelper.fromTagToLanguageName(raw) ?: SubtitleHelper.fromTagToLanguageName(raw.replace('_', '-').substringBefore('-').lowercase()) ?: raw
    } ?: t.label?.takeIf { it.isNotBlank() } ?: "Audio ${index + 1}").replaceFirstChar { it.uppercaseChar() }
    val channels = when (val n = t.channelCount) {
        null -> ""
        in Int.MIN_VALUE..0 -> ""
        1 -> "Mono"
        2 -> "Stereo"
        6 -> "5.1"
        8 -> "7.1"
        else -> "${n}ch"
    }
    val codec = t.sampleMimeType?.takeIf { it.isNotBlank() }?.uppercase().orEmpty()
    return listOf(language, channels, codec).filter { it.isNotBlank() }.joinToString(" • ")
}

/** Audio and video tracks of the playing file */
fun openTracksDialog(s: PlayerSession) {
    val wasPlaying = s.status == CSPlayerLoading.IsPlaying
    s.pause()
    val video = s.videoTracks().sortedByDescending { it.height ?: 0 }
    val audio = s.audioTracks()
    val videoStart = video.indexOfFirst { it.id == s.currentVideo()?.id }
    val audioStart = audio.indexOfFirst { it.id == s.currentAudio()?.id }.coerceAtLeast(0)
    var videoPick by mutableStateOf(videoStart)
    var audioPick by mutableStateOf(audioStart)
    fun resume() { if (wasPlaying) s.play() }

    Overlays.show(
        Overlays.Dialog(
            title = null, primary = "Apply", close = "Cancel", width = 860.dp,
            onPrimary = {
                if (audioPick != audioStart) audio.getOrNull(audioPick)?.let { s.selectAudio(it) }
                if (videoPick != videoStart) video.getOrNull(videoPick)?.let { s.selectVideo(it) }
                resume()
            },
            onClose = { resume() },
        ) {
            val showVideo = video.size > 1
            val showAudio = audio.size > 1
            if (!showVideo && !showAudio) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    FText("There are no other audio or video tracks in this video.", color = Fluent.colors.textSecondary)
                }
            } else {
                Row(Modifier.fillMaxWidth().height(380.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (showVideo) Column(Modifier.weight(1f).fillMaxSize()) {
                        ColumnHeader("Video tracks")
                        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                            itemsIndexed(video) { i, t ->
                                ChoiceRow(t.label ?: (if (t.width == null || t.height == null) "${i + 1}" else "${t.width}x${t.height}"), i == videoPick) { videoPick = i }
                            }
                        }
                    }
                    if (showAudio) Column(Modifier.weight(1f).fillMaxSize()) {
                        ColumnHeader("Audio tracks")
                        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                            itemsIndexed(audio) { i, t -> ChoiceRow(audioLabel(t, i), i == audioPick) { audioPick = i } }
                        }
                    }
                }
            }
        },
    )
}

// ------------------------------------------------------------------------------------------------

private fun originLabel(sub: SubtitleData): String = when (sub.origin) {
    SubtitleOrigin.URL -> "Online"
    SubtitleOrigin.DOWNLOADED_FILE -> "Downloaded"
    SubtitleOrigin.EMBEDDED_IN_VIDEO -> "Embedded"
}

/** Sources (links) on the left, subtitles on the right */
fun openSourcesDialog(s: PlayerSession) {
    val wasPlaying = s.status == CSPlayerLoading.IsPlaying
    s.pause()
    // the link that plays; the list itself is built inside the dialog, so sources that arrive while it is open show up
    val sourceStart: com.lagradost.cloudstream3.ui.player.VideoLink? = s.sources().firstOrNull { it.current }?.link
    var sourcePick by mutableStateOf(sourceStart)

    // subtitles grouped by name like Android: a group per name, and an option list when a name has several files
    val groups = s.subtitles().groupBy { it.originalName }.entries.toList().map { (name, list) -> name to list.sortedBy { it.nameSuffix.toIntOrNull() ?: 0 } }
    val current = s.currentSubtitle()
    val groupStart = groups.indexOfFirst { it.first == current?.originalName } + 1
    val optionStart = groups.getOrNull(groupStart - 1)?.second?.indexOfFirst { it.nameSuffix == current?.nameSuffix }?.coerceAtLeast(0) ?: 0
    var groupPick by mutableStateOf(groupStart)
    var optionPick by mutableStateOf(optionStart)
    fun resume() { if (wasPlaying) s.play() }

    lateinit var dialog: Overlays.Dialog
    fun leave() {
        Overlays.dismiss(dialog)
        resume()
    }

    dialog = Overlays.Dialog(
        title = null, primary = "Apply", close = "Cancel", width = 900.dp,
        onPrimary = {
            val reload = sourcePick != sourceStart
            if (groupPick != groupStart || optionPick != optionStart) {
                if (groupPick <= 0) s.selectSubtitle(null)
                else groups.getOrNull(groupPick - 1)?.second?.getOrNull(optionPick)?.let { s.selectSubtitle(it) }
            }
            if (reload) sourcePick?.let { s.selectSource(it) }
            resume()
        },
        onClose = { resume() },
    ) {
        Row(Modifier.fillMaxWidth().height(400.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            // ---- sources (more keep arriving while they are still being collected)
            val all = s.sources()
            val sources = all.filter { it.usable || it.current }
            val hidden = all.size - sources.size
            if (sources.isNotEmpty() || s.loadingMore) Column(Modifier.weight(1f).fillMaxSize()) {
                ColumnHeader("Sources") { if (s.loadingMore) FText("more loading…", style = Fluent.type.caption, color = Fluent.colors.textSecondary, maxLines = 1, softWrap = false) }
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    itemsIndexed(sources) { _, src ->
                        ChoiceRow(src.name + if (src.quality.isNotBlank()) "  ${src.quality}" else "", src.link == sourcePick) { sourcePick = src.link }
                    }
                    if (s.loadingMore) item { FText("Loading more sources…", Modifier.padding(10.dp), style = Fluent.type.caption, color = Fluent.colors.textTertiary) }
                    if (hidden > 0) item { FText("$hidden hidden by the quality profile", Modifier.padding(10.dp), style = Fluent.type.caption, color = Fluent.colors.textTertiary) }
                }
            }
            // ---- subtitles
            Column(Modifier.weight(1f).fillMaxSize()) {
                ColumnHeader("Subtitles") {
                    IconButton(Icons.Settings, { Overlays.dismiss(dialog); resume(); openSubtitleSettings(s) }, tooltip = "Subtitle settings", size = 32.dp, iconSize = 14.dp)
                }
                val options = groups.getOrNull(groupPick - 1)?.second.orEmpty()
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    item { ChoiceRow("None", groupPick == 0) { groupPick = 0; optionPick = 0 } }
                    itemsIndexed(groups) { i, g ->
                        ChoiceRow(g.first.trim(), groupPick == i + 1) { if (groupPick != i + 1) { groupPick = i + 1; optionPick = if (i + 1 == groupStart) optionStart else 0 } }
                    }
                    item { Box(Modifier.height(6.dp)) }
                    item { ActionRow("Add subtitle file…", Icons.Folder) { leave(); s.pickSubtitleFile() } }
                    item { ActionRow("Search subtitles online…", Icons.Search) { leave(); openSubtitleSearch(s) } }
                    item { ActionRow("Add the first online result", Icons.Download) { leave(); s.addFirstOnlineSubtitle() } }
                }
                if (options.size > 1) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Fluent.colors.stroke))
                    LazyColumn(Modifier.fillMaxWidth().height(110.dp)) {
                        itemsIndexed(options) { i, sub -> ChoiceRow(sub.nameSuffix.trim().ifBlank { originLabel(sub) }, i == optionPick) { optionPick = i } }
                    }
                }
            }
        }
    }
    Overlays.show(dialog)
}

// ------------------------------------------------------------------------------------------------

/** Delay and size of the subtitles */
fun openSubtitleSettings(s: PlayerSession) {
    val wasPlaying = s.status == CSPlayerLoading.IsPlaying
    Overlays.show(
        Overlays.Dialog(title = "Subtitle settings", close = "Done", width = 460.dp, onClose = { if (wasPlaying) s.play() }) { dismissDialog ->
            val c = Fluent.colors
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        FText("Style")
                        FText("Font, size, colours, outline, background and position", style = Fluent.type.caption, color = c.textSecondary)
                    }
                    Button("Customise…", { dismissDialog(); openSubtitleStyleDialog(s) })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        FText("Delay")
                        FText("Positive values show the text later", style = Fluent.type.caption, color = c.textSecondary)
                    }
                    Button("−100 ms", { s.setSubtitleDelay(s.subtitleDelayMs - 100) })
                    FText("${s.subtitleDelayMs} ms", Modifier.width(72.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Button("+100 ms", { s.setSubtitleDelay(s.subtitleDelayMs + 100) })
                }
                // the saved style's size: the same as Settings > Subtitles, kept for every video
                val size = com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.subtitleStyleState.value.fixedTextSize ?: com.lagradost.cloudstream3.ui.subtitles.DEFAULT_SUBTITLE_SIZE
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        FText("Size")
                        FText("Saved for all videos", style = Fluent.type.caption, color = c.textSecondary)
                    }
                    Slider(value = size, onValueChange = { s.changeSubtitleSize(it) }, valueRange = 5f..60f, modifier = Modifier.width(180.dp))
                    FText("${Math.round(size)}", Modifier.width(48.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Button("Reset", { s.setSubtitleDelay(0); s.changeSubtitleSize(com.lagradost.cloudstream3.ui.subtitles.DEFAULT_SUBTITLE_SIZE) }, enabled = s.subtitleDelayMs != 0L || size != com.lagradost.cloudstream3.ui.subtitles.DEFAULT_SUBTITLE_SIZE)
                }
            }
        },
    )
}

/** Font, size, colours, edge, background and position of the subtitles, with the same preview and options as the settings page */
fun openSubtitleStyleDialog(s: PlayerSession) {
    val opened = com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.subtitleStyleState.value
    val wasPlaying = s.status == CSPlayerLoading.IsPlaying
    s.pause()
    Overlays.show(
        Overlays.Dialog(title = "Subtitle style", close = "Done", width = 680.dp, onClose = { if (wasPlaying) s.play() }) {
            Box(Modifier.fillMaxWidth().height(520.dp)) {
                val scroll = androidx.compose.foundation.rememberScrollState()
                Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(end = 12.dp)) {
                    com.lagradost.desktop.ui.screens.settings.SubtitleStyleEditor(initial = opened)
                }
            }
        },
    )
}
