package com.lagradost.desktop.ui.screens.settings

import com.lagradost.desktop.ui.fluent.smoothWheel
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
import com.lagradost.desktop.core.appVm
import com.lagradost.desktop.core.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
import com.lagradost.desktop.AppInfo
import com.mihon.presentation.settings.Preference

private enum class Page(val id: String, val title: String, val glyph: String, val tint: Long, val about: String, val hidden: Boolean = false) {
    General("general", "General", Icons.Settings, 0xFF6E7BF2, "Language, providers, downloads and behaviour"),
    Player("player", "Player", Icons.Play, 0xFFE5484D, "Playback, sources, subtitles and controls"),
    Stremio("stremio", "Stremio & torrents", Icons.Link, 0xFF8E4EC6, "Add-ons, their sources and subtitles, torrent streaming", hidden = true),
    Appearance("ui", "Appearance", Icons.Theme, 0xFFD6409F, "Theme, colours, layout and motion"),
    Updates("updates", "Updates & backup", Icons.Update, 0xFF30A46C, "App and extension updates, backups"),
    Accounts("account", "Accounts & security", Icons.Person, 0xFFF76B15, "Sync accounts, profiles and lock"),
    About("about", "About", Icons.Info, 0xFF00A2C7, "Version, credits and links"),
    /** Reached from Player, Subtitles; no entry of its own in the list */
    Subtitles("subtitles", "Subtitles", Icons.Subtitles, 0xFFFFB224, "How subtitles look", hidden = true),
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
    // no meaning (or no effect) in this app: the player has its own controls, there is no torrent, TV or log sharing
    "Give a benene to the devs", "Seekbar preview", "Software decoding", "Test all Extensions", "Show cast panel",
    "Show Player Metadata Overlay", "Show real time clock", "Random Button", "Hide selected video quality in search results",
    "Poster title location", "Poster size", "Player resize button", "Playback speed", "Start videos paused",
    "Video cache on disk", "Video buffer size", "Video buffer length",
)
private val androidOnlyGroups = setOf("Android TV", "Looks", "Links", "Gestures", "Layout", "Toggle UI elements on poster")

private fun denied(title: String) = title in androidOnly || title.startsWith("Show Logcat")

