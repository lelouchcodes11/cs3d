package com.lagradost.desktop.tools

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.runtime.LogBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Headless end to end test of the extension system: downloads real plugins from repositories through
 * the ported RepositoryManager/PluginManager and exercises their providers.
 *
 * Usage: ExtensionHarness <dataDir> <repos: official,phisher,megix,cnc|all> [load|full] [pluginFilter...]
 */
object ExtensionHarness {
    val REPOS = linkedMapOf(
        "official" to "https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json",
        "phisher" to "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/refs/heads/builds/repo.json",
        "megix" to "https://raw.githubusercontent.com/SaurabhKaperwan/CSX/builds/CS.json",
        "cnc" to "https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/refs/heads/builds/CNC.json",
    )

    data class ProviderResult(
        val name: String,
        var mainPage: String = "-",
        var search: String = "-",
        var load: String = "-",
        var links: String = "-",
        var play: String = "-",
    )

    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking {
        System.setProperty("java.awt.headless", "true")
        val dataDir = File(args.getOrNull(0) ?: "build/harness-data")
        val repoKeys = (args.getOrNull(1) ?: "all").let { if (it == "all") REPOS.keys.toList() else it.split(",") }
        val mode = args.getOrNull(2) ?: "load"
        val filters = args.drop(3).map { it.lowercase() }

        val activity = DesktopBootstrap.init(dataDir)
        DesktopBootstrap.onCreate(activity, loadPlugins = false, activityLifecycle = false)

        val pluginResults = installPlugins(activity, repoKeys, filters)
        println()
        println("== Plugin load results")
        pluginResults.toSortedMap().forEach { (k, v) -> println("   $k: $v") }
        val failed = pluginResults.count { !it.value.startsWith("OK") }
        println("   loaded ${pluginResults.size - failed}/${pluginResults.size}, providers: ${APIHolder.apis.size}, extractors: ${com.lagradost.cloudstream3.utils.extractorApis.size}")

        if (mode == "full") {
            testProviders(APIHolder.apis.toList())
        }
        if (mode == "live") liveScan(APIHolder.apis.toList())

        File(dataDir, "harness-log.txt").writeText(LogBuffer.snapshot().joinToString("\n"))
        println("Log written to ${File(dataDir, "harness-log.txt").absolutePath}")
        kotlin.system.exitProcess(0)
    }

