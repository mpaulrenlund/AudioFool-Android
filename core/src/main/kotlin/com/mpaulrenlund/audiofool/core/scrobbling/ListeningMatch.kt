package com.mpaulrenlund.audiofool.core.scrobbling

import com.mpaulrenlund.audiofool.core.model.Track
import java.text.Normalizer

/**
 * Finds the library's copy of an artist, album or song Last.fm names, so a
 * Listening History row can go to it. Last.fm's names came from tags (this app's
 * and other players'), sometimes corrected by Last.fm, so they are compared
 * loosely: case, accents, punctuation and "&"/"and" don't count. A trailing
 * "(Remastered)" or "[Deluxe Edition]" on an album or song title is tried
 * without, too. Nothing is a guess beyond that: no match, no row to tap.
 *
 * Ported from the desktop's `AudioFool.Core/Scrobbling/ListeningMatch.cs` (session 74).
 */
class ListeningMatch(tracks: Iterable<Track>) {
    private val artists = HashMap<String, Track>()
    private val albums = HashMap<String, Track>()
    private val songs = HashMap<String, Track>()

    init {
        val all = tracks.toList()

        // Every artist the list groups on before any track artist, so an artist row
        // goes to their own entry rather than a compilation they appear on.
        for (t in all) index(artists, key(t.groupingArtist), t)
        for (t in all) index(artists, key(t.artist), t)

        for (t in all) {
            for (artist in listOf(t.groupingArtist, t.artist)) {
                val a = key(artist)
                if (a.isEmpty()) continue

                for (album in variants(t.album)) index(albums, "$a\n$album", t)
                for (title in variants(t.title)) index(songs, "$a\n$title", t)
            }
        }
    }

    /** A track by the artist, preferring one they are the album artist of. */
    fun artist(name: String): Track? = artists[key(name)]

    fun album(artist: String, album: String): Track? = find(albums, artist, album)

    fun song(artist: String, title: String): Track? = find(songs, artist, title)

    companion object {
        val Empty = ListeningMatch(emptyList())

        /**
         * Lower case, accents dropped, "&" read as "and", and only letters and digits
         * kept: "Beyoncé & Jay-Z" and "beyonce and jay z" are one key.
         */
        fun key(name: String?): String {
            if (name.isNullOrBlank()) return ""

            val text = Normalizer.normalize(name.replace("&", " and "), Normalizer.Form.NFD)
            val key = StringBuilder(text.length)
            for (c in text) {
                if (Character.getType(c) == Character.NON_SPACING_MARK.toInt()) continue
                if (Character.isLetterOrDigit(c)) key.append(Character.toLowerCase(c))
            }
            return key.toString()
        }

        private fun find(index: Map<String, Track>, artist: String, name: String): Track? {
            val a = key(artist)
            if (a.isEmpty()) return null

            for (n in variants(name)) index["$a\n$n"]?.let { return it }
            return null
        }

        private fun index(index: HashMap<String, Track>, key: String, track: Track) {
            if (key.isNotEmpty()) index.putIfAbsent(key, track)
        }

        /** The title as it is, then without a trailing bracketed part. */
        private fun variants(title: String?): List<String> {
            val whole = key(title)
            if (whole.isEmpty()) return emptyList()

            val bare = key(stripTrailingBrackets(title!!))
            return if (bare.isNotEmpty() && bare != whole) listOf(whole, bare) else listOf(whole)
        }

        private fun stripTrailingBrackets(title: String): String {
            var s = title.trimEnd()
            while (s.isNotEmpty() && (s.last() == ')' || s.last() == ']')) {
                val open = s.lastIndexOf(if (s.last() == ')') '(' else '[')
                if (open <= 0) break
                s = s.substring(0, open).trimEnd()
            }
            return s
        }
    }
}
