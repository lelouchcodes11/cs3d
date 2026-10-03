package com.lagradost.desktop.player

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertEquals

class MpvTest {
    @Test
    fun testMpvLifecycle() {
        val mpv = Mpv.INSTANCE
        val handle = mpv.mpv_create()
        assertNotNull(handle, "mpv_create should return non-null handle")

        try {
            // Set vo=null for headless testing
            mpv.mpv_set_option_string(handle, "vo", "null")
            mpv.mpv_set_option_string(handle, "ao", "null")
            val initResult = mpv.mpv_initialize(handle)
            assertEquals(0, initResult, "mpv_initialize should return 0 (success)")

            val ptr = mpv.mpv_get_property_string(handle, "mpv-version")
            assertNotNull(ptr, "mpv-version property should be readable")
            val version = ptr.getString(0, "UTF-8")
            println("Loaded mpv version: $version")
            mpv.mpv_free(ptr)
        } finally {
            mpv.mpv_terminate_destroy(handle)
        }
    }
}
