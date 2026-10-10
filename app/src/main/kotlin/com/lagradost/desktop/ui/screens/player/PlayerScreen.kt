package com.lagradost.desktop.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.lagradost.desktop.ui.fluent.Appearance
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.core.animateFloat
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
import kotlin.math.roundToInt
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
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.ui.player.CSPlayerLoading
import com.lagradost.cloudstream3.ui.player.PlayerGeneratorViewModel
import com.lagradost.cloudstream3.ui.result.SyncViewModel
import com.lagradost.desktop.core.Entry
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.core.Route
import com.lagradost.desktop.runtime.AndroidRuntime
import com.lagradost.desktop.ui.components.RemoteImage
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.shell.RevealedTitleBar
import com.lagradost.desktop.ui.shell.noWindowDrag
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.accentBrush
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
import com.lagradost.desktop.player.SubtitleLoadState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage
import androidx.compose.ui.semantics.Role

private val blankCursor = PointerIcon(
    Toolkit.getDefaultToolkit().createCustomCursor(BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), Point(0, 0), "blank"),
)

internal fun fmt(ms: Long): String {
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
            syncData = route.syncData,
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

/** Dev: keeps the controls up (/player?hold=1) so that they can be looked at */
object PlayerDev {
    var hold by mutableStateOf(false)

    /** Dev: how many pointer moves made the controls show, and whether they are shown now (a resting pointer must not count up) */
    @Volatile var pokes = 0
    @Volatile var controlsShown = true
    @Volatile var overControls = false
}

@Composable
private fun PlayerContent(s: PlayerSession) {
    val c = Fluent.colors
    val focus = remember { FocusRequester() }
    var rootFocused by remember { mutableStateOf(false) }
    var lastActivity by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // the player menu (PlayerMenu.kt): the page it shows, null when it is closed
    var menuPage by remember { mutableStateOf<MenuPage?>(null) }
    // the menu comes up from the pills at the top, or from the buttons at the bottom
    var menuAnchor by remember { mutableStateOf<Rect?>(null) }
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
    // the native video player (beta) is decided when the page opens (the setting applies to the next video: a core that is already playing
    // has no native window); it is given up for the standard player when it can not start
    val nativeRequested = remember { NativeVideo.available() }
    val native = nativeRequested && !NativeVideo.broken
    // leaving a native video page: the video core goes first, the page is taken apart after it (see PlayerSession.closeThen)
    DisposableEffect(s, native) {
        // Back can come from any thread (the end of a playlist); the player is released on the UI thread
        val hook: (() -> Unit) -> Boolean = { done ->
            if (java.awt.EventQueue.isDispatchThread()) s.closeThen(done)
            else { java.awt.EventQueue.invokeLater { if (!s.closeThen(done)) done() }; true }
        }
        if (native) com.lagradost.desktop.core.PlayerExit.hook = hook
        onDispose { if (com.lagradost.desktop.core.PlayerExit.hook === hook) com.lagradost.desktop.core.PlayerExit.hook = null }
    }

    /** The mouse moved or was pressed: the controls show and the 2 s countdown starts again. Keys never call this. */
    fun poke(local: Offset?) {
        lastActivity = System.currentTimeMillis()
        visible = true
        if (local != null) {
            val p = if (native) local else local + origin
            val slack = 8f
            fun Rect?.has() = this != null && p.x >= left - slack && p.x <= right + slack && p.y >= top - slack && p.y <= bottom + slack
            hoveringControls = topBounds.has() || bottomBounds.has()
        }
    }

    // Controls go away 2 s after the last mouse movement, unless the pointer is on them (or a menu / the episode list is open).
    // Keyboard shortcuts show their own small bubble instead (volume, seek, speed ...), never the controls.
    LaunchedEffect(lastActivity, hoveringControls, menuPage, showEpisodes, PlayerDev.hold) {
        if (hoveringControls || menuPage != null || showEpisodes || PlayerDev.hold) {
            visible = true
            return@LaunchedEffect
        }
        delay(2000)
        visible = false
    }
    LaunchedEffect(visible, hoveringControls) { PlayerDev.controlsShown = visible; PlayerDev.overControls = hoveringControls }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // the window changes size and may lose the keyboard focus when it enters or leaves full screen
    LaunchedEffect(fullscreen, pip) { delay(250); runCatching { focus.requestFocus() } }
    // the keyboard comes back to the player when a dialog (tracks, sources, search) closes
    val dialogCount = com.lagradost.desktop.ui.fluent.Overlays.dialogs.size
    LaunchedEffect(dialogCount) { if (dialogCount == 0) { delay(100); runCatching { focus.requestFocus() } } }
    LaunchedEffect(menuPage == null, showEpisodes) { runCatching { focus.requestFocus() } }
    // whatever took the focus away (a button that left with the controls, a closed popup), it comes back
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            if (!rootFocused && com.lagradost.desktop.ui.fluent.Overlays.dialogs.isEmpty()) runCatching { focus.requestFocus() }
        }
    }

    fun toggleFullscreen() = AndroidRuntime.host.setFullscreen(!AndroidRuntime.host.isFullscreen())

    fun onKey(e: KeyEvent): Boolean {
        if (e.type != KeyEventType.KeyDown) return false
        // a dialog (subtitle style, search) has the keys while it is open
        if (com.lagradost.desktop.ui.fluent.Overlays.dialogs.isNotEmpty()) return false
        // the menu: Esc closes it (the other shortcuts keep working while it is open)
        if (menuPage != null && e.key == Key.Escape) { menuPage = null; return true }
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
            Key.F1 -> Shortcuts.show()
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

    // the native video player (beta): mpv's GPU window below, the controls in a transparent window above it
    // a click pauses / plays at once (detectTapGestures held every single click ~300 ms to rule out a double click, which felt like a lag);
    // the second click of a double click puts the play state back and switches fullscreen, as in a browser's video player
    val tapGestures = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            var lastClick = 0L
            var playingBefore = false
            var downAt: androidx.compose.ui.geometry.Offset? = null
            while (true) {
                val e = awaitPointerEvent()
                val change = e.changes.firstOrNull() ?: continue
                when {
                    e.type == PointerEventType.Press -> downAt = if (e.buttons.isPrimaryPressed) change.position else null
                    e.type == PointerEventType.Release -> {
                        val start = downAt ?: continue
                        downAt = null
                        if (change.isConsumed || (change.position - start).getDistance() > viewConfiguration.touchSlop) continue
                        // the small window was dragged: the pointer is where it was pressed in the window's own terms, which looks like a click
                        if (com.lagradost.desktop.platform.WinChrome.pip && com.lagradost.desktop.platform.WinChrome.pipMoved) continue
                        val now = System.currentTimeMillis()
                        if (now - lastClick < 350) {
                            lastClick = 0L
                            // back to how it was before the first click (the state the first click set may not be reported yet)
                            if (playingBefore) s.play() else s.pause()
                            if (com.lagradost.desktop.platform.WinChrome.pip) s.setPip(false) else toggleFullscreen()
                        } else {
                            lastClick = now
                            playingBefore = s.status == CSPlayerLoading.IsPlaying
                            s.togglePlay()
                        }
                    }
                }
            }
        }
    }
    // The pointer: moving it shows the controls. [ownsHover]: this window's events say whether the pointer is on the controls. With the native video
    // player two windows see the mouse (the app's window under the video and the transparent controls window above it, whose coordinates differ):
    // only the controls window decides, else the answer flipped between the two (the controls hid under a pointer that rested on them and came
    // back at the next twitch).
    fun pointerWatch(ownsHover: Boolean): Modifier = Modifier
            .pointerInput(ownsHover) {
                awaitPointerEventScope {
                    // where the pointer last made the controls show: Compose repeats the last position as a Move when something under a resting pointer
                    // changes (the cursor going blank, controls sliding away), and that used to show the controls again and again
                    var shownAt: Offset? = null
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        val position = e.changes.firstOrNull()?.position
                        when (e.type) {
                            PointerEventType.Move -> {
                                val before = shownAt
                                if (position != null && (before == null || (position - before).getDistance() >= 2f)) {
                                    shownAt = position
                                    PlayerDev.pokes++
                                    poke(if (ownsHover) position else null)
                                }
                            }
                            PointerEventType.Press -> { shownAt = position; if (ownsHover && com.lagradost.desktop.platform.WinChrome.pip) com.lagradost.desktop.platform.WinChrome.pipDragBegin(); poke(if (ownsHover) position else null); runCatching { focus.requestFocus() } }
                            // the pointer left the window: nothing is hovered any more
                            PointerEventType.Exit -> if (ownsHover) { hoveringControls = false; shownAt = null }
                            PointerEventType.Scroll -> {
                                val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                // the wheel scrolls an open menu, the episode list or a dialog, never the volume under it
                                if (dy != 0f && !showEpisodes && menuPage == null && com.lagradost.desktop.ui.fluent.Overlays.dialogs.isEmpty()) s.stepVolume(if (dy < 0) 1 else -1)
                            }
                        }
                    }
                }
            }
    // everything over the picture
    val overlay: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = overlay@{
        // the page is going: a dark screen with a ring, nothing else touches the released player
        if (s.closing) {
            Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { } }, contentAlignment = Alignment.Center) { ProgressRing(size = 40.dp, color = Color.White) }
            return@overlay
        }
        if (s.loadingText != null && s.failure == null) LoadingOverlay(s) else if (s.failure != null) FailureOverlay(s)
        // playing, but no picture moves: waiting for data (shown after 0.3 s so that short stalls do not flicker)
        BufferingRing(s.loadingText == null && s.failure == null && s.status == CSPlayerLoading.IsBuffering, Modifier.align(Alignment.Center))
        // a paused video says so, also when the controls are hidden
        if (s.loadingText == null && s.failure == null && s.status == CSPlayerLoading.IsPaused && !pip) PausedBadge(s, Modifier.align(Alignment.Center))

        // a pause that lasts, with nobody on the controls: the picture says what it is
        var pauseInfo by remember { mutableStateOf(false) }
        LaunchedEffect(s.status, visible, s.loadingText, s.failure, pip) {
            if (s.status == CSPlayerLoading.IsPaused && !visible && s.loadingText == null && s.failure == null && !pip) { delay(1100); pauseInfo = true } else pauseInfo = false
        }
        if (!pip) PauseInfo(s, pauseInfo)
        if (!pip) ResumePulse(s, Modifier.align(Alignment.Center))

        // skip intro / recap / credits, next episode
        SkipLayer(s, visible, pip)

        val modernChrome = Appearance.playerStyle == com.lagradost.desktop.ui.fluent.PlayerStyle.Modern
        if (pip) {
            PipControls(s, visible, tapGestures)
        } else if (modernChrome) {
            ModernChrome(
                s, visible, fullscreen, { p, anchor -> menuAnchor = anchor; menuPage = if (menuPage == p) null else p },
                { showEpisodes = !showEpisodes }, ::toggleFullscreen, { topBounds = it }, { bottomBounds = it }, showEpisodes, loading = s.loadingText != null || s.failure != null,
            )
        } else AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(350))) {
            PlayerChrome(s, fullscreen, { menuAnchor = null; menuPage = if (menuPage == it) null else it }, { showEpisodes = !showEpisodes }, ::toggleFullscreen, { topBounds = it }, { bottomBounds = it })
        }

        // the menu, above the buttons at the bottom right (or below the pills at the top); a click anywhere else closes it (and does not pause the video)
        val page = menuPage
        if (page != null && !pip) androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { menuPage = null } })
            // it opens above the button that was clicked (the settings gear, Sources ...), not in a corner
            val density = androidx.compose.ui.platform.LocalDensity.current
            val anchor = menuAnchor
            val anchorX = anchor?.let { with(density) { it.center.x.toDp() } }
            // a button in the upper half (Video & Audio at the top right): the menu hangs below it
            val below = anchor != null && with(density) { anchor.center.y.toDp() } < maxHeight / 2
            val belowTop = anchor?.let { with(density) { it.bottom.toDp() } + 10.dp } ?: 80.dp
            val menuStart = anchorX?.let { (it - 180.dp).coerceIn(16.dp, (maxWidth - 360.dp - 16.dp).coerceAtLeast(16.dp)) }
            PlayerMenu(
                s, page, { menuPage = it },
                maxHeight = (if (below) maxHeight - belowTop - 24.dp else maxHeight - 200.dp).coerceAtLeast(200.dp),
                when {
                    menuStart != null && below -> Modifier.align(Alignment.TopStart).padding(start = menuStart, top = belowTop)
                    menuStart != null -> Modifier.align(Alignment.BottomStart).padding(start = menuStart, bottom = 108.dp)
                    else -> Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = if (modernChrome) 108.dp else 80.dp)
                },
                fromTop = below,
                originX = if (menuStart != null && anchorX != null) ((anchorX - menuStart) / 360.dp).coerceIn(0f, 1f) else 1f,
            )
        }
        LaunchedEffect(pip) { if (pip) menuPage = null }

        HudOverlay(s, Modifier.align(Alignment.TopCenter).padding(top = if (pip) 14.dp else 56.dp))
        // subtitles: fetching, on, or given up; at the top right, lower while the controls are shown
        if (!pip) {
            val pillTop by androidx.compose.animation.core.animateDpAsState(if (visible) (if (modernChrome) 112.dp else 96.dp) else 24.dp, glide(300))
            SubtitlePill(s, Modifier.align(Alignment.TopEnd).padding(top = pillTop, end = 28.dp + com.lagradost.desktop.ui.shell.captionInset))
            TorrentPill(s, Modifier.align(Alignment.TopStart).padding(top = pillTop, start = 28.dp))
        }

        val episodesOn = showEpisodes && !pip
        val panelReveal by androidx.compose.animation.core.animateFloatAsState(if (episodesOn) 1f else 0f, if (episodesOn) glide(420) else tween(180))
        val panelThere by remember { androidx.compose.runtime.derivedStateOf { panelReveal > 0.01f } }
        if (panelThere) Box(Modifier.align(Alignment.CenterEnd).graphicsLayer { alpha = panelReveal; translationX = (1f - panelReveal) * 72.dp.toPx() }) {
            EpisodesPanel(s, onClose = { showEpisodes = false })
        }
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
            // always there, only its icon changes: adding and removing the modifier changed the root's pointer chain at the moment of a press, which cancelled the press (no click, no drag)
            .pointerHoverIcon(if (!visible && !paused) blankCursor else PointerIcon.Default)
            .then(pointerWatch(!native)),
    ) {
        if (native) NativeVideoHost(Modifier.fillMaxSize()) else VideoSurface(s, Modifier.fillMaxSize().then(tapGestures))
        if (native) {
            val dialogsOpen = com.lagradost.desktop.ui.fluent.Overlays.dialogs.isNotEmpty()
            NativeOverlayWindow(focusable = dialogsOpen) {
                Box(Modifier.fillMaxSize().then(pointerWatch(true))) {
                    // the picture's layer under the controls (taps play and pause, a double tap is full screen); nearly invisible, not transparent:
                    // a fully transparent pixel of a window lets the pointer through to the video below
                    Box(
                        Modifier.fillMaxSize().background(Color(0x01000000))
                            .pointerHoverIcon(if (!visible && !paused) blankCursor else PointerIcon.Default)
                            .then(tapGestures),
                    )
                    overlay()
                    // dialogs and messages are drawn here too: the main window's layer is behind the video
                    com.lagradost.desktop.ui.FluentRequestDialogs()
                    com.lagradost.desktop.ui.LegacyOverlays()
                    com.lagradost.desktop.ui.fluent.DialogLayer()
                    RevealedTitleBar()
                }
            }
            // the keys come back to the player when the dialog closes
            // (the dialog's window had the keyboard: the main window gets it back, then the player's own focus)
            LaunchedEffect(dialogsOpen) { if (!dialogsOpen && !s.isReleased) { delay(120); if (!s.isReleased) { runCatching { com.lagradost.desktop.ui.DesktopUiHost.window?.requestFocus() }; runCatching { focus.requestFocus() } } } }
        } else {
            overlay()
        }
    }
}

