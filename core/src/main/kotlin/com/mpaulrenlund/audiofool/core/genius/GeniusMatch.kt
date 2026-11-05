package com.mpaulrenlund.audiofool.core.genius

import com.mpaulrenlund.audiofool.core.scrobbling.ListeningMatch

/**
 * Decides which Genius page is a library song. Genius's search is strict
 * about extras ("Paranoid Android (Remastered)" finds nothing), and it ranks
 * its own translation pages next to the song, so:
 * - the title is searched without extras such as "(Remastered)",
 *   "- 2011 Remaster", "(Live)" or "feat. X" ([cleanTitle]);
 * - a result counts only when its artist is the song's artist and its title is
 *   the song's title, both compared loosely: case, accents, punctuation,
 *   "&"/"and" and a leading "The" don't count, and extras are left out on both
 *   sides.
 *
 * Nothing else is guessed: with no such result the screen offers the nearest
 * ones instead ([suggestions]).
 *
 * Ported from the desktop's `AudioFool.Core/Genius/GeniusMatch.cs` (session 75).
 */
object GeniusMatch {
    /** How many near results are offered when nothing matches. */
    const val SUGGESTION_COUNT = 5

    /**
     * Words that mark a trailing "(…)", "[…]" or " - …" as an extra rather than
     * part of the name. A four-digit year counts too. "Part 2" doesn't, so
     * "Song (Part 1)" and "Song (Part 2)" stay apart.
     */
    private val extraWords = setOf(
        "remaster", "remastered", "remasters", "remix", "remixed", "mix", "live", "mono", "stereo",
        "version", "edit", "demo", "bonus", "deluxe", "edition", "feat", "ft", "featuring", "with",
        "acoustic", "instrumental", "single", "explicit", "clean", "radio", "anniversary", "take",
        "session", "sessions", "rerecorded", "recorded", "extended", "original", "unplugged",
        "outtake", "alternate", "early", "reissue", "expanded", "bbc", "mtv",
        // "(from Ookiku Furikabutte)", "- From "Frozen"": where a soundtrack song comes from.
        "from",
    )

    private val featuringTail = Regex("""\s+(?:feat\.?|ft\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE)
    private val dashTail = Regex("""^(?<name>.+?)\s+[-–—]\s+(?<extra>[^-–—]+)$""")
    private val bracketTail = Regex("""^(?<name>.+?)\s*[(\[](?<extra>[^()\[\]]*)[)\]]$""")
    private val featuring = Regex("""\s+(?:feat\.?|ft\.?|featuring)\s+""", RegexOption.IGNORE_CASE)
    private val artistJoin = Regex("""\s*(?:&|,|;|/|\+|\bx\b|\band\b|\bwith\b|\bvs\.?)\s*""", RegexOption.IGNORE_CASE)
    private val wordBreaks = charArrayOf(' ', '.', ',', '-', '/', '\'', '’', '"', ':', '!')

    /** The title without trailing extras: "Paranoid Android (Remastered)" → "Paranoid Android". */
    fun cleanTitle(title: String?): String {
        var s = (title ?: "").trim()
        while (true) {
            val before = s
            s = featuringTail.replace(s, "").trim()

            val bracket = bracketTail.find(s)?.takeIf { isExtra(it.groups["extra"]!!.value) }
            val tail = bracket ?: dashTail.find(s)?.takeIf { isExtra(it.groups["extra"]!!.value) }
            if (tail != null) s = tail.groups["name"]!!.value.trim()

            if (s == before || s.isEmpty()) return if (s.isEmpty()) before else s
        }
    }

    /** The artist to search with: the first one named, before any "feat.". */
    fun searchArtist(artist: String?): String {
        val a = (artist ?: "").trim()
        val feat = featuring.find(a)
        return if (feat != null) a.substring(0, feat.range.first).trim() else a
    }

    /** The first page in [hits] that is this song, or null. */
    fun pick(hits: Iterable<GeniusHit>, artist: String?, albumArtist: String?, title: String?): GeniusHit? {
        val ours = (artistKeys(artist) + artistKeys(albumArtist)).toHashSet()
        val titles = titleKeys(title).toHashSet()
        if (ours.isEmpty() || titles.isEmpty()) return null

        return hits.firstOrNull { h ->
            !isGeniusOwnPage(h) && artistKeys(h.artist).any(ours::contains) && titleKeys(h.title).any(titles::contains)
        }
    }

    /** The nearest results to offer when none is the song: Genius's own translation pages left out, no repeats. */
    fun suggestions(hits: Iterable<GeniusHit>): List<GeniusHit> =
        hits.filter { !isGeniusOwnPage(it) }.distinctBy { it.id }.take(SUGGESTION_COUNT)

    /**
     * Pages Genius's own accounts publish, "Genius English Translations",
     * "Genius Romanizations" and the like: never the song itself.
     */
    fun isGeniusOwnPage(hit: GeniusHit): Boolean = hit.artist.startsWith("Genius ", ignoreCase = true)

    /**
     * The keys an artist name answers to: the whole name, and each artist in a
     * joint credit ("A & B", "A feat. B", "A x B"), each with and without a
     * leading "The".
     */
    fun artistKeys(artist: String?): List<String> {
        if (artist.isNullOrBlank()) return emptyList()

        val main = searchArtist(artist)
        val parts = buildList {
            add(artist)
            add(main)
            addAll(featuring.split(artist))
            addAll(artistJoin.split(main))
        }

        return buildList {
            for (part in parts) {
                val key = ListeningMatch.key(part)
                if (key.isEmpty()) continue
                add(key)
                if (key.startsWith("the") && key.length > 3 && part.trimStart().startsWith("the ", ignoreCase = true)) {
                    add(key.substring(3))
                }
            }
        }
    }

    private fun titleKeys(title: String?): List<String> {
        val whole = ListeningMatch.key(title)
        if (whole.isEmpty()) return emptyList()

        val clean = ListeningMatch.key(cleanTitle(title))
        return if (clean.isNotEmpty() && clean != whole) listOf(whole, clean) else listOf(whole)
    }

    private fun isExtra(text: String): Boolean {
        for (word in text.split(*wordBreaks)) {
            if (word.isEmpty()) continue
            if (extraWords.contains(word.lowercase())) return true
            if (word.length == 4 && word.all { it in '0'..'9' } && (word[0] == '1' || word[0] == '2')) return true
        }
        return false
    }
}
