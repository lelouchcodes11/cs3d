package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.services.PackageInstallerService

// desktop: no APK installations on desktop
class ApkInstaller(private val service: PackageInstallerService? = null) {
    class DelayedInstaller {
        fun startInstallation(): Boolean = false
    }

    enum class InstallProgressStatus {
        Preparing,
        Downloading,
        Installing,
        Failed,
    }

    companion object {
        var delayedInstaller: DelayedInstaller? = null
    }
}
