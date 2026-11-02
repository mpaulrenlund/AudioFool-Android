package com.mpaulrenlund.audiofool.core.analysis

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * An in-place radix-2 complex FFT of one fixed size. The twiddle factors and
 * the bit-reversal order are worked out once, since a track runs thousands of
 * transforms of the same size.
 * Ported from the desktop's `AudioFool.Core/Analysis/Fft.cs`.
 */
class Fft(val size: Int) {
    private val reversed: IntArray
    private val cos: DoubleArray
    private val sin: DoubleArray

    init {
        require(size >= 2 && size and (size - 1) == 0) { "The size must be a power of two." }

        val bits = Integer.numberOfTrailingZeros(size)
        reversed = IntArray(size) { i ->
            var r = 0
            for (b in 0 until bits) r = r or (((i shr b) and 1) shl (bits - 1 - b))
            r
        }

        cos = DoubleArray(size / 2) { i -> cos(-2 * PI * i / size) }
        sin = DoubleArray(size / 2) { i -> sin(-2 * PI * i / size) }
    }

    /** Transforms [re] and [im] in place. */
    fun transform(re: DoubleArray, im: DoubleArray) {
        val n = size

        for (i in 0 until n) {
            val j = reversed[i]
            if (j > i) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }

        var length = 2
        while (length <= n) {
            val half = length shr 1
            val step = n / length

            var start = 0
            while (start < n) {
                for (k in 0 until half) {
                    val wr = cos[k * step]
                    val wi = sin[k * step]
                    val a = start + k
                    val b = a + half

                    val tr = re[b] * wr - im[b] * wi
                    val ti = re[b] * wi + im[b] * wr

                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                }
                start += length
            }
            length = length shl 1
        }
    }
}
