package com.lagradost.desktop.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.IconButton
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.fluent.ToggleSwitch

/**
 * Which rows of a provider's Home page are shown and in what order (Settings are per provider: each one names its rows its own way).
 * Kept as two small preference strings per provider; a row the provider adds later is shown at the end.
 */
object HomeLayout {
    private const val SEP = "\u0001"
    private var version by mutableIntStateOf(0)

    private fun prefs() = androidx.preference.PreferenceManager.getDefaultSharedPreferences(com.lagradost.desktop.runtime.AndroidRuntime.context)
    private fun read(key: String): List<String> = runCatching { prefs().getString(key, "").orEmpty().split(SEP).filter { it.isNotEmpty() } }.getOrDefault(emptyList())
    private fun write(key: String, list: List<String>) { runCatching { prefs().edit().putString(key, list.joinToString(SEP)).apply() }; version++ }

    fun hidden(api: String): Set<String> { version; return read("desktop_home_hidden/$api").toSet() }
    fun order(api: String): List<String> { version; return read("desktop_home_order/$api") }
    fun customised(api: String): Boolean = hidden(api).isNotEmpty() || order(api).isNotEmpty()

    fun save(api: String, order: List<String>, hidden: Set<String>) {
        write("desktop_home_order/$api", order)
        write("desktop_home_hidden/$api", hidden.toList())
    }

    fun reset(api: String) = save(api, emptyList(), emptySet())

    /** The rows to draw: hidden ones left out, the saved order first, new rows after it */
    fun <T> rowsFor(api: String, rows: List<Pair<String, T>>): List<Pair<String, T>> {
        val hide = hidden(api)
        val order = order(api)
        return rows.filter { it.first !in hide }.sortedBy { (name, _) -> order.indexOf(name).let { if (it < 0) Int.MAX_VALUE else it } }
    }

    /** The "Customise Home" window: a switch and two arrows for every row of the page that is showing */
    fun showDialog(api: String, names: List<String>) {
        Overlays.show(
            Overlays.Dialog(title = "Customise Home", secondary = "Reset", close = "Done", width = 520.dp, onSecondary = { reset(api) }) {
                val c = Fluent.colors
                val saved = order(api)
                // the saved order first, then rows that were never placed
                val list = remember { mutableStateListOf<String>().also { it.addAll(names.sortedBy { n -> saved.indexOf(n).let { i -> if (i < 0) Int.MAX_VALUE else i } }) } }
                val off = remember { mutableStateListOf<String>().also { it.addAll(hidden(api)) } }
                fun commit() = save(api, list.toList(), off.toSet())
                Column(Modifier.fillMaxWidth()) {
                    FText("Choose which rows of $api appear on Home and in what order.", color = c.textSecondary, modifier = Modifier.padding(bottom = 10.dp))
                    Column(Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        list.forEachIndexed { i, name ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FText(name, Modifier.weight(1f), color = if (name in off) c.textTertiary else c.text, maxLines = 1)
                                IconButton(Icons.ChevronUp, { if (i > 0) { list.add(i - 1, list.removeAt(i)); commit() } }, tooltip = "Move up", kind = ButtonKind.Subtle, size = 30.dp)
                                IconButton(Icons.ChevronDown, { if (i < list.lastIndex) { list.add(i + 1, list.removeAt(i)); commit() } }, tooltip = "Move down", kind = ButtonKind.Subtle, size = 30.dp)
                                ToggleSwitch(name !in off, { on -> if (on) off.remove(name) else off.add(name); commit() })
                            }
                        }
                    }
                }
            },
        )
    }
}
