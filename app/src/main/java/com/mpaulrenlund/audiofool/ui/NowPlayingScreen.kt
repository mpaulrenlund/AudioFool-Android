package com.mpaulrenlund.audiofool.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.mpaulrenlund.audiofool.art.CoverColors
import com.mpaulrenlund.audiofool.core.format.Display
import com.mpaulrenlund.audiofool.core.library.MusicLibrary
import com.mpaulrenlund.audiofool.core.model.Track
import com.mpaulrenlund.audiofool.core.playback.QualityLines
import com.mpaulrenlund.audiofool.core.playback.RepeatMode
import com.mpaulrenlund.audiofool.core.theme.ThemeColor
import com.mpaulrenlund.audiofool.library.CoverArt
import com.mpaulrenlund.audiofool.playback.NowPlaying
import com.mpaulrenlund.audiofool.playback.SystemControls
import com.mpaulrenlund.audiofool.theme.AppTheme
import com.mpaulrenlund.audiofool.theme.LocalAppTheme
import com.mpaulrenlund.audiofool.theme.WindowBackground
import com.mpaulrenlund.audiofool.theme.toCompose
import com.mpaulrenlund.audiofool.theme.tokenSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/** What Now Playing can ask for. */
class NowPlayingActions(
    val playPause: () -> Unit = {},
    val previous: () -> Unit = {},
    val next: () -> Unit = {},
    val seek: (Long) -> Unit = {},
    val cycleRepeat: () -> Unit = {},
    val toggleShuffle: () -> Unit = {},
    /** The cover menu's Bit-Perfect row (spec 6.4; the BP button until 2026-11-10); not called while it is greyed out. */
    val toggleBitPerfect: () -> Unit = {},
    /** The cover menu's Analyze (0.6.0): opens the Analysis screen on the song. */
    val analyze: (Track) -> Unit = {},
    /** The cover menu's Resolution row (spec 5.3): opens the cover full screen, to zoom. */
    val openCover: (Track) -> Unit = {},
    /** The cover menu's Genius (spec 5.2): opens the Genius screen on the song, looked up afresh. */
    val genius: (Track) -> Unit = {},
    /** A tap on the artist's name (spec 5): opens that artist's albums. */
    val openArtist: (Track) -> Unit = {},
    /** A tap on the album's name (spec 5): opens the album, the song chosen. */
    val openAlbum: (Track) -> Unit = {},
)

/**
 * Where Now Playing's artist line leads (spec 5): the albums of the artist named
 * on it, where the library lists that artist; otherwise of the artist the song's
 * album is under (a guest on someone else's album, a compilation). Null when the
 * library doesn't hold the song.
 */
fun artistRouteFor(track: Track, library: MusicLibrary?): Route.Albums? {
    val album = library?.albumHolding(track.filePath) ?: return null
    val named = library.artists.firstOrNull { it.name == track.artist }?.name
    return Route.Albums(named ?: album.artistName)
}

/** Where Now Playing's album line leads (spec 5): the song's album with the song chosen, as a Listening History song row opens it. */
fun albumRouteFor(track: Track, library: MusicLibrary?): Route.Album? =
    library?.albumHolding(track.filePath)?.let { Route.Album(it.artistName, it.title, track.filePath) }

/**
 * Now Playing (spec 5), opened from the mini player. No buttons at the top:
 * system back closes it. A sideways swipe on the art or the title block skips:
 * left for Next, right for Previous. From the top: the art, the title block
 * with the track count, the quality lines, the waveform seekbar with the times,
 * the controls row (Repeat at the left, the transport centred, Shuffle at the right;
 * 2026-11-10) and the output readout. A tap on the art opens its menu (0.6.0; a long
 * press until 2026-11-10), which holds Bit-Perfect and ends with the logo menu's rows
 * ([appActions], [version]; 0.7.4).
 *
 * The colour here is the cover's (the user's calls, 2026-11-09 and 2026-11-10; spec 5,
 * Cover colour): the seekbar's played part, the transport's icons and the ring and glow
 * round Play/Pause, Repeat and Shuffle when on, and the artist's name (the same colour, since 2026-11-11) take the cover's
 * main colour (the glow at the top of the screen did too, until it went on 2026-11-10), fitted to the theme
 * ([AppTheme.coverAccent]); a black and white cover's is a grey (2026-11-11). No cover
 * leaves them the theme's. The mini player's buttons take the same colour ([MiniPlayerBar]).
 *
 * [artMenuForPictures] is ThemeLab's: the menu open in the art's middle, showing that resolution.
 * So are [coverForPictures], a made-up cover shown as if it had been read from the song, and
 * [scrubForPictures], a finger held on the seekbar that far along it.
 */
