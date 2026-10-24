package com.mpaulrenlund.audiofool.core.model

/**
 * One row in the artist list, holding that artist's albums already ordered
 * oldest to newest. Ported from the desktop's `AudioFool.Core/Models/ArtistGroup.cs`.
 */
class ArtistGroup(
    /** The name as displayed, articles intact ("The Beatles"). */
    val name: String,
    /** The name as sorted, leading article stripped ("Beatles"). */
    val sortKey: String,
    val albums: List<Album> = emptyList(),
) {
    val trackCount: Int get() = albums.sumOf { it.tracks.size }

    /**
     * When the artist's newest file arrived. What "recently added" sorts on;
     * [Long.MIN_VALUE] when no track has a date yet.
     */
    val lastAddedUtc: Long
        get() = albums.asSequence().flatMap { it.tracks }.map { it.addedUtc ?: Long.MIN_VALUE }.maxOrNull() ?: Long.MIN_VALUE

    /** The space the listed albums take; what the size order sorts on. */
    val totalBytes: Long get() = albums.sumOf { a -> a.tracks.sumOf { it.fileSize } }

    /**
     * The newest album, by the desktop's release-date rules (spec 4.1: the
     * Artists row shows its art). [albums] is already oldest first, with
     * undated albums last, so it is the last dated album, else the last one.
     */
    val newestAlbum: Album? get() = albums.lastOrNull { it.sortDate != null } ?: albums.lastOrNull()

    /** "1 album" / "3 albums", under the name on the Artists screen (spec 4.1). */
    val albumCountDisplay: String get() = if (albums.size == 1) "1 album" else "%,d albums".format(albums.size)

    override fun toString(): String = name
}
