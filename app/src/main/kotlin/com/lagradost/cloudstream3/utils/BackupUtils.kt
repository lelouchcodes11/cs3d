package com.lagradost.cloudstream3.utils

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getActivity
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.plugins.PLUGINS_KEY
import com.lagradost.cloudstream3.plugins.PLUGINS_KEY_LOCAL
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.Companion.ANILIST_CACHED_LIST
import com.lagradost.cloudstream3.syncproviders.providers.MALApi.Companion.MAL_CACHED_LIST
import com.lagradost.cloudstream3.syncproviders.providers.KitsuApi.Companion.KITSU_CACHED_LIST
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStore.getDefaultSharedPrefs
import com.lagradost.cloudstream3.utils.DataStore.getSharedPrefs
import com.lagradost.cloudstream3.utils.UIHelper.checkWrite
import com.lagradost.cloudstream3.utils.UIHelper.requestRW
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager.setupStream
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects
import com.lagradost.cloudstream3.utils.downloader.DownloadQueueManager.QUEUE_KEY
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager.KEY_DOWNLOAD_INFO
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager.KEY_RESUME_IN_QUEUE
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager.KEY_RESUME_PACKAGES
import com.lagradost.desktop.platform.AppRestart
import com.lagradost.desktop.platform.PrefsBackup
import com.lagradost.desktop.runtime.SharedPreferencesImpl
import com.lagradost.safefile.MediaFileContentType
import com.lagradost.safefile.SafeFile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.internal.closeQuietly
import android.util.Log
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.io.OutputStream
import java.util.Collections
import java.io.PrintWriter
import java.lang.System.currentTimeMillis
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupUtils {

    /**
     * desktop: key inside the datastore part of a backup file that holds the installed extensions (a JSON list of [BackupPlugin]).
     * The extensions themselves and their local paths are not in a backup, only what is needed to download them again.
     * The Android app ignores the key (it only writes it to its preferences), so backups stay readable there.
     */
    private const val BACKUP_PLUGINS_KEY = "DESKTOP_BACKUP_PLUGINS"

    /** An installed online extension: [internalName] and the [url] its .cs3 file was downloaded from */
    @Serializable
    data class BackupPlugin(
        @JsonProperty("internalName") @SerialName("internalName") val internalName: String,
        @JsonProperty("url") @SerialName("url") val url: String,
    )

    /**
     * No sensitive or breaking data in the backup
     */
    private val nonTransferableKeys = listOf(
        ANILIST_CACHED_LIST,
        MAL_CACHED_LIST,
        KITSU_CACHED_LIST,

        // The plugins themselves are not backed up
        PLUGINS_KEY,
        PLUGINS_KEY_LOCAL,

        AccountManager.ACCOUNT_TOKEN,
        AccountManager.ACCOUNT_IDS,

        // TODO proper getter for string res keys to ensure that they are updated
        "biometric_key", // can lock down users if backup is shared on a incompatible device
        "nginx_user", // Nginx user key

        // No access rights after restore data from backup
        "download_path_key",
        "download_path_key_visual",
        "backup_path_key",
        "backup_dir_path_key",

        // When sharing backup we do not want to transfer what is essentially the password
        // Note that this is deprecated, and can be removed after all tokens have expired
        "anilist_token",
        "anilist_user",
        "mal_user",
        "mal_token",
        "mal_refresh_token",
        "mal_unixtime",
        "open_subtitles_user",
        "subdl_user",
        "simkl_token",


        // Downloads can not be restored from backups.
        // The download path URI can not be transferred.
        // In the future we may potentially write metadata to files in the download directory
        // and make it possible to restore download folders using that metadata.
        DOWNLOAD_EPISODE_CACHE_BACKUP,
        DOWNLOAD_EPISODE_CACHE,
        
        // Download headers are unintuitively used in the resume watching system.
        // We can therefore not prune download headers in backups.
        // DOWNLOAD_HEADER_CACHE_BACKUP,
        // DOWNLOAD_HEADER_CACHE,
        

        // This may overwrite valid local data with invalid data
        KEY_DOWNLOAD_INFO,

        // Prevent backups from automatically starting downloads
        KEY_RESUME_IN_QUEUE,
        KEY_RESUME_PACKAGES,
        QUEUE_KEY,

        // Prevent automatic plugin download after restoring backup
        "auto_download_plugins_key2",

        // desktop: not a preference, the list of installed extensions travels in the backup under this key and is read on restore
        BACKUP_PLUGINS_KEY,
    )

    /** false if key should not be contained in backup */
    private fun String.isTransferable(): Boolean {
        return !nonTransferableKeys.any { this.contains(it) }
    }

    private var restoreFileSelector: ActivityResultLauncher<Array<String>>? = null

    // Kinda hack, but I couldn't think of a better way
    @Serializable
    data class BackupVars(
        @JsonProperty("_Bool") @SerialName("_Bool") val bool: Map<String, Boolean>?,
        @JsonProperty("_Int") @SerialName("_Int") val int: Map<String, Int>?,
        @JsonProperty("_String") @SerialName("_String") val string: Map<String, String>?,
        @JsonProperty("_Float") @SerialName("_Float") val float: Map<String, Float>?,
        @JsonProperty("_Long") @SerialName("_Long") val long: Map<String, Long>?,
        @JsonProperty("_StringSet") @SerialName("_StringSet") val stringSet: Map<String, Set<String>?>?,
    )

    @Serializable
    data class BackupFile(
        @JsonProperty("datastore") @SerialName("datastore") val datastore: BackupVars,
        @JsonProperty("settings") @SerialName("settings") val settings: BackupVars,
    )

    @Suppress("UNCHECKED_CAST")
    private fun getBackup(context: Context): BackupFile {
        val allData = context.getSharedPrefs().all.filter { it.key.isTransferable() }
        val allSettings = context.getDefaultSharedPrefs().all.filter { it.key.isTransferable() }

        // desktop: which extensions are installed (restoring downloads them again, see reinstallPlugins)
        val installedPlugins = PluginManager.getPluginsOnline()
            .filter { it.isOnline && !it.url.isNullOrBlank() }
            .map { BackupPlugin(it.internalName, it.url!!) }
            .distinctBy { it.url }
        val pluginEntry = if (installedPlugins.isEmpty()) emptyMap() else mapOf(BACKUP_PLUGINS_KEY to installedPlugins.toJson())

        val allDataSorted = BackupVars(
            allData.filter { it.value is Boolean } as? Map<String, Boolean>,
            allData.filter { it.value is Int } as? Map<String, Int>,
            (allData.filter { it.value is String } as? Map<String, String>)?.plus(pluginEntry),
            allData.filter { it.value is Float } as? Map<String, Float>,
            allData.filter { it.value is Long } as? Map<String, Long>,
            allData.filter { it.value as? Set<String> != null } as? Map<String, Set<String>>,
        )

        val allSettingsSorted = BackupVars(
            allSettings.filter { it.value is Boolean } as? Map<String, Boolean>,
            allSettings.filter { it.value is Int } as? Map<String, Int>,
            allSettings.filter { it.value is String } as? Map<String, String>,
            allSettings.filter { it.value is Float } as? Map<String, Float>,
            allSettings.filter { it.value is Long } as? Map<String, Long>,
            allSettings.filter { it.value as? Set<String> != null } as? Map<String, Set<String>>,
        )

        return BackupFile(
            allDataSorted,
            allSettingsSorted,
        )
    }

    @WorkerThread
    fun restore(
        context: Context?,
        backupFile: BackupFile,
        restoreSettings: Boolean,
        restoreDataStore: Boolean,
    ) {
        if (context == null) return
        if (restoreSettings) {
            context.restoreMap(backupFile.settings.bool, true)
            context.restoreMap(backupFile.settings.int, true)
            context.restoreMap(backupFile.settings.string, true)
            context.restoreMap(backupFile.settings.float, true)
            context.restoreMap(backupFile.settings.long, true)
            context.restoreMap(backupFile.settings.stringSet, true)
        }

        if (restoreDataStore) {
            context.restoreMap(backupFile.datastore.bool)
            context.restoreMap(backupFile.datastore.int)
            context.restoreMap(backupFile.datastore.string)
            context.restoreMap(backupFile.datastore.float)
            context.restoreMap(backupFile.datastore.long)
            context.restoreMap(backupFile.datastore.stringSet)
        }

        // Make sure the library is fresh
        for(api in AccountManager.syncApis) {
            api.requireLibraryRefresh = true
        }
    }

    fun backup(context: Context?) = ioSafe {
        if (context == null) return@ioSafe
        var fileStream: OutputStream? = null
        var printStream: PrintWriter? = null

        try {
            if (!context.checkWrite()) {
                toast(context.getString(R.string.backup_failed))
                context.getActivity()?.requestRW()
                return@ioSafe
            }

            val date = SimpleDateFormat("yyyy_MM_dd_HH_mm", Locale.getDefault()).format(Date(currentTimeMillis()))
            val displayName = "CS3_Backup_${date}"
            val backupFile = getBackup(context)
            val stream = setupBackupStream(context, displayName)

            fileStream = stream.openNew()
            printStream = PrintWriter(fileStream)
            printStream.print(backupFile.toJson())
            printStream.flush()
            toast(
                "Backup saved as $displayName.txt" +
                    (getCurrentBackupDir(context).first?.filePath()?.let { " in $it" } ?: "")
            )
        } catch (e: Exception) {
            logError(e)
            try {
                toast(txt(R.string.backup_failed_error_format, e.toString()).asString(context))
            } catch (e: Exception) {
                logError(e)
            }
        } finally {
            printStream?.closeQuietly()
            fileStream?.closeQuietly()
        }
    }

    @Throws(IOException::class)
    private fun setupBackupStream(context: Context, name: String, ext: String = "txt"): DownloadObjects.StreamData {
        return setupStream(
            baseFile = getCurrentBackupDir(context).first ?: getDefaultBackupDir(context)
            ?: throw IOException("Bad config"),
            name,
            folder = null,
            extension = ext,
            tryResume = false,
        )
    }

    /** Reads the backup file at [uri] and restores it, shared by the file picker and the dev server */
    fun restoreFromUri(activity: FragmentActivity, uri: Uri) = ioSafe {
        try {
            val input = activity.contentResolver.openInputStream(uri)
                ?: return@ioSafe

            val text = input.bufferedReader().readText()
            val restoredValue = parseJson<BackupFile>(text)

            // desktop: a copy of the current data to go back to, taken before it is replaced
            PrefsBackup.snapshotNow("before restoring a backup file")
            restore(
                activity,
                restoredValue,
                restoreSettings = true,
                restoreDataStore = true,
            )
            // desktop: recreate() does nothing here and the open screens (theme, look, lists) keep what they read at start-up,
            // so the restored values only show after a start; saving anything from those screens would also write the old values back.
            // The writes are asked for later by apply(): put them on disk before the app goes down and comes back.
            SharedPreferencesImpl.flushAll()

            // desktop: the extensions of the backup are downloaded again from the (just restored) repositories before the restart,
            // which then loads them like any other start does
            val wanted = restoredValue.installedPlugins()
            var summary = "Backup restored."
            if (wanted.isNotEmpty()) {
                toast("Backup restored. Downloading ${wanted.size} extensions...")
                val failed = reinstallPlugins(activity, wanted)
                summary += if (failed == 0) " ${wanted.size} extensions installed."
                else " ${wanted.size - failed} of ${wanted.size} extensions installed, the others can be installed from Extensions."
            }
            toast("$summary CloudStream is restarting to apply it.")
            // long enough to read the message before the window goes away
            kotlinx.coroutines.delay(if (wanted.isEmpty()) 1500 else 3000)
            AppRestart.request()
        } catch (e: Exception) {
            logError(e)
            toast(activity.getString(R.string.restore_failed_format).format(e.toString()))
        }
    }

    private fun BackupFile.installedPlugins(): List<BackupPlugin> =
        datastore.string?.get(BACKUP_PLUGINS_KEY)
            ?.let { runCatching { parseJson<Array<BackupPlugin>>(it).toList() }.getOrNull() }
            ?: emptyList()

    /**
     * Downloads the [wanted] extensions from the repositories (the restored ones and the built-in ones) that are not installed yet.
     * They are only saved, not loaded: the app restarts right after and loads everything saved.
     * @return how many could not be installed (not offered by any repository any more, or the download failed)
     */
    private suspend fun reinstallPlugins(activity: Activity, wanted: List<BackupPlugin>): Int {
        val repositories = (RepositoryManager.getRepositories() + RepositoryManager.PREBUILT_REPOSITORIES).distinctBy { it.url }
        val offered = repositories.amap { RepositoryManager.getRepoPlugins(it) ?: emptyList() }.flatten()
        val gate = Semaphore(4)
        val failed = Collections.synchronizedList(ArrayList<String>())

        wanted.distinctBy { it.url }.amap { plugin ->
            gate.withPermit {
                // the same download address first, then the same name (a repository can move its files)
                val found = offered.firstOrNull { it.plugin.url == plugin.url }
                    ?: offered.firstOrNull { it.plugin.internalName == plugin.internalName }
                if (found == null) {
                    failed.add(plugin.internalName)
                    return@withPermit
                }
                val site = found.plugin
                if (PluginManager.getPluginPath(activity, site.internalName, found.repositoryData.url).exists()) return@withPermit
                // one more try, the connection can drop while fifty files are fetched
                val ok = (1..2).any {
                    PluginManager.downloadPlugin(activity, site.url, site.fileHash, site.internalName, found.repositoryData.url, false)
                }
                if (!ok) failed.add(plugin.internalName)
            }
        }
        if (failed.isNotEmpty()) Log.w("BackupUtils", "extensions that could not be installed again: ${failed.joinToString()}")
        return failed.size
    }

    /**
     * desktop: CommonActivity.showToast gives the toast an Android layout as its view, which the desktop toast never shows (nothing appears),
     * so the messages of backing up and restoring go to the desktop toast host directly.
     */
    private fun toast(text: String) = com.lagradost.desktop.ui.Toasts.show(text, true)

    fun FragmentActivity.setUpBackup() {
        try {
            restoreFileSelector =
                registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                    if (uri == null) return@registerForActivityResult
                    restoreFromUri(this, uri)
                }
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun Activity.restorePrompt() {
        runOnUiThread {
            try {
                restoreFileSelector?.launch(
                    arrayOf(
                        "text/plain",
                        "text/str",
                        "text/x-unknown",
                        "application/json",
                        "unknown/unknown",
                        "content/unknown",
                        "application/octet-stream",
                    )
                )
            } catch (e: Exception) {
                showToast(e.message)
                logError(e)
            }
        }
    }

    private fun <T> Context.restoreMap(
        map: Map<String, T>?,
        isEditingAppSettings: Boolean = false,
    ) {
        val editor = DataStore.editor(this, isEditingAppSettings)
        map?.forEach {
            if (it.key.isTransferable()) {
                editor.setKeyRaw(it.key, it.value)
            }
        }
        editor.apply()
    }

    /**
     * Copy of [com.lagradost.cloudstream3.utils.downloader.DownloadFileManagement.getDefaultDir],
     * modified for backup-specific paths.
     */
    fun getDefaultBackupDir(context: Context): SafeFile? {
        return SafeFile.fromMedia(context, MediaFileContentType.Downloads)
    }

    /**
     * Copy of [com.lagradost.cloudstream3.utils.downloader.DownloadFileManagement.getBasePath],
     * modified for backup-specific paths.
     */
    fun getCurrentBackupDir(context: Context): Pair<SafeFile?, String?> {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)
        val basePathSetting = settingsManager.getString(context.getString(R.string.backup_path_key), null)
        return baseBackupPathToFile(context, basePathSetting) to basePathSetting
    }

    /**
     * Copy of [com.lagradost.cloudstream3.utils.downloader.DownloadFileManagement.basePathToFile],
     * modified for backup-specific paths.
     */
    private fun baseBackupPathToFile(context: Context, path: String?): SafeFile? {
        return when {
            path.isNullOrBlank() -> getDefaultBackupDir(context)
            path.startsWith("content://") -> SafeFile.fromUri(context, path.toUri())
            else -> SafeFile.fromFilePath(context, path)
        }
    }
}
