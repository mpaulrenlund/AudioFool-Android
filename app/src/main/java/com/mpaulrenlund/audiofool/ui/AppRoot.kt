package com.mpaulrenlund.audiofool.ui

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mpaulrenlund.audiofool.AudioFoolApp
import com.mpaulrenlund.audiofool.core.library.LibraryStatistics
import com.mpaulrenlund.audiofool.core.library.MusicLibrary
import com.mpaulrenlund.audiofool.playback.BassRuntime
import com.mpaulrenlund.audiofool.storage.Storage

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
            // Spec 2: a tap on a song plays it, with the rest of its album after it.
            play = { track ->
                val album = app.library.state.value.library?.albumHolding(track.filePath)
                player.playFromAlbum(track, album?.tracks ?: listOf(track))
            },
            playPause = player::playPause,
            previous = player::previous,
            next = player::next,
            openNowPlaying = { nav.open(Route.NowPlaying) },
        )
    }
    val nowPlayingActions = remember(nav) {
        NowPlayingActions(
            playPause = player::playPause,
            previous = player::previous,
            next = player::next,
            seek = player::seek,
            cycleRepeat = player::cycleRepeat,
            toggleShuffle = player::toggleShuffle,
            toggleBitPerfect = player::toggleBitPerfect,
            close = nav::back,
        )
    }
    val lastFmActions = remember {
        val lastFm = app.lastFm
        LastFmActions(
            startConnecting = lastFm::startConnecting,
            connect = lastFm::connect,
            cancel = lastFm::cancel,
            openApprovalPage = lastFm::openApprovalPage,
            openCreateApiAccount = lastFm::openCreateApiAccount,
            disconnect = lastFm::disconnect,
        )
    }
    LaunchedEffect(openNowPlaying) {
        if (openNowPlaying > 0 && app.nowPlaying.track != null) nav.open(Route.NowPlaying)
    }

    CompositionLocalProvider(LocalArt provides app.art) {
        NavHost(nav) { route ->
            when (route) {
                Route.Artists -> ArtistsScreen(browse, actions, scanning = state.scanning, progress = state.progress)
                is Route.Albums -> AlbumsScreen(route.artist, browse, actions)
                is Route.Album -> AlbumScreen(route.artist, route.title, browse, actions)
                Route.Search -> SearchScreen(browse, actions)
                Route.NowPlaying -> NowPlayingScreen(app.nowPlaying, nowPlayingActions)
                Route.Settings -> SettingsScreen(
                    folders = folders.map { f ->
                        FolderLine(f, Storage.describe(context, f.path), state.unavailableFolders.any { it.equals(f.path, ignoreCase = true) })
                    },
                    actions = SettingsActions(
                        toggleTheme = actions.toggleTheme,
                        rescan = { app.library.rescan() },
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
                )
                Route.Statistics -> StatisticsScreen(
                    state = state,
                    stats = remember(library) { LibraryStatistics.compute(library) },
                    listening = app.listening.time,
                    numbers = DebugNumbers(
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
                    ),
                    actions = DebugActions(
                        showTokenSheet = { nav.open(Route.TokenSheet(SheetPage.Colours1)) },
                        forgetCache = { app.library.deleteCache() },
                    ),
                )
                is Route.TokenSheet -> TokenSheet(
                    route.page,
                    Modifier.clickable { nav.replaceTop(Route.TokenSheet(SheetPage.entries[(route.page.ordinal + 1) % SheetPage.entries.size])) },
                )
            }
        }
    }
}
