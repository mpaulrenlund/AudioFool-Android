package com.mpaulrenlund.audiofool.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.mpaulrenlund.audiofool.core.format.Display
import com.mpaulrenlund.audiofool.core.library.AlbumYearIndex
import com.mpaulrenlund.audiofool.core.library.ArtistIndex
import com.mpaulrenlund.audiofool.core.model.Album
import com.mpaulrenlund.audiofool.core.model.ArtistGroup
import com.mpaulrenlund.audiofool.core.model.Track
import com.mpaulrenlund.audiofool.library.LibraryState
import com.mpaulrenlund.audiofool.playlists.PlaylistRow
import com.mpaulrenlund.audiofool.playlists.ShownPlaylist
import com.mpaulrenlund.audiofool.theme.LocalAppTheme

// The drill-down (spec 4.1 - 4.3): Artists, an artist's Albums, and an Album.
// A row tapped on the way down stays selected when you come back to it.

/** Artists (spec 4.1), the start screen. */
@Composable
fun ArtistsScreen(
    browse: Browse,
    actions: AppActions,
    scanning: Boolean = false,
    scanText: String? = null,
    initialSelected: String? = null,
    initialMenuOpen: Boolean = false,
    indexPreview: IndexPreview? = null,
    /** The app's version, at the foot of the logo menu (0.7.1); fixed in ThemeLab so a release doesn't change its pictures. */
    version: String = "",
    /** The Playlists tab's rows (2026-11-12); null until they are read. */
    playlists: List<PlaylistRow>? = null,
    /** Whether the Playlists tab shows: ThemeLab's pictures of it. */
    initialPlaylistsShown: Boolean = false,
) {
    val t = LocalAppTheme.current
    var selected by rememberSaveable { mutableStateOf(initialSelected) }
    var menuOpen by rememberSaveable { mutableStateOf(initialMenuOpen) }
    var playlistsShown by rememberSaveable { mutableStateOf(initialPlaylistsShown) }
    // System back on the Playlists tab goes back to Artists (2026-11-12, the user's call), as the desktop's PLAYLISTS header does.
    BackHandler(enabled = playlistsShown) { playlistsShown = false }
    ScreenFrame(
        topBar = {
            // The search field from the left, the logo at the right (2026-11-10, the user's call; the logo led until then).
            TopBar(t.dp("topBar.logoBarPaddingStart"), t.dp("topBar.logoBarPaddingEnd"), t.dp("topBar.logoToSearchGap")) {
                SearchFieldButton(Modifier.weight(1f)) { actions.open(Route.Search) }
                LogoMenuButton(menuOpen, { menuOpen = true }, { menuOpen = false }, actions, version)
            }
        },
        bottom = { MiniPlayerBar(browse, actions) },
    ) {
        PanelSurface(Modifier.weight(1f)) {
            val artists = browse.library.artists
            // The shuffle button is the Artists tab's; a scan's note shows on either.
            val canShuffle = artists.isNotEmpty() && !playlistsShown
            PanelTabsHeader(
                listOf("Artists", "Playlists"),
                selected = if (playlistsShown) 1 else 0,
                onSelect = { playlistsShown = it == 1 },
                action = if (canShuffle || scanText != null) ({ ArtistsHeaderAction(scanText, canShuffle, actions.shuffleLibrary) }) else null,
            )
            if (playlistsShown) {
                PlaylistsList(playlists, browse, actions)
            } else if (artists.isEmpty()) {
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
                    IndexStrip(index, list, indexPreview)
                }
            }
        }
    }
}

/**
 * A panel header holding tabs (Artists' ARTISTS and PLAYLISTS, 2026-11-12, the user's
 * request: "a button to the right of Artists called Playlists"): each label is the
 * header label's look, the one showing in `text.primary` and the other in the label's
 * own `text.muted`, `panel.tabGap` apart. A tap on one switches to it; the whole height
 * of the header is its touch area. The first label sits where a plain header's does.
 */
