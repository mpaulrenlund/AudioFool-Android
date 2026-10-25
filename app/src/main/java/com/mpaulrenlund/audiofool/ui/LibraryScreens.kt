package com.mpaulrenlund.audiofool.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.mpaulrenlund.audiofool.core.format.Display
import com.mpaulrenlund.audiofool.core.library.ArtistIndex
import com.mpaulrenlund.audiofool.core.library.ScanProgress
import com.mpaulrenlund.audiofool.core.model.Album
import com.mpaulrenlund.audiofool.core.model.ArtistGroup
import com.mpaulrenlund.audiofool.core.model.Track
import com.mpaulrenlund.audiofool.theme.LocalAppTheme

// The drill-down (spec 4.1 - 4.3): Artists, an artist's Albums, and an Album.
// A row tapped on the way down stays selected when you come back to it.

/** Artists (spec 4.1), the start screen. */
@Composable
fun ArtistsScreen(
    browse: Browse,
    actions: AppActions,
    scanning: Boolean = false,
    progress: ScanProgress? = null,
    initialSelected: String? = null,
    initialMenuOpen: Boolean = false,
    indexPreview: IndexPreview? = null,
) {
    val t = LocalAppTheme.current
    var selected by rememberSaveable { mutableStateOf(initialSelected) }
    var menuOpen by rememberSaveable { mutableStateOf(initialMenuOpen) }
    ScreenFrame(
        topBar = {
            TopBar(t.dp("topBar.logoBarPaddingStart"), t.dp("topBar.logoBarPaddingEnd"), t.dp("topBar.logoToSearchGap")) {
                LogoMenuButton(menuOpen, { menuOpen = true }, { menuOpen = false }, actions)
                SearchFieldButton(Modifier.weight(1f)) { actions.open(Route.Search) }
            }
        },
        bottom = { MiniPlayerBar(browse, actions) },
    ) {
        PanelSurface(Modifier.weight(1f)) {
            PanelHeader("Artists", action = readingTags(scanning, progress))
            val artists = browse.library.artists
            if (artists.isEmpty()) {
                EmptyNote(browse, scanning)
            } else {
                val list = rememberLazyListState()
                val index = remember(artists) { ArtistIndex.of(artists) }
                val pad = t.dp("panel.listPadding")
                // The rows end short of the letter strip, which is always shown (0.2.1).
                val padEnd = if (showsStrip(index)) stripRoom() else pad
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(start = pad, top = pad, end = padEnd, bottom = pad)) {
                        items(artists, key = { it.name }, contentType = { "artist" }) { artist ->
                            ArtistRow(artist, browse, selected = artist.name == selected) {
                                selected = artist.name
                                actions.open(Route.Albums(artist.name))
                            }
                        }
                    }
                    ArtistIndexStrip(index, list, indexPreview)
                }
            }
        }
    }
}

/**
 * "Reading tags: 12,000 of 27,115" at the right of the ARTISTS header while a scan
 * reads tags (a first scan takes minutes on the M21), in the header's muted
 * colour. Nothing while a scan only checks for changes.
 */
private fun readingTags(scanning: Boolean, progress: ScanProgress?): (@Composable RowScope.() -> Unit)? {
    if (!scanning || progress == null || progress.filesFound == 0 || progress.filesRead >= progress.filesFound) return null
    return {
        val t = LocalAppTheme.current
        Line(
            "Reading tags: ${Display.count(progress.filesRead)} of ${Display.count(progress.filesFound)}",
            t.text("artists.countSize", "color.text.muted"),
            Modifier.padding(end = t.dp("panel.headerPaddingX") - t.dp("panel.headerActionPaddingEnd")),
        )
    }
}

/** A thumbnail of the artist's newest album, the name and "3 albums". */
@Composable
private fun ArtistRow(artist: ArtistGroup, browse: Browse, selected: Boolean, onClick: () -> Unit) {
    val t = LocalAppTheme.current
    ListRow(
        t.dp("artists.rowHeight"),
        t.dp("artists.rowPaddingX"),
        look = if (selected) RowLook.Selected else RowLook.Normal,
        unavailable = browse.isUnavailable(artist),
        gap = t.dp("artists.thumbGap"),
        onClick = onClick,
    ) {
        AlbumArt(artist.newestAlbum, t.dp("artists.thumb"), thumbBorder(selected))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(t.dp("artists.lineGap"))) {
            Line(artist.name, t.text("artists.nameSize", "color.text.primary"))
            Line(artist.albumCountDisplay, t.text("artists.countSize", secondLine(selected)))
        }
    }
}

