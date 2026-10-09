package com.lagradost.desktop.ui.screens.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import com.lagradost.cloudstream3.ui.player.CSPlayerLoading
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.ui.fluent.Appearance
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentMotion
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.Motion
import com.lagradost.desktop.ui.fluent.Tooltip
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction
import com.lagradost.desktop.ui.shell.noWindowDrag
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.cos
import kotlin.math.sin

// The player's modern look: pieces float on the picture (a glass back button, two glass capsules for the buttons, a thin seek bar, pills for
// the source and the subtitles at the top) instead of a heavy shade over everything. One easing for all movement (an ease-out that lands
// softly, the way Ayu's player does it) and every animation reads its state inside a layer or draw block, so a moving control never
// recomposes the screen (the picture's frames must not arrive late, see the notes at SubtitlePill).

/** Fast start, soft landing: the curve of everything that moves in the player */
internal val ExpoOut: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** An animation of [ms] with the player's easing; none at all when the user turned motion off */
internal fun <T> glide(ms: Int): FiniteAnimationSpec<T> = if (Appearance.motion == Motion.Off) snap() else tween(FluentMotion.ms(ms), easing = ExpoOut)

internal val GlassFill = Color(0xB80F1013)
internal val GlassBorder = Color(0x29FFFFFF)
private val HoverFill = Color(0x2EFFFFFF)
private val Ink = Color(0xFF111114)

/**
 * The controls over the picture, in a fixed order that never moves:
 *  - top left: the back button and what plays (title, episode, quality and source);
 *  - bottom: the seek bar, then at the left the jump / play / volume capsule and the time, at the right one capsule of tools
 *    (Sources, Subtitles, Audio & video | Speed, Episodes, Picture in picture, Settings, Full screen).
 * [visible] fades them in and out together (top slides down, bottom slides up).
 */
