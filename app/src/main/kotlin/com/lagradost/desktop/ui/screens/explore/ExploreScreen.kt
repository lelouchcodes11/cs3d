package com.lagradost.desktop.ui.screens.explore

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.tmdb.Tmdb
import com.lagradost.desktop.tmdb.TmdbCard
import com.lagradost.desktop.ui.components.RemoteImage
import com.lagradost.desktop.ui.fluent.Appearance
import com.lagradost.desktop.ui.fluent.ArtChip
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.Chip
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentMotion
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.MenuItem
import com.lagradost.desktop.ui.fluent.PillButton
import com.lagradost.desktop.ui.fluent.PillTabs
import com.lagradost.desktop.ui.fluent.PosterCard
import com.lagradost.desktop.ui.fluent.PosterSkeleton
import com.lagradost.desktop.ui.fluent.RankedPoster
import com.lagradost.desktop.ui.fluent.RichSectionHeader
import com.lagradost.desktop.ui.fluent.Shelf
import com.lagradost.desktop.ui.fluent.typeLabel
import kotlinx.coroutines.delay

private val gutter = 36.dp

/** What the viewer chose on the Explore part of the Search page: films, series or anime, a genre, a streaming service */
class ExploreFilters {
    var kind by mutableStateOf(ExploreKind.Movies)
    var genre by mutableStateOf<String?>(null)
    var service by mutableStateOf<String?>(null)
}

/**
 * Explore lives under the search box: trending, in cinemas, top rated, by genre and by streaming service, with a rotating banner.
 * The lists are items of the page's own LazyColumn; a poster opens a search for it in the installed extensions.
 */
fun LazyListScope.exploreSections(f: ExploreFilters, rows: List<ExploreRow>, cardWidth: Dp) {
    item(key = "explore-title") {
        Column(Modifier.padding(horizontal = gutter).padding(top = 8.dp, bottom = 14.dp)) {
            RichSectionHeader("Explore", subtitle = if (Tmdb.enabled) "Find something to watch; pick a title and your extensions look for it" else "TMDB is switched off (Settings > Appearance > Title information): most film and series lists are empty")
            Box(Modifier.height(14.dp))
            PillTabs(ExploreKind.values().map { it.label }, f.kind.ordinal, { f.kind = ExploreKind.values()[it]; f.genre = null; f.service = null })
            if (ExploreClient.hasServices(f.kind)) {
                Box(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FText("Streaming on", color = Fluent.colors.textSecondary, modifier = Modifier.padding(end = 4.dp))
                    Chip("Any", f.service == null, { f.service = null })
                    Tmdb.services.keys.forEach { s -> Chip(s, f.service == s, { f.service = if (f.service == s) null else s }) }
                }
            }
            Box(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FText("Genre", color = Fluent.colors.textSecondary, modifier = Modifier.padding(end = 4.dp))
                Chip("All", f.genre == null, { f.genre = null })
                ExploreClient.genres(f.kind).forEach { g -> Chip(g, f.genre == g, { f.genre = if (f.genre == g) null else g }) }
            }
        }
    }
    if (f.kind != ExploreKind.Anime && f.genre == null && f.service == null) item(key = "hero-${f.kind}") { ExploreHero(rows.first()) }
    rows.forEach { row ->
        item(key = "${f.kind.name}/${f.genre}/${f.service}/${row.title}") { ExploreShelf(row, cardWidth) }
    }
    item(key = "credit") {
        FText(
            "Catalogs from TMDB, AniList and Cinemeta. This product uses the TMDB API but is not endorsed or certified by TMDB.",
            color = Fluent.colors.textSecondary, style = Fluent.type.caption, modifier = Modifier.padding(horizontal = gutter, vertical = 8.dp),
        )
    }
}

