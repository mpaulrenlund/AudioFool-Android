package com.mpaulrenlund.audiofool.core.playback

import org.junit.Test
import kotlin.test.assertEquals

// Session 4's rules for Bit-Perfect on a phone (spec 6.2, 6.3): which way it
// works on each route, which USB mixer entry a track plays through, and the readout.

class BitPerfectTest {
    // ---------- the way per route ----------

    @Test fun usb_on_android_14_uses_the_mixer() =
        assertEquals(BitPerfectWay.UsbMixer, BitPerfectRule.way(OutputRoute.Usb, mixerApi = true, usbMixerEntries = 12))

    @Test fun usb_that_lists_no_mixer_entries_is_greyed() =
        assertEquals(BitPerfectWay.None, BitPerfectRule.way(OutputRoute.Usb, mixerApi = true, usbMixerEntries = 0))

    @Test fun usb_before_android_14_and_a_headphone_out_rely_on_the_source_rate() {
        assertEquals(BitPerfectWay.SourceRate, BitPerfectRule.way(OutputRoute.Usb, mixerApi = false, usbMixerEntries = 0))
        assertEquals(BitPerfectWay.SourceRate, BitPerfectRule.way(OutputRoute.Headphones, mixerApi = false, usbMixerEntries = 0))
        assertEquals(BitPerfectWay.SourceRate, BitPerfectRule.way(OutputRoute.Headphones, mixerApi = true, usbMixerEntries = 0))
    }

    @Test fun bluetooth_and_the_speaker_are_greyed() {
        for (route in listOf(OutputRoute.Bluetooth, OutputRoute.Speaker, OutputRoute.Other)) {
            assertEquals(BitPerfectWay.None, BitPerfectRule.way(route, mixerApi = true, usbMixerEntries = 12))
        }
    }

    // ---------- a player whose speaker is its headphone out (0.1.1) ----------

    @Test fun a_fiio_player_is_one_whose_speaker_is_its_headphone_out() {
        assertEquals(true, SpeakerlessPlayers.speakerIsHeadphones("FiiO", "FiiO"))
        assertEquals(true, SpeakerlessPlayers.speakerIsHeadphones("fiio", "other"))
        assertEquals(true, SpeakerlessPlayers.speakerIsHeadphones("unknown", "FIIO"))
        // The M21 (0.1.2's report): only its speaker's name was known to say FiiO.
        assertEquals(true, SpeakerlessPlayers.speakerIsHeadphones("QUALCOMM", "qti", "M21", "FiiO M21"))
        assertEquals(true, SpeakerlessPlayers.speakerIsHeadphones("unknown", null, "FiiO M21", null))
        assertEquals(false, SpeakerlessPlayers.speakerIsHeadphones("samsung", "samsung", "SM-S928B", "SM-S928B"))
        assertEquals(false, SpeakerlessPlayers.speakerIsHeadphones(null, null, null, null))
    }

    @Test fun there_its_speaker_is_the_headphone_route_and_bp_can_be_used() {
        val route = SpeakerlessPlayers.route(OutputRoute.Speaker, speakerIsHeadphones = true)
        assertEquals(OutputRoute.Headphones, route)
        assertEquals(BitPerfectWay.SourceRate, BitPerfectRule.way(route, mixerApi = false, usbMixerEntries = 0))
    }

    @Test fun other_routes_and_other_phones_are_left_alone() {
        for (route in listOf(OutputRoute.Bluetooth, OutputRoute.Usb, OutputRoute.Headphones, OutputRoute.Other)) {
            assertEquals(route, SpeakerlessPlayers.route(route, speakerIsHeadphones = true))
        }
        assertEquals(OutputRoute.Speaker, SpeakerlessPlayers.route(OutputRoute.Speaker, speakerIsHeadphones = false))
    }

    // ---------- choosing a mixer entry ----------

    private fun e(rate: Int, kind: SampleKind?, bitPerfect: Boolean, channels: Int = 2) = MixerEntry(rate, channels, kind, bitPerfect)

    private val usb = listOf(
        e(44100, SampleKind.Int16, false), e(48000, SampleKind.Int16, false), e(96000, SampleKind.Int24, false),
        e(44100, SampleKind.Int16, true), e(44100, SampleKind.Int24, true), e(44100, SampleKind.Int32, true),
        e(96000, SampleKind.Int32, true), e(176400, SampleKind.Int32, true), e(192000, SampleKind.Int24, false),
    )

    @Test fun the_bit_perfect_mixer_at_the_tracks_own_rate_and_depth_comes_first() {
        assertEquals(3, MixerChoice.pick(usb, 44100, 2, 16, dop = false))
        assertEquals(4, MixerChoice.pick(usb, 44100, 2, 24, dop = false))
    }

    @Test fun a_wider_container_holds_the_samples_exactly() =
        assertEquals(6, MixerChoice.pick(usb, 96000, 2, 24, dop = false))

    @Test fun without_a_bit_perfect_entry_an_ordinary_one_at_the_rate_is_rate_matched() =
        assertEquals(8, MixerChoice.pick(usb, 192000, 2, 24, dop = false))

