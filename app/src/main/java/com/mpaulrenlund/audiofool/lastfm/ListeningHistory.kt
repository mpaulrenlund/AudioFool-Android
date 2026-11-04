package com.mpaulrenlund.audiofool.lastfm

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mpaulrenlund.audiofool.core.format.Display
import com.mpaulrenlund.audiofool.core.library.MusicLibrary
import com.mpaulrenlund.audiofool.core.model.Track
import com.mpaulrenlund.audiofool.core.scrobbling.HistoryTime
import com.mpaulrenlund.audiofool.core.scrobbling.LastFmApi
import com.mpaulrenlund.audiofool.core.scrobbling.LastFmException
import com.mpaulrenlund.audiofool.core.scrobbling.ListenerProfile
import com.mpaulrenlund.audiofool.core.scrobbling.ListeningMatch
import com.mpaulrenlund.audiofool.core.scrobbling.ListeningPeriod
import com.mpaulrenlund.audiofool.core.scrobbling.RecentPlay
import com.mpaulrenlund.audiofool.core.scrobbling.TopEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** What Listening History reads with: the API key (no secret or session needed) and whose history. */
class HistoryReader(val api: LastFmApi, val user: String)

/** Which of Listening History's rows leads where: an artist, an album, or a song in its album. */
enum class HistoryKind { Artist, Album, Song }

/**
 * One Listening History row: a top-list line with a bar ([fill] 0 to 1), or a recent
 * play without one ([fill] null). It can be tapped when the library holds what it
 * names: [target] is the library's song it leads to. Ported from the desktop's
 * `ListeningRow` (`ListeningHistoryViewModel.cs`).
 */
class HistoryRow(
    val label: String,
    val value: String,
    detail: String?,
    fill: Double?,
    val kind: HistoryKind,
    val target: Track?,
) {
    val detail: String? = detail?.takeIf { it.isNotBlank() }
    val fill: Double? = fill?.let { if (it.isFinite()) it.coerceIn(0.0, 1.0) else 0.0 }
}

/** The three top lists of one period. */
class TopLists(val artists: List<TopEntry>, val albums: List<TopEntry>, val tracks: List<TopEntry>)

/** What the Listening History screen draws (spec 4.7). */
class HistoryState(
    /** Whose history; null when AudioFool isn't connected to Last.fm. */
    val user: String? = null,
    val phase: Phase = Phase.Loading,
    /** Why it couldn't load ([Phase.Failed]), as Last.fm or the network said. */
    val error: String? = null,
    /** The headline figures: value and label, as Statistics' tiles. */
    val tiles: List<Pair<String, String>> = emptyList(),
    val period: ListeningPeriod = ListeningHistory.FIRST_PERIOD,
    val topArtists: List<HistoryRow> = emptyList(),
    val topAlbums: List<HistoryRow> = emptyList(),
    val topTracks: List<HistoryRow> = emptyList(),
    /** What the three top panels say when they have no rows: loading, nothing played, or why not. */
    val topEmptyText: String = "",
    val recent: List<HistoryRow> = emptyList(),
) {
    enum class Phase { Loading, NotConnected, Failed, Loaded }

    fun copy(
        period: ListeningPeriod = this.period,
        topArtists: List<HistoryRow> = this.topArtists,
        topAlbums: List<HistoryRow> = this.topAlbums,
        topTracks: List<HistoryRow> = this.topTracks,
        topEmptyText: String = this.topEmptyText,
    ) = HistoryState(user, phase, error, tiles, period, topArtists, topAlbums, topTracks, topEmptyText, recent)

    companion object {
        /**
         * The screen once Last.fm has answered, built as the desktop's view model builds
         * it. Also how ThemeLab draws it, from made-up answers.
         */
        fun loaded(
            user: String,
            profile: ListenerProfile,
            period: ListeningPeriod,
            lists: TopLists,
            recent: List<RecentPlay>,
            match: ListeningMatch,
            now: Instant,
            zone: ZoneId,
        ) = HistoryState(
            user = user,
            phase = Phase.Loaded,
            tiles = HistoryRows.tiles(profile, zone),
            recent = recent.map { HistoryRows.recent(it, match, now, zone) },
        ).showing(period, lists, match)

        /** Listening History not connected. */
        val NotConnected = HistoryState(phase = Phase.NotConnected)
    }

    /** These lists, for [period]. */
    fun showing(period: ListeningPeriod, lists: TopLists, match: ListeningMatch) = copy(
        period = period,
        topArtists = HistoryRows.top(lists.artists) { HistoryKind.Artist to match.artist(it.name) },
        topAlbums = HistoryRows.top(lists.albums) { HistoryKind.Album to match.album(it.artist ?: "", it.name) },
        topTracks = HistoryRows.top(lists.tracks) { HistoryKind.Song to match.song(it.artist ?: "", it.name) },
        topEmptyText = HistoryRows.emptyText(period),
    )
}

