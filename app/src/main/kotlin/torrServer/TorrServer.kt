package torrServer

import android.util.Log
import com.lagradost.desktop.DesktopPlatform
import com.lagradost.desktop.runtime.AndroidRuntime
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URI

/**
 * Desktop replacement for the gomobile "torrServer" package that the Android app embeds
 * (github.com/recloudstream/torrentserver, a TorrServer fork). The official TorrServer program
 * exposes the same HTTP API (/echo, /torrents, /stream), so it is run as a local child process.
 */
object TorrServer {
    private const val TAG = "TorrServer"
    private const val VERSION = "MatriX.145"

    @Volatile
    private var process: Process? = null

    @Volatile
    private var configDir: File? = null

    private fun assetName(): String {
        val arch = System.getProperty("os.arch").lowercase()
        val cpu = when {
            arch.contains("aarch64") || arch.contains("arm64") -> "arm64"
            arch.contains("86") && !arch.contains("64") -> "386"
            else -> "amd64"
        }
        return when {
            DesktopPlatform.isWindows -> "TorrServer-windows-$cpu.exe"
            DesktopPlatform.isMac -> "TorrServer-darwin-$cpu"
            else -> "TorrServer-linux-$cpu"
        }
    }

    /** Bundled with the installer, or downloaded once into the data folder */
    private fun binary(): File {
        val name = assetName()
        System.getProperty("compose.application.resources.dir")?.let { dir ->
            val bundled = File(dir, "torrserver/$name")
            if (bundled.isFile) return bundled
        }
        val dir = File(AndroidRuntime.dataDir, "torrserver/$VERSION").also { it.mkdirs() }
        val file = File(dir, name)
        if (file.isFile && file.length() > 0) return file
        val url = "https://github.com/YouROK/TorrServer/releases/download/$VERSION/$name"
        Log.i(TAG, "Downloading $url")
        val tmp = File(dir, "$name.tmp")
        var connection = URI(url).toURL().openConnection() as HttpURLConnection
        var redirects = 0
        while (connection.responseCode in 300..399 && redirects++ < 10) {
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            connection = URI(location).toURL().openConnection() as HttpURLConnection
        }
        connection.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
        file.setExecutable(true)
        return file
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private fun echo(port: Int): Boolean = try {
        val c = URI("http://127.0.0.1:$port/echo").toURL().openConnection() as HttpURLConnection
        c.connectTimeout = 1000
        c.readTimeout = 1000
        c.responseCode == 200
    } catch (_: Throwable) {
        false
    }

    /**
     * Starts the server with [dir] as working/database folder, returns the port or -1.
     * [port] 0 picks a free port, like the embedded server.
     */
    @JvmStatic
    @Synchronized
    fun startTorrentServer(dir: String, port: Long): Long {
        try {
            stopTorrentServer()
            val config = File(dir).also { it.mkdirs() }
            configDir = config
            val realPort = if (port <= 0) freePort() else port.toInt()
            val exe = binary()
            val cmd = listOf(
                exe.absolutePath,
                "-p", realPort.toString(),
                "-d", config.absolutePath,
                "-i", "127.0.0.1",
                "-l", File(config, "torrserver.log").absolutePath,
            )
            val p = ProcessBuilder(cmd).directory(config).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            process = p
            Runtime.getRuntime().addShutdownHook(Thread { p.destroy() })
            val deadline = System.currentTimeMillis() + 15_000
            while (System.currentTimeMillis() < deadline) {
                if (!p.isAlive) {
                    Log.e(TAG, "TorrServer exited with ${p.exitValue()}")
                    return -1
                }
                if (echo(realPort)) {
                    Log.i(TAG, "TorrServer $VERSION listening on $realPort")
                    return realPort.toLong()
                }
                Thread.sleep(150)
            }
            Log.e(TAG, "TorrServer did not start in time")
            p.destroy()
            return -1
        } catch (t: Throwable) {
            Log.e(TAG, "Could not start TorrServer: ${Log.getStackTraceString(t)}")
            return -1
        }
    }

    /** Extra trackers, TorrServer reads them from trackers.txt in its config folder */
    @JvmStatic
    fun addTrackers(trackers: String) {
        val dir = configDir ?: return
        try {
            File(dir, "trackers.txt").writeText(
                trackers.split(",", "\n").map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n")
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Could not write trackers: $t")
        }
    }

    @JvmStatic
    @Synchronized
    fun stopTorrentServer() {
        process?.let {
            it.destroy()
            if (!it.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) it.destroyForcibly()
        }
        process = null
    }
}
