package com.lagradost.desktop.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.ui.home.HomeViewModel
import com.lagradost.desktop.core.Entry
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.core.Route
import com.lagradost.desktop.core.Tab
import com.lagradost.desktop.core.appVm
import com.lagradost.desktop.core.observeAsState
import com.lagradost.desktop.ui.Toasts
import com.lagradost.desktop.ui.components.SoftImage
import com.lagradost.desktop.ui.components.UiImageView
import com.lagradost.desktop.ui.fluent.Appearance
import com.lagradost.desktop.ui.fluent.Backdrop
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentMotion
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.IconButton
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.NavItem
import com.lagradost.desktop.ui.fluent.NavPaneCompact
import com.lagradost.desktop.ui.fluent.NavPaneOpen
import com.lagradost.desktop.ui.fluent.NavPosition
import com.lagradost.desktop.ui.fluent.NavStyle
import com.lagradost.desktop.ui.fluent.NavigationPane
import com.lagradost.desktop.ui.fluent.Tooltip
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction
import com.lagradost.desktop.ui.screens.details.DetailsScreen
import com.lagradost.desktop.ui.screens.downloads.DownloadsScreen
import com.lagradost.desktop.ui.screens.extensions.ExtensionsScreen
import com.lagradost.desktop.ui.screens.home.HomeScreen
import com.lagradost.desktop.ui.screens.library.LibraryScreen
import com.lagradost.desktop.ui.screens.player.PlayerScreen
import com.lagradost.desktop.ui.screens.search.SearchScreen
import com.lagradost.desktop.ui.screens.settings.SettingsScreen
import com.lagradost.desktop.ui.screens.common.SectionPage
import kotlinx.coroutines.delay

/** Cross page UI state of the shell */
val TopBarHeight = 52.dp

object ShellState {
    /** The top bar is see-through (pages with a full-bleed header) until the page scrolls */
    var topBarSolid by mutableStateOf(true)

    val searchFocus = FocusRequester()

    /** The window title (the player shows what is playing) */
    var windowTitle by mutableStateOf("CloudStream")
    var searchText by mutableStateOf("")

    /** Artwork whose colours tint the window behind the pages (Appearance > Backdrop > Ambient) */
    var ambientUrl by mutableStateOf<String?>(null)
    var ambientHeaders by mutableStateOf<Map<String, String>?>(null)
}

/** Pages with a full-bleed header call this: the top bar becomes solid once [scrolled] */
@Composable
fun TopBarOverlay(scrolled: Boolean) {
    LaunchedEffect(scrolled) { ShellState.topBarSolid = scrolled }
    DisposableEffect(Unit) { onDispose { ShellState.topBarSolid = true } }
}

/** A page names the artwork it shows; the ambient backdrop takes its colours */
@Composable
fun AmbientArtwork(url: String?, headers: Map<String, String>? = null) {
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        ShellState.ambientUrl = url
        ShellState.ambientHeaders = headers
    }
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
            AmbientBackdrop()
            val position = Appearance.navPosition
            val style = Appearance.navStyle
            val railRoom = if (style == NavStyle.Labels) NavPaneOpen.dp else NavPaneCompact.dp
            when (position) {
                NavPosition.Left -> {
                    Row(Modifier.fillMaxSize()) {
                        // the rail keeps its room all the time; the hover pane opens over the page, so the page never jumps
                        Box(Modifier.width(railRoom))
                        ContentArea(RoundedCornerShape(topStart = FluentShapes.overlay))
                    }
                    SideRail(Modifier.align(Alignment.CenterStart), end = false)
                }
                NavPosition.Right -> {
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) { ContentArea(RoundedCornerShape(topEnd = FluentShapes.overlay)) }
                        Box(Modifier.width(railRoom))
                    }
                    SideRail(Modifier.align(Alignment.CenterEnd), end = true)
                }
                NavPosition.Top -> ContentArea(RoundedCornerShape(0.dp), topNav = true)
                NavPosition.Bottom -> {
                    ContentArea(RoundedCornerShape(0.dp))
                    BottomDock(Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        ToastLayer()
    }
}

