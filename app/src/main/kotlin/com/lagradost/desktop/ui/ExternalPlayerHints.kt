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
}
