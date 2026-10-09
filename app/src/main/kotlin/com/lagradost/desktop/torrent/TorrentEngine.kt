package com.lagradost.desktop.torrent

import android.util.Log
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
    private const val RELEASES = "https://api.github.com/repos/YouROK/TorrServer/releases/latest"
    private const val FALLBACK_URL = "https://github.com/YouROK/TorrServer/releases/latest/download/TorrServer-windows-amd64.exe"
    private const val MIN_SIZE = 30L * 1024 * 1024
    private const val IDLE_STOP_MS = 5 * 60_000L

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
            }
        }
    }

    private fun savePrefs() {
        runCatching {
            prefs().edit().putBoolean(PREF + "enabled", enabled).putInt(PREF + "port", port).putInt(PREF + "cache_mb", cacheMb)
                .putBoolean(PREF + "encrypt", encrypt).putString(PREF + "version", version).apply()
        }
    }

    fun chooseEnabled(on: Boolean) { load(); enabled = on; savePrefs(); if (!on) stop() }
    fun chooseCacheMb(mb: Int) { load(); cacheMb = mb.coerceIn(64, 4096); savePrefs(); settingsApplied = false }
    fun chooseEncrypt(on: Boolean) { load(); encrypt = on; savePrefs(); settingsApplied = false }
    fun choosePort(p: Int) { load(); if (p != port) { stop(); port = p.coerceIn(1024, 65000); savePrefs() } }

    // ------------------------------------------------------------------ files

    private val dir: File get() = File(AndroidRuntime.dataDir, "torrserver").also { it.mkdirs() }
    val binary: File get() = File(dir, "TorrServer.exe")
    val installed: Boolean get() = binary.isFile && binary.length() > MIN_SIZE
    val sizeMb: Int get() = (binary.length() / (1024 * 1024)).toInt()
    private val dataDir: File get() = File(dir, "data").also { it.mkdirs() }
    val cacheDir: File get() = File(dir, "cache").also { it.mkdirs() }
    private val logFile: File get() = File(dir, "torrserver.log")

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

    /** The size of the whole disk cache of the engine in MB (what Settings can clear) */
    fun diskUseMb(): Long = runCatching { cacheDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() } / (1024 * 1024) }.getOrDefault(0)

    fun clearCache() {
        stop()
        runCatching { cacheDir.deleteRecursively(); cacheDir.mkdirs() }
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
                installError = t.message ?: t.javaClass.simpleName
                false
            } finally {
                installProgress = null
            }
            onDone(ok)
        }
    }

    fun cancelInstall() { installJob?.cancel() }

    /** The newest release of the engine (its tag), or null when GitHub did not answer */
    suspend fun latestTag(): String? = withContext(Dispatchers.IO) {
        runCatching { JSONObject(app.get(RELEASES, timeout = 15, headers = mapOf("Accept" to "application/vnd.github+json")).text).optString("tag_name").takeIf { it.isNotBlank() } }.getOrNull()
    }

    private suspend fun downloadBinary() = withContext(Dispatchers.IO) {
        installProgress = 0f
        // the newest release names the file; when GitHub's API is out of reach the "latest" redirect gives the same file
        var url = FALLBACK_URL
        var tag: String? = null
        runCatching {
            val j = JSONObject(app.get(RELEASES, timeout = 15, headers = mapOf("Accept" to "application/vnd.github+json")).text)
            tag = j.optString("tag_name").takeIf { it.isNotBlank() }
            val assets = j.optJSONArray("assets")
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").equals("TorrServer-windows-amd64.exe", true)) url = a.optString("browser_download_url", url)
            }
        }
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
        stop()
        binary.delete()
        if (!tmp.renameTo(binary)) throw TorrentException("The engine could not be saved in ${dir.path}")
        version = tag ?: "unknown"
        savePrefs()
        Log.i(TAG, "TorrServer ${version} installed (${sizeMb} MB)")
    }

    /** Removes the program and its files (the viewer can download it again) */
    fun uninstall() {
        stop()
        runCatching { binary.delete(); dataDir.deleteRecursively(); cacheDir.deleteRecursively() }
        version = null
        savePrefs()
    }

    // ------------------------------------------------------------------ process

    private val base: String get() = "http://127.0.0.1:$port"
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
            val command = listOf(binary.absolutePath, "-p", port.toString(), "-i", "127.0.0.1", "-d", dataDir.absolutePath, "-l", logFile.absolutePath, "--dontkill")
            Log.i(TAG, "starting ${command.drop(1).joinToString(" ")}")
            settingsApplied = false
            process = ProcessBuilder(command).directory(dir).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            val until = System.currentTimeMillis() + 25_000
            while (System.currentTimeMillis() < until) {
                if (echo()) break
                if (process?.isAlive != true) throw TorrentException("The torrent engine closed right after it started (see ${logFile.path})")
                Thread.sleep(250)
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

    private fun getText(path: String, timeoutSec: Long = 20): String? = runCatching {
        http.newBuilder().callTimeout(timeoutSec, TimeUnit.SECONDS).build().newCall(Request.Builder().url("$base$path").build()).execute().use { r -> if (r.isSuccessful) r.body.string() else null }
    }.getOrNull()

    /** The settings this app wants: a disk buffer of its own that is deleted with the torrent, no discovery on the home network, a bigger swarm */
    private fun applySettings() {
        if (settingsApplied) return
        val current = post("/settings", JSONObject().put("action", "get"))?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return
        current.put("CacheSize", cacheMb.toLong() * 1024 * 1024)
            .put("UseDisk", true).put("TorrentsSavePath", cacheDir.absolutePath).put("RemoveCacheOnDrop", true)
            .put("ReaderReadAHead", 95).put("PreloadCache", 40)
            .put("EnableBonjour", false).put("EnableDLNA", false).put("EnableDebug", false).put("EnableRutorSearch", false).put("EnableTorznabSearch", false)
            .put("ForceEncrypt", encrypt).put("ConnectionsLimit", 80).put("TorrentDisconnectTimeout", 45)
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
                val busy = progress != null || currentHash != null
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
        currentHash = null
        progress = null
        val p = process ?: return
        runCatching { http.newBuilder().callTimeout(2, TimeUnit.SECONDS).build().newCall(Request.Builder().url("$base/shutdown").build()).execute().close() }
        runCatching { if (!p.waitFor(2, TimeUnit.SECONDS)) p.destroyForcibly() }
        process = null
        settingsApplied = false
        Log.i(TAG, "engine stopped")
    }

    // ------------------------------------------------------------------ a torrent

    @Volatile private var currentHash: String? = null
    private var pollJob: Job? = null
    private val videoExtensions = setOf("mkv", "mp4", "avi", "mov", "webm", "ts", "m4v", "wmv", "flv", "mpg", "mpeg", "m2ts", "ogv")

    internal class TFile(val id: Int, val path: String, val length: Long)

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
        if (idx >= 0) files.firstOrNull { it.id == idx + 1 }?.takeIf { isVideo(it) }?.let { return it }
        if (name != null) files.firstOrNull { it.path.endsWith(name, ignoreCase = true) }?.let { return it }
        val videos = files.filter { isVideo(it) }
        if (season != null && episode != null) {
            val s = season; val e = episode
            val patterns = listOf(
                Regex("s0*$s[ ._-]*e0*$e(?!\\d)", RegexOption.IGNORE_CASE), Regex("(?<!\\d)0*${s}x0*$e(?!\\d)", RegexOption.IGNORE_CASE),
                Regex("season[ ._-]*0*$s.*episode[ ._-]*0*$e(?!\\d)", RegexOption.IGNORE_CASE),
            )
            for (re in patterns) videos.filter { re.containsMatchIn(it.path.substringAfterLast('/')) }.maxByOrNull { it.length }?.let { return it }
        }
        return videos.maxByOrNull { it.length } ?: files.maxByOrNull { it.length }
    }

    /**
     * Opens a magnet or torrent source: starts the engine, adds the torrent, waits for its file list, chooses the video and waits for the
     * first part of it. Returns the HTTP source the player plays. [report] says what is going on. Throws [TorrentException] with a reason.
     */
    suspend fun open(link: ExtractorLink, report: (TorrentProgress) -> Unit): ExtractorLink = withContext(Dispatchers.IO) {
        load()
        if (!enabled) throw TorrentException("Torrent streaming is off. Turn it on in Settings > Stremio & torrents.")
        report(TorrentProgress("Starting the torrent engine…"))
        ensureRunning()
        release()
        val data = link.extractorData?.let { runCatching { JSONObject(it) }.getOrNull() }
        val added = post("/torrents", JSONObject().put("action", "add").put("link", link.url).put("title", link.name.take(80)).put("save_to_db", false), 40)
            ?: throw TorrentException("The torrent engine did not accept this source")
        val hash = runCatching { JSONObject(added).optString("hash") }.getOrNull()?.takeIf { it.isNotBlank() } ?: throw TorrentException("The torrent engine did not accept this source")
        currentHash = hash
        lastUse = System.currentTimeMillis()
        Log.i(TAG, "added ${link.name.take(80)} ($hash)")

        // the file list: it comes from the peers, a torrent with few peers needs a while
        var stats: JSONObject? = null
        var files: List<TFile> = emptyList()
        val metaUntil = System.currentTimeMillis() + 75_000
        while (System.currentTimeMillis() < metaUntil) {
            if (currentHash != hash) throw kotlinx.coroutines.CancellationException()
            val s = statsOf(hash)
            if (s != null) {
                stats = s
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
            release()
            throw TorrentException("No peers answered in time (${stats?.optInt("total_peers") ?: 0} found). The torrent may be dead, or your network blocks torrents.")
        }
        val file = pickFile(files, data) ?: throw TorrentException("This torrent has no video file")
        Log.i(TAG, "file ${file.id}: ${file.path} (${file.length / 1024 / 1024} MB)")

        // the first part of the video; the player's own request carries on from there
        val preload = scope.launch { getText("/stream?link=$hash&index=${file.id}&preload", 90) }
        val bufferUntil = System.currentTimeMillis() + 60_000
        var started = false
        while (System.currentTimeMillis() < bufferUntil) {
            if (currentHash != hash) { preload.cancel(); throw kotlinx.coroutines.CancellationException() }
            val s = statsOf(hash)
            if (s != null) {
                val p = progressOf(s, "Buffering")
                report(p)
                val preloadSize = s.optLong("preload_size")
                if (s.optLong("preloaded_bytes") >= minOf(preloadSize, 24L * 1024 * 1024).coerceAtLeast(1) || (preloadSize > 0 && p.percent >= 90)) { started = true; break }
                if (s.optLong("loaded_size") >= 8L * 1024 * 1024) { started = true; break }
            }
            delay(500)
        }
        if (!started) Log.i(TAG, "buffer not full after 60 s, handing the stream to the player anyway")
        startPolling(hash)
        val safeName = file.path.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "video.mkv" }
        val url = "$base/stream/$safeName?link=$hash&index=${file.id}&play"
        newExtractorLink(link.source, link.name, url, ExtractorLinkType.VIDEO) { this.quality = link.quality }
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

    /** Drops the torrent in use (its buffer is deleted): the player closed, or another source was chosen */
    fun release() {
        val hash = currentHash ?: return
        currentHash = null
        pollJob?.cancel()
        progress = null
        scope.launch { post("/torrents", JSONObject().put("action", "drop").put("hash", hash), 10) }
    }
}
