package com.lagradost.desktop

import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import com.lagradost.desktop.ui.shell.ExternalTitleBar
import com.lagradost.desktop.ui.shell.RevealedTitleBar
import com.lagradost.desktop.ui.shell.PortableResizeEdges
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.foundation.onFocusedBoundsChanged
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.platform.SingleInstanceIpc
import com.lagradost.desktop.platform.WinTheme
import com.lagradost.desktop.platform.WindowGeometry
import com.lagradost.desktop.runtime.ui.DisplaySync
import com.lagradost.desktop.runtime.ui.ThemeBridge
import com.lagradost.desktop.ui.DesktopUiHost
import com.lagradost.desktop.ui.LegacyOverlays
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentSettings
import com.lagradost.desktop.ui.fluent.FluentTheme
import com.lagradost.desktop.ui.fluent.InputMode
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.shell.AppShell
import com.lagradost.desktop.ui.shell.ShellState
import kotlinx.coroutines.delay
import java.awt.Dimension

/** The native Windows UI: one Fluent window around [AppShell] */
@Composable
fun ApplicationScope.NativeWindow() {
    val state = remember { WindowGeometry.initialState() }
    LaunchedEffect(state) {
        snapshotFlow { Triple(state.placement, state.position, state.size) }
            .collect { delay(400); WindowGeometry.update(state) }
    }
    Window(
        onCloseRequest = {
            WindowGeometry.update(state)
            SingleInstanceIpc.stopServer()
            for (step in listOf(ActivityStack::destroyAll, MainActivity::deleteFilesOnExit)) {
                try {
                    step()
                } catch (t: Throwable) {
                    android.util.Log.e("NativeApp", "shutdown step failed", t)
                }
            }
            exitApplication()
        },
        state = state,
        title = com.lagradost.desktop.ui.shell.ShellState.windowTitle,
        // Linux: no system frame, the app's own title bar instead (WinChrome.portable)
        undecorated = com.lagradost.desktop.platform.WinChrome.portable,
        icon = painterResource(R.mipmap.ic_launcher),
        onPreviewKeyEvent = NativeKeys::onPreviewKey,
        onKeyEvent = NativeKeys::onKey,
    ) {
        DesktopUiHost.window = window
        // the arrow keys move the focus (see NativeKeys.onKey)
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        androidx.compose.runtime.SideEffect { NativeKeys.focusManager = focusManager }
        remember { com.lagradost.desktop.platform.StartupProfile.mark("window content composing") }
        // the rest of the extensions load once the first frame is up (see PluginLoadGate)
        LaunchedEffect(Unit) { delay(700); com.lagradost.cloudstream3.plugins.PluginLoadGate.firstFrame.complete(Unit) }
        // files and links dropped on the window (a video plays, subtitles are added, a CloudStream link opens): the drawing surface exists once the window shows
        LaunchedEffect(window) { delay(1500); com.lagradost.desktop.ui.DropOpen.install(window) }
        if (com.lagradost.desktop.tools.FrameStats.enabled) LaunchedEffect(Unit) { com.lagradost.desktop.tools.FrameStats.run() }
        remember {
            window.addWindowFocusListener(object : java.awt.event.WindowAdapter() {
                override fun windowGainedFocus(e: java.awt.event.WindowEvent) = com.lagradost.desktop.DesktopLifecycle.windowFocus(true)
                override fun windowLostFocus(e: java.awt.event.WindowEvent) = com.lagradost.desktop.DesktopLifecycle.windowFocus(false)
            })
        }
        DesktopUiHost.windowState = state
        // a few seconds after the app is on screen: tells about a newer version on GitHub (nothing is installed by the app)
        remember { com.lagradost.desktop.update.UpdateCheck.startAutoCheck() }
        // Windows only snaps a window to half or a corner of the screen when the zone is not smaller than the window's minimum size: 760 x 520 (the
        // pixels of a 150 % display: 1140 x 780) was larger than a half and a corner of a 1920 x 1080 screen, so dragging the window to an edge
        // or a corner showed no snap. A quarter of the smallest common screen (1366 x 768 at 125 %) fits this.
        remember { window.minimumSize = Dimension(480, 320) }
        // a content area of a whole number of pixels: no stretched (smeared) 1 px borders at 125 / 150 / 175 % display scaling
        remember { com.lagradost.desktop.platform.EvenPixels.install(window) }
        remember { com.lagradost.desktop.platform.AppIcon.install(window) }
        remember { FluentSettings.load(); com.lagradost.desktop.ui.fluent.Appearance.load() }
        FluentTheme {
            val c = Fluent.colors
            // what is not painted (yet, or any more) shows the page colour and not the white of an AWT window: a canvas that did not follow a resize, the
            // first paint of a new window
            LaunchedEffect(window, c.bg) { runCatching { paintBackground(window, c.bg.toArgb()) } }
            // the Compose scene must be as big as the window; when it is not for a second (the white area to the right and below the page of a bug report)
            // the frame messages are sent again and the window is laid out again
            val sceneInfo = androidx.compose.ui.platform.LocalWindowInfo.current
            LaunchedEffect(window) { watchSceneSize(window) { sceneInfo.containerSize } }
            val fullscreenNow = com.lagradost.desktop.platform.WinChrome.fullscreen
            SideEffect { WinTheme.styleWindow(window, c.dark, c.bg, c.text, fullscreenNow) }
            // integrated title bar (falls back to the native one if the window procedure can not be hooked)
            LaunchedEffect(window) {
                repeat(40) {
                    if (window.isDisplayable && com.lagradost.desktop.platform.WinChrome.install(window)) return@LaunchedEffect
                    delay(100)
                }
            }
            // finger input: Windows marks the mouse events it makes from touches; the window procedures are only observed, nothing is consumed or made
            // (the canvas can be recreated: installing again is harmless)
            LaunchedEffect(window) {
                while (true) {
                    if (window.isDisplayable) com.lagradost.desktop.platform.WindowsTouch.install(window)
                    delay(2000)
                }
            }
            // Compose's canvas window can be recreated; it must keep letting the frame decide over the caption area
            LaunchedEffect(window) {
                while (true) {
                    delay(2000)
                    if (com.lagradost.desktop.platform.WinChrome.enabled) com.lagradost.desktop.platform.WinChrome.refreshChildren(window)
                }
            }
            // Android widgets (extension screens, engine dialogs) follow the same colours
            SideEffect {
                ThemeBridge.colorPrimary = c.accent.toArgb()
                ThemeBridge.colorOnPrimary = c.onAccent.toArgb()
                ThemeBridge.background = c.bg.toArgb()
                ThemeBridge.surface = c.flyout.toArgb()
                ThemeBridge.surfaceVariant = c.layer.toArgb()
                ThemeBridge.textColorPrimary = c.text.toArgb()
                ThemeBridge.textColorSecondary = c.textSecondary.toArgb()
                ThemeBridge.isDark = c.dark
                // the themes of the contexts were made with the first colours: the next dialog or extension screen reads the current ones
                runCatching {
                    ThemeBridge.populate(com.lagradost.desktop.DesktopBootstrap.activity.theme)
                    ThemeBridge.populate(com.lagradost.desktop.runtime.AndroidRuntime.context.theme)
                }
            }
            LaunchedEffect(Unit) {
                // first run wizard, then the profile picker when there are several profiles
                if (!com.lagradost.desktop.ui.screens.setup.setupDone()) Navigator.go(com.lagradost.desktop.core.Route.Setup)
                else runCatching {
                    val skip = androidx.preference.PreferenceManager.getDefaultSharedPreferences(com.lagradost.desktop.DesktopBootstrap.activity)
                        .getBoolean(com.lagradost.desktop.DesktopBootstrap.activity.getString(R.string.skip_startup_account_select_key), false)
                    val all = com.lagradost.cloudstream3.utils.DataStoreHelper.getAccounts(com.lagradost.desktop.DesktopBootstrap.activity)
                    // the profile chosen in Edit profile ("Open the app with this profile") is used without asking; a PIN of it is still asked
                    val chosen = com.lagradost.desktop.ui.shell.StartProfile.get()?.let { key -> all.firstOrNull { it.keyIndex == key } }
                    if (chosen != null) {
                        com.lagradost.desktop.ui.Startup.revealed.await()
                        if (chosen.keyIndex != com.lagradost.cloudstream3.utils.DataStoreHelper.selectedKeyIndex || chosen.lockPin != null) {
                            com.lagradost.desktop.core.AppVms.get<com.lagradost.cloudstream3.ui.account.AccountViewModel>().handleAccountSelect(chosen, com.lagradost.desktop.DesktopBootstrap.activity, true)
                        }
                    } else if (!skip && all.size > 1) {
                        // after the start screen
                        com.lagradost.desktop.ui.Startup.revealed.await()
                        // the last profile may be locked: the app waits behind the picker on an open one (or behind the lock cover when every profile has a PIN)
                        com.lagradost.desktop.ui.shell.PinLock.leaveLocked()
                        com.lagradost.desktop.ui.shell.showAccountPicker(forStartup = true)
                    } else {
                        // one profile (or the picker switched off) and it has a PIN: the PIN is asked before anything shows; "Other profile" opens the
                        // picker, where a new profile can be added
                        val current = all.firstOrNull { it.keyIndex == com.lagradost.cloudstream3.utils.DataStoreHelper.selectedKeyIndex }
                        if (current?.lockPin != null) {
                            com.lagradost.desktop.ui.Startup.revealed.await()
                            com.lagradost.desktop.core.AppVms.get<com.lagradost.cloudstream3.ui.account.AccountViewModel>().handleAccountSelect(current, com.lagradost.desktop.DesktopBootstrap.activity, true)
                        }
                    }
                }
            }
            val density = LocalDensity.current
            Box(
                Modifier
                    .fillMaxSize()
                    // where the keyboard focus is (for the Up and Down keys, see NativeKeys.onKey)
                    .then(@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class) Modifier.onFocusedBoundsChanged { NativeKeys.focused = it })
                    .onSizeChanged { DisplaySync.sync(density.density, density.fontScale, it.width, it.height) }
                    .pointerInput(Unit) {
                        // pointer use hides the keyboard focus rings
                        awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                if (e.type == PointerEventType.Press || e.type == PointerEventType.Scroll) {
                                    InputMode.lastInputAt = System.currentTimeMillis()
                                    com.lagradost.desktop.platform.Graphics.markUsed()
                                }
                                if (e.type == PointerEventType.Press) {
                                    InputMode.keyboard = false
                                    InputMode.touchscreen = e.changes.any { it.type == androidx.compose.ui.input.pointer.PointerType.Touch } ||
                                        com.lagradost.desktop.platform.WindowsTouch.lastPrimaryWasTouch
                                }
                                // the pointer is over the app, not over a caption button
                                if (e.type == PointerEventType.Move) com.lagradost.desktop.platform.WinChrome.hover = 0
                            }
                        }
                    },
            ) {
                androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                    // a window that is not maximized has its title bar above the app, not over it
                    ExternalTitleBar()
                    // clipped: the ambient artwork behind the pages is drawn larger than the window and spilled over the title bar (a colour fade in it)
                    Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                        com.lagradost.desktop.ui.fluent.ScaledContent {
                            com.lagradost.desktop.ui.StartupReveal {
                                AppShell()
                            }
                            // a profile with a PIN that nobody unlocked yet: its pages stay hidden (under the PIN pad and the profile picker)
                            com.lagradost.desktop.ui.shell.LockCover()
                            // the page dims for a moment before a video page takes its place (see Navigator.go)
                            run {
                                val entering = com.lagradost.desktop.core.Navigator.enteringPlayer
                                val dim by androidx.compose.animation.core.animateFloatAsState(
                                    if (entering) 1f else 0f,
                                    if (entering) androidx.compose.animation.core.tween(com.lagradost.desktop.ui.fluent.FluentMotion.ms(com.lagradost.desktop.core.Navigator.ENTER_PLAYER_MS).coerceAtLeast(1), easing = androidx.compose.animation.core.LinearOutSlowInEasing) else androidx.compose.animation.core.snap(),
                                )
                                if (dim > 0f) Box(Modifier.fillMaxSize().background((if (com.lagradost.desktop.ui.fluent.Fluent.colors.dark) com.lagradost.desktop.ui.fluent.Fluent.colors.bg else androidx.compose.ui.graphics.Color(0xFF0E0F13)).copy(alpha = dim)))
                            }
                            // the native video player draws these in its controls window (this layer is behind the video then)
                            if (!com.lagradost.desktop.ui.screens.player.NativeVideo.overlayActive) {
                                com.lagradost.desktop.ui.FluentRequestDialogs()
                                LegacyOverlays()
                                // above the engine's Android dialogs: a prompt raised by an extension's settings sheet must be seen
                                com.lagradost.desktop.ui.fluent.DialogLayer()
                            }
                        }
                    }
                }
                // a maximized or fullscreen window shows it over the app while the pointer is at the top edge
                if (!com.lagradost.desktop.ui.screens.player.NativeVideo.overlayActive) {
                    RevealedTitleBar()
                }
                com.lagradost.desktop.ui.DropHint()
                com.lagradost.desktop.ui.fluent.DimmerLayer()
                // Linux: the edges of the frameless window resize it
                PortableResizeEdges()
            }
        }
    }
}

