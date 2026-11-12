package com.mpaulrenlund.audiofool.core.playlists

import com.mpaulrenlund.audiofool.core.library.Paths
import com.mpaulrenlund.audiofool.core.model.Track
import java.util.UUID

// Ported from the desktop's `AudioFool.Core/Playlists/Playlist.cs` (Playlists and
// Liked, the user's request on the desktop 2026-10-07; on the phone 2026-11-12).

/**
 * One song in a playlist. The path finds it; the artist and album, with the file
 * name, find it again when the music moves (an SD card mounted under another
 * name), as the desktop finds a song after a drive-letter change.
 */
data class PlaylistEntry(val filePath: String, val artist: String, val album: String) {
    companion object {
        fun of(track: Track): PlaylistEntry = PlaylistEntry(track.filePath, track.groupingArtist, track.album)
    }
}

/**
 * A named, ordered list of songs, kept in the app's own file rather than in the
 * music files. Changed only through [PlaylistStore], which stamps [modifiedUtc]
 * (epoch milliseconds, UTC) and saves.
 */
class Playlist(
    val id: String = UUID.randomUUID().toString(),
    name: String = "",
    /** The one playlist the heart fills. Always there; can't be renamed or deleted. */
    val isLiked: Boolean = false,
    modifiedUtc: Long = 0,
    /**
     * The file name of a picture chosen for it on the desktop. Kept so a file
     * the desktop wrote round-trips; the phone has no pictures yet.
     */
    picture: String? = null,
    val entries: MutableList<PlaylistEntry> = ArrayList(),
) {
    var name: String = name
        internal set

    /** When a song was added or removed, or the name changed. */
    var modifiedUtc: Long = modifiedUtc
        internal set

    var picture: String? = picture
        internal set

    fun contains(path: String): Boolean = entries.any { Paths.same(it.filePath, path) }
}
