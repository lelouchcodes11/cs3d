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

    private fun registerScheme(scheme: String, exePath: String) {
        try {
            val baseKey = "HKCU\\Software\\Classes\\$scheme"
            val commandKey = "$baseKey\\shell\\open\\command"
            val commandVal = "\"$exePath\" \"%1\""

            ProcessBuilder("reg", "add", baseKey, "/ve", "/d", "URL:$scheme Protocol", "/f").start().waitFor()
            ProcessBuilder("reg", "add", baseKey, "/v", "URL Protocol", "/d", "", "/f").start().waitFor()
            ProcessBuilder("reg", "add", commandKey, "/ve", "/d", commandVal, "/f").start().waitFor()
            Log.d(TAG, "Registered protocol: $scheme -> $commandVal")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to register scheme $scheme: $t")
        }
    }
}
