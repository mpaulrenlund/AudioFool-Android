package com.mpaulrenlund.audiofool.core.library

import com.mpaulrenlund.audiofool.core.model.Album
import com.mpaulrenlund.audiofool.core.model.ArtistGroup
import com.mpaulrenlund.audiofool.core.model.Track

/**
 * A scanned library: the artist tree plus a flat list of every track.
 * Ported from the desktop's `AudioFool.Core/Library/MusicLibrary.cs`.
 */
class MusicLibrary(
    val artists: List<ArtistGroup> = emptyList(),
    val allTracks: List<Track> = emptyList(),
) {
    val albumCount: Int get() = artists.sumOf { it.albums.size }

    private val albumByPath: Map<String, Album> by lazy {
        HashMap<String, Album>().also { map ->
            for (album in artists.flatMap { it.albums })
                for (track in album.tracks) map.putIfAbsent(Paths.key(track.filePath), album)
        }
    }

    /** The album in this library that holds the file at [path]; null if none. */
    fun albumHolding(path: String): Album? = albumByPath[Paths.key(path)]

    companion object {
        val Empty = MusicLibrary()
    }
}

/** Progress while scanning. */
data class ScanProgress(val filesFound: Int, val filesRead: Int) {
    val fraction: Double get() = if (filesFound == 0) 0.0 else filesRead.toDouble() / filesFound
}

/**
 * What a scan actually did. Lets the app skip saving the cache when a
 * background refresh found nothing new.
 */
data class ScanSummary(
    val reused: Int,
    val read: Int,
    val removed: Int,
    /** Reused tracks that were given their [Track.addedUtc], which an older cache lacked. */
    val backfilled: Int = 0,
    /** Known files that couldn't be opened, which kept the tags the cache had for them. Counted in [reused]. */
    val unopened: Int = 0,
) {
    val anyChanges: Boolean get() = read > 0 || removed > 0 || backfilled > 0

    /** True when every track came straight from the cache. */
    val entirelyCached: Boolean get() = read == 0 && removed == 0 && reused > 0
}

data class ScanResult(
    val library: MusicLibrary,
    val summary: ScanSummary,
    /**
     * Configured folders that could not be read: typically a removed SD card.
     * Their tracks are still in the library, carried over from the cache.
     */
    val unavailableFolders: List<String> = emptyList(),
) {
    val anyFolderUnavailable: Boolean get() = unavailableFolders.isNotEmpty()
}
