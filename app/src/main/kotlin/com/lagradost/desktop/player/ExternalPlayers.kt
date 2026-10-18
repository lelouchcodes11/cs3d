package com.lagradost.desktop.player

import android.util.Log
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.desktop.DesktopPlatform
import java.io.File

/**
 * Hands a link over to another program: VLC (found through its install registry entry, Program Files or PATH), mpv (the one chosen in Settings > Player,
 * or found in its usual places) or the default browser. Used by the episode menu and the default player setting ([com.lagradost.desktop.player.DesktopVlcAction]) and by the
 * player's "Open in" menu.
 */
object ExternalPlayers {
    private const val TAG = "ExternalPlayers"

    const val VLC_PAGE = "https://www.videolan.org/vlc/"
    const val MPV_PAGE = "https://mpv.io/installation/"

    /** Where an external player can be handed a link: the player menu's "Open in" rows */
    enum class Target { Vlc, Mpv, Browser }

    /** Changes when the mpv chosen in Settings > Player does (the row that shows it reads this) */
    val mpvVersion = androidx.compose.runtime.mutableIntStateOf(0)

    private const val MPV_PATH_KEY = "external_mpv_path_key"

    private fun prefs() = com.lagradost.desktop.runtime.AndroidRuntime.applicationContext?.let { androidx.preference.PreferenceManager.getDefaultSharedPreferences(it) }

    /** The mpv.exe the user chose, or empty */
    fun chosenMpv(): String = runCatching { prefs()?.getString(MPV_PATH_KEY, "") }.getOrNull().orEmpty()

    fun setMpvPath(path: String?) {
        prefs()?.edit()?.putString(MPV_PATH_KEY, path.orEmpty())?.apply()
        mpvVersion.intValue++
    }

