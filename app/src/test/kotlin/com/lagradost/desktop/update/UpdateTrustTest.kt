package com.lagradost.desktop.update

import com.lagradost.desktop.AppInfo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which addresses the updater may download from */
class UpdateTrustTest {
    private val prefix = "https://github.com/${AppInfo.REPO}/releases/download/v1.0.8/"

    @Test
    fun onlyTheRepositorysReleaseFilesAreTrusted() {
        assertTrue(UpdateCheck.trustedRepositoryDownload(prefix + "CloudStream-1.0.8-windows-x64-setup.exe"))
        assertFalse(UpdateCheck.trustedRepositoryDownload("https://github.com/someone-else/repo/releases/download/v1/file.exe"))
        assertFalse(UpdateCheck.trustedRepositoryDownload("http://github.com/${AppInfo.REPO}/releases/download/v1/file.exe"))
    }

    @Test
    fun lookAlikeHostsPathsAndExtrasAreRefused() {
        assertFalse(UpdateCheck.trustedRepositoryDownload(prefix.replace("github.com", "github.com.evil.test") + "file.exe"))
        assertFalse(UpdateCheck.trustedRepositoryDownload(prefix.replace("https://github.com", "https://github.com@evil.test") + "file.exe"))
        assertFalse(UpdateCheck.trustedRepositoryDownload(prefix.replace("github.com", "github.com:444") + "file.exe"))
        assertFalse(UpdateCheck.trustedRepositoryDownload(prefix + "../../file.exe"))
        assertFalse(UpdateCheck.trustedRepositoryDownload(prefix + "file.exe?x=1"))
        assertFalse(UpdateCheck.trustedRepositoryDownload(prefix + "file.exe#top"))
    }
}
