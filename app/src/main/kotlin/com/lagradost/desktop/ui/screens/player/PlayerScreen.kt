package com.lagradost.desktop.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.ui.player.CSPlayerLoading
import com.lagradost.cloudstream3.ui.player.PlayerGeneratorViewModel
import com.lagradost.cloudstream3.ui.result.SyncViewModel
import com.lagradost.desktop.core.Entry
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.core.Route
import com.lagradost.desktop.runtime.AndroidRuntime
import com.lagradost.desktop.ui.components.RemoteImage
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.shell.noWindowDrag
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FlyoutSurface
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.IconButton
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.MenuEntry
import com.lagradost.desktop.ui.fluent.MenuFlyout
import com.lagradost.desktop.ui.fluent.MenuItem
import com.lagradost.desktop.ui.fluent.MenuSeparator
import com.lagradost.desktop.ui.fluent.ProgressBar
import com.lagradost.desktop.ui.fluent.ProgressRing
import com.lagradost.desktop.ui.fluent.Slider
import com.lagradost.desktop.ui.fluent.Tooltip
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage
import androidx.compose.ui.semantics.Role

private val blankCursor = PointerIcon(
    Toolkit.getDefaultToolkit().createCustomCursor(BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), Point(0, 0), "blank"),
)

private fun fmt(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
fun PlayerScreen(entry: Entry, route: Route.Player) {
    val session = remember(entry.id) {
        PlayerSession(
            vm = entry.vms.get<PlayerGeneratorViewModel>(),
            generator = route.generator,
            index = route.index,
            sync = entry.vms.get<SyncViewModel>(),
            exit = { Navigator.back() },
        )
    }
    DisposableEffect(session) {
        com.lagradost.desktop.platform.WinMedia.startMediaKeys()
        // like on Android the screen stays on for as long as the player is open (also while paused)
        com.lagradost.desktop.platform.WinMedia.keepAwake(true)
        com.lagradost.desktop.platform.WinMedia.handler = { key ->
            when (key) {
                com.lagradost.desktop.platform.WinMedia.Key.PlayPause -> session.togglePlay()
                com.lagradost.desktop.platform.WinMedia.Key.Next -> session.nextEpisode()
                com.lagradost.desktop.platform.WinMedia.Key.Previous -> session.prevEpisode()
                com.lagradost.desktop.platform.WinMedia.Key.Stop -> Navigator.back()
            }
        }
        onDispose {
            com.lagradost.desktop.platform.WinMedia.handler = null
            com.lagradost.desktop.platform.WinMedia.keepAwake(false)
            com.lagradost.desktop.ui.shell.ShellState.windowTitle = "CloudStream"
            session.exitFullscreen()
            if (session.pip) session.setPip(false)
            session.release()
        }
    }
    // the taskbar shows what is playing
    LaunchedEffect(session.title, session.episodeLabel) {
        if (session.title.isNotBlank()) com.lagradost.desktop.ui.shell.ShellState.windowTitle = listOfNotNull(session.title, session.episodeLabel).joinToString(" · ") + " — CloudStream"
    }
    PlayerContent(session)
}

/**
 * Keys of the player, handled for the whole window (see `NativeKeys`): they work whatever has the keyboard focus.
 * A button that disappeared together with the controls used to leave the focus nowhere and the shortcuts dead
 * until something was clicked.
 */
object PlayerKeys {
    @Volatile
    var handler: ((KeyEvent) -> Boolean)? = null
}

@Composable
private fun PlayerContent(s: PlayerSession) {
    val c = Fluent.colors
    val focus = remember { FocusRequester() }
    var rootFocused by remember { mutableStateOf(false) }
    var lastActivity by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var menusOpen by remember { mutableStateOf(0) }
    var showEpisodes by remember { mutableStateOf(false) }
    var hoveringControls by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(true) }
    // where the controls are (root pixels), so "the pointer is on them" does not depend on enter/exit events
    var topBounds by remember { mutableStateOf<Rect?>(null) }
    var bottomBounds by remember { mutableStateOf<Rect?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val paused = s.status != CSPlayerLoading.IsPlaying
    val fullscreen = AndroidRuntime.host.isFullscreen()
    val pip = com.lagradost.desktop.platform.WinChrome.pip

    /** The mouse moved or was pressed: the controls show and the 2 s countdown starts again. Keys never call this. */
    fun poke(local: Offset?) {
        lastActivity = System.currentTimeMillis()
        visible = true
        if (local != null) {
            val p = local + origin
            val slack = 8f
            fun Rect?.has() = this != null && p.x >= left - slack && p.x <= right + slack && p.y >= top - slack && p.y <= bottom + slack
            hoveringControls = topBounds.has() || bottomBounds.has()
        }
    }

    // Controls go away 2 s after the last mouse movement, unless the pointer is on them (or a menu / the episode list is open).
    // Keyboard shortcuts show their own small bubble instead (volume, seek, speed ...), never the controls.
    LaunchedEffect(lastActivity, hoveringControls, menusOpen, showEpisodes) {
        if (hoveringControls || menusOpen > 0 || showEpisodes) {
            visible = true
            return@LaunchedEffect
        }
        delay(2000)
        visible = false
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // the window changes size and may lose the keyboard focus when it enters or leaves full screen
    LaunchedEffect(fullscreen, pip) { delay(250); runCatching { focus.requestFocus() } }
    // the keyboard comes back to the player when a dialog (tracks, sources, search) closes
    val dialogCount = com.lagradost.desktop.ui.fluent.Overlays.dialogs.size
    LaunchedEffect(dialogCount) { if (dialogCount == 0) { delay(100); runCatching { focus.requestFocus() } } }
    LaunchedEffect(menusOpen, showEpisodes) { if (menusOpen == 0) runCatching { focus.requestFocus() } }
    // whatever took the focus away (a button that left with the controls, a closed popup), it comes back
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            if (!rootFocused && menusOpen == 0 && com.lagradost.desktop.ui.fluent.Overlays.dialogs.isEmpty()) runCatching { focus.requestFocus() }
        }
    }

    fun toggleFullscreen() = AndroidRuntime.host.setFullscreen(!AndroidRuntime.host.isFullscreen())

    fun onKey(e: KeyEvent): Boolean {
        if (e.type != KeyEventType.KeyDown) return false
        // a dialog (tracks, sources, search) or an open menu has the keys while it is open
        if (com.lagradost.desktop.ui.fluent.Overlays.dialogs.isNotEmpty() || menusOpen > 0) return false
        val big = e.isShiftPressed
        when (e.key) {
            Key.Spacebar, Key.K -> {
                s.showHud(if (s.status == CSPlayerLoading.IsPlaying) Icons.Pause else Icons.Play, if (s.status == CSPlayerLoading.IsPlaying) "Paused" else "Playing")
                s.togglePlay()
            }
            Key.DirectionLeft -> if (e.isCtrlPressed) s.prevEpisode() else s.seekBy(if (big) -30_000 else -5_000)
            Key.DirectionRight -> if (e.isCtrlPressed) s.nextEpisode() else s.seekBy(if (big) 30_000 else 5_000)
            Key.J -> s.seekBy(-10_000)
            Key.L -> s.seekBy(10_000)
            Key.DirectionUp -> s.stepVolume(1)
            Key.DirectionDown -> s.stepVolume(-1)
            Key.M -> s.toggleMute()
            Key.F, Key.F11 -> toggleFullscreen()
            Key.I -> s.togglePip()
            Key.Escape -> if (pip) s.setPip(false) else if (fullscreen) toggleFullscreen() else if (showEpisodes) showEpisodes = false else Navigator.back()
            Key.N -> s.nextEpisode()
            Key.P -> s.prevEpisode()
            Key.S -> s.cycleSubtitle()
            Key.A -> s.cycleAudio()
            Key.Z -> s.cycleResize()
            Key.E -> showEpisodes = !showEpisodes
            Key.Comma -> s.changeSpeed((s.speed - 0.25f).coerceAtLeast(0.25f))
            Key.Period -> s.changeSpeed((s.speed + 0.25f).coerceAtMost(3f))
            Key.MoveHome -> s.seekTo(0)
            Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine, Key.Zero -> {
                val digit = when (e.key) {
                    Key.Zero -> 0; Key.One -> 1; Key.Two -> 2; Key.Three -> 3; Key.Four -> 4
                    Key.Five -> 5; Key.Six -> 6; Key.Seven -> 7; Key.Eight -> 8; else -> 9
                }
                if (s.durationMs > 0) s.seekTo(s.durationMs * digit / 10)
            }
            else -> return false
        }
        return true
    }

    // the window hands the keys to the newest version of this function
    val latestKey = rememberUpdatedState<(KeyEvent) -> Boolean> { onKey(it) }
    DisposableEffect(Unit) {
        PlayerKeys.handler = { e -> latestKey.value(e) }
        onDispose { PlayerKeys.handler = null }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onGloballyPositioned { origin = it.positionInRoot() }
            .focusRequester(focus)
            .onFocusChanged { rootFocused = it.hasFocus }
            .focusable()
            .onPreviewKeyEvent(::onKey)
            .let { if (!visible && !paused) it.pointerHoverIcon(blankCursor) else it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        val position = e.changes.firstOrNull()?.position
                        when (e.type) {
                            PointerEventType.Move -> poke(position)
                            PointerEventType.Press -> { poke(position); runCatching { focus.requestFocus() } }
                            // the pointer left the window: nothing is hovered any more
                            PointerEventType.Exit -> hoveringControls = false
                            PointerEventType.Scroll -> {
                                val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                if (dy != 0f && !showEpisodes) s.stepVolume(if (dy < 0) 1 else -1)
                            }
                        }
                    }
                }
            },
    ) {
        VideoSurface(s, Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onTap = { s.togglePlay() }, onDoubleTap = { if (pip) s.setPip(false) else toggleFullscreen() })
        })

        if (s.loadingText != null && s.failure == null) LoadingOverlay(s) else if (s.failure != null) FailureOverlay(s)
        // playing, but no picture moves: waiting for data (shown after 0.3 s so that short stalls do not flicker)
        BufferingRing(s.loadingText == null && s.failure == null && s.status == CSPlayerLoading.IsBuffering, Modifier.align(Alignment.Center))
        // a paused video says so, also when the controls are hidden
        if (s.loadingText == null && s.failure == null && s.status == CSPlayerLoading.IsPaused && !pip) PausedBadge(s, Modifier.align(Alignment.Center))

        // skip intro / outro / next episode
        val stamp = s.activeStamp
        AnimatedVisibility(stamp != null && !pip, Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = if (visible) 132.dp else 40.dp), enter = fadeIn(tween(167)), exit = fadeOut(tween(100))) {
            if (stamp != null) Button(stamp.uiText.asStringNull(com.lagradost.desktop.DesktopBootstrap.activity) ?: "Skip", { s.skipStamp() }, kind = ButtonKind.Accent, icon = Icons.Next, height = 40.dp)
        }
        val remaining = s.durationMs - s.positionMs
        if (!pip && s.hasNext && s.durationMs > 0 && remaining in 1..25_000 && stamp == null) {
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = if (visible) 132.dp else 40.dp)) {
                Button("Next episode in ${remaining / 1000 + 1}s", { s.nextEpisode() }, kind = ButtonKind.Accent, icon = Icons.Next, height = 40.dp)
            }
        }

        if (pip) {
            PipControls(s, visible)
        } else AnimatedVisibility(visible, enter = fadeIn(tween(167)), exit = fadeOut(tween(300))) {
            Box(Modifier.fillMaxSize()) {
                // top bar
                Box(
                    Modifier.align(Alignment.TopStart).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                        .padding(start = 12.dp, end = 16.dp + com.lagradost.desktop.ui.shell.captionInset, top = 12.dp, bottom = 40.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().onGloballyPositioned { topBounds = it.boundsInRoot() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(Icons.Back, { Navigator.back() }, Modifier.noWindowDrag("playerBack"), tooltip = "Back (Esc)", size = 40.dp, iconSize = 18.dp, tint = Color.White)
                        Box(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            FText(s.title, style = Fluent.type.subtitle, color = Color.White, maxLines = 1)
                            s.episodeLabel?.let { FText(it, color = Color(0xCCFFFFFF), maxLines = 1) }
                            // the source that plays, under the title
                            s.sourceName?.let { name ->
                                FText(listOfNotNull(name, s.resolution, if (s.loadingMore) "more sources loading…" else null).joinToString("  ·  "), style = Fluent.type.caption, color = Color(0x99FFFFFF), maxLines = 1)
                            }
                        }
                    }
                }
                // bottom bar
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                        .padding(horizontal = 20.dp).padding(top = 40.dp, bottom = 12.dp),
                ) {
                    Column(Modifier.fillMaxWidth().onGloballyPositioned { bottomBounds = it.boundsInRoot() }) {
                        SeekBar(s)
                        Box(Modifier.height(4.dp))
                        ControlRow(s, fullscreen, { menusOpen += it }, { showEpisodes = !showEpisodes }, ::toggleFullscreen)
                    }
                }
            }
        }

        HudOverlay(s, Modifier.align(Alignment.TopCenter).padding(top = if (pip) 36.dp else 56.dp))

        AnimatedVisibility(showEpisodes && !pip, Modifier.align(Alignment.CenterEnd), enter = fadeIn(tween(167)), exit = fadeOut(tween(120))) {
            EpisodesPanel(s, onClose = { showEpisodes = false })
        }
    }
}

