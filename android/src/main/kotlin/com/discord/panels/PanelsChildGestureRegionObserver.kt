package com.discord.panels

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup

class PanelsChildGestureRegionObserver {
    interface GestureRegionsListener {
        fun onGestureRegionsUpdate(gestureRegions: List<Rect>)
    }

    object Provider {
        private val instance = PanelsChildGestureRegionObserver()
        @JvmStatic
        fun get(): PanelsChildGestureRegionObserver = instance
    }

    fun register(view: View) {}
    fun unregister(view: View) {}
    fun addGestureRegionsUpdateListener(listener: GestureRegionsListener) {}
    fun removeGestureRegionsUpdateListener(listener: GestureRegionsListener) {}
    fun addGestureRegionsUpdateListener(viewGroup: ViewGroup, listener: GestureRegionsListener) {}
    fun removeGestureRegionsUpdateListener(viewGroup: ViewGroup, listener: GestureRegionsListener) {}
}
