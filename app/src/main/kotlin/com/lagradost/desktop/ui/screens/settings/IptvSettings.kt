package com.lagradost.desktop.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.iptv.IptvPlaylist
import com.lagradost.desktop.iptv.IptvPlaylists
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.fluent.TextBox
import com.lagradost.desktop.ui.fluent.ToggleSwitch
import com.lagradost.desktop.ui.fluent.cardSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings > Stremio add-ons, TV playlists and torrents: the IPTV playlists, each a source of its own (see [IptvPlaylists]) */
@Composable
internal fun IptvCards() {
    IptvPlaylists.load()
    AddPlaylistCard()
    for (p in IptvPlaylists.playlists.toList()) PlaylistCard(p)
}

@Composable
private fun AddPlaylistCard() {
    val c = Fluent.colors
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    fun add(address: String) {
        if (busy || address.isBlank()) return
        busy = true
        note = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { IptvPlaylists.add(address) }
            busy = false
            r.onSuccess { (p, n) -> text = ""; note = "Added ${p.name}: $n channels. Pick it as the source on Home." to true }
                .onFailure { note = (it.message ?: "Could not add it") to false }
        }
    }
    fun pickFile() {
        val dialog = java.awt.FileDialog(com.lagradost.desktop.ui.DesktopUiHost.window, "Choose a playlist file", java.awt.FileDialog.LOAD)
        dialog.setFilenameFilter { _, n -> n.lowercase().let { it.endsWith(".m3u") || it.endsWith(".m3u8") } }
        dialog.isVisible = true
        dialog.file?.let { add(java.io.File(dialog.directory, it).absolutePath) }
    }
    Column(Modifier.fillMaxWidth().cardSurface(RoundedCornerShape(FluentShapes.card)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FText("Add a TV playlist", style = Fluent.type.body)
        FText("Paste an M3U playlist address, or the Xtream link of your IPTV service (…/get.php?username=…&password=…). Its channels become a source with a TV guide.", style = Fluent.type.caption, color = c.textSecondary, maxLines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextBox(text, { text = it }, Modifier.weight(1f), placeholder = "https://…/playlist.m3u", leadingIcon = Icons.Link, height = 36.dp, onSubmit = { add(text) })
            Button(if (busy) "Adding…" else "Add", { add(text) }, kind = ButtonKind.Accent, icon = Icons.Add, height = 36.dp, enabled = !busy && text.isNotBlank())
            Button("File…", { pickFile() }, icon = Icons.Folder, height = 36.dp, enabled = !busy)
        }
        note?.let { (message, ok) -> FText(message, style = Fluent.type.caption, color = if (ok) c.success else c.critical, maxLines = 3) }
    }
}

@Composable
private fun PlaylistCard(p: IptvPlaylist) {
    val c = Fluent.colors
    Row(
        Modifier.fillMaxWidth().cardSurface(RoundedCornerShape(FluentShapes.card)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FText(p.name, style = Fluent.type.bodyStrong, maxLines = 1)
            FText(if (p.xtream) "Xtream login · ${p.user}" else p.url, style = Fluent.type.caption, color = c.textSecondary, maxLines = 1)
        }
        Button("Remove", {
            Overlays.show(
                Overlays.Dialog(title = "Remove ${p.name}?", primary = "Remove", close = "Keep", onPrimary = { IptvPlaylists.remove(p.id) }) {
                    FText("Its channels are no longer a source. You can add it again at any time.", color = c.textSecondary, maxLines = 3)
                },
            )
        }, kind = ButtonKind.Subtle, icon = Icons.Delete, height = 30.dp)
        ToggleSwitch(p.enabled, { IptvPlaylists.setEnabled(p.id, it) })
    }
}
