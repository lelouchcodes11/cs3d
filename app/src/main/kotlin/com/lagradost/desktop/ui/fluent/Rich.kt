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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
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
            FText(title, style = Fluent.type.subtitle.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            if (subtitle != null) FText(subtitle, style = Fluent.type.caption, color = c.textTertiary, maxLines = 1)
        }
        trailing()
        if (onSeeAll != null) SeeAllLink(onSeeAll)
    }
}

@Composable
fun SeeAllLink(onClick: () -> Unit, text: String = "View all") {
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

/** Text tabs for pages over artwork: the shown one is white and one thin line glides (spring) from the tab you leave to the one you open */
@Composable
fun UnderlineTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, counts: List<Int?>? = null) {
    val c = Fluent.colors
    // where each tab sits (px), so the line can glide between them
    val spots = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateMapOf<Int, Pair<Float, Float>>() }
    val target = spots[selected]
    val spec = if (Appearance.motion == Motion.Off) androidx.compose.animation.core.snap<Float>() else androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 460f)
    val lineX by animateFloatAsState(target?.first ?: 0f, spec, label = "tabX")
    val lineW by animateFloatAsState(target?.second ?: 0f, spec, label = "tabW")
    val density = androidx.compose.ui.platform.LocalDensity.current
    Box(modifier.horizontalScroll(rememberScrollState())) {
        Row(horizontalArrangement = Arrangement.spacedBy(30.dp), verticalAlignment = Alignment.Bottom) {
            tabs.forEachIndexed { i, text ->
                val source = rememberInteraction()
                val hovered by source.collectIsHoveredAsState()
                val on = i == selected
                val tint by animateColorAsState(if (on) c.text else if (hovered) c.textSecondary else c.textTertiary, FluentMotion.tweenStd(160), label = "tabTint")
                Column(
                    Modifier
                        .onPlacedAt { x, w -> spots[i] = x to w }
                        .fluentClickable(source, true, RoundedCornerShape(4.dp), Role.Tab) { onSelect(i) },
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        FText(text, style = Fluent.type.subtitle.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold), color = tint, maxLines = 1, softWrap = false)
                        counts?.getOrNull(i)?.takeIf { it > 0 }?.let { n ->
                            FText(n.toString(), style = Fluent.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.textTertiary, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 6.dp, bottom = 3.dp))
                        }
                    }
                    Box(Modifier.padding(top = 7.dp).height(2.dp))
                }
            }
        }
        if (target != null) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset { androidx.compose.ui.unit.IntOffset(lineX.toInt(), 0) }
                    .width(with(density) { lineW.toDp() })
                    .height(2.dp)
                    .background(c.text, RoundedCornerShape(1.dp)),
            )
        }
    }
}

