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

    /** Energy Media Player is not installed: it is a Microsoft Store app, so the Store page opens */
    fun energyMissing() {
        EventQueue.invokeLater {
            Overlays.message(
                "Energy Media Player was not found",
                "Install Energy Media Player (free) or Energy Media Player for Windows (paid) from the Microsoft Store and try again, or choose another player. Either one works.",
                primary = "Open the Store",
                onPrimary = { DesktopPlatform.openExternalBrowser(ExternalPlayers.ENERGY_STORE) },
            )
        }
    }

    /**
     * The source needs headers (a referer, cookies) that Energy can not send, so the app's own server sends them; Windows keeps Store apps away from
     * servers on the same PC until they are allowed once (an admin prompt). [retry] opens the video again after that.
     */
    fun energyNeedsLoopback(retry: (() -> Unit)? = null) {
        EventQueue.invokeLater {
            Overlays.message(
                "Allow Energy to play this source",
                "This source only plays with extra information the app adds to each request. Energy gets it through the app, and Windows keeps Store apps from talking to other apps on this PC until you allow it once. Windows will ask for permission (admin). Sources without this need play in Energy straight away.",
                primary = "Allow Energy",
                onPrimary = {
                    Thread {
                        if (!ExternalPlayers.allowEnergyLoopback()) return@Thread
                        // the admin prompt is answered in its own window: wait for the change to show up, then open the video again
                        repeat(60) {
                            Thread.sleep(1000)
                            if (ExternalPlayers.energyLoopbackAllowed()) { retry?.let { EventQueue.invokeLater(it) }; return@Thread }
                        }
                    }.apply { isDaemon = true }.start()
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
