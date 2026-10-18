package com.lagradost.desktop.net

import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.desktop.runtime.AndroidRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/**
 * The icon of an extension, for the list of extensions at the top of Home. A provider (an API name) knows its plugin file; the repositories
 * know the icon of every plugin by its download address. The icons are kept in a small file, so the list has them at once at the next start
 * and no repository has to be asked for them again.
 */
object ExtensionIcons {
    private const val TAG = "ExtensionIcons"
    private val byUrl = HashMap<String, String>()
    private val fileToUrl = HashMap<String, String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false
    private val file: File get() = File(AndroidRuntime.dataDir, "extension_icons.json")

    /** Changes when more icons are known: the composables that read it draw again */
    val version = mutableIntStateOf(0)

    fun start() {
        if (started) return
        started = true
        scope.launch {
            runCatching {
                if (file.isFile) JSONObject(file.readText()).let { j -> j.keys().forEach { k -> j.optString(k).takeIf { it.isNotBlank() }?.let { byUrl[k] = it } } }
            }
            refreshInstalled()
            version.intValue++
            // then the repositories (a request each), only when an installed plugin has no icon yet
            val missing = fileToUrl.values.any { byUrl[it] == null }
            if (missing) runCatching {
                for (repo in RepositoryManager.getRepositories()) {
                    RepositoryManager.getRepoPlugins(repo)?.forEach { w -> w.plugin.iconUrl?.takeIf { it.isNotBlank() }?.let { byUrl[w.plugin.url] = it } }
                }
                runCatching { file.writeText(JSONObject(byUrl as Map<*, *>).toString()) }
                version.intValue++
            }.onFailure { Log.w(TAG, "repositories: $it") }
        }
    }

    private var lastRefresh = 0L

    private fun refreshInstalled() {
        lastRefresh = System.currentTimeMillis()
        runCatching {
            for (p in PluginManager.getPluginsOnline()) p.url?.let { fileToUrl[p.filePath] = it }
        }
    }

    /** The icon url of the provider [apiName] (null when it has none known) */
    fun iconFor(apiName: String): String? {
        version.intValue // read: redraws when icons arrive
        val api = APIHolder.getApiFromNameNull(apiName) ?: return null
        val plugin = api.sourcePlugin ?: return null
        return iconForFile(plugin)
    }

    /**
     * The icon url of the installed plugin file [filePath]. The Installed page of the extensions lists the plugins from what is on the disk,
     * which has no icon; the repositories know it by the address the plugin was downloaded from.
     */
    fun iconForFile(filePath: String): String? {
        version.intValue // read: redraws when icons arrive
        // a plugin that is not from a repository (a local file) stays unknown: ask the plugin list again at most every 3 s, not at every draw
        val url = fileToUrl[filePath] ?: run { if (System.currentTimeMillis() - lastRefresh > 3_000) refreshInstalled(); fileToUrl[filePath] } ?: return null
        return byUrl[url]?.let { sharper(it) }
    }

    // https://www.google.com/s2/favicons?domain=example.com&sz=%size%: that service stretches the site's 16 px icon to the size asked for
    // (a blur). The one behind the browser's own tab icons gives the largest real icon of the site, up to 256 px, and a small one only when
    // the site has no more.
    private val s2Favicon = Regex("""^https?://(?:www\.)?google\.com/s2/favicons\?(.*)$""", RegexOption.IGNORE_CASE)

    internal fun sharper(raw: String): String {
        val plain = raw.replace("%size%", "128").replace("%exact_size%", "128")
        val query = s2Favicon.find(plain)?.groupValues?.get(1) ?: return plain
        val param = query.split('&').mapNotNull { kv -> kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            .firstOrNull { it.first == "domain" || it.first == "domain_url" } ?: return plain
        val site = runCatching { java.net.URLDecoder.decode(param.second, "UTF-8") }.getOrDefault(param.second)
        val host = site.removePrefix("https://").removePrefix("http://").substringBefore('/').trim()
        if (host.isEmpty() || !host.contains('.')) return plain
        return "https://t3.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://$host&size=256"
    }
}
