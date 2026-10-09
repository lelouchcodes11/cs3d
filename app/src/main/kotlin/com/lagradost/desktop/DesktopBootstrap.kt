package com.lagradost.desktop

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import com.lagradost.cloudstream3.APIHolder.allProviders
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.APIHolder.initAll
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey as appGetKey
import com.lagradost.cloudstream3.APIHolder
import kotlin.reflect.full.createInstance
import kotlinx.coroutines.sync.withLock
import com.lagradost.cloudstream3.actions.temp.fcast.FcastManager
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.utils.AppContextUtils.getApiDubstatusSettings
import com.lagradost.cloudstream3.utils.AppContextUtils.isNetworkAvailable
import com.lagradost.cloudstream3.utils.AppContextUtils.loadCache
import com.lagradost.cloudstream3.utils.AppContextUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.AppContextUtils.updateHasTrailers
import com.lagradost.cloudstream3.utils.BackupUtils.setUpBackup
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.DataStoreHelper.migrateResumeWatching
import com.lagradost.cloudstream3.utils.SnackbarHelper.showSnackbar
import com.lagradost.cloudstream3.utils.USER_SELECTED_HOMEPAGE_API
import com.lagradost.cloudstream3.utils.downloader.DownloadQueueManager
import com.lagradost.safefile.SafeFile
import com.lagradost.cloudstream3.AutoDownloadMode
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.CommonActivity.updateLocale
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.MainActivity.Companion.lastError
import com.lagradost.cloudstream3.MainActivity.Companion.mainPluginsLoadedEvent
import com.lagradost.cloudstream3.MainActivity.Companion.setLastError
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SettingsJson
import com.lagradost.cloudstream3.UnsafeSSL
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.insecureApp
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.network.initClient
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_loadAllOnlinePlugins
import com.lagradost.cloudstream3.plugins.PluginManager.loadSinglePlugin
import com.lagradost.cloudstream3.ui.settings.Globals.updateTv
import com.lagradost.cloudstream3.utils.BackupUtils.backup
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.desktop.runtime.AndroidRuntime
import com.lagradost.desktop.runtime.ContextImpl
import com.lagradost.desktop.runtime.res.IndexedResourceTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.security.Security

/**
 * Process start of the desktop app: the Android runtime, the application object and the non UI part
 * of the upstream MainActivity.onCreate (network clients, provider settings, plugin loading).
 */
object DesktopBootstrap {
    private const val TAG = "DesktopBootstrap"

    @Volatile
    lateinit var activity: MainActivity
        private set

    fun activityOrNull(): MainActivity? = if (::activity.isInitialized) activity else null

    internal fun setMainActivity(act: MainActivity) {
        activity = act
    }

    /** The launcher intent, like Android starting the app: the manifest's MAIN activity is AccountSelectActivity */
    fun launcherIntent(link: String? = null): android.content.Intent =
        android.content.Intent(if (link != null) android.content.Intent.ACTION_VIEW else android.content.Intent.ACTION_MAIN)
            .setClassName(AndroidRuntime.PACKAGE_NAME, com.lagradost.cloudstream3.ui.account.AccountSelectActivity::class.java.name)
            .also { if (link != null) it.setData(android.net.Uri.parse(link)) }

    private val resourceIndexLock = Any()
    private var resourceIndexValue: IndexedResourceTable? = null

    /** The index of the packaged Android resources takes about 0.6 s to read: main() asks for it on a worker, the start-up on the UI thread then finds it ready */
    fun resourceIndex(): IndexedResourceTable = synchronized(resourceIndexLock) {
        resourceIndexValue ?: IndexedResourceTable(DesktopBootstrap::class.java.classLoader).also { resourceIndexValue = it }
    }

    /** Starts the app's task. Must run on the main thread after [initApplication]. */
    fun launch(link: String? = null) = ActivityStack.start(launcherIntent(link))

