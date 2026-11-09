package com.lagradost.desktop.isolate

import android.util.Log
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.TextNode
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.desktop.runtime.AndroidRuntime
import com.lagradost.desktop.runtime.web.WebCookies
import com.lagradost.nicehttp.requestCreator
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request
import java.io.File

/**
 * The app's side of the extension sandbox: starts the sandbox process ([ExtensionWorker] in a Windows AppContainer), keeps it running, loads the
 * extensions into it and carries the app's calls (search, load a title, get links) over the pipe. One process holds all the sandboxed extensions, so
 * extractors of one are found by the others, as in the app.
 *
 * Nothing the sandbox sends is trusted: what it asks of the app ([serve]) is checked here, and what it returns is parsed by [IsolationSerde].
 */
object IsolatedHost {
    private const val TAG = "IsolatedHost"
    internal const val CONTAINER = "CloudStream.Extensions"
    private const val MEMORY_LIMIT = 1_536L * 1024 * 1024

    /** What an extension in the sandbox is made of: where its file is, and what the repository calls it */
    class Spec(val path: String, val internalName: String, val url: String?)

    private val startLock = Mutex()
    private val loaded = LinkedHashMap<String, Spec>()
    private val browsers = Semaphore(2)

    @Volatile private var process: AppContainerLauncher.Isolated? = null
    @Volatile private var peer: RpcPeer? = null
    @Volatile private var settingsHash = 0
    @Volatile var lastError: String? = null
        private set

    private var failedStarts = 0
    private var lastFailedStart = 0L

    private fun pauseAfterFailure(): Long = when (failedStarts) { 1 -> 15_000L; 2 -> 60_000L; else -> 5 * 60_000L }

    /** Why the sandbox is not running, when it was asked to start and could not (null: it runs, or nobody asked yet) */
    fun startFailure(): String? = if (failedStarts > 0) lastError else null

    /** What the sandbox program printed last (the Java runtime says why it cannot start before it ends) */
    private val recentOutput = java.util.ArrayDeque<String>()

    private fun noteOutput(line: String) = synchronized(recentOutput) { recentOutput.addLast(line.take(300)); while (recentOutput.size > 40) recentOutput.removeFirst() }

    private fun diagnose(child: AppContainerLauncher.Isolated): String {
        child.waitFor(2_000)
        val code = child.exitCode()
        val said = synchronized(recentOutput) { recentOutput.toList().takeLast(6).joinToString(" | ") }
        return "the sandbox program ended" + (code?.let { " (exit code 0x" + Integer.toHexString(it).uppercase() + ")" } ?: "") + (if (said.isNotBlank()) ": $said" else "")
    }

    fun running(): Boolean = peer?.isClosed == false && process?.isAlive() == true

    /** The pipe to the sandbox while it runs (the pages of its extensions' browsers send their events through it) */
    internal fun rpcOrNull(): RpcPeer? = peer?.takeIf { !it.isClosed }

    fun owns(path: String): Boolean = synchronized(loaded) { path in loaded }

    private val loading = HashSet<String>()

    /** Whether the extension of this file is in the sandbox or is being loaded there (its load code may already ask the app for a page: StreamPlay's Enma opens one at once) */
    fun ownsOrLoads(path: String): Boolean = synchronized(loaded) { path in loaded } || synchronized(loading) { path in loading }

    fun specOf(path: String): Spec? = synchronized(loaded) { loaded[path] }

    private val settingsPaths = HashSet<String>()

    /** Whether the extension of this file has a settings page of its own (it is opened in the app: see Isolation.openSettings) */
    fun hasSettings(path: String): Boolean = synchronized(settingsPaths) { path in settingsPaths }

    /** The file of the sandboxed extension that was installed from [urlOrPath] (the Extensions page knows an extension by its address) */
    fun pathOfUrl(urlOrPath: String): String? = synchronized(loaded) { loaded.values.firstOrNull { it.url == urlOrPath || it.path == urlOrPath }?.path }

    private val workerRoot: File get() = File(AndroidRuntime.dataDir, "isolation").also { it.mkdirs() }

    // ---- start-up ------------------------------------------------------------------------------------------------------------------------------

