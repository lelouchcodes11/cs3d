package com.lagradost.desktop.isolate

import android.util.Log
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.lagradost.cloudstream3.APIHolder
import kotlin.reflect.full.createInstance
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.plugins.PluginData
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.runtime.AndroidRuntime
import com.lagradost.desktop.runtime.UiHost
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import java.io.File
import kotlin.system.exitProcess

/**
 * The program that runs in the AppContainer (see [AppContainerLauncher] and [IsolatedHost]): the engine of the app without a window, with the extensions
 * loaded into it. It answers the app's requests (load an extension, search, load a title, get the links of an episode) and asks the app for the few
 * things that need a screen or the user's rights: a hidden browser for a Cloudflare check, a message for the user, an address to open.
 * Everything it can reach on this PC is what the container was granted: its own data folder, the extension files (read only), the program files.
 */
object ExtensionWorker {
    private const val TAG = "ExtensionWorker"

    @Volatile
    private var peer: RpcPeer? = null

    private val browser = WorkerBrowser({ peer }, { callingExtension() })

    /** A request of this process to the app, answered on the calling thread (null: there is no app to ask, or it did not answer) */
    internal fun askApp(op: String, body: JsonNode, timeoutMs: Long): JsonNode? =
        runCatching { runBlocking { peer?.call(op, body, timeoutMs) } }.getOrNull()

    /**
     * What an extension's load(context) is given: the activity, as in the app (CNCVerse's plugins cast it to AppCompatActivity; the application object fails that cast and
     * the extension does not load at all)
     */
    @Volatile internal var pluginContext: android.content.Context? = null

    @JvmStatic
    fun main(args: Array<String>) {
        // the protocol owns standard output; whatever an extension prints goes to standard error (the app logs it)
        val protocolOut = System.out
        System.setOut(System.err)
        System.setProperty("java.awt.headless", "true")
        val dataDir = File(System.getProperty("cloudstream.data") ?: error("no data folder"))
        val activity = DesktopBootstrap.init(dataDir)
        pluginContext = activity
        AndroidRuntime.host = WorkerUiHost
        DesktopBootstrap.onCreate(activity, loadPlugins = false, activityLifecycle = false)
        val rpc = RpcPeer("worker", System.`in`, protocolOut) { handle(it) }
        peer = rpc
        rpc.onClosed = { reason -> System.err.println("worker: $reason"); exitProcess(0) }
        WebViewResolver.remote = RemoteBrowser { rpc }
        // an extension that builds a browser view of its own gets a page in the app's browser (see WorkerBrowser); it can also show it to the user in a window of the app
        android.webkit.RemoteBrowsers.factory = browser
        // when the app cannot make a page, the extension is taken out of the sandbox, as before: the app is told which extension it is and moves it into the app
        android.webkit.WebBrowserGate.unavailable = {
            val path = callingExtension()
            if (path != null) runCatching { runBlocking { rpc.call("needsApp", mapper.createObjectNode().put("path", path).put("reason", "a browser of its own"), 60_000) } }
            throw UnsupportedOperationException("a browser is not available in the extension sandbox")
        }
        rpc.start()
        System.err.println("worker: ready (pid ${ProcessHandle.current().pid()})")
        Thread.currentThread().join()
    }

    /** What the app can be asked for by an extension: toasts and links to open go to the app, which decides */
    private object WorkerUiHost : UiHost by UiHost.Headless {
        override fun showToast(message: CharSequence, long: Boolean) {
            val p = peer ?: return
            runCatching { runBlocking { p.call("toast", mapper.createObjectNode().put("text", message.toString()).put("long", long), 3_000) } }
        }

        override fun openUrl(url: String): Boolean {
            val p = peer ?: return false
            return runCatching { runBlocking { p.call("openUrl", mapper.createObjectNode().put("url", url), 3_000).asBoolean() } }.getOrDefault(false)
        }

