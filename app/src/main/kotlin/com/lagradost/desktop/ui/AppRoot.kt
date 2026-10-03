package com.lagradost.desktop.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.lagradost.cloudstream4.compose.DeviceLayout
import com.lagradost.cloudstream4.compose.EMULATOR
import com.lagradost.cloudstream4.compose.PHONE
import com.lagradost.cloudstream4.compose.TV
import com.lagradost.cloudstream4.rememberAppSettings
import com.lagradost.cloudstream4.theme.CloudStreamTheme
import com.lagradost.cloudstream4.theme.perfToColor
import com.lagradost.cloudstream4.theme.perfToMode
import com.lagradost.desktop.ActivityStack
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.runtime.ui.ThemeBridge
import com.mihon.presentation.LocalBackPress
import com.mihon.presentation.settings.collectAsState

@Composable
private fun PopupLayer() {
    for (popup in DesktopUiHost.popups) {
        Popup(
            alignment = Alignment.TopStart,
            offset = IntOffset(popup.x, popup.y),
            onDismissRequest = {
                DesktopUiHost.popups.remove(popup)
                popup.onDismiss()
            },
            properties = PopupProperties(focusable = true),
        ) {
            com.lagradost.desktop.runtime.ui.AndroidViewHost(popup.content)
        }
    }
}
@Composable
fun LegacyOverlays() {
    val top = ActivityStack.activities.lastOrNull() ?: DesktopBootstrap.activityOrNull() ?: return
    val settings = rememberAppSettings()
    val mode by settings.ui.theme.collectAsState()
    val primaryColor by settings.ui.primaryColor.collectAsState()
    val providers = buildList {
        add(LocalContext provides top)
        add(LocalActivity provides top)
        (top as? ViewModelStoreOwner)?.let { add(LocalViewModelStoreOwner provides it) }
        add(LocalBackPress provides { BackHandlers.dispatch() })
        add(DeviceLayout.LocalLayout provides PHONE)
    }
    CompositionLocalProvider(*providers.toTypedArray()) {
        com.lagradost.desktop.ui.fluent.FluentMaterialTheme {
            Box(Modifier.fillMaxSize()) {
                val background = MaterialTheme.colorScheme.background
                for (activity in ActivityStack.activities) {
                    key(activity) {
                        Box(Modifier.fillMaxSize().background(background).pointerInput(Unit) {}) {
                            com.lagradost.desktop.runtime.ui.AndroidViewHost(activity.getWindow().getDecorView(), Modifier.fillMaxSize())
                        }
                    }
                }
                SnackbarHost()
                DesktopUiHost.androidDialogs.toList().forEach { AndroidDialogHost(it) }
                PopupLayer()
            }
        }
    }
}

/**
 * Native UI: hosts content written for the old Compose settings/theme (Material 3 + Android Context
 * locals), such as the upstream preference screens and custom preference widgets.
 */
@Composable
fun LegacyContent(content: @Composable () -> Unit) {
    val top = DesktopBootstrap.activityOrNull() ?: return
    val settings = rememberAppSettings()
    val mode by settings.ui.theme.collectAsState()
    val primaryColor by settings.ui.primaryColor.collectAsState()
    val providers = buildList {
        add(LocalContext provides top)
        add(LocalActivity provides top)
        (top as? ViewModelStoreOwner)?.let { add(LocalViewModelStoreOwner provides it) }
        add(LocalBackPress provides { com.lagradost.desktop.core.Navigator.back() })
        add(DeviceLayout.LocalLayout provides PHONE)
    }
    CompositionLocalProvider(*providers.toTypedArray()) {
        com.lagradost.desktop.ui.fluent.FluentMaterialTheme {
            Surface(Modifier, color = Color.Transparent) { content() }
        }
    }
}
