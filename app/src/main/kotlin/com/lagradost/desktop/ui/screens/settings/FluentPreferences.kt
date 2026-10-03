package com.lagradost.desktop.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.ui.LegacyContent
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.CheckBox
import com.lagradost.desktop.ui.fluent.ComboBox
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.fluent.Slider
import com.lagradost.desktop.ui.fluent.TextBox
import com.lagradost.desktop.ui.fluent.ToggleSwitch
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction
import com.mihon.presentation.settings.Preference
import com.mihon.presentation.settings.collectAsState
import kotlinx.coroutines.launch

/**
 * Renders the upstream preference definitions (the `Settings*Screen` objects) as Windows 11 settings
 * cards, so every setting keeps its keys, defaults and side effects.
 */
@Composable
fun FluentPreferenceList(items: List<Preference>, modifier: Modifier = Modifier) {
    Column(modifier) {
        items.forEachIndexed { i, pref ->
            when (pref) {
                is Preference.PreferenceGroup -> {
                    if (!pref.enabled) return@forEachIndexed
                    FText(pref.title, style = Fluent.type.bodyStrong, modifier = Modifier.padding(top = if (i == 0) 0.dp else 24.dp, bottom = 8.dp, start = 2.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        pref.preferenceItems.forEach { PreferenceCard(it) }
                    }
                }
                is Preference.PreferenceItem<*, *> -> {
                    Box(Modifier.padding(top = if (i == 0) 0.dp else 3.dp)) { PreferenceCard(pref) }
                }
            }
        }
    }
}

@Composable
fun SettingsCard(
    title: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.painter.Painter? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(FluentShapes.card)
    val base = Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(shape)
        .background(if (hovered && onClick != null && enabled) c.cardHover else c.card, shape)
        .border(androidx.compose.ui.unit.Dp.Hairline, c.stroke, shape)
    Row(
        (if (onClick != null && enabled) base.fluentClickable(source, true, shape, Role.Button, onClick) else base).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) Image(icon, null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(if (enabled) c.text else c.textDisabled))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FText(title, color = if (enabled) c.text else c.textDisabled, maxLines = 3)
            if (!subtitle.isNullOrBlank()) FText(subtitle, style = Fluent.type.caption, color = if (enabled) c.textSecondary else c.textDisabled, maxLines = 4)
        }
        trailing()
        if (onClick != null) Icon(Icons.ChevronRightSmall, size = 12.dp, tint = c.textSecondary)
    }
}

