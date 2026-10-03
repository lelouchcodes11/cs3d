package com.lagradost.desktop

import android.util.Log
import java.awt.Desktop
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.Locale

/** Operating system integration used by the ported app code */
object DesktopPlatform {
    private const val TAG = "DesktopPlatform"

    val isWindows = System.getProperty("os.name").lowercase(Locale.ROOT).contains("win")
    val isMac = System.getProperty("os.name").lowercase(Locale.ROOT).contains("mac")

    /** Set by the UI, opens a url in the built in Chromium browser */
    @Volatile
    var inAppBrowser: ((String) -> Unit)? = null

    fun openInAppBrowser(url: String) {
        val handler = inAppBrowser
        if (handler != null) {
            handler(url)
        } else {
            openExternalBrowser(url)
        }
    }

    /** Opens the url with the default browser/handler of the OS */
    fun openExternalBrowser(url: String): Boolean {
        return try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
                true
            } else {
                openWithShell(url)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Desktop.browse failed for $url: $t")
            openWithShell(url)
        }
    }

    /** Opens a file or folder with its default application */
    fun openFile(file: File): Boolean {
        return try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file)
                true
            } else openWithShell(file.absolutePath)
        } catch (t: Throwable) {
            Log.w(TAG, "Desktop.open failed for $file: $t")
            openWithShell(file.absolutePath)
        }
    }

    private fun openWithShell(target: String): Boolean {
        return try {
            val cmd = when {
                isWindows -> arrayOf("rundll32", "url.dll,FileProtocolHandler", target)
                isMac -> arrayOf("open", target)
                else -> arrayOf("xdg-open", target)
            }
            ProcessBuilder(*cmd).start()
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Could not open $target: $t")
            false
        }
    }

    @Volatile
    private var lastNetworkCheck = 0L

    @Volatile
    private var lastNetworkResult = true

    /** Cheap connectivity probe, cached for a few seconds */
    fun isNetworkAvailable(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastNetworkCheck < 5_000) return lastNetworkResult
        lastNetworkCheck = now
        lastNetworkResult = try {
            Socket().use { it.connect(InetSocketAddress("1.1.1.1", 443), 1500) }
            true
        } catch (_: Throwable) {
            try {
                Socket().use { it.connect(InetSocketAddress("8.8.8.8", 53), 1500) }
                true
            } catch (_: Throwable) {
                false
            }
        }
        return lastNetworkResult
    }

    /** The user's Downloads folder (Windows known folder, XDG_DOWNLOAD_DIR, ~/Downloads) */
    fun downloadsDir(): File = userFolder("Downloads", "{374DE290-123F-4565-9164-39C4925E467B}", "XDG_DOWNLOAD_DIR")

    fun videosDir(): File = userFolder("Videos", "My Video", "XDG_VIDEOS_DIR", macName = "Movies")

    fun musicDir(): File = userFolder("Music", "My Music", "XDG_MUSIC_DIR")

    fun picturesDir(): File = userFolder("Pictures", "My Pictures", "XDG_PICTURES_DIR")

    private fun userFolder(name: String, windowsShellValue: String, xdg: String, macName: String = name): File {
        val home = File(System.getProperty("user.home"))
        if (isWindows) {
            readWindowsShellFolder(windowsShellValue)?.let { return it }
        } else if (!isMac) {
            readXdgUserDir(xdg, home)?.let { return it }
        }
        return File(home, if (isMac) macName else name)
    }

    private fun readWindowsShellFolder(value: String): File? {
        return try {
            val p = ProcessBuilder(
                "reg", "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\User Shell Folders",
                "/v", value
            ).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            val line = out.lines().firstOrNull { it.contains("REG_") } ?: return null
            var path = line.substringAfter("REG_EXPAND_SZ").substringAfter("REG_SZ").trim()
            Regex("%([^%]+)%").findAll(path).toList().forEach { m ->
                System.getenv(m.groupValues[1])?.let { path = path.replace(m.value, it) }
            }
            File(path).takeIf { path.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun readXdgUserDir(key: String, home: File): File? {
        val config = File(System.getenv("XDG_CONFIG_HOME") ?: File(home, ".config").path, "user-dirs.dirs")
        if (!config.isFile) return null
        val line = config.readLines().firstOrNull { it.startsWith("$key=") } ?: return null
        val value = line.substringAfter('=').trim().removeSurrounding("\"").replace("\$HOME", home.path)
        return File(value)
    }
}
