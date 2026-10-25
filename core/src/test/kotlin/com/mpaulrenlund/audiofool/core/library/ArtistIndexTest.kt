package com.mpaulrenlund.audiofool.core.library

import com.mpaulrenlund.audiofool.core.model.ArtistGroup
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// New for the phone (0.2.0): the Artists screen's letter strip.

class ArtistIndexTest {
    private fun artists(vararg names: String): List<ArtistGroup> =
        SortRules.sortArtists(names.map { ArtistGroup(it, SortRules.artistSortKey(it)) })

    @Test fun files_under_the_sort_key_not_the_shown_name() {
        assertEquals("B", ArtistIndex.labelFor(SortRules.artistSortKey("The Beatles")))
        assertEquals("T", ArtistIndex.labelFor(SortRules.artistSortKey("Thelonious Monk")))
        assertEquals("T", ArtistIndex.labelFor(SortRules.artistSortKey("The The")))
    }

    @Test fun drops_case_and_accents() {
        mapOf(
            "abba" to "A", "Åsa" to "A", "Ébène" to "E", "Ötzi" to "O", "Ñu" to "N",
            "Øystein" to "O", "Æther" to "A", "Łódź" to "L", "Þeyr" to "T",
        ).forEach { (name, label) -> assertEquals(label, ArtistIndex.labelFor(name), name) }
    }

    @Test fun digits_symbols_and_blanks_are_hash() {
        for (name in listOf("2Pac", "50 Cent", "!!!", "(hed) p.e.", "", "   "))
            assertEquals(ArtistIndex.DIGITS_AND_SYMBOLS, ArtistIndex.labelFor(name), name)
    }

    @Test fun other_scripts_share_one_entry() {
        for (name in listOf("Кино", "Σωκράτης", "坂本龍一", "ДДТ"))
            assertEquals(ArtistIndex.OTHER_SCRIPTS, ArtistIndex.labelFor(name), name)
    }

    @Test fun lists_only_the_letters_present_with_their_first_artist() {
        val list = artists("Aeon Static", "Aphelion Drive", "The Beatles", "Bitcrush Quartet", "Dual Shock", "Zed")
        val index = ArtistIndex.of(list)
        assertEquals(listOf("A", "B", "D", "Z"), index.sections.map { it.label })
        assertEquals("The Beatles", list[index.sections[1].firstIndex].name)
        assertEquals("Dual Shock", list[index.sections[2].firstIndex].name)
    }

    @Test fun sections_follow_the_list_order() {
        val list = artists("Zed", "2Pac", "Кино", "Åsa", "abba", "Mogwai", "!!!")
        val index = ArtistIndex.of(list)
        val starts = index.sections.map { it.firstIndex }
        assertEquals(starts.sorted(), starts)
        assertEquals(0, starts.first())
        // Every artist lands in the section labelled with its own letter.
        list.forEachIndexed { i, artist ->
            assertEquals(ArtistIndex.labelFor(artist.sortKey), index.sections[index.sectionAt(i)].label, artist.name)
        }
    }

    @Test fun section_at_a_list_position() {
        val index = ArtistIndex.of(artists("Aeon", "Aphelion", "Bitcrush", "Dual", "Emotion"))
        assertEquals(listOf(0, 0, 1, 2, 3), (0..4).map { index.sectionAt(it) })
    }

    @Test fun an_empty_library_has_no_letters() {
        assertTrue(ArtistIndex.of(emptyList()).isEmpty)
        assertEquals(-1, ArtistIndex.Empty.sectionAt(0))
    }
}
