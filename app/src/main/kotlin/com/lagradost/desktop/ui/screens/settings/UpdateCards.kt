package com.lagradost.desktop.ui.screens.settings

import androidx.compose.foundation.layout.Box
import com.lagradost.desktop.ui.fluent.tabletVerticalScroll as verticalScroll
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

/** The updater of the About page: check, download with progress, restart and update; and the automatic check at start */
@Composable
fun UpdateCards() {
    val c = Fluent.colors
    val status = UpdateCheck.status
    val busy = status is UpdateCheck.Status.Checking || status is UpdateCheck.Status.Downloading || status is UpdateCheck.Status.Installing
    val summary = when (status) {
        is UpdateCheck.Status.Checking -> "Checking…"
        is UpdateCheck.Status.Available -> "Version ${status.release.label} is available (you have ${UpdateCheck.currentVersion})"
        is UpdateCheck.Status.Downloading -> "Downloading version ${status.release.label}: " + (if (status.total > 0) "${(100L * status.done / status.total).coerceIn(0L, 100L)} %" else "%.1f MB".format(status.done / 1_048_576.0))
        is UpdateCheck.Status.Ready -> "Version ${status.release.label} is downloaded and checked: restart to finish updating"
        is UpdateCheck.Status.Installing -> "Updating: the app closes and opens again in a moment"
        is UpdateCheck.Status.UpToDate -> "You have the latest version (${UpdateCheck.currentVersion})"
        is UpdateCheck.Status.Failed -> "Could not check: ${status.reason}"
        else -> UpdateCheck.lastChecked.takeIf { it > 0 }?.let { "Version ${UpdateCheck.currentVersion}. Last checked ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))}" } ?: "Version ${UpdateCheck.currentVersion}. Looks for a newer release on GitHub"
    }
    SettingsCard("Check for updates", summary) {
        when (status) {
            is UpdateCheck.Status.Available -> {
                val canInstall = UpdateCheck.canInstall(status.release)
                if (canInstall) {
                    Button("Update now", { UpdateCheck.showUpdateDialog(status.release) }, kind = ButtonKind.Accent)
                    Box(Modifier.width(8.dp))
                    Button("Download in browser", { UpdateCheck.downloadInBrowser(status.release) })
                    Box(Modifier.width(8.dp))
                } else {
                    Button("Download in browser", { UpdateCheck.downloadInBrowser(status.release) }, kind = ButtonKind.Accent)
                    Box(Modifier.width(8.dp))
                }
            }
            is UpdateCheck.Status.Ready -> {
                Button("Restart and update", { UpdateCheck.showReadyDialog(status.release, status.file) }, kind = ButtonKind.Accent)
                Box(Modifier.width(8.dp))
                Button("Download in browser", { UpdateCheck.downloadInBrowser(status.release) })
                Box(Modifier.width(8.dp))
            }
            is UpdateCheck.Status.Downloading -> {
                Button("Show", { UpdateCheck.showUpdateDialog(status.release) })
                Box(Modifier.width(8.dp))
                Button("Download in browser", { UpdateCheck.cancelDownload(); UpdateCheck.downloadInBrowser(status.release) })
                Box(Modifier.width(8.dp))
                Button("Cancel", { UpdateCheck.cancelDownload() })
                Box(Modifier.width(8.dp))
            }
            else -> {}
        }
        Button("Check now", { UpdateCheck.checkNow() }, enabled = !busy)
    }
    SettingsCard("Check for updates automatically", "A few seconds after the app is up, and again every few hours while it stays open (never during a video), it asks GitHub (${AppInfo.REPO}) for the list of releases and tells you when there is a newer one. Nothing is installed without your click, and nothing else is sent.") {
        FText(if (UpdateCheck.autoCheckEnabled) "On" else "Off", color = c.textSecondary, style = Fluent.type.caption)
        Box(Modifier.width(8.dp))
        ToggleSwitch(UpdateCheck.autoCheckEnabled, { UpdateCheck.setAutoCheck(it) })
    }
    SettingsCard(
        "All releases",
        "The releases page on GitHub: every version with its notes and files, for a manual download.",
        onClick = { DesktopPlatform.openExternalBrowser(AppInfo.RELEASES_URL) },
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
