package com.mpaulrenlund.audiofool.core.playback

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The desktop's 5 ms raised-cosine fade either side of a seek (OutputChain.Fade),
// ported for the click the M21 made at every restart and tap (0.1.3).

class SeekFadeTest {
    @Test fun five_milliseconds_at_the_tracks_rate() {
        assertEquals(220, SeekFade.frames(44100))
        assertEquals(480, SeekFade.frames(96000))
        assertEquals(1, SeekFade.frames(100))
    }

    @Test fun rising_starts_silent_and_climbs_to_almost_full() {
        val count = 220
        assertEquals(0f, SeekFade.gain(0, count, rising = true))
        val gains = (0 until count).map { SeekFade.gain(it, count, rising = true) }
        assertTrue(gains.zipWithNext().all { (a, b) -> b > a })
        assertTrue(gains.last() > 0.99f)
    }

    @Test fun falling_ends_silent() {
        val count = 220
        assertEquals(0f, SeekFade.gain(count - 1, count, rising = false), 1e-6f)
        val gains = (0 until count).map { SeekFade.gain(it, count, rising = false) }
        assertTrue(gains.zipWithNext().all { (a, b) -> b < a })
        assertTrue(gains.first() > 0.99f)
    }

    @Test fun the_two_halves_meet_at_one_half() {
        assertEquals(0.5f, SeekFade.gain(50, 100, rising = true), 1e-6f)
        assertEquals(0.5f, SeekFade.gain(49, 100, rising = false), 1e-6f)
    }

    @Test fun fade_in_scales_every_channel_of_the_first_frames_only() {
        val channels = 2
        val frames = 10
        val samples = FloatArray(frames * channels) { 1f }
        SeekFade.fadeIn(frames, channels, fadeFrames = 4, get = { samples[it] }, set = { i, v -> samples[i] = v })
        for (f in 0 until 4) {
            val g = SeekFade.gain(f, 4, rising = true)
            assertEquals(g, samples[f * 2])
            assertEquals(g, samples[f * 2 + 1])
        }
        assertTrue((8 until 20).all { samples[it] == 1f })
    }

    @Test fun a_block_shorter_than_the_fade_is_faded_as_far_as_it_goes() {
        val samples = FloatArray(3) { 1f }
        SeekFade.fadeIn(3, 1, fadeFrames = 220, get = { samples[it] }, set = { i, v -> samples[i] = v })
        assertEquals(0f, samples[0])
        assertTrue(samples[2] < 0.01f)
    }
}
