package com.lagradost.desktop.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.AppInfo
import com.lagradost.desktop.DesktopPlatform
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.core.Route
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.Overlays
import kotlinx.coroutines.delay

/**
 * Once a day, a few seconds after the app is up, a small window asks for a star on GitHub or a donation. Never on the very first day
 * and never over the player or the setup. There is no way to switch it off: it comes once every day, whatever was clicked before.
 */
object SupportPrompt {
    private const val NEXT_KEY = "desktop_support_next_at"
    private const val FIRST_KEY = "desktop_first_run_at"
    private const val DAY = 24 * 3_600_000L

    private fun prefs() = androidx.preference.PreferenceManager.getDefaultSharedPreferences(com.lagradost.desktop.runtime.AndroidRuntime.context)

    /** The first start is only noted: the first window comes the next day */
    private fun due(): Boolean = runCatching {
        val p = prefs()
        val now = System.currentTimeMillis()
        val first = p.getLong(FIRST_KEY, 0L)
        if (first == 0L) { p.edit().putLong(FIRST_KEY, now).putLong(NEXT_KEY, now + DAY).apply(); return@runCatching false }
        now >= p.getLong(NEXT_KEY, 0L)
    }.getOrDefault(false)

    /** The next window is a day away, whatever the person does with this one */
    private fun postponeOneDay() { runCatching { prefs().edit().putLong(NEXT_KEY, System.currentTimeMillis() + DAY).apply() } }

    /** Called from the shell: waits for a quiet moment and shows the window when it is due */
    @Composable
    fun Effect() {
        LaunchedEffect(Unit) {
            if (!due()) return@LaunchedEffect
            // the extensions load and the first page draws first; then wait for a page that is not the player
            delay(9_000)
            while (Navigator.current.route is Route.Player || Navigator.current.route is Route.Setup || Overlays.dialogs.isNotEmpty()) delay(5_000)
            show()
        }
    }

    fun show() {
        postponeOneDay()
        Overlays.show(
            Overlays.Dialog(
                title = "Enjoying CloudStream for Windows?",
                primary = "★  Star on GitHub",
                secondary = "♥  Donate",
                close = "Later",
                width = 480.dp,
                onPrimary = { DesktopPlatform.openExternalBrowser(AppInfo.REPO_URL) },
                onSecondary = { DesktopPlatform.openExternalBrowser(AppInfo.DONATE_URL) },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    FText("It is free, has no ads and is made in spare time. A star on GitHub helps other people find it, and a donation keeps it growing. Thank you!", color = Fluent.colors.textSecondary)
                    // news and new versions are on Telegram, chat and help on Discord
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button("Telegram channel", { DesktopPlatform.openExternalBrowser(AppInfo.TELEGRAM_URL) }, icon = Icons.Link, height = 34.dp)
                        Button("Discord server", { DesktopPlatform.openExternalBrowser(AppInfo.DISCORD_URL) }, icon = Icons.Link, height = 34.dp)
                    }
                }
            },
        )
    }
}