        // a dialog with a page in it (a Cloudflare check): the page is the app's, and so is the window it is shown in
        override fun showDialog(dialog: android.app.Dialog) = browser.dialogShown(dialog)
        override fun dismissDialog(dialog: android.app.Dialog) = browser.dialogDismissed(dialog)
    }

    /** The extension a probe of the tests acts as (set only by the probe operation, which exists only when the tests ask for it) */
    @Volatile internal var probeExtension: String? = null

    /** The file of the extension whose code is on the call stack (the first class of a dex class loader), or null */
    private fun callingExtension(): String? =
        StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk { frames ->
            frames.map { it.declaringClass.classLoader }.filter { it is dalvik.system.BaseDexClassLoader }.findFirst().orElse(null)
        }?.let { PluginManager.pluginFileOfLoader(it) } ?: probeExtension

    private fun provider(path: String, name: String): MainAPI =
        APIHolder.allProviders.toList().firstOrNull { it.name == name && it.sourcePlugin == path } ?: error("no provider $name in $path")

    private val cloneLock = Any()

    /**
     * A call for a cloned site (Settings > General > Clone site: an extension's provider under another address). The copy is made here, from the
     * extension's own class, the first time it is called for (and again after the extension was loaded anew): the app only knows what it is a copy of.
     */
    private fun ensureClone(b: JsonNode) {
        val parentName = b.get("cloneOf")?.asText() ?: return
        val path = b.get("path")?.asText() ?: return
        val name = b.get("api")?.asText() ?: return
        synchronized(cloneLock) {
            val all = APIHolder.allProviders.toList()
            if (all.any { it.name == name && it.sourcePlugin == path }) return
            val parent = all.firstOrNull { it.name == parentName && it.sourcePlugin == path } ?: return
            val copy = parent::class.createInstance().apply {
                this.name = name
                b.get("cloneLang")?.asText()?.takeIf { it.isNotBlank() }?.let { lang = it }
                mainUrl = b.get("cloneUrl").asText().trimEnd('/')
                canBeOverridden = false
                sourcePlugin = path
            }
            APIHolder.allProviders.add(copy)
            System.err.println("worker: cloned site $name (${copy.mainUrl}) of $parentName")
        }
    }

