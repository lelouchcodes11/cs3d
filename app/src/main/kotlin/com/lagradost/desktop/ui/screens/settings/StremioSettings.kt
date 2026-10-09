package com.lagradost.desktop.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.DesktopPlatform
import com.lagradost.desktop.stremio.StremioAddon
import com.lagradost.desktop.stremio.StremioAddons
import com.lagradost.desktop.stremio.StremioClient
import com.lagradost.desktop.torrent.TorrentConsent
import com.lagradost.desktop.torrent.TorrentEngine
import com.lagradost.desktop.ui.Toasts
import com.lagradost.desktop.ui.components.RemoteImage
import com.lagradost.desktop.ui.fluent.Badge
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.ComboBox
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.IconButton
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.fluent.ProgressBar
import com.lagradost.desktop.ui.fluent.TextBox
import com.lagradost.desktop.ui.fluent.ToggleSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings > Stremio & torrents: the add-ons and the torrent engine */
@Composable
fun StremioPage() {
    val c = Fluent.colors
    StremioAddons.load()
    TorrentEngine.load()
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        FText("Add-ons", style = Fluent.type.bodyStrong, modifier = Modifier.padding(bottom = 8.dp, start = 2.dp))
        AddAddonCard()
        for (a in StremioAddons.addons.toList()) AddonCard(a)
        FText(
            "Add-ons and extensions stay apart: an add-on's titles are played from the add-ons' sources and subtitles, and an extension's titles from that extension.",
            style = Fluent.type.caption, color = c.textTertiary, maxLines = 3, modifier = Modifier.padding(top = 6.dp, start = 2.dp).widthIn(max = 720.dp),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 28.dp)) {
        FText("Torrents", style = Fluent.type.bodyStrong, modifier = Modifier.padding(bottom = 8.dp, start = 2.dp))
        TorrentCards()
    }
    FText(
        "Stremio add-ons are made by other people and are not part of this app or of Stremio. This app is not made by or connected to Stremio; it speaks the open add-on protocol. " +
            "Add only add-ons you trust: an add-on sees the title you open.",
        style = Fluent.type.caption, color = c.textTertiary, maxLines = 4, modifier = Modifier.padding(top = 20.dp, start = 2.dp).widthIn(max = 720.dp),
    )
}

@Composable
private fun AddAddonCard() {
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
            val r = withContext(Dispatchers.IO) { StremioAddons.add(address) }
            busy = false
            r.onSuccess {
                text = ""
                note = "Added ${it.name}" to true
                if (it.manifest?.torrents == true && !TorrentEngine.ready) note = "Added ${it.name}. Its sources are torrents: playing one asks to turn on torrent streaming." to true
            }.onFailure { note = (it.message ?: "Could not add it") to false }
        }
    }
    val shape = RoundedCornerShape(FluentShapes.card)
    Column(Modifier.fillMaxWidth().background(c.card, shape).border(androidx.compose.ui.unit.Dp.Hairline, c.stroke, shape).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FText("Add an add-on", style = Fluent.type.body)
        FText("Paste the address of its manifest (stremio://… or https://…/manifest.json) or of its page.", style = Fluent.type.caption, color = c.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextBox(text, { text = it }, Modifier.weight(1f), placeholder = "stremio://…", leadingIcon = Icons.Link, height = 36.dp, onSubmit = { add(text) })
            Button(if (busy) "Adding…" else "Add", { add(text) }, kind = ButtonKind.Accent, icon = Icons.Add, height = 36.dp, enabled = !busy && text.isNotBlank())
        }
        note?.let { (message, ok) -> FText(message, style = Fluent.type.caption, color = if (ok) c.success else c.critical, maxLines = 3) }
        val offered = StremioAddons.suggestions.filter { s -> StremioAddons.addons.none { it.manifestUrl == s.url } }
        if (offered.isNotEmpty()) {
            FText("Popular", style = Fluent.type.caption, color = c.textTertiary)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in offered) com.lagradost.desktop.ui.fluent.Tooltip(s.about) { Button(s.name + if (s.needsP2p) "  (torrents)" else "", { add(s.url) }, icon = Icons.Add, enabled = !busy) }
            }
        }
    }
}

