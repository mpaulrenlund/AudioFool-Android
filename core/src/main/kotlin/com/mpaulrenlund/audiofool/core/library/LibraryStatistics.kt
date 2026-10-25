package com.mpaulrenlund.audiofool.core.library

import com.mpaulrenlund.audiofool.core.model.Track

/**
 * Aggregate facts about a library, for Statistics (spec 4.6). Pure arithmetic
 * over tracks the scan already holds: no file is opened, so it costs a few
 * milliseconds over 26,000 tracks and can be worked out each time the screen
 * opens. Ported from the desktop's `AudioFool.Core/Library/LibraryStatistics.cs`:
 * the totals and the file types, which is what the phone's spec asks for (the
 * desktop's quality tiers, decades, tag and album checks are left out).
 */
data class LibraryStatistics(
    val trackCount: Int = 0,
    val albumCount: Int = 0,
    val artistCount: Int = 0,
    val totalDurationMs: Long = 0,
    val totalBytes: Long = 0,
    /** One row per Kind ("FLAC", "MP3", ...), most tracks first. */
    val fileTypes: List<Slice> = emptyList(),
) {
    /** [share] is the fraction of all tracks, 0 to 1. */
    data class Slice(val label: String, val count: Int, val share: Double, val bytes: Long)

    companion object {
        val Empty = LibraryStatistics()

        fun compute(library: MusicLibrary): LibraryStatistics {
            val tracks = library.allTracks
            val total = tracks.size
            if (total == 0) return Empty

            return LibraryStatistics(
                trackCount = total,
                albumCount = library.albumCount,
                artistCount = library.artists.size,
                totalDurationMs = tracks.sumOf { it.durationMs },
                totalBytes = tracks.sumOf { it.fileSize },
                // Grouped ignoring case, as the desktop's OrdinalIgnoreCase; the
                // first spelling met names the row.
                fileTypes = tracks.groupBy { kindOf(it).uppercase() }.values
                    .map { g -> Slice(kindOf(g[0]), g.size, share(g.size, total), g.sumOf { it.fileSize }) }
                    .sortedWith(compareByDescending<Slice> { it.count }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }),
            )
        }

        /** The file types grouping key: the Kind, or "Other" when blank. */
        private fun kindOf(track: Track): String = track.kind.ifBlank { "Other" }

        private fun share(count: Int, total: Int): Double = if (total == 0) 0.0 else count.toDouble() / total
    }
}
