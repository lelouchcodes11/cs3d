package com.lagradost.desktop.ui.screens.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.lagradost.desktop.ui.fluent.Motion
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.ui.player.SubtitleOrigin
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.desktop.core.ioTask
import com.lagradost.desktop.ui.fluent.Appearance
import com.lagradost.desktop.ui.fluent.AudioDecoder
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.Slider
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction

// The player's one menu: a card above the buttons at the bottom right, with pages (YouTube / Netflix style). The root lists every
// setting with its current value; a row opens its page, the header goes back. A click applies at once, the current choice has a
// check mark, a click outside or Esc closes it, and the video keeps playing. The Subtitles and Sources buttons open their page directly.
// Same rows, sizes and colours on every page; the player is always dark, so the colours are fixed (not the app theme's).

enum class MenuPage(val title: String) {
    Root(""), Sources("Source"), Quality("Quality"), Audio("Audio"), Tracks("Video & Audio"), Subtitles("Subtitles"), SubtitleTiming("Subtitle delay and size"),
    Speed("Playback speed"), Picture("Picture size"), Decoder("Audio output"),
    Anime4K("Anime upscaling (Anime4K)"), Skip("Skip intro & credits"),
}

/** Every panel and bubble over the video (menu, episodes, volume and seek bubbles, subtitle pill): one surface */
internal val PlayerSurface = Color(0xF21A1A1C)
internal val PlayerSurfaceBorder = Color(0x24FFFFFF)

private object MenuColors {
    val card = PlayerSurface
    val border = PlayerSurfaceBorder
    val hover = Color(0x1AFFFFFF)
    val divider = Color(0x14FFFFFF)
    val text = Color.White
    val secondary = Color(0xB3FFFFFF)
    val tertiary = Color(0x80FFFFFF)
}

private val MENU_WIDTH = 360.dp
private val ROW_HEIGHT = 44.dp

/** The menu card; [onPage] null closes it. [maxHeight]: the room above the controls */
@Composable
internal fun PlayerMenu(s: PlayerSession, page: MenuPage, onPage: (MenuPage?) -> Unit, maxHeight: Dp, modifier: Modifier = Modifier, fromTop: Boolean = false, originX: Float = 1f) {
    val shape = RoundedCornerShape(16.dp)
    // it comes up out of the button that opened it: a little scale, a soft slide, a fade
    val appear = remember { Animatable(if (Appearance.motion == Motion.Off) 1f else 0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, glide(300)) }
    Box(
        modifier.width(MENU_WIDTH)
            .graphicsLayer {
                val k = 0.94f + 0.06f * appear.value
                scaleX = k; scaleY = k; alpha = appear.value
                translationY = (1f - appear.value) * 16.dp.toPx() * (if (fromTop) -1f else 1f)
                transformOrigin = TransformOrigin(originX, if (fromTop) 0f else 1f)
            }
            .clip(shape).background(MenuColors.card, shape).border(androidx.compose.ui.unit.Dp.Hairline, MenuColors.border, shape)
            // clicks on the card stay on the card (the layer behind it closes the menu)
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    ) {
        AnimatedContent(
            page,
            transitionSpec = {
                // into a page from the right, back to the root from the left
                val forward = targetState != MenuPage.Root
                (slideInHorizontally(tween(180)) { w -> if (forward) w / 5 else -w / 5 } + fadeIn(tween(180))) togetherWith
                    (slideOutHorizontally(tween(120)) { w -> if (forward) -w / 5 else w / 5 } + fadeOut(tween(100))) using
                    SizeTransform(clip = true) { _, _ -> tween(180) }
            },
        ) { p ->
            Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).padding(vertical = 6.dp)) {
                if (p != MenuPage.Root) PageHeader(p.title, { onPage(MenuPage.Root) }) {
                    if (p == MenuPage.Sources) {
                        // looks for the sources again from scratch (the ones found are thrown away and the video starts again from where it was)
                        HeaderAction("Reload") { onPage(null); s.reloadSources() }
                        HeaderAction("Priority") { onPage(null); s.openSourcePriority() }
                    }
                }
                when (p) {
                    MenuPage.Root -> RootPage(s, onPage)
                    MenuPage.Sources -> SourcesPage(s, onPage)
                    MenuPage.Quality -> QualityPage(s)
                    MenuPage.Audio -> AudioPage(s)
                    MenuPage.Tracks -> TracksPage(s)
                    MenuPage.Subtitles -> SubtitlesPage(s, onPage)
                    MenuPage.SubtitleTiming -> SubtitleTimingPage(s)
                    MenuPage.Speed -> ChoicePage(speeds.map { speedLabel(it) }, speeds.indexOf(s.speed)) { s.changeSpeed(speeds[it]) }
                    MenuPage.Picture -> ChoicePage(Resize.entries.map { it.label }, Resize.entries.indexOf(s.resize)) { s.changeResize(Resize.entries[it]) }
                    MenuPage.Decoder -> DecoderPage()
                    MenuPage.Anime4K -> Anime4KPage()
                    MenuPage.Skip -> SkipPage()
                }
            }
        }
    }
}

