package com.lagradost.desktop.ui.screens.settings

import com.lagradost.desktop.ui.fluent.cardSurface
import com.lagradost.desktop.ui.fluent.hairBorder
import com.lagradost.desktop.ui.fluent.hoverEdge

import com.lagradost.desktop.ui.fluent.smoothWheel
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import com.lagradost.desktop.ui.fluent.tabletVerticalScroll as verticalScroll
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
import com.lagradost.desktop.ui.fluent.lightShadow
import com.lagradost.desktop.ui.fluent.FluentMotion
import com.lagradost.desktop.ui.fluent.FluentScrollbar
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.TextBox
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction
import com.lagradost.desktop.ui.shell.TopBarHeight

/** The groups of the list on the left: a caption over the pages that belong together */
private enum class Section(val title: String) {
    App("App"),
    Content("Content and accounts"),
    System("System"),
}

private enum class Page(val id: String, val title: String, val glyph: String, val tint: Long, val about: String, val section: Section? = null, val hidden: Boolean = false) {
    /** The page Settings opens on: what is worth a look right now, the settings people change most, and a card for every page */
    Overview("overview", "Overview", Icons.Home, 0xFF8B95A7, "What needs a look, the settings you change most, and every page"),

    General("general", "General", Icons.Settings, 0xFF6E7BF2, "App language, downloads, the window and performance", Section.App),
    Appearance("ui", "Appearance", Icons.Personalize, 0xFFD6409F, "Themes, layout and what the pages show", Section.App),
    Colors("colors", "Colors", Icons.Theme, 0xFFAF52DE, "Colour palettes, the accent colour and the colour of buttons", Section.App),
    Player("player", "Player", Icons.Play, 0xFFE5484D, "Playback, video, audio, subtitles and controls", Section.App),

    Sources("sources", "Extensions and sources", Icons.Extensions, 0xFF8E4EC6, "Extension languages and order, extension updates, Stremio add-ons and torrents", Section.Content),
    Accounts("account", "Accounts and sync", Icons.Person, 0xFFF76B15, "Sign in to your services, keep your lists up to date, lock the app", Section.Content),

    Security("security", "Network and security", Icons.Lock, 0xFF2F9E8F, "DNS and blocked sites, Cloudflare checks and the sandbox that keeps extensions in check", Section.System),
    Updates("updates", "Updates", Icons.Update, 0xFF30A46C, "This version, the update channel and the newest release", Section.System),
    Backup("backup", "Backup and reset", Icons.Backup, 0xFF3E8FD0, "Save and restore your data, set up again or start over", Section.System),
    About("about", "About", Icons.Info, 0xFF00A2C7, "Version, help and links", Section.System),

    /** Reached from Extensions and sources; no entry of its own in the list */
    Stremio("stremio", "Stremio and torrents", Icons.Link, 0xFF8E4EC6, "Add-ons, their sources and subtitles, torrent streaming", hidden = true),
    /** Reached from Player; no entry of its own in the list */
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
    Page.Colors -> colorGroups()
    Page.Sources -> sourcesGroups()
    Page.Security -> securityGroups()
    Page.Updates -> updateGroups()
    Page.Backup -> backupGroups()
    Page.Accounts -> accountGroups()
    Page.About -> aboutGroups()
    // pages of their own (not lists of rows)
    Page.Subtitles, Page.Stremio, Page.Overview -> emptyList()
}