/** Window wide shortcuts of the native UI */
object NativeKeys {
    fun onPreviewKey(e: KeyEvent): Boolean {
        com.lagradost.desktop.ui.fluent.InputMode.lastInputAt = System.currentTimeMillis()
        // Space is held to play at 2x: the player needs to see it let go
        if (e.type == KeyEventType.KeyUp && e.key == Key.Spacebar) return playerKey(e)
        if (e.type != KeyEventType.KeyDown) return false
        when (e.key) {
            Key.Tab, Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> InputMode.keyboard = true
        }
        return when {
            // Esc closes the top dialog, else leaves full screen (whatever has the keyboard focus)
            e.key == Key.Escape && ShellState.homeSearchOpen -> { ShellState.homeSearchOpen = false; true }
            e.key == Key.Escape -> Overlays.onEscape() || com.lagradost.desktop.ui.DesktopUiHost.closeTopAndroidDialog() || run {
                val host = com.lagradost.desktop.runtime.AndroidRuntime.host
                if (host.isFullscreen()) { host.setFullscreen(false); true } else false
            } || playerKey(e)
            e.isAltPressed && e.key == Key.DirectionLeft -> Navigator.back()
            e.isCtrlPressed && (e.key == Key.K || e.key == Key.E) -> {
                // the floating bar look has no search box in its bar: on Home the one of the extension shown there opens
                if (Navigator.current.route is com.lagradost.desktop.core.Route.Home && (com.lagradost.desktop.ui.fluent.Appearance.navPosition == com.lagradost.desktop.ui.fluent.NavPosition.Floating || com.lagradost.desktop.ui.fluent.Skins.spec != null)) ShellState.homeSearchOpen = true
                runCatching { ShellState.searchFocus.requestFocus() }
                true
            }
            e.key == Key.F11 -> {
                val host = com.lagradost.desktop.runtime.AndroidRuntime.host
                host.setFullscreen(!host.isFullscreen())
                true
            }
            else -> playerKey(e)
        }
    }

