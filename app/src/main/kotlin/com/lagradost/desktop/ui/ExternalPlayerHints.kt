package com.lagradost.desktop.ui

import com.lagradost.desktop.DesktopPlatform
import com.lagradost.desktop.player.ExternalPlayers
import com.lagradost.desktop.ui.fluent.Overlays
import java.awt.EventQueue

/** Dialogs of the external player feature */
object ExternalPlayerHints {
    /** VLC is not installed: say so and offer its download page */
    fun vlcMissing() {
        EventQueue.invokeLater {
            Overlays.message(
                "VLC was not found",
                "Install VLC (videolan.org) and try again, or choose another player.",
                primary = "Get VLC",
                onPrimary = { DesktopPlatform.openExternalBrowser(ExternalPlayers.VLC_PAGE) },
            )
        }
    }

    /** mpv is not installed (or not where it is looked for): say how to get it, and offer to point the app at mpv.exe */
    fun mpvMissing() {
        EventQueue.invokeLater {
            Overlays.show(
                Overlays.Dialog(
                    "mpv was not found", primary = "Get mpv", secondary = "Choose mpv.exe…", close = "Close",
                    onPrimary = { DesktopPlatform.openExternalBrowser(ExternalPlayers.MPV_PAGE) },
                    onSecondary = { chooseMpv() },
                ) {
                    com.lagradost.desktop.ui.fluent.FText(
                        "Install mpv (mpv.io, or winget install mpv, or scoop install mpv) and try again, or tell the app where mpv.exe is. Settings > Player > External mpv does the same.",
                        color = com.lagradost.desktop.ui.fluent.Fluent.colors.textSecondary,
                    )
                },
            )
        }
    }

    /** A file chooser for mpv.exe (or mpvnet.exe); the choice is kept. Returns whether one was chosen. */
    fun chooseMpv(): Boolean {
        val dialog = java.awt.FileDialog(DesktopUiHost.window, "Choose mpv.exe", java.awt.FileDialog.LOAD)
        dialog.setFilenameFilter { _, n -> n.lowercase().endsWith(".exe") }
        dialog.isVisible = true
        val file = dialog.file?.let { java.io.File(dialog.directory, it) }?.takeIf { it.isFile } ?: return false
        ExternalPlayers.setMpvPath(file.absolutePath)
        return true
    }
}
