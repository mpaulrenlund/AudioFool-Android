package com.mpaulrenlund.audiofool.lastfm

import com.mpaulrenlund.audiofool.core.scrobbling.LastFmException
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The phone's Last.fm sign-in (spec 7): what the form says when Last.fm turns it down.

class LastFmSignInTest {
    @Test
    fun `a wrong name or password says so`() {
        assertEquals("Last.fm didn't accept that username and password.", LastFm.signInError(LastFmException(4, "Authentication Failed")))
    }

    @Test
    fun `a refused or suspended app key says a new build is needed`() {
        for (code in listOf(10, 26)) assertTrue("new build" in LastFm.signInError(LastFmException(code, "Suspended API key")))
    }

    @Test
    fun `no network gives the reason as it came`() {
        assertEquals("Couldn't reach Last.fm: timeout", LastFm.signInError(LastFmException(0, "Couldn't reach Last.fm: timeout")))
    }
}