/**
 * The skip button of the segment that plays (an opening or a recap at the left, credits and the like at the right) and the next-episode
 * button before the end. Automatic skipping counts down on the button itself. They ride above the controls while these are shown.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.SkipLayer(s: PlayerSession, visible: Boolean, pip: Boolean) {
    val stamp = s.activeStamp
    var countdown by remember(stamp) { mutableStateOf(5) }
    LaunchedEffect(stamp, Appearance.autoSkipStamps, Appearance.autoSkipDelay5s) {
        if (stamp != null && Appearance.autoSkipStamps) {
            if (!Appearance.autoSkipDelay5s) {
                s.skipStamp()
            } else {
                countdown = 5
                while (countdown > 0) {
                    kotlinx.coroutines.delay(1000L)
                    if (s.status == CSPlayerLoading.IsPlaying) countdown--
                }
                if (s.activeStamp == stamp) s.skipStamp()
            }
        }
    }
    val isOpening = stamp != null && when (stamp.timestamp.type) {
        com.lagradost.cloudstream3.utils.videoskip.SkipType.Opening,
        com.lagradost.cloudstream3.utils.videoskip.SkipType.Recap,
        com.lagradost.cloudstream3.utils.videoskip.SkipType.MixedOpening,
        com.lagradost.cloudstream3.utils.videoskip.SkipType.Intro -> true
        else -> false
    }
    val above by androidx.compose.animation.core.animateDpAsState(if (visible) 150.dp else 44.dp, glide(420))
    val enter = fadeIn(glide(300)) + androidx.compose.animation.slideInVertically(glide(460)) { it / 2 }
    val exit = fadeOut(tween(140)) + androidx.compose.animation.slideOutVertically(tween(160)) { it / 3 }
    // the last stamp stays while the button fades out
    var lastStamp by remember { mutableStateOf(stamp) }
    if (stamp != null) lastStamp = stamp
    AnimatedVisibility(
        stamp != null && !pip,
        Modifier.align(if (isOpening) Alignment.BottomStart else Alignment.BottomEnd).padding(start = 28.dp, end = 28.dp, bottom = above),
        enter = enter, exit = exit,
    ) {
        val shown = stamp ?: lastStamp
        if (shown != null) {
            val label = shown.uiText.asStringNull(com.lagradost.desktop.DesktopBootstrap.activity) ?: "Skip"
            SkipPill(label, if (Appearance.autoSkipStamps && Appearance.autoSkipDelay5s) (5 - countdown) / 5f else null, { s.skipStamp() })
        }
    }
    val remaining = s.durationMs - s.positionMs
    AnimatedVisibility(
        !pip && s.hasNext && s.durationMs > 0 && remaining in 1..25_000 && stamp == null,
        Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = above), enter = enter, exit = exit,
    ) {
        SkipPill("Next episode in ${(remaining / 1000 + 1).coerceAtLeast(1)} s", (1f - remaining / 25_000f).coerceIn(0f, 1f), { s.nextEpisode() })
    }
}

/**
 * Picture in picture: the picture is the whole window. Press and drag anywhere on it to move the window (it sticks to the edges of the screen),
 * drag its edges or corners to resize it (it keeps the video's shape); a click plays or pauses, a double click goes back to the app. The title,
 * back to the app and close buttons, play and the progress show while the pointer is moving over it.
 */