@Composable
fun NowPlayingScreen(
    nowPlaying: NowPlaying,
    actions: NowPlayingActions,
    appActions: AppActions = AppActions(),
    version: String = "",
    artMenuForPictures: String? = null,
    coverForPictures: Bitmap? = null,
    scrubForPictures: Float? = null,
) {
    val t = LocalAppTheme.current
    val track = nowPlaying.track

    // The whole screen, bars included: the spec's sizes are fractions of it.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        val padX = t.dp("nowPlaying.paddingX")
        // Spec 5: the smaller of (width − 40) and (height − artHeightReserve), so the M21 keeps room for the controls.
        // The reserve holds artReserveText of text, which grows with Android's font size (session 5: at 1.3 the
        // M21 lost the readout off the bottom).
        val textGrowth = t.dp("nowPlaying.artReserveText") * (LocalDensity.current.fontScale - 1f).coerceAtLeast(0f)
        val heightForArt = screenHeight - t.dp("nowPlaying.artHeightReserve") - textGrowth
        val art = minOf(maxWidth - padX * 2, heightForArt)
        // Spare height (0.2.3, the user's call): where the width limits the cover (the S24), the height it
        // can't use. The waveform takes a share and the space below the times grows toward the space above,
        // so it isn't one gap over the quality line. None on the M21, which stays as it was.
        val spare = (heightForArt - art).coerceAtLeast(Dp(0f))
        val waveformGrowth = minOf(spare * t.number("nowPlaying.waveformGrowth.ofSpare").toFloat(), t.dp("nowPlaying.waveformGrowth.max"))
        val aboveWeight = t.number("nowPlaying.spaceAboveQualityWeight").toFloat()
        val belowWeight = t.number("nowPlaying.spaceBelowTimesWeight").toFloat()
        val evenness = (spare / t.dp("nowPlaying.spareEvenAt")).coerceIn(0f, 1f)
        // 0.2.4 (the user's call): the S24's six buttons sat too low to press comfortably; spare height
        // between them and the readout lifts them, the readout staying put.
        val controlsLift = minOf(spare * t.number("nowPlaying.controlsLift.ofSpare").toFloat(), t.dp("nowPlaying.controlsLift.max"))
        // 0.7.1 (the user's call): the S24's readout sat awkwardly close to the bottom. Part of that
        // gap goes under the readout instead, so it rises and the buttons stay where they were.
        val readoutLift = minOf(spare * t.number("nowPlaying.readoutLift.ofSpare").toFloat(), t.dp("nowPlaying.readoutLift.max"), controlsLift)
        val cover = shownCover(track, art, coverForPictures)
        // Fitted to the theme as it is now, not kept from the last one: a theme switch gives the other theme's.
        val accent = remember(t, cover?.color) { cover?.color?.let { t.coverAccent(it) } }

        WindowBackground(t, Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(barInsets().only(WindowInsetsSides.Vertical))) {
                SwipeToSkip(track, art, actions) {
                    Box(Modifier.fillMaxWidth().padding(top = t.dp("nowPlaying.artTop")), contentAlignment = Alignment.Center) {
                        BigArt(nowPlaying, art, cover, actions, appActions, version, artMenuForPictures)
                    }
                    TitleBlock(nowPlaying, track, accent, actions)
                }
                Spacer(Modifier.weight(aboveWeight))
                Column(Modifier.padding(horizontal = padX)) {
                    track?.let { Quality(it) }
                    Seekbar(nowPlaying, actions.seek, t.dp("nowPlaying.waveformHeight") + waveformGrowth, accent, scrubForPictures)
                }
                Spacer(Modifier.weight(belowWeight + (aboveWeight - belowWeight) * evenness))
                Controls(nowPlaying, actions, screenHeight, accent)
                Spacer(Modifier.height(controlsLift - readoutLift))
                Line(
                    nowPlaying.readout,
                    t.text("nowPlaying.outputSize", "color.text.muted").copy(textAlign = TextAlign.Center),
                    Modifier.fillMaxWidth().padding(start = padX, end = padX, bottom = t.dp("nowPlaying.outputBottom") + readoutLift),
                )
            }
        }
    }
}

