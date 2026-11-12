package com.mpaulrenlund.audiofool.core.playlists

import com.mpaulrenlund.audiofool.core.library.LibraryScanner
import com.mpaulrenlund.audiofool.core.library.MusicLibrary
import com.mpaulrenlund.audiofool.core.model.Album
import com.mpaulrenlund.audiofool.core.model.Track
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Recently Added: which songs fall in the 30 days, how they group into albums,
 * and the order they're listed in. Ported from the desktop's `RecentlyAddedTests.cs`.
 */
class RecentlyAddedTest {
    private val now = LocalDateTime.of(2026, 10, 7, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val day = 24L * 60 * 60 * 1000

    private fun t(artist: String, album: String, track: Int, added: Long?, disc: Int = 1) = Track(
        filePath = "/music/$artist/$album/$disc-${"%02d".format(track)}.flac",
        artist = artist,
        albumArtist = artist,
        album = album,
        title = "$album $disc-$track",
        trackNumber = track,
        discNumber = disc,
        durationMs = 60_000,
        addedUtc = added,
    )

    private fun recent(vararg tracks: Track): List<Album> = RecentlyAdded.albums(LibraryScanner.build(tracks.toList()), now)

    @Test fun only_songs_added_in_the_last_30_days_show() {
        val albums = recent(
            t("Loam", "New", 1, now - 2 * day),
            t("Rush", "Old", 1, now - 90 * day),
            t("Keyan", "Undated", 1, null),
        )
        assertEquals(listOf("New"), albums.map { it.title })
    }

    @Test fun the_window_includes_exactly_30_days_ago_and_not_a_moment_earlier() {
        val albums = recent(
            t("A", "Edge", 1, now - 30 * day),
            t("B", "Gone", 1, now - 30 * day - 1000),
        )
        assertEquals(listOf("Edge"), albums.map { it.title })
    }

    @Test fun newest_album_comes_first() {
        val albums = recent(
            t("Rush", "Older", 1, now - 20 * day),
            t("Loam", "Newest", 1, now - 1 * day),
            t("Keyan", "Middle", 1, now - 10 * day),
        )
        assertEquals(listOf("Newest", "Middle", "Older"), albums.map { it.title })
    }

    @Test fun an_album_is_placed_by_its_newest_song() {
        val albums = recent(
            t("Rush", "Grew", 1, now - 25 * day),
            t("Rush", "Grew", 2, now - 1 * day),
            t("Loam", "Once", 1, now - 5 * day),
        )
        assertEquals(listOf("Grew", "Once"), albums.map { it.title })
    }

    @Test fun songs_within_an_album_keep_disc_and_track_order_not_copy_order() {
        // Copied out of order: track 3 landed first, disc 2 before disc 1.
        val albums = recent(
            t("Rush", "Set", 3, now - day + 1),
            t("Rush", "Set", 1, now - day + 5, disc = 2),
            t("Rush", "Set", 2, now - day + 9),
            t("Rush", "Set", 1, now - day + 3),
        )
        assertEquals(listOf("Set 1-1", "Set 1-2", "Set 1-3", "Set 2-1"), albums.single().tracks.map { it.title })
    }

    @Test fun part_of_an_album_holds_only_its_new_songs() {
        val library = LibraryScanner.build(
            listOf(
                t("Rush", "Bonus", 1, now - 200 * day),
                t("Rush", "Bonus", 2, now - 200 * day),
                t("Rush", "Bonus", 3, now - 3 * day),
            ),
        )
        assertEquals(listOf("Bonus 1-3"), RecentlyAdded.albums(library, now).single().tracks.map { it.title })
    }

    @Test fun a_wholly_new_album_is_the_library_album_itself() {
        val library = LibraryScanner.build(
            listOf(
                t("Loam", "Fresh", 1, now - day),
                t("Loam", "Fresh", 2, now - day),
            ),
        )
        assertSame(library.artists[0].albums[0], RecentlyAdded.albums(library, now).single())
    }

    @Test fun ties_fall_back_to_artist_then_title() {
        val same = now - 4 * day
        val albums = recent(
            t("The Zombies", "Z", 1, same),
            t("Ash", "B", 1, same),
            t("Ash", "A", 1, same),
        )
        assertEquals(listOf("Ash - A", "Ash - B", "The Zombies - Z"), albums.map { it.toString() })
    }

    @Test fun tracks_run_album_by_album_newest_first() {
        val albums = recent(
            t("Rush", "Old", 2, now - 9 * day),
            t("Rush", "Old", 1, now - 9 * day),
            t("Loam", "New", 1, now - 1 * day),
        )
        assertEquals(listOf("New 1-1", "Old 1-1", "Old 1-2"), RecentlyAdded.tracks(albums).map { it.title })
    }

    @Test fun an_empty_library_has_nothing_recent() {
        assertTrue(RecentlyAdded.albums(MusicLibrary.Empty, now).isEmpty())
    }
}
