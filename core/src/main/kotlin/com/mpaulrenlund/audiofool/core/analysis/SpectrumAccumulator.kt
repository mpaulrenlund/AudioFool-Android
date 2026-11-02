package com.mpaulrenlund.audiofool.core.analysis

import kotlin.math.abs
import kotlin.math.max

/**
 * What a pass over a track's decoded audio measured: the picture the Analysis
 * screen draws, and the full-resolution spectra the opinion is formed from.
 * Ported from the desktop's `AudioFool.Core/Analysis/SpectrumAccumulator.cs` (`SpectrumAnalysis`).
 */
class SpectrumAnalysis(
    val sampleRate: Int,
    val channels: Int,
    val durationSeconds: Double,
    val fftSize: Int,
    /**
     * Mean power of each FFT bin over the whole track, in dB relative to a
     * full-scale sine (0 dB). Index 0 is DC; the last is the Nyquist frequency.
     */
    val average: DoubleArray,
    /** The loudest any single transform got in each bin, same scale. */
    val peak: DoubleArray,
    /** The spectrogram's time slices, left to right. */
    val columns: Int,
    /** The spectrogram's frequency bands, bottom (0 Hz) to top (Nyquist). */
    val rows: Int,
    /** The spectrogram in dB, column by column: `picture[column * rows + row]`, row 0 at the lowest frequency. */
    val picture: FloatArray,
    /** The largest sample, in dB relative to full scale. */
    val peakSampleDb: Double,
    /**
     * How many of a 24-bit file's bits the samples actually use: 16 for a CD
     * master padded with zeros. Null when it wasn't checked (a lossy or 16-bit
     * source) or the decoded samples don't sit on a 24-bit grid.
     */
    val usedBits: Int? = null,
    /**
     * Of the samples that aren't digital silence, the share using more than 16
     * bits: about 99% for real 24-bit audio. A 16-bit master can have 24-bit
     * fades or edits added later, which makes [usedBits] 24 while the music
     * itself is 16-bit. Null when the bits weren't checked.
     */
    val extraBitsShare: Double? = null,
) {
    /** Hz per FFT bin. */
    val binWidth: Double get() = sampleRate.toDouble() / fftSize

    /** The bin a frequency falls in, clamped to the spectrum. Halves round to even, as .NET's Math.Round. */
    fun binOf(hz: Double): Int = Math.rint(hz / binWidth).toInt().coerceIn(0, average.size - 1)
}

/**
 * Turns a stream of decoded samples into a [SpectrumAnalysis]. Pure arithmetic
 * with no BASS in it, so the tests can feed it synthetic audio.
 * Ported from the desktop's `AudioFool.Core/Analysis/SpectrumAccumulator.cs`.
 *
 * Each transform is a Hann-windowed FFT per channel, the channels' powers
 * averaged (not the samples, so out-of-phase content can't cancel). Transforms
 * step through the track by `hop` frames, which is the FFT size for a long
 * track and less for a short one, so every picture column gets at least one.
 */
