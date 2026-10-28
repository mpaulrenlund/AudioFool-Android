package com.mpaulrenlund.audiofool.core.usbaudio

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// New (0.4.0): the driver's rules against the user's UA2 Plus, and Linux's feedback and packet sums.

class UsbStreamRulesTest {
    private val ua2 = UsbAudioDescriptors.parse(UsbDacFixtures.ua2Plus)

    /** What the UA2 Plus's clock answered on the S24 (0.3.0). */
    private val ua2Rates = listOf(44100, 48000, 88200, 96000, 176400, 192000, 352800, 384000, 705600, 768000)

    private fun alt(rate: Int, bits: Int, dop: Boolean = false, channels: Int = 2) =
        UsbFormatChoice.pick(ua2, ua2Rates, rate, channels, bits, dop)?.alternate

    @Test fun a_16_bit_track_goes_out_as_16_bit() = assertEquals(1, alt(44100, 16))

    @Test fun a_24_bit_track_goes_out_as_24_bit_in_3_bytes() = assertEquals(2, alt(96000, 24))

    @Test fun a_lossy_track_goes_out_in_the_widest_slot() = assertEquals(3, alt(44100, 0))

    @Test fun dop_goes_in_24_bits_never_the_native_dsd_setting() = assertEquals(2, alt(176400, 24, dop = true))

    @Test fun a_rate_the_dac_doesnt_offer_has_no_setting() = assertNull(alt(22050, 16))

    @Test fun mono_has_no_setting_on_a_stereo_dac() = assertNull(alt(44100, 16, channels = 1))

    @Test fun the_nominal_rate_is_linuxs() {
        // 44.1 kHz is 5.5125 frames a microframe (361267.2 in 16.16), 44.1 a frame (2890137.6, rounded).
        assertEquals(361267, UsbFeedback.nominal(44100, highSpeed = true))
        assertEquals(2890138, UsbFeedback.nominal(44100, highSpeed = false))
    }

    @Test fun feedback_in_16_16_is_taken_as_it_is() {
        val f = UsbFeedback(44100, highSpeed = true)
        assertTrue(f.take(361300))
        assertEquals(0, f.shift)
        assertEquals(361300, f.current)
    }

    @Test fun feedback_in_another_format_is_shifted_to_fit() {
        val f = UsbFeedback(48000, highSpeed = true)
        // 6 frames a microframe in 10.14, as some DACs send it.
        assertTrue(f.take(6 shl 14))
        assertEquals(2, f.shift)
        assertEquals(6 shl 16, f.current)
        assertTrue(f.take((6 shl 14) + 3))
        assertEquals((6 shl 16) + 12, f.current)
    }

    @Test fun feedback_far_off_is_ignored_and_the_format_found_again() {
        val f = UsbFeedback(48000, highSpeed = true)
        assertTrue(f.take(6 shl 16))
        assertFalse(f.take(3 shl 16))
        assertEquals(6 shl 16, f.current)
        assertNull(f.shift)
        assertFalse(f.take(0))
    }

    @Test fun reads_a_feedback_packet() {
        val bytes = byteArrayOf(0x33, 0x83.toByte(), 0x05, 0xF0.toByte())
        assertEquals(0x00058333, UsbFeedback.valueOf(bytes, 4))
        assertEquals(0x058333, UsbFeedback.valueOf(bytes, 3))
    }

    @Test fun packets_carry_the_rate_exactly_over_a_second() {
        val sizer = PacketSizer(dataInterval = 0, maxFrames = 97)
        val rate = UsbFeedback.nominal(44100, highSpeed = true)
        val frames = (1..8000).sumOf { sizer.next(rate) }
        assertTrue(frames in 44099..44100, "$frames")
    }

    @Test fun a_packet_never_carries_more_than_it_holds() {
        val sizer = PacketSizer(dataInterval = 0, maxFrames = 5)
        assertEquals(5, sizer.next(UsbFeedback.nominal(48000, highSpeed = true)))
    }

    // The UA2 Plus: -63.5 to 0 dB in 0.5 dB steps (0.3.0's report).
    private val volume = UsbVolume(min = -16256, max = 0, resolution = 128)

    @Test fun the_top_step_is_the_dacs_loudest() = assertEquals(0, volume.value(UsbVolume.STEPS))

    @Test fun each_step_is_a_db() {
        assertEquals(-256, volume.value(59))
        assertEquals(-59 * 256, volume.value(1))
    }

    @Test fun step_0_is_muted_at_the_bottom() {
        assertTrue(volume.muted(0))
        assertEquals(-16256, volume.value(0))
    }

    @Test fun a_step_snaps_to_the_dacs_resolution() {
        val coarse = UsbVolume(min = -16256, max = 0, resolution = 768)
        assertEquals(0, (coarse.value(50) - coarse.min) % 768)
    }

    @Test fun the_dacs_own_value_maps_back_to_a_step() {
        assertEquals(60, volume.stepNear(0))
        assertEquals(48, volume.stepNear(-12 * 256))
    }

    // 0.4.3: taking the DAC is never louder than what was heard through Android.
    @Test fun taking_the_dac_moves_androids_cut_onto_the_dac() {
        // The DAC at 0 dB, Android's media volume taking 24.5 dB off: the DAC goes to -24.5 dB.
        assertEquals(-24 * 256 - 128, UsbVolume.startingValue(0, -24.5f))
        // Already turned down to -55 dB, Android at full: stays at -55 dB.
        assertEquals(-55 * 256, UsbVolume.startingValue(-55 * 256, 0f))
    }

    @Test fun android_at_zero_starts_muted() {
        assertNull(UsbVolume.startingValue(0, Float.NEGATIVE_INFINITY))
        assertNull(UsbVolume.startingValue(0, Float.NaN))
    }

    @Test fun a_gain_from_android_is_never_added() = assertEquals(-10 * 256, UsbVolume.startingValue(-10 * 256, 3f))

    @Test fun the_starting_step_is_never_louder_than_asked() {
        assertEquals(35, volume.stepAtOrBelow(-24 * 256 - 128))
        assertTrue(volume.value(volume.stepAtOrBelow(-24 * 256 - 128)) <= -24 * 256 - 128)
        assertEquals(60, volume.stepAtOrBelow(0))
        assertEquals(5, volume.stepAtOrBelow(-55 * 256))
    }

    @Test fun quieter_than_step_1_is_muted() = assertEquals(0, volume.stepAtOrBelow(-60 * 256))
}
