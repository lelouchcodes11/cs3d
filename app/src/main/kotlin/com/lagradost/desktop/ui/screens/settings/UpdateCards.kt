package com.lagradost.desktop.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.AppInfo
import com.lagradost.desktop.DesktopPlatform
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.ToggleSwitch
import com.lagradost.desktop.update.UpdateCheck
import java.text.DateFormat
import java.util.Date

/** "Check for updates" and "Check at start" of the About page: the app only looks and points to GitHub, it does not update itself */
@Composable
fun UpdateCards() {
    val c = Fluent.colors
    val status = UpdateCheck.status
    val busy = status is UpdateCheck.Status.Checking
    val summary = when (status) {
        is UpdateCheck.Status.Checking -> "Checking…"
        is UpdateCheck.Status.Available -> "Version ${status.release.label} is available"
        is UpdateCheck.Status.UpToDate -> "You have the latest version (${UpdateCheck.currentVersion})"
        is UpdateCheck.Status.Failed -> "Could not check: ${status.reason}"
        else -> UpdateCheck.lastChecked.takeIf { it > 0 }?.let { "Last checked ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))}" } ?: "Looks for a newer release on GitHub"
    }
    SettingsCard("Check for updates", summary) {
        val available = status as? UpdateCheck.Status.Available
        if (available != null) {
            Button("View update", { UpdateCheck.showUpdateDialog(available.release) }, kind = ButtonKind.Accent)
            Box(Modifier.width(8.dp))
        }
        Button("Check now", { UpdateCheck.checkNow() }, enabled = !busy)
    }
    SettingsCard("Check for updates at start", "A few seconds after the app is up (at most every few hours) it asks GitHub (${AppInfo.REPO}) for the list of releases and tells you when there is a newer one, with a button to its page. Nothing is downloaded or installed by the app, and nothing else is sent.") {
        FText(if (UpdateCheck.autoCheckEnabled) "On" else "Off", color = c.textSecondary, style = Fluent.type.caption)
        Box(Modifier.width(8.dp))
        ToggleSwitch(UpdateCheck.autoCheckEnabled, { UpdateCheck.setAutoCheck(it) })
    }
    SettingsCard(
        "Get the newest version",
        "New versions are published on GitHub. This opens the releases page in your browser, download the newest installer there and run it.",
        onClick = { DesktopPlatform.openExternalBrowser(AppInfo.LATEST_RELEASE_URL) },
    )
}
