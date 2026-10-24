package com.mpaulrenlund.audiofool.core.library

import com.mpaulrenlund.audiofool.core.model.Track
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Ported from the desktop's ReleaseDateTests.cs and ReleaseDateSortTests.cs.
// The desktop tests that write tags with TagLib and read them back belong to
// tag writing, which the Android app doesn't do; reading is covered on the
// emulator with real files (HANDOFF.md).

class ReleaseDateTest {
    @Test fun accepts_year_month_and_full_date() {
        listOf(
            Triple("2026", "2026", 2026),
            Triple("2026-10", "2026-10", 2026),
            Triple("2026-10-02", "2026-10-02", 2026),
            Triple(" 2026-10-02 ", "2026-10-02", 2026),
            Triple("2026-1-2", "2026-01-02", 2026),
            Triple("2024-02-29", "2024-02-29", 2024),
        ).forEach { (typed, normalized, year) ->
            assertEquals(ReleaseDate.Parsed(normalized, year), ReleaseDate.tryParse(typed), typed)
        }
    }

    @Test fun rejects_anything_else() {
        listOf(
            "0-02",        // the half-typed date in the bug report
            "abc", "26", "2026-13-01", "2026-02-30", "2025-02-29",
            "02-10-2026",  // day first is ambiguous; only year-month-day is taken
            "2026/10/02", "0999", "",
            "٢٠٢٦-١٠-٠٢",  // Arabic-Indic digits
            "２０２６",      // full-width digits
        ).forEach { assertNull(ReleaseDate.tryParse(it), it) }
    }

    @Test fun reads_the_date_part_of_a_stored_timestamp() {
        listOf(
            "2014-05-01T07:00:00Z" to "2014-05-01", // MP4
            "2014-05-01T12:30" to "2014-05-01",     // ID3v2.4 timestamp
            "2014-05" to "2014-05",
            "2014" to "2014",
            "May 2014" to null,
            null to null,
            "٢٠١٤-٠٥-٠١" to null,
            "２０１４-０５" to null,
            "2014-٠٥-01" to null,
        ).forEach { (raw, expected) -> assertEquals(expected, ReleaseDate.fromTag(raw), raw.toString()) }
    }

    @Test fun has_month_tells_a_date_from_a_year() {
        assertFalse(ReleaseDate.hasMonth("2014"))
        assertTrue(ReleaseDate.hasMonth("2014-05"))
    }
}

class ReleaseDateSortTest {
    private var n = 0
    private fun track(album: String, year: Int?, date: String? = null) = Track(
        filePath = "/music/$album/${n++}.flac",
        album = album,
        albumArtist = "Cartoon Theory",
        year = year,
        releaseDate = date,
    )

    private fun albumOrder(vararg tracks: Track) =
        LibraryScanner.build(tracks.toList()).artists.single().albums.map { it.title }

    @Test fun a_full_date_sorts_after_a_bare_year_in_the_same_year() = assertEquals(
        // Remixes + More is tagged "2026" and Ephemeral Dance "2026-10-02";
        // alphabetically Ephemeral would come first.
        listOf("FEEL", "Remixes + More", "Ephemeral Dance"),
        albumOrder(track("Ephemeral Dance", 2026, "2026-10-02"), track("Remixes + More", 2026), track("FEEL", 2022)),
    )

    @Test fun dates_in_the_same_year_sort_by_month_and_day() = assertEquals(
        listOf("March", "Early October", "Late October", "Next Year"),
        albumOrder(
            track("Late October", 2026, "2026-10-25"),
            track("Next Year", 2027),
            track("March", 2026, "2026-03"),
            track("Early October", 2026, "2026-10-02"),
        ),
    )

    @Test fun an_album_sorts_on_its_earliest_track_date() {
        val album = LibraryScanner.build(
            listOf(track("Reissue", 2026, "2026-10-02"), track("Reissue", 1998, "1998-06-01"), track("Reissue", 2026)),
        ).artists.single().albums.single()

        assertEquals("1998-06-01", album.sortDate)
        assertEquals(1998, album.year)
    }

    @Test fun undated_albums_still_go_to_the_bottom() =
        assertEquals(listOf("Dated", "Undated"), albumOrder(track("Undated", null), track("Dated", 2026, "2026-10-02")))

    @Test fun a_tag_date_without_a_month_gives_no_release_date() {
        // TagReader keeps a date only when it names more than the year.
        val reader = TagReader { RawTags(RawTags.Status.Read, year = 2026, date = "2026") }
        assertNull(reader.read("/music/a.flac", FileStamp(1, 1)).track.releaseDate)

        val dated = TagReader { RawTags(RawTags.Status.Read, year = 2026, date = "2026-10-02T00:00:00Z") }
        assertEquals("2026-10-02", dated.read("/music/a.flac", FileStamp(1, 1)).track.releaseDate)
    }

    @Test fun a_full_date_gives_the_year_when_the_tag_library_gives_none() {
        // TagLib's APE tag (WavPack, APE, Musepack) reads "2026-10-02" as no year.
        val track = TagReader { RawTags(RawTags.Status.Read, year = 0, date = "2026-10-02") }.read("/music/a.wv", FileStamp(1, 1)).track
        assertEquals(2026, track.year)
        assertEquals("2026-10-02", track.releaseDate)
    }
}