/**
 * The art and title block, swiped sideways to skip (the user's call, 0.2.0): left
 * is Next, right is Previous (the button's rule, so more than 3 s in it restarts
 * the song). They follow the finger, fading toward `swipeFadeTo` over the art's
 * width; past `swipeSkipDistance`, or flung faster than `swipeSkipVelocity`, the
 * song changes and they are back in place at once with the new song; short of it
 * they spring back. The seekbar keeps its own drag.
 *
 * 0.2.0 left them stuck part-way off the screen after every skip on the S24 (never
 * on the emulator): the spring back ran after the skip and something stopped it.
 * Now a skip involves no animation at all, the finger's place is set as it moves
 * (not by a coroutine per move, which could land after the spring had started and
 * stop it), and any song change puts them back in place.
 */
@Composable
private fun SwipeToSkip(track: Track?, art: Dp, actions: NowPlayingActions, content: @Composable ColumnScope.() -> Unit) {
    val t = LocalAppTheme.current
    val scope = rememberCoroutineScope()
    var offset by remember { mutableFloatStateOf(0f) }
    var springBack by remember { mutableStateOf<Job?>(null) }
    var dragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val skipAtPx = with(density) { t.dp("nowPlaying.swipeSkipDistance").toPx() }
    val flingPx = with(density) { t.dp("nowPlaying.swipeSkipVelocity").toPx() }
    val artPx = with(density) { art.toPx() }
    val fadeTo = t.number("nowPlaying.swipeFadeTo").toFloat()
    LaunchedEffect(track?.filePath) {
        if (!dragging) {
            springBack?.cancel()
            offset = 0f
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .draggable(
                rememberDraggableState { delta -> offset += delta },
                Orientation.Horizontal,
                onDragStarted = {
                    springBack?.cancel()
                    dragging = true
                },
                onDragStopped = { velocity ->
                    dragging = false
                    val from = offset
                    // A fling counts only away from the middle, not one flicked back.
                    val skip = when {
                        from < -skipAtPx || (velocity < -flingPx && from < 0) -> actions.next
                        from > skipAtPx || (velocity > flingPx && from > 0) -> actions.previous
                        else -> null
                    }
                    if (skip != null) {
                        offset = 0f
                        skip()
                    } else {
                        springBack = scope.launch { animate(from, 0f) { value, _ -> offset = value } }
                    }
                },
            )
            .graphicsLayer {
                translationX = offset
                alpha = 1f - (1f - fadeTo) * (abs(offset) / artPx).coerceAtMost(1f)
            },
        content = content,
    )
}

/**
 * The cover to show for [track], read at the art's size off the main thread, with
 * its colour from [CoverColors] (the mini player's too). Until the next song's cover
 * has been read the last one stays, picture and colour: an album's songs share a
 * cover, and the placeholder and the theme's colour would blink between them
 * otherwise (the placeholder did, up to 0.12.0). Null before the first one is in; a
 * song without a cover gives one with no picture.
 * [forPictures] is ThemeLab's cover, which can't read one.
 */
@Composable
private fun shownCover(track: Track?, side: Dp, forPictures: Bitmap?): BigCover? {
    val px = with(LocalDensity.current) { side.roundToPx() }
    var cover by remember { mutableStateOf(forPictures?.let { BigCover.of(it) }) }
    val colors = LocalCoverColors.current
    if (track != null && LocalArt.current != null && colors != null) {
        LaunchedEffect(track.filePath, px) { cover = withContext(Dispatchers.IO) { BigCover.read(track, px, colors) } }
    }
    return cover.takeIf { track != null }
}

/**
 * The cover, square, with `shadow.headerArt`; the full picture decoded at its size.
 * No border round a cover (the user's call, 0.2.2: it read as a light frame); the
 * placeholder keeps its 1 dp `art.border`, or its square would vanish into the page.
 * A tap opens its menu at the finger (2026-11-10, the user's call); so does a long
 * press, with a haptic tick, as it did from 0.6.0.
 */
@Composable
private fun BigArt(nowPlaying: NowPlaying, side: Dp, cover: BigCover?, actions: NowPlayingActions, appActions: AppActions, version: String, menuForPictures: String?) {
    val t = LocalAppTheme.current
    val track = nowPlaying.track
    val density = LocalDensity.current
    val px = with(density) { side.roundToPx() }
    val bitmap = cover?.image
    // Where the finger pressed, while the menu is open (0.6.0).
    var menuAt by remember(track?.filePath) { mutableStateOf(menuForPictures?.let { IntOffset(px / 2, px / 2) }) }
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier
            .size(side)
            .pointerInput(track?.filePath) {
                if (track == null) return@pointerInput
                // A sideways drag (the skip) takes the touch from this before it is a tap or a long press.
                detectTapGestures(
                    onTap = { at -> menuAt = IntOffset(at.x.roundToInt(), at.y.roundToInt()) },
                    onLongPress = { at ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuAt = IntOffset(at.x.roundToInt(), at.y.roundToInt())
                    },
                )
            }
            // For a screen reader: the cover is the menu's button, opened in its middle.
            .semantics {
                contentDescription = "Cover"
                if (track != null) onClick("Menu") {
                    menuAt = IntOffset(px / 2, px / 2)
                    true
                }
            }
            .tokenSurface(
                RectangleShape,
                t.color("color.panel.bg"),
                t.shadow("shadow.headerArt"),
                if (bitmap == null) t.color("color.art.border") else null,
                t.dp("borderWidth"),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            // The desktop's album-header placeholder, the biggest art it draws.
            Icon(AppIcons.MUSIC_NOTE, t.desktopNumber("albumHeader.placeholderIconSize").dp, "color.text.muted")
        }
        val at = menuAt
        if (at != null && track != null) {
            ArtMenu(
                at,
                menuForPictures ?: cover?.resolution ?: if (cover == null && LocalArt.current != null) "…" else "No cover",
                onClose = { menuAt = null },
                appActions,
                version,
                nowPlaying,
                actions.toggleBitPerfect,
                // Only where there is a picture to show (ThemeLab's menu stands for one).
                openCover = if (menuForPictures != null || bitmap != null) {
                    {
                        menuAt = null
                        actions.openCover(track)
                    }
                } else null,
                analyze = {
                    menuAt = null
                    actions.analyze(track)
                },
                genius = {
                    menuAt = null
                    actions.genius(track)
                },
            )
        }
    }
}

