package com.lagradost.desktop.net

import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CacheFileSystemTest {
    /** Refuses the first [refusals] moves, like Windows while another request reads the entry */
    private class Busy(var refusals: Int) : ForwardingFileSystem(FileSystem.SYSTEM) {
        override fun atomicMove(source: Path, target: Path) {
            if (refusals-- > 0) throw IOException("AccessDeniedException")
            super.atomicMove(source, target)
        }
    }

    @Test fun aMoveRefusedForAMomentStillHappens() {
        val dir = Files.createTempDirectory("cachefs").toFile()
        val from = dir.resolve("a.0.tmp").apply { writeText("new") }.toOkioPath()
        val to = dir.resolve("a.0").toOkioPath()
        CacheFileSystem(Busy(2)).atomicMove(from, to)
        assertEquals("new", dir.resolve("a.0").readText())
        dir.deleteRecursively()
    }

    @Test fun aMoveThatStaysRefusedStillFails() {
        val dir = Files.createTempDirectory("cachefs").toFile()
        val from = dir.resolve("a.0.tmp").apply { writeText("new") }.toOkioPath()
        assertFailsWith<IOException> { CacheFileSystem(Busy(100)).atomicMove(from, dir.resolve("a.0").toOkioPath()) }
        assertTrue(dir.resolve("a.0.tmp").exists())
        dir.deleteRecursively()
    }
}
