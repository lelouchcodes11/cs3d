package com.lagradost.cloudstream3.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.MainActivity.Companion.deleteFileOnExit
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.services.PackageInstallerService
import com.lagradost.cloudstream3.utils.AppContextUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.GitInfo.currentCommitHash
import com.lagradost.desktop.DesktopPlatform
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okio.BufferedSink
import okio.buffer
import okio.sink
import java.io.File

object InAppUpdater {
    // desktop: releases come from this port's repo ("owner/repo"), never upstream's Android releases
    private val DESKTOP_UPDATE_REPO: String? = System.getProperty("cloudstream.updateRepo")?.trim()?.takeIf { it.count { c -> c == '/' } == 1 }
    private val GITHUB_USER_NAME get() = DESKTOP_UPDATE_REPO!!.substringBefore('/')
    private val GITHUB_REPO get() = DESKTOP_UPDATE_REPO!!.substringAfter('/')

    private const val LOG_TAG = "InAppUpdater"

    @Serializable
    private data class GithubAsset(
        @JsonProperty("name") @SerialName("name") val name: String,
        @JsonProperty("size") @SerialName("size") val size: Int, // Size in bytes
        @JsonProperty("browser_download_url") @SerialName("browser_download_url") val browserDownloadUrl: String,
        @JsonProperty("content_type") @SerialName("content_type") val contentType: String,
    )

    @Serializable
    private data class GithubRelease(
        @JsonProperty("tag_name") @SerialName("tag_name") val tagName: String, // Version code
        @JsonProperty("body") @SerialName("body") val body: String, // Description
        @JsonProperty("assets") @SerialName("assets") val assets: List<GithubAsset>,
        @JsonProperty("target_commitish") @SerialName("target_commitish") val targetCommitish: String, // Branch
        @JsonProperty("prerelease") @SerialName("prerelease") val prerelease: Boolean,
        @JsonProperty("node_id") @SerialName("node_id") val nodeId: String,
    )

    @Serializable
    private data class GithubObject(
        @JsonProperty("sha") @SerialName("sha") val sha: String,
        @JsonProperty("type") @SerialName("type") val type: String? = null,
        @JsonProperty("url") @SerialName("url") val url: String? = null,
    )

    @Serializable
    private data class GithubTag(
        @JsonProperty("object") @SerialName("object") val githubObject: GithubObject,
    )

    @Serializable
    private data class Update(
        @JsonProperty("shouldUpdate") @SerialName("shouldUpdate") val shouldUpdate: Boolean,
        @JsonProperty("updateURL") @SerialName("updateURL") val updateURL: String?,
        @JsonProperty("updateVersion") @SerialName("updateVersion") val updateVersion: String?,
        @JsonProperty("changelog") @SerialName("changelog") val changelog: String?,
        @JsonProperty("updateNodeId") @SerialName("updateNodeId") val updateNodeId: String?,
        @JsonProperty("assetName") @SerialName("assetName") val assetName: String? = null,
    )

    private suspend fun Activity.getAppUpdate(installPrerelease: Boolean): Update {
        return try {
            when {
                BuildConfig.DEBUG -> Update(false, null, null, null, null)
                DESKTOP_UPDATE_REPO == null -> Update(false, null, null, null, null)
                BuildConfig.FLAVOR == "prerelease" || installPrerelease -> getPreReleaseUpdate()
                else -> getReleaseUpdate()
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, Log.getStackTraceString(e))
            Update(false, null, null, null, null)
        }
    }