/**
 * The cover decoded at the art's size; the picture's own size, for the menu; and
 * its main colour (core `CoverColor` through [CoverColors]; null without a cover),
 * for the playback controls.
 */
private class BigCover(val image: ImageBitmap?, val width: Int, val height: Int, val color: Int?) {
    /** "1200 × 1200", as the desktop's art viewer gives it; "No cover" when there is none. */
    val resolution: String get() = if (width > 0 && height > 0) "$width × $height" else "No cover"

    companion object {
        fun read(track: Track, px: Int, colors: CoverColors): BigCover {
            val bytes = CoverArt.read(track)?.bytes
            val color = colors.load(track) { bytes }.color
            if (bytes == null) return BigCover(null, 0, 0, color)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val bitmap = SystemControls.decode(bytes, px)
            return BigCover(bitmap?.asImageBitmap(), bounds.outWidth, bounds.outHeight, color)
        }

        /** ThemeLab's made-up cover, as [read] would give it. */
        fun of(bitmap: Bitmap) = BigCover(bitmap.asImageBitmap(), bitmap.width, bitmap.height, CoverColors.main(bitmap))
    }
}

/**
 * The cover menu (0.6.0, the user's call): a tap on the cover opens it at the finger
 * (a long press until 2026-11-10), kept `artMenu.edgeMargin` inside the screen. The
 * logo menu's look. The first row is the cover's resolution, which opens the cover
 * full screen ([openCover], spec 5.3; 2026-11-10, the user's call; information only
 * until then, and still where there is no cover); then Analyze, which opens the Analysis screen; Bit-Perfect (spec 6.4, 2026-11-10, the
 * user's call: the BP button moved here), checked while on, which switches it and
 * keeps the menu open; and Genius, which opens the Genius screen (spec 5.2); then
 * the logo menu's Theme and version (0.7.4, the user's call; Listening History,
 * Rescan Library, Settings and Statistics are the logo menu's only since 2026-11-10,
 * the user's call), so all but the resolution are in alphabetical order with the
 * version last.
 */
