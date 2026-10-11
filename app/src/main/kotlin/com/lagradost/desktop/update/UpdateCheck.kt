package com.lagradost.desktop.update

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.desktop.AppInfo
import com.lagradost.desktop.DesktopPlatform
import com.lagradost.desktop.runtime.AndroidRuntime
import com.lagradost.desktop.ui.DesktopUiHost
import com.lagradost.desktop.ui.Startup
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
 * The updater: looks for a newer version in the GitHub releases of this app's repository, downloads the installer that fits the way this copy
 * was installed, checks it (size and the SHA-256 GitHub states for the file), and, when the user says so, closes the app, runs the installer
 * and opens the app again. Nothing is installed without that click, and the data (settings, accounts, extensions, history) is outside the
 * program folder, so no installer touches it.
 *
 *  - installed with the setup program (`-setup.exe`, Inno Setup): that setup, run silently with its progress window;
 *  - installed from the MSI: that MSI, run with its progress window;
 *  - a portable folder (data next to the exe): the portable zip, unpacked over the folder with `data` left alone;
 *  - anything else (a development run, a release without the matching file): the release page.
 *
 * The check runs a few seconds after the app is on screen, then again every few hours while it stays open (never during a video), can be
 * turned off in Settings > About, and sends nothing but the request for the public list of releases. Pre-releases count while the
 * installed version is a pre-release itself (0.x).
 */
object UpdateCheck {
    private const val TAG = "UpdateCheck"

    private const val KEY_AUTO = "auto_update"
    private const val KEY_SKIPPED = "desktop_update_skipped"
    private const val KEY_CHECKED = "desktop_update_checked_at"
    private const val KEY_LAST_VERSION = "desktop_update_last_version"
    private const val CHECK_EVERY_MS = 6L * 60 * 60 * 1000

    /** `-Dcloudstream.updateApi=` points a test build at another address (its downloads may then come from anywhere too) */
    private val apiOverride: String? = System.getProperty("cloudstream.updateApi")?.trim()?.takeIf { it.isNotEmpty() }
    private val apiUrl: String = apiOverride ?: "https://api.github.com/repos/${AppInfo.REPO}/releases?per_page=20"

    val currentVersion: String get() = AppInfo.version

    // ---------------------------------------------------------------------------------------------
    // How this copy was installed
    // ---------------------------------------------------------------------------------------------

    enum class InstallKind { Setup, Msi, Portable, Other }

    /** The folder of the launcher (`CloudStream.exe`), null when the app does not run from a packaged copy */
    private val launcherDir: File? by lazy {
        System.getProperty("jpackage.app-path")?.let { File(it).absoluteFile.takeIf { f -> f.isFile }?.parentFile }
    }

    val installKind: InstallKind by lazy {
        val dir = launcherDir ?: return@lazy InstallKind.Other
        when {
            // Inno Setup puts its uninstaller beside the program
            File(dir, "unins000.exe").isFile -> InstallKind.Setup
            File(dir, "portable.txt").exists() || File(dir, "data").isDirectory -> InstallKind.Portable
            // the MSI's place (the default folder, or the one the registry says): a copy of the program folder somewhere else is not an installation
            installedByMsi(dir) -> InstallKind.Msi
            else -> InstallKind.Other
        }
    }

