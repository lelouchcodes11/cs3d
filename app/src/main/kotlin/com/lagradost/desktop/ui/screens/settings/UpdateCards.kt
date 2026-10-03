package com.lagradost.desktop.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.ToggleSwitch
import com.lagradost.desktop.update.AppUpdater
import java.text.DateFormat
import java.util.Date

/** "Check for updates" and "Check automatically" of the About page */
@Composable
fun UpdateCards() {
    val c = Fluent.colors
    val status = AppUpdater.status
    val busy = status is AppUpdater.Status.Checking || status is AppUpdater.Status.Downloading
    val summary = when (status) {
        is AppUpdater.Status.Checking -> "Checking…"
        is AppUpdater.Status.Downloading -> "Downloading ${status.release.label}…"
        is AppUpdater.Status.Available -> "Version ${status.release.label} is available"
        is AppUpdater.Status.UpToDate -> "You have the latest version (${AppUpdater.currentVersion})"
        is AppUpdater.Status.Failed -> (if (status.download) "Update failed: " else "Could not check: ") + status.reason
        else -> AppUpdater.lastChecked.takeIf { it > 0 }?.let { "Last checked ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))}" } ?: "Looks for a newer release on GitHub"
    }
    SettingsCard("Check for updates", summary) {
        val available = status as? AppUpdater.Status.Available
        if (available != null) {
            Button("View update", { AppUpdater.showUpdateDialog(available.release) }, kind = ButtonKind.Accent)
            Box(Modifier.width(8.dp))
        }
        Button("Check now", { AppUpdater.checkNow() }, enabled = !busy)
    }
    SettingsCard("Check for updates automatically", "A few seconds after start the app asks GitHub (${AppUpdater.repo}) for the list of releases, at most every few hours. Nothing else is sent.") {
        FText(if (AppUpdater.autoCheckEnabled) "On" else "Off", color = c.textSecondary, style = Fluent.type.caption)
        Box(Modifier.width(8.dp))
        ToggleSwitch(AppUpdater.autoCheckEnabled, { AppUpdater.setAutoCheck(it) })
    }
}
