package com.lagradost.desktop.ui.screens.settings

import com.lagradost.desktop.ui.fluent.hairBorder

import com.lagradost.desktop.ui.fluent.smoothWheel
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.lagradost.desktop.core.appVm
import com.lagradost.desktop.core.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.core.Route
import com.lagradost.desktop.core.Tab
import com.lagradost.desktop.ui.LegacyContent
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentScrollbar
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.TextBox
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction
import com.lagradost.desktop.ui.shell.TopBarHeight

private enum class Page(val id: String, val title: String, val glyph: String, val tint: Long, val about: String, val hidden: Boolean = false) {
    General("general", "General", Icons.Settings, 0xFF6E7BF2, "Language, downloads, network and the window"),
    Player("player", "Player", Icons.Play, 0xFFE5484D, "Playback, video, audio, subtitles and controls"),
    Stremio("stremio", "Stremio & torrents", Icons.Link, 0xFF8E4EC6, "Add-ons, their sources and subtitles, torrent streaming", hidden = true),
    Appearance("ui", "Appearance", Icons.Theme, 0xFFD6409F, "Theme, colours, layout and what the pages show"),
    Updates("updates", "Updates & backup", Icons.Update, 0xFF30A46C, "App and extension updates, backups"),
    Accounts("account", "Accounts & sync", Icons.Person, 0xFFF76B15, "Sign in to your services, keep your lists up to date, lock the app"),
    About("about", "About", Icons.Info, 0xFF00A2C7, "Version, help and links"),
    /** Reached from Player, Subtitles; no entry of its own in the list */
    Subtitles("subtitles", "Subtitles", Icons.Subtitles, 0xFFFFB224, "How subtitles look", hidden = true),
}

/** Lets a preference row (built by upstream code) ask the settings screen to open a page */
object SettingsNav {
    var page by androidx.compose.runtime.mutableStateOf<String?>(null)

    /**
     * What each Settings page of the history shows (page, search, scroll), kept outside the composition: a change that rebuilds the page host
     * (the navigation position, a theme) must not send the user back to General.
     */
    class View(var page: String, var query: String = "", var scroll: Int = 0)

    val views = HashMap<Long, View>()
}

/** The rows of a page, in groups. Only what means something in this app is listed: the phone's gestures, TV and APK settings are not. */
@Composable
private fun groupsOf(page: Page): List<SettingGroup> = when (page) {
    Page.General -> generalGroups()
    Page.Player -> playerGroups()
    Page.Appearance -> appearanceGroups()
    Page.Updates -> updateGroups()
    Page.Accounts -> accountGroups()
    Page.About -> aboutGroups()
    // pages of their own (not lists of rows)
    Page.Subtitles, Page.Stremio -> emptyList()
}

@Composable
fun SettingsScreen(route: Route.Settings, entryId: Long? = null) {
    val c = Fluent.colors
    val view = remember(entryId) { entryId?.let { SettingsNav.views.getOrPut(it) { SettingsNav.View(route.page ?: Page.General.id) } } ?: SettingsNav.View(route.page ?: Page.General.id) }
    var page by remember { mutableStateOf(Page.entries.firstOrNull { it.id == view.page } ?: Page.General) }
    var query by remember { mutableStateOf(view.query) }
    val scroll = rememberScrollState(view.scroll)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun show(p: Page, q: String = "") {
        page = p; query = q; view.page = p.id; view.query = q
        scope.launch { scroll.scrollTo(0) }
    }
    androidx.compose.runtime.LaunchedEffect(scroll) { androidx.compose.runtime.snapshotFlow { scroll.value }.collect { view.scroll = it } }
    androidx.compose.runtime.LaunchedEffect(SettingsNav.page) {
        val id = SettingsNav.page ?: return@LaunchedEffect
        Page.entries.firstOrNull { it.id == id }?.let { show(it) }
        SettingsNav.page = null
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 800.dp
        Row(Modifier.fillMaxSize().padding(top = TopBarHeight)) {
            Column(Modifier.width(if (compact) 220.dp else 290.dp).fillMaxHeight().padding(start = 20.dp, end = 8.dp, top = 12.dp)) {
                AccountHeader()
                Box(Modifier.height(12.dp))
                TextBox(query, { query = it; view.query = it; scope.launch { scroll.scrollTo(0) } }, Modifier.fillMaxWidth(), placeholder = "Search settings", leadingIcon = Icons.Search)
                Box(Modifier.height(12.dp))
                Page.entries.filter { !it.hidden }.forEach { p ->
                    // Extensions are managed from here now: the row sits just above About
                    if (p == Page.About) CategoryRow("Extensions", Icons.Extensions, Color(0xFF8E4EC6), selected = false) { Navigator.goTab(Tab.Extensions) }
                    CategoryRow(p.title, p.glyph, Color(p.tint), selected = query.isBlank() && (p == page || (page == Page.Subtitles && p == Page.Player))) { show(p) } }
                Box(Modifier.weight(1f))
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
            FluentScrollbar(scroll)
            Column(Modifier.fillMaxSize().smoothWheel(scroll).verticalScroll(scroll).padding(bottom = com.lagradost.desktop.ui.shell.LocalDockInset.current), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = 940.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                    LegacyContent {
                        Column(Modifier.fillMaxWidth()) {
                            if (query.isNotBlank()) SearchResults(query) else PageContent(page)
                        }
                    }
                    Box(Modifier.height(32.dp))
                }
            }
            }
        }
    }
}

