package com.lagradost.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.SingletonImageLoader
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.ImageLoader.buildImageLoader
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import com.lagradost.desktop.runtime.AndroidRuntime
import com.lagradost.desktop.runtime.ui.DisplaySync
import com.lagradost.desktop.tools.DevServer
import com.lagradost.desktop.ui.DesktopUiHost
import com.lagradost.desktop.platform.DesktopTrayNotifications
import com.lagradost.desktop.platform.SingleInstanceIpc
import com.lagradost.desktop.platform.WindowsProtocols
import java.awt.EventQueue
import java.awt.event.MouseEvent
import java.awt.AWTEvent
import java.awt.Toolkit
import kotlin.system.exitProcess

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    // Conscrypt must be the first TLS provider before OkHttp is touched for the first time: OkHttp picks its platform (and with it the way
    // HTTP/2 is negotiated) once. Otherwise every connection of the app stayed on HTTP/1.1 (also the DoH lookups: "Incorrect protocol: http/1.1")
    runCatching { java.security.Security.insertProviderAt(org.conscrypt.Conscrypt.newProvider(), 1) }
    com.lagradost.desktop.platform.AppIcon.setAppId()
    com.lagradost.desktop.platform.AppIcon.prepareAsync()
    // the engine's start (UI thread) would otherwise initialise this one after the other: the JSON mappers
    Thread({
        runCatching { Class.forName("com.lagradost.cloudstream3.MainAPIKt") }
    }, "warm-up").apply { isDaemon = true }.start()
    Thread({ runCatching { DesktopBootstrap.resourceIndex() } }, "resource-index").apply { isDaemon = true }.start()
    val startedAt = System.currentTimeMillis()
    com.lagradost.desktop.platform.StartupProfile.mark("main() entered")
    // a restarted instance waits for the previous process to exit (lock, data directory, ports)
    com.lagradost.desktop.platform.AppRestart.awaitPreviousInstance()

    // Single instance check: if an instance is already running, forward args and exit
    if (SingleInstanceIpc.sendArgsToExistingInstance(args)) {
        println("Forwarded arguments to running CloudStream instance.")
        exitProcess(0)
    }

    // Uncaught exceptions are logged (class, message and stack even when toString/printStackTrace fail)
    Thread.setDefaultUncaughtExceptionHandler { thread, e ->
        val summary = runCatching { e.toString() }.getOrElse { e.javaClass.name + " (toString failed: $it)" }
        val stack = runCatching { e.stackTrace.take(40).joinToString("\n\tat ") }.getOrDefault("")
        System.err.println("Uncaught exception in ${thread.name}: $summary\n\tat $stack")
        runCatching { android.util.Log.e("Uncaught", "${thread.name}: $summary\n\tat $stack") }
        var cause = runCatching { e.cause }.getOrNull()
        while (cause != null && cause !== e) {
            System.err.println("Caused by: " + runCatching { cause.toString() }.getOrDefault(cause.javaClass.name))
            cause = runCatching { cause.cause }.getOrNull()
        }
        // Write crash log file in user's app-data directory (WP11)
        runCatching {
            val dir = if (AndroidRuntime.isInitialized) AndroidRuntime.dataDir else AndroidRuntime.defaultDataDir()
            dir.mkdirs()
            val crashReport = buildString {
                appendLine("CloudStream Desktop Crash Log")
                appendLine("Date: ${java.util.Date()}")
                appendLine("Thread: ${thread.name}")
                appendLine("Exception: $summary")
                appendLine("Stack trace:")
                appendLine(stack)
                var c = e.cause
                while (c != null) {
                    appendLine("Caused by: ${c.javaClass.name}: ${c.message}")
                    appendLine(c.stackTrace.take(30).joinToString("\n\tat "))
                    c = c.cause
                }
            }
            java.io.File(dir, "crash.log").writeText(crashReport)
            val filesDir = java.io.File(dir, "files").also { it.mkdirs() }
            // "safe mode" (no extensions at the next start) only for a crash right after the start, which is when a bad
            // extension takes the app down; a stray exception in a background thread later must not empty the app
            if (System.currentTimeMillis() - startedAt < 60_000) java.io.File(filesDir, "last_error").writeText(crashReport)
        }
    }
    com.lagradost.desktop.platform.HangWatchdog.start()
    // the text stack (native Skia, the font manager and a first shaped paragraph) loads here, next to the engine start-up on the UI thread,
    // and not in front of the first frame
    Thread({
        runCatching {
            org.jetbrains.skia.impl.Library.staticLoad()
            val fonts = org.jetbrains.skia.FontMgr.default
            fonts.familiesCount
            val collection = org.jetbrains.skia.paragraph.FontCollection().setDefaultFontManager(fonts)
            val builder = org.jetbrains.skia.paragraph.ParagraphBuilder(org.jetbrains.skia.paragraph.ParagraphStyle(), collection)
            builder.pushStyle(org.jetbrains.skia.paragraph.TextStyle().setFontSize(14f))
            builder.addText("CloudStream 0123 abc")
            builder.build().layout(300f)
        }
    }, "ui-warmup").apply { isDaemon = true }.start()
    System.setProperty("skiko.renderApi", System.getProperty("skiko.renderApi") ?: "DIRECT3D")
    // Android dimensions (dp) must match the Compose density of the screen before anything resolves them
    runCatching {
        val gc = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
        val scale = gc.defaultTransform.scaleX.toFloat()
        val bounds = gc.bounds
        DisplaySync.sync(scale, 1f, (bounds.width * scale).toInt(), (bounds.height * scale).toInt())
    }
    val isLink = { arg: String -> arg.contains("://") || arg.startsWith("csshare") }
    EventQueue.invokeAndWait {
        DesktopBootstrap.initApplication()
        AndroidRuntime.host = DesktopUiHost()
    }
    com.lagradost.desktop.platform.StartupProfile.startSamplerIfRequested()
    com.lagradost.desktop.platform.StartupProfile.mark("application initialised")
    // upstream CloudStreamApp is the SingletonImageLoader.Factory, so the loader exists before any activity
    SingletonImageLoader.setSafe { buildImageLoader(AndroidRuntime.context) }
    // The engine starts without any Android UI; a deep link on the command line is handled like an intent
    EventQueue.invokeAndWait {
        DesktopBootstrap.startEngine()
        com.lagradost.desktop.core.NativeUi.active = true
        args.firstOrNull(isLink)?.let { NativeLinks.open(it) }
    }
    com.lagradost.desktop.platform.StartupProfile.mark("engine started")
    System.getProperty("cloudstream.devport")?.toIntOrNull()?.let { DevServer.start(it) }

    // Register URL protocol associations (WP11)
    WindowsProtocols.registerCurrentExecutable()

    // Initialize desktop tray notifications (WP10.1)
    DesktopTrayNotifications.init { DesktopUiHost.window }

    // Start single instance IPC listener (WP10.2)
    SingleInstanceIpc.startServer { receivedArgs ->
        EventQueue.invokeLater {
            DesktopUiHost.window?.let { w ->
                w.isVisible = true
                w.toFront()
                w.requestFocus()
            }
            receivedArgs.firstOrNull(isLink)?.let { NativeLinks.open(it) }
        }
    }

    // Mouse "back" button navigates back, like Android's back gesture
    Toolkit.getDefaultToolkit().addAWTEventListener({ event ->
        if (event is MouseEvent && event.id == MouseEvent.MOUSE_RELEASED && event.button == 4) {
            com.lagradost.desktop.core.Navigator.back()
        }
    }, AWTEvent.MOUSE_EVENT_MASK)

    // objects that hold Compose state are created here, on the UI thread before the first composition: created later by a
    // background thread (the update check) inside a running composition, their state could not be read and the window stayed blank
    EventQueue.invokeAndWait {
        com.lagradost.desktop.ui.Startup.stage
        com.lagradost.desktop.ui.fluent.Appearance.navPosition
    }

    application(exitProcessOnExit = false) {
        // An exception in the UI (e.g. an extension view) is logged, the window keeps running.
        // Compose's default handler shows a modal dialog that stops the whole window.
        CompositionLocalProvider(LocalWindowExceptionHandlerFactory provides UiExceptionHandlerFactory) {
            NativeWindow()
        }
    }
    // Stop background services (downloads, torrent server) and exit
    exitProcess(0)
}

/** Logs UI exceptions instead of Compose's modal error dialog, keeping the window usable */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
private object UiExceptionHandlerFactory : androidx.compose.ui.window.WindowExceptionHandlerFactory {
    override fun exceptionHandler(window: java.awt.Window) = androidx.compose.ui.window.WindowExceptionHandler { throwable ->
        android.util.Log.e("UI", "Uncaught exception in the window", throwable)
        com.lagradost.cloudstream3.mvvm.logError(throwable)
    }
}
