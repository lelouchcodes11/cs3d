package com.lagradost.desktop.isolate

import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rights of the sandbox are read from Windows, not remembered: a folder that was replaced (a new build, an update) has none of the rights the old one had, and a sandbox
 * program that cannot read its own Java runtime dies at start. (The version that remembered its grants in a file did exactly that after the first update.)
 */
class SandboxAccessTest {
    private lateinit var root: File

    @BeforeTest
    fun setUp() {
        assumeTrue("needs Windows AppContainers", AppContainerLauncher.available)
        root = File.createTempFile("sandboxaccess", "").also { it.delete(); it.mkdirs() }
    }

    @AfterTest
    fun tearDown() {
        if (::root.isInitialized) root.deleteRecursively()
    }

    @Test
    fun aReplacedFolderIsNotTakenForOneTheSandboxMayRead() {
        val container = AppContainerLauncher.container("CloudStream.Extensions")
        val runtime = File(root, "runtime").also { it.mkdirs() }
        assertFalse(AppContainerLauncher.hasAccess(container, runtime, false), "a new folder has no right for the sandbox")

        assertTrue(AppContainerLauncher.grant(container, runtime, false))
        assertTrue(AppContainerLauncher.hasAccess(container, runtime, false), "after the grant it may read")
        assertFalse(AppContainerLauncher.hasAccess(container, runtime, true), "reading is not changing")
        val inside = File(runtime, "bin.txt").also { it.writeText("x") }
        assertTrue(AppContainerLauncher.hasAccess(container, inside, false), "a file made in the folder inherits the right")

        // the folder is deleted and made again, as a new build or an update does: the right is gone, whatever was granted before
        runtime.deleteRecursively()
        runtime.mkdirs()
        assertFalse(AppContainerLauncher.hasAccess(container, runtime, false), "the new folder has no right again")
        assertTrue(AppContainerLauncher.grant(container, runtime, false))
        assertTrue(AppContainerLauncher.hasAccess(container, runtime, false))
    }
}
