package com.lagradost.desktop.platform

import org.junit.Assume.assumeTrue
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Windows limits are checked on a real process: a command interpreter that is confined, and then asked to do what a hijacked program would do */
class ProcessConfineTest {
    private lateinit var root: File

    @BeforeTest
    fun setUp() {
        assumeTrue("Windows only", System.getProperty("os.name").lowercase().contains("win"))
        root = File.createTempFile("confine", "").also { it.delete(); it.mkdirs() }
    }

    @AfterTest
    fun tearDown() {
        if (::root.isInitialized) root.deleteRecursively()
    }

    @Test
    fun sha256OfAFile() {
        val f = File(root, "a.txt").also { it.writeText("abc") }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ProcessConfine.sha256(f))
    }

    @Test
    fun aConfinedProcessCannotChangeFilesOutsideItsFolderOrStartPrograms() {
        val outside = File(root, "outside").also { it.mkdirs() }
        val inside = File(root, "inside").also { it.mkdirs() }
        assertTrue(ProcessConfine.prepareFolder(inside), "the folder could be marked low")

        val shell = ProcessBuilder("cmd.exe", "/q", "/k").redirectErrorStream(true).start()
        val applied = ProcessConfine.confine(shell, lowIntegrity = true)
        assertEquals("job+low", applied)
        shell.outputStream.bufferedWriter().use { w ->
            w.write("echo outside> \"${File(outside, "o.txt").absolutePath}\"\r\n")
            w.write("echo inside> \"${File(inside, "i.txt").absolutePath}\"\r\n")
            w.write("ping -n 1 127.0.0.1\r\n")
            w.write("exit\r\n")
        }
        val output = shell.inputStream.bufferedReader().readText()
        shell.waitFor(20, TimeUnit.SECONDS)

        assertFalse(File(outside, "o.txt").exists(), "a low process cannot write where only a normal one can, output: $output")
        assertTrue(File(inside, "i.txt").exists(), "it can write in its own folder, output: $output")
        assertFalse(output.contains("Reply from 127.0.0.1"), "it cannot start another program, output: $output")
    }

    @Test
    fun theJobEndsTheProcessWhenItsHandleIsClosedWithTheApp() {
        val shell = ProcessBuilder("cmd.exe", "/q", "/k").redirectErrorStream(true).start()
        assertTrue(ProcessConfine.confine(shell, lowIntegrity = false).startsWith("job"))
        assertTrue(shell.isAlive)
        shell.destroyForcibly().waitFor(10, TimeUnit.SECONDS)
    }
}