    /** The sandbox process, started (or started again after it ended). Null when it cannot be started here: the extensions then run in the app. */
    private suspend fun ensureRunning(): RpcPeer? {
        peer?.takeIf { !it.isClosed && process?.isAlive() == true }?.let { return it }
        return startLock.withLock {
            peer?.takeIf { !it.isClosed && process?.isAlive() == true }?.let { return@withLock it }
            // a start that just failed is not repeated for every extension that loads (twenty of them made twenty processes that died the same way): it is tried again after a pause
            if (failedStarts > 0 && System.currentTimeMillis() - lastFailedStart < pauseAfterFailure()) return@withLock null
            val started = runCatching { start() }.onFailure {
                failedStarts++
                lastFailedStart = System.currentTimeMillis()
                lastError = it.message ?: it.javaClass.simpleName
                Log.e(TAG, "the sandbox did not start ($failedStarts): ${it.message}")
            }.getOrNull() ?: return@withLock null
            failedStarts = 0
            lastError = null
            // after a restart the extensions that were in it are loaded again
            val specs = synchronized(loaded) { loaded.values.toList() }
            for (spec in specs) runCatching { started.call("loadPlugin", loadBody(spec), 120_000) }.onFailure { Log.w(TAG, "${spec.internalName} did not load again: $it") }
            started
        }
    }

    /** A Windows that starts the program with the extra process policies and then lets it die (some do): it is started without them from then on */
    @Volatile private var policiesBroken = false

    private suspend fun start(): RpcPeer {
        try {
            return startOnce(!policiesBroken)
        } catch (t: Throwable) {
            if (policiesBroken) throw t
            Log.w(TAG, "the sandbox did not start with the process policies (${t.message}): trying without")
            policiesBroken = true
            return startOnce(false)
        }
    }

    private suspend fun startOnce(policies: Boolean): RpcPeer {
        val (child, rpc) = launch(workerRoot, policies)
        process = child
        rpc.onClosed = { reason -> Log.w(TAG, "the sandbox ended: $reason"); child.destroy(); if (peer === rpc) peer = null; RemoteBrowserHost.closeFor(null) }
        peer = rpc
        settingsHash = 0
        try {
            rpc.call("ping", null, 60_000)
            syncSettings(rpc)
        } catch (t: Throwable) {
            val why = diagnose(child)
            runCatching { rpc.close("it did not answer") }
            runCatching { child.destroy() }
            if (peer === rpc) peer = null
            throw IllegalStateException(why, t)
        }
        Log.i(TAG, "the sandbox runs (pid ${child.pid})")
        return rpc
    }