private val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f)
private fun speedLabel(v: Float) = if (v == 1f) "Normal" else "${v.toString().removeSuffix(".0")}×"

// ---- pages ---------------------------------------------------------------------------------------

@Composable
private fun RootPage(s: PlayerSession, onPage: (MenuPage?) -> Unit) {
    Column(Modifier.verticalScrollIfNeeded()) {
        // the three things a viewer changes most, in the order of the buttons under the picture
        NavRow(Icons.Video, "Video & Audio", listOfNotNull(qualityLabel(s), s.currentAudio()?.let { a -> audioLabel(a, s.audioTracks().indexOfFirst { it.id == a.id }.coerceAtLeast(0)).substringBefore(" • ") }).joinToString(" · ").ifBlank { null }) { onPage(MenuPage.Tracks) }
        NavRow(Icons.Subtitles, "Subtitles", s.currentSubtitle()?.originalName?.trim() ?: "Off") { onPage(MenuPage.Subtitles) }
        NavRow(Icons.Link, "Sources", s.sourceName?.lineSequence()?.firstOrNull()) { onPage(MenuPage.Sources) }
        Divider()
        NavRow(Icons.Speed, "Playback speed", speedLabel(s.speed)) { onPage(MenuPage.Speed) }
        NavRow(Icons.Aspect, "Picture size", s.resize.label) { onPage(MenuPage.Picture) }
        NavRow(Icons.Volume, "Audio output", Appearance.audioDecoder.label.substringBefore(" (")) { onPage(MenuPage.Decoder) }
        NavRow(Icons.Anime, "Anime upscaling (Anime4K)", if (Appearance.anime4k) "On" else "Off") { onPage(MenuPage.Anime4K) }
        NavRow(Icons.Skip, "Skip intro & credits", if (Appearance.autoSkipStamps) (if (Appearance.autoSkipDelay5s) "Auto (5s)" else "Auto") else "Manual") { onPage(MenuPage.Skip) }
        Divider()
        ActionRow(Icons.Help, "Keyboard shortcuts") { onPage(null); Shortcuts.show() }
        ActionRow(Icons.Play, "Open in VLC") { onPage(null); s.openExternal(vlc = true) }
        ActionRow(Icons.OpenInNewWindow, "Open in browser") { onPage(null); s.openExternal(vlc = false) }
    }
}

internal fun qualityLabel(s: PlayerSession): String? {
    val v = s.currentVideo() ?: return s.resolution
    return tier(v.width, v.height) ?: v.label ?: s.resolution
}

/** The usual name of a picture size: a 1920×800 film is "1080p" (its width), not "800p" */
private fun tier(w: Int?, h: Int?): String? {
    if (w == null && h == null) return null
    val width = w ?: 0
    val height = h ?: 0
    return when {
        width >= 3800 || height >= 2100 -> "4K"
        width >= 2500 || height >= 1400 -> "1440p"
        width >= 1900 || height >= 1000 -> "1080p"
        width >= 1260 || height >= 700 -> "720p"
        width >= 840 || height >= 460 -> "480p"
        else -> "${height}p"
    }
}

