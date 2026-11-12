package com.mpaulrenlund.audiofool.core.library

import com.mpaulrenlund.audiofool.core.model.Track
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Edit Tags (spec 5.4): the desktop's TagDetails, NumberTexts and the single-track
// window's rules (TagEditViewModel, which has no tests of its own on the desktop).
// Separators_are_normalised… is ported from the desktop's TagWriterTests.cs.

class TagEditTest {
    private val track = Track(
        filePath = "/m/a/09.flac", title = "Tunnel Vision", artist = "Magdalena Bay", albumArtist = "Magdalena Bay",
        album = "Imaginal Disk", year = 2024, trackNumber = 9, trackCount = 15, discNumber = 1, discCount = 1,
    )

    private fun initial(details: TagDetails? = TagDetails()) = TagEditRules.initial(track, details)

    @Test fun separators_are_normalised_and_empty_entries_dropped() {
        assertEquals(listOf("Bach", "Handel"), TagDetails.split(" Bach ;; Handel ; "))
        assertEquals("Bach; Handel", TagDetails.join(listOf("Bach", " ", "Handel ")))
        assertEquals("", TagDetails.join(null))
    }

    @Test fun numbers_keep_the_files_spelling_only_when_it_is_plain_digits_naming_the_number() {
        assertEquals(NumberTexts("09", "15", "1", "2"), NumberTexts.read("09/15", null, "1/2", null))
        // Xiph's own totals, leading zeros kept.
        assertEquals(NumberTexts("3", "012", "", ""), NumberTexts.read("3", "012", null, null))
        // A total in the number wins over a total field, as the scan reads them.
        assertEquals(NumberTexts("3", "12", "", ""), NumberTexts.read("3/12", "10", null, null))
        // Not plain digits: the number, written plainly.
        assertEquals(NumberTexts("7", "", "", ""), NumberTexts.read("7a", null, null, null))
        assertEquals(NumberTexts.Empty, NumberTexts.read(null, null, "0", ""))
    }

    @Test fun the_boxes_start_from_the_library_and_the_files_details() {
        val details = TagDetails(publisher = "Mom+Pop", composer = "Matthew Lewin; Mica Tenenbaum", date = "2024-08-23",
            numbers = NumberTexts("09", "15", "1", "1"))
        val boxes = initial(details)
        assertEquals("Tunnel Vision", boxes[TagField.Title])
        assertEquals("2024-08-23", boxes[TagField.Year])
        assertEquals("09", boxes[TagField.TrackNumber])
        assertEquals("Matthew Lewin; Mica Tenenbaum", boxes[TagField.Composer])
        assertEquals("", boxes[TagField.Comment])
    }

    @Test fun a_file_that_cant_be_read_gives_the_librarys_numbers_and_empty_details() {
        val boxes = TagEditRules.initial(track, null)
        assertEquals("2024", boxes[TagField.Year])
        assertEquals("9", boxes[TagField.TrackNumber])
        assertEquals("15", boxes[TagField.TrackCount])
        assertEquals("", boxes[TagField.Publisher])
    }

    @Test fun the_first_problem_is_given_in_the_desktops_words() {
        val ok = initial()
        assertNull(TagEditRules.problem(ok))
        assertEquals("Album can't be empty.", TagEditRules.problem(ok + (TagField.Album to "  ")))
        assertEquals("Year must be a year (2026) or a date (2026-10-02).", TagEditRules.problem(ok + (TagField.Year to "2026-13")))
        assertNull(TagEditRules.problem(ok + (TagField.Year to "")))
        assertEquals("Track # must be a whole number.", TagEditRules.problem(ok + (TagField.TrackNumber to "-2")))
        assertEquals("Total discs must be a whole number.", TagEditRules.problem(ok + (TagField.DiscCount to "0")))
        assertNull(TagEditRules.problem(ok + (TagField.TrackNumber to "")))
    }

    @Test fun whole_numbers_are_digits_from_one_up() {
        assertEquals(12, TagEditRules.wholeNumber("012"))
        assertEquals(3, TagEditRules.wholeNumber(" 3 "))
        listOf("", "0", "-2", "+3", "1.5", "٣", "99999999999").forEach { assertNull(TagEditRules.wholeNumber(it), it) }
    }

    @Test fun the_edit_writes_every_box_and_only_the_changed_details() {
        val start = initial(TagDetails(publisher = "Mom+Pop", genre = "Pop"))
        val now = start + mapOf(
            TagField.Title to " Tunnel Vision (Live) ",
            TagField.Year to "2024-8-23",
            TagField.TrackNumber to "09",
            TagField.TrackCount to "",
            TagField.Genre to "Pop; Synth-pop",
        )
        val edit = TagEditRules.edit(start, now, emptySet())
        assertEquals("Tunnel Vision (Live)", edit.title)
        assertEquals(2024, edit.year)
        assertEquals("2024-08-23", edit.date)
        assertEquals(9, edit.trackNumber)
        assertNull(edit.trackCount)
        assertEquals(TagDetailsEdit(genre = "Pop; Synth-pop"), edit.details)
    }

    @Test fun a_year_alone_writes_no_date() {
        val edit = TagEditRules.edit(initial(), initial(), emptySet())
        assertEquals(2024, edit.year)
        assertNull(edit.date)
        assertEquals(TagDetailsEdit.None, edit.details)
    }

    @Test fun a_cleared_detail_is_written_empty_and_typing_after_the_clear_wins() {
        val start = initial(TagDetails(comment = "ripped 2024"))
        val cleared = setOf(TagField.Comment, TagField.Conductor)
        val emptied = start + (TagField.Comment to "")
        assertEquals(TagDetailsEdit(conductor = "", comment = ""), TagEditRules.edit(start, emptied, cleared).details)
        val retyped = start + (TagField.Comment to "new")
        assertEquals("new", TagEditRules.edit(start, retyped, cleared).details.comment)
    }

    @Test fun changes_are_typed_text_or_a_clear() {
        val start = initial(TagDetails(publisher = "Mom+Pop"))
        assertFalse(TagEditRules.hasChanges(start, start, emptySet()))
        assertFalse(TagEditRules.hasChanges(start, start + (TagField.Title to "Tunnel Vision "), emptySet()))
        assertTrue(TagEditRules.hasChanges(start, start + (TagField.DiscCount to "2"), emptySet()))
        assertTrue(TagEditRules.hasChanges(start, start + (TagField.Publisher to ""), emptySet()))
        assertTrue(TagEditRules.hasChanges(start, start, setOf(TagField.Genre)))
    }
}
