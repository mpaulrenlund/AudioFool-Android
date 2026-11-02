package com.mpaulrenlund.audiofool.core.analysis

import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Ported from the desktop's tests/AudioFool.Core.Tests/QualityOpinionTests.cs: the
// opinion's rules, on synthetic spectra shaped like the real files they were set
// against (see QualityOpinion).

class QualityOpinionTest {
    private val cdFlac = QualityClaim("FLAC", 16, 44100, 900, false, false, false)
    private val hiResFlac = QualityClaim("FLAC", 24, 96000, 2800, false, false, false)
    private val mp3At320 = QualityClaim("MP3", null, 44100, 320, true, false, false)

    /** Music falling 2 dB per kHz from -40 dB at 1 kHz. */
    private fun music(hz: Double) = -40 - 2 * (hz - 1000) / 1000

    /** A lossy encoder's lowpass: the music, then decoder silence. */
    private fun cutAt(cutoff: Double): (Double) -> Double = { hz -> if (hz < cutoff) music(hz) else -140.0 }

    @Test fun a_gradual_rolloff_is_consistent_with_lossless() {
        val opinion = QualityOpinion.form(spectrum(44100, ::music), cdFlac)

        assertEquals(Verdict.Consistent, opinion.verdict)
        assertEquals("Consistent with FLAC · 16-bit · 44.1 kHz", opinion.headline)
        assertNull(opinion.cutoffHz)
    }

    @Test fun a_cliff_at_16_kHz_in_a_flac_is_a_lossy_source() {
        val opinion = QualityOpinion.form(spectrum(44100, cutAt(16000.0)), cdFlac)

        assertEquals(Verdict.Inconsistent, opinion.verdict)
        assertEquals("Made from a lossy file", opinion.headline)
        assertTrue(opinion.cutoffHz!! in 15800.0..16200.0)
        assertTrue("about 128 kbps" in opinion.findings[0].explanation)
    }

    @Test fun a_cliff_at_20_kHz_is_only_suspect_since_some_masters_have_one() {
        val opinion = QualityOpinion.form(spectrum(44100, cutAt(20200.0)), cdFlac)

        assertEquals(Verdict.Suspect, opinion.verdict)
        assertEquals("Possibly from a lossy file", opinion.headline)
    }

    @Test fun a_converter_filter_at_21_kHz_is_consistent() {
        val opinion = QualityOpinion.form(spectrum(44100, cutAt(21200.0)), cdFlac)

        assertEquals(Verdict.Consistent, opinion.verdict)
        assertTrue(opinion.cutoffHz!! in 21000.0..21400.0)
        assertTrue("converter" in opinion.findings[0].explanation)
    }

    @Test fun a_single_loud_tone_does_not_make_a_cliff() {
        // FM synthesis: quiet treble with one loud partial in it.
        val opinion = QualityOpinion.form(spectrum(44100, { hz -> if (abs(hz - 16750) < 100) -58.0 else -82.0 }), cdFlac)

        assertNull(opinion.cutoffHz)
        assertEquals(Verdict.Consistent, opinion.verdict)
    }

    @Test fun a_cliff_that_recovers_is_not_a_cutoff() {
        assertNull(QualityOpinion.findCliff(spectrum(44100, { hz -> if (hz > 17000 && hz < 17600) -140.0 else music(hz) })))
    }

    @Test fun treble_too_faint_to_judge_is_inconclusive() {
        // A dull old recording: fading 6 dB per kHz into the noise, no cliff anywhere.
        val opinion = QualityOpinion.form(spectrum(44100, { hz -> max(-125.0, -40 - 6 * (hz - 1000) / 1000) }), cdFlac)

        assertEquals(Verdict.Inconclusive, opinion.verdict)
        assertEquals("Can't tell", opinion.headline)
    }

    @Test fun a_320_kbps_mp3_cut_at_16_kHz_was_re_encoded() {
        val opinion = QualityOpinion.form(spectrum(44100, cutAt(16000.0)), mp3At320)

        assertEquals(Verdict.Inconsistent, opinion.verdict)
        assertEquals("Re-encoded from a lower bitrate", opinion.headline)
    }

    @Test fun a_320_kbps_mp3_cut_at_20_kHz_is_consistent() {
        val opinion = QualityOpinion.form(spectrum(44100, cutAt(20300.0)), mp3At320)

        assertEquals(Verdict.Consistent, opinion.verdict)
        assertEquals("Consistent with MP3 · 320 kbps · 44.1 kHz", opinion.headline)
    }

    @Test fun a_low_rate_mp3_is_not_expected_to_reach_past_its_own_range() {
        val claim = QualityClaim("MP3", null, 32000, 320, true, false, false)
        val opinion = QualityOpinion.form(spectrum(32000, cutAt(15100.0)), claim)

        assertEquals(Verdict.Consistent, opinion.verdict)
    }

