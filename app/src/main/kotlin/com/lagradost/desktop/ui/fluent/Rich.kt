package com.lagradost.desktop.ui.fluent

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.desktop.ui.components.RemoteImage
import com.lagradost.desktop.ui.components.SoftImage

/** Big page title with an optional line under it and controls at the right */
@Composable
fun PageHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
    val c = Fluent.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            FText(title, style = Fluent.type.title.copy(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            if (subtitle != null) FText(subtitle, color = c.textSecondary, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { trailing() }
    }
}

/** Shelf title: bold, plain, and "See all" that slides its arrow on hover */
@Composable
fun RichSectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, onSeeAll: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    val c = Fluent.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            FText(title, style = Fluent.type.subtitle.copy(fontSize = 21.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            if (subtitle != null) FText(subtitle, style = Fluent.type.caption, color = c.textTertiary, maxLines = 1)
        }
        trailing()
        if (onSeeAll != null) SeeAllLink(onSeeAll)
    }
}

@Composable
fun SeeAllLink(onClick: () -> Unit, text: String = "See all") {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shift by animateDpAsState(if (hovered) 4.dp else 0.dp, FluentMotion.tweenIn(160))
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape).background(if (hovered) c.subtleHover else Color.Transparent, shape)
            .fluentClickable(source, true, shape, Role.Button, onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FText(text, style = Fluent.type.bodyStrong, color = if (hovered) c.text else c.textSecondary, maxLines = 1, softWrap = false)
        Box(Modifier.width(6.dp + shift))
        Icon(Icons.ChevronRight, size = 11.dp, tint = if (hovered) c.accentText else c.textSecondary)
    }
}

/**
 * Pill tabs: the selected one sits on a filled pill. Counts (optional) show as small badges.
 */
