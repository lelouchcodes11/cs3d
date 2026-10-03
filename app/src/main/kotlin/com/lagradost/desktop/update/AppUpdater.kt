package com.lagradost.desktop.update

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.desktop.DesktopPlatform
import com.lagradost.desktop.runtime.AndroidRuntime
import com.lagradost.desktop.ui.DesktopUiHost
import com.lagradost.desktop.ui.Toasts
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.fluent.ProgressBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Looks for a newer version of this app in the GitHub releases of its repository, tells the user, and (installed copies on Windows)
 * downloads the MSI of the release, checks it and starts it. Portable copies are only sent to the release page: replacing the
 * files of a folder that is running is not something to do behind the user's back.
 *
 * Pre-releases count while the installed version is a pre-release itself (0.x); a stable install is only offered stable releases.
 * Nothing but the (public) releases list is requested, nothing about the user is sent.
 */
object AppUpdater {
    private const val TAG = "AppUpdater"
    const val DEFAULT_REPO = "lelouchcodes11/cs3d"

    private const val KEY_AUTO = "auto_update"
    private const val KEY_SKIPPED = "desktop_update_skipped"
    private const val KEY_CHECKED = "desktop_update_checked_at"
    private const val CHECK_EVERY_MS = 6L * 60 * 60 * 1000

    /** "owner/name"; `-Dcloudstream.updateRepo=` points a test build somewhere else */
    val repo: String = System.getProperty("cloudstream.updateRepo")?.trim()?.takeIf { it.count { c -> c == '/' } == 1 } ?: DEFAULT_REPO
    private val apiUrl: String = System.getProperty("cloudstream.updateApi")?.trim()?.takeIf { it.isNotEmpty() } ?: "https://api.github.com/repos/$repo/releases?per_page=20"

    val currentVersion: String = BuildConfig.DESKTOP_VERSION
    val repoUrl: String get() = "https://github.com/$repo"

    /** A folder with `portable.txt` or `data` next to the launcher: files are not replaced by an installer */
    val isPortable: Boolean by lazy {
        val launcher = System.getProperty("jpackage.app-path")?.let { File(it).absoluteFile.parentFile } ?: return@lazy true
        File(launcher, "portable.txt").exists() || File(launcher, "data").isDirectory
    }

    // ---------------------------------------------------------------------------------------------
    // Versions and releases
    // ---------------------------------------------------------------------------------------------

    class Version(val numbers: List<Int>, val preRelease: Boolean) : Comparable<Version> {
        override fun compareTo(other: Version): Int {
            for (i in 0 until 3) {
                val c = numbers.getOrElse(i) { 0 }.compareTo(other.numbers.getOrElse(i) { 0 })
                if (c != 0) return c
            }
            // 0.2.0 is newer than 0.2.0-beta
            return other.preRelease.compareTo(preRelease)
        }
    }

