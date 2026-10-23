package com.lagradost.desktop.ui.fluent

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ui.WatchType

/** The green of "98% Match" */
val MatchGreen = Color(0xFF46D369)

/** A score out of ten as Netflix shows a taste: "71% Match" (the extension's or TMDB's score times ten) */
fun matchText(score: com.lagradost.cloudstream3.Score?): String? {
    val v = score?.toString(10, 1)?.toFloatOrNull()?.takeIf { it > 0f } ?: return null
    return "${(v * 10).toInt().coerceIn(1, 99)}% Match"
}

/**
 * The card of a title in Netflix's interface ([Skins]): the picture alone; under the pointer it grows over its neighbours, its picture dims and the title, a
 * play button, "add to my list", "more info" and the facts come up on it. The library calls are those of the card's right-click menu ([CardLibrary]), so a
 * title put on My List here is the one the library and the title page show.
 */
@Composable
fun SkinPosterCard(
    item: SearchResponse,
    onClick: () -> Unit,
    width: Dp?,
    modifier: Modifier = Modifier,
    landscape: Boolean = false,
    subtitle: String? = null,
    showType: Boolean = false,
    onPlay: (() -> Unit)? = null,
    menu: (() -> List<MenuEntry>)? = null,
) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(FluentShapes.card)
    val lift by animateFloatAsState(if (pressed) 0.98f else if (hovered && Appearance.hoverZoom) 1.1f else 1f, FluentMotion.springSoft(), label = "skinLift")
    val info by animateFloatAsState(if (hovered) 1f else 0f, FluentMotion.tweenIn(200), label = "skinInfo")
    val owned = item.apiName.isNotBlank() && item.url.isNotBlank() && item !is com.lagradost.desktop.tmdb.TmdbCard
    val status = remember(item.url, hovered) { if (hovered && owned) runCatching { CardLibrary.status(item) }.getOrDefault(WatchType.NONE) else WatchType.NONE }
    val saved = status != WatchType.NONE
    val body = @Composable {
        Column(
            modifier
                .let { if (width != null) it.width(width) else it.fillMaxWidth() }
                // the one under the pointer is drawn above its neighbours: it grows over them
                .zIndex(if (hovered) 2f else 0f)
                .fluentClickable(source, true, shape, Role.Button, onClick),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (landscape) 16f / 9f else 2f / 3f)
                    .graphicsLayer { scaleX = lift; scaleY = lift }
                    .let { if (info > 0.05f) it.shadow((14 * info).dp, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black) else it }
                    .clip(shape),
            ) {
                PosterImage(item, Modifier.fillMaxSize())
                if (info > 0.01f) {
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = info }) {
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.2f to Color.Transparent, 0.62f to Color(0xB3000000), 1f to Color(0xF2000000))))
                        InfoPanel(item, width, owned, saved, onPlay ?: onClick, onClick)
                    }
                }
                qualityLabel(item.quality)?.let { q ->
                    Box(Modifier.align(Alignment.TopStart).padding(6.dp).graphicsLayer { alpha = 1f - info }.background(Color(0xB3000000), RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                        FText(q, style = Fluent.type.caption.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp), color = Color.White, maxLines = 1, softWrap = false)
                    }
                }
                item.watchedFraction()?.let { f -> Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()) { ProgressBar(f, height = 3.dp, color = c.accent) } }
            }
            Box(Modifier.height(8.dp))
            // under the pointer the title is on the card itself (which has grown over this line)
            if (item.name.isNotBlank()) FText(item.name, style = Fluent.type.caption.copy(fontSize = 12.5.sp), color = c.textSecondary, maxLines = 1, modifier = Modifier.padding(horizontal = 1.dp).graphicsLayer { alpha = 1f - info })
            val sub = subtitle ?: if (showType) typeLabel(item.type) else null
            if (sub != null) FText(sub, style = Fluent.type.caption, color = c.textTertiary, maxLines = 1, modifier = Modifier.padding(horizontal = 1.dp))
        }
    }
    if (menu != null) ContextMenuArea(menu) { body() }
    else if (owned) CatalogCardMenu(item) { body() }
    else body()
}

/** What comes up on a card: the round buttons, the title and the facts */
@Composable
private fun InfoPanel(item: SearchResponse, width: Dp?, owned: Boolean, saved: Boolean, onPlay: () -> Unit, onOpen: () -> Unit) {
    val narrow = width != null && width < 150.dp
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!narrow) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                SkinCircleButton(Icons.Play, "Play", onPlay, size = 30.dp, filled = true)
                if (owned) SkinCircleButton(if (saved) Icons.Check else Icons.Add, if (saved) "Remove from My List" else "Add to My List", { toggleList(item, saved) }, size = 30.dp)
                Box(Modifier.weight(1f))
                SkinCircleButton(Icons.ChevronDown, "More Info", onOpen, size = 30.dp)
            }
            FText(item.name, style = Fluent.type.bodyStrong.copy(fontSize = 13.sp, lineHeight = 17.sp), color = Color.White, maxLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                matchText(item.score)?.let { FText(it, style = Fluent.type.caption.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp), color = MatchGreen, maxLines = 1, softWrap = false) }
                val facts = listOfNotNull(CardLibrary.yearOf(item)?.toString(), typeLabel(item.type)).joinToString("  ·  ")
                if (facts.isNotEmpty()) FText(facts, style = Fluent.type.caption.copy(fontSize = 11.sp), color = Color(0xCCFFFFFF), maxLines = 1)
            }
        }
    }
}

