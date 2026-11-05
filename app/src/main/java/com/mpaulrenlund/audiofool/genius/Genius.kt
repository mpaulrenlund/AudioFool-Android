package com.mpaulrenlund.audiofool.genius

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mpaulrenlund.audiofool.core.format.Display
import com.mpaulrenlund.audiofool.core.genius.GeniusClient
import com.mpaulrenlund.audiofool.core.genius.GeniusComment
import com.mpaulrenlund.audiofool.core.genius.GeniusException
import com.mpaulrenlund.audiofool.core.genius.GeniusHit
import com.mpaulrenlund.audiofool.core.genius.GeniusLookup
import com.mpaulrenlund.audiofool.core.genius.GeniusPageResult
import com.mpaulrenlund.audiofool.core.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A question and its answer, an explanation under the lyric line it explains
 * ([isQuote]), or a comment under its author. [figure] is the right-hand note:
 * "Verified", or a comment's date and votes. The desktop's `GeniusEntry`.
 */
data class GeniusEntry(val heading: String, val body: String, val figure: String?, val isQuote: Boolean)

/** A credit or connection line: the label, then who or what (several lines for connections). */
data class GeniusCreditRow(val label: String, val value: String)

/**
 * The line in place of the page, or over it: looking it up, no match, why it couldn't,
 * or which parts Genius didn't send. [detail] goes under it; [retry] makes a tap try again.
 */
data class GeniusNote(val text: String, val detail: String? = null, val retry: Boolean = false)

/** What the Genius screen draws. The header is the library's own song; everything under it is the Genius page. */
data class GeniusState(
    val track: Track? = null,
    val note: GeniusNote? = null,
    /** With no match, Genius's nearest results; choosing one loads it. */
    val suggestions: List<GeniusHit> = emptyList(),
    /** The Genius page's address once one is loaded; the header's link opens it. */
    val pageUrl: String? = null,
    val about: String? = null,
    val answers: List<GeniusEntry> = emptyList(),
    val annotations: List<GeniusEntry> = emptyList(),
    val credits: List<GeniusCreditRow> = emptyList(),
    val connections: List<GeniusCreditRow> = emptyList(),
    val comments: List<GeniusEntry> = emptyList(),
    /** "34 comments", at the right of the section's header. */
    val commentCount: String = "",
    /** "Show 50 more" while Genius has more comments; null after the last. */
    val moreComments: String? = null,
)

/**
 * How Genius's page becomes the screen's rows: the desktop's `GeniusViewModel`
 * (session 75), its wording ported unchanged.
 */
object GeniusRows {
    /** How many songs of one kind of connection are named before "and 7 more". */
    const val CONNECTIONS_SHOWN = 5

    const val LOOKING_UP = "Looking up on Genius…"
    const val NO_MATCH = "Genius has no page that matches this song."
    const val NO_TITLE = "This song has no title to look up on Genius."
    const val EMPTY_PAGE = "Genius's page for this song has nothing on it yet."
    const val MORE_FAILED = "Couldn't load more comments. Try again"

    /** In place of "Show 50 more" while they load: the phone's, as a tap shows nothing else for a second or two. */
    const val LOADING_MORE = "Loading…"

    /** The phone's addition: a tap on the line tries again, as Listening History's does. */
    const val TAP_TO_RETRY = "Tap to try again."

    /** The page as the screen shows it, under [track]'s header. */
    fun page(track: Track?, result: GeniusPageResult, zone: ZoneId): GeniusState {
        val page = result.page
        val song = page.song
        val comments = page.comments.comments.map { comment(it, zone) }
        val total = maxOf(page.comments.total, comments.size)
        val state = GeniusState(
            track = track,
            pageUrl = song.url.ifEmpty { null },
            about = song.about,
            answers = page.answers.map { GeniusEntry(it.question, it.answer, null, false) },
            annotations = page.annotations.map { GeniusEntry(it.fragment, it.body, if (it.verified) "Verified" else null, true) },
            credits = song.credits.map { GeniusCreditRow(it.label, it.names.joinToString(", ")) },
            connections = song.connections.map { GeniusCreditRow(it.label, connectionText(it.songs)) },
            comments = comments,
            commentCount = commentCount(total),
            moreComments = moreText(page.comments.nextPage, total, comments.size),
        )
        val note = when {
            result.missing.isNotEmpty() -> GeniusNote("Genius didn't send the ${joinNames(result.missing)} this time.", TAP_TO_RETRY, retry = true)
            state.isEmpty() -> GeniusNote(EMPTY_PAGE)
            else -> null
        }
        return state.copy(note = note)
    }

    fun comment(c: GeniusComment, zone: ZoneId): GeniusEntry {
        val votes = if (c.votes == 1 || c.votes == -1) "${c.votes} vote" else "${Display.count(c.votes)} votes"
        val figure = c.posted?.let { "${DATE.format(it.atZone(zone))} · $votes" } ?: votes
        return GeniusEntry(c.author, c.body, figure, false)
    }

    fun commentCount(total: Int): String = if (total == 1) "1 comment" else "${Display.count(total)} comments"

    /** One song per line, "Title · Artist", the first five, then "and 7 more". */
    fun connectionText(songs: List<GeniusHit>): String {
        val lines = songs.take(CONNECTIONS_SHOWN).map { if (it.artist.isNotEmpty()) "${it.title} · ${it.artist}" else it.title }.toMutableList()
        if (songs.size > CONNECTIONS_SHOWN) lines.add("and ${Display.count(songs.size - CONNECTIONS_SHOWN)} more")
        return lines.joinToString("\n")
    }