    @Volatile var focusManager: androidx.compose.ui.focus.FocusManager? = null

    /**
     * An arrow key that nothing on the page used (a text box, a slider and the player use theirs) moves the focus to the nearest card or button that way, like
     * on a TV: on the desktop Compose moves the focus with Tab only, so the arrows did nothing on Home. Lazy rows scroll the focused card into view.
     */
    fun onKey(e: KeyEvent): Boolean {
        if (e.type != KeyEventType.KeyDown || e.isAltPressed || e.isCtrlPressed) return false
        if (Navigator.current.route is com.lagradost.desktop.core.Route.Player) return false
        val direction = when (e.key) {
            Key.DirectionLeft -> androidx.compose.ui.focus.FocusDirection.Left
            Key.DirectionRight -> androidx.compose.ui.focus.FocusDirection.Right
            Key.DirectionUp -> androidx.compose.ui.focus.FocusDirection.Up
            Key.DirectionDown -> androidx.compose.ui.focus.FocusDirection.Down
            else -> return false
        }
        val fm = focusManager ?: return false
        if (runCatching { fm.moveFocus(direction) }.getOrDefault(false)) return true
        // Down from the top bar found nothing (Compose's search does not leave it): the focus goes along the Tab order until it is below (or above) where it was
        val down = direction == androidx.compose.ui.focus.FocusDirection.Down
        if (!down && direction != androidx.compose.ui.focus.FocusDirection.Up) return false
        val start = focusedRect() ?: return false
        val step = if (down) androidx.compose.ui.focus.FocusDirection.Next else androidx.compose.ui.focus.FocusDirection.Previous
        var moved = 0
        while (moved < 40 && runCatching { fm.moveFocus(step) }.getOrDefault(false)) {
            moved++
            val r = focusedRect() ?: break
            if (if (down) r.top >= start.bottom - 1f else r.bottom <= start.top + 1f) return true
        }
        // nothing that way: the focus goes back where it was
        val back = if (down) androidx.compose.ui.focus.FocusDirection.Previous else androidx.compose.ui.focus.FocusDirection.Next
        repeat(moved) { runCatching { fm.moveFocus(back) } }
        return false
    }