private fun toggleList(item: SearchResponse, saved: Boolean) {
    runCatching { CardLibrary.setStatus(item, if (saved) WatchType.NONE else WatchType.PLANTOWATCH) }
}

/** A round button on a card or on the artwork: outlined and dark, or filled white */
@Composable
fun SkinCircleButton(glyph: String, tooltip: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 40.dp, filled: Boolean = false) {
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val fill by animateColorAsState(
        when {
            filled -> if (hovered) Color.White.copy(alpha = 0.85f) else Color.White
            hovered -> Color(0x99000000)
            else -> Color(0x66000000)
        },
        FluentMotion.tweenStd(140), label = "skinCircle",
    )
    Tooltip(tooltip) {
        Box(
            modifier.size(size).background(fill, CircleShape)
                .let { if (filled) it else it.border(1.5.dp, if (hovered) Color.White else Color(0x99FFFFFF), CircleShape) }
                .fluentClickable(source, true, CircleShape, Role.Button, onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(glyph, size = (size.value * 0.45f).dp, tint = if (filled) Color(0xFF111111) else Color.White) }
    }
}

/** A row's title: bold, with "Explore All ›" sliding out beside it while the pointer is on the title line (the way into the whole list) */
@Composable
fun SkinRowHeader(spec: SkinSpec, title: String, modifier: Modifier = Modifier, onSeeAll: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val slide by animateDpAsState(if (hovered) 0.dp else (-8).dp, FluentMotion.tweenIn(220), label = "skinSeeAll")
    val reveal by animateFloatAsState(if (hovered) 1f else 0f, FluentMotion.tweenIn(220), label = "skinSeeAllA")
    Row(modifier.fillMaxWidth().hoverable(source), verticalAlignment = Alignment.CenterVertically) {
        FText(title, style = Fluent.type.subtitle.copy(fontSize = spec.rowTitleSp.sp, lineHeight = (spec.rowTitleSp + 8).sp, fontWeight = FontWeight.Bold), color = c.text, maxLines = 1)
        if (onSeeAll != null) {
            Row(
                Modifier.padding(start = 12.dp).graphicsLayer { alpha = reveal; translationX = slide.toPx() }.fluentClickable(rememberInteraction(), true, RoundedCornerShape(4.dp), Role.Button, onSeeAll),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FText(spec.seeAll, style = Fluent.type.caption.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp), color = c.accentText, maxLines = 1, softWrap = false)
                Box(Modifier.width(4.dp))
                Icon(Icons.ChevronRight, size = 10.dp, tint = c.accentText)
            }
        }
        Box(Modifier.weight(1f))
        trailing()
    }
}

/** How a button of the billboard looks: the main action (white), or the quieter one beside it (grey, see-through) */
enum class SkinButtonKind { Primary, Secondary }

/** The buttons of the billboard of Home, in Netflix's shape and colours */
@Composable
fun SkinButton(label: String, glyph: String?, kind: SkinButtonKind, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 46.dp) {
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val shape = FluentShapes.pill
    val primary = kind == SkinButtonKind.Primary
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, FluentMotion.tweenIn(120), label = "skinBtn")
    val fill = if (primary) (if (hovered) Color(0xBFFFFFFF) else Color.White) else (if (hovered) Color(0x996D6D6E) else Color(0xB36D6D6E))
    val fg = if (primary) Color(0xFF111111) else Color.White
    Row(
        modifier.height(height).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape).background(fill, shape)
            .fluentClickable(source, true, shape, Role.Button, onClick)
            .padding(horizontal = if (label.isEmpty()) 0.dp else 24.dp)
            .let { if (label.isEmpty()) it.width(height) else it },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) Icon(glyph, size = 20.dp, tint = fg)
        if (label.isNotEmpty()) {
            if (glyph != null) Box(Modifier.width(10.dp))
            FText(label, style = Fluent.type.bodyStrong.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold), color = fg, maxLines = 1, softWrap = false)
        }
    }
}

/** The arrow at the end of a shelf in Netflix's interface: a tall dark strip over the edge of the row with a bare arrow on it */
@Composable
fun SkinShelfArrow(glyph: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val start = glyph == Icons.ChevronLeft
    val strip by animateFloatAsState(if (hovered) 0.85f else 0.55f, FluentMotion.tweenIn(160), label = "shelfArrow")
    Box(
        modifier.fillMaxHeight().width(46.dp)
            .background(Brush.horizontalGradient(if (start) listOf(c.bg.copy(alpha = strip), Color.Transparent) else listOf(Color.Transparent, c.bg.copy(alpha = strip))))
            .fluentClickable(source, true, RoundedCornerShape(0.dp), Role.Button, onClick),
        contentAlignment = if (start) Alignment.CenterStart else Alignment.CenterEnd,
    ) { Icon(glyph, size = if (hovered) 24.dp else 20.dp, tint = Color.White, modifier = Modifier.padding(horizontal = 10.dp)) }
}
