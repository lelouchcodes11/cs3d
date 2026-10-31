package com.lagradost.desktop.isolate

import com.lagradost.desktop.runtime.web.PrivateNetworkGuard
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A page made for an extension in the sandbox may not be a way to this PC's own services or the home network */
class PrivateNetworkGuardTest {
    @Test
    fun thisPcAndItsNetworkAreRefused() {
        for (url in listOf(
            "http://127.0.0.1:8080/", "http://localhost/", "http://foo.localhost/", "http://[::1]/", "http://0.0.0.0/", "http://192.168.1.1/admin", "http://10.0.0.5/", "http://172.16.0.9/",
            "http://169.254.169.254/latest/meta-data/", "http://100.64.0.1/", "http://[fd00::1]/", "http://[fe80::1]/", "http://printer.local/", "http://router.lan/", "http://intranet/",
            "http://2130706433/", "http://0x7f000001/", "http://127.1/", "http://[::ffff:127.0.0.1]/", "ws://127.0.0.1:9/", "file:///C:/Users/", "javascript:alert(1)", "ftp://example.com/",
        )) assertFalse(PrivateNetworkGuard.isAllowed(url), "$url must be refused")
    }

    @Test
    fun publicAddressesAndPagesWithoutANetworkAreAllowed() {
        for (url in listOf("https://8.8.8.8/", "http://93.184.216.34/page", "https://[2606:4700:4700::1111]/", "about:blank", "data:text/html,<p>hi</p>", "blob:https://example.com/x")) {
            assertTrue(PrivateNetworkGuard.isAllowed(url), "$url must be allowed")
        }
    }
}
