package com.lagradost.desktop

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
        // a newer release of this app: asked for a few seconds after the start (off with "Check for updates automatically")
        remember { com.lagradost.desktop.update.AppUpdater.startAutoCheck() }
        remember {
            window.addWindowFocusListener(object : java.awt.event.WindowAdapter() {
                override fun windowGainedFocus(e: java.awt.event.WindowEvent) = com.lagradost.desktop.DesktopLifecycle.windowFocus(true)
                override fun windowLostFocus(e: java.awt.event.WindowEvent) = com.lagradost.desktop.DesktopLifecycle.windowFocus(false)
            })
        }
        DesktopUiHost.windowState = state
        remember { window.minimumSize = Dimension(760, 520) }
        remember { com.lagradost.desktop.platform.AppIcon.install(window) }
        remember { FluentSettings.load(); com.lagradost.desktop.ui.fluent.Appearance.load() }
        FluentTheme {
            val c = Fluent.colors
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
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        com.lagradost.desktop.ui.fluent.ScaledContent {
                            com.lagradost.desktop.ui.StartupReveal {
                                AppShell()
                            }
                            com.lagradost.desktop.ui.FluentRequestDialogs()
                            LegacyOverlays()
                            // above the engine's Android dialogs: a prompt raised by an extension's settings sheet must be seen
                            com.lagradost.desktop.ui.fluent.DialogLayer()
                        }
                    }
                }
                // a maximized window shows it over the app while the pointer is at the top edge
                RevealedTitleBar()
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
            e.key == Key.Escape -> Overlays.onEscape() || com.lagradost.desktop.ui.DesktopUiHost.closeTopAndroidDialog() || run {
                val host = com.lagradost.desktop.runtime.AndroidRuntime.host
                if (host.isFullscreen()) { host.setFullscreen(false); true } else false
            } || playerKey(e)
            e.isAltPressed && e.key == Key.DirectionLeft -> Navigator.back()
            e.isCtrlPressed && (e.key == Key.K || e.key == Key.E) -> {
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