    /** The mpv to use: the one chosen in Settings (while it exists), else the first one found where mpv is usually installed; null when there is none */
    fun mpvPath(): File? = chosenMpv().takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile } ?: findMpv()

    /** mpv.exe in the registry's App Paths, on PATH, and where winget, scoop, Chocolatey and the zip builds put it; mpv.net as the last resort */
    fun findMpv(): File? {
        val names = listOf("mpv.exe", "mpvnet.exe")
        val candidates = ArrayList<File>()
        runCatching {
            for (hive in listOf(com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER, com.sun.jna.platform.win32.WinReg.HKEY_LOCAL_MACHINE)) for (exe in names) {
                val key = "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\$exe"
                if (!com.sun.jna.platform.win32.Advapi32Util.registryKeyExists(hive, key)) continue
                runCatching { com.sun.jna.platform.win32.Advapi32Util.registryGetStringValue(hive, key, "") }.getOrNull()?.trim('"')?.takeIf { it.isNotBlank() }?.let { candidates += File(it) }
            }
        }
        System.getenv("PATH")?.split(File.pathSeparator)?.forEach { dir -> if (dir.isNotBlank()) names.forEach { candidates += File(dir.trim('"'), it) } }
        val home = System.getProperty("user.home")
        val local = System.getenv("LOCALAPPDATA")
        val roots = listOfNotNull(
            local?.let { "$it\\Microsoft\\WinGet\\Links" }, local?.let { "$it\\Programs\\mpv" }, local?.let { "$it\\Programs\\mpv.net" },
            home?.let { "$it\\scoop\\shims" }, home?.let { "$it\\scoop\\apps\\mpv\\current" }, home?.let { "$it\\mpv" },
            (System.getenv("ChocolateyInstall") ?: "C:\\ProgramData\\chocolatey") + "\\bin",
            System.getenv("ProgramFiles")?.let { "$it\\mpv" }, System.getenv("ProgramFiles")?.let { "$it\\mpv.net" }, System.getenv("ProgramFiles(x86)")?.let { "$it\\mpv" },
            "C:\\mpv",
        )
        for (root in roots) names.forEach { candidates += File(root, it) }
        return candidates.firstOrNull { it.isFile }
    }

    /** What mpv can play: plain files, HLS and DASH (no protected streams) */
    fun mpvCanPlay(link: ExtractorLink): Boolean =
        (link.type == ExtractorLinkType.VIDEO || link.type == ExtractorLinkType.M3U8 || link.type == ExtractorLinkType.DASH) && link !is com.lagradost.cloudstream3.utils.DrmExtractorLink

    /**
     * The command line that starts mpv on [link]: the headers of the link go to mpv itself (it sends them with every request, segments included), so no
     * helper server is needed as for VLC; every subtitle file is loaded, the title is shown, and it starts at [startSeconds].
     */
    fun mpvCommand(exe: File, link: ExtractorLink, title: String?, subtitleFiles: List<File> = emptyList(), startSeconds: Double? = null): List<String> {
        val ua = link.headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value ?: com.lagradost.cloudstream3.USER_AGENT
        val referer = link.headers.entries.firstOrNull { it.key.equals("Referer", true) }?.value?.takeIf { it.isNotBlank() } ?: link.referer.takeIf { it.isNotBlank() }
        val command = ArrayList<String>()
        command += exe.absolutePath
        command += com.lagradost.desktop.net.UrlFix.encode(link.url)
        command += "--no-terminal"
        // the link is the video: mpv's own hook must not hand it to yt-dlp first (a slow detour that can pick another stream)
        command += "--ytdl=no"
        command += "--user-agent=$ua"
        if (referer != null) command += "--referrer=$referer"
        for ((name, value) in link.headers) {
            if (name.equals("User-Agent", true) || name.equals("Referer", true) || value.isBlank()) continue
            command += "--http-header-fields-append=$name: ${value.replace('\n', ' ')}"
        }
        title?.replace("\"", "'")?.takeIf { it.isNotBlank() }?.let { command += "--force-media-title=$it" }
        for (file in subtitleFiles) command += "--sub-file=${file.absolutePath}"
        if (startSeconds != null && startSeconds > 5) command += "--start=${startSeconds.toInt()}"
        return command
    }

    /** Starts mpv with [link] (see [mpvCommand]); [exe] is the mpv to use when it is not the chosen or found one */
    fun openInMpv(link: ExtractorLink, title: String?, subtitles: List<SubtitleData> = emptyList(), startSeconds: Double? = null, exe: File? = mpvPath(), extraArgs: List<String> = emptyList()): Outcome {
        val mpv = exe ?: return Outcome(false, "mpv is not installed")
        if (!mpvCanPlay(link)) return Outcome(false, "mpv can not play this kind of link (protected streams). Use the in-app player.")
        val command = mpvCommand(mpv, link, title, subtitleFiles(subtitles, 4), startSeconds) + extraArgs
        return try {
            Log.i(TAG, "mpv: ${command.drop(1).joinToString(" ").take(400)}")
            ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            Outcome(true)
        } catch (t: Throwable) {
            Log.w(TAG, "mpv did not start: $t")
            Outcome(false, "mpv did not start: ${t.message}")
        }
    }


    /** The full path of vlc.exe, or null when VLC is not installed */
    fun vlcPath(): File? {
        val candidates = ArrayList<File>()
        runCatching {
            val hives = listOf(com.sun.jna.platform.win32.WinReg.HKEY_LOCAL_MACHINE, com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER)
            for (hive in hives) for (key in listOf("SOFTWARE\\VideoLAN\\VLC", "SOFTWARE\\WOW6432Node\\VideoLAN\\VLC")) {
                if (!com.sun.jna.platform.win32.Advapi32Util.registryKeyExists(hive, key)) continue
                val dir = runCatching { com.sun.jna.platform.win32.Advapi32Util.registryGetStringValue(hive, key, "InstallDir") }.getOrNull()
                if (!dir.isNullOrBlank()) candidates += File(dir, "vlc.exe")
                val exe = runCatching { com.sun.jna.platform.win32.Advapi32Util.registryGetStringValue(hive, key, "") }.getOrNull()
                if (!exe.isNullOrBlank()) candidates += File(exe)
            }
        }
        for (env in listOf("ProgramFiles", "ProgramFiles(x86)", "ProgramW6432", "LOCALAPPDATA")) {
            System.getenv(env)?.let { candidates += File(it, "VideoLAN\\VLC\\vlc.exe"); candidates += File(it, "Programs\\VideoLAN\\VLC\\vlc.exe") }
        }
        System.getenv("PATH")?.split(File.pathSeparator)?.forEach { candidates += File(it, "vlc.exe") }
        return candidates.firstOrNull { it.isFile }
    }

    /** What VLC can play: plain files and HLS (no DASH, no DRM) */
    fun vlcCanPlay(link: ExtractorLink): Boolean =
        (link.type == ExtractorLinkType.VIDEO || link.type == ExtractorLinkType.M3U8) && link !is com.lagradost.cloudstream3.utils.DrmExtractorLink

    class Outcome(val ok: Boolean, val message: String? = null)

    /**
     * Starts VLC with [link] (its referer and user agent as VLC options; other headers through the app's own header-adding
     * servers) and, when given, the first of [subtitles] that downloads.
     */
    fun openInVlc(link: ExtractorLink, title: String?, subtitles: List<SubtitleData> = emptyList(), startSeconds: Double? = null, extraArgs: List<String> = emptyList()): Outcome {
        val vlc = vlcPath() ?: return Outcome(false, "VLC is not installed")
        if (!vlcCanPlay(link)) return Outcome(false, "VLC can not play this kind of link (DASH or protected streams). Use the in-app player.")
        val ua = link.headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value ?: com.lagradost.cloudstream3.USER_AGENT
        val referer = link.headers.entries.firstOrNull { it.key.equals("Referer", true) }?.value?.takeIf { it.isNotBlank() } ?: link.referer.takeIf { it.isNotBlank() }
        val others = link.headers.filterKeys { !it.equals("User-Agent", true) && !it.equals("Referer", true) }
        var address = com.lagradost.desktop.net.UrlFix.encode(link.url)
        if (others.isNotEmpty()) {
            val all = buildMap { put("User-Agent", ua); putAll(link.headers); if (referer != null) put("Referer", referer) }
            address = if (link.type == ExtractorLinkType.M3U8) HlsProxy.wrap(address, all) else RangeProxy.wrap(address, all)
        }
        val command = ArrayList<String>()
        command += vlc.absolutePath
        command += address
        command += "--http-user-agent=$ua"
        if (referer != null) command += "--http-referrer=$referer"
        title?.replace("\"", "'")?.takeIf { it.isNotBlank() }?.let { command += "--meta-title=$it" }
        subtitleFile(subtitles)?.let { command += "--sub-file=${it.absolutePath}" }
        if (startSeconds != null && startSeconds > 5) command += "--start-time=${startSeconds.toInt()}"
        command += extraArgs
        return try {
            Log.i(TAG, "VLC: ${command.drop(1).joinToString(" ").take(300)}")
            ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            Outcome(true)
        } catch (t: Throwable) {
            Log.w(TAG, "VLC did not start: $t")
            Outcome(false, "VLC did not start: ${t.message}")
        }
    }

    fun openInBrowser(link: ExtractorLink): Outcome =
        if (DesktopPlatform.openExternalBrowser(link.url)) Outcome(true) else Outcome(false, "The browser could not be opened")

    /** A subtitle VLC can load (it takes local files): the first one that downloads, preferring English */
    private fun subtitleFile(subtitles: List<SubtitleData>): File? = subtitleFiles(subtitles, 1).firstOrNull()

    /** Up to [max] subtitle files that download (local files, English first): what the other player is given with the video */
    private fun subtitleFiles(subtitles: List<SubtitleData>, max: Int): List<File> {
        val ordered = subtitles.filter { it.origin != com.lagradost.cloudstream3.ui.player.SubtitleOrigin.EMBEDDED_IN_VIDEO }
            .sortedByDescending { (it.languageCode ?: "").startsWith("en", true) || it.originalName.contains("english", true) }
        val found = ArrayList<File>()
        for (sub in ordered.take(max + 4)) {
            if (found.size >= max) break
            val file = runCatching {
                if (sub.url.startsWith("file:")) return@runCatching File(java.net.URI(sub.url))
                val bytes = kotlinx.coroutines.runBlocking { com.lagradost.cloudstream3.app.get(sub.url, headers = sub.headers, timeout = 12).okhttpResponse.body.bytes() }
                val head = String(bytes, 0, minOf(bytes.size, 200), Charsets.UTF_8).trimStart()
                if (bytes.isEmpty() || head.startsWith("<")) return@runCatching null
                val ext = when { sub.mimeType.contains("vtt") -> ".vtt"; sub.mimeType.contains("ass") || sub.mimeType.contains("ssa") -> ".ass"; else -> ".srt" }
                File.createTempFile("subtitle-", ext).apply { deleteOnExit(); writeBytes(bytes) }
            }.getOrNull()
            if (file != null && file !in found) found += file
        }
        return found
    }
}

