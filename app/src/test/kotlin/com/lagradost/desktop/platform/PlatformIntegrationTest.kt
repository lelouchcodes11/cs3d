package com.lagradost.desktop.platform

import android.content.ContentResolver
import android.net.Uri
import com.lagradost.desktop.DesktopPlatform
import com.lagradost.safefile.SafeFile
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlatformIntegrationTest {

    @Test
    fun testContentResolverWindowsPathNormalization() {
        val uri = Uri.parse("file:///C:/Users/test/video.mp4")
        val resolved = ContentResolver.resolveToFile(uri)
        assertNotNull(resolved, "Resolved file should not be null")
        if (DesktopPlatform.isWindows) {
            assertEquals("C:\\Users\\test\\video.mp4", resolved.path)
        }
    }

    @kotlin.test.BeforeTest
    fun setUp() {
        val tempDir = java.nio.file.Files.createTempDirectory("platform-test").toFile()
        com.lagradost.desktop.runtime.AndroidRuntime.init(tempDir)
    }

    @Test
    fun testSafeFilePathNormalization() {
        // SafeFile from URI on Windows
        val uri = Uri.parse("file:///C:/Users/test/backup.txt")
        val ctx = com.lagradost.desktop.runtime.ContextImpl.create()
        val safeFile = SafeFile.fromUri(ctx, uri)
        assertNotNull(safeFile, "SafeFile should resolve file URI")
        if (DesktopPlatform.isWindows) {
            assertEquals("C:\\Users\\test\\backup.txt", safeFile.filePath())
        }
    }

    @Test
    fun testSingleInstanceIpcForwarding() {
        val testPort = 53535
        System.setProperty("cloudstream.ipcport", testPort.toString())

        val latch = CountDownLatch(1)
        var receivedArgs: List<String>? = null

        try {
            SingleInstanceIpc.startServer { args ->
                receivedArgs = args
                latch.countDown()
            }

            val testArgs = arrayOf("cloudstreamrepo://https://example.com/repo", "test_query")
            val sent = SingleInstanceIpc.sendArgsToExistingInstance(testArgs)
            assertTrue(sent, "sendArgsToExistingInstance should return true when server is running")

            val completed = latch.await(5, TimeUnit.SECONDS)
            assertTrue(completed, "Server should receive forwarded arguments within timeout")
            assertNotNull(receivedArgs)
            assertEquals(2, receivedArgs?.size)
            assertEquals("cloudstreamrepo://https://example.com/repo", receivedArgs?.get(0))
            assertEquals("test_query", receivedArgs?.get(1))
        } finally {
            SingleInstanceIpc.stopServer()
            System.clearProperty("cloudstream.ipcport")
        }
    }
}
