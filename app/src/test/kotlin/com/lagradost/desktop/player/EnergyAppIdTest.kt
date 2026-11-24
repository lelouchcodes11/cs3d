package com.lagradost.desktop.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The app id Windows starts for each Energy player is read from the installed package (checked where the free one is installed) */
class EnergyAppIdTest {
    private fun installed(family: String) = System.getenv("LOCALAPPDATA")?.let { java.io.File(java.io.File(it, "Packages"), family).isDirectory } == true

    @Test
    fun freePlayerIdComesFromTheRegistry() {
        if (!System.getProperty("os.name").startsWith("Windows") || !installed(ExternalPlayers.ENERGY_FAMILY)) return
        assertEquals("App", ExternalPlayers.energyAppId(ExternalPlayers.ENERGY_FAMILY))
    }

    @Test
    fun aPlayerThatIsNotInstalledFallsBackToApp() {
        if (installed(ExternalPlayers.ENERGY_PAID_FAMILY)) return
        assertTrue(ExternalPlayers.energyAumid(ExternalPlayers.ENERGY_PAID_FAMILY).endsWith("!App"))
    }
}