@Composable
fun PanelTabsHeader(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, action: (@Composable RowScope.() -> Unit)? = null) {
    val t = LocalAppTheme.current
    val halfGap = t.dp("panel.tabGap") / 2
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .height(t.dp("panel.headerHeight") - t.dp("borderWidth"))
                .padding(start = t.dp("panel.headerPaddingX") - halfGap, end = t.dp(if (action != null) "panel.headerActionPaddingEnd" else "panel.headerPaddingX")),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labels.forEachIndexed { i, label ->
                val on = i == selected
                Box(
                    Modifier
                        .fillMaxHeight()
                        .selectable(on, interactionSource = null, indication = null, role = Role.Tab) { onSelect(i) }
                        .padding(horizontal = halfGap),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Line(
                        label.uppercase(),
                        t.text(
                            "panel.headerTextSize",
                            if (on) "color.text.primary" else "color.text.muted",
                            weight = 600,
                            letterSpacingEm = t.number("panel.headerLetterSpacingEm"),
                        ),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            action?.invoke(this)
        }
        Divider(t)
    }
}

/**
 * The Playlists tab (2026-11-12): Liked, then Recently Added, then any others (the
 * desktop's order), each with the cover of its first song's album, the name and
 * "12 tracks", as an artist's row is drawn. A tap opens the playlist.
 */
@Composable
private fun PlaylistsList(playlists: List<PlaylistRow>?, browse: Browse, actions: AppActions) {
    val t = LocalAppTheme.current
    val pad = t.dp("panel.listPadding")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(pad)) {
        items(playlists.orEmpty(), key = { it.id }, contentType = { "playlist" }) { row ->
            ListRow(
                t.dp("artists.rowHeight"),
                t.dp("artists.rowPaddingX"),
                gap = t.dp("artists.thumbGap"),
                onClick = { actions.open(Route.Playlist(row.id)) },
            ) {
                AlbumArt(row.coverAlbum, t.dp("artists.thumb"), thumbBorder(false))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(t.dp("artists.lineGap"))) {
                    Line(row.name, t.text("artists.nameSize", "color.text.primary"))
                    Line(row.trackCount, t.text("artists.countSize", "color.text.muted"))
                }
            }
        }
    }
}

/**
 * What the ARTISTS and MUSIC FOLDERS headers say of a scan: "Reading tags: 12,000 of
 * 27,115" while any scan reads tags (a first scan takes minutes on the M21). A rescan
 * the user asked for also shows "Checking for changes…" before that, and how it
 * ended for a few seconds after ("Up to date · 3 new, 1 removed", 0.7.0). Nothing
 * while an automatic scan only checks for changes.
 */
fun scanHeaderText(state: LibraryState): String? {
    val p = state.progress
    return when {
        state.scanning && p != null && p.filesFound > 0 && (state.asked || p.filesRead < p.filesFound) ->
            "Reading tags: ${Display.count(p.filesRead)} of ${Display.count(p.filesFound)}"
        state.scanning && state.asked -> "Checking for changes…"
        state.scanning -> null
        else -> state.outcome?.text
    }
}

/**
 * The right of the ARTISTS header: the shuffle button at the far right (0.9.0, the
 * user's request), with a scan's note left of it, as Music folders' refresh button
 * has in Settings. No button while there is nothing to shuffle; then the note alone,
 * a header's padding from the edge, as before.
 */
@Composable
private fun ArtistsHeaderAction(scanText: String?, canShuffle: Boolean, onShuffle: () -> Unit) {
    val t = LocalAppTheme.current
    if (!canShuffle) {
        if (scanText != null) ScanNote(scanText, Modifier.padding(end = t.dp("panel.headerPaddingX") - t.dp("panel.headerActionPaddingEnd")))
        return
    }
    // The button keeps its room; a long note ellipsizes before it.
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (scanText != null) ScanNote(scanText, Modifier.weight(1f, fill = false))
        IconButtonSmall(AppIcons.SHUFFLE, onShuffle, label = "Shuffle library")
    }
}

