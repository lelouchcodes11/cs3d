package com.lagradost.desktop.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.utils.BackupUtils
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.platform.ResetApp
import com.lagradost.desktop.ui.Toasts
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.fluent.TextBox
import com.lagradost.desktop.ui.fluent.ToggleSwitch
import kotlinx.coroutines.runBlocking

/** The word that has to be typed before everything is erased */
private const val ERASE_WORD = "ERASE"

private fun refused() = Toasts.show("Close the other CloudStream windows first: a reset needs the app's data folder to itself.", true)

/** Settings > Updates & backup > Reset settings */
internal fun showResetSettings() {
    Overlays.show(
        Overlays.Dialog(
            title = "Reset settings", primary = "Reset settings", close = "Cancel", width = 480.dp,
            onPrimary = { if (!ResetApp.request(ResetApp.Scope.Settings)) refused() },
        ) {
            FText(
                "The look, player, subtitle, performance and security settings go back to their defaults. Your profiles, library, watch history, accounts and extensions stay. A copy of the current settings is kept in the data folder (backups). The app restarts.",
                color = Fluent.colors.textSecondary,
            )
        },
    )
}

/** Settings > Updates & backup > Erase all data and reset the app */
internal fun showEraseEverything() {
    var typed by mutableStateOf("")
    var backupFirst by mutableStateOf(true)
    Overlays.show(
        Overlays.Dialog(
            title = "Erase all data and reset the app?", primary = "Erase everything", close = "Cancel", width = 520.dp,
            primaryEnabled = { typed.trim() == ERASE_WORD },
            onPrimary = {
                val save = backupFirst
                Thread({
                    if (save) runCatching { BackupUtils.backup(DesktopBootstrap.activity).let { job -> runBlocking { job.join() } } }
                    if (!ResetApp.request(ResetApp.Scope.Everything)) refused()
                }, "reset-app").apply { isDaemon = true }.start()
            },
        ) {
            val c = Fluent.colors
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                FText(
                    "This deletes every profile, sign-in, library entry, watch position, extension, repository, cache and setting, and starts the app like a first install. Downloaded videos in your download folder are not deleted. It cannot be undone.",
                    color = c.textSecondary,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FText("Save a backup file first (in your backup folder)", modifier = Modifier.weight(1f))
                    ToggleSwitch(backupFirst, { backupFirst = it })
                }
                TextBox(typed, { typed = it.take(16) }, Modifier.fillMaxWidth(), placeholder = "Type $ERASE_WORD to confirm")
            }
        },
    )
}
