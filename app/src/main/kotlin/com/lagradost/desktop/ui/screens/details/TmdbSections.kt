package com.lagradost.desktop.ui.screens.details

import com.lagradost.desktop.ui.fluent.hairBorder

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.core.Route
import com.lagradost.desktop.tmdb.Tmdb
import com.lagradost.desktop.tmdb.TmdbInfo
import com.lagradost.desktop.tmdb.TmdbReview
import com.lagradost.desktop.ui.components.RemoteImage
import com.lagradost.desktop.ui.fluent.ChevronButton
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.IconButton
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction
import kotlinx.coroutines.launch

private val gutter: Dp
    @Composable get() = 36.dp + com.lagradost.desktop.ui.shell.LocalDockStart.current

@Composable
private fun InfoPill(text: String, accent: Boolean = false) {
    val c = Fluent.colors
    Box(
        Modifier.background(if (accent) c.accent.copy(alpha = 0.18f) else c.card, RoundedCornerShape(50))
            .hairBorder(c.stroke, RoundedCornerShape(50)).padding(horizontal = 11.dp, vertical = 4.dp),
    ) { FText(text, style = Fluent.type.caption, color = if (accent) c.accentText else c.text, maxLines = 1, softWrap = false) }
}

@Composable
private fun MakerChip(text: String, onClick: () -> Unit) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.background(if (hovered) c.control else c.card, shape).hairBorder(if (hovered) c.strokeStrong else c.stroke, shape)
            .hoverable(source).fluentClickable(source, true, shape, Role.Button, onClick).padding(horizontal = 14.dp, vertical = 7.dp),
    ) { FText(text, style = Fluent.type.body, color = c.text, maxLines = 1, softWrap = false) }
}

private fun runtimeText(min: Int): String = if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"

/** Money as a short sentence ("$12.5 million") */
private fun moneyText(n: Long): String = when {
    n >= 1_000_000_000L -> "$" + "%.1f".format(n / 1_000_000_000.0) + " billion"
    n >= 1_000_000L -> "$" + "%.1f".format(n / 1_000_000.0) + " million"
    else -> "$" + "%,d".format(n)
}

