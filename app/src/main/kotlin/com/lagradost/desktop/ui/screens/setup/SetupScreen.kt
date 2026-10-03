package com.lagradost.desktop.ui.screens.setup

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.AllLanguagesName
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.ui.settings.appLanguages
import com.lagradost.cloudstream3.ui.settings.extensions.ExtensionsViewModel
import com.lagradost.cloudstream3.ui.settings.extensions.PluginsViewModel
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import com.lagradost.cloudstream3.ui.setup.HAS_DONE_SETUP_KEY
import com.lagradost.cloudstream3.utils.AppContextUtils.getApiProviderLangSettings
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.SubtitleHelper.getNameNextToFlagEmoji
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.core.appVm
import com.lagradost.desktop.core.ioTask
import com.lagradost.desktop.core.observeAsState
import com.lagradost.desktop.ui.Toasts
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.CheckBox
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.ProgressBar
import com.lagradost.desktop.ui.fluent.ProgressRing
import com.lagradost.desktop.ui.fluent.TextBox
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction

/** True once the wizard ran (or was skipped) */
fun setupDone(): Boolean = runCatching { com.lagradost.cloudstream3.CloudStreamApp.getKey<Boolean>(HAS_DONE_SETUP_KEY, false) }.getOrNull() == true

private enum class Step(val title: String) { Welcome("Welcome"), Extensions("Add extensions"), Content("Choose content"), Done("All set") }

/** First run: language, a repository with extensions, content preferences. Shown full window. */
@Composable
fun SetupScreen() {
    val c = Fluent.colors
    var step by remember { mutableStateOf(Step.Welcome) }
    Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 760.dp).fillMaxWidth().fillMaxHeight(0.92f).padding(24.dp)
                .background(c.layer, RoundedCornerShape(FluentShapes.card)).border(androidx.compose.ui.unit.Dp.Hairline, c.stroke, RoundedCornerShape(FluentShapes.card)),
        ) {
            // progress dots
            Row(Modifier.fillMaxWidth().padding(24.dp, 20.dp, 24.dp, 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Step.entries.forEachIndexed { i, s ->
                    Box(Modifier.size(if (s == step) 28.dp else 8.dp, 8.dp).clip(RoundedCornerShape(4.dp)).background(if (i <= step.ordinal) c.accent else c.strokeStrong.copy(alpha = 0.4f)))
                }
                Box(Modifier.weight(1f))
                FText("Step ${step.ordinal + 1} of ${Step.entries.size}", style = Fluent.type.caption, color = c.textSecondary)
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp)) {
                when (step) {
                    Step.Welcome -> WelcomeStep()
                    Step.Extensions -> ExtensionsStep()
                    Step.Content -> ContentStep()
                    Step.Done -> DoneStep()
                }
            }
            Row(Modifier.fillMaxWidth().background(c.bgPane.copy(alpha = 0.6f)).padding(24.dp, 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step != Step.Done) Button("Skip setup", { finish() }, kind = ButtonKind.Subtle)
                Box(Modifier.weight(1f))
                if (step.ordinal > 0) Button("Back", { step = Step.entries[step.ordinal - 1] })
                if (step == Step.Done) Button("Start watching", { finish() }, kind = ButtonKind.Accent, icon = Icons.Play)
                else Button("Next", { step = Step.entries[step.ordinal + 1] }, kind = ButtonKind.Accent)
            }
        }
    }
}

private fun finish() {
    val ctx = DesktopBootstrap.activity
    setKey(HAS_DONE_SETUP_KEY, true)
    // the home page is rebuilt for the chosen providers
    DataStoreHelper.currentHomePage = null
    com.lagradost.cloudstream3.MainActivity.afterPluginsLoadedEvent.invoke(true)
    Navigator.reset(com.lagradost.desktop.core.Route.Home)
}