/** Artwork colours behind everything: the page's artwork decoded tiny and stretched, under a scrim */
@Composable
private fun AmbientBackdrop() {
    val c = Fluent.colors
    if (Appearance.backdrop != Backdrop.Ambient) return
    Crossfade(ShellState.ambientUrl, animationSpec = FluentMotion.tweenStd(700), label = "ambient") { url ->
        if (url != null) SoftImage(url, ShellState.ambientHeaders, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.3f; scaleY = 1.3f }, alpha = if (c.dark) 0.5f else 0.35f, tiny = true)
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c.bg.copy(alpha = 0.5f), c.bg.copy(alpha = 0.8f), c.bg.copy(alpha = 0.92f)))))
}

/** The page layer: rounded where it meets the navigation, see-through to the ambient backdrop */
@Composable
private fun ContentArea(shape: Shape, topNav: Boolean = false) {
    val c = Fluent.colors
    val layer = if (Appearance.backdrop == Backdrop.Ambient) c.layer.copy(alpha = if (c.dark) 0.62f else 0.7f) else c.layer
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(layer, shape)
            .border(Dp.Hairline, c.stroke, shape),
    ) {
        // with the dock the page ends above it (the dock still floats over the window's edge)
        Box(Modifier.fillMaxSize().padding(bottom = if (Appearance.navPosition == NavPosition.Bottom) 78.dp else 0.dp)) { PageHost() }
        TopBar(topNav)
    }
}

/**
 * Side rail (left or right). Hover style: a narrow icon rail that opens (with the names) over the page while the pointer is
 * on it; Icons: never opens; Labels: always open, the page leaves room for it.
 */
@Composable
private fun SideRail(modifier: Modifier, end: Boolean) {
    val c = Fluent.colors
    val style = Appearance.navStyle
    var hovered by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    // a short delay each way: sweeping the pointer across the edge does not flash the pane
    LaunchedEffect(hovered, style) {
        when (style) {
            NavStyle.Labels -> open = true
            NavStyle.Icons -> open = false
            NavStyle.Hover -> if (hovered) { delay(150); open = true } else { delay(250); open = false }
        }
    }
    val floating = open && style == NavStyle.Hover
    val shape = if (end) RoundedCornerShape(topStart = FluentShapes.overlay, bottomStart = FluentShapes.overlay) else RoundedCornerShape(topEnd = FluentShapes.overlay, bottomEnd = FluentShapes.overlay)
    val glass = Appearance.glass
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
        showBack = !com.lagradost.desktop.platform.WinChrome.windowedBar,
        header = { BrandMark(open) },
        modifier = modifier
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
            .shadow(if (floating) 24.dp else 0.dp, shape, clip = false)
            .background(if (floating) c.flyout else Color.Transparent, shape)
            .then(if (floating) Modifier.border(Dp.Hairline, c.stroke, shape) else Modifier),
    )
}

