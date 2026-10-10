package com.lagradost.desktop

import com.lagradost.cloudstream3.BuildConfig

/**
 * What the app says about itself and where its people are. There is no updater inside the app: a new version is
 * downloaded from the GitHub releases page, which is where every "update" link of the app goes.
 */
object AppInfo {
    const val REPO = "lelouchcodes11/cs3d"
    const val REPO_URL = "https://github.com/$REPO"
    const val RELEASES_URL = "$REPO_URL/releases"
    const val LATEST_RELEASE_URL = "$RELEASES_URL/latest"
    const val ISSUES_URL = "$REPO_URL/issues"

    /** News, new versions, help and feedback */
    const val TELEGRAM_URL = "https://t.me/cs3d_official"

    /** The community server: chat, help, ideas */
    const val DISCORD_URL = "https://discord.gg/u82JU5JDM"
    const val DONATE_URL = "https://razorpay.me/@lelouch11"

    val version: String = BuildConfig.DESKTOP_VERSION

    /** 0.x versions are pre-releases */
    val isPreRelease: Boolean get() = version.startsWith("0.")
}
