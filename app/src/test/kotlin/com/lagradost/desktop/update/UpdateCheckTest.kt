package com.lagradost.desktop.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Versions of release tags and the choice of the file that updates a copy of the app */
class UpdateCheckTest {
    private fun release(vararg names: String) = UpdateCheck.Release(
        tag = "v1.0.3", version = UpdateCheck.parseVersion("v1.0.3")!!, prerelease = false, notes = "", pageUrl = "https://example.org/r",
        assets = names.map { UpdateCheck.Asset(it, "https://example.org/$it", 10, null) },
    )

    @Test
    fun tagsAreOrderedByNumber() {
        val a = UpdateCheck.parseVersion("v1.0.9")!!
        val b = UpdateCheck.parseVersion("1.0.10")!!
        val c = UpdateCheck.parseVersion("v1.1")!!
        assertTrue(a < b)
        assertTrue(b < c)
        assertTrue(UpdateCheck.parseVersion("v1.0.3-beta.2")!!.preRelease)
        assertTrue(UpdateCheck.parseVersion("v1.0.3-beta.2")!! < UpdateCheck.parseVersion("v1.0.3")!!)
        assertNull(UpdateCheck.parseVersion("latest"))
    }

    @Test
    fun theFileFitsTheWayTheCopyWasInstalled() {
        val r = release("CloudStream-1.0.3-windows-x64.msi", "CloudStream-1.0.3-windows-x64-setup.exe", "CloudStream-1.0.3-windows-x64-portable.zip", "notes.txt")
        assertEquals("CloudStream-1.0.3-windows-x64-setup.exe", r.installerFor(UpdateCheck.InstallKind.Setup)?.name)
        assertEquals("CloudStream-1.0.3-windows-x64.msi", r.installerFor(UpdateCheck.InstallKind.Msi)?.name)
        assertEquals("CloudStream-1.0.3-windows-x64-portable.zip", r.installerFor(UpdateCheck.InstallKind.Portable)?.name)
        // a development run or an unknown copy is never given an installer
        assertNull(r.installerFor(UpdateCheck.InstallKind.Other))
    }

    @Test
    fun aMissingFileIsNotReplacedByAnother() {
        // an MSI must not update a copy installed with the setup program (two installations), nor the other way round
        val onlyMsi = release("CloudStream-1.0.3-windows-x64.msi")
        assertNull(onlyMsi.installerFor(UpdateCheck.InstallKind.Setup))
        assertNull(onlyMsi.installerFor(UpdateCheck.InstallKind.Portable))
        assertNotNull(onlyMsi.installerFor(UpdateCheck.InstallKind.Msi))
    }
}