/** Picture in picture: a strip at the top that drags the small window (and goes back / closes), play and progress on hover */
@Composable
private fun PipControls(s: PlayerSession, visible: Boolean) {
    val strip = com.lagradost.desktop.platform.WinChrome.PIP_STRIP_DP.dp
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth().height(strip).background(Color(0xB3000000)).padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FText(listOfNotNull(s.title, s.episodeLabel).joinToString(" · "), Modifier.weight(1f), style = Fluent.type.caption, color = Color(0xE6FFFFFF), maxLines = 1)
            IconButton(Icons.BackToWindow, { s.setPip(false) }, Modifier.noWindowDrag("pipBack"), tooltip = "Back to the app (Esc)", size = strip, iconSize = 12.dp, tint = Color.White)
            IconButton(Icons.Close, { Navigator.back() }, Modifier.noWindowDrag("pipClose"), tooltip = "Close", size = strip, iconSize = 12.dp, tint = Color.White)
        }
        AnimatedVisibility(visible, Modifier.fillMaxSize(), enter = fadeIn(tween(120)), exit = fadeOut(tween(250))) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.align(Alignment.Center).size(52.dp).background(Color(0x99000000), CircleShape), contentAlignment = Alignment.Center) {
                    IconButton(if (s.status == CSPlayerLoading.IsPlaying) Icons.Pause else Icons.Play, { s.togglePlay() }, size = 52.dp, iconSize = 22.dp, tint = Color.White)
                }
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    val duration = s.durationMs.coerceAtLeast(1)
                    ProgressBar((s.positionMs.toFloat() / duration).coerceIn(0f, 1f), Modifier.fillMaxWidth(), height = 3.dp)
                    FText("${fmt(s.positionMs)} / ${fmt(s.durationMs)}", style = Fluent.type.caption, color = Color(0xCCFFFFFF), maxLines = 1)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------

