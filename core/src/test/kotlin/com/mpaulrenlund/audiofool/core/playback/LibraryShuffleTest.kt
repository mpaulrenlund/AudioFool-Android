package com.mpaulrenlund.audiofool.core.playback

import com.mpaulrenlund.audiofool.core.library.LibraryScanner
import com.mpaulrenlund.audiofool.core.library.MusicLibrary
import com.mpaulrenlund.audiofool.core.model.Track
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// New for the phone (Artists' shuffle button, 0.9.0): the desktop has no such button.

class LibraryShuffleTest {
    private fun track(artist: String, album: String, year: Int, number: Int, root: String = "/music") =
        Track(
            filePath = "$root/$artist/$album/$number.flac",
            artist = artist,
            albumArtist = artist,
            album = album,
            year = year,
            trackNumber = number,
            title = "$album $number",
        )

    /** Scanned in a jumbled order, as files come off the disk. */
    private val library = LibraryScanner.build(
        listOf(
            track("Wire", "Chairs Missing", 1978, 2),
            track("Air", "Talkie Walkie", 2004, 1),
            track("Wire", "Pink Flag", 1977, 1),
            track("Air", "Moon Safari", 1998, 2, root = "/sdcard"),
            track("Wire", "Chairs Missing", 1978, 1),
            track("Air", "Moon Safari", 1998, 1, root = "/sdcard"),
        ),
    )

    @Test fun the_queue_is_the_library_in_its_own_order() {
        // Artists, then each artist's albums oldest first, then each album's songs.
        assertEquals(
            listOf("Moon Safari 1", "Moon Safari 2", "Talkie Walkie 1", "Pink Flag 1", "Chairs Missing 1", "Chairs Missing 2"),
            LibraryShuffle.queue(library).map { it.title },
        )
    }

    @Test fun the_queue_holds_each_song_once_as_the_library_does() {
        val queue = LibraryShuffle.queue(library)
        assertEquals(library.allTracks.size, queue.size)
        assertEquals(library.allTracks.toSet(), queue.toSet())
        // The library's own objects: the player matches tracks by identity.
        assertTrue(queue.all { q -> library.allTracks.any { it === q } })
    }

    @Test fun songs_that_cant_be_reached_are_left_out() {
        val queue = LibraryShuffle.queue(library) { it.filePath.startsWith("/sdcard/") }
        assertEquals(listOf("Talkie Walkie 1", "Pink Flag 1", "Chairs Missing 1", "Chairs Missing 2"), queue.map { it.title })
    }

    @Test fun an_empty_library_gives_an_empty_queue() {
        assertEquals(emptyList(), LibraryShuffle.queue(MusicLibrary.Empty))
        assertEquals(emptyList(), LibraryShuffle.queue(library) { true })
    }

    // ---- one artist (Albums' shuffle button, 2026-11-10) ----

    private fun artist(name: String) = library.artists.single { it.name == name }

    @Test fun an_artists_queue_is_their_albums_in_the_albums_screens_order() {
        assertEquals(listOf("Pink Flag 1", "Chairs Missing 1", "Chairs Missing 2"), LibraryShuffle.queue(artist("Wire")).map { it.title })
        assertEquals(listOf("Moon Safari 1", "Moon Safari 2", "Talkie Walkie 1"), LibraryShuffle.queue(artist("Air")).map { it.title })
    }

    @Test fun an_artists_queue_holds_only_their_songs_the_librarys_own() {
        val wire = artist("Wire")
        val queue = LibraryShuffle.queue(wire)
        assertEquals(wire.trackCount, queue.size)
        assertTrue(queue.all { q -> wire.albums.any { album -> album.tracks.any { it === q } } })
    }

    @Test fun an_artists_songs_that_cant_be_reached_are_left_out() {
        val air = artist("Air")
        assertEquals(listOf("Talkie Walkie 1"), LibraryShuffle.queue(air) { it.filePath.startsWith("/sdcard/") }.map { it.title })
        assertEquals(emptyList(), LibraryShuffle.queue(air) { true })
    }

    @Test fun the_start_is_any_song_of_the_queue() {
        val rng = Random(42)
        val starts = List(2000) { LibraryShuffle.start(6, rng) }
        assertEquals((0 until 6).toSet(), starts.toSet())
    }

    @Test fun no_songs_no_start() = assertEquals(-1, LibraryShuffle.start(0))

    @Test fun the_random_start_plays_first_and_the_rest_follow_shuffled() {
        // What the player does with it: the start pinned to the front of a shuffled order.
        val count = 50
        val start = LibraryShuffle.start(count, Random(7))
        val order = PlayOrder().apply { reset(count, start, shuffle = true, rng = Random(7)) }
        assertEquals(start, order.order.first())
        assertEquals((0 until count).toList(), order.order.sorted())
    }
}
