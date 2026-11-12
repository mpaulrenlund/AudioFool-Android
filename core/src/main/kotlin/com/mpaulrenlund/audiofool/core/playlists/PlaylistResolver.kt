package com.mpaulrenlund.audiofool.core.playlists

import com.mpaulrenlund.audiofool.core.library.Paths
import com.mpaulrenlund.audiofool.core.library.SortRules
import com.mpaulrenlund.audiofool.core.model.Track

// Ported from the desktop's `AudioFool.Core/Playlists/PlaylistResolver.cs`.

/**
 * A playlist's songs as the library has them now.
 * [tracks]: the songs found, in playlist order. [missing]: songs the library
 * doesn't have; they stay in the playlist. [changed]: an entry was brought up to
 * date (re-pointed to a file found elsewhere, or given the artist or album its
 * tags now carry): worth saving, but not a modification of the playlist.
 */
class ResolvedPlaylist(val tracks: List<Track>, val missing: Int, val changed: Boolean) {
    val durationMs: Long get() = tracks.sumOf { it.durationMs }
}

/**
 * Finds playlist songs in a library. By path first; when the path is gone (the
 * music moved), by file name within the same artist and album. One resolver per
 * library, since building the lookups is the costly part.
 */
class PlaylistResolver(tracks: List<Track>) {
    private val byPath = HashMap<String, Track>(tracks.size * 2)
    private val byFileName = HashMap<String, MutableList<Track>>(tracks.size * 2)

    init {
        for (track in tracks) {
            byPath.putIfAbsent(Paths.key(track.filePath), track)
            byFileName.getOrPut(Paths.key(Paths.fileName(track.filePath))) { ArrayList(1) }.add(track)
        }
    }

    fun find(entry: PlaylistEntry): Track? {
        byPath[Paths.key(entry.filePath)]?.let { return it }
        return byFileName[Paths.key(Paths.fileName(entry.filePath))]?.firstOrNull {
            SortRules.sameName(it.groupingArtist, entry.artist) && SortRules.sameName(it.album, entry.album)
        }
    }

    /**
     * The playlist's songs in order. Entries found are updated in place to the
     * track's current path, artist and album (see [ResolvedPlaylist.changed]);
     * entries not found are left exactly as they were.
     */
    fun resolve(playlist: Playlist): ResolvedPlaylist {
        val tracks = ArrayList<Track>(playlist.entries.size)
        val shown = HashSet<String>()
        var missing = 0
        var changed = false

        for (i in playlist.entries.indices) {
            val entry = playlist.entries[i]
            val track = find(entry)
            if (track == null) {
                missing++
                continue
            }

            val current = PlaylistEntry.of(track)
            if (current != entry) {
                playlist.entries[i] = current
                changed = true
            }

            // Two entries can land on one file once re-pointed; it shows once.
            if (shown.add(Paths.key(track.filePath))) tracks.add(track)
        }

        return ResolvedPlaylist(tracks, missing, changed)
    }
}
