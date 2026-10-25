package com.mpaulrenlund.audiofool.ui

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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
    /** A swipe down: back to the screen underneath (spec 5). */
    val close: () -> Unit = {},
)

/**
 * Now Playing (spec 5), opened from the mini player. No buttons at the top:
 * system back or a swipe down closes it. From the top: the art, the title block
 * with the track count, the quality lines, the waveform seekbar with the times,
 * the controls row (Repeat / Shuffle / BP on the left, the transport on the
 * right for a right thumb) and the output readout.
 */
@Composable
fun NowPlayingScreen(nowPlaying: NowPlaying, actions: NowPlayingActions) {
    val t = LocalAppTheme.current
    val track = nowPlaying.track
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    val closeAtPx = with(LocalDensity.current) { t.dp("nowPlaying.swipeCloseDistance").toPx() }

    WindowBackground(t, Modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(barInsets().only(WindowInsetsSides.Vertical))
                // A swipe down closes it; the screen follows the finger.
                .draggable(
                    rememberDraggableState { delta -> scope.launch { drag.snapTo(maxOf(0f, drag.value + delta)) } },
                    Orientation.Vertical,
                    onDragStopped = {
                        if (drag.value > closeAtPx) actions.close() else drag.animateTo(0f)
                    },
                )
                .graphicsLayer { translationY = drag.value },
        ) {
            val screenHeight = maxHeight + barHeights()
            val padX = t.dp("nowPlaying.paddingX")
            // Spec 5: the smaller of (width − 40) and (height − artHeightReserve), so the M21 keeps room for the controls.
            // The reserve holds artReserveText of text, which grows with Android's font size (session 5: at 1.3 the
            // M21 lost the readout off the bottom).
            val textGrowth = t.dp("nowPlaying.artReserveText") * (LocalDensity.current.fontScale - 1f).coerceAtLeast(0f)
            val art = minOf(maxWidth - padX * 2, screenHeight - t.dp("nowPlaying.artHeightReserve") - textGrowth)

            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().padding(top = t.dp("nowPlaying.artTop")), contentAlignment = Alignment.Center) {
                    BigArt(track, art)
                }
                TitleBlock(nowPlaying, track)
                Spacer(Modifier.weight(t.number("nowPlaying.spaceAboveQualityWeight").toFloat()))
                Column(Modifier.padding(horizontal = padX)) {
                    track?.let { Quality(it) }
                    Seekbar(nowPlaying, actions.seek)
                }
                Spacer(Modifier.weight(t.number("nowPlaying.spaceBelowTimesWeight").toFloat()))
                Controls(nowPlaying, actions, screenHeight)
                Line(
                    nowPlaying.readout,
                    t.text("nowPlaying.outputSize", "color.text.muted").copy(textAlign = TextAlign.Center),
                    Modifier.fillMaxWidth().padding(start = padX, end = padX, bottom = t.dp("nowPlaying.outputBottom")),
                )
            }
        }
    }
}

/** The status and navigation bars' heights: the spec's sizes are fractions of the whole screen. */
@Composable
private fun barHeights(): Dp {
    val density = LocalDensity.current
    val insets = barInsets()
    return with(density) { (insets.getTop(this) + insets.getBottom(this)).toDp() }
}

/** The cover, square, with a 1 dp `art.border` and `shadow.headerArt`; the full picture decoded at its size. */
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
    Box(
        Modifier
            .size(side)
            .tokenSurface(RectangleShape, t.color("color.panel.bg"), t.shadow("shadow.headerArt"), t.color("color.art.border"), t.dp("borderWidth")),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().padding(t.dp("borderWidth")))
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

/** Two right-aligned lines: "FLAC · 96 kHz", then "24 bit · 1,140 kbps" (spec 5). */
@Composable
private fun Quality(track: Track) {
    val t = LocalAppTheme.current
    val (first, second) = QualityLines.of(track)
    val style = t.text("nowPlaying.qualitySize", "color.text.secondary").copy(textAlign = TextAlign.End)
    Column(
        Modifier.fillMaxWidth().padding(top = t.dp("nowPlaying.qualityTop")),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(t.dp("nowPlaying.qualityLineGap")),
    ) {
        Line(first, style)
        Line(second, style)
    }
}