    private suspend fun handle(req: RpcPeer.Incoming): JsonNode? {
        val b = req.body
        if (b.has("cloneOf")) ensureClone(b)
        return when (req.op) {
            "ping" -> mapper.createObjectNode().put("pid", ProcessHandle.current().pid())
            // how much of its memory the program uses (the app shows it in the log when something is slow)
            "stats" -> Runtime.getRuntime().let { r -> mapper.createObjectNode().put("usedMb", (r.totalMemory() - r.freeMemory()) / 1_048_576).put("committedMb", r.totalMemory() / 1_048_576).put("maxMb", r.maxMemory() / 1_048_576).put("threads", Thread.activeCount()) }
            // what the tests ask: what this process can do on the PC (only when the app that started it asked for the probe)
            "probe" -> if (System.getProperty("cloudstream.workerprobe") != null) SandboxProbe.run(b) else error("unknown operation probe")
            "settings" -> { applySettings(b); null }
            "loadPlugin" -> loadPlugin(b)
            "unloadPlugin" -> { PluginManager.unloadPlugin(b.get("path").asText()); null }
            "mainPage" -> {
                val api = provider(b.get("path").asText(), b.get("api").asText())
                val page = api.getMainPage(b.get("page").asInt(1), MainPageRequest(b.get("name").asText(), b.get("data").asText(), b.get("horizontal").asBoolean()))
                page?.let { response ->
                    val lists = mapper.createArrayNode()
                    for (list in response.items) {
                        lists.add(mapper.createObjectNode().put("name", list.name).put("horizontal", list.isHorizontalImages).also { n ->
                            n.set<JsonNode>("list", mapper.createArrayNode().also { a -> list.list.forEach { a.add(IsolationSerde.write(it, com.lagradost.cloudstream3.SearchResponse::class.java)) } })
                        })
                    }
                    mapper.createObjectNode().put("hasNext", response.hasNext).also { it.set<JsonNode>("lists", lists) }
                }
            }
            "search" -> {
                val api = provider(b.get("path").asText(), b.get("api").asText())
                val result = api.search(b.get("q").asText(), b.get("page").asInt(1))
                result?.let { r -> mapper.createObjectNode().put("hasNext", r.hasNext).also { n -> n.set<JsonNode>("items", searchItems(r.items)) } }
            }
            "quickSearch" -> {
                val api = provider(b.get("path").asText(), b.get("api").asText())
                api.quickSearch(b.get("q").asText())?.let { mapper.createObjectNode().also { n -> n.set<JsonNode>("items", searchItems(it)) } }
            }
            "load" -> {
                val api = provider(b.get("path").asText(), b.get("api").asText())
                api.load(b.get("url").asText())?.let { IsolationSerde.write(it, com.lagradost.cloudstream3.LoadResponse::class.java) }
            }
            "loadLinks" -> {
                val api = provider(b.get("path").asText(), b.get("api").asText())
                val ok = api.loadLinks(
                    b.get("data").asText(), b.get("casting").asBoolean(),
                    { sub: SubtitleFile -> req.emit("sub", mapper.valueToTree<JsonNode>(sub)) },
                    { link: ExtractorLink -> req.emit("link", IsolationSerde.write(link, ExtractorLink::class.java)) },
                )
                mapper.createObjectNode().put("ok", ok)
            }
            // an extractor the extension registered, asked for by the app (a trailer, an extension in the app that meets that host)
            "extract" -> {
                val path = b.get("path").asText()
                val api = com.lagradost.cloudstream3.utils.extractorApis.toList().firstOrNull { it.sourcePlugin == path && it.name == b.get("name").asText() } ?: error("no extractor ${b.get("name")}")
                api.getUrl(
                    b.get("url").asText(), b.get("referer")?.takeIf { !it.isNull }?.asText(),
                    { sub: SubtitleFile -> req.emit("sub", mapper.valueToTree<JsonNode>(sub)) },
                    { link: ExtractorLink -> req.emit("link", IsolationSerde.write(link, ExtractorLink::class.java)) },
                )
                null
            }
            "hasInterceptor" -> {
                val api = provider(b.get("path").asText(), b.get("api").asText())
                val link = IsolationSerde.read(b.get("link"), ExtractorLink::class.java) ?: error("not a link")
                mapper.valueToTree<JsonNode>(WorkerInterceptors.interceptorOf(api, link) != null)
            }
            "interceptOpen" -> WorkerInterceptors.open(provider(b.get("path").asText(), b.get("api").asText()), b)
            "interceptRead" -> WorkerInterceptors.read(b)
            "interceptClose" -> { WorkerInterceptors.close(b.get("id").asLong()); null }
            // what the app's page of an extension's browser did (see RemoteBrowserHost), and the user's Cancel in the window that shows it
            "wvEvent" -> browser.event(b)
            "dialogCancel" -> { browser.cancel(b.get("id").asText()); null }
            else -> error("unknown operation ${req.op}")
        }
    }

    private fun searchItems(items: List<com.lagradost.cloudstream3.SearchResponse>): ArrayNode =
        mapper.createArrayNode().also { a -> items.forEach { a.add(IsolationSerde.write(it, com.lagradost.cloudstream3.SearchResponse::class.java)) } }