@Composable
private fun VideoSurface(s: PlayerSession, modifier: Modifier) {
    val surface = s.surface
    Box(
        modifier
            .onSizeChanged { surface.setRenderSize(it.width, it.height) }
            .drawBehind {
                surface.frameVersion.intValue
                val image = surface.frame ?: return@drawBehind
                drawIntoCanvas { c -> c.nativeCanvas.drawImageRect(image, org.jetbrains.skia.Rect.makeWH(size.width, size.height)) }
            },
    )
}

@Composable
private fun LoadingOverlay(s: PlayerSession) {
    Box(Modifier.fillMaxSize().background(Color(0xE6101010)).pointerInput(Unit) { detectTapGestures { } }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ProgressRing(size = 56.dp, color = Color.White)
            FText(s.title, style = Fluent.type.subtitle, color = Color.White, maxLines = 2)
            s.episodeLabel?.let { FText(it, color = Color(0xCCFFFFFF)) }
            FText(s.loadingText ?: "", color = Color(0xCCFFFFFF), maxLines = 2)
            // which source is being started, and how far down the list it is
            if (s.startingSource) {
                s.sourceName?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { FText(it, color = Color(0xB3FFFFFF), maxLines = 1) }
                s.sourcePosition?.let { FText(it, color = Color(0x99FFFFFF), style = Fluent.type.caption) }
                // after a few seconds: how long the source has been given already
                var waited by remember(s.sourceStartedAt) { mutableStateOf(0) }
                LaunchedEffect(s.sourceStartedAt) {
                    while (true) {
                        waited = ((System.currentTimeMillis() - s.sourceStartedAt) / 1000).toInt()
                        delay(1000)
                    }
                }
                if (waited >= 6) FText("Waiting for the source… ${waited} s", color = Color(0x99FFFFFF), style = Fluent.type.caption)
            }
            if (s.linksFound > 0) FText("${s.linksFound} source${if (s.linksFound == 1) "" else "s"} found" + if (s.loadingMore) " · still looking" else "", color = Color(0x99FFFFFF), style = Fluent.type.caption)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    // a source is starting: it can be skipped instead of waiting for it to give up
                    s.startingSource -> if (s.hasNextMirror) Button("Try next source", { s.nextMirror() }, kind = ButtonKind.Accent, icon = Icons.Next)
                    s.waitingForMoreSources -> Button("Stop looking", { s.skipLoading() }, icon = Icons.Refresh)
                    s.canSkipLoading -> Button("Play now (${s.linksFound})", { s.skipLoading() }, kind = ButtonKind.Accent, icon = Icons.Play)
                }
                Button("Cancel", { Navigator.back() })
            }
        }
    }
}

