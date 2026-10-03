package com.lagradost.desktop.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import com.lagradost.cloudstream3.SearchResponse
import java.awt.EventQueue
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------
// LiveData -> Compose
// ---------------------------------------------------------------------------------------------

/** The engine's LiveData as Compose state (null until the first value) */
@Composable
fun <T> LiveData<T>.observeAsState(): State<T?> {
    val live = this
    val state = remember(live) { mutableStateOf(live.value) }
    DisposableEffect(live) {
        val observer = Observer<T> { state.value = it }
        live.observeForever(observer)
        onDispose { live.removeObserver(observer) }
    }
    return state
}

// ---------------------------------------------------------------------------------------------
// ViewModels
// ---------------------------------------------------------------------------------------------

/** A set of upstream ViewModels with one lifetime (the app, or one page) */
class VmScope {
    val store = ViewModelStore()

    inline fun <reified T : ViewModel> get(): T =
        ViewModelProvider.create(store, ViewModelProvider.NewInstanceFactory.instance, CreationExtras.Empty)[T::class]

    fun clear() = store.clear()
}

/** ViewModels that live as long as the app (home, search, library, downloads, plugins) */
object AppVms {
    val scope = VmScope()
    inline fun <reified T : ViewModel> get(): T = scope.get<T>()
}

@Composable
inline fun <reified T : ViewModel> appVm(): T = remember { AppVms.get<T>() }

// ---------------------------------------------------------------------------------------------
// Navigation
// ---------------------------------------------------------------------------------------------

sealed interface Route {
    data object Home : Route
    /** [only]: search in this extension only (Home page search); null searches all extensions */
    data class Search(val query: String? = null, val nonce: Long = System.nanoTime(), val only: String? = null) : Route
    data object Library : Route
    data object Downloads : Route
    data object Extensions : Route
    data class Settings(val page: String? = null) : Route

    /** Title page of a movie, series, anime, ... */
    data class Details(
        val url: String,
        val apiName: String,
        val name: String,
        val poster: String? = null,
        val startAction: Int = 0,
        val startValue: Int? = null,
    ) : Route

    /** A full list ("See all") */
    data class Section(val title: String, val items: List<SearchResponse>) : Route

    /** First run wizard */
    data object Setup : Route

    /** Dev only: glyph chart */
    data class Icons(val start: String) : Route

    /** The video player; the generator is the engine object that loads links and episodes */
    data class Player(
        val generator: com.lagradost.cloudstream3.ui.player.VideoGenerator<*>,
        val index: Int,
        val syncData: HashMap<String, String>? = null,
    ) : Route
}

enum class Tab(val route: Route) {
    Home(Route.Home), Search(Route.Search()), Library(Route.Library), Downloads(Route.Downloads),
    Extensions(Route.Extensions), Settings(Route.Settings())
}

private fun Route.tab(): Tab? = when (this) {
    Route.Home -> Tab.Home
    is Route.Search -> Tab.Search
    Route.Library -> Tab.Library
    Route.Downloads -> Tab.Downloads
    Route.Extensions -> Tab.Extensions
    is Route.Settings -> Tab.Settings
    else -> null
}

/** One page in the back stack, with the ViewModels that only live while it is in the stack */
class Entry(val route: Route) {
    val id = nextId++
    val vms = VmScope()

    /** A page opened with "play / resume" starts the player once; coming back to it (Back from the player) must not start it again */
    var autoStartDone = false

    private companion object {
        var nextId = 0L
    }
}

object Navigator {
    val stack = mutableStateListOf(Entry(Route.Home))
    val current: Entry get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    /** The nav pane item that stays selected: the last top level page in the stack */
    val selectedTab: Tab get() = stack.asReversed().firstNotNullOfOrNull { it.route.tab() } ?: Tab.Home

    /** Bumped on every navigation, for transitions that need direction */
    var lastWasBack by mutableStateOf(false)

