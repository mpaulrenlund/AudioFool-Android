package com.mpaulrenlund.audiofool.core.library

import com.mpaulrenlund.audiofool.core.model.Album
import com.mpaulrenlund.audiofool.core.model.ArtistGroup
import com.mpaulrenlund.audiofool.core.model.Track
import java.text.CollationKey
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** How the Artists list is ordered. Ported from the desktop's `ArtistOrder.cs`. */
enum class ArtistOrder {
    /** A-Z, ignoring a leading article. What the app opens on. */
    Name,

    /** Most recently added file first. */
    Recent,

    /** Most space taken first. */
    Size,
}

/**
 * The three ordering rules the app is built around:
 *   artists alphabetically, ignoring a leading article;
 *   albums oldest first;
 *   tracks by disc then track number.
 * Ported from the desktop's `AudioFool.Core/Library/SortRules.cs`, with its tests.
 */
object SortRules {
    /**
     * Articles ignored when sorting artist names. "The Beatles" files under B.
     * Add "A" and "An" here if you ever want those ignored too.
     */
    val defaultLeadingArticles: List<String> = listOf("The")

    /**
     * The desktop's `StringComparer.InvariantCultureIgnoreCase`: a root-locale
     * collation that ignores case but not accents, so ordering is the same on
     * every phone whatever its language.
     */
    val nameComparator: Comparator<String> = NameComparator

    /** Whether two names are the same under [nameComparator]. */
    fun sameName(a: String, b: String): Boolean = NameComparator.compare(a, b) == 0

    /** A key equal for names [nameComparator] calls equal, for grouping. */
    fun nameKey(name: String): CollationKey = NameComparator.key(name)

    /**
     * The string an artist actually sorts on. Strips a leading article, so
     * "The Beatles" sorts as "Beatles" while "Thelonious Monk" is untouched
     * (the whitespace after the article is what makes that work).
     */
    fun artistSortKey(name: String?, articles: List<String>? = null): String {
        if (name.isNullOrBlank()) return ""

        val trimmed = name.trim()

        for (article in articles ?: defaultLeadingArticles) {
            if (article.isEmpty()) continue

            // Require whitespace after the article: "The Beatles" matches,
            // "Thelonious" and a band literally named "The" do not.
            if (trimmed.length <= article.length) continue
            if (!trimmed.startsWith(article, ignoreCase = true)) continue
            if (!trimmed[article.length].isWhitespace()) continue

            val stripped = trimmed.substring(article.length).trimStart()

            // "The " on its own leaves nothing to sort by; keep the original.
            return stripped.ifEmpty { trimmed }
        }

        return trimmed
    }

    /** Artist groups A-Z on their precomputed sort key. */
    fun sortArtists(artists: Iterable<ArtistGroup>): List<ArtistGroup> =
        artists.sortedWith(compareBy<ArtistGroup, String>(nameComparator) { it.sortKey }
            .thenBy(nameComparator) { it.name })

    /** Artist groups by their most recently added file, newest first; ties fall back to A-Z. */
    fun sortArtistsByRecent(artists: Iterable<ArtistGroup>): List<ArtistGroup> =
        artists.map { it to it.lastAddedUtc }
            .sortedWith(compareByDescending<Pair<ArtistGroup, Long>> { it.second }
                .thenBy(nameComparator) { it.first.sortKey }
                .thenBy(nameComparator) { it.first.name })
            .map { it.first }

    /** Artist groups by the space their albums take, largest first; ties fall back to A-Z. */
    fun sortArtistsBySize(artists: Iterable<ArtistGroup>): List<ArtistGroup> =
        artists.map { it to it.totalBytes }
            .sortedWith(compareByDescending<Pair<ArtistGroup, Long>> { it.second }
                .thenBy(nameComparator) { it.first.sortKey }
                .thenBy(nameComparator) { it.first.name })
            .map { it.first }

    /** Artists in the given order. [artists] arrives A-Z, as the library builds it. */
    fun sortArtists(artists: List<ArtistGroup>, order: ArtistOrder): List<ArtistGroup> = when (order) {
        ArtistOrder.Recent -> sortArtistsByRecent(artists)
        ArtistOrder.Size -> sortArtistsBySize(artists)
        ArtistOrder.Name -> artists
    }

    /** A-Z, then Recent, then Size, then back to A-Z. */
    fun next(order: ArtistOrder): ArtistOrder = when (order) {
        ArtistOrder.Name -> ArtistOrder.Recent
        ArtistOrder.Recent -> ArtistOrder.Size
        ArtistOrder.Size -> ArtistOrder.Name
    }

    /**
     * Albums oldest at the top, newest at the bottom, by full release date where
     * the files have one: "2026" (year only) comes before "2026-10-02", and
     * "2026-03" before "2026-10-02". Albums with no year land at the bottom
     * rather than pretending to be from year zero.
     */
    fun sortAlbums(albums: Iterable<Album>): List<Album> =
        albums.sortedWith(compareBy<Album> { if (albumDate(it) == null) 1 else 0 }
            .thenBy { albumDate(it) ?: "" }          // ISO dates of differing precision sort as ordinal text
            .thenBy(nameComparator) { it.title })

    private fun albumDate(album: Album): String? = album.sortDate ?: album.year?.toString()?.padStart(4, '0')

    /**
     * Tracks in playing order: disc number, then track number. Untagged discs
     * are treated as disc 1; untagged track numbers sink to the end of their disc.
     */
    fun sortTracks(tracks: Iterable<Track>): List<Track> =
        tracks.sortedWith(compareBy<Track> { it.discNumber ?: 1 }
            .thenBy { it.trackNumber ?: Int.MAX_VALUE }
            .thenBy(nameComparator) { it.displayTitle })

    /**
     * Collation keys are cached per string: a library has a few thousand distinct
     * artist and album names among tens of thousands of tracks.
     */
    private object NameComparator : Comparator<String> {
        private val collator: Collator = Collator.getInstance(Locale.ROOT).apply {
            strength = Collator.SECONDARY
            decomposition = Collator.CANONICAL_DECOMPOSITION
        }
        private val keys = ConcurrentHashMap<String, CollationKey>()

        fun key(s: String): CollationKey = keys[s] ?: synchronized(collator) { collator.getCollationKey(s) }
            .also { if (keys.size < 200_000) keys.putIfAbsent(s, it) }

        override fun compare(a: String, b: String): Int = key(a).compareTo(key(b))
    }
}
