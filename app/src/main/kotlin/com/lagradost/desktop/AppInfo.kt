package com.lagradost.desktop

import com.lagradost.cloudstream3.BuildConfig

/**
 * What the app says about itself and where its people are. New versions come from the GitHub releases of [REPO]; the updater
 * (update/UpdateCheck.kt) downloads and installs them from there.
 */
object AppInfo {
    const val REPO = "lelouchcodes11/cs3d"
    const val REPO_URL = "https://github.com/$REPO"
    const val RELEASES_URL = "$REPO_URL/releases"

    /** News, new versions, help and feedback */

    /** The community server: chat, help, ideas */
    const val DISCORD_URL = "https://discord.gg/9XJQx8eYSG"

    /** Donations from India (UPI and cards) */
    const val DONATE_URL = "https://razorpay.me/@lelouch11"

    /** Donations from anywhere else: Buy Me a Coffee takes international cards */
    const val DONATE_INTL_URL = "https://buymeacoffee.com/lelouchcodes11"

    val version: String = BuildConfig.DESKTOP_VERSION

    /** 0.x versions are pre-releases */
    val isPreRelease: Boolean get() = version.startsWith("0.")
}