    /** Starts the sandbox process with its files under [root] and connects to it (also used by the tests, which have no app around them) */
    internal fun launch(root: File, policies: Boolean = true): Pair<AppContainerLauncher.Isolated, RpcPeer> {
        check(AppContainerLauncher.available) { "no AppContainer here" }
        val workerRoot = root
        val data = File(workerRoot, "worker").also { it.mkdirs() }
        val tmp = File(data, "tmp").also { it.mkdirs() }
        val container = AppContainerLauncher.container(CONTAINER)
        // what the program may use: the Java runtime and the app's own files (read), and its own folder (write)
        val javaHome = File(System.getProperty("java.home"))
        val classpath = (System.getProperty("java.class.path") ?: "").split(File.pathSeparator).filter { it.isNotBlank() }
        val readable = LinkedHashSet<File>()
        readable += javaHome
        classpath.forEach { readable += File(it) }
        // the program files of Windows already let every app container read; elsewhere the user owns the folder and can grant
        // what the program may use is checked against Windows' own access lists at every start, not remembered: a runtime or app folder that was replaced (an update, a new
        // build) has lost the rights it had, and a program that cannot read its own Java runtime dies at once. A folder with thousands of files is granted once (the right is
        // inherited by what is in it), a file is granted when it has none.
        fun grantOnce(f: File, write: Boolean): Boolean = AppContainerLauncher.hasAccess(container, f, write) || AppContainerLauncher.grant(container, f, write)
        runCatching { File(workerRoot, "granted.txt").delete() } // the list of an earlier version
        // the program files of Windows already let every app container read; elsewhere the user owns the folder and can grant
        for (f in readable) if (!grantOnce(f, false) && !f.invariantSeparatorsPath.startsWith("C:/Program Files", ignoreCase = true)) Log.w(TAG, "no read access could be granted on ${f.path}")
        check(grantOnce(data, true)) { "the sandbox's folder could not be opened for it" }

        // JNA unpacks its native library into a temporary file at first use; the sandbox's own folder is not the place for code that it loads: the library
        // is put there by the app (checked by size), read only for the container, and loaded from it
        val nativeDir = File(workerRoot, "native").also { it.mkdirs() }
        val dispatch = File(nativeDir, "jnidispatch.dll")
        IsolatedHost::class.java.getResourceAsStream("/com/sun/jna/win32-x86-64/jnidispatch.dll")?.use { input ->
            val bytes = input.readBytes()
            if (!dispatch.isFile || dispatch.length() != bytes.size.toLong()) dispatch.writeBytes(bytes)
        } ?: error("no JNA library in the app")
        // the same for the graphics library: an extension's window (its Cloudflare check page, for one) builds views, and a view makes a paint, which loads it
        // (in the packaged app the library is a file next to the jars, named by -Dskiko.library.path; from the build it is inside a jar)
        val skikoDir = System.getProperty("skiko.library.path")?.let { File(it) }
        for (name in listOf("skiko-windows-x64.dll", "icudtl.dat")) {
            val bytes = IsolatedHost::class.java.getResourceAsStream("/" + name)?.use { it.readBytes() }
                ?: skikoDir?.let { File(it, name) }?.takeIf { it.isFile }?.readBytes() ?: continue
            val target = File(nativeDir, name)
            if (!target.isFile || target.length() != bytes.size.toLong()) target.writeBytes(bytes)
        }
        // and for the TLS library (Conscrypt, the first TLS provider as in the app: HTTP/2 and the handshake the sites know): it unpacks its own library into the temp folder
        // when it is not found, and Windows does not load a file that the sandbox itself wrote (see AppContainerLauncher.MITIGATIONS), so the program never had it
        val conscrypt = File(nativeDir, "conscrypt_openjdk_jni-windows-x86_64.dll")
        IsolatedHost::class.java.getResourceAsStream("/META-INF/native/conscrypt_openjdk_jni-windows-x86_64.dll")?.use { input ->
            val bytes = input.readBytes()
            if (!conscrypt.isFile || conscrypt.length() != bytes.size.toLong()) conscrypt.writeBytes(bytes)
        } ?: Log.w(TAG, "no Conscrypt library in the app: the sandbox uses the Java TLS")
        grantOnce(nativeDir, false)

        val argFile = File(workerRoot, "worker.args")
        // the program's options with [d] as its data folder and [t] as its temporary one (the start-up archive is made with the same ones, see WorkerCds)
        fun options(d: File, t: File): List<String> = listOf(
            "-Xms64m", "-Xmx768m", "-XX:MaxMetaspaceSize=384m", "-Xss4m", "-XX:+UseSerialGC",
            "--enable-native-access=ALL-UNNAMED", "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "-Djava.awt.headless=true", "-Dfile.encoding=UTF-8", "-Dcloudstream.worker=true",
            "-XX:ErrorFile=" + File(t, "hs_err_%p.log").absolutePath, "-XX:-CreateCoredumpOnCrash",
            "-Dcloudstream.data=" + d.absolutePath,
            "-Djava.io.tmpdir=" + t.absolutePath, "-Djna.tmpdir=" + t.absolutePath,
            "-Duser.home=" + d.absolutePath,
            "-Djna.boot.library.path=" + nativeDir.absolutePath, "-Djna.nounpack=true",
            "-Dskiko.library.path=" + nativeDir.absolutePath,
            "-Djava.library.path=" + listOf(nativeDir, File(javaHome, "bin")).joinToString(File.pathSeparator) { it.absolutePath },
            *(if (System.getProperty("cloudstream.workerdebug") != null) arrayOf("-Djna.debug_load=true", "-Djna.debug_load.jna=true") else emptyArray()),
            *(if (System.getProperty("cloudstream.workerprobe") != null) arrayOf("-Dcloudstream.workerprobe=1") else emptyArray()),
            "-cp", classpath.joinToString(File.pathSeparator),
            "com.lagradost.desktop.isolate.ExtensionWorker",
        )
        // the start-up archive, when the app has made one for this version: read only for the sandbox (it lies outside the sandbox's own folder)
        val cds = WorkerCds.archive(workerRoot, javaHome, classpath)?.takeIf { grantOnce(it.parentFile, false) }
        val cdsOptions = if (cds != null) listOf("-XX:SharedArchiveFile=" + cds.absolutePath, "-Xshare:auto") else emptyList()
        // an argument file: one quoted option per line, a backslash written twice; the JVM's own messages go to standard error (standard output is the channel to the app)
        fun q(s: String) = "\"" + s.replace("\\", "\\\\") + "\""
        argFile.writeText((cdsOptions + WorkerCds.quietLog + options(data, tmp)).joinToString("\n") { if (it.startsWith("-") && !it.contains(File.separatorChar) && !it.contains(' ')) it else q(it) })
        AppContainerLauncher.grant(container, argFile, write = false)
        // the app's own runtime (jlink) has javaw.exe and no java.exe; javaw has no console window, and the pipes are its standard streams all the same
        val javaExe = listOf("bin/java.exe", "bin/javaw.exe").map { File(javaHome, it) }.firstOrNull { it.isFile } ?: error("no java.exe or javaw.exe in $javaHome")
        val systemRoot = System.getenv("SystemRoot") ?: "C:\\Windows"
        // the program gets a small environment of its own: the app's variables may hold keys and tokens
        val env = linkedMapOf("SystemRoot" to systemRoot, "windir" to systemRoot, "TEMP" to tmp.absolutePath, "TMP" to tmp.absolutePath, "PATH" to "$systemRoot\\system32",
            // Windows does not start an AppContainer program without this variable
            "LOCALAPPDATA" to data.absolutePath)
        if (cds == null) WorkerCds.trainLater(workerRoot, javaHome, javaExe, classpath, env, ::options)
        else Log.i(TAG, "the sandbox starts with its start-up archive (${cds.name})")
        val child = AppContainerLauncher.launch(container, "\"${javaExe.absolutePath}\" @\"${argFile.absolutePath}\"", data, env, MEMORY_LIMIT, policies)
        Thread({ child.stderr.bufferedReader().forEachLine { noteOutput(it); Log.i("worker", it.take(500)) } }, "worker-log").apply { isDaemon = true }.start()

        val rpc = RpcPeer("host", child.stdout, child.stdin) { serve(it) }
        rpc.start()
        return child to rpc
    }

