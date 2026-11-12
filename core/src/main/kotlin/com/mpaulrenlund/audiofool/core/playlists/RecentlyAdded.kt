package com.mpaulrenlund.audiofool.core.playlists

import com.mpaulrenlund.audiofool.core.library.MusicLibrary
import com.mpaulrenlund.audiofool.core.library.SortRules
import com.mpaulrenlund.audiofool.core.model.Album
import com.mpaulrenlund.audiofool.core.model.Track

/**
 * The Recently Added playlist, ported from the desktop's
 * `AudioFool.Core/Playlists/RecentlyAdded.cs` (the user's request there,
 * 2026-10-07): every song that arrived in the last [DAYS] days, worked out from
 * the library each time rather than saved. "Arrived" is [Track.addedUtc], the
 * file's creation time where the file system keeps one.
 *
 * It is grouped into albums. An album holds only its recent songs (the user's
 * call on the desktop), newest album first; within an album the songs keep disc
 * and track order, since files copied together get creation times a few
 * milliseconds apart in whatever order the copy took them.
 */
object RecentlyAdded {
    const val NAME = "Recently Added"

    const val DAYS = 30

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** The header's line in place of "Modified ...". */
    val window: String get() = "Last $DAYS days"

    /**
     * The library's albums that hold a song added since [nowUtc] less [days], each
     * cut down to those songs. Newest addition first; ties by artist, then title,
     * as the Artists list sorts. Songs with no date yet are left out.
     */
    fun albums(library: MusicLibrary, nowUtc: Long, days: Int = DAYS): List<Album> {
        val since = nowUtc - days * DAY_MS
        class Found(val album: Album, val artistKey: String, val newest: Long)
        val found = ArrayList<Found>()

        for (artist in library.artists) {
            for (album in artist.albums) {
                val recent = album.tracks.filter { val added = it.addedUtc; added != null && added >= since }
                if (recent.isEmpty()) continue

                val part = if (recent.size == album.tracks.size) album
                else Album(
                    title = album.title,
                    artistName = album.artistName,
                    year = album.year,
                    sortDate = album.sortDate,
                    tracks = recent,
                    folderArtPath = album.folderArtPath,
                )
                found.add(Found(part, artist.sortKey, recent.maxOf { it.addedUtc!! }))
            }
        }

        return found
            .sortedWith(
                compareByDescending<Found> { it.newest }
                    .thenBy(SortRules.nameComparator) { it.artistKey }
                    .thenBy(SortRules.nameComparator) { it.album.title },
            )
            .map { it.album }
    }

    /** Every song in [albums], in the order the playlist lists them. */
    fun tracks(albums: List<Album>): List<Track> = albums.flatMap { it.tracks }
}