@Composable
private fun ArtMenu(
    at: IntOffset,
    resolution: String,
    onClose: () -> Unit,
    appActions: AppActions,
    version: String,
    nowPlaying: NowPlaying,
    toggleBitPerfect: () -> Unit,
    openCover: (() -> Unit)?,
    analyze: () -> Unit,
    genius: () -> Unit,
) {
    val t = LocalAppTheme.current
    val density = LocalDensity.current
    val room = with(density) { menuShadowRoom().roundToPx() }
    val margin = with(density) { t.dp("artMenu.edgeMargin").roundToPx() }
    val position = remember(at, room, margin) { AtFinger(at, room, margin) }
    Popup(popupPositionProvider = position, onDismissRequest = onClose, properties = PopupProperties(focusable = true)) {
        MenuSurface(t.dp("artMenu.width")) {
            MenuRow("Resolution", value = resolution, onClick = openCover)
            MenuRow("Analyze", onClick = analyze)
            // The check in Bit-Perfect's own "on" colour, as Settings' dot (spec 4.5); greyed out where it can't work (spec 6.2).
            MenuCheckRow("Bit-Perfect", nowPlaying.bitPerfect, "color.status.bitPerfectOn", toggleBitPerfect, greyed = nowPlaying.bitPerfectGreyed)
            MenuRow("Genius", onClick = genius)
            AppMenuRows(onClose, appActions, version, allRows = false)
        }
    }
}

/** The menu's top-left corner at the finger, moved back inside the window (less [margin]) where it would run off it. */
private class AtFinger(private val at: IntOffset, private val room: Int, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        // The popup's window holds the shadow's room round the menu; the menu itself is what keeps inside.
        fun place(wanted: Int, size: Int, window: Int) = wanted.coerceAtMost(window - margin - size + room).coerceAtLeast(margin - room)
        return IntOffset(
            place(anchorBounds.left + at.x - room, popupContentSize.width, windowSize.width),
            place(anchorBounds.top + at.y - room, popupContentSize.height, windowSize.height),
        )
    }
}

/**
 * Title, artist and album, left-aligned, with the track count "3 / 10" right of the
 * title. The artist's name is in the seekbar's and Play/Pause's colour (2026-11-11, the
 * user's call): the cover's ([accent]), or `accent.playback` without one. Up to then it was
 * the cover's colour fitted to `accent.blue`, and `accent.blue` without one. A tap on
 * the artist's name opens that artist's albums, a tap on the album's name the album with the song chosen (spec 5); each shows the press, as a
 * row does. A sideways drag across them still skips.
 */
@Composable
private fun TitleBlock(nowPlaying: NowPlaying, track: Track?, accent: ThemeColor?, actions: NowPlayingActions) {
    val t = LocalAppTheme.current
    val lineShape = RoundedCornerShape(t.radius("radius.row"))
    Row(
        Modifier.fillMaxWidth().padding(start = t.dp("nowPlaying.paddingX"), end = t.dp("nowPlaying.paddingX"), top = t.dp("nowPlaying.artToText")),
        horizontalArrangement = Arrangement.spacedBy(t.dp("nowPlaying.trackCountGap")),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(t.dp("nowPlaying.lineGap"))) {
            Line(
                track?.displayTitle ?: "",
                t.text("nowPlaying.titleSize", "color.text.heading", weight = t.number("nowPlaying.titleWeight").toInt(), lineHeight = t.number("nowPlaying.titleLineHeight")),
            )
            val artistStyle = t.text("nowPlaying.artistSize", "color.accent.playback", weight = t.number("nowPlaying.artistWeight").toInt())
            Line(
                track?.let { it.artist.ifBlank { it.groupingArtist } } ?: "",
                if (accent != null) artistStyle.copy(color = accent.toCompose()) else artistStyle,
                Modifier.tappable(lineShape, track?.let { { actions.openArtist(it) } }),
            )
            Line(track?.album ?: "", t.text("nowPlaying.albumSize", "color.text.secondary"), Modifier.tappable(lineShape, track?.let { { actions.openAlbum(it) } }))
        }
        if (nowPlaying.queueSize > 0) {
            Line(
                // With thousands separators: the whole library shuffled is 27,115 on the M21 (0.9.0).
                "${Display.count(nowPlaying.queueIndex + 1)} / ${Display.count(nowPlaying.queueSize)}",
                t.text("nowPlaying.trackCountSize", "color.text.muted"),
                Modifier.padding(top = t.dp("nowPlaying.trackCountTop")),
            )
        }
    }
}

/** One right-aligned line over the seekbar: "FLAC · 1,901 kbps · 24-Bit · 48 kHz" (spec 5; 0.2.2). */
@Composable
private fun Quality(track: Track) {
    val t = LocalAppTheme.current
    Line(
        QualityLines.of(track),
        t.text("nowPlaying.qualitySize", "color.text.secondary").copy(textAlign = TextAlign.End),
        Modifier.fillMaxWidth().padding(top = t.dp("nowPlaying.qualityTop")),
    )
}

