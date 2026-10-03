package com.lagradost.desktop

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.desktop.runtime.AndroidRuntime

/**
 * The task of this app's activities, as Android keeps it: the window shows the top activity, the
 * ones below are stopped. MainActivity is singleTask like in the upstream manifest.
 */
object ActivityStack {
    private const val TAG = "ActivityStack"

    val activities = mutableStateListOf<Activity>()

    private class Caller(val activity: Activity, val requestCode: Int)

    private val callers = HashMap<Activity, Caller>()

    /** Called when the last activity finishes, like Android closing the task */
    var onEmpty: () -> Unit = {}

    val top: Activity? get() = activities.lastOrNull()

    fun isAppActivity(intent: Intent): Boolean {
        val name = intent.component?.className ?: return false
        return try {
            Activity::class.java.isAssignableFrom(Class.forName(name, false, ActivityStack::class.java.classLoader))
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    /** Context.startActivity for an activity of this app. Must run on the main thread. */
    fun start(intent: Intent) {
        val cls = Class.forName(intent.component!!.className)
        val requestCode = intent.getIntExtra("desktop.requestCode", -1)
        val requester = intent.getIntExtra("desktop.requestActivity", 0).let { id ->
            activities.lastOrNull { System.identityHashCode(it) == id }
        }

        if (cls == MainActivity::class.java) {
            val existing = activities.firstOrNull { it is MainActivity }
            if (existing != null) {
                // singleTask: clear what is above it and deliver the intent
                while (activities.last() !== existing) destroy(activities.last(), resumeBelow = false)
                existing.performNewIntent(intent)
                existing.performRestartIfStopped()
                return
            }
        }

        val activity = cls.getDeclaredConstructor().newInstance() as Activity
        activity.attach(AndroidRuntime.context, AndroidRuntime.applicationContext as? android.app.Application)
        activity.setIntent(intent)
        if (activity is MainActivity) DesktopBootstrap.setMainActivity(activity)
        if (requester != null && requestCode >= 0) callers[activity] = Caller(requester, requestCode)

        val previous = top
        previous?.performPause()
        activities.add(activity)
        Log.d(TAG, "start ${cls.simpleName}, stack=${activities.map { it.javaClass.simpleName }}")
        activity.performCreate(null)
        // onCreate may already have finished it (AccountSelectActivity forwarding to MainActivity)
        if (activity.isFinishing()) return
        activity.performStart()
        activity.performResume()
        previous?.performStop()
    }

    /** Activity.finish(). Must run on the main thread. */
    fun finish(activity: Activity) {
        if (activity !in activities) return
        destroy(activity, resumeBelow = true)
    }

    private fun destroy(activity: Activity, resumeBelow: Boolean) {
        val wasTop = activity === top
        if (!activity.isDestroyed()) {
            safeStep { activity.performPause() }
            safeStep { activity.performStop() }
            safeStep { activity.performDestroy() }
        }
        activities.remove(activity)
        Log.d(TAG, "finish ${activity.javaClass.simpleName}, stack=${activities.map { it.javaClass.simpleName }}")

        callers.remove(activity)?.let { caller ->
            safeStep { caller.activity.dispatchActivityResult(caller.requestCode, activity.resultCodeForHost(), activity.resultDataForHost()) }
        }

        val below = top
        if (below == null) {
            onEmpty()
            return
        }
        if (resumeBelow && wasTop) {
            CommonActivity.setActivityInstance(below)
            below.performRestartIfStopped()
        }
    }

    /** Window close: every activity is paused, stopped and destroyed from the top */
    fun destroyAll() {
        while (activities.isNotEmpty()) {
            val activity = activities.last()
            safeStep { activity.performPause() }
            safeStep { activity.performStop() }
            safeStep { activity.performDestroy() }
            activities.remove(activity)
        }
    }

    private inline fun safeStep(step: () -> Unit) {
        try {
            step()
        } catch (t: Throwable) {
            Log.e(TAG, "lifecycle step failed", t)
        }
    }
}