/** How Last.fm's answers become rows: the desktop's `ListeningHistoryViewModel`, ported. */
object HistoryRows {
    fun tiles(p: ListenerProfile, zone: ZoneId): List<Pair<String, String>> = buildList {
        add(Display.count(p.scrobbles) to "Scrobbles")
        p.registered?.let { add(HistoryTime.month(it, zone) to "Scrobbling since") }
        p.artists?.let { add(Display.count(it) to "Artists") }
        p.albums?.let { add(Display.count(it) to "Albums") }
        p.tracks?.let { add(Display.count(it) to "Tracks") }
    }

    /** Each row's bar measured against the first (the most played). */
    fun top(entries: List<TopEntry>, find: (TopEntry) -> Pair<HistoryKind, Track?>): List<HistoryRow> {
        val most = if (entries.isEmpty()) 1 else maxOf(1, entries.maxOf { it.plays })
        return entries.map { e ->
            val (kind, track) = find(e)
            HistoryRow(e.name, plays(e.plays), e.artist, e.plays.toDouble() / most, kind, track)
        }
    }

    fun recent(play: RecentPlay, match: ListeningMatch, now: Instant, zone: ZoneId): HistoryRow {
        val detail = if (play.album.isBlank()) play.artist else "${play.artist} · ${play.album}"
        return HistoryRow(play.track, HistoryTime.ago(play, now, zone), detail, null, HistoryKind.Song, match.song(play.artist, play.track))
    }

    fun emptyText(period: ListeningPeriod): String = when (period) {
        ListeningPeriod.AllTime -> "Nothing played yet."
        ListeningPeriod.Month -> "Nothing played in the last month."
        else -> "Nothing played in the last ${period.label.lowercase()}."
    }

    fun plays(n: Long): String = if (n == 1L) "1 play" else "${Display.count(n)} plays"
}

/**
 * Listening History (spec 4.7): the user's Last.fm totals, their top artists, albums
 * and tracks for a chosen period, and what they played last. Ported from the
 * desktop's `ListeningHistoryViewModel` (session 74). Read-only; nothing is saved.
 *
 * Held by the app, not the screen, so it is still there when the user comes back from
 * a row they opened. Each opening from a menu ([open]) fetches afresh, as the
 * desktop's window does; within one, each period is fetched once, so going back to
 * one is instant. Main thread only.
 */