    /** "v0.1", "0.2.0", "v1.0.3-beta.2" -> numbers and the pre-release mark of a suffix */
    fun parseVersion(text: String): Version? {
        val m = Regex("""(\d+)(?:\.(\d+))?(?:\.(\d+))?""").find(text) ?: return null
        val numbers = m.groupValues.drop(1).filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: return null }
        val rest = text.substring(m.range.last + 1)
        return Version(numbers, rest.startsWith("-") && rest.length > 1 && rest[1].isLetter())
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class GhAsset(
        @JsonProperty("name") val name: String = "",
        @JsonProperty("size") val size: Long = 0,
        @JsonProperty("browser_download_url") val url: String = "",
        @JsonProperty("digest") val digest: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class GhRelease(
        @JsonProperty("tag_name") val tag: String = "",
        @JsonProperty("body") val body: String? = null,
        @JsonProperty("html_url") val htmlUrl: String = "",
        @JsonProperty("draft") val draft: Boolean = false,
        @JsonProperty("prerelease") val prerelease: Boolean = false,
        @JsonProperty("assets") val assets: List<GhAsset> = emptyList(),
    )

    class Release(
        val tag: String,
        val version: Version,
        val prerelease: Boolean,
        val notes: String,
        val pageUrl: String,
        val installerName: String?,
        val installerUrl: String?,
        val installerSize: Long,
        val sha256: String?,
    ) {
        val label: String get() = tag.trimStart('v', 'V')
    }

    /** Markdown of the release notes as plain text for the dialog */
    private fun plainNotes(markdown: String?): String = (markdown ?: "")
        .replace(Regex("""\[([^\]]*)]\([^)]*\)"""), "$1")
        .replace(Regex("""(?m)^#{1,6}\s*"""), "")
        .replace("**", "").replace("`", "")
        .replace(Regex("""\r?\n{3,}"""), "\n\n")
        .trim().take(1800)

    private fun toRelease(r: GhRelease): Release? {
        val version = parseVersion(r.tag) ?: return null
        val msi = r.assets.filter { it.name.endsWith(".msi", ignoreCase = true) && it.url.isNotBlank() }
            .let { list -> list.firstOrNull { it.name.contains("cloudstream", ignoreCase = true) } ?: list.firstOrNull() }
        val sha = msi?.digest?.takeIf { it.startsWith("sha256:", ignoreCase = true) }?.substringAfter(':')?.trim()?.lowercase()
        return Release(r.tag, version, r.prerelease, plainNotes(r.body), r.htmlUrl.ifBlank { "$repoUrl/releases" }, msi?.name, msi?.url, msi?.size ?: 0, sha)
    }

    /** The newest release this install should be offered, or null when there is none newer */
    private fun newestOffer(all: List<GhRelease>): Release? {
        val current = parseVersion(currentVersion) ?: return null
        val acceptPre = current.numbers.firstOrNull() == 0 || current.preRelease
        return all.asSequence().filter { !it.draft && (acceptPre || !it.prerelease) }.mapNotNull { toRelease(it) }
            .maxByOrNull { it.version }?.takeIf { it.version > current }
    }

    // ---------------------------------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------------------------------

    sealed interface Status {
        object Idle : Status
        object Checking : Status
        class UpToDate(val at: Long) : Status
        class Available(val release: Release) : Status
        class Failed(val reason: String, val download: Boolean = false) : Status
        class Downloading(val release: Release, val done: Long, val total: Long) : Status
    }

    var status: Status by mutableStateOf(Status.Idle)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(AndroidRuntime.context)

    var autoCheckEnabled: Boolean by mutableStateOf(runCatching { prefs.getBoolean(KEY_AUTO, true) }.getOrDefault(true))
        private set

    fun setAutoCheck(value: Boolean) {
        autoCheckEnabled = value
        prefs.edit().putBoolean(KEY_AUTO, value).apply()
    }

    val lastChecked: Long get() = runCatching { prefs.getLong(KEY_CHECKED, 0L) }.getOrDefault(0L)

    // ---------------------------------------------------------------------------------------------
    // Checking
    // ---------------------------------------------------------------------------------------------

    /** Asks GitHub; the result is also the new [status] */
    suspend fun check(): Status {
        status = Status.Checking
        val result: Status = withContext(Dispatchers.IO) {
            runCatching {
                val response = app.get(
                    apiUrl, headers = mapOf("Accept" to "application/vnd.github+json", "X-GitHub-Api-Version" to "2022-11-28"),
                    cacheTime = 0, timeout = 20,
                )
                when {
                    // no releases yet, or the repository is not public (yet)
                    response.code == 404 -> Status.UpToDate(System.currentTimeMillis())
                    response.code == 403 || response.code == 429 -> Status.Failed("GitHub is limiting requests from this network, try again later")
                    response.code !in 200..299 -> Status.Failed("GitHub answered ${response.code}")
                    else -> {
                        val offer = newestOffer(parseJson<Array<GhRelease>>(response.text).toList())
                        if (offer != null) Status.Available(offer) else Status.UpToDate(System.currentTimeMillis())
                    }
                }
            }.getOrElse { e ->
                Log.w(TAG, "update check failed: ${e.message}")
                Status.Failed(e.message?.takeIf { it.isNotBlank() }?.take(120) ?: "no connection")
            }
        }
        if (result !is Status.Failed) runCatching { prefs.edit().putLong(KEY_CHECKED, System.currentTimeMillis()).apply() }
        status = result
        return result
    }

    /** The user asked ("Check for updates"): always answers, with a dialog or a short message */
    fun checkNow() {
        if (status is Status.Checking || status is Status.Downloading) return
        scope.launch {
            when (val s = check()) {
                is Status.Available -> showUpdateDialog(s.release)
                is Status.UpToDate -> Toasts.show("CloudStream $currentVersion is the latest version", false)
                is Status.Failed -> Toasts.show("Could not check for updates: ${s.reason}", true)
                else -> {}
            }
        }
    }

    /** Some seconds after the start: once every few hours at most, and not for a version that was skipped */
    fun startAutoCheck() {
        // checked while the start screen shows (it waits for this, at most a few seconds); an offer is shown once the app is on screen
        scope.launch {
            val found = try {
                if (!autoCheckEnabled || System.currentTimeMillis() - lastChecked < CHECK_EVERY_MS) null
                else kotlinx.coroutines.withTimeoutOrNull(3_000) { check() } as? Status.Available
            } finally {
                com.lagradost.desktop.ui.Startup.updateChecked.complete(Unit)
            }
            if (found == null) return@launch
            if (skippedTag() == found.release.tag) return@launch
            com.lagradost.desktop.ui.Startup.revealed.await()
            delay(1500)
            offeredTag = found.release.tag
            showUpdateDialog(found.release)
        }
        keepCheckingWhileOpen()
    }

    /** The tag of the release the dialog was shown for in this run: it is not shown again until a newer one appears */
    @Volatile private var offeredTag: String? = null

    private fun skippedTag(): String? = runCatching { prefs.getString(KEY_SKIPPED, "") }.getOrNull()

    /**
     * The app is often left open for days, so it asks again while it runs: every few hours (the same limit as at the start), and an
     * offer waits until no video is playing. Each version is offered once per run, and never when it was skipped.
     */
    private fun keepCheckingWhileOpen() {
        scope.launch {
            while (true) {
                delay(30L * 60 * 1000)
                if (!autoCheckEnabled || System.currentTimeMillis() - lastChecked < CHECK_EVERY_MS) continue
                val found = check() as? Status.Available ?: continue
                val tag = found.release.tag
                if (tag == offeredTag || skippedTag() == tag) continue
                // not in the middle of a film
                while (com.lagradost.desktop.core.Navigator.current.route is com.lagradost.desktop.core.Route.Player) delay(60_000)
                offeredTag = tag
                showUpdateDialog(found.release)
            }
        }
    }

    private fun skip(release: Release) {
        prefs.edit().putString(KEY_SKIPPED, release.tag).apply()
    }

    // ---------------------------------------------------------------------------------------------
    // Dialogs
    // ---------------------------------------------------------------------------------------------

    fun showUpdateDialog(release: Release) {
        val canInstall = DesktopPlatform.isWindows && !isPortable && release.installerUrl != null
        Overlays.show(
            Overlays.Dialog(
                title = "Update available",
                primary = if (canInstall) "Install update" else "Open download page",
                onPrimary = { if (canInstall) install(release) else DesktopPlatform.openExternalBrowser(release.pageUrl) },
                secondary = if (canInstall) "Release notes" else null,
                onSecondary = { DesktopPlatform.openExternalBrowser(release.pageUrl) },
                close = "Later",
                width = 520.dp,
            ) { dismiss -> UpdateBody(release, canInstall, dismiss) },
        )
    }

    @Composable
    private fun UpdateBody(release: Release, canInstall: Boolean, dismiss: () -> Unit) {
        val c = Fluent.colors
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FText("CloudStream ${release.label}${if (release.prerelease) " (pre-release)" else ""} is available. You have $currentVersion.")
            if (release.notes.isNotBlank()) {
                Box(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                    FText(release.notes, style = Fluent.type.caption, color = c.textSecondary)
                }
            }
            if (!canInstall) {
                FText(
                    if (isPortable) "This is a portable copy: download the new version and replace this folder, your data folder stays." else "The release has no installer for Windows.",
                    style = Fluent.type.caption, color = c.textSecondary,
                )
            }
            Button("Skip this version", { skip(release); dismiss() }, kind = ButtonKind.Subtle)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Installing
    // ---------------------------------------------------------------------------------------------

    private fun install(release: Release) {
        val url = release.installerUrl ?: return
        val progress = Overlays.Dialog(
            title = "Downloading update", close = "Cancel", width = 460.dp,
            onClose = { downloadJob?.cancel() },
        ) {
            val s = status as? Status.Downloading
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FText("CloudStream ${release.label}", color = Fluent.colors.textSecondary)
                ProgressBar(s?.takeIf { it.total > 0 }?.let { it.done.toFloat() / it.total })
                FText(
                    if (s == null) "Starting…" else "%.1f of %.1f MB".format(s.done / 1_048_576.0, maxOf(s.total, release.installerSize) / 1_048_576.0),
                    style = Fluent.type.caption, color = Fluent.colors.textSecondary,
                )
            }
        }
        Overlays.show(progress)
        downloadJob = scope.launch {
            try {
                status = Status.Downloading(release, 0, release.installerSize)
                val file = download(release, url)
                Overlays.dismiss(progress)
                status = Status.Available(release)
                launchInstaller(file)
            } catch (e: kotlinx.coroutines.CancellationException) {
                status = Status.Available(release)
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "update download failed: ${e.message}")
                Overlays.dismiss(progress)
                status = Status.Failed(e.message ?: "download failed", download = true)
                Toasts.show("Could not download the update: ${e.message}. You can get it from the release page.", true)
                DesktopPlatform.openExternalBrowser(release.pageUrl)
            }
        }
    }

