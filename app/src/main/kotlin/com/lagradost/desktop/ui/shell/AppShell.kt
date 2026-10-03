package com.lagradost.desktop.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import com.lagradost.desktop.ui.fluent.NavPaneCompact
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.ui.home.HomeViewModel
import com.lagradost.desktop.core.Entry
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.core.Route
import com.lagradost.desktop.core.Tab
import com.lagradost.desktop.core.appVm
import com.lagradost.desktop.core.observeAsState
import com.lagradost.desktop.ui.DesktopUiHost
import com.lagradost.desktop.ui.Toasts
import com.lagradost.desktop.ui.components.UiImageView
import com.lagradost.desktop.ui.fluent.DialogLayer
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.IconButton
import com.lagradost.desktop.ui.fluent.NavItem
import com.lagradost.desktop.ui.fluent.NavigationPane
import com.lagradost.desktop.ui.fluent.TextBox
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction
import com.lagradost.desktop.ui.screens.ComingSoonPage
import com.lagradost.desktop.ui.screens.home.HomeScreen
import com.lagradost.desktop.ui.screens.search.SearchScreen
import com.lagradost.desktop.ui.screens.common.SectionPage
import com.lagradost.desktop.ui.screens.player.PlayerScreen
import com.lagradost.desktop.ui.screens.details.DetailsScreen
import com.lagradost.desktop.ui.screens.extensions.ExtensionsScreen
import com.lagradost.desktop.ui.screens.library.LibraryScreen
import com.lagradost.desktop.ui.screens.downloads.DownloadsScreen
import com.lagradost.desktop.ui.screens.settings.SettingsScreen
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight

/** Cross page UI state of the shell */
val TopBarHeight = 52.dp

object ShellState {
    /** The top bar is see-through (pages with a full-bleed header) until the page scrolls */
    var topBarSolid by mutableStateOf(true)

    val searchFocus = FocusRequester()

    /** The window title (the player shows what is playing) */
    var windowTitle by mutableStateOf("CloudStream")
    var searchText by mutableStateOf("")
}

/** Pages with a full-bleed header call this: the top bar becomes solid once [scrolled] */
@Composable
fun TopBarOverlay(scrolled: Boolean) {
    LaunchedEffect(scrolled) { ShellState.topBarSolid = scrolled }
    DisposableEffect(Unit) { onDispose { ShellState.topBarSolid = true } }
}

private val navItems = listOf(
    NavItem("home", "Home", Icons.Home),
    NavItem("search", "Search", Icons.Search),
    NavItem("library", "Library", Icons.Library),
    NavItem("downloads", "Downloads", Icons.Download),
)
private val footerItems = listOf(
    NavItem("extensions", "Extensions", Icons.Extensions),
    NavItem("settings", "Settings", Icons.Settings),
)

private fun tabOf(id: String): Tab = when (id) {
    "home" -> Tab.Home
    "search" -> Tab.Search
    "library" -> Tab.Library
    "downloads" -> Tab.Downloads
    "extensions" -> Tab.Extensions
    else -> Tab.Settings
}

private fun Tab.id(): String = when (this) {
    Tab.Home -> "home"
    Tab.Search -> "search"
    Tab.Library -> "library"
    Tab.Downloads -> "downloads"
    Tab.Extensions -> "extensions"
    Tab.Settings -> "settings"
}

@Composable
fun AppShell() {
    val c = Fluent.colors
    Box(Modifier.fillMaxSize().background(c.bg)) {
        if (Navigator.current.route is Route.Player || Navigator.current.route is Route.Setup) {
            // the player and the setup wizard own the whole window
            PageHost()
        } else {
            Row(Modifier.fillMaxSize()) {
                // the rail keeps its room all the time; the pane opens over the page, so the page never jumps
                Box(Modifier.width(NavPaneCompact.dp))
                ContentArea(showHamburger = false, onHamburger = {})
            }
            HoverNavigationPane()
        }
        ToastLayer()
    }
}

/**
 * The left pane is a narrow icon rail all the time. It opens (with the names) over the page while the pointer
 * is on it and closes again when the pointer leaves.
 */
