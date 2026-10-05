package com.lagradost.desktop.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.lagradost.desktop.player.Mpv
import com.sun.jna.Native
import com.sun.jna.Pointer
import kotlinx.coroutines.delay
import java.awt.Canvas
import java.io.File

/**
 * Prototype (dev tool, not used by the app): can mpv draw with its own GPU renderer into a window that Compose embeds (SwingPanel), with Compose
 * controls drawn above it? Run: ./gradlew :app:runEmbedProto -Pargs="<video file or url> [shotDir]".
 * What it checks: the picture appears in the panel (mpv `wid`), the Compose box over it is visible and clickable (interop blending), and what mpv reports
 * (vo, hwdec, display refresh, dropped frames) while it plays with display-resample + interpolation.
 */
fun main(args: Array<String>) {
    System.setProperty("compose.interop.blending", System.getProperty("compose.interop.blending") ?: "true")
    val source = args.getOrNull(0) ?: error("video file or url")
    val shotDir = args.getOrNull(1)?.let { File(it) }
    application {
        Window(onCloseRequest = ::exitApplication, title = "Embed prototype") {
            val canvas = remember { Canvas().apply { background = java.awt.Color.BLACK } }
            var clicks by remember { mutableIntStateOf(0) }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                SwingPanel(factory = { canvas }, modifier = Modifier.fillMaxSize())
            }
            // transparent window owned by this one, kept exactly over the canvas
            val overlay = androidx.compose.ui.window.rememberDialogState(position = androidx.compose.ui.window.WindowPosition(0.dp, 0.dp), size = androidx.compose.ui.unit.DpSize(400.dp, 300.dp))
            androidx.compose.ui.window.DialogWindow(onCloseRequest = {}, state = overlay, undecorated = true, transparent = true, resizable = false, focusable = true) {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.align(Alignment.BottomCenter).padding(32.dp).background(Color(0xCC101828)).clickable { clicks++ }.padding(20.dp)) {
                        BasicText("Compose controls over mpv (GPU). Clicks: $clicks", style = TextStyle(color = Color.White, fontSize = 22.sp))
                    }
                }
            }
            LaunchedEffect(Unit) {
                while (true) {
                    runCatching {
                        val at = canvas.locationOnScreen
                        overlay.position = androidx.compose.ui.window.WindowPosition(at.x.dp, at.y.dp)
                        overlay.size = androidx.compose.ui.unit.DpSize(canvas.width.dp, canvas.height.dp)
                    }
                    delay(30)
                }
            }
            LaunchedEffect(Unit) {
                delay(1500)
                if (System.getProperty("proto.nompv") != null) {
                    // is the panel itself visible (a black canvas)?
                    delay(1500)
                    if (shotDir != null) { shotDir.mkdirs(); window.isAlwaysOnTop = true; window.toFront(); delay(700); javax.imageio.ImageIO.write(java.awt.Robot().createScreenCapture(window.bounds), "png", File(shotDir, "embed-nompv.png")); window.isAlwaysOnTop = false }
                    System.exit(0)
                }
                val mpv = Mpv.INSTANCE
                val h = mpv.mpv_create()!!
                val hwnd: Pointer = Native.getComponentPointer(canvas)
                println("canvas hwnd=0x${java.lang.Long.toHexString(Pointer.nativeValue(hwnd))} size=${canvas.width}x${canvas.height}")
                fun opt(k: String, v: String) = println("  $k=$v -> ${mpv.mpv_set_option_string(h, k, v)}")
                opt("wid", Pointer.nativeValue(hwnd).toString())
                opt("vo", System.getProperty("proto.vo") ?: "gpu")
                opt("gpu-api", "d3d11")
                opt("hwdec", "auto-safe")
                opt("video-sync", "display-resample")
                opt("interpolation", "yes")
                opt("tscale", "oversample")
                opt("keep-open", "yes")
                opt("osc", "no")
                opt("input-default-bindings", "no")
                opt("input-vo-keyboard", "no")
                println("initialize -> " + mpv.mpv_initialize(h))
                mpv.mpv_command(h, arrayOf("loadfile", source, null))
                fun prop(n: String): String? = mpv.mpv_get_property_string(h, n)?.let { p -> try { p.getString(0, "UTF-8") } finally { mpv.mpv_free(p) } }
                repeat(40) { i ->
                    delay(2500)
                    println("t+${(i + 1) * 2.5}s pos=${prop("time-pos")} vo=${prop("current-vo")} hwdec=${prop("hwdec-current")} fps=${prop("container-fps")} display-fps=${prop("display-fps")} drops=${prop("frame-drop-count")}/${prop("decoder-frame-drop-count")}/${prop("vo-delayed-frame-count")} mistimed=${prop("mistimed-frame-count")} vsync-ratio=${prop("vsync-ratio")} clicks=$clicks")
                    if (shotDir != null && (i == 3 || i == 8)) {
                        shotDir.mkdirs()
                        // only this window: brought to the front for the shot
                        window.isAlwaysOnTop = true; window.toFront(); delay(700)
                        val img = java.awt.Robot().createScreenCapture(window.bounds)
                        window.isAlwaysOnTop = false
                        javax.imageio.ImageIO.write(img, "png", File(shotDir, "embed-$i.png"))
                    }
                }
                mpv.mpv_terminate_destroy(h)
                System.exit(0)
            }
        }
    }
}
