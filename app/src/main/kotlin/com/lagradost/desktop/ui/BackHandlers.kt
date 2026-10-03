package com.lagradost.desktop.ui

import com.lagradost.desktop.ActivityStack
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Back navigation for the desktop window (Escape, mouse back button, back arrow), delivered to the
 * top activity like Android's back key: its OnBackPressedDispatcher callbacks first, then compose
 * BackHandlers, then the activity's default (finish).
 */
object BackHandlers {
    class Handler(val enabled: () -> Boolean, val onBack: () -> Unit)

    private val handlers = CopyOnWriteArrayList<Handler>()

    fun add(handler: Handler) {
        handlers.add(handler)
    }

    fun remove(handler: Handler) {
        handlers.remove(handler)
    }

    /** @return true if something handled the back press */
    fun dispatch(): Boolean {
        // A showing dialog has the focused window, so it receives back first (Dialog.onBackPressed)
        DesktopUiHost.androidDialogs.lastOrNull { it.isShowing() }?.let {
            it.onBackPressed()
            return true
        }
        val activity = ActivityStack.top ?: return false
        if (activity is androidx.activity.ComponentActivity && activity.onBackPressedDispatcher.hasEnabledCallbacks()) {
            activity.onBackPressedDispatcher.onBackPressed()
            return true
        }
        val handler = handlers.lastOrNull { it.enabled() }
        if (handler != null) {
            handler.onBack()
            return true
        }
        activity.onBackPressed()
        return true
    }
}
