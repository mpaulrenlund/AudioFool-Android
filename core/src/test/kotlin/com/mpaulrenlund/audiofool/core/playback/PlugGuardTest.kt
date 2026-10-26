package com.mpaulrenlund.audiofool.core.playback

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Plugging in a USB DAC or adapter never starts the music (0.2.3). New: the desktop has no plug.

class PlugGuardTest {
    @Test fun a_play_with_no_plug_goes_ahead() {
        val guard = PlugGuard()
        assertTrue(guard.mayStart(10_000))
        assertEquals(0, guard.ignored)
    }

    @Test fun a_play_right_after_a_plug_is_ignored() {
        val guard = PlugGuard(windowMs = 3_000)
        assertFalse(guard.plugged(10_000))
        assertFalse(guard.mayStart(10_200))
        assertFalse(guard.mayStart(13_000))
        assertEquals(2, guard.ignored)
    }

    @Test fun a_play_after_the_window_goes_ahead() {
        val guard = PlugGuard(windowMs = 3_000)
        guard.plugged(10_000)
        assertTrue(guard.mayStart(13_001))
        assertEquals(0, guard.ignored)
    }

    @Test fun a_play_just_before_the_plug_is_undone() {
        val guard = PlugGuard(windowMs = 3_000)
        assertTrue(guard.mayStart(10_000))
        assertTrue(guard.plugged(10_400))
        assertEquals(1, guard.undone)
    }

    @Test fun a_play_well_before_the_plug_is_left_alone() {
        val guard = PlugGuard(windowMs = 3_000)
        guard.mayStart(10_000)
        assertFalse(guard.plugged(20_000))
        assertEquals(0, guard.undone)
    }

    @Test fun only_one_plug_undoes_a_play() {
        // A DAC arrives as several devices (output and input) in a row.
        val guard = PlugGuard(windowMs = 3_000)
        guard.mayStart(10_000)
        assertTrue(guard.plugged(10_100))
        assertFalse(guard.plugged(10_300))
        assertEquals(1, guard.undone)
    }

    @Test fun a_pause_or_a_play_from_the_app_in_between_is_left_alone() {
        val guard = PlugGuard(windowMs = 3_000)
        guard.mayStart(10_000)
        guard.settled()
        assertFalse(guard.plugged(10_500))
    }

    @Test fun since_plug() {
        val guard = PlugGuard()
        assertNull(guard.sincePlug(5_000))
        guard.plugged(5_000)
        assertEquals(2_500, guard.sincePlug(7_500))
    }
}
