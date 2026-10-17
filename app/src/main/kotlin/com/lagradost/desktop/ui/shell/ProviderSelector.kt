package com.lagradost.desktop.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.home.HomeViewModel
import com.lagradost.cloudstream3.utils.AppContextUtils.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.core.appVm
import com.lagradost.desktop.core.observeAsState
import com.lagradost.desktop.stremio.StremioApi
import com.lagradost.desktop.ui.fluent.BelowAnchor
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.FlyoutSurface
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.StremioBadge
import com.lagradost.desktop.ui.fluent.TextBox
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.hairBorder
import com.lagradost.desktop.ui.fluent.rememberInteraction
import com.lagradost.desktop.ui.fluent.tvTypeName

/**
 * The extension whose home page is shown. It sits in the top bar of the Home page and also decides where the
 * search box of that page searches: only in this extension (the Search page of the left bar searches all).
 *
 * A button with the icon and name of the extension; it opens a panel with a search box (type to narrow the list, Up / Down / Enter), the
 * extensions with their icon, language and what they offer, and the Stremio add-ons in a group of their own.
 */
@Composable
fun ProviderSelector(modifier: Modifier = Modifier) {
    val vm = appVm<HomeViewModel>()
    val page by vm.page.observeAsState()
    val apiName by vm.apiName.observeAsState()
    val random = APIRepository.randomApi.name
    val names = remember(page, apiName) {
        val ctx = DesktopBootstrap.activityOrNull()
        runCatching { ctx?.filterProviderByPreferredMedia()?.map { it.name }?.distinct()?.sortedBy { it.lowercase() } }.getOrNull().orEmpty()
    }
    val selected = apiName?.takeIf { it == random || it in names } ?: DataStoreHelper.currentHomePage?.takeIf { it == random || it in names }
    var open by remember { mutableStateOf(false) }
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(FluentShapes.control)
    Box(modifier) {
        Row(
            Modifier.height(34.dp).widthIn(min = 150.dp).background(if (hovered) c.controlHover else c.control, shape)
                .hairBorder(if (hovered || open) c.accent.copy(alpha = 0.6f) else c.strokeStrong.copy(alpha = 0.45f), shape)
                .fluentClickable(source, true, shape, Role.DropdownList) { open = !open }
                .padding(start = 8.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected != null && selected != random) ExtensionIcon(selected, 22) else Icon(Icons.Globe, size = 16.dp, tint = c.accentText, modifier = Modifier.padding(horizontal = 3.dp))
            Box(Modifier.width(8.dp))
            FText(selected ?: "Choose a source", Modifier.weight(1f, fill = false), color = if (selected == null) c.textTertiary else c.text, maxLines = 1)
            if (selected != null && APIHolder.getApiFromNameNull(selected) is StremioApi) {
                Box(Modifier.width(6.dp))
                StremioBadge()
            }
            Box(Modifier.width(10.dp))
            Icon(Icons.ChevronDownSmall, size = 12.dp, tint = c.accentText)
        }
        if (open) {
            Popup(popupPositionProvider = BelowAnchor(6, alignEnd = true), onDismissRequest = { open = false }, properties = PopupProperties(focusable = true)) {
                ExtensionPanel(names, random, selected) { picked ->
                    open = false
                    vm.loadAndCancel(picked, forceReload = true, fromUI = true)
                }
            }
        }
    }
}

private sealed interface Row2 {
    class Header(val text: String) : Row2
    class Item(val name: String) : Row2
}

