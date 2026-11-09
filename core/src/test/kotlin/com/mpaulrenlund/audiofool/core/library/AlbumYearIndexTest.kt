package com.mpaulrenlund.audiofool.core.library

import com.mpaulrenlund.audiofool.core.model.Album
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// New for the phone (0.11.0): the Albums screen's year strip.

class AlbumYearIndexTest {
    private fun albums(vararg years: Int?): List<Album> =
        SortRules.sortAlbums(years.mapIndexed { i, year -> Album("Album $i", "Artist", year = year) })

    @Test fun labels_are_two_digit_years_and_the_bubble_the_whole_year() {
        assertEquals("’97", AlbumYearIndex.labelFor(1997))
        assertEquals("’05", AlbumYearIndex.labelFor(2005))
        assertEquals("’00", AlbumYearIndex.labelFor(2000))
        assertEquals("1997", AlbumYearIndex.bubbleFor(1997))
    }

    @Test fun no_year_is_a_question_mark() {
        assertEquals(AlbumYearIndex.NO_YEAR, AlbumYearIndex.labelFor(null))
        assertEquals(AlbumYearIndex.NO_YEAR, AlbumYearIndex.bubbleFor(null))
    }

    @Test fun one_section_per_year_at_its_first_album_with_undated_albums_last() {
        val list = albums(2001, null, 1995, 1997, 1997, 2001)
        val index = AlbumYearIndex.of(list)
        assertEquals(listOf("’95", "’97", "’01", "?"), index.sections.map { it.label })
        assertEquals(listOf("1995", "1997", "2001", "?"), index.sections.map { it.bubble })
        assertEquals(listOf(0, 1, 3, 5), index.sections.map { it.firstIndex })
        // Every album lands in the section of its own year.
        list.forEachIndexed { i, album ->
            assertEquals(AlbumYearIndex.bubbleFor(album.year), index.sections[index.sectionAt(i)].bubble, album.title)
        }
    }

    @Test fun years_a_century_apart_are_two_sections() {
        val index = AlbumYearIndex.of(albums(1928, 2028))
        assertEquals(listOf("’28", "’28"), index.sections.map { it.label })
        assertEquals(listOf("1928", "2028"), index.sections.map { it.bubble })
    }

    @Test fun no_albums_no_years() {
        assertTrue(AlbumYearIndex.of(emptyList()).isEmpty)
    }
}
