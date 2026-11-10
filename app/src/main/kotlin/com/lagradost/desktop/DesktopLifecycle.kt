package com.lagradost.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.desktop.runtime.AndroidRuntime
import java.awt.EventQueue
import javax.swing.Timer

/**
 * The activity lifecycle callbacks Android apps (and extensions) listen to, for the native UI where the engine's activity never
 * runs onCreate/onStart/onResume. Extensions such as Ultima pull changes from their cloud sync in `onActivityResumed` and
 * refresh the app afterwards only while the activity is resumed:
 *  - the engine activity counts as resumed from the start of the engine on;
 *  - when the window gains the focus the callbacks get `onActivityResumed` (at most every [RESUME_GAP_MS]), when it loses it `onActivityPaused`;
 *  - a callback registered later (a plugin that loads while the app is open) is told that the activity was created, started and resumed.
 */
object DesktopLifecycle {
    private const val TAG = "DesktopLifecycle"
    private const val RESUME_GAP_MS = 15_000L
    private const val LATE_REGISTRATION_DELAY_MS = 2_500

    @Volatile
    private var activity: MainActivity? = null

    @Volatile
    private var lastResumeAt = 0L

    fun install(act: MainActivity) {
        activity = act
        act.desktopSetResumed(true)
        val app = AndroidRuntime.applicationContext as? Application ?: return
        app.onLifecycleCallbackRegistered = { callback ->
            // the plugin has finished its own start by then
            val timer = Timer(LATE_REGISTRATION_DELAY_MS) {
                val a = activity
                Log.i(TAG, "late lifecycle callback ${callback.javaClass.name}: created, started, resumed")
                if (a != null) safe(callback) { cb ->
                    cb.onActivityCreated(a, null as Bundle?)
                    cb.onActivityStarted(a)
                    cb.onActivityResumed(a)
                }
            }
            timer.isRepeats = false
            timer.start()
        }
    }

    /**
     * False while the main window is not the active one (another app in front, or the window minimised): what only decorates the page
     * (the Home banner turning its slides) stops then. Home kept redrawing the whole window 8 times a second for its banner's progress line,
     * also minimised: 9-10 % of a core for nothing, measured.
     */
    var windowActive by androidx.compose.runtime.mutableStateOf(true)
        private set

    /** The main window gained or lost the keyboard focus (called on the UI thread) */
    fun windowFocus(gained: Boolean) {
        windowActive = gained
        val a: Activity = activity ?: return
        if (gained) com.lagradost.desktop.platform.PrefsBackup.maybeBackup("window focus")
        val app = AndroidRuntime.applicationContext as? Application ?: return
        if (gained) {
            val now = System.currentTimeMillis()
            if (now - lastResumeAt < RESUME_GAP_MS) return
            lastResumeAt = now
        }
        val callbacks = app.lifecycleCallbacks()
        if (callbacks.isNotEmpty()) Log.i(TAG, "window ${if (gained) "gained" else "lost"} the focus: ${callbacks.size} lifecycle callback(s) told")
        for (callback in callbacks) {
            safe(callback) { cb -> if (gained) cb.onActivityResumed(a) else cb.onActivityPaused(a) }
        }
    }

    private fun safe(callback: Application.ActivityLifecycleCallbacks, block: (Application.ActivityLifecycleCallbacks) -> Unit) {
        EventQueue.invokeLater {
            try {
                block(callback)
            } catch (t: Throwable) {
                Log.w(TAG, "lifecycle callback failed: ${t.message}", t)
            }
        }
    }
}
