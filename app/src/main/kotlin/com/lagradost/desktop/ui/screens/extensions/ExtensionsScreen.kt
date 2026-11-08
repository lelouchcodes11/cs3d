package com.lagradost.desktop.ui.screens.extensions

import com.lagradost.desktop.ui.fluent.cardSurface
import com.lagradost.desktop.ui.fluent.selectedEdge

import com.lagradost.desktop.ui.fluent.hoverEdge

import com.lagradost.desktop.ui.fluent.smoothWheel
import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import com.lagradost.desktop.ui.fluent.tabletVerticalScroll as verticalScroll
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
import com.lagradost.desktop.ui.fluent.TouchLazyColumn as LazyColumn
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.hoverable
import com.lagradost.desktop.ui.fluent.glass
import androidx.compose.ui.unit.sp
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
import com.lagradost.desktop.ui.fluent.lightShadow
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.FluentScrollbar
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.IconButton
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.MenuItem
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.fluent.ProgressRing
import com.lagradost.desktop.ui.fluent.StremioLogo
import com.lagradost.desktop.ui.fluent.TextBox
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction
import com.lagradost.desktop.ui.shell.TopBarHeight
import androidx.compose.ui.focus.FocusRequester

private const val INSTALLED = "__installed__"
private const val STREMIO = "__stremio__"