/** The "VLC" entry of the player lists (episode menu, default player setting) */
class DesktopVlcAction : com.lagradost.cloudstream3.actions.VideoClickAction() {
    override val name = com.lagradost.cloudstream3.utils.txt(com.lagradost.cloudstream3.R.string.episode_action_play_in_format, "VLC")

    /** one link at a time: the app asks which source */
    override val oneSource = true

    override val isPlayer = true

    override val sourceTypes: Set<ExtractorLinkType> = setOf(ExtractorLinkType.VIDEO, ExtractorLinkType.M3U8)

    override fun shouldShow(context: android.content.Context?, video: com.lagradost.cloudstream3.ui.result.ResultEpisode?) = true

    override suspend fun runAction(
        context: android.content.Context?,
        video: com.lagradost.cloudstream3.ui.result.ResultEpisode,
        result: com.lagradost.cloudstream3.ui.result.LinkLoadingResult,
        index: Int?,
    ) {
        val link = result.links.getOrNull(index ?: 0) ?: return
        val outcome = ExternalPlayers.openInVlc(link, video.name ?: video.headerName, result.subs)
        if (!outcome.ok) {
            if (ExternalPlayers.vlcPath() == null) com.lagradost.desktop.ui.ExternalPlayerHints.vlcMissing()
            else throw com.lagradost.cloudstream3.ErrorLoadingException(outcome.message ?: "VLC did not start")
        }
    }
}

