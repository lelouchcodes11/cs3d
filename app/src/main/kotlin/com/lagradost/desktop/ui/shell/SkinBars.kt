package com.lagradost.desktop.ui.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.core.AppVms
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.core.Route
import com.lagradost.desktop.core.Tab
import com.lagradost.desktop.platform.WinChrome
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentMotion
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.IconButton
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.MenuFlyout
import com.lagradost.desktop.ui.fluent.MenuItem
import com.lagradost.desktop.ui.fluent.MenuSeparator
import com.lagradost.desktop.ui.fluent.NavIcon
import com.lagradost.desktop.ui.fluent.NavItem
import com.lagradost.desktop.ui.fluent.SkinSpec
import com.lagradost.desktop.ui.fluent.Tooltip
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction

/** The navigation item with this id (the labels of the interface of a special theme are theirs, see [SkinSpec.labels]) */
private fun item(id: String): NavItem = (navItems + footerItems).first { it.id == id }

/**
 * The top bar of Netflix's interface: the app's name in red, the pages as text links, and at the right what the app has to offer on this page (on Home the
 * extension chooser, the reload and the extension's search), the notifications and the profile menu. Over the billboard it is see-through on a dark fade; once the
 * page has scrolled it is the page's own black.
 */
@Composable
internal fun SkinTopBar(spec: SkinSpec) {
    val c = Fluent.colors
    val onHome = Navigator.current.route is Route.Home
    val solid = ShellState.topBarSolid
    val fade by animateFloatAsState(if (solid) 1f else 0f, FluentMotion.tweenStd(400), label = "skinBar")
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .drawBehind {
                // see-through over the artwork: a dark fade from the top, as the picture needs for the white text; solid once the page has scrolled
                val reach = size.height + 40.dp.toPx()
                if (fade < 0.99f) drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.78f * (1f - fade)), Color.Transparent), endY = reach), size = Size(size.width, reach))
                if (fade > 0.01f) drawRect(c.bg.copy(alpha = fade))
            },
    ) {
        // a narrow window (half a screen, a snapped one): the mark instead of the name, then icons instead of the links
        // the extension search opened as a box takes room: the bar then keeps its short form up to 1500 dp
        val searching = onHome && ShellState.homeSearchOpen
        val narrow = maxWidth < 1040.dp || (searching && maxWidth < 1500.dp)
        val tiny = maxWidth < 780.dp
        Row(Modifier.fillMaxSize().padding(start = if (narrow) 4.dp else 24.dp, end = if (narrow) 12.dp else 36.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!WinChrome.windowedBar) {
                IconButton(Icons.Back, { Navigator.back() }, Modifier.noWindowDrag("skinBack"), enabled = Navigator.canGoBack, tooltip = "Back (Alt+Left)")
                Box(Modifier.width(6.dp))
            } else Box(Modifier.width(12.dp))
            if (narrow) brandLogo?.let { androidx.compose.foundation.Image(it, "CloudStream", Modifier.size(26.dp).noWindowDrag("skinMark").fluentClickable(rememberInteraction(), true, RoundedCornerShape(6.dp), Role.Button) { Navigator.goTab(Tab.Home) }) }
            else Wordmark(spec)
            Box(Modifier.width(if (narrow) 12.dp else 28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                for (id in spec.links) SkinLink(spec, item(id), iconOnly = tiny)
            }
            Box(Modifier.weight(1f))
            if (onHome) {
                if (!narrow && !searching) ShellState.homeActions?.let { a ->
                    IconButton(Icons.Refresh, a.reload, Modifier.noWindowDrag("skinReload"), tooltip = "Reload the home page", kind = ButtonKind.Subtle, size = 36.dp, tint = Color.White)
                    Box(Modifier.width(2.dp))
                }
                ProviderSelector(Modifier.widthIn(max = if (narrow) 150.dp else 200.dp).noWindowDrag("skinProvider"))
                Box(Modifier.width(8.dp))
            }
            SkinSearch(onHome)
            Box(Modifier.width(2.dp))
            val schedule = item("schedule")
            if (!searching) Tooltip("Notifications and new episodes") {
                val bell = rememberInteraction()
                Box(Modifier.size(36.dp).clip(CircleShape).noWindowDrag("skinBell").fluentClickable(bell, true, CircleShape, Role.Button) { Navigator.goTab(Tab.Schedule) }, contentAlignment = Alignment.Center) {
                    NavIcon(NavItem(schedule.id, schedule.label, Icons.Notification, schedule.badge), size = 18.dp, tint = Color.White)
                }
            }
            Box(Modifier.width(10.dp))
            SkinProfileMenu()
        }
    }
}

