package com.mpaulrenlund.audiofool.core.analysis

import kotlin.math.sqrt

/**
 * Turns interleaved samples into the seekbar waveform's levels (spec 5): one RMS
 * level per column, scaled so the loudest column is 1. Samples are summed into
 * small blocks as they arrive, and the blocks are shared out among the columns
 * at the end, so a length that was only an estimate (or unknown) still fills
 * every column evenly.
 * Ported from the desktop's `AudioFool.Core/Analysis/Waveform.cs` (`WaveformLevels`);
 * the decoding half (`Waveform.Read`) needs BASS and lives in the app.
 *
 * RMS rather than peak, because the point is quiet against loud: a modern
 * master peaks near full scale nearly everywhere, so its peaks draw a solid
 * brick. The scale is linear, so a quiet passage really looks quiet.
 *
 * @param totalFrames The expected length, or 0 if unknown.
 */
class WaveformLevels(private val channels: Int, totalFrames: Long, private val columns: Int) {
    private val blockFrames: Long
    private val blockSums = ArrayList<Double>()
    private val blockCounts = ArrayList<Long>()

    private var sum = 0.0
    private var count = 0L

    init {
        require(channels >= 1) { "channels must be at least 1" }
        require(columns >= 1) { "columns must be at least 1" }

        // Four blocks a column, so an estimate a little short still has slack.
        blockFrames = if (totalFrames > 0) maxOf(1, totalFrames / (columns * 4L)) else UNKNOWN_LENGTH_BLOCK_FRAMES
    }

    fun add(interleaved: FloatArray, offset: Int = 0, length: Int = interleaved.size - offset) {
        val blockSamples = blockFrames * channels
        for (i in offset until offset + length) {
            val sample = interleaved[i].toDouble()
            sum += sample * sample
            if (++count == blockSamples) closeBlock()
        }
    }

    /**
     * One level per column, 0 to 1. Fewer than the columns asked for when the
     * track was shorter than that many blocks; empty when nothing was read.
     */
    fun finish(): FloatArray {
        if (count > 0) closeBlock()

        val blocks = blockSums.size
        val columns = minOf(this.columns, blocks)
        val levels = FloatArray(columns)

        for (c in 0 until columns) {
            val first = (c.toLong() * blocks / columns).toInt()
            val end = ((c + 1).toLong() * blocks / columns).toInt()

            var sum = 0.0
            var count = 0L
            for (b in first until end) {
                sum += blockSums[b]
                count += blockCounts[b]
            }
            levels[c] = if (count == 0L) 0f else sqrt(sum / count).toFloat()
        }

        val loudest = levels.maxOrNull() ?: 0f
        if (loudest > 0) {
            for (c in levels.indices) levels[c] /= loudest
        }
        return levels
    }

    private fun closeBlock() {
        blockSums.add(sum)
        blockCounts.add(count)
        sum = 0.0
        count = 0
    }

    private companion object {
        const val UNKNOWN_LENGTH_BLOCK_FRAMES = 2048L
    }
}
