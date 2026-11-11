package com.mpaulrenlund.audiofool.core.art

import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The phone's own rule (spec 5, 2026-11-09): the desktop has no cover colour to port tests from.

class CoverColorTest {
    private fun opaque(rgb: Int) = 0xFF000000.toInt() or rgb

    /** A picture of so many pixels of each colour. */
    private fun picture(vararg parts: Pair<Int, Int>): IntArray =
        parts.flatMap { (rgb, count) -> List(count) { opaque(rgb) } }.toIntArray()

    private fun assertHue(expected: Int, actual: Int?, within: Double = 6.0) {
        assertNotNull(actual, "no colour found")
        val apart = abs(CoverColor.hue(opaque(expected)) - CoverColor.hue(actual))
        assertTrue(minOf(apart, 360 - apart) <= within, "hue ${CoverColor.hue(actual)}, expected ${CoverColor.hue(opaque(expected))}")
    }

    // ---- the cover's main colour ----

    @Test fun a_flat_cover_is_its_own_colour() =
        assertEquals(opaque(RED), CoverColor.main(picture(RED to 100)))

    @Test fun a_red_logo_on_a_black_cover_is_red() =
        assertEquals(opaque(RED), CoverColor.main(picture(0x000000 to 95, RED to 5)))

    /** Since 2026-11-11 (the user's call) a cover without a colour is a grey, at its average lightness; it was null. */
    private fun assertGrey(lightness: Double, actual: Int?) {
        assertNotNull(actual, "no colour found")
        assertTrue(CoverColor.chroma(actual) < 0.002, "chroma ${CoverColor.chroma(actual)}")
        assertEquals(lightness, CoverColor.lightness(actual), 0.01)
    }

    private fun averageLightness(vararg parts: Pair<Int, Int>) =
        parts.sumOf { (rgb, count) -> CoverColor.lightness(opaque(rgb)) * count } / parts.sumOf { it.second }

    @Test fun black_and_white_is_a_grey_of_its_lightness() {
        val parts = arrayOf(0x000000 to 40, 0xFFFFFF to 20, 0x808080 to 30, 0x8A8580 to 10)
        assertGrey(averageLightness(*parts), CoverColor.main(picture(*parts)))
    }

    @Test fun a_black_cover_is_a_dark_grey_and_a_white_one_a_light_grey() {
        assertGrey(0.0, CoverColor.main(picture(0x000000 to 100)))
        assertGrey(1.0, CoverColor.main(picture(0xFFFFFF to 100)))
    }

    @Test fun an_off_white_cover_is_a_grey() =
        assertGrey(averageLightness(CREAM to 90, 0x000000 to 10), CoverColor.main(picture(CREAM to 90, 0x000000 to 10)))

    @Test fun a_dark_brown_cover_is_brown_not_grey() =
        assertHue(DARK_BROWN, CoverColor.main(picture(DARK_BROWN to 70, 0x0C0A08 to 30)))

    /** Under half a percent (2% until 2026-11-11, the user's call: "take any predominant color"). */
    @Test fun a_speck_of_colour_is_not_the_covers_colour() {
        assertGrey(CoverColor.lightness(opaque(0x808080)), CoverColor.main(picture(0x808080 to 299, RED to 1)))
        assertEquals(opaque(RED), CoverColor.main(picture(0x808080 to 99, RED to 1)))
    }

    /** A JPEG's black has a cast; made as light as the playback colour it was a strong blue (2026-11-11). */
    @Test fun a_black_covers_faint_tint_is_not_its_colour() {
        assertHue(GOLD, CoverColor.main(picture(BLUISH_BLACK to 95, GOLD to 5)))
        assertGrey(averageLightness(BLUISH_BLACK to 60, 0xFFFFFF to 40), CoverColor.main(picture(BLUISH_BLACK to 60, 0xFFFFFF to 40)))
    }

    /** Each pixel counts by its chroma squared (2026-11-11): a grey's faint cast doesn't outweigh the colour in it. */
    @Test fun a_faint_cast_over_most_of_a_cover_loses_to_a_vivid_part() =
        assertHue(ORANGE, CoverColor.main(picture(BLUE_GREY to 85, ORANGE to 15)))

    @Test fun the_hue_with_the_most_colour_wins() =
        assertHue(SKY, CoverColor.main(picture(SKY to 60, GRASS to 30, ORANGE to 10)))

    @Test fun a_colourful_part_counts_for_more_than_a_dull_one_of_its_size() =
        assertHue(RED, CoverColor.main(picture(OLIVE to 50, RED to 50)))

    @Test fun a_large_dull_part_beats_a_small_vivid_one() =
        assertHue(DULL_BLUE, CoverColor.main(picture(DULL_BLUE to 80, 0x000000 to 15, RED to 5)))

    /** Crimson (hue 5) and a magenta red (hue 355) lie either side of where the hue circle joins. */
    @Test fun reds_either_side_of_the_hue_circles_join_are_one_colour() {
        val found = CoverColor.main(picture(CRIMSON to 25, MAGENTA_RED to 25, SKY to 50))
        assertNotNull(found)
        val hue = CoverColor.hue(found)
        assertTrue(hue > 350 || hue < 10, "hue $hue")
    }