@Composable
private fun AddonCard(a: StremioAddon) {
    val c = Fluent.colors
    val scope = rememberCoroutineScope()
    val m = a.manifest
    val shape = RoundedCornerShape(FluentShapes.card)
    val index = StremioAddons.addons.indexOfFirst { it.manifestUrl == a.manifestUrl }
    Row(
        Modifier.fillMaxWidth().background(c.card, shape).border(androidx.compose.ui.unit.Dp.Hairline, c.stroke, shape).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(c.control), contentAlignment = Alignment.Center) {
            if (m?.logo != null) RemoteImage(m.logo, null, a.name, Modifier.size(44.dp), ContentScale.Fit) else Icon(Icons.Extensions, size = 20.dp, tint = c.textTertiary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FText(a.name, style = Fluent.type.bodyStrong, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                m?.version?.takeIf { it.isNotBlank() }?.let { FText("v$it", style = Fluent.type.caption, color = c.textTertiary, maxLines = 1) }
            }
            m?.description?.takeIf { it.isNotBlank() }?.let { FText(it, style = Fluent.type.caption, color = c.textSecondary, maxLines = 2) }
            if (m != null) androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
                if (m.providesCatalogs) Badge("${m.catalogs.size} catalogs")
                if (m.providesMeta) Badge("Details")
                if (m.providesStreams) Badge("Sources")
                if (m.providesSubtitles) Badge("Subtitles")
                if (m.torrents) Badge("Torrents", accent = true)
                if (m.adult) Badge("Adult")
                if (m.configurationRequired) Badge("Needs setup", accent = true)
            }
            if (m == null) FText("Could not be read: ${a.error ?: "no answer"}. It is tried again at the next start.", style = Fluent.type.caption, color = c.caution, maxLines = 2)
            else if (a.error != null) FText(a.error, style = Fluent.type.caption, color = c.caution, maxLines = 2)
            if (m?.configurable == true) FText("This add-on has settings of its own (for example a debrid service): configure it on its page, then add the address it gives you.", style = Fluent.type.caption, color = c.textTertiary, maxLines = 3)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (m?.configurable == true) Button("Configure", { DesktopPlatform.openExternalBrowser(StremioClient.baseOf(a.manifestUrl) + "/configure") }, kind = ButtonKind.Subtle, icon = Icons.OpenInNewWindow, height = 30.dp)
                Button("Refresh", { scope.launch(Dispatchers.IO) { StremioAddons.refresh(a.manifestUrl); Toasts.show("${a.name} refreshed", false) } }, kind = ButtonKind.Subtle, icon = Icons.Refresh, height = 30.dp)
                Button("Remove", {
                    Overlays.show(
                        Overlays.Dialog(title = "Remove ${a.name}?", primary = "Remove", close = "Keep", onPrimary = { StremioAddons.remove(a.manifestUrl) }) {
                            FText("Its catalogs, sources and subtitles are no longer used. You can add it again at any time.", color = c.textSecondary, maxLines = 4)
                        },
                    )
                }, kind = ButtonKind.Subtle, icon = Icons.Delete, height = 30.dp)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ToggleSwitch(a.enabled, { StremioAddons.setEnabled(a.manifestUrl, it) })
            Row {
                IconButton(Icons.ChevronUp, { StremioAddons.move(a.manifestUrl, -1) }, tooltip = "Earlier (its sources come first)", size = 28.dp, iconSize = 12.dp, enabled = index > 0)
                IconButton(Icons.ChevronDown, { StremioAddons.move(a.manifestUrl, 1) }, tooltip = "Later", size = 28.dp, iconSize = 12.dp, enabled = index in 0 until StremioAddons.addons.size - 1)
            }
        }
    }
}