@Composable
private fun SourcesPage(s: PlayerSession, onPage: (MenuPage?) -> Unit) {
    val all = s.sources()
    val sources = all.filter { it.usable || it.current }
    val hidden = all.size - sources.size
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (sources.indexOfFirst { it.current } - 2).coerceAtLeast(0))
    LazyColumn(Modifier.fillMaxWidth(), state = state) {
        itemsIndexed(sources) { _, src ->
            CheckRow(src.name.lineSequence().first(), src.current, detail = src.quality.takeIf { it.isNotBlank() }) {
                if (!src.current) { onPage(null); s.selectSource(src.link) }
            }
        }
        if (sources.isEmpty() && !s.loadingMore) item { Note("No sources") }
        if (s.loadingMore) item { Note("More sources are being collected…") }
        if (hidden > 0) item { Note("$hidden hidden by the quality profile") }
    }
}

@Composable
private fun QualityPage(s: PlayerSession) {
    val video = s.videoTracks().sortedByDescending { it.height ?: 0 }
    var pick by remember { mutableStateOf(video.indexOfFirst { it.id == s.currentVideo()?.id }) }
    if (video.isEmpty()) { Note(s.resolution?.let { "$it, the only quality of this source" } ?: "The only quality of this source"); return }
    ChoicePage(video.mapIndexed { i, t -> tier(t.width, t.height) ?: t.label ?: "${i + 1}" }, pick, details = video.map { t -> if (t.width != null && t.height != null) "${t.width}×${t.height}" else null }) {
        if (it != pick) { pick = it; s.selectVideo(video[it]) }
    }
}

@Composable
private fun AudioPage(s: PlayerSession) {
    val audio = s.audioTracks()
    var pick by remember { mutableStateOf(audio.indexOfFirst { it.id == s.currentAudio()?.id }.coerceAtLeast(0)) }
    if (audio.isEmpty()) { Note("No audio track"); return }
    ChoicePage(audio.mapIndexed { i, t -> audioLabel(t, i) }, pick) { if (it != pick) { pick = it; s.selectAudio(audio[it]) } }
}

/** The audio tracks and the picture qualities of the video in one page: what a viewer means by "tracks" */
@Composable
private fun TracksPage(s: PlayerSession) {
    val audio = s.audioTracks()
    var audioPick by remember { mutableStateOf(audio.indexOfFirst { it.id == s.currentAudio()?.id }.coerceAtLeast(0)) }
    val video = s.videoTracks().sortedByDescending { it.height ?: 0 }
    var videoPick by remember { mutableStateOf(video.indexOfFirst { it.id == s.currentVideo()?.id }) }
    Column(Modifier.verticalScrollIfNeeded()) {
        SectionLabel("Video")
        if (video.isEmpty()) Note(s.resolution?.let { "$it, the only quality of this source" } ?: "The only quality of this source")
        video.forEachIndexed { i, t ->
            CheckRow(tier(t.width, t.height) ?: t.label ?: "${i + 1}", i == videoPick, detail = if (t.width != null && t.height != null) "${t.width}×${t.height}" else null) { if (i != videoPick) { videoPick = i; s.selectVideo(t) } }
        }
        SectionLabel("Audio")
        if (audio.isEmpty()) Note("No audio track")
        audio.forEachIndexed { i, t -> CheckRow(audioLabel(t, i), i == audioPick) { if (i != audioPick) { audioPick = i; s.selectAudio(t) } } }
    }
}

@Composable
private fun SectionLabel(text: String) {
    FText(text.uppercase(), Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp), style = Fluent.type.caption.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold), color = MenuColors.tertiary, maxLines = 1)
}

