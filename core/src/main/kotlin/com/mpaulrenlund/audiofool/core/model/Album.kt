package com.mpaulrenlund.audiofool.core.model

/**
 * A set of tracks sharing an album artist and album title.
 * Ported from the desktop's `AudioFool.Core/Models/Album.cs`.
 */
class Album(
    val title: String,
    /** The album artist this album hangs under. */
    val artistName: String,
    /** Earliest year found across the album's tracks; null when untagged. */
    val year: Int? = null,
    /**
     * Earliest [Track.sortDate] across the tracks: a full date when the files
     * carry one, else the year, so two albums from the same year still sort in
     * release order. Null when untagged.
     */
    val sortDate: String? = null,
    val tracks: List<Track> = emptyList(),
    /** Cover image found alongside the files (cover.jpg, folder.jpg, ...). */
    val folderArtPath: String? = null,
) {
    val discCount: Int get() = tracks.map { it.discNumber ?: 1 }.ifEmpty { listOf(1) }.distinct().size

    val totalDurationMs: Long get() = tracks.sumOf { it.durationMs }

    /** "1975" or "Year unknown". */
    val yearDisplay: String get() = year?.toString() ?: "Year unknown"

    /** "1 track" / "21 tracks". */
    val trackCountDisplay: String get() = if (tracks.size == 1) "1 track" else "${tracks.size} tracks"

    override fun toString(): String = "$artistName - $title"
}
