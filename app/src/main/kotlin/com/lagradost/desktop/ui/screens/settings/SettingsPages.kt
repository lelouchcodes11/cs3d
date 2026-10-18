package com.lagradost.desktop.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.settings.SettingsAccountScreen
import com.lagradost.cloudstream3.ui.settings.SettingsGeneralScreen
import com.lagradost.cloudstream3.ui.settings.SettingsPlayerScreen
import com.lagradost.cloudstream3.ui.settings.SettingsUIScreen
import com.lagradost.cloudstream3.ui.settings.SettingsUpdatesScreen
import com.lagradost.cloudstream4.rememberAppSettings
import com.lagradost.desktop.AppInfo
import com.lagradost.desktop.DesktopPlatform
import com.lagradost.desktop.player.SubtitleEncoding
import com.lagradost.desktop.runtime.AndroidRuntime
import com.lagradost.desktop.ui.fluent.Appearance
import com.mihon.presentation.settings.Preference
import com.mihon.presentation.settings.collectAsState

// The pages of Settings: which rows each one has and how they are grouped. The rows come from the upstream preference lists (picked by what they keep)
// and from this app's own cards; what is not listed here (touch gestures, TV, APK installs, phone player layout) is not shown at all.

@Composable
internal fun generalGroups(): List<SettingGroup> {
    com.lagradost.desktop.net.ExtensionPriority.names // read: the row says what is on the list
    val up = Upstream(SettingsGeneralScreen.getPreferences())
    return listOf(
        group("Language") {
            pref(up.key("app_locale"), "locale translate interface")
            pref(up.key("provider_lang_key"), "extension languages providers")
            pref(up.key("prefer_media_type_key_2"), "movies series anime content types")
            pref(extensionPriorityPreference(), "providers order sources first")
        },
        group("Downloads") {
            pref(up.title(stringResource(R.string.download_path_pref)), "folder location save")
            pref(up.key("download_parallel_key"), "simultaneous at once")
            pref(up.key("download_concurrent_key"), "threads speed")
        },
        group("Network") {
            pref(up.title(stringResource(R.string.add_site_pref)), "domain mirror bypass blocked site")
            pref(up.key("dns_key"), "doh resolver blocked")
            pref(up.key("jsdelivr_proxy_key"), "cdn github blocked")
            pref(cloudflarePreference(), "captcha bot challenge solver browser window flash popup turn off disable")
        },
        group("Window") { windowItems() },
    )
}

@Composable
internal fun playerGroups(): List<SettingGroup> {
    val up = Upstream(SettingsPlayerScreen.getPreferences())
    return listOf(
        group("Playback") {
            pref(up.key("player_default_key"), "external vlc mpv open with")
            mpvItem()
            pref(up.key("autoplay_next_key"), "next episode continue")
            pref(up.key("enable_skip_op_from_database"), "skip intro opening ending credits aniskip")
        },
        group("Sources") {
            pref(up.title(stringResource(R.string.source_priority)), "quality order profile")
            smoothSourcesItem()
        },
        group("Video") { videoItems() },
        group("Audio") {
            pref(audioDecoderPreference(), "hardware software passthrough spdif sound")
            pref(dynamicRangePreference(), "compressor loudness night volume")
        },
        group("Subtitles") {
            link(stringResource(R.string.player_subtitles_settings), stringResource(R.string.player_subtitles_settings_des), "captions font size colour outline shadow background") { SettingsNav.page = "subtitles" }
            pref(subtitleEncodingPreference(), "charset utf-8 language characters")
        },
        group("Controls") { controlItems() },
        group("Cache") { pref(up.title(stringResource(R.string.video_buffer_clear_settings)), "clear storage space delete temporary") },
    )
}

@Composable
internal fun appearanceGroups(): List<SettingGroup> {
    val up = Upstream(SettingsUIScreen.getPreferences())
    return listOf(
        group("Theme & colours") { themeItems() },
        group("Background") { backgroundItems() },
        group("Layout") { layoutItems() },
        group("Home") { homeItems() },
        group("Title pages") {
            titleInfoItems()
            spoilerItems()
            pref(up.key("show_trailers_key"), "video preview")
            pref(up.key("show_fillers_key"), "filler episodes anime")
            pref(up.key("display_sub_key"), "dub sub language audio filter")
        },
    )
}

