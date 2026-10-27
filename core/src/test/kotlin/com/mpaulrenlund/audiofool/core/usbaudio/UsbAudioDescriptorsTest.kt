package com.mpaulrenlund.audiofool.core.usbaudio

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// New (0.3.0, the look-only build): descriptors laid out by hand from the USB Audio Class 1.0 and
// 2.0 specs. Once the user's DAC has been read, its own bytes (Debug's report) join these.

class UsbAudioDescriptorsTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private val device = bytes(0x12, 0x01, 0x00, 0x02, 0xEF, 0x02, 0x01, 0x40, 0x34, 0x12, 0x78, 0x56, 0x00, 0x01, 0x01, 0x02, 0x03, 0x01)
    private val config = bytes(0x09, 0x02, 0x00, 0x00, 0x02, 0x01, 0x00, 0x80, 0xFA)

    /** A typical UAC2 DAC: an asynchronous OUT endpoint with explicit feedback, 32- and 16-bit PCM and native DSD. */
    private val uac2 = device + config +
        bytes(0x08, 0x0B, 0x00, 0x02, 0x01, 0x00, 0x20, 0x00) +                          // interface association
        bytes(0x09, 0x04, 0x00, 0x00, 0x00, 0x01, 0x01, 0x20, 0x00) +                    // audio control, UAC2
        bytes(0x09, 0x24, 0x01, 0x00, 0x02, 0x0A, 0x00, 0x00, 0x00) +                    // header, bcdADC 2.00
        bytes(0x08, 0x24, 0x0A, 0x29, 0x03, 0x07, 0x00, 0x00) +                          // clock 0x29, programmable
        bytes(0x11, 0x24, 0x02, 0x01, 0x01, 0x01, 0x00, 0x29, 0x02, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00) + // input terminal 1 (USB streaming)
        bytes(0x12, 0x24, 0x06, 0x0A, 0x01, 0x0F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00) + // feature unit 10: master mute + volume
        bytes(0x0C, 0x24, 0x03, 0x14, 0x02, 0x03, 0x00, 0x0A, 0x29, 0x00, 0x00, 0x00) +  // output terminal 20 (headphones)
        bytes(0x09, 0x04, 0x01, 0x00, 0x00, 0x01, 0x02, 0x20, 0x00) +                    // streaming alt 0: no endpoints
        streaming(alt = 1, formats = 0x01, subslot = 4, bits = 32) +
        streaming(alt = 2, formats = 0x01, subslot = 2, bits = 16) +
        streaming(alt = 3, formats = 0x80000000L, subslot = 4, bits = 32)

    private fun streaming(alt: Int, formats: Long, subslot: Int, bits: Int) =
        bytes(0x09, 0x04, 0x01, alt, 0x02, 0x01, 0x02, 0x20, 0x00) +
            bytes(0x10, 0x24, 0x01, 0x01, 0x00, 0x01,
                (formats and 0xFF).toInt(), ((formats shr 8) and 0xFF).toInt(), ((formats shr 16) and 0xFF).toInt(), ((formats shr 24) and 0xFF).toInt(),
                0x02, 0x03, 0x00, 0x00, 0x00, 0x00) +
            bytes(0x06, 0x24, 0x02, 0x01, subslot, bits) +
            bytes(0x07, 0x05, 0x01, 0x05, 0x00, 0x04, 0x01) +                            // OUT 0x01: iso, async, 1024 B
            bytes(0x08, 0x25, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00) +
            bytes(0x07, 0x05, 0x81, 0x11, 0x04, 0x00, 0x04)                              // IN 0x81: feedback

    /** A UAC1 DAC: an adaptive endpoint, the rates listed in the format descriptor. */
    private val uac1 = device + config +
        bytes(0x09, 0x04, 0x00, 0x00, 0x00, 0x01, 0x01, 0x00, 0x00) +
        bytes(0x09, 0x24, 0x01, 0x00, 0x01, 0x00, 0x00, 0x01, 0x01) +
        bytes(0x0C, 0x24, 0x02, 0x01, 0x01, 0x01, 0x00, 0x02, 0x03, 0x00, 0x00, 0x00) +
        bytes(0x0A, 0x24, 0x06, 0x02, 0x01, 0x01, 0x03, 0x00, 0x00, 0x00) +
        bytes(0x09, 0x24, 0x03, 0x03, 0x01, 0x03, 0x00, 0x02, 0x00) +
        bytes(0x09, 0x04, 0x01, 0x00, 0x00, 0x01, 0x02, 0x00, 0x00) +
        bytes(0x09, 0x04, 0x01, 0x01, 0x01, 0x01, 0x02, 0x00, 0x00) +
        bytes(0x07, 0x24, 0x01, 0x01, 0x01, 0x01, 0x00) +
        bytes(0x0E, 0x24, 0x02, 0x01, 0x02, 0x02, 0x10, 0x02, 0x44, 0xAC, 0x00, 0x80, 0xBB, 0x00) +
        bytes(0x09, 0x05, 0x01, 0x09, 0xC8, 0x00, 0x01, 0x00, 0x00)

    @Test fun reads_a_uac2_dac() {
        val d = UsbAudioDescriptors.parse(uac2)
        assertEquals(2, d.uacVersion)
        assertEquals(0x1234, d.vendorId)
        assertEquals(0x5678, d.productId)
        assertEquals(0, d.controlInterface)
        assertEquals(listOf(1, 2, 3), d.playback.map { it.alternate })
        val pcm32 = d.playback[0]
        assertEquals(2, pcm32.channels)
        assertEquals(4, pcm32.subslotBytes)
        assertEquals(32, pcm32.bits)
        assertEquals(1, pcm32.interfaceNumber)
        assertEquals(1024, pcm32.dataEndpoint?.maxPacketBytes)
        assertEquals(SyncType.Async, pcm32.dataEndpoint?.sync)
        assertEquals(0x81, pcm32.feedbackEndpoint?.address)
        assertEquals(16, d.playback[1].bits)
    }

    @Test fun finds_the_clock_that_feeds_the_stream() {
        val d = UsbAudioDescriptors.parse(uac2)
        val clock = d.clockSources.single()
        assertEquals(0x29, clock.id)
        assertTrue(clock.rateSettable)
        assertEquals(0x29, d.clockFor(d.playback[0]))
    }

    @Test fun finds_the_hardware_volume() {
        val d = UsbAudioDescriptors.parse(uac2)
        val unit = assertNotNull(d.volumeUnitFor(d.playback[0]))
        assertEquals(0x0A, unit.id)
        assertTrue(unit.masterVolume)
        assertTrue(unit.masterMute)
    }

    @Test fun names_native_dsd_apart_from_pcm() {
        val d = UsbAudioDescriptors.parse(uac2)
        assertEquals("PCM", UsbAudioReport.formatName(2, d.playback[0].formats))
        assertEquals("raw (native DSD)", UsbAudioReport.formatName(2, d.playback[2].formats))
    }

    @Test fun reads_a_uac1_dac_with_its_rates() {
        val d = UsbAudioDescriptors.parse(uac1)
        assertEquals(1, d.uacVersion)
        val s = d.playback.single()
        assertEquals(listOf(44100, 48000), s.rates)
        assertEquals(2, s.subslotBytes)
        assertEquals(16, s.bits)
        assertEquals(200, s.dataEndpoint?.maxPacketBytes)
        assertEquals(SyncType.Adaptive, s.dataEndpoint?.sync)
        assertNull(s.feedbackEndpoint)
        assertNull(d.clockFor(s))
        assertEquals(2, d.volumeUnitFor(s)?.id)
    }

    @Test fun a_device_without_audio_is_not_a_dac() {
        val d = UsbAudioDescriptors.parse(device + config + bytes(0x09, 0x04, 0x00, 0x00, 0x01, 0x08, 0x06, 0x50, 0x00))
        assertFalse(d.isAudio)
        assertEquals(listOf("no USB Audio interface"), UsbAudioReport.describe(d))
    }

    // Cut inside the last setting's format descriptor: that setting has no endpoints, the others stand.
    @Test fun stops_at_a_broken_descriptor() {
        val d = UsbAudioDescriptors.parse(uac2.copyOf(uac2.size - 25))
        assertEquals(2, d.uacVersion)
        assertEquals(2, d.playback.size)
    }

    @Test fun describes_each_setting_in_one_line() {
        val d = UsbAudioDescriptors.parse(uac2)
        assertEquals(
            "interface 1 alt 1: PCM 32-bit in 4 bytes, 2 ch · clock 41 · async · feedback 0x81 · 1024 B every 1 µframe",
            UsbAudioReport.setting(d, d.playback[0]),
        )
    }

    @Test fun a_clock_that_lists_its_rates() {
        val reply = bytes(0x03, 0x00) + range(44100, 44100, 0) + range(48000, 48000, 0) + range(96000, 96000, 0)
        assertEquals(listOf(44100, 48000, 96000), UacRanges.rates(reply))
    }

    @Test fun a_clock_with_a_continuous_range() =
        assertEquals(listOf(32000, 44100, 48000, 88200, 96000, 176400, 192000), UacRanges.rates(bytes(0x01, 0x00) + range(32000, 192000, 1)))

    @Test fun a_volume_range_in_256ths_of_a_db() =
        assertEquals(Triple(-32512, 0, 256), UacRanges.volume(bytes(0x01, 0x00, 0x00, 0x81, 0x00, 0x00, 0x00, 0x01)))

    private fun range(min: Int, max: Int, res: Int) = le32(min) + le32(max) + le32(res)
    private fun le32(v: Int) = bytes(v and 0xFF, (v shr 8) and 0xFF, (v shr 16) and 0xFF, (v shr 24) and 0xFF)
}
