package com.lagradost.desktop.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.utils.AppContextUtils.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.home.HomeViewModel
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.core.appVm
import com.lagradost.desktop.core.observeAsState
import com.lagradost.desktop.ui.fluent.ComboBox
import com.lagradost.desktop.ui.fluent.Icons

/**
 * The extension whose home page is shown. It sits in the top bar of the Home page and also decides where the
 * search box of that page searches: only in this extension (the Search page of the left bar searches all).
 */
@Composable
fun ProviderSelector(modifier: Modifier = Modifier) {
    val vm = appVm<HomeViewModel>()
    val page by vm.page.observeAsState()
    val apiName by vm.apiName.observeAsState()
    val names = remember(page, apiName) {
        val ctx = DesktopBootstrap.activityOrNull()
        val providers = runCatching { ctx?.filterProviderByPreferredMedia()?.map { it.name }?.sorted() }.getOrNull().orEmpty()
        listOf(APIRepository.randomApi.name) + providers
    }
    ComboBox(
        items = names,
        selected = apiName?.takeIf { it in names } ?: DataStoreHelper.currentHomePage?.takeIf { it in names },
        label = { it },
        onSelect = { vm.loadAndCancel(it, forceReload = true, fromUI = true) },
        modifier = modifier,
        placeholder = "Choose a source",
        icon = Icons.Globe,
        minWidth = 190.dp,
    )
}

/** The extension the Home page shows (null for "Random", which has no single extension to search in) */
@Composable
fun selectedHomeProvider(): String? {
    val vm = appVm<HomeViewModel>()
    val apiName by vm.apiName.observeAsState()
    val name = apiName ?: DataStoreHelper.currentHomePage
    return name?.takeIf { it != APIRepository.randomApi.name }
}
