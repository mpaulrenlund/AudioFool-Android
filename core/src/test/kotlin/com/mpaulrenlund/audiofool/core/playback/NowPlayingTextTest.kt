package com.mpaulrenlund.audiofool.core.playback

import com.mpaulrenlund.audiofool.core.model.Track
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// New for the phone: Now Playing's quality lines (spec 5) and the saved queue
// it reopens to (spec 6.1, Resume).

class NowPlayingTextTest {
    // ---------- quality lines ----------

    @Test fun lossless_shows_format_rate_then_depth_and_bitrate() {
        val track = Track(filePath = "a.flac", kind = "FLAC", sampleRate = 96000, bitDepth = 24, bitrate = 1140)
        assertEquals("FLAC · 96 kHz" to "24 bit · 1,140 kbps", QualityLines.of(track))
    }

    @Test fun lossy_has_no_bit_depth_only_the_bitrate() {
        val track = Track(filePath = "a.mp3", kind = "MP3", sampleRate = 44100, bitDepth = null, bitrate = 320)
        assertEquals("MP3 · 44.1 kHz" to "320 kbps", QualityLines.of(track))
    }

    @Test fun dsd_shows_its_rate_in_megahertz_and_one_bit() {
        val dsd64 = Track(filePath = "a.dsf", kind = "DSD", sampleRate = 2_822_400, bitDepth = 1, bitrate = 5645)
        assertEquals("DSD · 2.8 MHz" to "1 bit", QualityLines.of(dsd64))
        val dsd128 = Track(filePath = "b.dff", kind = "DSD", sampleRate = 5_644_800, bitDepth = 1)
        assertEquals("DSD · 5.6 MHz" to "1 bit", QualityLines.of(dsd128))
    }

    @Test fun what_is_unknown_is_left_out() {
        assertEquals("WAV" to "16 bit", QualityLines.of(Track(filePath = "a.wav", kind = "WAV", bitDepth = 16)))
        assertEquals("Opus · 48 kHz" to "", QualityLines.of(Track(filePath = "a.opus", kind = "Opus", sampleRate = 48000)))
    }

    // ---------- the saved queue ----------

    private val a = Track(filePath = "/storage/0000-0000/Music/A/01.flac", durationMs = 200_000)
    private val b = Track(filePath = "/storage/0000-0000/Music/A/02.flac", durationMs = 180_000)
    private val c = Track(filePath = "/storage/0000-0000/Music/A/03.flac", durationMs = 240_000)

    @Test fun a_saved_queue_survives_a_round_trip() {
        val point = ResumePoint(listOf(a, b, c).map { it.filePath }, 1, 61_500, shuffle = true, repeat = RepeatMode.One, order = listOf(1, 2, 0))
        assertEquals(point, ResumePoint.decode(point.encode()))
    }

    @Test fun it_restores_the_librarys_tracks_ignoring_case() {
        val point = ResumePoint(listOf(a, b, c).map { it.filePath.uppercase() }, 2, 10_000)
        val restored = assertNotNull(point.resolve(ResumePoint.lookupIn(listOf(a, b, c))))
        assertEquals(listOf(a, b, c), restored.queue)
        assertEquals(2, restored.index)
        assertEquals(10_000, restored.positionMs)
    }

    @Test fun songs_gone_from_the_library_drop_out_and_the_index_follows() {
        val point = ResumePoint(listOf(a, b, c).map { it.filePath }, 2, 5_000, shuffle = true, order = listOf(2, 0, 1))
        val restored = assertNotNull(point.resolve(ResumePoint.lookupIn(listOf(a, c))))
        assertEquals(listOf(a, c), restored.queue)
        assertEquals(1, restored.index)
        // The saved order no longer fits the queue: shuffled again instead.
        assertTrue(restored.order.isEmpty())
        assertTrue(restored.shuffle)
    }

    @Test fun nothing_is_restored_when_the_playing_song_is_gone() =
        assertNull(ResumePoint(listOf(a, b).map { it.filePath }, 1, 0).resolve(ResumePoint.lookupIn(listOf(a))))

    @Test fun the_position_is_kept_inside_the_song() {
        val restored = assertNotNull(ResumePoint(listOf(a.filePath), 0, 999_999).resolve(ResumePoint.lookupIn(listOf(a))))
        assertEquals(200_000, restored.positionMs)
    }

    @Test fun a_damaged_save_reads_as_nothing() {
        assertNull(ResumePoint.decode("""{"paths":[],"index":0,"positionMs":0}""".toByteArray()))
    }
}
