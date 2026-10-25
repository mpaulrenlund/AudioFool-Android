package com.mpaulrenlund.audiofool.core.playback

import kotlin.random.Random

/**
 * The order tracks are played in, kept separate from the queue itself.
 * Ported from the desktop's `AudioFool.Core/Playback/PlayOrder.cs`.
 *
 * The queue always stays in album order. This holds a permutation of its
 * indices - `order[p]` is the queue index of the p-th track to play - so
 * turning shuffle off restores the album running order without disturbing what
 * is playing, and Previous retraces the order actually heard rather than the
 * order the tracks happen to sit in.
 *
 * Pure logic, free of any BASS dependency, so it can be tested without a sound device.
 */
class PlayOrder {
    private var _order = IntArray(0)

    /** Inverse of [_order]: queue index to play position. */
    private var positionOf = IntArray(0)

    val count: Int get() = _order.size

    var isShuffled: Boolean = false
        private set

    /** The permutation itself. Exposed for tests and diagnostics. */
    val order: List<Int> get() = _order.toList()

    /**
     * Rebuilds the order for a queue of [count] tracks.
     *
     * When shuffling, the track at [currentIndex] is pulled to the front.
     * Toggling shuffle on must not change what is playing right now - only what
     * comes after it.
     */
    fun reset(count: Int, currentIndex: Int, shuffle: Boolean, rng: Random = Random.Default) {
        if (count <= 0) {
            _order = IntArray(0)
            positionOf = IntArray(0)
            isShuffled = false
            return
        }

        val order = IntArray(count) { it }

        if (shuffle) {
            // Fisher-Yates.
            for (i in count - 1 downTo 1) {
                val j = rng.nextInt(i + 1)
                order[i] = order[j].also { order[j] = order[i] }
            }

            if (currentIndex in 0 until count) {
                val landedAt = order.indexOf(currentIndex)
                order[0] = order[landedAt].also { order[landedAt] = order[0] }
            }
        }

        val positions = IntArray(count)
        for (p in 0 until count) positions[order[p]] = p

        _order = order
        positionOf = positions
        isShuffled = shuffle
    }

    /**
     * Puts back a shuffled order saved with the queue ([ResumePoint]), so Next
     * carries on after a restart as it would have. Not in the desktop, which
     * doesn't restore a queue. False, changing nothing, unless [order] is a
     * permutation of the queue's indices.
     */
    fun restore(order: List<Int>): Boolean {
        val count = order.size
        if (count == 0 || order.toSet().size != count || order.any { it !in 0 until count }) return false
        _order = order.toIntArray()
        positionOf = IntArray(count).also { p -> for (i in 0 until count) p[_order[i]] = i }
        isShuffled = true
        return true
    }

    /** Where a queue index sits in the play order, or -1 if out of range. */
    fun positionOf(index: Int): Int = if (index in positionOf.indices) positionOf[index] else -1

    /** The queue index that follows [fromIndex], or -1 when the order runs out and [wrap] is false. */
    fun next(fromIndex: Int, wrap: Boolean): Int = step(fromIndex, 1, wrap)

    /** The queue index before [fromIndex], or -1 at the start of the order when [wrap] is false. */
    fun previous(fromIndex: Int, wrap: Boolean): Int = step(fromIndex, -1, wrap)

    /**
     * The queue indices a play starting at [startIndex] tries in turn, for
     * skipping a song that won't open: the start first, then each after it in the
     * order (or before it, going back), each at most once, until the order runs
     * out or, with [wrap], comes round to the start again. Empty for an index
     * outside the order.
     */
    fun candidates(startIndex: Int, forward: Boolean, wrap: Boolean): List<Int> {
        if (positionOf(startIndex) < 0) return emptyList()

        val candidates = ArrayList<Int>()
        var index = startIndex
        while (index >= 0 && candidates.size < _order.size) {
            if (candidates.isNotEmpty() && index == startIndex) break
            candidates.add(index)
            index = step(index, if (forward) 1 else -1, wrap)
        }
        return candidates
    }

    private fun step(fromIndex: Int, delta: Int, wrap: Boolean): Int {
        val count = _order.size
        if (count == 0) return -1

        val position = positionOf(fromIndex)
        if (position < 0) return -1

        var target = position + delta
        if (target < 0 || target >= count) {
            if (!wrap) return -1
            target = (target + count) % count
        }
        return _order[target]
    }
}