    /** Initialise the runtime and the application object (no activity yet) */
    fun initApplication(dataDir: File? = null) {
        runCatching { Security.insertProviderAt(org.conscrypt.Conscrypt.newProvider(), 1) } // before OkHttp is first used, see main()
        if (dataDir != null) AndroidRuntime.init(dataDir) else AndroidRuntime.init()
        TrustStore.install(AndroidRuntime.dataDir)
        // the trust store (a few hundred certificates) takes over a second to parse, and the first HTTPS client of the engine, created on the UI thread,
        // would wait for that: it is read here, in parallel with the rest of the start-up, and the JDK keeps the result
        Thread({
            runCatching { javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm()).init(null as java.security.KeyStore?) }
        }, "trust-store-warm-up").apply { isDaemon = true }.start()
        // Chromium (WebView) is needed by few extensions and its start takes the UI thread for about two seconds (it pumps its message loop there),
        // so it starts when an extension first asks for it (JcefRuntime.await), not in the background of every session.
        // libmpv is loaded early (the first Play must not wait for it).
        Thread({
            Thread.sleep(5_000)
            // libmpv (115 MB of code, then its own memory) is loaded ahead of the first Play, unless the PC is short of memory: with little free
            // memory Windows compresses and pages, which is what freezes every window; then it loads when a video is first opened
            val freeMb = runCatching { (java.lang.management.ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean).freeMemorySize / 1_048_576 }.getOrDefault(Long.MAX_VALUE)
            if (freeMb >= 1500) runCatching { com.lagradost.desktop.player.Mpv.INSTANCE } else android.util.Log.i("Startup", "libmpv is not preloaded: only $freeMb MB of memory free")
            // the answer needs a PowerShell run (0.5 to 3 s): asked first by the Accounts settings page on the UI thread, it froze the window
            runCatching { com.lagradost.desktop.WindowsHello.isAvailable() }
        }, "warm-up-delayed").apply { isDaemon = true }.start()
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
        ContextImpl.appResourceTable = resourceIndex()
        AndroidRuntime.startApplication(CloudStreamApp())
        // work the window's thread would otherwise do the first time a page needs it, and Chromium when the last run needed it
        Warmups.start()
    }

    /** Headless tools (ExtensionHarness): the runtime, the application and a MainActivity instance */
    fun init(dataDir: File? = null): MainActivity {
        runCatching { Security.insertProviderAt(org.conscrypt.Conscrypt.newProvider(), 1) } // before OkHttp is first used, see main()
        if (dataDir != null) AndroidRuntime.init(dataDir) else AndroidRuntime.init()
        TrustStore.install(AndroidRuntime.dataDir)
        // bundled Chromium (only where WebView2 is missing) takes a few seconds to start, do it in the background right away
        if (!com.lagradost.desktop.runtime.web.WebRuntime.usesWebView2) com.lagradost.desktop.runtime.web.JcefRuntime.startAsync()

        // Android ships a Bouncy Castle based "BC" provider (e.g. AES/CBC/PKCS7Padding)
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }

        ContextImpl.appResourceTable = resourceIndex()
        val application = CloudStreamApp()
        AndroidRuntime.startApplication(application)

        val act = MainActivity()
        act.attach(AndroidRuntime.context, application)
        activity = act
        CommonActivity.setActivityInstance(act)
        return act
    }

    /**
     * Native UI: the runtime, the application, and a MainActivity instance that is only the engine's
     * Activity/Context (it is never created, so no Android view is inflated), then the engine start.
     */
    fun startEngine(): MainActivity {
        initApplication()
        com.lagradost.desktop.platform.StartupProfile.mark("startEngine: application ready")
        val act = MainActivity()
        act.attach(AndroidRuntime.context, AndroidRuntime.applicationContext as? android.app.Application)
        activity = act
        CommonActivity.setActivityInstance(act)
        onCreate(act, loadPlugins = true, activityLifecycle = false)
        // fragments of the engine activity (extension settings dialogs) run although the activity never started
        act.startFragmentHost()
        DesktopLifecycle.install(act)
        com.lagradost.desktop.platform.PrefsBackup.maybeBackup("start")
        return act
    }

    private val cloneLock = kotlinx.coroutines.sync.Mutex()

    /**
     * Settings > General > Clone site: a copy of an extension's site under another address (a site that moved, an ISP block). Upstream
     * makes them after each load of the extensions in MainActivity.onCreate, which the native UI never runs: the clones were saved but
     * never appeared. They are made again after every load, and when one is added.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun loadClonedSites(success: Boolean) {
        ioSafe {
            cloneLock.withLock {
                allProviders.withLock {
                    try {
                        appGetKey<Array<com.lagradost.cloudstream3.ui.settings.SettingsGeneral.CustomSite>>(com.lagradost.cloudstream3.utils.USER_PROVIDER_API)?.let { list ->
                            list.forEach { custom ->
                                if (allProviders.any { it.name == custom.name && it.mainUrl == custom.url.trimEnd('/') }) return@forEach
                                allProviders.firstOrNull { it::class.simpleName == custom.parentClassName }?.let {
                                    allProviders.add(
                                        it::class.createInstance().apply {
                                            name = custom.name
                                            lang = custom.lang
                                            mainUrl = custom.url.trimEnd('/')
                                            canBeOverridden = false
                                        },
                                    )
                                    Log.i(TAG, "cloned site ${custom.name} (${custom.url}) of ${custom.parentClassName}")
                                }
                            }
                        }
                        apis = allProviders.distinctBy { it.lang + it.name + it.mainUrl + it::class.qualifiedName }
                        APIHolder.apiMap = null
                    } catch (e: Exception) {
                        logError(e)
                    }
                }
            }
        }
    }

    /**
     * The non-UI part of upstream MainActivity.onCreate. Headless tools and the native UI call it;
     * the legacy Android UI runs the real activity lifecycle through [launch]. [loadPlugins] false is
     * for tools that load plugins themselves. [activityLifecycle] false: no Activity.onCreate/onStart/onResume.
     */
    @OptIn(UnsafeSSL::class)
    fun onCreate(act: MainActivity, loadPlugins: Boolean = true, activityLifecycle: Boolean = true) {
        app.initClient(act, ignoreSSL = false)
        insecureApp.initClient(act, ignoreSSL = true)
        com.lagradost.desktop.platform.StartupProfile.mark("onCreate: clients ready")

        val settingsManager = PreferenceManager.getDefaultSharedPreferences(act)
        setLastError(act)

        val settingsForProvider = SettingsJson()
        settingsForProvider.enableAdult =
            settingsManager.getBoolean(act.getString(R.string.enable_nsfw_on_providers_key), false)
        MainAPI.settingsForProvider = settingsForProvider

        CommonActivity.loadThemes(act)
        act.updateLocale()
        com.lagradost.desktop.platform.StartupProfile.mark("onCreate: theme and locale")
        if (!activityLifecycle) MainActivity.afterPluginsLoadedEvent += ::loadClonedSites
        // Stremio add-ons with catalogs are providers: made after every load of the extensions (which may rebuild the list) and at the start
        MainActivity.afterPluginsLoadedEvent += { ioSafe { com.lagradost.desktop.stremio.StremioAddons.syncProviders() } }
        com.lagradost.desktop.stremio.StremioAddons.start()
        if (activityLifecycle) act.performCreate(null as Bundle?)
        act.updateTv()

        // backup when we update the app, I don't trust myself to not boot lock users, might want to make this a setting?
        safe {
            val appVer = BuildConfig.VERSION_NAME
            val lastAppAutoBackup: String = act.getKey<String>("VERSION_NAME") ?: ""
            if (appVer != lastAppAutoBackup) {
                act.setKey("VERSION_NAME", BuildConfig.VERSION_NAME)
                if (lastAppAutoBackup.isEmpty()) return@safe

                safe {
                    backup(act)
                }
                safe {
                    // Recompile oat on new version
                    PluginManager.deleteAllOatFiles(act)
                }
            }
        }

        // Automatically enable jsdelivr if cant connect to raw.githubusercontent.com
        if (act.getKey<Boolean>(act.getString(R.string.jsdelivr_proxy_key)) == null && act.isNetworkAvailable()) {
            main {
                // Desktop: one retry, the first launch converts every extension and can starve the 5s check
                if (act.checkGithubConnectivity() || kotlinx.coroutines.delay(3000).let { act.checkGithubConnectivity() }) {
                    act.setKey(act.getString(R.string.jsdelivr_proxy_key), false)
                } else {
                    act.setKey(act.getString(R.string.jsdelivr_proxy_key), true)
                    showSnackbar(
                        act,
                        R.string.jsdelivr_enabled,
                        Snackbar.LENGTH_LONG,
                        R.string.revert
                    ) { act.setKey(act.getString(R.string.jsdelivr_proxy_key), false) }
                }
            }
        }

        ioSafe { SafeFile.check(act) }
        com.lagradost.desktop.platform.StartupProfile.mark("onCreate: before plugins")

        if (loadPlugins) loadPlugins(act, settingsManager)

        ioSafe {
            initAll()
            // No duplicates (which can happen by registerMainAPI)
            apis = allProviders.distinctBy { it }
        }

        com.lagradost.desktop.platform.StartupProfile.mark("onCreate: plugins launched")
        act.setUpBackup()

        CommonActivity.init(act)

        act.loadCache()
        act.updateHasTrailers()

        // Desktop: deep links from the command line are passed to handleAppIntentUrl by main()
        // Start OAuth loopback callback listener for AniList / MyAnimeList / Simkl browser logins
        ioSafe { com.lagradost.desktop.net.OAuthCallback.start() }

        com.lagradost.desktop.platform.StartupProfile.mark("onCreate: before fcast")
        FcastManager().init(act, false)
        com.lagradost.desktop.platform.StartupProfile.mark("onCreate: after fcast")

        APIRepository.dubStatusActive = act.getApiDubstatusSettings()

        try {
            // this ensures that no unnecessary space is taken
            act.loadCache()
            File(act.filesDir, "exoplayer").deleteRecursively() // old cache
            MainActivity.deleteFileOnExit(File(act.cacheDir, "exoplayer"))   // current cache
        } catch (e: Exception) {
            logError(e)
        }
        println("Loaded everything")
        com.lagradost.desktop.platform.StartupProfile.mark("onCreate: loaded everything")

        ioSafe {
            migrateResumeWatching()
        }

        act.getKey<String>(USER_SELECTED_HOMEPAGE_API)?.let { homepage ->
            DataStoreHelper.currentHomePage = homepage
            act.removeKey(USER_SELECTED_HOMEPAGE_API)
        }

        // Start the download queue
        DownloadQueueManager.init(act)

        // onCreate is done, the activity becomes visible
        if (activityLifecycle) {
            act.performStart()
            act.performResume()
        }
    }

    private fun loadPlugins(act: MainActivity, settingsManager: android.content.SharedPreferences) {
        if (PluginManager.checkSafeModeFile()) {
            safe {
                showToast(R.string.safe_mode_file, Toast.LENGTH_LONG)
            }
        } else if (lastError == null) {
            ioSafe {
                DataStoreHelper.currentHomePage?.let { homeApi ->
                    mainPluginsLoadedEvent.invoke(loadSinglePlugin(act, homeApi))
                } ?: run {
                    mainPluginsLoadedEvent.invoke(false)
                }

                ioSafe {
                    // the first screen is built first: loading every extension at the same time took the CPU from it
                    com.lagradost.cloudstream3.plugins.PluginLoadGate.awaitFirstFrame()
                    if (settingsManager.getBoolean(act.getString(R.string.auto_update_plugins_key), true)) {
                        PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_updateAllOnlinePluginsAndLoadThem(act)
                    } else {
                        ___DO_NOT_CALL_FROM_A_PLUGIN_loadAllOnlinePlugins(act)
                    }

                    val autoDownloadPlugin = AutoDownloadMode.getEnum(
                        settingsManager.getInt(act.getString(R.string.auto_download_plugins_key), 0)
                    ) ?: AutoDownloadMode.Disable
                    if (autoDownloadPlugin != AutoDownloadMode.Disable) {
                        PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_downloadNotExistingPluginsAndLoad(
                            act,
                            autoDownloadPlugin
                        )
                    }
                }

                ioSafe {
                    PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_loadAllLocalPlugins(act, false)
                }
            }
        } else {
            Log.e(TAG, "Previous session crashed, plugins are not loaded (safe mode):\n$lastError")
            val builder: AlertDialog.Builder = AlertDialog.Builder(act)
            builder.setTitle(R.string.safe_mode_title)
            builder.setMessage(R.string.safe_mode_description)
            builder.apply {
                setPositiveButton(R.string.safe_mode_crash_info) { _, _ ->
                    val tbBuilder: AlertDialog.Builder = AlertDialog.Builder(getContext())
                    tbBuilder.setTitle(R.string.safe_mode_title)
                    tbBuilder.setMessage(lastError)
                    tbBuilder.show()
                }

                setNegativeButton("Ok") { _, _ -> }
            }
            builder.show().setDefaultFocus()
        }
    }
}