/** Albums (spec 4.2): one artist's albums, in the desktop's release-date order. */
@Composable
fun AlbumsScreen(artistName: String, browse: Browse, actions: AppActions, initialSelected: String? = null) {
    val t = LocalAppTheme.current
    val artist = browse.artist(artistName)
    var selected by rememberSaveable { mutableStateOf(initialSelected) }
    ScreenFrame(
        topBar = {
            TopBar(t.dp("topBar.paddingStart"), t.dp("topBar.paddingEnd"), t.dp("topBar.gap")) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(t.dp("borderWidth"))) {
                    Line(artist?.name ?: artistName, t.text("topBar.titleSize", "color.accent.blue", weight = t.number("topBar.titleWeight").toInt()))
                    Line(artist?.albumCountDisplay ?: "", t.text("topBar.subtitleSize", "color.text.muted"))
                }
                SearchButton(actions)
            }
        },
        bottom = { MiniPlayerBar(browse, actions) },
    ) {
        PanelSurface(Modifier.weight(1f)) {
            PanelHeader("Albums")
            LazyColumn(contentPadding = PaddingValues(t.dp("panel.listPadding"))) {
                items(artist?.albums.orEmpty(), key = { it.title }, contentType = { "album" }) { album ->
                    AlbumRow(album, "${album.yearDisplay} · ${album.trackCountDisplay}", browse, selected = album.title == selected) {
                        selected = album.title
                        actions.open(Route.Album(album.artistName, album.title))
                    }
                }
            }
        }
    }
}

/** An album row (Albums and Search): a 64 dp thumbnail, the title on two lines at most, and [meta]. */
@Composable
fun AlbumRow(album: Album, meta: String, browse: Browse, selected: Boolean = false, onClick: () -> Unit) {
    val t = LocalAppTheme.current
    ListRow(
        t.dp("albums.rowHeight"),
        t.dp("albums.rowPaddingX"),
        look = if (selected) RowLook.Selected else RowLook.Normal,
        unavailable = browse.isUnavailable(album),
        gap = t.dp("albums.thumbGap"),
        onClick = onClick,
    ) {
        AlbumArt(album, t.dp("albums.thumb"), thumbBorder(selected))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(t.dp("albums.lineGap"))) {
            Line(
                album.title,
                t.text("albums.titleSize", "color.text.primary", lineHeight = t.number("albums.titleLineHeight")),
                maxLines = t.number("albums.titleMaxLines").toInt(),
            )
            Line(meta, t.text("albums.metaSize", secondLine(selected)))
        }
    }
}

/** Album (spec 4.3): the header block, then # · Song · Time. */
@Composable
fun AlbumScreen(artistName: String, title: String, browse: Browse, actions: AppActions) {
    val t = LocalAppTheme.current
    val album = browse.album(artistName, title)
    ScreenFrame(
        topBar = {
            TopBar(t.dp("topBar.paddingStart"), t.dp("topBar.paddingEnd"), t.dp("topBar.gap")) {
                Line(artistName, t.text("topBar.parentLabelSize", "color.text.muted"), Modifier.weight(1f))
                SearchButton(actions)
            }
        },
        bottom = { MiniPlayerBar(browse, actions) },
    ) {
        PanelSurface(Modifier.weight(1f)) {
            if (album != null) AlbumHeader(album)
            TableHeader()
            LazyColumn(
                contentPadding = PaddingValues(horizontal = t.dp("album.listPaddingX"), vertical = t.dp("album.listPaddingY")),
            ) {
                items(album?.tracks.orEmpty(), key = { it.filePath }, contentType = { "song" }) { track ->
                    SongRow(track, browse, actions)
                }
            }
        }
    }
}