@Composable
private fun PipControls(s: PlayerSession, visible: Boolean, taps: Modifier) {
    val bar = com.lagradost.desktop.platform.WinChrome.PIP_STRIP_DP.dp
    // the window is moved from here, not by the frame window: over the video of the native player the frame never sees the pointer
    val drag = Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { },
            onDragEnd = { com.lagradost.desktop.platform.WinChrome.pipDragEnd() },
            onDragCancel = { com.lagradost.desktop.platform.WinChrome.pipDragEnd() },
        ) { change, _ ->
            change.consume()
            com.lagradost.desktop.platform.WinChrome.pipDragMove()
        }
    }
    Box(Modifier.fillMaxSize().then(taps).then(drag)) {
        AnimatedVisibility(visible, Modifier.fillMaxSize(), enter = fadeIn(tween(120)), exit = fadeOut(tween(250))) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().height(bar + 10.dp)
                        .background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent))).padding(start = 12.dp, top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FText(listOfNotNull(s.title, s.episodeLabel).joinToString(" · "), Modifier.weight(1f), style = Fluent.type.caption, color = Color(0xE6FFFFFF), maxLines = 1)
                    IconButton(Icons.BackToWindow, { s.setPip(false) }, tooltip = "Back to the app (Esc)", size = bar, iconSize = 13.dp, tint = Color.White)
                    IconButton(Icons.Close, { Navigator.back() }, tooltip = "Close", size = bar, iconSize = 13.dp, tint = Color.White)
                }
                Box(Modifier.align(Alignment.Center).size(52.dp).background(Color(0x99000000), CircleShape), contentAlignment = Alignment.Center) {
                    IconButton(if (s.status == CSPlayerLoading.IsPlaying) Icons.Pause else Icons.Play, { s.togglePlay() }, size = 52.dp, iconSize = 22.dp, tint = Color.White)
                }
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    val duration = s.durationMs.coerceAtLeast(1)
                    ProgressBar((s.positionMs.toFloat() / duration).coerceIn(0f, 1f), Modifier.fillMaxWidth(), height = 3.dp)
                    FText("${fmt(s.positionMs)} / ${fmt(s.durationMs)}", style = Fluent.type.caption, color = Color(0xCCFFFFFF), maxLines = 1)
                }
            }
        }
        // the grips: the edges and corners resize the window (always there, drawn by the pointer's shape only)
        val edge = 6.dp
        val corner = 16.dp
        PipGrip(3, Alignment.TopCenter, Modifier.fillMaxWidth().height(edge), java.awt.Cursor.N_RESIZE_CURSOR)
        PipGrip(6, Alignment.BottomCenter, Modifier.fillMaxWidth().height(edge), java.awt.Cursor.S_RESIZE_CURSOR)
        PipGrip(1, Alignment.CenterStart, Modifier.fillMaxHeight().width(edge), java.awt.Cursor.W_RESIZE_CURSOR)
        PipGrip(2, Alignment.CenterEnd, Modifier.fillMaxHeight().width(edge), java.awt.Cursor.E_RESIZE_CURSOR)
        PipGrip(4, Alignment.TopStart, Modifier.size(corner), java.awt.Cursor.NW_RESIZE_CURSOR)
        PipGrip(5, Alignment.TopEnd, Modifier.size(corner), java.awt.Cursor.NE_RESIZE_CURSOR)
        PipGrip(7, Alignment.BottomStart, Modifier.size(corner), java.awt.Cursor.SW_RESIZE_CURSOR)
        PipGrip(8, Alignment.BottomEnd, Modifier.size(corner), java.awt.Cursor.SE_RESIZE_CURSOR)
    }
}

