package com.mpaulrenlund.audiofool.ui

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mpaulrenlund.audiofool.AudioFoolApp
import com.mpaulrenlund.audiofool.core.library.MusicLibrary
import com.mpaulrenlund.audiofool.storage.Storage

/**
 * The app's screens (spec 4), wired to the library, the settings and what is
 * playing. Navigation keeps each screen's state; a theme switch only recomposes.
 */
@Composable
fun AppRoot(app: AudioFoolApp, dark: Boolean, pickFolder: () -> Unit) {
    val context = LocalContext.current
    val state by app.library.state.collectAsStateWithLifecycle()
    val folders by app.settings.folders.collectAsStateWithLifecycle()
    val nav = rememberNavigator()

    val library = state.library ?: MusicLibrary.Empty
    val browse = remember(library, state.unavailableFolders, folders) {
        Browse(library, state.unavailableFolders, app.nowPlaying, anyFolderIncluded = folders.any { it.included })
    }
    val actions = remember(nav) {
        AppActions(
            open = nav::open,
            back = nav::back,
            toggleTheme = { app.settings.setDarkTheme(!app.settings.darkTheme.value) },
            play = app.nowPlaying::play,
            playPause = app.nowPlaying::playPause,
        )
    }

    CompositionLocalProvider(LocalArt provides app.art) {
        NavHost(nav) { route ->
            when (route) {
                Route.Artists -> ArtistsScreen(browse, actions, scanning = state.scanning)
                is Route.Albums -> AlbumsScreen(route.artist, browse, actions)
                is Route.Album -> AlbumScreen(route.artist, route.title, browse, actions)
                Route.Search -> SearchScreen(browse, actions)
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
                    ),
                )
                Route.Statistics -> StatisticsScreen(
                    state = state,
                    numbers = DebugNumbers(
                        startTimes = app.startTimes,
                        thumbnailsDone = { app.art.list.prefilled to app.art.list.prefillTotal },
                        describeFolder = { Storage.describe(context, it) },
                    ),
                    preview = app.nowPlaying.preview,
                    actions = DebugActions(
                        setPreview = { on -> app.nowPlaying.setPreview(on, library.artists.firstOrNull()?.newestAlbum?.tracks?.firstOrNull()) },
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
