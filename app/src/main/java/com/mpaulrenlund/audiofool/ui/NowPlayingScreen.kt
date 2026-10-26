package com.mpaulrenlund.audiofool.ui

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpaulrenlund.audiofool.core.format.Display
import com.mpaulrenlund.audiofool.core.model.Track
import com.mpaulrenlund.audiofool.core.playback.QualityLines
import com.mpaulrenlund.audiofool.core.playback.RepeatMode
import com.mpaulrenlund.audiofool.library.CoverArt
import com.mpaulrenlund.audiofool.playback.NowPlaying
import com.mpaulrenlund.audiofool.playback.SystemControls
import com.mpaulrenlund.audiofool.theme.LocalAppTheme
import com.mpaulrenlund.audiofool.theme.WindowBackground
import com.mpaulrenlund.audiofool.theme.tokenSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** What Now Playing can ask for. */
class NowPlayingActions(
    val playPause: () -> Unit = {},
    val previous: () -> Unit = {},
    val next: () -> Unit = {},
    val seek: (Long) -> Unit = {},
    val cycleRepeat: () -> Unit = {},
    val toggleShuffle: () -> Unit = {},
    /** The BP button (spec 6.4); not called while it is greyed out. */
    val toggleBitPerfect: () -> Unit = {},
)

/**
 * Now Playing (spec 5), opened from the mini player. No buttons at the top:
 * system back closes it. A sideways swipe on the art or the title block skips:
 * left for Next, right for Previous. From the top: the art, the title block
 * with the track count, the quality lines, the waveform seekbar with the times,
 * the controls row (Repeat / Shuffle / BP on the left, the transport on the
 * right for a right thumb) and the output readout.
 */
