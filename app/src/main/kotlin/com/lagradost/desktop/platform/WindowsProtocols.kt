package com.lagradost.desktop.platform

import android.util.Log
import com.lagradost.desktop.DesktopPlatform
import java.io.File

/**
 * Registers URL protocol associations on Windows in HKCU\Software\Classes
 * (cloudstreamapp://, cloudstreamrepo://, cloudstreamsearch://) as specified in WP10.2 / WP11.
 */
object WindowsProtocols {
    private const val TAG = "WindowsProtocols"
    val SCHEMES = listOf("cloudstreamapp", "cloudstreamrepo", "cloudstreamsearch")

    /**
     * Registers URL protocol schemes in HKCU\Software\Classes so clicking links in browsers
     * forwards to the application.
     */
    fun registerCurrentExecutable() {
        if (!DesktopPlatform.isWindows) return

        try {
            val exePath = getExecutablePath() ?: return
            Log.i(TAG, "Registering URL protocols for executable: $exePath")

            for (scheme in SCHEMES) {
                registerScheme(scheme, exePath)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to register URL protocol schemes: $t")
        }
    }

    fun getExecutablePath(): String? {
        try {
            val processPath = ProcessHandle.current().info().command().orElse(null)
            if (processPath != null && processPath.endsWith(".exe", ignoreCase = true) && !processPath.contains("java", ignoreCase = true)) {
                return processPath
            }
        } catch (_: Throwable) {
        }

        val javaHome = System.getProperty("java.home")
        if (javaHome != null) {
            val appDir = File(javaHome).parentFile
            if (appDir != null) {
                val possibleExe = File(appDir, "CloudStream.exe")
                if (possibleExe.isFile) return possibleExe.absolutePath
            }
        }

        return null
    }

    /**
     * Writes the scheme with the registry API. (It used to run `reg add`: Java does not escape the quotes inside an argument on
     * Windows, so the command value `"<exe>" "%1"` was "Invalid syntax", only the empty scheme keys were written, and Windows had
     * no program to hand `cloudstreamapp://` links to: AniList / MAL / Simkl logins never came back to the app.)
     */
    private fun registerScheme(scheme: String, exePath: String) {
        try {
            val root = com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER
            val base = "Software\\Classes\\$scheme"
            val command = "$base\\shell\\open\\command"
            val value = "\"$exePath\" \"%1\""
            com.sun.jna.platform.win32.Advapi32Util.registryCreateKey(root, "Software\\Classes", scheme)
            com.sun.jna.platform.win32.Advapi32Util.registrySetStringValue(root, base, "", "URL:$scheme Protocol")
            com.sun.jna.platform.win32.Advapi32Util.registrySetStringValue(root, base, "URL Protocol", "")
            com.sun.jna.platform.win32.Advapi32Util.registryCreateKey(root, base, "shell\\open\\command")
            com.sun.jna.platform.win32.Advapi32Util.registrySetStringValue(root, command, "", value)
            // read back: a failed write must show in the log
            val written = com.sun.jna.platform.win32.Advapi32Util.registryGetStringValue(root, command, "")
            if (written == value) Log.i(TAG, "Registered protocol: $scheme -> $value") else Log.w(TAG, "Protocol $scheme reads back as $written")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to register scheme $scheme: $t")
        }
    }
}
