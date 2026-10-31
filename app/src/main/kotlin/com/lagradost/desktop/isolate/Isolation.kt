package com.lagradost.desktop.isolate

import android.util.Log
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.plugins.PluginData
import com.lagradost.desktop.runtime.AndroidRuntime
import com.lagradost.desktop.sandbox.PluginSandbox
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Which extensions run in the sandbox process (a Windows AppContainer, see [IsolatedHost]) and which run in the app.
 *
 * Automatic (the default): every extension is loaded into the sandbox, whatever it does: a settings page is opened by the app on a click, a video interceptor and
 * the video actions it registers run or stay in the sandbox, a browser view of its own gets a page of the app's browser ([RemoteBrowserHost]) and, when it shows that
 * page in a dialog, a window of the app. What runs in the app, under the Java sandbox of [PluginSandbox] (and the Extensions page says so): an extension the user has
 * marked *Trust* (Settings > General > Security), everything when the sandbox cannot start on this PC, and an extension whose browser the app could not make a page for.
 */
object Isolation {
    private const val TAG = "Isolation"
    private const val PREF = "desktop_extension_isolation"

    enum class Where { Sandbox, App }

    /** An extension that is not loaded because the sandbox cannot start: it would otherwise run in the app, with the rights of the user */
    class Blocked(message: String) : RuntimeException(message)

    private const val FALLBACK = "desktop_sandbox_fallback_in_app"

    /**
     * What happens when the sandbox cannot start (Windows refuses it, its files cannot be read): by default **nothing is run**, because an extension in the app can do to this PC
     * what the user can. The user may allow the app to run them anyway (a weaker Java guard then watches them).
     */
    fun fallbackAllowed(): Boolean = runCatching { PreferenceManager.getDefaultSharedPreferences(AndroidRuntime.context).getBoolean(FALLBACK, false) }.getOrDefault(false)

    fun setFallbackAllowed(on: Boolean) {
        runCatching { PreferenceManager.getDefaultSharedPreferences(AndroidRuntime.context).edit().putBoolean(FALLBACK, on).apply() }
    }

    /** The extensions that were not loaded because the sandbox could not start (file -> name) */
    private val blocked = ConcurrentHashMap<String, String>()

    fun blockedNames(): List<String> = blocked.values.distinct().sorted()

    private var lastAnnounced = 0L

    /** The sandbox cannot run [name]: it runs in the app when the user allowed that, else it is not loaded (and the user is told, once in a while) */
    private fun cannotSandbox(path: String, name: String, why: String): Boolean {
        if (fallbackAllowed()) { inApp[path] = why; return false }
        blocked[path] = name
        inApp[path] = "blocked: $why"
        val now = System.currentTimeMillis()
        if (now - lastAnnounced > 120_000) {
            lastAnnounced = now
            runCatching { com.lagradost.desktop.ui.Toasts.show(com.lagradost.desktop.i18n.Tr.f("Extensions are not loaded: the sandbox could not start ({0}). Settings > Network and security > Sandbox for extensions.", why.take(140)), true) }
        }
        throw Blocked("$name is not loaded: the sandbox cannot start ($why)")
    }

    /**
     * Extensions whose whole job is the app's own data: Ultima's sync keeps the library, history, resume points and extension list of every device the
     * same. In the sandbox it saw only the copy of the settings the sandbox gets (the library, history and accounts are kept out of it on purpose), so it
     * synced that copy and what it pulled from the cloud never reached the app. They run in the app, watched by the Java guard of [PluginSandbox].
     */
    private val WORKS_ON_APP_DATA = setOf("ultima")

    /** Why an extension that is not in the sandbox is in the app; the Extensions page shows it */
    private val inApp = ConcurrentHashMap<String, String>()

    fun enabledSetting(): Boolean = System.getProperty("cloudstream.isolation") != "off" && runCatching { PreferenceManager.getDefaultSharedPreferences(AndroidRuntime.context).getBoolean(PREF, true) }.getOrDefault(true)

    fun setEnabled(on: Boolean) {
        runCatching { PreferenceManager.getDefaultSharedPreferences(AndroidRuntime.context).edit().putBoolean(PREF, on).apply() }
    }

    // (the list of an earlier version, "desktop_isolation_in_app", held every extension that built a browser view: those run in the sandbox now, so it is not read any more)
    private const val MOVED = "desktop_isolation_in_app_v2"

    /** Extensions found to need the app (by name): the next start loads them into the app at once */
    private fun moved(): Set<String> = runCatching { PreferenceManager.getDefaultSharedPreferences(AndroidRuntime.context).getStringSet(MOVED, emptySet()) }.getOrNull().orEmpty()

    private fun remember(name: String) {
        runCatching { PreferenceManager.getDefaultSharedPreferences(AndroidRuntime.context).edit().putStringSet(MOVED, moved() + name.lowercase()).apply() }
    }