/**
 * [scanHeaderText] at the right of a panel header, in the header's muted colour,
 * kept a header's padding clear of the label when it is long enough to ellipsize.
 */
@Composable
fun ScanNote(text: String, modifier: Modifier = Modifier) {
    val t = LocalAppTheme.current
    Line(text, t.text("artists.countSize", "color.text.muted"), modifier.padding(start = t.dp("panel.headerPaddingX")))
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

/**
 * Albums (spec 4.2): one artist's albums, in the desktop's release-date order, with
 * a year strip at the right when they don't all fit (0.11.0, the user's call), and a
 * shuffle button at the right of the ALBUMS header that plays the artist's songs
 * shuffled (2026-11-10, the user's request), as Artists' does the library's.
 */
@Composable
fun AlbumsScreen(
    artistName: String,
    browse: Browse,
    actions: AppActions,
    initialSelected: String? = null,
    indexPreview: IndexPreview? = null,
) {
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
            val albums = artist?.albums.orEmpty()
            // Artists' shuffle button, for this artist (2026-11-10, the user's request), at the far right.
            PanelHeader(
                "Albums",
                action = if (artist != null && albums.isNotEmpty()) ({ IconButtonSmall(AppIcons.SHUFFLE, { actions.shuffleArtist(artist.name) }, label = "Shuffle artist") }) else null,
            )
            val list = rememberLazyListState()
            val index = remember(albums) { AlbumYearIndex.of(albums) }
            val pad = t.dp("panel.listPadding")
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                // Rows are a fixed height, so whether the list scrolls is known before it is drawn.
                val scrolls = t.dp("albums.rowHeight") * albums.size + pad * 2 > maxHeight
                val strip = scrolls && showsStrip(index)
                val padEnd = if (strip) stripRoom() else pad
                LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(start = pad, top = pad, end = padEnd, bottom = pad)) {
                    items(albums, key = { it.title }, contentType = { "album" }) { album ->
                        AlbumRow(album, "${album.yearDisplay} · ${album.trackCountDisplay}", browse, selected = album.title == selected) {
                            selected = album.title
                            actions.open(Route.Album(album.artistName, album.title))
                        }
                    }
                }
                if (strip) IndexStrip(index, list, indexPreview)
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

/**
 * Album (spec 4.3): the header block, then # · Song · Time. A [chosenSong] (a path:
 * Listening History's song rows, spec 4.7) is drawn selected, and the list opens
 * scrolled to it, as the desktop selects the song in its album.
 */
@Composable
fun AlbumScreen(artistName: String, title: String, browse: Browse, actions: AppActions, chosenSong: String? = null) {
    val t = LocalAppTheme.current
    val album = browse.album(artistName, title)
    val chosenIndex = album?.tracks?.indexOfFirst { it.filePath.equals(chosenSong, ignoreCase = true) } ?: -1
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = chosenIndex.coerceAtLeast(0))
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
            val numberWidth = numberColumnWidth(album?.tracks.orEmpty().map { Display.number(it.trackNumber) })
            TableHeader(numberWidth)
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = t.dp("album.listPaddingX"), vertical = t.dp("album.listPaddingY")),
            ) {
                items(album?.tracks.orEmpty(), key = { it.filePath }, contentType = { "song" }) { track ->
                    SongRow(track, browse, actions, numberWidth, chosen = chosenIndex >= 0 && track === album?.tracks?.get(chosenIndex))
                }
            }
        }
    }
}

/**
 * The # column's width: `album.columns.number.width` (two digits), or the album's
 * longest number if that is wider (100+ tracks, a large font size). Numbers use
 * tabular figures, so the longest is the widest. The header and every row share it.
 */
