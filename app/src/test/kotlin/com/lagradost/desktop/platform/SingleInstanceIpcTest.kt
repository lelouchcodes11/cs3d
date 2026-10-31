package com.lagradost.desktop.platform

import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The single-instance port acts only on a sender that read the secret of the running app */
class SingleInstanceIpcTest {
    private lateinit var data: File
    private var port = 0

    @BeforeTest
    fun setUp() {
        data = File.createTempFile("ipc", "").also { it.delete(); it.mkdirs() }
        port = ServerSocket(0).use { it.localPort }
        System.setProperty("cloudstream.data", data.absolutePath)
        System.setProperty("cloudstream.ipcport", port.toString())
    }

    @AfterTest
    fun tearDown() {
        SingleInstanceIpc.stopServer()
        System.clearProperty("cloudstream.data")
        System.clearProperty("cloudstream.ipcport")
        data.deleteRecursively()
    }

    private fun raw(vararg lines: String): String? = Socket(InetAddress.getByName("127.0.0.1"), port).use { s ->
        s.soTimeout = 3000
        val out = s.getOutputStream().bufferedWriter()
        for (l in lines) out.write(l + "\n")
        out.flush()
        runCatching { s.getInputStream().bufferedReader().readLine() }.getOrNull()
    }

    @Test
    fun theAppsOwnSenderIsAnswered() {
        val got = CountDownLatch(1)
        var received: List<String> = emptyList()
        SingleInstanceIpc.startServer { received = it; got.countDown() }
        assertTrue(File(data, "ipc-$port.token").isFile, "the secret is written to the data folder")
        assertTrue(SingleInstanceIpc.sendArgsToExistingInstance(arrayOf("cloudstreamapp://example")))
        assertTrue(got.await(3, TimeUnit.SECONDS))
        assertEquals(listOf("cloudstreamapp://example"), received)
    }

    @Test
    fun aSenderWithoutTheSecretIsRefused() {
        var called = false
        SingleInstanceIpc.startServer { called = true }
        assertEquals("ERR", raw("CS_IPC_ARGS", "cloudstreamapp://evil", ""))
        assertEquals("ERR", raw("CS_IPC_ARGS2 wrong-secret", "cloudstreamapp://evil", ""))
        val token = File(data, "ipc-$port.token").readText().trim()
        // an oversized first line is dropped without being read to the end
        assertEquals(null, runCatching { raw("x".repeat(100_000)) }.getOrNull().takeIf { it == "OK" })
        Thread.sleep(200)
        assertFalse(called, "nothing was acted on")
        assertEquals("OK", raw("CS_IPC_ARGS2 $token", "cloudstreamapp://fine", ""))
    }
}
