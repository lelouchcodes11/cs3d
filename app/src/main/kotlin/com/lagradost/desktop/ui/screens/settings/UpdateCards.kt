package com.lagradost.desktop.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.AppInfo
import com.lagradost.desktop.DesktopPlatform
import com.lagradost.desktop.ui.fluent.cardSurface
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.ComboBox
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.ProgressBar
import com.lagradost.desktop.ui.fluent.ProgressRing
import com.lagradost.desktop.ui.fluent.ToggleSwitch
import com.lagradost.desktop.ui.fluent.hairBorder
import com.lagradost.desktop.update.UpdateCheck
import com.lagradost.desktop.update.UpdateUi
import androidx.compose.foundation.shape.RoundedCornerShape
import com.lagradost.desktop.ui.fluent.tabletVerticalScroll as verticalScroll
import java.text.DateFormat
import java.util.Date

/**
 * Settings > Updates: where the app stands (this version, how it was installed, when it last looked), the newest release with its notes and the
 * ways to get it, and the choices (channel, automatic checks, a skipped version).
 */
@Composable
fun UpdateCards() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StatusCard()
        OfferCard()
        PreferenceCards()
    }
}

private fun installLabel(kind: UpdateCheck.InstallKind) = when (kind) {
    UpdateCheck.InstallKind.Setup -> "Installed with the setup program"
    UpdateCheck.InstallKind.Msi -> "Installed with the MSI package"
    UpdateCheck.InstallKind.Portable -> "Portable copy"
    UpdateCheck.InstallKind.Other -> "Development run"
}

private fun installAbout(kind: UpdateCheck.InstallKind) = when (kind) {
    UpdateCheck.InstallKind.Setup -> "Update now downloads the new setup, checks it, closes the app, runs it and opens the app again. Your settings, accounts and extensions stay."
    UpdateCheck.InstallKind.Msi -> "Update now downloads the new MSI package, checks it and installs it. Your settings, accounts and extensions stay."
    UpdateCheck.InstallKind.Portable -> "Update now downloads the new portable zip, checks it and replaces the program files. The data folder next to them stays."
    UpdateCheck.InstallKind.Other -> "This is not an installed copy, so the app cannot replace itself: a new version is downloaded from the release page."
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = Fluent.colors
    val shape = RoundedCornerShape(FluentShapes.card)
    Column(modifier.fillMaxWidth().clip(shape).cardSurface(shape).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

/** The state of the app: "CloudStream 1.0.8", how it is installed, and the answer of the last check with the button for the next one */
@Composable
private fun StatusCard() {
    val c = Fluent.colors
    val status = UpdateCheck.status
    val busy = status is UpdateCheck.Status.Checking || status is UpdateCheck.Status.Downloading || status is UpdateCheck.Status.Installing
    val line = when (status) {
        is UpdateCheck.Status.Checking -> "Looking for a newer version…"
        is UpdateCheck.Status.Available -> "Version ${status.release.label} is available."
        is UpdateCheck.Status.Downloading -> "Downloading version ${status.release.label}…"
        is UpdateCheck.Status.Ready -> "Version ${status.release.label} is downloaded and checked."
        is UpdateCheck.Status.Installing -> "Updating: the app closes and opens again in a moment."
        is UpdateCheck.Status.UpToDate -> "You have the latest version."
        is UpdateCheck.Status.Failed -> "Could not check: ${status.reason}"
        else -> "Not checked in this run."
    }
    val last = UpdateCheck.lastChecked.takeIf { it > 0 }?.let { "Last checked " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) + "." }
    Panel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconBadge(52.dp) { tint -> Icon(Icons.Update, size = 24.dp, tint = tint) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FText("CloudStream ${UpdateCheck.currentVersion}", style = Fluent.type.subtitle)
                FText(installLabel(UpdateCheck.installKind) + " · " + UpdateCheck.channel.label + " channel", style = Fluent.type.caption, color = c.textSecondary)
                FText(line + (if (status !is UpdateCheck.Status.Checking && last != null) " $last" else ""), color = if (status is UpdateCheck.Status.Failed) c.critical else c.text)
            }
            if (status is UpdateCheck.Status.Checking) ProgressRing(size = 22.dp, strokeWidth = 2.5.dp)
            else Button("Check for updates", { UpdateCheck.checkNow() }, enabled = !busy)
        }
    }
}