/** A studio or a network as a tile with its logo (the name when it has none); a click opens everything it made */
@Composable
private fun MakerTile(name: String, logo: String?, onClick: () -> Unit) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(FluentShapes.control)
    if (logo == null) { MakerChip(name, onClick); return }
    // the logos are made for a light ground (dark marks, transparent): a pale tile shows all of them
    Box(
        Modifier.size(width = 132.dp, height = 68.dp).clip(shape).background(Color(if (hovered) 0xFFFFFFFF else 0xEEF4F4F4)).hairBorder(if (hovered) c.accent else c.stroke, shape)
            .hoverable(source).fluentClickable(source, true, shape, Role.Button, onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { RemoteImage(logo, null, name, Modifier.fillMaxSize(), ContentScale.Fit) }
}

@Composable
private fun SectionLabel(text: String) {
    FText(text, style = Fluent.type.bodyStrong.copy(fontSize = 17.sp), modifier = Modifier.padding(top = 4.dp))
}

/**
 * What TMDB adds under the title's header (Ayu's layout): the facts in two columns of a card, then who made it, the studios and networks as logo
 * tiles, where it streams and its collection. Each part is only there when TMDB has something for it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TmdbStrip(info: TmdbInfo, pageWidth: Dp) {
    val c = Fluent.colors
    val scope = rememberCoroutineScope()
    fun open(id: Int, name: String, company: Boolean) = scope.launch { Tmdb.byMaker(id, company, info.isMovie).takeIf { it.isNotEmpty() }?.let { Navigator.go(Route.Section(name, it)) } }
    Column(Modifier.fillMaxWidth().padding(horizontal = gutter).padding(top = 6.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        info.tagline?.let { FText("“$it”", style = Fluent.type.bodyLarge, color = c.textSecondary, maxLines = 3, modifier = Modifier.widthIn(max = 900.dp)) }
        // the facts: a label over its value, two to a row
        val facts = buildList<Pair<String, String>> {
            info.status?.let { add("Status" to it) }
            info.date?.let { add((if (info.isMovie) "Release date" else "First aired") to it) }
            info.runtime?.let { add((if (info.isMovie) "Runtime" else "Episode runtime") to (runtimeText(it) + if (!info.isMovie) " per episode" else "")) }
            info.certification?.takeIf { it.isNotBlank() }?.let { add("Age rating" to it) }
            info.country?.let { add("Origin country" to it) }
            info.language?.let { add("Original language" to it) }
            if (!info.isMovie && info.seasons != null) add("Seasons" to (info.seasons.toString() + info.episodes?.let { " · $it episodes" }.orEmpty()))
            info.rating?.let { r -> add("TMDB rating" to ("★ " + "%.1f".format(r) + (if (info.votes > 0) "  ·  ${compactCount(info.votes)} votes" else ""))) }
            info.budget?.let { add("Budget" to moneyText(it)) }
            info.revenue?.let { add("Revenue" to moneyText(it)) }
        }
        val columns = if (pageWidth - gutter * 2 >= 700.dp) 2 else 1
        val shape = RoundedCornerShape(FluentShapes.card)
        val writers = info.writers.filter { w -> info.directors.none { it.first == w.first } }
        if (facts.isNotEmpty() || info.genres.isNotEmpty()) Column(
            Modifier.widthIn(max = 1000.dp).fillMaxWidth().clip(shape).background(c.card).hairBorder(c.stroke, shape).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            facts.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    row.forEach { (label, value) ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            FText(label, style = Fluent.type.caption, color = c.textTertiary, maxLines = 1)
                            FText(value, style = Fluent.type.bodyStrong.copy(fontSize = 15.sp), maxLines = 2)
                        }
                    }
                    repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                }
            }
            if (info.genres.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FText("Genres", style = Fluent.type.caption, color = c.textTertiary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { info.genres.forEach { InfoPill(it) } }
            }
        }
        // the people behind it: a name opens their page
        if (info.directors.isNotEmpty()) {
            SectionLabel(if (info.isMovie) "Directed by" else "Created by")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { info.directors.forEach { (id, name) -> MakerChip(name) { Navigator.go(Route.Person(id, name, null)) } } }
        }
        if (writers.isNotEmpty() && info.isMovie) {
            SectionLabel("Written by")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { writers.forEach { (id, name) -> MakerChip(name) { Navigator.go(Route.Person(id, name, null)) } } }
        }
        if (info.studios.isNotEmpty()) {
            SectionLabel("Production studios")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                info.studios.take(6).forEach { (id, name) -> MakerTile(name, info.logos[id]) { open(id, name, true) } }
            }
        }
        if (info.networks.isNotEmpty()) {
            SectionLabel("Networks")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                info.networks.take(4).forEach { (id, name) -> MakerTile(name, info.logos[id]) { open(id, name, false) } }
            }
        }
        if (info.providers.isNotEmpty()) {
            SectionLabel("Streaming in ${Tmdb.region}")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                info.providers.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(c.card)) { RemoteImage(p.logo, null, p.name, Modifier.fillMaxSize(), ContentScale.Crop) }
                        FText(p.name, style = Fluent.type.body, maxLines = 1)
                    }
                }
            }
        }
        info.collection?.let { (id, name) ->
            SectionLabel("Collection")
            MakerChip("Part of $name") { scope.launch { Tmdb.collection(id)?.let { (title, parts) -> if (parts.isNotEmpty()) Navigator.go(Route.Section(title, parts)) } } }
        }
    }
}

private fun compactCount(n: Int): String = if (n >= 1000) "%.1fk".format(n / 1000.0) else n.toString()

/** TMDB serves the same picture in several sizes; the viewer asks for the whole one */
private fun bigShot(url: String): String = url.replace("/t/p/w780/", "/t/p/original/")

/** Landscape stills and backdrops, three or four to a row; a click opens the picture large */
@Composable
fun TmdbGallery(info: TmdbInfo, pageWidth: Dp) {
    val per = ((pageWidth - gutter * 2 + 14.dp) / (300.dp + 14.dp)).toInt().coerceIn(2, 5)
    Column(Modifier.padding(horizontal = gutter), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        var index = 0
        info.backdrops.chunked(per).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { url ->
                    val at = index++
                    val source = rememberInteraction()
                    val hovered by source.collectIsHoveredAsState()
                    val shape = RoundedCornerShape(FluentShapes.card)
                    Box(
                        Modifier.weight(1f).aspectRatio(16f / 9f).clip(shape).background(Fluent.colors.card)
                            .hairBorder(if (hovered) Fluent.colors.strokeStrong else Fluent.colors.stroke, shape)
                            .hoverable(source).fluentClickable(source, true, shape, Role.Button) { openShots(info.backdrops, at) },
                    ) {
                        RemoteImage(url, null, null, Modifier.fillMaxSize(), ContentScale.Crop)
                    }
                }
                repeat(per - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** The pictures of a title one at a time over the dimmed page: the arrows (or the keys) go on and back, Esc or a click outside closes */
private fun openShots(urls: List<String>, start: Int) {
    Overlays.show(Overlays.Dialog(title = null, close = null, dismissOnOutside = true, fullscreen = true) { dismiss ->
        var i by remember { mutableIntStateOf(start.coerceIn(0, urls.lastIndex)) }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Box(
            Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xE8000000)).padding(horizontal = 24.dp, vertical = 56.dp).focusRequester(focus).focusable().onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) false else when (e.key) {
                    Key.DirectionLeft -> { if (i > 0) i--; true }
                    Key.DirectionRight -> { if (i < urls.lastIndex) i++; true }
                    else -> false
                }
            },
        ) {
            RemoteImage(bigShot(urls[i]), null, null, Modifier.fillMaxSize(), ContentScale.Fit)
            if (i > 0) ChevronButton(Icons.ChevronLeft, Modifier.align(Alignment.CenterStart)) { i-- }
            if (i < urls.lastIndex) ChevronButton(Icons.ChevronRight, Modifier.align(Alignment.CenterEnd)) { i++ }
            Row(Modifier.align(Alignment.TopEnd).offset(y = (-44).dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FText("${i + 1} / ${urls.size}", style = Fluent.type.body, color = androidx.compose.ui.graphics.Color.White)
                IconButton(Icons.Close, dismiss, tooltip = "Close", tint = androidx.compose.ui.graphics.Color.White)
            }
        }
    })
}

/** Reader reviews: author, their score and the text, which opens up when clicked. Two columns where the page is wide, so the whole width is used */
@Composable
fun TmdbReviews(reviews: List<TmdbReview>, pageWidth: Dp) {
    val columns = if (pageWidth - gutter * 2 >= 900.dp) reviews.size.coerceIn(1, 2) else 1
    Row(Modifier.padding(horizontal = gutter).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(columns) { col ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                reviews.filterIndexed { i, _ -> i % columns == col }.forEach { r -> ReviewCard(r) }
            }
        }
    }
}

@Composable
private fun ReviewCard(r: TmdbReview) {
    val c = Fluent.colors
    var open by remember { mutableStateOf(false) }
    val source = rememberInteraction()
    val shape = RoundedCornerShape(FluentShapes.card)
    val text = org.jsoup.parser.Parser.unescapeEntities(r.text, false).replace(Regex("[*_#>]+"), "")
    Column(
        Modifier.fillMaxWidth().clip(shape).background(c.card).hairBorder(c.stroke, shape)
            .fluentClickable(source, true, shape, Role.Button) { open = !open }.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(c.accent.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                FText(r.author.take(1).uppercase(), style = Fluent.type.bodyStrong)
            }
            Column(Modifier.weight(1f)) {
                FText(r.author, style = Fluent.type.bodyStrong, maxLines = 1)
                r.date?.let { FText(it, style = Fluent.type.caption, color = c.textTertiary) }
            }
            r.rating?.let { InfoPill("★ " + "%.0f".format(it) + "/10") }
        }
        FText(text, color = c.textSecondary, maxLines = if (open) 80 else 6)
        if (text.length > 360) FText(if (open) "Show less" else "Read more", style = Fluent.type.caption, color = c.accentText)
    }
}