@Composable
private fun TorrentCards() {
    val c = Fluent.colors
    val scope = rememberCoroutineScope()
    SettingsCard(
        "Torrent streaming",
        "Plays the torrent sources of your add-ons (such as Torrentio) and of extensions, while they download. The engine is a separate program that runs on this PC only while a torrent plays.",
    ) {
        ToggleSwitch(TorrentEngine.enabled, { on -> if (on) TorrentConsent.request({}, {}) else TorrentEngine.chooseEnabled(false) })
    }
    // the engine
    val progress = TorrentEngine.installProgress
    val shape = RoundedCornerShape(FluentShapes.card)
    var latest by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(c.card, shape).border(androidx.compose.ui.unit.Dp.Hairline, c.stroke, shape).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FText("Torrent engine (TorrServer)", style = Fluent.type.body)
        if (TorrentEngine.installed) FText("Installed: ${TorrentEngine.version ?: "unknown version"}  ·  ${TorrentEngine.sizeMb} MB", style = Fluent.type.caption, color = c.textSecondary)
        else FText("Not installed. It is downloaded once, about 60 MB, from github.com/YouROK/TorrServer.", style = Fluent.type.caption, color = c.textSecondary)
        if (progress != null) {
            ProgressBar(progress, Modifier.fillMaxWidth())
            FText("Downloading… ${(progress * 100).toInt()} %", style = Fluent.type.caption, color = c.textSecondary)
        }
        TorrentEngine.installError?.let { FText("The last download failed: $it", style = Fluent.type.caption, color = c.critical, maxLines = 3) }
        latest?.let { tag -> FText(if (tag == TorrentEngine.version) "This is the newest version." else "Newest version: $tag", style = Fluent.type.caption, color = c.textSecondary) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!TorrentEngine.installed && progress == null) Button("Download", { TorrentEngine.install() }, kind = ButtonKind.Accent, icon = Icons.Download)
            if (progress != null) Button("Cancel", { TorrentEngine.cancelInstall() })
            if (TorrentEngine.installed && progress == null) {
                Button(if (checking) "Checking…" else "Check for update", {
                    checking = true
                    scope.launch { latest = TorrentEngine.latestTag(); checking = false }
                }, icon = Icons.Refresh, enabled = !checking)
                if (latest != null && latest != TorrentEngine.version) Button("Update", { TorrentEngine.install() }, kind = ButtonKind.Accent, icon = Icons.Download)
                Button("Remove", { TorrentEngine.uninstall(); Toasts.show("Torrent engine removed", false) }, kind = ButtonKind.Subtle, icon = Icons.Delete)
            }
        }
    }
    SettingsCard("Buffer size", "How much of the playing torrent is kept ready on the disk. A bigger buffer helps with seeking and slow swarms.") {
        ComboBox(listOf(128, 256, 512, 1024, 2048), TorrentEngine.cacheMb, { if (it >= 1024) "${it / 1024} GB" else "$it MB" }, { TorrentEngine.chooseCacheMb(it) }, minWidth = 110.dp)
    }
    SettingsCard("Only encrypted connections", "Some internet providers slow plain torrent traffic down. Some peers do not support encryption, so fewer are found.") {
        ToggleSwitch(TorrentEngine.encrypt, { TorrentEngine.chooseEncrypt(it) })
    }
    var used by remember { mutableStateOf(TorrentEngine.diskUseMb()) }
    SettingsCard("Downloaded parts", "What the engine keeps of the torrents you played: $used MB. It is deleted when a torrent is closed; this removes what a crash left behind.") {
        Button("Clear", { TorrentEngine.clearCache(); used = 0; Toasts.show("Torrent cache cleared", false) }, icon = Icons.Delete, enabled = used > 0)
    }
    FText(TorrentConsent.NOTICE, style = Fluent.type.caption, color = c.textTertiary, maxLines = 5, modifier = Modifier.padding(top = 10.dp, start = 2.dp).widthIn(max = 720.dp))
}
