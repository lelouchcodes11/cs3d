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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.requiredSize
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
import com.lagradost.desktop.ui.fluent.Themes
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

    /** Home (floating bar look): the search of the extension shown there is open as a box instead of a button */
    var homeSearchOpen by mutableStateOf(false)

    /** The bottom dock slides away while a page is scrolled down and comes back when it is scrolled up (or the pointer goes to the bottom edge) */
    var dockHidden by mutableStateOf(false)

    /** The window title (the player shows what is playing) */
    var windowTitle by mutableStateOf("CloudStream")
    var searchText by mutableStateOf("")

    /** Artwork whose colours tint the window behind the pages (Appearance > Backdrop > Ambient) */
    var ambientUrl by mutableStateOf<String?>(null)
    var ambientHeaders by mutableStateOf<Map<String, String>?>(null)

    /** What the Home page offers the top bar (the floating-dock look has no header of its own on Home) */
    var homeActions by mutableStateOf<HomeActions?>(null)
}

class HomeActions(val customise: (() -> Unit)?, val reload: () -> Unit)

/** The floating dock's width plus its margins: pages without artwork under the dock start after it, full-bleed pages use [LocalDockStart] for their text */
val DockRoom = 92.dp

/** How far from the window's left edge a full-bleed page (Home, a title page) starts its content: 0 unless the floating dock floats over the artwork */
val LocalDockStart = androidx.compose.runtime.staticCompositionLocalOf { 0.dp }

/** Pages with a full-bleed header call this: the top bar becomes solid once [scrolled] */
@Composable
fun TopBarOverlay(scrolled: Boolean) {
    // two pages are composed while one replaces the other: the one that goes must not hand the bar back to "solid" after the new one made it see-through
    val token = remember { Any() }
    LaunchedEffect(scrolled) { overlayOwner = token; ShellState.topBarSolid = scrolled }
    DisposableEffect(Unit) { onDispose { if (overlayOwner === token) { overlayOwner = null; ShellState.topBarSolid = true } } }
}

private var overlayOwner: Any? = null

/** A page names the artwork it shows; the ambient backdrop takes its colours */
@Composable
fun AmbientArtwork(url: String?, headers: Map<String, String>? = null) {
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        ShellState.ambientUrl = url
        ShellState.ambientHeaders = headers
    }
}

internal val navItems = listOf(
    NavItem("home", "Home", Icons.Home),
    NavItem("search", "Search", Icons.Search),
    NavItem("library", "Library", Icons.Library),
    NavItem("downloads", "Downloads", Icons.Download),
)
internal val footerItems = listOf(
    NavItem("settings", "Settings", Icons.Settings),
)

internal fun tabOf(id: String): Tab = when (id) {
    "home" -> Tab.Home
    "search" -> Tab.Search
    "library" -> Tab.Library
    "downloads" -> Tab.Downloads
    "extensions" -> Tab.Extensions
    else -> Tab.Settings
}

internal fun Tab.id(): String = when (this) {
    Tab.Home -> "home"
    Tab.Search -> "search"
    Tab.Library -> "library"
    Tab.Downloads -> "downloads"
    Tab.Extensions -> "extensions"
    Tab.Settings -> "settings"
}

/** Room the floating bottom dock takes at the bottom of a page (0 with any other navigation): scrolling pages add it to their end padding so the last row can be lifted clear of the dock */
val LocalDockInset = androidx.compose.runtime.staticCompositionLocalOf { 0.dp }

private val DockInset = 78.dp

