package com.mpaulrenlund.audiofool.core.model

import com.mpaulrenlund.audiofool.core.library.Paths

/**
 * One audio file and everything we know about it. Immutable once scanned.
 * Ported from the desktop's `AudioFool.Core/Models/Track.cs`.
 *
 * Stored in the library cache ([com.mpaulrenlund.audiofool.core.library.LibraryCache]),
 * so its shape is a file format: changing it needs the cache version bumped.
 * Times are epoch milliseconds, UTC; durations are milliseconds.
 */
data class Track(
    val filePath: String,
    /**
     * File size and last-write time, used to decide whether the cached tags are
     * still good. Re-reading tags costs about a millisecond per file; comparing
     * these two numbers costs nothing, which is the whole basis of the cache.
     */
    val fileSize: Long = 0,
    val modifiedUtc: Long = 0,
    /**
     * When the file arrived on the storage (its creation time, where the file
     * system keeps one). Null only until a scan's stat fills it in.
     */
    val addedUtc: Long? = null,
    val trackNumber: Int? = null,
    /** Total tracks on this disc: the "12" in "3/12". */
    val trackCount: Int? = null,
    val title: String = "",
    val artist: String = "",
    /**
     * The album's credited artist. Distinct from [artist] so that compilations
     * and "feat." guests don't fragment the artist list.
     */
    val albumArtist: String = "",
    val album: String = "",
    val durationMs: Long = 0,
    val discNumber: Int? = null,
    /** Total discs in the set. */
    val discCount: Int? = null,
    val year: Int? = null,
    /**
     * The full release date, when the file names more than the year
     * ("2026-10-02" or "2026-10"); null otherwise. Only the album sort uses it.
     */
    val releaseDate: String? = null,
    /** Container/codec name from [com.mpaulrenlund.audiofool.core.library.AudioFormats], e.g. "FLAC", "DSD". */
    val kind: String = "",
    val bitrate: Int? = null,
    /** Source bit depth. Null for lossy codecs, which decode to float and have none. */
    val bitDepth: Int? = null,
    val sampleRate: Int? = null,
    /** A cover image sitting next to the file, if one was found. */
    val folderArtPath: String? = null,
) {
    /**
     * What albums sort on: the release date, or the year alone. ISO dates sort
     * correctly as plain text, and a bare "2026" sorts before "2026-10-02".
     */
    val sortDate: String? get() = releaseDate ?: year?.toString()?.padStart(4, '0')

    /** The name the artist list groups and sorts on: album artist when tagged, else the track artist. */
    val groupingArtist: String get() = if (albumArtist.isBlank()) artist else albumArtist

    /** Display title, falling back to the file name for untagged files. */
    val displayTitle: String get() = if (title.isBlank()) Paths.fileNameWithoutExtension(filePath) else title

    /** A copy pointing at a new location, with everything else carried over. */
    fun relocated(filePath: String, folderArtPath: String?): Track =
        copy(filePath = filePath, folderArtPath = folderArtPath)

    /**
     * True when the file on disk still matches what was cached. Size and write
     * time together are enough: a tag edit rewrites the file and moves both.
     */
    fun matchesFile(length: Long, modifiedUtc: Long): Boolean =
        fileSize == length && this.modifiedUtc == modifiedUtc

    override fun toString(): String = "$trackNumber. $displayTitle"
}