@Composable
fun PillTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, counts: List<Int?>? = null) {
    val c = Fluent.colors
    Row(
        modifier.background(c.card, RoundedCornerShape(50)).border(Dp.Hairline, c.stroke, RoundedCornerShape(50)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { i, text ->
            val source = rememberInteraction()
            val hovered by source.collectIsHoveredAsState()
            val on = i == selected
            // selected = a quiet neutral pill (no accent fill)
            val bg by animateColorAsState(if (on) (if (c.dark) Color(0x2EFFFFFF) else Color(0x17000000)) else if (hovered) c.subtleHover else Color.Transparent, FluentMotion.tweenStd(180))
            val fg = if (on || hovered) c.text else c.textSecondary
            val shape = RoundedCornerShape(50)
            Row(
                Modifier.height(34.dp).clip(shape).background(bg, shape).fluentClickable(source, true, shape, Role.Tab) { onSelect(i) }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FText(text, style = Fluent.type.bodyStrong, color = fg, maxLines = 1, softWrap = false)
                counts?.getOrNull(i)?.let { n ->
                    Box(Modifier.width(8.dp))
                    Box(Modifier.background(c.control, RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 1.dp)) {
                        FText(n.toString(), style = Fluent.type.caption.copy(fontWeight = FontWeight.SemiBold), color = fg, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

/** A raised glass surface: translucent layer, hairline edge, soft shadow */
@Composable
fun Modifier.glass(radius: Dp = FluentShapes.card, elevation: Dp = 0.dp, strong: Boolean = false): Modifier {
    val c = Fluent.colors
    val shape = RoundedCornerShape(radius)
    return this
        .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape, clip = false) else Modifier)
        .clip(shape)
        .background(if (strong || elevation > 0.dp) c.flyout else c.card, shape)
        .border(Dp.Hairline, c.stroke, shape)
}

/** Icon, title, text and an optional action: for empty lists and failures */
@Composable
fun EmptyState(glyph: String, title: String, text: String? = null, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    val c = Fluent.colors
    Column(modifier.fillMaxWidth().padding(vertical = 56.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(84.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(56.dp).background(c.card, CircleShape).border(Dp.Hairline, c.stroke, CircleShape), contentAlignment = Alignment.Center) {
                Icon(glyph, size = 24.dp, tint = c.textSecondary)
            }
        }
        Box(Modifier.height(16.dp))
        FText(title, style = Fluent.type.subtitle.copy(fontWeight = FontWeight.Bold), maxLines = 2)
        if (text != null) FText(text, color = c.textSecondary, maxLines = 4, modifier = Modifier.padding(top = 6.dp).widthIn(max = 460.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Box(Modifier.height(18.dp))
        action()
    }
}

/** A big outlined rank number behind the left edge of a poster (top lists) */
@Composable
fun RankedPoster(rank: Int, width: Dp, content: @Composable () -> Unit) {
    val c = Fluent.colors
    val numberWidth = width * 0.62f
    Box(Modifier.width(numberWidth + width * 0.78f)) {
        FText(
            rank.toString(),
            Modifier.align(Alignment.BottomStart).offset(y = 18.dp),
            style = TextStyle(
                fontFamily = Fluent.type.title.fontFamily, fontWeight = FontWeight.Black, fontSize = (width.value * 0.95f).sp,
                lineHeight = (width.value * 0.95f).sp,
                drawStyle = Stroke(width = 3f),
            ),
            color = c.textSecondary,
            maxLines = 1, softWrap = false,
        )
        Box(Modifier.align(Alignment.TopEnd).width(width * 0.78f)) { content() }
    }
}

/** A row of small cards that pick the hero item; the shown one is outlined and fills while it is on screen */
@Composable
fun FeaturedStrip(count: Int, selected: Int, progress: () -> Float, image: (Int) -> Pair<String?, Map<String, String>?>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Fluent.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (i in 0 until count) {
            val on = i == selected
            val source = rememberInteraction()
            val hovered by source.collectIsHoveredAsState()
            val scale by animateFloatAsState(if (on) 1f else if (hovered) 0.97f else 0.92f, FluentMotion.tweenIn(220))
            val dim by animateFloatAsState(if (on) 0f else if (hovered) 0.25f else 0.5f, FluentMotion.tweenIn(220))
            val shape = RoundedCornerShape(FluentShapes.small)
            val (url, headers) = image(i)
            Box(
                Modifier.size(132.dp, 74.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(shape).background(Color(0xFF15161A))
                    .border(if (on) 2.dp else Dp.Hairline, if (on) Color.White else Color(0x33FFFFFF), shape)
                    .fluentClickable(source, true, shape, Role.Button) { onSelect(i) },
            ) {
                RemoteImage(url, headers, null, Modifier.fillMaxSize(), ContentScale.Crop)
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = dim }.background(Color.Black))
                if (on) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).drawBehind {
                    drawRect(Color(0x55FFFFFF))
                    drawRect(c.accent, size = androidx.compose.ui.geometry.Size(size.width * progress().coerceIn(0f, 1f), size.height))
                })
            }
        }
    }
}

/** Circle icon button on artwork (favourite, trailer, more) */
@Composable
fun GlassCircleButton(glyph: String, tooltip: String, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false, size: Dp = 44.dp) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val bg by animateColorAsState(if (active) c.accent.copy(alpha = 0.25f) else if (hovered) Color(0x40FFFFFF) else Color(0x24FFFFFF), FluentMotion.tweenStd(140))
    Tooltip(tooltip) {
        Box(
            modifier.size(size).clip(CircleShape).background(bg, CircleShape).border(Dp.Hairline, if (active) c.accent else Color(0x33FFFFFF), CircleShape)
                .fluentClickable(source, true, CircleShape, Role.Button, onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(glyph, size = 16.dp, tint = if (active) c.accentText else Color.White) }
    }
}

/** Pill button for artwork: accent (primary) or frosted glass */
@Composable
fun PillButton(text: String, glyph: String?, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 44.dp) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val scale = 1f
    val shape = RoundedCornerShape(50)
    // on artwork the main action is plain white with dark text, the others frosted: no accent glow
    val bg = if (primary) Color.White.copy(alpha = if (hovered) 0.86f else 1f) else Color.White.copy(alpha = if (hovered) 0.22f else 0.14f)
    val fg = if (primary) Color(0xFF111114) else Color.White
    Row(
        modifier.height(height).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape).background(bg, shape)
            .border(Dp.Hairline, if (primary) Color.Transparent else Color(0x40FFFFFF), shape)
            .focusRing(source, shape)
            .fluentClickable(source, true, shape, Role.Button, onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            Icon(glyph, size = 16.dp, tint = fg)
            Box(Modifier.width(10.dp))
        }
        FText(text, style = Fluent.type.bodyStrong, color = fg, maxLines = 1, softWrap = false)
    }
}

/** Small pill label on artwork */
@Composable
fun ArtChip(text: String, accent: Boolean = false) {
    val c = Fluent.colors
    Box(
        Modifier.background(if (accent) Color(0x40FFFFFF) else Color(0x26FFFFFF), RoundedCornerShape(50))
            .border(Dp.Hairline, Color(0x2EFFFFFF), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) { FText(text, style = Fluent.type.caption.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1, softWrap = false) }
}

/** Overlay content on a 16:9 still with a gradient for legibility */
@Composable
fun BoxScope.BottomScrim(strength: Float = 0.8f) {
    Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = strength))))
}