    /**
     * An extension in the sandbox turned out to need the app (it builds a browser view of its own, which exists only in the app): it is taken out of the
     * sandbox and loaded into the app, its providers there take over the ones that were in use, and the choice is remembered.
     */
    suspend fun moveToApp(path: String, reason: String) {
        val spec = IsolatedHost.specOf(path) ?: return
        remember(spec.internalName)
        if (!fallbackAllowed()) {
            // the sandbox could not give it the browser page it asked for: it is not run in the app instead
            Log.w(TAG, "${spec.internalName}: unloaded ($reason); running it in the app is not allowed")
            unload(path)
            blocked[path] = spec.internalName
            inApp[path] = "blocked: $reason"
            return
        }
        Log.i(TAG, "${spec.internalName}: moves to the app ($reason)")
        val remote = APIHolder.allProviders.toList().filter { it.sourcePlugin == path }.filterIsInstance<RemoteMainAPI>()
        unload(path)
        inApp[path] = reason
        val loaded = com.lagradost.cloudstream3.plugins.PluginManager.loadPluginFile(com.lagradost.desktop.DesktopBootstrap.activityOrNull() ?: AndroidRuntime.context, File(path), PluginData(spec.internalName, spec.url, true, path, -1))
        if (loaded) {
            val now = APIHolder.allProviders.toList().filter { it.sourcePlugin == path }
            for (r in remote) r.moved = now.firstOrNull { it.name == r.name }
        }
    }

    /** Where the extension with this file runs (null: it is not loaded) */
    fun whereIs(path: String): Where? = when {
        IsolatedHost.owns(path) -> Where.Sandbox
        else -> null
    }

    /** Whether the sandboxed extension of this file has a settings page of its own */
    fun hasSettings(path: String): Boolean = IsolatedHost.hasSettings(path)

    /** The file of the sandboxed extension installed from this address or file (null: it is not in the sandbox) */
    fun sandboxedPathFor(urlOrPath: String): String? = IsolatedHost.pathOfUrl(urlOrPath)

    /**
     * The settings page of an extension in the sandbox. The page is code of the extension that needs the app's window, so it is run in the app: on this click and not
     * before, from a copy of the extension that registers nothing (its search and links stay in the sandbox). What the page saves reaches the sandbox with its next call.
     */
    suspend fun openSettings(context: android.content.Context, path: String): Boolean {
        val plugin = com.lagradost.cloudstream3.plugins.PluginManager.settingsOnlyInstance(context, File(path)) ?: return false
        val open = plugin.openSettings ?: return false
        java.awt.EventQueue.invokeLater { runCatching { open(context) }.onFailure { Log.w(TAG, "settings page: $it") } }
        return true
    }

    /** The reason an extension of this file runs in the app, or null */
    fun reasonInApp(path: String): String? = inApp[path]

    /**
     * Tries to load [file] into the sandbox. True: it is loaded there and its providers are registered (as [RemoteMainAPI]); nothing of it runs in the
     * app. False: the caller loads it into the app.
     */
    suspend fun tryLoad(file: File, data: PluginData): Boolean {
        val path = file.absolutePath
        inApp.remove(path)
        if (System.getProperty("cloudstream.worker") != null) return false
        blocked.remove(path)
        // the user switched the sandbox off, or trusts this extension: it runs in the app, as they decided
        if (!enabledSetting()) { inApp[path] = "sandbox off"; return false }
        if (PluginSandbox.isTrusted(data.internalName)) { inApp[path] = "trusted"; return false }
        if (data.internalName.lowercase() in WORKS_ON_APP_DATA) { inApp[path] = "syncs the app's library"; return false }
        if (!AppContainerLauncher.available) return cannotSandbox(path, data.internalName, "Windows cannot start it on this PC")
        if (data.internalName.lowercase() in moved()) return cannotSandbox(path, data.internalName, "its browser page could not be made")
        return when (val result = IsolatedHost.loadPlugin(IsolatedHost.Spec(path, data.internalName, data.url))) {
            is IsolatedHost.Loaded.Providers -> {
                val apis = result.providers.map { RemoteMainAPI(it) }
                for (api in apis) {
                    APIHolder.allProviders.add(api)
                    APIHolder.addPluginMapping(api)
                }
                // the video hosts the extension reads: the app's own lookups find them too (a trailer), the work is done in the sandbox
                for (meta in result.extractors) com.lagradost.cloudstream3.utils.extractorApis.add(RemoteExtractorApi(meta, path))
                Log.i(TAG, "${data.internalName}: in the sandbox, ${apis.size} providers, ${result.extractors.size} extractors")
                true
            }
            is IsolatedHost.Loaded.NeedsApp -> {
                val why = result.reasons.joinToString(", ")
                Log.i(TAG, "${data.internalName}: cannot run in the sandbox ($why)")
                cannotSandbox(path, data.internalName, why)
            }
            is IsolatedHost.Loaded.Failed -> throw IllegalStateException("${data.internalName} did not load in the sandbox: ${result.reason}")
            IsolatedHost.Loaded.Unavailable -> cannotSandbox(path, data.internalName, IsolatedHost.startFailure() ?: "sandbox unavailable")
        }
    }

    /** An extension of the sandbox is unloaded: its providers leave the app's lists */
    suspend fun unload(path: String) {
        IsolatedHost.unloadPlugin(path)
        com.lagradost.cloudstream3.plugins.PluginManager.dropSettingsOnly(path)
        APIHolder.apis.filter { it.sourcePlugin == path }.forEach { APIHolder.removePluginMapping(it) }
        APIHolder.allProviders.withLock { APIHolder.allProviders.removeAll { it.sourcePlugin == path } }
        com.lagradost.cloudstream3.utils.extractorApis.withLock { com.lagradost.cloudstream3.utils.extractorApis.removeAll { it.sourcePlugin == path && it is RemoteExtractorApi } }
        inApp.remove(path)
        blocked.remove(path)
    }
}