@Composable
private fun DecoderPage() {
    var pick by remember { mutableStateOf(Appearance.audioDecoder) }
    Column {
        AudioDecoder.entries.forEach { d ->
            CheckRow(d.label, d == pick, detail = d.detail) {
                if (d != pick) {
                    pick = d
                    Appearance.audioDecoder = d
                    Appearance.save()
                    ioTask { com.lagradost.desktop.player.MpvPlayer.active?.applyAudioDecoder() }
                }
            }
        }
    }
}

@Composable
private fun Anime4KPage() {
    var pick by remember { mutableStateOf(Appearance.anime4k) }
    Column {
        CheckRow("Off", !pick, detail = "Standard video playback") {
            if (pick) {
                pick = false
                Appearance.anime4k = false
                Appearance.save()
                ioTask { com.lagradost.desktop.player.MpvPlayer.active?.applyAnime4k() }
            }
        }
        CheckRow("Anime4K (bloc97)", pick, detail = "Neural filters that clean up and sharpen anime in real time (GPU player)") {
            if (!pick) {
                pick = true
                Appearance.anime4k = true
                Appearance.nativePlayer = true
                Appearance.save()
                ioTask { com.lagradost.desktop.player.MpvPlayer.active?.applyAnime4k() }
            }
        }
    }
}

@Composable
private fun SkipPage() {
    var autoSkip by remember { mutableStateOf(Appearance.autoSkipStamps) }
    var delay5s by remember { mutableStateOf(Appearance.autoSkipDelay5s) }
    Column {
        CheckRow("Auto-skip", autoSkip, detail = "Automatically skip openings, recaps and credits") {
            autoSkip = !autoSkip
            Appearance.autoSkipStamps = autoSkip
            Appearance.save()
        }
        CheckRow("5-second toggle to skip", delay5s, detail = "Show a 5s countdown timer before auto-skipping") {
            delay5s = !delay5s
            Appearance.autoSkipDelay5s = delay5s
            Appearance.save()
        }
    }
}

private fun originLabel(sub: SubtitleData): String = when (sub.origin) {
    SubtitleOrigin.URL -> runCatching { java.net.URI(sub.url.trim()).host?.removePrefix("www.") }.getOrNull()?.let { "Online · $it" } ?: "Online"
    SubtitleOrigin.DOWNLOADED_FILE -> "Downloaded"
    SubtitleOrigin.EMBEDDED_IN_VIDEO -> "In the video"
}

/** Subtitles grouped by name like Android (a name with several files opens to list them), then what can be done with them */
@Composable
private fun SubtitlesPage(s: PlayerSession, onPage: (MenuPage?) -> Unit) {
    var pickState by remember { mutableStateOf(s.currentSubtitle()) }
    var groupOpen by remember { mutableStateOf(pickState?.originalName) }
    remember(s.listsVersion) { 0 } // subtitles that arrive while the menu is open: read again
    val groups = s.subtitles().groupBy { it.originalName }.entries.map { (name, list) -> name to list.sortedBy { it.nameSuffix.toIntOrNull() ?: 0 } }
    fun pick(sub: SubtitleData?) { pickState = sub; s.selectSubtitle(sub) }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (groups.indexOfFirst { it.first == pickState?.originalName }).coerceAtLeast(0))
    LazyColumn(Modifier.fillMaxWidth(), state = state) {
        item { CheckRow("Off", pickState == null) { if (pickState != null) { groupOpen = null; pick(null) } } }
        for ((name, list) in groups) {
            val inGroup = pickState?.originalName == name
            item(key = "g:$name") {
                CheckRow(name.trim(), inGroup, detail = if (list.size > 1) "${list.size} files" else originLabel(list.first()), trailing = if (list.size > 1) (if (groupOpen == name) Icons.ChevronUp else Icons.ChevronDown) else null) {
                    groupOpen = if (groupOpen == name && inGroup) null else name
                    if (!inGroup) pick(list.first())
                }
            }
            if (list.size > 1 && groupOpen == name) {
                itemsIndexed(list, key = { i, _ -> "o:$name:$i" }) { i, sub ->
                    CheckRow(sub.nameSuffix.trim().ifBlank { "File ${i + 1}" }, pickState == sub, detail = originLabel(sub), indent = 24.dp) { if (pickState != sub) pick(sub) }
                }
            }
        }
        item { Divider() }
        item { NavRow(Icons.Clock, "Delay and size", if (s.subtitleDelayMs != 0L) "${s.subtitleDelayMs} ms" else null) { onPage(MenuPage.SubtitleTiming) } }
        item { ActionRow(Icons.Edit, "Style…") { onPage(null); openSubtitleStyleDialog(s) } }
        item { ActionRow(Icons.Folder, "Add a subtitle file…") { onPage(null); s.pickSubtitleFile() } }
        item { ActionRow(Icons.Search, "Search online…") { onPage(null); openSubtitleSearch(s) } }
        item { ActionRow(Icons.Download, "Add the first online result") { onPage(null); s.addFirstOnlineSubtitle() } }
    }
}

