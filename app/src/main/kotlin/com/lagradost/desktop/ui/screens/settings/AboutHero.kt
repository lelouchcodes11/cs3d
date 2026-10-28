package com.lagradost.desktop.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.desktop.AppInfo
import com.lagradost.desktop.i18n.Tr
import com.lagradost.desktop.DesktopPlatform
import com.lagradost.desktop.ui.fluent.Badge
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.hairBorder
import com.lagradost.desktop.ui.fluent.lightShadow
import com.lagradost.desktop.ui.shell.brandLogo

/**
 * The top of Settings > About: the app's mark, its name and version, and the places to go (the project, news, chat, support) as buttons instead of
 * four plain rows of a list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AboutHero() {
    val c = Fluent.colors
    val shape = RoundedCornerShape(FluentShapes.card)
    Column(
        Modifier.fillMaxWidth().lightShadow(shape).clip(shape).background(c.card, shape).hairBorder(c.stroke, shape).padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            brandLogo?.let { Image(it, "CloudStream", Modifier.size(68.dp)) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FText("CloudStream for Windows", style = Fluent.type.title.copy(fontSize = 26.sp, lineHeight = 32.sp), maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Badge(Tr.f("Version {0}", AppInfo.version), accent = true)
                    if (AppInfo.isPreRelease) Badge(Tr.t("pre-release"))
                    FText(Tr.f("{0} engine", "CloudStream ${BuildConfig.VERSION_NAME}"), style = Fluent.type.caption, color = c.textSecondary, maxLines = 1)
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button("GitHub", { DesktopPlatform.openExternalBrowser(AppInfo.REPO_URL) }, icon = Icons.Link, height = 36.dp)
            Button("Telegram", { DesktopPlatform.openExternalBrowser(AppInfo.TELEGRAM_URL) }, icon = Icons.Link, height = 36.dp)
            Button("Discord", { DesktopPlatform.openExternalBrowser(AppInfo.DISCORD_URL) }, icon = Icons.Link, height = 36.dp)
            Button("Support (India)", { DesktopPlatform.openExternalBrowser(AppInfo.DONATE_URL) }, kind = ButtonKind.Accent, icon = Icons.Favorite, height = 36.dp)
            Button("Support (international)", { DesktopPlatform.openExternalBrowser(AppInfo.DONATE_INTL_URL) }, icon = Icons.Favorite, height = 36.dp)
        }
    }
}
