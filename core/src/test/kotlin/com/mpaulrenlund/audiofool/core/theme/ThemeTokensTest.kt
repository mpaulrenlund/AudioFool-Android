package com.mpaulrenlund.audiofool.core.theme

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// Ported from the desktop's tests/AudioFool.Core.Tests/ThemeTokensTests.cs,
// reading the token files from design/ (the desktop's own, copied unchanged).

class ThemeTokensTest {
    private val design = File("../design").takeIf { it.isDirectory } ?: File("design")
    private fun real(file: String) = ThemeTokens.parse(File(design, file).readText())
    private val light by lazy { real("theme-tokens.json") }
    private val dark by lazy { real("theme-tokens.dark.json") }
    private val mobile by lazy { real("mobile-tokens.json") }

    // ------------------------------------------------------------- colours

    @Test fun colours_parse_to_argb() {
        mapOf(
            "#BAB7B3" to ThemeColor(255, 0xBA, 0xB7, 0xB3),
            "#ffda00" to ThemeColor(255, 0xFF, 0xDA, 0x00),
            "rgba(255,255,255,0.45)" to ThemeColor(115, 255, 255, 255),
            "rgba(0, 0, 0, 0.035)" to ThemeColor(9, 0, 0, 0),
            "rgba(0,0,0,0)" to ThemeColor(0, 0, 0, 0),
            "rgb(21,120,108)" to ThemeColor(255, 21, 120, 108),
        ).forEach { (text, expected) -> assertEquals(expected, ThemeColor.parse(text), text) }
    }

    @Test fun ambiguous_or_bad_colours_are_refused() {
        // CSS RRGGBBAA and Android AARRGGBB disagree, so neither 8-digit form is accepted.
        for (text in listOf("#BAB7B3FF", "#FFF", "rgba(0,0,0,1.5)", "rgba(0,0,300,0.5)", "teal")) {
            val ex = assertFailsWith<ThemeTokenException>(text) { ThemeColor.parse(text, "color.x") }
            assertEquals("color.x", ex.tokenPath)
        }
    }

    // ------------------------------------------------------------- shadows

    @Test fun a_multi_layer_shadow_splits_on_commas_outside_rgba() {
        val layers = ShadowLayer.parseList(
            "inset 0 -3px 0 rgba(0,0,0,0.12), inset 0 1px 0 rgba(255,255,255,0.8), 0 1px 3px rgba(0,0,0,0.25)",
        )
        assertEquals(3, layers.size)
        assertEquals(ShadowLayer(true, 0.0, -3.0, 0.0, 0.0, ThemeColor(31, 0, 0, 0)), layers[0])
        assertEquals(ShadowLayer(true, 0.0, 1.0, 0.0, 0.0, ThemeColor(204, 255, 255, 255)), layers[1])
        assertEquals(ShadowLayer(false, 0.0, 1.0, 3.0, 0.0, ThemeColor(64, 0, 0, 0)), layers[2])
    }

    @Test fun shadow_lengths_are_x_y_blur_spread() {
        val a = ShadowLayer.parseList("0 4px rgba(0,0,0,0.2)").single()
        assertEquals(listOf(0.0, 4.0, 0.0, 0.0), listOf(a.offsetX, a.offsetY, a.blur, a.spread))
        val b = ShadowLayer.parseList("2px 4px 10px 1px #000000").single()
        assertEquals(listOf(2.0, 4.0, 10.0, 1.0), listOf(b.offsetX, b.offsetY, b.blur, b.spread))
    }

    @Test fun bad_shadows_are_refused() {
        for (text in listOf("0 4 10px rgba(0,0,0,0.2)", "4px rgba(0,0,0,0.2)", "0 4px 10px"))
            assertFailsWith<ThemeTokenException>(text) { ShadowLayer.parseList(text, "shadow.x") }
    }

    // ------------------------------------------------------------ the walk

    @Test fun leaves_are_keyed_by_path_and_typed_by_group() {
        val t = ThemeTokens.parse(
            """
            {
              "${'$'}description": "ignored",
              "color": { "panel": { "bg": "#CAC7C3" } },
              "shadow": { "art": "0 0 6px rgba(0,0,0,0.25)" },
              "albums": { "rowHeight": 64, "align": "right" },
              "type": { "x": { "size": 12, "weight": 600, "color": "panel.bg", "uppercase": true } },
              "font": { "family": ["A", "B"] }
            }
            """,
        )
        assertEquals(ThemeColor(255, 0xCA, 0xC7, 0xC3), t.colors["color.panel.bg"])
        assertEquals(64.0, t.numbers["albums.rowHeight"])
        assertEquals("right", t.texts["albums.align"])
        assertEquals(true, t.flags["type.x.uppercase"])
        assertEquals(1, t.shadows["shadow.art"]!!.size)
        assertEquals(listOf("A", "B"), t.fontFamily)
        assertTrue(t.keys.none { '$' in it })
    }

    @Test fun an_array_of_objects_is_addressed_by_id() {
        val t = ThemeTokens.parse(
            """{ "songTable": { "columns": [ { "id": "time", "width": 44, "align": "right" }, { "id": "song", "flex": 1.05 } ] } }""",
        )
        assertEquals(44.0, t.numbers["songTable.columns.time.width"])
        assertEquals("right", t.texts["songTable.columns.time.align"])
        assertEquals(1.05, t.numbers["songTable.columns.song.flex"])
        assertTrue(t.keys.none { it.endsWith(".id") })
    }

