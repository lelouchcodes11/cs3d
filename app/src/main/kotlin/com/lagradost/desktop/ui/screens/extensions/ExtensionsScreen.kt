package com.lagradost.desktop.ui.screens.extensions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.PROVIDER_STATUS_DOWN
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.ui.settings.extensions.ExtensionsViewModel
import com.lagradost.cloudstream3.ui.settings.extensions.PluginViewData
import com.lagradost.cloudstream3.ui.settings.extensions.PluginsViewModel
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import com.lagradost.desktop.core.ioTask
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromTagToLanguageName
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.DesktopPlatform
import com.lagradost.desktop.core.appVm
import com.lagradost.desktop.core.observeAsState
import com.lagradost.desktop.ui.Toasts
import com.lagradost.desktop.ui.components.RemoteImage
import com.lagradost.desktop.ui.fluent.Badge
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.Chip
import com.lagradost.desktop.ui.fluent.ContextMenuArea
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.FluentScrollbar
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.IconButton
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.MenuItem
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.fluent.ProgressRing
import com.lagradost.desktop.ui.fluent.TextBox
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction
import com.lagradost.desktop.ui.shell.TopBarHeight
import androidx.compose.ui.focus.FocusRequester

private const val INSTALLED = "__installed__"

@Composable
fun ExtensionsScreen() {
    val c = Fluent.colors
    val ctx = DesktopBootstrap.activity
    val ext = appVm<ExtensionsViewModel>()
    val plugins = appVm<PluginsViewModel>()
    val repos by ext.repositories.observeAsState()
    val stats by ext.pluginStats.observeAsState()
    val list by plugins.filteredPlugins.observeAsState()
    var selected by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        ext.loadRepositories()
        ext.loadStats()
    }
    val repoList = repos.orEmpty()
    LaunchedEffect(repoList.size) {
        if (selected == null) selected = repoList.firstOrNull()?.url ?: INSTALLED
    }
    LaunchedEffect(selected, repoList.size) {
        query = ""
        plugins.search(null)
        val sel = selected ?: return@LaunchedEffect
        if (sel == INSTALLED) plugins.updatePluginListLocal()
        else repoList.firstOrNull { it.url == sel }?.let { plugins.updatePluginList(ctx, listOf(it)) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 900.dp
        Row(Modifier.fillMaxSize().padding(top = TopBarHeight)) {
            // ---- repositories
            Column(Modifier.width(if (compact) 240.dp else 320.dp).fillMaxHeight().padding(start = 16.dp, end = 8.dp, top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FText("Repositories", Modifier.weight(1f), style = Fluent.type.subtitle)
                    Button("Add", { addRepositoryDialog(ext) }, kind = ButtonKind.Accent, icon = Icons.Add)
                }
                Box(Modifier.height(4.dp))
                stats?.let { s ->
                    FText("${s.downloaded} installed · ${s.notDownloaded} available" + if (s.disabled > 0) " · ${s.disabled} disabled" else "", style = Fluent.type.caption, color = c.textSecondary)
                }
                Box(Modifier.height(12.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item(key = INSTALLED) {
                        RepoRow("Installed extensions", "Everything downloaded or loaded locally", null, selected == INSTALLED, Icons.Download, { selected = INSTALLED }, null)
                    }
                    items(repoList.toList(), key = { it.url }) { repo ->
                        RepoRow(repo.name, repo.url, repo.iconUrl, selected == repo.url, Icons.Extensions, { selected = repo.url }, repo)
                    }
                    if (repoList.isEmpty()) item(key = "no-repos") {
                        FText("No repositories yet. Add one with its URL (or a short code), then install the providers you want.", color = c.textSecondary, style = Fluent.type.caption, modifier = Modifier.padding(8.dp))
                    }
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(c.divider))
            // ---- plugins of the selected repository
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 20.dp, vertical = 8.dp)) {
                val sel = selected
                val repo = repoList.firstOrNull { it.url == sel }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        FText(if (sel == INSTALLED) "Installed extensions" else repo?.name ?: "Extensions", style = Fluent.type.subtitle, maxLines = 1)
                        if (repo != null) FText(repo.url, style = Fluent.type.caption, color = c.textSecondary, maxLines = 1)
                    }
                    if (repo != null) Button("Install all", { PluginsViewModel.downloadAll(ctx, repo, plugins) }, icon = Icons.Download)
                    TextBox(
                        query, { query = it; plugins.search(it.ifBlank { null }) }, Modifier.width(260.dp),
                        placeholder = "Filter extensions", leadingIcon = Icons.Search,
                    )
                }
                Box(Modifier.height(12.dp))
                val items = list?.second.orEmpty()
                if (sel == null || (list == null)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ProgressRing() }
                } else if (items.isEmpty()) {
                    EmptyPlugins(sel == INSTALLED)
                } else {
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    Box(Modifier.fillMaxSize()) {
                        FluentScrollbar(listState)
                        LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp, end = 12.dp)) {
                            items(items, key = { it.pluginWrapper.plugin.url + "|" + it.pluginWrapper.plugin.internalName }) { item ->
                                PluginRow(item, plugins, repo?.let { listOf(it) } ?: emptyList(), sel == INSTALLED)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPlugins(installed: Boolean) {
    val c = Fluent.colors
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Extensions, size = 40.dp, tint = c.textTertiary)
        Box(Modifier.height(12.dp))
        FText(if (installed) "No extensions installed" else "No extensions found", style = Fluent.type.subtitle)
        FText(if (installed) "Pick a repository on the left and install providers." else "This repository is empty, still loading, or filtered out.", color = c.textSecondary)
    }
}

@Composable
private fun RepoRow(name: String, url: String, iconUrl: String?, selected: Boolean, fallbackIcon: String, onClick: () -> Unit, repo: RepositoryData?) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(FluentShapes.card)
    val menu = {
        if (repo == null) emptyList() else listOf(
            MenuItem("Copy link", Icons.Copy) { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection("${repo.name} : ${repo.url}"), null); Toasts.show("Repository copied", false) },
            MenuItem("Remove repository", Icons.Delete, destructive = true) { confirmRemove(repo) },
        )
    }
    ContextMenuArea(menu) {
        Row(
            Modifier.fillMaxWidth().clip(shape)
                .background(if (selected) c.cardHover else if (hovered) c.card else Color.Transparent, shape)
                .border(androidx.compose.ui.unit.Dp.Hairline, if (selected) c.accent.copy(alpha = 0.6f) else Color.Transparent, shape)
                .fluentClickable(source, true, shape, Role.Tab, onClick)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(c.control), contentAlignment = Alignment.Center) {
                Icon(fallbackIcon, size = 18.dp, tint = c.textSecondary)
                if (iconUrl != null) RemoteImage(iconUrl, null, null, Modifier.size(36.dp), ContentScale.Crop)
            }
            Column(Modifier.weight(1f)) {
                FText(name, style = Fluent.type.bodyStrong, maxLines = 1)
                FText(url, style = Fluent.type.caption, color = c.textSecondary, maxLines = 1)
            }
            if (repo != null && hovered) IconButton(Icons.Delete, { confirmRemove(repo) }, tooltip = "Remove repository", size = 28.dp, iconSize = 14.dp)
        }
    }
}

