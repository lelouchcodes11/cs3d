package com.lagradost.desktop.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which releases a channel offers, which channel a version starts on, and how release notes are turned into text */
class UpdateChannelTest {
    @Test
    fun stableOffersOnlyReleasesThatAreNotPreReleases() {
        assertTrue(UpdateCheck.channelAccepts(UpdateCheck.Channel.Stable, prerelease = false))
        assertFalse(UpdateCheck.channelAccepts(UpdateCheck.Channel.Stable, prerelease = true))
    }

    @Test
    fun betaOffersEverything() {
        assertTrue(UpdateCheck.channelAccepts(UpdateCheck.Channel.Beta, prerelease = false))
        assertTrue(UpdateCheck.channelAccepts(UpdateCheck.Channel.Beta, prerelease = true))
    }

    @Test
    fun aPreReleaseVersionStartsOnTheBetaChannelAndAReleaseOnStable() {
        assertEquals(UpdateCheck.Channel.Stable, UpdateCheck.defaultChannel("1.0.8"))
        assertEquals(UpdateCheck.Channel.Stable, UpdateCheck.defaultChannel("v2.3.0"))
        assertEquals(UpdateCheck.Channel.Beta, UpdateCheck.defaultChannel("0.9.2"))
        assertEquals(UpdateCheck.Channel.Beta, UpdateCheck.defaultChannel("1.1.0-beta.2"))
        assertEquals(UpdateCheck.Channel.Stable, UpdateCheck.defaultChannel("not a version"))
    }

    @Test
    fun aChannelIsFoundByItsStoredName() {
        assertEquals(UpdateCheck.Channel.Beta, UpdateCheck.Channel.of("beta"))
        assertEquals(null, UpdateCheck.Channel.of("nightly"))
        assertEquals(null, UpdateCheck.Channel.of(null))
    }

    @Test
    fun releaseNotesKeepTheirTextAndLoseTheirMarks() {
        val line = UpdateUi.inline("**Bold** then `code` and [a link](https://example.com/x)")
        assertEquals("Bold then code and a link", line.text)
        // the bold word and the code word are styled, the rest is not
        assertEquals(2, line.spanStyles.size)
    }
}
