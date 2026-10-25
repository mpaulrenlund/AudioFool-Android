package com.mpaulrenlund.audiofool.core.playback

import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// Ported from the desktop's tests/AudioFool.Core.Tests/PlayOrderTests.cs.

class PlayOrderTest {
    /** A fixed seed, so a shuffled order is reproducible in assertions. */
    private fun shuffled(count: Int, currentIndex: Int, seed: Int = 1234) =
        PlayOrder().apply { reset(count, currentIndex, shuffle = true, rng = Random(seed)) }

    private fun sequential(count: Int) = PlayOrder().apply { reset(count, 0, shuffle = false) }

    // ---------- sequential ----------

    @Test fun sequential_order_is_the_queue_order() {
        val order = sequential(5)
        assertEquals(listOf(0, 1, 2, 3, 4), order.order)
        assertFalse(order.isShuffled)
    }

    @Test fun sequential_steps_forward_one_at_a_time() {
        val order = sequential(4)
        assertEquals(1, order.next(0, wrap = false))
        assertEquals(2, order.next(1, wrap = false))
        assertEquals(3, order.next(2, wrap = false))
    }

    @Test fun stops_at_the_end_without_wrap() = assertEquals(-1, sequential(3).next(2, wrap = false))

    @Test fun wraps_to_the_start_with_wrap() = assertEquals(0, sequential(3).next(2, wrap = true))

    @Test fun stops_at_the_start_going_back_without_wrap() = assertEquals(-1, sequential(3).previous(0, wrap = false))

    @Test fun wraps_to_the_end_going_back_with_wrap() = assertEquals(2, sequential(3).previous(0, wrap = true))

    // ---------- shuffled ----------

    @Test fun shuffling_keeps_every_track_exactly_once() {
        val order = shuffled(50, currentIndex = 0)
        assertEquals(50, order.count)
        assertEquals((0 until 50).toList(), order.order.sorted())
    }

    @Test fun shuffling_actually_reorders_something() {
        // With 50 tracks the odds of the identity permutation are 1 in 50 factorial.
        val order = shuffled(50, currentIndex = 0)
        assertNotEquals((0 until 50).toList(), order.order)
        assertTrue(order.isShuffled)
    }

    @Test fun the_playing_track_is_pinned_to_the_front() {
        // Toggling shuffle mid-album must not change what is currently playing.
        for (current in listOf(0, 1, 7, 19)) {
            val order = shuffled(20, current)
            assertEquals(current, order.order[0])
            assertEquals(0, order.positionOf(current))
        }
    }

    @Test fun next_follows_the_shuffled_order_not_the_queue_order() {
        val order = shuffled(10, currentIndex = 3)
        val walked = mutableListOf(3)
        var at = 3
        while (true) {
            at = order.next(at, wrap = false)
            if (at < 0) break
            walked.add(at)
        }
        assertEquals(order.order, walked)
    }

    @Test fun previous_retraces_the_shuffled_order() {
        val order = shuffled(10, currentIndex = 4)
        val first = order.next(4, wrap = false)
        val second = order.next(first, wrap = false)
        assertEquals(first, order.previous(second, wrap = false))
        assertEquals(4, order.previous(first, wrap = false))
    }

    @Test fun walking_forward_visits_every_track_once() {
        val order = shuffled(30, currentIndex = 11)
        val seen = mutableSetOf(11)
        var at = 11
        repeat(29) {
            at = order.next(at, wrap = false)
            assertTrue(at >= 0)
            assertTrue(seen.add(at), "a track came up twice in one pass")
        }
        assertEquals(30, seen.size)
        assertEquals(-1, order.next(at, wrap = false))
    }

    @Test fun turning_shuffle_off_restores_the_queue_order() {
        val order = PlayOrder()
        order.reset(8, 5, shuffle = true, rng = Random(99))
        order.reset(8, 5, shuffle = false)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7), order.order)
        assertFalse(order.isShuffled)
        assertEquals(6, order.next(5, wrap = false))
    }

    // ---------- edges ----------

    @Test fun an_empty_queue_has_no_next_or_previous() {
        val order = PlayOrder()
        order.reset(0, -1, shuffle = false)
        assertEquals(0, order.count)
        assertEquals(-1, order.next(0, wrap = true))
        assertEquals(-1, order.previous(0, wrap = true))
    }

    @Test fun a_single_track_wraps_to_itself() {
        val order = sequential(1)
        assertEquals(-1, order.next(0, wrap = false))
        assertEquals(0, order.next(0, wrap = true))
        assertEquals(0, order.previous(0, wrap = true))
    }

    @Test fun a_single_track_shuffles_to_itself() = assertEquals(listOf(0), shuffled(1, currentIndex = 0).order)

    @Test fun an_index_outside_the_queue_has_no_neighbours() {
        for (index in listOf(-1, 5, 99)) {
            val order = sequential(5)
            assertEquals(-1, order.positionOf(index))
            assertEquals(-1, order.next(index, wrap = true))
            assertEquals(-1, order.previous(index, wrap = true))
        }
    }

    @Test fun shuffling_with_no_current_track_still_covers_the_queue() =
        assertEquals((0 until 12).toList(), shuffled(12, currentIndex = -1).order.sorted())

    // ---------- candidates: skipping a song that won't open ----------

    @Test fun candidates_run_from_the_start_to_the_end_of_the_order() =
        assertEquals(listOf(2, 3, 4), sequential(5).candidates(2, forward = true, wrap = false))

    @Test fun candidates_going_back_run_to_the_first() =
        assertEquals(listOf(2, 1, 0), sequential(5).candidates(2, forward = false, wrap = false))

    @Test fun candidates_with_wrap_come_round_once_and_stop_before_the_start() {
        assertEquals(listOf(3, 4, 0, 1, 2), sequential(5).candidates(3, forward = true, wrap = true))
        assertEquals(listOf(1, 0, 4, 3, 2), sequential(5).candidates(1, forward = false, wrap = true))
    }

    @Test fun candidates_follow_the_shuffled_order() {
        val order = shuffled(8, currentIndex = 5)
        val expected = order.order.drop(order.positionOf(5))
        assertEquals(expected, order.candidates(5, forward = true, wrap = false))
    }

    @Test fun candidates_of_one_song_or_none() {
        assertEquals(listOf(0), sequential(1).candidates(0, forward = true, wrap = true))
        assertTrue(sequential(3).candidates(7, forward = true, wrap = true).isEmpty())
        assertTrue(PlayOrder().candidates(0, forward = true, wrap = false).isEmpty())
    }

    // ---------- restoring a saved order (new for the phone) ----------

    @Test fun a_saved_shuffled_order_is_put_back_as_it_was() {
        val saved = shuffled(10, currentIndex = 3).order
        val order = PlayOrder()
        assertTrue(order.restore(saved))
        assertEquals(saved, order.order)
        assertTrue(order.isShuffled)
        assertEquals(saved[1], order.next(3, wrap = false))
    }

    @Test fun an_order_that_is_not_a_permutation_is_refused() {
        val order = sequential(3)
        assertFalse(order.restore(listOf(0, 0, 1)))
        assertFalse(order.restore(listOf(0, 1, 3)))
        assertFalse(order.restore(emptyList()))
        assertEquals(listOf(0, 1, 2), order.order)
    }
}