@Composable
private fun PluginRow(item: PluginViewData, vm: PluginsViewModel, repos: List<RepositoryData>, local: Boolean) {
    val c = Fluent.colors
    val ctx = DesktopBootstrap.activity
    val p = item.pluginWrapper.plugin
    val shape = RoundedCornerShape(FluentShapes.card)
    val instance = (PluginManager.urlPlugins[p.url] ?: PluginManager.plugins[p.url]) as? Plugin
    val hasSettings = item.isDownloaded && instance?.openSettings != null
    val repoList = if (repos.isEmpty()) listOf(item.pluginWrapper.repositoryData) else repos
    var busy by remember(item) { mutableStateOf(false) }
    LaunchedEffect(item) { busy = false }
    Row(
        Modifier.fillMaxWidth().background(c.card, shape).border(androidx.compose.ui.unit.Dp.Hairline, c.stroke, shape).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(c.control), contentAlignment = Alignment.Center) {
            Icon(Icons.Extensions, size = 20.dp, tint = c.textTertiary)
            p.iconUrl?.replace("%size%", "128")?.replace("%exact_size%", "128")?.let { RemoteImage(it, null, null, Modifier.size(44.dp), ContentScale.Crop) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FText(p.name, style = Fluent.type.bodyStrong, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                FText("v${p.version}", style = Fluent.type.caption, color = c.textTertiary)
                p.language?.takeIf { it.isNotBlank() }?.let { Badge(fromTagToLanguageName(it) ?: it) }
                if (p.status == PROVIDER_STATUS_DOWN) Badge("Down")
                if (item.isDownloaded) Badge("Installed", accent = true)
            }
            p.description?.takeIf { it.isNotBlank() }?.let { FText(it, style = Fluent.type.caption, color = c.textSecondary, maxLines = 2) }
            val meta = listOfNotNull(p.authors.takeIf { it.isNotEmpty() }?.joinToString(", ")?.let { "by $it" }, p.tvTypes?.take(5)?.joinToString(" · "))
            if (meta.isNotEmpty()) FText(meta.joinToString("   "), style = Fluent.type.caption, color = c.textTertiary, maxLines = 1)
        }
        if (hasSettings) IconButton(Icons.Settings, {
            try { instance?.openSettings?.invoke(ctx) } catch (t: Throwable) { logError(t); Toasts.show("Could not open settings: ${t.message}", true) }
        }, tooltip = "Extension settings", kind = ButtonKind.Standard)
        if (item.isDownloaded) {
            Button("Uninstall", { busy = true; vm.handlePluginAction(ctx, repoList, item.pluginWrapper, local) }, enabled = !busy, icon = Icons.Delete)
        } else {
            Button(if (busy) "Installing…" else "Install", { busy = true; vm.handlePluginAction(ctx, repoList, item.pluginWrapper, false) }, kind = ButtonKind.Accent, enabled = !busy, icon = Icons.Download)
        }
    }
}