@Composable
private fun ExtensionPanel(names: List<String>, random: String, selected: String?, onPick: (String) -> Unit) {
    val c = Fluent.colors
    var query by remember { mutableStateOf("") }
    var index by remember { mutableStateOf(0) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val q = query.trim().lowercase()
    val rows = remember(names, q, com.lagradost.desktop.net.ExtensionPriority.names) {
        val matching = names.filter { q.isEmpty() || it.lowercase().contains(q) }
        // the extensions pinned in Settings > General > Extension priority sit at the top, in their order
        val pinned = matching.filter { com.lagradost.desktop.net.ExtensionPriority.isPinned(it) }.sortedBy { com.lagradost.desktop.net.ExtensionPriority.rank(it) }
        val unpinned = matching - pinned.toSet()
        val stremio = unpinned.filter { APIHolder.getApiFromNameNull(it) is StremioApi }
        val others = unpinned - stremio.toSet()
        buildList<Row2> {
            if (q.isEmpty() || random.lowercase().contains(q)) add(Row2.Item(random))
            if (pinned.isNotEmpty()) { add(Row2.Header("Pinned · ${pinned.size}")); pinned.forEach { add(Row2.Item(it)) } }
            if (others.isNotEmpty()) { add(Row2.Header("Extensions · ${others.size}")); others.forEach { add(Row2.Item(it)) } }
            if (stremio.isNotEmpty()) { add(Row2.Header("Stremio add-ons · ${stremio.size}")); stremio.forEach { add(Row2.Item(it)) } }
        }
    }
    val items = remember(rows) { rows.filterIsInstance<Row2.Item>() }
    val state = rememberLazyListState()
    // the highlighted row follows the keys and stays on screen
    LaunchedEffect(index, rows) {
        val target = items.getOrNull(index) ?: return@LaunchedEffect
        val at = rows.indexOfFirst { it is Row2.Item && it.name == target.name }
        if (at >= 0) runCatching { state.animateScrollToItem((at - 3).coerceAtLeast(0)) }
    }
    LaunchedEffect(q) { index = 0 }

    FlyoutSurface(Modifier.width(380.dp)) {
        Column(Modifier.padding(8.dp)) {
            TextBox(
                query, { query = it }, Modifier.fillMaxWidth(), placeholder = "Search extensions", leadingIcon = Icons.Search, focusRequester = focus, height = 36.dp,
                onSubmit = { items.getOrNull(index)?.let { onPick(it.name) } },
                onKey = { e ->
                    if (e.type != KeyEventType.KeyDown) false
                    else when (e.key) {
                        Key.DirectionDown -> { index = (index + 1).coerceAtMost((items.size - 1).coerceAtLeast(0)); true }
                        Key.DirectionUp -> { index = (index - 1).coerceAtLeast(0); true }
                        else -> false
                    }
                },
            )
            Box(Modifier.height(6.dp))
            LazyColumn(Modifier.heightIn(max = 440.dp), state = state) {
                itemsIndexed(rows) { _, row ->
                    when (row) {
                        is Row2.Header -> FText(
                            row.text, style = Fluent.type.caption, color = c.textTertiary, maxLines = 1,
                            modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 4.dp),
                        )
                        is Row2.Item -> ExtensionRow(row.name, row.name == random, row.name == selected, items.getOrNull(index)?.name == row.name) { onPick(row.name) }
                    }
                }
                if (items.isEmpty()) item { FText("No extension matches “$query”", color = c.textSecondary, modifier = Modifier.padding(12.dp)) }
            }
        }
    }
}

@Composable
private fun ExtensionRow(name: String, isRandom: Boolean, selected: Boolean, highlighted: Boolean, onClick: () -> Unit) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(FluentShapes.control)
    val api = if (isRandom) null else APIHolder.getApiFromNameNull(name)
    val detail = if (isRandom) "A different extension each time"
    else listOfNotNull(
        api?.lang?.takeIf { it.isNotBlank() && it != "un" }?.let { SubtitleHelper.fromTagToLanguageName(it) ?: it },
        api?.supportedTypes?.take(3)?.joinToString(", ") { tvTypeName(it) },
    ).joinToString("  ·  ").ifBlank { null }
    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(vertical = 1.dp)
            .background(if (highlighted || hovered) c.subtleHover else if (selected) c.subtlePressed else androidx.compose.ui.graphics.Color.Transparent, shape)
            .hoverable(source)
            .fluentClickable(source, true, shape, Role.DropdownList, onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isRandom) Box(Modifier.size(30.dp).clip(RoundedCornerShape(7.dp)).background(c.control), contentAlignment = Alignment.Center) { Icon(Icons.Shuffle, size = 15.dp, tint = c.textSecondary) }
        else ExtensionIcon(name, 30)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            FText(name, style = if (selected) Fluent.type.bodyStrong else Fluent.type.body, maxLines = 1)
            if (detail != null) FText(detail, style = Fluent.type.caption, color = c.textTertiary, maxLines = 1)
        }
        if (api is StremioApi) { StremioBadge(showText = false); Box(Modifier.width(8.dp)) }
        if (selected) Icon(Icons.Check, size = 14.dp, tint = c.accentText)
    }
}

/** The extension the Home page shows (null for "Random", which has no single extension to search in) */
@Composable
fun selectedHomeProvider(): String? {
    val vm = appVm<HomeViewModel>()
    val apiName by vm.apiName.observeAsState()
    val name = apiName ?: DataStoreHelper.currentHomePage
    return name?.takeIf { it != APIRepository.randomApi.name }
}

/** The icon of the extension of a provider (a letter tile while it has none) */
@Composable
private fun ExtensionIcon(name: String, sizeDp: Int) {
    com.lagradost.desktop.net.ExtensionIcons.start()
    val c = Fluent.colors
    val url = com.lagradost.desktop.net.ExtensionIcons.iconFor(name)
    Box(
        Modifier.size(sizeDp.dp).clip(RoundedCornerShape((sizeDp / 4).dp)).background(c.control),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) com.lagradost.desktop.ui.components.RemoteImage(url, null, null, Modifier.size(sizeDp.dp), ContentScale.Crop)
        else FText(name.firstOrNull()?.uppercase().orEmpty(), style = Fluent.type.bodyStrong, color = c.textSecondary, maxLines = 1)
    }
}
