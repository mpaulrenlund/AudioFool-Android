package com.mpaulrenlund.audiofool.core.lyrics

import com.mpaulrenlund.audiofool.core.genius.GeniusMatch
import com.mpaulrenlund.audiofool.core.scrobbling.ListeningMatch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** What LRCLIB has for a song: its lyrics, or none; [instrumental] when LRCLIB says it has no words. */
data class OnlineFind(val lyrics: Lyrics?, val instrumental: Boolean = false) {
    companion object {
        val None = OnlineFind(null)
    }
}

/**
 * Which of LRCLIB's songs is the library's: the artist and the title compared
 * loosely, as the Genius screen compares them ([GeniusMatch]: case, accents,
 * punctuation, "&"/"and", a leading "The", a title's "(Remastered)"), and the
 * length within [DURATION_SLACK_SECONDS] where both are known, so a live or
 * extended version isn't taken for the album's.
 */
object LrcLibMatch {
    const val DURATION_SLACK_SECONDS = 3.0

    /** The best of [records] that is this song: timed lyrics over plain ones, plain over an instrumental's none. */
    fun pick(records: List<LrcLibRecord>, artist: String?, albumArtist: String?, title: String?, durationMs: Long): LrcLibRecord? {
        val ours = (GeniusMatch.artistKeys(artist) + GeniusMatch.artistKeys(albumArtist)).toHashSet()
        val titles = titleKeys(title).toHashSet()
        if (ours.isEmpty() || titles.isEmpty()) return null

        val seconds = durationMs / 1000.0
        val matches = records.filter { r ->
            GeniusMatch.artistKeys(r.artist).any(ours::contains) &&
                titleKeys(r.title).any(titles::contains) &&
                (seconds <= 0 || r.durationSeconds <= 0 || abs(r.durationSeconds - seconds) <= DURATION_SLACK_SECONDS)
        }
        return matches.firstOrNull { it.synced != null }
            ?: matches.firstOrNull { it.plain != null }
            ?: matches.firstOrNull { it.instrumental }
    }

    private fun titleKeys(title: String?): List<String> {
        val whole = ListeningMatch.key(title)
        if (whole.isEmpty()) return emptyList()
        val clean = ListeningMatch.key(GeniusMatch.cleanTitle(title))
        return if (clean.isNotEmpty() && clean != whole) listOf(whole, clean) else listOf(whole)
    }
}

/**
 * Finds a library song's lyrics on LRCLIB, keeping what it found (or that it found
 * nothing) until AudioFool closes, so the same song again asks LRCLIB nothing.
 * Nothing is saved to disk, and a failed request is never kept: the next try asks
 * again. As [com.mpaulrenlund.audiofool.core.genius.GeniusLookup] keeps Genius's.
 */
class OnlineLyrics(private val client: LrcLibClient) {
    private val found = ConcurrentHashMap<String, OnlineFind>()

    /**
     * The song's lyrics: LRCLIB's song under exactly these names and this length
     * first; when that has no timed lyrics (or there is none), LRCLIB's search by
     * artist and title, its best match ([LrcLibMatch]). A song with no title or no
     * artist isn't looked up.
     */
    suspend fun find(artist: String?, albumArtist: String?, title: String?, album: String?, durationMs: Long): OnlineFind {
        val searchArtist = GeniusMatch.searchArtist(if (artist.isNullOrBlank()) albumArtist else artist)
        val cleanTitle = GeniusMatch.cleanTitle(title)
        if (searchArtist.isEmpty() || cleanTitle.isEmpty()) return OnlineFind.None

        val key = listOf(artist, albumArtist, title, album).joinToString("\n") { ListeningMatch.key(it) } + "\n${durationMs / 1000}"
        found[key]?.let { return it }

        val exact = if (!album.isNullOrBlank() && durationMs > 0) {
            client.get(artist?.takeIf { it.isNotBlank() } ?: searchArtist, (title ?: "").trim(), album.trim(), Math.round(durationMs / 1000.0))
        } else null
        val best = if (exact?.synced != null) exact else {
            val searched = LrcLibMatch.pick(client.search(searchArtist, cleanTitle), artist, albumArtist, title, durationMs)
            when {
                searched?.synced != null -> searched
                exact != null && (exact.plain != null || exact.instrumental) -> exact
                else -> searched ?: exact
            }
        }

        val result = best?.let(::toFind) ?: OnlineFind.None
        found[key] = result
        return result
    }

    private fun toFind(r: LrcLibRecord): OnlineFind {
        val lyrics = r.synced?.let { Lrc.parse(it, LyricsSource.LrcLib) }?.takeIf { it.synced }
            ?: r.plain?.let { Lrc.parse(it, LyricsSource.LrcLib) }
        return OnlineFind(lyrics, instrumental = lyrics == null && r.instrumental)
    }
}