@Composable
internal fun ModernChrome(
    s: PlayerSession,
    visible: Boolean,
    fullscreen: Boolean,
    openMenu: (MenuPage, Rect?) -> Unit,
    toggleEpisodes: () -> Unit,
    toggleFullscreen: () -> Unit,
    onTop: (Rect) -> Unit,
    onBottom: (Rect) -> Unit,
    episodesOpen: Boolean = false,
    loading: Boolean = false,
) {
    val reveal by animateFloatAsState(if (visible) 1f else 0f, glide(if (visible) 380 else 420))
    // hidden controls are not there at all: a click on an invisible button must not do anything
    val shown by remember { derivedStateOf { reveal > 0.02f } }
    if (!shown) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 760.dp
        val labels = maxWidth >= 1040.dp
        val roomy = maxWidth >= 1100.dp
        // ---- top: back and what plays
        Box(
            Modifier.align(Alignment.TopStart).fillMaxWidth()
                .graphicsLayer { alpha = reveal; translationY = -(1f - reveal) * 16.dp.toPx() }
                .background(Brush.verticalGradient(0f to Color(0x99000000), 0.55f to Color(0x40000000), 1f to Color.Transparent))
                .padding(start = 28.dp, end = 28.dp + com.lagradost.desktop.ui.shell.captionInset, top = 16.dp, bottom = 44.dp),
        ) {
            Row(Modifier.fillMaxWidth().onGloballyPositioned { onTop(it.boundsInRoot()) }, verticalAlignment = Alignment.CenterVertically) {
                GlassButton("Back (Esc)", Modifier.noWindowDrag("playerBack"), size = 44.dp, glass = true, onClick = { Navigator.back() }) { Icon(Icons.Back, size = 18.dp, tint = Color.White) }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    if (s.title.isNotBlank()) FText(s.title, style = Fluent.type.subtitle.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1)
                    s.episodeLabel?.let { FText(it, color = Color(0xCCFFFFFF), maxLines = 1) }
                    val playing = listOfNotNull(qualityLabel(s), s.sourceName?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() }).joinToString("  ·  ")
                    if (playing.isNotBlank() && !loading) FText(playing, style = Fluent.type.caption, color = Color(0x99FFFFFF), maxLines = 1)
                }
                // video and audio tracks live at the top right (not while the loading screen is up)
                if (!loading && !episodesOpen) {
                    Spacer(Modifier.width(16.dp))
                    Capsule(44.dp) {
                        ToolButton(Icons.Video, "Video & Audio", labels, "Video and audio tracks (A: next audio)") { openMenu(MenuPage.Tracks, it) }
                    }
                }
            }
        }
        // ---- bottom: seek bar, then the two capsules (not while the loading screen is up: there is nothing to control yet)
        if (!loading) Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .graphicsLayer { alpha = reveal; translationY = (1f - reveal) * 20.dp.toPx() }
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.5f to Color(0x4D000000), 1f to Color(0xB3000000)))
                .padding(start = 28.dp, end = 28.dp, top = 72.dp, bottom = 20.dp)
                .onGloballyPositioned { onBottom(it.boundsInRoot()) },
        ) {
            ModernSeekBar(s)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Capsule {
                    // play sits between the two 10 s jumps, the episode buttons at the ends
                    if (s.hasPrev && !compact) PlayerIconButton(Icons.Previous, "Previous episode (Ctrl+Left)", iconSize = 16.dp) { s.prevEpisode() }
                    SeekButton(back = true, "Back 10 s (J)") { s.seekBy(-10_000) }
                    PlayButton(s)
                    SeekButton(back = false, "Forward 10 s (L)") { s.seekBy(10_000) }
                    if (s.hasNext && !compact) PlayerIconButton(Icons.Next, "Next episode (Ctrl+Right)", iconSize = 16.dp) { s.nextEpisode() }
                    Spacer(Modifier.width(2.dp))
                    VolumeControl(s)
                    if (s.live) { Spacer(Modifier.width(6.dp)); LivePill(s) }
                    Spacer(Modifier.width(6.dp))
                }
                Spacer(Modifier.width(18.dp))
                if (!compact) TimeText(s, showEnds = roomy)
                Box(Modifier.weight(1f))
                Capsule {
                    ToolButton(Icons.Subtitles, "Subtitles", labels, "Subtitles (S: next)") { openMenu(MenuPage.Subtitles, it) }
                    ToolButton(Icons.Link, "Sources", labels, "Sources: pick another server") { openMenu(MenuPage.Sources, it) }
                    CapsuleDivider()
                    if (!compact) {
                        if (s.speed != 1f) SpeedChip(s) { openMenu(MenuPage.Speed, it) }
                        else AnchoredIconButton(Icons.Speed, "Playback speed") { openMenu(MenuPage.Speed, it) }
                        PlayerIconButton(Icons.List, "Episodes (E)", onClick = toggleEpisodes)
                    }
                    PlayerIconButton(Icons.Pip, "Picture in picture (I)") { s.togglePip() }
                    AnchoredIconButton(Icons.Settings, "All settings and keyboard shortcuts") { openMenu(MenuPage.Root, it) }
                    PlayerIconButton(if (fullscreen) Icons.ExitFullscreen else Icons.Fullscreen, if (fullscreen) "Exit full screen (F)" else "Full screen (F)", onClick = toggleFullscreen)
                }
            }
        }
    }
}

