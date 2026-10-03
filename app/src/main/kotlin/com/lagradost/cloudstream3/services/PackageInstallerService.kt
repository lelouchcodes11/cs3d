package com.lagradost.cloudstream3.services

import android.app.Service
import android.content.Intent
import android.os.IBinder

// desktop: no APK installations on desktop
class PackageInstallerService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