@Composable
@Suppress("UNCHECKED_CAST")
private fun PreferenceCard(item: Preference.PreferenceItem<*, *>) {
    val scope = rememberCoroutineScope()
    val c = Fluent.colors
    if (!item.enabled && item !is Preference.PreferenceItem.CustomPreference) return
    when (item) {
        is Preference.PreferenceItem.SwitchPreference -> {
            val value by item.preference.collectAsState()
            SettingsCard(item.title, item.subtitle, item.icon, onClick = null) {
                FText(if (value) "On" else "Off", color = c.textSecondary, style = Fluent.type.caption)
                Box(Modifier.width(8.dp))
                ToggleSwitch(value, { new -> scope.launch { if (item.onValueChanged(new)) item.preference.set(new) } })
            }
        }
        is Preference.PreferenceItem.ListPreference<*> -> {
            val value by item.preference.collectAsState()
            val keys = item.entries.keys.toList()
            SettingsCard(item.title, if (value in item.entries) item.internalSubtitleProvider(value, item.entries)?.takeIf { it != item.entries[value] } else null, item.icon) {
                // by position: a null key ("Normal", "Automatic") is a real choice, which a nullable "selected" could not tell from "nothing"
                ComboBox(
                    items = keys.indices.toList(), selected = keys.indexOf(value).takeIf { it >= 0 },
                    label = { i -> keys.getOrNull(i).let { k -> item.entries[k] ?: k.toString() } },
                    onSelect = { i -> val new = keys[i]; scope.launch { if (item.internalOnValueChanged(new)) item.internalSet(new) } },
                    minWidth = 180.dp,
                )
            }
        }
        is Preference.PreferenceItem.MultiSelectListPreference<*> -> {
            val values by item.preference.collectAsState()
            SettingsCard(item.title, item.internalSubtitleProvider(values, item.entries), item.icon, onClick = {
                val keys = item.entries.keys.toList()
                val working = mutableStateListOf<Any?>().apply { addAll(values) }
                Overlays.show(
                    Overlays.Dialog(
                        title = item.title, primary = "Apply", close = "Cancel", width = 460.dp,
                        onPrimary = { prefScope.launch { val set = working.toSet(); if (item.internalOnValueChanged(set)) item.internalSet(set) } },
                    ) {
                        Column {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button("Select all", { working.clear(); working.addAll(keys) }, kind = ButtonKind.Subtle)
                                Button("Select none", { working.clear() }, kind = ButtonKind.Subtle)
                            }
                            Box(Modifier.size(8.dp))
                            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                                items(keys) { k -> CheckBox(k in working, { on -> if (on) working.add(k) else working.remove(k) }, Modifier.fillMaxWidth().padding(vertical = 6.dp), label = item.entries[k] ?: k.toString()) }
                            }
                        }
                    },
                )
            }) { }
        }
        is Preference.PreferenceItem.TextPreference -> {
            SettingsCard(item.title, item.subtitle, item.icon, item.enabled, onClick = item.onClick) {
                item.widget?.let { w -> LegacyContent { w() } }
            }
        }
        is Preference.PreferenceItem.EditTextPreference -> {
            val value by item.preference.collectAsState()
            SettingsCard(item.title, item.subtitle?.let { runCatching { it.format(value) }.getOrDefault(it) }, null, onClick = {
                var text by mutableStateOf(value)
                Overlays.show(
                    Overlays.Dialog(
                        title = item.title, primary = "Save", close = "Cancel", width = 460.dp,
                        onPrimary = { prefScope.launch { if (item.onValueChanged(text)) item.preference.set(text) } },
                    ) { TextBox(text, { text = it }, Modifier.fillMaxWidth(), singleLine = true) },
                )
            }) { }
        }
        is Preference.PreferenceItem.SliderPreference -> {
            val state by item.preference.collectAsState()
            SettingsCard(item.title, item.subtitle, item.icon) {
                val range = item.valueRange
                Slider(
                    value = state.toFloat(), onValueChange = { v -> scope.launch { val n = v.toInt(); if (item.onValueChanged(n)) item.preference.set(n) } },
                    valueRange = range.first.toFloat()..range.last.toFloat(), steps = range.step.toFloat(), modifier = Modifier.width(220.dp),
                )
                FText(item.valueString?.takeIf { it.isNotEmpty() } ?: state.toString(), Modifier.width(64.dp).padding(start = 8.dp), style = Fluent.type.caption, color = c.textSecondary)
            }
        }
        is Preference.PreferenceItem.ColorPreference -> {
            val value by item.preference.collectAsState()
            SettingsCard(item.title, item.subtitle, item.icon, onClick = {
                var picked by mutableStateOf(value)
                var hex by mutableStateOf(colorHex(value))
                fun pick(argb: Int) { picked = argb; hex = colorHex(argb) }
                Overlays.show(
                    Overlays.Dialog(
                        title = item.title, primary = "Apply", close = "Cancel", width = 460.dp,
                        onPrimary = {
                            val chosen = picked
                            prefScope.launch { if (item.onValueChanged(chosen)) item.preference.set(chosen) }
                        },
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                ColorSwatch(picked, 44.dp)
                                FText(colorHex(picked), color = Fluent.colors.textSecondary)
                            }
                            // a few colours people use for subtitles, then none at all and two see-through blacks
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                colorPresets.chunked(6).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        row.forEach { (name, argb) ->
                                            com.lagradost.desktop.ui.fluent.Tooltip(name) {
                                                Box(Modifier.clip(CircleShape).fluentClickable(rememberInteraction(), true, CircleShape, Role.Button) { pick(argb) }) { ColorSwatch(argb, 32.dp, selected = picked == argb) }
                                            }
                                        }
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                FText("Opacity", Modifier.width(64.dp), color = Fluent.colors.textSecondary)
                                Slider(
                                    value = ((picked ushr 24) and 0xFF) * 100f / 255f, onValueChange = { pct -> pick((((pct * 255f / 100f).toInt().coerceIn(0, 255)) shl 24) or (picked and 0xFFFFFF)) },
                                    valueRange = 0f..100f, modifier = Modifier.weight(1f),
                                )
                                FText("${(((picked ushr 24) and 0xFF) * 100f / 255f).toInt()}%", Modifier.width(44.dp), color = Fluent.colors.textSecondary)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                FText("Hex colour (RRGGBB, or AARRGGBB with opacity)", color = Fluent.colors.textSecondary, style = Fluent.type.caption)
                                TextBox(hex, { text ->
                                    hex = text
                                    parseColorHex(text)?.let { picked = it }
                                }, Modifier.fillMaxWidth(), leadingIcon = Icons.Theme, singleLine = true)
                            }
                        }
                    },
                )
            }) { ColorSwatch(value, 28.dp) }
        }
        is Preference.PreferenceItem.InfoPreference -> {
            Row(Modifier.fillMaxWidth().background(c.card, RoundedCornerShape(FluentShapes.card)).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Info, size = 16.dp, tint = c.accentText)
                FText(item.title, color = c.textSecondary, maxLines = 12)
            }
        }
        is Preference.PreferenceItem.CustomPreference -> {
            LegacyContent { item.content() }
        }
    }
}

