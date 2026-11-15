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
    enum class Target { Vlc, Mpv, Energy, Browser }

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

    // ------------------------------------------------------------------ Energy Media Player (Microsoft Store)

    /** The Store page of Energy Media Player (DimitrovDev) */
    const val ENERGY_STORE = "ms-windows-store://pdp/?productid=9P9ZH5FL1BFK"
    const val ENERGY_FAMILY = "6615IvanDimitrov89.EnergyPlayer_1gdjqnh0agk8c"
    private const val ENERGY_AUMID = "$ENERGY_FAMILY!App"

    /**
     * Whether Energy is installed: a Store app has no exe to find, but installing it puts its command alias in WindowsApps. (The alias itself is not
     * used to open videos: Energy ignores a link given on its command line and reopens the last thing it played.)
     */
    fun energyInstalled(): Boolean = DesktopPlatform.isWindows && System.getenv("LOCALAPPDATA")?.let { local ->
        // the alias is a special link Java does not see as a file (exists() is false): it is looked for in the folder listing
        File(File(local, "Microsoft"), "WindowsApps").list()?.any { it.equals("EnergyPlayer.exe", true) } == true || File(File(local, "Packages"), ENERGY_FAMILY).isDirectory
    } == true

    /** What Energy can play: plain files and HLS (no DASH, no protected streams) */
    fun energyCanPlay(link: ExtractorLink): Boolean =
        (link.type == ExtractorLinkType.VIDEO || link.type == ExtractorLinkType.M3U8) && link !is com.lagradost.cloudstream3.utils.DrmExtractorLink

    /** Whether the link needs headers sent with it (anything but the default user agent): Energy can not send any, so the app's proxy must */
    fun needsHeaders(link: ExtractorLink): Boolean =
        link.referer.isNotBlank() || link.headers.any { (k, v) -> v.isNotBlank() && !(k.equals("User-Agent", true) && v == com.lagradost.cloudstream3.USER_AGENT) }

    /**
     * Whether Windows lets Energy reach this PC's own addresses (127.0.0.1, where the app's header-adding servers listen). Store apps are kept off
     * them unless exempted ("CheckNetIsolation LoopbackExempt"); reading the list needs no admin.
     */
    fun energyLoopbackAllowed(): Boolean = runCatching {
        val p = ProcessBuilder("CheckNetIsolation.exe", "LoopbackExempt", "-s").redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
        out.contains(ENERGY_FAMILY, ignoreCase = true)
    }.getOrDefault(false)

    /** Asks Windows (an admin prompt the person answers) to let Energy reach the app's servers on this PC; true when the prompt was shown */
    fun allowEnergyLoopback(): Boolean = runCatching {
        val r = com.sun.jna.platform.win32.Shell32.INSTANCE.ShellExecute(null, "runas", "CheckNetIsolation.exe", "LoopbackExempt -a -n=$ENERGY_FAMILY", null, 0)
        r.toLong() > 32
    }.getOrDefault(false)

    /** [Outcome.message] when the link needs headers and Windows keeps Energy off the app's servers ([com.lagradost.desktop.ui.ExternalPlayerHints.energyNeedsLoopback]) */
    const val ENERGY_LOOPBACK = "energy-loopback"

    /**
     * Opens [link] in Energy Media Player. Energy takes no link on its command line, but it plays the entries of a playlist file it is opened with,
     * so the link goes into a one-line .m3u that Windows hands to Energy (IApplicationActivationManager.ActivateForFile with Energy's app id).
     * Links that need headers go through the app's HLS or range server, which Energy can reach only once it is exempted from loopback isolation.
     */
    fun openInEnergy(link: ExtractorLink, title: String?): Outcome {
        if (!energyInstalled()) return Outcome(false, "Energy Media Player is not installed")
        if (!energyCanPlay(link)) return Outcome(false, "Energy can not play this kind of link (DASH or protected streams). Use the in-app player.")
        var address = com.lagradost.desktop.net.UrlFix.encode(link.url)
        if (needsHeaders(link)) {
            if (!energyLoopbackAllowed()) return Outcome(false, ENERGY_LOOPBACK)
            val ua = link.headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value ?: com.lagradost.cloudstream3.USER_AGENT
            val referer = link.headers.entries.firstOrNull { it.key.equals("Referer", true) }?.value?.takeIf { it.isNotBlank() } ?: link.referer.takeIf { it.isNotBlank() }
            val all = buildMap { put("User-Agent", ua); putAll(link.headers); if (referer != null) put("Referer", referer) }
            address = if (link.type == ExtractorLinkType.M3U8) HlsProxy.wrap(address, all) else RangeProxy.wrap(address, all)
        }
        return try {
            val name = title?.replace('\n', ' ')?.replace('\r', ' ')?.takeIf { it.isNotBlank() } ?: "CloudStream"
            val playlist = File.createTempFile("energy-", ".m3u").apply { deleteOnExit(); writeText("#EXTM3U\r\n#EXTINF:-1,$name\r\n$address\r\n", Charsets.UTF_8) }
            Log.i(TAG, "Energy: ${address.take(300)}")
            WinActivation.activateForFile(ENERGY_AUMID, playlist.absolutePath)
            Outcome(true)
        } catch (t: Throwable) {
            Log.w(TAG, "Energy did not start: $t")
            Outcome(false, "Energy did not start: ${t.message}")
        }
    }

    /** Opens a file with a given Store app (by its app id), as "Open with" does: Store apps are started through COM, not as a process */
    private object WinActivation {
        private val CLSID_AAM = com.sun.jna.platform.win32.Guid.CLSID("{45BA127D-10A8-46EA-8AB7-56EA9078943C}")
        private val IID_AAM = com.sun.jna.platform.win32.Guid.IID("{2e941141-7f97-4756-ba1d-9decde894a3d}")
        private val IID_SHELLITEM = com.sun.jna.platform.win32.Guid.IID("{43826d1e-e718-42ee-bc55-a1e261c37bfe}")
        private val IID_SHELLITEMARRAY = com.sun.jna.platform.win32.Guid.IID("{b63ea76d-1f85-456f-a19c-48159efa858b}")

        @Suppress("FunctionName")
        private interface Shell : com.sun.jna.win32.StdCallLibrary {
            fun SHCreateItemFromParsingName(path: com.sun.jna.WString, bc: com.sun.jna.Pointer?, riid: com.sun.jna.platform.win32.Guid.REFIID, out: com.sun.jna.ptr.PointerByReference): com.sun.jna.platform.win32.WinNT.HRESULT
            fun SHCreateShellItemArrayFromShellItem(item: com.sun.jna.Pointer, riid: com.sun.jna.platform.win32.Guid.REFIID, out: com.sun.jna.ptr.PointerByReference): com.sun.jna.platform.win32.WinNT.HRESULT
        }

        private val shell by lazy { com.sun.jna.Native.load("shell32", Shell::class.java) }

        /** IApplicationActivationManager: after IUnknown's three, slot 3 is ActivateApplication and slot 4 ActivateForFile */
        private class Manager(p: com.sun.jna.Pointer) : com.sun.jna.platform.win32.COM.Unknown(p) {
            fun activateForFile(aumid: String, items: com.sun.jna.Pointer): Int =
                _invokeNativeInt(4, arrayOf(pointer, com.sun.jna.WString(aumid), items, com.sun.jna.WString("open"), com.sun.jna.ptr.IntByReference()))
        }

        private fun check(hr: Int, what: String) { if (hr < 0) throw IllegalStateException("$what failed (0x${Integer.toHexString(hr)})") }

        /** On a thread of its own, so the COM apartment belongs to this call alone */
        fun activateForFile(aumid: String, path: String) {
            var error: Throwable? = null
            val t = Thread {
                val ole = com.sun.jna.platform.win32.Ole32.INSTANCE
                ole.CoInitializeEx(null, com.sun.jna.platform.win32.Ole32.COINIT_MULTITHREADED)
                try {
                    val item = com.sun.jna.ptr.PointerByReference()
                    check(shell.SHCreateItemFromParsingName(com.sun.jna.WString(path), null, com.sun.jna.platform.win32.Guid.REFIID(IID_SHELLITEM), item).toInt(), "Reading the playlist file")
                    val items = com.sun.jna.ptr.PointerByReference()
                    try {
                        check(shell.SHCreateShellItemArrayFromShellItem(item.value, com.sun.jna.platform.win32.Guid.REFIID(IID_SHELLITEMARRAY), items).toInt(), "Listing the playlist file")
                    } finally { com.sun.jna.platform.win32.COM.Unknown(item.value).Release() }
                    try {
                        val manager = com.sun.jna.ptr.PointerByReference()
                        check(ole.CoCreateInstance(CLSID_AAM, null, com.sun.jna.platform.win32.WTypes.CLSCTX_LOCAL_SERVER, IID_AAM, manager).toInt(), "Windows app activation")
                        val m = Manager(manager.value)
                        try { check(m.activateForFile(aumid, items.value), "Starting the app") } finally { m.Release() }
                    } finally { com.sun.jna.platform.win32.COM.Unknown(items.value).Release() }
                } catch (e: Throwable) { error = e } finally { ole.CoUninitialize() }
            }
            t.isDaemon = true
            t.start()
            t.join(20_000)
            error?.let { throw it }
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

/** The "Energy Media Player" entry of the player lists (episode menu, default player setting): the Microsoft Store player, handed a one-line playlist */
class DesktopEnergyAction : com.lagradost.cloudstream3.actions.VideoClickAction() {
    override val name = com.lagradost.cloudstream3.utils.txt(com.lagradost.cloudstream3.R.string.episode_action_play_in_format, "Energy Media Player")

    /** one link at a time: the app asks which source */
    override val oneSource = true

    override val isPlayer = true

    override val sourceTypes: Set<ExtractorLinkType> = setOf(ExtractorLinkType.VIDEO, ExtractorLinkType.M3U8)

    /** only once it is installed (it is a Store app few people have; VLC and mpv are always listed because they are common) */
    override fun shouldShow(context: android.content.Context?, video: com.lagradost.cloudstream3.ui.result.ResultEpisode?) = ExternalPlayers.energyInstalled()

    override suspend fun runAction(
        context: android.content.Context?,
        video: com.lagradost.cloudstream3.ui.result.ResultEpisode,
        result: com.lagradost.cloudstream3.ui.result.LinkLoadingResult,
        index: Int?,
    ) {
        val link = result.links.getOrNull(index ?: 0) ?: return
        val outcome = ExternalPlayers.openInEnergy(link, video.name ?: video.headerName)
        if (!outcome.ok) when {
            !ExternalPlayers.energyInstalled() -> com.lagradost.desktop.ui.ExternalPlayerHints.energyMissing()
            outcome.message == ExternalPlayers.ENERGY_LOOPBACK -> com.lagradost.desktop.ui.ExternalPlayerHints.energyNeedsLoopback()
            else -> throw com.lagradost.cloudstream3.ErrorLoadingException(outcome.message ?: "Energy did not start")
        }
    }
}
