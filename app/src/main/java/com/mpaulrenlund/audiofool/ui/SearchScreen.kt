package com.mpaulrenlund.audiofool.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import com.mpaulrenlund.audiofool.core.format.Display
import com.mpaulrenlund.audiofool.core.library.LibrarySearch
import com.mpaulrenlund.audiofool.core.library.SearchResults
import com.mpaulrenlund.audiofool.core.model.ArtistGroup
import com.mpaulrenlund.audiofool.core.model.Track
import com.mpaulrenlund.audiofool.core.theme.ShadowLayer
import com.mpaulrenlund.audiofool.theme.LocalAppTheme
import com.mpaulrenlund.audiofool.theme.tokenSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Search (spec 4.4): the field, focused, and three groups updated as you type,
 * each shown only when it has results. The matching is the desktop's
 * [LibrarySearch], run off the main thread on an index folded once per library.
 */
@Composable
fun SearchScreen(browse: Browse, actions: AppActions, initialQuery: String = "") {
    val t = LocalAppTheme.current
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    // The keyboard comes up once, on the way in; not again on coming back from a result.
    var focusedOnce by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Opened with a query (ThemeLab), the first results are there for the first frame.
    val first = remember { if (initialQuery.isEmpty()) SearchResults.None else search(browse, initialQuery) }
    val results by produceState(first, browse, query) {
        value = withContext(Dispatchers.Default) { search(browse, query) }
    }

    fun open(route: Route) {
        keyboard?.hide()
        actions.open(route)
    }

    ScreenFrame(
        topBar = {
            TopBar(t.dp("topBar.searchBarPaddingX"), t.dp("topBar.searchBarPaddingX"), t.dp("topBar.gap")) {
                SearchBox(query, { query = it }, Modifier.weight(1f).focusRequester(focus))
            }
        },
        bottom = { MiniPlayerBar(browse, actions) },
    ) {
        PanelSurface(Modifier.weight(1f)) {
            LazyColumn {
                resultGroups(results, browse, actions, ::open)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!focusedOnce) {
            focusedOnce = true
            focus.requestFocus()
        }
    }
}

private fun search(browse: Browse, query: String): SearchResults {
    val index = browse.searchIndex ?: LibrarySearch.Index(browse.library.allTracks).also { browse.searchIndex = it }
    return SearchResults.of(browse.library, index, query)
}

private fun LazyListScope.resultGroups(results: SearchResults, browse: Browse, actions: AppActions, open: (Route) -> Unit) {
    var first = true
    fun header(label: String) {
        val above = !first
        first = false
        item(key = "header:$label", contentType = "header") { PanelHeader(label, dividerAbove = above) }
    }

    if (results.artists.isNotEmpty()) {
        header("Artists")
        groupPadding("artists:top")
        items(results.artists, key = { "artist:${it.name}" }, contentType = { "artist" }) { artist ->
            SearchArtistRow(artist, browse) { open(Route.Albums(artist.name)) }
        }
        groupPadding("artists:bottom")
    }
    if (results.albums.isNotEmpty()) {
        header("Albums")
        groupPadding("albums:top")
        items(results.albums, key = { "album:${it.artistName}/${it.title}" }, contentType = { "album" }) { album ->
            ListPadding {
                AlbumRow(album, "${album.artistName} · ${album.yearDisplay} · ${album.trackCountDisplay}", browse) {
                    open(Route.Album(album.artistName, album.title))
                }
            }
        }
        groupPadding("albums:bottom")
    }
    if (results.songs.isNotEmpty()) {
        header("Songs")
        groupPadding("songs:top")
        items(results.songs, key = { "song:${it.filePath}" }, contentType = { "song" }) { track ->
            SearchSongRow(track, browse) { actions.play(track) }
        }
        groupPadding("songs:bottom")
    }
}

/** The `panel.listPadding` above and below each group's rows. */
private fun LazyListScope.groupPadding(key: String) {
    item(key = key, contentType = "padding") {
        val t = LocalAppTheme.current
        Spacer(Modifier.height(t.dp("panel.listPadding")))
    }
}

/** A group's rows sit `panel.listPadding` in from the panel's sides. */
@Composable
private fun ListPadding(content: @Composable () -> Unit) {
    val t = LocalAppTheme.current
    Box(Modifier.padding(horizontal = t.dp("panel.listPadding"))) { content() }
}

/** ARTISTS: the name and the album count, no thumbnail. */
@Composable
private fun SearchArtistRow(artist: ArtistGroup, browse: Browse, onClick: () -> Unit) {
    val t = LocalAppTheme.current
    ListPadding {
        ListRow(t.dp("search.artistRowHeight"), t.dp("search.artistRowPaddingX"), unavailable = browse.isUnavailable(artist), onClick = onClick) {
            Column(verticalArrangement = Arrangement.spacedBy(t.dp("artists.lineGap"))) {
                Line(artist.name, t.text("artists.nameSize", "color.text.primary"))
                Line(artist.albumCountDisplay, t.text("artists.countSize", "color.text.muted"))
            }
        }
    }
}

/** SONGS: the title, "Artist · Album" and the time; the song playing in the now-playing style. */
@Composable
private fun SearchSongRow(track: Track, browse: Browse, onClick: () -> Unit) {
    val t = LocalAppTheme.current
    val playing = browse.isPlaying(track)
    val unavailable = browse.isUnavailable(track)
    val sub = if (playing) "color.accent.playbackText" else "color.text.secondary"
    Box(Modifier.padding(horizontal = t.dp("album.listPaddingX"))) {
        ListRow(
            t.dp("search.songRowHeight"),
            t.dp("search.songRowPaddingX"),
            look = if (playing) RowLook.NowPlaying else RowLook.Normal,
            unavailable = unavailable,
            gap = t.dp("search.songRowGap"),
            onClick = if (unavailable) null else onClick,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(t.dp("artists.lineGap"))) {
                Line(
                    track.displayTitle,
                    t.text("artists.nameSize", if (playing) "color.accent.playbackText" else "color.text.primary", if (playing) 600 else 400),
                )
                Line(listOf(track.artist.ifBlank { track.groupingArtist }, track.album).filter { it.isNotBlank() }.joinToString(" · "), t.text("artists.countSize", sub))
            }
            Line(
                Display.time(track.durationMs),
                t.text("album.cellSize", sub).copy(textAlign = TextAlign.End),
                Modifier.width(t.dp("search.timeWidth")),
            )
        }
    }
}