/** One grip of the small window: [edge] numbers as WMSZ_* (left 1, right 2, top 3, topleft 4, topright 5, bottom 6, bottomleft 7, bottomright 8) */
@Composable
private fun BoxScope.PipGrip(edge: Int, at: Alignment, size: Modifier, cursor: Int) {
    val chrome = com.lagradost.desktop.platform.WinChrome
    Box(
        Modifier.align(at).then(size)
            .pointerHoverIcon(PointerIcon(java.awt.Cursor.getPredefinedCursor(cursor)))
            .pointerInput(edge) {
                detectDragGestures(
                    onDragStart = { chrome.pipResizeBegin(edge) },
                    onDragEnd = { chrome.pipResizeEnd() },
                    onDragCancel = { chrome.pipResizeEnd() },
                ) { change, _ ->
                    change.consume()
                    chrome.pipResizeMove()
                }
            },
    )
}

// ------------------------------------------------------------------------------------------------

@Composable
private fun VideoSurface(s: PlayerSession, modifier: Modifier) {
    val surface = s.surface
    // a very large window is rendered smaller than it is and enlarged here (see MpvSurfaceView.scaledDrawing)
    surface.scaledDrawing = true
    val smooth = com.lagradost.desktop.ui.fluent.Appearance.smoothMotion
    surface.smoothMotion = smooth
    // smooth motion: while a video whose frame rate does not divide the screen's plays, the picture is drawn on every refresh (a mix of the two frames
    // around it, see MpvSurfaceView.pick); otherwise it is drawn when a frame arrives
    val everyRefresh = smooth && surface.blendUseful && s.status == CSPlayerLoading.IsPlaying
    LaunchedEffect(surface, everyRefresh) {
        if (!everyRefresh) return@LaunchedEffect
        while (true) {
            androidx.compose.runtime.withFrameNanos { }
            surface.drawTick.intValue++
        }
    }
    Box(
        modifier
            .onSizeChanged { surface.setRenderSize(it.width, it.height) }
            .drawBehind {
                val version = surface.frameVersion.intValue
                val image = surface.frame ?: return@drawBehind
                surface.present.drawn(version)
                var mixed: org.jetbrains.skia.Image? = null
                var first = image
                var alpha = 0f
                if (everyRefresh) {
                    surface.drawTick.intValue
                    val grid = surface.present.grid
                    // the refresh this draw ends up in: the next one after it (the draw is handed to the window a few milliseconds from now)
                    val show = grid?.refreshAtOrAfter(System.nanoTime() + 3_000_000L) ?: 0L
                    if (grid != null && show != 0L) {
                        val pick = surface.pick(show, grid.periodNs)
                        pick.first?.let { first = it }
                        mixed = pick.second
                        alpha = pick.alpha
                    }
                }
                drawVideoImage(first, 1f)
                when (blendDebug) { "off" -> {} ; "alpha1" -> mixed?.let { drawVideoImage(it, 1f) } ; else -> mixed?.let { drawVideoImage(it, alpha) } }
            },
    )
}

/** Dev: "off" draws no second frame, "alpha1" draws it fully, anything else is the normal mix (set with /look?blend=) */
@Volatile var blendDebug = ""

/** One video frame over the whole of the draw area; [alpha] below 1 lays it over what is already drawn (the mix of two frames) */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVideoImage(image: org.jetbrains.skia.Image, alpha: Float) {
    if (alpha >= 1f && image.width == size.width.roundToInt() && image.height == size.height.roundToInt()) {
        drawIntoCanvas { c -> c.nativeCanvas.drawImageRect(image, org.jetbrains.skia.Rect.makeWH(size.width, size.height)) }
        return
    }
    // a mix, or rendered smaller than the view (enlarged by the graphics card with a smooth filter): a shader, because the image overloads
    // with a sampling mode or a paint draw nothing in a layer
    // the shader is closed here: it holds the frame (8 MB at 1080p) and, left to the garbage collector, 60 of them a second piled up to ~800 MB
    drawIntoCanvas { c ->
        org.jetbrains.skia.Paint().use { paint ->
            image.makeShader(
                org.jetbrains.skia.FilterTileMode.CLAMP, org.jetbrains.skia.FilterTileMode.CLAMP,
                if (image.width == size.width.roundToInt() && image.height == size.height.roundToInt()) org.jetbrains.skia.SamplingMode.DEFAULT else org.jetbrains.skia.SamplingMode.CATMULL_ROM,
                org.jetbrains.skia.Matrix33.makeScale(size.width / image.width, size.height / image.height),
            ).use { shader ->
                paint.shader = shader
                paint.setAlphaf(alpha)
                c.nativeCanvas.drawRect(org.jetbrains.skia.Rect.makeWH(size.width, size.height), paint)
            }
        }
    }
}

