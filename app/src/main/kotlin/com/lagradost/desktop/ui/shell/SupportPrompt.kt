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
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.lagradost.desktop.ui.fluent.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import com.lagradost.desktop.ui.fluent.FluentShapes
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
 * Once every two days, a few seconds after the app is up, a small window asks for a star on GitHub or a donation. Never on the very first day
 * and never over the player or the setup. There is no way to switch it off: it comes again two days later, whatever was clicked before.
 */
object SupportPrompt {
    private const val NEXT_KEY = "desktop_support_next_at"
    private const val FIRST_KEY = "desktop_first_run_at"
    private const val DAY = 24 * 3_600_000L
    private const val GAP = 2 * DAY

    private fun prefs() = androidx.preference.PreferenceManager.getDefaultSharedPreferences(com.lagradost.desktop.runtime.AndroidRuntime.context)

    /** The first start is only noted: the first window comes the next day */
    private fun due(): Boolean = runCatching {
        val p = prefs()
        val now = System.currentTimeMillis()
        val first = p.getLong(FIRST_KEY, 0L)
        if (first == 0L) { p.edit().putLong(FIRST_KEY, now).putLong(NEXT_KEY, now + DAY).apply(); return@runCatching false }
        now >= p.getLong(NEXT_KEY, 0L)
    }.getOrDefault(false)

    /** The next window is two days away, whatever the person does with this one */
    private fun postpone() { runCatching { prefs().edit().putLong(NEXT_KEY, System.currentTimeMillis() + GAP).apply() } }

    private var activeDialog: Overlays.Dialog? = null

    /** Dismiss the prompt if open (e.g. when opening the player) */
    fun dismiss() {
        val d = activeDialog ?: return
        activeDialog = null
        Overlays.dismiss(d)
    }

    /** Called from the shell: waits for a quiet moment and shows the window when it is due */
    @Composable
    fun Effect() {
        val route = Navigator.current.route
        LaunchedEffect(route) {
            if (route is Route.Player || route is Route.Setup) {
                dismiss()
            }
        }

        LaunchedEffect(Unit) {
            if (!due()) return@LaunchedEffect
            // the extensions load and the first page draws first; then wait for a page that is not the player
            delay(9_000)
            while (Navigator.current.route is Route.Player || Navigator.current.route is Route.Setup || Overlays.dialogs.isNotEmpty()) delay(5_000)
            show()
        }
    }

    fun show() {
        postpone()
        val d = Overlays.Dialog(
            title = null,
            primary = "★  Star on GitHub",
            close = "Later",
            width = 640.dp,
            bodyMax = 640.dp,
            enterPresses = false,
            onPrimary = { activeDialog = null; DesktopPlatform.openExternalBrowser(AppInfo.REPO_URL) },
            onClose = { activeDialog = null },
        ) {
            val c = Fluent.colors
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // the announcement first, on a solid accent card that can not be missed, with the address on a large white button
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(FluentShapes.card)).background(c.accent).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Globe, size = 20.dp, tint = c.onAccent)
                        }
                        FText("CS3D Web is here", style = Fluent.type.subtitle.copy(fontWeight = FontWeight.Bold), color = c.onAccent, maxLines = 1)
                        Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 9.dp, vertical = 2.dp)) {
                            FText("NEW", style = Fluent.type.caption.copy(fontWeight = FontWeight.Bold), color = c.accent, maxLines = 1)
                        }
                    }
                    FText(
                        "Your CloudStream extensions in the browser: browse and search every extension at once, and play right in the page. It runs through the CS3D engine on your computer.",
                        color = c.onAccent.copy(alpha = 0.92f),
                    )
                    LinkTile(
                        Modifier.fillMaxWidth(), Color.White, Color(0xFFE8E8E8), {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Globe, size = 18.dp, tint = Color.Black)
                                FText(AppInfo.WEB_APP_URL.removePrefix("https://"), Modifier.weight(1f), style = Fluent.type.bodyStrong, color = Color.Black, maxLines = 1)
                                Icon(Icons.OpenInNewWindow, size = 16.dp, tint = Color.Black)
                            }
                        },
                    ) { DesktopPlatform.openExternalBrowser(AppInfo.WEB_APP_URL) }
                }
                // then the ask: tiles, one per place (outside India the Razorpay page does not take the card: Buy Me a Coffee does)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FText("Support CloudStream for Windows", style = Fluent.type.bodyStrong, maxLines = 1)
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SupportTile(Modifier.weight(1f), Icons.FavoriteFilled, Color(0xFFE5484D), "Donate (India)", "UPI and cards") { DesktopPlatform.openExternalBrowser(AppInfo.DONATE_URL) }
                        SupportTile(Modifier.weight(1f), Icons.FavoriteFilled, Color(0xFFF5A524), "Buy Me a Coffee", "International") { DesktopPlatform.openExternalBrowser(AppInfo.DONATE_INTL_URL) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SupportTile(Modifier.weight(1f), Icons.Notification, Color(0xFF26A5E4), "Telegram channel", "News and new versions") { DesktopPlatform.openExternalBrowser(AppInfo.TELEGRAM_URL) }
                        SupportTile(Modifier.weight(1f), Icons.Account, Color(0xFF5865F2), "Discord server", "Chat, help and ideas") { DesktopPlatform.openExternalBrowser(AppInfo.DISCORD_URL) }
                    }
                }
            }
        }
        activeDialog = d
        Overlays.show(d)
    }

    /** A clickable surface that is lighter under the pointer */
    @Composable
    private fun LinkTile(modifier: Modifier, fill: Color, hoverFill: Color, content: @Composable () -> Unit, onClick: () -> Unit) {
        val source = remember { MutableInteractionSource() }
        val hovered by source.collectIsHoveredAsState()
        val shape = RoundedCornerShape(FluentShapes.control * 2)
        Box(
            modifier.clip(shape).background(if (hovered) hoverFill else fill)
                .hoverable(source).pointerHoverIcon(PointerIcon.Hand).clickable(source, indication = null, onClick = onClick),
        ) { content() }
    }

    /** One place to support or follow the app: a coloured icon, its name and what it is */
    @Composable
    private fun SupportTile(modifier: Modifier, icon: String, tint: Color, title: String, caption: String, onClick: () -> Unit) {
        val c = Fluent.colors
        LinkTile(
            modifier.border(1.dp, c.stroke, RoundedCornerShape(FluentShapes.control * 2)), c.card, c.cardHover, {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        Icon(icon, size = 17.dp, tint = tint)
                    }
                    Column(Modifier.weight(1f)) {
                        FText(title, style = Fluent.type.bodyStrong, maxLines = 1)
                        FText(caption, style = Fluent.type.caption, color = c.textSecondary, maxLines = 1)
                    }
                    Icon(Icons.ChevronRightSmall, size = 12.dp, tint = c.textTertiary)
                }
            },
            onClick,
        )
    }
}
