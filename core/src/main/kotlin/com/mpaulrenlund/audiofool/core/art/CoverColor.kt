package com.mpaulrenlund.audiofool.core.art

import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The colour a cover is in, and that colour as Now Playing's playback colour
 * (spec 5; the user's call, 2026-11-09). The phone's own rule: the desktop has
 * none to port.
 *
 * Worked out in OKLab (Björn Ottosson, 2020), where a colour is a lightness, a
 * chroma (how far from grey) and a hue, and a hue keeps its name as it is made
 * lighter or darker; in HSL a blue made lighter drifts to purple.
 */
object CoverColor {
    /** A cover is judged from this many pixels a side: its middle square, scaled down. */
    const val SAMPLE_SIDE = 64

    /**
     * Below this chroma a pixel is grey, black or white, and says nothing of the
     * cover's colour. A dark pixel needs less to be a colour (a dark brown has
     * little chroma and is plainly brown; a cream with as much is off-white): a
     * tenth of its lightness, no less than [GREY_BELOW_DARK].
     */
    const val GREY_BELOW = 0.04
    const val GREY_BELOW_DARK = 0.02
    private const val GREY_PER_LIGHTNESS = 0.1

    /**
     * Below this lightness a pixel is black, whatever its tint: a JPEG's black is
     * rarely neutral, and a black cover's faint blue or green cast, made as light
     * as the playback colour, came out a strong blue or green (2026-11-11: a black
     * cover with a gold outline was blue). A dark brown is above it.
     */
    const val BLACK_BELOW = 0.15

    /**
     * A cover with a smaller share of coloured pixels than this is black and white:
     * a few pixels, not a part of the picture. 2% until 2026-11-11 (the user's call:
     * "take any predominant color from the album"). Of the desktop's 8,604 covers,
     * 9% are under it; those looked at were all black and white.
     */
    const val MIN_SHARE = 0.005

    /** A [main] with less chroma than this is the grey of a black and white cover: a colour has at least [GREY_BELOW_DARK]. */
    const val NEUTRAL_BELOW = 0.01

    /** Hues are counted in slices of 10 degrees; the winner is the best three neighbours. */
    private const val SLICES = 36

    /**
     * The cover's main colour, as opaque 0xAARRGGBB: the hue that most of its
     * colour is in, counting each pixel by its chroma squared, so a red logo on a
     * black cover is red, a blue sky with a small sun is blue, and a grey's faint
     * cast doesn't outweigh the colour beside it (by its chroma until 2026-11-11).
     * Black pixels ([BLACK_BELOW]) don't count. The colour given is the average of
     * the pixels of that hue.
     *
     * A cover with no colour to speak of (black and white, or under [MIN_SHARE]
     * coloured) is a grey, at its pixels' average lightness, so [accent] makes it
     * a neutral that reads on the theme (2026-11-11, the user's call: until then
     * such a cover kept the theme's teal or blue). Null only for a picture with
     * nothing in it; see-through pixels are not part of the picture.
     */
    fun main(pixels: IntArray): Int? {
        val weight = DoubleArray(SLICES)
        val l = DoubleArray(SLICES)
        val a = DoubleArray(SLICES)
        val b = DoubleArray(SLICES)
        var seen = 0
        var coloured = 0
        var lightness = 0.0
        for (pixel in pixels) {
            if (pixel ushr 24 < 128) continue
            seen++
            val lab = Lab.of(pixel)
            lightness += lab.l
            val chroma = lab.chroma
            if (lab.l < BLACK_BELOW) continue
            if (chroma < (GREY_PER_LIGHTNESS * lab.l).coerceIn(GREY_BELOW_DARK, GREY_BELOW)) continue
            coloured++
            val slice = (lab.hueDegrees / 360.0 * SLICES).toInt().coerceIn(0, SLICES - 1)
            val w = chroma * chroma
            weight[slice] += w
            l[slice] += w * lab.l
            a[slice] += w * lab.a
            b[slice] += w * lab.b
        }
        if (seen == 0) return null
        if (coloured < seen * MIN_SHARE) return Lab(lightness / seen, 0.0, 0.0).toArgb()

        // A hue that straddles two slices isn't split: each slice is counted with its neighbours.
        fun around(values: DoubleArray, i: Int) = values[(i + SLICES - 1) % SLICES] + values[i] + values[(i + 1) % SLICES]
        val best = (0 until SLICES).maxBy { around(weight, it) }
        val total = around(weight, best)
        return Lab(around(l, best) / total, around(a, best) / total, around(b, best) / total).toArgb()
    }

    /**
     * [main] as a playback colour that reads on the theme: the same hue, at a
     * lightness within [range] of the theme's own [playback] colour, on the side
     * away from the background: no darker than it on Dark (and up to [range]
     * lighter), no lighter than it on Light (and down to [range] darker). So the
     * accent is never harder to see than the colour the theme was designed with.
     *
     * Made lighter, the colour keeps its saturation (its chroma for its lightness),
     * so a deep red cover gives a red, not a greyish pink; made darker it keeps
     * its chroma, so a pastel stays a colour. Where the screen can't show that much
     * chroma at that lightness, it gets as much as there is. Opaque 0xAARRGGBB.
     *
     * A grey ([main] of a black and white cover) on Dark is [greyOnDark] light, when
     * given, whatever the cover's lightness: at the band's lightness it read quieter
     * than any colour (2026-11-11, the user's call: "Brighter on Dark"). On Light it
     * is fitted as any colour is.
     */
    fun accent(main: Int, playback: Int, dark: Boolean, range: Double, greyOnDark: Double? = null): Int {
        val from = Lab.of(main)
        if (dark && greyOnDark != null && from.chroma < NEUTRAL_BELOW) return Lab(greyOnDark, 0.0, 0.0).toArgb()
        val anchor = Lab.of(playback).l
        val lightness = if (dark) from.l.coerceIn(anchor, anchor + range) else from.l.coerceIn(anchor - range, anchor)
        val chroma = if (lightness > from.l && from.l > 0) from.chroma * lightness / from.l else from.chroma
        val hue = from.hueRadians

        fun at(c: Double) = Lab(lightness, c * cos(hue), c * sin(hue))
        if (at(chroma).showable) return at(chroma).toArgb()
        // The most chroma the screen can show at this lightness and hue.
        var low = 0.0
        var high = chroma
        repeat(20) {
            val middle = (low + high) / 2
            if (at(middle).showable) low = middle else high = middle
        }
        return at(low).toArgb()
    }

    /** A colour's OKLab lightness (0 black to 1 white), chroma and hue in degrees (0 to 360), for the tests. */
    internal fun lightness(argb: Int): Double = Lab.of(argb).l
    internal fun chroma(argb: Int): Double = Lab.of(argb).chroma
    internal fun hue(argb: Int): Double = Lab.of(argb).hueDegrees

    /** A colour in OKLab: [l] lightness, [a] green to red, [b] blue to yellow. */
    private class Lab(val l: Double, val a: Double, val b: Double) {
        val chroma: Double get() = hypot(a, b)
        val hueRadians: Double get() = atan2(b, a)
        val hueDegrees: Double get() = (Math.toDegrees(hueRadians) + 360.0) % 360.0

        /** Linear red, green and blue; outside 0 to 1 where sRGB has no such colour. */
        private fun linear(): DoubleArray {
            val l3 = (l + 0.3963377774 * a + 0.2158037573 * b).pow(3)
            val m3 = (l - 0.1055613458 * a - 0.0638541728 * b).pow(3)
            val s3 = (l - 0.0894841775 * a - 1.2914855480 * b).pow(3)
            return doubleArrayOf(
                4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3,
                -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3,
                -0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3,
            )
        }

        /** Whether a screen can show it, give or take a rounding. */
        val showable: Boolean get() = linear().all { it >= -SLACK && it <= 1 + SLACK }

        fun toArgb(): Int {
            val (r, g, b) = linear().map { (encode(it.coerceIn(0.0, 1.0)) * 255).roundToInt() }
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        companion object {
            private const val SLACK = 0.0005

            fun of(argb: Int): Lab {
                val r = decode(((argb shr 16) and 0xFF) / 255.0)
                val g = decode(((argb shr 8) and 0xFF) / 255.0)
                val b = decode((argb and 0xFF) / 255.0)
                val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
                val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
                val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
                return Lab(
                    0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                    1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                    0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
                )
            }

            /** sRGB's curve: a stored channel value to light, and back. */
            private fun decode(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            private fun encode(c: Double) = if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1 / 2.4) - 0.055
        }
    }
}
