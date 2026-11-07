package com.lagradost.desktop.ui

import android.app.Dialog
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.desktop.ActivityStack
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.DesktopPlatform
import com.lagradost.desktop.runtime.UiHost
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager

/** Toast messages shown at the bottom of the window */
object Toasts {
    data class Toast(val id: Long, val text: String, val long: Boolean)

    val current = mutableStateListOf<Toast>()
    private var ids = 0L

    fun show(text: String, long: Boolean) {
        EventQueue.invokeLater {
            // Like Android, a new toast replaces the one showing
            current.clear()
            current.add(Toast(++ids, text, long))
        }
    }

    fun dismiss(toast: Toast) {
        current.remove(toast)
    }
}

/**
 * The desktop implementation of the Android runtime's UI bridge: toasts, intents (browser, deep
 * links, app pages), Android dialogs (rendered by the compose dialog host), clipboard and native
 * file dialogs.
 */
class DesktopUiHost : UiHost {
    companion object {
        private const val TAG = "DesktopUiHost"

        /** Android dialogs currently shown, rendered by the compose dialog host */
        val androidDialogs = mutableStateListOf<Dialog>()

        /** Dialogs asked for while a page dialog is open (or the other way round): they come up one after the other */
        private val waiting = ArrayDeque<Dialog>()

        /** The Reload button of Home: every dialog holding a page (an extension's check) is closed, through its own Cancel when it has one */
        fun cancelPageDialogs() {
            EventQueue.invokeLater {
                val gate = com.lagradost.desktop.runtime.web.CloudflareGate
                for (d in (androidDialogs.toList() + waiting.toList())) {
                    val decor = d.getWindow().getDecorView()
                    if (gate.hasWebView(decor)) gate.cancelBrowserDialog(decor) { runCatching { d.cancel() } }
                }
            }
        }

        /** Back / Esc for the topmost Android dialog: it cancels itself when it is cancelable; true when there was one */
        fun closeTopAndroidDialog(): Boolean {
            val dialog = androidDialogs.lastOrNull { it.isShowing() } ?: return false
            dialog.onBackPressed()
            return true
        }

        /** Anchored popups (PopupMenu, PopupWindow), drawn by AppRoot */
        val popups = mutableStateListOf<Popup>()

        data class Popup(
            val id: Long,
            val x: Int,
            val y: Int,
            val content: android.view.View,
            val onDismiss: () -> Unit,
        )

        private var popupIds = 0L

        /** The main window, parent of native dialogs */
        @Volatile
        var window: Frame? = null

        /** The main window's state (placement for fullscreen) */
        @Volatile
        var windowState: androidx.compose.ui.window.WindowState? = null
        private var placementBeforeFullscreen = androidx.compose.ui.window.WindowPlacement.Floating
    }

    override fun showToast(message: CharSequence, long: Boolean) {
        Toasts.show(message.toString(), long)
    }

    override fun showSnackbar(bar: com.google.android.material.snackbar.BaseTransientBottomBar<*>) {
        val snackbar = bar as? com.google.android.material.snackbar.Snackbar ?: return
        Snackbars.show(snackbar)
    }

    override fun dismissSnackbar(bar: com.google.android.material.snackbar.BaseTransientBottomBar<*>) {
        Snackbars.remove(bar)
    }

    override fun openUrl(url: String): Boolean {
        // an extension may open a web page or a mail address, not a file or a program (Desktop.browse starts the program a file:// address belongs to)
        val extension = com.lagradost.desktop.sandbox.PluginSandbox.extensionOnStack()
        if (extension != null) {
            val scheme = runCatching { java.net.URI(url.trim()).scheme?.lowercase() }.getOrNull()
            if (scheme != "http" && scheme != "https" && scheme != "mailto") com.lagradost.desktop.sandbox.PluginSandbox.refuse(extension, "open the address $url")
        }
        return com.lagradost.desktop.sandbox.SandboxGate.run(java.util.function.Supplier { DesktopPlatform.openExternalBrowser(url) })
    }

    override fun startActivity(intent: Intent): Boolean {
        // An extension can ask for a page, a file or the clipboard. The app does these for it, and for safe things only: the extension's own rights stop
        // at the sandbox (see PluginSandbox), so the work is done as the app's own (SandboxGate) after the extension was found out and its request judged below.
        val extension = com.lagradost.desktop.sandbox.PluginSandbox.extensionOnStack()
        return com.lagradost.desktop.sandbox.SandboxGate.run(java.util.function.Supplier { startActivityAsApp(intent, extension) })
    }