/**
 * The field in its focused state (spec 4.4): `state.searchFocusBg`, a 2 dp
 * `state.searchFocusRing` ring, the query, and a clear (x) button.
 */
@Composable
fun SearchBox(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val t = LocalAppTheme.current
    val shape = RoundedCornerShape(t.radius("radius.surface"))
    val ring = ShadowLayer(
        inset = false, offsetX = 0.0, offsetY = 0.0, blur = 0.0,
        spread = t.number("searchField.focusRingWidth"), color = t.themeColor("color.state.searchFocusRing"),
    )
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        modifier = modifier.height(t.dp("searchField.height")),
        singleLine = true,
        textStyle = t.text("searchField.textSize", "color.text.primary"),
        cursorBrush = SolidColor(t.color("color.accent.playback")),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        decorationBox = { field ->
            Row(
                Modifier
                    .tokenSurface(shape, t.color("color.state.searchFocusBg"), t.shadow("shadow.searchInset") + ring, t.color("color.panel.border"), t.dp("borderWidth"))
                    .padding(start = t.dp("searchField.paddingStart"), end = t.dp("searchField.paddingEndWithClear")),
                horizontalArrangement = Arrangement.spacedBy(t.dp("searchField.iconGap")),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.SEARCH, t.dp("searchField.icon"), "color.text.muted", "icon.searchStrokeOn24Grid")
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Line("Search", t.text("searchField.textSize", "color.text.placeholder"))
                    field()
                }
                Box(
                    Modifier.size(t.dp("searchField.clearButton")).tappable(CircleShape) { onQuery("") },
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.CLEAR, t.dp("searchField.clearIcon"), "color.text.muted") }
            }
        },
    )
}