class SpectrumAccumulator(
    private val sampleRate: Int,
    private val channels: Int,
    expectedFrames: Long,
    private val columns: Int = 800,
    private val rows: Int = 512,
    private val checkBits: Boolean = false,
) {
    private val expectedFrames = max(1L, expectedFrames)

    val fftSize: Int = fftSizeFor(sampleRate.also { require(it > 0) { "sampleRate" } })
    private val size = fftSize
    private val fft = Fft(size)
    private val hop = (this.expectedFrames / columns).coerceIn(64L, size.toLong())

    private val window = SpectrumScale.hann(size)
    private val scale = SpectrumScale.zeroDb(size)

    // The last `size` frames of each channel, as a ring.
    private val ring = Array(channels.also { require(it > 0) { "channels" } }) { FloatArray(size) }
    private var ringPos = 0
    private var frames = 0L

    // Frames in the ring since the last restart: a transform waits for a full window.
    private var filled = 0L
    private var sinceLast = 0L

    private val re = DoubleArray(size)
    private val im = DoubleArray(size)

    private val bins = size / 2 + 1
    private val power = DoubleArray(bins)
    private val sum = DoubleArray(bins)
    private val max = DoubleArray(bins)
    private var transforms = 0L

    private val columnSum = DoubleArray(columns * rows)
    private val columnCount = IntArray(columns)

    // Row r covers bins [rowStart[r], rowStart[r + 1]).
    private val rowStart = IntArray(rows + 1) { r -> (r.toLong() * bins / rows).toInt() }

    private var peakSample = 0.0
    private var bitsUsed = 0L
    private var offGrid = false
    private var soundingSamples = 0L
    private var extraBitSamples = 0L

    /** Feeds interleaved samples; a partial frame at the end is ignored. */
    fun add(interleaved: FloatArray, offset: Int = 0, length: Int = interleaved.size - offset) {
        val count = length / channels

        for (f in 0 until count) {
            val base = offset + f * channels
            for (c in 0 until channels) {
                val s = interleaved[base + c]
                ring[c][ringPos] = s

                val a = abs(s).toDouble()
                if (a > peakSample) peakSample = a

                if (checkBits && !offGrid) checkBits(s)
            }

            ringPos = (ringPos + 1) % size
            frames++
            filled++
            sinceLast++

            if (filled >= size && sinceLast >= hop) {
                sinceLast = 0
                transform()
            }
        }
    }

    /**
     * Marks a jump in the audio, as between the slices of a sampled analysis.
     * No transform spans it: a window across the join would hear it as a click,
     * whose noise reaches every frequency and would fill in a lossy cutoff.
     */
    fun restart() {
        filled = 0
        sinceLast = 0
    }

    private fun checkBits(s: Float) {
        val v = s * 8388608.0
        val r = Math.rint(v)
        if (abs(v - r) > 1e-6) {
            // A decoder that isn't handing back 24-bit integers; nothing to say.
            offGrid = true
            return
        }

        val value = abs(r).toLong()
        bitsUsed = bitsUsed or value
        if (value != 0L) {
            soundingSamples++
            if (value and 0xFF != 0L) extraBitSamples++
        }
    }

    private fun transform() {
        power.fill(0.0)

        for (c in 0 until channels) {
            val ring = ring[c]
            for (i in 0 until size) {
                // Oldest frame first: the ring's write position is the oldest.
                re[i] = ring[(ringPos + i) % size] * window[i]
                im[i] = 0.0
            }

            fft.transform(re, im)

            for (k in 0 until bins) power[k] += (re[k] * re[k] + im[k] * im[k]) * scale
        }

        for (k in 0 until bins) {
            val p = power[k] / channels
            power[k] = p
            sum[k] += p
            if (p > max[k]) max[k] = p
        }

        transforms++

        // The column this transform's centre falls in. A track shorter than half a
        // transform has its one, zero-padded, centred before the start: column 0.
        val centre = frames - size / 2
        val length = max(expectedFrames, frames)
        val column = (centre * columns / length).coerceIn(0L, columns - 1L).toInt()
        val offset = column * rows

        for (r in 0 until rows) {
            var band = 0.0
            for (k in rowStart[r] until rowStart[r + 1]) band += power[k]
            columnSum[offset + r] += band / max(1, rowStart[r + 1] - rowStart[r])
        }

        columnCount[column]++
    }

    fun finish(): SpectrumAnalysis {
        // A track shorter than one transform still gets one, zero-padded.
        if (transforms == 0L && frames > 0) {
            sinceLast = 0
            transform()
        }

        val average = DoubleArray(bins) { k -> toDb(if (transforms == 0L) 0.0 else sum[k] / transforms) }
        val peak = DoubleArray(bins) { k -> toDb(max[k]) }

        val picture = FloatArray(columns * rows) { FLOOR_DB.toFloat() }
        for (c in 0 until columns) {
            if (columnCount[c] == 0) continue
            val offset = c * rows
            for (r in 0 until rows) picture[offset + r] = toDb(columnSum[offset + r] / columnCount[c]).toFloat()
        }

        // A column with no transform (only ever at the ends, or a track shorter
        // than the picture is wide) repeats its nearest neighbour.
        val first = columnCount.indexOfFirst { it > 0 }
        if (first >= 0) {
            var source = first
            for (c in 0 until columns) {
                if (columnCount[c] > 0) source = c
                else System.arraycopy(picture, source * rows, picture, c * rows, rows)
            }
        }

        val usedBits = if (checkBits && !offGrid && bitsUsed != 0L) 24 - java.lang.Long.numberOfTrailingZeros(bitsUsed) else null

        return SpectrumAnalysis(
            sampleRate = sampleRate,
            channels = channels,
            durationSeconds = frames.toDouble() / sampleRate,
            fftSize = size,
            average = average,
            peak = peak,
            columns = columns,
            rows = rows,
            picture = picture,
            peakSampleDb = toDb(peakSample * peakSample),
            usedBits = usedBits,
            extraBitsShare = if (usedBits == null || soundingSamples == 0L) null else extraBitSamples.toDouble() / soundingSamples,
        )
    }

    private fun toDb(power: Double): Double = SpectrumScale.toDb(power, FLOOR_DB)

    companion object {
        /** The quietest level kept, in dB. Below it is drawn as silence. */
        const val FLOOR_DB = -160.0

        /**
         * About 11 Hz per bin at any rate: 4,096 at 44.1 and 48 kHz, doubling with
         * each doubling of the rate.
         */
        fun fftSizeFor(sampleRate: Int): Int {
            var size = 4096
            while (sampleRate / size > 12) size = size shl 1
            return size
        }
    }
}