    @Test fun see_through_pixels_are_not_part_of_the_picture() {
        val clearRed = 0x00C0392B
        assertHue(SKY, CoverColor.main(IntArray(90) { clearRed } + picture(SKY to 10)))
        assertNull(CoverColor.main(IntArray(100) { clearRed }), "nothing to see is no colour, not a grey")
    }

    @Test fun an_empty_picture_has_no_colour() = assertNull(CoverColor.main(IntArray(0)))

    // ---- that colour as the playback colour ----

    private val darkAnchor = CoverColor.lightness(DARK_PLAYBACK)
    private val lightAnchor = CoverColor.lightness(LIGHT_PLAYBACK)

    private fun onDark(rgb: Int) = CoverColor.accent(opaque(rgb), DARK_PLAYBACK, dark = true, RANGE)
    private fun onLight(rgb: Int) = CoverColor.accent(opaque(rgb), LIGHT_PLAYBACK, dark = false, RANGE)

    @Test fun on_dark_a_deep_colour_is_raised_to_the_themes_lightness() {
        val accent = onDark(NAVY)
        assertEquals(darkAnchor, CoverColor.lightness(accent), 0.01)
        assertHue(NAVY, accent)
    }

    @Test fun on_dark_a_pale_colour_comes_down_to_the_top_of_the_band() {
        val accent = onDark(PALE_YELLOW)
        assertEquals(darkAnchor + RANGE, CoverColor.lightness(accent), 0.01)
        assertHue(PALE_YELLOW, accent)
    }

    @Test fun on_light_a_bright_colour_is_lowered_to_the_themes_lightness() {
        val accent = onLight(YELLOW)
        assertEquals(lightAnchor, CoverColor.lightness(accent), 0.01)
        assertHue(YELLOW, accent)
    }

    @Test fun on_light_a_deep_colour_comes_up_to_the_bottom_of_the_band() {
        val accent = onLight(NAVY)
        assertEquals(lightAnchor - RANGE, CoverColor.lightness(accent), 0.01)
        assertHue(NAVY, accent)
    }

    /** Terracotta's lightness (0.69) is inside Dark's band (0.66 to 0.81); a mid blue's (0.44) inside Light's (0.37 to 0.52). */
    @Test fun a_colour_already_in_the_band_is_left_as_it_is() {
        assertEquals(opaque(TERRACOTTA), onDark(TERRACOTTA))
        assertEquals(opaque(LIGHT_BLUE), onLight(LIGHT_BLUE))
    }

    @Test fun a_deep_colour_made_lighter_keeps_its_saturation() {
        val accent = onDark(DEEP_RED)
        assertTrue(CoverColor.chroma(accent) > 2 * CoverColor.chroma(opaque(DEEP_RED)), "chroma ${CoverColor.chroma(accent)}")
        assertHue(DEEP_RED, accent)
    }

    @Test fun a_pastel_made_darker_keeps_its_chroma() {
        val accent = onLight(PALE_PINK)
        assertEquals(CoverColor.chroma(opaque(PALE_PINK)), CoverColor.chroma(accent), 0.01)
        assertHue(PALE_PINK, accent)
    }

    /** Every sixth step of each channel: 216 colours, the greys left out. */
    @Test fun any_colour_lands_in_the_band_opaque_with_its_hue() {
        val steps = (0..255 step 51).toList()
        for (r in steps) for (g in steps) for (b in steps) {
            val rgb = (r shl 16) or (g shl 8) or b
            if (CoverColor.chroma(opaque(rgb)) < CoverColor.GREY_BELOW) continue
            for ((accent, low, high) in listOf(
                Triple(onDark(rgb), darkAnchor, darkAnchor + RANGE),
                Triple(onLight(rgb), lightAnchor - RANGE, lightAnchor),
            )) {
                val name = "#%06X as #%08X".format(rgb, accent)
                assertEquals(0xFF, accent ushr 24, name)
                assertTrue(CoverColor.lightness(accent) in (low - 0.01)..(high + 0.01), "$name: lightness ${CoverColor.lightness(accent)}")
                assertHue(rgb, accent, within = 8.0)
            }
        }
    }

    private companion object {
        // design/theme-tokens.dark.json and theme-tokens.json: color.accent.playback.
        val DARK_PLAYBACK = 0xFF4C8DFF.toInt()
        val LIGHT_PLAYBACK = 0xFF15786C.toInt()

        /** mobile-tokens.json: nowPlaying.coverAccent.lightnessRange. */
        const val RANGE = 0.15

        const val RED = 0xC0392B
        const val SKY = 0x4A90D9
        const val GRASS = 0x5B8C3A
        const val ORANGE = 0xF39C12
        const val OLIVE = 0x6B6B47
        const val DULL_BLUE = 0x39506B
        const val CRIMSON = 0xD6336C
        const val MAGENTA_RED = 0xD63384
        const val CREAM = 0xF0E0D0
        const val DARK_BROWN = 0x2A1A10
        const val NAVY = 0x101830
        const val DEEP_RED = 0x400808
        const val YELLOW = 0xF2C500
        const val PALE_YELLOW = 0xFFE9A8
        const val PALE_PINK = 0xFFD0E0
        const val TERRACOTTA = 0xE07A5F
        const val LIGHT_BLUE = 0x00519A
        const val GOLD = 0xC8A040
        const val BLUISH_BLACK = 0x000814
        const val BLUE_GREY = 0x5A6E8C
    }
}
