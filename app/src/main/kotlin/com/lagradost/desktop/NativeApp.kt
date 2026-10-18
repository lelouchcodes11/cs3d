package com.lagradost.desktop

import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import com.lagradost.desktop.ui.shell.ExternalTitleBar
import com.lagradost.desktop.ui.shell.RevealedTitleBar
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
        icon = painterResource(R.mipmap.ic_launcher),
        onPreviewKeyEvent = NativeKeys::onPreviewKey,
    ) {
        DesktopUiHost.window = window
        remember { com.lagradost.desktop.platform.StartupProfile.mark("window content composing") }
        // the rest of the extensions load once the first frame is up (see PluginLoadGate)
        LaunchedEffect(Unit) { delay(700); com.lagradost.cloudstream3.plugins.PluginLoadGate.firstFrame.complete(Unit) }
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
                    if (!skip && com.lagradost.cloudstream3.utils.DataStoreHelper.getAccounts(com.lagradost.desktop.DesktopBootstrap.activity).size > 1) {
                        // after the start screen
                        com.lagradost.desktop.ui.Startup.revealed.await()
                        com.lagradost.desktop.ui.shell.showAccountPicker(forStartup = true)
                    }
                }
            }
            val density = LocalDensity.current
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { DisplaySync.sync(density.density, density.fontScale, it.width, it.height) }
                    .pointerInput(Unit) {
                        // pointer use hides the keyboard focus rings
                        awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                if (e.type == PointerEventType.Press) InputMode.keyboard = false
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
            }
        }
    }
}

/** Window wide shortcuts of the native UI */
object NativeKeys {
    fun onPreviewKey(e: KeyEvent): Boolean {
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
                if (Navigator.current.route is com.lagradost.desktop.core.Route.Home && com.lagradost.desktop.ui.fluent.Appearance.navPosition == com.lagradost.desktop.ui.fluent.NavPosition.Floating) ShellState.homeSearchOpen = true
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
        if (++badPolls < 4) continue
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