    @Test fun a_bad_value_names_its_path() {
        val ex = assertFailsWith<ThemeTokenException> { ThemeTokens.parse("""{ "color": { "text": { "muted": "grey" } } }""") }
        assertEquals("color.text.muted", ex.tokenPath)
    }

    @Test fun a_type_style_must_name_a_real_colour() {
        val ex = assertFailsWith<ThemeTokenException> {
            ThemeTokens.parse("""{ "color": { "text": { "muted": "#4E4B48" } }, "type": { "x": { "size": 11, "color": "text.mute" } } }""")
        }
        assertEquals("type.x.color", ex.tokenPath)
    }

    @Test fun tabular_figures_must_name_a_type_style() {
        val ex = assertFailsWith<ThemeTokenException> {
            ThemeTokens.parse(
                """{ "font": { "tabularFiguresFor": ["times"] }, "color": { "t": { "a": "#000000" } }, "type": { "x": { "size": 11, "color": "t.a" } } }""",
            )
        }
        assertEquals("font.tabularFiguresFor", ex.tokenPath)
    }

    // ----------------------------------------------------- the real files

    @Test fun the_shipped_token_files_parse() {
        for (t in listOf(light, dark)) {
            assertEquals(67, t.colors.size)
            assertEquals(20, t.textStyles.size)
            assertEquals(9, t.shadows.size)
            assertEquals(listOf("Segoe UI Variable Text", "Segoe UI"), t.fontFamily)
        }
    }

    @Test fun the_light_file_matches_spot_values_in_the_spec() {
        assertEquals(ThemeColor.parse("#BAB7B3"), light.colors["color.window.bg"])
        assertEquals(ThemeColor.parse("#15786C"), light.colors["color.accent.playback"])
        assertEquals(ThemeColor(115, 255, 255, 255), light.colors["color.panel.highlight"])
        assertEquals(4.0, light.numbers["radius.surface"])
    }

    @Test fun the_dark_file_matches_spot_values_in_its_spec() {
        assertEquals(ThemeColor.parse("#111216"), dark.colors["color.window.bg"])
        assertEquals(ThemeColor.parse("#4C8DFF"), dark.colors["color.accent.playback"])
        assertEquals(ThemeColor.parse("#A9C8FF"), dark.colors["color.accent.blue"])
        assertEquals(ThemeColor.parse("#FF5A5F"), dark.colors["color.status.bitPerfectOff"])
        assertEquals(ThemeColor(51, 40, 90, 200), dark.colors["color.window.titleGlow"])

        val glow = dark.shadows["shadow.playGlow"]!!.single()
        assertEquals(listOf(0.0, 0.0, 18.0), listOf(glow.offsetX, glow.offsetY, glow.blur))
        assertEquals(false, glow.inset)
    }

    @Test fun the_dark_only_effects_are_clear_in_light() {
        assertEquals(0, light.colors["color.window.titleGlow"]!!.a)
        assertEquals(0, light.colors["color.control.playRing"]!!.a)
        assertEquals(0, light.shadows["shadow.playGlow"]!!.single().color.a)
        // Spec 2: the glow's shape.
        assertEquals(listOf(900.0, 130.0, -40.0),
            listOf("titleBar.glow.radiusX", "titleBar.glow.radiusY", "titleBar.glow.centerY").map { light.number(it) })
    }

    // ------------------------------------------------ the two files agree

    @Test fun both_colour_files_have_the_same_keys() {
        assertEquals(emptySet(), light.keys - dark.keys, "only in Light")
        assertEquals(emptySet(), dark.keys - light.keys, "only in Dark")
    }

    @Test fun outside_colours_and_shadows_both_files_hold_the_same_values() {
        fun nonColour(t: ThemeTokens): Map<String, String> =
            (t.numbers.mapValues { it.value.toString() } + t.flags.mapValues { it.value.toString() } +
                t.texts + t.lists.mapValues { it.value.joinToString("|") })
        val l = nonColour(light)
        val d = nonColour(dark)
        val differ = (l.keys + d.keys).filter { l[it] != d[it] }.map { "$it: ${l[it]} / ${d[it]}" }
        assertEquals(emptyList(), differ)
    }

    // ------------------------------------------------------ mobile tokens

    @Test fun every_mobile_colour_has_a_light_and_a_dark_value() {
        val colours = mobile.colors.keys
        assertTrue(colours.isNotEmpty())
        val roles = colours.map { it.substringBeforeLast('.') }.toSet()
        for (role in roles) {
            assertTrue("$role.light" in colours, "$role has no light value")
            assertTrue("$role.dark" in colours, "$role has no dark value")
        }
        assertEquals(colours.size, roles.size * 2, "a mobile colour has something besides light and dark")
    }

    @Test fun the_mobile_file_matches_spot_values_in_the_spec() {
        assertEquals(ThemeColor.parse("#B3261E"), mobile.colors["color.bpLabel.off.light"])
        assertEquals(ThemeColor.parse("#3DDC84"), mobile.colors["color.bpLabel.on.dark"])
        assertEquals(64.0, mobile.numbers["artists.rowHeight"])
        assertEquals(32.0, mobile.numbers["album.columns.number.width"])
        assertEquals(0.117, mobile.numbers["nowPlaying.playButton.ofScreenHeight"])
        assertEquals("Selawik", mobile.texts["font.family"])
    }
}