    private suspend fun Activity.getReleaseUpdate(): Update {
        val url = "https://api.github.com/repos/$GITHUB_USER_NAME/$GITHUB_REPO/releases"
        val headers = mapOf("Accept" to "application/vnd.github.v3+json")
        val response = parseJson<Array<GithubRelease>>(
            app.get(url, headers = headers).text
        ).toList()

        val versionRegex = Regex("""(.*?((\d+)\.(\d+)\.(\d+)).*)""")
        val versionRegexLocal = Regex("""(.*?((\d+)\.(\d+)\.(\d+)).*)""")
        val foundList = response.filter { rel ->
            !rel.prerelease
        }.sortedWith(compareBy { release ->
            release.assets.firstOrNull {
                it.name.endsWith(".msi", ignoreCase = true) ||
                it.name.endsWith(".exe", ignoreCase = true) ||
                it.contentType == "application/vnd.android.package-archive"
            }?.name?.let { it1 ->
                versionRegex.find(it1)?.groupValues?.let {
                    it[3].toInt() * 100_000_000 + it[4].toInt() * 10_000 + it[5].toInt()
                }
            } ?: 0
        }).toList()

        val found = foundList.lastOrNull()
        if (found == null) {
            return Update(false, null, null, null, null)
        }

        val foundAsset = found.assets.firstOrNull {
            if (DesktopPlatform.isWindows) it.name.endsWith(".msi", ignoreCase = true)
            else it.contentType == "application/vnd.android.package-archive"
        } ?: found.assets.firstOrNull {
            it.name.endsWith(".msi", ignoreCase = true) ||
            it.name.endsWith(".exe", ignoreCase = true) ||
            it.contentType == "application/vnd.android.package-archive"
        } ?: found.assets.getOrNull(0)

        val foundVersion = foundAsset?.name?.let { versionRegex.find(it) }

        if (foundAsset == null || foundVersion == null) {
            return Update(false, null, null, null, null)
        }

        val currentVersion = packageName?.let {
            packageManager.getPackageInfo(it, 0)
        }

        val shouldUpdate = if (foundAsset.browserDownloadUrl.isBlank()) {
            false
        } else {
            val localScore = currentVersion?.versionName?.let { versionName ->
                versionRegexLocal.find(versionName)?.groupValues?.let {
                    it[3].toInt() * 100_000_000 + it[4].toInt() * 10_000 + it[5].toInt()
                }
            } ?: 0
            val remoteScore = foundVersion.groupValues.let {
                it[3].toInt() * 100_000_000 + it[4].toInt() * 10_000 + it[5].toInt()
            }
            localScore < remoteScore
        }

        return Update(
            shouldUpdate,
            foundAsset.browserDownloadUrl,
            foundVersion.groupValues[2],
            found.body,
            found.nodeId,
            foundAsset.name,
        )
    }

    private suspend fun Activity.getPreReleaseUpdate(): Update {
        val tagUrl = "https://api.github.com/repos/$GITHUB_USER_NAME/$GITHUB_REPO/git/ref/tags/pre-release"
        val releaseUrl = "https://api.github.com/repos/$GITHUB_USER_NAME/$GITHUB_REPO/releases"
        val headers = mapOf("Accept" to "application/vnd.github.v3+json")
        val response = parseJson<Array<GithubRelease>>(
            app.get(releaseUrl, headers = headers).text
        ).toList()

        val found = response.lastOrNull { rel ->
            rel.prerelease || rel.tagName == "pre-release"
        }
        if (found == null) {
            return Update(false, null, null, null, null)
        }

        val foundAsset = found.assets.firstOrNull {
            if (DesktopPlatform.isWindows) it.name.endsWith(".msi", ignoreCase = true)
            else it.contentType == "application/vnd.android.package-archive"
        } ?: found.assets.firstOrNull {
            it.name.endsWith(".msi", ignoreCase = true) ||
            it.name.endsWith(".exe", ignoreCase = true) ||
            it.contentType == "application/vnd.android.package-archive"
        } ?: found.assets.getOrNull(0)

        if (foundAsset == null) {
            return Update(false, null, null, null, null)
        }

        val tagResponse = parseJson<GithubTag>(app.get(tagUrl, headers = headers).text)
        val updateCommitHash = tagResponse.githubObject.sha.trim().take(7)
        Log.d(LOG_TAG, "Fetched GitHub tag: $updateCommitHash")

        return Update(
            currentCommitHash() != updateCommitHash,
            foundAsset.browserDownloadUrl,
            updateCommitHash,
            found.body,
            found.nodeId,
            foundAsset.name,
        )
    }

    private val updateLock = Mutex()