@Composable
private fun WelcomeStep() {
    val c = Fluent.colors
    val ctx = DesktopBootstrap.activity
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(ctx) }
    var selected by remember { mutableStateOf(prefs.getString(ctx.getString(R.string.locale_key), null) ?: "en") }
    var filter by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Video, size = 36.dp, tint = c.accent)
        FText("Welcome to CloudStream", style = Fluent.type.title)
        FText("Stream movies, series and anime from the extensions you add. First, pick the language of the app.", color = c.textSecondary)
        TextBox(filter, { filter = it }, Modifier.fillMaxWidth(), placeholder = "Search languages", leadingIcon = Icons.Language)
        val langs = appLanguages.filter { filter.isBlank() || it.first.contains(filter, true) || it.second.contains(filter, true) }
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        LaunchedEffect(Unit) { langs.indexOfFirst { it.second == selected }.takeIf { it > 2 }?.let { listState.scrollToItem(it - 2) } }
        LazyColumn(Modifier.weight(1f, fill = true).heightIn(max = 380.dp), state = listState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(langs, key = { it.second }) { (name, tag) ->
                val source = rememberInteraction()
                val hovered by source.collectIsHoveredAsState()
                val shape = RoundedCornerShape(4.dp)
                Row(
                    Modifier.fillMaxWidth().height(36.dp).clip(shape)
                        .background(if (tag == selected) c.subtleHover else if (hovered) c.subtleHover else Color.Transparent, shape)
                        .fluentClickable(source, true, shape, Role.RadioButton) {
                            selected = tag
                            CommonActivity.setLocale(ctx, tag)
                            prefs.edit { putString(ctx.getString(R.string.locale_key), tag) }
                        }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FText(name, Modifier.weight(1f), maxLines = 1)
                    if (tag == selected) Icon(Icons.Check, size = 14.dp, tint = c.accent)
                }
            }
        }
    }
}

@Composable
private fun ExtensionsStep() {
    val c = Fluent.colors
    val ctx = DesktopBootstrap.activity
    val ext = appVm<ExtensionsViewModel>()
    val plugins = appVm<PluginsViewModel>()
    val repos by ext.repositories.observeAsState()
    val list by plugins.filteredPlugins.observeAsState()
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ext.loadRepositories() }
    val repoList = repos.orEmpty()
    LaunchedEffect(repoList.size) { if (repoList.isNotEmpty()) plugins.updatePluginList(ctx, repoList.toList()) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FText("Add extensions", style = Fluent.type.title)
        FText("Extensions provide the movies and shows. Add a repository by its address, then install the providers you want.", color = c.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextBox(url, { url = it }, Modifier.weight(1f), placeholder = "Repository URL or short code", leadingIcon = Icons.Link, onSubmit = { addRepo(url, ext) { busy = it }; url = "" })
            Button(if (busy) "Adding…" else "Add repository", { addRepo(url, ext) { busy = it }; url = "" }, kind = ButtonKind.Accent, enabled = !busy && url.isNotBlank(), icon = Icons.Add)
        }
        if (repoList.isNotEmpty()) FText("Repositories: " + repoList.joinToString { it.name }, style = Fluent.type.caption, color = c.textSecondary)
        val items = list?.second.orEmpty()
        Row(verticalAlignment = Alignment.CenterVertically) {
            FText(if (items.isEmpty()) "No extensions yet" else "${items.count { it.isDownloaded }} of ${items.size} installed", Modifier.weight(1f), style = Fluent.type.bodyStrong)
            if (items.isNotEmpty()) Button("Install all", { repoList.forEach { PluginsViewModel.downloadAll(ctx, it, plugins) } }, icon = Icons.Download)
        }
        LazyColumn(Modifier.weight(1f, fill = true).heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(items, key = { it.pluginWrapper.plugin.url }) { item ->
                val p = item.pluginWrapper.plugin
                Row(
                    Modifier.fillMaxWidth().background(c.card, RoundedCornerShape(FluentShapes.control)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        FText(p.name, style = Fluent.type.bodyStrong, maxLines = 1)
                        p.description?.takeIf { it.isNotBlank() }?.let { FText(it, style = Fluent.type.caption, color = c.textSecondary, maxLines = 1) }
                    }
                    if (item.isDownloaded) FText("Installed", color = c.success, style = Fluent.type.caption)
                    else Button("Install", { plugins.handlePluginAction(ctx, repoList.toList(), item.pluginWrapper, false) }, icon = Icons.Download)
                }
            }
        }
    }
}

