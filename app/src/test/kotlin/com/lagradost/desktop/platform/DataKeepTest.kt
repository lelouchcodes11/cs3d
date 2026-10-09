package com.lagradost.desktop.platform

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The copy of the settings outside the data folder: put back into a fresh data folder of another version, never over settings and never for the same version */
class DataKeepTest {
    private lateinit var root: File
    private lateinit var keep: File

    @BeforeTest
    fun setUp() {
        root = File.createTempFile("datakeep", "").also { it.delete(); it.mkdirs() }
        keep = File(root, "keep").also { it.mkdirs() }
        // the folder is read once per run: every test of this class uses the same place, emptied before each one
        System.setProperty("cloudstream.keep", keep.absolutePath)
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun keepSettings(version: String) {
        File(keep, "shared_prefs").mkdirs()
        File(keep, "shared_prefs/rebuild_preference.xml").writeText("<map><string name=\"REPOSITORIES_KEY\">[]</string></map>")
        File(keep, "shared_prefs/Other.xml").writeText("<map/>")
        File(keep, "version.txt").writeText(version)
    }

    @Test
    fun aFreshDataFolderOfAnotherVersionGetsTheSettingsBack() {
        keepSettings("0.0.1")
        val data = File(root, "data").also { it.mkdirs() }
        DataKeep.restoreIfFresh(data)
        assertTrue(File(data, "shared_prefs/rebuild_preference.xml").exists())
        assertTrue(File(data, "shared_prefs/Other.xml").exists())
    }

    @Test
    fun theSameVersionIsNotRestored() {
        keepSettings(com.lagradost.desktop.AppInfo.version)
        val data = File(root, "data").also { it.mkdirs() }
        DataKeep.restoreIfFresh(data)
        assertFalse(File(data, "shared_prefs/rebuild_preference.xml").exists())
    }

    @Test
    fun settingsThatAreThereAreNeverTouched() {
        keepSettings("0.0.1")
        val data = File(root, "data").also { it.mkdirs() }
        File(data, "shared_prefs").mkdirs()
        File(data, "shared_prefs/rebuild_preference.xml").writeText("mine")
        DataKeep.restoreIfFresh(data)
        assertEquals("mine", File(data, "shared_prefs/rebuild_preference.xml").readText())
        assertFalse(File(data, "shared_prefs/Other.xml").exists())
    }

    @Test
    fun nothingKeptMeansNothingRestored() {
        val data = File(root, "data").also { it.mkdirs() }
        DataKeep.restoreIfFresh(data)
        assertFalse(File(data, "shared_prefs").exists())
    }
}