@Composable
fun NowPlayingScreen(nowPlaying: NowPlaying, actions: NowPlayingActions) {
    val t = LocalAppTheme.current
    val track = nowPlaying.track

    WindowBackground(t, Modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(barInsets().only(WindowInsetsSides.Vertical)),
        ) {
            val screenHeight = maxHeight + barHeights()
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

            Column(Modifier.fillMaxSize()) {
                SwipeToSkip(track, art, actions) {
                    Box(Modifier.fillMaxWidth().padding(top = t.dp("nowPlaying.artTop")), contentAlignment = Alignment.Center) {
                        BigArt(track, art)
                    }
                    TitleBlock(nowPlaying, track)
                }
                Spacer(Modifier.weight(aboveWeight))
                Column(Modifier.padding(horizontal = padX)) {
                    track?.let { Quality(it) }
                    Seekbar(nowPlaying, actions.seek, t.dp("nowPlaying.waveformHeight") + waveformGrowth)
                }
                Spacer(Modifier.weight(belowWeight + (aboveWeight - belowWeight) * evenness))
                Controls(nowPlaying, actions, screenHeight)
                Spacer(Modifier.height(controlsLift))
                Line(
                    nowPlaying.readout,
                    t.text("nowPlaying.outputSize", "color.text.muted").copy(textAlign = TextAlign.Center),
                    Modifier.fillMaxWidth().padding(start = padX, end = padX, bottom = t.dp("nowPlaying.outputBottom")),
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

/** The status and navigation bars' heights: the spec's sizes are fractions of the whole screen. */
@Composable
private fun barHeights(): Dp {
    val density = LocalDensity.current
    val insets = barInsets()
    return with(density) { (insets.getTop(this) + insets.getBottom(this)).toDp() }
}

/**
 * The cover, square, with `shadow.headerArt`; the full picture decoded at its size.
 * No border round a cover (the user's call, 0.2.2: it read as a light frame); the
 * placeholder keeps its 1 dp `art.border`, or its square would vanish into the page.
 */
@Composable
private fun BigArt(track: Track?, side: Dp) {
    val t = LocalAppTheme.current
    val px = with(LocalDensity.current) { side.roundToPx() }
    var image by remember(track?.filePath) { mutableStateOf<ImageBitmap?>(null) }
    if (track != null && LocalArt.current != null) {
        LaunchedEffect(track.filePath, px) {
            image = withContext(Dispatchers.IO) {
                CoverArt.read(track)?.bytes?.let { SystemControls.decode(it, px) }?.asImageBitmap()
            }
        }
    }
    val bitmap = image
    Box(
        Modifier
            .size(side)
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
    }
}

/** Title, artist and album, left-aligned, with the track count "3 / 10" right of the title. */
@Composable
private fun TitleBlock(nowPlaying: NowPlaying, track: Track?) {
    val t = LocalAppTheme.current
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
            Line(track?.let { it.artist.ifBlank { it.groupingArtist } } ?: "", t.text("nowPlaying.artistSize", "color.accent.blue", weight = t.number("nowPlaying.artistWeight").toInt()))
            Line(track?.album ?: "", t.text("nowPlaying.albumSize", "color.text.secondary"))
        }
        if (nowPlaying.queueSize > 0) {
            Line(
                "${nowPlaying.queueIndex + 1} / ${nowPlaying.queueSize}",
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
 * `slider.seekFill`, the rest `slider.track`, split at the exact place so it
 * glides while playing. Until the waveform is ready, a flat bar `waveformFloor` high. Drag or tap to seek; the bar follows
 * the finger and the seek happens on letting go. The elapsed and total times
 * sit under it.
 */
@Composable
private fun Seekbar(nowPlaying: NowPlaying, seek: (Long) -> Unit, height: Dp) {
    val t = LocalAppTheme.current
    val duration = nowPlaying.durationMs
    // Read every frame while playing, and only then; the rest of the screen doesn't
    // recompose for it. Paused, the held place is read (it is state, so a seek shows).
    var ticked by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val ticking = nowPlaying.live && nowPlaying.isPlaying
    LaunchedEffect(nowPlaying, ticking) {
        if (ticking) while (true) withFrameMillis { if (dragging == null) ticked = nowPlaying.positionMs() }
    }
    val position = if (ticking) ticked else nowPlaying.positionMs()
    val fraction = dragging ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val floor = t.dp("nowPlaying.waveformFloor")
    val played = t.color("color.slider.seekFill")
    val rest = t.color("color.slider.track")
    val levels = nowPlaying.waveform
    val shape = remember(levels) { WaveShape(levels) }

    Canvas(
        Modifier
            .fillMaxWidth()
            .padding(top = t.dp("nowPlaying.waveformTop"))
            .height(height)
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
    val shown = if (dragging != null) (fraction * duration).toLong() else position
    Row(Modifier.fillMaxWidth().padding(top = t.dp("nowPlaying.timesTop")), horizontalArrangement = Arrangement.SpaceBetween) {
        Line(Display.time(shown), t.text("nowPlaying.timeSize", "color.text.muted"))
        Line(Display.time(duration), t.text("nowPlaying.timeSize", "color.text.muted"))
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
 * The controls row (spec 5): on the left Repeat and Shuffle side by side,
 * `modeGap` apart, their tops level with Play/Pause's, and BP centred under them
 * with its bottom level with Play/Pause's (the user's call, 0.2.2; a column of
 * three until then); on the right Previous, Play/Pause and Next, sized as a
 * fraction of the screen height between a min and a max. At least
 * `modeToTransportGap` lies between the two groups: where the screen is too
 * narrow for the transport's own gaps (the S24), they shrink to fit.
 */
@Composable
private fun Controls(nowPlaying: NowPlaying, actions: NowPlayingActions, screenHeight: Dp) {
    val t = LocalAppTheme.current
    fun scaled(path: String): Dp =
        (screenHeight.value * t.number("$path.ofScreenHeight")).coerceIn(t.number("$path.min"), t.number("$path.max")).dp
    val skip = scaled("nowPlaying.skipButton")
    val play = scaled("nowPlaying.playButton")
    val mode = t.dp("nowPlaying.modeButton")
    val modeGap = t.dp("nowPlaying.modeGap")
    val modeIcon = t.dp("nowPlaying.modeIcon")
    val padX = t.dp("nowPlaying.paddingX")

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val modesWidth = mode * 2 + modeGap
        val room = maxWidth - padX * 2 - modesWidth - t.dp("nowPlaying.modeToTransportGap") - skip * 2 - play
        val gap = minOf(scaled("nowPlaying.transportGap"), (room / 2).coerceAtLeast(Dp(0f)))
        Row(
            Modifier.fillMaxWidth().padding(
                start = padX,
                end = padX,
                top = t.dp("nowPlaying.controlsPaddingTop"),
                bottom = t.dp("nowPlaying.controlsPaddingBottom"),
            ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // As tall as Play/Pause: Repeat and Shuffle at its top, BP at its bottom.
            Box(Modifier.size(modesWidth, play)) {
                Row(Modifier.align(Alignment.TopStart), horizontalArrangement = Arrangement.spacedBy(modeGap)) {
                    val repeat = nowPlaying.repeat
                    Box {
                        RoundButton(mode, mode, mode, actions.cycleRepeat) {
                            Icon(AppIcons.REPEAT, modeIcon, if (repeat == RepeatMode.Off) "color.control.iconOff" else "color.control.repeatOn")
                        }
                        if (repeat == RepeatMode.One) RepeatOneBadge(Modifier.align(Alignment.TopEnd))
                    }
                    RoundButton(mode, mode, mode, actions.toggleShuffle) {
                        Icon(AppIcons.SHUFFLE, modeIcon, if (nowPlaying.shuffle) "color.control.shuffleOn" else "color.control.iconOff")
                    }
                }
                Box(Modifier.align(Alignment.BottomCenter)) {
                    val greyed = nowPlaying.bitPerfectGreyed
                    BitPerfectButton(nowPlaying.bitPerfect, if (greyed) null else actions.toggleBitPerfect, greyed)
                }
            }
            TransportButtons(nowPlaying, actions, skip, play, gap)
        }
    }
}

/** Previous, Play/Pause and Next, [gap] apart, centred on Play/Pause. */
@Composable
private fun TransportButtons(nowPlaying: NowPlaying, actions: NowPlayingActions, skip: Dp, play: Dp, gap: Dp) {
    val t = LocalAppTheme.current
    Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
        val skipIcon = skip * t.number("nowPlaying.skipIconOfButton").toFloat()
        RoundButton(skip, skip, skip, actions.previous) { Icon(AppIcons.PREVIOUS, skipIcon, "color.control.iconNeutral") }
        RoundButton(play, play, play, actions.playPause, play = true) {
            Icon(if (nowPlaying.isPlaying) AppIcons.PAUSE else AppIcons.PLAY, play * t.number("nowPlaying.playIconOfButton").toFloat(), "color.accent.playback")
        }
        RoundButton(skip, skip, skip, actions.next) { Icon(AppIcons.NEXT, skipIcon, "color.control.iconNeutral") }
    }
}

/**
 * Repeat One's badge, the desktop's (`playbackBar.repeatOneBadge`): a "1" in a
 * `control.repeatOn` disc on the button's top-right corner, `outset` beyond it.
 */
@Composable
private fun RepeatOneBadge(modifier: Modifier) {
    val t = LocalAppTheme.current
    val disc = t.desktopNumber("playbackBar.repeatOneBadge.discSize").dp
    val outset = t.desktopNumber("playbackBar.repeatOneBadge.outset").dp
    Box(
        modifier
            .offset { IntOffset(outset.roundToPx(), -outset.roundToPx()) }
            .size(disc)
            .tokenSurface(CircleShape, t.color("color.control.repeatOn"), emptyList(), null, 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Line(
            "1",
            t.text("nowPlaying.modeIcon", "color.control.face", weight = t.desktopNumber("playbackBar.repeatOneBadge.weight").toInt())
                .copy(fontSize = t.desktopNumber("playbackBar.repeatOneBadge.size").sp),
        )
    }
}

/**
 * The BP button (spec 6.4): a `modeButton` round button labelled "BP" (12/700),
 * `bpLabel.on` when Bit-Perfect is on and `bpLabel.off` when off. Greyed out
 * (`disabledOpacity`) and not tappable where it can't work (spec 6.2: Bluetooth,
 * the speaker): [greyed], with [onClick] null.
 */
@Composable
fun BitPerfectButton(on: Boolean, onClick: (() -> Unit)?, greyed: Boolean = false) {
    val t = LocalAppTheme.current
    val mode = t.dp("nowPlaying.modeButton")
    Box(Modifier.alpha(if (greyed) t.number("disabledOpacity").toFloat() else 1f)) {
        RoundButton(mode, mode, mode, onClick ?: {}) {
            Line(
                "BP",
                t.text(
                    "nowPlaying.bpLabelSize",
                    "color.text.primary",
                    weight = t.number("nowPlaying.bpLabelWeight").toInt(),
                    letterSpacingEm = t.number("nowPlaying.bpLabelLetterSpacingEm"),
                ).copy(color = t.mobileColor(if (on) "color.bpLabel.on" else "color.bpLabel.off")),
            )
        }
    }
}
