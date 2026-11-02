package com.mpaulrenlund.audiofool.core.analysis

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max

/**
 * The window and the calibration every spectrum uses: a Hann window, and a
 * scale that makes a full-scale sine read 0 dB.
 * Ported from the desktop's `AudioFool.Core/Analysis/SpectrumScale.cs`.
 */
object SpectrumScale {
    /** A Hann window of [size] points, the periodic form an FFT wants. */
    fun hann(size: Int): DoubleArray = DoubleArray(size) { i -> 0.5 - 0.5 * cos(2 * PI * i / size) }

    /**
     * What a Hann-windowed FFT bin's |X|² is multiplied by so a full-scale sine's
     * peak bin reads 1 (0 dB). The window halves a sine's peak, so |X| = A * N / 4;
     * scaling by (4 / N)² brings it to A².
     */
    fun zeroDb(size: Int): Double = 16.0 / (size.toDouble() * size)

    /** Power to dB, no quieter than [floorDb]. */
    fun toDb(power: Double, floorDb: Double): Double = if (power <= 0) floorDb else max(floorDb, 10 * log10(power))
}