// ------------------------------------------------------------------------------------------

private fun confirmRemove(repo: RepositoryData) {
    Overlays.message("Remove repository?", "“${repo.name}” and the extensions installed from it will be removed.", primary = "Remove", onPrimary = {
        ioTask {
            RepositoryManager.removeRepository(DesktopBootstrap.activity, repo)
            com.lagradost.cloudstream3.MainActivity.afterPluginsLoadedEvent.invoke(true)
        }
    })
}

private fun addRepositoryDialog(ext: ExtensionsViewModel) {
    var name by mutableStateOf("")
    var url by mutableStateOf("")
    val clip = runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String }.getOrNull()
    if (!clip.isNullOrBlank() && clip.length < 400) {
        val sep = " : "
        if (clip.contains(sep)) {
            name = clip.substringBefore(sep).trim()
            url = clip.substringAfter(sep).trim()
        } else if (clip.startsWith("http") || clip.length < 40) url = clip.trim()
    }
    Overlays.show(
        Overlays.Dialog(
            title = "Add repository", primary = "Add", close = "Cancel", width = 520.dp,
            onPrimary = {
                val input = url.trim()
                if (input.isEmpty()) {
                    Toasts.show("Enter a repository URL or short code", false)
                    return@Dialog
                }
                ioTask {
                    val parsed = RepositoryManager.parseRepoUrl(input)
                    if (parsed.isNullOrBlank()) { Toasts.show("That does not look like a repository URL", true); return@ioTask }
                    val repository = RepositoryManager.parseRepository(parsed)
                    if (repository == null) { Toasts.show("No repository was found at that address", true); return@ioTask }
                    val data = RepositoryData(repository.iconUrl, name.trim().ifBlank { repository.name }, parsed)
                    RepositoryManager.addRepository(data)
                    ext.loadStats()
                    ext.loadRepositories()
                    val found = RepositoryManager.getRepoPlugins(data)
                    Toasts.show(if (found.isNullOrEmpty()) "Repository added, but it lists no extensions" else "Added “${data.name}” with ${found.size} extensions", false)
                }
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FText("Paste the address of a CloudStream repository (repo.json or its short code).", color = Fluent.colors.textSecondary)
                TextBox(url, { url = it }, Modifier.fillMaxWidth(), placeholder = "https://…/repo.json or short code", leadingIcon = Icons.Link, focusRequester = remember { FocusRequester() })
                TextBox(name, { name = it }, Modifier.fillMaxWidth(), placeholder = "Name (optional)")
            }
        },
    )
}
