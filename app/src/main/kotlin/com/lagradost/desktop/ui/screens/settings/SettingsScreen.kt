package com.lagradost.desktop.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.ui.settings.SettingsAccountScreen
import com.lagradost.cloudstream3.ui.settings.SettingsGeneralScreen
import com.lagradost.cloudstream3.ui.settings.SettingsPlayerScreen
import com.lagradost.cloudstream3.ui.settings.SettingsUIScreen
import com.lagradost.cloudstream3.ui.settings.SettingsUpdatesScreen
import com.lagradost.desktop.DesktopPlatform
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.core.Route
import com.lagradost.desktop.core.Tab
import com.lagradost.desktop.runtime.AndroidRuntime
import com.lagradost.desktop.ui.LegacyContent
import com.lagradost.desktop.ui.components.UiImageView
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ComboBox
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentScrollbar
import com.lagradost.desktop.ui.fluent.FluentSettings
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.TextBox
import com.lagradost.desktop.ui.fluent.ThemeMode
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction
import com.lagradost.desktop.ui.shell.TopBarHeight
import com.lagradost.desktop.update.AppUpdater
import com.mihon.presentation.settings.Preference

private enum class Page(val id: String, val title: String, val glyph: String, val hidden: Boolean = false) {
    General("general", "General", Icons.Settings),
    Player("player", "Player", Icons.Play),
    Appearance("ui", "Appearance", Icons.Theme),
    Updates("updates", "Updates & backup", Icons.Update),
    Accounts("account", "Accounts & security", Icons.Person),
    About("about", "About", Icons.Info),
    /** Reached from Player, Subtitles; no entry of its own in the list */
    Subtitles("subtitles", "Subtitles", Icons.Subtitles, hidden = true),
}

/** Lets a preference row (built by upstream code) ask the settings screen to open a page */
object SettingsNav {
    var page by androidx.compose.runtime.mutableStateOf<String?>(null)
}

/** Settings that only mean something on a phone or TV (touch gestures, APK installs, battery, Material theme) */
private val androidOnly = setOf(
    "Disable Battery optimization", "Picture-in-picture", "Auto rotate", "Rotate", "Swipe to seek", "Swipe to change settings",
    "Double tap to seek", "Double tap to pause", "LongPress Speed Toggle", "Extra brightness", "Player Shown - Seek Amount",
    "Player Hidden - Seek Amount", "Chromecast Subtitles", "Primary color", "App theme", "App Layout", "Overscan",
    "Install pre-release version", "APK Installer", "Confirm before exiting",
)
private val androidOnlyGroups = setOf("Android TV", "Looks")

@Composable
private fun prefsOf(page: Page): List<Preference> = rawPrefsOf(page).mapNotNull { p ->
    when (p) {
        is Preference.PreferenceGroup -> if (p.title in androidOnlyGroups) null else p.copy(preferenceItems = p.preferenceItems.filter { it.title !in androidOnly }).takeIf { it.preferenceItems.isNotEmpty() }
        is Preference.PreferenceItem<*, *> -> if (p.title in androidOnly) null else p
    }
}

@Composable
private fun rawPrefsOf(page: Page): List<Preference> = when (page) {
    Page.General -> SettingsGeneralScreen.getPreferences()
    Page.Player -> {
        val subtitlesTitle = androidx.compose.ui.res.stringResource(com.lagradost.cloudstream3.R.string.player_subtitles_settings)
        SettingsPlayerScreen.getPreferences().map { p ->
            if (p !is Preference.PreferenceGroup) p
            else p.copy(preferenceItems = p.preferenceItems.map { item ->
                if (item is Preference.PreferenceItem.TextPreference && item.title == subtitlesTitle) item.copy(onClick = { SettingsNav.page = "subtitles" }) else item
            })
        }
    }
    Page.Appearance -> SettingsUIScreen.getPreferences()
    Page.Updates -> SettingsUpdatesScreen.getPreferences()
    Page.Accounts -> SettingsAccountScreen.getPreferences()
    Page.About, Page.Subtitles -> emptyList()
}