    /** Whether [dir] is where the MSI put the program: its default folder, or the folder a registered CloudStream product points into */
    private fun installedByMsi(dir: File): Boolean {
        val here = runCatching { dir.canonicalFile }.getOrNull() ?: return false
        fun same(x: String?): Boolean {
            val text = x?.trim()?.trim('"')?.trimEnd('\\', '/') ?: return false
            return runCatching { File(text).canonicalFile == here }.getOrDefault(false)
        }
        val local = System.getenv("LOCALAPPDATA")
        if (local != null && same(local + "\\CloudStream")) return true
        return runCatching {
            val root = com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER
            val base = "Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall"
            com.sun.jna.platform.win32.Advapi32Util.registryGetKeys(root, base).any { key ->
                val path = base + "\\" + key
                val name = runCatching { com.sun.jna.platform.win32.Advapi32Util.registryGetStringValue(root, path, "DisplayName") }.getOrNull()
                val windowsInstaller = runCatching { com.sun.jna.platform.win32.Advapi32Util.registryGetIntValue(root, path, "WindowsInstaller") }.getOrNull() == 1
                if (!windowsInstaller || name == null || !name.equals("CloudStream", ignoreCase = true)) {
                    false
                } else {
                    val location = runCatching { com.sun.jna.platform.win32.Advapi32Util.registryGetStringValue(root, path, "InstallLocation") }.getOrNull()
                    val icon = runCatching { com.sun.jna.platform.win32.Advapi32Util.registryGetStringValue(root, path, "DisplayIcon") }.getOrNull()
                        ?.substringBefore(',')?.trim()?.trim('"')
                    same(location) || same(icon?.let { File(it).parent })
                }
            }
        }.getOrDefault(false)
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

    /** A file of a release: [sha256] is what GitHub states for it (null for a file uploaded before GitHub did) */
    class Asset(val name: String, val url: String, val size: Long, val sha256: String?)

    class Release(
        val tag: String,
        val version: Version,
        val prerelease: Boolean,
        val notes: String,
        val pageUrl: String,
        val assets: List<Asset>,
    ) {
        val label: String get() = tag.trimStart('v', 'V')

        /** The file that updates a copy installed in the given way, null when the release has none for it */
        fun installerFor(kind: InstallKind): Asset? = when (kind) {
            InstallKind.Setup -> assets.firstOrNull { it.name.endsWith("-setup.exe", ignoreCase = true) }
            InstallKind.Msi -> assets.firstOrNull { it.name.endsWith(".msi", ignoreCase = true) }
            InstallKind.Portable -> assets.firstOrNull { it.name.contains("portable", ignoreCase = true) && it.name.endsWith(".zip", ignoreCase = true) }
            InstallKind.Other -> null
        }
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
        val assets = r.assets.filter { it.url.isNotBlank() && it.name.isNotBlank() }.map {
            Asset(it.name, it.url, it.size, it.digest?.takeIf { d -> d.startsWith("sha256:", ignoreCase = true) }?.substringAfter(':')?.trim()?.lowercase())
        }
        return Release(r.tag, version, r.prerelease, plainNotes(r.body), r.htmlUrl.ifBlank { AppInfo.RELEASES_URL }, assets)
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
        class Failed(val reason: String) : Status
        class Downloading(val release: Release, val done: Long, val total: Long) : Status
        /** Downloaded and checked: the next step is the click that closes the app and installs it */
        class Ready(val release: Release, val file: File) : Status
        object Installing : Status
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

    private fun skippedTag(): String? = runCatching { prefs.getString(KEY_SKIPPED, "") }.getOrNull()

    /** True when "Update now" can do the whole job for this copy of the app and this release */
    fun canInstall(release: Release): Boolean = release.installerFor(installKind) != null && DesktopPlatform.isWindows && installKind != InstallKind.Other

    // ---------------------------------------------------------------------------------------------
    // Checking
    // ---------------------------------------------------------------------------------------------

    /** Asks GitHub; the result is also the new [status] */
    suspend fun check(): Status {
        // a download or an update that is under way, or a finished one that waits for its click, is not replaced by a new answer
        val before = status
        if (before is Status.Downloading || before is Status.Ready || before is Status.Installing) return before
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

    /** The user asked ("Check now"): always answers, with the dialog or a short message */
    fun checkNow() {
        if (status is Status.Checking || status is Status.Downloading || status is Status.Installing) return
        scope.launch {
            when (val s = check()) {
                is Status.Available -> showUpdateDialog(s.release)
                is Status.Ready -> showReadyDialog(s.release, s.file)
                is Status.UpToDate -> Toasts.show("CloudStream $currentVersion is the latest version", false)
                is Status.Failed -> Toasts.show("Could not check for updates: ${s.reason}", true)
                else -> {}
            }
        }
    }

    /** The tag of the release the dialog was shown for in this run: it is not shown again until a newer one appears */
    @Volatile private var offeredTag: String? = null

    /**
     * Once the app is on screen and calm: asks GitHub (at most every few hours) and shows the dialog for a new version that was not skipped; then
     * again every half hour while the app stays open (the same limit applies, and nothing is shown during a video).
     * Fails silently, a missing connection is not worth a message at start.
     */
    fun startAutoCheck() {
        scope.launch {
            Startup.revealed.await()
            announceUpdated()
            delay(4000)
            while (true) {
                if (autoCheckEnabled && System.currentTimeMillis() - lastChecked >= CHECK_EVERY_MS) {
                    val found = check() as? Status.Available
                    if (found != null && skippedTag() != found.release.tag && offeredTag != found.release.tag) {
                        // not in the middle of a film, nor while the first-run setup is open
                        while (com.lagradost.desktop.core.Navigator.current.route.let { it is com.lagradost.desktop.core.Route.Player || it is com.lagradost.desktop.core.Route.Setup }) delay(30_000)
                        offeredTag = found.release.tag
                        showUpdateDialog(found.release)
                    }
                }
                delay(30L * 60 * 1000)
            }
        }
    }

    /** The first start of a new version after an update: a short message, once */
    private fun announceUpdated() {
        runCatching {
            val before = prefs.getString(KEY_LAST_VERSION, "").orEmpty()
            if (before != currentVersion) {
                prefs.edit().putString(KEY_LAST_VERSION, currentVersion).apply()
                val was = parseVersion(before)
                val now = parseVersion(currentVersion)
                if (before.isNotBlank() && was != null && now != null && now > was) Toasts.show("CloudStream was updated to $currentVersion", false)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Dialogs
    // ---------------------------------------------------------------------------------------------

    /** Opens the matching installer/portable asset URL directly in the user's browser, or the GitHub release page if no matching asset exists */
    fun downloadInBrowser(release: Release) {
        val asset = release.installerFor(installKind)?.takeIf { trusted(it.url) }
        DesktopPlatform.openExternalBrowser(asset?.url ?: release.pageUrl)
    }

    fun showUpdateDialog(release: Release) {
        // a download that runs shows its progress instead; a finished one asks for the restart
        when (val s = status) {
            is Status.Downloading -> { showProgressDialog(s.release); return }
            is Status.Ready -> { showReadyDialog(s.release, s.file); return }
            else -> {}
        }
        val canInstall = canInstall(release)
        Overlays.show(
            Overlays.Dialog(
                title = "Update available",
                primary = if (canInstall) "Update now" else "Download in browser",
                onPrimary = { if (canInstall) startUpdate(release) else downloadInBrowser(release) },
                secondary = if (canInstall) "Download in browser" else "Release page",
                onSecondary = { if (canInstall) downloadInBrowser(release) else DesktopPlatform.openExternalBrowser(release.pageUrl) },
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
            val size = release.installerFor(installKind)?.size?.takeIf { it > 0 }
            FText(
                when {
                    canInstall -> "Update now downloads and verifies the update in the app${size?.let { " (%.0f MB)".format(it / 1_048_576.0) } ?: ""}, or choose Download in browser to get the installer directly in your browser. Your settings, accounts and extensions stay."
                    installKind == InstallKind.Other -> "This is a development run: open the release page to get the new version."
                    installKind == InstallKind.Portable -> "This is a portable copy and the release has no portable file for it: download it from the release page and replace the folder, the data folder stays."
                    else -> "The release has no installer for this copy: download it from the release page."
                },
                style = Fluent.type.caption, color = c.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button("Release page on GitHub", { DesktopPlatform.openExternalBrowser(release.pageUrl) }, kind = ButtonKind.Subtle)
                Button("Skip this version", { prefs.edit().putString(KEY_SKIPPED, release.tag).apply(); dismiss() }, kind = ButtonKind.Subtle)
            }
        }
    }

    private fun showProgressDialog(release: Release): Overlays.Dialog {
        val dialog = Overlays.Dialog(
            title = "Downloading update",
            primary = "Hide",
            secondary = "Download in browser",
            onSecondary = { cancelDownload(); downloadInBrowser(release) },
            close = "Cancel",
            width = 480.dp,
            onClose = { cancelDownload() },
        ) {
            val s = status as? Status.Downloading
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FText("CloudStream ${release.label}", color = Fluent.colors.textSecondary)
                ProgressBar(s?.takeIf { it.total > 0 }?.let { (it.done.toFloat() / it.total).coerceIn(0f, 1f) })
                FText(
                    if (s == null) "Starting…" else "%.1f of %.1f MB".format(s.done / 1_048_576.0, maxOf(s.total, 1L) / 1_048_576.0),
                    style = Fluent.type.caption, color = Fluent.colors.textSecondary,
                )
                FText("You can hide this and keep using the app; it asks again when the update is ready.", style = Fluent.type.caption, color = Fluent.colors.textSecondary)
            }
        }
        progressDialog = dialog
        Overlays.show(dialog)
        return dialog
    }

    @Volatile private var progressDialog: Overlays.Dialog? = null

    fun showReadyDialog(release: Release, file: File) {
        Overlays.show(
            Overlays.Dialog(
                title = "Update ready",
                primary = "Restart and update", onPrimary = { install(release, file) },
                secondary = "Download in browser", onSecondary = { downloadInBrowser(release) },
                close = "Later", width = 480.dp,
            ) {
                FText(
                    "CloudStream ${release.label} is downloaded and checked. The app closes now, installs it and opens again in a moment. Your settings, accounts and extensions stay.",
                    color = Fluent.colors.textSecondary,
                )
            },
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Downloading
    // ---------------------------------------------------------------------------------------------

    private fun updateDir(): File = File(System.getProperty("java.io.tmpdir"), "cloudstream-update").also { it.mkdirs() }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    /** A file that is there already and is the one the release states (a download that was finished earlier) */
    private fun verified(file: File, asset: Asset): Boolean {
        if (!file.isFile || (asset.size > 0 && file.length() != asset.size)) return false
        val expected = asset.sha256 ?: return false
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input -> val buffer = ByteArray(256 * 1024); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
            hex(digest.digest()) == expected
        }.getOrDefault(false)
    }

    /** Only the repository's own release files are downloaded (a test build with its own update address may use any) */
    private fun trusted(url: String): Boolean =
        apiOverride != null || url.startsWith("https://github.com/${AppInfo.REPO}/releases/download/", ignoreCase = true)

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    /** "Update now": downloads the installer (the dialog can be hidden), checks it, then asks for the restart */
    fun startUpdate(release: Release) {
        val asset = release.installerFor(installKind)
        if (asset == null || !trusted(asset.url)) {
            DesktopPlatform.openExternalBrowser(release.pageUrl)
            return
        }
        if (status is Status.Downloading || status is Status.Installing) return
        val progress = showProgressDialog(release)
        downloadJob = scope.launch {
            try {
                status = Status.Downloading(release, 0, asset.size)
                val file = download(release, asset)
                status = Status.Ready(release, file)
                Overlays.dismiss(progress)
                // not over a film: the dialog waits until the page is left, the Settings card has the button meanwhile
                if (com.lagradost.desktop.core.Navigator.current.route is com.lagradost.desktop.core.Route.Player) {
                    Toasts.show("CloudStream ${release.label} is downloaded: restart to update from Settings > About", false)
                } else showReadyDialog(release, file)
            } catch (e: kotlinx.coroutines.CancellationException) {
                status = Status.Available(release)
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "update download failed: ${e.message}")
                Overlays.dismiss(progress)
                status = Status.Available(release)
                Toasts.show("Could not download the update: ${e.message}", true)
            }
        }
    }

    /** To the temp folder, checked against the size and, when GitHub states one, the SHA-256 of the file */
    private suspend fun download(release: Release, asset: Asset): File = withContext(Dispatchers.IO) {
        val dir = updateDir()
        val file = File(dir, asset.name.replace(Regex("""[\\/:*?"<>|]"""), "_"))
        // the folder holds only the file that is being updated to; an earlier one is removed
        dir.listFiles()?.filter { it.isFile && it.name != file.name }?.forEach { it.delete() }
        if (verified(file, asset)) {
            status = Status.Downloading(release, file.length(), file.length())
            return@withContext file
        }
        val part = File(dir, file.name + ".part")
        val response = app.get(asset.url, cacheTime = 0, timeout = 60)
        if (response.code !in 200..299) error("the server answered ${response.code}")
        val body = response.body
        val total = body.contentLength().takeIf { it > 0 } ?: asset.size
        val digest = MessageDigest.getInstance("SHA-256")
        var done = 0L
        var lastReport = 0L
        try {
            body.byteStream().use { input ->
                part.outputStream().use { output ->
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
        } catch (t: Throwable) {
            part.delete()
            throw t
        }
        if (asset.size > 0 && part.length() != asset.size) { part.delete(); error("the download is incomplete") }
        if (asset.sha256 != null && hex(digest.digest()) != asset.sha256) { part.delete(); error("the download does not match the checksum of the release") }
        file.delete()
        if (!part.renameTo(file)) { part.copyTo(file, overwrite = true); part.delete() }
        file
    }

    // ---------------------------------------------------------------------------------------------
    // Installing
    // ---------------------------------------------------------------------------------------------

    /**
     * The app has to be gone before the installer replaces its files, so a small script runs apart from it (no window): it waits for this
     * process to end, runs the installer, and starts the app again whatever the installer did. The app closes the normal way (its data is
     * written first); should it hang, it is ended after ten seconds.
     */
    private fun install(release: Release, file: File) {
        val kind = installKind
        val launcher = launcherDir?.let { File(it, "CloudStream.exe") }
        val launcherPath = System.getProperty("jpackage.app-path")?.takeIf { File(it).isFile }
        if (kind == InstallKind.Other || launcherPath == null || !file.isFile) {
            DesktopPlatform.openExternalBrowser(release.pageUrl)
            return
        }
        val dir = updateDir()
        val log = File(dir, "update.log")
        fun cmdPath(f: String) = f.replace("%", "%%")
        val script = StringBuilder()
        script.append("@echo off\r\nsetlocal\r\n")
        script.append("set \"CS_LOG=${cmdPath(log.absolutePath)}\"\r\n")
        script.append("echo %DATE% %TIME% waiting for the app (pid ${ProcessHandle.current().pid()}) > \"%CS_LOG%\"\r\n")
        script.append("set /a CS_N=0\r\n:wait\r\n")
        script.append("tasklist /FI \"PID eq ${ProcessHandle.current().pid()}\" /NH /FO CSV 2>NUL | find \",\"\"${ProcessHandle.current().pid()}\"\",\" >NUL\r\n")
        script.append("if errorlevel 1 goto go\r\nset /a CS_N+=1\r\nif %CS_N% GEQ 60 goto go\r\nping -n 2 127.0.0.1 >NUL\r\ngoto wait\r\n:go\r\n")
        // a moment for the files of the old process to be released
        script.append("ping -n 2 127.0.0.1 >NUL\r\n")
        when (kind) {
            InstallKind.Setup -> script.append(
                "start \"\" /wait \"${cmdPath(file.absolutePath)}\" /SILENT /SUPPRESSMSGBOXES /NORESTART /CLOSEAPPLICATIONS /NOCANCEL /LOG=\"${cmdPath(File(dir, "setup.log").absolutePath)}\"\r\n",
            )
            InstallKind.Msi -> script.append(
                "start \"\" /wait msiexec /i \"${cmdPath(file.absolutePath)}\" /passive /norestart /l*v \"${cmdPath(File(dir, "msi.log").absolutePath)}\"\r\n",
            )
            InstallKind.Portable -> {
                val target = launcher?.parentFile ?: run { DesktopPlatform.openExternalBrowser(release.pageUrl); return }
                // the program's own folders are replaced, nothing else; the data folder is never touched
                if (!File(target, "app").isDirectory || !File(target, "runtime").isDirectory) { DesktopPlatform.openExternalBrowser(release.pageUrl); return }
                val tar = File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32\\tar.exe")
                if (!tar.isFile) { DesktopPlatform.openExternalBrowser(release.pageUrl); return }
                val extract = File(dir, "extract")
                script.append("rmdir /s /q \"${cmdPath(extract.absolutePath)}\" 2>NUL\r\nmkdir \"${cmdPath(extract.absolutePath)}\"\r\n")
                script.append("\"${tar.absolutePath}\" -xf \"${cmdPath(file.absolutePath)}\" -C \"${cmdPath(extract.absolutePath)}\"\r\n")
                script.append("set \"CS_SRC=\"\r\n")
                script.append("if exist \"${cmdPath(extract.absolutePath)}\\CloudStream.exe\" set \"CS_SRC=${cmdPath(extract.absolutePath)}\"\r\n")
                script.append("if not defined CS_SRC for /d %%D in (\"${cmdPath(extract.absolutePath)}\\*\") do if exist \"%%D\\CloudStream.exe\" set \"CS_SRC=%%D\"\r\n")
                script.append("if not defined CS_SRC (echo no CloudStream.exe in the zip >> \"%CS_LOG%\" & goto restart)\r\n")
                script.append("rmdir /s /q \"${cmdPath(File(target, "app").absolutePath)}\"\r\nrmdir /s /q \"${cmdPath(File(target, "runtime").absolutePath)}\"\r\n")
                script.append("robocopy \"%CS_SRC%\" \"${cmdPath(target.absolutePath)}\" /E /XD data /R:3 /W:1 /NFL /NDL /NJH /NJS >> \"%CS_LOG%\"\r\n")
                script.append("rmdir /s /q \"${cmdPath(extract.absolutePath)}\" 2>NUL\r\n")
            }
            InstallKind.Other -> return
        }
        script.append("echo %DATE% %TIME% installer finished, exit code %ERRORLEVEL% >> \"%CS_LOG%\"\r\n")
        script.append(":restart\r\nstart \"\" \"${cmdPath(launcherPath)}\"\r\n")
        val scriptFile = File(dir, "cloudstream-update.cmd")
        scriptFile.writeText(script.toString())

        status = Status.Installing
        if (!startHidden(scriptFile, dir)) {
            status = Status.Ready(release, file)
            Toasts.show("The update could not be started. Open the release page to install it by hand.", true)
            DesktopPlatform.openExternalBrowser(release.pageUrl)
            return
        }
        // the normal shutdown (settings are written), and the end of the process whatever it does: the installer waits for it
        java.awt.EventQueue.invokeLater {
            DesktopUiHost.window?.let { it.dispatchEvent(java.awt.event.WindowEvent(it, java.awt.event.WindowEvent.WINDOW_CLOSING)) }
        }
        Thread { Thread.sleep(10_000); Runtime.getRuntime().halt(0) }.apply { isDaemon = true }.start()
    }

    /** Starts `cmd /c script` with no console window, apart from this process (it goes on when the app ends) */
    private fun startHidden(script: File, workDir: File): Boolean {
        val command = "cmd.exe /d /c \"\"${script.absolutePath}\"\""
        val kernel = runCatching { com.sun.jna.platform.win32.Kernel32.INSTANCE }.getOrNull()
        if (kernel != null) {
            val CREATE_NO_WINDOW = 0x08000000L
            val CREATE_BREAKAWAY_FROM_JOB = 0x01000000L
            for (flags in listOf(CREATE_NO_WINDOW or CREATE_BREAKAWAY_FROM_JOB, CREATE_NO_WINDOW)) {
                val si = com.sun.jna.platform.win32.WinBase.STARTUPINFO()
                val pi = com.sun.jna.platform.win32.WinBase.PROCESS_INFORMATION()
                val ok = runCatching {
                    kernel.CreateProcess(null, command, null, null, false, com.sun.jna.platform.win32.WinDef.DWORD(flags), null, workDir.absolutePath, si, pi)
                }.getOrDefault(false)
                if (ok) {
                    runCatching { kernel.CloseHandle(pi.hProcess); kernel.CloseHandle(pi.hThread) }
                    return true
                }
            }
        }
        // no way to hide the window: it shows while the update runs
        return runCatching {
            ProcessBuilder("cmd.exe", "/d", "/c", script.absolutePath).directory(workDir)
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            true
        }.getOrDefault(false)
    }
}