@Composable
fun AppShell() {
    val c = Fluent.colors
    // 1 = the bottom dock is shown, 0 = it has slid away; a new page always shows it
    // (only when "Hide the dock while scrolling" is on in Settings)
    val dockShown by animateFloatAsState(if (Appearance.dockAutoHide && ShellState.dockHidden) 0f else 1f, FluentMotion.tweenIn(220), label = "dockShown")
    LaunchedEffect(Navigator.current.id) { ShellState.dockHidden = false }
    Box(Modifier.fillMaxSize().background(c.bg)) {
        ScreenWarmup()
        SupportPrompt.Effect()
        if (Navigator.current.route is Route.Player || Navigator.current.route is Route.Setup) {
            // the player and the setup wizard own the whole window
            PageHost()
        } else {
            AmbientBackdrop()
            val position = Appearance.navPosition
            val style = Appearance.navStyle
            val railRoom = if (style == NavStyle.Labels) NavPaneOpen.dp else NavPaneCompact.dp
            when (position) {
                // edge to edge: the page runs under the floating bar (a capsule at the top) or the floating dock (a pill at the left)
                NavPosition.Floating -> ContentArea(RoundedCornerShape(0.dp), edge = true)
                NavPosition.Dock -> {
                    ContentArea(RoundedCornerShape(0.dp), edge = true)
                    FloatingDock(Modifier.align(Alignment.CenterStart).padding(start = 16.dp))
                }
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
                    // with auto hide the dock comes back when the pointer goes to the bottom edge (and stays while it is on the dock)
                    if (Appearance.dockAutoHide) Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(40.dp).pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                    if (e.type == androidx.compose.ui.input.pointer.PointerEventType.Move || e.type == androidx.compose.ui.input.pointer.PointerEventType.Enter) ShellState.dockHidden = false
                                }
                            }
                        },
                    )
                    BottomDock(Modifier.align(Alignment.BottomCenter), dockShown)
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
private fun ContentArea(shape: Shape, topNav: Boolean = false, edge: Boolean = false) {
    val c = Fluent.colors
    val layer = if (Appearance.backdrop == Backdrop.Ambient) c.layer.copy(alpha = if (c.dark) 0.62f else 0.7f) else if (edge) c.bg else c.layer
    val preset = Themes.byId(Appearance.theme)
    val glowA = preset.glowA?.takeIf { Appearance.themeGlow && c.dark && Appearance.backdrop != Backdrop.Black && !edge }
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(layer, shape)
            // two soft colour glows of the theme, in the corners of the page
            .drawBehind {
                if (glowA != null) {
                    drawRect(Brush.radialGradient(listOf(glowA.copy(alpha = 0.22f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.05f), radius = size.width * 0.55f))
                    preset.glowB?.let { b -> drawRect(Brush.radialGradient(listOf(b.copy(alpha = 0.15f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(size.width * 0.95f, size.height * 0.95f), radius = size.width * 0.5f)) }
                }
            }
            .let { if (edge) it else it.border(Dp.Hairline, c.stroke, shape) },
    ) {
        // the user's own picture, dimmed with the page colour so the text stays readable
        if (Appearance.wallpaper.isNotBlank()) {
            val ctx = coil3.compose.LocalPlatformContext.current
            val request = remember(Appearance.wallpaper) { coil3.request.ImageRequest.Builder(ctx).data(java.io.File(Appearance.wallpaper)).size(1920, 1080).build() }
            coil3.compose.AsyncImage(request, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(layer.copy(alpha = Appearance.wallpaperDim)))
        }
        // with the dock the page runs the whole height and scrolls under the floating dock (a page that ended above it left a flat band across the bottom);
        // scrolling pages add LocalDockInset to their end padding
        val dock = Appearance.navPosition == NavPosition.Bottom
        Box(
            Modifier.fillMaxSize()
                .let { if (dock && Appearance.dockAutoHide) it.nestedScroll(dockScroll) else it },
        ) {
            androidx.compose.runtime.CompositionLocalProvider(LocalDockInset provides (if (dock) DockInset else 0.dp)) { PageHost() }
        }
        if (Appearance.navPosition == NavPosition.Floating) FloatingTopBar() else TopBar(topNav)
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
            when {
                // the video page has a window of its own (a heavyweight canvas) that no fade or slide reaches: it stood there, grey or white, while the page faded
                // in around it. Into and out of the video there is no motion at all.
                targetState.route is Route.Player || initialState.route is Route.Player -> androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                // only what moves cheaply: a fade and a short rise (a scale of the whole page redrew it at another size on every frame), a moment late
                Navigator.lastWasBack -> fadeIn(FluentMotion.tweenInAfter(220, 30)) togetherWith fadeOut(FluentMotion.tweenOut(90))
                else -> (fadeIn(FluentMotion.tweenInAfter(240, 30)) + slideInVertically(FluentMotion.tweenInAfter(320, 30)) { 24 }) togetherWith fadeOut(FluentMotion.tweenOut(90))
            }
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

/** Scrolling a page down (some distance, so a nudge does not count) hides the bottom dock, scrolling up brings it back; nothing is consumed */
private val dockScroll = object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
    private var travelled = 0f
    override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
        val dy = available.y
        if (dy < 0f) {
            travelled = minOf(travelled, 0f) + dy
            if (travelled < -48f) ShellState.dockHidden = true
        } else if (dy > 0f) {
            travelled = maxOf(travelled, 0f) + dy
            if (travelled > 24f) ShellState.dockHidden = false
        }
        return androidx.compose.ui.geometry.Offset.Zero
    }
}

/**
 * The first visit of a page cost 100 to 350 ms in which the window did not move (class loading, text layout and the JIT of that page's code;
 * measured with /framestats). A few seconds after the start, once, each of those pages is composed and laid out off screen (never drawn, not
 * reachable by the pointer), one every 1.4 s, so the first real visit finds it warm. Stops when a video starts. -Dcloudstream.warmup=false turns it off.
 */
/** Less than 1.5 GB of the PC's memory is free: the app should not take more for work nobody asked for */
private fun lowOnMemory(): Boolean = runCatching {
    (java.lang.management.ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean).freeMemorySize < 1_500L * 1024 * 1024
}.getOrDefault(false)

/** Returns once 60 frames in a row came within 22 ms of each other (gives up after 20 s) */
private suspend fun awaitCalmFrames() {
    val give = System.currentTimeMillis() + 20_000
    var calm = 0
    var last = androidx.compose.runtime.withFrameNanos { it }
    while (calm < 60 && System.currentTimeMillis() < give) {
        val now = androidx.compose.runtime.withFrameNanos { it }
        calm = if ((now - last) / 1_000_000 <= 22) calm + 1 else 0
        last = now
    }
}

@Composable
private fun ScreenWarmup() {
    if (System.getProperty("cloudstream.warmup") == "false") return
    var step by remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    LaunchedEffect(Unit) {
        com.lagradost.desktop.ui.Startup.revealed.await()
        // the pages are composed off screen to warm them: with little free memory that work is what makes the window stall, so it is left out
        if (lowOnMemory()) return@LaunchedEffect
        // not while the extensions are still loading and updating (several seconds of heavy work in parallel, the pages would only add to it)
        val pm = com.lagradost.cloudstream3.plugins.PluginManager
        val giveUp = System.currentTimeMillis() + 60_000
        while (!(pm.loadedLocalPlugins && pm.loadedOnlinePlugins) && System.currentTimeMillis() < giveUp) delay(500)
        delay(3000)
        for (i in 0 until 5) {
            if (Navigator.current.route is Route.Player || lowOnMemory()) break
            // only while the window is calm: a page composed while the extensions are loading (or Chromium is starting) would add its own freeze
            awaitCalmFrames()
            step = i
            delay(1400)
        }
        step = -1
    }
    if (step < 0) return
    Box(Modifier.requiredSize(1100.dp, 700.dp).graphicsLayer { translationX = -30000f }.drawWithContent { }) {
        when (step) {
            0 -> LibraryScreen()
            1 -> ExtensionsScreen()
            2 -> SettingsScreen(Route.Settings())
            3 -> DownloadsScreen()
            4 -> SearchScreen(Route.Search())
        }
    }
}

@Composable
private fun Page(entry: Entry) {
    val r = entry.route
    // floating dock: pages with artwork (Home, a title) run under it and keep their text clear of it; the others start after it
    val floating = Appearance.navPosition == NavPosition.Dock && r !is Route.Player && r !is Route.Setup
    val bleed = r is Route.Home || r is Route.Details
    if (floating && bleed) androidx.compose.runtime.CompositionLocalProvider(LocalDockStart provides DockRoom) { PageBody(entry) }
    else if (floating) Box(Modifier.fillMaxSize().padding(start = DockRoom)) { PageBody(entry) }
    else PageBody(entry)
}

@Composable
private fun PageBody(entry: Entry) {
    when (val r = entry.route) {
        Route.Home -> HomeScreen()
        is Route.Search -> SearchScreen(r)
        is Route.Person -> com.lagradost.desktop.ui.screens.person.PersonScreen(r)
        Route.History -> com.lagradost.desktop.ui.screens.history.HistoryScreen()
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
    is Route.Person -> null
    Route.History -> null
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
        if (solid) (if (glass && Appearance.navPosition != NavPosition.Floating) c.layer.copy(alpha = 0.86f) else (if (Appearance.navPosition == NavPosition.Floating) c.bg else c.layer).copy(alpha = 0.96f)) else Color.Transparent,
        FluentMotion.tweenStd(220), label = "topbar",
    )
    val dock = Appearance.navPosition == NavPosition.Bottom
    val floating = Appearance.navPosition == NavPosition.Dock
    Box(
        Modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .background(bg)
            .padding(horizontal = 12.dp),
    ) {
        val onHome = Navigator.current.route is Route.Home
        // the Search page has its own big box until a search is made: a second one at the top would only repeat it
        val searchLanding = (Navigator.current.route as? Route.Search)?.let { it.query.isNullOrBlank() } == true
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            // the title bar above the app has Back while the window is not maximized
            if ((topNav || dock || floating) && !com.lagradost.desktop.platform.WinChrome.windowedBar) {
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
            // the floating-dock look has no search box up here (Search is in the dock, Ctrl+K reaches it); every other look keeps it:
            // on the Home page the box searches the extension chosen at the right, everywhere else all of them
            if (!floating) {
                Box(Modifier.weight(3f).widthIn(min = 180.dp, max = 520.dp)) {
                    if (!searchLanding) GlobalSearchBox(Modifier.fillMaxWidth().noWindowDrag("search"), only = if (onHome) selectedHomeProvider() else (Navigator.current.route as? Route.Search)?.only)
                }
                Box(Modifier.weight(1f))
            }
            if (onHome) {
                if (floating) ShellState.homeActions?.let { a ->
                    IconButton(Icons.Refresh, a.reload, Modifier.noWindowDrag("reload"), tooltip = "Reload the home page", kind = com.lagradost.desktop.ui.fluent.ButtonKind.Subtle, size = 36.dp)
                    Box(Modifier.width(6.dp))
                }
                ProviderSelector(Modifier.widthIn(max = 230.dp).noWindowDrag("provider"))
            }
            if (topNav) {
                Box(Modifier.width(6.dp))
                for (item in footerItems) TopTab(item, iconOnly = true)
            }
            if (topNav || dock || floating) {
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
            selected -> if (c.dark) Color(0x1FFFFFFF) else Color(0x14000000)
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
            Icon(item.glyph, size = 16.dp, tint = if (selected) c.text else c.textSecondary)
            if (!iconOnly) {
                Box(Modifier.width(8.dp))
                FText(item.label, style = if (selected) Fluent.type.bodyStrong else Fluent.type.body, color = if (selected) c.text else c.textSecondary, maxLines = 1, softWrap = false)
            }
        }
    }
    if (iconOnly) Tooltip(item.label) { body() } else body()
}

/**
 * The floating dock: one tall dark pill at the left, icons only (the name shows beside the icon on hover), vertically centred over the page. The page
 * runs under it; the selected page has a thin accent bar at the pill's edge and a white icon.
 */
@Composable
private fun FloatingDock(modifier: Modifier) {
    val c = Fluent.colors
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .width(56.dp)
            .shadow(18.dp, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(if (Appearance.glass) Color(0xE60A0A0E) else c.flyout, shape)
            .border(Dp.Hairline, Color(0x24FFFFFF), shape)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (item in navItems + footerItems) FloatingDockItem(item)
    }
}

@Composable
private fun FloatingDockItem(item: NavItem) {
    val c = Fluent.colors
    val selected = Navigator.selectedTab.id() == item.id
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val bar by animateDpAsState(if (selected) 20.dp else 0.dp, FluentMotion.tweenIn(220), label = "dockBar")
    val shape = RoundedCornerShape(14.dp)
    Tooltip(item.label, side = true) {
        Box(Modifier.width(56.dp).height(46.dp), contentAlignment = Alignment.Center) {
            // the mark of the page you are on, at the pill's edge
            Box(Modifier.align(Alignment.CenterStart).padding(start = 4.dp).size(3.dp, bar).clip(RoundedCornerShape(2.dp)).background(c.accent))
            Box(
                Modifier.size(40.dp).clip(shape)
                    .background(if (hovered && !selected) Color(0x14FFFFFF) else Color.Transparent, shape)
                    .fluentClickable(source, true, shape, Role.Tab) { Navigator.goTab(tabOf(item.id)) },
                contentAlignment = Alignment.Center,
            ) { Icon(item.glyph, size = 20.dp, tint = if (selected) Color.White else if (hovered) Color(0xE6FFFFFF) else Color(0x99FFFFFF)) }
        }
    }
}

/** The floating dock at the bottom: a glass pill with every page, the selected one on a quiet neutral pill */
@Composable
private fun BottomDock(modifier: Modifier, shown: Float = 1f) {
    val c = Fluent.colors
    val corner = (FluentShapes.overlay * 1.4f).coerceAtLeast(16.dp)
    val shape = RoundedCornerShape(corner)
    Row(
        modifier
            .padding(bottom = 14.dp)
            // slides down and fades while it is hidden (read inside the layer: no recomposition per frame)
            .graphicsLayer { translationY = (1f - shown) * (size.height + 40.dp.toPx()); alpha = shown }
            // a soft shadow drawn by hand: Modifier.shadow with a large elevation drew a hard dark shape around the dock on some windows
            .drawBehind {
                val r = corner.toPx()
                for (i in 1..5) {
                    val grow = i * 3.5f
                    drawRoundRect(
                        Color.Black.copy(alpha = 0.07f), topLeft = androidx.compose.ui.geometry.Offset(-grow, -grow * 0.4f + 5f),
                        size = androidx.compose.ui.geometry.Size(size.width + grow * 2, size.height + grow * 2 * 0.8f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r + grow),
                    )
                }
            }
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
                Box(Modifier.width(pill + 18.dp).height(30.dp).background(if (selected) (if (c.dark) Color(0x24FFFFFF) else Color(0x14000000)) else Color.Transparent, RoundedCornerShape(50)))
                Icon(item.glyph, size = 18.dp, tint = if (selected) c.text else c.textSecondary)
            }
            FText(item.label, style = Fluent.type.caption, color = if (selected) c.text else c.textTertiary, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
internal fun AccountAvatar() {
    val c = Fluent.colors
    val vm = appVm<HomeViewModel>()
    val account by vm.currentAccount.observeAsState()
    LaunchedEffect(Unit) { vm.currentAccount.value ?: runCatching { com.lagradost.cloudstream3.utils.DataStoreHelper.getCurrentAccount() }.getOrNull()?.let { vm.currentAccount.postValue(it) } }
    val a = account
    Box(Modifier.size(32.dp).clip(CircleShape).background(c.control).border(Dp.Hairline, c.stroke, CircleShape).noWindowDrag("avatar").fluentClickable(rememberInteraction(), true, CircleShape, Role.Button) { showAccountPicker() }, contentAlignment = Alignment.Center) {
        if (a != null) {
            ProfileAvatar(a, 32.dp)
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
                if (a != null) ProfileAvatar(a, 28.dp) else FText("?", color = c.textSecondary)
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

internal val brandLogo by lazy {
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
