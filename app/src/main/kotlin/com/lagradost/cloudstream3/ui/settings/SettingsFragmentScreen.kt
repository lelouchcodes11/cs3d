package com.lagradost.cloudstream3.ui.settings

import android.content.ActivityNotFoundException
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityOptionsCompat
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError

/**
 * Upstream's settings screen holds this helper (4.9.1, "Fixed launch" #3316): a file or folder picker that can not be opened says so instead of
 * crashing. The desktop app has its own settings screens, so only the helper is kept here for the upstream files that call it.
 */
object SettingsFragmentScreen {
    fun <I> ActivityResultLauncher<I>.launchSafe(input: I, options: ActivityOptionsCompat? = null) {
        try {
            this.launch(input, options)
        } catch (_: ActivityNotFoundException) {
            showToast(R.string.app_not_found_error)
        } catch (t: Throwable) {
            showToast(t.toString())
            logError(t)
        }
    }
}