@Composable
private fun numberColumnWidth(numbers: List<String>): Dp {
    val t = LocalAppTheme.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = t.text("album.cellSize", "color.text.muted")
    val longest = numbers.maxByOrNull { it.length }.orEmpty()
    val measured = remember(longest, style, density) {
        if (longest.isEmpty()) 0 else measurer.measure(longest, style, maxLines = 1).size.width
    }
    return maxOf(t.dp("album.columns.number.width"), with(density) { measured.toDp() })
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

/** "# · SONG · TIME" (spec 4.3), lined up with the rows' columns; "#" left-aligned as the numbers are. */
@Composable
private fun TableHeader(numberWidth: Dp) {
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
            Line("#", style, Modifier.width(numberWidth))
            Line("SONG", style, Modifier.weight(1f))
            Line("TIME", style.copy(textAlign = TextAlign.End), Modifier.width(t.dp("album.columns.time.width")))
        }
        Divider(t)
    }
}

/**
 * A song in the Album table: the track number, left-aligned at the row's start
 * (the playing song shows a solid `accent.playback` triangle in its place), the
 * title and the time. The # shows the tag's track number, blank when untagged,
 * as the desktop's table does; discs follow each other with no disc column.
 * The desktop keeps a triangle slot left of the number; the phone gives that
 * width to the titles (the user's call, 2026-11-06).
 */
@Composable
private fun SongRow(track: Track, browse: Browse, actions: AppActions, numberWidth: Dp, chosen: Boolean = false) {
    val t = LocalAppTheme.current
    val playing = browse.isPlaying(track)
    val unavailable = browse.isUnavailable(track)
    val weight = if (playing) 600 else 400
    val cell = if (playing) "color.accent.playbackText" else "color.text.secondary"
    ListRow(
        t.dp("album.rowHeight"),
        t.dp("album.rowPaddingX"),
        look = if (playing) RowLook.NowPlaying else if (chosen) RowLook.Selected else RowLook.Normal,
        unavailable = unavailable,
        gap = t.dp("album.columnGap"),
        onClick = if (unavailable) null else ({ actions.play(track) }),
    ) {
        Box(Modifier.width(numberWidth), contentAlignment = Alignment.CenterStart) {
            if (playing) {
                Icon(AppIcons.TRIANGLE, t.dp("album.triangle.width"), t.dp("album.triangle.height"), "color.accent.playback", "album.triangle.strokeOnGrid")
            } else {
                Line(Display.number(track.trackNumber), t.text("album.cellSize", "color.text.muted", weight))
            }
        }
        Line(track.displayTitle, t.text("album.songSize", if (playing) "color.accent.playbackText" else "color.text.primary", weight), Modifier.weight(1f))
        Line(
            Display.time(track.durationMs),
            t.text("album.cellSize", cell, weight).copy(textAlign = TextAlign.End),
            Modifier.width(t.dp("album.columns.time.width")),
        )
    }
}

/**
 * A playlist (2026-11-12, the user's request: the desktop's Liked and Recently Added),
 * opened from the Playlists tab: Album's screen with a playlist header, the # column
 * holding the song's place in the playlist (Recently Added's songs run album by album,
 * so there the track number, as on the desktop), and Search's song rows, since a
 * playlist mixes artists: the title over "Artist · Album", and the time. A tap plays
 * the playlist from that song and opens Now Playing, as a song in an album does.
 * [shown] is null while the songs are being found; [ShownPlaylist.empty] is said
 * in place of the table when there are none.
 */
