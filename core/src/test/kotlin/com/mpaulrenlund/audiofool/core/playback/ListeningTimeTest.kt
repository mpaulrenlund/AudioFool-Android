package com.mpaulrenlund.audiofool.core.playback

import com.mpaulrenlund.audiofool.core.model.Track
import org.junit.Test
import kotlin.test.assertEquals

class ListeningTimeTest {
    private val song = Track(filePath = "/sdcard/Music/a.flac")
    private val next = Track(filePath = "/sdcard/Music/b.flac")

    private fun play(counter: ListeningCounter, track: Track, fromMs: Long, toMs: Long): Long {
        var heard = 0L
        var p = fromMs
        while (p <= toMs) {
            heard += counter.heard(track, p)
            p += 250
        }
        return heard
    }

    @Test fun steady_playing_counts_its_length() {
        val counter = ListeningCounter()
        assertEquals(60_000, play(counter, song, 0, 60_000))
    }

    @Test fun a_seek_forward_and_a_pause_add_nothing() {
        val counter = ListeningCounter()
        play(counter, song, 0, 10_000)

        assertEquals(0, counter.heard(song, 120_000))
        repeat(100) { assertEquals(0, counter.heard(song, 120_000)) }
    }

    @Test fun the_first_report_of_a_new_track_adds_nothing_then_it_counts() {
        val counter = ListeningCounter()
        play(counter, song, 170_000, 180_000)

        // A gapless join: the next song's first tick is already 200 ms in.
        assertEquals(0, counter.heard(next, 200))
        assertEquals(250, counter.heard(next, 450))
    }

    @Test fun after_a_reset_the_same_track_starts_afresh() {
        val counter = ListeningCounter()
        play(counter, song, 0, 1_000)
        counter.reset()

        assertEquals(0, counter.heard(song, 1_250))
    }

    @Test fun the_saved_total_reads_back() {
        val saved = ListeningTime(totalMs = 123_456_789, sinceUtc = 1_790_596_800_000)
        assertEquals(saved, ListeningTime.decode(saved.encode()))
    }
}
