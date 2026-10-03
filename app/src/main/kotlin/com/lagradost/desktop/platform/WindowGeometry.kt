package com.lagradost.desktop.platform

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.lagradost.desktop.runtime.AndroidRuntime
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit
import java.io.File
import java.util.Properties

/** Remembers the main window's floating bounds and maximized state between launches */
object WindowGeometry {
    private val file: File get() = File(AndroidRuntime.dataDir, "window.properties")

    private data class Bounds(val x: Int, val y: Int, val width: Int, val height: Int)

    private var lastFloating: Bounds? = null
    private var maximized = false

    /** Work areas (screen bounds minus taskbars) in window coordinates, the primary screen first */
    private fun workAreas(): List<Rectangle> {
        val env = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val primary = env.defaultScreenDevice
        return (listOf(primary) + env.screenDevices.filter { it !== primary }).map { device ->
            val gc = device.defaultConfiguration
            val b = gc.bounds
            val i = Toolkit.getDefaultToolkit().getScreenInsets(gc)
            Rectangle(b.x + i.left, b.y + i.top, b.width - i.left - i.right, b.height - i.top - i.bottom)
        }
    }

    private fun load(): Pair<Bounds, Boolean>? = runCatching {
        if (!file.isFile) return null
        val p = Properties().apply { file.inputStream().use { load(it) } }
        Bounds(p.getProperty("x").toInt(), p.getProperty("y").toInt(), p.getProperty("width").toInt(), p.getProperty("height").toInt()) to
            (p.getProperty("maximized") == "true")
    }.getOrNull()

    private fun save() {
        val b = lastFloating ?: return
        runCatching {
            val p = Properties()
            p.setProperty("x", b.x.toString())
            p.setProperty("y", b.y.toString())
            p.setProperty("width", b.width.toString())
            p.setProperty("height", b.height.toString())
            p.setProperty("maximized", maximized.toString())
            file.outputStream().use { p.store(it, "CloudStream window") }
        }
    }

    /** The saved window if it is still visible on a screen, else 85% of the primary work area, centered */
    fun initialState(): WindowState {
        val areas = workAreas()
        val saved = load()
        val visible = saved?.first?.let { b ->
            val r = Rectangle(b.x, b.y, b.width, b.height)
            b.width >= 400 && b.height >= 300 && areas.any { a -> a.intersection(r).let { it.width >= 200 && it.height >= 150 } }
        } == true
        if (saved != null && visible) {
            val b = saved.first
            lastFloating = b
            maximized = saved.second
            return WindowState(
                placement = if (maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
                position = WindowPosition(b.x.dp, b.y.dp),
                size = DpSize(b.width.dp, b.height.dp),
            )
        }
        val area = areas.firstOrNull() ?: Rectangle(0, 0, 1280, 800)
        val w = minOf(1280, (area.width * 0.85).toInt())
        val h = minOf(860, (area.height * 0.85).toInt())
        return WindowState(position = WindowPosition(Alignment.Center), size = DpSize(w.dp, h.dp))
    }

    /** Called whenever the window moves, resizes or changes placement; fullscreen keeps the last state */
    fun update(state: WindowState) {
        if (WinChrome.fullscreen || WinChrome.pip) return
        when (state.placement) {
            WindowPlacement.Floating -> {
                maximized = false
                val pos = state.position
                if (pos is WindowPosition.Absolute && state.size.width.value > 0f && state.size.height.value > 0f) {
                    lastFloating = Bounds(pos.x.value.toInt(), pos.y.value.toInt(), state.size.width.value.toInt(), state.size.height.value.toInt())
                }
            }
            WindowPlacement.Maximized -> maximized = true
            WindowPlacement.Fullscreen -> return
        }
        save()
    }
}
