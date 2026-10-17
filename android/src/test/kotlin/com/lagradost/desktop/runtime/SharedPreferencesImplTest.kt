package com.lagradost.desktop.runtime

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Two windows of the app on one data folder: neither wipes what the other saved */
class SharedPreferencesImplTest {
    private fun folder(): File = java.nio.file.Files.createTempDirectory("prefs-test").toFile().also { it.deleteOnExit() }

    @Test
    fun twoProcessesKeepEachOthersKeys() {
        val dir = File(folder(), "shared_prefs")
        val file = File(dir, "store.xml")
        val first = SharedPreferencesImpl(file)
        val second = SharedPreferencesImpl(file)
        // both have the (empty) file in memory, then each saves its own key
        first.edit().putString("a", "1").commit()
        second.edit().putString("b", "2").commit()
        // a third reader (a start of the app) sees both
        val reader = SharedPreferencesImpl(file)
        assertEquals("1", reader.getString("a", null))
        assertEquals("2", reader.getString("b", null))
    }

    @Test
    fun aRemovalDoesNotBringBackOrWipeOtherKeys() {
        val file = File(File(folder(), "shared_prefs"), "store.xml")
        val first = SharedPreferencesImpl(file)
        first.edit().putString("keep", "k").putString("gone", "g").commit()
        val second = SharedPreferencesImpl(file)
        assertEquals("g", second.getString("gone", null))
        second.edit().remove("gone").commit()
        first.edit().putString("later", "l").commit()
        val reader = SharedPreferencesImpl(file)
        assertEquals("k", reader.getString("keep", null))
        assertEquals("l", reader.getString("later", null))
        assertNull(reader.getString("gone", null))
        assertFalse(reader.contains("gone"))
    }

    @Test
    fun aChangeOfAnotherProcessIsTakenOver() {
        val file = File(File(folder(), "shared_prefs"), "store.xml")
        val first = SharedPreferencesImpl(file)
        first.edit().putInt("n", 1).commit()
        val second = SharedPreferencesImpl(file)
        second.edit().putInt("n", 2).commit()
        // the timer looks once a second
        var seen = first.getInt("n", 0)
        var tries = 0
        while (seen != 2 && tries++ < 40) {
            Thread.sleep(100)
            seen = first.getInt("n", 0)
        }
        assertEquals(2, seen)
    }
}

/** A sync extension that lists the folder while the app saves must never find the profile missing */
class SharedPreferencesNeverMissingTest {
    @Test
    fun theFileIsThereWhileItIsReplaced() {
        val dir = java.nio.file.Files.createTempDirectory("prefs-test2").toFile().also { it.deleteOnExit() }
        val file = File(File(dir, "shared_prefs"), "store.xml")
        val prefs = SharedPreferencesImpl(file)
        prefs.edit().putString("first", "x").commit()
        assertTrue(file.exists())
        val running = java.util.concurrent.atomic.AtomicBoolean(true)
        val missing = java.util.concurrent.atomic.AtomicInteger(0)
        val watcher = Thread {
            while (running.get()) { if (!file.exists()) missing.incrementAndGet() }
        }.also { it.start() }
        for (i in 0 until 400) prefs.edit().putInt("n", i).putString("pad", "p".repeat(2000)).commit()
        running.set(false)
        watcher.join()
        assertEquals(0, missing.get(), "the preferences file was missing ${missing.get()} times while it was written")
    }
}
