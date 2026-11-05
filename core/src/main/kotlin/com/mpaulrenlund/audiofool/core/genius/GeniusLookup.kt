package com.mpaulrenlund.audiofool.core.genius

import com.mpaulrenlund.audiofool.core.scrobbling.ListeningMatch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds a library song's Genius page and fetches what the Genius screen shows,
 * keeping both until AudioFool closes, so opening the same song again asks
 * Genius nothing. Nothing is saved to disk, and a failed request is never kept:
 * the next try asks again.
 *
 * Ported from the desktop's `AudioFool.Core/Genius/GeniusLookup.cs` (session 75).
 */
class GeniusLookup(val client: GeniusClient) {
    private val finds = ConcurrentHashMap<String, GeniusFind>()
    private val pages = ConcurrentHashMap<Long, GeniusPage>()

    /**
     * The page that is this song: searched as "artist title", then by the title
     * alone (which finds songs the artist search buries). With no page that is
     * the song, the nearest results of both searches, to choose from.
     */
    suspend fun find(artist: String?, albumArtist: String?, title: String?): GeniusFind {
        val mainArtist = GeniusMatch.searchArtist(if (artist.isNullOrBlank()) albumArtist else artist)
        val cleanTitle = GeniusMatch.cleanTitle(title)
        if (cleanTitle.isEmpty()) return GeniusFind(null, emptyList())

        val key = "${ListeningMatch.key(artist)}\n${ListeningMatch.key(albumArtist)}\n${ListeningMatch.key(title)}"
        finds[key]?.let { return it }

        val seen = ArrayList<GeniusHit>()
        val queries = if (mainArtist.isNotEmpty()) listOf("$mainArtist $cleanTitle", cleanTitle) else listOf(cleanTitle)
        var match: GeniusHit? = null
        for (query in queries) {
            val hits = client.search(query, SEARCH_COUNT)
            seen.addAll(hits)
            match = GeniusMatch.pick(hits, artist, albumArtist, title)
            if (match != null) break
        }

        val find = GeniusFind(match, if (match == null) GeniusMatch.suggestions(seen) else emptyList())
        finds[key] = find
        return find
    }

    /**
     * The song's page, its answered questions, its explanations and the first
     * page of comments, asked for together. Only the song is required: if one of
     * the other three fails, the page comes back without it, named in
     * [GeniusPageResult.missing], and isn't kept.
     */
    suspend fun load(id: Long): GeniusPageResult {
        pages[id]?.let { return GeniusPageResult(it, emptyList()) }

        val missing = ArrayList<String>()
        val page = coroutineScope {
            suspend fun <T> optional(name: String, empty: T, fetch: suspend () -> T): T = try {
                fetch()
            } catch (e: GeniusException) {
                synchronized(missing) { missing.add(name) }
                empty
            }

            val song = async { client.getSong(id) }
            val answers = async { optional(QA, emptyList()) { client.getAnswers(id) } }
            val annotations = async { optional(ANNOTATIONS, emptyList()) { client.getAnnotations(id) } }
            val comments = async { optional(COMMENTS, GeniusCommentPage.Empty) { client.getComments(id, 1) } }
            GeniusPage(song.await(), answers.await(), annotations.await(), comments.await())
        }

        val missed = listOf(QA, ANNOTATIONS, COMMENTS).filter { it in missing }
        if (missed.isEmpty()) pages[id] = page
        return GeniusPageResult(page, missed)
    }

    companion object {
        /** Results asked for per search. */
        const val SEARCH_COUNT = 10

        private const val QA = "questions and answers"
        private const val ANNOTATIONS = "annotations"
        private const val COMMENTS = "comments"
    }
}