    /** "Show 50 more" (or fewer, near the end) while Genius has another page; null after the last. */
    fun moreText(nextPage: Int?, total: Int, shown: Int): String? {
        if (nextPage == null) return null
        val left = total - shown
        val next = if (left > 0) minOf(GeniusClient.COMMENTS_PER_PAGE, left) else GeniusClient.COMMENTS_PER_PAGE
        return "Show ${Display.count(next)} more"
    }

    fun joinNames(names: List<String>): String = when (names.size) {
        1 -> names[0]
        2 -> "${names[0]} or the ${names[1]}"
        else -> names.dropLast(1).joinToString(", the ") + " or the ${names.last()}"
    }

    /** "Couldn't load the Genius page." and why, as Genius or the network said it. */
    fun failed(e: GeniusException): GeniusNote =
        GeniusNote("Couldn't load the Genius page.", "${(e.message ?: "").trimEnd('.')}. $TAP_TO_RETRY".trimStart('.', ' '), retry = true)

    /** The song's own header line under the artist: "Album · Year", from its tags. */
    fun detailLine(track: Track): String? =
        listOf(track.album, track.year?.toString() ?: "").map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null }

    private fun GeniusState.isEmpty() =
        about == null && answers.isEmpty() && annotations.isEmpty() && credits.isEmpty() && connections.isEmpty() && comments.isEmpty()

    /** "Mar 7, 2017", as the desktop writes a comment's date. */
    private val DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
}

/**
 * The Genius screen (spec 5.2): what genius.com has on one library song, from Now
 * Playing's cover menu. Ported from the desktop's `GeniusViewModel` (session 75).
 * Read-only; nothing is saved.
 *
 * Held by the app, so its [GeniusLookup] keeps what it found for the run, as the
 * desktop's does: the same song again asks Genius nothing. One song at a time:
 * [show] again switches to another, dropping whatever the last one still waited
 * for. Main thread only.
 */
@Stable
class Genius(private val lookup: GeniusLookup, private val zone: () -> ZoneId = ZoneId::systemDefault) {
    private val scope = MainScope()
    private var job: Job? = null
    private var more: Job? = null
    private var songId: Long? = null
    private var nextCommentPage: Int? = null
    private var commentTotal = 0
    /** What a tap on a failure note does again: the lookup, or loading the chosen result. */
    private var again: (() -> Unit)? = null

    var state: GeniusState by mutableStateOf(GeniusState())
        private set

    /**
     * Looks the song up and fills the screen: the page that is the song, or the
     * nearest results when none is. Opened from the cover menu each time, as the
     * desktop's Ctrl+G is: a song already found shows at once.
     */
    fun show(track: Track) {
        restart()
        again = { show(track) }
        if (track.title.isBlank()) {
            state = GeniusState(track = track, note = GeniusNote(GeniusRows.NO_TITLE))
            return
        }

        state = GeniusState(track = track, note = GeniusNote(GeniusRows.LOOKING_UP))
        job = scope.launch {
            try {
                val find = lookup.find(track.artist, track.albumArtist, track.title)
                val match = find.match
                if (match != null) {
                    loadPage(track, match.id)
                } else {
                    state = GeniusState(track = track, note = GeniusNote(GeniusRows.NO_MATCH), suggestions = find.suggestions)
                }
            } catch (e: GeniusException) {
                state = GeniusState(track = track, note = GeniusRows.failed(e))
            }
        }
    }

    /** The screen is showing [track]: looks it up unless it is the one shown (after Android restored the screen, say). */
    fun showing(track: Track) {
        if (state.track?.filePath != track.filePath) show(track)
    }

    /** One of the nearest results, chosen: its page fills the screen under the same header. */
    fun choose(hit: GeniusHit) {
        val track = state.track
        restart()
        again = { choose(hit) }
        state = GeniusState(track = track, note = GeniusNote(GeniusRows.LOOKING_UP))
        job = scope.launch {
            try {
                loadPage(track, hit.id)
            } catch (e: GeniusException) {
                state = GeniusState(track = track, note = GeniusRows.failed(e))
            }
        }
    }

    /** A tap on a note that offers it: whatever failed, asked again. */
    fun retry() {
        if (state.note?.retry == true) again?.invoke()
    }

    /** The next page of comments, added under the ones shown. */
    fun showMoreComments() {
        val id = songId ?: return
        val next = nextCommentPage ?: return
        if (more?.isActive == true) return

        state = state.copy(moreComments = GeniusRows.LOADING_MORE)
        more = scope.launch {
            try {
                val page = lookup.client.getComments(id, next)
                if (songId != id) return@launch
                val comments = state.comments + page.comments.map { GeniusRows.comment(it, zone()) }
                nextCommentPage = if (page.comments.isNotEmpty()) page.nextPage else null
                state = state.copy(comments = comments, moreComments = GeniusRows.moreText(nextCommentPage, commentTotal, comments.size))
            } catch (e: CancellationException) {
                throw e
            } catch (e: GeniusException) {
                if (songId == id) state = state.copy(moreComments = GeniusRows.MORE_FAILED)
            }
        }
    }

    private suspend fun loadPage(track: Track?, id: Long) {
        val result = lookup.load(id)
        songId = result.page.song.id
        nextCommentPage = result.page.comments.nextPage
        commentTotal = maxOf(result.page.comments.total, result.page.comments.comments.size)
        state = GeniusRows.page(track, result, zone())
    }

    private fun restart() {
        job?.cancel()
        more?.cancel()
        job = null
        more = null
        songId = null
        nextCommentPage = null
        commentTotal = 0
    }
}