/**
 * The waveform seekbar (spec 5): full width, `waveformHeight` tall, the track's
 * waveform as `waveformBars` hard-edged bars centred on the middle line, the
 * played part `slider.seekFill`, the rest `slider.track`. Until the waveform is
 * ready, a flat bar `waveformFloor` high. Drag or tap to seek; the bar follows
 * the finger and the seek happens on letting go. The elapsed and total times
 * sit under it.
 */
@Composable
private fun Seekbar(nowPlaying: NowPlaying, seek: (Long) -> Unit) {
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
    val bars = t.number("nowPlaying.waveformBars").toInt()
    val floor = t.dp("nowPlaying.waveformFloor")
    val played = t.color("color.slider.seekFill")
    val rest = t.color("color.slider.track")
    val levels = nowPlaying.waveform

    Canvas(
        Modifier
            .fillMaxWidth()
            .padding(top = t.dp("nowPlaying.waveformTop"))
            .height(t.dp("nowPlaying.waveformHeight"))
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
        val barWidth = size.width / bars
        val floorPx = floor.toPx()
        val split = (fraction * bars).roundToInt()
        for (i in 0 until bars) {
            // The levels are 96 columns already; shorter tracks give fewer, spread out.
            val level = when {
                levels == null || levels.isEmpty() -> 0f
                else -> levels[(i.toLong() * levels.size / bars).toInt().coerceIn(0, levels.size - 1)]
            }
            val h = maxOf(floorPx, minOf(size.height, level * size.height))
            val left = (i * barWidth).roundToInt().toFloat()
            val right = ((i + 1) * barWidth).roundToInt().toFloat()
            drawRect(if (i < split) played else rest, Offset(left, (size.height - h) / 2), Size(right - left, h))
        }
    }
    val shown = if (dragging != null) (fraction * duration).toLong() else position
    Row(Modifier.fillMaxWidth().padding(top = t.dp("nowPlaying.timesTop")), horizontalArrangement = Arrangement.SpaceBetween) {
        Line(Display.time(shown), t.text("nowPlaying.timeSize", "color.text.muted"))
        Line(Display.time(duration), t.text("nowPlaying.timeSize", "color.text.muted"))
    }
}

/**
 * The controls row (spec 5): on the left a column of three `modeButton` round
 * buttons, `modeGap` apart (Repeat, Shuffle, BP); on the right Previous,
 * Play/Pause and Next, centred on Shuffle's height, sized as a fraction of the
 * screen height between a min and a max.
 */
@Composable
private fun Controls(nowPlaying: NowPlaying, actions: NowPlayingActions, screenHeight: Dp) {
    val t = LocalAppTheme.current
    fun scaled(path: String): Dp =
        (screenHeight.value * t.number("$path.ofScreenHeight")).coerceIn(t.number("$path.min"), t.number("$path.max")).dp
    val skip = scaled("nowPlaying.skipButton")
    val play = scaled("nowPlaying.playButton")
    val gap = scaled("nowPlaying.transportGap")
    val mode = t.dp("nowPlaying.modeButton")
    val modeIcon = t.dp("nowPlaying.modeIcon")

    Row(
        Modifier.fillMaxWidth().padding(
            start = t.dp("nowPlaying.paddingX"),
            end = t.dp("nowPlaying.paddingX"),
            top = t.dp("nowPlaying.controlsPaddingTop"),
            bottom = t.dp("nowPlaying.controlsPaddingBottom"),
        ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(t.dp("nowPlaying.modeGap"))) {
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
            val greyed = nowPlaying.bitPerfectGreyed
            BitPerfectButton(nowPlaying.bitPerfect, if (greyed) null else actions.toggleBitPerfect, greyed)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
            val skipIcon = skip * t.number("nowPlaying.skipIconOfButton").toFloat()
            RoundButton(skip, skip, skip, actions.previous) { Icon(AppIcons.PREVIOUS, skipIcon, "color.control.iconNeutral") }
            RoundButton(play, play, play, actions.playPause, play = true) {
                Icon(if (nowPlaying.isPlaying) AppIcons.PAUSE else AppIcons.PLAY, play * t.number("nowPlaying.playIconOfButton").toFloat(), "color.accent.playback")
            }
            RoundButton(skip, skip, skip, actions.next) { Icon(AppIcons.NEXT, skipIcon, "color.control.iconNeutral") }
        }
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