    private suspend fun Activity.downloadUpdate(url: String, assetName: String? = null): Boolean {
        try {
            Log.d(LOG_TAG, "Downloading update: $url")
            val appUpdateName = "CloudStream"
            val appUpdateSuffix = when {
                assetName?.endsWith(".msi", ignoreCase = true) == true -> "msi"
                assetName?.endsWith(".exe", ignoreCase = true) == true -> "exe"
                DesktopPlatform.isWindows -> "msi"
                else -> "apk"
            }

            // Delete all old updates
            this.cacheDir.listFiles()?.filter {
                it.name.startsWith(appUpdateName) && (it.extension == "apk" || it.extension == "msi" || it.extension == "exe")
            }?.forEach { deleteFileOnExit(it) }

            val downloadedFile = File.createTempFile(appUpdateName, ".$appUpdateSuffix")
            val sink: BufferedSink = downloadedFile.sink().buffer()

            updateLock.withLock {
                sink.writeAll(app.get(url).body.source())
                sink.close()
                openInstaller(this, downloadedFile)
            }

            return true
        } catch (e: Exception) {
            logError(e)
            return false
        }
    }

    private fun openInstaller(context: Context, file: File) = safe {
        if (DesktopPlatform.isWindows && file.extension.equals("msi", ignoreCase = true)) {
            ProcessBuilder("msiexec", "/i", file.absolutePath).start()
            kotlin.system.exitProcess(0)
        } else if (DesktopPlatform.isWindows && file.extension.equals("exe", ignoreCase = true)) {
            ProcessBuilder(file.absolutePath).start()
            kotlin.system.exitProcess(0)
        } else if (!DesktopPlatform.openFile(file)) {
            val contentUri = FileProvider.getUriForFile(
                context, BuildConfig.APPLICATION_ID + ".provider", file
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                data = contentUri
            }
            context.startActivity(installIntent)
        }
    }

    fun Activity.installPreReleaseIfNeeded() = ioSafe {
        runAutoUpdate(checkAutoUpdate = false, installPrerelease = true)
    }

    /**
     * @param checkAutoUpdate if the update check was launched automatically
     * @param installPrerelease if we want to install the pre-release version
     */
    suspend fun Activity.runAutoUpdate(
        checkAutoUpdate: Boolean = true, installPrerelease: Boolean = false
    ): Boolean {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val autoUpdateEnabled =
            settingsManager.getBoolean(getString(R.string.auto_update_key), true)
        if (checkAutoUpdate && !autoUpdateEnabled) {
            return false
        }

        val update = getAppUpdate(installPrerelease)
        if (!update.shouldUpdate || update.updateURL == null) {
            return false
        }

        val updateNodeId = settingsManager.getString(
            getString(R.string.skip_update_key), ""
        )

        if (update.updateNodeId.equals(updateNodeId) && checkAutoUpdate) {
            return false
        }

        runOnUiThread {
            safe {
                val currentVersion = packageName?.let {
                    packageManager.getPackageInfo(it, 0)
                }

                val builder = AlertDialog.Builder(this, R.style.AlertDialogCustom)
                builder.setTitle(
                    getString(R.string.new_update_format).format(
                        currentVersion?.versionName, update.updateVersion
                    )
                )

                val logRegex = Regex("\\[(.*?)]\\((.*?)\\)")
                val sanitizedChangelog = update.changelog?.replace(logRegex) { matchResult ->
                    matchResult.groupValues[1]
                }

                builder.setMessage(sanitizedChangelog)
                builder.apply {
                    setPositiveButton(R.string.update) { _, _ ->
                        if (ApkInstaller.delayedInstaller?.startInstallation() == true) return@setPositiveButton

                        showToast(R.string.download_started, Toast.LENGTH_LONG)

                        ioSafe {
                            if (!downloadUpdate(update.updateURL, update.assetName)) {
                                runOnUiThread {
                                    showToast(R.string.download_failed, Toast.LENGTH_LONG)
                                }
                            }
                        }
                    }

                    setNegativeButton(R.string.cancel) { _, _ -> }

                    if (checkAutoUpdate) {
                        setNeutralButton(R.string.skip_update) { _, _ ->
                            settingsManager.edit {
                                putString(
                                    getString(R.string.skip_update_key), update.updateNodeId ?: ""
                                )
                            }
                        }
                    }
                }
                builder.show().setDefaultFocus()
            }
        }
        return true
    }
}
