package com.lagradost.desktop.ui

import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.lagradost.cloudstream3.R
import com.lagradost.desktop.ActivityStack
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.runtime.AndroidRuntime
import com.lagradost.desktop.runtime.ui.androidKeyEvent

/**
 * Keyboard input of the window. Like Android, keys reach the top activity's dispatchKeyEvent, which
 * is where the player listens (CommonActivity.keyEventListener). In the player the desktop keys
 * come first: F toggles fullscreen, Esc leaves fullscreen, arrow up/down are the volume keys.
 */
object KeyRouting {
    fun mainNavController(): androidx.navigation.NavController? {
        val act = DesktopBootstrap.activityOrNull() ?: return null
        val host = act.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment)
        return (host as? androidx.navigation.fragment.NavHostFragment)?.navController
    }

    private fun playerShowing(): Boolean =
        ActivityStack.top === DesktopBootstrap.activityOrNull() && DesktopUiHost.androidDialogs.none { it.isShowing() } &&
            mainNavController()?.currentDestination?.id == R.id.navigation_player

    private fun toActivity(event: androidx.compose.ui.input.key.KeyEvent, keyCode: Int? = null): Boolean {
        val activity = ActivityStack.top ?: return false
        val e = androidKeyEvent(event, keyCode) ?: return false
        return activity.dispatchKeyEvent(e)
    }

    /** Before the focused element; true consumes the key */
    fun onPreviewKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        val down = event.type == KeyEventType.KeyDown
        if (event.key == Key.F11) {
            if (down) AndroidRuntime.host.setFullscreen(!AndroidRuntime.host.isFullscreen())
            return true
        }
        if (!playerShowing()) return false
        return when (event.key) {
            Key.F -> {
                if (down) AndroidRuntime.host.setFullscreen(!AndroidRuntime.host.isFullscreen())
                true
            }
            Key.Escape -> if (AndroidRuntime.host.isFullscreen()) {
                if (down) AndroidRuntime.host.setFullscreen(false)
                true
            } else false
            Key.DirectionUp -> toActivity(event, KeyEvent.KEYCODE_VOLUME_UP)
            Key.DirectionDown -> toActivity(event, KeyEvent.KEYCODE_VOLUME_DOWN)
            // the player's own keys (space/P, arrows, M, N/B, L, S, ...) before any focused control
            else -> toActivity(event)
        }
    }

    /** After the focused element did not handle the key */
    fun onKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.key == Key.Escape) {
            if (event.type == KeyEventType.KeyUp) BackHandlers.dispatch()
            return true
        }
        return toActivity(event)
    }
}
