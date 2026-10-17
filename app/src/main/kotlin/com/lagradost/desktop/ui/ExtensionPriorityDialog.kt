package com.lagradost.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.desktop.net.ExtensionPriority
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.IconButton
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.fluent.TextBox
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction

/** What the settings row says about the list ("Not set" / "3 on top: A, B, C") */
fun extensionPrioritySummary(): String {
    val n = ExtensionPriority.names
    return if (n.isEmpty()) "Not set: results come in the order the extensions answer" else "${n.size} on top: " + n.take(4).joinToString(", ") + if (n.size > 4) "…" else ""
}

/**
 * Settings > General > Extension priority: the extensions that should come first, in the order they are put. Their results lead the search,
 * and they head the list of extensions on Home. Every change is saved at once.
 */
fun showExtensionPriorityDialog() {
    Overlays.show(
        Overlays.Dialog(title = "Extension priority", close = "Done", width = 560.dp) {
            val c = Fluent.colors
            var query by remember { mutableStateOf("") }
            val pinned = ExtensionPriority.names
            val installed = remember { APIHolder.apis.map { it.name }.distinct().sortedBy { it.lowercase() } }
            val q = query.trim().lowercase()
            val others = installed.filter { it !in pinned && (q.isEmpty() || it.lowercase().contains(q)) }
            Column {
                FText(
                    "The extensions on top come first in the search results and at the head of the list on Home. Put the ones you use most first.",
                    style = Fluent.type.caption, color = c.textSecondary, maxLines = 3,
                )
                // the ones on top have their own area (a new pin must not scroll out of sight), the rest scrolls under the search box
                if (pinned.isNotEmpty()) {
                    Header("On top (${pinned.size})")
                    Column(Modifier.fillMaxWidth().heightIn(max = 120.dp).verticalScroll(rememberScrollState())) {
                        for (name in pinned) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                FText("${pinned.indexOf(name) + 1}", Modifier.padding(start = 10.dp, end = 10.dp), color = c.textTertiary)
                                FText(name, Modifier.weight(1f), maxLines = 1)
                                IconButton(Icons.ChevronUp, { ExtensionPriority.move(name, -1) }, tooltip = "Up", size = 28.dp, iconSize = 12.dp, enabled = pinned.first() != name)
                                IconButton(Icons.ChevronDown, { ExtensionPriority.move(name, 1) }, tooltip = "Down", size = 28.dp, iconSize = 12.dp, enabled = pinned.last() != name)
                                IconButton(Icons.Close, { ExtensionPriority.unpin(name) }, tooltip = "Take off the list", size = 28.dp, iconSize = 12.dp)
                            }
                        }
                    }
                }
                Header(if (pinned.isEmpty()) "Pick the extensions to put on top" else "Add more")
                TextBox(query, { query = it }, Modifier.fillMaxWidth().padding(bottom = 6.dp), placeholder = "Find an extension", leadingIcon = Icons.Search, height = 32.dp)
                LazyColumn(Modifier.heightIn(max = if (pinned.isEmpty()) 260.dp else 130.dp)) {
                    items(others, key = { "o-$it" }) { name ->
                        val source = rememberInteraction()
                        val hovered by source.collectIsHoveredAsState()
                        val shape = RoundedCornerShape(6.dp)
                        Row(
                            Modifier.fillMaxWidth().clip(shape).background(if (hovered) c.subtleHover else Color.Transparent, shape)
                                .fluentClickable(source, true, shape, Role.Button) { ExtensionPriority.pin(name) }.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FText(name, Modifier.weight(1f), maxLines = 1)
                            Icon(Icons.Pin, size = 14.dp, tint = c.textTertiary)
                        }
                    }
                    if (others.isEmpty()) item(key = "none") { FText(if (q.isEmpty()) "Every extension is on the list" else "No extension matches", Modifier.padding(10.dp), color = c.textTertiary) }
                }
            }
        },
    )
}

@androidx.compose.runtime.Composable
private fun Header(text: String) {
    FText(text, Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp), style = Fluent.type.caption, color = Fluent.colors.textSecondary)
}
