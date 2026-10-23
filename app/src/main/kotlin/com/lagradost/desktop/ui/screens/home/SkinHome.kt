package com.lagradost.desktop.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.isMovieType
import com.lagradost.cloudstream3.ui.result.START_ACTION_RESUME_LATEST
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.core.Route
import com.lagradost.desktop.ui.components.RemoteImage
import com.lagradost.desktop.ui.fluent.ArtChip
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentMotion
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.MatchGreen
import com.lagradost.desktop.ui.fluent.SkinButton
import com.lagradost.desktop.ui.fluent.SkinButtonKind
import com.lagradost.desktop.ui.fluent.SkinSpec
import com.lagradost.desktop.ui.fluent.matchText
import com.lagradost.desktop.ui.fluent.rememberInteraction
import com.lagradost.desktop.ui.shell.AmbientArtwork
import com.lagradost.desktop.ui.shell.LocalDockStart
import com.lagradost.desktop.ui.shell.TopBarHeight

private val ageRatingForm = Regex("""(?i)(tv-?)?(g|pg|pg-?13|r|nc-?17|ma|y7?|u|ua|a|nr|u/a ?\d{1,2}\+?|\d{1,2}\+?)""")

/** An age rating that looks like one (PG-13, TV-MA, 16+, U/A 13+); extensions also put the name of a site ("IMDB") in that field, which is no rating */
internal fun String?.ageRating(): String? = this?.trim()?.takeIf { it.isNotEmpty() && ageRatingForm.matches(it) }

/** How high the billboard is: most of the window, as Netflix's is */
internal fun skinHeroHeight(window: Dp): Dp = (window * 0.9f).coerceIn(480.dp, 860.dp)

/** How far the first row climbs into the foot of the billboard (Netflix lays its rows over the picture) */
internal val SkinRowOverlap: Dp = 96.dp

/**
 * The billboard at the top of Home in Netflix's interface: the title that is featured fills the width with its text low at the left (logo or name, how well it
 * matches, a few lines of the story, Play and More Info) and the age rating on a dark strip at the right edge. The titles the page loaded take turns every nine
 * seconds (the pointer on the billboard holds it); small marks show which one is up.
 */