    /** The focused element (reported by the window's root) */
    @Volatile var focused: androidx.compose.ui.layout.LayoutCoordinates? = null

    private fun focusedRect(): androidx.compose.ui.geometry.Rect? = focused?.takeIf { it.isAttached }?.let { it.boundsInWindow() }

    /** The player's shortcuts work for the whole window, whatever has the focus (nothing while a dialog is open) */
    private fun playerKey(e: KeyEvent): Boolean = !e.isAltPressed && com.lagradost.desktop.ui.screens.player.PlayerKeys.handler?.invoke(e) == true
}

/** Deep links (`cloudstreamapp://`, `cloudstreamrepo://`, `cloudstreamsearch://`, `https://cs.repo/`, `csshare:`) */
object NativeLinks {
    fun open(link: String) {
        val activity = DesktopBootstrap.activityOrNull() ?: return
        try {
            MainActivity.handleAppIntentUrl(activity, link, false, null)
        } catch (t: Throwable) {
            android.util.Log.e("NativeLinks", "link $link", t)
        }
    }
}

private fun paintBackground(window: java.awt.Window, argb: Int) {
    val color = java.awt.Color(argb)
    // the window itself and its content pane only: what shows through where the picture does not reach
    window.background = color
    (window as? javax.swing.RootPaneContainer)?.contentPane?.background = color
    // and the native class brush, which is what Windows erases the window with (white by default) when a child window appears over a part of it
    if (System.getProperty("cloudstream.noclassbrush") == null) com.lagradost.desktop.ui.screens.player.NativeVideo.paintClassBackground(window, argb)
}