    fun go(route: Route) {
        EventQueue.invokeLater {
            val top = stack.last().route
            if (top == route && route !is Route.Search) return@invokeLater
            // a search page is reused: the new query replaces the old one
            if (route is Route.Search && top is Route.Search) {
                val old = stack.removeAt(stack.lastIndex)
                old.vms.clear()
            }
            lastWasBack = false
            stack.add(Entry(route))
            while (stack.size > 40) stack.removeAt(0).vms.clear()
        }
    }

    /** Selecting a nav pane item */
    fun goTab(tab: Tab) = go(tab.route)

    /** Drops the whole history and shows [route] */
    fun reset(route: Route) {
        EventQueue.invokeLater {
            stack.forEach { it.vms.clear() }
            stack.clear()
            lastWasBack = false
            stack.add(Entry(route))
        }
    }

    fun back(): Boolean {
        // an Android dialog of an extension (its settings, a prompt) has the Back key first, as on Android
        if (com.lagradost.desktop.ui.DesktopUiHost.closeTopAndroidDialog()) return true
        if (stack.size <= 1) return false
        EventQueue.invokeLater {
            if (stack.size > 1) {
                lastWasBack = true
                stack.removeAt(stack.lastIndex).vms.clear()
            }
        }
        return true
    }

    fun openDetails(card: SearchResponse, startAction: Int = 0, startValue: Int? = null) =
        go(Route.Details(card.url, card.apiName, card.name, card.posterUrl, startAction, startValue))

    fun search(query: String?, only: String? = null) = go(Route.Search(query, only = only))
}

/**
 * Entry points the engine's code calls where the Android app navigated with its NavController
 * (opening a result, deep links). Active when the native UI runs.
 */
object NativeUi {
    @Volatile
    var active = false

    fun loadResult(url: String, apiName: String, name: String, startAction: Int, startValue: Int?): Boolean {
        if (!active) return false
        Navigator.go(Route.Details(url, apiName, name, null, startAction, startValue))
        return true
    }

    fun openPlayer(generator: com.lagradost.cloudstream3.ui.player.VideoGenerator<*>, index: Int, syncData: HashMap<String, String>?): Boolean {
        if (!active) return false
        Navigator.go(Route.Player(generator, index, syncData))
        return true
    }

    fun loadSearchResult(card: SearchResponse, startAction: Int, startValue: Int?): Boolean {
        if (!active) return false
        Navigator.openDetails(card, startAction, startValue)
        return true
    }
}

/** Background work for UI actions: runs on the IO dispatcher, failures are logged instead of crashing */
fun ioTask(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit): kotlinx.coroutines.Job =
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()).launch {
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            com.lagradost.cloudstream3.mvvm.logError(t)
        }
    }

/** Engine navigation by Android destination id, mapped to native pages */
fun nativeNavigate(id: Int, args: android.os.Bundle?): Boolean {
    if (!NativeUi.active) return false
    return when (id) {
        com.lagradost.cloudstream3.R.id.global_to_navigation_player -> {
            val uuid = args?.getString("uuid") ?: return true
            val generator = com.lagradost.cloudstream3.ui.player.GeneratorPlayer.generators[uuid] ?: return true
            @Suppress("UNCHECKED_CAST", "DEPRECATION")
            val sync = args.getSerializable("syncData") as? HashMap<String, String>
            Navigator.go(Route.Player(generator, args.getInt("index"), sync))
            true
        }
        com.lagradost.cloudstream3.R.id.navigation_home -> { Navigator.goTab(Tab.Home); true }
        com.lagradost.cloudstream3.R.id.navigation_search -> { Navigator.goTab(Tab.Search); true }
        com.lagradost.cloudstream3.R.id.navigation_library -> { Navigator.goTab(Tab.Library); true }
        com.lagradost.cloudstream3.R.id.navigation_downloads -> { Navigator.goTab(Tab.Downloads); true }
        com.lagradost.cloudstream3.R.id.navigation_settings -> { Navigator.goTab(Tab.Settings); true }
        else -> false
    }
}

fun nativeBack(): Boolean {
    if (!NativeUi.active) return false
    Navigator.back()
    return true
}