/**
 * The waveform seekbar (spec 5): full width, [height] tall (`waveformHeight`, more
 * with spare height), the track's waveform as one filled shape mirrored about the
 * middle line (0.2.4, the user's call; 96 hard-edged bars before), the played part
 * in the cover's colour ([accent]; `slider.seekFill` without one), the rest
 * `slider.track`, split at the exact place so it
 * glides while playing. Until the waveform is ready, a flat bar `waveformFloor` high. Drag or tap to seek; the bar follows
 * the finger and the seek happens on letting go. While a finger drags it, the time
 * it would seek to shows in a bubble over the finger ([ScrubBubble]). The elapsed
 * and total times sit under it. [scrubForPictures] is ThemeLab's finger, held that
 * far along the bar.
 */
@Composable
private fun Seekbar(nowPlaying: NowPlaying, seek: (Long) -> Unit, height: Dp, accent: ThemeColor?, scrubForPictures: Float?) {
    val t = LocalAppTheme.current
    val duration = nowPlaying.durationMs
    // Read every frame while playing, and only then; the rest of the screen doesn't
    // recompose for it. Paused, the held place is read (it is state, so a seek shows).
    var ticked by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(scrubForPictures) }
    val ticking = nowPlaying.live && nowPlaying.isPlaying
    LaunchedEffect(nowPlaying, ticking) {
        if (ticking) while (true) withFrameMillis { if (dragging == null) ticked = nowPlaying.positionMs() }
    }
    val position = if (ticking) ticked else nowPlaying.positionMs()
    val fraction = dragging ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val floor = t.dp("nowPlaying.waveformFloor")
    val played = accent?.toCompose() ?: t.color("color.slider.seekFill")
    val rest = t.color("color.slider.track")
    val levels = nowPlaying.waveform
    val shape = remember(levels) { WaveShape(levels) }

    Box(Modifier.fillMaxWidth().padding(top = t.dp("nowPlaying.waveformTop"))) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                // For a screen reader: a slider it can name, read and move.
                .semantics {
                    contentDescription = "Seek"
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    setProgress { to ->
                        if (duration > 0) seek((to.coerceIn(0f, 1f) * duration).toLong())
                        duration > 0
                    }
                }
                .pointerInput(duration) {
                    if (duration <= 0) return@pointerInput
                    detectTapGestures { offset -> seek((offset.x / size.width * duration).toLong().coerceIn(0, duration)) }
                }
                .pointerInput(duration) {
                    if (duration <= 0) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> dragging = (offset.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragging?.let {
                                seek((it * duration).toLong())
                                ticked = (it * duration).toLong()
                            }
                            dragging = null
                        },
                        onDragCancel = { dragging = null },
                        onHorizontalDrag = { change, _ -> dragging = (change.position.x / size.width).coerceIn(0f, 1f) },
                    )
                },
        ) {
            val path = shape.pathFor(size, floor.toPx())
            // The split falls at the exact place, the shape clipped there, as the desktop's WaveformView does.
            val split = fraction * size.width
            clipRect(right = split) { drawPath(path, played) }
            clipRect(left = split) { drawPath(path, rest) }
        }
        dragging?.let { ScrubBubble(Display.time((it * duration).toLong()), it) }
    }
    val shown = if (dragging != null) (fraction * duration).toLong() else position
    Row(Modifier.fillMaxWidth().padding(top = t.dp("nowPlaying.timesTop")), horizontalArrangement = Arrangement.SpaceBetween) {
        Line(Display.time(shown), t.text("nowPlaying.timeSize", "color.text.muted"))
        Line(Display.time(duration), t.text("nowPlaying.timeSize", "color.text.muted"))
    }
}

/**
 * The time under the finger while the seekbar is dragged (spec 5; the user's call,
 * 2026-11-09): big, on the lifted surface the index strips' bubble uses, centred
 * over the finger `scrubBubble.gap` above the bar and kept within the bar's ends.
 * It takes no room: the bar and everything round it stay where they are.
 * [fraction] is how far along the bar the finger is.
 */
