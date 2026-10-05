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
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.desktop.AppInfo
import com.lagradost.desktop.DesktopPlatform
import com.lagradost.desktop.runtime.AndroidRuntime
import com.lagradost.desktop.ui.Startup
import com.lagradost.desktop.ui.Toasts
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.Overlays
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Looks for a newer version of this app in the GitHub releases of its repository and tells the user, with a button to the release page.
 * There is no updater inside the app: nothing is downloaded or installed here, a new version is downloaded from GitHub and run by the user.
 *
 * The check runs a few seconds after the app is on screen (it never holds the start-up back), at most every six hours, and can be turned
 * off in Settings > About. Pre-releases count while the installed version is a pre-release itself (0.x). Nothing but the (public) list of
 * releases is requested, nothing about the user is sent.
 */
object UpdateCheck {
    private const val TAG = "UpdateCheck"

    private const val KEY_AUTO = "auto_update"
    private const val KEY_SKIPPED = "desktop_update_skipped"
    private const val KEY_CHECKED = "desktop_update_checked_at"
    private const val CHECK_EVERY_MS = 6L * 60 * 60 * 1000

    /** `-Dcloudstream.updateApi=` points a test build at another address */
    private val apiUrl: String = System.getProperty("cloudstream.updateApi")?.trim()?.takeIf { it.isNotEmpty() }
        ?: "https://api.github.com/repos/${AppInfo.REPO}/releases?per_page=20"

    val currentVersion: String get() = AppInfo.version

    /** A folder with `portable.txt` or `data` next to the launcher: the new version replaces the folder, not an installation */
    private val isPortable: Boolean by lazy {
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
    private data class GhRelease(
        @JsonProperty("tag_name") val tag: String = "",
        @JsonProperty("body") val body: String? = null,
        @JsonProperty("html_url") val htmlUrl: String = "",
        @JsonProperty("draft") val draft: Boolean = false,
        @JsonProperty("prerelease") val prerelease: Boolean = false,
    )

    class Release(
        val tag: String,
        val version: Version,
        val prerelease: Boolean,
        val notes: String,
        val pageUrl: String,
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
        return Release(r.tag, version, r.prerelease, plainNotes(r.body), r.htmlUrl.ifBlank { AppInfo.RELEASES_URL })
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
    }

    var status: Status by mutableStateOf(Status.Idle)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(AndroidRuntime.context)

    var autoCheckEnabled: Boolean by mutableStateOf(runCatching { prefs.getBoolean(KEY_AUTO, true) }.getOrDefault(true))
        private set

    fun setAutoCheck(value: Boolean) {
        autoCheckEnabled = value
        prefs.edit().putBoolean(KEY_AUTO, value).apply()
    }

    val lastChecked: Long get() = runCatching { prefs.getLong(KEY_CHECKED, 0L) }.getOrDefault(0L)

    private fun skippedTag(): String? = runCatching { prefs.getString(KEY_SKIPPED, "") }.getOrNull()

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

    /** The user asked ("Check now"): always answers, with the dialog or a short message */
    fun checkNow() {
        if (status is Status.Checking) return
        scope.launch {
            when (val s = check()) {
                is Status.Available -> showUpdateDialog(s.release)
                is Status.UpToDate -> Toasts.show("CloudStream $currentVersion is the latest version", false)
                is Status.Failed -> Toasts.show("Could not check for updates: ${s.reason}", true)
                else -> {}
            }
        }
    }

    /**
     * Once the app is on screen and calm: asks GitHub (at most every few hours) and shows the dialog for a new version that was not skipped.
     * Fails silently, a missing connection is not worth a message at start.
     */
    fun startAutoCheck() {
        scope.launch {
            if (!autoCheckEnabled) return@launch
            Startup.revealed.await()
            delay(4000)
            if (System.currentTimeMillis() - lastChecked < CHECK_EVERY_MS) return@launch
            val found = check() as? Status.Available ?: return@launch
            if (skippedTag() == found.release.tag) return@launch
            showUpdateDialog(found.release)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Dialog
    // ---------------------------------------------------------------------------------------------

    fun showUpdateDialog(release: Release) {
        Overlays.show(
            Overlays.Dialog(
                title = "Update available",
                primary = "Open download page",
                onPrimary = { DesktopPlatform.openExternalBrowser(release.pageUrl) },
                close = "Later",
                width = 520.dp,
            ) { dismiss -> UpdateBody(release, dismiss) },
        )
    }

    @Composable
    private fun UpdateBody(release: Release, dismiss: () -> Unit) {
        val c = Fluent.colors
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FText("CloudStream ${release.label}${if (release.prerelease) " (pre-release)" else ""} is available. You have $currentVersion.")
            if (release.notes.isNotBlank()) {
                Box(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                    FText(release.notes, style = Fluent.type.caption, color = c.textSecondary)
                }
            }
            FText(
                if (isPortable) "The app does not update itself. Download the new version from the release page and replace this folder, your data folder stays."
                else "The app does not update itself. Download the installer from the release page and run it.",
                style = Fluent.type.caption, color = c.textSecondary,
            )
            Button("Skip this version", { prefs.edit().putString(KEY_SKIPPED, release.tag).apply(); dismiss() }, kind = ButtonKind.Subtle)
        }
    }
}
