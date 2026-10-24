package com.mpaulrenlund.audiofool.core.library

import com.mpaulrenlund.audiofool.core.model.Track
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

// Search's three groups (spec 4.4), on top of the ported LibrarySearch.

class SearchResultsTest {
    private fun t(title: String, artist: String, album: String, n: Int, year: Int, albumArtist: String? = null) = Track(
        filePath = "/music/${albumArtist ?: artist}/$album/$n $title.flac",
        title = title,
        artist = artist,
        albumArtist = albumArtist ?: artist,
        album = album,
        trackNumber = n,
        year = year,
    )

    private val library = LibraryScanner.build(
        listOf(
            t("Sunlight Through Static", "Aphelion Drive", "Second Sight", 1, 1997),
            t("Polygon Weather", "Aphelion Drive", "Second Sight", 3, 1997),
            t("First", "Aphelion Drive", "First Light", 1, 1995),
            t("Polygon Count", "The Polygon Set", "Polygon Count", 1, 2003),
            t("Another", "The Polygon Set", "Polygon Count", 2, 2003),
            t("Older", "The Polygon Set", "Early Days", 1, 1999),
            t("Low Polygon Lullaby", "Grey Chassis", "Idle Animation", 4, 2010),
            t("Guest Spot", "Polygon Guest", "Big Compilation", 1, 2005, albumArtist = "Various Artists"),
        ),
    )
    private val index = LibrarySearch.Index(library.allTracks)

    private fun search(query: String?) = SearchResults.of(library, index, query)

    @Test fun a_blank_query_shows_nothing() {
        assertTrue(search("").isEmpty)
        assertTrue(search("   ").isEmpty)
        assertTrue(search(null).isEmpty)
    }

    @Test fun nothing_matching_shows_nothing() = assertTrue(search("zzz").isEmpty)

    @Test fun groups_hold_every_artist_album_and_song_with_a_matching_track() {
        val r = search("polygon")
        assertEquals(listOf("Grey Chassis", "The Polygon Set", "Various Artists", "Aphelion Drive").sorted(), r.artists.map { it.name }.sorted())
        assertEquals(setOf("Second Sight", "Polygon Count", "Early Days", "Idle Animation", "Big Compilation"), r.albums.map { it.title }.toSet())
        // "Early Days" is by The Polygon Set, so its track matches through the album artist.
        assertTrue(r.songs.any { it.title == "Older" })
        assertTrue(r.songs.none { it.title == "First" || it.title == "Sunlight Through Static" })
    }

    @Test fun counts_are_the_whole_artist_and_album_not_just_the_matches() {
        val r = search("weather")
        val artist = r.artists.single()
        assertEquals("Aphelion Drive", artist.name)
        assertEquals(2, artist.albums.size)
        val album = r.albums.single()
        assertEquals(2, album.tracks.size)
        assertSame(library.artists.first { it.name == "Aphelion Drive" }, artist)
    }

    @Test fun everything_follows_the_library_order() {
        val r = search("polygon")
        assertEquals(library.artists.filter { it in r.artists }, r.artists)
        val libraryAlbums = library.artists.flatMap { it.albums }
        assertEquals(libraryAlbums.filter { it in r.albums }, r.albums)
        val librarySongs = libraryAlbums.flatMap { it.tracks }
        assertEquals(librarySongs.filter { it in r.songs }, r.songs)
    }

    @Test fun the_guest_artist_finds_the_compilation_it_is_on() {
        val r = search("guest")
        assertEquals(listOf("Various Artists"), r.artists.map { it.name })
        assertEquals(listOf("Guest Spot"), r.songs.map { it.title })
    }
}
