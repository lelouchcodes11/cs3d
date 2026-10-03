package com.lagradost.desktop.runtime

import android.content.Intent

/**
 * Bridge from the Android API layer to the desktop UI. The application installs an implementation
 * in [AndroidRuntime.host]; the headless default logs instead of showing anything.
 */
interface UiHost {
    /** Show a transient message, equivalent of android.widget.Toast */
    fun showToast(message: CharSequence, long: Boolean)

    /** Open a url in the system browser */
    fun openUrl(url: String): Boolean

    /** Handle an Intent passed to Context.startActivity, return false if nothing could handle it */
    fun startActivity(intent: Intent): Boolean

    /** Activity.finish(): remove the activity from the task */
    fun finishActivity(activity: android.app.Activity) {}

    /** The window covers the whole screen (desktop fullscreen, like a video player) */
    fun isFullscreen(): Boolean = false
    fun setFullscreen(fullscreen: Boolean) {}

    /** Show a dialog (android.app.Dialog and subclasses) */
    fun showDialog(dialog: android.app.Dialog)

    /** Dismiss a dialog previously shown with [showDialog] */
    fun dismissDialog(dialog: android.app.Dialog)

    /**
     * Show a Material snackbar. The host calls [BaseTransientBottomBar.dispatchShown] once visible and
     * [BaseTransientBottomBar.dispatchDismiss] on timeout; the default shows it as a toast.
     */
    fun showSnackbar(bar: com.google.android.material.snackbar.BaseTransientBottomBar<*>) {
        val text = (bar as? com.google.android.material.snackbar.Snackbar)?.getText() ?: return
        showToast(text, bar.getDuration() != com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
    }

    /** Remove a snackbar from the screen (no callbacks, the bar dispatches them itself) */
    fun dismissSnackbar(bar: com.google.android.material.snackbar.BaseTransientBottomBar<*>) {}

    /**
     * Show [content] in a popup whose top-left is the window point ([x], [y]).
     * Returns a token for [dismissPopup]. The headless host dismisses immediately.
     */
    fun showPopup(x: Int, y: Int, anchorWidth: Int, anchorHeight: Int, content: android.view.View, onDismiss: () -> Unit): Any {
        onDismiss()
        return Unit
    }

    fun dismissPopup(token: Any) {}

    fun setClipboard(text: String)
    fun getClipboard(): String?

    enum class FileChooser { OPEN, SAVE, DIRECTORY }

    /** Native file/folder dialog, [callback] receives null when cancelled. Must not block. */
    fun chooseFile(mode: FileChooser, suggestedName: String?, mimeTypes: Array<String>?, callback: (java.io.File?) -> Unit) {
        callback(null)
    }

    object Headless : UiHost {
        override fun showToast(message: CharSequence, long: Boolean) {
            println("TOAST: $message")
        }

        override fun openUrl(url: String): Boolean {
            println("OPEN URL: $url")
            return false
        }

        override fun startActivity(intent: Intent): Boolean {
            println("START ACTIVITY: $intent")
            return false
        }

        override fun showDialog(dialog: android.app.Dialog) {
            println("SHOW DIALOG: $dialog")
        }

        override fun dismissDialog(dialog: android.app.Dialog) {}

        private var clip: String? = null
        override fun setClipboard(text: String) {
            clip = text
        }

        override fun getClipboard(): String? = clip
    }
}
