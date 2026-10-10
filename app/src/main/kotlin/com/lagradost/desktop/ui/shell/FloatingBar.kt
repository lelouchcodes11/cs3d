package com.lagradost.desktop.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.ui.fluent.Appearance
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentMotion
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.IconButton
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.NavItem
import com.lagradost.desktop.ui.fluent.Tooltip
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction
import kotlin.math.roundToInt

/**
 * The top bar of the "Floating bar" look: nothing but what is needed, floating over the page. At the left the app's mark (and Back when the title
 * bar has none), in the middle a capsule with the pages (a light pill glides to the page you choose), at the right what belongs to the page (on
 * Home: customise, reload and the extension) and your profile. It has no background of its own: over artwork the page shows through, over a page
 * that has scrolled a soft fade of the page colour keeps the capsule and the buttons readable.
 */
@Composable
internal fun FloatingTopBar() {
    val c = Fluent.colors
    val fade by animateFloatAsState(if (ShellState.topBarSolid) 1f else 0f, FluentMotion.tweenStd(260), label = "barFade")
    val onHome = Navigator.current.route is com.lagradost.desktop.core.Route.Home
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .drawBehind {
                if (fade > 0.01f) {
                    // the page colour fading out below the bar (it reaches a little lower than the bar itself)
                    val reach = size.height + 30.dp.toPx()
                    drawRect(
                        Brush.verticalGradient(0f to c.bg.copy(alpha = 0.97f * fade), 0.62f to c.bg.copy(alpha = 0.9f * fade), 1f to Color.Transparent, endY = reach),
                        size = Size(size.width, reach),
                    )
                }
            },
    ) {
        // what is at the right decides how much room the capsule has: on Home the extension chooser is wide
        // the same on every page, so the capsule does not move or change when you go from Home to another page
        val rightNeed = if (ShellState.homeSearchOpen && onHome) 640.dp else 404.dp
        val wide = maxWidth / 2 >= rightNeed + 250.dp
        val tight = maxWidth / 2 < rightNeed + 150.dp
        Row(Modifier.fillMaxSize().padding(start = 14.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!com.lagradost.desktop.platform.WinChrome.windowedBar) {
                IconButton(Icons.Back, { Navigator.back() }, Modifier.noWindowDrag("topBack"), enabled = Navigator.canGoBack, tooltip = "Back (Alt+Left)")
                Box(Modifier.width(6.dp))
            }
            brandLogo?.let { androidx.compose.foundation.Image(it, "CloudStream", Modifier.size(26.dp)) }
            Box(Modifier.weight(1f))
            if (onHome) {
                selectedHomeProvider()?.let { ExtensionSearch(it) }
                ShellState.homeActions?.let { a ->
                    IconButton(Icons.Refresh, a.reload, Modifier.noWindowDrag("reload"), tooltip = "Reload the home page", kind = ButtonKind.Subtle, size = 36.dp)
                    Box(Modifier.width(6.dp))
                }
                ProviderSelector(Modifier.widthIn(max = 200.dp).noWindowDrag("provider"))
                Box(Modifier.width(8.dp))
            }
            AccountAvatar()
            Box(Modifier.width(captionInset))
        }
        NavCapsule(
            compact = !wide,
            modifier = if (tight) Modifier.align(Alignment.CenterStart).padding(start = 64.dp) else Modifier.align(Alignment.Center),
        )
    }
}

/**
 * Home: a search that looks only in the extension shown there (the Search page looks in all of them). A button that opens into a box;
 * Ctrl+K opens it too, and it closes when it loses the focus.
 */
@Composable
private fun ExtensionSearch(provider: String) {
    if (ShellState.homeSearchOpen) {
        androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { ShellState.searchFocus.requestFocus() } }
        // it closes with its button, Esc, a search, or by itself when it loses the focus with nothing typed
        GlobalSearchBox(Modifier.width(280.dp).noWindowDrag("extSearch"), only = provider, onBlur = { if (ShellState.searchText.isBlank()) ShellState.homeSearchOpen = false }, onDone = { ShellState.homeSearchOpen = false })
        Box(Modifier.width(4.dp))
        IconButton(Icons.Close, { ShellState.homeSearchOpen = false }, Modifier.noWindowDrag("extSearchClose"), tooltip = "Close the search (Esc)", kind = ButtonKind.Subtle, size = 36.dp)
    } else {
        IconButton(Icons.Search, { ShellState.homeSearchOpen = true }, Modifier.noWindowDrag("extSearchButton"), tooltip = "Search in $provider (Ctrl+K)", kind = ButtonKind.Subtle, size = 36.dp)
    }
    Box(Modifier.width(6.dp))
}

