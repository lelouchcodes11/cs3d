package com.lagradost.desktop.ui.screens.settings

import androidx.compose.runtime.Composable
import com.mihon.presentation.settings.Preference

/**
 * What a Settings page is made of. A page is a list of [SettingGroup]s, a group a list of [SettingItem]s. Every row of every page is an item with a
 * title, a description and some extra words, so the search can find any of them (and show it, working, in its results) instead of only the rows
 * that happen to be preference definitions.
 */
class SettingItem(val id: String, val title: String, val about: String?, val keywords: String, val content: @Composable () -> Unit) {
    /** A plain row (a [SettingsCard]) that is drawn inside its group's card; a block (a picture grid, a colour picker) stands on its own */
    var row: Boolean = true

    /** The row has an icon: its group keeps the place of an icon free in the rows that have none, so their text lines up */
    var hasIcon: Boolean = false
}

/** A titled run of rows; [action] is a small button beside the title (Reset to default) */
class SettingGroup(val title: String, val items: List<SettingItem>, val action: (@Composable () -> Unit)? = null)

class GroupBuilder {
    val items = mutableListOf<SettingItem>()
    var action: (@Composable () -> Unit)? = null

    /** A settings card: [title] and [about] on the left, the [control] on the right */
    fun card(title: String, about: String? = null, keywords: String = "", control: @Composable () -> Unit = {}) {
        items += SettingItem(title, title, about, keywords) { SettingsCard(title, about) { control() } }
    }

    /** A card that opens something when it is clicked */
    fun link(title: String, about: String? = null, keywords: String = "", onClick: () -> Unit) {
        items += SettingItem(title, title, about, keywords) { SettingsCard(title, about, onClick = onClick) }
    }

    /** A row of its own (a picture grid, a colour picker): the search knows it by [title], [about] and [keywords] */
    fun block(title: String, about: String? = null, keywords: String = "", content: @Composable () -> Unit) {
        items += SettingItem(title, title, about, keywords, content).also { it.row = false }
    }

    /** A button beside the group's title */
    fun action(content: @Composable () -> Unit) {
        action = content
    }

    /** An upstream preference (null when this build does not have it) */
    fun pref(p: Preference.PreferenceItem<*, *>?, keywords: String = "") {
        if (p == null || (!p.enabled && p !is Preference.PreferenceItem.CustomPreference)) return
        val about = p.subtitle?.takeIf { it.isNotBlank() && !it.contains("%s") }
        items += SettingItem(Upstream.idOf(p), p.title, about, keywords) { PreferenceCard(p) }.also { it.row = p !is Preference.PreferenceItem.CustomPreference; it.hasIcon = p.icon != null }
    }
}

/** A group built by [build]: composable so the rows can read state and use upstream's composable preference lists */
@Composable
fun group(title: String, build: @Composable GroupBuilder.() -> Unit): SettingGroup {
    val g = GroupBuilder()
    g.build()
    return SettingGroup(title, g.items, g.action)
}

/**
 * The preference lists of the upstream settings screens, searched by what they keep (the preference's key) or, for rows that only do something
 * when clicked, by their title: the pages pick the rows that mean something in this app and leave the phone-only ones out.
 */
class Upstream(list: List<Preference>) {
    private val all: List<Preference.PreferenceItem<*, *>> = list.flatMap { p ->
        when (p) {
            is Preference.PreferenceGroup -> p.preferenceItems
            is Preference.PreferenceItem<*, *> -> listOf(p)
        }
    }

    /** The row that keeps the preference [key] */
    fun key(key: String): Preference.PreferenceItem<*, *>? = all.firstOrNull { keyOf(it) == key }

    /** The row with this [title] (a row without a preference of its own: a button) */
    fun title(title: String): Preference.PreferenceItem<*, *>? = all.firstOrNull { it.title == title }

    companion object {
        fun keyOf(p: Preference.PreferenceItem<*, *>): String? = when (p) {
            is Preference.PreferenceItem.SwitchPreference -> p.preference.key()
            is Preference.PreferenceItem.ListPreference<*> -> p.preference.key()
            is Preference.PreferenceItem.MultiSelectListPreference<*> -> p.preference.key()
            is Preference.PreferenceItem.SliderPreference -> p.preference.key()
            is Preference.PreferenceItem.EditTextPreference -> p.preference.key()
            is Preference.PreferenceItem.ColorPreference -> p.preference.key()
            else -> null
        }

        fun idOf(p: Preference.PreferenceItem<*, *>): String = keyOf(p) ?: p.title
    }
}

/** Every word of [query] is somewhere in the item (or in its group and page names) */
fun SettingItem.matches(words: List<String>, groupTitle: String, pageTitle: String): Boolean {
    // what the page shows in the language of the app is searched as well as the English text and the keywords
    val tr = com.lagradost.desktop.i18n.Tr
    val hay = tr.lower("$title ${about.orEmpty()} $keywords $groupTitle $pageTitle ${tr.t(title)} ${tr.t(about.orEmpty())} ${tr.t(groupTitle)} ${tr.t(pageTitle)}")
    return words.all { it in hay }
}
