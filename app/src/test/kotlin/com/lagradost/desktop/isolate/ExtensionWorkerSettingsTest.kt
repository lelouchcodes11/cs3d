package com.lagradost.desktop.isolate

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mapper
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.runtime.AndroidRuntime
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ExtensionWorkerSettingsTest {
    private fun payload(dns: Int, adult: Boolean) = mapper.createObjectNode().apply {
        val def = mapper.createObjectNode()
        val ctx = AndroidRuntime.context
        def.set<com.fasterxml.jackson.databind.JsonNode>(
            ctx.getString(R.string.dns_key),
            mapper.createObjectNode().put("t", "i").put("v", dns),
        )
        def.set<com.fasterxml.jackson.databind.JsonNode>(
            ctx.getString(R.string.enable_nsfw_on_providers_key),
            mapper.createObjectNode().put("t", "b").put("v", adult),
        )
        set<com.fasterxml.jackson.databind.JsonNode>("default", def)
    }

    @Test
    fun dnsAndAdultSettingsTakeEffectInWorkerImmediately() {
        val dir = File.createTempFile("worker-settings", "").also { it.delete(); it.mkdirs() }
        try {
            if (!AndroidRuntime.isInitialized) DesktopBootstrap.init(dir)
            // First apply: sets DNS = 2 (Cloudflare) and enableAdult = true -> rebuilds HTTP client
            val before = app.baseClient
            ExtensionWorker.applySettings(payload(dns = 2, adult = true))
            val afterDns2 = app.baseClient
            assertNotSame(before, afterDns2, "changing DNS must rebuild app.baseClient in the sandbox worker")
            assertTrue(MainAPI.settingsForProvider.enableAdult, "enableAdult must update immediately")

            // Same DNS again: must not needlessly rebuild OkHttpClient
            ExtensionWorker.applySettings(payload(dns = 2, adult = true))
            assertSame(afterDns2, app.baseClient, "unchanged DNS must keep the existing client")

            // Switching DNS back to 0 and adult off: rebuilds HTTP client and disables adult
            ExtensionWorker.applySettings(payload(dns = 0, adult = false))
            assertNotSame(afterDns2, app.baseClient, "switching DNS back must rebuild app.baseClient")
            assertFalse(MainAPI.settingsForProvider.enableAdult, "disabling adult must apply immediately")
        } finally {
            dir.deleteRecursively()
        }
    }
}
