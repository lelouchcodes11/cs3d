package com.lagradost.desktop.platform

import android.util.Log
import java.lang.management.ManagementFactory

/**
 * Settings > General > Performance > High performance: the app takes more of the PC so the window stays smooth under load.
 *
 *  - Memory: the garbage collector aims at a 2 GB heap instead of 768 MB (`SoftMaxHeapSize`, changeable while running), so it runs far less often
 *    while pages and pictures come and go (each run takes processor time from the window and the video).
 *  - Priority: the app's process runs above normal, so another program busy on the PC does not take the window's and the player's turns.
 *  - At the next start (Main, ImageModuleCoil): 512 MB of decoded pictures instead of 160, 256 MB of graphics textures instead of 64 and 1.5 GB of
 *    pictures on the disk instead of 400 MB, so pages that were seen before come back without being fetched, decoded and uploaded again; 32 pictures
 *    of a site at once instead of 16; and pages get the rows below the window ready further ahead (ComposeAhead).
 *
 * Applied at start and whenever the setting changes; switching back restores the normal values. The extension sandbox keeps its low priority.
 */
object HighPerformance {
    private const val TAG = "HighPerformance"
    private const val NORMAL_SOFT_MAX = 768L * 1024 * 1024
    private const val SMOOTH_SOFT_MAX = 2048L * 1024 * 1024
    private const val NORMAL_PRIORITY_CLASS = 0x00000020
    private const val ABOVE_NORMAL_PRIORITY_CLASS = 0x00008000

    /** Chosen when the app started (the caches are sized once, at start) */
    val atStart: Boolean by lazy { runCatching { !com.lagradost.desktop.ui.fluent.Appearance.lightFromPrefs() && com.lagradost.desktop.ui.fluent.Appearance.highPerformanceFromPrefs() }.getOrDefault(false) }

    /** The light look when the app started */
    val lightAtStart: Boolean by lazy { runCatching { com.lagradost.desktop.ui.fluent.Appearance.lightFromPrefs() }.getOrDefault(false) }

    /** What is in force now, for the diagnostics */
    @Volatile
    var note = "not applied"
        private set

    fun apply(on: Boolean) {
        val parts = ArrayList<String>()
        runCatching {
            val bean = ManagementFactory.getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean::class.java)
            val max = Runtime.getRuntime().maxMemory()
            val want = (if (on) SMOOTH_SOFT_MAX else NORMAL_SOFT_MAX).coerceAtMost(max)
            bean.setVMOption("SoftMaxHeapSize", want.toString())
            parts += "heap target ${want / (1024 * 1024)} MB"
        }.onFailure { parts += "heap target unchanged (${it.message})" }
        if (System.getProperty("os.name").orEmpty().lowercase().contains("win")) {
            runCatching {
                val k = com.sun.jna.platform.win32.Kernel32.INSTANCE
                val ok = k.SetPriorityClass(k.GetCurrentProcess(), com.sun.jna.platform.win32.WinDef.DWORD((if (on) ABOVE_NORMAL_PRIORITY_CLASS else NORMAL_PRIORITY_CLASS).toLong()))
                parts += if (ok) "priority ${if (on) "above normal" else "normal"}" else "priority unchanged"
            }.onFailure { parts += "priority unchanged (${it.message})" }
        }
        note = (if (on) "on: " else "off: ") + parts.joinToString(", ")
        Log.i(TAG, note)
    }
}
