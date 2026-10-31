package torrServer

import android.util.Log
import com.lagradost.desktop.DesktopPlatform
import com.lagradost.desktop.platform.ProcessConfine
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

    /** SHA-256 of the program for each file name of release [VERSION] (as GitHub states them): a download or a file on the disk that differs is never run */
    private val PINNED = mapOf(
        "TorrServer-windows-amd64.exe" to "407532e5da6e7676ad274489b322d28f8f1ae7d2e12032e9d6acfadd9b945006",
        "TorrServer-windows-386.exe" to "d9fc5ca0eb46b299dbecfc11f4ec49d037bb1473eab40bf97274b47a33904961",
        "TorrServer-linux-amd64" to "0819f0d697b778d85feb5d8c06becb2c63b06e7eedbb4690bafb460e220221b4",
        "TorrServer-darwin-amd64" to "fcab1a670cb1e3bd44c60a56a70d2cedff904c39a7524aa0e4380679d93be5cf",
        "TorrServer-darwin-arm64" to "2b1d47a2b6c57f891413fe19e28439647b3b7f02b751b8a129d848770308e864",
    )

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
        val pinned = PINNED[name] ?: error("no checksum is known for $name: the program is not downloaded")
        if (file.isFile && file.length() > 0) {
            if (ProcessConfine.sha256(file) == pinned) return file
            Log.w(TAG, "$name on the disk does not match release $VERSION: deleted")
            file.delete()
        }
        val url = "https://github.com/YouROK/TorrServer/releases/download/$VERSION/$name"
        Log.i(TAG, "Downloading $url")
        val tmp = File(dir, "$name.tmp")
        var connection = URI(url).toURL().openConnection() as HttpURLConnection
        var redirects = 0
        while (connection.responseCode in 300..399 && redirects++ < 10) {
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            // a redirect must stay on https
            require(location.startsWith("https://")) { "the download was redirected to an address that is not https" }
            connection = URI(location).toURL().openConnection() as HttpURLConnection
        }
        connection.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        if (ProcessConfine.sha256(tmp) != pinned) {
            tmp.delete()
            error("the download of $name does not match the checksum of release $VERSION")
        }
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
            // the folder it writes in is marked low integrity first; the program then runs at the low level with the job limits (see ProcessConfine)
            val lowOk = ProcessConfine.prepareFolder(config)
            val p = ProcessBuilder(cmd).directory(config).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            ProcessConfine.confine(p, lowIntegrity = lowOk)
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
