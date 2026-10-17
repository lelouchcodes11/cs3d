package com.lagradost.desktop.net

import com.lagradost.desktop.runtime.AndroidRuntime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The viewer's list of extensions that come first (search results, Home, the search's extension list) */
class ExtensionPriorityTest {
    @BeforeTest
    fun setUp() {
        if (!AndroidRuntime.isInitialized) AndroidRuntime.init(java.nio.file.Files.createTempDirectory("priority-test").toFile())
        if (runCatching { AndroidRuntime.context }.isFailure) AndroidRuntime.startApplication(android.app.Application())
        ExtensionPriority.names.toList().forEach { ExtensionPriority.unpin(it) }
    }

    @Test
    fun pinnedOnesComeFirstInTheOrderTheyWerePut() {
        ExtensionPriority.pin("Kisskh")
        ExtensionPriority.pin("AnimePahe")
        ExtensionPriority.pin("Kisskh") // twice: nothing changes
        assertEquals(listOf("Kisskh", "AnimePahe"), ExtensionPriority.names)
        val all = listOf("Zinkmovies", "AnimePahe", "StreamPlay", "Kisskh")
        assertEquals(listOf("Kisskh", "AnimePahe", "Zinkmovies", "StreamPlay"), ExtensionPriority.ordered(all) { it })
    }

    @Test
    fun theOthersKeepTheirOrderAndMovingWorksAtTheEnds() {
        ExtensionPriority.pin("A")
        ExtensionPriority.pin("B")
        ExtensionPriority.pin("C")
        ExtensionPriority.move("C", -1)
        assertEquals(listOf("A", "C", "B"), ExtensionPriority.names)
        ExtensionPriority.move("A", -1) // already first
        ExtensionPriority.move("B", 1) // already last
        assertEquals(listOf("A", "C", "B"), ExtensionPriority.names)
        ExtensionPriority.unpin("C")
        assertEquals(listOf("A", "B"), ExtensionPriority.names)
        assertEquals(listOf("x", "y"), ExtensionPriority.ordered(listOf("x", "y")) { it })
    }

    @Test
    fun theListIsSavedWithTheSettings() {
        ExtensionPriority.pin("One")
        ExtensionPriority.pin("Two")
        val saved = androidx.preference.PreferenceManager.getDefaultSharedPreferences(AndroidRuntime.context).getString("extension_priority", null)
        assertEquals("""["One","Two"]""", saved)
    }
}
