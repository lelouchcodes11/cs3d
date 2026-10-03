package com.lagradost.cloudstream3.ui.player

import android.app.Activity
import android.content.Context
import android.util.Rational

object PlayerPipHelper {
    /** Desktop has no picture-in-picture mode support */
    fun Context.isPIPPossible(): Boolean = false
    fun Activity.isPIPPossible(): Boolean = false

    fun updatePIPModeActions(
        activity: Activity?,
        status: CSPlayerLoading,
        pipEnabled: Boolean,
        aspectRatio: Rational?
    ) {
        // No-op on desktop
    }
}
