package com.lagradost.desktop.torrent

import android.util.Log
import com.lagradost.desktop.platform.ProcessConfine
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.desktop.runtime.AndroidRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

/** What the player shows while a torrent is found and buffered, and while it plays */
class TorrentProgress(
    val phase: String,
    val peers: Int = 0,
    val seeds: Int = 0,
    val speedBps: Long = 0,
    val percent: Int = 0,
    val sizeBytes: Long = 0,
)

/** The torrent engine could not open a source: the text says why in words for the viewer */
class TorrentException(message: String) : Exception(message)

/**
 * Plays magnet / torrent sources (from Stremio add-ons such as Torrentio, or from extensions). The work is done by TorrServer
 * (github.com/YouROK/TorrServer, GPL-3), a separate program that this app downloads once, when the user turns torrent streaming on, and
 * runs on the loopback address only: it turns a torrent into an HTTP address with ranges that the player (mpv) opens like any file.
 * It starts with the first torrent, stops five minutes after the last one and when the app closes.
 */
object TorrentEngine {
    private const val TAG = "Torrent"
    private const val PREF = "desktop_torrent_"
    private const val RELEASES = "https://api.github.com/repos/YouROK/TorrServer/releases?per_page=40"
    private const val ASSET = "TorrServer-windows-amd64.exe"
    private const val DOWNLOAD_PREFIX = "https://github.com/YouROK/TorrServer/releases/download/"

    /**
     * Only the "MatriX" line of releases speaks the command line and HTTP interface this app uses. Since 2026-11-14 the project's
     * "latest" release is a rewrite ("Silo": one YAML config file instead of options, listens on every network, a web UI with plugins)
     * that exits at once with this app's options, so "latest" is never used: the newest MatriX release is.
     */
    private const val LINE = "MatriX"
    internal fun compatible(tag: String?): Boolean = tag != null && tag.startsWith(LINE, ignoreCase = true)

    /** A release this app knows and has tested (used when GitHub's API cannot be reached) */
    private const val PINNED_TAG = "MatriX.150"
    private const val PINNED_SHA256 = "3a96b97a24d86a90c0712a6ec47d4ed52edc7eabac1bce6a0d9bbc8fd30de5bb"
    /** The release the first version of this app installed without keeping its checksum: still recognised */
    private const val LEGACY_TAG = "MatriX.145"
    private const val LEGACY_SHA256 = "407532e5da6e7676ad274489b322d28f8f1ae7d2e12032e9d6acfadd9b945006"
    private const val MIN_SIZE = 30L * 1024 * 1024
    private const val IDLE_STOP_MS = 5 * 60_000L

    /**
     * Public trackers that answer (ngosang/trackerslist "best", 2026-11-17). Every torrent gets them: most Torrentio sources (18 of 23 for one
     * film) name no tracker of their own, and the engine's built-in list dates from 2019 (city9x, internetwarriors, popcorn-tracker: all gone).
     */
    val trackers = listOf(
        "udp://tracker.opentrackr.org:1337/announce", "udp://open.stealth.si:80/announce", "udp://tracker.torrent.eu.org:451/announce",
        "udp://open.tracker.cl:1337/announce", "udp://open.demonii.com:1337/announce", "udp://exodus.desync.com:6969/announce",
        "udp://tracker.qu.ax:6969/announce", "udp://tracker.ducks.party:1984/announce", "udp://tracker.bittor.pw:1337/announce",
        "udp://tracker-udp.gbitt.info:80/announce", "udp://tracker.corpscorp.online:80/announce", "udp://tracker.nyaa.vc:6969/announce",
        "udp://tracker2.dler.org:80/announce", "http://tracker.dler.org:6969/announce", "udp://explodie.org:6969/announce",
        "udp://tracker.gmi.gd:6969/announce", "http://tracker.renfei.net:8080/announce", "udp://tracker.peerfect.org:6969/announce",
    )

    // ------------------------------------------------------------------ settings (Settings > Stremio & torrents)

    /** The viewer read the notice and turned torrent streaming on */
    var enabled by mutableStateOf(false)
        private set

    /** The loopback port TorrServer listens on (the first free one from 8097 when this one is taken) */
    var port by mutableIntStateOf(8097)
        private set

    /** Size of the buffer TorrServer keeps for the playing torrent, MB */
    var cacheMb by mutableIntStateOf(256)
        private set

    /** Only connect to peers that use encryption (some providers throttle plain torrent traffic) */
    var encrypt by mutableStateOf(false)
        private set

    /** The version of TorrServer on the disk (its release tag), null when none */
    var version by mutableStateOf<String?>(null)
        private set

    /** SHA-256 of the engine file as it was installed: checked before every start, so a program that was swapped in is never run */
    @Volatile private var binarySha: String? = null

    /** 0..1 while TorrServer is downloaded, null otherwise; [installError] says why the last try failed */
    var installProgress by mutableStateOf<Float?>(null)
        private set
    var installError by mutableStateOf<String?>(null)
        private set

    /** The torrent in use, for the player's pill; null when none */
    var progress by mutableStateOf<TorrentProgress?>(null)
        private set

    private fun prefs() = androidx.preference.PreferenceManager.getDefaultSharedPreferences(AndroidRuntime.context)

    @Volatile private var loaded = false

