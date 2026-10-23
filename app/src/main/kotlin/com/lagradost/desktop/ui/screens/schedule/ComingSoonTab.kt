package com.lagradost.desktop.ui.screens.schedule

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.TvType
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.notify.ComingSoon
import com.lagradost.desktop.tmdb.Tmdb
import com.lagradost.desktop.tmdb.TmdbCard
import com.lagradost.desktop.ui.components.RemoteImage
import com.lagradost.desktop.ui.components.SoftImage
import com.lagradost.desktop.ui.fluent.ArtChip
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.EmptyState
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentMotion
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.IconButton
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.MenuItem
import com.lagradost.desktop.ui.fluent.PillButton
import com.lagradost.desktop.ui.fluent.PillTabs
import com.lagradost.desktop.ui.fluent.PosterCard
import com.lagradost.desktop.ui.fluent.PosterSkeleton
import com.lagradost.desktop.ui.fluent.RichSectionHeader
import com.lagradost.desktop.ui.fluent.Shelf
import com.lagradost.desktop.ui.fluent.rememberInteraction
import com.lagradost.desktop.ui.fluent.scrollEdgeFade
import com.lagradost.desktop.ui.fluent.shimmer
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val longDate = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())
private val shortDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val monthDate = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

internal fun LazyListScope.comingSoon(width: Dp) {
    item(key = "soon") { ComingSoonBody(width) }
}

/** "in 8 days", "tomorrow" ... */
private fun countdown(item: ComingSoon.Item, today: LocalDate): String {
    if (item.approx) return "date not fixed yet"
    val d = ChronoUnit.DAYS.between(today, item.date)
    return when {
        d <= 0 -> "today"
        d == 1L -> "tomorrow"
        d < 14 -> "in $d days"
        d < 60 -> "in ${d / 7} weeks"
        else -> "in ${d / 30} months"
    }
}

private fun dateLine(item: ComingSoon.Item, today: LocalDate): String = when {
    item.approx -> item.date.format(monthDate)
    item.date == today -> "Today"
    item.date == today.plusDays(1) -> "Tomorrow"
    else -> item.date.format(shortDate)
}

private fun channelLabel(item: ComingSoon.Item): String = when {
    item.channel == ComingSoon.Channel.Cinema -> "In cinemas"
    item.kind == ComingSoon.Kind.Movie -> "On streaming"
    item.by != null -> item.by
    else -> "TV & streaming"
}

private fun ComingSoon.Item.toCard(): TmdbCard = TmdbCard(
    name = title, url = "soon:$key",
    type = when (kind) { ComingSoon.Kind.Movie -> TvType.Movie; ComingSoon.Kind.Series -> TvType.TvSeries; ComingSoon.Kind.Anime -> TvType.Anime },
    posterUrl = poster, year = date.year, genres = genres, score = Score.from10(rating),
    isMovie = kind == ComingSoon.Kind.Movie, backdrop = backdrop, overview = overview,
)

@Composable
private fun ComingSoonBody(width: Dp) {
    val c = Fluent.colors
    var refresh by remember { mutableStateOf(0) }
    val loaded by produceState<Result<List<ComingSoon.Item>>?>(null, refresh) {
        value = null
        value = runCatching { ComingSoon.upcoming(force = refresh > 0) }
    }
    var kind by remember { mutableStateOf(0) }
    var where by remember { mutableStateOf(0) }
    val result = loaded
    when {
        result == null -> Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.fillMaxWidth().height(340.dp).clip(RoundedCornerShape(FluentShapes.card)).shimmer())
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { repeat(6) { PosterSkeleton(168.dp) } }
        }
        result.isFailure -> EmptyState(Icons.Warning, "The releases could not be loaded", result.exceptionOrNull()?.message ?: "Check the connection and try again.") {
            Button("Try again", { refresh++ }, kind = ButtonKind.Accent, icon = Icons.Refresh, height = 36.dp)
        }
        else -> {
            val all = result.getOrDefault(emptyList())
            val today = LocalDate.now()
            val shown = all.filter { i ->
                (kind == 0 || i.kind.ordinal == kind - 1) &&
                    (where == 0 || i.channel == (if (where == 1) ComingSoon.Channel.Cinema else ComingSoon.Channel.Streaming))
            }
            val scroll = rememberScrollState()
            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp).scrollEdgeFade(scroll).horizontalScroll(scroll),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                PillTabs(listOf("All") + ComingSoon.Kind.values().map { it.label }, kind, { kind = it })
                PillTabs(listOf("Everywhere") + ComingSoon.Channel.values().map { it.label }, where, { where = it })
                IconButton(Icons.Refresh, { refresh++ }, tooltip = "Reload", size = 32.dp, iconSize = 14.dp)
            }
            if (!Tmdb.enabled) {
                FText(
                    "Films and series come from TMDB, which is switched off (Settings > Appearance > Title information); only anime is listed.",
                    color = c.textSecondary, modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            if (shown.isEmpty()) {
                EmptyState(Icons.Calendar, "Nothing announced here yet", "Try another filter, or reload in a little while.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(30.dp)) {
                    // two of the most wanted of each kind of release, soonest first
                    val featured = shown.groupBy { it.kind to it.channel }.values.flatMap { g -> g.sortedBy { it.rank }.filter { it.poster != null }.take(2) }
                        .sortedBy { it.date }.take(8)
                    if (featured.isNotEmpty()) SoonHero(featured, today)
                    val cardWidth = if (width >= 900.dp) 176.dp else 152.dp
                    Shelves(shown, today, cardWidth)
                }
            }
        }
    }
}

