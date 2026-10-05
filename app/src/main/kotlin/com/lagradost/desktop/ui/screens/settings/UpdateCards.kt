package com.lagradost.desktop.ui.screens.settings

import androidx.compose.runtime.Composable
import com.lagradost.desktop.AppInfo
import com.lagradost.desktop.DesktopPlatform

/** "Get the newest version" of the About page: the app does not update itself, GitHub is where the new installer is */
@Composable
fun UpdateCards() {
    SettingsCard(
        "Get the newest version",
        "New versions are published on GitHub. This opens the releases page in your browser, download the newest installer there and run it.",
        onClick = { DesktopPlatform.openExternalBrowser(AppInfo.LATEST_RELEASE_URL) },
    )
}
