package com.mpaulrenlund.audiofool.core.genius

import java.time.Instant

// What genius.com has on a song, as the Genius screen shows it. Ported from the
// desktop's AudioFool.Core/Genius/GeniusModels.cs (session 75).

/** Genius couldn't be reached, or answered with something other than a page. */
class GeniusException(message: String) : Exception(message)

/** One song as a search or a connection lists it: enough to show it and to load it. */
data class GeniusHit(val id: Long, val title: String, val artist: String, val url: String)

/** A credit line: "Produced by" and who. */
data class GeniusCredit(val label: String, val names: List<String>)

/** One kind of connection ("Samples", "Covered by") and the songs Genius lists for it. */
data class GeniusConnection(val label: String, val songs: List<GeniusHit>)

/**
 * A song's Genius page, less the lyrics (which Genius never hands out). The
 * write-up is null when the page has none; Genius sends "?" for that.
 */
data class GeniusSong(
    val id: Long,
    val title: String,
    val artist: String,
    val url: String,
    val about: String?,
    val album: String?,
    val releaseDate: String?,
    val credits: List<GeniusCredit>,
    val connections: List<GeniusConnection>,
)

/** An explanation of a lyric line. Verified when the artist wrote it. */
data class GeniusAnnotation(val fragment: String, val body: String, val verified: Boolean)

/** A question on the page and its answer. Unanswered ones aren't kept. */
data class GeniusAnswer(val question: String, val answer: String)

data class GeniusComment(val author: String, val posted: Instant?, val votes: Int, val body: String)

/** One page of comments, most-voted first; [nextPage] is null after the last. */
data class GeniusCommentPage(val comments: List<GeniusComment>, val total: Int, val nextPage: Int?) {
    companion object {
        val Empty = GeniusCommentPage(emptyList(), 0, null)
    }
}

/** Everything the Genius screen shows for one song, fetched together. */
data class GeniusPage(
    val song: GeniusSong,
    val answers: List<GeniusAnswer>,
    val annotations: List<GeniusAnnotation>,
    val comments: GeniusCommentPage,
)

/**
 * What a search for a library song found: the page that is that song, or
 * none and up to five near results to choose from instead.
 */
data class GeniusFind(val match: GeniusHit?, val suggestions: List<GeniusHit>)

/** A loaded page, and which of its optional parts couldn't be fetched. */
data class GeniusPageResult(val page: GeniusPage, val missing: List<String>)
