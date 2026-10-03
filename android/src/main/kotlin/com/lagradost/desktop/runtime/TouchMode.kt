package com.lagradost.desktop.runtime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Android touch mode: entered by mouse input, left by keyboard navigation (like a d-pad). In touch
 * mode only focusableInTouchMode views (text fields) take focus, so focus highlights of buttons and
 * list items only show while navigating with the keyboard, as on a phone vs a TV.
 */
object TouchMode {
    var inTouchMode by mutableStateOf(true)

    /** TV layout: never in touch mode, focus always drives the UI */
    @Volatile
    var forceKeyboard = false

    fun onPointerInput() {
        if (!forceKeyboard && !inTouchMode) inTouchMode = true
    }

    fun onKeyNavigation() {
        if (inTouchMode) inTouchMode = false
    }
}
