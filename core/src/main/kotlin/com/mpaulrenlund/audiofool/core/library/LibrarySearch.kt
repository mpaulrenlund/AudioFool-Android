package com.mpaulrenlund.audiofool.core.library

import com.mpaulrenlund.audiofool.core.model.Track
import java.text.Normalizer
import java.util.Locale

/**
 * Free-text search over the library. Ported from the desktop's
 * `AudioFool.Core/Library/LibrarySearch.cs`, with its tests.
 *
 * One rule does all three levels: a track matches when every term of the query
 * is found in its title, artist, album artist or album. Albums and artists are
 * then shown when any of their tracks match, which falls out of rebuilding the
 * tree from the filtered tracks.
 *
 * Matching ignores case and accents (the desktop's
 * `CompareOptions.IgnoreNonSpace | IgnoreCase`): "bjork" finds "Björk", and
 * "Beyoncé" finds "Beyonce".
 */
object LibrarySearch {
    private val termSeparators = Regex("[ \t]+")

    /**
     * Splits a query into terms. Every term has to match somewhere for the track
     * to count, so "floyd dark" finds Dark Side of the Moon even though no single
     * field contains that whole phrase.
     */
    fun terms(query: String?): List<String> =
        if (query.isNullOrBlank()) emptyList()
        else query.trim().split(termSeparators).filter { it.isNotEmpty() }.map(::fold)

    fun matches(track: Track, terms: List<String>): Boolean =
        terms.all { term -> fields(track).any { fold(it).contains(term) } }

    /**
     * The matching subset of [tracks]. An empty query returns everything, so
     * callers don't need to special-case "not searching".
     */
    fun filter(tracks: List<Track>, query: String?): List<Track> {
        val terms = terms(query)
        if (terms.isEmpty()) return tracks
        return tracks.filter { matches(it, terms) }
    }

    /**
     * Case and accents folded away: decomposed, combining marks dropped, lower
     * case. "Björk" and "BJORK" both become "bjork".
     */
    fun fold(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        val sb = StringBuilder(decomposed.length)
        for (c in decomposed) {
            if (Character.getType(c) != Character.NON_SPACING_MARK.toInt()) sb.append(c)
        }
        return sb.toString().lowercase(Locale.ROOT)
    }

    private fun fields(track: Track) = sequenceOf(track.displayTitle, track.artist, track.albumArtist, track.album)

    /**
     * The same search over a library that is searched many times, as Search
     * does while you type: each track's fields are folded once, up front.
     */
    class Index(val tracks: List<Track>) {
        private val folded: Array<String> = Array(tracks.size) { i ->
            fields(tracks[i]).filter { it.isNotEmpty() }.joinToString("\u0000") { fold(it) }
        }

        fun filter(query: String?): List<Track> {
            val terms = terms(query)
            if (terms.isEmpty()) return tracks
            val result = ArrayList<Track>()
            for (i in tracks.indices) {
                // The separator can't be in a term, so a term never spans two fields.
                if (terms.all { folded[i].contains(it) }) result.add(tracks[i])
            }
            return result
        }
    }
}
