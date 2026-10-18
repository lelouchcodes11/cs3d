package com.lagradost.desktop.runtime

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A sync extension that mirrors a small or "deleted" cloud copy onto the store must not empty it; the user's own bulk changes still work */
class WipeGuardTest {
    private var told = 0
    private lateinit var file: File

    private fun store(keys: Int = 100): SharedPreferencesImpl {
        val dir = java.nio.file.Files.createTempDirectory("wipe-test").toFile().also { it.deleteOnExit() }
        file = File(File(dir, "shared_prefs"), "rebuild_preference.xml")
        val prefs = SharedPreferencesImpl(file)
        val e = prefs.edit()
        for (i in 0 until keys) e.putString("0/history/$i", "value $i")
        e.putString("PLUGINS_KEY", "[{\"name\":\"a\"}]").putString("REPOSITORIES_KEY", "[{\"url\":\"x\"}]")
        e.commit()
        told = 0
        WipeGuard.onBlocked = { _, _, _ -> told++ }
        return prefs
    }

    @AfterTest
    fun reset() {
        WipeGuard.onBlocked = null
        WipeGuard.enabled = true
    }

    @Test
    fun clearIsHeldBackButNewDataComesIn() {
        val p = store()
        // the shape of a mirror: clear, then what the cloud copy has
        p.edit().clear().putString("0/history/new", "from the cloud").commit()
        assertEquals(102 + 1, p.all.size)
        assertEquals("from the cloud", p.getString("0/history/new", null))
        assertTrue(p.contains("PLUGINS_KEY") && p.contains("REPOSITORIES_KEY"))
        assertEquals(1, told)
    }

    @Test
    fun oneBigRemovalIsHeldBack() {
        val p = store()
        val e = p.edit()
        for (i in 0 until 60) e.remove("0/history/$i")
        e.commit()
        assertEquals(102, p.all.size)
    }

    @Test
    fun aLongRunOfSingleRemovalsIsStoppedAndWhatWentIsPutBack() {
        val p = store()
        for (i in 0 until 90) p.edit().remove("0/history/$i").commit()
        // 40 % of 102 keys is 41: from there on nothing goes, and the keys removed before come back
        assertEquals(102, p.all.size)
        assertEquals("value 5", p.getString("0/history/5", null))
        assertTrue(told >= 1)
    }

    @Test
    fun theExtensionListAndTheRepositoriesCannotBeRemovedAlone() {
        val p = store()
        p.edit().remove("PLUGINS_KEY").remove("REPOSITORIES_KEY").commit()
        assertTrue(p.contains("PLUGINS_KEY") && p.contains("REPOSITORIES_KEY"))
        // but they can be changed, also to an empty list
        p.edit().putString("PLUGINS_KEY", "[]").commit()
        assertEquals("[]", p.getString("PLUGINS_KEY", null))
        assertFalse(p.all.isEmpty())
    }

    @Test
    fun smallRemovalsGoThrough() {
        val p = store()
        val e = p.edit()
        for (i in 0 until 10) e.remove("0/history/$i")
        e.commit()
        assertEquals(92, p.all.size)
        assertNull(p.getString("0/history/3", null))
        assertEquals(0, told)
    }

    @Test
    fun theUsersOwnBulkChangeIsLetThrough() {
        val p = store()
        WipeGuard.allow { p.edit().clear().commit() }
        assertTrue(p.all.isEmpty())
    }

    @Test
    fun whatIsKeptIsOnTheDiskToo() {
        val p = store()
        p.edit().clear().putString("n", "v").commit()
        // a start of the app reads the file again
        assertEquals(103, SharedPreferencesImpl(file).all.size)
    }
}
