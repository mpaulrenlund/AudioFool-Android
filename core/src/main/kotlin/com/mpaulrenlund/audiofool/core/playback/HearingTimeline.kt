package com.mpaulrenlund.audiofool.core.playback

import com.mpaulrenlund.audiofool.core.model.Track
import java.util.concurrent.atomic.AtomicInteger

/**
 * What the output has been handed, block by block, so the app can tell what is
 * coming out of the speakers rather than what the decoder has reached.
 * Ported from the desktop's `AudioFool.Core/Playback/HearingTimeline.cs`.
 *
 * The output holds a few hundred milliseconds it has already been given, so the
 * decode position runs that far ahead of what you hear. Each write is appended
 * here with the track it came from and where in that track it started; the
 * frame being heard is the output's playback head, and [lookup] turns that
 * frame back into a track and a position.
 *
 * One writer, the output's thread, and any number of readers, without a lock:
 * the output's thread must never wait for the UI. Entries sit in a ring; the
 * count is published after an entry is written, and a reader checks afterwards
 * that nothing it read was overwritten, or a [reset] happened.
 */
class HearingTimeline {
    /** Where the last seek's audio begins, and what it begins with. */
    private class SeekMark(val frame: Long, val track: Track?, val seconds: Double)

    /** A track and a position in it, in seconds. */
    data class Point(val track: Track?, val seconds: Double)

    private val start = LongArray(CAPACITY)
    private val frames = IntArray(CAPACITY)
    private val seconds = DoubleArray(CAPACITY)
    private val tracks = arrayOfNulls<Track>(CAPACITY)

    @Volatile private var count = 0L
    @Volatile private var total = 0L
    private val generationCounter = AtomicInteger()
    @Volatile private var sampleRateHz = 44100
    @Volatile private var seek: SeekMark? = null

    /** Changes on every [reset], so a reader can tell its reading spans one. */
    val generation: Int get() = generationCounter.get()

    /** The rate the frames are counted at: the output's. */
    val sampleRate: Int get() = sampleRateHz

    /** Every frame handed to the output since the last [reset]. */
    val totalFrames: Long get() = total

    /** Starts again for a new output, at its rate. Called while nothing is being written. */
    fun reset(sampleRate: Int) {
        generationCounter.incrementAndGet()
        sampleRateHz = maxOf(1, sampleRate)
        count = 0
        total = 0
        seek = null
        tracks.fill(null)
    }

    /**
     * Records [frameCount] handed to the output, from [track] starting
     * [startSeconds] in (null: silence, nothing playing). Output's thread only.
     */
    fun append(track: Track?, startSeconds: Double, frameCount: Int) {
        if (frameCount <= 0) return

        val n = count
        val slot = (n % CAPACITY).toInt()
        start[slot] = total
        frames[slot] = frameCount
        seconds[slot] = startSeconds
        tracks[slot] = track

        count = n + 1
        total += frameCount
    }

    /**
     * Notes a seek the output has just made: everything handed over from now on
     * starts at [targetSeconds] in [track]. Until the speakers reach it, [lookup]
     * gives the target, so the seekbar doesn't jump back to the old spot for the
     * length of the buffer. A later seek replaces it. Output's thread only.
     */
    fun markSeek(track: Track?, targetSeconds: Double) {
        seek = SeekMark(total, track, targetSeconds)
    }

    /**
     * The track and position at [frame], counted as [totalFrames] is. A frame
     * past the end gives the end of the last block; one before a seek not yet
     * heard gives the seek's target. Null when there is nothing to go on: no
     * blocks, a frame older than the history, or a reading torn by the writer.
     */
    fun lookup(frame: Long): Point? {
        repeat(3) {
            val generation = generationCounter.get()

            val mark = seek
            if (mark != null && frame < mark.frame) {
                if (generationCounter.get() == generation) return Point(mark.track, mark.seconds)
                return@repeat
            }

            val count = this.count
            val found = find(frame, count)
            if (found != null && generationCounter.get() == generation && this.count - count < CAPACITY / 2) return found
        }
        return null
    }

    private fun find(frame: Long, count: Long): Point? {
        if (count <= 0) return null

        val rate = sampleRateHz.toDouble()
        val oldest = maxOf(0, count - CAPACITY / 2)

        var n = count - 1
        while (n >= oldest) {
            val slot = (n % CAPACITY).toInt()
            val blockStart = start[slot]
            if (blockStart <= frame) {
                return Point(tracks[slot], seconds[slot] + minOf(frame - blockStart, frames[slot].toLong()) / rate)
            }
            n--
        }
        return null
    }

    private companion object {
        /**
         * Enough history for the output's whole buffer many times over: 10-20 ms
         * writes give several seconds.
         */
        const val CAPACITY = 512
    }
}