private fun Modifier.onPlacedAt(onPlace: (Float, Float) -> Unit): Modifier =
    this.then(Modifier.onGloballyPositioned { onPlace(it.positionInParent().x, it.size.width.toFloat()) })

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
            // selected = a flat light pill with dark text
            val bg by animateColorAsState(if (hovered && !on) c.subtleHover else Color.Transparent, FluentMotion.tweenStd(180))
            val fg = if (on) c.bg else if (hovered) c.text else c.textSecondary
            val shape = RoundedCornerShape(50)
            Row(
                Modifier.height(34.dp).clip(shape)
                    .background(if (on) SolidColor(c.text.copy(alpha = if (hovered) 0.9f else 1f)) else SolidColor(bg), shape)
                    .fluentClickable(source, true, shape, Role.Tab) { onSelect(i) }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FText(text, style = Fluent.type.bodyStrong, color = fg, maxLines = 1, softWrap = false)
                counts?.getOrNull(i)?.let { n ->
                    Box(Modifier.width(8.dp))
                    Box(Modifier.background(if (on) c.bg.copy(alpha = 0.14f) else c.control, RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 1.dp)) {
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

/**
 * A row of small cards that pick the hero item. The shown one is wide (a spring opens it, the others close), carries the title and a thin line that
 * fills while it is on screen; the rest are narrow slices of their artwork that open a little when the pointer is over them.
 */
@Composable
fun FeaturedStrip(count: Int, selected: Int, progress: () -> Float, image: (Int) -> Pair<String?, Map<String, String>?>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, label: (Int) -> String = { "" }) {
    val spec = if (Appearance.motion == Motion.Off) androidx.compose.animation.core.snap<Dp>() else androidx.compose.animation.core.spring(dampingRatio = 0.82f, stiffness = 360f)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until count) {
            val on = i == selected
            val source = rememberInteraction()
            val hovered by source.collectIsHoveredAsState()
            val width by animateDpAsState(if (on) 168.dp else if (hovered) 66.dp else 54.dp, spec, label = "stripW")
            val dim by animateFloatAsState(if (on) 0f else if (hovered) 0.2f else 0.45f, FluentMotion.tweenIn(220), label = "stripDim")
            val shape = RoundedCornerShape(14.dp)
            val (url, headers) = image(i)
            Box(
                Modifier.size(width, 64.dp)
                    .clip(shape).background(Color(0xFF15161A))
                    .border(Dp.Hairline, if (on) Color(0x80FFFFFF) else Color(0x24FFFFFF), shape)
                    .fluentClickable(source, true, shape, Role.Button) { onSelect(i) },
            ) {
                RemoteImage(url, headers, null, Modifier.fillMaxSize(), ContentScale.Crop)
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = dim }.background(Color.Black))
                if (on) {
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xCC000000))))
                    label(i).takeIf { it.isNotBlank() }?.let { name ->
                        FText(name, style = Fluent.type.caption.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1, modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 12.dp, bottom = 15.dp))
                    }
                    // the line floats inside the slice: on the bottom row it ran into the 1 px edge and its ends into the rounded corners
                    Box(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 12.dp, bottom = 8.dp).fillMaxWidth().height(2.dp).drawBehind {
                        drawRoundRect(Color(0x40FFFFFF), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                        drawRoundRect(Color.White, size = androidx.compose.ui.geometry.Size(size.width * progress().coerceIn(0f, 1f), size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                    })
                }
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
    // dark glass with a thin white edge; the switched-on one (favourite, subscribed) is a flat white disc
    val glass by animateColorAsState(if (hovered) Color(0x66000000) else Color(0x4D000000), FluentMotion.tweenStd(140))
    Tooltip(tooltip) {
        Box(
            modifier.size(size).clip(CircleShape)
                .background(if (active) SolidColor(Color.White) else SolidColor(glass), CircleShape)
                .border(Dp.Hairline, Color.White.copy(alpha = if (active) 0f else if (hovered) 0.5f else 0.3f), CircleShape)
                .fluentClickable(source, true, CircleShape, Role.Button, onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(glyph, size = 16.dp, tint = if (active) Color(0xFF0B0B0F) else Color.White) }
    }
}

/** Pill button for artwork: accent (primary) or frosted glass */
@Composable
fun PillButton(text: String, glyph: String?, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 44.dp) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, FluentMotion.tweenIn(120))
    val shape = RoundedCornerShape(50)
    // the main action: a flat white button with dark text (Settings > Look can give it the accent colour instead); the others are dark glass with a thin white edge
    val white = primary && Appearance.whitePrimary
    val fg = if (white) Color(0xFF0B0B0F) else if (primary) c.onAccent else Color.White
    val fill: androidx.compose.ui.graphics.Brush = when {
        white -> SolidColor(Color.White.copy(alpha = if (pressed) 0.82f else if (hovered) 0.92f else 1f))
        primary -> c.accentBrush(hovered, pressed)
        else -> SolidColor(if (hovered) Color(0x66000000) else Color(0x4D000000))
    }
    Row(
        modifier.height(height).graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (primary && !white) Modifier.accentGlow(shape, if (hovered) 1.25f else 0.9f) else Modifier)
            .clip(shape)
            .background(fill, shape)
            .border(Dp.Hairline, if (primary) Color.Transparent else Color.White.copy(alpha = if (hovered) 0.5f else 0.3f), shape)
            .focusRing(source, shape)
            .fluentClickable(source, true, shape, Role.Button, onClick)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
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
        // a thin outlined tag (an age rating, a type): no fill, no accent
        Modifier.border(Dp.Hairline, Color.White.copy(alpha = if (accent) 0.7f else 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) { FText(text, style = Fluent.type.caption.copy(fontWeight = FontWeight.SemiBold), color = Color.White.copy(alpha = if (accent) 1f else 0.85f), maxLines = 1, softWrap = false) }
}

/** Overlay content on a 16:9 still with a gradient for legibility */
@Composable
fun BoxScope.BottomScrim(strength: Float = 0.8f) {
    Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = strength))))
}
