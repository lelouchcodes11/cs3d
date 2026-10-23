package com.lagradost.desktop.ui.screens.player

import com.lagradost.desktop.ui.fluent.hairBorder

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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.ContentScale
import com.lagradost.cloudstream3.isMovieType
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
import com.lagradost.desktop.ui.fluent.primaryContactPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
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

/** A soft shadow under text that floats on the picture: it reads on a white scene too, where the shade behind it is faint */
private val TextShade = androidx.compose.ui.graphics.Shadow(Color(0x8C000000), Offset(0f, 1.5f), 8f)

/**
 * The shade behind the controls: [peak] at the screen's edge and nothing at the far end, fading the way light does (smoothstep) so there is
 * no band and no visible edge. A straight three-stop gradient left a grey fog over the picture, worst on a bright scene.
 */
private fun scrim(fromTop: Boolean, peak: Float): Brush {
    val steps = 8
    val stops = Array(steps + 1) { i ->
        val p = i / steps.toFloat()
        val a = peak * (1f - p * p * (3f - 2f * p))
        (if (fromTop) p else 1f - p) to Color(0f, 0f, 0f, a)
    }
    return Brush.verticalGradient(*(if (fromTop) stops else stops.reversedArray()))
}

/**
 * The controls over the picture, flat on a soft shade (no panels), in a fixed order that never moves:
 *  - top left: the back button and what plays (title, episode, quality and source); top right: Video & Audio;
 *  - bottom: the seek bar with the time played at its left end and the time left at its right end, and under it one row: at the left the
 *    transport (previous, back 10 s, play, forward 10 s, next) and the volume, at the right the tools
 *    (Subtitles, Sources | Speed, Episodes, Picture in picture, Settings, Full screen).
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
        // what the middle and the right zone can hold: the names of two tools need about 1120 dp, the speed and episode buttons about 900 dp
        val compact = maxWidth < 760.dp
        val labels = maxWidth >= 1120.dp
        val moreTools = maxWidth >= 900.dp
        // ---- top: back and what plays
        Box(
            Modifier.align(Alignment.TopStart).fillMaxWidth()
                .graphicsLayer { alpha = reveal; translationY = -(1f - reveal) * 16.dp.toPx() }
                .background(scrim(fromTop = true, peak = 0.66f))
                .padding(start = 28.dp, end = 28.dp + com.lagradost.desktop.ui.shell.captionInset, top = 16.dp, bottom = 40.dp),
        ) {
            Row(Modifier.fillMaxWidth().onGloballyPositioned { onTop(it.boundsInRoot()) }, verticalAlignment = Alignment.CenterVertically) {
                GlassButton("Back (Esc)", Modifier.noWindowDrag("playerBack"), size = 42.dp, onClick = { Navigator.back() }) { Icon(Icons.Back, size = 19.dp, tint = Color.White) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    if (s.title.isNotBlank()) FText(s.title, style = Fluent.type.subtitle.copy(fontSize = 21.sp, fontWeight = FontWeight.SemiBold, shadow = TextShade), color = Color.White, maxLines = 1)
                    s.episodeLabel?.let { FText(it, style = Fluent.type.body.copy(shadow = TextShade), color = Color(0xE6FFFFFF), maxLines = 1) }
                    // what plays: the picture size as a small badge, the source beside it
                    // (a track that has no size yet is named by its bitrate line: far too long for a badge)
                    val badge = qualityLabel(s)?.takeIf { it.length <= 8 }
                    val source = s.sourceName?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() }
                    if ((badge != null || source != null) && !loading) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        badge?.let { QualityBadge(it) }
                        source?.let { FText(it, style = Fluent.type.caption.copy(shadow = TextShade), color = Color(0xD9FFFFFF), maxLines = 1) }
                    }
                }
                // video and audio tracks live at the top right (not while the loading screen is up)
                if (!loading && !episodesOpen) {
                    Spacer(Modifier.width(16.dp))
                    ToolButton(Icons.Video, "Video & Audio", labels, "Video and audio tracks (A: next audio)") { openMenu(MenuPage.Tracks, it) }
                }
            }
        }
        // ---- bottom: the seek bar and the row of buttons (not while the loading screen is up: there is nothing to control yet)
        if (!loading) Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .graphicsLayer { alpha = reveal; translationY = (1f - reveal) * 20.dp.toPx() }
                .background(scrim(fromTop = false, peak = 0.9f))
                .padding(start = 28.dp, end = 28.dp, top = 84.dp, bottom = 12.dp)
                .onGloballyPositioned { onBottom(it.boundsInRoot()) },
        ) {
            SeekRow(s)
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                // the transport at the left edge: play sits between the two 10 s jumps, the episode buttons at the ends
                if (s.hasPrev && !compact) PlayerIconButton(Icons.Previous, "Previous episode (Ctrl+Left)", iconSize = 16.dp) { s.prevEpisode() }
                SeekButton(back = true, "Back 10 s (J)") { s.seekBy(-10_000) }
                PlayButton(s)
                SeekButton(back = false, "Forward 10 s (L)") { s.seekBy(10_000) }
                if (s.hasNext && !compact) PlayerIconButton(Icons.Next, "Next episode (Ctrl+Right)", iconSize = 16.dp) { s.nextEpisode() }
                Spacer(Modifier.width(10.dp))
                VolumeControl(s)
                if (s.live) { Spacer(Modifier.width(6.dp)); LivePill(s) }
                // the tools at the right edge
                Box(Modifier.weight(1f))
                ToolButton(Icons.Subtitles, "Subtitles", labels, "Subtitles (S: next)") { openMenu(MenuPage.Subtitles, it) }
                ToolButton(Icons.Link, "Sources", labels, "Sources: pick another server") { openMenu(MenuPage.Sources, it) }
                ToolDivider()
                if (moreTools) {
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

/** The seek bar with the time played at its left end and the time left (or the total, a click switches) at its right end; a live channel has the bar alone */
@Composable
private fun SeekRow(s: PlayerSession) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (!s.live) { PlayedTime(s); Spacer(Modifier.width(14.dp)) }
        ModernSeekBar(s, Modifier.weight(1f))
        if (!s.live) { Spacer(Modifier.width(14.dp)); TimeLeft(s) }
    }
}

