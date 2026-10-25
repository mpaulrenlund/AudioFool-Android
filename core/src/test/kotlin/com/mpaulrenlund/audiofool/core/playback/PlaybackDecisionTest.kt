package com.mpaulrenlund.audiofool.core.playback

import com.mpaulrenlund.audiofool.core.model.Track
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Ported from the desktop's tests/AudioFool.Core.Tests/PlaybackDecisionTests.cs
// (the end of a track, where a reopen carries on, and when DSD goes out as DoP) and
// PlaybackBarTextTests.cs (the output readout).

class PlaybackDecisionTest {
    // ---------- the end of a track ----------

    private fun end(
        repeatOne: Boolean = false, hasOutput: Boolean = true, nextOpened: Boolean = true,
        shared: Boolean = true, sameRate: Boolean = true, dop: Boolean = false,
        midSave: Boolean = false, hasNext: Boolean = true,
    ) = TrackEnd.decide(repeatOne, hasOutput, nextOpened, shared, sameRate, dop, midSave, hasNext)

    @Test fun repeat_one_plays_the_same_track_again() {
        assertEquals(TrackEndAction.RepeatOne, end(repeatOne = true))
        assertEquals(TrackEndAction.RepeatOne, end(repeatOne = true, nextOpened = false, hasNext = false))
    }

    @Test fun shared_output_joins_any_rate() = assertEquals(TrackEndAction.Splice, end(shared = true, sameRate = false))

    @Test fun exclusive_output_joins_the_same_rate_and_reopens_for_another() {
        assertEquals(TrackEndAction.Splice, end(shared = false, sameRate = true))
        assertEquals(TrackEndAction.Reopen, end(shared = false, sameRate = false))
    }

    @Test fun dop_joins_only_an_exclusive_output() {
        assertEquals(TrackEndAction.Splice, end(shared = false, sameRate = true, dop = true))
        assertEquals(TrackEndAction.Reopen, end(shared = true, sameRate = true, dop = true))
    }

    @Test fun a_next_track_that_wasnt_opened_is_opened_now_not_the_end_of_the_queue() =
        assertEquals(TrackEndAction.OpenNext, end(nextOpened = false))

    @Test fun a_next_track_out_for_a_save_waits_for_it() =
        assertEquals(TrackEndAction.WaitForSave, end(nextOpened = false, midSave = true))

    @Test fun the_end_of_the_order_finishes() =
        assertEquals(TrackEndAction.Finish, end(nextOpened = false, hasNext = false))

    // ---------- where a reopen carries on ----------

    private val song = Track(filePath = "/storage/emulated/0/Music/a.flac", title = "a")
    private val before = Track(filePath = "/storage/emulated/0/Music/0.flac", title = "0")

    @Test fun a_reopen_carries_on_from_what_was_heard() =
        assertEquals(41_800, ReopenPoint.choose(song, HeardPoint(song, 41_800), 42_000))

    @Test fun a_reopen_before_a_join_is_heard_starts_the_track() =
        assertEquals(0, ReopenPoint.choose(song, HeardPoint(before, 200_000), 150))

    @Test fun a_reopen_with_nothing_heard_to_go_by_uses_the_decoder() =
        assertEquals(3_000, ReopenPoint.choose(song, HeardPoint(null, 0), 3_000))

    @Test fun a_reopen_never_goes_past_the_decoder_or_below_zero() {
        assertEquals(3_000, ReopenPoint.choose(song, HeardPoint(song, 5_000), 3_000))
        assertEquals(0, ReopenPoint.choose(song, HeardPoint(song, -1_000), 3_000))
    }

    // ---------- the output readout ----------

    @Test fun shared_readout_names_rate_and_depth() =
        assertEquals("Shared · 96 kHz / 32-bit", OutputReadout.describe(OutputMode.Shared, 96000, 32, 96000, false))

    @Test fun shared_readout_says_resampled_when_the_rates_differ() =
        assertEquals("Shared · 96 kHz / 32-bit (resampled)", OutputReadout.describe(OutputMode.Shared, 96000, 32, 44100, false))

    @Test fun shared_readout_without_a_known_depth_or_source() =
        assertEquals("Shared · 48 kHz", OutputReadout.describe(OutputMode.Shared, 48000, 0, 0, false))

    @Test fun exclusive_readout_has_no_depth_and_does_not_repeat_bit_perfect() =
        assertEquals("Exclusive · 44.1 kHz", OutputReadout.describe(OutputMode.Exclusive, 44100, 24, 44100, false))

    @Test fun exclusive_dop_readout_says_so() =
        assertEquals("Exclusive · 176.4 kHz · DSD over PCM", OutputReadout.describe(OutputMode.Exclusive, 176400, 24, 176400, true))

    // ---------- DoP ----------

    private val deviceRates = listOf(44100, 48000, 88200, 96000, 176400, 192000, 352800, 384000)

    @Test fun dop_needs_bit_perfect_and_a_rate_the_device_clocks() {
        assertTrue(DopRule.mayOpen(OutputMode.Exclusive, 176400, deviceRates, emptyList()))
        assertFalse(DopRule.mayOpen(OutputMode.Shared, 176400, deviceRates, emptyList()))
        assertFalse(DopRule.mayOpen(OutputMode.Exclusive, 705600, deviceRates, emptyList()))
        assertFalse(DopRule.mayOpen(OutputMode.Exclusive, 0, deviceRates, emptyList()))
    }

    @Test fun dop_is_never_opened_at_a_rate_exclusive_mode_refused() {
        assertFalse(DopRule.mayOpen(OutputMode.Exclusive, 176400, deviceRates, listOf(176400)))
        assertTrue(DopRule.mayOpen(OutputMode.Exclusive, 352800, deviceRates, listOf(176400)))
    }

    @Test fun dop_plays_only_on_an_exclusive_output_at_its_rate() {
        assertTrue(DopRule.mayPlayOn(OutputMode.Exclusive, 176400, 176400))
        assertFalse(DopRule.mayPlayOn(OutputMode.Shared, 176400, 176400))
        assertFalse(DopRule.mayPlayOn(OutputMode.Exclusive, 88200, 176400))
    }
}