@Composable
private fun HoverNavigationPane() {
    val c = Fluent.colors
    var hovered by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    // a short delay each way: sweeping the pointer across the edge does not flash the pane
    LaunchedEffect(hovered) {
        if (hovered) { delay(150); open = true } else { delay(250); open = false }
    }
    val shape = RoundedCornerShape(topEnd = FluentShapes.card, bottomEnd = FluentShapes.card)
    NavigationPane(
        items = navItems,
        footerItems = footerItems,
        selectedId = Navigator.selectedTab.id(),
        expanded = open,
        canGoBack = Navigator.canGoBack,
        onBack = { Navigator.back() },
        onToggle = {},
        onSelect = { Navigator.goTab(tabOf(it.id)) },
        footer = { AccountButton(open) },
        showMenuButton = false,
        modifier = Modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        when (e.type) {
                            PointerEventType.Enter, PointerEventType.Move -> hovered = true
                            PointerEventType.Exit -> hovered = false
                        }
                    }
                }
            }
            .shadow(if (open) 16.dp else 0.dp, shape, clip = false)
            .background(c.bg, shape)
            .then(if (open) Modifier.border(androidx.compose.ui.unit.Dp.Hairline, c.stroke, shape) else Modifier),
    )
}

@Composable
private fun ContentArea(showHamburger: Boolean, onHamburger: () -> Unit) {
    val c = Fluent.colors
    val shape = RoundedCornerShape(topStart = FluentShapes.card)
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(c.layer, shape)
            .border(androidx.compose.ui.unit.Dp.Hairline, c.stroke, shape),
    ) {
        PageHost()
        TopBar(showHamburger, onHamburger)
    }
}

@Composable
private fun PageHost() {
    val holder = rememberSaveableStateHolder()
    val entry = Navigator.current
    AnimatedContent(
        targetState = entry,
        transitionSpec = {
            if (Navigator.lastWasBack) fadeIn(tween(167)) togetherWith fadeOut(tween(100))
            else (fadeIn(tween(250)) + slideInVertically(tween(250)) { 24 }) togetherWith fadeOut(tween(100))
        },
        label = "page",
    ) { e ->
        holder.SaveableStateProvider(e.id) { Page(e) }
    }
    // pages that left the stack drop their saved state
    val ids = Navigator.stack.map { it.id }
    LaunchedEffect(ids) {
        // SaveableStateHolder keeps what it has seen; popped entries are removed here
        lastSeen.filter { it !in ids }.forEach { holder.removeState(it) }
        lastSeen = ids
    }
}

private var lastSeen: List<Long> = emptyList()

@Composable
private fun Page(entry: Entry) {
    when (val r = entry.route) {
        Route.Home -> HomeScreen()
        is Route.Search -> SearchScreen(r)
        Route.Library -> LibraryScreen()
        Route.Downloads -> DownloadsScreen()
        Route.Extensions -> ExtensionsScreen()
        is Route.Settings -> SettingsScreen(r)
        is Route.Details -> DetailsScreen(entry, r)
        is Route.Section -> SectionPage(r)
        is Route.Player -> PlayerScreen(entry, r)
        is Route.Icons -> com.lagradost.desktop.ui.screens.IconGalleryPage(r.start)
        Route.Setup -> com.lagradost.desktop.ui.screens.setup.SetupScreen()
    }
}

private fun titleOf(route: Route): String? = when (route) {
    Route.Home -> null
    is Route.Search -> null
    Route.Library -> "Library"
    Route.Downloads -> "Downloads"
    Route.Extensions -> "Extensions"
    is Route.Settings -> "Settings"
    is Route.Details -> null
    is Route.Section -> route.title
    is Route.Player -> null
    is Route.Icons -> "Icons"
    Route.Setup -> null
}