/** The first five of the first list, large, rotating: backdrop, title, score and a button to look for it */
@Composable
private fun ExploreHero(first: ExploreRow) {
    val items by produceState<List<TmdbCard>>(emptyList(), first) { value = runCatching { first.load() }.getOrDefault(emptyList()).filter { it.backdrop != null }.take(5) }
    if (items.isEmpty()) return
    var index by remember(items) { mutableStateOf(0) }
    LaunchedEffect(items) {
        while (true) { delay(8000); index = (index + 1) % items.size }
    }
    val shape = RoundedCornerShape(com.lagradost.desktop.ui.fluent.FluentShapes.card)
    Box(Modifier.fillMaxWidth().padding(horizontal = gutter).padding(bottom = 30.dp).height(340.dp).clip(shape)) {
        Crossfade(items[index], animationSpec = FluentMotion.tweenStd(700), label = "exploreHero") { item ->
            Box(Modifier.fillMaxSize()) {
                RemoteImage(item.backdrop, null, null, Modifier.fillMaxSize(), ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Fluent.colors.bg.copy(alpha = 0.95f), 0.5f to Fluent.colors.bg.copy(alpha = 0.6f), 1f to Fluent.colors.bg.copy(alpha = 0.1f))))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Fluent.colors.bg.copy(alpha = 0.6f))))
                Column(Modifier.align(Alignment.BottomStart).padding(28.dp).widthIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        typeLabel(item.type)?.let { ArtChip(it, accent = true) }
                        item.year?.let { ArtChip(it.toString()) }
                        item.score?.let { ArtChip("★ " + it.toString(10, 1)) }
                    }
                    FText(item.name, style = Fluent.type.titleLarge.copy(fontSize = 38.sp, lineHeight = 44.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black), color = Color.White, maxLines = 2)
                    item.overview?.let { FText(it, color = Color(0xD9FFFFFF), maxLines = 2) }
                    Box(Modifier.height(2.dp))
                    PillButton("Find to watch", Icons.Search, primary = true, onClick = { Navigator.search(item.name) }, height = 44.dp)
                }
            }
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEachIndexed { i, _ ->
                Box(Modifier.size(if (i == index) 22.dp else 8.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(if (i == index) Color.White else Color(0x55FFFFFF)).clickable(remember { MutableInteractionSource() }, null) { index = i })
            }
        }
    }
}

@Composable
private fun ExploreShelf(row: ExploreRow, cardWidth: Dp) {
    var attempt by remember { mutableStateOf(0) }
    // null while loading, an empty list when it failed
    val cards by produceState<List<TmdbCard>?>(null, row, attempt) {
        value = null
        value = runCatching { row.load() }.getOrDefault(emptyList())
    }
    Column(Modifier.padding(bottom = Appearance.space(30.dp))) {
        RichSectionHeader(row.title, Modifier.padding(horizontal = gutter), subtitle = row.subtitle)
        Box(Modifier.height(12.dp))
        val list = cards
        when {
            list == null -> Row(Modifier.padding(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(8) { PosterSkeleton(cardWidth) }
            }
            list.isEmpty() -> Row(Modifier.padding(horizontal = gutter), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FText("Nothing to show here, or the list could not be loaded.", color = Fluent.colors.textSecondary)
                Button("Try again", { attempt++ }, kind = ButtonKind.Standard, icon = Icons.Refresh, height = 32.dp)
            }
            row.ranked -> {
                val shown = list.take(10)
                Shelf(shown, cardWidth, gutter = gutter, spacing = 6.dp, key = { it.url }) { card ->
                    RankedPoster(shown.indexOf(card) + 1, cardWidth) { PosterCard(card, { Navigator.search(card.name) }, null) }
                }
            }
            else -> Shelf(list, cardWidth, gutter = gutter, spacing = 14.dp, key = { it.url }) { card ->
                PosterCard(
                    card, { Navigator.search(card.name) }, cardWidth,
                    subtitle = listOfNotNull(card.year?.toString(), card.genres.firstOrNull()).joinToString(" · ").ifBlank { null },
                    menu = { listOf(MenuItem("Search extensions", Icons.Search) { Navigator.search(card.name) }) },
                )
            }
        }
    }
}