    fun load() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            loaded = true
            runCatching {
                val p = prefs()
                enabled = p.getBoolean(PREF + "enabled", false)
                port = p.getInt(PREF + "port", 8097).coerceIn(1024, 65000)
                cacheMb = p.getInt(PREF + "cache_mb", 256).coerceIn(64, 4096)
                encrypt = p.getBoolean(PREF + "encrypt", false)
                version = p.getString(PREF + "version", null)?.takeIf { binary.isFile }
                binarySha = p.getString(PREF + "sha256", null)
            }
        }
    }

    private fun savePrefs() {
        runCatching {
            prefs().edit().putBoolean(PREF + "enabled", enabled).putInt(PREF + "port", port).putInt(PREF + "cache_mb", cacheMb)
                .putBoolean(PREF + "encrypt", encrypt).putString(PREF + "version", version).putString(PREF + "sha256", binarySha).apply()
        }
    }

    fun chooseEnabled(on: Boolean) { load(); enabled = on; savePrefs(); if (!on) stop() }
    fun chooseCacheMb(mb: Int) { load(); cacheMb = mb.coerceIn(64, 4096); savePrefs(); settingsApplied = false }
    fun chooseEncrypt(on: Boolean) { load(); encrypt = on; savePrefs(); settingsApplied = false }

    // ------------------------------------------------------------------ files

    private val dir: File get() = File(AndroidRuntime.dataDir, "torrserver").also { it.mkdirs() }
    val binary: File get() = File(dir, "TorrServer.exe")
    /** An engine this app can drive is on the disk (one of the new "Silo" line, installed by 1.1.3 and earlier, is not: it is replaced) */
    val installed: Boolean get() = binary.isFile && binary.length() > MIN_SIZE && (version == null || compatible(version))
    val sizeMb: Int get() = (binary.length() / (1024 * 1024)).toInt()
    private val dataDir: File get() = File(dir, "data").also { it.mkdirs() }
    val cacheDir: File get() = File(dir, "cache").also { it.mkdirs() }
    // inside the folder the engine may write to (see ProcessConfine): the engine itself and its folder stay as only this app can change them
    private val logFile: File get() = File(dataDir, "torrserver.log")

    /** Torrent streaming is on and the engine is on the disk: a torrent source can be played */
    val ready: Boolean get() { load(); return enabled && installed }

    fun isTorrent(link: ExtractorLink?): Boolean {
        if (link == null) return false
        if (link.type == ExtractorLinkType.MAGNET || link.type == ExtractorLinkType.TORRENT) return true
        val u = link.url.trim().lowercase()
        return u.startsWith("magnet:") || u.substringBefore('?').endsWith(".torrent")
    }

    /** The seeders the add-on reported for a torrent source, null when it did not say */
    fun seeders(link: ExtractorLink?): Int? = runCatching { link?.extractorData?.let { JSONObject(it).optInt("seeders", -1) }?.takeIf { it >= 0 } }.getOrNull()

    /**
     * How well shared a torrent source is, in steps, for the order sources are tried in: 0 under 3 seeders, 1 for 3-9, 2 for 10-29, 3 for 30-99,
     * 4 for 100 and more. Steps, not the bare number, so 480 against 500 seeders does not outrank a better quality. A torrent whose add-on
     * gave no count is put with 3-9 (unknown, not dead); a source that is not a torrent is 0 (they are all sorted before torrents anyway).
     */
    fun seedTier(link: ExtractorLink?): Int {
        if (!isTorrent(link)) return 0
        val n = seeders(link) ?: return 1
        return when {
            n < 3 -> 0
            n < 10 -> 1
            n < 30 -> 2
            n < 100 -> 3
            else -> 4
        }
    }

    /**
     * A torrent too big to stream from a swarm: its file needs over ~1.2 MB/s (more than 3.2 GB for a 45 min episode, 7.7 GB for a film).
     * Swarms gave 0.1 to 2 MB/s in the measures, and a 7 GB episode (2.7 MB/s) was tried and left as "too slow" on every Breaking Bad
     * episode, ahead of releases that played. Such a release is tried after the others (it stays in the list, to be picked by hand).
     */
    fun tooHeavy(link: ExtractorLink?): Boolean {
        if (!isTorrent(link)) return false
        val gb = com.lagradost.desktop.net.SourceWeight.sizeGb(link) ?: return false
        val series = link?.extractorData?.let { runCatching { JSONObject(it).has("episode") }.getOrNull() } == true
        val minutes = if (series) 45.0 else 110.0
        return gb * 1024 / (minutes * 60) > 1.2
    }

    /** The size of the whole disk cache of the engine in MB (what Settings can clear) */
    fun diskUseMb(): Long = runCatching { cacheDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() } / (1024 * 1024) }.getOrDefault(0)

    // the engine is stopped first and waited for: Windows does not delete files a running program holds open
    fun clearCache() {
        scope.launch {
            stopNow()
            runCatching { cacheDir.deleteRecursively(); cacheDir.mkdirs() }
        }
    }

    // ------------------------------------------------------------------ install

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var installJob: Job? = null

    private val http: OkHttpClient by lazy { OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(0, TimeUnit.SECONDS).build() }
    private val json = "application/json; charset=utf-8".toMediaType()

    /** Downloads TorrServer (about 60 MB) into the data folder; [onDone] once it is there or failed. Safe to call again while it runs. */
    fun install(onDone: (Boolean) -> Unit = {}) {
        load()
        if (installJob?.isActive == true) return
        installError = null
        installJob = scope.launch {
            val ok = try {
                downloadBinary()
                true
            } catch (t: Throwable) {
                Log.w(TAG, "install failed: ${t.message}")
                // a download that stopped half way is not left in the data folder
                runCatching { File(dir, "TorrServer.exe.part").delete() }
                installError = t.message ?: t.javaClass.simpleName
                false
            } finally {
                installProgress = null
            }
            onDone(ok)
        }
    }

    fun cancelInstall() { installJob?.cancel() }

    private class Release(val tag: String, val url: String, val sha256: String)

    /** The newest MatriX release with a Windows engine and a published checksum, or null when GitHub did not answer */
    private suspend fun newestRelease(): Release? = runCatching {
        val list = org.json.JSONArray(app.get(RELEASES, timeout = 15, headers = mapOf("Accept" to "application/vnd.github+json")).text)
        // newest first; pre-releases and drafts are left out
        for (r in 0 until list.length()) {
            val j = list.getJSONObject(r)
            val tag = j.optString("tag_name")
            if (!compatible(tag) || j.optBoolean("prerelease") || j.optBoolean("draft")) continue
            val assets = j.optJSONArray("assets") ?: continue
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (!a.optString("name").equals(ASSET, true)) continue
                val found = a.optString("browser_download_url")
                val digest = a.optString("digest").removePrefix("sha256:").lowercase().takeIf { Regex("[0-9a-f]{64}").matches(it) }
                // only this program's own release files, and only with a checksum to compare with
                if (found.startsWith(DOWNLOAD_PREFIX) && digest != null) return@runCatching Release(tag, found, digest)
            }
        }
        null
    }.getOrNull()

    /** The newest release of the engine this app can use (its tag), or null when GitHub did not answer */
    suspend fun latestTag(): String? = withContext(Dispatchers.IO) { newestRelease()?.tag }

    private suspend fun downloadBinary() = withContext(Dispatchers.IO) {
        installProgress = 0f
        // the newest MatriX release names the file and states its SHA-256; when GitHub's API is out of reach the release this app knows is installed
        val release = newestRelease() ?: Release(PINNED_TAG, DOWNLOAD_PREFIX + PINNED_TAG + "/" + ASSET, PINNED_SHA256)
        val url = release.url
        val tag: String? = release.tag
        val expected: String? = release.sha256
        val tmp = File(dir, "TorrServer.exe.part")
        val client = app.baseClient.newBuilder().callTimeout(0, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw TorrentException("The download failed (HTTP ${r.code})")
            val body = r.body
            val total = body.contentLength()
            body.byteStream().use { input ->
                tmp.outputStream().use { out ->
                    val buffer = ByteArray(128 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        if (total > 0) installProgress = (done.toFloat() / total).coerceIn(0f, 1f)
                        if (!isActive) throw kotlinx.coroutines.CancellationException()
                    }
                }
            }
        }
        // a program, not a web page: big enough and an executable ("MZ")
        val head = tmp.inputStream().use { it.readNBytes(2) }
        if (tmp.length() < MIN_SIZE || head.size < 2 || head[0] != 'M'.code.toByte() || head[1] != 'Z'.code.toByte()) {
            tmp.delete()
            throw TorrentException("The downloaded file is not the torrent engine")
        }
        val actual = ProcessConfine.sha256(tmp)
        if (actual != expected) {
            tmp.delete()
            throw TorrentException("The downloaded file does not match the checksum published for the release, so it was not installed")
        }
        stopNow()
        binary.delete()
        if (!tmp.renameTo(binary)) throw TorrentException("The engine could not be saved in ${dir.path}")
        version = tag ?: "unknown"
        binarySha = actual
        savePrefs()
        Log.i(TAG, "TorrServer ${version} installed (${sizeMb} MB)")
    }

    /** Removes the program and its files (the viewer can download it again) */
    fun uninstall() {
        scope.launch {
            stopNow()
            runCatching { binary.delete(); dataDir.deleteRecursively(); cacheDir.deleteRecursively() }
            version = null
            binarySha = null
            savePrefs()
        }
    }

    // ------------------------------------------------------------------ process

    private val base: String get() = "http://127.0.0.1:$port"
    /** An address of a file of a torrent this engine serves (the video, a subtitle file) */
    fun isOwnAddress(url: String): Boolean = url.startsWith("http://127.0.0.1:" + port + "/stream/")
    @Volatile private var process: Process? = null
    @Volatile private var settingsApplied = false
    @Volatile private var lastUse = 0L
    private var idleJob: Job? = null
    private val startLock = Any()

    init {
        Runtime.getRuntime().addShutdownHook(Thread({ runCatching { stopNow() } }, "torrserver-shutdown"))
    }

    private fun echo(): Boolean = runCatching {
        http.newBuilder().callTimeout(1, TimeUnit.SECONDS).build().newCall(Request.Builder().url("$base/echo").build()).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    private fun freePort(preferred: Int): Int {
        for (p in preferred..preferred + 40) if (runCatching { ServerSocket(p, 1, java.net.InetAddress.getByName("127.0.0.1")).use { true } }.getOrDefault(false)) return p
        return ServerSocket(0).use { it.localPort }
    }

    /** The file on the disk is the one that was installed (or a release this app knows): anything else is not run */
    private fun verifyBinary() {
        val actual = ProcessConfine.sha256(binary)
        val known = binarySha
        if (known != null && actual == known) return
        // installed by an earlier version of this app, which did not keep the checksum: accepted only when it is the release this app knows
        if (known == null && (actual == LEGACY_SHA256 || actual == PINNED_SHA256)) { binarySha = actual; version = version ?: if (actual == LEGACY_SHA256) LEGACY_TAG else PINNED_TAG; savePrefs(); return }
        Log.w(TAG, "the torrent engine on the disk does not match what was installed (sha256 $actual)")
        throw TorrentException("The torrent engine file has changed since it was installed, so it was not started. Install it again in Settings > Stremio & torrents.")
    }

    /**
     * The port peers reach the engine on, chosen here: free for both TCP and UDP, kept from one start to the next. Left to itself the engine
     * takes a random one, and on a PC with WSL, Docker or Hyper-V Windows holds back blocks of ports for those (`netsh int ipv4 show
     * excludedportrange protocol=udp`): when its pick fell in one (60538 in 60512-60611 here) it closed at once ("bind: An attempt was made to
     * access a socket in a way forbidden"), and as Windows hands out neighbouring ports, at every start for a while.
     */
    @Volatile private var peerPort = 0

    private fun peerPortUsable(p: Int): Boolean = runCatching { ServerSocket(p).use { java.net.DatagramSocket(p).use { true } } }.getOrDefault(false)

    private fun choosePeerPort(fresh: Boolean): Int {
        val stored = runCatching { prefs().getInt(PREF + "peer_port", 0) }.getOrDefault(0)
        if (!fresh && stored in 1024..65535 && peerPortUsable(stored)) return stored.also { peerPort = it }
        // below the range Windows hands out by itself (49152 and up), where its blocks are
        val random = java.util.Random()
        repeat(60) {
            val p = 20_000 + random.nextInt(20_000)
            if (p != stored && peerPortUsable(p)) {
                runCatching { prefs().edit().putInt(PREF + "peer_port", p).apply() }
                return p.also { peerPort = it }
            }
        }
        return 0.also { peerPort = it }
    }

    /** Puts the port in the engine's settings file, which it reads as it starts (there is none before its first start) */
    private fun writePeerPort(p: Int) {
        if (p <= 0) return
        val f = File(dataDir, "settings.json")
        if (!f.isFile) return
        runCatching {
            val text = f.readText()
            val changed = text.replace(Regex("\"PeersListenPort\"\\s*:\\s*\\d+"), "\"PeersListenPort\": $p")
            if (changed != text) f.writeText(changed)
        }.onFailure { Log.w(TAG, "the port for peers could not be written: ${it.message}") }
    }

    /** Starts TorrServer when it does not run (and tells it how this app wants it to behave) */
    suspend fun ensureRunning() = withContext(Dispatchers.IO) {
        load()
        if (!installed) throw TorrentException("The torrent engine is not installed. Turn on torrent streaming in Settings > Stremio & torrents.")
        lastUse = System.currentTimeMillis()
        synchronized(startLock) {
            if (process?.isAlive == true && echo()) return@synchronized
            // an engine of an earlier run that was left behind on the port: closed first
            if (echo()) runCatching { http.newCall(Request.Builder().url("$base/shutdown").build()).execute().close() }.also { Thread.sleep(500) }
            if (!runCatching { ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1")).use { true } }.getOrDefault(false)) {
                port = freePort(port + 1)
                savePrefs()
            }
            verifyBinary()
            // the folders the engine writes in are marked low integrity; with that done the engine itself runs at the low level (see ProcessConfine)
            val lowOk = ProcessConfine.prepareFolder(dataDir) && ProcessConfine.prepareFolder(cacheDir)
            val command = listOf(binary.absolutePath, "-p", port.toString(), "-i", "127.0.0.1", "-d", dataDir.absolutePath, "-l", logFile.absolutePath, "--dontkill")
            // twice at most: the engine writes its settings file at its first start, so a port can only be put in it from then on
            for (attempt in 0..1) {
                writePeerPort(choosePeerPort(fresh = attempt > 0))
                Log.i(TAG, "starting ${command.drop(1).joinToString(" ")} (peers on port $peerPort)")
                settingsApplied = false
                val logBefore = logFile.length()
                process = ProcessBuilder(command).directory(dir).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
                process?.let { ProcessConfine.confine(it, lowIntegrity = lowOk) }
                val until = System.currentTimeMillis() + 25_000
                var closed = false
                while (System.currentTimeMillis() < until) {
                    if (echo()) break
                    if (process?.isAlive != true) { closed = true; break }
                    Thread.sleep(250)
                }
                if (!closed) break
                val why = runCatching { java.io.RandomAccessFile(logFile, "r").use { f -> f.seek(logBefore.coerceAtMost(f.length())); ByteArray((f.length() - f.filePointer).toInt().coerceAtMost(8192)).also { f.readFully(it) }.decodeToString() } }.getOrDefault("")
                val portRefused = "BTS.Connect() error" in why
                Log.w(TAG, "the engine closed at its start" + if (portRefused) ": the port for peers could not be used" else "")
                if (!portRefused || attempt == 1) throw TorrentException("The torrent engine closed right after it started (see ${logFile.path})")
            }
            if (!echo()) throw TorrentException("The torrent engine did not start in time")
        }
        applySettings()
        startIdleWatch()
    }

    private fun post(path: String, body: JSONObject, timeoutSec: Long = 20): String? = runCatching {
        http.newBuilder().callTimeout(timeoutSec, TimeUnit.SECONDS).build().newCall(Request.Builder().url("$base$path").post(body.toString().toRequestBody(json)).build()).execute().use { r ->
            if (!r.isSuccessful) { Log.w(TAG, "POST $path: HTTP ${r.code}"); null } else r.body.string()
        }
    }.onFailure { Log.w(TAG, "POST $path: ${it.message}") }.getOrNull()

    /** The settings this app wants: a disk buffer of its own that is deleted with the torrent, no discovery on the home network, a bigger swarm */
    private fun applySettings() {
        if (settingsApplied) return
        val current = post("/settings", JSONObject().put("action", "get"))?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return
        current.put("CacheSize", cacheMb.toLong() * 1024 * 1024)
            .put("UseDisk", true).put("TorrentsSavePath", cacheDir.absolutePath).put("RemoveCacheOnDrop", true)
            .put("ReaderReadAHead", 95).put("PreloadCache", 40)
            .put("EnableBonjour", false).put("EnableDLNA", false).put("EnableDebug", false).put("EnableRutorSearch", false).put("EnableTorznabSearch", false)
            .put("ForceEncrypt", encrypt).put("ConnectionsLimit", 80).put("TorrentDisconnectTimeout", 45)
            // added to every torrent (also extension magnets and the torrent picker's), on top of the torrent's own
            .put("RetrackersMode", 1).put("DefaultTrackers", trackers.joinToString("\n"))
        // the port chosen for peers (see [peerPort]): kept when the engine stores these settings
        if (peerPort > 0) current.put("PeersListenPort", peerPort)
        post("/settings", JSONObject().put("action", "set").put("sets", current))
        settingsApplied = true
        // the engine restarts its client with the new settings
        Thread.sleep(800)
    }

    private fun startIdleWatch() {
        if (idleJob?.isActive == true) return
        idleJob = scope.launch {
            while (isActive) {
                delay(30_000)
                val busy = progress != null || currentHash != null || downloading.isNotEmpty()
                if (!busy && System.currentTimeMillis() - lastUse > IDLE_STOP_MS) {
                    Log.i(TAG, "idle for ${IDLE_STOP_MS / 60_000} minutes: stopping the engine")
                    stopNow()
                    return@launch
                }
            }
        }
    }

    fun stop() { scope.launch { stopNow() } }

    private fun stopNow() {
        pollJob?.cancel()
        prepared.clear()
        currentHash = null
        currentOwner = null
        currentTicket = 0L
        lingerJob?.cancel()
        lingering = null
        progress = null
        val p = process ?: return
        runCatching { http.newBuilder().callTimeout(2, TimeUnit.SECONDS).build().newCall(Request.Builder().url("$base/shutdown").build()).execute().close() }
        runCatching { if (!p.waitFor(2, TimeUnit.SECONDS)) { p.destroyForcibly(); p.waitFor(3, TimeUnit.SECONDS) } }
        process = null
        settingsApplied = false
        Log.i(TAG, "engine stopped")
    }

    // ------------------------------------------------------------------ the next episode of a season pack, fetched ahead

    /** The torrent in use: its files, what the source said of it, and the file playing (for [prefetchNextEpisode]) */
    @Volatile private var openFiles: List<TFile> = emptyList()
    @Volatile private var openData: JSONObject? = null
    @Volatile private var openFileId = -1
    @Volatile private var prefetchedFor: String? = null

    /**
     * Near the end of an episode of a season pack: the start and the end of the next episode's file (what the player reads before its first picture)
     * are fetched while the swarm is busy with this torrent anyway, so the next episode opens without waiting for them (28 s for the next episode
     * of a test show, most of it those two parts). Once per episode; nothing when the pack has no file of the next episode.
     */
    fun prefetchNextEpisode() {
        val hash = currentHash ?: return
        val data = openData ?: return
        val episode = data.takeIf { it.has("episode") }?.optInt("episode", -1)?.takeIf { it >= 0 } ?: return
        val key = "$hash:$episode"
        if (prefetchedFor == key) return
        prefetchedFor = key
        val nextData = JSONObject().put("episode", episode + 1).also { o -> data.optInt("season", -1).takeIf { it >= 0 }?.let { o.put("season", it) } }
        val season = nextData.optInt("season", -1).takeIf { it >= 0 }
        // (also an anime batch's "Show - 07.mkv" when a season is known: only "S01E07" names were taken, and such packs got nothing ahead)
        val next = pickFile(openFiles, nextData)?.takeIf { f -> f.id != openFileId && isEpisode(f.path, season, episode + 1) == true } ?: return
        val address = "$base/stream/next.mkv?link=$hash&index=${next.id}&play"
        val head = minOf(8L * 1024 * 1024, next.length / 10)
        val tail = (next.length / 400).coerceIn(1L * 1024 * 1024, 6L * 1024 * 1024).coerceAtMost(next.length / 10)
        val from = System.currentTimeMillis()
        Log.i(TAG, "fetching the start and the end of the next episode ahead: ${next.path.substringAfterLast('/')}")
        for ((start, count) in listOf(0L to head, next.length - tail to tail)) scope.launch {
            val read = runCatching {
                http.newBuilder().callTimeout(180, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
                    .newCall(Request.Builder().url(address).header("Range", "bytes=$start-${start + count - 1}").build()).execute().use { r ->
                        if (r.code != 206) 0L else r.body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024); var n = 0L
                            while (n < count) { val k = input.read(buffer); if (k < 0) break; n += k }
                            n
                        }
                    }
            }.getOrDefault(0L)
            Log.i(TAG, "next episode: ${if (start == 0L) "start" else "end"} ${read / 1024} of ${count / 1024} KB in ${(System.currentTimeMillis() - from) / 1000} s")
        }
    }

    // ------------------------------------------------------------------ the next torrent, made ready

    /** Torrents added ahead (peers found, file list fetched, nothing downloaded) in case the one being opened does not play, by hash */
    private val prepared = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private const val PREPARED_MS = 120_000L

    /**
     * Adds the torrent the player would try next while this one is opened: the engine finds its peers and its file list, and downloads nothing
     * until it is played. A torrent dropped as dead or too slow was followed by 3 to 20 s (10 on average) of "Getting the file list" for the
     * next one, three or four times on a title with many releases. Not used within two minutes, it is dropped.
     */
    private fun prepare(next: ExtractorLink?) {
        if (next == null || !isTorrent(next)) return
        val known = hashOf(next)
        if (known != null && (known == currentHash || prepared.containsKey(known))) return
        scope.launch {
            val added = post("/torrents", JSONObject().put("action", "add").put("link", next.url).put("title", next.name.take(80)).put("save_to_db", false), 40) ?: return@launch
            val h = runCatching { JSONObject(added).optString("hash") }.getOrNull()?.takeIf { it.isNotBlank() }?.lowercase() ?: return@launch
            if (h == currentHash || h == lingering || prepared.putIfAbsent(h, System.currentTimeMillis()) != null) return@launch
            Log.i(TAG, "made ready in case it is needed: ${next.name.take(70)} ($h)")
            delay(PREPARED_MS)
            // under the hand-over lock: an open of this very torrent at that moment (added again, not yet the one in use) had it dropped under it
            handover.withLock { if (prepared.remove(h) != null && h != currentHash && h != lingering) drop(h) }
        }
    }

    // ------------------------------------------------------------------ what played and what failed (this run of the app)

    /** Torrents (by hash) that gave a picture, and that failed (dead, far too slow, no picture), with when: see [history] */
    private val playedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val failedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private const val REMEMBER_FAILED_MS = 30 * 60_000L
    private val btih = Regex("btih:([0-9a-fA-F]{40})")

    /** The info hash of a torrent source (Stremio names it, a magnet has it), lower case; null for a .torrent address */
    fun hashOf(link: ExtractorLink?): String? {
        link ?: return null
        val named = link.extractorData?.let { d -> runCatching { JSONObject(d).optString("hash") }.getOrNull() }?.takeIf { it.length == 40 }
        return (named ?: btih.find(link.url)?.groupValues?.get(1))?.lowercase()
    }

    /** The torrent in use showed its picture */
    fun markPlayed() { currentHash?.let { playedAt[it] = System.currentTimeMillis(); failedAt.remove(it) } }

    /** The torrent in use gave no picture (the player moved on) */
    fun markFailed() { currentHash?.let { failedAt[it] = System.currentTimeMillis() } }

    private fun markFailed(hash: String) { failedAt[hash] = System.currentTimeMillis() }

    /**
     * For the order sources are tried in: 0 the torrent that played last, 1 any other, 2 one that failed in the last 30 minutes. The next episode
     * of a season pack is the same torrent, but its sources are sorted afresh: the dead and the far too slow releases that were gone through for the
     * episode before (minutes, on a title with many) were tried again first, and the pack that played came after them.
     */
    fun history(link: ExtractorLink?): Int {
        if (!isTorrent(link)) return 1
        val h = hashOf(link) ?: return 1
        if (h == playedAt.maxByOrNull { it.value }?.key) return 0
        val failed = failedAt[h] ?: return 1
        return if (System.currentTimeMillis() - failed < REMEMBER_FAILED_MS) 2 else 1
    }

    // ------------------------------------------------------------------ a torrent

    @Volatile private var currentHash: String? = null
    /**
     * Whose the torrent in use is (a player page), and which open of it: a page that closes lets go of its own torrent only. The page of the
     * next episode took over the same season pack, then the page before, closing a moment later, let go of it: the new episode stopped
     * loading without a word and stayed on "Buffering" (and the engine closed the unused torrent 45 s later)
     */
    @Volatile private var currentOwner: Any? = null
    @Volatile private var currentTicket = 0L
    private val tickets = java.util.concurrent.atomic.AtomicLong()
    /** A torrent the player let go of a moment ago, kept for [LINGER_MS] in case it is opened again (see [release]) */
    @Volatile private var lingering: String? = null
    private var lingerJob: Job? = null
    private const val LINGER_MS = 60_000L
    /** One at a time: adding a torrent, and keeping or dropping the one before */
    private val handover = Mutex()
    private var pollJob: Job? = null
    private val videoExtensions = setOf("mkv", "mp4", "avi", "mov", "webm", "ts", "m4v", "wmv", "flv", "mpg", "mpeg", "m2ts", "ogv")

    internal class TFile(val id: Int, val path: String, val length: Long)

    /** What [open] gives the player: the address of the video on this PC, and the subtitle files that came with it in the torrent */
    class Opened(val link: ExtractorLink, val subtitles: List<Subtitle>)

    private fun statsOf(hash: String): JSONObject? = post("/torrents", JSONObject().put("action", "get").put("hash", hash), 10)?.let { runCatching { JSONObject(it) }.getOrNull() }

    private fun progressOf(s: JSONObject, phase: String): TorrentProgress {
        val preload = s.optLong("preload_size")
        val preloaded = s.optLong("preloaded_bytes")
        val total = s.optLong("torrent_size")
        val percent = when {
            preload > 0 && preloaded > 0 -> (preloaded * 100 / preload).toInt()
            total > 0 -> (s.optLong("loaded_size") * 100 / total).toInt()
            else -> 0
        }.coerceIn(0, 100)
        return TorrentProgress(phase, s.optInt("active_peers"), s.optInt("connected_seeders"), s.optDouble("download_speed", 0.0).toLong(), percent, total)
    }

    /** The video of a torrent: the file the source named, else the episode asked for, else the biggest video */
    internal fun pickFile(files: List<TFile>, data: JSONObject?): TFile? {
        val idx = data?.takeIf { it.has("fileIdx") }?.optInt("fileIdx", -1) ?: -1
        val name = data?.optString("filename")?.takeIf { it.isNotBlank() }
        val season = data?.takeIf { it.has("season") }?.optInt("season", -1)?.takeIf { it >= 0 }
        val episode = data?.takeIf { it.has("episode") }?.optInt("episode", -1)?.takeIf { it >= 0 }
        fun isVideo(f: TFile) = f.path.substringAfterLast('.', "").lowercase() in videoExtensions
        if (name != null) files.firstOrNull { it.path.endsWith(name, ignoreCase = true) }?.let { return it }
        val videos = files.filter { isVideo(it) }
        val patterns = if (season != null && episode != null) episodePatterns(season, episode) else emptyList()
        // the add-on's file number counts in the torrent's own order, the engine numbers its files sorted by name: the two mostly agree, not always
        // (a five-season pack gave episode 4 for episode 2). So the number is taken when its file is the episode asked for, or nothing says which
        val numbered = if (idx >= 0) files.firstOrNull { it.id == idx + 1 }?.takeIf { isVideo(it) } else null
        if (numbered != null && isEpisode(numbered.path, season, episode) != false) return numbered
        for (re in patterns) videos.filter { re.containsMatchIn(it.path.substringAfterLast('/')) }.maxByOrNull { it.length }?.let { return it }
        // anime batches name their files "Title - 06 [1080p].mkv": the episode alone. In a pack of several seasons ("Season 2/Show - 06.mkv") the one
        // of the season asked for comes first, then one that names no season, then any (the biggest of them all was taken, often another season's)
        if (episode != null) {
            val same = videos.filter { TorrentSearch.episodeOf(it.path.substringAfterLast('/')) == episode }
            val ranked = if (season == null) same else same.filter { statedSeason(it.path) == season }.ifEmpty { same.filter { statedSeason(it.path) == null } }.ifEmpty { same }
            ranked.maxByOrNull { it.length }?.let { return it }
        }
        numbered?.let { return it }
        return videos.maxByOrNull { it.length } ?: files.maxByOrNull { it.length }
    }

    /** "S02E05", "2x05", "Season 2 Episode 5" (a season 1 file is not taken for season 10, nor episode 5 for 15) */
    private fun episodePatterns(season: Int, episode: Int): List<Regex> = listOf(
        Regex("s0*$season[ ._-]*e0*$episode(?![0-9])", RegexOption.IGNORE_CASE), Regex("(?<![0-9])0*${season}x0*$episode(?![0-9])", RegexOption.IGNORE_CASE),
        Regex("season[ ._-]*0*$season(?![0-9]).*episode[ ._-]*0*$episode(?![0-9])", RegexOption.IGNORE_CASE),
    )

    /** The one season a file's name, else the nearest of its folders, states ("Season 2/Show - 05.mkv" -> 2); null when none does */
    internal fun statedSeason(path: String): Int? = path.split('/').asReversed().withIndex().firstNotNullOfOrNull { (i, part) ->
        TorrentSearch.seasonsOf(if (i == 0) part.substringBeforeLast('.') else part)?.takeIf { it.first == it.last }?.first
    }

    /**
     * Is the file at [path] the episode asked for (null: nothing to tell by). A name with "S01E05" says it outright; a name with only the episode
     * ("Show - 05.mkv", in a folder "Season 2") is that episode when no season it states is another one.
     */
    internal fun isEpisode(path: String, season: Int?, episode: Int?): Boolean? {
        episode ?: return null
        val n = path.substringAfterLast('/')
        if (season != null) {
            if (episodePatterns(season, episode).any { it.containsMatchIn(n) }) return true
            // another episode's code ("S02E05" when S01E05 is asked for)
            if (episodeCode.containsMatchIn(n)) return false
        }
        if (TorrentSearch.episodeOf(n) != episode) return false
        return season == null || statedSeason(path).let { it == null || it == season }
    }

    /** A subtitle file that comes with the video in its torrent: [language] its language's English name when the file name says it */
    class Subtitle(val name: String, val language: String?, val url: String)

    internal class SubFile(val file: TFile, val name: String, val language: String?)

    private val subtitleExtensions = setOf("srt", "ass", "ssa", "vtt")
    private val episodeCode = Regex("s(\\d{1,2})[ ._-]*e(\\d{1,3})(?!\\d)", RegexOption.IGNORE_CASE)
    private val flagWords = setOf("forced", "sdh", "hi", "cc", "full", "subs", "sub", "subtitles")

    /** The language a subtitle file's name ends in: "2_English.srt", "Film.2019.en.srt", "Film.eng.forced.srt" */
    internal fun languageOf(fileName: String): String? {
        val tokens = fileName.substringBeforeLast('.').split('.', '_', '-', ' ', '(', ')', '[', ']').filter { it.isNotBlank() }
        for (t in tokens.asReversed().take(4)) {
            val w = t.lowercase()
            if (w in flagWords || w.all { it.isDigit() }) continue
            return when (w.length) {
                2 -> com.lagradost.cloudstream3.utils.SubtitleHelper.fromTagToEnglishLanguageName(w)
                // three letters only for the common languages (those with a two-letter code too): a release group ("x264-ION", "-GRP") is not one
                3 -> com.lagradost.cloudstream3.utils.SubtitleHelper.fromTagToEnglishLanguageName(w)
                    ?.takeIf { !com.lagradost.cloudstream3.utils.SubtitleHelper.fromLanguageToTwoLetters(it, false).isNullOrEmpty() }
                else -> w.takeIf { com.lagradost.cloudstream3.utils.SubtitleHelper.fromLanguageToTagIETF(it) != null }?.replaceFirstChar { it.uppercase() }
            }
        }
        return null
    }

    /**
     * The subtitle files of a torrent that go with [video]: every one when the torrent holds one video (a film with a "Subs" folder), else those
     * named after the video or kept in a folder of its episode ("Subs/Show.S01E02/2_English.srt"). Named by their language where the file name says it.
     */
    internal fun subtitlesFor(files: List<TFile>, video: TFile): List<SubFile> {
        val subs = files.filter { it.path.substringAfterLast('.', "").lowercase() in subtitleExtensions && it.length in 1..5_000_000 }
        if (subs.isEmpty()) return emptyList()
        fun isVideo(f: TFile) = f.path.substringAfterLast('.', "").lowercase() in videoExtensions
        // samples and extras are far smaller than the video
        val mains = files.count { isVideo(it) && it.length * 3 >= video.length }
        val videoName = video.path.substringAfterLast('/')
        val stem = videoName.substringBeforeLast('.').lowercase()
        val code = episodeCode.find(videoName)?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
        val mine = if (mains <= 1) subs else subs.filter { s ->
            s.path.lowercase().contains(stem) || (code != null && episodeCode.findAll(s.path).any { (it.groupValues[1].toInt() to it.groupValues[2].toInt()) == code })
        }
        val seen = HashMap<String, Int>()
        return mine.take(40).map { f ->
            val fileName = f.path.substringAfterLast('/')
            val language = languageOf(fileName)
            val words = fileName.lowercase().split('.', '_', '-', ' ', '(', ')', '[', ']').toSet()
            var name = (language ?: fileName.substringBeforeLast('.')) +
                (if ("forced" in words) " (forced)" else "") + (if ("sdh" in words || "hi" in words || "cc" in words) " (SDH)" else "")
            val n = (seen[name] ?: 0) + 1
            seen[name] = n
            if (n > 1) name += " $n"
            SubFile(f, name, language)
        }
    }

    /** The speed a video needs, bytes a second: its size over its length (a guess when the length is not known: an episode 45 min, a film 100 min) */
    internal fun neededBps(fileBytes: Long, runtimeSec: Int?, series: Boolean): Long {
        val seconds = (runtimeSec?.takeIf { it in 300..6 * 3600 } ?: if (series) 45 * 60 else 100 * 60).toLong()
        return fileBytes / seconds
    }

    /** Downloading at under half the speed the video plays at: it would stop to load every few seconds, if it started at all */
    internal fun tooSlow(averageBps: Long, neededBps: Long): Boolean = averageBps * 2 < neededBps

    /**
     * Far too slow already after 12 s: under a quarter of the speed the video needs. Swarms speed up over their first minute (about twice from
     * 20 to 60 s in the measures), not four times: a 7 GB episode needing 2.7 MB/s at 300 KB/s was waited for 20 s, three or four such
     * releases on a popular title. The half-speed check at 20 s stays for the ones in between.
     */
    internal fun hopeless(averageBps: Long, neededBps: Long): Boolean = averageBps * 4 < neededBps

    /**
     * Opens a magnet or torrent source: starts the engine, adds the torrent, waits for its file list, chooses the video and waits for the
     * first part of it. Returns the HTTP source the player plays. [report] says what is going on. Throws [TorrentException] with a reason.
     *
     * [hasAlternative]: the player has another source to try. A torrent then gets less time: one nobody shares is given up after 30 s, and one
     * that downloads far slower than the video plays (Torrentio's seeder counts are often old: a "21 seeders" release gave one peer and
     * 120 KB/s here, and sat on the loading screen for minutes) after 20 s of buffering. Without one, it is waited for as long as before.
     * [runtimeSec]: how long the video is, when known; with the file's size it gives the speed the video needs.
     */
    suspend fun open(link: ExtractorLink, hasAlternative: Boolean = false, runtimeSec: Int? = null, resumeMs: Long? = null, owner: Any? = null, next: ExtractorLink? = null, report: (TorrentProgress) -> Unit): Opened = withContext(Dispatchers.IO) {
        load()
        if (!enabled) throw TorrentException("Torrent streaming is off. Turn it on in Settings > Stremio & torrents.")
        report(TorrentProgress(if (process?.isAlive == true) "Opening the torrent…" else "Starting the torrent engine…"))
        ensureRunning()
        val mine = tickets.incrementAndGet()
        // the torrent of the video before is let go of here, but only dropped once the new one is known: the next episode of a season pack is
        // the same torrent, and dropping it first made the engine find its peers and its file list all over again (or, as the drop was sent
        // in the background, removed the torrent the new episode had just been given)
        val previous = currentHash
        currentHash = null
        pollJob?.cancel()
        progress = null
        val data = link.extractorData?.let { runCatching { JSONObject(it) }.getOrNull() }
        val hash = handover.withLock {
            val added = post("/torrents", JSONObject().put("action", "add").put("link", link.url).put("title", link.name.take(80)).put("save_to_db", false), 40)
            val h = added?.let { runCatching { JSONObject(it).optString("hash") }.getOrNull() }?.takeIf { it.isNotBlank() }?.lowercase()
            val kept = h != null && (h == previous || h == lingering)
            val ready = h != null && prepared.remove(h) != null
            listOfNotNull(previous, lingering).distinct().filter { it != h }.forEach { drop(it) }
            lingering = null
            lingerJob?.cancel()
            if (h == null) throw TorrentException("The torrent engine did not accept this source")
            currentHash = h
            currentOwner = owner
            currentTicket = mine
            Log.i(TAG, (if (kept) "kept the open torrent for " else if (ready) "made ready before, now opened: " else "added ") + "${link.name.take(80)} ($h)")
            h
        }
        // the one after it, in case this one does not play
        if (hasAlternative) prepare(next)
        // stopped from outside (the page closed, another source was picked): the torrent is let go of, unless another open took it over
        // The reads of the start and the end of the file stop with it: left running (up to two minutes) they took the next source's speed and
        // added the dropped torrent back to the engine
        var calls: List<okhttp3.Call> = emptyList()
        try {
        lastUse = System.currentTimeMillis()

        // the file list: it comes from the peers, a torrent with few peers needs a while
        var stats: JSONObject? = null
        var files: List<TFile> = emptyList()
        val metaStart = System.currentTimeMillis()
        val metaUntil = metaStart + if (hasAlternative) 60_000 else 90_000
        while (System.currentTimeMillis() < metaUntil) {
            if (currentTicket != mine) throw kotlinx.coroutines.CancellationException()
            val s = statsOf(hash)
            if (s != null) {
                stats = s
                if (hasAlternative && s.optInt("total_peers") == 0 && System.currentTimeMillis() - metaStart > 30_000) {
                    releaseTicket(mine)
                    markFailed(hash)
                    throw TorrentException("Nobody is sharing this torrent right now (no peers found in 30 seconds)")
                }
                val fs = s.optJSONArray("file_stats")
                if (fs != null && fs.length() > 0) {
                    files = (0 until fs.length()).map { i -> fs.getJSONObject(i).let { TFile(it.optInt("id"), it.optString("path"), it.optLong("length")) } }
                    break
                }
                report(progressOf(s, if (s.optInt("total_peers") > 0) "Getting the file list from ${s.optInt("total_peers")} peers…" else "Looking for peers…"))
            }
            delay(500)
        }
        if (files.isEmpty()) {
            releaseTicket(mine)
            markFailed(hash)
            throw TorrentException("No peers answered in time (${stats?.optInt("total_peers") ?: 0} found). The torrent may be dead, or your network blocks torrents.")
        }
        val file = pickFile(files, data) ?: throw TorrentException("This torrent has no video file")
        openFiles = files
        openData = data
        openFileId = file.id
        Log.i(TAG, "file ${file.id}: ${file.path} (${file.length / 1024 / 1024} MB)")

        fun address(f: TFile) = "$base/stream/" + f.path.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "video.mkv" } + "?link=$hash&index=${f.id}&play"

        // What the player reads before its first picture, fetched together and nothing else: the start of the file, and its end, where an mkv keeps its
        // index and tags (an mp4 often its "moov"). mpv reads those at the opening (it only leaves the index for later when nothing else is there): a
        // 1080p HEVC pack sat 112 s on "Seeking to 973305493 to read header element 0x1c53bb6b" after the hand-over. The engine's own preload was used
        // before: ~100 MB from the start (40 % of its buffer), which took the swarm's early speed from that last piece.
        // (Not when the video carries on from where it was left: the player asks for that part at once and the engine fetches it first.)
        val fromStart = resumeMs == null || resumeMs < 60_000
        val headBytes = if (fromStart) minOf(8L * 1024 * 1024, file.length / 10) else 0L
        val tailBytes = if (fromStart) (file.length / 400).coerceIn(1L * 1024 * 1024, 6L * 1024 * 1024).coerceAtMost(file.length / 10) else 0L
        val headRead = java.util.concurrent.atomic.AtomicLong()
        val tailRead = java.util.concurrent.atomic.AtomicLong()
        val headDone = java.util.concurrent.atomic.AtomicBoolean(headBytes <= 0)
        val tailDone = java.util.concurrent.atomic.AtomicBoolean(tailBytes <= 0)
        val fetchFrom = System.currentTimeMillis()
        fun fetch(from: Long, count: Long, read: java.util.concurrent.atomic.AtomicLong, done: java.util.concurrent.atomic.AtomicBoolean, what: String): okhttp3.Call? {
            if (count <= 0) return null
            val call = http.newBuilder().callTimeout(120, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
                .newCall(Request.Builder().url(address(file)).header("Range", "bytes=$from-${from + count - 1}").build())
            scope.launch {
                runCatching {
                    call.execute().use { r ->
                        // only a part: an engine that answered with the whole file is not read to its end
                        if (r.code == 206) r.body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (read.get() < count) { val n = input.read(buffer); if (n < 0) break; read.addAndGet(n.toLong()) }
                        }
                    }
                }
                if (read.get() >= count) Log.i(TAG, "$what of the file (${count / 1024} KB) in after ${(System.currentTimeMillis() - fetchFrom) / 1000} s")
                done.set(true)
            }
            return call
        }
        calls = listOfNotNull(fetch(0, headBytes, headRead, headDone, "the start"), fetch(file.length - tailBytes, tailBytes, tailRead, tailDone, "the end"))
        val bufferStart = System.currentTimeMillis()
        val bufferUntil = if (fromStart) bufferStart + 60_000 else bufferStart
        val neededBps = neededBps(file.length, runtimeSec, series = data?.has("episode") == true)
        // (time, bytes loaded) a few seconds apart: the speed is judged over the last 10 s, not since the start. A swarm gathers its peers over the first
        // minute (measured on four Torrentio releases: 147-364 KB/s at 20 s, 280-2164 KB/s at 60 s), so the average since the start, which counts
        // the seconds with 2 peers, put good releases at half their speed and dropped them. A dead swarm is near nothing either way
        val samples = ArrayDeque<Pair<Long, Long>>()
        var started = false
        while (System.currentTimeMillis() < bufferUntil) {
            if (currentTicket != mine) { calls.forEach { it.cancel() }; throw kotlinx.coroutines.CancellationException() }
            if (headDone.get() && tailDone.get()) { started = true; break }
            val s = statsOf(hash)
            if (s != null) {
                val p = progressOf(s, "Buffering")
                val percent = ((headRead.get() + tailRead.get()) * 100 / (headBytes + tailBytes).coerceAtLeast(1)).toInt().coerceIn(0, 100)
                report(TorrentProgress(p.phase, p.peers, p.seeds, p.speedBps, percent, p.sizeBytes))
                val loaded = s.optLong("loaded_size")
                val now = System.currentTimeMillis()
                // the engine's count of what it holds can go down (its buffer is trimmed too): a window it went down in says nothing, it starts again
                // ("too slow: -462 KB/s from 25 peers" dropped a release that was coming in)
                if (samples.isNotEmpty() && loaded < samples.last().second) samples.clear()
                samples.addLast(now to loaded)
                while (samples.size > 2 && now - samples[1].first >= 10_000) samples.removeFirst()
                val elapsed = now - bufferStart
                val (since, from) = samples.first()
                if (hasAlternative && elapsed >= 12_000 && now - since >= 8_000) {
                    // the engine's own measure of the speed now, when it is higher: either says the swarm can carry the video
                    val averageBps = maxOf((loaded - from) * 1000 / (now - since), p.speedBps, 0L)
                    if (if (elapsed >= 20_000) tooSlow(averageBps, neededBps) else hopeless(averageBps, neededBps)) {
                        Log.i(TAG, "too slow: ${averageBps / 1024} KB/s from ${p.peers} peers, the video needs about ${neededBps / 1024} KB/s")
                        calls.forEach { it.cancel() }
                        releaseTicket(mine)
                        markFailed(hash)
                        throw TorrentException("Too slow to play: ${averageBps / 1024} KB/s from ${p.peers} peers, the video needs about ${neededBps / 1024} KB/s")
                    }
                }
            }
            delay(500)
        }
        // the player asks for what is still missing itself
        calls.forEach { it.cancel() }
        if (!fromStart) Log.i(TAG, "carries on at ${resumeMs!! / 1000} s: handed to the player without the first part")
        else if (!started) Log.i(TAG, "start and end of the file not in after 60 s (${(headRead.get() + tailRead.get()) / 1024} KB), handing the stream to the player anyway")
        startPolling(hash)
        val subtitles = subtitlesFor(files, file).map { Subtitle(it.name, it.language, address(it.file)) }
        if (subtitles.isNotEmpty()) Log.i(TAG, "${subtitles.size} subtitle files in the torrent: ${subtitles.joinToString { it.name }.take(200)}")
        Opened(newExtractorLink(link.source, link.name, address(file), ExtractorLinkType.VIDEO) { this.quality = link.quality }, subtitles)
        } catch (e: Throwable) {
            calls.forEach { it.cancel() }
            releaseTicket(mine)
            throw e
        }
    }

    private fun startPolling(hash: String) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive && currentHash == hash) {
                statsOf(hash)?.let { progress = progressOf(it, "Streaming") }
                lastUse = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    /** The engine removes a torrent and deletes its buffer */
    // ------------------------------------------------------------------ downloads

    /** Torrents being saved to disk (hash -> how many downloads read it): never dropped by playback, and the engine is not stopped as idle */
    private val downloading = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /**
     * The address on this PC to save [link]'s video from (the episode asked for, when it is a season pack): the downloader reads it like any
     * file on the web, with Range for resuming. Call [downloadDone] with the same link when the download ends, however it ends.
     */
    suspend fun downloadLink(link: ExtractorLink): ExtractorLink = withContext(Dispatchers.IO) {
        load()
        if (!ready) throw TorrentException("Torrent streaming is off. Turn it on in Settings > Stremio & torrents to download torrents.")
        ensureRunning()
        startIdleWatch()
        val data = link.extractorData?.let { runCatching { JSONObject(it) }.getOrNull() }
        val hash = handover.withLock {
            val added = post("/torrents", JSONObject().put("action", "add").put("link", link.url).put("title", link.name.take(80)).put("save_to_db", false), 40)
            val h = added?.let { runCatching { JSONObject(it).optString("hash") }.getOrNull() }?.takeIf { it.isNotBlank() }?.lowercase()
                ?: throw TorrentException("The torrent engine did not accept this source")
            downloading.merge(h, 1, Int::plus)
            h
        }
        try {
            var files: List<TFile> = emptyList()
            val until = System.currentTimeMillis() + 120_000
            while (files.isEmpty() && System.currentTimeMillis() < until) {
                lastUse = System.currentTimeMillis()
                statsOf(hash)?.optJSONArray("file_stats")?.takeIf { it.length() > 0 }?.let { fs ->
                    files = (0 until fs.length()).map { i -> fs.getJSONObject(i).let { TFile(it.optInt("id"), it.optString("path"), it.optLong("length")) } }
                }
                if (files.isEmpty()) delay(1_000)
            }
            if (files.isEmpty()) throw TorrentException("No peers answered in time. The torrent may be dead, or your network blocks torrents.")
            val file = pickFile(files, data) ?: throw TorrentException("This torrent has no video file")
            Log.i(TAG, "download of ${file.path} (${file.length / 1024 / 1024} MB) from $hash")
            val name = file.path.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "video.mkv" }
            newExtractorLink(link.source, link.name, "$base/stream/$name?link=$hash&index=${file.id}&play", ExtractorLinkType.VIDEO) {
                this.quality = link.quality
            }
        } catch (t: Throwable) {
            downloadDone(hash)
            throw t
        }
    }

    /** A download from [downloadLink] ended ([address]: the address it gave): its torrent is dropped unless something else uses it */
    fun downloadDone(address: ExtractorLink) = downloadDone(Regex("[?&]link=([0-9a-f]+)").find(address.url)?.groupValues?.get(1))

    private fun downloadDone(hash: String?) {
        hash ?: return
        val left = downloading.computeIfPresent(hash) { _, n -> if (n <= 1) null else n - 1 }
        lastUse = System.currentTimeMillis()
        if (left == null && hash != currentHash && hash != lingering) scope.launch { handover.withLock { if (!downloading.containsKey(hash) && hash != currentHash && hash != lingering) drop(hash) } }
    }

    private fun drop(hash: String) {
        // a torrent being saved to disk stays: playback switching away from it (the same pack) would have cut the download
        if (downloading.containsKey(hash)) return
        post("/torrents", JSONObject().put("action", "drop").put("hash", hash), 10)
        // a read of the torrent that is still going on adds it again at once (the video that plays on while a source picked by hand is tried):
        // a torrent the app switched away from kept downloading from its peers. Dropped again until it stays gone
        scope.launch {
            repeat(24) {
                delay(5_000)
                // under the hand-over lock: the same torrent opened again within these two minutes was added, then dropped here before it became
                // the one in use (the open then waited for a file list that never came and called it dead). One made ready for the next try is kept
                val stop = handover.withLock {
                    if (hash == currentHash || hash == lingering || prepared.containsKey(hash) || downloading.containsKey(hash)) return@withLock true
                    val held = post("/torrents", JSONObject().put("action", "list"), 10)?.contains(hash, ignoreCase = true) ?: return@withLock true
                    if (!held) return@withLock true
                    Log.i(TAG, "dropped again: $hash was asked for after it was dropped")
                    post("/torrents", JSONObject().put("action", "drop").put("hash", hash), 10)
                    false
                }
                if (stop) return@launch
            }
        }
    }

    /**
     * Lets go of the torrent in use: the player closed, or a source that is not a torrent was chosen. It is kept a minute before it is dropped
     * (its buffer deleted), so closing the player and opening the next episode of the same season pack, or the same video again, does not
     * start from nothing; another torrent opened in that minute drops it at once.
     */
    fun release(owner: Any? = null) {
        if (owner != null && owner !== currentOwner) return
        releaseNow()
    }

    /** Lets go of the torrent of one open, when it is still the one in use */
    private fun releaseTicket(ticket: Long) {
        if (ticket == currentTicket) releaseNow()
    }

    private fun releaseNow() {
        val hash = currentHash ?: return
        currentHash = null
        currentOwner = null
        currentTicket = 0L
        pollJob?.cancel()
        progress = null
        scope.launch {
            handover.withLock {
                // opened again meanwhile, or another torrent plays now: kept / dropped at once
                if (currentHash == hash) return@withLock
                if (currentHash != null) { drop(hash); return@withLock }
                lingering?.takeIf { it != hash }?.let { drop(it) }
                lingering = hash
                lingerJob?.cancel()
                lingerJob = scope.launch {
                    delay(LINGER_MS)
                    handover.withLock { if (lingering == hash && currentHash != hash) { lingering = null; drop(hash) } }
                }
            }
        }
    }
}
