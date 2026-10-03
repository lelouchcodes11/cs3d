package com.lagradost.desktop.runtime.ui

/**
 * Desktop mouse behaviour of a video player (set on media3's PlayerView by the player UI): moving
 * the pointer shows the controls, the wheel changes the volume, a double click toggles fullscreen,
 * and the cursor hides while the controls are hidden.
 */
interface DesktopPlayerInput {
    fun onPointerMoved()

    /** Wheel notches, positive = away from the user (volume up) */
    fun onWheel(steps: Int)
    fun onDoubleClick()
    fun isControllerShowing(): Boolean
}
