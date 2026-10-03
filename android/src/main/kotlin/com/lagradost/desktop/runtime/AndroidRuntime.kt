package com.lagradost.desktop.runtime

import android.content.Context
import java.io.File
import java.util.Locale

/**
 * Global state of the desktop Android runtime.
 *
 * The desktop application initialises this once at startup, before anything else touches
 * the Android APIs (see [init]).
 */
object AndroidRuntime {
    const val PACKAGE_NAME = "com.lagradost.cloudstream3"

    /** Root folder for all app data, equivalent of /data/data/<package> on Android. */
    @Volatile
    lateinit var dataDir: File
        private set

    /** Equivalent of the external storage root (/sdcard), used for "Cloudstream3/" folders. */
    @Volatile
    lateinit var externalStorageDir: File
        private set

    @Volatile
    var applicationContext: Context? = null
        internal set

    /** Implemented by the application to show platform UI (toasts, dialogs, browser, ...). */
    @Volatile
    var host: UiHost = UiHost.Headless

    @Volatile
    var displayMetrics = android.util.DisplayMetrics().apply {
        density = 1.5f
        densityDpi = 240
        scaledDensity = 1.5f
        widthPixels = 1920
        heightPixels = 1080
        xdpi = 240f
        ydpi = 240f
    }

    /**
     * Portable copy of the app: when a `data` folder (or `portable.txt`) sits next to the launcher, everything
     * the app stores goes there instead of the user profile, so the folder can be moved or deleted as a whole.
     */
    private fun portableDataDir(): File? {
        val launcher = System.getProperty("jpackage.app-path")?.let { File(it) } ?: return null
        val dir = launcher.absoluteFile.parentFile ?: return null
        return if (File(dir, "portable.txt").exists() || File(dir, "data").isDirectory) File(dir, "data") else null
    }

    fun defaultDataDir(): File {
        portableDataDir()?.let { return it }
        val os = System.getProperty("os.name").lowercase(Locale.ROOT)
        val home = System.getProperty("user.home")
        val base = when {
            os.contains("win") -> System.getenv("APPDATA")?.let { File(it) } ?: File(home, "AppData/Roaming")
            os.contains("mac") -> File(home, "Library/Application Support")
            else -> System.getenv("XDG_DATA_HOME")?.let { File(it) } ?: File(home, ".local/share")
        }
        return File(base, "CloudStream")
    }

    fun init(dataDir: File = System.getProperty("cloudstream.data")?.let { File(it) } ?: defaultDataDir()) {
        this.dataDir = dataDir.absoluteFile
        this.dataDir.mkdirs()
        this.externalStorageDir = File(this.dataDir, "storage").also { it.mkdirs() }
    }

    val isInitialized: Boolean get() = this::dataDir.isInitialized

    /** The application context; only valid after [startApplication] */
    val context: Context
        get() = applicationContext ?: error("AndroidRuntime.startApplication has not been called")

    /**
     * Equivalent of the process start on Android: attaches a base context to [app], makes it the
     * application context and calls [android.app.Application.onCreate].
     */
    fun startApplication(app: android.app.Application) {
        check(isInitialized) { "AndroidRuntime.init must be called first" }
        app.attach(ContextImpl.create())
        applicationContext = app
        app.onCreate()
    }

    fun filesDir(): File = File(dataDir, "files").also { it.mkdirs() }
    fun cacheDir(): File = File(dataDir, "cache").also { it.mkdirs() }
    fun sharedPrefsDir(): File = File(dataDir, "shared_prefs").also { it.mkdirs() }
    fun externalFilesDir(): File = File(dataDir, "external_files").also { it.mkdirs() }
}