@Composable
fun PlaylistScreen(shown: ShownPlaylist?, browse: Browse, actions: AppActions) {
    val t = LocalAppTheme.current
    ScreenFrame(
        topBar = {
            TopBar(t.dp("topBar.paddingStart"), t.dp("topBar.paddingEnd"), t.dp("topBar.gap")) {
                Line("Playlists", t.text("topBar.parentLabelSize", "color.text.muted"), Modifier.weight(1f))
                SearchButton(actions)
            }
        },
        bottom = { MiniPlayerBar(browse, actions) },
    ) {
        PanelSurface(Modifier.weight(1f)) {
            if (shown != null) {
                PlaylistHeader(shown)
                val empty = shown.empty
                if (empty != null) {
                    Column(
                        Modifier.fillMaxWidth().padding(t.dp("settings.rowPaddingX")),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(t.dp("artists.lineGap")),
                    ) {
                        Line(empty.first, t.text("artists.nameSize", "color.text.primary").copy(textAlign = TextAlign.Center))
                        Line(empty.second, t.text("artists.countSize", "color.text.muted").copy(textAlign = TextAlign.Center), maxLines = 2)
                    }
                } else {
                    val numbers = remember(shown) {
                        shown.tracks.mapIndexed { i, track -> if (shown.numbered) Display.count(i + 1) else Display.number(track.trackNumber) }
                    }
                    val numberWidth = numberColumnWidth(numbers)
                    TableHeader(numberWidth)
                    LazyColumn(contentPadding = PaddingValues(horizontal = t.dp("album.listPaddingX"), vertical = t.dp("album.listPaddingY"))) {
                        itemsIndexed(shown.tracks, key = { _, track -> track.filePath }, contentType = { _, _ -> "song" }) { i, track ->
                            PlaylistSongRow(track, numbers[i], numberWidth, browse) { actions.playIn(track, shown.tracks) }
                        }
                    }
                }
            }
        }
    }
}

/** The album header's block for a playlist: its first song's cover, the name, "Modified 2026-11-12" and "12 tracks · 42:58". */
@Composable
private fun PlaylistHeader(shown: ShownPlaylist) {
    val t = LocalAppTheme.current
    Column {
        Row(
            Modifier.fillMaxWidth().padding(t.dp("album.headerPadding")),
            horizontalArrangement = Arrangement.spacedBy(t.dp("album.artGap")),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlbumArt(shown.coverAlbum, t.dp("album.art"), "color.art.border", t.shadow("shadow.headerArt"), header = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(t.dp("album.lineGap"))) {
                Line(
                    shown.name,
                    t.text("album.titleSize", "color.text.heading", weight = 600, lineHeight = t.number("album.titleLineHeight")),
                    maxLines = t.number("album.titleMaxLines").toInt(),
                )
                Line(shown.line, t.text("album.artistSize", "color.text.muted"))
                Line(
                    if (shown.tracks.isEmpty()) shown.trackCount else "${shown.trackCount} · ${Display.time(shown.durationMs)}",
                    t.text("album.detailsSize", "color.text.secondary"),
                )
            }
        }
        Divider(t)
    }
}

/** A playlist's song: [number] in Album's # column (the triangle while playing), then Search's two lines and the time. */
@Composable
private fun PlaylistSongRow(track: Track, number: String, numberWidth: Dp, browse: Browse, onClick: () -> Unit) {
    val t = LocalAppTheme.current
    val playing = browse.isPlaying(track)
    val unavailable = browse.isUnavailable(track)
    val weight = if (playing) 600 else 400
    val sub = if (playing) "color.accent.playbackText" else "color.text.secondary"
    ListRow(
        t.dp("search.songRowHeight"),
        t.dp("album.rowPaddingX"),
        look = if (playing) RowLook.NowPlaying else RowLook.Normal,
        unavailable = unavailable,
        gap = t.dp("album.columnGap"),
        onClick = if (unavailable) null else onClick,
    ) {
        Box(Modifier.width(numberWidth), contentAlignment = Alignment.CenterStart) {
            if (playing) {
                Icon(AppIcons.TRIANGLE, t.dp("album.triangle.width"), t.dp("album.triangle.height"), "color.accent.playback", "album.triangle.strokeOnGrid")
            } else {
                Line(number, t.text("album.cellSize", "color.text.muted", weight))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(t.dp("artists.lineGap"))) {
            Line(track.displayTitle, t.text("album.songSize", if (playing) "color.accent.playbackText" else "color.text.primary", weight))
            Line(listOf(track.artist.ifBlank { track.groupingArtist }, track.album).filter { it.isNotBlank() }.joinToString(" · "), t.text("artists.countSize", sub))
        }
        Line(
            Display.time(track.durationMs),
            t.text("album.cellSize", sub, weight).copy(textAlign = TextAlign.End),
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