@Composable
private fun LoadingOverlay(s: PlayerSession) {
    var listOpen by remember { mutableStateOf(false) }
    Box(
        // the page colour of the theme, as every other page (a blue-grey gradient used to show here)
        Modifier.fillMaxSize().background(Fluent.colors.bg).pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        // the episode's own picture, dark and blurred, behind the words
        s.episodePoster?.let { com.lagradost.desktop.ui.components.SoftImage(it, null, Modifier.fillMaxSize(), alpha = 0.22f) }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x66000000), 0.5f to Color.Transparent, 1f to Color(0x99000000))))
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.widthIn(max = 600.dp).padding(24.dp)) {
            FText(s.title, style = Fluent.type.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Color.White, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            s.episodeLabel?.let { FText(it, style = Fluent.type.bodyStrong.copy(fontSize = 16.sp), color = Color(0xCCFFFFFF), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            Box(Modifier.height(10.dp))
            ProgressBar(null, Modifier.width(320.dp), height = 3.dp)
            FText(s.loadingText ?: "", color = Color(0xB3FFFFFF), maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            // a torrent: who it is downloaded from and how far the first part is
            s.torrent?.let { t ->
                if (t.peers > 0 || t.speedBps > 0) FText("${t.peers} peers (${t.seeds} seeds)" + (if (t.speedBps > 0) "  ·  " + com.lagradost.desktop.torrent.TorrentConsent.speed(t.speedBps) else ""), style = Fluent.type.caption, color = Color(0xB3FFFFFF))
                if (t.percent in 1..99) ProgressBar(t.percent / 100f, Modifier.width(260.dp))
            }
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
                // the sources found so far, to pick one by hand without waiting
                if (s.linksFound > 0) Button(if (listOpen) "Hide sources" else "Show sources (${s.linksFound})", { listOpen = !listOpen }, icon = Icons.Link)
                Button("Cancel", { Navigator.back() })
            }
        }
        }
        if (listOpen) SourcesPanel(s, { listOpen = false }, Modifier.padding(end = 28.dp))
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
    val appear = remember { androidx.compose.animation.core.Animatable(0.6f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, androidx.compose.animation.core.spring(0.5f, 420f)) }
    Box(
        modifier.size(88.dp).graphicsLayer { scaleX = appear.value; scaleY = appear.value; alpha = ((appear.value - 0.6f) / 0.4f).coerceIn(0f, 1f) }
            .clip(CircleShape).background(Color(0x73000000)).border(androidx.compose.ui.unit.Dp.Hairline, Color(0x4DFFFFFF), CircleShape)
            .pointerInput(Unit) { detectTapGestures { s.play() } },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Play, size = 34.dp, tint = Color.White)
    }
}

@Composable
private fun FailureOverlay(s: PlayerSession) {
    var listOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Fluent.colors.bg.copy(alpha = 0.94f)).pointerInput(Unit) { detectTapGestures { } }, contentAlignment = Alignment.Center) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.widthIn(max = 520.dp).padding(24.dp)) {
            Icon(Icons.Warning, size = 40.dp, tint = Fluent.colors.caution)
            FText("This video could not be played", style = Fluent.type.subtitle, color = Color.White)
            FText(s.failure ?: "", color = Color(0xCCFFFFFF), maxLines = 5)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.hasNextMirror) Button("Try next source", { s.nextMirror() }, kind = ButtonKind.Accent, icon = Icons.Next)
                Button("Reload sources", { s.reloadSources() }, icon = Icons.Refresh)
                if (s.linksFound > 0) Button(if (listOpen) "Hide sources" else "Show sources (${s.linksFound})", { listOpen = !listOpen }, icon = Icons.Link)
                Button("Back", { Navigator.back() })
            }
        }
        }
        if (listOpen) SourcesPanel(s, { listOpen = false }, Modifier.padding(end = 28.dp))
        }
    }
}