@Composable
private fun BufferingRing(on: Boolean, modifier: Modifier) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(on) {
        if (on) { delay(300); show = true } else show = false
    }
    if (show) Box(modifier) { ProgressRing(size = 48.dp, color = Color.White) }
}

/** A round "paused" mark in the middle of the picture; a click resumes */
@Composable
private fun PausedBadge(s: PlayerSession, modifier: Modifier) {
    Box(
        modifier.size(76.dp).clip(CircleShape).background(Color(0x99000000)).pointerInput(Unit) { detectTapGestures { s.play() } },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Play, size = 34.dp, tint = Color.White)
    }
}

@Composable
private fun FailureOverlay(s: PlayerSession) {
    Box(Modifier.fillMaxSize().background(Color(0xE6101010)).pointerInput(Unit) { detectTapGestures { } }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.widthIn(max = 520.dp).padding(24.dp)) {
            Icon(Icons.Warning, size = 40.dp, tint = Fluent.colors.caution)
            FText("This video could not be played", style = Fluent.type.subtitle, color = Color.White)
            FText(s.failure ?: "", color = Color(0xCCFFFFFF), maxLines = 5)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.hasNextMirror) Button("Try next source", { s.nextMirror() }, kind = ButtonKind.Accent, icon = Icons.Next)
                Button("Reload sources", { s.reloadSources() }, icon = Icons.Refresh)
                Button("Back", { Navigator.back() })
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------

@Composable
private fun SeekBar(s: PlayerSession) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    var width by remember { mutableStateOf(1f) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var hoverX by remember { mutableStateOf<Float?>(null) }
    val duration = s.durationMs.coerceAtLeast(1)
    val shown = dragFraction ?: (s.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val buffered = (s.bufferedMs.toFloat() / duration).coerceIn(0f, 1f)
    val stamps = s.stampFractions(duration)

    Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
        FText(fmt((shown * duration).toLong()), style = Fluent.type.caption, color = Color.White, modifier = Modifier.width(52.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .hoverable(source)
                .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
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
                                PointerEventType.Exit -> { hoverX = null }
                                PointerEventType.Press -> if (e.buttons.isPrimaryPressed) { dragFraction = (x / width).coerceIn(0f, 1f); e.changes.forEach { it.consume() } }
                                PointerEventType.Release -> {
                                    dragFraction?.let { f -> s.seekTo((f * s.durationMs).toLong()) }
                                    dragFraction = null
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val trackH = if (hovered || dragFraction != null) 6.dp else 4.dp
            Box(Modifier.fillMaxWidth().height(trackH).background(Color(0x55FFFFFF), RoundedCornerShape(3.dp)))
            Box(Modifier.fillMaxWidth(buffered).height(trackH).background(Color(0x66FFFFFF), RoundedCornerShape(3.dp)))
            Box(Modifier.fillMaxWidth(shown).height(trackH).background(c.accent, RoundedCornerShape(3.dp)))
            // skip markers (intro / outro)
            for ((from, to) in stamps) {
                Box(
                    Modifier.fillMaxWidth().height(trackH).drawBehind {
                        drawRect(Color(0xCCFFD54F), Offset(size.width * from, 0f), Size(size.width * (to - from), size.height))
                    },
                )
            }
            if (hovered || dragFraction != null) {
                Box(
                    Modifier.fillMaxWidth().height(16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        Modifier.offset2(shown * width - 8f).size(16.dp).background(Color.White, CircleShape)
                            .border(3.dp, c.accent, CircleShape),
                    )
                }
            }
            hoverX?.let { x ->
                val f = (x / width).coerceIn(0f, 1f)
                Box(Modifier.align(Alignment.TopStart).offset2((x - 28f).coerceIn(0f, width - 56f)).offsetY(-34f)) {
                    Box(Modifier.background(c.flyout, RoundedCornerShape(4.dp)).border(androidx.compose.ui.unit.Dp.Hairline, c.strokeStrong.copy(alpha = 0.4f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        FText(fmt((f * s.durationMs).toLong()), style = Fluent.type.caption, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
        FText("-" + fmt((s.durationMs - (shown * duration).toLong()).coerceAtLeast(0)), style = Fluent.type.caption, color = Color.White, modifier = Modifier.width(60.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

private fun Modifier.offset2(xPx: Float): Modifier = this.then(Modifier.offsetPx(xPx, 0f))
private fun Modifier.offsetY(yPx: Float): Modifier = this.then(Modifier.offsetPx(0f, yPx))
private fun Modifier.offsetPx(x: Float, y: Float): Modifier =
    this.offset { androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt()) }

/** Skip stamps as fractions of the duration */
private fun PlayerSession.stampFractions(duration: Long): List<Pair<Float, Float>> =
    stampRanges().map { (a, b) -> (a.toFloat() / duration).coerceIn(0f, 1f) to (b.toFloat() / duration).coerceIn(0f, 1f) }

// ------------------------------------------------------------------------------------------------

@Composable
private fun ControlRow(s: PlayerSession, fullscreen: Boolean, menuDelta: (Int) -> Unit, toggleEpisodes: () -> Unit, toggleFullscreen: () -> Unit) {
    val white = Color.White
    // as on Android the tracks button only exists when there is something to choose: more than one video or audio track
    val hasTrackChoice = remember(s.listsVersion, s.status) { s.videoTracks().size > 1 || s.audioTracks().size > 1 }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        IconButton(if (s.status == CSPlayerLoading.IsPlaying) Icons.Pause else Icons.Play, { s.togglePlay() }, tooltip = "Play / Pause (Space)", size = 40.dp, iconSize = 20.dp, tint = white)
        if (s.hasPrev) IconButton(Icons.Previous, { s.prevEpisode() }, tooltip = "Previous episode (Ctrl+Left)", size = 36.dp, tint = white)
        IconButton(Icons.Rewind, { s.seekBy(-10_000) }, tooltip = "Back 10 s (J)", size = 36.dp, tint = white)
        IconButton(Icons.FastForward, { s.seekBy(10_000) }, tooltip = "Forward 10 s (L)", size = 36.dp, tint = white)
        if (s.hasNext) IconButton(Icons.Next, { s.nextEpisode() }, tooltip = "Next episode (Ctrl+Right)", size = 36.dp, tint = white)
        Box(Modifier.width(8.dp))
        IconButton(if (s.muted || s.volume == 0) Icons.Mute else Icons.Volume, { s.toggleMute() }, tooltip = "Mute (M)", size = 36.dp, tint = white)
        Slider(
            value = if (s.muted) 0f else s.volume.toFloat(), onValueChange = { s.changeVolume(it.toInt()) },
            valueRange = 0f..200f, modifier = Modifier.width(96.dp),
        )
        FText("${s.volume}%", style = Fluent.type.caption, color = Color(0xCCFFFFFF), modifier = Modifier.width(40.dp).padding(start = 6.dp))
        Box(Modifier.weight(1f))
        if (s.speed != 1f) FText("${s.speed}×", color = white, style = Fluent.type.bodyStrong, modifier = Modifier.padding(end = 8.dp))

        // like Android: one button for sources and subtitles, one for audio and video tracks
        IconButton(Icons.Subtitles, { openSourcesDialog(s) }, tooltip = "Sources and subtitles", size = 36.dp, tint = white)
        if (hasTrackChoice) IconButton(Icons.Audio, { openTracksDialog(s) }, tooltip = "Audio and video tracks", size = 36.dp, tint = white)
        FlyoutButton(Icons.Speed, "Playback speed", menuDelta) { speedEntries(s) }
        FlyoutButton(Icons.Aspect, "Picture size (Z)", menuDelta) { resizeEntries(s) }
        IconButton(Icons.List, toggleEpisodes, tooltip = "Episodes (E)", size = 36.dp, tint = white)
        IconButton(Icons.Pip, { s.togglePip() }, tooltip = "Picture in picture (I)", size = 36.dp, tint = white)
        IconButton(if (fullscreen) Icons.ExitFullscreen else Icons.Fullscreen, toggleFullscreen, tooltip = if (fullscreen) "Exit full screen (F)" else "Full screen (F)", size = 36.dp, tint = white)
    }
}

@Composable
private fun FlyoutButton(glyph: String, tooltip: String, menuDelta: (Int) -> Unit, entries: () -> List<MenuEntry>) {
    var open by remember { mutableStateOf(false) }
    // counted for as long as the menu is open, whatever closes it (an item, a click elsewhere, the controls leaving)
    DisposableEffect(open) {
        val counted = open
        if (counted) menuDelta(1)
        onDispose { if (counted) menuDelta(-1) }
    }
    Box {
        IconButton(glyph, { open = !open }, tooltip = tooltip, size = 36.dp, tint = Color.White)
        if (open) {
            val list = entries()
            MenuFlyout(list, onDismiss = { open = false })
        }
    }
}

private fun speedEntries(s: PlayerSession): List<MenuEntry> =
    listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f).map { v -> MenuItem(if (v == 1f) "Normal" else "${v}×", checked = s.speed == v) { s.changeSpeed(v) } }

private fun resizeEntries(s: PlayerSession): List<MenuEntry> =
    Resize.entries.map { r -> MenuItem(r.label, checked = s.resize == r) { s.changeResize(r) } }

// ------------------------------------------------------------------------------------------------

@Composable
private fun EpisodesPanel(s: PlayerSession, onClose: () -> Unit) {
    val c = Fluent.colors
    val items = s.episodes()
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { items.indexOfFirst { it.current }.takeIf { it >= 0 }?.let { listState.scrollToItem((it - 1).coerceAtLeast(0)) } }
    Column(
        Modifier.fillMaxHeight().width(380.dp).background(Color(0xF2181818)).border(androidx.compose.ui.unit.Dp.Hairline, c.stroke)
            .padding(top = if (com.lagradost.desktop.platform.WinChrome.enabled && !com.lagradost.desktop.platform.WinChrome.fullscreen) 34.dp else 0.dp)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            FText("Episodes", Modifier.weight(1f), style = Fluent.type.subtitle, color = Color.White)
            IconButton(Icons.Close, onClose, tooltip = "Close", tint = Color.White)
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
            itemsIndexed(items) { _, item ->
                val ep = item.episode
                val source = rememberInteraction()
                val hovered by source.collectIsHoveredAsState()
                val shape = RoundedCornerShape(6.dp)
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 2.dp).fillMaxWidth().clip(shape)
                        .background(if (item.current) Color(0x22FFFFFF) else if (hovered) Color(0x14FFFFFF) else Color.Transparent, shape)
                        .fluentClickable(source, true, shape, Role.Button) { s.playEpisode(item) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(112.dp, 63.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x22FFFFFF))) {
                        RemoteImage(ep.poster, null, null, Modifier.fillMaxSize(), ContentScale.Crop)
                        if (item.current) Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) { Icon(Icons.Play, size = 20.dp, tint = Color.White) }
                        if (item.fraction > 0f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()) { ProgressBar(item.fraction, height = 3.dp) }
                    }
                    Column(Modifier.weight(1f)) {
                        FText(buildString {
                            if (ep.season != null) append("S${ep.season} · ")
                            append("Episode ${ep.episode}")
                        }, style = Fluent.type.caption, color = Color(0xCCFFFFFF), maxLines = 1)
                        FText(ep.name ?: "Episode ${ep.episode}", style = Fluent.type.bodyStrong, color = Color.White, maxLines = 2)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------

/** Online subtitle search across the subtitle providers (OpenSubtitles, SubDL, ...) */
internal fun openSubtitleSearch(s: PlayerSession) {
    com.lagradost.desktop.ui.fluent.Overlays.show(
        com.lagradost.desktop.ui.fluent.Overlays.Dialog(title = "Search subtitles", close = "Close", width = 680.dp) { dismiss ->
            val c = Fluent.colors
            var q by remember { mutableStateOf(s.defaultSubtitleQuery) }
            var lang by remember { mutableStateOf(com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.getAutoSelectLanguageTagIETF()) }
            var results by remember { mutableStateOf<List<com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleEntity>>(emptyList()) }
            var busy by remember { mutableStateOf(false) }
            var searched by remember { mutableStateOf(false) }
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val tags = remember { com.lagradost.cloudstream3.utils.SubtitleHelper.languages.map { it.IETF_tag }.distinct() }
            fun search() {
                if (busy) return
                busy = true
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try { results = s.searchSubtitles(q, lang) } finally { busy = false; searched = true }
                }
            }
            LaunchedEffect(Unit) { search() }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.lagradost.desktop.ui.fluent.TextBox(q, { q = it }, Modifier.weight(1f), placeholder = "Title", leadingIcon = Icons.Search, onSubmit = ::search)
                    com.lagradost.desktop.ui.fluent.ComboBox(tags, lang, { t -> com.lagradost.cloudstream3.utils.SubtitleHelper.getNameNextToFlagEmoji(t) ?: t }, { lang = it; search() }, minWidth = 170.dp)
                    Button("Search", ::search, kind = ButtonKind.Accent)
                }
                if (busy) ProgressBar(null, Modifier.fillMaxWidth())
                if (!busy && searched && results.isEmpty()) FText("No subtitles found. Try another language or a shorter title.", color = c.textSecondary)
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    itemsIndexed(results) { _, entity ->
                        val source = rememberInteraction()
                        val hovered by source.collectIsHoveredAsState()
                        val shape = RoundedCornerShape(4.dp)
                        Row(
                            Modifier.fillMaxWidth().clip(shape).background(if (hovered) c.subtleHover else Color.Transparent, shape)
                                .fluentClickable(source, true, shape, Role.Button) { scope.launch(kotlinx.coroutines.Dispatchers.IO) { s.applyOnlineSubtitle(entity) }; dismiss() }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                FText(entity.name, maxLines = 2)
                                FText(listOfNotNull(entity.source, entity.lang, entity.epNumber?.let { "E$it" }, if (entity.isHearingImpaired) "Hearing impaired" else null).joinToString("  ·  "), style = Fluent.type.caption, color = c.textSecondary, maxLines = 1)
                            }
                            Icon(Icons.Download, size = 14.dp, tint = c.textSecondary)
                        }
                    }
                }
            }
        },
    )
}

/** A small pill at the top of the picture that confirms volume, seek, speed and play/pause changes made with the keyboard */
@Composable
private fun HudOverlay(s: PlayerSession, modifier: Modifier) {
    val hud = s.hud
    var last by remember { mutableStateOf<Hud?>(null) }
    if (hud != null) last = hud
    LaunchedEffect(hud) {
        if (hud != null) {
            delay(1000)
            s.clearHud(hud)
        }
    }
    AnimatedVisibility(
        hud != null, modifier,
        enter = fadeIn(tween(100)) + androidx.compose.animation.slideInVertically(androidx.compose.animation.core.spring(0.7f, 800f)) { -it / 2 } +
            androidx.compose.animation.scaleIn(androidx.compose.animation.core.spring(0.7f, 800f), initialScale = 0.9f),
        exit = fadeOut(tween(260)) + androidx.compose.animation.slideOutVertically(tween(260)) { -it / 3 },
    ) {
        val h = last ?: return@AnimatedVisibility
        val shape = RoundedCornerShape(50)
        Row(
            Modifier.background(Color(0xE6202020), shape).border(androidx.compose.ui.unit.Dp.Hairline, Color(0x26FFFFFF), shape).padding(start = 14.dp, end = 16.dp).height(38.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(h.glyph, size = 16.dp, tint = Color.White)
            FText(h.text, style = Fluent.type.bodyStrong, color = Color.White, maxLines = 1, softWrap = false)
            h.fraction?.let { target ->
                val shown by androidx.compose.animation.core.animateFloatAsState(target.coerceIn(0f, 1f), tween(120))
                Box(Modifier.width(64.dp).height(4.dp).background(Color(0x40FFFFFF), RoundedCornerShape(2.dp))) {
                    Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(if (target > 0.5f) Color(0xFFFFB74D) else Color.White, RoundedCornerShape(2.dp)))
                }
            }
        }
    }
}
