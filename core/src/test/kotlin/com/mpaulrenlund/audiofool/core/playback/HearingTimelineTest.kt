package com.mpaulrenlund.audiofool.core.playback

import com.mpaulrenlund.audiofool.core.model.Track
import org.junit.Test
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

// Ported from the desktop's tests/AudioFool.Core.Tests/HearingTimelineTests.cs.

class HearingTimelineTest {
    private val first = Track(filePath = "first.flac")
    private val second = Track(filePath = "second.flac")

    /** A timeline at 1000 Hz, so a frame is a millisecond. */
    private fun atOneKilohertz() = HearingTimeline().apply { reset(1000) }

    @Test fun nothing_to_go_on_before_the_first_block() {
        val timeline = atOneKilohertz()
        assertNull(timeline.lookup(0))
        assertEquals(0, timeline.totalFrames)
    }

    @Test fun a_frame_inside_a_block_is_that_blocks_start_plus_the_way_in() {
        val timeline = atOneKilohertz()
        timeline.append(first, 10.0, 20)
        timeline.append(first, 10.020, 20)

        val point = assertNotNull(timeline.lookup(25))
        assertSame(first, point.track)
        assertEquals(10.025, point.seconds, 1e-6)
        assertEquals(40, timeline.totalFrames)
    }

    @Test fun the_heard_frame_trails_what_was_handed_over_by_what_the_device_holds() {
        // 300 ms handed over, 200 ms still in the device: 100 ms has been heard.
        val timeline = atOneKilohertz()
        for (i in 0 until 15) timeline.append(first, 5.0 + i * 0.020, 20)

        assertEquals(5.100, assertNotNull(timeline.lookup(timeline.totalFrames - 200)).seconds, 1e-6)
    }

    @Test fun a_frame_past_the_end_gives_the_end_of_the_last_block() {
        val timeline = atOneKilohertz()
        timeline.append(first, 1.0, 20)

        val point = assertNotNull(timeline.lookup(500))
        assertSame(first, point.track)
        assertEquals(1.020, point.seconds, 1e-6)
    }

    @Test fun a_gapless_join_switches_track_at_the_join_not_at_the_block() {
        // The engine splits a write at the join: 12 frames of the old track's
        // end, then 8 of the new one from its start.
        val timeline = atOneKilohertz()
        timeline.append(first, 179.988, 12)
        timeline.append(second, 0.0, 8)

        val before = assertNotNull(timeline.lookup(11))
        assertSame(first, before.track)
        assertEquals(179.999, before.seconds, 1e-6)

        val after = assertNotNull(timeline.lookup(12))
        assertSame(second, after.track)
        assertEquals(0.0, after.seconds, 1e-6)
    }

    @Test fun a_seek_shows_its_target_until_it_is_heard() {
        // At 30 s the bar was moved to 90 s. The device still holds the old spot.
        val timeline = atOneKilohertz()
        timeline.append(first, 30.000, 20)
        timeline.append(first, 30.020, 20)
        timeline.markSeek(first, 90.000)

        // Before the first block from the target has even been handed over.
        val held = assertNotNull(timeline.lookup(10))
        assertSame(first, held.track)
        assertEquals(90.000, held.seconds, 1e-6)

        timeline.append(first, 90.000, 20)
        timeline.append(first, 90.020, 20)

        assertEquals(90.000, assertNotNull(timeline.lookup(39)).seconds, 1e-6)
        assertEquals(90.005, assertNotNull(timeline.lookup(45)).seconds, 1e-6)
    }

    @Test fun two_seeks_in_a_row_hold_at_the_newer_target() {
        val timeline = atOneKilohertz()
        timeline.append(first, 30.000, 20)
        timeline.markSeek(first, 90.000)
        timeline.append(first, 90.000, 20)
        timeline.markSeek(first, 120.000)
        timeline.append(first, 120.000, 20)

        assertEquals(120.000, assertNotNull(timeline.lookup(5)).seconds, 1e-6)
        // Inside the first seek's block, the second one is still to come.
        assertEquals(120.000, assertNotNull(timeline.lookup(25)).seconds, 1e-6)
        assertEquals(120.010, assertNotNull(timeline.lookup(50)).seconds, 1e-6)
    }

    @Test fun a_reset_forgets_a_seek() {
        val timeline = atOneKilohertz()
        timeline.append(first, 30.000, 20)
        timeline.markSeek(first, 90.000)

        timeline.reset(1000)
        timeline.append(second, 0.0, 20)

        val point = assertNotNull(timeline.lookup(10))
        assertSame(second, point.track)
        assertEquals(0.010, point.seconds, 1e-6)
    }

    @Test fun silence_after_the_queue_ends_has_no_track() {
        val timeline = atOneKilohertz()
        timeline.append(first, 200.0, 20)
        timeline.append(null, 0.0, 20)

        assertNull(assertNotNull(timeline.lookup(30)).track)
    }

    @Test fun old_frames_drop_out_of_the_history() {
        val timeline = atOneKilohertz()
        for (i in 0 until 2000) timeline.append(first, i * 0.010, 10)

        assertNull(timeline.lookup(0))
        assertEquals(19.800, assertNotNull(timeline.lookup(timeline.totalFrames - 200)).seconds, 1e-6)
    }

    @Test fun a_reset_starts_again_at_the_new_rate() {
        val timeline = atOneKilohertz()
        timeline.append(first, 100.0, 1000)
        val generation = timeline.generation

        timeline.reset(48000)
        assertNotEquals(generation, timeline.generation)
        assertEquals(0, timeline.totalFrames)
        assertNull(timeline.lookup(0))

        timeline.append(second, 0.0, 4800)
        val point = assertNotNull(timeline.lookup(2400))
        assertSame(second, point.track)
        assertEquals(0.050, point.seconds, 1e-6)
    }

    @Test fun empty_blocks_are_ignored() {
        val timeline = atOneKilohertz()
        timeline.append(first, 1.0, 0)

        assertEquals(0, timeline.totalFrames)
        assertNull(timeline.lookup(0))
    }

    @Test fun a_reader_on_another_thread_never_sees_a_torn_or_backward_reading() {
        // The writer appends 10 ms blocks of one steadily advancing track as fast
        // as it can; the reader asks for a frame 200 ms behind the end each time.
        // Every answer must be a real position, never earlier than the last one.
        val timeline = atOneKilohertz()
        timeline.append(first, 0.0, 10)

        val stop = java.util.concurrent.atomic.AtomicBoolean(false)
        val writer = thread {
            var i = 1
            while (i < 300_000 && !stop.get()) {
                timeline.append(first, i * 0.010, 10)
                i++
            }
        }

        var last = -1.0
        var readings = 0
        var done: Boolean
        do {
            done = !writer.isAlive
            val frame = maxOf(0, timeline.totalFrames - 200)
            val point = timeline.lookup(frame) ?: continue
            assertSame(first, point.track)
            assertTrue(point.seconds >= last)
            assertEquals(frame / 1000.0, point.seconds, 1e-6)
            last = point.seconds
            readings++
        } while (!done)

        stop.set(true)
        writer.join()
        assertTrue(readings > 0)
    }
}