@Composable
private fun PageHost() {
    val holder = rememberSaveableStateHolder()
    val entry = Navigator.current
    AnimatedContent(
        targetState = entry,
        transitionSpec = {
            if (Navigator.lastWasBack) (fadeIn(FluentMotion.tweenIn(220)) + scaleIn(FluentMotion.tweenIn(260), initialScale = 1.015f)) togetherWith fadeOut(FluentMotion.tweenOut(110))
            else (fadeIn(FluentMotion.tweenIn(260)) + slideInVertically(FluentMotion.tweenIn(340)) { 36 } + scaleIn(FluentMotion.tweenIn(340), initialScale = 0.992f)) togetherWith fadeOut(FluentMotion.tweenOut(110))
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
    // these pages have a big header of their own
    Route.Library -> null
    Route.Downloads -> null
    Route.Extensions -> null
    is Route.Settings -> null
    is Route.Details -> null
    is Route.Section -> null
    is Route.Player -> null
    is Route.Icons -> "Icons"
    Route.Setup -> null
}

/**
 * The bar over the page: title, global search, the Home provider. With the navigation at the top it also carries the
 * page tabs; with the dock at the bottom it carries Back and the profile.
 */
@Composable
private fun TopBar(topNav: Boolean) {
    val c = Fluent.colors
    val solid = ShellState.topBarSolid
    val glass = Appearance.glass
    val bg by androidx.compose.animation.animateColorAsState(
        if (solid) (if (glass) c.layer.copy(alpha = 0.86f) else c.layer) else Color.Transparent,
        FluentMotion.tweenStd(220), label = "topbar",
    )
    val dock = Appearance.navPosition == NavPosition.Bottom
    Box(
        Modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .background(bg)
            .padding(horizontal = 12.dp),
    ) {
        val onHome = Navigator.current.route is Route.Home
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            // the title bar above the app has Back while the window is not maximized
            if ((topNav || dock) && !com.lagradost.desktop.platform.WinChrome.windowedBar) {
                IconButton(Icons.Back, { Navigator.back() }, Modifier.noWindowDrag("topBack"), enabled = Navigator.canGoBack, tooltip = "Back (Alt+Left)")
                Box(Modifier.width(6.dp))
            }
            if (topNav) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (item in navItems) TopTab(item)
                }
                Box(Modifier.width(12.dp))
            } else {
                val title = titleOf(Navigator.current.route)
                if (title != null) FText(title, style = Fluent.type.subtitle, maxLines = 1, modifier = Modifier.widthIn(max = 320.dp))
            }
            Box(Modifier.weight(1f))
            // on the Home page the box searches the extension chosen at the right; everywhere else all of them
            Box(Modifier.weight(3f).widthIn(min = 180.dp, max = 520.dp)) {
                GlobalSearchBox(Modifier.fillMaxWidth().noWindowDrag("search"), only = if (onHome) selectedHomeProvider() else (Navigator.current.route as? Route.Search)?.only)
            }
            Box(Modifier.weight(1f))
            if (onHome) {
                ProviderSelector(Modifier.widthIn(max = 230.dp).noWindowDrag("provider"))
            }
            if (topNav) {
                Box(Modifier.width(6.dp))
                for (item in footerItems) TopTab(item, iconOnly = true)
            }
            if (topNav || dock) {
                Box(Modifier.width(6.dp))
                AccountAvatar()
            }
            // the caption buttons sit over a right-hand rail: only what the rail does not cover is left free
            val railRoom = if (Appearance.navStyle == NavStyle.Labels) NavPaneOpen.dp else NavPaneCompact.dp
            Box(Modifier.width(if (Appearance.navPosition == NavPosition.Right) (captionInset - railRoom).coerceAtLeast(0.dp) else captionInset))
        }
    }
}

