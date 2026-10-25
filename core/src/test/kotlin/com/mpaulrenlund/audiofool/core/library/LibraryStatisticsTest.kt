package com.mpaulrenlund.audiofool.core.library

import com.mpaulrenlund.audiofool.core.model.Track
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Ported from the desktop's tests/AudioFool.Core.Tests/LibraryStatisticsTests.cs
// (the totals and file types; the rest of the desktop's statistics isn't ported).

class LibraryStatisticsTest {
    private fun t(artist: String, album: String, ext: String = "flac", size: Long = 1_000, seconds: Int = 60, kind: String = "") = Track(
        filePath = "/sdcard/Music/$artist/$album/Song${UUID.randomUUID()}.$ext",
        title = "Song",
        artist = artist,
        albumArtist = artist,
        album = album,
        kind = kind.ifEmpty { AudioFormats.kindFor("x.$ext") },
        fileSize = size,
        durationMs = seconds * 1000L,
        trackNumber = 1,
    )

    private fun stats(vararg tracks: Track) = LibraryStatistics.compute(LibraryScanner.build(tracks.toList()))

    @Test fun an_empty_library_has_no_rows() {
        val stats = LibraryStatistics.compute(MusicLibrary.Empty)

        assertEquals(0, stats.trackCount)
        assertTrue(stats.fileTypes.isEmpty())
    }

    @Test fun totals_add_up_duration_and_size() {
        val stats = stats(
            t("A", "One", size = 100, seconds = 60),
            t("A", "Two", size = 200, seconds = 120),
            t("B", "Three", size = 300, seconds = 30),
        )

        assertEquals(3, stats.trackCount)
        assertEquals(3, stats.albumCount)
        assertEquals(2, stats.artistCount)
        assertEquals(210_000, stats.totalDurationMs)
        assertEquals(600, stats.totalBytes)
    }

    @Test fun file_types_carry_share_and_size_and_sum_to_one() {
        val stats = stats(
            t("A", "X", "flac", size = 30),
            t("A", "X", "flac", size = 30),
            t("A", "X", "flac", size = 30),
            t("A", "X", "mp3", size = 5),
        )

        assertEquals(listOf("FLAC", "MP3"), stats.fileTypes.map { it.label })
        assertEquals(0.75, stats.fileTypes[0].share, 1e-6)
        assertEquals(90, stats.fileTypes[0].bytes)
        assertEquals(1.0, stats.fileTypes.sumOf { it.share }, 1e-6)
    }

    @Test fun kinds_group_ignoring_case_and_blank_is_other_with_ties_by_name() {
        val stats = stats(
            t("A", "X", kind = "flac"),
            t("A", "X", kind = "FLAC"),
            t("A", "X", "wav", kind = " "),
            t("A", "X", "dsf"),
        )

        assertEquals(listOf("flac" to 2, "DSD" to 1, "Other" to 1), stats.fileTypes.map { it.label to it.count })
    }
}
