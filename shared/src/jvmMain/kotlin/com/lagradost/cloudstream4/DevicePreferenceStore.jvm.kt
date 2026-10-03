package com.lagradost.cloudstream4

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.lagradost.desktop.runtime.AndroidRuntime
import com.mihon.common.preference.AndroidPreferenceStore

/**
 * Same as the Android actual: settings live in the default SharedPreferences
 * ("<package>_preferences.xml"), shared with the rest of the app and with extensions.
 */
@Composable
actual fun rememberAppSettings(): AppSettings {
    val context = AndroidRuntime.context
    return remember(context) { AppSettings(context) }
}

fun AppSettings(context: Context): AppSettings {
    return AppSettings(preferences = AndroidPreferenceStore(context))
}