    /** To the temp folder, checked against the size and, when GitHub gives one, the SHA-256 of the release asset */
    private suspend fun download(release: Release, url: String): File = withContext(Dispatchers.IO) {
        val dir = File(System.getProperty("java.io.tmpdir"), "cloudstream-update").also { it.mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, release.installerName ?: "CloudStream-${release.label}.msi")
        val response = app.get(url, cacheTime = 0, timeout = 60)
        if (response.code !in 200..299) error("the server answered ${response.code}")
        val body = response.body
        val total = body.contentLength().takeIf { it > 0 } ?: release.installerSize
        val digest = MessageDigest.getInstance("SHA-256")
        var done = 0L
        var lastReport = 0L
        body.byteStream().use { input ->
            file.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    ensureActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    digest.update(buffer, 0, n)
                    done += n
                    val now = System.currentTimeMillis()
                    if (now - lastReport > 150) { lastReport = now; status = Status.Downloading(release, done, total) }
                }
            }
        }
        if (release.installerSize > 0 && file.length() != release.installerSize) { file.delete(); error("the download is incomplete") }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (release.sha256 != null && actual != release.sha256) { file.delete(); error("the download does not match the checksum of the release") }
        file
    }

    /**
     * Windows Installer replaces the files of the running app, so the app closes first. A small batch file, started apart from
     * the app, runs the installer (a major upgrade of the same product) and starts the app again when it is done.
     */
    private fun launchInstaller(msi: File) {
        val script = File(msi.parentFile, "cloudstream-update.cmd")
        script.writeText(
            "@echo off\r\nstart \"\" /wait msiexec /i \"%CS_UPDATE_MSI%\" /passive /norestart\r\n" +
                "if exist \"%CS_UPDATE_EXE%\" start \"\" \"%CS_UPDATE_EXE%\"\r\n",
        )
        val builder = ProcessBuilder(script.absolutePath)
        builder.environment()["CS_UPDATE_MSI"] = msi.absolutePath
        System.getProperty("jpackage.app-path")?.let { builder.environment()["CS_UPDATE_EXE"] = it }
        builder.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        // the normal shutdown, and the end of the process whatever it does (the installer waits for the files)
        java.awt.EventQueue.invokeLater {
            DesktopUiHost.window?.let { it.dispatchEvent(java.awt.event.WindowEvent(it, java.awt.event.WindowEvent.WINDOW_CLOSING)) }
        }
        Thread { Thread.sleep(10_000); Runtime.getRuntime().halt(0) }.apply { isDaemon = true }.start()
    }
}