/** 72 dp art with `shadow.headerArt`, the title (two lines), the artist and "1997 · 10 tracks · 42:58". */
@Composable
private fun AlbumHeader(album: Album) {
    val t = LocalAppTheme.current
    Column {
        Row(
            Modifier.fillMaxWidth().padding(t.dp("album.headerPadding")),
            horizontalArrangement = Arrangement.spacedBy(t.dp("album.artGap")),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlbumArt(album, t.dp("album.art"), "color.art.border", t.shadow("shadow.headerArt"), header = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(t.dp("album.lineGap"))) {
                Line(
                    album.title,
                    t.text("album.titleSize", "color.text.heading", weight = 600, lineHeight = t.number("album.titleLineHeight")),
                    maxLines = t.number("album.titleMaxLines").toInt(),
                )
                Line(album.artistName, t.text("album.artistSize", "color.accent.blue", weight = 600))
                Line(
                    "${album.yearDisplay} · ${album.trackCountDisplay} · ${Display.time(album.totalDurationMs)}",
                    t.text("album.detailsSize", "color.text.secondary"),
                )
            }
        }
        Divider(t)
    }
}

/** "# · SONG · TIME" (spec 4.3), lined up with the rows' columns. */
@Composable
private fun TableHeader() {
    val t = LocalAppTheme.current
    val style = t.text("album.tableHeaderSize", "color.text.muted", weight = 600, letterSpacingEm = t.number("album.tableHeaderLetterSpacingEm"))
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .height(t.dp("album.tableHeaderHeight") - t.dp("borderWidth"))
                .padding(horizontal = t.dp("album.tablePaddingX")),
            horizontalArrangement = Arrangement.spacedBy(t.dp("album.columnGap")),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Line("#", style.copy(textAlign = TextAlign.End), Modifier.width(t.dp("album.columns.number.width")))
            Line("SONG", style, Modifier.weight(1f))
            Line("TIME", style.copy(textAlign = TextAlign.End), Modifier.width(t.dp("album.columns.time.width")))
        }
        Divider(t)
    }
}

/**
 * A song in the Album table: the track number with the triangle slot left of it
 * (a solid `accent.playback` triangle while it plays), the title and the time.
 * The # shows the tag's track number, blank when untagged, as the desktop's
 * table does; discs follow each other with no disc column.
 */
@Composable
private fun SongRow(track: Track, browse: Browse, actions: AppActions) {
    val t = LocalAppTheme.current
    val playing = browse.isPlaying(track)
    val unavailable = browse.isUnavailable(track)
    val weight = if (playing) 600 else 400
    val cell = if (playing) "color.accent.playbackText" else "color.text.secondary"
    ListRow(
        t.dp("album.rowHeight"),
        t.dp("album.rowPaddingX"),
        look = if (playing) RowLook.NowPlaying else RowLook.Normal,
        unavailable = unavailable,
        gap = t.dp("album.columnGap"),
        onClick = if (unavailable) null else ({ actions.play(track) }),
    ) {
        Row(
            Modifier.width(t.dp("album.columns.number.width")),
            horizontalArrangement = Arrangement.spacedBy(t.dp("album.triangle.gap"), Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (playing) {
                Icon(AppIcons.TRIANGLE, t.dp("album.triangle.width"), t.dp("album.triangle.height"), "color.accent.playback", "album.triangle.strokeOnGrid")
            }
            Line(
                Display.number(track.trackNumber),
                t.text("album.cellSize", if (playing) "color.accent.playbackText" else "color.text.muted", weight).copy(textAlign = TextAlign.End),
                Modifier.widthIn(min = t.dp("album.numberMinWidth")),
            )
        }
        Line(track.displayTitle, t.text("album.songSize", if (playing) "color.accent.playbackText" else "color.text.primary", weight), Modifier.weight(1f))
        Line(
            Display.time(track.durationMs),
            t.text("album.cellSize", cell, weight).copy(textAlign = TextAlign.End),
            Modifier.width(t.dp("album.columns.time.width")),
        )
    }
}

/** The top bars' search button: 48 dp, a 20 dp icon in `text.muted`. */
@Composable
fun SearchButton(actions: AppActions) {
    val t = LocalAppTheme.current
    IconButton(AppIcons.SEARCH, t.dp("topBar.searchIcon"), "color.text.muted", { actions.open(Route.Search) }, "icon.searchStrokeOn24Grid")
}

/** A thumbnail's border: `art.thumbBorderSelected` on a selected row (spec 2). */
fun thumbBorder(selected: Boolean) = if (selected) "color.art.thumbBorderSelected" else "color.art.thumbBorder"

/** A row's second line: `text.secondary` when selected (spec 2), else `text.muted`. */
fun secondLine(selected: Boolean) = if (selected) "color.text.secondary" else "color.text.muted"