@Composable
private fun prefsOf(page: Page): List<Preference> = rawPrefsOf(page).mapNotNull { p ->
    when (p) {
        is Preference.PreferenceGroup -> if (p.title in androidOnlyGroups) null else p.copy(preferenceItems = p.preferenceItems.filter { !denied(it.title) }).takeIf { it.preferenceItems.isNotEmpty() }
        is Preference.PreferenceItem<*, *> -> if (denied(p.title)) null else p
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
        } + Preference.PreferenceGroup("Audio", preferenceItems = listOf(audioDecoderPreference()))
    }
    Page.Appearance -> SettingsUIScreen.getPreferences()
    Page.Updates -> SettingsUpdatesScreen.getPreferences()
    Page.Accounts -> SettingsAccountScreen.getPreferences()
    Page.About, Page.Subtitles, Page.Stremio -> emptyList()
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
            Column(Modifier.width(if (compact) 220.dp else 290.dp).fillMaxHeight().padding(start = 20.dp, end = 8.dp, top = 12.dp)) {
                AccountHeader()
                Box(Modifier.height(12.dp))
                TextBox(query, { query = it }, Modifier.fillMaxWidth(), placeholder = "Find a setting", leadingIcon = Icons.Search)
                Box(Modifier.height(12.dp))
                Page.entries.filter { !it.hidden }.forEach { p ->
                    // Extensions are managed from here now: the row sits just above About
                    if (p == Page.About) CategoryRow("Extensions", Icons.Extensions, Color(0xFF8E4EC6), selected = false) { Navigator.goTab(Tab.Extensions) }
                    CategoryRow(p.title, p.glyph, Color(p.tint), selected = query.isEmpty() && (p == page || (page == Page.Subtitles && p == Page.Player))) { query = ""; page = p } }
                Box(Modifier.weight(1f))
            }
            val scroll = rememberScrollState()
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
        Page.About -> AboutPage()
        Page.Stremio -> StremioPage()
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
    val live by appVm<com.lagradost.cloudstream3.ui.home.HomeViewModel>().currentAccount.observeAsState()
    val account = live ?: remember { runCatching { com.lagradost.cloudstream3.utils.DataStoreHelper.getCurrentAccount() }.getOrNull() }
    Row(
        Modifier.fillMaxWidth().background(c.card, RoundedCornerShape(FluentShapes.card)).border(androidx.compose.ui.unit.Dp.Hairline, c.stroke, RoundedCornerShape(FluentShapes.card)).padding(12.dp),
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
    // neutral rows: a monochrome icon, a quiet fill and a short accent bar on the selected one (the Windows Settings look)
    val bg by androidx.compose.animation.animateColorAsState(if (selected) c.subtleHover else if (hovered) c.subtleHover.copy(alpha = c.subtleHover.alpha * 0.6f) else Color.Transparent, com.lagradost.desktop.ui.fluent.FluentMotion.tweenStd(160))
    Row(
        Modifier.padding(vertical = 1.dp).fillMaxWidth().height(40.dp).clip(shape)
            .background(bg, shape)
            .fluentClickable(source, true, shape, Role.Tab, onClick).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(3.dp, 16.dp).background(if (selected) c.accent else Color.Transparent, RoundedCornerShape(2.dp)))
        Box(Modifier.width(13.dp))
        Icon(glyph, size = 16.dp, tint = if (selected) c.text else c.textSecondary)
        Box(Modifier.width(14.dp))
        FText(title, style = if (selected) Fluent.type.bodyStrong else Fluent.type.body, color = if (selected) c.text else c.textSecondary, maxLines = 1)
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
    ThemeCards()
    LayoutAndStyleCards()
    TitleInfoCards()
    SpoilerCards()
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
        val pre = if (AppInfo.isPreRelease) " (pre-release)" else ""
        SettingsCard("CloudStream for Windows", "Version ${AppInfo.version}$pre  ·  a native Windows app on the CloudStream ${BuildConfig.VERSION_NAME} engine")
        SettingsCard("Join us on Telegram", "News, new versions, help and feedback: t.me/cs3d_official", onClick = { DesktopPlatform.openExternalBrowser(AppInfo.TELEGRAM_URL) })
        SettingsCard("Join us on Discord", "Chat, help and ideas with other users: discord.gg/u82JU5JDM", onClick = { DesktopPlatform.openExternalBrowser(AppInfo.DISCORD_URL) })
        UpdateCards()
        SettingsCard("Support the project", "CloudStream for Windows is free. If it saves you time, a donation keeps it going: razorpay.me/@lelouch11", onClick = { DesktopPlatform.openExternalBrowser(AppInfo.DONATE_URL) })
        SettingsCard("App data folder", dir.absolutePath, onClick = { DesktopPlatform.openFile(dir) })
        SettingsCard("Keyboard shortcuts", "The keys of the player: play, seek, volume, full screen, subtitles and more (F1 in the player shows the same list)", onClick = { com.lagradost.desktop.ui.screens.player.Shortcuts.show() })
        SettingsCard("Copy diagnostics", "Puts what a bug report needs on the clipboard: versions, your PC and screens, VPN adapters, the player's output and the latest errors. Passwords and tokens are left out.", onClick = {
            com.lagradost.desktop.Diagnostics.copyToClipboard { n -> com.lagradost.desktop.ui.Toasts.show("Diagnostics copied ($n lines): paste them into your message", false) }
        })
        SettingsCard("Log file", "The last two runs of the app, for a bug report", onClick = {
            val f = java.io.File(dir, "logs/app.log")
            if (f.isFile) DesktopPlatform.openFile(f) else com.lagradost.desktop.ui.Toasts.show("No log file yet", false)
        })
        SettingsCard("Crash log", "Opens the last crash report, if there is one", onClick = {
            val f = java.io.File(dir, "crash.log")
            if (f.isFile) DesktopPlatform.openFile(f) else com.lagradost.desktop.ui.Toasts.show("No crash log yet", false)
        })
        SettingsCard("Source code and releases", "${AppInfo.REPO} on GitHub (GPL-3.0). Report problems and get new versions there.", onClick = { DesktopPlatform.openExternalBrowser(AppInfo.REPO_URL) })
        SettingsCard("The CloudStream project", "This app is built on the engine of CloudStream, an open source (GPL-3.0) video app for Android.", onClick = { DesktopPlatform.openExternalBrowser("https://github.com/recloudstream/cloudstream") })
    }
}

/** The audio decoder (SW / HW / HW+) of Settings > Player: the same saved choice as Audio output in the player's menu */
private fun audioDecoderPreference(): Preference.PreferenceItem.ListPreference<com.lagradost.desktop.ui.fluent.AudioDecoder> {
    val look = com.lagradost.desktop.ui.fluent.Appearance
    return Preference.PreferenceItem.ListPreference(
        preference = com.mihon.common.preference.StatePreferenceStore(look.audioDecoderState).field(get = { this }, set = { it }),
        entries = com.lagradost.desktop.ui.fluent.AudioDecoder.entries.associateWith { it.label },
        title = "Audio decoder",
        subtitleProvider = { v, e -> "${e[v]}: ${v.detail}" },
        onValueChanged = { v ->
            look.audioDecoder = v
            look.save()
            com.lagradost.desktop.core.ioTask { com.lagradost.desktop.player.MpvPlayer.active?.applyAudioDecoder() }
            true
        },
    )
}
