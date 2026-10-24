package com.mpaulrenlund.audiofool.core.library

import com.mpaulrenlund.audiofool.core.model.Track
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Ported from the desktop's tests/AudioFool.Core.Tests/LibrarySearchTests.cs.
// Each case runs through both LibrarySearch.filter and the pre-folded Index.

class LibrarySearchTest {
    private fun t(title: String, artist: String, album: String, albumArtist: String? = null) = Track(
        filePath = "/music/$artist/$album/$title.flac",
        title = title,
        artist = artist,
        albumArtist = albumArtist ?: artist,
        album = album,
    )

    private val library = listOf(
        t("Come Together", "The Beatles", "Abbey Road"),
        t("Something", "The Beatles", "Abbey Road"),
        t("Taxman", "The Beatles", "Revolver"),
        t("Speak to Me", "Pink Floyd", "The Dark Side of the Moon"),
        t("Money", "Pink Floyd", "The Dark Side of the Moon"),
        t("Wish You Were Here", "Pink Floyd", "Wish You Were Here"),
    )

    /** The same query both ways; they must agree. */
    private fun filter(tracks: List<Track>, query: String?): List<Track> {
        val plain = LibrarySearch.filter(tracks, query)
        assertEquals(plain, LibrarySearch.Index(tracks).filter(query), "Index disagrees for \"$query\"")
        return plain
    }

    private fun titles(tracks: List<Track>) = tracks.map { it.displayTitle }

    @Test fun an_empty_query_returns_everything() {
        assertEquals(library.size, filter(library, "").size)
        assertEquals(library.size, filter(library, "   ").size)
        assertEquals(library.size, filter(library, null).size)
    }

    @Test fun searching_an_artist_keeps_their_whole_discography() {
        val results = filter(library, "beatles")
        assertEquals(3, results.size)
        assertTrue(results.all { it.artist == "The Beatles" })
    }

    @Test fun searching_an_album_keeps_all_of_its_tracks() =
        assertEquals(listOf("Come Together", "Something"), titles(filter(library, "abbey road")))

    @Test fun searching_a_song_narrows_to_that_track_alone() =
        assertEquals(listOf("Come Together"), titles(filter(library, "come together")))

    @Test fun matching_is_case_insensitive() {
        assertEquals(1, filter(library, "TAXMAN").size)
        assertEquals(1, filter(library, "taxman").size)
        assertEquals(1, filter(library, "TaXmAn").size)
    }

    @Test fun a_partial_word_matches() {
        // Typing is incremental, so the list has to narrow as you go.
        assertEquals(3, filter(library, "beat").size)
        assertEquals(3, filter(library, "b").size)
    }

    @Test fun every_term_must_match_but_they_can_match_different_fields() =
        // "floyd" is the artist and "dark" is in the album title.
        assertEquals(listOf("Speak to Me", "Money"), titles(filter(library, "floyd dark")))

    @Test fun terms_that_cannot_all_match_return_nothing() =
        assertEquals(0, filter(library, "beatles floyd").size)

    @Test fun extra_whitespace_between_terms_is_ignored() =
        assertEquals(2, filter(library, "  floyd    dark  ").size)

    @Test fun album_artist_is_searched_as_well_as_track_artist() {
        val compilation = listOf(t("Song One", "Guest Performer", "Big Compilation", albumArtist = "Various Artists"))
        assertEquals(1, filter(compilation, "various").size)
        assertEquals(1, filter(compilation, "guest").size)
    }

    @Test fun untagged_files_are_searchable_by_filename() {
        val untagged = listOf(Track(filePath = "/music/mystery track.mp3"))
        assertEquals(1, filter(untagged, "mystery").size)
    }

    @Test fun filtered_tracks_rebuild_into_a_correctly_grouped_tree() {
        val built = LibraryScanner.build(filter(library, "pink floyd"))
        val artist = built.artists.single()
        assertEquals("Pink Floyd", artist.name)
        assertEquals(2, artist.albums.size)
        assertEquals(3, artist.trackCount)
    }

    @Test fun unaccented_query_finds_accented_artist() {
        listOf(
            listOf("Bjork", "Björk", "Homogenic", "Human Behaviour"),
            listOf("bjork", "Björk", "Homogenic", "Human Behaviour"),
            listOf("Bela Fleck", "Béla Fleck", "UFO TOFU", "Sinister Minister"),
            listOf("Sigur Ros", "Sigur Rós", "Ágætis byrjun", "Ný batterí"),
            listOf("Beyonce", "Beyoncé", "Lemonade", "Hold Up"),
            listOf("Nino Rota", "Nino Röta", "Fellini", "La Strada"),
        ).forEach { (query, artist, album, title) ->
            assertEquals(1, filter(listOf(t(title, artist, album)), query).size, query)
        }
    }

    @Test fun unaccented_query_finds_accented_album_or_artist() {
        listOf(
            listOf("Blue Oyster Cult", "Blue Öyster Cult", "Tyranny and Mutation", "Hot Rails to Hell"),
            listOf("Cafe Tacvba", "Café Tacvba", "Re", "El Borrego"),
        ).forEach { (query, artist, album, title) ->
            assertEquals(1, filter(listOf(t(title, artist, album)), query).size, query)
        }
    }

    @Test fun accented_query_still_finds_unaccented_values() =
        assertEquals(1, filter(listOf(t("Hold Up", "Beyonce", "Lemonade")), "Beyoncé").size)
}