    private fun startActivityAsApp(intent: Intent, extension: String?): Boolean {
        Log.d(TAG, "startActivity $intent")
        val action = intent.action
        val data = intent.dataString
        val component = intent.component?.className

        if (action?.startsWith("desktop.intent.action.START_SERVICE") == true) return true

        // desktop: extensions restart the app by starting its launcher activity (Intent.makeRestartActivityTask)
        // and then killing the process; here that relaunches the program
        if (com.lagradost.desktop.platform.AppRestart.isRestartIntent(intent)) {
            com.lagradost.desktop.platform.AppRestart.request()
            return true
        }

        // Activities of this app run in the window's task. Queued like Android, so an activity that
        // starts another and then finishes itself (AccountSelectActivity) keeps that order.
        if (component != null && ActivityStack.isAppActivity(intent)) {
            EventQueue.invokeLater { ActivityStack.start(intent) }
            return true
        }

        if (action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (text != null) {
                setClipboard(text)
                showToast("Copied to clipboard", false)
                return true
            }
        }

        if (data != null && (action == null || action == Intent.ACTION_VIEW) && intent.`package` == null) {
            val scheme = intent.data?.scheme?.lowercase()
            val isLoopbackAuth = scheme == "http" && (
                data.contains("localhost:${com.lagradost.desktop.net.OAuthCallback.PORT}/") ||
                data.contains("127.0.0.1:${com.lagradost.desktop.net.OAuthCallback.PORT}/") ||
                data.contains("[::1]:${com.lagradost.desktop.net.OAuthCallback.PORT}/")
            )
            when {
                isLoopbackAuth -> {
                    var handled = false
                    EventQueue.invokeAndWait {
                        handled = MainActivity.handleAppIntentUrl(DesktopBootstrap.activity, data, false, intent.extras)
                    }
                    if (handled) return true
                }
                scheme == "http" || scheme == "https" || scheme == "mailto" -> return openUrl(data)
                scheme == null -> {}
                else -> {
                    // cloudstreamapp://, cloudstreamrepo://, csshare: ... deep links into the app
                    var handled = false
                    EventQueue.invokeAndWait {
                        handled = MainActivity.handleAppIntentUrl(DesktopBootstrap.activity, data, false, intent.extras)
                    }
                    if (handled) return true
                    if (scheme == "file") {
                        val file = File(intent.data!!.path!!)
                        // a program, a script or a shortcut is run when it is opened: an extension may only ask for passive files (media, subtitles, pictures, documents)
                        if (extension != null && !com.lagradost.desktop.sandbox.SafeOpen.isPassive(file)) com.lagradost.desktop.sandbox.PluginSandbox.refuse(extension, "open the file ${file.name}")
                        return DesktopPlatform.openFile(file)
                    }
                }
            }
        }
        // Intents for other Android apps (external players, ...) can not be handled
        return false
    }

    override fun isFullscreen(): Boolean =
        com.lagradost.desktop.platform.WinChrome.fullscreen || windowState?.placement == androidx.compose.ui.window.WindowPlacement.Fullscreen

    override fun setFullscreen(fullscreen: Boolean) {
        EventQueue.invokeLater {
            // borderless window over the monitor (integrated title bar); the AWT full screen is the fallback
            val window = window
            if (window != null && com.lagradost.desktop.platform.WinChrome.setFullscreen(window, fullscreen)) return@invokeLater
            val state = windowState ?: return@invokeLater
            val isFull = state.placement == androidx.compose.ui.window.WindowPlacement.Fullscreen
            if (fullscreen == isFull) return@invokeLater
            if (fullscreen) {
                placementBeforeFullscreen = state.placement
                state.placement = androidx.compose.ui.window.WindowPlacement.Fullscreen
            } else {
                state.placement = placementBeforeFullscreen
            }
        }
    }

    override fun finishActivity(activity: android.app.Activity) {
        EventQueue.invokeLater { ActivityStack.finish(activity) }
    }

