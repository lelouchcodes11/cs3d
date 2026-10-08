package com.lagradost.desktop.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction
import kotlinx.coroutines.launch

private val gutter = 36.dp

@Composable
private fun InfoPill(text: String, accent: Boolean = false) {
    val c = Fluent.colors
    Box(
        Modifier.background(if (accent) c.accent.copy(alpha = 0.18f) else c.card, RoundedCornerShape(50))
            .border(androidx.compose.ui.unit.Dp.Hairline, c.stroke, RoundedCornerShape(50)).padding(horizontal = 11.dp, vertical = 4.dp),
    ) { FText(text, style = Fluent.type.caption, color = if (accent) c.accentText else c.text, maxLines = 1, softWrap = false) }
}

@Composable
private fun MakerChip(text: String, onClick: () -> Unit) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.background(if (hovered) c.control else c.card, shape).border(androidx.compose.ui.unit.Dp.Hairline, if (hovered) c.strokeStrong else c.stroke, shape)
            .hoverable(source).fluentClickable(source, true, shape, Role.Button, onClick).padding(horizontal = 12.dp, vertical = 5.dp),
    ) { FText(text, style = Fluent.type.caption, color = c.textSecondary, maxLines = 1, softWrap = false) }
}

private fun runtimeText(min: Int): String = if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"

/** What TMDB adds under the title's header: score, age rating, length, crew, studios, where it streams and its collection */
@Composable
fun TmdbStrip(info: TmdbInfo) {
    val c = Fluent.colors
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().padding(horizontal = gutter).padding(top = 6.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        info.tagline?.let { FText("“$it”", style = Fluent.type.bodyLarge, color = c.textSecondary, maxLines = 2, modifier = Modifier.widthIn(max = 900.dp)) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            info.rating?.let { InfoPill("★ " + "%.1f".format(it) + (if (info.votes > 0) "  ·  ${compactCount(info.votes)} votes" else "") + "  ·  TMDB", accent = true) }
            info.certification?.let { InfoPill(it) }
            info.runtime?.let { InfoPill(runtimeText(it) + if (!info.isMovie) " per episode" else "") }
            if (!info.isMovie && info.seasons != null) InfoPill(buildString { append(info.seasons).append(if (info.seasons == 1) " season" else " seasons"); info.episodes?.let { append(" · $it episodes") } })
            info.status?.takeIf { it != "Released" }?.let { InfoPill(it) }
            info.date?.let { InfoPill(it) }
        }
        // the people behind it: a name opens their page
        val crew = buildList {
            if (info.directors.isNotEmpty()) add((if (info.isMovie) "Directed by" else "Created by") to info.directors)
            if (info.writers.isNotEmpty() && info.isMovie) add("Written by" to info.writers.filter { w -> info.directors.none { it.first == w.first } })
        }.filter { it.second.isNotEmpty() }
        if (crew.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            crew.forEach { (label, people) ->
                FText(label, style = Fluent.type.caption, color = c.textSecondary, modifier = Modifier.padding(end = 2.dp))
                people.forEach { (id, name) -> MakerChip(name) { Navigator.go(Route.Person(id, name, null)) } }
                Box(Modifier.width(10.dp))
            }
        }
        // a studio or a network opens everything it made
        if (info.networks.isNotEmpty() || info.studios.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            info.networks.take(3).forEach { (id, name) -> MakerChip(name) { scope.launch { Tmdb.byMaker(id, false, info.isMovie).takeIf { it.isNotEmpty() }?.let { Navigator.go(Route.Section(name, it)) } } } }
            info.studios.take(4).forEach { (id, name) -> MakerChip(name) { scope.launch { Tmdb.byMaker(id, true, info.isMovie).takeIf { it.isNotEmpty() }?.let { Navigator.go(Route.Section(name, it)) } } } }
        }
        if (info.providers.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FText("Streaming in ${Tmdb.region}", style = Fluent.type.caption, color = c.textSecondary)
                info.providers.forEach { p ->
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(c.card)) { RemoteImage(p.logo, null, p.name, Modifier.fillMaxSize(), ContentScale.Crop) }
                }
            }
        }
        info.collection?.let { (id, name) ->
            val source = rememberInteraction()
            Row(
                Modifier.clip(RoundedCornerShape(FluentShapes.control)).background(c.card).border(androidx.compose.ui.unit.Dp.Hairline, c.stroke, RoundedCornerShape(FluentShapes.control))
                    .fluentClickable(source, true, RoundedCornerShape(FluentShapes.control), Role.Button) {
                        scope.launch { Tmdb.collection(id)?.let { (title, parts) -> if (parts.isNotEmpty()) Navigator.go(Route.Section(title, parts)) } }
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Video, size = 16.dp, tint = c.textSecondary)
                FText("Part of $name", style = Fluent.type.bodyStrong)
                Icon(Icons.ChevronRightSmall, size = 12.dp, tint = c.textSecondary)
            }
        }
    }
}

private fun compactCount(n: Int): String = if (n >= 1000) "%.1fk".format(n / 1000.0) else n.toString()

/** Landscape stills and backdrops, three or four to a row */
@Composable
fun TmdbGallery(info: TmdbInfo, pageWidth: Dp) {
    val per = ((pageWidth - gutter * 2 + 14.dp) / (300.dp + 14.dp)).toInt().coerceIn(2, 5)
    Column(Modifier.padding(horizontal = gutter), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        info.backdrops.chunked(per).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { url ->
                    Box(Modifier.weight(1f).aspectRatio(16f / 9f).clip(RoundedCornerShape(FluentShapes.card)).background(Fluent.colors.card).border(androidx.compose.ui.unit.Dp.Hairline, Fluent.colors.stroke, RoundedCornerShape(FluentShapes.card))) {
                        RemoteImage(url, null, null, Modifier.fillMaxSize(), ContentScale.Crop)
                    }
                }
                repeat(per - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** Reader reviews: author, their score and the text, which opens up when clicked */
@Composable
fun TmdbReviews(reviews: List<TmdbReview>) {
    val c = Fluent.colors
    Column(Modifier.padding(horizontal = gutter), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        reviews.forEach { r ->
            var open by remember { mutableStateOf(false) }
            val source = rememberInteraction()
            val shape = RoundedCornerShape(FluentShapes.card)
            Column(
                Modifier.widthIn(max = 1000.dp).fillMaxWidth().clip(shape).background(c.card).border(androidx.compose.ui.unit.Dp.Hairline, c.stroke, shape)
                    .fluentClickable(source, true, shape, Role.Button) { open = !open }.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(50)).background(c.accent.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                        FText(r.author.take(1).uppercase(), style = Fluent.type.bodyStrong)
                    }
                    Column(Modifier.weight(1f)) {
                        FText(r.author, style = Fluent.type.bodyStrong, maxLines = 1)
                        r.date?.let { FText(it, style = Fluent.type.caption, color = c.textTertiary) }
                    }
                    r.rating?.let { InfoPill("★ " + "%.0f".format(it) + "/10") }
                }
                FText(r.text.replace(Regex("[*_#>]+"), ""), color = c.textSecondary, maxLines = if (open) 60 else 5)
            }
        }
    }
}
