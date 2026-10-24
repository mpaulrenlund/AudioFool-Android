package com.mpaulrenlund.audiofool.core.library

import com.mpaulrenlund.audiofool.core.model.Album
import com.mpaulrenlund.audiofool.core.model.ArtistGroup
import com.mpaulrenlund.audiofool.core.model.Track
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Search's three groups (spec 4.4): the artists, albums and songs that hold a
 * matching track. The matching is the desktop's [LibrarySearch]: a track matches
 * when every term is in its title, artist, album artist or album, and an album or
 * artist shows when any of its tracks match.
 *
 * Unlike the desktop, which rebuilds a smaller tree from the matches, the groups
 * hold the library's own artists and albums, so "3 albums" and "11 tracks" count
 * all of them, not only the matching ones (the mockup's figures). Everything is in
 * the library's order: artists as listed, their albums oldest first, the songs
 * album by album.
 */
class SearchResults(
    val artists: List<ArtistGroup>,
    val albums: List<Album>,
    val songs: List<Track>,
) {
    val isEmpty: Boolean get() = artists.isEmpty() && albums.isEmpty() && songs.isEmpty()

    companion object {
        val None = SearchResults(emptyList(), emptyList(), emptyList())

        /** The groups for [query]; none for a blank query. */
        fun of(library: MusicLibrary, index: LibrarySearch.Index, query: String?): SearchResults {
            if (LibrarySearch.terms(query).isEmpty()) return None
            return of(library, index.filter(query))
        }

        /** The groups holding [matched], tracks of [library]. */
        fun of(library: MusicLibrary, matched: Collection<Track>): SearchResults {
            if (matched.isEmpty()) return None
            val hit: MutableSet<Track> = Collections.newSetFromMap(IdentityHashMap(matched.size * 2))
            hit.addAll(matched)

            val artists = ArrayList<ArtistGroup>()
            val albums = ArrayList<Album>()
            val songs = ArrayList<Track>()
            for (artist in library.artists) {
                var artistHit = false
                for (album in artist.albums) {
                    var albumHit = false
                    for (track in album.tracks) {
                        if (track in hit) {
                            songs.add(track)
                            albumHit = true
                        }
                    }
                    if (albumHit) {
                        albums.add(album)
                        artistHit = true
                    }
                }
                if (artistHit) artists.add(artist)
            }
            return SearchResults(artists, albums, songs)
        }
    }
}