/** Delay (steps of 100 ms) and the saved size, applied while they change */
@Composable
private fun SubtitleTimingPage(s: PlayerSession) {
    val size = com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.subtitleStyleState.value.fixedTextSize ?: com.lagradost.cloudstream3.ui.subtitles.DEFAULT_SUBTITLE_SIZE
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column {
            FText("Delay", color = MenuColors.text)
            FText("Later: the text comes after the voice", style = Fluent.type.caption, color = MenuColors.tertiary)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                StepButton("−") { s.setSubtitleDelay(s.subtitleDelayMs - 100) }
                FText("${if (s.subtitleDelayMs > 0) "+" else ""}${s.subtitleDelayMs} ms", Modifier.weight(1f), style = Fluent.type.bodyStrong.copy(fontFeatureSettings = "tnum"), color = MenuColors.text, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                StepButton("+") { s.setSubtitleDelay(s.subtitleDelayMs + 100) }
            }
        }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FText("Size", Modifier.weight(1f), color = MenuColors.text)
                FText("${Math.round(size)}", style = Fluent.type.bodyStrong.copy(fontFeatureSettings = "tnum"), color = MenuColors.secondary)
            }
            Slider(value = size, onValueChange = { s.changeSubtitleSize(it) }, valueRange = 5f..60f, modifier = Modifier.fillMaxWidth())
            FText("Saved for all videos", style = Fluent.type.caption, color = MenuColors.tertiary)
        }
        val changed = s.subtitleDelayMs != 0L || size != com.lagradost.cloudstream3.ui.subtitles.DEFAULT_SUBTITLE_SIZE
        if (changed) Box(Modifier.padding(top = 2.dp)) {
            StepButton("Reset", wide = true) { s.setSubtitleDelay(0); s.changeSubtitleSize(com.lagradost.cloudstream3.ui.subtitles.DEFAULT_SUBTITLE_SIZE) }
        }
    }
}

/** A single-choice list that opens at the current item */
@Composable
private fun ChoicePage(labels: List<String>, selected: Int, details: List<String?>? = null, onPick: (Int) -> Unit) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (selected - 2).coerceAtLeast(0))
    LazyColumn(Modifier.fillMaxWidth(), state = state) {
        itemsIndexed(labels) { i, label -> CheckRow(label, i == selected, detail = details?.getOrNull(i)) { onPick(i) } }
    }
}

// ---- rows ----------------------------------------------------------------------------------------

@Composable
private fun rowModifier(onClick: () -> Unit, role: Role = Role.Button): Modifier {
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(6.dp)
    return Modifier.fillMaxWidth().padding(horizontal = 6.dp).clip(shape)
        .background(if (hovered) MenuColors.hover else Color.Transparent, shape)
        .fluentClickable(source, true, shape, role, onClick)
}