private fun addRepo(input: String, ext: ExtensionsViewModel, busy: (Boolean) -> Unit) {
    val text = input.trim()
    if (text.isEmpty()) return
    busy(true)
    ioTask {
        try {
            val parsed = RepositoryManager.parseRepoUrl(text)
            if (parsed.isNullOrBlank()) { Toasts.show("That does not look like a repository address", true); return@ioTask }
            val repository = RepositoryManager.parseRepository(parsed)
            if (repository == null) { Toasts.show("No repository was found at that address", true); return@ioTask }
            RepositoryManager.addRepository(RepositoryData(repository.iconUrl, repository.name, parsed))
            ext.loadRepositories()
            ext.loadStats()
            Toasts.show("Added “${repository.name}”", false)
        } finally {
            busy(false)
        }
    }
}

@Composable
private fun ContentStep() {
    val c = Fluent.colors
    val ctx = DesktopBootstrap.activity
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(ctx) }
    val typeKey = ctx.getString(R.string.prefer_media_type_key)
    val langKey = ctx.getString(R.string.provider_lang_key)
    val types = remember { enumValues<TvType>().sorted() }
    val chosenTypes = remember {
        mutableStateListOf<TvType>().apply {
            val stored = prefs.getStringSet(typeKey, null)
            addAll(types.filter { t -> stored?.contains(t.ordinal.toString()) ?: (t != TvType.NSFW) })
        }
    }
    val languages = remember {
        APIHolder.apis.withLock {
            listOf(AllLanguagesName to "All languages") + APIHolder.apis.map { it.lang to (getNameNextToFlagEmoji(it.lang) ?: it.lang) }.toSet().sortedBy { it.second.substringAfter(" ").lowercase() }
        }
    }
    val chosenLangs = remember { mutableStateListOf<String>().apply { addAll(ctx.getApiProviderLangSettings()) } }
    fun save() {
        prefs.edit {
            putStringSet(typeKey, chosenTypes.map { it.ordinal.toString() }.toSet())
            putStringSet(langKey, chosenLangs.toSet())
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FText("Choose content", style = Fluent.type.title)
        FText("Only providers matching these choices are used for the home page and search. You can change this later in Settings.", color = c.textSecondary)
        Row(Modifier.weight(1f, fill = true), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f)) {
                FText("Media types", style = Fluent.type.bodyStrong, modifier = Modifier.padding(bottom = 6.dp))
                LazyColumn { items(types, key = { it.name }) { t -> CheckBox(t in chosenTypes, { on -> if (on) chosenTypes.add(t) else chosenTypes.remove(t); save() }, Modifier.fillMaxWidth().padding(vertical = 5.dp), label = com.lagradost.desktop.ui.fluent.tvTypeName(t)) } }
            }
            Column(Modifier.weight(1f)) {
                FText("Provider languages", style = Fluent.type.bodyStrong, modifier = Modifier.padding(bottom = 6.dp))
                LazyColumn { items(languages, key = { it.first }) { (tag, name) -> CheckBox(tag in chosenLangs, { on -> if (on) chosenLangs.add(tag) else chosenLangs.remove(tag); save() }, Modifier.fillMaxWidth().padding(vertical = 5.dp), label = name) } }
            }
        }
    }
}

@Composable
private fun DoneStep() {
    val c = Fluent.colors
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(72.dp).background(c.accent, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Check, size = 32.dp, tint = c.onAccent) }
        Box(Modifier.height(16.dp))
        FText("You are all set", style = Fluent.type.title)
        Box(Modifier.height(4.dp))
        FText("Press Ctrl+K anywhere to search. Right-click posters and episodes for more actions.", color = c.textSecondary)
    }
}