    fun stop() {
        peer?.close("the app stops it")
        process?.destroy()
        peer = null
        process = null
    }

    // ---- the app's settings that extensions read ------------------------------------------------------------------------------------------------

    private val sensitive = Regex("token|secret|password|passwd|credential|cookie|session|pin\\b|auth", RegexOption.IGNORE_CASE)

    /** What of the app's data store (the profiles' library, history and accounts, sign-ins) is not an extension's setting: stays in the app */
    private val appStoreKey = Regex("^(\\d+/|data_store_helper|BACKUP_|download|auth_|subs_|seen_providers|result_|mal_|anilist_|simkl_|kitsu_|oauth)|token|secret|password|passwd|credential|cookie|session|account|\\bpin\\b", RegexOption.IGNORE_CASE)

    private fun typed(values: Map<String, *>, keep: (String) -> Boolean): JsonNode {
        val node = mapper.createObjectNode()
        for ((key, value) in values) {
            if (!keep(key)) continue
            val (type, v) = when (value) {
                is String -> "s" to TextNode(value)
                is Int -> "i" to mapper.valueToTree<JsonNode>(value)
                is Long -> "l" to mapper.valueToTree<JsonNode>(value)
                is Float -> "f" to mapper.valueToTree<JsonNode>(value)
                is Boolean -> "b" to mapper.valueToTree<JsonNode>(value)
                is Set<*> -> "set" to mapper.valueToTree<JsonNode>(value.map { it.toString() })
                else -> continue
            }
            node.set<JsonNode>(key, mapper.createObjectNode().put("t", type).also { it.set<JsonNode>("v", v) })
        }
        return node
    }