@Composable
internal fun accountGroups(): List<SettingGroup> {
    val settings = rememberAppSettings()
    val auto by settings.player.autoStatusSync.collectAsState()
    val upstream = SettingsAccountScreen.getPreferences().filterIsInstance<Preference.PreferenceGroup>().filter { it.enabled }
    return buildList {
        upstream.firstOrNull()?.let { accounts -> add(group("Accounts") { accounts.preferenceItems.forEach { pref(it, "sign in log in anilist mal myanimelist simkl kitsu opensubtitles") } }) }
        add(
            group("Lists") {
                pref(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.player.episodeSync,
                        title = stringResource(R.string.episode_sync_settings),
                        subtitle = stringResource(R.string.episode_sync_settings_des),
                        icon = painterResource(R.drawable.autorenew_24px),
                    ),
                    "progress watched episode count tracker anilist mal",
                )
                pref(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.player.autoStatusSync,
                        title = stringResource(R.string.auto_status_sync_title),
                        subtitle = stringResource(R.string.auto_status_sync_des),
                        icon = painterResource(R.drawable.baseline_sync_24),
                    ),
                    "watching planning completed on hold dropped status tracker anilist mal add",
                )
                // only a choice while the list status follows playback at all
                if (auto) pref(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.player.autoStatusSyncAll,
                        title = stringResource(R.string.auto_status_sync_all_title),
                        subtitle = stringResource(R.string.auto_status_sync_all_des),
                        icon = painterResource(R.drawable.movie_edit_24px),
                    ),
                    "watching every all anime titles not on a list planning add automatically",
                )
            },
        )
        upstream.drop(1).forEach { g -> add(group(g.title) { g.preferenceItems.forEach { pref(it) } }) }
    }
}

@Composable
internal fun updateGroups(): List<SettingGroup> {
    val up = Upstream(SettingsUpdatesScreen.getPreferences())
    return listOf(
        group("App updates") {
            block("Check for updates", "Looks for a newer version on GitHub and updates the app when you say so", "version release download install github automatic") {
                androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)) { UpdateCards() }
            }
        },
        group("Backup") {
            pref(up.title(stringResource(R.string.backup_settings)), "save export file")
            pref(up.key("automatic_backup_key"), "schedule periodic")
            pref(up.title(stringResource(R.string.backup_path_title)), "folder location")
            pref(up.title(stringResource(R.string.restore_settings)), "import load file")
        },
        group("Extensions") {
            pref(up.key("auto_update_plugins"), "plugins")
            pref(up.key("auto_download_plugins_key2"), "plugins install new repositories")
            pref(up.title(stringResource(R.string.update_plugins)), "plugins now manually")
            pref(up.title("Extension update history"), "plugins log")
        },
        group("Maintenance") {
            pref(up.title(stringResource(R.string.redo_setup_process)), "wizard first run language")
        },
    )
}

@Composable
internal fun aboutGroups(): List<SettingGroup> {
    val dir = AndroidRuntime.dataDir
    return listOf(
        group("CloudStream for Windows") {
            val pre = if (AppInfo.isPreRelease) " (pre-release)" else ""
            card("CloudStream for Windows", "Version ${AppInfo.version}$pre  ·  a native Windows app on the CloudStream ${BuildConfig.VERSION_NAME} engine", "version build")
            link("Join us on Telegram", "News, new versions, help and feedback: t.me/cs3d_official", "chat community support") { DesktopPlatform.openExternalBrowser(AppInfo.TELEGRAM_URL) }
            link("Join us on Discord", "Chat, help and ideas with other users: discord.gg/u82JU5JDM", "chat community support") { DesktopPlatform.openExternalBrowser(AppInfo.DISCORD_URL) }
            link("Support the project (India)", "CloudStream for Windows is free. If it saves you time, a donation keeps it going: razorpay.me/@lelouch11 (UPI and cards)", "donate razorpay upi india") { DesktopPlatform.openExternalBrowser(AppInfo.DONATE_URL) }
            link("Support the project (international)", "From anywhere else: buymeacoffee.com/lelouchcodes11 takes international cards", "donate buy me a coffee international") { DesktopPlatform.openExternalBrowser(AppInfo.DONATE_INTL_URL) }
            link("Source code and releases", "${AppInfo.REPO} on GitHub (GPL-3.0). Report problems and get new versions there.", "github license bug report") { DesktopPlatform.openExternalBrowser(AppInfo.REPO_URL) }
            link("The CloudStream project", "This app is built on the engine of CloudStream, an open source (GPL-3.0) video app for Android.", "upstream credits") { DesktopPlatform.openExternalBrowser("https://github.com/recloudstream/cloudstream") }
            block("Legal notice", null, "disclaimer copyright") { PreferenceCard(Preference.PreferenceItem.InfoPreference(title = stringResource(R.string.legal_notice_text))) }
        },
        group("Help") {
            link("Keyboard shortcuts", "The keys of the player: play, seek, volume, full screen, subtitles and more (F1 in the player shows the same list)", "hotkeys keys") { com.lagradost.desktop.ui.screens.player.Shortcuts.show() }
            link("Copy diagnostics", "Puts what a bug report needs on the clipboard: versions, your PC and screens, VPN adapters, the player's output and the latest errors. Passwords and tokens are left out.", "bug report support debug") {
                com.lagradost.desktop.Diagnostics.copyToClipboard { n -> com.lagradost.desktop.ui.Toasts.show("Diagnostics copied ($n lines): paste them into your message", false) }
            }
            link("Log file", "The last two runs of the app, for a bug report", "debug errors") {
                val f = java.io.File(dir, "logs/app.log")
                if (f.isFile) DesktopPlatform.openFile(f) else com.lagradost.desktop.ui.Toasts.show("No log file yet", false)
            }
            link("Crash log", "Opens the last crash report, if there is one", "debug errors") {
                val f = java.io.File(dir, "crash.log")
                if (f.isFile) DesktopPlatform.openFile(f) else com.lagradost.desktop.ui.Toasts.show("No crash log yet", false)
            }
            link("App data folder", dir.absolutePath, "files location portable") { DesktopPlatform.openFile(dir) }
        },
    )
}