/** The pages in one capsule: a light pill glides (spring) from the page you leave to the one you open; names show beside the icons while there is room */
@Composable
private fun NavCapsule(compact: Boolean, modifier: Modifier) {
    val c = Fluent.colors
    val items = navItems + footerItems
    val selected = items.indexOfFirst { it.id == Navigator.selectedTab.id() }
    // where each item sits inside the capsule (px), so the pill can glide between them
    val spots = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val target = spots[selected]
    val spec = if (Appearance.motion == com.lagradost.desktop.ui.fluent.Motion.Off) androidx.compose.animation.core.snap<Float>() else spring(dampingRatio = 0.78f, stiffness = 420f)
    val pillX by animateFloatAsState(target?.first ?: 0f, spec, label = "pillX")
    val pillW by animateFloatAsState(target?.second ?: 0f, spec, label = "pillW")
    val density = LocalDensity.current
    val shape = RoundedCornerShape(26.dp)
    Box(
        // a maximized window has no title bar of its own: the top band answers Windows as a caption (drag, double click) unless a control says otherwise
        modifier
            .noWindowDrag("capsule")
            .shadow(24.dp, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Color(0xD90B0B10), shape)
            .border(Dp.Hairline, Color(0x2BFFFFFF), shape)
            .padding(4.dp),
    ) {
        if (target != null && selected >= 0) {
            Box(
                Modifier
                    .offset { IntOffset(pillX.roundToInt(), 0) }
                    .width(with(density) { pillW.toDp() })
                    .height(38.dp)
                    .background(Color(0x2EFFFFFF), RoundedCornerShape(19.dp)),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { i, item ->
                if (i == navItems.size) Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(18.dp).background(Color(0x26FFFFFF)))
                CapsuleItem(item, selected = i == selected, showName = i < navItems.size && (!compact || i == selected), onPlace = { x, w -> spots[i] = x to w })
            }
        }
    }
}

@Composable
private fun CapsuleItem(item: NavItem, selected: Boolean, showName: Boolean, onPlace: (Float, Float) -> Unit) {
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(19.dp)
    val tint by androidx.compose.animation.animateColorAsState(
        if (selected) Color.White else if (hovered) Color(0xE6FFFFFF) else Color(0x99FFFFFF),
        FluentMotion.tweenStd(160), label = "tint",
    )
    val body = @Composable {
        Row(
            Modifier
                .height(38.dp)
                .clip(shape)
                .background(if (hovered && !selected) Color(0x14FFFFFF) else Color.Transparent, shape)
                .fluentClickable(source, true, shape, Role.Tab) { Navigator.goTab(tabOf(item.id)) }
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(item.glyph, size = 18.dp, tint = tint)
            AnimatedVisibility(
                showName,
                enter = fadeIn(FluentMotion.tweenIn(200)) + expandHorizontally(FluentMotion.tweenIn(260)),
                exit = fadeOut(FluentMotion.tweenOut(120)) + shrinkHorizontally(FluentMotion.tweenOut(200)),
            ) {
                Row {
                    Box(Modifier.width(8.dp))
                    FText(item.label, style = Fluent.type.bodyStrong, color = tint, maxLines = 1, softWrap = false)
                }
            }
        }
    }
    // names that are not shown (a narrow window, the two utility pages) are in a tip
    // the place is reported by a box that is a direct child of the capsule row: inside the tooltip wrapper the position is relative to the wrapper, which put the
    // light pill at the left end (over Home) for every item that shows no name, Settings always
    Box(Modifier.onGloballyPositioned { onPlace(it.positionInParent().x, it.size.width.toFloat()) }) {
        if (showName) body() else Tooltip(item.label) { body() }
    }
}