@Composable
fun SettingsScreen(route: Route.Settings) {
    val c = Fluent.colors
    var page by remember { mutableStateOf(Page.entries.firstOrNull { it.id == route.page } ?: Page.General) }
    var query by remember { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(SettingsNav.page) {
        val id = SettingsNav.page ?: return@LaunchedEffect
        Page.entries.firstOrNull { it.id == id }?.let { page = it; query = "" }
        SettingsNav.page = null
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 800.dp
        Row(Modifier.fillMaxSize().padding(top = TopBarHeight)) {
            Column(Modifier.width(if (compact) 200.dp else 270.dp).fillMaxHeight().padding(start = 12.dp, end = 8.dp, top = 8.dp)) {
                AccountHeader()
                Box(Modifier.height(12.dp))
                TextBox(query, { query = it }, Modifier.fillMaxWidth(), placeholder = "Find a setting", leadingIcon = Icons.Search)
                Box(Modifier.height(12.dp))
                Page.entries.filter { !it.hidden }.forEach { p -> CategoryRow(p.title, p.glyph, selected = query.isEmpty() && (p == page || (page == Page.Subtitles && p == Page.Player))) { query = ""; page = p } }
                Box(Modifier.weight(1f))
                CategoryRow("Extensions", Icons.Extensions, selected = false) { Navigator.goTab(Tab.Extensions) }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(c.divider))
            val scroll = rememberScrollState()
            Box(Modifier.weight(1f).fillMaxHeight()) {
            FluentScrollbar(scroll)
            Column(Modifier.fillMaxSize().verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
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
    FText(page.title, style = Fluent.type.title, modifier = Modifier.padding(bottom = 16.dp))
    when (page) {
        Page.About -> AboutPage()
        Page.Subtitles -> SubtitleStyleEditor()
        else -> {
            if (page == Page.Appearance) {
                AppearanceCards()
                Box(Modifier.height(24.dp))
            }
            FluentPreferenceList(prefsOf(page))
        }
    }
}

@Composable
private fun SearchResults(query: String) {
    val all = Page.entries.flatMap { page ->
        prefsOf(page).flatMap { p ->
            when (p) {
                is Preference.PreferenceGroup -> p.preferenceItems.map { page to it }
                is Preference.PreferenceItem<*, *> -> listOf(page to p)
                else -> emptyList()
            }
        }
    }
    val q = query.trim().lowercase()
    val hits = all.filter { (_, item) -> item.title.lowercase().contains(q) || (item.subtitle?.lowercase()?.contains(q) == true) }
    FText("Results for “$query”", style = Fluent.type.title, modifier = Modifier.padding(bottom = 16.dp))
    if (hits.isEmpty()) FText("No settings match.", color = Fluent.colors.textSecondary)
    else FluentPreferenceList(hits.groupBy { it.first }.map { (page, list) -> Preference.PreferenceGroup(page.title, preferenceItems = list.map { it.second }) })
}

@Composable
private fun AccountHeader() {
    val c = Fluent.colors
    val account = remember { runCatching { com.lagradost.cloudstream3.utils.DataStoreHelper.getCurrentAccount() }.getOrNull() }
    Row(
        Modifier.fillMaxWidth().background(c.card, RoundedCornerShape(FluentShapes.card)).border(androidx.compose.ui.unit.Dp.Hairline, c.stroke, RoundedCornerShape(FluentShapes.card)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.control)) { account?.let { UiImageView(it.image, it.name, Modifier.size(40.dp)) } }
        Column {
            FText(account?.name ?: "CloudStream", style = Fluent.type.bodyStrong, maxLines = 1)
            FText("Settings", style = Fluent.type.caption, color = c.textSecondary)
        }
    }
}

@Composable
private fun CategoryRow(title: String, glyph: String, selected: Boolean, onClick: () -> Unit) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(FluentShapes.control)
    Box(Modifier.padding(vertical = 1.dp).fillMaxWidth().height(40.dp)) {
        Row(
            Modifier.fillMaxWidth().height(40.dp).clip(shape)
                .background(if (selected || hovered) c.subtleHover else Color.Transparent, shape)
                .fluentClickable(source, true, shape, Role.Tab, onClick).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(glyph, size = 16.dp)
            FText(title, maxLines = 1)
        }
        if (selected) Box(Modifier.align(Alignment.CenterStart).size(3.dp, 16.dp).background(c.accent, RoundedCornerShape(2.dp)))
    }
}

// ----------------------------------------------------------------------------------------------

private val accentChoices = listOf(
    "Blue" to 0xFF0078D4, "Purple" to 0xFF8764B8, "Pink" to 0xFFE3008C, "Red" to 0xFFE81123,
    "Orange" to 0xFFF7630C, "Green" to 0xFF107C10, "Teal" to 0xFF00B7C3, "Gray" to 0xFF7A7574,
)

@Composable
private fun AppearanceCards() {
    val c = Fluent.colors
    FText("Windows look", style = Fluent.type.bodyStrong, modifier = Modifier.padding(bottom = 8.dp, start = 2.dp))
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SettingsCard("App theme", "Follow Windows, or always use light or dark.") {
            ComboBox(ThemeMode.entries.toList(), FluentSettings.themeMode, { when (it) { ThemeMode.System -> "Use system setting"; ThemeMode.Light -> "Light"; ThemeMode.Dark -> "Dark" } }, { FluentSettings.chooseMode(it) }, minWidth = 190.dp)
        }
        SettingsCard("Accent colour", "Use your Windows accent colour, or pick one for CloudStream.") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val system = FluentSettings.accentOverride == null
                Swatch(null, system, "Windows accent")
                accentChoices.forEach { (name, argb) -> Swatch(Color(argb), FluentSettings.accentOverride?.value == Color(argb).value, name) }
            }
        }
    }
}

