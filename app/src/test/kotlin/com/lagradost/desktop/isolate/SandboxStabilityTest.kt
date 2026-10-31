package com.lagradost.desktop.isolate

import com.lagradost.cloudstream3.mapper
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** The sandbox program must live through many connections at once (an extension loads, searches and resolves in parallel): a crash of the Java runtime in it costs a restart and every request that was open */
class SandboxStabilityTest {
    private lateinit var root: File
    private var child: AppContainerLauncher.Isolated? = null

    @BeforeTest
    fun setUp() {
        assumeTrue("needs Windows AppContainers", AppContainerLauncher.available)
        System.setProperty("cloudstream.workerprobe", "1")
        root = File.createTempFile("sandboxstable", "").also { it.delete(); it.mkdirs() }
    }

    @AfterTest
    fun tearDown() {
        child?.destroy()
        System.clearProperty("cloudstream.workerprobe")
        if (::root.isInitialized) root.deleteRecursively()
    }

    @Test
    fun theProgramHasTheAppsTlsAndGivesExtensionsAnActivity() = runBlocking {
        val (process, rpc) = IsolatedHost.launch(File(root, "box"))
        child = process
        rpc.call("ping", null, 90_000)
        val reply = rpc.call("probe", mapper.createObjectNode(), 60_000)
        println("sandbox tls: ${reply.get("tlsProvider")}, context: ${reply.get("pluginContext")}")
        // Windows does not load a library the sandbox wrote itself; Conscrypt is first only when the app put its library where the sandbox may load it from
        assertTrue(reply.get("tlsProvider").asText().contains("Conscrypt", ignoreCase = true), "TLS provider is ${reply.get("tlsProvider")}")
        // CNCVerse's plugins cast what load(context) gets to AppCompatActivity
        assertTrue(reply.get("pluginContext").asText().endsWith("activity=true"), "plugin context is ${reply.get("pluginContext")}")
    }

    @Test
    fun theProgramSurvivesManyConnectionsAtOnce() = runBlocking {
        val (process, rpc) = IsolatedHost.launch(File(root, "box"))
        child = process
        rpc.call("ping", null, 90_000)
        repeat(4) { round ->
            val reply = rpc.call("probe", mapper.createObjectNode().put("hammer", 60), 150_000)
            println("sandbox hammer round $round: ${reply.get("hammer")}")
            assertTrue(process.isAlive(), "the sandbox program is still running after round $round")
        }
    }
}