/** A setting of the root page: icon, name, current value and an arrow to its page */
@Composable
private fun NavRow(glyph: String, label: String, value: String?, onClick: () -> Unit) {
    Row(rowModifier(onClick).heightIn(min = ROW_HEIGHT).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(glyph, size = 16.dp, tint = MenuColors.secondary)
        FText(label, Modifier.padding(start = 14.dp, end = 12.dp), color = MenuColors.text, maxLines = 1, softWrap = false)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (value != null) FText(value, color = MenuColors.secondary, maxLines = 1, softWrap = false)
        }
        Icon(Icons.ChevronRightSmall, Modifier.padding(start = 8.dp), size = 10.dp, tint = MenuColors.tertiary)
    }
}

/** A row of a choice list: a check mark on the chosen one */
@Composable
private fun CheckRow(text: String, selected: Boolean, detail: String? = null, indent: Dp = 0.dp, trailing: String? = null, onClick: () -> Unit) {
    Row(rowModifier(onClick, Role.RadioButton).heightIn(min = ROW_HEIGHT).padding(start = 10.dp + indent, end = 10.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(30.dp)) { if (selected) Icon(Icons.Check, size = 14.dp, tint = MenuColors.text) }
        Column(Modifier.weight(1f)) {
            FText(text, style = if (selected) Fluent.type.bodyStrong else Fluent.type.body, color = if (selected) MenuColors.text else MenuColors.secondary, maxLines = 2)
            if (detail != null) FText(detail, style = Fluent.type.caption, color = MenuColors.tertiary, maxLines = 2)
        }
        if (trailing != null) Icon(trailing, Modifier.padding(start = 8.dp), size = 10.dp, tint = MenuColors.tertiary)
    }
}

/** Something to do (not a setting): icon and name */
@Composable
private fun ActionRow(glyph: String, text: String, onClick: () -> Unit) {
    Row(rowModifier(onClick).height(ROW_HEIGHT).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(glyph, size = 16.dp, tint = MenuColors.secondary)
        FText(text, Modifier.padding(start = 14.dp), color = MenuColors.secondary, maxLines = 1, softWrap = false)
    }
}

/** Back arrow and the page's name; [trailing] at the right */
@Composable
private fun PageHeader(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(rowModifier(onBack).weight(1f).height(ROW_HEIGHT).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.ChevronLeftSmall, size = 10.dp, tint = MenuColors.secondary)
            FText(title, Modifier.padding(start = 14.dp), style = Fluent.type.bodyStrong, color = MenuColors.text, maxLines = 1, softWrap = false)
        }
        trailing()
    }
    Divider()
}

@Composable
private fun HeaderAction(text: String, onClick: () -> Unit) {
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.height(30.dp).clip(shape).background(if (hovered) MenuColors.hover else Color.Transparent, shape)
            .fluentClickable(source, true, shape, Role.Button, onClick).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { FText(text, style = Fluent.type.caption.copy(fontWeight = FontWeight.SemiBold), color = MenuColors.secondary, maxLines = 1, softWrap = false) }
}

@Composable
private fun StepButton(text: String, wide: Boolean = false, onClick: () -> Unit) {
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.height(34.dp).let { if (wide) it.widthIn(min = 88.dp) else it.width(48.dp) }.clip(shape)
            .background(if (hovered) Color(0x33FFFFFF) else Color(0x1FFFFFFF), shape)
            .fluentClickable(source, true, shape, Role.Button, onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { FText(text, style = Fluent.type.bodyStrong, color = MenuColors.text, maxLines = 1, softWrap = false) }
}

@Composable
private fun Note(text: String) {
    FText(text, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = Fluent.type.caption, color = MenuColors.tertiary)
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(MenuColors.divider))
}

/** The root page scrolls when the window is too low for it */
@Composable
private fun Modifier.verticalScrollIfNeeded(): Modifier = this.verticalScroll(androidx.compose.foundation.rememberScrollState())

// ---- labels --------------------------------------------------------------------------------------

internal fun audioLabel(t: com.lagradost.cloudstream3.ui.player.AudioTrack, index: Int): String {
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