    /** Downloads and loads the plugins of [repoKeys] whose name contains one of [filters] (all when empty); name -> status */
    suspend fun installPlugins(activity: com.lagradost.cloudstream3.MainActivity, repoKeys: List<String>, filters: List<String>): ConcurrentHashMap<String, String> = kotlinx.coroutines.coroutineScope {
        val pluginResults = ConcurrentHashMap<String, String>()
        for (key in repoKeys) {
            val url = REPOS[key] ?: error("Unknown repo $key")
            val repo = RepositoryManager.parseRepository(url)
            println("== Repository $key: ${repo?.name} (${repo?.pluginLists?.size} lists)")
            val data = RepositoryData(repo?.iconUrl, repo?.name ?: key, url)
            RepositoryManager.addRepository(data)
            val plugins = RepositoryManager.getRepoPlugins(data) ?: emptyList()
            println("   ${plugins.size} plugins")
            val selected = plugins.filter { w ->
                filters.isEmpty() || filters.any { f -> w.plugin.internalName.lowercase().contains(f) }
            }
            val semaphore = Semaphore(6)
            selected.map { w ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val start = System.currentTimeMillis()
                        val ok = try {
                            PluginManager.downloadPlugin(
                                activity,
                                w.plugin.url,
                                w.plugin.fileHash,
                                w.plugin.internalName,
                                url,
                                true
                            )
                        } catch (t: Throwable) {
                            println("   ! ${w.plugin.internalName}: $t")
                            false
                        }
                        val status = if (ok) "OK" else "FAILED"
                        pluginResults["$key/${w.plugin.internalName}"] =
                            "$status (${System.currentTimeMillis() - start} ms, status=${w.plugin.status})"
                    }
                }
            }.awaitAll()
        }

        pluginResults
    }

    /** Every event of the live providers: what kind of link, DRM, and does the address answer */
    private suspend fun liveScan(apis: List<MainAPI>) {
        for (api in apis.filter { it.hasMainPage }) {
            println("== live scan: ${api.name}")
            var total = 0
            val tally = java.util.TreeMap<String, Int>()
            for (section in api.mainPage.take(3)) {
                val items = runCatching { api.getMainPage(1, MainPageRequest(section.name, section.data, section.horizontalImages))?.items?.flatMap { it.list } }.getOrNull().orEmpty().take(25)
                for (item in items) {
                    total++
                    val line = withTimeoutOrNull(40_000) {
                        val response = runCatching { api.load(item.url) }.getOrNull()
                        val data = (response as? LiveStreamLoadResponse)?.dataUrl ?: (response as? MovieLoadResponse)?.dataUrl ?: return@withTimeoutOrNull "no data"
                        val links = mutableListOf<ExtractorLink>()
                        runCatching { api.loadLinks(data, false, {}, { synchronized(links) { links.add(it) } }) }
                        if (links.isEmpty()) return@withTimeoutOrNull "no links"
                        links.take(3).joinToString(" | ") { l ->
                            val drm = (l as? com.lagradost.cloudstream3.utils.DrmExtractorLink)?.let { d -> if (d.uuid == com.lagradost.cloudstream3.utils.CLEARKEY_DRM_UUID) "ClearKey" else "DRM " + d.uuid.toString().take(8) } ?: "clear"
                            "${l.type} $drm: ${probe(l)}"
                        }
                    } ?: "timeout"
                    val kind = when { line.contains("DRM ") -> "other DRM (no player support)"; line.contains("ClearKey") -> "ClearKey"; line.contains("no links") || line.contains("no data") || line == "timeout" -> line; line.contains("HTTP 4") || line.contains("ERR") -> "link answers with error"; else -> "ok" }
                    tally.merge(kind, 1, Int::plus)
                    println("   ${item.name.take(40)}: $line")
                }
            }
            println("   -- ${api.name}: $total events: $tally")
        }
    }

    private suspend fun testProviders(apis: List<MainAPI>) = kotlinx.coroutines.coroutineScope {
        println()
        println("== Provider tests (${apis.size})")
        val semaphore = Semaphore(8)
        val done = AtomicInteger()
        val results = apis.map { api ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val r = ProviderResult(api.name)
                    withTimeoutOrNull(120_000) { testProvider(api, r) } ?: run { r.links += " (timeout)" }
                    println("   [${done.incrementAndGet()}/${apis.size}] ${r.name}: home=${r.mainPage} search=${r.search} load=${r.load} links=${r.links} play=${r.play}")
                    r
                }
            }
        }.awaitAll()
        val ok = results.count { it.links.startsWith("OK") }
        println("   providers with playable links: $ok/${results.size}")
    }

    private suspend fun testProvider(api: MainAPI, r: ProviderResult) {
        var firstUrl: String? = null
        if (api.hasMainPage && api.mainPage.isNotEmpty()) {
            r.mainPage = try {
                val data = api.mainPage.first()
                val page = api.getMainPage(1, MainPageRequest(data.name, data.data, data.horizontalImages))
                val items = page?.items?.sumOf { it.list.size } ?: 0
                firstUrl = page?.items?.firstOrNull { it.list.isNotEmpty() }?.list?.firstOrNull()?.url
                if (items > 0) "OK($items)" else "EMPTY"
            } catch (t: Throwable) {
                "ERR(${t.javaClass.simpleName}: ${t.message?.take(80)})"
            }
        }
        // Same entry point as the app's search page
        r.search = when (val res = com.lagradost.cloudstream3.ui.APIRepository(api).search("love", 1)) {
            is com.lagradost.cloudstream3.mvvm.Resource.Success -> {
                val items = res.value.items
                if (firstUrl == null) firstUrl = items.firstOrNull()?.url
                if (items.isNotEmpty()) "OK(${items.size})" else "EMPTY"
            }
            is com.lagradost.cloudstream3.mvvm.Resource.Failure -> "ERR(${res.errorString.take(80)})"
            else -> "LOADING"
        }
        val url = firstUrl ?: return
        val loadResponse = try {
            api.load(url).also { r.load = if (it != null) "OK(${it.javaClass.simpleName})" else "NULL" }
        } catch (t: Throwable) {
            r.load = "ERR(${t.javaClass.simpleName}: ${t.message?.take(80)})"
            null
        } ?: return
        val data = when (loadResponse) {
            is MovieLoadResponse -> loadResponse.dataUrl
            is TvSeriesLoadResponse -> loadResponse.episodes.firstOrNull()?.data
            is AnimeLoadResponse -> loadResponse.episodes.values.firstOrNull()?.firstOrNull()?.data
            is LiveStreamLoadResponse -> loadResponse.dataUrl
            else -> null
        } ?: run {
            r.links = "NO_DATA"
            return
        }
        val links = mutableListOf<ExtractorLink>()
        var subs = 0
        r.links = try {
            api.loadLinks(data, false, { subs++ }, { synchronized(links) { links.add(it) } })
            if (links.isNotEmpty()) {
                // do the first links of different kinds answer with something a player can use?
                val sample = synchronized(links) { links.distinctBy { it.name }.take(4) }
                r.play = sample.joinToString(" | ") { l -> l.name.take(18) + ": " + probe(l) }
            }
            if (links.isNotEmpty()) "OK(${links.size} links, $subs subs)" else "EMPTY"
        } catch (t: Throwable) {
            if (links.isNotEmpty()) "OK(${links.size} links, $subs subs, then ${t.javaClass.simpleName})"
            else "ERR(${t.javaClass.simpleName}: ${t.message?.take(80)})"
        }
    }

    private val probeClient: java.net.http.HttpClient by lazy {
        java.net.http.HttpClient.newBuilder().followRedirects(java.net.http.HttpClient.Redirect.ALWAYS).connectTimeout(java.time.Duration.ofSeconds(10)).build()
    }

    private class Fetched(val code: Int, val type: String, val bytes: ByteArray)

    /** Reads at most [limit] bytes: a server that ignores Range sends the whole file, and with a video that is gigabytes held in memory */
    private fun fetch(url: String, headers: Map<String, String>, range: Boolean, limit: Int = 4096): Fetched {
        val b = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).timeout(java.time.Duration.ofSeconds(15)).GET()
        if (range) b.header("Range", "bytes=0-4095")
        for ((k, v) in headers) runCatching { b.header(k, v) }
        val r = probeClient.send(b.build(), java.net.http.HttpResponse.BodyHandlers.ofInputStream())
        val bytes = r.body().use { it.readNBytes(limit) }
        return Fetched(r.statusCode(), r.headers().firstValue("content-type").orElse(""), bytes)
    }

    /** A cheap stand-in for playing: the address answers, and the first bytes look like media (playlist + first segment) */
    private fun probe(link: ExtractorLink): String = runCatching {
        val headers = HashMap(link.headers)
        if (link.referer.isNotBlank() && headers.keys.none { it.equals("Referer", true) }) headers["Referer"] = link.referer
        val r = fetch(link.url, headers, true)
        val head = String(r.bytes.copyOf(minOf(r.bytes.size, 200)), Charsets.ISO_8859_1)
        val type = r.type
        when {
            r.code !in 200..299 -> "HTTP ${r.code}"
            head.startsWith("#EXTM3U") -> {
                val text = String(fetch(link.url, headers, false, 4_000_000).bytes, Charsets.UTF_8)
                val seg = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                if (seg == null) "m3u8 (no entries)"
                else {
                    val abs = java.net.URI.create(link.url).resolve(seg).toString()
                    val s = fetch(abs, headers, true)
                    if (s.code !in 200..299) "m3u8, segment HTTP ${s.code}"
                    else if (seg.startsWith("#EXT-X-STREAM-INF")) "m3u8 master" else "m3u8 ok" + if (text.contains("#EXT-X-STREAM-INF")) " (master)" else ""
                }
            }
            head.contains("<html", true) || head.contains("<!doctype", true) -> "HTML page, not media"
            type.contains("video") || type.contains("octet") || type.contains("mpegurl") || type.contains("dash") || r.bytes.size >= 1024 -> "media ($type)"
            else -> "? $type ${r.bytes.size}b"
        }
    }.getOrElse { "ERR ${it.javaClass.simpleName}" }
}
