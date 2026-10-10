package com.lagradost.desktop.ui.screens.person

import com.lagradost.desktop.ui.fluent.smoothWheel
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.desktop.core.Route
import com.lagradost.desktop.tmdb.Tmdb
import com.lagradost.desktop.tmdb.TmdbPersonInfo
import com.lagradost.desktop.ui.components.RemoteImage
import com.lagradost.desktop.ui.fluent.Appearance
import com.lagradost.desktop.ui.fluent.EmptyState
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentScrollbar
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.PosterCard
import com.lagradost.desktop.ui.fluent.ProgressRing
import com.lagradost.desktop.ui.fluent.RichSectionHeader
import com.lagradost.desktop.ui.fluent.Shelf
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction
import com.lagradost.desktop.ui.screens.home.openCard
import com.lagradost.desktop.ui.shell.LocalDockInset
import com.lagradost.desktop.ui.shell.TopBarHeight
import com.lagradost.desktop.ui.shell.TopBarOverlay
import java.time.LocalDate
import java.time.Period

private val gutter = 36.dp

/** An actor, director or writer: photo, life, biography, what they are known for and everything they appear in */
@Composable
fun PersonScreen(route: Route.Person) {
    val c = Fluent.colors
    // (finished, result)
    val loaded by produceState(false to (null as TmdbPersonInfo?), route.id) { value = true to runCatching { Tmdb.person(route.id) }.getOrNull() }
    val info = loaded.second
    val listState = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120 } }
        TopBarOverlay(scrolled)
        FluentScrollbar(listState, TopBarHeight)
        val cardWidth = Appearance.posterSize.width * (if (maxWidth >= 1008.dp) 1.05f else 0.92f)
        val perRow = ((maxWidth - gutter * 2 + 16.dp) / (cardWidth + 16.dp)).toInt().coerceAtLeast(2)
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().smoothWheel(listState), contentPadding = PaddingValues(top = TopBarHeight + 20.dp, bottom = 40.dp + LocalDockInset.current)) {
            item(key = "header") { Header(route, info) }
            when {
                !loaded.first -> item(key = "loading") { Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { ProgressRing() } }
                info == null -> item(key = "failed") { EmptyState(Icons.Warning, "This page could not be loaded", "TMDB did not answer. Check the connection and try again.") }
                else -> {
                    if (info.known.isNotEmpty()) item(key = "known") {
                        Column(Modifier.padding(bottom = 30.dp, top = 8.dp)) {
                            RichSectionHeader("Known for", Modifier.padding(horizontal = gutter))
                            Box(Modifier.height(12.dp))
                            Shelf(info.known, cardWidth, gutter = gutter, spacing = 14.dp, key = { it.url }) { card ->
                                PosterCard(card, { openCard(card) }, cardWidth, subtitle = listOfNotNull(card.year?.toString(), card.genres.firstOrNull()).joinToString(" · ").ifBlank { null })
                            }
                        }
                    }
                    if (info.credits.isNotEmpty()) {
                        item(key = "credits-title") { RichSectionHeader("Filmography", Modifier.padding(horizontal = gutter), subtitle = "${info.credits.size} titles, newest first") }
                        val rows = info.credits.chunked(perRow)
                        items(rows.size, key = { "credits-$it" }) { i ->
                            Row(Modifier.padding(horizontal = gutter).padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                rows[i].forEach { card -> PosterCard(card, { openCard(card) }, cardWidth, subtitle = listOfNotNull(card.year?.toString(), card.genres.firstOrNull()).joinToString(" · ").ifBlank { null }) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(route: Route.Person, info: TmdbPersonInfo?) {
    val c = Fluent.colors
    val image = info?.image ?: route.image
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = gutter).padding(bottom = 26.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        val shape = RoundedCornerShape(FluentShapes.card)
        Box(Modifier.width(210.dp).aspectRatio(2f / 3f).clip(shape).background(c.card).border(androidx.compose.ui.unit.Dp.Hairline, c.stroke, shape), contentAlignment = Alignment.Center) {
            FText(route.name.split(' ').mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString(""), style = Fluent.type.titleLarge, color = c.textTertiary)
            RemoteImage(image, null, route.name, Modifier.fillMaxSize(), ContentScale.Crop)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FText(info?.name ?: route.name, style = Fluent.type.titleLarge.copy(fontSize = 42.sp, lineHeight = 50.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black), maxLines = 2)
            val facts = buildList {
                info?.department?.let { add(it) }
                info?.birthday?.let { b ->
                    val age = runCatching { Period.between(LocalDate.parse(b), info.deathday?.let { LocalDate.parse(it) } ?: LocalDate.now()).years }.getOrNull()
                    add(if (info.deathday != null) "$b – ${info.deathday}" + (age?.let { " (aged $it)" } ?: "") else "Born $b" + (age?.let { " ($it)" } ?: ""))
                }
                info?.place?.let { add(it) }
            }
            if (facts.isNotEmpty()) FText(facts.joinToString("   •   "), style = Fluent.type.bodyStrong, color = c.textSecondary, maxLines = 2)
            info?.bio?.let { bio ->
                Box(Modifier.height(4.dp))
                FText(
                    bio.trim(), style = Fluent.type.bodyLarge.copy(lineHeight = 26.sp), color = c.textSecondary, maxLines = if (open) 60 else 8,
                    modifier = Modifier.widthIn(max = 900.dp).fluentClickable(rememberInteraction(), true, RoundedCornerShape(4.dp), Role.Button) { open = !open },
                )
                if (bio.length > 600) FText(if (open) "Show less" else "Read more", color = c.accentText, style = Fluent.type.caption)
            }
        }
    }
}
