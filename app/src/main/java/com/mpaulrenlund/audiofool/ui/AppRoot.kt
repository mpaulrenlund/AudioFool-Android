package com.mpaulrenlund.audiofool.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mpaulrenlund.audiofool.AudioFoolApp
import com.mpaulrenlund.audiofool.BuildConfig
import com.mpaulrenlund.audiofool.core.library.LibraryStatistics
import com.mpaulrenlund.audiofool.core.library.MusicLibrary
import com.mpaulrenlund.audiofool.core.model.Track
import com.mpaulrenlund.audiofool.core.playback.LibraryShuffle
import com.mpaulrenlund.audiofool.genius.GeniusNote
import com.mpaulrenlund.audiofool.genius.GeniusRows
import com.mpaulrenlund.audiofool.genius.GeniusState
import com.mpaulrenlund.audiofool.lastfm.historyTarget
import com.mpaulrenlund.audiofool.library.TagEditing
import com.mpaulrenlund.audiofool.lyrics.LyricsNote
import com.mpaulrenlund.audiofool.lyrics.LyricsRows
import com.mpaulrenlund.audiofool.lyrics.LyricsState
import com.mpaulrenlund.audiofool.playback.BassRuntime
import com.mpaulrenlund.audiofool.storage.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The app's screens (spec 4, 5), wired to the library, the settings and the
 * player. Navigation keeps each screen's state; a theme switch only recomposes.
 * [openNowPlaying] changes when the notification asks for Now Playing.
 */