/** Settings > Player > External mpv: the mpv.exe that "Play in mpv" (Preferred video player, episode menu) and "Open in mpv" (player menu) start */
@Composable
private fun GroupBuilder.mpvItem() {
    val version = com.lagradost.desktop.player.ExternalPlayers.mpvVersion.intValue // read: the row follows the choice
    val chosen = com.lagradost.desktop.player.ExternalPlayers.chosenMpv()
    val found = androidx.compose.runtime.remember(version) { com.lagradost.desktop.player.ExternalPlayers.mpvPath() }
    val about = when {
        found == null -> "mpv.exe was not found (mpv.io, winget install mpv, scoop install mpv). Choose it here, then pick mpv as the preferred video player or use Open in mpv in the player's menu."
        chosen.isBlank() -> "Found: ${found.absolutePath}. Pick mpv as the preferred video player, or use Open in mpv in the player's menu."
        else -> "Your choice: ${found.absolutePath}"
    }
    card("External mpv", about, "mpv.exe mpvnet player open with play in") {
        androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            if (chosen.isNotBlank()) com.lagradost.desktop.ui.fluent.Button("Find automatically", { com.lagradost.desktop.player.ExternalPlayers.setMpvPath(null) }, kind = com.lagradost.desktop.ui.fluent.ButtonKind.Subtle)
            com.lagradost.desktop.ui.fluent.Button("Choose mpv.exe…", { com.lagradost.desktop.ui.ExternalPlayerHints.chooseMpv() }, icon = com.lagradost.desktop.ui.fluent.Icons.Folder)
        }
    }
}

// ---- rows that are not upstream's -----------------------------------------------------------------------------------------------------------

/** The order of the extensions in search results and sources */
@Composable
private fun extensionPriorityPreference(): Preference.PreferenceItem.TextPreference {
    com.lagradost.desktop.net.ExtensionPriority.names // read: the row says what is on the list
    return Preference.PreferenceItem.TextPreference(
        title = "Extension priority",
        subtitle = com.lagradost.desktop.ui.extensionPrioritySummary(),
        onClick = { com.lagradost.desktop.ui.showExtensionPriorityDialog() },
    )
}

/** Cloudflare solvers: the pages the app opens to pass a Cloudflare check (see CloudflareGate) */
@Composable
private fun cloudflarePreference(): Preference.PreferenceItem.SwitchPreference {
    val settings = rememberAppSettings()
    return Preference.PreferenceItem.SwitchPreference(
        preference = settings.general.cloudflareSolvers,
        title = stringResource(R.string.cloudflare_solvers_title),
        subtitle = stringResource(R.string.cloudflare_solvers_des),
        icon = painterResource(R.drawable.baseline_network_ping_24),
    )
}

/** Subtitle encoding of Settings > Player: Automatic or the character set the subtitle files are read with (the phone's list, with Apply / Cancel) */
@Composable
private fun subtitleEncodingPreference(): Preference.PreferenceItem.TextPreference {
    SubtitleEncoding.version // read: the name changes when a set is applied
    return Preference.PreferenceItem.TextPreference(
        title = "Subtitle encoding",
        subtitle = SubtitleEncoding.label(),
        onClick = { com.lagradost.desktop.ui.showSubtitleEncodingDialog() },
    )
}

/** The compressor of Settings > Player (upstream's "Dynamic Range Compressor"): the same saved choice as Dynamic range in the player's menu */
private fun dynamicRangePreference(): Preference.PreferenceItem.ListPreference<com.lagradost.desktop.ui.fluent.DynamicRange> {
    val look = Appearance
    return Preference.PreferenceItem.ListPreference(
        preference = com.mihon.common.preference.StatePreferenceStore(look.dynamicRangeState).field(get = { this }, set = { it }),
        entries = com.lagradost.desktop.ui.fluent.DynamicRange.entries.associateWith { it.label },
        title = "Dynamic range compressor",
        subtitleProvider = { v, e -> "${e[v]}: ${v.detail}" },
        onValueChanged = { v ->
            look.dynamicRange = v
            look.save()
            com.lagradost.desktop.core.ioTask { com.lagradost.desktop.player.MpvPlayer.active?.applyDynamicRange() }
            true
        },
    )
}

/** The audio decoder (SW / HW / HW+) of Settings > Player: the same saved choice as Audio output in the player's menu */
private fun audioDecoderPreference(): Preference.PreferenceItem.ListPreference<com.lagradost.desktop.ui.fluent.AudioDecoder> {
    val look = Appearance
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
