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
    fun `a wrong, mistyped or suspended key points at the key and secret`() {
        assertEquals(
            "Last.fm didn't accept that API key and shared secret (Invalid API key - You must be granted a valid key by last.fm).",
            LastFm.signInError(LastFmException(10, "Invalid API key - You must be granted a valid key by last.fm")),
        )
        // 13: a wrong secret makes the signature wrong; 26: the key suspended.
        for (code in listOf(13, 26)) assertTrue("API key and shared secret" in LastFm.signInError(LastFmException(code, "Invalid method signature supplied")))
    }

    @Test
    fun `no network gives the reason as it came`() {
        assertEquals("Couldn't reach Last.fm: timeout", LastFm.signInError(LastFmException(0, "Couldn't reach Last.fm: timeout")))
    }
}