@Composable
private fun TopBar(showHamburger: Boolean, onHamburger: () -> Unit) {
    val c = Fluent.colors
    val solid = ShellState.topBarSolid
    Box(
        Modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .background(if (solid) c.layer else Color.Transparent)
            .padding(horizontal = 12.dp),
    ) {
        val onHome = Navigator.current.route is Route.Home
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (showHamburger) {
                IconButton(Icons.Menu, onHamburger, Modifier.noWindowDrag("hamburger"), tooltip = "Open navigation")
                Box(Modifier.width(8.dp))
            }
            val title = titleOf(Navigator.current.route)
            if (title != null) FText(title, style = Fluent.type.subtitle, maxLines = 1, modifier = Modifier.widthIn(max = 320.dp))
            Box(Modifier.weight(1f))
            // on the Home page the box searches the extension chosen at the right; everywhere else all of them
            Box(Modifier.weight(3f).widthIn(min = 180.dp, max = 520.dp)) {
                GlobalSearchBox(Modifier.fillMaxWidth().noWindowDrag("search"), only = if (onHome) selectedHomeProvider() else null)
            }
            Box(Modifier.weight(1f))
            if (onHome) {
                ProviderSelector(Modifier.widthIn(max = 230.dp).noWindowDrag("provider"))
            }
            Box(Modifier.width(captionInset))
        }
    }
}

@Composable
private fun AccountAvatar() {
    val c = Fluent.colors
    val vm = appVm<HomeViewModel>()
    val account by vm.currentAccount.observeAsState()
    LaunchedEffect(Unit) { vm.currentAccount.value ?: runCatching { com.lagradost.cloudstream3.utils.DataStoreHelper.getCurrentAccount() }.getOrNull()?.let { vm.currentAccount.postValue(it) } }
    val a = account
    Box(Modifier.size(32.dp).clip(CircleShape).background(c.control).border(androidx.compose.ui.unit.Dp.Hairline, c.stroke, CircleShape).noWindowDrag("avatar").fluentClickable(rememberInteraction(), true, CircleShape, androidx.compose.ui.semantics.Role.Button) { showAccountPicker() }, contentAlignment = Alignment.Center) {
        if (a != null) {
            UiImageView(a.image, a.name, Modifier.size(32.dp))
        } else {
            FText("?", color = c.textSecondary)
        }
    }
}

@Composable
private fun AccountButton(expanded: Boolean) {
    val c = Fluent.colors
    val vm = appVm<HomeViewModel>()
    val account by vm.currentAccount.observeAsState()
    LaunchedEffect(Unit) { vm.currentAccount.value ?: runCatching { com.lagradost.cloudstream3.utils.DataStoreHelper.getCurrentAccount() }.getOrNull()?.let { vm.currentAccount.postValue(it) } }
    val a = account
    val source = rememberInteraction()
    val shape = RoundedCornerShape(FluentShapes.control)
    Row(
        Modifier.padding(horizontal = 4.dp, vertical = 1.dp).fillMaxWidth().height(44.dp).clip(shape)
            .background(if (source.collectIsHoveredAsState().value) c.subtleHover else Color.Transparent, shape)
            .fluentClickable(source, true, shape, androidx.compose.ui.semantics.Role.Button) { showAccountPicker() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(c.control).border(androidx.compose.ui.unit.Dp.Hairline, c.stroke, CircleShape), contentAlignment = Alignment.Center) {
                if (a != null) UiImageView(a.image, a.name, Modifier.size(28.dp)) else FText("?", color = c.textSecondary)
            }
        }
        if (expanded) FText(a?.name ?: "Profile", Modifier.padding(end = 12.dp), maxLines = 1, softWrap = false)
    }
}

/** Fluent info bars for the engine's toasts (top right, auto dismiss) */
@Composable
private fun ToastLayer() {
    val c = Fluent.colors
    Box(Modifier.fillMaxSize().padding(top = 60.dp, end = 16.dp), contentAlignment = Alignment.TopEnd) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (toast in Toasts.current.toList()) {
                LaunchedEffect(toast.id) {
                    delay(if (toast.long) 7000 else 3500)
                    Toasts.dismiss(toast)
                }
                val shape = RoundedCornerShape(FluentShapes.card)
                Row(
                    Modifier
                        .widthIn(max = 420.dp)
                        .background(c.flyout, shape)
                        .border(androidx.compose.ui.unit.Dp.Hairline, c.strokeStrong.copy(alpha = 0.4f), shape)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    com.lagradost.desktop.ui.fluent.Icon(Icons.Info, size = 16.dp, tint = c.accentText)
                    Box(Modifier.width(12.dp))
                    FText(toast.text, maxLines = 6)
                }
            }
        }
    }
}