    /**
     * What the app tells this process (see IsolatedHost.settingsSnapshot): its settings that extensions read, the data store keys that are an extension's, the
     * extensions' own preference files (what the user changes on an extension's settings page) and the browser's user agent. Merged into this process's preferences:
     * what an extension saved here itself (a token it refreshed) is not taken away.
     */
    private fun applySettings(payload: JsonNode) {
        val ctx = AndroidRuntime.context
        payload.get("userAgent")?.asText()?.takeIf { it.isNotBlank() }?.let { Log.i(TAG, "the app's user agent: $it"); WebViewResolver.webViewUserAgent = it; com.lagradost.desktop.runtime.web.WebRuntime.reportedUserAgent = it }
        merge(androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx), payload.get("default"))
        merge(ctx.getSharedPreferences("rebuild_preference", 0), payload.get("store"))
        payload.get("files")?.fields()?.forEach { (name, values) -> if (Regex("[A-Za-z0-9._-]{1,120}").matches(name)) merge(ctx.getSharedPreferences(name, 0), values) }
    }

    private fun merge(prefs: android.content.SharedPreferences, settings: JsonNode?) {
        if (settings == null || !settings.isObject) return
        val edit = prefs.edit()
        settings.fields().forEach { (key, spec) ->
            val v = spec.get("v")
            when (spec.get("t")?.asText()) {
                "s" -> edit.putString(key, v.asText())
                "i" -> edit.putInt(key, v.asInt())
                "l" -> edit.putLong(key, v.asLong())
                "f" -> edit.putFloat(key, v.floatValue())
                "b" -> edit.putBoolean(key, v.asBoolean())
                "set" -> edit.putStringSet(key, v.map { it.asText() }.toSet())
            }
        }
        edit.apply()
    }

    private suspend fun loadPlugin(b: JsonNode): JsonNode {
        val path = b.get("path").asText()
        val data = PluginData(b.get("internalName").asText(), b.get("url")?.asText(), true, path, -1)
        val loaded = PluginManager.loadPluginFile(pluginContext ?: AndroidRuntime.context, File(path), data)
        val result = mapper.createObjectNode().put("loaded", loaded)
        if (!loaded) return result
        val plugin = PluginManager.plugins[path]
        val providers = APIHolder.allProviders.toList().filter { it.sourcePlugin == path }
        // the extension's settings page is code that needs the app's window: the app runs it when the user opens it (see Isolation.openSettings), so it only says that it has one;
        // a video interceptor is run here, for the app's player (see WorkerInterceptors); a browser view gets a page in the app (see WorkerBrowser). The video actions an extension
        // registers (open in another player) have nothing to run in on the desktop (the app lists none), so they stay here, unused.
        val hasSettings = (plugin as? Plugin)?.openSettings != null
        val array = mapper.createArrayNode()
        for (p in providers) {
            array.add(
                mapper.createObjectNode().put("name", p.name).put("mainUrl", p.mainUrl).put("lang", p.lang).put("hasMainPage", p.hasMainPage).put("className", p::class.simpleName)
                    .put("hasQuickSearch", p.hasQuickSearch).put("usesWebView", p.usesWebView).put("instantLinkLoading", p.instantLinkLoading)
                    .put("hasChromecastSupport", p.hasChromecastSupport).put("hasDownloadSupport", p.hasDownloadSupport)
                    .put("vpn", p.vpnStatus.name).put("providerType", p.providerType.name).put("hasInterceptor", overridesInterceptor(p))
                    .also { n ->
                        n.set<JsonNode>("types", mapper.valueToTree(p.supportedTypes.map { it.name }))
                        n.set<JsonNode>("syncNames", mapper.valueToTree(p.supportedSyncNames.map { it.name }))
                        n.set<JsonNode>("mainPage", mapper.createArrayNode().also { a ->
                            p.mainPage.forEach { a.add(mapper.createObjectNode().put("name", it.name).put("data", it.data).put("horizontal", it.horizontalImages)) }
                        })
                    },
            )
        }
        result.set<JsonNode>("providers", array)
        result.set<JsonNode>(
            "extractors",
            mapper.createArrayNode().also { a ->
                com.lagradost.cloudstream3.utils.extractorApis.toList().filter { it.sourcePlugin == path }
                    .forEach { a.add(mapper.createObjectNode().put("name", it.name).put("mainUrl", it.mainUrl).put("requiresReferer", it.requiresReferer)) }
            },
        )
        result.put("hasSettings", hasSettings)
        return result
    }

    private fun overridesInterceptor(api: MainAPI): Boolean =
        runCatching { api.javaClass.getMethod("getVideoInterceptor", ExtractorLink::class.java).declaringClass != MainAPI::class.java }.getOrDefault(false)

    /** The hidden browser of an extension (a Cloudflare check, a page that shows the video address only to a browser) is the app's: the app opens it and sends what it sees */
    private class RemoteBrowser(private val rpc: () -> RpcPeer) : WebViewResolver.Remote {
        override suspend fun resolve(
            resolver: WebViewResolver, request: Request, requestCallBack: (Request) -> Boolean,
        ): Pair<Request?, List<Request>> {
            val body = mapper.createObjectNode()
                .put("url", request.url.toString()).put("method", request.method)
                .put("intercept", source(resolver.interceptUrl)).put("userAgent", resolver.userAgent).put("script", resolver.script)
                .put("timeout", resolver.timeout)
            body.set<JsonNode>("headers", mapper.valueToTree(request.headers.toMultimap().mapValues { it.value.firstOrNull().orEmpty() }))
            body.set<JsonNode>("additional", mapper.valueToTree(resolver.additionalUrls.map { source(it) }))
            val collected = ArrayList<Request>()
            var final: Request? = null
            // a Cloudflare check may be held until its extension is the one in use (see IsolatedHost.webview): the call waits as long
            val cloudflareSolve = source(resolver.interceptUrl) == ".^"
            val hold = if (cloudflareSolve) 190_000 else 15_000
            val reply = rpc().call("webview", body, resolver.timeout + hold) { op, node ->
                when (op) {
                    "request" -> runCatching { toRequest(node) }.getOrNull()?.let { r ->
                        synchronized(collected) { collected += r }
                        requestCallBack(r)
                    }
                    "script" -> resolver.scriptCallback?.invoke(node.asText())
                }
            }
            // the cookies the app's browser holds for this site (the Cloudflare clearance) are this process's too from now on: its own requests carry them
            reply.get("cookies")?.forEach { c -> runCatching { com.lagradost.desktop.runtime.web.WebCookies.put(mapper.treeToValue(c, com.lagradost.desktop.runtime.web.WebCookie::class.java)) } }
            // Cloudflare ties its clearance to the user agent that passed the check: this process's requests use the browser's from now on
            reply.get("userAgent")?.asText()?.takeIf { it.isNotBlank() }?.let { WebViewResolver.webViewUserAgent = it }
            // the extension looks for the clearance in the cookies while its page works (CloudflareKiller does, at every request of the page); here the cookies come only when the page
            // has ended, so it looks once more now that they are in: without that look it never found the clearance the app's browser had earned and went on without it
            if (cloudflareSolve) runCatching { requestCallBack(request) }
            reply.get("final")?.takeIf { !it.isNull }?.let { final = runCatching { toRequest(it) }.getOrNull() }
            val extra = synchronized(collected) { collected.filter { it.url != final?.url } }
            return final to extra
        }

        /** A pattern as text, with the case flag (the browser side builds a Regex from it) */
        private fun source(r: Regex) = if (RegexOption.IGNORE_CASE in r.options) "(?i)" + r.pattern else r.pattern

        private fun toRequest(node: JsonNode): Request {
            val builder = Request.Builder().url(node.get("url").asText())
            node.get("headers")?.fields()?.forEach { (k, v) -> builder.header(k, v.asText()) }
            return builder.method(node.get("method")?.asText() ?: "GET", null).build()
        }
    }
}