    @Test fun never_a_narrower_format_than_the_track() =
        assertEquals(-1, MixerChoice.pick(listOf(e(48000, SampleKind.Int16, true)), 48000, 2, 24, dop = false))

    @Test fun no_entry_at_the_tracks_rate_or_channels_means_shared() {
        assertEquals(-1, MixerChoice.pick(usb, 88200, 2, 16, dop = false))
        assertEquals(-1, MixerChoice.pick(usb, 44100, 1, 16, dop = false))
    }

    @Test fun a_float_decoder_takes_the_widest_whole_number_format() =
        assertEquals(5, MixerChoice.pick(usb, 44100, 2, 0, dop = false))

    @Test fun dop_takes_only_the_bit_perfect_mixer_in_24_or_32_bit() {
        assertEquals(7, MixerChoice.pick(usb, 176400, 2, 0, dop = true))
        assertEquals(-1, MixerChoice.pick(usb, 192000, 2, 0, dop = true))
        assertEquals(setOf(44100, 96000, 176400), MixerChoice.dopRates(usb, 2))
    }

    @Test fun entries_of_unknown_formats_are_never_chosen() =
        assertEquals(-1, MixerChoice.pick(listOf(e(48000, null, true)), 48000, 2, 16, dop = false))

    @Test fun the_summary_groups_by_behaviour() {
        assertEquals(
            "bit-perfect: 44.1, 96, 176.4 kHz · 16, 24, 32-bit · 2 ch\ndefault: 44.1, 48, 96, 192 kHz · 16, 24-bit · 2 ch",
            MixerChoice.summary(usb),
        )
        assertEquals("none", MixerChoice.summary(emptyList()))
    }

    // ---------- the readout ----------
    // 0.2.5 (the user's call): the device's name, "Exclusive" for bit-perfect, dots throughout.

    private val dac = "Shanling UA2 Plus"

    @Test fun bit_perfect_readout() =
        assertEquals("Shanling UA2 Plus · 96 kHz · 24-Bit · Exclusive", OutputReadout.describe(dac, OutputPath.BitPerfect, 96000, 24, 96000, false))

    @Test fun dop_readout() =
        assertEquals("Shanling UA2 Plus · 176.4 kHz · DSD over PCM · Exclusive", OutputReadout.describe(dac, OutputPath.BitPerfect, 176400, 24, 176400, true))

    @Test fun rate_matched_never_says_exclusive() =
        assertEquals("Shanling UA2 Plus · 96 kHz · 24-Bit · Rate Matched", OutputReadout.describe(dac, OutputPath.RateMatched, 96000, 24, 96000, false))

    @Test fun the_source_rate_way_is_exclusive_only_when_the_system_runs_at_the_tracks_rate() {
        assertEquals("4.4mm · 96 kHz · 24-Bit · Exclusive", OutputReadout.describe("4.4mm", OutputPath.SourceRate, 96000, 24, 96000, false))
        assertEquals("4.4mm · 48 kHz · Resampled", OutputReadout.describe("4.4mm", OutputPath.SourceRate, 48000, 24, 96000, false))
    }

    // The M21 (0.1.5): FiiO reports its DAC's rate; Android's system output always says 48 kHz.

    @Test fun fiios_reported_rate_is_used_once_there_is_one() {
        assertEquals(44100, OutputReadout.hardwareRate(systemRate = 48000, reportedRate = 44100))
        assertEquals(48000, OutputReadout.hardwareRate(systemRate = 48000, reportedRate = 0))
    }

    @Test fun the_m21_at_the_tracks_rate_is_exclusive_by_fiios_report() =
        assertEquals(
            "3.5mm · 44.1 kHz · 16-Bit · Exclusive",
            OutputReadout.describe("3.5mm", OutputPath.SourceRate, OutputReadout.hardwareRate(48000, 44100), 16, 44100, false),
        )

    @Test fun the_m21_says_resampled_when_fiio_reports_another_rate_or_none_yet() {
        assertEquals(
            "3.5mm · 48 kHz · Resampled",
            OutputReadout.describe("3.5mm", OutputPath.SourceRate, OutputReadout.hardwareRate(48000, 48000), 24, 96000, false),
        )
        assertEquals(
            "3.5mm · 48 kHz · Resampled",
            OutputReadout.describe("3.5mm", OutputPath.SourceRate, OutputReadout.hardwareRate(48000, 0), 16, 44100, false),
        )
    }

    // 0.2.2: no "Shared", and the words capitalised (the user's call).
    @Test fun shared_readout_names_the_output_and_not_shared() {
        assertEquals("Speaker · 48 kHz · Resampled", OutputReadout.describe("Speaker", OutputPath.Shared, 48000, 24, 44100, false))
        assertEquals("Bluetooth · 48 kHz", OutputReadout.describe("Bluetooth", OutputPath.Shared, 48000, 16, 48000, false))
    }

    @Test fun an_unknown_output_rate_says_only_the_name() =
        assertEquals("Bluetooth", OutputReadout.describe("Bluetooth", OutputPath.Shared, 0, 0, 44100, false))
}