/** Home: the search of the extension that is shown there opens as a box (the same one as the floating bar's); elsewhere the search page */
@Composable
private fun SkinSearch(onHome: Boolean) {
    val provider = if (onHome) selectedHomeProvider() else null
    if (provider != null) ExtensionSearch(provider)
    else IconButton(Icons.Search, { Navigator.goTab(Tab.Search) }, Modifier.noWindowDrag("skinSearch"), tooltip = "Search (Ctrl+K)", kind = ButtonKind.Subtle, size = 36.dp, iconSize = 18.dp, tint = Color.White)
}

@Composable
private fun Wordmark(spec: SkinSpec) {
    val c = Fluent.colors
    FText(
        spec.wordmark,
        Modifier.noWindowDrag("skinWordmark").fluentClickable(rememberInteraction(), true, RoundedCornerShape(4.dp), Role.Button) { Navigator.goTab(Tab.Home) },
        style = Fluent.type.title.copy(fontSize = spec.wordmarkSp.sp, lineHeight = (spec.wordmarkSp + 6).sp, fontWeight = FontWeight.Black, letterSpacing = spec.wordmarkTracking.sp),
        color = c.accent, maxLines = 1, softWrap = false,
    )
}

@Composable
private fun SkinLink(spec: SkinSpec, nav: NavItem, iconOnly: Boolean = false) {
    val c = Fluent.colors
    val selected = Navigator.selectedTab.id() == nav.id
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val tint by animateColorAsState(
        when {
            selected -> Color.White
            hovered -> Color.White.copy(alpha = 0.85f)
            else -> Color.White.copy(alpha = 0.7f)
        },
        FluentMotion.tweenStd(160), label = "skinLink",
    )
    val shape = RoundedCornerShape(6.dp)
    if (iconOnly) {
        Tooltip(spec.labels[nav.id] ?: nav.label) {
            Box(Modifier.size(36.dp).clip(shape).noWindowDrag("skinLink-" + nav.id).fluentClickable(source, true, shape, Role.Tab) { Navigator.goTab(tabOf(nav.id)) }, contentAlignment = Alignment.Center) {
                NavIcon(nav, size = 18.dp, tint = tint)
            }
        }
        return
    }
    Row(
        Modifier.clip(shape).noWindowDrag("skinLink-" + nav.id).fluentClickable(source, true, shape, Role.Tab) { Navigator.goTab(tabOf(nav.id)) }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FText(spec.labels[nav.id] ?: nav.label, style = (if (selected) Fluent.type.bodyStrong else Fluent.type.body).copy(fontSize = 14.sp), color = tint, maxLines = 1, softWrap = false)
        if (nav.badge()) Box(Modifier.padding(start = 6.dp).size(7.dp).background(c.accent, CircleShape))
    }
}

/**
 * The profile at the right end of the bar, with its caret: a click opens the menu of Netflix's: the profiles to switch to, managing them and the places a
 * profile's menu leads to there (here also the pages the bar has no link for: History, Stats, Extensions and Settings).
 */
@Composable
private fun SkinProfileMenu() {
    val ctx = DesktopBootstrap.activity
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(4.dp)
    Box {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AccountAvatar(shape = shape, onClick = { open = !open })
            val caret by animateFloatAsState(if (open) 180f else 0f, FluentMotion.tweenStd(200), label = "caret")
            Box(Modifier.size(20.dp, 32.dp).noWindowDrag("skinCaret").fluentClickable(rememberInteraction(), true, RoundedCornerShape(4.dp), Role.Button) { open = !open }, contentAlignment = Alignment.Center) {
                Icon(Icons.ChevronDown, Modifier.rotate(caret), size = 12.dp, tint = Color.White)
            }
        }
        if (open) {
            val vm = AppVms.get<com.lagradost.cloudstream3.ui.account.AccountViewModel>()
            val accounts = remember { runCatching { DataStoreHelper.getAccounts(ctx) }.getOrDefault(emptyList()) }
            MenuFlyout(
                buildList {
                    for (a in accounts) add(MenuItem(a.name, Icons.Person, checked = a.keyIndex == DataStoreHelper.selectedKeyIndex) { vm.handleAccountSelect(a, ctx, false) })
                    add(MenuItem("Manage Profiles", Icons.Edit) { showAccountPicker() })
                    add(MenuSeparator)
                    add(MenuItem("Search all extensions", Icons.Search) { Navigator.goTab(Tab.Search) })
                    add(MenuItem("History", Icons.History) { Navigator.go(Route.History) })
                    add(MenuItem("Stats", Icons.Chart) { Navigator.go(Route.Stats) })
                    add(MenuItem("Extensions", Icons.Extensions) { Navigator.goTab(Tab.Extensions) })
                    add(MenuItem("Settings", Icons.Settings) { Navigator.goTab(Tab.Settings) })
                },
                onDismiss = { open = false },
            )
        }
    }
}