@Stable
class ListeningHistory(
    private val reader: suspend () -> HistoryReader?,
    /** What the rows are matched against: the library as shown. */
    private val library: () -> MusicLibrary?,
    private val clock: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val scope = MainScope()
    private var job: Job? = null
    private var source: HistoryReader? = null
    private val periods = HashMap<ListeningPeriod, TopLists>()
    /** What the rows on screen were matched with, for a period chosen later. */
    private var match = ListeningMatch.Empty
    @Volatile private var matched: Pair<MusicLibrary, ListeningMatch>? = null

    var state: HistoryState by mutableStateOf(HistoryState())
        private set

    /** Opened from a menu, or tapped again after a failure: everything fetched afresh. */
    fun open() {
        job?.cancel()
        periods.clear()
        source = null
        state = HistoryState()
        job = scope.launch { load() }
    }

    /** The screen is showing: loads unless it has (after Android restored the screen, say). */
    fun showing() {
        if (job == null) open()
    }

    /** A period chosen on the switch: shown at once if already fetched, otherwise fetched now. */
    fun choose(period: ListeningPeriod) {
        val api = source ?: return
        if (period == state.period || state.phase != HistoryState.Phase.Loaded) return

        periods[period]?.let {
            state = state.showing(period, it, match)
            return
        }

        state = state.copy(period = period, topArtists = emptyList(), topAlbums = emptyList(), topTracks = emptyList(), topEmptyText = "Loading…")
        scope.launch {
            try {
                val lists = fetchPeriod(api, period)
                periods[period] = lists
                if (state.period == period && source === api) state = state.showing(period, lists, match)
            } catch (e: LastFmException) {
                if (state.period == period && source === api) state = state.copy(topEmptyText = "Couldn't load this period. ${sentence(e)}")
            }
        }
    }

    private suspend fun load() {
        val r = reader()
        if (r == null) {
            state = HistoryState.NotConnected
            return
        }
        state = HistoryState(user = r.user)
        try {
            val loaded = coroutineScope {
                val matching = async(Dispatchers.Default) { matchFor(library()) }
                val profile = async { r.api.getProfile(r.user) }
                val recent = async { r.api.getRecentTracks(r.user, RECENT_COUNT) }
                val top = async { fetchPeriod(r, FIRST_PERIOD) }
                val lists = top.await()
                periods[FIRST_PERIOD] = lists
                match = matching.await()
                HistoryState.loaded(r.user, profile.await(), FIRST_PERIOD, lists, recent.await(), match, clock(), zone())
            }
            source = r
            state = loaded
        } catch (e: LastFmException) {
            state = HistoryState(user = r.user, phase = HistoryState.Phase.Failed, error = sentence(e))
        }
    }

    /** The library's match index, built once per library (a rescan makes a new one), off the main thread. */
    private fun matchFor(library: MusicLibrary?): ListeningMatch {
        if (library == null) return ListeningMatch.Empty
        matched?.let { (of, index) -> if (of === library) return index }
        return ListeningMatch(library.allTracks).also { matched = library to it }
    }

    private suspend fun fetchPeriod(r: HistoryReader, period: ListeningPeriod): TopLists = coroutineScope {
        val artists = async { r.api.getTopArtists(r.user, period, TOP_COUNT) }
        val albums = async { r.api.getTopAlbums(r.user, period, TOP_COUNT) }
        val tracks = async { r.api.getTopTracks(r.user, period, TOP_COUNT) }
        TopLists(artists.await(), albums.await(), tracks.await())
    }

    /** "User not found." from Last.fm's "User not found", as the desktop words it. */
    private fun sentence(e: LastFmException): String = "${(e.message ?: "Last.fm error ${e.code}").trimEnd('.')}."

    companion object {
        const val TOP_COUNT = 10
        const val RECENT_COUNT = 20

        /** The desktop opens on 12 Months. */
        val FIRST_PERIOD = ListeningPeriod.Year
    }
}

/** Where a row leads in the app, given the library: its artist's albums, its album, or its album with the song chosen. */
fun historyTarget(row: HistoryRow, library: MusicLibrary?): HistoryDestination? {
    val track = row.target ?: return null
    val album = library?.albumHolding(track.filePath) ?: return null
    return when (row.kind) {
        HistoryKind.Artist -> HistoryDestination(album.artistName, null, null)
        HistoryKind.Album -> HistoryDestination(album.artistName, album.title, null)
        HistoryKind.Song -> HistoryDestination(album.artistName, album.title, track.filePath)
    }
}

/** An artist's Albums screen ([album] null), an Album screen, or an Album screen with [song] chosen. */
data class HistoryDestination(val artist: String, val album: String?, val song: String?)
