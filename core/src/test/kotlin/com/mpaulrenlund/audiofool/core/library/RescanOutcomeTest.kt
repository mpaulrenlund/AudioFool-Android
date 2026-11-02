package com.mpaulrenlund.audiofool.core.library

import com.mpaulrenlund.audiofool.core.model.Track
import org.junit.Test
import kotlin.test.assertEquals

// New for the phone (0.7.0): what the headers say when a rescan the user asked for ends.

class RescanOutcomeTest {
    private fun t(path: String) = Track(filePath = path)

    @Test fun nothing_changed() {
        val tracks = listOf(t("/sd/Music/A/1.flac"), t("/sd/Music/A/2.flac"))
        val outcome = RescanOutcome.of(tracks, tracks, read = 0, unavailableFolders = 0)
        assertEquals(RescanOutcome(0, 0, 0, 0), outcome)
        assertEquals("Up to date · no changes", outcome.text)
    }

    @Test fun a_read_file_that_was_shown_before_is_updated_one_that_was_not_is_new() {
        val before = listOf(t("/sd/Music/A/1.flac"), t("/sd/Music/A/2.flac"), t("/sd/Music/A/3.flac"))
        val after = listOf(t("/sd/Music/A/1.flac"), t("/sd/Music/A/2.flac"), t("/sd/Music/B/4.flac"), t("/sd/Music/B/5.flac"))
        val outcome = RescanOutcome.of(before, after, read = 3, unavailableFolders = 0)
        assertEquals(RescanOutcome(added = 2, updated = 1, removed = 1, unavailableFolders = 0), outcome)
        assertEquals("Up to date · 2 new, 1 updated, 1 removed", outcome.text)
    }

    @Test fun paths_compare_ignoring_case() {
        val outcome = RescanOutcome.of(listOf(t("/sd/Music/A/1.flac")), listOf(t("/SD/music/a/1.FLAC")), read = 1, unavailableFolders = 0)
        assertEquals(RescanOutcome(added = 0, updated = 1, removed = 0, unavailableFolders = 0), outcome)
    }

    @Test fun only_the_counts_that_are_not_zero_are_named() {
        assertEquals("Up to date · 1,234 new", RescanOutcome(1234, 0, 0, 0).text)
        assertEquals("Up to date · 7 removed", RescanOutcome(0, 0, 7, 0).text)
    }

    @Test fun unreachable_folders_are_named_after_the_changes() {
        assertEquals("Up to date · no changes · 1 folder unavailable", RescanOutcome(0, 0, 0, 1).text)
        assertEquals("Up to date · 3 new · 2 folders unavailable", RescanOutcome(3, 0, 0, 2).text)
    }
}