    override fun showDialog(dialog: Dialog) {
        EventQueue.invokeLater {
            // desktop: the native UI shows simple Android dialogs (message, buttons, plain lists) as Fluent dialogs
            if (com.lagradost.desktop.core.NativeUi.active && FluentAlerts.show(dialog)) return@invokeLater
            if (androidDialogs.contains(dialog) || waiting.contains(dialog)) return@invokeLater
            // a page in a dialog (a Cloudflare check) is a window of its own that is drawn above everything: it may not share the screen with another
            // dialog (the donation popup of an extension under it showed through it as a smear), the second one waits for the first
            if (androidDialogs.isNotEmpty() && (holdsPage(dialog) || androidDialogs.any { holdsPage(it) })) waiting.addLast(dialog) else androidDialogs.add(dialog)
        }
    }

    private fun holdsPage(dialog: Dialog) = com.lagradost.desktop.runtime.web.CloudflareGate.hasWebView(dialog.getWindow().getDecorView())

    override fun dismissDialog(dialog: Dialog) {
        EventQueue.invokeLater {
            waiting.remove(dialog)
            androidDialogs.remove(dialog)
            FluentAlerts.dismiss(dialog)
            if (androidDialogs.isEmpty()) waiting.removeFirstOrNull()?.let { if (it.isShowing()) androidDialogs.add(it) }
        }
    }

    override fun showPopup(
        x: Int,
        y: Int,
        anchorWidth: Int,
        anchorHeight: Int,
        content: android.view.View,
        onDismiss: () -> Unit,
    ): Any {
        val popup = Popup(++popupIds, x, y, content, onDismiss)
        EventQueue.invokeLater { popups.add(popup) }
        return popup.id
    }

    override fun dismissPopup(token: Any) {
        EventQueue.invokeLater {
            val id = token as? Long ?: return@invokeLater
            popups.removeAll { it.id == id }
        }
    }

    override fun setClipboard(text: String) {
        try {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        } catch (t: Throwable) {
            Log.w(TAG, "Clipboard: $t")
        }
    }

    override fun getClipboard(): String? = try {
        Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
    } catch (_: Throwable) {
        null
    }

    override fun chooseFile(
        mode: UiHost.FileChooser,
        suggestedName: String?,
        mimeTypes: Array<String>?,
        callback: (File?) -> Unit
    ) {
        EventQueue.invokeLater {
            val result: File? = try {
                when (mode) {
                    UiHost.FileChooser.DIRECTORY -> {
                        try {
                            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
                        } catch (_: Throwable) {
                        }
                        val chooser = JFileChooser()
                        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                        chooser.isAcceptAllFileFilterUsed = false
                        if (chooser.showOpenDialog(window) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
                    }

                    else -> {
                        val dialog = FileDialog(window, null, if (mode == UiHost.FileChooser.SAVE) FileDialog.SAVE else FileDialog.LOAD)
                        if (suggestedName != null) dialog.file = suggestedName
                        val extensions = mimeTypes?.mapNotNull { mimeToExtension(it) }?.flatten().orEmpty()
                        if (extensions.isNotEmpty() && mode == UiHost.FileChooser.OPEN) {
                            dialog.setFilenameFilter { _, name -> extensions.any { name.lowercase().endsWith(it) } }
                            if (DesktopPlatform.isWindows) dialog.file = extensions.joinToString(";") { "*$it" }
                        }
                        dialog.isVisible = true
                        val file = dialog.file
                        val dir = dialog.directory
                        if (file != null && dir != null) File(dir, file) else null
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "File chooser failed: $t")
                null
            }
            Thread { callback(result) }.start()
        }
    }

    private fun mimeToExtension(mime: String): List<String>? = when (mime.lowercase()) {
        "*/*" -> null
        "application/json", "text/json" -> listOf(".json", ".txt")
        "text/plain" -> listOf(".txt")
        "application/zip" -> listOf(".zip")
        "application/octet-stream" -> null
        "font/ttf", "font/otf", "application/x-font-ttf" -> listOf(".ttf", ".otf")
        "text/vtt" -> listOf(".vtt")
        "application/x-subrip" -> listOf(".srt")
        else -> when {
            mime.startsWith("video/") -> listOf(".mp4", ".mkv", ".webm", ".avi", ".mov", ".m4v", ".ts", ".m3u8")
            mime.startsWith("audio/") -> listOf(".mp3", ".m4a", ".aac", ".flac", ".ogg", ".opus", ".wav")
            mime.startsWith("image/") -> listOf(".png", ".jpg", ".jpeg", ".webp", ".gif")
            else -> null
        }
    }
}