/** A page tab of the top navigation: icon + name, the selected one on an accent pill */
@Composable
private fun TopTab(item: NavItem, iconOnly: Boolean = false) {
    val c = Fluent.colors
    val selected = Navigator.selectedTab.id() == item.id
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(50)
    val bg by androidx.compose.animation.animateColorAsState(
        when {
            selected -> c.accent.copy(alpha = if (c.dark) 0.18f else 0.14f)
            hovered -> c.subtleHover
            else -> Color.Transparent
        }, FluentMotion.tweenStd(160), label = "tab",
    )
    val body = @Composable {
        Row(
            Modifier.height(34.dp).clip(shape).background(bg, shape).noWindowDrag("tab-" + item.id)
                .fluentClickable(source, true, shape, Role.Tab) { Navigator.goTab(tabOf(item.id)) }
                .padding(horizontal = if (iconOnly) 9.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(item.glyph, size = 16.dp, tint = if (selected) c.accentText else c.textSecondary)
            if (!iconOnly) {
                Box(Modifier.width(8.dp))
                FText(item.label, style = if (selected) Fluent.type.bodyStrong else Fluent.type.body, color = if (selected) c.text else c.textSecondary, maxLines = 1, softWrap = false)
            }
        }
    }
    if (iconOnly) Tooltip(item.label) { body() } else body()
}

/** The floating dock at the bottom: a glass pill with every page, the selected one lifted on an accent pill */
@Composable
private fun BottomDock(modifier: Modifier) {
    val c = Fluent.colors
    val shape = RoundedCornerShape((FluentShapes.overlay * 1.4f).coerceAtLeast(16.dp))
    val glass = Appearance.glass
    Row(
        modifier
            .padding(bottom = 14.dp)
            .shadow(28.dp, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(c.flyout, shape)
            .border(Dp.Hairline, c.strokeStrong.copy(alpha = 0.35f), shape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (item in navItems + footerItems) DockItem(item)
    }
}

@Composable
private fun DockItem(item: NavItem) {
    val c = Fluent.colors
    val selected = Navigator.selectedTab.id() == item.id
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val lift by animateFloatAsState(if (hovered && Appearance.hoverZoom) 1.12f else 1f, FluentMotion.tweenIn(180), label = "dock")
    val pill by animateDpAsState(if (selected) 26.dp else 0.dp, FluentMotion.tweenIn(260), label = "pill")
    val shape = RoundedCornerShape(FluentShapes.card.coerceAtLeast(10.dp))
    Tooltip(item.label) {
        Column(
            Modifier.width(64.dp).clip(shape)
                .background(if (hovered) c.subtleHover else Color.Transparent, shape)
                .fluentClickable(source, true, shape, Role.Tab) { Navigator.goTab(tabOf(item.id)) }
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.height(30.dp).graphicsLayer { scaleX = lift; scaleY = lift }, contentAlignment = Alignment.Center) {
                Box(Modifier.width(pill + 18.dp).height(30.dp).background(if (selected) c.accent.copy(alpha = if (c.dark) 0.22f else 0.16f) else Color.Transparent, RoundedCornerShape(50)))
                Icon(item.glyph, size = 18.dp, tint = if (selected) c.accentText else c.textSecondary)
            }
            FText(item.label, style = Fluent.type.caption, color = if (selected) c.text else c.textTertiary, maxLines = 1, softWrap = false)
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
    Box(Modifier.size(32.dp).clip(CircleShape).background(c.control).border(Dp.Hairline, c.stroke, CircleShape).noWindowDrag("avatar").fluentClickable(rememberInteraction(), true, CircleShape, Role.Button) { showAccountPicker() }, contentAlignment = Alignment.Center) {
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
            .fluentClickable(source, true, shape, Role.Button) { showAccountPicker() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(c.control).border(Dp.Hairline, c.stroke, CircleShape), contentAlignment = Alignment.Center) {
                if (a != null) UiImageView(a.image, a.name, Modifier.size(28.dp)) else FText("?", color = c.textSecondary)
            }
        }
        if (expanded) FText(a?.name ?: "Profile", Modifier.padding(end = 12.dp), maxLines = 1, softWrap = false)
    }
}

/** Fluent info bars for the engine's toasts (top right, slide in, auto dismiss) */
@Composable
private fun ToastLayer() {
    val c = Fluent.colors
    Box(Modifier.fillMaxSize().padding(top = 60.dp, end = 16.dp), contentAlignment = Alignment.TopEnd) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (toast in Toasts.current.toList()) {
                androidx.compose.runtime.key(toast.id) {
                    LaunchedEffect(toast.id) {
                        delay(if (toast.long) 7000 else 3500)
                        Toasts.dismiss(toast)
                    }
                    val shown = remember { androidx.compose.animation.core.MutableTransitionState(false) }.apply { targetState = true }
                    AnimatedVisibility(shown, enter = fadeIn(FluentMotion.tweenIn(200)) + slideInHorizontally(FluentMotion.tweenIn(320)) { it / 3 }) {
                        val shape = RoundedCornerShape(FluentShapes.card)
                        Row(
                            Modifier
                                .widthIn(max = 420.dp)
                                .shadow(18.dp, shape, clip = false)
                                .background(c.flyout, shape)
                                .border(Dp.Hairline, c.strokeStrong.copy(alpha = 0.4f), shape)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Info, size = 16.dp, tint = c.accentText)
                            Box(Modifier.width(12.dp))
                            FText(toast.text, maxLines = 6)
                        }
                    }
                }
            }
        }
    }
}

private val brandLogo by lazy {
    runCatching { androidx.compose.ui.graphics.painter.BitmapPainter(Thread.currentThread().contextClassLoader.getResourceAsStream("app-icon.png")!!.use { androidx.compose.ui.res.loadImageBitmap(it) }) }.getOrNull()
}

/** The app's mark at the top of the rail; the name joins it when the rail is open */
@Composable
private fun BrandMark(expanded: Boolean) {
    val c = Fluent.colors
    Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        brandLogo?.let { androidx.compose.foundation.Image(it, "CloudStream", Modifier.size(32.dp)) }
        if (expanded) {
            Box(Modifier.width(12.dp))
            FText("CloudStream", style = Fluent.type.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = c.text, maxLines = 1, softWrap = false)
        }
    }
}