/**
 * Looks at the picture of the window against the window. The picture is always a little smaller than the client area of the frame (the invisible
 * resize frame: 21 x 10 px measured at 150 %), so only a clear difference counts: a stale canvas, as in a bug report of the page drawn at the size
 * the window had when it opened, in the top left of a window that was then made bigger (the saved window was small and maximized: the rest of the
 * screen stayed white). In the first 20 s, while the window opens and is made big, it looks every 150 ms and needs 0.6 s of difference; later every
 * half second and 2 s. Then the window is laid out again; if that does not help its frame messages are sent again; then a maximized window is made
 * normal and maximized again (a window that is not maximized is made a pixel wider and back). Never in full screen or the small window.
 */
private suspend fun watchSceneSize(window: java.awt.Window, scene: () -> androidx.compose.ui.unit.IntSize) {
    val chrome = com.lagradost.desktop.platform.WinChrome
    val opened = System.currentTimeMillis()
    var badPolls = 0
    var heals = 0
    while (true) {
        val early = System.currentTimeMillis() - opened < 20_000
        delay(if (early) 150 else 500)
        val native = chrome.clientSize()
        val size = scene()
        val off = native != null && size.width > 0 && size.height > 0 && !chrome.fullscreen && !chrome.pip &&
            (kotlin.math.abs(native[0] - size.width) > maxOf(48, native[0] / 25) || kotlin.math.abs(native[1] - size.height) > maxOf(48, native[1] / 25))
        if (!off) { badPolls = 0; heals = 0; continue }
        // while the start screen is up (and for 15 s at most) a weak PC is still making its window big and laying it out: the picture is behind the
        // window for seconds without being stale, and laying out or maximizing the window again then showed as flicker. Wait much longer first.
        val starting = !com.lagradost.desktop.ui.Startup.revealed.isCompleted && System.currentTimeMillis() - opened < 15_000
        if (++badPolls < (if (starting) 12 else 4)) continue
        badPolls = 0
        heals++
        android.util.Log.w("NativeApp", "the picture is ${size.width}x${size.height} but the window is ${native!![0]}x${native[1]}: laying it out again (#$heals, maximized=${chrome.maximized})")
        java.awt.EventQueue.invokeLater {
            runCatching {
                if (heals >= 2) chrome.resendFrame()
                window.invalidate(); window.validate(); window.repaint()
            }
        }
        if (heals >= 3) {
            val state = com.lagradost.desktop.ui.DesktopUiHost.windowState
            if (state != null && state.placement == androidx.compose.ui.window.WindowPlacement.Maximized) {
                // the window is maximized and its picture is not: made normal and maximized again, which lays everything out as a new maximize does
                state.placement = androidx.compose.ui.window.WindowPlacement.Floating
                delay(250)
                state.placement = androidx.compose.ui.window.WindowPlacement.Maximized
            } else if (!chrome.maximized) {
                java.awt.EventQueue.invokeLater {
                    runCatching { val w = window.width; val h = window.height; window.setSize(w + 1, h); window.setSize(w, h) }
                }
            }
        }
    }
}
