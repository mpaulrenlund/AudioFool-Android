package com.mpaulrenlund.audiofool.core.analysis

import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Ported from the desktop's tests/AudioFool.Core.Tests/WaveformTests.cs. Its
// missing-file test belongs to Waveform.Read, which needs BASS and lives in the
// app (playback/WaveformReader); it is checked on the emulator.

class WaveformLevelsTest {
    @Test fun a_steady_tone_is_level_at_full_height() {
        val levels = feed(44100, 2, seconds = 10, columns = 100) { 0.5 }
        assertEquals(100, levels.size)
        levels.forEach { assertEquals(1f, it, 1e-3f) }
    }

    @Test fun a_quiet_half_then_a_loud_half_reads_as_quiet_then_loud() {
        // Ten times quieter, so a tenth of the height: the scale is linear.
        val levels = feed(44100, 2, seconds = 10, columns = 100) { t -> if (t < 5) 0.05 else 0.5 }
        levels.take(48).forEach { assertEquals(0.1f, it, 1e-2f) }
        levels.drop(52).forEach { assertEquals(1f, it, 1e-3f) }
    }

    @Test fun silence_gives_zeros_not_nan() {
        val levels = feed(44100, 2, seconds = 3, columns = 50) { 0.0 }
        assertEquals(50, levels.size)
        levels.forEach { assertEquals(0f, it) }
    }

    @Test fun an_unknown_length_still_fills_every_column() {
        val levels = feed(44100, 1, seconds = 30, columns = 200, knownLength = false) { t -> if (t < 15) 0.2 else 0.4 }
        assertEquals(200, levels.size)
        assertEquals(0.5f, levels[50], 1e-2f)
        assertEquals(1f, levels[150], 1e-2f)
    }

    @Test fun a_length_estimate_that_is_short_still_spreads_evenly() {
        // A VBR MP3's length is an estimate; here the file runs 20% longer.
        val rate = 8000
        val levels = WaveformLevels(1, totalFrames = rate * 10L, columns = 100)
        levels.add(signal(rate, 1, seconds = 12) { t -> if (t < 6) 0.1 else 0.3 })

        val result = levels.finish()
        assertEquals(100, result.size)
        assertEquals(1f / 3, result[40], 1e-2f)
        assertEquals(1f, result[60], 1e-3f)
    }

    @Test fun a_track_shorter_than_the_columns_gives_fewer_columns() {
        val levels = WaveformLevels(2, totalFrames = 10, columns = 600)
        levels.add(FloatArray(20))
        assertEquals(10, levels.finish().size)
    }

    @Test fun nothing_read_gives_nothing() = assertTrue(WaveformLevels(2, totalFrames = 0, columns = 600).finish().isEmpty())

    @Test fun channels_are_measured_together() {
        // Left loud, right silent, throughout: still level, not halved in places.
        val levels = WaveformLevels(2, totalFrames = 8000, columns = 10)
        val samples = FloatArray(16000)
        for (i in samples.indices step 2) samples[i] = 0.5f
        levels.add(samples)
        levels.finish().forEach { assertEquals(1f, it, 1e-3f) }
    }

    private fun feed(
        rate: Int, channels: Int, seconds: Int, columns: Int, knownLength: Boolean = true, amplitude: (Double) -> Double,
    ): FloatArray {
        val levels = WaveformLevels(channels, if (knownLength) rate.toLong() * seconds else 0, columns)
        val samples = signal(rate, channels, seconds, amplitude)

        // In uneven pieces, as a decoder hands them over.
        var at = 0
        while (at < samples.size) {
            val length = minOf(7777 * channels, samples.size - at)
            levels.add(samples, at, length)
            at += 7777 * channels
        }
        return levels.finish()
    }

    /** A 440 Hz sine whose amplitude follows [amplitude] (seconds). */
    private fun signal(rate: Int, channels: Int, seconds: Int, amplitude: (Double) -> Double): FloatArray {
        val samples = FloatArray(rate * seconds * channels)
        for (i in 0 until rate * seconds) {
            val t = i.toDouble() / rate
            val value = (amplitude(t) * sin(2 * PI * 440 * t)).toFloat()
            for (c in 0 until channels) samples[i * channels + c] = value
        }
        return samples
    }
}