@Composable
private fun ScrubBubble(time: String, fraction: Float) {
    val t = LocalAppTheme.current
    val gap = t.dp("nowPlaying.scrubBubble.gap")
    Box(
        Modifier
            .layout { measurable, constraints ->
                val bubble = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                val bar = constraints.maxWidth
                layout(0, 0) {
                    bubble.place(((bar * fraction).roundToInt() - bubble.width / 2).coerceIn(0, maxOf(0, bar - bubble.width)), -bubble.height - gap.roundToPx())
                }
            }
            .heightIn(min = t.dp("nowPlaying.scrubBubble.height"))
            .widthIn(min = t.dp("nowPlaying.scrubBubble.minWidth"))
            .tokenSurface(
                RoundedCornerShape(t.radius("radius.surface")),
                t.color("color.panel.bg"),
                t.shadow("shadow.headerArt") + t.shadow("shadow.panelHighlight"),
                t.color("color.panel.border"),
                t.dp("borderWidth"),
            )
            .padding(horizontal = t.dp("nowPlaying.scrubBubble.paddingX")),
        contentAlignment = Alignment.Center,
    ) {
        Line(time, t.text("nowPlaying.scrubBubble.textSize", "color.text.heading", weight = t.number("nowPlaying.scrubBubble.weight").toInt()))
    }
}

/**
 * The waveform's outline, ported from the desktop's `WaveformView.Build`: along
 * the top edge from left to right, then back along the bottom, one point per
 * level at the middle of its column, mirrored about the middle line. Silence
 * still gets the floor, so the shape never breaks up; with no levels it is a flat
 * bar the floor high. Built once per size, not every frame.
 */
private class WaveShape(private val levels: FloatArray?) {
    private var builtFor: Size? = null
    private val path = Path()

    fun pathFor(size: Size, floor: Float): Path {
        if (builtFor == size) return path
        builtFor = size
        path.reset()
        val middle = size.height / 2
        val levels = levels?.takeIf { it.isNotEmpty() } ?: floatArrayOf(0f)
        val reach = maxOf(0f, middle - floor / 2)
        val column = size.width / levels.size
        fun half(i: Int) = floor / 2 + levels[i].coerceIn(0f, 1f) * reach

        path.moveTo(0f, middle - half(0))
        for (i in levels.indices) path.lineTo((i + 0.5f) * column, middle - half(i))
        path.lineTo(size.width, middle - half(levels.size - 1))
        path.lineTo(size.width, middle + half(levels.size - 1))
        for (i in levels.indices.reversed()) path.lineTo((i + 0.5f) * column, middle + half(i))
        path.lineTo(0f, middle + half(0))
        path.close()
        return path
    }
}

/**
 * The controls row (spec 5; 2026-11-10, the user's calls): Previous, Play/Pause and
 * Next centred on the screen, Repeat at the left and Shuffle at the right, each
 * `paddingX` from its edge and level with Play/Pause's middle (Repeat over Shuffle at
 * the left until the user's second call that day). They keep the size they had
 * stacked, (Play/Pause − `modeGap`) / 2 within `modeButton.min` and `max`: 44 dp on
 * the S24, 35 on the M21. The transport's sizes are fractions of the screen height
 * between a min and a max. At least `modeToTransportGap` lies between Repeat and
 * Previous, and between Next and Shuffle: where the screen is too narrow for that
 * (the S24), the transport's gaps shrink, to `transportGap.fitMin` at most, and then
 * Previous and Next do.
 * [accent] is the cover's colour, for the transport's icons and for Repeat and
 * Shuffle when they are on (`control.repeatOn` and `control.shuffleOn` without one),
 * so the screen has one colour for "on".
 */