@Composable
internal fun SkinHero(spec: SkinSpec, all: List<LoadResponse>, height: Dp, width: Dp, scroll: () -> Float) {
    val c = Fluent.colors
    val gutter = 36.dp + LocalDockStart.current
    val items = remember(all, all.size) { all.take(8) }
    var index by remember { mutableStateOf(0) }
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val progress = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(index, hovered, items.size) {
        if (items.size <= 1 || hovered) return@LaunchedEffect
        while (progress.floatValue < 1f) {
            kotlinx.coroutines.delay(HERO_TICK_MS.toLong())
            progress.floatValue = (progress.floatValue + HERO_TICK_MS / HERO_MS.toFloat()).coerceAtMost(1f)
        }
        progress.floatValue = 0f
        index = (index + 1) % items.size
    }
    val current = items[index.coerceIn(0, items.lastIndex)]
    // artwork TMDB has for titles the extension sent no backdrop or logo for: this one and the next are looked up
    val art = remember(items) { mutableStateMapOf<String, com.lagradost.desktop.tmdb.TmdbInfo>() }
    LaunchedEffect(index, items) {
        for (item in listOf(current, items[(index + 1) % items.size])) {
            if (art.containsKey(item.url) || (item.backgroundPosterUrl != null && item.logoUrl != null)) continue
            runCatching { com.lagradost.desktop.tmdb.Tmdb.info(item.name, item.year, item.type?.isMovieType() == true, item.syncData.values) }.getOrNull()?.let { art[item.url] = it }
        }
    }
    AmbientArtwork(current.backgroundPosterUrl ?: current.posterUrl, current.posterHeaders)
    Box(Modifier.fillMaxWidth().height(height).hoverable(source)) {
        Crossfade(current, animationSpec = FluentMotion.tweenStd(800), label = "skinHeroBackdrop") { item ->
            HeroBackdrop(item, art[item.url]?.backdrop, scroll, scrims = false, fadeStart = 0.5f)
        }
        // the shade that keeps the white text readable: dark at the left, clear at the right
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.66f), 0.5f to Color.Black.copy(alpha = 0.2f), 0.85f to Color.Transparent)))
        // ... which does not stop at the billboard's foot with a visible edge: the page colour takes over there
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.62f to Color.Transparent, 1f to c.bg)))
        AnimatedContent(
            current,
            modifier = Modifier.fillMaxSize().graphicsLayer { val s = scroll().coerceAtMost(height.toPx()); translationY = s * 0.18f; alpha = (1f - s / (height.toPx() * 0.5f)).coerceIn(0f, 1f) },
            transitionSpec = { fadeIn(FluentMotion.tweenIn(420)) togetherWith fadeOut(FluentMotion.tweenOut(160)) },
            label = "skinHeroText",
        ) { item -> SkinHeroText(spec, item, gutter, art[item.url]?.logo, width, height) }
        // the age rating at the foot of the right edge, on a dark strip
        current.contentRating.ageRating()?.let { rating ->
            Row(
                Modifier.align(Alignment.BottomEnd).padding(bottom = SkinRowOverlap + 74.dp).background(Color(0x66000000)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(3.dp).height(34.dp).background(Color(0xCCFFFFFF)))
                FText(rating, Modifier.padding(start = 14.dp, end = 40.dp), style = Fluent.type.bodyStrong.copy(fontSize = 17.sp), color = Color.White, maxLines = 1, softWrap = false)
            }
        }
        // which of the titles is up: a mark each, the one up filled while it is on screen
        if (items.size > 1) Row(
            Modifier.align(Alignment.BottomEnd).padding(end = 40.dp, bottom = SkinRowOverlap + 140.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { i, _ ->
                Box(Modifier.size(width = if (i == index) 26.dp else 14.dp, height = 14.dp).clickableNoRipple { progress.floatValue = 0f; index = i }, contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x4DFFFFFF))) {
                        if (i == index) Box(Modifier.fillMaxHeight().fillMaxWidth(if (hovered) 1f else progress.floatValue).background(Color.White))
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(remember { MutableInteractionSource() }, null, onClick = onClick)

@Composable
private fun SkinHeroText(spec: SkinSpec, item: LoadResponse, gutter: Dp, tmdbLogo: String?, window: Dp, height: Dp) {
    val compact = window < 900.dp
    val bottom = SkinRowOverlap + 74.dp
    // what is left of the billboard's height below the top bar: a short window shows less (the plot goes first, then the genres, the logo shrinks) and never cuts the buttons
    val room = height - TopBarHeight - bottom - 8.dp
    val showPlot = room >= 330.dp
    val showTags = room >= 285.dp
    val logoHeight = if (room < 255.dp) 64.dp else if (compact) 84.dp else 124.dp
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = gutter, bottom = bottom, top = TopBarHeight + 8.dp).widthIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val logo = item.logoUrl ?: tmdbLogo
            if (logo != null) {
                RemoteImage(logo, item.posterHeaders, item.name, Modifier.height(logoHeight).widthIn(max = 480.dp), ContentScale.Fit, alignment = Alignment.CenterStart)
            } else {
                val size = if (compact || room < 255.dp) 34 else 54
                FText(
                    item.name, style = Fluent.type.titleLarge.copy(fontSize = size.sp, lineHeight = (size + 6).sp, fontWeight = FontWeight.Black, shadow = Shadow(Color(0x99000000), Offset(0f, 2f), 14f)),
                    color = Color.White, maxLines = 2,
                )
            }
            // the facts: how well it matches (the score times ten), the year, how long
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                matchText(item.score)?.let { FText(it, style = Fluent.type.bodyStrong.copy(fontSize = 16.sp), color = MatchGreen, maxLines = 1, softWrap = false) }
                val facts = buildList {
                    item.year?.let { add(it.toString()) }
                    item.duration?.takeIf { it > 0 }?.let { add(if (it >= 60) "${it / 60}h ${it % 60}m" else "$it min") }
                }
                if (facts.isNotEmpty()) FText(facts.joinToString("   "), style = Fluent.type.bodyStrong.copy(fontSize = 15.sp), color = Color(0xE6FFFFFF), maxLines = 1, softWrap = false)
            }
            val tags = item.tags?.take(3).orEmpty()
            if (tags.isNotEmpty() && showTags) FText(tags.joinToString("  •  "), style = Fluent.type.bodyStrong.copy(fontSize = 14.sp), color = Color(0xCCFFFFFF), maxLines = 1)
            if (showPlot) item.plot?.takeIf { it.isNotBlank() }?.let { FText(stripHtml(it), style = Fluent.type.bodyLarge.copy(fontSize = 17.sp, lineHeight = 25.sp, shadow = Shadow(Color(0x99000000), Offset(0f, 1f), 8f)), color = Color(0xF2FFFFFF), maxLines = 3) }
            Box(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                SkinButton(spec.play, Icons.Play, SkinButtonKind.Primary, { Navigator.go(Route.Details(item.url, item.apiName, item.name, item.posterUrl, START_ACTION_RESUME_LATEST)) })
                SkinButton(spec.more, Icons.Info, SkinButtonKind.Secondary, { Navigator.go(Route.Details(item.url, item.apiName, item.name, item.posterUrl)) })
            }
        }
    }
}
