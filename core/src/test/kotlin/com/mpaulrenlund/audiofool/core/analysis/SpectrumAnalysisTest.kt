package com.mpaulrenlund.audiofool.core.analysis

import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Ported from the desktop's tests/AudioFool.Core.Tests/SpectrumAnalysisTests.cs: the
// FFT and the accumulator, fed synthetic audio. Its AnalysisSampling test belongs to
// the library-wide Quality Check, which the phone doesn't have.

class SpectrumAnalysisTest {
    @Test fun fft_puts_a_sine_in_its_own_bin() {
        val fft = Fft(256)
        val re = DoubleArray(256) { i -> sin(2 * PI * 32 * i / 256) }
        val im = DoubleArray(256)

        fft.transform(re, im)

        val magnitudes = DoubleArray(129) { k -> sqrt(re[k] * re[k] + im[k] * im[k]) }
        assertEquals(32, magnitudes.indices.maxBy { magnitudes[it] })
        assertEquals(128.0, magnitudes[32], 1e-6)
        assertTrue(magnitudes.withIndex().filter { it.index != 32 }.all { it.value < 1e-9 })
    }

    @Test fun fft_refuses_a_size_that_is_not_a_power_of_two() {
        assertFailsWith<IllegalArgumentException> { Fft(1000) }
    }

    @Test fun fft_size_keeps_about_eleven_hertz_per_bin() {
        for ((rate, size) in listOf(44100 to 4096, 48000 to 4096, 96000 to 8192, 176400 to 16384, 192000 to 16384)) {
            assertEquals(size, SpectrumAccumulator.fftSizeFor(rate), "$rate")
        }
    }

    @Test fun a_full_scale_sine_reads_0_dB_in_its_bin_and_nothing_elsewhere() {
        val rate = 44100
        val hz = 100 * rate / 4096.0 // exactly on bin 100
        val analysis = feed(rate, 2, seconds = 3.0) { t, _ -> sin(2 * PI * hz * t) }

        assertEquals(0.0, analysis.average[100], 0.1)
        assertEquals(0.0, analysis.peak[100], 0.1)
        assertTrue(analysis.average[analysis.binOf(10000.0)] < -100)
        assertEquals(0.0, analysis.peakSampleDb, 0.1)
        assertEquals(3.0, analysis.durationSeconds, 0.01)
    }

    @Test fun channels_are_averaged_by_power_so_opposite_phases_do_not_cancel() {
        val rate = 44100
        val hz = 200 * rate / 4096.0
        val analysis = feed(rate, 2, seconds = 2.0) { t, c -> (if (c == 0) 1 else -1) * 0.5 * sin(2 * PI * hz * t) }

        // Half amplitude is -6 dB in each channel, so -6 dB on average too.
        assertEquals(-6.02, analysis.average[200], 0.1)
    }

    @Test fun the_picture_has_a_row_per_band_and_shows_the_tone_in_its_row() {
        val rate = 44100
        val analysis = feed(rate, 1, seconds = 10.0) { t, _ -> 0.5 * sin(2 * PI * 5000 * t) }

        assertEquals(800, analysis.columns)
        assertEquals(512, analysis.rows)
        assertEquals(800 * 512, analysis.picture.size)

        val expectedRow = (5000 / (rate / 2.0) * 512).toInt()
        for (column in 0 until analysis.columns step 97) {
            val slice = analysis.picture.copyOfRange(column * 512, column * 512 + 512)
            assertTrue(slice.indices.maxBy { slice[it] } in expectedRow - 1..expectedRow + 1)
        }
    }

    @Test fun a_track_shorter_than_one_transform_still_gets_a_picture() {
        val analysis = feed(44100, 2, seconds = 0.05) { t, _ -> 0.1 * sin(2 * PI * 1000 * t) }

        assertTrue(analysis.average.max() > -60)
        assertTrue(analysis.picture.none { it.isNaN() })
        assertTrue(analysis.picture.max() > -100)
    }

    @Test fun a_track_shorter_than_half_a_transform_still_gets_a_picture() {
        // 20 ms: the one transform's centre falls before the start of the track,
        // which used to put it in column -1 ("The analysis failed") on the desktop.
        val analysis = feed(44100, 2, seconds = 0.02) { t, _ -> 0.1 * sin(2 * PI * 1000 * t) }

        assertTrue(analysis.average.max() > -60)
        assertTrue(analysis.picture.max() > -100)
    }