private val SeekTimeStyle: TextStyle
    @Composable get() = Fluent.type.bodyStrong.copy(fontSize = 13.sp, fontFeatureSettings = "tnum", shadow = TextShade)

@Composable
private fun PlayedTime(s: PlayerSession) {
    FText(fmt(s.positionMs), Modifier.widthIn(min = 44.dp), style = SeekTimeStyle, color = Color.White, maxLines = 1, softWrap = false)
}

/** The time left; a click shows the total length instead (and back). The tooltip says when the video will end. */
@Composable
private fun TimeLeft(s: PlayerSession) {
    val remainingMs = (s.durationMs - s.positionMs).coerceAtLeast(0L)
    val showLeft = Appearance.showRemainingTime
    val ends = if (s.durationMs > 0 && s.status == CSPlayerLoading.IsPlaying) {
        LocalTime.now().plusSeconds((remainingMs / 1000f / s.speed.coerceAtLeast(0.25f)).toLong()).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    } else null
    val tip = (if (showLeft) "Time left (click for the total length)" else "Total length (click for the time left)") + (ends?.let { "  ·  ends $it" } ?: "")
    val shape = RoundedCornerShape(6.dp)
    Tooltip(tip) {
        FText(
            if (showLeft) "-${fmt(remainingMs)}" else fmt(s.durationMs),
            Modifier.widthIn(min = 44.dp).clip(shape).fluentClickable(rememberInteraction(), true, shape, Role.Button) { Appearance.showRemainingTime = !Appearance.showRemainingTime; Appearance.save() },
            style = SeekTimeStyle, color = Color(0xE6FFFFFF), textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 1, softWrap = false,
        )
    }
}

/** The picture size of what plays ("1080p"): a small outlined badge, so it reads as a fact about the video and not as a caption */
@Composable
private fun QualityBadge(text: String) {
    val shape = RoundedCornerShape(5.dp)
    Box(Modifier.clip(shape).background(Color(0x2EFFFFFF), shape).hairBorder(Color(0x59FFFFFF), shape).padding(horizontal = 6.dp, vertical = 1.dp)) {
        FText(text, style = Fluent.type.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp), color = Color.White, maxLines = 1, softWrap = false)
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
            if (showLabel) FText(label, style = Fluent.type.bodyStrong.copy(fontSize = 13.sp, shadow = TextShade), color = Color.White, maxLines = 1, softWrap = false)
        }
    }
}

/** A thin line between the two named tools and the icon tools */
@Composable
private fun ToolDivider() {
    Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(20.dp).background(Color(0x38FFFFFF)))
}