    /**
     * What the sandbox gets of the app's data so that the extensions work there as they do in the app: the settings of the app that extensions read (without
     * the app's own look and anything that looks like a credential), the data store keys that are an extension's (its provider switches), the extensions' own
     * preference files (what is changed on an extension's settings page) and the browser's user agent. Sent again when it changed.
     */
    private fun settingsSnapshot(): JsonNode {
        val ctx = AndroidRuntime.context
        val root = mapper.createObjectNode()
        root.set<JsonNode>("default", typed(androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx).all) { !it.startsWith("desktop_") && !sensitive.containsMatchIn(it) })
        root.set<JsonNode>("store", typed(ctx.getSharedPreferences("rebuild_preference", 0).all) { !appStoreKey.containsMatchIn(it) })
        val files = mapper.createObjectNode()
        val appFiles = setOf("com.lagradost.cloudstream3_preferences", "rebuild_preference")
        AndroidRuntime.sharedPrefsDir().listFiles { f -> f.isFile && f.name.endsWith(".xml") }?.forEach { f ->
            val name = f.name.removeSuffix(".xml")
            if (name !in appFiles && Regex("[A-Za-z0-9._-]{1,120}").matches(name)) files.set<JsonNode>(name, typed(ctx.getSharedPreferences(name, 0).all) { true })
        }
        root.set<JsonNode>("files", files)
        root.put("userAgent", runCatching { WebViewResolver.webViewUserAgent ?: com.lagradost.desktop.runtime.web.WebRuntime.defaultUserAgent }.getOrNull())
        return root
    }

    @Volatile private var lastSettingsCheck = 0L

    private suspend fun syncSettings(rpc: RpcPeer) {
        // looked at most every second and a half: a call for each piece of a video would otherwise build the snapshot each time
        val now = System.currentTimeMillis()
        if (settingsHash != 0 && now - lastSettingsCheck < 1_500) return
        lastSettingsCheck = now
        val snapshot = settingsSnapshot()
        val hash = snapshot.toString().hashCode()
        if (hash == settingsHash) return
        rpc.call("settings", snapshot, 30_000)
        settingsHash = hash
    }

    // ---- extensions --------------------------------------------------------------------------------------------------------------------------------

    private fun loadBody(spec: Spec) = mapper.createObjectNode().put("path", spec.path).put("internalName", spec.internalName).put("url", spec.url)

    /** The result of loading an extension into the sandbox */
    sealed class Loaded {
        class Providers(val providers: List<RemoteMainAPI.Meta>, val hasSettings: Boolean, val extractors: List<RemoteExtractorApi.Meta> = emptyList()) : Loaded()
        /** The extension has something that only the app's own window can do (video actions of its own): it runs in the app */
        class NeedsApp(val reasons: List<String>) : Loaded()
        object Unavailable : Loaded()

        /** The sandbox works, the extension did not load in it */
        class Failed(val reason: String) : Loaded()
    }

    suspend fun loadPlugin(spec: Spec): Loaded {
        synchronized(loading) { loading += spec.path }
        try {
            return loadPluginNow(spec)
        } finally {
            synchronized(loading) { loading -= spec.path }
        }
    }

    private suspend fun loadPluginNow(spec: Spec): Loaded {
        var attempt = 0
        var reply: JsonNode
        while (true) {
            val rpc = ensureRunning() ?: return Loaded.Unavailable
            // the container may read this file and nothing else of the extension folders
            val container = AppContainerLauncher.container(CONTAINER)
            if (!AppContainerLauncher.hasAccess(container, File(spec.path), false)) AppContainerLauncher.grant(container, File(spec.path), write = false)
            try {
                reply = rpc.call("loadPlugin", loadBody(spec), 180_000)
                break
            } catch (t: Throwable) {
                Log.w(TAG, "${spec.internalName} did not load in the sandbox: $t")
                // the program ended while it was loading this (a crash of the Java runtime in it): it is started again, once, and this extension is asked for again
                if (attempt++ < 1 && rpc.isClosed) continue
                return if (rpc.isClosed) Loaded.Unavailable else Loaded.Failed(t.message ?: t.javaClass.simpleName)
            }
        }
        reply.get("needsApp")?.takeIf { it.isArray }?.let { return Loaded.NeedsApp(it.map { r -> r.asText() }) }
        // the extension's own code failed to load in the sandbox (the same would happen in the app): the sandbox is working
        if (reply.get("loaded")?.asBoolean() != true) return Loaded.Failed("the extension did not load")
        synchronized(loaded) { loaded[spec.path] = spec }
        val hasSettings = reply.get("hasSettings")?.asBoolean() == true
        synchronized(settingsPaths) { if (hasSettings) settingsPaths += spec.path else settingsPaths -= spec.path }
        return Loaded.Providers(reply.get("providers").map { RemoteMainAPI.Meta.from(spec.path, it) }, hasSettings, reply.get("extractors")?.map { RemoteExtractorApi.Meta.from(it) }.orEmpty())
    }

    suspend fun unloadPlugin(path: String) {
        synchronized(loaded) { loaded.remove(path) }
        synchronized(settingsPaths) { settingsPaths -= path }
        RemoteBrowserHost.closeFor(path)
        val rpc = peer?.takeIf { !it.isClosed } ?: return
        runCatching { rpc.call("unloadPlugin", mapper.createObjectNode().put("path", path), 30_000) }
    }

    /** A call for an extension in the sandbox; [onEvent] gets what it sends while the call is open (a link, a subtitle) */
    suspend fun call(op: String, body: Any, timeoutMs: Long, onEvent: ((String, JsonNode) -> Unit)? = null): JsonNode {
        var attempt = 0
        while (true) {
            val rpc = ensureRunning() ?: throw IllegalStateException("the extension sandbox is not available")
            try {
                syncSettings(rpc)
                return rpc.call(op, body, timeoutMs, onEvent)
            } catch (t: IllegalStateException) {
                // the process ended while it was working (a crash of the Java runtime in it): it is started again, with its extensions, and the request is made once more
                if (attempt++ >= 1 || !rpc.isClosed) throw t
                Log.w(TAG, "the sandbox ended during $op: starting it again")
            }
        }
    }

    // ---- what the sandbox may ask of the app ------------------------------------------------------------------------------------------------

    private suspend fun serve(req: RpcPeer.Incoming): JsonNode? {
        val b = req.body
        return when (req.op) {
            "toast" -> {
                com.lagradost.desktop.ui.Toasts.show(b.get("text")?.asText().orEmpty().take(300), b.get("long")?.asBoolean() == true)
                null
            }
            // an extension has no business opening the user's browser or programs on its own: refused (the sandbox holds popups the same way)
            "openUrl" -> {
                Log.w(TAG, "an extension asked to open ${b.get("url")?.asText()?.take(200)}: not allowed")
                mapper.valueToTree<JsonNode>(false)
            }
            "webview" -> webview(req)
            // a page of a site whose Cloudflare check was passed, fetched by the app's browser (see BrowserFetch)
            "fetch" -> BrowserFetch.serve(b)
            // the pages of the browsers that extensions in the sandbox build (see RemoteBrowserHost) and the window for one of them
            "wv" -> RemoteBrowserHost.serve(b)
            "dialogShow" -> mapper.valueToTree<JsonNode>(RemoteBrowserHost.dialogShow(b))
            "dialogHide" -> { RemoteBrowserHost.dialogHide(b); null }
            "needsApp" -> {
                // only an extension that is in the sandbox can be moved out of it
                val path = b.get("path")?.asText().orEmpty()
                if (owns(path)) Isolation.moveToApp(path, b.get("reason")?.asText().orEmpty().take(80))
                null
            }
            else -> error("unknown request ${req.op}")
        }
    }

    private fun requestNode(r: Request): JsonNode =
        mapper.createObjectNode().put("url", r.url.toString()).put("method", r.method).also { n ->
            n.set<JsonNode>("headers", mapper.valueToTree(r.headers.toMultimap().mapValues { it.value.firstOrNull().orEmpty() }))
        }

    /** A hidden browser for the sandbox: opened here, with the app's cookies, and only for http(s) pages; what it sees goes back as events */
    private suspend fun webview(req: RpcPeer.Incoming): JsonNode {
        val b = req.body
        val url = b.get("url")?.asText().orEmpty()
        require(url.startsWith("https://") || url.startsWith("http://")) { "not a web address" }
        // the hidden browser may not be used to reach this PC or its network
        require(com.lagradost.desktop.runtime.web.PrivateNetworkGuard.isAllowed(url)) { "that address is not allowed" }
        val intercept = b.get("intercept")?.asText().orEmpty()
        val additional = b.get("additional")?.map { it.asText() }.orEmpty()
        require(intercept.length <= 600 && additional.size <= 12 && additional.all { it.length <= 600 }) { "patterns too long" }
        // the page of a Cloudflare check (the pattern the extension's CloudflareKiller uses) is not waited for as long as another page: see CloudflareGate.UNATTENDED_MS
        val cloudflareSolve = intercept == ".^"
        val timeout = (b.get("timeout")?.asLong() ?: WebViewResolver.DEFAULT_TIMEOUT).coerceIn(1_000L, 90_000L).let { if (cloudflareSolve) minOf(it, com.lagradost.desktop.runtime.web.CloudflareGate.UNATTENDED_MS) else it }
        val headers = b.get("headers")?.fields()?.asSequence()?.associate { it.key to it.value.asText() }.orEmpty()
        val resolver = WebViewResolver(
            Regex(intercept), additional.map { Regex(it) }, b.get("userAgent")?.asText()?.takeIf { it.isNotBlank() }, true,
            b.get("script")?.asText()?.takeIf { it.isNotBlank() }, { text -> req.emit("script", TextNode(text)) }, timeout,
        )
        resolver.urlGuard = com.lagradost.desktop.runtime.web.PrivateNetworkGuard::isAllowed
        val request = requestCreator(b.get("method")?.asText() ?: "GET", url, headers = headers)
        val host = request.url.host
        val userAgent = runCatching { WebViewResolver.webViewUserAgent ?: com.lagradost.desktop.runtime.web.WebRuntime.defaultUserAgent }.getOrNull()
        // the page of a Cloudflare check (the pattern the extension's CloudflareKiller uses) is opened only when the solvers are on, the user did not just cancel that site, and the
        // extension that owns the site is the one in use: an extension that checks its site while it loads (AnimePahe) must not put a window up for someone on another one.
        // Until then the answer is held (the extension waits, as in the app).
        var solve: com.lagradost.desktop.runtime.web.CloudflareGate.Solve? = null
        if (intercept == ".^") {
            if (!com.lagradost.desktop.runtime.web.CloudflareGate.allowsSolving(host)) return mapper.createObjectNode().also { it.putNull("final"); it.set<JsonNode>("cookies", mapper.createArrayNode()); it.put("userAgent", userAgent) }
            val until = System.currentTimeMillis() + 180_000
            while (!com.lagradost.desktop.runtime.web.CloudflareGate.inUseHost(host) && System.currentTimeMillis() < until) kotlinx.coroutines.delay(1_000)
            if (!com.lagradost.desktop.runtime.web.CloudflareGate.inUseHost(host) || !com.lagradost.desktop.runtime.web.CloudflareGate.allowsSolving(host)) {
                return mapper.createObjectNode().also { it.putNull("final"); it.set<JsonNode>("cookies", mapper.createArrayNode()); it.put("userAgent", userAgent) }
            }
            // the bar with a Cancel button shows while the page works
            solve = com.lagradost.desktop.runtime.web.CloudflareGate.beginSolve(host) { resolver.cancel() }
        }
        // the clearance the browser held before the page was opened: only a new one ends the check (the sandbox's copy of the jar has nothing of it until this call returns)
        fun clearance(): String? = WebCookies.all().firstOrNull { c -> c.name == "cf_clearance" && host.let { h -> val d = c.domain.trimStart('.').lowercase(); h == d || h.endsWith(".$d") } }?.value
        val clearanceBefore = if (cloudflareSolve) clearance() else null
        try {
            return browsers.withPermit {
                val watcher = if (!cloudflareSolve) null else kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    while (true) {
                        kotlinx.coroutines.delay(600)
                        val now = runCatching { WebCookies.freshHeader("https://$host/"); clearance() }.getOrNull()
                        if (now != null && now != clearanceBefore) { Log.i(TAG, "the clearance of $host came: the check is over"); resolver.cancel(); break }
                    }
                }
                val (final, _) = try { resolver.resolveUsingWebView(request) { r -> req.emit("request", requestNode(r)); false } } finally { watcher?.cancel() }
                val cookies = WebCookies.all().filter { c -> val d = c.domain.trimStart('.').lowercase(); host == d || host.endsWith(".$d") }
                if (cloudflareSolve) {
                    // no clearance at all (a clearance that was already there and is still good gives none that is new, and is no failure)
                    if (cookies.none { it.name == "cf_clearance" }) com.lagradost.desktop.runtime.web.CloudflareGate.markFailed(host)
                }
                mapper.createObjectNode().also { n ->
                    n.set<JsonNode>("final", final?.let { requestNode(it) })
                    n.set<JsonNode>("cookies", mapper.valueToTree(cookies))
                    n.put("userAgent", userAgent)
                }
            }
        } finally {
            solve?.let { com.lagradost.desktop.runtime.web.CloudflareGate.endSolve(it) }
            // the sandbox gave up on this (a request it wanted came, or its caller was cancelled): the page is closed
            resolver.cancel()
        }
    }
}
