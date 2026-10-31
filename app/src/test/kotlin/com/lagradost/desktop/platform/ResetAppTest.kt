package com.lagradost.desktop.platform

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** "Reset app": what the next start deletes for each scope, and the guards against deleting a folder that is not the app's data folder */
class ResetAppTest {
    private lateinit var root: File
    private lateinit var data: File
    private lateinit var keep: File

    @BeforeTest
    fun setUp() {
        root = File.createTempFile("resetapp", "").also { it.delete(); it.mkdirs() }
        data = File(root, "data").also { it.mkdirs() }
        keep = File(root, "keep").also { it.mkdirs() }
        System.setProperty("cloudstream.keep", keep.absolutePath)
        System.setProperty("cloudstream.data", data.absolutePath)
        fill()
    }

    @AfterTest
    fun tearDown() {
        System.clearProperty("cloudstream.keep")
        System.clearProperty("cloudstream.data")
        root.deleteRecursively()
    }

    private fun fill() {
        File(data, "shared_prefs").mkdirs()
        File(data, "shared_prefs/com.lagradost.cloudstream3_preferences.xml").writeText("<map/>")
        File(data, "shared_prefs/rebuild_preference.xml").writeText("<map><string name=\"PLUGINS_KEY\">x</string></map>")
        File(data, "files").mkdirs()
        File(data, "files/graphics.txt").writeText("opengl")
        File(data, "window.properties").writeText("x=1")
        File(data, "logs").mkdirs()
        File(data, "logs/app.log").writeText("log")
        File(data, "web/profile").mkdirs()
        File(data, "web/profile/cookies").writeText("c")
        File(keep, "shared_prefs").mkdirs()
        File(keep, "shared_prefs/rebuild_preference.xml").writeText("<map/>")
    }

    private fun mark(scope: ResetApp.Scope) = File(data, "reset.pending").writeText(scope.name)

    @Test
    fun settingsResetKeepsTheUsersData() {
        mark(ResetApp.Scope.Settings)
        ResetApp.applyPending()
        assertFalse(File(data, "shared_prefs/com.lagradost.cloudstream3_preferences.xml").exists(), "the settings file goes")
        assertFalse(File(data, "window.properties").exists())
        assertFalse(File(data, "files/graphics.txt").exists())
        assertTrue(File(data, "shared_prefs/rebuild_preference.xml").isFile, "profiles, library, history and extensions stay")
        assertTrue(File(data, "web/profile/cookies").isFile)
        assertFalse(File(data, "reset.pending").exists(), "one attempt only")
    }

    @Test
    fun eraseEverythingStartsLikeAFirstInstall() {
        mark(ResetApp.Scope.Everything)
        ResetApp.applyPending()
        assertEquals(emptyList(), data.listFiles().orEmpty().toList(), "the data folder is empty")
        // the second copy that survives updates would bring the old settings back
        assertFalse(File(keep, "shared_prefs").exists())
    }

    @Test
    fun noMarkerMeansNothingIsDeleted() {
        ResetApp.applyPending()
        assertTrue(File(data, "shared_prefs/rebuild_preference.xml").isFile)
        assertTrue(File(keep, "shared_prefs/rebuild_preference.xml").isFile)
    }

    @Test
    fun anUnknownScopeIsIgnoredAndTheMarkerRemoved() {
        File(data, "reset.pending").writeText("Everything-and-more")
        ResetApp.applyPending()
        assertTrue(File(data, "shared_prefs/rebuild_preference.xml").isFile)
        assertFalse(File(data, "reset.pending").exists())
    }

    @Test
    fun onlyADataFolderIsWiped() {
        assertTrue(ResetApp.isSafeToWipe(data))
        // the user's home, a drive root and a folder that is not an app data folder (no shared_prefs or files)
        assertFalse(ResetApp.isSafeToWipe(File(System.getProperty("user.home"))))
        assertFalse(ResetApp.isSafeToWipe(File(System.getProperty("user.home")).parentFile))
        assertFalse(ResetApp.isSafeToWipe(File("C:\\")))
        assertFalse(ResetApp.isSafeToWipe(root))
        assertFalse(ResetApp.isSafeToWipe(File(root, "missing")))
    }

    @Test
    fun aMarkerInAFolderThatIsNotADataFolderDeletesNothing() {
        val other = File(root, "documents").also { it.mkdirs() }
        File(other, "thesis.docx").writeText("precious")
        System.setProperty("cloudstream.data", other.absolutePath)
        File(other, "reset.pending").writeText(ResetApp.Scope.Everything.name)
        ResetApp.applyPending()
        assertTrue(File(other, "thesis.docx").isFile)
    }
}