@Composable
private fun Swatch(color: Color?, selected: Boolean, label: String) {
    val c = Fluent.colors
    val shape = CircleShape
    val fill = color ?: Color(0xFF0078D4)
    com.lagradost.desktop.ui.fluent.Tooltip(label) {
        Box(
            Modifier.size(26.dp).clip(shape).background(fill, shape)
                .border(if (selected) 2.dp else 1.dp, if (selected) c.text else c.strokeStrong, shape)
                .fluentClickable(rememberInteraction(), true, shape, Role.RadioButton) { FluentSettings.chooseAccent(color) },
            contentAlignment = Alignment.Center,
        ) {
            if (color == null) Icon(Icons.Theme, size = 12.dp, tint = Color.White)
            else if (selected) Icon(Icons.Check, size = 12.dp, tint = Color.White)
        }
    }
}

@Composable
private fun AboutPage() {
    val c = Fluent.colors
    val dir = AndroidRuntime.dataDir
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        val pre = if (com.lagradost.desktop.update.AppUpdater.parseVersion(AppUpdater.currentVersion)?.numbers?.firstOrNull() == 0) " (pre-release)" else ""
        SettingsCard("CloudStream for Windows", "Version ${AppUpdater.currentVersion}$pre  ·  a native Windows app on the CloudStream ${BuildConfig.VERSION_NAME} engine")
        UpdateCards()
        SettingsCard("App data folder", dir.absolutePath, onClick = { DesktopPlatform.openFile(dir) })
        SettingsCard("Crash log", "Opens the last crash report, if there is one", onClick = {
            val f = java.io.File(dir, "crash.log")
            if (f.isFile) DesktopPlatform.openFile(f) else com.lagradost.desktop.ui.Toasts.show("No crash log yet", false)
        })
        SettingsCard("Source code and releases", "${AppUpdater.repo} on GitHub (GPL-3.0). Report problems and get new versions there.", onClick = { DesktopPlatform.openExternalBrowser(AppUpdater.repoUrl) })
        SettingsCard("The CloudStream project", "This app is built on the engine of CloudStream, an open source (GPL-3.0) video app for Android.", onClick = { DesktopPlatform.openExternalBrowser("https://github.com/recloudstream/cloudstream") })
    }
}