@Composable
fun SettingsScreen(route: Route.Settings, entryId: Long? = null) {
    val c = Fluent.colors
    val view = remember(entryId) { entryId?.let { SettingsNav.views.getOrPut(it) { SettingsNav.View(route.page ?: Page.Overview.id) } } ?: SettingsNav.View(route.page ?: Page.Overview.id) }
    var page by remember { mutableStateOf(Page.entries.firstOrNull { it.id == view.page } ?: Page.Overview) }
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
                // a long list scrolls inside its own column: a short window must still reach About
                Column(Modifier.weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    fun row(p: Page) = @Composable {
                        CategoryRow(p.title, p.glyph, Color(p.tint), selected = query.isBlank() && (p == page || (page == Page.Subtitles && p == Page.Player) || (page == Page.Stremio && p == Page.Sources))) { show(p) }
                    }
                    row(Page.Overview)()
                    for (section in Section.entries) {
                        SectionCaption(section.title)
                        Page.entries.filter { it.section == section && !it.hidden }.forEach { row(it)() }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
            FluentScrollbar(scroll)
            Column(Modifier.fillMaxSize().smoothWheel(scroll).verticalScroll(scroll).padding(bottom = com.lagradost.desktop.ui.shell.LocalDockInset.current), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = 940.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                    LegacyContent {
                        // a page comes in with a short fade (the old one goes at once: composing two settings pages at the same time would be a stall of its own);
                        // typing in the search keeps the same "search" target, so the results do not fade on every key
                        val shown: Any = if (query.isNotBlank()) "search" else page
                        androidx.compose.animation.AnimatedContent(
                            shown,
                            transitionSpec = {
                                androidx.compose.animation.fadeIn(com.lagradost.desktop.ui.fluent.FluentMotion.tweenInAfter(180, 40)) togetherWith androidx.compose.animation.ExitTransition.None using androidx.compose.animation.SizeTransform(clip = false) { _, _ -> androidx.compose.animation.core.snap() }
                            },
                            label = "settings page",
                        ) { target ->
                            Column(Modifier.fillMaxWidth()) {
                                if (target == "search") SearchResults(query) else PageContent(target as Page)
                            }
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
    if (page == Page.Stremio) Button("Extensions and sources", { SettingsNav.page = "sources" }, kind = com.lagradost.desktop.ui.fluent.ButtonKind.Subtle, icon = Icons.ChevronLeft, modifier = Modifier.padding(bottom = 4.dp))
    // a large title and its line, like every page (the coloured square stays where the page is listed, and in front of its rows)
    Box(Modifier.padding(bottom = 22.dp, top = 4.dp)) { com.lagradost.desktop.ui.fluent.PageHeader(if (page == Page.Overview) "Settings" else page.title, subtitle = page.about) }
    androidx.compose.runtime.CompositionLocalProvider(LocalSettingsTint provides if (page == Page.Overview) Color.Unspecified else Color(page.tint)) {
        when (page) {
            Page.Overview -> OverviewPage()
            // a service that says it is down (or no longer accepts the sign-in) is said at the top of the page where it is signed in
            Page.Accounts -> { OutageNotices(); GroupList(groupsOf(page)) }
            Page.Stremio -> StremioPage()
            Page.Subtitles -> SubtitleStyleEditor()
            else -> GroupList(groupsOf(page))
        }
    }
}

@Composable
private fun OutageNotices() {
    val notes = com.lagradost.desktop.net.ServiceOutage.notes
    if (notes.isEmpty()) return
    Column(Modifier.padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        notes.entries.sortedBy { it.key }.forEach { (_, note) -> com.lagradost.desktop.ui.fluent.NoticeBar(note.message) }
    }
}

/** A new version (or one that is downloaded) is said at the top of the overview, with the way to the Updates page */
@Composable
private fun UpdateNotice() {
    val status = com.lagradost.desktop.update.UpdateCheck.status
    val release = when (status) {
        is com.lagradost.desktop.update.UpdateCheck.Status.Available -> status.release
        is com.lagradost.desktop.update.UpdateCheck.Status.Ready -> status.release
        is com.lagradost.desktop.update.UpdateCheck.Status.Downloading -> status.release
        else -> null
    } ?: return
    Box(Modifier.padding(bottom = 20.dp)) {
        com.lagradost.desktop.ui.fluent.NoticeBar(
            if (status is com.lagradost.desktop.update.UpdateCheck.Status.Ready) "Version ${release.label} is downloaded and ready to install." else "Version ${release.label} is available (you have ${com.lagradost.desktop.update.UpdateCheck.currentVersion}).",
            action = "Open Updates", onAction = { SettingsNav.page = Page.Updates.id },
        )
    }
}

/** The small grey line over a group of pages in the list on the left */
@Composable
private fun SectionCaption(title: String) {
    FText(title.uppercase(), Modifier.padding(start = 14.dp, top = 16.dp, bottom = 4.dp), style = Fluent.type.caption, color = Fluent.colors.textTertiary, maxLines = 1)
}

/** The settings people reach for most, pulled from their own pages (the same rows, so they work and are found by the search exactly alike) */
@Composable
private fun quickItems(): List<SettingItem> {
    val wanted = listOf("App mode", "Animations", "Zoom on hover", "Poster size", "Hide spoilers in episode lists", "Autoplay next episode", "Smooth motion")
    val all = listOf(Page.Appearance, Page.Player).flatMap { groupsOf(it) }.flatMap { it.items }.filter { it.row }
    return wanted.mapNotNull { title -> all.firstOrNull { it.title == title } }
}

@Composable
private fun OverviewPage() {
    val c = Fluent.colors
    val quick = quickItems()
    UpdateNotice()
    if (quick.isNotEmpty()) {
        FText("Quick settings", style = Fluent.type.bodyStrong, modifier = Modifier.padding(bottom = 8.dp, start = 2.dp))
        androidx.compose.runtime.CompositionLocalProvider(LocalRowIcons provides false) { RowCard(quick) }
        Box(Modifier.height(28.dp))
    }
    for (section in Section.entries) {
        FText(section.title, style = Fluent.type.bodyStrong, modifier = Modifier.padding(bottom = 8.dp, start = 2.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 620.dp) 2 else 1
            val tiles = Page.entries.filter { it.section == section && !it.hidden }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                tiles.chunked(columns).forEach { rowTiles ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowTiles.forEach { t -> CategoryTile(t.title, t.about, t.glyph, Color(t.tint), Modifier.weight(1f)) { SettingsNav.page = t.id } }
                        repeat(columns - rowTiles.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
        }
        Box(Modifier.height(24.dp))
    }
}

/** A page of the settings as a card: its square, its name and what is on it */
@Composable
private fun CategoryTile(title: String, about: String, glyph: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(FluentShapes.card)
    val fill by androidx.compose.animation.animateColorAsState(if (hovered) c.cardHover else c.card, FluentMotion.tweenStd(FluentMotion.FAST), label = "tileFill")
    Row(
        modifier.heightIn(min = 84.dp).clip(shape).background(fill, shape).hairBorder(if (hovered) c.hoverEdge else c.stroke, shape)
            .fluentClickable(source, true, shape, Role.Button, onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(48.dp, tint) { col -> Icon(glyph, size = 22.dp, tint = col) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // the tile grows with its text: a translated name or description is longer than the English one, and a narrower window makes it longer still
            FText(title, Modifier.padding(top = 8.dp), style = Fluent.type.bodyStrong, maxLines = 2)
            FText(about, Modifier.padding(bottom = 8.dp), style = Fluent.type.caption, color = c.textSecondary, maxLines = 3)
        }
        Icon(Icons.ChevronRightSmall, size = 12.dp, tint = if (hovered) c.text else c.textSecondary)
    }
}

/** Rows that share one rounded card, a hairline between them */
@Composable
private fun RowCard(items: List<SettingItem>) {
    val c = Fluent.colors
    val shape = RoundedCornerShape(FluentShapes.card)
    Column(Modifier.fillMaxWidth().lightShadow(shape).clip(shape).cardSurface(shape)) {
        androidx.compose.runtime.CompositionLocalProvider(LocalRowGroup provides true, LocalReserveIcon provides items.any { it.hasIcon }) {
            items.forEachIndexed { i, item ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                item.content()
            }
        }
    }
}

/** The groups of a page: a title (with its action), then the rows */
@Composable
private fun GroupList(groups: List<SettingGroup>, firstGap: Boolean = false) {
    groups.forEachIndexed { i, g ->
        // a group without a title (the card at the top of About) starts straight with its content
        if (g.title.isNotBlank() || g.action != null) Row(Modifier.fillMaxWidth().padding(top = if (i == 0 && !firstGap) 0.dp else 24.dp, bottom = 8.dp, start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            FText(g.title, style = Fluent.type.bodyStrong, modifier = Modifier.weight(1f))
            g.action?.invoke()
        } else if (i > 0) Box(Modifier.height(24.dp))
        // plain rows that follow one another share one rounded card, divided by hairlines; a block (a picture grid, a colour picker) stands on its own
        val parts = buildList<Pair<Boolean, List<SettingItem>>> {
            var run = mutableListOf<SettingItem>()
            for (item in g.items) {
                if (item.row) run += item
                else {
                    if (run.isNotEmpty()) { add(true to run); run = mutableListOf() }
                    add(false to listOf(item))
                }
            }
            if (run.isNotEmpty()) add(true to run)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            parts.forEach { (isRun, items) -> if (isRun) RowCard(items) else items.forEach { it.content() } }
        }
    }
}

/** Every row of every page whose title, description or name of its group or page has all the words of [query], working where it stands */
@Composable
private fun SearchResults(query: String) {
    val words = com.lagradost.desktop.i18n.Tr.lower(query.trim()).split(Regex("\\s+")).filter { it.isNotEmpty() }
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
        androidx.compose.runtime.CompositionLocalProvider(LocalSettingsTint provides Color(page.tint)) { GroupList(groups, firstGap = false) }
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
    // the selected page is a lit pill like the one in the top bar (no blue edge mark)
    val shape = FluentShapes.pill
    val fill by androidx.compose.animation.animateColorAsState(if (selected) c.controlHover else if (hovered) c.subtleHover else Color.Transparent, FluentMotion.tweenStd(FluentMotion.FAST), label = "settingsNavFill")
    Row(
        Modifier.padding(vertical = 1.dp).fillMaxWidth().heightIn(min = 40.dp).clip(shape)
            .background(fill, shape)
            .fluentClickable(source, true, shape, Role.Tab, onClick).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(8.dp))
        IconBadge(28.dp, tint) { col -> Icon(glyph, size = 15.dp, tint = col) }
        Box(Modifier.width(12.dp))
        // a long translation ("Updates and backup" in Russian, Spanish, French) wraps to a second line instead of ending in an ellipsis
        FText(title, Modifier.padding(vertical = 8.dp), style = if (selected) Fluent.type.bodyStrong else Fluent.type.body, color = if (selected) c.text else c.textSecondary, maxLines = 2)
    }
}
