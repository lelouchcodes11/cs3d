package com.lagradost.desktop.net

import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager
import kotlin.test.Test
import kotlin.test.assertEquals

/** The reason a download failed, in plain words for its row on the Downloads page */
class DownloadFailureTextTest {
    private fun t(raw: String?) = VideoDownloadManager.failureText(raw)

    @Test
    fun commonReasons() {
        assertEquals("the disk is full", t("There is not enough space on the disk"))
        assertEquals("the source refused it (its link may have expired)", t("HTTP 403 Forbidden"))
        assertEquals("the file is no longer on the source", t("Response code 404"))
        assertEquals("no source was found for it", t("no links"))
        assertEquals("the connection kept breaking off (try again carries on where it stopped)", t("cut off"))
        assertEquals("the connection kept breaking off (try again carries on where it stopped)", t("SocketTimeoutException: timeout"))
        assertEquals("there is not enough room on the disk (4.2 GB needed, 1.0 GB free)", t("not enough space: 4.2 GB needed, 1.0 GB free"))
        assertEquals(true, VideoDownloadManager.isConnectionDrop("a piece could not be fetched"))
        assertEquals(false, VideoDownloadManager.isConnectionDrop("HTTP 403"))
        assertEquals("no source could be saved", t(null))
        // a size in a message is not a status code
        assertEquals("no source could be saved", t("wrote 14031 bytes"))
    }
}