@Composable
private fun PageContent(page: Page) {
    if (page == Page.Subtitles) Button("Player", { SettingsNav.page = "player" }, kind = com.lagradost.desktop.ui.fluent.ButtonKind.Subtle, icon = Icons.ChevronLeft, modifier = Modifier.padding(bottom = 4.dp))
    // a plain title: no coloured tile or glow
    Box(Modifier.padding(bottom = 22.dp, top = 4.dp)) { com.lagradost.desktop.ui.fluent.PageHeader(page.title, subtitle = page.about) }
    when (page) {
        Page.Stremio -> StremioPage()
        Page.Subtitles -> SubtitleStyleEditor()
        else -> GroupList(groupsOf(page))
    }
}

/** The groups of a page: a title (with its action), then the rows */
@Composable
private fun GroupList(groups: List<SettingGroup>, firstGap: Boolean = false) {
    groups.forEachIndexed { i, g ->
        Row(Modifier.fillMaxWidth().padding(top = if (i == 0 && !firstGap) 0.dp else 24.dp, bottom = 8.dp, start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            FText(g.title, style = Fluent.type.bodyStrong, modifier = Modifier.weight(1f))
            g.action?.invoke()
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            g.items.forEach { it.content() }
        }
    }
}

/** Every row of every page whose title, description or name of its group or page has all the words of [query], working where it stands */
@Composable
private fun SearchResults(query: String) {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val pages = Page.entries.filter { !it.hidden && it != Page.Subtitles }.map { page ->
        val hits = groupsOf(page).mapNotNull { g ->
            val items = g.items.filter { it.matches(words, g.title, page.title) }
            if (items.isEmpty()) null else SettingGroup(g.title, items)
        }
        page to hits
    }.filter { it.second.isNotEmpty() }
    val count = pages.sumOf { (_, groups) -> groups.sumOf { it.items.size } }
    FText(if (count == 0) "Nothing matches “${query.trim()}”" else if (count == 1) "1 setting matches “${query.trim()}”" else "$count settings match “${query.trim()}”", style = Fluent.type.title, modifier = Modifier.padding(bottom = 16.dp))
    if (count == 0) FText("Try fewer or different words, for example “subtitle”, “cloudflare” or “theme”.", color = Fluent.colors.textSecondary)
    pages.forEachIndexed { i, (page, groups) ->
        FText(page.title, style = Fluent.type.caption, color = Fluent.colors.textSecondary, modifier = Modifier.padding(top = if (i == 0) 0.dp else 28.dp, bottom = 2.dp, start = 2.dp))
        GroupList(groups, firstGap = false)
    }
}

@Composable
private fun AccountHeader() {
    val c = Fluent.colors
    val live by appVm<com.lagradost.cloudstream3.ui.home.HomeViewModel>().currentAccount.observeAsState()
    val account = live ?: remember { runCatching { com.lagradost.cloudstream3.utils.DataStoreHelper.getCurrentAccount() }.getOrNull() }
    Row(
        Modifier.fillMaxWidth().background(c.card, RoundedCornerShape(FluentShapes.card)).hairBorder(c.stroke, RoundedCornerShape(FluentShapes.card)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp)) { account?.let { com.lagradost.desktop.ui.shell.ProfileAvatar(it, 40.dp) } }
        Column {
            FText(account?.name ?: "CloudStream", style = Fluent.type.bodyStrong, maxLines = 1)
            FText("Settings", style = Fluent.type.caption, color = c.textSecondary)
        }
    }
}

@Composable
private fun CategoryRow(title: String, glyph: String, tint: Color, selected: Boolean, onClick: () -> Unit) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(FluentShapes.card)
    Row(
        Modifier.padding(vertical = 1.dp).fillMaxWidth().height(40.dp).clip(shape)
            .background(if (selected || hovered) c.subtleHover else Color.Transparent, shape)
            .fluentClickable(source, true, shape, Role.Tab, onClick).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(3.dp, 18.dp).background(if (selected) c.accent else Color.Transparent, RoundedCornerShape(2.dp)))
        Box(Modifier.width(13.dp))
        Icon(glyph, size = 16.dp, tint = if (selected) c.text else c.textSecondary)
        Box(Modifier.width(14.dp))
        FText(title, style = if (selected) Fluent.type.bodyStrong else Fluent.type.body, color = if (selected) c.text else c.textSecondary, maxLines = 1)
    }
}
