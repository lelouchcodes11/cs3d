package com.lagradost.desktop.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import com.lagradost.desktop.ui.fluent.tabletVerticalScroll as verticalScroll
import androidx.compose.runtime.Composable
import kotlinx.coroutines.async
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

    // ------------------------------------------------------------------ rows of other sources on the same Home

    /** At most this many other sources, each with its first [MIX_ROWS] rows: Home stays quick */
    const val MIX_MAX = 3
    private const val MIX_ROWS = 3
    private const val MIX_KEY = "desktop_home_mix"

    /** The other sources whose first rows are shown under the rows of the Home source */
    fun mixed(): List<String> { version; return read(MIX_KEY) }
    fun setMixed(list: List<String>) = write(MIX_KEY, list.take(MIX_MAX))

    /** The first rows of every mixed-in source but [current], named "Row · Source"; loaded after the source's own rows, slow ones are left out */
    suspend fun loadMixed(current: String?): List<Pair<String, com.lagradost.cloudstream3.ui.home.HomeViewModel.ExpandableHomepageList>> = kotlinx.coroutines.coroutineScope {
        val apis = mixed().filter { it != current }.mapNotNull { com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(it) }
            .filter { it.hasMainPage && com.lagradost.desktop.core.Kids.allows(it) }
        apis.map { api ->
            async(kotlinx.coroutines.Dispatchers.IO) {
                val repo = com.lagradost.cloudstream3.ui.APIRepository(api)
                (0 until minOf(MIX_ROWS, api.mainPage.size)).map { i ->
                    async {
                        kotlinx.coroutines.withTimeoutOrNull(20_000L) { (repo.getMainPage(1, i) as? com.lagradost.cloudstream3.mvvm.Resource.Success)?.value }.orEmpty()
                            .filterNotNull().flatMap { it.items }.filter { it.list.isNotEmpty() }
                    }
                }.flatMap { it.await() }.map { list ->
                    val name = "${list.name} · ${api.name}"
                    name to com.lagradost.cloudstream3.ui.home.HomeViewModel.ExpandableHomepageList(com.lagradost.cloudstream3.HomePageList(name, list.list, list.isHorizontalImages), 1, false)
                }
            }
        }.flatMap { it.await() }
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
                    // other sources whose first rows come under these (a few, so Home stays quick)
                    val others = remember {
                        runCatching { com.lagradost.desktop.DesktopBootstrap.activity.let { ctx -> with(com.lagradost.cloudstream3.utils.AppContextUtils) { ctx.filterProviderByPreferredMedia() } } }
                            .getOrDefault(emptyList()).map { it.name }.filter { it != api }.distinct()
                    }
                    if (others.isNotEmpty()) {
                        val mix = remember { mutableStateListOf<String>().also { it.addAll(mixed()) } }
                        FText("Also show rows from", color = c.text, modifier = Modifier.padding(top = 16.dp, bottom = 2.dp))
                        FText("The first rows of up to $MIX_MAX other sources, under the rows above.", color = c.textSecondary, style = Fluent.type.caption, modifier = Modifier.padding(bottom = 6.dp))
                        Column(Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (name in others) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                FText(name, Modifier.weight(1f), color = if (name in mix) c.text else c.textTertiary, maxLines = 1)
                                ToggleSwitch(name in mix, { on ->
                                    if (on) { if (mix.size < MIX_MAX) mix.add(name) } else mix.remove(name)
                                    setMixed(mix.toList())
                                }, enabled = name in mix || mix.size < MIX_MAX)
                            }
                        }
                    }
                }
            },
        )
    }
}