/** The "mpv" entry of the player lists (episode menu, default player setting): the video goes to mpv.exe, which plays it in a window of its own */
class DesktopMpvAction : com.lagradost.cloudstream3.actions.VideoClickAction() {
    override val name = com.lagradost.cloudstream3.utils.txt(com.lagradost.cloudstream3.R.string.episode_action_play_in_format, "mpv")

    /** one link at a time: the app asks which source */
    override val oneSource = true

    override val isPlayer = true

    override val sourceTypes: Set<ExtractorLinkType> = setOf(ExtractorLinkType.VIDEO, ExtractorLinkType.M3U8, ExtractorLinkType.DASH)

    override fun shouldShow(context: android.content.Context?, video: com.lagradost.cloudstream3.ui.result.ResultEpisode?) = true

    override suspend fun runAction(
        context: android.content.Context?,
        video: com.lagradost.cloudstream3.ui.result.ResultEpisode,
        result: com.lagradost.cloudstream3.ui.result.LinkLoadingResult,
        index: Int?,
    ) {
        val link = result.links.getOrNull(index ?: 0) ?: return
        val outcome = ExternalPlayers.openInMpv(link, video.name ?: video.headerName, result.subs)
        if (!outcome.ok) {
            if (ExternalPlayers.mpvPath() == null) com.lagradost.desktop.ui.ExternalPlayerHints.mpvMissing()
            else throw com.lagradost.cloudstream3.ErrorLoadingException(outcome.message ?: "mpv did not start")
        }
    }
}
