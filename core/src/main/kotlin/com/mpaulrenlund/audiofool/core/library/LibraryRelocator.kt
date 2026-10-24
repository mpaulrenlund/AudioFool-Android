package com.mpaulrenlund.audiofool.core.library

import com.mpaulrenlund.audiofool.core.model.Track

/**
 * Path containment and re-pointing, from the desktop's
 * `AudioFool.Core/Library/LibraryRelocator.cs`.
 *
 * The desktop also hunts for a music folder that moved to another drive letter
 * (FindRelocation). Android mounts an SD card under its own volume id
 * (/storage/1A2B-3C4D), which stays the same each time the card goes back in,
 * so that search isn't ported; [rebase] is, for the day it's needed.
 */
object LibraryRelocator {
    data class Relocation(val oldFolder: String, val newFolder: String)

    /**
     * Whether a path sits inside a folder. Compares on a separator boundary so
     * "/storage/x/MusicOld" is not treated as being inside "/storage/x/Music".
     */
    fun isUnder(path: String, folder: String): Boolean {
        if (path.isEmpty() || folder.isEmpty()) return false

        val trimmed = Paths.trimEndSeparators(folder)
        if (trimmed.isEmpty()) return Paths.isSeparator(path[0])

        if (!path.startsWith(trimmed, ignoreCase = true)) return false

        // Equal, or the next character starts a new path segment.
        return path.length == trimmed.length || Paths.isSeparator(path[trimmed.length])
    }

    /** Swaps one path prefix for another, preserving the rest verbatim. */
    fun rebase(path: String, oldRoot: String, newRoot: String): String =
        if (isUnder(path, oldRoot)) {
            val rest = path.substring(Paths.trimEndSeparators(oldRoot).length).trimStart { Paths.isSeparator(it) }
            if (rest.isEmpty()) newRoot else Paths.combine(newRoot, rest)
        } else path

    /** Re-points every track from one root to another. */
    fun rebase(tracks: List<Track>, oldRoot: String, newRoot: String): List<Track> =
        tracks.map { track ->
            if (!isUnder(track.filePath, oldRoot)) track
            else track.relocated(
                rebase(track.filePath, oldRoot, newRoot),
                track.folderArtPath?.let { rebase(it, oldRoot, newRoot) },
            )
        }

    /**
     * Where a track is after [moves]: the library's copy at the new path when
     * [byPath] (keyed by [Paths.key]) holds one, else the track itself re-pointed.
     * Null for a track in none of the moved folders.
     */
    fun movedCopy(track: Track, moves: List<Relocation>, byPath: Map<String, Track>): Track? {
        for (move in moves) {
            if (!isUnder(track.filePath, move.oldFolder)) continue

            val path = rebase(track.filePath, move.oldFolder, move.newFolder)
            return byPath[Paths.key(path)]
                ?: track.relocated(path, track.folderArtPath?.let { rebase(it, move.oldFolder, move.newFolder) })
        }
        return null
    }
}