@Composable
private fun Shelves(shown: List<ComingSoon.Item>, today: LocalDate, cardWidth: Dp) {
    val groups = listOf(
        Triple("Films in cinemas", "Opening in your country", shown.filter { it.kind == ComingSoon.Kind.Movie && it.channel == ComingSoon.Channel.Cinema }),
        Triple("Films coming to streaming", "Digital release", shown.filter { it.kind == ComingSoon.Kind.Movie && it.channel == ComingSoon.Channel.Streaming }),
        Triple("New series", "First episodes in the coming weeks", shown.filter { it.kind == ComingSoon.Kind.Series }),
        Triple("Anime", "Seasons and films that start soon", shown.filter { it.kind == ComingSoon.Kind.Anime }),
    )
    groups.forEach { (title, subtitle, list) ->
        if (list.isEmpty()) return@forEach
        Column {
            RichSectionHeader(title, subtitle = "$subtitle  ·  ${list.size}")
            Box(Modifier.height(12.dp))
            Shelf(list, cardWidth, gutter = 0.dp, spacing = 14.dp, key = { it.key }) { item ->
                PosterCard(
                    item.toCard(), { Navigator.search(item.title) }, cardWidth,
                    subtitle = dateLine(item, today) + (item.by?.takeIf { item.kind == ComingSoon.Kind.Series }?.let { "  ·  $it" } ?: ""),
                    menu = { listOf(MenuItem("Search extensions", Icons.Search) { Navigator.search(item.title) }) },
                )
            }
        }
    }
}

/** The slider: one release at a time, large, turning by itself every few seconds (it waits while the pointer is on it) */
@Composable
private fun SoonHero(items: List<ComingSoon.Item>, today: LocalDate) {
    var index by remember(items) { mutableStateOf(0) }
    val source = rememberInteraction()
    val hovering by source.collectIsHoveredAsState()
    LaunchedEffect(items, hovering) {
        if (hovering || items.size < 2) return@LaunchedEffect
        while (true) { delay(7000); index = (index + 1) % items.size }
    }
    val shape = RoundedCornerShape(FluentShapes.card)
    val bg = Fluent.colors.bg
    BoxWithConstraints(Modifier.fillMaxWidth().height(340.dp).clip(shape).hoverable(source)) {
        val roomy = maxWidth >= 820.dp
        Crossfade(items[index.coerceIn(0, items.lastIndex)], animationSpec = FluentMotion.tweenStd(600), label = "soonHero") { item ->
            Box(Modifier.fillMaxSize()) {
                if (item.backdrop != null) RemoteImage(item.backdrop, null, null, Modifier.fillMaxSize(), ContentScale.Crop)
                else SoftImage(item.poster, null, Modifier.fillMaxSize(), alpha = 0.9f)
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to bg.copy(alpha = 0.95f), 0.55f to bg.copy(alpha = 0.6f), 1f to bg.copy(alpha = 0.15f))))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to bg.copy(alpha = 0.6f))))
                Row(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 28.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ArtChip(item.kind.label.removeSuffix("s"), accent = true)
                            ArtChip(channelLabel(item))
                            item.rating?.let { ArtChip("★ " + "%.1f".format(it)) }
                        }
                        FText(item.title, style = Fluent.type.titleLarge.copy(fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Black), color = Color.White, maxLines = 2)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Calendar, size = 16.dp, tint = Color.White)
                            FText(
                                if (item.approx) item.date.format(monthDate) else item.date.format(longDate), style = Fluent.type.bodyStrong, color = Color.White, maxLines = 1,
                            )
                            FText(countdown(item, today), color = Fluent.colors.accentText, maxLines = 1)
                        }
                        if (item.genres.isNotEmpty()) FText(item.genres.joinToString("  ·  "), style = Fluent.type.caption, color = Color(0xB3FFFFFF), maxLines = 1)
                        item.overview?.let { FText(it, color = Color(0xD9FFFFFF), maxLines = 2, modifier = Modifier.widthIn(max = 620.dp)) }
                        Box(Modifier.height(2.dp))
                        PillButton("Find to watch", Icons.Search, primary = true, onClick = { Navigator.search(item.title) }, height = 42.dp)
                    }
                    if (roomy) Box(Modifier.height(284.dp).size(190.dp, 284.dp).clip(RoundedCornerShape(FluentShapes.card)).background(Fluent.colors.control)) {
                        RemoteImage(item.poster, null, null, Modifier.fillMaxSize(), ContentScale.Crop)
                    }
                }
            }
        }
        if (items.size > 1) {
            HeroArrow(Icons.ChevronLeft, "Previous", Modifier.align(Alignment.CenterStart).padding(start = 8.dp)) { index = (index - 1 + items.size) % items.size }
            HeroArrow(Icons.ChevronRight, "Next", Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) { index = (index + 1) % items.size }
            Row(Modifier.align(Alignment.TopEnd).padding(18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items.forEachIndexed { i, _ ->
                    Box(
                        Modifier.size(if (i == index) 22.dp else 8.dp, 4.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (i == index) Color.White else Color(0x55FFFFFF))
                            .clickable(remember { MutableInteractionSource() }, null) { index = i },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroArrow(glyph: String, tooltip: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.size(40.dp).background(Color(0x66000000), CircleShape), contentAlignment = Alignment.Center) {
        IconButton(glyph, onClick, tooltip = tooltip, size = 40.dp, iconSize = 16.dp, tint = Color.White)
    }
}
