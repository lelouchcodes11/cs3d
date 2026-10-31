package com.lagradost.desktop.isolate

import com.lagradost.cloudstream3.mapper
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The extension sandbox on a real Windows AppContainer: a program is started in it the way the app starts the extensions' process, and asked to do what a
 * hijacked extension would try. This is what makes the sandbox a property of Windows and not of the app's code.
 */
class ExtensionSandboxTest {
    private lateinit var root: File
    private var child: AppContainerLauncher.Isolated? = null
    private var loopback: ServerSocket? = null

    @BeforeTest
    fun setUp() {
        assumeTrue("needs Windows AppContainers", AppContainerLauncher.available)
        System.setProperty("cloudstream.workerprobe", "1")
        root = File.createTempFile("sandbox", "").also { it.delete(); it.mkdirs() }
    }

    @AfterTest
    fun tearDown() {
        child?.destroy()
        loopback?.close()
        System.clearProperty("cloudstream.workerprobe")
        if (::root.isInitialized) root.deleteRecursively()
    }

    @Test
    fun aProgramInTheSandboxCannotTouchTheUsersPc() = runBlocking {
        // things of the user that a program with the user's rights could read or change
        val secret = File(root.parentFile, "sandbox-secret-${System.nanoTime()}.txt").also { it.writeText("the user's private file") }
        val victim = File(root.parentFile, "sandbox-victim-${System.nanoTime()}.txt")
        val documents = File(System.getProperty("user.home"), "Documents")
        loopback = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        try {
            val (process, rpc) = IsolatedHost.launch(File(root, "box"))
            child = process
            val ping = rpc.call("ping", null, 90_000)
            assertTrue(ping.get("pid").asLong() > 0, "the sandbox program runs")

            val own = File(root, "box/worker/own.txt")
            val body = mapper.createObjectNode().put("readFile", secret.absolutePath).put("listFolder", documents.absolutePath)
                .put("writeFile", victim.absolutePath).put("ownFile", own.absolutePath).put("loopbackPort", loopback!!.localPort).put("internetHost", "1.1.1.1")
            val r = rpc.call("probe", body, 60_000)

            assertTrue(r.get("readFile").asText().startsWith("blocked"), "it cannot read the user's files: ${r.get("readFile")}")
            assertTrue(r.get("listFolder").asText().startsWith("blocked"), "it cannot list the user's documents: ${r.get("listFolder")}")
            assertTrue(r.get("writeFile").asText().startsWith("blocked") && !victim.exists(), "it cannot write outside its folder: ${r.get("writeFile")}")
            assertTrue(r.get("startProgram").asText().startsWith("blocked"), "it cannot start a program: ${r.get("startProgram")}")
            assertTrue(r.get("loopback").asText().startsWith("blocked"), "it cannot reach this PC's loopback: ${r.get("loopback")}")
            assertTrue(r.get("ownFile").asText().startsWith("allowed") && own.isFile, "it can write in its own folder: ${r.get("ownFile")}")
            // the one thing an extension needs: the internet (a PC without a connection cannot tell)
            val internet = r.get("internet").asText()
            println("sandbox internet: $internet")
            assertEquals("the user's private file", secret.readText(), "the file was not changed")
        } finally {
            secret.delete()
            victim.delete()
        }
    }
}
