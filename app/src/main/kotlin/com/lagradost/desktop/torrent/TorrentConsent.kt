package com.lagradost.desktop.torrent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.ui.Toasts
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.fluent.ProgressBar

/**
 * The questions before a torrent plays for the first time: the notice (what a torrent is, that the viewer's address is visible to the
 * peers, the engine that is downloaded) and, when the engine is not on the disk, its download with progress.
 */
object TorrentConsent {
    const val NOTICE = "A torrent is downloaded from other people's computers (peers) while it plays, and what you have downloaded is shared with them. " +
        "The peers can see your IP address; a VPN hides it. Only watch what you are allowed to watch: what you play is your responsibility."
    const val ENGINE = "For this the app downloads a program once: TorrServer (about 60 MB, open source, github.com/YouROK/TorrServer). " +
        "It runs on this PC only while a torrent plays, and Windows may ask whether to let it through the firewall."

    fun speed(bytesPerSecond: Long): String = when {
        bytesPerSecond >= 1_000_000 -> "%.1f MB/s".format(bytesPerSecond / 1_000_000.0)
        else -> "${bytesPerSecond / 1000} KB/s"
    }

    /** Runs [onReady] when torrents can be played (after the notice and the download if they were needed), [onDecline] when the viewer says no or the download fails */
    /** The callbacks of the question that is open: a second request (the player moved to the next source meanwhile) replaces them, it does not ask again */
    private class Pending(var onReady: () -> Unit, var onDecline: () -> Unit)

    private var pending: Pending? = null

    fun request(onReady: () -> Unit, onDecline: () -> Unit = {}) {
        TorrentEngine.load()
        pending?.let { it.onReady = onReady; it.onDecline = onDecline; return }
        if (TorrentEngine.ready) { onReady(); return }
        val p = Pending(onReady, onDecline).also { pending = it }
        val ready = { pending = null; p.onReady() }
        val decline = { pending = null; p.onDecline() }
        if (TorrentEngine.enabled) download(ready, decline) else notice(ready, decline)
    }

    private fun notice(onReady: () -> Unit, onDecline: () -> Unit) {
        var answered = false
        Overlays.show(
            Overlays.Dialog(
                title = "Play torrents?",
                primary = if (TorrentEngine.installed) "Turn on" else "Turn on and download",
                close = "Not now",
                width = 520.dp,
                onPrimary = {
                    answered = true
                    TorrentEngine.chooseEnabled(true)
                    if (TorrentEngine.installed) onReady() else download(onReady, onDecline)
                },
                onClose = { if (!answered) onDecline() },
            ) {
                val c = Fluent.colors
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FText(NOTICE, color = c.text, maxLines = 8)
                    if (!TorrentEngine.installed) FText(ENGINE, color = c.textSecondary, maxLines = 8)
                    FText("You can turn this off later in Settings > Stremio & torrents.", style = Fluent.type.caption, color = c.textTertiary)
                }
            },
        )
    }

    private fun download(onReady: () -> Unit, onDecline: () -> Unit) {
        var finished = false
        TorrentEngine.install { ok ->
            // the dialog below closes itself when the file is there; on a failure it stays so the reason can be read
            if (ok) { finished = true; java.awt.EventQueue.invokeLater { onReady() } }
        }
        Overlays.show(
            Overlays.Dialog(
                title = "Downloading the torrent engine",
                primary = null,
                close = "Cancel",
                width = 480.dp,
                onClose = { if (!finished) { TorrentEngine.cancelInstall(); onDecline() } },
            ) { dismiss ->
                val c = Fluent.colors
                val progress = TorrentEngine.installProgress
                val error = TorrentEngine.installError
                LaunchedEffect(TorrentEngine.installed, progress) { if (TorrentEngine.installed && progress == null && error == null) dismiss() }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    if (error != null) {
                        FText("The download failed: $error", color = c.critical, maxLines = 4)
                        FText("Check the connection and try again from Settings > Stremio & torrents.", style = Fluent.type.caption, color = c.textSecondary)
                    } else {
                        ProgressBar(progress)
                        FText(if (progress == null) "Starting…" else "${(progress * 100).toInt()} %", style = Fluent.type.caption, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            },
        )
        // a failure of the download is told once, and closes nothing (the dialog shows it); a cancel declines
        if (TorrentEngine.installError != null) Toasts.show("The torrent engine could not be downloaded", true)
    }
}