@Composable
fun AppRoot(app: AudioFoolApp, dark: Boolean, openNowPlaying: Int = 0, pickFolder: () -> Unit) {
    val context = LocalContext.current
    val state by app.library.state.collectAsStateWithLifecycle()
    val folders by app.settings.folders.collectAsStateWithLifecycle()
    val nav = rememberNavigator()
    val player = app.player

    val library = state.library ?: MusicLibrary.Empty
    val browse = remember(library, state.unavailableFolders, folders) {
        Browse(library, state.unavailableFolders, app.nowPlaying, anyFolderIncluded = folders.any { it.included })
    }
    val actions = remember(nav) {
        AppActions(
            open = nav::open,
            back = nav::back,
            toggleTheme = { app.settings.setDarkTheme(!app.settings.darkTheme.value) },
            // Spec 2: a tap on a song plays it, with the rest of its album after it,
            // and opens Now Playing on it (the user's call, 0.1.1).
            play = { track ->
                val album = app.library.state.value.library?.albumHolding(track.filePath)
                player.playFromAlbum(track, album?.tracks ?: listOf(track))
                if (app.nowPlaying.track?.filePath == track.filePath) nav.open(Route.NowPlaying)
            },
            playPause = player::playPause,
            previous = player::previous,
            next = player::next,
            openNowPlaying = { nav.open(Route.NowPlaying) },
            rescan = { app.library.rescan(asked = true) },
            openListeningHistory = {
                app.history.open()
                nav.open(Route.ListeningHistory)
            },
            // Spec 4.1 (0.9.0): every song that can be reached, shuffled, from a random
            // one, and Now Playing opens on it, as for a tapped song (the user's call).
            shuffleLibrary = {
                val now = app.library.state.value
                val started = now.library?.let { player.shuffleAll(LibraryShuffle.queue(it, now::isUnavailable)) } == true
                if (started) nav.open(Route.NowPlaying)
            },
            // Spec 4.1, 9 (2026-11-12, the user's call): the menus' Version row looks for a newer build.
            checkForUpdates = app.updates::checkNow,
            // Spec 4.2 (2026-11-10): the same for one artist, from Albums; back returns there.
            shuffleArtist = { name ->
                val now = app.library.state.value
                val artist = now.library?.artists?.firstOrNull { it.name == name }
                val started = artist?.let { player.shuffleAll(LibraryShuffle.queue(it, now::isUnavailable)) } == true
                if (started) nav.open(Route.NowPlaying)
            },
        )
    }
    // Listening History (spec 4.7): a row goes to its artist's albums, its album, or its
    // album with the song chosen; back comes back to the history, as it was.
    val historyActions = remember(nav) {
        HistoryActions(
            choose = app.history::choose,
            go = { row ->
                historyTarget(row, app.library.state.value.library)?.let { d ->
                    nav.open(if (d.album == null) Route.Albums(d.artist) else Route.Album(d.artist, d.album, d.song))
                }
            },
            retry = app.history::open,
            openSettings = { nav.open(Route.Settings) },
        )
    }
    val nowPlayingActions = remember(nav) {
        NowPlayingActions(
            playPause = player::playPause,
            previous = player::previous,
            stepBack = player::stepBack,
            next = player::next,
            seek = player::seek,
            cycleRepeat = player::cycleRepeat,
            toggleShuffle = player::toggleShuffle,
            toggleBitPerfect = player::toggleBitPerfect,
            analyze = { track -> nav.open(Route.Analysis(track.filePath)) },
            openCover = { track -> nav.open(Route.Cover(track.filePath)) },
            // Spec 5.2: looked up each time, as the desktop's Ctrl+G; a song already found shows at once.
            genius = { track ->
                app.genius.show(track)
                nav.open(Route.Genius(track.filePath))
            },
            // Spec 5.4: the song's tags; not while its folder can't be reached (the SD card out).
            editTags = { track -> nav.open(Route.EditTags(track.filePath)) },
            canEditTags = { track -> !app.library.state.value.isUnavailable(track) },
            // Spec 5.5: the song playing's lyrics; the screen follows the song.
            lyrics = { nav.open(Route.Lyrics) },
            // Spec 5: the artist's and the album's names open them; back returns to Now Playing.
            openArtist = { track -> artistRouteFor(track, app.library.state.value.library)?.let(nav::open) },
            openAlbum = { track -> albumRouteFor(track, app.library.state.value.library)?.let(nav::open) },
        )
    }
    val geniusActions = remember {
        GeniusActions(
            choose = app.genius::choose,
            retry = app.genius::retry,
            showMoreComments = app.genius::showMoreComments,
            openUrl = { url ->
                // The browser (or Genius's app); with neither, the tap does nothing, as on the desktop.
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: ActivityNotFoundException) {
                }
            },
        )
    }
    val lyricsActions = remember { LyricsActions(seek = player::seek, retry = app.lyrics::retry) }
    val lastFmActions = remember {
        val lastFm = app.lastFm
        LastFmActions(
            startConnecting = lastFm::startConnecting,
            connect = lastFm::connect,
            cancel = lastFm::cancel,
            disconnect = lastFm::disconnect,
        )
    }
    // Statistics and its Debug screen (0.2.2) read the same numbers.
    val debugNumbers = remember {
        DebugNumbers(
            startTimes = app.startTimes,
            thumbnailsDone = { app.art.list.prefilled to app.art.list.prefillTotal },
            describeFolder = { Storage.describe(context, it) },
            playback = PlaybackNumbers(
                nowPlaying = app.nowPlaying,
                bass = { if (BassRuntime.isInitialised) "BASS ${BassRuntime.version}" else BassRuntime.initError ?: "not started yet (starts on the first play)" },
                formats = { BassRuntime.availableFormats.joinToString(", ") },
                missing = { BassRuntime.missingFormats.joinToString(", ") },
                joins = player::outputStats,
                output = player::outputDetails,
                pretendRoute = { player.pretendRoute },
                cyclePretendRoute = player::cyclePretendRoute,
            ),
            usb = UsbDacNumbers(app.usbDac),
        )
    }
    val debugActions = remember(nav) {
        DebugActions(
            showTokenSheet = { nav.open(Route.TokenSheet(SheetPage.Colours1)) },
            forgetCache = { app.library.deleteCache() },
            openDebug = { nav.open(Route.Debug) },
        )
    }
    LaunchedEffect(openNowPlaying) {
        if (openNowPlaying > 0 && app.nowPlaying.track != null) nav.open(Route.NowPlaying)
    }
    val updateActions = remember {
        UpdateActions(update = app.updates::update, cancel = app.updates::cancel, openSettings = app.updates::openPermission)
    }

    CompositionLocalProvider(LocalArt provides app.art, LocalCoverColors provides app.coverColors) {
        Box(Modifier.fillMaxSize()) {
            NavHost(nav) { route ->
                when (route) {
                    Route.Artists -> ArtistsScreen(browse, actions, scanning = state.scanning, scanText = scanHeaderText(state), version = BuildConfig.VERSION_NAME)
                    is Route.Albums -> AlbumsScreen(route.artist, browse, actions)
                    is Route.Album -> AlbumScreen(route.artist, route.title, browse, actions, chosenSong = route.song)
                    Route.Search -> SearchScreen(browse, actions)
                    Route.NowPlaying -> NowPlayingScreen(app.nowPlaying, nowPlayingActions, actions, BuildConfig.VERSION_NAME)
                    is Route.Analysis -> AnalysisScreen(remember(library, route.path) { trackAt(library, route.path, app) })
                    is Route.Cover -> CoverViewerScreen(remember(library, route.path) { trackAt(library, route.path, app) })
                    is Route.EditTags -> {
                        // Kept while the screen is open, whatever a rescan does meanwhile; with the tags last saved.
                        val edit = remember(route.path) { EditTagsState(app.nowPlaying.shown(trackAt(library, route.path, app)) ?: Track(filePath = route.path)) }
                        LaunchedEffect(route.path) { edit.read(withContext(Dispatchers.IO) { TagEditing.readDetails(route.path) }) }
                        EditTagsScreen(edit, remember(edit) { EditTagsActions(save = { saveTags(app, nav, edit, route) }, leave = nav::back) })
                    }
                    is Route.Genius -> {
                        val track = remember(library, route.path) { trackAt(library, route.path, app) }
                        LaunchedEffect(route.path) { app.genius.showing(track) }
                        // Until the model is on this song (a frame, after Android restored the screen), the header and "Looking up…".
                        val shown = app.genius.state.takeIf { it.track?.filePath == route.path }
                            ?: GeniusState(track = track, note = GeniusNote(GeniusRows.LOOKING_UP))
                        GeniusScreen(shown, remember(library, route.path) { library.albumHolding(route.path) }, geniusActions)
                    }
                    Route.Lyrics -> {
                        // The song playing, as the screens show it (Edit Tags' saved tags); the next one when it changes.
                        val playing = app.nowPlaying.shownTrack
                        LaunchedEffect(playing) { app.lyrics.showing(playing) }
                        // Until the model is on this song (a frame), the header and "Looking for lyrics…".
                        val shown = app.lyrics.state.takeIf { it.track == playing }
                            ?: LyricsState(playing, note = LyricsNote(LyricsRows.LOOKING))
                        LyricsScreen(shown, app.nowPlaying, lyricsActions)
                    }
                    Route.Settings -> SettingsScreen(
                        folders = folders.map { f ->
                            FolderLine(f, Storage.describe(context, f.path), state.unavailableFolders.any { it.equals(f.path, ignoreCase = true) })
                        },
                        actions = SettingsActions(
                            toggleTheme = actions.toggleTheme,
                            rescan = { app.library.rescan(asked = true) },
                            addFolder = pickFolder,
                            setIncluded = { path, included ->
                                app.settings.setIncluded(path, included)
                                app.library.foldersChanged()
                            },
                            toggleBitPerfect = player::toggleBitPerfect,
                        ),
                        readout = app.nowPlaying.readout,
                        bitPerfect = app.nowPlaying.bitPerfect,
                        bitPerfectGreyed = app.nowPlaying.bitPerfectGreyed,
                        lastFm = app.lastFm.status,
                        lastFmActions = lastFmActions,
                        scanText = scanHeaderText(state),
                    )
                    Route.Statistics -> StatisticsScreen(
                        state = state,
                        stats = remember(library) { LibraryStatistics.compute(library) },
                        listening = app.listening.time,
                        numbers = debugNumbers,
                        actions = debugActions,
                    )
                    Route.ListeningHistory -> {
                        LaunchedEffect(Unit) { app.history.showing() }
                        ListeningHistoryScreen(app.history.state, historyActions)
                    }
                    Route.Debug -> DebugScreen(state, debugNumbers, debugActions)
                    is Route.TokenSheet -> TokenSheet(
                        route.page,
                        Modifier.clickable { nav.replaceTop(Route.TokenSheet(SheetPage.entries[(route.page.ordinal + 1) % SheetPage.entries.size])) },
                    )
                }
            }
            // Spec 9: the note over whatever screen is showing, in the same window, so taps go through it.
            app.updates.note?.let { UpdateNote(it, app.updates::noteGone, Modifier.align(Alignment.Center)) }
        }
        // Spec 9: the pop-up, over whatever screen is showing.
        app.updates.prompt?.let { UpdatePrompt(it, updateActions) }
    }
}