private val colorPresets: List<Pair<String, Int>> = listOf(
    "White" to 0xFFFFFFFF.toInt(), "Yellow" to 0xFFFFEB3B.toInt(), "Cyan" to 0xFF00E5FF.toInt(),
    "Green" to 0xFF76FF03.toInt(), "Pink" to 0xFFFF4081.toInt(), "Red" to 0xFFFF5252.toInt(),
    "Blue" to 0xFF448AFF.toInt(), "Black" to 0xFF000000.toInt(), "Gray" to 0xFF9E9E9E.toInt(),
    "Transparent" to 0x00000000, "Black, half see-through" to 0x80000000.toInt(), "Black, mostly see-through" to 0x40000000,
)

/** #RRGGBB, or #AARRGGBB when the colour is not fully opaque */
private fun colorHex(argb: Int): String = if ((argb ushr 24) == 0xFF) "%06X".format(argb and 0xFFFFFF) else "%08X".format(argb)

private fun parseColorHex(text: String): Int? {
    val digits = text.trim().removePrefix("#")
    val value = digits.toLongOrNull(16) ?: return null
    return when (digits.length) {
        6 -> (0xFF000000 or value).toInt()
        8 -> value.toInt()
        else -> null
    }
}

/** A colour dot; a see-through colour is drawn over a light and a dark half so it can be told from "no colour" */
@Composable
private fun ColorSwatch(argb: Int, size: androidx.compose.ui.unit.Dp, selected: Boolean = false) {
    val c = Fluent.colors
    Box(Modifier.size(size).clip(CircleShape).border(if (selected) 2.dp else androidx.compose.ui.unit.Dp.Hairline, if (selected) c.text else c.strokeStrong, CircleShape)) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFFBDBDBD)))
            Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF4A4A4A)))
        }
        Box(Modifier.fillMaxSize().background(Color(argb)))
    }
}

/**
 * For what a dialog does when it closes: the dialog layer shows the newest dialog only, so the card that opened it
 * (inside an older dialog, like the player's style dialog) is out of the composition and its own scope is cancelled.
 */
private val prefScope = kotlinx.coroutines.MainScope()
