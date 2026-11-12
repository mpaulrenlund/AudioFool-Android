package com.mpaulrenlund.audiofool.lyrics

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mpaulrenlund.audiofool.core.lyrics.LrcFile
import com.mpaulrenlund.audiofool.core.lyrics.Lrc
import com.mpaulrenlund.audiofool.core.lyrics.Lyrics
import com.mpaulrenlund.audiofool.core.lyrics.LyricsException
import com.mpaulrenlund.audiofool.core.lyrics.LyricsSource
import com.mpaulrenlund.audiofool.core.lyrics.OnlineLyrics
import com.mpaulrenlund.audiofool.core.model.Track
import com.mpaulrenlund.audiofool.library.TagLibNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The line in place of the lyrics: looking, none, or why it couldn't. [detail] goes under it; [retry] makes a tap try again. */
data class LyricsNote(val text: String, val detail: String? = null, val retry: Boolean = false)

/** What the Lyrics screen draws: the song, and its lyrics or a note. */
data class LyricsState(val track: Track? = null, val lyrics: Lyrics? = null, val note: LyricsNote? = null)

/** The Lyrics screen's wording (spec 5.5). */
object LyricsRows {
    const val LOOKING = "Looking for lyrics…"
    const val NONE = "No lyrics found for this song."
    const val INSTRUMENTAL = "LRCLIB lists this song as an instrumental."
    const val NOTHING_PLAYING = "Nothing is playing."
    const val TAP_TO_RETRY = "Tap to try again."

    /** "Couldn't look up the lyrics." and why, as LRCLIB or the network said it. */
    fun failed(e: LyricsException): LyricsNote =
        LyricsNote("Couldn't look up the lyrics.", "${(e.message ?: "").trimEnd('.')}. $TAP_TO_RETRY".trimStart('.', ' '), retry = true)

    /** The header's second line: "Artist · Album". */
    fun subtitle(track: Track): String =
        listOf(track.artist.ifBlank { track.groupingArtist }, track.album).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ")

    /** Under the lyrics: where they came from, and whether they are timed. */
    fun source(lyrics: Lyrics): String {
        val from = when (lyrics.source) {
            LyricsSource.LrcFile -> "the song's .lrc file"
            LyricsSource.Tags -> "the song's tags"
            LyricsSource.LrcLib -> "LRCLIB"
        }
        return if (lyrics.synced) "Timed lyrics from $from" else "Lyrics from $from, without timing"
    }
}

/**
 * The song's own lyrics (spec 5.5): its .lrc file, then its tags, timed over
 * plain. Reads the files, so off the main thread. Null when it has none (or its
 * folder can't be reached: the SD card out).
 */
fun readLocalLyrics(track: Track): Lyrics? {
    val texts = ArrayList<Pair<String, LyricsSource>>()
    LrcFile.read(track.filePath)?.let { text ->
        // A timed .lrc file is the answer: the tags needn't be read.
        Lrc.parse(text, LyricsSource.LrcFile)?.takeIf { it.synced }?.let { return it }
        texts.add(text to LyricsSource.LrcFile)
    }
    TagLibNative.embeddedLyrics(track.filePath)?.forEach { texts.add(it to LyricsSource.Tags) }
    return Lrc.pick(texts)
}

/**
 * The Lyrics screen's model (spec 5.5; 2026-11-11, the user's request): the song
 * playing's lyrics, from its own files first and LRCLIB when they have none (the
 * user's call). It follows the song playing: [showing] with the next song looks
 * that one up. LRCLIB's answers are kept for the run by [OnlineLyrics]; nothing is
 * saved. Held by the app; main thread only.
 */
@Stable
class SongLyrics(private val online: OnlineLyrics, private val readLocal: (Track) -> Lyrics? = ::readLocalLyrics) {
    private val scope = MainScope()
    private var job: Job? = null

    var state: LyricsState by mutableStateOf(LyricsState())
        private set

    /** The screen shows [track] (the song playing, as the screens show it): looks it up unless it is the one shown. */
    fun showing(track: Track?) {
        if (track == null) {
            job?.cancel()
            state = LyricsState(note = LyricsNote(LyricsRows.NOTHING_PLAYING))
            return
        }
        // The same song with the same tags; Edit Tags' new ones are looked up again.
        if (state.track == track) return
        show(track)
    }

    /** A tap on a note that offers it: the lookup again. */
    fun retry() {
        val track = state.track ?: return
        if (state.note?.retry == true) show(track)
    }

    private fun show(track: Track) {
        job?.cancel()
        state = LyricsState(track, note = LyricsNote(LyricsRows.LOOKING))
        job = scope.launch {
            val local = withContext(Dispatchers.IO) { readLocal(track) }
            if (local != null) {
                state = LyricsState(track, local)
                return@launch
            }
            state = try {
                val found = online.find(track.artist, track.albumArtist, track.title, track.album, track.durationMs)
                when {
                    found.lyrics != null -> LyricsState(track, found.lyrics)
                    found.instrumental -> LyricsState(track, note = LyricsNote(LyricsRows.INSTRUMENTAL))
                    else -> LyricsState(track, note = LyricsNote(LyricsRows.NONE))
                }
            } catch (e: LyricsException) {
                LyricsState(track, note = LyricsRows.failed(e))
            }
        }
    }
}
