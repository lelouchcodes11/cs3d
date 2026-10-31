package com.lagradost.desktop.net

import okhttp3.Dns
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** A name that is no host name must fail like an unknown host (an IOException OkHttp reports to the caller), not end a dispatcher thread in crash.log */
class UsableAddressesTest {
    @Test
    fun aHostPatternUsedAsAnAddressIsAnUnknownHost() {
        // the SecurityManager of the app throws this from its permission check for "*.m4uplay.*"
        val dns = UsableAddresses.wrap(Dns { throw IllegalArgumentException("invalid host wildcard specification") })
        assertFailsWith<UnknownHostException> { dns.lookup("*.m4uplay.*") }
    }
}