    @Test fun silence_reads_as_the_floor() {
        val analysis = feed(44100, 2, seconds = 1.0) { _, _ -> 0.0 }

        analysis.average.forEach { assertEquals(SpectrumAccumulator.FLOOR_DB, it) }
        assertEquals(SpectrumAccumulator.FLOOR_DB, analysis.peakSampleDb)
    }

    @Test fun sixteen_bit_values_in_a_24_bit_file_use_16_bits() {
        val analysis = feed(44100, 2, seconds = 1.0, checkBits = true) { t, _ -> quantise(0.5 * sin(2 * PI * 440 * t), 16) }
        assertEquals(16, analysis.usedBits)
    }

    @Test fun real_24_bit_values_use_24_bits() {
        val analysis = feed(44100, 2, seconds = 1.0, checkBits = true) { t, _ -> quantise(0.5 * sin(2 * PI * 440 * t), 24) }
        assertEquals(24, analysis.usedBits)
    }

    @Test fun sixteen_bit_music_with_a_24_bit_fade_uses_24_bits_but_mostly_16() {
        // Nine seconds on the 16-bit grid, then a one-second 24-bit fade.
        val analysis = feed(44100, 1, seconds = 10.0, checkBits = true) { t, _ ->
            quantise(0.5 * sin(2 * PI * 440 * t), if (t < 9) 16 else 24)
        }

        assertEquals(24, analysis.usedBits)
        assertTrue(analysis.extraBitsShare!! in 0.08..0.12)
    }

    @Test fun real_24_bit_values_use_the_extra_bits_in_nearly_every_sample() {
        val analysis = feed(44100, 1, seconds = 1.0, checkBits = true) { t, _ -> quantise(0.5 * sin(2 * PI * 440 * t), 24) }
        assertTrue(analysis.extraBitsShare!! > 0.98)
    }

    @Test fun a_restart_keeps_the_join_between_slices_out_of_the_spectrum() {
        // Two slices of a quiet tone whose phase jumps at the join: heard across
        // the join it is a click, which would put noise in every band.
        val rate = 44100
        val hz = 100 * rate / 4096.0
        fun slice(phase: Double) = FloatArray(rate) { i -> (0.5 * sin(2 * PI * hz * i / rate + phase)).toFloat() }

        fun run(restart: Boolean): SpectrumAnalysis {
            val accumulator = SpectrumAccumulator(rate, 1, 2L * rate, 16, 16)
            accumulator.add(slice(0.0))
            if (restart) accumulator.restart()
            accumulator.add(slice(PI / 2))
            return accumulator.finish()
        }

        val top = run(restart = true).binOf(18000.0)
        assertTrue(run(restart = true).average[top] < -140)
        assertTrue(run(restart = false).average[top] > -120)
    }

    @Test fun bits_are_not_counted_unless_asked_or_when_samples_are_off_the_24_bit_grid() {
        assertNull(feed(44100, 1, seconds = 1.0) { t, _ -> quantise(sin(t * 1000), 16) }.usedBits)
        assertNull(feed(44100, 1, seconds = 1.0, checkBits = true) { t, _ -> 0.3 * sin(t * 1000) }.usedBits)
    }

    /** A sample on an n-bit grid, as float, as BASS hands back a lossless decode. */
    private fun quantise(x: Double, bits: Int): Double {
        val scale = 2.0.pow(bits - 1)
        return (Math.rint(x * scale) / scale).toFloat().toDouble()
    }

    private fun feed(rate: Int, channels: Int, seconds: Double, checkBits: Boolean = false, sample: (Double, Int) -> Double): SpectrumAnalysis {
        val frames = (rate * seconds).toLong()
        val accumulator = SpectrumAccumulator(rate, channels, frames, checkBits = checkBits)
        val buffer = FloatArray(4096 * channels)
        var done = 0L
        while (done < frames) {
            val count = minOf(4096L, frames - done).toInt()
            for (f in 0 until count) {
                val t = (done + f).toDouble() / rate
                for (c in 0 until channels) buffer[f * channels + c] = sample(t, c).toFloat()
            }
            accumulator.add(buffer, 0, count * channels)
            done += count
        }
        return accumulator.finish()
    }
}