/** The sources found so far, to pick one by hand while the player is still looking (or after it gave up) */
@Composable
internal fun SourcesPanel(s: PlayerSession, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val items = s.sources().filter { it.usable || it.current }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.width(420.dp).heightIn(max = 460.dp).clip(shape).background(Color(0xF0101114), shape).border(Dp.Hairline, GlassBorder, shape)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                FText("Sources", style = Fluent.type.subtitle, color = Color.White, maxLines = 1)
                FText("${items.size} found" + if (s.loadingMore) "  ·  still looking" else "", style = Fluent.type.caption, color = Color(0x99FFFFFF), maxLines = 1)
            }
            IconButton(Icons.Refresh, { onClose(); s.reloadSources() }, tooltip = "Reload sources: look for them again", tint = Color.White)
            IconButton(Icons.Close, onClose, tooltip = "Close", tint = Color.White)
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).padding(bottom = 8.dp)) {
            if (items.isEmpty()) item { FText("Nothing yet…", Modifier.padding(horizontal = 18.dp, vertical = 12.dp), style = Fluent.type.caption, color = Color(0x99FFFFFF)) }
            itemsIndexed(items) { _, src ->
                val source = rememberInteraction()
                val hovered by source.collectIsHoveredAsState()
                val rowShape = RoundedCornerShape(10.dp)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(rowShape)
                        .background(if (src.current) Color(0x26FFFFFF) else if (hovered) Color(0x14FFFFFF) else Color.Transparent, rowShape)
                        .fluentClickable(source, true, rowShape, Role.Button) { s.selectSource(src.link) }.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        FText(src.name.lineSequence().first(), style = Fluent.type.bodyStrong, color = Color.White, maxLines = 1)
                        if (src.quality.isNotBlank()) FText(src.quality, style = Fluent.type.caption, color = Color(0xB3FFFFFF), maxLines = 1)
                    }
                    if (src.current) FText(if (s.startingSource) "Starting…" else "Playing", style = Fluent.type.caption, color = Fluent.colors.accentText, maxLines = 1)
                    else Icon(Icons.Play, size = 12.dp, tint = Color(0x80FFFFFF))
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------

/**
 * The controls over the picture. Modern: a soft shade at the top (back, title, what plays) and one at the bottom with the seek
 * bar and the buttons right on the picture; Classic: the same in a solid full-width band. Both report where they are so the
 * 2 s auto-hide knows the pointer is on them.
 */
@Composable
private fun PlayerChrome(
    s: PlayerSession,
    fullscreen: Boolean,
    openMenu: (MenuPage) -> Unit,
    toggleEpisodes: () -> Unit,
    toggleFullscreen: () -> Unit,
    onTop: (Rect) -> Unit,
    onBottom: (Rect) -> Unit,
) {
    val modern = com.lagradost.desktop.ui.fluent.Appearance.playerStyle == com.lagradost.desktop.ui.fluent.PlayerStyle.Modern
    Box(Modifier.fillMaxSize()) {
        // as on Android the tracks button only exists when there is something to choose: more than one video or audio track
        // top: back, title, what plays
        Box(
            Modifier.align(Alignment.TopStart).fillMaxWidth()
                .background(Brush.verticalGradient(0f to Color(if (modern) 0xB8000000 else 0xE6000000), 0.3f to Color(if (modern) 0x85000000 else 0xC0000000), 0.62f to Color(if (modern) 0x33000000 else 0x66000000), 0.85f to Color(if (modern) 0x0F000000 else 0x1A000000), 1f to Color.Transparent))
                .padding(start = 20.dp, end = 20.dp + com.lagradost.desktop.ui.shell.captionInset, top = 16.dp, bottom = 64.dp),
        ) {
            Row(Modifier.fillMaxWidth().onGloballyPositioned { onTop(it.boundsInRoot()) }, verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Back, "Back (Esc)", Modifier.noWindowDrag("playerBack")) { Navigator.back() }
                Box(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    FText(s.title, style = Fluent.type.subtitle.copy(fontSize = 19.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Color.White, maxLines = 1)
                    s.episodeLabel?.let { FText(it, color = Color(0xB3FFFFFF), maxLines = 1) }
                    // what plays: the source in use and its picture size, quietly, under the title
                    s.sourceName?.let { name ->
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            InfoChip(name.lineSequence().first())
                            s.resolution?.let { InfoChip(it) }
                            if (s.loadingMore) FText("more sources loading…", style = Fluent.type.caption, color = Color(0x99FFFFFF), maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
        // bottom: seek bar and buttons
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(if (modern) Brush.verticalGradient(0f to Color.Transparent, 0.15f to Color(0x0F000000), 0.38f to Color(0x4D000000), 0.7f to Color(0x9E000000), 1f to Color(0xE0000000)) else Brush.verticalGradient(listOf(Color(0xE6000000), Color(0xE6000000))))
                .padding(top = if (modern) 110.dp else 0.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = if (modern) 0.dp else 10.dp, bottom = if (modern) 14.dp else 10.dp)
                    .onGloballyPositioned { onBottom(it.boundsInRoot()) },
            ) {
                SeekRow(s)
                ControlRow(s, fullscreen, openMenu, toggleEpisodes, toggleFullscreen)
            }
        }
    }
}

/** Elapsed time, the seek bar and the length of the video (a live channel has only the bar) */
@Composable
private fun SeekRow(s: PlayerSession) {
    val time = Fluent.type.bodyStrong.copy(fontFeatureSettings = "tnum")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (!s.live) FText(fmt(s.positionMs), Modifier.widthIn(min = 52.dp), style = time, color = Color.White, maxLines = 1, softWrap = false)
        Box(Modifier.weight(1f)) { SeekBar(s) }
        if (!s.live) {
            val remainingMs = (s.durationMs - s.positionMs).coerceAtLeast(0L)
            val text = if (Appearance.showRemainingTime) "-${fmt(remainingMs)}" else fmt(s.durationMs)
            val tooltip = if (Appearance.showRemainingTime) "Remaining time (click for total)" else "Total duration (click for remaining)"
            com.lagradost.desktop.ui.fluent.Tooltip(tooltip) {
                val shape = RoundedCornerShape(4.dp)
                FText(
                    text,
                    Modifier.widthIn(min = 54.dp)
                        .clip(shape)
                        .fluentClickable(rememberInteraction(), true, shape, Role.Button) {
                            Appearance.showRemainingTime = !Appearance.showRemainingTime
                            Appearance.save()
                        },
                    style = time.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                    color = Color(0xB3FFFFFF),
                    maxLines = 1,
                    softWrap = false,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
        }
    }
}


/** A round see-through button for use on the picture */
@Composable
private fun GlassIconButton(glyph: String, tooltip: String, modifier: Modifier = Modifier, size: Dp = 40.dp, onClick: () -> Unit) {
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val bg by androidx.compose.animation.animateColorAsState(if (hovered) Color(0x40FFFFFF) else Color(0x1FFFFFFF), com.lagradost.desktop.ui.fluent.FluentMotion.tweenStd(140))
    com.lagradost.desktop.ui.fluent.Tooltip(tooltip) {
        Box(
            modifier.size(size).clip(CircleShape).background(bg, CircleShape).border(androidx.compose.ui.unit.Dp.Hairline, Color(0x33FFFFFF), CircleShape)
                .fluentClickable(source, true, CircleShape, Role.Button, onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(glyph, size = 16.dp, tint = Color.White) }
    }
}

@Composable
private fun InfoChip(text: String) {
    Box(Modifier.background(Color(0x26FFFFFF), RoundedCornerShape(50)).border(androidx.compose.ui.unit.Dp.Hairline, Color(0x1AFFFFFF), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 1.dp)) {
        FText(text, style = Fluent.type.caption, color = Color(0xE6FFFFFF), maxLines = 1, softWrap = false)
    }
}

/**
 * Seek bar: thin at rest, thicker under the pointer with a glowing accent fill, the buffered part, skip markers and a time
 * bubble. For a live channel it spans the buffered window and its end is "live".
 */
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
    val active = hovered || dragFraction != null
    val trackH by androidx.compose.animation.core.animateDpAsState(if (active) 7.dp else 4.dp, com.lagradost.desktop.ui.fluent.FluentMotion.tweenIn(160))
    val thumb by androidx.compose.animation.core.animateFloatAsState(if (active) 1f else 0f, com.lagradost.desktop.ui.fluent.FluentMotion.tweenIn(180))
    val accent = c.accent

    Box(
        Modifier
            .fillMaxWidth()
            .height(26.dp)
            .hoverable(source)
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .pointerHoverIcon(androidx.compose.ui.input.pointer.PointerIcon.Hand)
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
            }
            .drawBehind {
                val h = trackH.toPx()
                val y = (size.height - h) / 2
                val r = androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2)
                drawRoundRect(Color(0x33FFFFFF), Offset(0f, y), Size(size.width, h), r)
                drawRoundRect(Color(0x4DFFFFFF), Offset(0f, y), Size(size.width * buffered, h), r)
                // skip markers (intro / outro)
                for ((from, to) in stamps) drawRect(Color(0xCCFFD54F), Offset(size.width * from, y), Size(size.width * (to - from), h))
                // the played part: a flat accent fill (no glow)
                drawRoundRect(accent, Offset(0f, y), Size(size.width * shown, h), r)
                hoverX?.let { hx -> if (!s.live) drawRect(Color(0x66FFFFFF), Offset(hx - 1f, y), Size(2f, h)) }
                if (thumb > 0f) {
                    val cx = size.width * shown
                    drawCircle(Color.White, 7.dp.toPx() * thumb, Offset(cx, size.height / 2))
                }
            },
    ) {
        hoverX?.let { x ->
            val f = (x / width).coerceIn(0f, 1f)
            val at = (f * s.durationMs).toLong()
            val stamp = s.stampLabelAt(at)
            val label = if (s.live) "-" + fmt((s.durationMs - at).coerceAtLeast(0)) else fmt(at)
            Box(Modifier.align(Alignment.TopStart).offset2((x - 44f).coerceIn(0f, (width - 88f).coerceAtLeast(0f))).offsetY(-40f)) {
                Column(
                    Modifier.background(PlayerSurface, RoundedCornerShape(com.lagradost.desktop.ui.fluent.FluentShapes.small)).border(androidx.compose.ui.unit.Dp.Hairline, PlayerSurfaceBorder, RoundedCornerShape(com.lagradost.desktop.ui.fluent.FluentShapes.small)).padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (stamp != null) FText(stamp, style = Fluent.type.caption, color = Color(0xFFFFD54F), maxLines = 1, softWrap = false)
                    FText(label, style = Fluent.type.bodyStrong, color = Color.White, maxLines = 1, softWrap = false)
                }
            }
        }
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
private fun ControlRow(s: PlayerSession, fullscreen: Boolean, openMenu: (MenuPage) -> Unit, toggleEpisodes: () -> Unit, toggleFullscreen: () -> Unit) {
    val white = Color.White
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        PlayPauseButton(s)
        Box(Modifier.width(8.dp))
        if (s.hasPrev) IconButton(Icons.Previous, { s.prevEpisode() }, tooltip = "Previous episode (Ctrl+Left)", size = 40.dp, iconSize = 18.dp, tint = white)
        IconButton(Icons.Rewind, { s.seekBy(-10_000) }, tooltip = "Back 10 s (J)", size = 40.dp, iconSize = 20.dp, tint = white)
        IconButton(Icons.FastForward, { s.seekBy(10_000) }, tooltip = "Forward 10 s (L)", size = 40.dp, iconSize = 20.dp, tint = white)
        if (s.hasNext) IconButton(Icons.Next, { s.nextEpisode() }, tooltip = "Next episode (Ctrl+Right)", size = 40.dp, iconSize = 18.dp, tint = white)
        Box(Modifier.width(4.dp))
        VolumeControl(s)
        if (s.live) { Box(Modifier.width(12.dp)); LivePill(s) }
        Box(Modifier.weight(1f))
        if (s.speed != 1f) Box(Modifier.padding(end = 8.dp)) { InfoChip("${s.speed}×") }

        // one menu for every choice (PlayerMenu.kt): these open it at their page, the gear at the list of all settings
        IconButton(Icons.Subtitles, { openMenu(MenuPage.Subtitles) }, tooltip = "Subtitles (S: next)", size = 40.dp, iconSize = 18.dp, tint = white)
        IconButton(Icons.Link, { openMenu(MenuPage.Sources) }, tooltip = "Sources", size = 40.dp, iconSize = 18.dp, tint = white)
        IconButton(Icons.Settings, { openMenu(MenuPage.Root) }, tooltip = "Quality, audio, speed and more", size = 40.dp, iconSize = 18.dp, tint = white)
        IconButton(Icons.List, toggleEpisodes, tooltip = "Episodes (E)", size = 40.dp, iconSize = 18.dp, tint = white)
        IconButton(Icons.Pip, { s.togglePip() }, tooltip = "Picture in picture (I)", size = 40.dp, iconSize = 18.dp, tint = white)
        IconButton(if (fullscreen) Icons.ExitFullscreen else Icons.Fullscreen, toggleFullscreen, tooltip = if (fullscreen) "Exit full screen (F)" else "Full screen (F)", size = 40.dp, iconSize = 18.dp, tint = white)
    }
}

/** The main button: an accent disc that swaps its glyph with a little turn */
@Composable
private fun PlayPauseButton(s: PlayerSession) {
    val c = Fluent.colors
    val playing = s.status == CSPlayerLoading.IsPlaying
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.92f else if (hovered) 1.06f else 1f, com.lagradost.desktop.ui.fluent.FluentMotion.tweenIn(140))
    com.lagradost.desktop.ui.fluent.Tooltip("Play / Pause (Space)") {
        Box(
            Modifier.size(42.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(c.accentBrush(hovered, pressed), CircleShape)
                .fluentClickable(source, true, CircleShape, Role.Button) { s.togglePlay() },
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.animation.AnimatedContent(playing, transitionSpec = {
                (fadeIn(com.lagradost.desktop.ui.fluent.FluentMotion.tweenIn(160)) + androidx.compose.animation.scaleIn(com.lagradost.desktop.ui.fluent.FluentMotion.tweenIn(200), initialScale = 0.6f)) togetherWith
                    (fadeOut(com.lagradost.desktop.ui.fluent.FluentMotion.tweenOut(100)) + androidx.compose.animation.scaleOut(com.lagradost.desktop.ui.fluent.FluentMotion.tweenOut(100), targetScale = 0.6f))
            }) { p -> Icon(if (p) Icons.Pause else Icons.Play, size = 18.dp, tint = c.onAccent) }
        }
    }
}

/** Mute button; the slider slides out while the pointer is on it */
@Composable
internal fun VolumeControl(s: PlayerSession) {
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val open = hovered || dragging
    val w by androidx.compose.animation.core.animateDpAsState(if (open) 104.dp else 0.dp, com.lagradost.desktop.ui.fluent.FluentMotion.tweenIn(220))
    Row(Modifier.hoverable(source), verticalAlignment = Alignment.CenterVertically) {
        IconButton(if (s.muted || s.volume == 0) Icons.Mute else Icons.Volume, { s.toggleMute() }, tooltip = "Mute (M) · wheel: volume", size = 40.dp, iconSize = 18.dp, tint = Color.White)
        Box(Modifier.width(w).clipToBounds()) {
            Row(Modifier.width(104.dp), verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = if (s.muted) 0f else s.volume.toFloat(), onValueChange = { dragging = true; s.changeVolume(it.toInt()) },
                    valueRange = 0f..200f, modifier = Modifier.width(66.dp), onValueChangeFinished = { dragging = false },
                )
                FText("${s.volume}", style = Fluent.type.caption, color = Color(0xCCFFFFFF), modifier = Modifier.padding(start = 8.dp), maxLines = 1, softWrap = false)
            }
        }
    }
}

/** Live: a red dot and LIVE at the live point; behind it, how far, and a click goes back to live */
@Composable
internal fun LivePill(s: PlayerSession) {
    val behind = s.liveBehindS ?: 0.0
    val atLive = behind < 12.0
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition()
    val a by pulse.animateFloat(0.45f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(900), androidx.compose.animation.core.RepeatMode.Reverse))
    val shape = RoundedCornerShape(50)
    com.lagradost.desktop.ui.fluent.Tooltip(if (atLive) "Live" else "Go to the live picture") {
        Row(
            Modifier.height(28.dp).clip(shape)
                .background(if (atLive) Color(0x33FF4040) else if (hovered) Color(0x40FFFFFF) else Color(0x26FFFFFF), shape)
                .fluentClickable(source, !atLive, shape, Role.Button) { s.goLive() }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).graphicsLayer { alpha = if (atLive) a else 1f }.background(if (atLive) Color(0xFFFF4545) else Color(0xFF9E9E9E), CircleShape))
            Box(Modifier.width(8.dp))
            FText(if (atLive) "LIVE" else "LIVE  −${behind.toInt()} s", style = Fluent.type.bodyStrong, color = Color.White, maxLines = 1, softWrap = false)
        }
    }
}

