package com.lagradost.desktop

import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.R
import com.lagradost.desktop.player.MpvPlayer
import com.lagradost.desktop.runtime.AndroidRuntime
import com.lagradost.desktop.runtime.LogBuffer
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.NetworkInterface

/**
 * "Copy diagnostics" (Settings > About): what a bug report needs, as text: versions, the PC (Windows, graphics card, screens and their refresh
 * rates), network adapters that look like a VPN, the player's output and decoder, and the latest warnings and errors of the log. Addresses with
 * a token, key or password are masked, and nothing about accounts or the data folder is included.
 */
object Diagnostics {
    private val secret = Regex("""(?i)((?:token|key|apikey|api_key|auth|authorization|sig|signature|password|passwd|secret|cookie|session)=)[^&\s"']+""")

    /** Hides the value of every query parameter that looks like a secret */
    fun mask(text: String): String = secret.replace(text) { it.groupValues[1] + "…" }

    private fun run(vararg command: String, seconds: Long = 6): String = runCatching {
        val p = ProcessBuilder(*command).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(seconds, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly()
        out.trim()
    }.getOrDefault("")

    fun build(): String {
        val sb = StringBuilder()
        fun line(label: String, value: String?) { sb.append(label).append(": ").append(value?.takeIf { it.isNotBlank() } ?: "unknown").append('\n') }
        line("CloudStream for Windows", "${AppInfo.version}${if (AppInfo.isPreRelease) " (pre-release)" else ""}, engine ${com.lagradost.cloudstream3.BuildConfig.VERSION_NAME}")
        line("Windows", System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"))
        line("Java", System.getProperty("java.vendor") + " " + System.getProperty("java.version"))
        runCatching {
            val os = java.lang.management.ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean
            line("Memory / CPUs", "%.1f GB, %d logical CPUs, app heap max %d MB".format(os.totalMemorySize / 1e9, Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory() / 1_048_576))
        }
        val gpu = run("powershell", "-NoProfile", "-Command", "(Get-CimInstance Win32_VideoController | ForEach-Object { \$_.Name + ' (driver ' + \$_.DriverVersion + ')' }) -join '; '")
        line("Graphics", gpu)
        val screens = runCatching {
            GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.joinToString("; ") { d ->
                val m = d.displayMode
                "${d.iDstring.substringAfterLast('\\')} ${m.width}x${m.height}@${m.refreshRate} Hz"
            } + " (UI scale ${Toolkit.getDefaultToolkit().screenResolution / 96.0})"
        }.getOrNull()
        line("Screens", screens)
        val adapters = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }.map { it.displayName }
        }.getOrDefault(emptyList())
        val vpn = adapters.filter { n -> listOf("warp", "wintun", "wireguard", "vpn", "tap-", "nord", "proton", "openvpn", "tailscale", "zerotier").any { n.contains(it, ignoreCase = true) } }
        line("VPN-like adapters", if (vpn.isEmpty()) "none" else vpn.joinToString("; "))
        runCatching {
            val prefs = PreferenceManager.getDefaultSharedPreferences(AndroidRuntime.context)
            line("DNS setting", prefs.getInt(AndroidRuntime.context.getString(R.string.dns_key), 0).toString())
        }
        line("Smooth motion", com.lagradost.desktop.ui.fluent.Appearance.smoothMotion.toString())
        val player = MpvPlayer.active
        if (player != null) {
            line("Player", "output ${player.getMpvPropertyString("current-vo")}, decoder ${player.getMpvPropertyString("hwdec-current")}, video ${player.getMpvPropertyString("video-params/w")}x${player.getMpvPropertyString("video-params/h")} at ${player.getMpvPropertyString("container-fps")} fps, display ${player.getMpvPropertyString("display-fps")} Hz, dropped ${player.getMpvPropertyString("frame-drop-count")}/${player.getMpvPropertyString("decoder-frame-drop-count")}")
        }
        sb.append("\nLatest warnings and errors:\n")
        // lines are "MM-dd HH:mm:ss.SSS pid tid L tag: text": the level letter is in the first 40 characters
        val problems = LogBuffer.snapshot().filter { l -> l.length > 40 && l.substring(0, 40).let { it.contains(" W ") || it.contains(" E ") } && !l.contains("UltimaSync") }
        problems.takeLast(40).forEach { sb.append(mask(it.take(300))).append('\n') }
        if (problems.isEmpty()) sb.append("(none)\n")
        return sb.toString()
    }

    /** Builds the text on a worker thread and puts it on the clipboard; [done] gets the number of lines (UI thread not guaranteed) */
    fun copyToClipboard(done: (Int) -> Unit) {
        Thread({
            val text = build()
            runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
            done(text.lines().size)
        }, "Diagnostics").apply { isDaemon = true }.start()
    }
}