@Composable
fun ExtensionsScreen() {
    val c = Fluent.colors
    val ctx = DesktopBootstrap.activity
    val ext = appVm<ExtensionsViewModel>()
    val plugins = appVm<PluginsViewModel>()
    val repos by ext.repositories.observeAsState()
    val stats by ext.pluginStats.observeAsState()
    val list by plugins.filteredPlugins.observeAsState()
    // the page opens on what is installed (it opened on the first repository when the list of repositories was already there, and showed that repository's one extension)
    var selected by remember { mutableStateOf<String?>(INSTALLED) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        ext.loadRepositories()
        ext.loadStats()
        // the icons of the installed plugins (the Installed page has none of its own)
        com.lagradost.desktop.net.ExtensionIcons.start()
    }
    val repoList = repos.orEmpty()
    LaunchedEffect(selected, repoList.size) {
        query = ""
        plugins.search(null)
        val sel = selected ?: return@LaunchedEffect
        if (sel == INSTALLED) plugins.updatePluginListLocal()
        else if (sel == STREMIO) { /* Stremio page manages its own data */ }
        else repoList.firstOrNull { it.url == sel }?.let { plugins.updatePluginList(ctx, listOf(it)) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 1000.dp
        Column(Modifier.fillMaxSize().padding(top = TopBarHeight + 20.dp)) {
            // ---- header with numbers
            // in a half-screen window the two buttons left the title ~160 dp ("Расш…" in Russian): they go under it
            val stacked = this@BoxWithConstraints.maxWidth < 820.dp
            val actions: @Composable () -> Unit = {
                // the same as Settings > Updates & backup > Update Plugins: looks at every repository and installs newer versions of what is installed
                com.lagradost.desktop.ui.fluent.PillButton("Update all", Icons.Refresh, primary = false, onImage = false, onClick = { ioTask { com.lagradost.cloudstream3.plugins.PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_manuallyReloadAndUpdatePlugins(ctx) } }, height = 42.dp)
                com.lagradost.desktop.ui.fluent.PillButton("Add repository", Icons.Add, primary = true, onClick = { addRepositoryDialog(ext) }, height = 42.dp)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                com.lagradost.desktop.ui.fluent.PageHeader("Extensions", Modifier.weight(1f), icon = Icons.Extensions, tint = androidx.compose.ui.graphics.Color(0xFFB07CFF), subtitle = "Add a repository, then install the providers you want")
                stats?.let { s ->
                    if (!compact) {
                        StatTile(Icons.Download, s.downloaded.toString(), "installed")
                        StatTile(Icons.Extensions, s.notDownloaded.toString(), "available")
                        if (s.disabled > 0) StatTile(Icons.Warning, s.disabled.toString(), "disabled")
                    }
                }
                if (!stacked) actions()
            }
            if (stacked) Row(Modifier.fillMaxWidth().padding(horizontal = 36.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) { actions() }
            Box(Modifier.height(22.dp))
            Row(Modifier.fillMaxSize()) {
                // ---- repositories
                Column(Modifier.width(if (compact) 250.dp else 320.dp).fillMaxHeight().padding(start = 36.dp, end = 8.dp)) {
                    FText("REPOSITORIES", style = Fluent.type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, letterSpacing = 1.2.sp), color = c.textTertiary)
                    Box(Modifier.height(10.dp))
                    val repoState = androidx.compose.foundation.lazy.rememberLazyListState()
                    LazyColumn(Modifier.weight(1f).smoothWheel(repoState), state = repoState, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp + com.lagradost.desktop.ui.shell.LocalDockInset.current)) {
                        item(key = INSTALLED) {
                            RepoRow("Installed", "Everything downloaded or loaded locally", null, selected == INSTALLED, Icons.Download, { selected = INSTALLED }, null)
                        }
                        item(key = STREMIO) {
                            RepoRow("Stremio add-ons", "Add-ons, streams and torrents", null, selected == STREMIO, Icons.Link, { selected = STREMIO }, null, customIcon = { StremioLogo(size = 24.dp) })
                        }
                        items(repoList.toList(), key = { it.url }) { repo ->
                            val isStremio = repo.name.contains("stremio", ignoreCase = true) || repo.url.contains("stremio", ignoreCase = true)
                            val customIcon: (@Composable () -> Unit)? = if (isStremio && repo.iconUrl == null) {
                                { StremioLogo(size = 24.dp) }
                            } else null
                            RepoRow(repo.name, repo.url, repo.iconUrl, selected == repo.url, Icons.Extensions, { selected = repo.url }, repo, customIcon = customIcon)
                        }
                        if (repoList.isEmpty()) item(key = "no-repos") {
                            FText("No repositories yet. Add one with its URL (or a short code), then install the providers you want.", color = c.textSecondary, style = Fluent.type.caption, modifier = Modifier.padding(8.dp))
                        }
                    }
                }
                // ---- plugins or stremio add-ons of the selected category
                Column(Modifier.weight(1f).fillMaxHeight().padding(start = 16.dp, end = 28.dp)) {
                    val sel = selected
                    if (sel == STREMIO) {
                        Row(Modifier.fillMaxWidth().glass(FluentShapes.overlay).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                FText("Stremio add-ons", style = Fluent.type.subtitle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), maxLines = 1)
                                FText("Add-ons, their sources and subtitles, torrent streaming", style = Fluent.type.caption, color = c.textSecondary, maxLines = 1)
                            }
                        }
                        Box(Modifier.height(16.dp))
                        val scroll = rememberScrollState()
                        Box(Modifier.fillMaxSize()) {
                            FluentScrollbar(scroll)
                            Column(Modifier.fillMaxSize().smoothWheel(scroll).verticalScroll(scroll).padding(end = 12.dp, bottom = 32.dp + com.lagradost.desktop.ui.shell.LocalDockInset.current)) {
                                com.lagradost.desktop.ui.screens.settings.StremioPage()
                            }
                        }
                    } else {
                        val repo = repoList.firstOrNull { it.url == sel }
                        // the name and address, then the filter and "Install all": side by side when there is room, one under the other when not (the name had vanished)
                        val heading = @Composable { m: Modifier ->
                            Column(m) {
                                FText(if (sel == INSTALLED) "Installed extensions" else repo?.name ?: "Extensions", style = Fluent.type.subtitle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), maxLines = 1)
                                FText(repo?.url ?: "${list?.second?.size ?: 0} extensions", style = Fluent.type.caption, color = c.textSecondary, maxLines = 1)
                            }
                        }
                        val filterBox = @Composable { m: Modifier ->
                            TextBox(query, { query = it; plugins.search(it.ifBlank { null }) }, m, placeholder = "Filter extensions", leadingIcon = Icons.Search)
                        }
                        val installAll = @Composable {
                            if (repo != null) Button("Install all", { PluginsViewModel.downloadAll(ctx, repo, plugins) }, icon = Icons.Download, height = 34.dp)
                        }
                        if (compact) {
                            Column(Modifier.fillMaxWidth().glass(FluentShapes.overlay).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                heading(Modifier.fillMaxWidth())
                                if (this@BoxWithConstraints.maxWidth < 760.dp) {
                                    // half a screen: the filter had ~130 dp beside the button ("Фильтр рас…"), so the button goes under it
                                    filterBox(Modifier.fillMaxWidth())
                                    installAll()
                                } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    filterBox(Modifier.weight(1f))
                                    installAll()
                                }
                            }
                        } else {
                            Row(Modifier.fillMaxWidth().glass(FluentShapes.overlay).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                heading(Modifier.weight(1f))
                                filterBox(Modifier.width(280.dp))
                                installAll()
                            }
                        }
                        Box(Modifier.height(16.dp))
                        val items = list?.second.orEmpty()
                        if (sel == null || (list == null)) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ProgressRing() }
                        } else if (items.isEmpty()) {
                            EmptyPlugins(sel == INSTALLED)
                        } else {
                            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                            val columns = ((this@BoxWithConstraints.maxWidth - (if (compact) 250.dp else 320.dp) - 44.dp) / 460.dp).toInt().coerceIn(1, 3)
                            val rows = items.chunked(columns)
                            Box(Modifier.fillMaxSize()) {
                                FluentScrollbar(listState)
                                LazyColumn(Modifier.fillMaxSize().smoothWheel(listState), state = listState, verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 32.dp + com.lagradost.desktop.ui.shell.LocalDockInset.current, end = 12.dp)) {
                                    items(rows.size, key = { i -> rows[i].joinToString("|") { it.pluginWrapper.plugin.url + "|" + it.pluginWrapper.plugin.internalName } }) { i ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                            rows[i].forEach { item -> Box(Modifier.weight(1f)) { PluginCard(item, plugins, repo?.let { listOf(it) } ?: emptyList(), sel == INSTALLED) } }
                                            repeat(columns - rows[i].size) { Box(Modifier.weight(1f)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(glyph: String, value: String, label: String) {
    val c = Fluent.colors
    Row(Modifier.glass(FluentShapes.card).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).background(c.control, CircleShape), contentAlignment = Alignment.Center) { Icon(glyph, size = 14.dp, tint = c.textSecondary) }
        Box(Modifier.width(10.dp))
        Column {
            FText(value, style = Fluent.type.bodyStrong.copy(fontSize = 17.sp), maxLines = 1)
            FText(label, style = Fluent.type.caption, color = c.textTertiary, maxLines = 1)
        }
    }
}

@Composable
private fun EmptyPlugins(installed: Boolean) {
    com.lagradost.desktop.ui.fluent.EmptyState(
        Icons.Extensions,
        if (installed) "No extensions installed" else "No extensions found",
        if (installed) "Pick a repository on the left and install providers." else "This repository is empty, still loading, or filtered out.",
    )
}

@Composable
private fun RepoRow(
    name: String,
    url: String,
    iconUrl: String?,
    selected: Boolean,
    fallbackIcon: String,
    onClick: () -> Unit,
    repo: RepositoryData?,
    customIcon: (@Composable () -> Unit)? = null,
) {
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
    val bg = if (selected) c.controlHover else if (hovered) c.cardHover else c.card
    ContextMenuArea(menu) {
        Row(
            Modifier.fillMaxWidth()
                .lightShadow(shape).clip(shape)
                .let { if (selected) it.background(bg, shape) else it.cardSurface(shape, hovered) }
                .fluentClickable(source, true, shape, Role.Tab, onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(FluentShapes.small)).background(if (selected) c.controlHover else c.control), contentAlignment = Alignment.Center) {
                if (customIcon != null) {
                    customIcon()
                } else {
                    Icon(fallbackIcon, size = 18.dp, tint = if (selected) c.text else c.textSecondary)
                    if (iconUrl != null) RemoteImage(iconUrl, null, null, Modifier.size(42.dp), ContentScale.Crop)
                }
            }
            Column(Modifier.weight(1f)) {
                FText(name, style = Fluent.type.bodyStrong, maxLines = 2)
                // an address stays on one line; a sentence ("Everything downloaded or loaded locally") may take three in the narrow list
                FText(url, style = Fluent.type.caption, color = c.textTertiary, maxLines = if (url.startsWith("http")) 1 else 3)
            }
            if (repo != null && hovered) IconButton(Icons.Delete, { confirmRemove(repo) }, tooltip = "Remove repository", size = 28.dp, iconSize = 14.dp)
        }
    }
}

@Composable
private fun PluginCard(item: PluginViewData, vm: PluginsViewModel, repos: List<RepositoryData>, local: Boolean) {
    val c = Fluent.colors
    val ctx = DesktopBootstrap.activity
    val p = item.pluginWrapper.plugin
    val shape = RoundedCornerShape(FluentShapes.card)
    val instance = (PluginManager.urlPlugins[p.url] ?: PluginManager.plugins[p.url]) as? Plugin
    // an extension that runs in the sandbox has its settings page opened in the app on this click (see Isolation.openSettings)
    val sandboxed = if (item.isDownloaded) com.lagradost.desktop.isolate.Isolation.sandboxedPathFor(p.url) else null
    val hasSettings = item.isDownloaded && (instance?.openSettings != null || (sandboxed != null && com.lagradost.desktop.isolate.Isolation.hasSettings(sandboxed)))
    val settingsScope = androidx.compose.runtime.rememberCoroutineScope()
    val repoList = if (repos.isEmpty()) listOf(item.pluginWrapper.repositoryData) else repos
    var busy by remember(item) { mutableStateOf(false) }
    LaunchedEffect(item) { busy = false }
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    // an installed extension's "description" is its file name on the disk (CNCVerse.-2008229843.cs3): what it offers is more use there, and where it runs (the sandbox is
    // what makes an extension safe: the page says which ones are in it)
    val about = p.description?.takeIf { it.isNotBlank() && !it.endsWith(".cs3", true) && !it.endsWith(".jar", true) } ?: (p.tvTypes?.take(5)?.joinToString(" · ") ?: "")
    val sources = remember(item) { if (item.isDownloaded) com.lagradost.cloudstream3.APIHolder.allProviders.count { it.sourcePlugin == p.url } else 0 }
    val runs = when {
        !item.isDownloaded -> ""
        com.lagradost.desktop.isolate.Isolation.whereIs(p.url) == com.lagradost.desktop.isolate.Isolation.Where.Sandbox -> com.lagradost.desktop.i18n.Tr.t("Runs in the Windows sandbox")
        else -> com.lagradost.desktop.isolate.Isolation.reasonInApp(p.url)?.let { com.lagradost.desktop.i18n.Tr.f("Not in the sandbox: {0}", it) } ?: ""
    }
    val counts = when { sources == 1 -> com.lagradost.desktop.i18n.Tr.t("1 source"); sources > 1 -> com.lagradost.desktop.i18n.Tr.f("{0} sources", sources); else -> "" }
    // one row: the icon, what the extension is and does, and its buttons at the right (the card is as tall as its text, not a fixed block with empty space in it)
    Row(
        Modifier.fillMaxWidth().hoverable(source).lightShadow(shape).clip(shape).cardSurface(shape, hovered).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(FluentShapes.small)).background(c.control), contentAlignment = Alignment.Center) {
            Icon(Icons.Extensions, size = 22.dp, tint = c.textTertiary)
            // the Installed page lists what is on the disk, which has no icon: the repositories' icon of the same plugin is used
            val iconUrl = p.iconUrl?.replace("%size%", "128")?.replace("%exact_size%", "128")
                ?: if (local) com.lagradost.desktop.net.ExtensionIcons.iconForFile(p.url) else null
            iconUrl?.let { RemoteImage(it, null, null, Modifier.size(46.dp), ContentScale.Crop) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FText(p.name, style = Fluent.type.bodyStrong.copy(fontSize = 16.sp), maxLines = 1)
            FText("v${p.version}" + (p.authors.takeIf { it.isNotEmpty() }?.joinToString(", ")?.let { "  ·  by $it" } ?: ""), style = Fluent.type.caption, color = c.textTertiary, maxLines = 1)
            val line = listOf(about.ifBlank { counts }, runs).filter { it.isNotBlank() }.joinToString("  ·  ")
            if (line.isNotBlank()) FText(line, style = Fluent.type.caption.copy(lineHeight = 18.sp), color = c.textSecondary, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
            val language = p.language?.takeIf { it.isNotBlank() }
            val type = p.tvTypes?.firstOrNull()
            if (language != null || type != null || p.status == PROVIDER_STATUS_DOWN) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    language?.let { Badge(fromTagToLanguageName(it) ?: it) }
                    if (p.status == PROVIDER_STATUS_DOWN) Badge("Down")
                    type?.let { Badge(it) }
                }
            }
        }
        if (hasSettings) IconButton(Icons.Settings, {
            if (instance?.openSettings != null) {
                try { instance.openSettings?.invoke(ctx) } catch (t: Throwable) { logError(t); Toasts.show("Could not open settings: ${t.message}", true) }
            } else if (sandboxed != null) settingsScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                if (!com.lagradost.desktop.isolate.Isolation.openSettings(ctx, sandboxed)) Toasts.show("Could not open the settings of ${p.name}", true)
            }
        }, tooltip = "Extension settings", kind = ButtonKind.Standard, size = 34.dp)
        if (item.isDownloaded) {
            // a round button like the settings one beside it: two different shapes side by side looked unfinished
            IconButton(Icons.Delete, { if (!busy) { busy = true; vm.handlePluginAction(ctx, repoList, item.pluginWrapper, local) } }, tooltip = "Uninstall", kind = ButtonKind.Standard, size = 34.dp)
        } else {
            com.lagradost.desktop.ui.fluent.PillButton(if (busy) "Installing…" else "Install", Icons.Download, primary = true, onClick = { if (!busy) { busy = true; vm.handlePluginAction(ctx, repoList, item.pluginWrapper, false) } }, height = 34.dp)
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

/**
 * Is a copied text worth putting in the "Add repository" box: the address of a repository file (or of GitHub's raw files, or one that says "repo" or
 * "cloudstream"), or a short code (letters, digits, no spaces). The box used to take any link and any short text: a page link or a word copied for another
 * purpose appeared in it.
 */
internal fun looksLikeRepository(text: String): Boolean {
    if (text.isEmpty() || text.any { it.isWhitespace() }) return false
    if (text.startsWith("http://") || text.startsWith("https://") || text.startsWith("cloudstreamrepo://")) {
        val t = text.lowercase()
        return t.endsWith(".json") || "raw.githubusercontent.com" in t || "repo" in t || "cloudstream" in t
    }
    return text.length in 3..39 && text.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
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
        } else if (looksLikeRepository(clip.trim())) url = clip.trim()
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
