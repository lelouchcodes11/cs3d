package com.lagradost.desktop.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.lagradost.cloudstream3.ui.search.SearchViewModel
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.core.appVm
import com.lagradost.desktop.core.observeAsState
import com.lagradost.desktop.ui.fluent.BelowAnchor
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FlyoutSurface
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.TextBox
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction

/** The search box of the title bar: type-ahead suggestions and recent searches in a flyout */
@Composable
fun GlobalSearchBox(modifier: Modifier = Modifier, only: String? = null) {
    val vm = appVm<SearchViewModel>()
    val suggestions by vm.searchSuggestions.observeAsState()
    val history by vm.currentHistory.observeAsState()
    var focused by remember { mutableStateOf(false) }
    val text = ShellState.searchText

    LaunchedEffect(text) { vm.fetchSuggestions(text) }
    LaunchedEffect(focused) { if (focused) vm.updateHistory() }

    fun submit(q: String) {
        val query = q.trim()
        if (query.isEmpty()) return
        ShellState.searchText = query
        vm.clearSuggestions()
        Navigator.search(query, only)
    }

    val rows: List<Pair<String, Boolean>> = when {
        text.length >= 2 -> suggestions.orEmpty().take(8).map { it to false }
        text.isEmpty() -> history.orEmpty().take(6).map { it.searchText to true }
        else -> emptyList()
    }

    Box(modifier) {
        TextBox(
            value = text,
            onValueChange = { ShellState.searchText = it },
            placeholder = if (only != null) "Search in $only…   (Ctrl+K)" else "Search all extensions…   (Ctrl+K)",
            leadingIcon = Icons.Search,
            focusRequester = ShellState.searchFocus,
            onSubmit = { submit(text) },
            onFocusChange = { focused = it },
            modifier = Modifier.fillMaxWidth(),
            pill = true,
            height = 38.dp,
        )
        if (focused && rows.isNotEmpty()) {
            Popup(popupPositionProvider = BelowAnchor(4), properties = PopupProperties(focusable = false)) {
                FlyoutSurface(Modifier.width(480.dp)) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        for ((s, isHistory) in rows) SuggestionRow(s, isHistory) { submit(s) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(text: String, history: Boolean, onClick: () -> Unit) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(4.dp)
    Row(
        Modifier
            .padding(horizontal = 4.dp)
            .fillMaxWidth()
            .height(36.dp)
            .clip(shape)
            .background(if (hovered) c.subtleHover else Color.Transparent, shape)
            .fluentClickable(source, true, shape, Role.Button, onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (history) Icons.History else Icons.Search, size = 14.dp, tint = c.textSecondary)
        Box(Modifier.width(12.dp))
        FText(text, maxLines = 1)
    }
}