// ---------------------------------------------------------------------------------------------------------------------------------
// bottom

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
                .let { if (glass) it.hairBorder(GlassBorder, CircleShape) else it }
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
internal fun SeekGlyph(back: Boolean, seconds: Int, glyphSize: Dp = 26.dp) {
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
    val playing = s.showsPause
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else if (hovered) 1.07f else 1f, glide(200))
    Tooltip("Play / Pause (Space)") {
        Box(
            Modifier.size(52.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(if (hovered) Color.White else Color(0xFFEDEDF0), CircleShape)
                .fluentClickable(source, true, CircleShape, Role.Button) { s.togglePlay() },
            contentAlignment = Alignment.Center,
        ) {
            com.lagradost.desktop.ui.fluent.PlayPauseMorph(playing, 22.dp, Ink)
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

// ---------------------------------------------------------------------------------------------------------------------------------
// seek bar

/**
 * Seek bar: a thin line that grows under the pointer, with the buffered part, skip segments, a ghost fill up to the pointer, a round knob and a
 * glass bubble with the time (and the name of a skip segment). For a live channel it spans the buffered window.
 */
@Composable
internal fun ModernSeekBar(s: PlayerSession, modifier: Modifier = Modifier.fillMaxWidth()) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    var width by remember { mutableStateOf(1f) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var hoverX by remember { mutableStateOf<Float?>(null) }
    val devHover = PlayerDev.seekHover
    val active = hovered || dragFraction != null || devHover != null
    val trackH by animateDpAsState(if (active) 7.dp else 4.dp, glide(220))
    val knob by animateFloatAsState(if (active) 1f else 0f, spring(0.55f, 520f))
    val accent = c.accent
    // the played part glides between the four position reports a second instead of stepping (drawn only: nothing recomposes)
    val glide = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(s) {
        snapshotFlow { (s.positionMs.toFloat() / s.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f) }.collectLatest { f ->
            // a seek or a new video jumps; normal playback moves a hair
            if (kotlin.math.abs(f - glide.value) > 0.02f || Appearance.motion == Motion.Off) glide.snapTo(f)
            else glide.animateTo(f, tween(300, easing = LinearEasing))
        }
    }

    Box(
        modifier
            .height(32.dp)
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
                                if (e.primaryContactPressed) dragFraction = (x / width).coerceIn(0f, 1f)
                            }
                            PointerEventType.Exit -> hoverX = null
                            PointerEventType.Press -> if (e.primaryContactPressed) { dragFraction = (x / width).coerceIn(0f, 1f); e.changes.forEach { it.consume() } }
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
                val shown = dragFraction ?: if (s.live) (s.positionMs.toFloat() / duration).coerceIn(0f, 1f) else glide.value
                val buffered = (s.bufferedMs.toFloat() / duration).coerceIn(0f, 1f)
                val h = trackH.toPx()
                val y = (size.height - h) / 2
                val r = CornerRadius(h / 2, h / 2)
                // a faint shadow under the line: on a white scene the see-through track is all but gone without it
                drawRoundRect(Color(0x38000000), Offset(0f, y + 1.dp.toPx()), Size(size.width, h), r)
                drawRoundRect(Color(0x47FFFFFF), Offset(0f, y), Size(size.width, h), r)
                drawRoundRect(Color(0x5CFFFFFF), Offset(0f, y), Size(size.width * buffered, h), r)
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
        (hoverX ?: devHover?.let { it * width })?.let { x ->
            val f = (x / width).coerceIn(0f, 1f)
            val at = (f * s.durationMs).toLong()
            val stamp = s.stampLabelAt(at)
            val label = if (s.live) "-" + fmt((s.durationMs - at).coerceAtLeast(0)) else fmt(at)
            // the still of the scene (a second headless player makes them, see SeekThumbs); only the time while there is none yet
            val thumb = if (s.live) null else s.seekThumbs?.frameAt(f)
            val crop = if (thumb != null) s.seekThumbs?.content() else null
            SeekBubble(thumb, crop, stamp, label, x, width, Modifier.align(Alignment.TopStart))
        }
    }
}

/**
 * What shows above the seek bar while the pointer is on it: the still of the scene with the time in a small pill over its lower edge, or
 * only the time pill. It stands a hand's breadth above the bar, centred on the pointer, and never leaves the bar's width. The black bars of
 * a letterboxed film are cut off the still so that it is the picture, not a frame with a picture inside.
 */
@Composable
private fun SeekBubble(thumb: androidx.compose.ui.graphics.ImageBitmap?, crop: IntArray?, stamp: String?, label: String, x: Float, barWidth: Float, modifier: Modifier) {
    val gap = 12.dp
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.layout { measurable, _ ->
            // measured without the bar's height as a limit (the bar is 28 dp, the bubble is taller and stands above it)
            val p = measurable.measure(androidx.compose.ui.unit.Constraints())
            // no height of its own: a size larger than the bar's would be cut to it and the picture centred in what is left (it sat half its height too high)
            layout(p.width, 0) {
                val left = (x - p.width / 2f).coerceIn(0f, (barWidth - p.width).coerceAtLeast(0f)).toInt()
                p.place(left, -p.height - gap.roundToPx())
            }
        },
    ) {
        if (thumb != null) {
            val sw = crop?.let { it[2] - it[0] } ?: thumb.width
            val sh = crop?.let { it[3] - it[1] } ?: thumb.height
            val aspect = (sw.toFloat() / sh.coerceAtLeast(1)).coerceIn(1.2f, 2.45f)
            Box(Modifier.width(176.dp).aspectRatio(aspect).clip(shape).background(Color(0xFF0B0C0E), shape)) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    drawImage(
                        thumb,
                        srcOffset = androidx.compose.ui.unit.IntOffset(crop?.get(0) ?: 0, crop?.get(1) ?: 0),
                        srcSize = androidx.compose.ui.unit.IntSize(sw, sh),
                        dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                    )
                }
                // a soft shade under the time so that it reads on a bright scene
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(40.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000)))))
                Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (stamp != null) FText(stamp, style = Fluent.type.caption, color = Color(0xFFFFD54F), maxLines = 1, softWrap = false)
                    FText(label, style = Fluent.type.bodyStrong.copy(fontFeatureSettings = "tnum"), color = Color.White, maxLines = 1, softWrap = false)
                }
                // the border drawn over the picture (not around it): no dark frame between the still and the edge
                Box(Modifier.fillMaxSize().hairBorder(GlassBorder, shape))
            }
        } else {
            Column(
                Modifier.background(Color(0xE6121316), shape).hairBorder(GlassBorder, shape).padding(horizontal = 12.dp, vertical = 5.dp),
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

/**
 * A pause that lasts, once the controls are away: the picture settles (it dims, the edges darken) and says what it is, the way a streaming
 * app does: the title's logo (its own artwork, TMDB's when the extension sent none, the name when neither exists), what is paused (episode),
 * one line about the title and its story. The parts come in one after the other and leave together, fast, the moment the video plays again.
 * The video itself is not touched (a native video window can not be blurred or scaled from here): the dim and the vignette are drawn above it.
 */
@Composable
internal fun PauseInfo(s: PlayerSession, show: Boolean) {
    s.episodeVersion
    val response = remember(s.title, s.episodeVersion, s.loadingText == null) { s.titleResponse }
    // the logo is looked up while the video plays, so it is there when the pause comes
    // (the first value is only the start: a state made by produceState is not made again when its keys change, the producer sets it)
    val logo by produceState<String?>(PlayerDev.pauseLogo ?: response?.logoUrl, response?.url, PlayerDev.pauseLogo) {
        value = PlayerDev.pauseLogo ?: response?.logoUrl
        if (value == null && response != null && com.lagradost.desktop.tmdb.Tmdb.enabled) {
            value = runCatching {
                com.lagradost.desktop.tmdb.Tmdb.info(response.name, response.year, response.type?.isMovieType() == true, response.syncData.values)
            }.getOrNull()?.logo
        }
    }
    val headers = response?.posterHeaders
    val p = animateFloatAsState(if (show) 1f else 0f, if (show) tween(FluentMotion.ms(1100), easing = LinearEasing) else tween(FluentMotion.ms(220), easing = LinearEasing))
    val hidden by remember { androidx.compose.runtime.derivedStateOf { p.value < 0.005f } }
    if (hidden && !show) {
        // out of sight, but its picture is fetched now: the logo does not pop in when the pause comes
        logo?.let { com.lagradost.desktop.ui.components.RemoteImage(it, headers, null, Modifier.size(1.dp).graphicsLayer { alpha = 0f }, ContentScale.Fit) }
        return
    }
    val description = remember(s.title, s.episodeLabel, s.episodeVersion, response, PlayerDev.pausePlot) {
        s.episodeDescription ?: PlayerDev.pausePlot ?: response?.plot?.let { com.lagradost.desktop.ui.screens.home.stripHtml(it) }?.trim()?.takeIf { it.isNotBlank() }
    }
    // one line: year, age rating, what it is
    val facts = remember(response) {
        listOfNotNull(
            response?.year?.toString(), response?.contentRating?.takeIf { it.isNotBlank() },
            response?.tags?.take(3)?.joinToString(" / ")?.takeIf { it.isNotBlank() },
        ).joinToString("   ·   ")
    }
    // the parts of the group: 0 = how far the whole pause screen is in; each part starts a little later and takes the rest of the time
    fun part(start: Float): Float = ((p.value - start) / (1f - start)).coerceIn(0f, 1f)
    val eased = remember { androidx.compose.animation.core.CubicBezierEasing(0.16f, 1f, 0.3f, 1f) }
    Box(Modifier.fillMaxSize()) {
        // the dim, the vignette and the shade behind the text
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = (p.value * 1.6f).coerceIn(0f, 1f) }
                .drawBehind {
                    drawRect(Color(0x52000000))
                    drawRect(
                        Brush.radialGradient(
                            0.55f to Color.Transparent, 1f to Color(0xB3000000),
                            center = Offset(size.width / 2f, size.height / 2f), radius = maxOf(size.width, size.height) * 0.75f,
                        ),
                    )
                    drawRect(Brush.horizontalGradient(0f to Color(0xCC000000), 0.55f to Color(0x66000000), 0.9f to Color.Transparent))
                },
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 64.dp, bottom = 118.dp, end = 64.dp).widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // PAUSED, with the mark of it
            Row(
                Modifier.graphicsLayer { val k = eased.transform(part(0f)); alpha = k; translationX = (1f - k) * -14.dp.toPx() },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Pause, size = 13.dp, tint = Color(0xCCFFFFFF))
                FText("PAUSED", style = Fluent.type.caption.copy(letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold), color = Color(0xCCFFFFFF), maxLines = 1)
            }
            // the logo, or the name
            Box(Modifier.graphicsLayer { val k = eased.transform(part(0.08f)); alpha = k; translationY = (1f - k) * 20.dp.toPx(); scaleX = 0.96f + 0.04f * k; scaleY = 0.96f + 0.04f * k; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 1f) }) {
                val mark = logo
                if (mark != null) {
                    com.lagradost.desktop.ui.components.RemoteImage(mark, headers, s.title, Modifier.height(104.dp).widthIn(max = 440.dp), ContentScale.Fit, alignment = Alignment.CenterStart)
                } else {
                    FText(s.title, style = Fluent.type.titleLarge.copy(fontWeight = FontWeight.Black, fontSize = 40.sp, lineHeight = 46.sp, shadow = androidx.compose.ui.graphics.Shadow(Color(0x99000000), Offset(0f, 2f), 16f)), color = Color.White, maxLines = 2)
                }
            }
            Column(
                Modifier.graphicsLayer { val k = eased.transform(part(0.2f)); alpha = k; translationY = (1f - k) * 16.dp.toPx() },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                s.episodeLabel?.let { FText(it, style = Fluent.type.bodyStrong.copy(fontSize = 17.sp), color = Color.White, maxLines = 2) }
                if (facts.isNotBlank()) FText(facts, style = Fluent.type.bodyStrong.copy(fontSize = 13.sp), color = Color(0xCCFFFFFF), maxLines = 1)
            }
            description?.let {
                FText(
                    it, Modifier.graphicsLayer { val k = eased.transform(part(0.34f)); alpha = k; translationY = (1f - k) * 14.dp.toPx() },
                    style = Fluent.type.body.copy(fontSize = 15.sp, lineHeight = 23.sp), color = Color(0xD9FFFFFF), maxLines = 4,
                )
            }
            if (s.durationMs > 0 && !s.live) {
                FText(
                    "${fmt(s.positionMs)} of ${fmt(s.durationMs)}   ·   ${fmt((s.durationMs - s.positionMs).coerceAtLeast(0))} left",
                    Modifier.graphicsLayer { alpha = eased.transform(part(0.5f)) },
                    style = Fluent.type.caption, color = Color(0x99FFFFFF), maxLines = 1,
                )
            }
        }
        // how far into the film: a thin line along the bottom edge, drawn only here (the controls have the seek bar)
        if (s.durationMs > 0 && !s.live) {
            val fraction = (s.positionMs.toFloat() / s.durationMs).coerceIn(0f, 1f)
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).graphicsLayer { alpha = part(0.4f) }
                    .drawBehind {
                        drawRect(Color(0x33FFFFFF))
                        drawRect(Color.White, size = androidx.compose.ui.geometry.Size(size.width * fraction, size.height))
                    },
            )
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
