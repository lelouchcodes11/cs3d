package com.lagradost.desktop.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileIconsTest {

    @Test
    fun catalogContainsAllIcons() {
        assertEquals(359, ProfileIconCatalog.all.size)
        assertEquals(29, ProfileIconCatalog.categories.size)
    }

    @Test
    fun defaultIconIsOneOfTheClassics() {
        val defaultIcon = ProfileIconCatalog.defaultFor(0)
        assertEquals("the_classics_scarlet_chilleez", defaultIcon.id)
        assertEquals("Scarlet Chilleez", defaultIcon.name)
        assertEquals("The Classics", defaultIcon.category)
    }

    @Test
    fun coolDefaultsAreAvailableAndLoadable() {
        assertTrue(ProfileIconCatalog.coolDefaults.isNotEmpty())
        for (icon in ProfileIconCatalog.coolDefaults) {
            val bmp = ProfileIconLoader.load(icon.id)
            assertNotNull("Cool default icon ${icon.id} should load successfully", bmp)
        }
    }

    @Test
    fun everyCatalogIconHasValidWebpResource() {
        for (icon in ProfileIconCatalog.all) {
            val stream = ProfileIconsTest::class.java.classLoader.getResourceAsStream("profile_icons/${icon.id}.webp")
            assertNotNull("Resource profile_icons/${icon.id}.webp must exist", stream)
            val bytes = stream.use { it.readBytes() }
            assertTrue("Icon ${icon.id} should not be empty", bytes.isNotEmpty())
        }
    }
}