@Composable
private fun Controls(nowPlaying: NowPlaying, actions: NowPlayingActions, screenHeight: Dp, accent: ThemeColor?) {
    val t = LocalAppTheme.current
    fun scaled(path: String): Dp =
        (screenHeight.value * t.number("$path.ofScreenHeight")).coerceIn(t.number("$path.min"), t.number("$path.max")).dp
    val play = scaled("nowPlaying.playButton")
    val modeGap = t.dp("nowPlaying.modeGap")
    val mode = ((play - modeGap) / 2).coerceIn(t.dp("nowPlaying.modeButton.min"), t.dp("nowPlaying.modeButton.max"))
    val modeIcon = mode * t.number("nowPlaying.modeIconOfButton").toFloat()
    val padX = t.dp("nowPlaying.paddingX")

    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(top = t.dp("nowPlaying.controlsPaddingTop"), bottom = t.dp("nowPlaying.controlsPaddingBottom")),
    ) {
        // Play/Pause's middle is the screen's: what is left of each half for a gap and a skip button.
        val half = maxWidth / 2 - padX - mode - t.dp("nowPlaying.modeToTransportGap") - play / 2
        val fullSkip = scaled("nowPlaying.skipButton")
        val gap = minOf(scaled("nowPlaying.transportGap"), maxOf(t.dp("nowPlaying.transportGap.fitMin"), half - fullSkip))
        val skip = minOf(fullSkip, half - gap)
        val repeat = nowPlaying.repeat
        val off = t.color("color.control.iconOff")
        Box(Modifier.align(Alignment.CenterStart).padding(start = padX)) {
            RoundButton(mode, mode, mode, "Repeat", actions.cycleRepeat, state = repeat.name) {
                Icon(AppIcons.REPEAT, modeIcon, if (repeat == RepeatMode.Off) off else accent?.toCompose() ?: t.color("color.control.repeatOn"))
            }
            if (repeat == RepeatMode.One) RepeatOneBadge(Modifier.align(Alignment.TopEnd), accent)
        }
        TransportButtons(nowPlaying, actions, skip, play, gap, accent, Modifier.align(Alignment.Center))
        Box(Modifier.align(Alignment.CenterEnd).padding(end = padX)) {
            RoundButton(mode, mode, mode, "Shuffle", actions.toggleShuffle, state = if (nowPlaying.shuffle) "On" else "Off") {
                Icon(AppIcons.SHUFFLE, modeIcon, if (nowPlaying.shuffle) accent?.toCompose() ?: t.color("color.control.shuffleOn") else off)
            }
        }
    }
}

/**
 * Previous, Play/Pause and Next, [gap] apart, centred on Play/Pause. Their icons
 * are in the cover's colour ([accent]): Play/Pause's ring and glow too (Previous and
 * Next keep their plain faces, 2026-11-10, the user's call). Without one, Play/Pause
 * has `accent.playback` and the theme's ring and glow, and Previous and Next
 * `control.iconNeutral`. The glow shows only while playing (the user's call,
 * 2026-11-09): paused, the ring alone is left, so the glow says at a glance that
 * music is playing.
 */
@Composable
private fun TransportButtons(nowPlaying: NowPlaying, actions: NowPlayingActions, skip: Dp, play: Dp, gap: Dp, accent: ThemeColor?, modifier: Modifier = Modifier) {
    val t = LocalAppTheme.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
        val skipIcon = skip * t.number("nowPlaying.skipIconOfButton").toFloat()
        val skipColor = accent?.toCompose() ?: t.color("color.control.iconNeutral")
        val playing = nowPlaying.isPlaying
        RoundButton(skip, skip, skip, "Previous", actions.previous) { Icon(AppIcons.PREVIOUS, skipIcon, skipColor) }
        RoundButton(play, play, play, if (playing) "Pause" else "Play", actions.playPause, play = true, playTint = accent, playGlow = playing) {
            Icon(
                if (playing) AppIcons.PAUSE else AppIcons.PLAY,
                play * t.number("nowPlaying.playIconOfButton").toFloat(),
                accent?.toCompose() ?: t.color("color.accent.playback"),
            )
        }
        RoundButton(skip, skip, skip, "Next", actions.next) { Icon(AppIcons.NEXT, skipIcon, skipColor) }
    }
}

/**
 * Repeat One's badge, the desktop's (`playbackBar.repeatOneBadge`): a "1" in a
 * disc on the button's top-right corner, `outset` beyond it. The disc is the
 * cover's colour ([accent]), as the lit Repeat icon is; `control.repeatOn` without one.
 */
@Composable
private fun RepeatOneBadge(modifier: Modifier, accent: ThemeColor?) {
    val t = LocalAppTheme.current
    val disc = t.desktopNumber("playbackBar.repeatOneBadge.discSize").dp
    val outset = t.desktopNumber("playbackBar.repeatOneBadge.outset").dp
    Box(
        modifier
            .offset { IntOffset(outset.roundToPx(), -outset.roundToPx()) }
            .size(disc)
            .tokenSurface(CircleShape, accent?.toCompose() ?: t.color("color.control.repeatOn"), emptyList(), null, 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        // The size is the desktop badge's, set over the style's.
        Line(
            "1",
            t.text("nowPlaying.timeSize", "color.control.face", weight = t.desktopNumber("playbackBar.repeatOneBadge.weight").toInt())
                .copy(fontSize = t.desktopNumber("playbackBar.repeatOneBadge.size").sp),
        )
    }
}
