package com.lagradost.desktop.ui.fluent

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.ui.shell.noWindowDrag

class NavItem(val id: String, val label: String, val glyph: String)

const val NavPaneOpen = 240
const val NavPaneCompact = 48

/** Windows 11 NavigationView pane: back + hamburger on top, items, footer items at the bottom */
@Composable
fun NavigationPane(
    items: List<NavItem>,
    footerItems: List<NavItem>,
    selectedId: String,
    expanded: Boolean,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onSelect: (NavItem) -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
    showMenuButton: Boolean = true,
) {
    val width by animateDpAsState(if (expanded) NavPaneOpen.dp else NavPaneCompact.dp, tween(167))
    Column(modifier.width(width).fillMaxHeight().padding(vertical = 4.dp).noWindowDrag("pane")) {
        NavButton(Icons.Back, "", expanded, false, canGoBack, onBack, tooltip = "Back (Alt+Left)")
        if (showMenuButton) NavButton(Icons.Menu, "", expanded, false, true, onToggle, tooltip = if (expanded) "Close navigation" else "Open navigation")
        Box(Modifier.height(4.dp))
        for (item in items) NavButton(item.glyph, item.label, expanded, item.id == selectedId, true, { onSelect(item) }, tooltip = if (expanded) null else item.label)
        Spacer(Modifier.weight(1f))
        for (item in footerItems) NavButton(item.glyph, item.label, expanded, item.id == selectedId, true, { onSelect(item) }, tooltip = if (expanded) null else item.label)
        footer()
    }
}

@Composable
private fun NavButton(
    glyph: String,
    label: String,
    expanded: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    tooltip: String? = null,
) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(FluentShapes.control)
    val body = @Composable {
        Box(Modifier.padding(horizontal = 4.dp, vertical = 1.dp).fillMaxWidth().height(40.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(shape)
                    .background(
                        when {
                            selected -> c.subtleHover
                            hovered && enabled -> c.subtleHover
                            else -> androidx.compose.ui.graphics.Color.Transparent
                        }, shape,
                    )
                    .fluentClickable(source, enabled, shape, Role.Tab, onClick),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(glyph, size = 16.dp, tint = if (enabled) c.text else c.textDisabled)
                }
                if (expanded && label.isNotEmpty()) {
                    FText(label, Modifier.padding(end = 12.dp), color = if (enabled) c.text else c.textDisabled, maxLines = 1, softWrap = false)
                }
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.CenterStart).padding(start = 0.dp).size(3.dp, 16.dp)
                        .background(c.accent, RoundedCornerShape(2.dp)),
                )
            }
        }
    }
    if (tooltip != null) Tooltip(tooltip) { body() } else body()
}

/** Pivot style tabs (underline on the selected one) */
@Composable
fun TabBar(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Fluent.colors
    Row(modifier) {
        tabs.forEachIndexed { i, text ->
            val source = rememberInteraction()
            val hovered by source.collectIsHoveredAsState()
            val shape = RoundedCornerShape(FluentShapes.control)
            Column(
                Modifier
                    .padding(end = 4.dp)
                    .clip(shape)
                    .background(if (hovered) c.subtleHover else androidx.compose.ui.graphics.Color.Transparent, shape)
                    .fluentClickable(source, true, shape, Role.Tab) { onSelect(i) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FText(text, style = if (i == selected) Fluent.type.bodyStrong else Fluent.type.body,
                    color = if (i == selected) c.text else c.textSecondary, maxLines = 1, softWrap = false)
                Box(Modifier.height(4.dp))
                Box(Modifier.width(if (i == selected) 24.dp else 0.dp).height(3.dp).background(c.accent, RoundedCornerShape(2.dp)))
            }
        }
    }
}