/** A tool of the right capsule: an icon, with its name beside it when the window is wide; [on] puts a little mark on it (subtitles are showing) */
@Composable
private fun ToolButton(glyph: String, label: String, showLabel: Boolean, tooltip: String, onClick: (Rect?) -> Unit) {
    val bounds = remember { arrayOfNulls<Rect>(1) }
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, glide(180))
    val bg by animateColorAsState(if (hovered) HoverFill else Color.Transparent, glide(160))
    val shape = RoundedCornerShape(50)
    Tooltip(tooltip) {
        Row(
            Modifier.onGloballyPositioned { bounds[0] = it.boundsInRoot() }.height(40.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(shape).background(bg, shape)
                .fluentClickable(source, true, shape, Role.Button) { onClick(bounds[0]) }.padding(horizontal = if (showLabel) 14.dp else 11.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                Icon(glyph, size = 18.dp, tint = Color.White)
            }
            if (showLabel) FText(label, style = Fluent.type.bodyStrong.copy(fontSize = 13.sp), color = Color.White, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun CapsuleDivider() {
    Box(Modifier.padding(horizontal = 5.dp).width(1.dp).height(22.dp).background(Color(0x2EFFFFFF)))
}

// ---------------------------------------------------------------------------------------------------------------------------------
// bottom

/** The floating pill that holds a group of buttons */
@Composable
private fun Capsule(height: Dp = 54.dp, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.height(height).clip(shape).background(GlassFill, shape).border(Dp.Hairline, GlassBorder, shape).padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp), content = content,
    )
}

/** A round button with the player's hover and press feeling; [glass] gives it a see-through disc of its own (for use outside a capsule) */
@Composable
internal fun GlassButton(
    tooltip: String, modifier: Modifier = Modifier, size: Dp = 40.dp, glass: Boolean = false, onClick: () -> Unit, content: @Composable () -> Unit,
) {
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else if (hovered) 1.08f else 1f, glide(200))
    val bg by animateColorAsState(if (hovered) HoverFill else if (glass) GlassFill else Color.Transparent, glide(160))
    Tooltip(tooltip) {
        Box(
            modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(bg, CircleShape)
                .let { if (glass) it.border(Dp.Hairline, GlassBorder, CircleShape) else it }
                .fluentClickable(source, true, CircleShape, Role.Button, onClick),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

/** An icon button that tells where it is when clicked (the menu it opens grows out of that place) */
@Composable
private fun AnchoredIconButton(glyph: String, tooltip: String, onClick: (Rect?) -> Unit) {
    val bounds = remember { arrayOfNulls<Rect>(1) }
    GlassButton(tooltip, Modifier.onGloballyPositioned { bounds[0] = it.boundsInRoot() }, onClick = { onClick(bounds[0]) }) { Icon(glyph, size = 18.dp, tint = Color.White) }
}

@Composable
private fun PlayerIconButton(glyph: String, tooltip: String, iconSize: Dp = 18.dp, onClick: () -> Unit) =
    GlassButton(tooltip, onClick = onClick) { Icon(glyph, size = iconSize, tint = Color.White) }

/** A circular arrow with the seconds in it, for the jump back and forward */
@Composable
private fun SeekButton(back: Boolean, tooltip: String, seconds: Int = 10, onClick: () -> Unit) =
    GlassButton(tooltip, onClick = onClick) { SeekGlyph(back, seconds) }

@Composable
private fun SeekGlyph(back: Boolean, seconds: Int, glyphSize: Dp = 26.dp) {
    Box(Modifier.size(glyphSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.085f
            val pad = stroke * 1.7f
            val d = this.size.minDimension - pad * 2
            val center = Offset(this.size.width / 2, this.size.height / 2)
            val r = d / 2
            withTransform({ if (!back) scale(-1f, 1f, center) }) {
                // an almost closed circle, open at the top, with the arrow head where it ends
                drawArc(Color.White, startAngle = -110f, sweepAngle = -300f, useCenter = false, topLeft = Offset(pad, pad), size = Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
                val a = Math.toRadians(-50.0)
                val hx = center.x + r * cos(a).toFloat()
                val hy = center.y + r * sin(a).toFloat()
                val dx = sin(a).toFloat()
                val dy = -cos(a).toFloat()
                val h = stroke * 2.7f
                val px = -dy
                val py = dx
                val head = Path().apply {
                    moveTo(hx + dx * h * 0.9f, hy + dy * h * 0.9f)
                    lineTo(hx - dx * h * 0.3f + px * h * 0.8f, hy - dy * h * 0.3f + py * h * 0.8f)
                    lineTo(hx - dx * h * 0.3f - px * h * 0.8f, hy - dy * h * 0.3f - py * h * 0.8f)
                    close()
                }
                drawPath(head, Color.White)
            }
        }
        BasicText(seconds.toString(), style = TextStyle(color = Color.White, fontSize = (glyphSize.value * 0.34f).sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"))
    }
}

/** The main button: a white disc that swaps its glyph with a little pop */
@Composable
private fun PlayButton(s: PlayerSession) {
    val playing = s.status == CSPlayerLoading.IsPlaying
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else if (hovered) 1.07f else 1f, glide(200))
    Tooltip("Play / Pause (Space)") {
        Box(
            Modifier.size(46.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(if (hovered) Color.White else Color(0xFFEDEDF0), CircleShape)
                .fluentClickable(source, true, CircleShape, Role.Button) { s.togglePlay() },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(playing, transitionSpec = {
                (fadeIn(glide(200)) + scaleIn(glide(260), initialScale = 0.5f)) togetherWith (fadeOut(glide(90)) + scaleOut(glide(90), targetScale = 0.5f))
            }) { p -> Icon(if (p) Icons.Pause else Icons.Play, size = 20.dp, tint = Ink) }
        }
    }
}

@Composable
private fun SpeedChip(s: PlayerSession, onClick: (Rect?) -> Unit) {
    val bounds = remember { arrayOfNulls<Rect>(1) }
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(50)
    Tooltip("Playback speed") {
        Box(
            Modifier.onGloballyPositioned { bounds[0] = it.boundsInRoot() }.height(32.dp).clip(shape).background(if (hovered) Color(0x47FFFFFF) else Color(0x2EFFFFFF), shape)
                .fluentClickable(source, true, shape, Role.Button) { onClick(bounds[0]) }.padding(horizontal = 11.dp),
            contentAlignment = Alignment.Center,
        ) { FText("${s.speed.toString().removeSuffix(".0")}×", style = Fluent.type.bodyStrong, color = Color.White, maxLines = 1, softWrap = false) }
    }
}

/** 07:34 / 10:34 and, when there is room, when the video will end */
@Composable
private fun TimeText(s: PlayerSession, showEnds: Boolean) {
    if (s.live) return
    val time = Fluent.type.bodyStrong.copy(fontSize = 14.sp, fontFeatureSettings = "tnum")
    val remainingMs = (s.durationMs - s.positionMs).coerceAtLeast(0L)
    Row(verticalAlignment = Alignment.CenterVertically) {
        FText(fmt(s.positionMs), style = time, color = Color.White, maxLines = 1, softWrap = false)
        FText("  /  ", style = time.copy(fontWeight = FontWeight.Normal), color = Color(0x80FFFFFF), maxLines = 1, softWrap = false)
        val total = if (Appearance.showRemainingTime) "-${fmt(remainingMs)}" else fmt(s.durationMs)
        val shape = RoundedCornerShape(6.dp)
        Tooltip(if (Appearance.showRemainingTime) "Remaining time (click for total)" else "Total duration (click for remaining)") {
            FText(
                total,
                Modifier.clip(shape).fluentClickable(rememberInteraction(), true, shape, Role.Button) { Appearance.showRemainingTime = !Appearance.showRemainingTime; Appearance.save() },
                style = time.copy(fontWeight = FontWeight.Normal), color = Color(0xCCFFFFFF), maxLines = 1, softWrap = false,
            )
        }
        if (showEnds && s.durationMs > 0 && s.status == CSPlayerLoading.IsPlaying) {
            val ends = LocalTime.now().plusSeconds((remainingMs / 1000f / s.speed.coerceAtLeast(0.25f)).toLong()).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
            FText("   ·   ends $ends", style = Fluent.type.caption, color = Color(0x80FFFFFF), maxLines = 1, softWrap = false)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------
// seek bar

/**
 * Seek bar: a thin line that grows under the pointer, with the buffered part, skip segments, a ghost fill up to the pointer, a round knob and a
 * glass bubble with the time (and the name of a skip segment). For a live channel it spans the buffered window.
 */
@Composable
internal fun ModernSeekBar(s: PlayerSession) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    var width by remember { mutableStateOf(1f) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var hoverX by remember { mutableStateOf<Float?>(null) }
    val active = hovered || dragFraction != null
    val trackH by animateDpAsState(if (active) 8.dp else 4.dp, glide(220))
    val knob by animateFloatAsState(if (active) 1f else 0f, spring(0.55f, 520f))
    val accent = c.accent

    Box(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .hoverable(source)
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(s.durationMs) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        val x = e.changes.first().position.x
                        when (e.type) {
                            PointerEventType.Move, PointerEventType.Enter -> {
                                hoverX = x
                                if (e.buttons.isPrimaryPressed) dragFraction = (x / width).coerceIn(0f, 1f)
                            }
                            PointerEventType.Exit -> hoverX = null
                            PointerEventType.Press -> if (e.buttons.isPrimaryPressed) { dragFraction = (x / width).coerceIn(0f, 1f); e.changes.forEach { it.consume() } }
                            PointerEventType.Release -> {
                                dragFraction?.let { f -> s.seekTo((f * s.durationMs).toLong()) }
                                dragFraction = null
                            }
                        }
                    }
                }
            }
            // everything is drawn here: the position changes four times a second and must not recompose anything
            .drawBehind {
                val duration = s.durationMs.coerceAtLeast(1)
                val shown = dragFraction ?: (s.positionMs.toFloat() / duration).coerceIn(0f, 1f)
                val buffered = (s.bufferedMs.toFloat() / duration).coerceIn(0f, 1f)
                val h = trackH.toPx()
                val y = (size.height - h) / 2
                val r = CornerRadius(h / 2, h / 2)
                drawRoundRect(Color(0x38FFFFFF), Offset(0f, y), Size(size.width, h), r)
                drawRoundRect(Color(0x52FFFFFF), Offset(0f, y), Size(size.width * buffered, h), r)
                // skip segments (intro, recap, credits ...)
                for ((from, to) in s.stampRanges()) {
                    val a = (from.toFloat() / duration).coerceIn(0f, 1f) * size.width
                    val b = (to.toFloat() / duration).coerceIn(0f, 1f) * size.width
                    if (b > a) drawRoundRect(Color(0xB3FFD54F), Offset(a, y), Size(b - a, h), r)
                }
                // where a click would land: a faint fill from the played part up to the pointer
                hoverX?.let { hx ->
                    val to = hx.coerceIn(0f, size.width)
                    if (!s.live && to > size.width * shown) drawRoundRect(Color(0x33FFFFFF), Offset(size.width * shown, y), Size(to - size.width * shown, h), r)
                }
                drawRoundRect(accent, Offset(0f, y), Size((size.width * shown).coerceAtLeast(h), h), r)
                if (knob > 0.01f) {
                    val cx = size.width * shown
                    drawCircle(Color(0x40000000), 11.dp.toPx() * knob, Offset(cx, size.height / 2))
                    drawCircle(Color.White, 7.5.dp.toPx() * knob, Offset(cx, size.height / 2))
                }
            },
    ) {
        hoverX?.let { x ->
            val f = (x / width).coerceIn(0f, 1f)
            val at = (f * s.durationMs).toLong()
            val stamp = s.stampLabelAt(at)
            val label = if (s.live) "-" + fmt((s.durationMs - at).coerceAtLeast(0)) else fmt(at)
            val shape = RoundedCornerShape(10.dp)
            Column(
                Modifier.align(Alignment.TopStart)
                    .offset { IntOffset((x - 40.dp.toPx()).coerceIn(0f, (width - 80.dp.toPx()).coerceAtLeast(0f)).toInt(), -52.dp.roundToPx()) }
                    .width(80.dp).background(Color(0xE6121316), shape).border(Dp.Hairline, GlassBorder, shape).padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (stamp != null) FText(stamp, style = Fluent.type.caption, color = Color(0xFFFFD54F), maxLines = 1, softWrap = false)
                FText(label, style = Fluent.type.bodyStrong.copy(fontFeatureSettings = "tnum"), color = Color.White, maxLines = 1, softWrap = false)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------
// over the picture

/** "Skip intro", "Skip recap", "Next episode": a white pill; with a countdown it fills as the time runs out */
@Composable
internal fun SkipPill(label: String, progress: Float?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else if (hovered) 1.04f else 1f, glide(200))
    val fill by animateFloatAsState(progress ?: 0f, tween(if (progress == null) 0 else 1000, easing = LinearEasing))
    val accent = Fluent.colors.accent
    val shape = RoundedCornerShape(50)
    Row(
        modifier.height(46.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(shape).background(Color(0xF5FFFFFF), shape)
            .drawBehind { if (progress != null) drawRect(accent.copy(alpha = 0.32f), Offset.Zero, Size(size.width * fill, size.height)) }
            .fluentClickable(source, true, shape, Role.Button, onClick).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Next, size = 14.dp, tint = Ink)
        FText(label, style = Fluent.type.bodyStrong.copy(fontSize = 15.sp), color = Ink, maxLines = 1, softWrap = false)
    }
}

/** A paused picture says what it is (Ayu's "now playing"): a soft shade at the left and the title, episode and story, once the controls are away */
@Composable
internal fun PauseInfo(s: PlayerSession, show: Boolean) {
    val a by animateFloatAsState(if (show) 1f else 0f, glide(if (show) 700 else 260))
    if (a < 0.01f && !show) return
    s.episodeVersion
    val description = remember(s.title, s.episodeLabel, s.episodeVersion) { s.episodeDescription }
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = a }
            .background(Brush.horizontalGradient(0f to Color(0xCC000000), 0.5f to Color(0x66000000), 0.85f to Color.Transparent)),
    ) {
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 64.dp, bottom = 110.dp).widthIn(max = 560.dp)
                .graphicsLayer { translationY = (1f - a) * 18.dp.toPx() },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FText("NOW PLAYING", style = Fluent.type.caption.copy(letterSpacing = 2.4.sp, fontWeight = FontWeight.SemiBold), color = Fluent.colors.accentText, maxLines = 1)
            FText(s.title, style = Fluent.type.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 2)
            s.episodeLabel?.let { FText(it, style = Fluent.type.bodyStrong.copy(fontSize = 16.sp), color = Color(0xE6FFFFFF), maxLines = 2) }
            description?.let { FText(it, style = Fluent.type.body.copy(fontSize = 14.5.sp, lineHeight = 22.sp), color = Color(0xB3FFFFFF), maxLines = 4) }
            if (s.durationMs > 0 && !s.live) FText("Paused at ${fmt(s.positionMs)}  ·  ${fmt((s.durationMs - s.positionMs).coerceAtLeast(0))} left", style = Fluent.type.caption, color = Color(0x99FFFFFF), maxLines = 1)
        }
    }
}

/** Play again after a pause: a soft disc that grows and fades where the picture is (a pause already has the round mark) */
@Composable
internal fun ResumePulse(s: PlayerSession, modifier: Modifier) {
    var last by remember { mutableStateOf(s.status) }
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(s.status) {
        val before = last
        last = s.status
        if (Appearance.motion != Motion.Off && s.loadingText == null && before == CSPlayerLoading.IsPaused && s.status == CSPlayerLoading.IsPlaying) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(FluentMotion.ms(560), easing = ExpoOut))
        }
    }
    Box(
        modifier.size(88.dp).graphicsLayer { val k = 0.8f + 0.55f * pulse.value; scaleX = k; scaleY = k; alpha = if (pulse.value >= 1f) 0f else (1f - pulse.value) * 0.9f }
            .clip(CircleShape).background(Color(0x73000000)),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Play, size = 34.dp, tint = Color.White) }
}
