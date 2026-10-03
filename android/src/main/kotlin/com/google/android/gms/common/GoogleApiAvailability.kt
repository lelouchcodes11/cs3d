package com.google.android.gms.common

import android.content.Context

// desktop: Google Play services are never available, so cast and other GMS paths stay off
open class GoogleApiAvailability {
    open fun isGooglePlayServicesAvailable(context: Context): Int = ConnectionResult.SERVICE_MISSING
    open fun isGooglePlayServicesAvailable(context: Context, minApkVersion: Int): Int = ConnectionResult.SERVICE_MISSING

    companion object {
        private val instance = GoogleApiAvailability()

        @JvmStatic
        fun getInstance(): GoogleApiAvailability = instance
    }
}

object ConnectionResult {
    const val SUCCESS = 0
    const val SERVICE_MISSING = 1
    const val SERVICE_VERSION_UPDATE_REQUIRED = 2
    const val SERVICE_DISABLED = 3
}
