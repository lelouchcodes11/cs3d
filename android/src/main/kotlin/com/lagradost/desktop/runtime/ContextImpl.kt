package com.lagradost.desktop.runtime

import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Display
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import com.lagradost.desktop.runtime.res.FrameworkResources
import com.lagradost.desktop.runtime.res.ResourceTable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * The base Context of the desktop application (Android's ContextImpl). Directories live under
 * [AndroidRuntime.dataDir], resources come from the app resource table.
 */
class ContextImpl private constructor(
    private val configurationOverride: Configuration?,
) : Context() {
    companion object {
        private val prefs = ConcurrentHashMap<String, SharedPreferencesImpl>()
        private val receivers = ConcurrentHashMap<BroadcastReceiver, IntentFilter>()
        private val sharedPackageManager = PackageManager()
        private val notificationManager = NotificationManager()
        private val clipboard = ClipboardManager()
        private val connectivity = ConnectivityManager()
        private val activityManager = ActivityManager()
        private val uiModeManager = UiModeManager()
        private val powerManager = PowerManager()
        private val inputMethodManager = InputMethodManager()
        private val audioManager = android.media.AudioManager()
        private val nsdManager by lazy { android.net.nsd.NsdManager() }

        @Volatile
        var appResourceTable: ResourceTable? = null

        @Volatile
        private var cachedResources: Resources? = null

        @JvmStatic
        fun create(): ContextImpl = ContextImpl(null)

        /** Drop cached resources, e.g. after a locale change */
        @JvmStatic
        fun invalidateResources() {
            cachedResources = null
        }

        @JvmStatic
        fun sharedPreferences(name: String): SharedPreferencesImpl =
            prefs.getOrPut(name) { SharedPreferencesImpl(File(AndroidRuntime.sharedPrefsDir(), "$name.xml")) }
    }

    private val mainLooper = Looper.getMainLooper()
    private var theme: Resources.Theme? = null
    private var themeRes = 0
    private val mContentResolver by lazy { ContentResolver(this) }
    private var ownResources: Resources? = null

    override fun getAssets(): AssetManager = getResources().getAssets()

    override fun getResources(): Resources {
        if (configurationOverride != null) {
            return ownResources ?: Resources(
                AssetManager(appResourceTable ?: FrameworkResources.INSTANCE),
                AndroidRuntime.displayMetrics,
                configurationOverride
            ).also { ownResources = it }
        }
        return cachedResources ?: Resources(
            AssetManager(appResourceTable ?: FrameworkResources.INSTANCE),
            AndroidRuntime.displayMetrics,
            Configuration.current()
        ).also { cachedResources = it }
    }

    override fun getPackageManager(): PackageManager = sharedPackageManager
    override fun getContentResolver(): ContentResolver = mContentResolver
    override fun getMainLooper(): Looper = mainLooper
    override fun getApplicationContext(): Context = AndroidRuntime.applicationContext ?: this
    override fun setTheme(resid: Int) {
        themeRes = resid
        theme?.applyStyle(resid, true)
    }

    override fun getTheme(): Resources.Theme {
        return theme ?: getResources().newTheme().also {
            if (themeRes != 0) it.applyStyle(themeRes, true)
            theme = it
        }
    }

    override fun getClassLoader(): ClassLoader = ContextImpl::class.java.classLoader
    override fun getPackageName(): String = AndroidRuntime.PACKAGE_NAME

    override fun getApplicationInfo(): ApplicationInfo = ApplicationInfo().also {
        it.packageName = AndroidRuntime.PACKAGE_NAME
        it.processName = AndroidRuntime.PACKAGE_NAME
        it.dataDir = AndroidRuntime.dataDir.absolutePath
        it.sourceDir = File(ContextImpl::class.java.protectionDomain?.codeSource?.location?.toURI() ?: AndroidRuntime.dataDir.toURI()).absolutePath
        it.publicSourceDir = it.sourceDir
        it.nativeLibraryDir = File(AndroidRuntime.dataDir, "lib").absolutePath
        it.nonLocalizedLabel = "CloudStream"
        it.name = "com.lagradost.cloudstream3.CloudStreamApp"
    }

    override fun getPackageResourcePath(): String = getApplicationInfo().sourceDir
    override fun getPackageCodePath(): String = getApplicationInfo().sourceDir

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = sharedPreferences(name)

    override fun deleteSharedPreferences(name: String): Boolean {
        prefs.remove(name)?.edit()?.clear()?.commit()
        return File(AndroidRuntime.sharedPrefsDir(), "$name.xml").delete()
    }

    override fun openFileInput(name: String): FileInputStream = FileInputStream(File(filesDir, name))
    override fun openFileOutput(name: String, mode: Int): FileOutputStream =
        FileOutputStream(File(filesDir, name), (mode and MODE_APPEND) != 0)

    override fun deleteFile(name: String): Boolean = File(filesDir, name).delete()
    override fun getFileStreamPath(name: String): File = File(filesDir, name)
    override fun getDataDir(): File = AndroidRuntime.dataDir
    override fun getFilesDir(): File = AndroidRuntime.filesDir()
    override fun getNoBackupFilesDir(): File = File(AndroidRuntime.dataDir, "no_backup").also { it.mkdirs() }
    override fun getExternalFilesDir(type: String?): File =
        (if (type == null) AndroidRuntime.externalFilesDir() else File(AndroidRuntime.externalFilesDir(), type)).also { it.mkdirs() }

    override fun getExternalFilesDirs(type: String?): Array<File> = arrayOf(getExternalFilesDir(type))
    override fun getObbDir(): File = File(AndroidRuntime.dataDir, "obb").also { it.mkdirs() }
    override fun getCacheDir(): File = AndroidRuntime.cacheDir()
    override fun getCodeCacheDir(): File = File(AndroidRuntime.dataDir, "code_cache").also { it.mkdirs() }
    override fun getExternalCacheDir(): File = File(AndroidRuntime.externalFilesDir(), "cache").also { it.mkdirs() }
    override fun getExternalCacheDirs(): Array<File> = arrayOf(externalCacheDir!!)
    override fun getExternalMediaDirs(): Array<File> = arrayOf(File(AndroidRuntime.externalFilesDir(), "media").also { it.mkdirs() })
    override fun fileList(): Array<String> = filesDir.list() ?: emptyArray()
    override fun getDir(name: String, mode: Int): File = File(AndroidRuntime.dataDir, "app_$name").also { it.mkdirs() }
    override fun getDatabasePath(name: String): File = File(File(AndroidRuntime.dataDir, "databases").also { it.mkdirs() }, name)
    override fun databaseList(): Array<String> = File(AndroidRuntime.dataDir, "databases").list() ?: emptyArray()
    override fun deleteDatabase(name: String): Boolean = getDatabasePath(name).delete()

    override fun startActivity(intent: Intent) = startActivity(intent, null)
    override fun startActivity(intent: Intent, options: Bundle?) {
        if (!AndroidRuntime.host.startActivity(intent)) {
            android.util.Log.w("ContextImpl", "No activity found to handle $intent")
            throw android.content.ActivityNotFoundException("No Activity found to handle $intent")
        }
    }

    override fun sendBroadcast(intent: Intent) {
        val action = intent.action ?: return
        val handler = Handler(mainLooper)
        for ((receiver, filter) in receivers) {
            if (filter.hasAction(action)) handler.post { receiver.onReceive(this, intent) }
        }
    }

    override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter): Intent? {
        if (receiver != null) receivers[receiver] = filter
        return null
    }

    override fun unregisterReceiver(receiver: BroadcastReceiver) {
        receivers.remove(receiver)
    }

    override fun startService(service: Intent): ComponentName? {
        return Services.start(this, service) ?: run {
            AndroidRuntime.host.startActivity(Intent(service).setAction("desktop.intent.action.START_SERVICE:" + (service.action ?: "")))
            service.component
        }
    }

    override fun stopService(service: Intent): Boolean = Services.stop(service)

    override fun getSystemService(name: String): Any? = when (name) {
        LAYOUT_INFLATER_SERVICE -> LayoutInflater.create(this)
        CLIPBOARD_SERVICE -> clipboard
        CONNECTIVITY_SERVICE -> connectivity
        ACTIVITY_SERVICE -> activityManager
        INPUT_METHOD_SERVICE -> inputMethodManager
        NOTIFICATION_SERVICE -> notificationManager
        UI_MODE_SERVICE -> uiModeManager
        POWER_SERVICE -> powerManager
        AUDIO_SERVICE -> audioManager
        KEYGUARD_SERVICE -> KeyguardManager()
        WINDOW_SERVICE -> DesktopWindowManager
        NSD_SERVICE -> nsdManager
        APP_OPS_SERVICE -> android.app.AppOpsManager()
        else -> null
    }

    override fun getSystemServiceName(serviceClass: Class<*>): String? = when (serviceClass) {
        LayoutInflater::class.java -> LAYOUT_INFLATER_SERVICE
        ClipboardManager::class.java -> CLIPBOARD_SERVICE
        ConnectivityManager::class.java -> CONNECTIVITY_SERVICE
        ActivityManager::class.java -> ACTIVITY_SERVICE
        InputMethodManager::class.java -> INPUT_METHOD_SERVICE
        NotificationManager::class.java -> NOTIFICATION_SERVICE
        UiModeManager::class.java -> UI_MODE_SERVICE
        PowerManager::class.java -> POWER_SERVICE
        android.media.AudioManager::class.java -> AUDIO_SERVICE
        android.net.nsd.NsdManager::class.java -> NSD_SERVICE
        KeyguardManager::class.java -> KEYGUARD_SERVICE
        WindowManager::class.java -> WINDOW_SERVICE
        else -> null
    }

    override fun checkPermission(permission: String, pid: Int, uid: Int): Int = PackageManager.PERMISSION_GRANTED
    override fun checkCallingOrSelfPermission(permission: String): Int = PackageManager.PERMISSION_GRANTED
    override fun checkSelfPermission(permission: String): Int = PackageManager.PERMISSION_GRANTED

    override fun createConfigurationContext(overrideConfiguration: Configuration): Context {
        val config = Configuration(Configuration.current())
        config.setTo(overrideConfiguration)
        return ContextImpl(config)
    }

    override fun createPackageContext(packageName: String, flags: Int): Context = this

    private object DesktopWindowManager : WindowManager {
        override fun addView(view: View, params: ViewGroup.LayoutParams?) {}
        override fun updateViewLayout(view: View, params: ViewGroup.LayoutParams?) {}
        override fun removeView(view: View) {}
        override fun getDefaultDisplay(): Display = Display()
    }
}