    @Test fun hi_res_with_real_ultrasonic_content_is_consistent() {
        val opinion = QualityOpinion.form(spectrum(96000, ::music, usedBits = 24), hiResFlac)

        assertEquals(Verdict.Consistent, opinion.verdict)
        assertEquals("Consistent with FLAC · 24-bit · 96 kHz", opinion.headline)
        assertTrue(opinion.findings.any { "above 25 kHz" in it.explanation })
    }

    @Test fun hi_res_with_nothing_above_cd_range_was_upsampled() {
        // A resampler's gentle filter from 20 kHz into silence by 24 kHz.
        val upsampled = { hz: Double -> if (hz < 20000) music(hz) else max(-143.0, music(20000.0) - (hz - 20000) / 4000 * 64) }
        val opinion = QualityOpinion.form(spectrum(96000, upsampled, usedBits = 24), hiResFlac)

        assertEquals(Verdict.Inconsistent, opinion.verdict)
        assertEquals("Not true hi-res", opinion.headline)
    }

    @Test fun hi_res_with_a_mirror_image_above_22_kHz_was_upsampled() {
        // Treble mirrored about 22.05 kHz, as a weak interpolation filter leaves it.
        val imaged = { hz: Double ->
            when {
                hz < 21000 -> music(hz)
                hz < 23200 -> -102.0
                hz < 30000 -> music(44100 - hz) - 6
                else -> -115.0
            }
        }
        val opinion = QualityOpinion.form(spectrum(96000, imaged, usedBits = 24), hiResFlac)

        assertEquals(Verdict.Inconsistent, opinion.verdict)
        assertTrue(opinion.findings.any { "mirror image" in it.explanation })
    }

    @Test fun hi_res_with_only_faint_noise_up_high_is_suspect() {
        val opinion = QualityOpinion.form(spectrum(96000, ::faint, usedBits = 24), hiResFlac)

        assertEquals(Verdict.Suspect, opinion.verdict)
        assertEquals("Possibly not true hi-res", opinion.headline)
    }

    // Snarky Puppy's Empire Central, measured on the desktop: the ultrasonic band
    // ~40 dB under the treble, fading smoothly. Real 96 kHz recordings often look like this.
    @Test fun hi_res_with_quiet_real_content_up_high_is_consistent() {
        val quiet = { hz: Double -> if (hz < 22000) music(hz) else music(22000.0) - 5.5 * (hz - 22000) / 1000 }
        val analysis = spectrum(96000, quiet, usedBits = 24)
        val below = QualityOpinion.level(analysis, 14000.0, 19000.0) - QualityOpinion.level(analysis, 25000.0, 32000.0)
        assertTrue(below in 35.0..QualityOpinion.FAINT_ULTRASONIC_DB - 1)

        val opinion = QualityOpinion.form(analysis, hiResFlac)

        assertEquals(Verdict.Consistent, opinion.verdict)
        assertTrue(opinion.flags.isEmpty())
    }

    @Test fun hi_res_made_from_a_lossy_file_says_so() {
        val opinion = QualityOpinion.form(spectrum(96000, cutAt(18800.0), usedBits = 24), hiResFlac)

        assertEquals(Verdict.Inconsistent, opinion.verdict)
        assertEquals("Made from a lossy file", opinion.headline)
    }

    @Test fun padded_24_bit_is_not_true_24_bit() {
        val claim = QualityClaim("FLAC", 24, 44100, 1500, false, false, false)
        val opinion = QualityOpinion.form(spectrum(44100, ::music, usedBits = 16), claim)

        assertEquals(Verdict.Inconsistent, opinion.verdict)
        assertEquals("Not true 24-bit", opinion.headline)
        assertTrue("8 zero bits" in opinion.findings[0].explanation) // the reason comes first
    }

    @Test fun twenty_bits_of_twenty_four_are_fine() {
        val claim = QualityClaim("FLAC", 24, 44100, 1500, false, false, false)
        val opinion = QualityOpinion.form(spectrum(44100, ::music, usedBits = 20), claim)

        assertEquals(Verdict.Consistent, opinion.verdict)
        assertTrue("20 of the 24 bits" in opinion.findings.last().explanation)
    }

    @Test fun sixteen_bit_music_with_24_bit_fades_is_not_true_24_bit() {
        // Measured on a real album: 24 bits in the fades, 7% of samples in all.
        val claim = QualityClaim("FLAC", 24, 48000, 1200, false, false, false)
        val opinion = QualityOpinion.form(spectrum(48000, ::music, usedBits = 24, extraBitsShare = 0.07), claim)

        assertEquals(Verdict.Inconsistent, opinion.verdict)
        assertEquals("Not true 24-bit", opinion.headline)
        assertTrue("7%" in opinion.findings[0].explanation)
        assertEquals(listOf(QualityFlag.Padded24Bit), opinion.flags)
    }

    @Test fun a_cliff_far_below_any_encoder_is_the_music_not_a_lossy_source() {
        // An N64 soundtrack's low-rate samples: nothing above 6.4 kHz.
        val opinion = QualityOpinion.form(spectrum(44100, cutAt(6400.0)), cdFlac)

        assertEquals(Verdict.Inconclusive, opinion.verdict)
        assertTrue(opinion.flags.isEmpty())
        assertTrue("far below" in opinion.findings[0].explanation)
    }