/**
 * Edit Tags' Save (spec 5.4): the file is saved off the main thread, in the app's
 * scope so that leaving can't cut it short; then the library and what plays take
 * the song as read back, and the screen goes back to Now Playing. A save that
 * didn't happen stays on the screen, saying why.
 */
private fun saveTags(app: AudioFoolApp, nav: Navigator, state: EditTagsState, route: Route.EditTags) {
    if (!state.canSave) return
    val edit = state.edit()
    state.saving = true
    state.failure = null
    app.scope.launch {
        val result = withContext(Dispatchers.IO) { TagEditing.save(state.track, edit) }
        withContext(Dispatchers.Main) {
            state.saving = false
            result
                .onSuccess { saved ->
                    app.library.retagged(saved)
                    app.player.retagged(saved)
                    if (nav.top == route) nav.back()
                }
                .onFailure { state.failure = "Couldn't save the tags: ${it.message}." }
        }
    }
}

/** The library's song at [path] (the playing one if the library doesn't hold it), for the screens that name a song by path. */
private fun trackAt(library: MusicLibrary, path: String, app: AudioFoolApp): Track =
    library.albumHolding(path)?.tracks?.firstOrNull { it.filePath.equals(path, ignoreCase = true) }
        ?: app.nowPlaying.track?.takeIf { it.filePath == path }
        ?: Track(filePath = path)
