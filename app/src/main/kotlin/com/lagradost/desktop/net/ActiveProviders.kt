package com.lagradost.desktop.net

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.core.Route
import com.lagradost.desktop.runtime.web.CloudflareGate

/**
 * Which extensions the user is working with right now, for [CloudflareGate]: a Cloudflare check window is only worth showing for one of them.
 * AnimePahe's plugin looks at its site while it loads (at every start of the app), and its check window used to come up over Home whatever
 * extension was chosen there. In use: the extension chosen on Home, the extension of every title page in the stack (a player sits above one),
 * and every extension while a search in all of them is the page on top (its hidden pages only: a WINDOW is never put up for a search in all extensions, and the empty Search
 * page does not count as a search: opening it put AnimePahe's check window on screen).
 */
object ActiveProviders {
    fun install() {
        CloudflareGate.activeApis = ::names
        CloudflareGate.activeApisForWindows = ::namesForWindows
        CloudflareGate.apisOfLoader = ::ofLoader
        CloudflareGate.apisOfHost = ::ofHost
        CloudflareGate.managingExtensions = ::managing
    }

    private fun names(): Set<String>? {
        val top = Navigator.stack.toList().lastOrNull()?.route
        // a search that has a query looks in every extension; the empty Search page (Explore) looks in none
        if (top is Route.Search && top.only == null && !top.query.isNullOrBlank()) return null
        return namesForWindows()
    }

    /** The extensions a window may be put up for: the one chosen on Home, the one of each title page that is open and the one a Home search is limited to */
    private fun namesForWindows(): Set<String> {
        val stack = Navigator.stack.toList()
        val top = stack.lastOrNull()?.route
        val out = HashSet<String>()
        for (entry in stack) (entry.route as? Route.Details)?.let { out += it.apiName }
        (top as? Route.Search)?.only?.let { out += it }
        DataStoreHelper.currentHomePage?.let { out += it }
        return out
    }

    /** The Extensions or Settings page is on top: a plugin's own settings window is wanted there */
    private fun managing(): Boolean = Navigator.stack.lastOrNull()?.route.let { it is Route.Extensions || it is Route.Settings }

    private fun ofLoader(loader: ClassLoader?): Set<String> {
        val file = PluginManager.pluginFileOfLoader(loader) ?: return emptySet()
        val own = APIHolder.allProviders.filter { it.sourcePlugin == file }.map { it.name }.toSet()
        // a plugin whose extensions are not registered yet is still loading: what it asks for now (AnimePahe checks its site there) is nobody's request,
        // so it counts as an extension that is not in use (an empty answer would mean "not an extension's window: show it")
        return own.ifEmpty { setOf("plugin:" + java.io.File(file).name) }
    }

    private fun ofHost(host: String): Set<String> =
        APIHolder.allProviders.filter { api ->
            val h = runCatching { java.net.URI(api.mainUrl).host }.getOrNull()?.removePrefix("www.") ?: return@filter false
            val x = host.removePrefix("www.")
            h == x || x.endsWith(".$h") || h.endsWith(".$x")
        }.map { it.name }.toSet()
}