// ------------------------------------------------------------------------------------------------

@Composable
private fun EpisodesPanel(s: PlayerSession, onClose: () -> Unit) {
    val c = Fluent.colors
    val items by androidx.compose.runtime.produceState(emptyList<PlayerSession.EpisodeItem>(), s.episodeVersion) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { s.episodes() }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(items.isNotEmpty()) { items.indexOfFirst { it.current }.takeIf { it >= 0 }?.let { listState.scrollToItem((it - 1).coerceAtLeast(0)) } }
    // a card that floats at the right edge (it slides in, see the caller)
    val panelShape = RoundedCornerShape(18.dp)
    Column(
        Modifier.padding(top = if (com.lagradost.desktop.platform.WinChrome.windowedBar) 46.dp else 12.dp, end = 12.dp, bottom = 12.dp)
            .fillMaxHeight().width(392.dp).clip(panelShape).background(Color(0xFF161719), panelShape).border(androidx.compose.ui.unit.Dp.Hairline, PlayerSurfaceBorder, panelShape)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            FText("Episodes", Modifier.weight(1f), style = Fluent.type.subtitle, color = Color.White)
            IconButton(Icons.Close, onClose, tooltip = "Close", tint = Color.White)
        }
        val currentIdx = items.indexOfFirst { it.current }
        val uncoveredIds = remember { mutableStateListOf<Int>() }
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
            itemsIndexed(items) { index, item ->
                val ep = item.episode
                val source = rememberInteraction()
                val hovered by source.collectIsHoveredAsState()
                val shape = RoundedCornerShape(6.dp)

                val isAfterCurrent = if (currentIdx >= 0) index > currentIdx else {
                    val firstUnwatched = items.indexOfFirst { it.fraction <= 0f && it.episode.videoWatchState != com.lagradost.cloudstream3.ui.result.VideoWatchState.Watched }
                    firstUnwatched >= 0 && index > firstUnwatched
                }
                val isUnviewed = item.fraction <= 0f && ep.videoWatchState != com.lagradost.cloudstream3.ui.result.VideoWatchState.Watched
                val spoilerAhead = Appearance.hideSpoilers && isAfterCurrent && isUnviewed
                val isCovered = spoilerAhead && (ep.id !in uncoveredIds)

                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 2.dp).fillMaxWidth().clip(shape)
                        .background(if (item.current) Color(0x22FFFFFF) else if (hovered) Color(0x14FFFFFF) else Color.Transparent, shape)
                        .fluentClickable(source, true, shape, Role.Button) { s.playEpisode(item) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(112.dp, 63.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x22FFFFFF))) {
                        RemoteImage(ep.poster, null, null, if (isCovered) Modifier.fillMaxSize().blur(16.dp) else Modifier.fillMaxSize(), ContentScale.Crop)
                        if (isCovered) {
                            Box(Modifier.fillMaxSize().background(Color(0x66000000)))
                            Box(
                                Modifier.align(Alignment.Center).clip(CircleShape).background(Color(0x99000000)).padding(6.dp)
                                    .fluentClickable(rememberInteraction(), true, CircleShape, Role.Button) { uncoveredIds.add(ep.id) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Eye, size = 14.dp, tint = Color.White)
                            }
                        }
                        if (item.current) Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) { Icon(Icons.Play, size = 20.dp, tint = Color.White) }
                        if (item.fraction > 0f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()) { ProgressBar(item.fraction, height = 3.dp) }
                    }
                    Column(Modifier.weight(1f)) {
                        FText(buildString {
                            if (ep.season != null) append("S${ep.season} · ")
                            append("Episode ${ep.episode}")
                        }, style = Fluent.type.caption, color = Color(0xCCFFFFFF), maxLines = 1)
                        val title = if (isCovered) "Episode ${ep.episode}" else (ep.name ?: "Episode ${ep.episode}")
                        FText(title, style = Fluent.type.bodyStrong, color = Color.White, maxLines = 2)
                    }
                    // a spoiler that was shown can be covered again
                    if (spoilerAhead && !isCovered) IconButton(Icons.Hide, { uncoveredIds.remove(ep.id) }, tooltip = "Hide spoiler", size = 28.dp, iconSize = 14.dp, tint = Color(0xCCFFFFFF))
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
            // a new search replaces the one that is running (Search, Enter, another language), instead of being ignored until it ends
            val generation = remember { intArrayOf(0) }
            val running = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
            fun search() {
                running[0]?.cancel()
                val mine = ++generation[0]
                busy = true
                results = emptyList()
                running[0] = scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    // results show as each provider answers (one that times out must not hold the rest back)
                    try {
                        val all = s.searchSubtitles(q, lang) { partial -> if (mine == generation[0]) results = partial }
                        if (mine == generation[0]) results = all
                    } finally {
                        if (mine == generation[0]) { busy = false; searched = true }
                    }
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
                                .fluentClickable(source, true, shape, Role.Button) { s.downloadOnlineSubtitle(entity); dismiss() }
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

/**
 * Says what the subtitle loader is doing, so a subtitle that takes a while (or never comes) is not a mystery: a subtitles glyph while it is looked
 * for or fetched, a check when it is on (gone after 2 s), a warning when it could not be loaded (gone after 5 s). Quick local loads do not flash it.
 * No animation at all: a ring or a fade over the video made its frames arrive late (measured: publish->draw 0.8 ms -> 6.9 ms, late frames).
 */
@Composable
private fun SubtitlePill(s: PlayerSession, modifier: Modifier) {
    val status = s.subtitleIndicator
    var shown by remember { mutableStateOf(false) }
    // keyed by the status object itself: the timers below must follow the status that is shown, also when a new one has the same words
    LaunchedEffect(status) {
        when (status?.state) {
            null -> shown = false
            // a pill that is up stays up when the next step of the same load replaces it (download -> adding it to the video), no blink in between
            SubtitleLoadState.Loading -> if (!shown) { delay(300); shown = true }
            SubtitleLoadState.Loaded -> { shown = true; delay(2000); s.clearSubtitleStatus(status) }
            SubtitleLoadState.Failed -> { shown = true; delay(5000); s.clearSubtitleStatus(status) }
        }
    }
    if (shown && status != null) {
        val st = status
        val shape = RoundedCornerShape(50)
        Row(
            modifier.widthIn(max = 380.dp).background(PlayerSurface, shape).border(androidx.compose.ui.unit.Dp.Hairline, PlayerSurfaceBorder, shape).padding(start = 12.dp, end = 14.dp).height(32.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (st.state) {
                SubtitleLoadState.Loading -> Icon(Icons.Subtitles, size = 14.dp, tint = Color(0xB3FFFFFF))
                SubtitleLoadState.Loaded -> Icon(Icons.Check, size = 14.dp, tint = Color(0xFF6CCB5F))
                SubtitleLoadState.Failed -> Icon(Icons.Warning, size = 14.dp, tint = Color(0xFFFFB74D))
            }
            FText(st.text, style = Fluent.type.caption, color = Color(0xE6FFFFFF), maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
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
            Modifier.background(PlayerSurface, shape).border(androidx.compose.ui.unit.Dp.Hairline, PlayerSurfaceBorder, shape).padding(start = 14.dp, end = 16.dp).height(38.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(h.glyph, size = 16.dp, tint = Color.White)
            FText(h.text, style = Fluent.type.bodyStrong, color = Color.White, maxLines = 1, softWrap = false)
            h.fraction?.let { target ->
                val shown by androidx.compose.animation.core.animateFloatAsState(target.coerceIn(0f, 1f), tween(120))
                Box(Modifier.width(64.dp).height(4.dp).background(Color(0x40FFFFFF), RoundedCornerShape(2.dp))) {
                    Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(Fluent.colors.accent, RoundedCornerShape(2.dp)))
                }
            }
        }
    }
}


/** While a torrent plays: the peers it comes from, the speed and how full the buffer is (the viewer sees why it stalls) */
@Composable
private fun TorrentPill(s: PlayerSession, modifier: Modifier) {
    val t = s.torrent ?: return
    if (s.loadingText != null) return
    val shape = RoundedCornerShape(50)
    Row(
        modifier.widthIn(max = 420.dp).background(PlayerSurface, shape).border(androidx.compose.ui.unit.Dp.Hairline, PlayerSurfaceBorder, shape).padding(start = 12.dp, end = 14.dp).height(32.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Link, size = 14.dp, tint = Fluent.colors.accent)
        FText(
            "Torrent  ·  ${t.peers} peers  ·  ${com.lagradost.desktop.torrent.TorrentConsent.speed(t.speedBps)}" + if (t.percent in 1..99) "  ·  buffer ${t.percent} %" else "",
            style = Fluent.type.caption, color = Color(0xE6FFFFFF), maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}
