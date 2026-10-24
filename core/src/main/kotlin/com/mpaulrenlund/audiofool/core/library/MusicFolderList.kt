package com.mpaulrenlund.audiofool.core.library

import com.mpaulrenlund.audiofool.core.model.Track

/**
 * Rules for the list of music folders: when two entries are the same folder,
 * and what a removal takes out of the library. Pure; it opens nothing.
 * Ported from the desktop's `AudioFool.Core/Library/MusicFolderList.cs`.
 */
object MusicFolderList {
    /**
     * A folder path in comparable form: trimmed, without a trailing separator.
     * The root keeps its separator, and so does a Windows drive root (for the
     * desktop tests): "E:" alone means the current directory on E:.
     */
    fun normalize(path: String): String {
        val trimmed = path.trim()
        val withoutSeparator = Paths.trimEndSeparators(trimmed)
        if (withoutSeparator.isEmpty()) return trimmed
        return if (withoutSeparator.endsWith(':')) withoutSeparator + Paths.SEPARATOR else withoutSeparator
    }

    /** Whether two entries name the same folder, ignoring case and a trailing separator. */
    fun sameFolder(a: String, b: String): Boolean = normalize(a).equals(normalize(b), ignoreCase = true)

    /** The folders with blanks and repeats removed, first occurrence kept, in order, each normalised. */
    fun distinct(folders: Iterable<String>): List<String> {
        val result = mutableListOf<String>()
        for (folder in folders) {
            if (folder.isBlank()) continue
            val normalized = normalize(folder)
            if (result.none { sameFolder(it, normalized) }) result.add(normalized)
        }
        return result
    }

    /**
     * The tracks left once [removed] stops being watched. A track under the
     * removed folder stays when another remaining folder still covers it.
     */
    fun withoutFolder(tracks: Iterable<Track>, removed: String, remaining: Collection<String>): List<Track> {
        val gone = normalize(removed)
        val kept = remaining.map(::normalize)
        return tracks.filter { track ->
            !LibraryRelocator.isUnder(track.filePath, gone) || kept.any { LibraryRelocator.isUnder(track.filePath, it) }
        }
    }
}

/**
 * Whether storage being mounted or removed while the app runs is worth a
 * library refresh: only when one of the music folders now differs from what the
 * library says about it. Ported from the desktop's `AudioFool.Core/Library/DriveChange.cs`.
 */
object DriveChange {
    /**
     * @param musicFolders the music folders, included or not.
     * @param unavailable the folders the last scan couldn't reach.
     * @param arrived whether storage was mounted, rather than only removed.
     * @param folderExists whether a folder is there now.
     */
    fun worthRefresh(
        musicFolders: List<String>,
        unavailable: List<String>,
        arrived: Boolean,
        folderExists: (String) -> Boolean,
    ): Boolean {
        for (folder in musicFolders) {
            val exists = folderExists(folder)
            val listedGone = unavailable.any { MusicFolderList.sameFolder(it, folder) }

            // Back again, or gone since the last scan.
            if (exists == listedGone) return true

            // Still missing, but what arrived may be it.
            if (arrived && !exists) return true
        }
        return false
    }
}
