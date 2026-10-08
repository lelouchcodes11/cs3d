package com.lagradost.desktop.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
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

/** The window behind Settings > Updates & backup > Extension update history */
fun showPluginUpdateHistory() {
    val entries = com.lagradost.desktop.platform.PluginUpdateLog.all()
    com.lagradost.desktop.ui.fluent.Overlays.show(
        com.lagradost.desktop.ui.fluent.Overlays.Dialog(
            title = "Extension update history", close = "Close", width = 520.dp,
            secondary = if (entries.isNotEmpty()) "Clear" else null, onSecondary = { com.lagradost.desktop.platform.PluginUpdateLog.clear() },
        ) {
            val c = Fluent.colors
            if (entries.isEmpty()) FText("No extension has been updated yet. Updates found by the automatic check at start, or by Update Plugins, are listed here.", color = c.textSecondary)
            else androidx.compose.foundation.layout.Column(
                Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                for (e in entries) androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    FText(e.name + if (e.version > 0) "  v${e.version}" else "", Modifier.weight(1f), style = Fluent.type.bodyStrong, maxLines = 1)
                    FText(fmt.format(Date(e.at)), color = c.textSecondary, style = Fluent.type.caption)
                }
            }
        },
    )
}