/** The newest release when it is newer than this one: size, notes, progress, and what can be done with it */
@Composable
private fun OfferCard() {
    val c = Fluent.colors
    val status = UpdateCheck.status
    val release = when (status) {
        is UpdateCheck.Status.Available -> status.release
        is UpdateCheck.Status.Downloading -> status.release
        is UpdateCheck.Status.Ready -> status.release
        else -> return
    }
    val canInstall = UpdateCheck.canInstall(release)
    val size = UpdateCheck.sizeOf(release)
    val error = UpdateCheck.downloadError.takeIf { status is UpdateCheck.Status.Available }
    Panel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FText(
                "Version ${release.label}" + (if (release.prerelease) " (pre-release)" else "") + (size?.let { " · %.0f MB".format(it / 1_048_576.0) } ?: ""),
                style = Fluent.type.bodyStrong, modifier = Modifier.weight(1f),
            )
            if (UpdateCheck.skipped == release.tag) FText("Skipped", style = Fluent.type.caption, color = c.textSecondary)
        }
        if (release.markdown.isNotBlank()) {
            Box(Modifier.fillMaxWidth().heightIn(max = 170.dp).clip(RoundedCornerShape(FluentShapes.control)).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                UpdateUi.Markdown(release.markdown.lines().take(24).joinToString("\n"))
            }
        }
        if (status is UpdateCheck.Status.Downloading) {
            val progress = status.takeIf { it.total > 0 }?.let { (it.done.toFloat() / it.total).coerceIn(0f, 1f) }
            ProgressBar(progress)
            FText(
                if (status.total > 0) "%.1f of %.1f MB".format(status.done / 1_048_576.0, status.total / 1_048_576.0) else "%.1f MB".format(status.done / 1_048_576.0),
                style = Fluent.type.caption, color = c.textSecondary,
            )
        }
        if (error != null) FText("Could not download: $error", color = c.critical)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (status) {
                is UpdateCheck.Status.Downloading -> Button("Cancel", { UpdateCheck.cancelDownload() })
                is UpdateCheck.Status.Ready -> {
                    Button("Restart and update", { UpdateCheck.install(status.release, status.file) }, kind = ButtonKind.Accent)
                    Button("Download in browser", { UpdateCheck.downloadInBrowser(release) })
                }
                else -> {
                    if (canInstall) {
                        Button(if (error != null) "Retry" else "Update now", { UpdateCheck.startUpdate(release) }, kind = ButtonKind.Accent)
                        Button("Download in browser", { UpdateCheck.downloadInBrowser(release) })
                    } else {
                        Button("Download in browser", { UpdateCheck.downloadInBrowser(release) }, kind = ButtonKind.Accent)
                    }
                }
            }
            if (release.notes.isNotBlank()) Button("Release notes", { UpdateUi.showNotes(release) }, kind = ButtonKind.Subtle)
            Button("Release page", { DesktopPlatform.openExternalBrowser(release.pageUrl) }, kind = ButtonKind.Subtle)
            if (status is UpdateCheck.Status.Available && UpdateCheck.skipped != release.tag) Button("Skip this version", { UpdateCheck.skip(release) }, kind = ButtonKind.Subtle)
        }
        if (!canInstall && status !is UpdateCheck.Status.Downloading) FText(installAbout(UpdateCheck.installKind), style = Fluent.type.caption, color = c.textSecondary)
    }
}

@Composable
private fun PreferenceCards() {
    val c = Fluent.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsCard(
            "Update channel",
            UpdateCheck.channel.about + " Changing it looks for updates again.",
        ) {
            ComboBox(UpdateCheck.Channel.entries.toList(), UpdateCheck.channel, { it.label }, { UpdateCheck.chooseChannel(it) }, minWidth = 140.dp)
        }
        SettingsCard(
            "Check for updates automatically",
            "A few seconds after the app is up, and again every few hours while it stays open (never during a video), it asks GitHub (${AppInfo.REPO}) for the list of releases and shows a banner when a newer one is out. Nothing but that request is sent.",
        ) {
            ToggleSwitch(UpdateCheck.autoCheckEnabled, { UpdateCheck.setAutoCheck(it) })
        }
        UpdateCheck.skipped?.let { tag ->
            SettingsCard("Skipped version", "You chose to ignore $tag: no banner for it until a newer version comes out.") {
                Button("Show it again", { UpdateCheck.clearSkipped(); UpdateCheck.checkNow(quiet = true) })
            }
        }
        SettingsCard("How this copy updates", installAbout(UpdateCheck.installKind))
        SettingsCard(
            "All releases",
            "The releases page on GitHub: every version with its notes and files, for a manual download.",
            onClick = { UpdateUi.openReleases() },
        )
    }
}

/** The window behind Settings > Extensions and sources > Extension update history */
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