    @Test fun a_192_kbps_mp3_cut_at_16_kHz_is_only_possibly_re_encoded() {
        val claim = QualityClaim("MP3", null, 44100, 192, true, false, false)
        val opinion = QualityOpinion.form(spectrum(44100, cutAt(16100.0)), claim)

        assertEquals(Verdict.Suspect, opinion.verdict)
        assertEquals("Possibly re-encoded from a lower bitrate", opinion.headline)
        assertEquals(listOf(QualityFlag.PossiblyReEncodedLossy), opinion.flags)
    }

    @Test fun a_320_kbps_mp3_cut_at_18_kHz_is_only_possibly_re_encoded() {
        val opinion = QualityOpinion.form(spectrum(44100, cutAt(18300.0)), mp3At320)
        assertEquals(listOf(QualityFlag.PossiblyReEncodedLossy), opinion.flags)
    }

    @Test fun lossless_cutoffs_carry_their_flag() {
        for ((cutoff, flag) in listOf(16000.0 to QualityFlag.LossySource, 20200.0 to QualityFlag.PossiblyLossySource)) {
            assertEquals(listOf(flag), QualityOpinion.form(spectrum(44100, cutAt(cutoff)), cdFlac).flags)
        }
    }

    @Test fun upsampled_hi_res_carries_its_flag_and_a_clean_file_none() {
        assertEquals(listOf(QualityFlag.PossiblyUpsampled), QualityOpinion.form(spectrum(96000, ::faint, usedBits = 24), hiResFlac).flags)
        assertTrue(QualityOpinion.form(spectrum(96000, ::music, usedBits = 24), hiResFlac).flags.isEmpty())
    }

    @Test fun the_re_encoded_flag_is_set() {
        assertEquals(listOf(QualityFlag.ReEncodedLossy), QualityOpinion.form(spectrum(44100, cutAt(16000.0)), mp3At320).flags)
    }

    @Test fun a_quiet_track_is_inconclusive() {
        val opinion = QualityOpinion.form(spectrum(44100, cutAt(16000.0), peakDb = -70.0), cdFlac)
        assertEquals(Verdict.Inconclusive, opinion.verdict)
    }

    @Test fun a_tracker_module_has_nothing_to_check() {
        val claim = QualityClaim("XM", null, 44100, null, false, false, true)
        val opinion = QualityOpinion.form(spectrum(44100, ::music), claim)

        assertEquals(Verdict.Inconclusive, opinion.verdict)
        assertEquals("Nothing to check", opinion.headline)
    }

    @Test fun the_claim_reads_like_the_grid() {
        val cases = listOf(
            QualityClaim("FLAC", 16, 44100, 900, false, false, false) to "FLAC · 16-bit · 44.1 kHz",
            QualityClaim("FLAC", 24, 96000, 2800, false, false, false) to "FLAC · 24-bit · 96 kHz",
            QualityClaim("MP3", null, 48000, 128, true, false, false) to "MP3 · 128 kbps · 48 kHz",
            QualityClaim("DSD", 1, 5644800, 11356, false, true, false) to "DSD128",
            QualityClaim("DSD", 1, 2822400, 5645, false, true, false) to "DSD64",
        )
        for ((claim, expected) in cases) assertEquals(expected, claim.describe())
    }

    @Test fun frequencies_read_naturally() {
        for ((hz, expected) in listOf(96000.0 to "96 kHz", 44100.0 to "44.1 kHz", 22050.0 to "22.05 kHz", 20187.5 to "20.2 kHz")) {
            assertEquals(expected, QualityOpinion.khz(hz))
        }
    }

    /** Fading 8 dB per kHz past 22 kHz into a noise floor: no cliff, little content. */
    private fun faint(hz: Double) = if (hz < 22000) music(hz) else max(-118.0, music(22000.0) - 8 * (hz - 22000) / 1000)

    /** A finished analysis whose average spectrum follows [db]. */
    private fun spectrum(rate: Int, db: (Double) -> Double, usedBits: Int? = null, peakDb: Double = -0.5, extraBitsShare: Double? = null): SpectrumAnalysis {
        val size = SpectrumAccumulator.fftSizeFor(rate)
        val bins = size / 2 + 1
        val average = DoubleArray(bins) { k -> max(SpectrumAccumulator.FLOOR_DB, db(k.toDouble() * rate / size)) }

        return SpectrumAnalysis(
            sampleRate = rate,
            channels = 2,
            durationSeconds = 240.0,
            fftSize = size,
            average = average,
            peak = average,
            columns = 1,
            rows = 1,
            picture = floatArrayOf(-60f),
            peakSampleDb = peakDb,
            usedBits = usedBits,
            extraBitsShare = extraBitsShare ?: when {
                usedBits == null -> null
                usedBits > 16 -> 0.99
                else -> 0.0
            },
        )
    }
}
