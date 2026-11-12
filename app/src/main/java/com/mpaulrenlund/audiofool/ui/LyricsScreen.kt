package com.mpaulrenlund.audiofool.ui

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Velocity
import com.mpaulrenlund.audiofool.core.lyrics.LyricLine
import com.mpaulrenlund.audiofool.core.lyrics.Lyrics
import com.mpaulrenlund.audiofool.core.model.Track
import com.mpaulrenlund.audiofool.lyrics.LyricsRows
import com.mpaulrenlund.audiofool.lyrics.LyricsState
import com.mpaulrenlund.audiofool.playback.NowPlaying
import com.mpaulrenlund.audiofool.theme.LocalAppTheme
import com.mpaulrenlund.audiofool.theme.toCompose
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** What the Lyrics screen can ask for. */
class LyricsActions(
    /** A tap on a timed line: the song from there. */
    val seek: (Long) -> Unit = {},
    /** A note that offers it ("Tap to try again."): the lookup again. */
    val retry: () -> Unit = {},
)

/**
 * The Lyrics screen (spec 5.5; 2026-11-11, the user's request; no mockup, and the
 * phone's own: the desktop has no lyrics), from Now Playing's cover menu. The song
 * playing's title over "Artist · Album" (Analyze's header), then its lyrics. Timed
 * lyrics light the line being sung in the cover's colour (the user's call: the
 * seekbar's and Play/Pause's, `accent.playback` without a cover), a little larger;
 * lines sung are `text.muted`, lines to come `text.secondary`. The list keeps the
 * line being sung `lyrics.anchor` of the way down, and a finger's scroll stops that
 * for `lyrics.followAgainMs`. A tap on a timed line seeks there. Lyrics with no
 * timing are shown as written, in `text.primary`. Under them, where they came
 * from. Looking, none and a failure are a LYRICS panel's row, as Genius's are.
 */
@Composable
fun LyricsScreen(state: LyricsState, nowPlaying: NowPlaying, actions: LyricsActions) {
    val lyrics = state.lyrics
    val track = state.track
    ScreenFrame(topBar = { Header(track) }) {
        if (lyrics != null && track != null) {
            LyricLines(lyrics, track, nowPlaying, actions.seek, Modifier.weight(1f))
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                state.note?.let { note ->
                    Panel("Lyrics") { SettingRow(note.text, note.detail, onClick = if (note.retry) actions.retry else null) }
                }
            }
        }
    }
}

/** The song's title over "Artist · Album", as Analyze's header draws its song (its `analysis.*` sizes). "Lyrics" with no song. */
@Composable
private fun Header(track: Track?) {
    val t = LocalAppTheme.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = t.dp("analysis.headerPaddingX"), vertical = t.dp("analysis.headerPaddingY")),
        verticalArrangement = Arrangement.spacedBy(t.dp("analysis.headerLineGap")),
    ) {
        Line(track?.displayTitle ?: "Lyrics", t.text("analysis.titleSize", "color.text.heading", weight = t.number("analysis.titleWeight").toInt()))
        val subtitle = track?.let(LyricsRows::subtitle).orEmpty()
        if (subtitle.isNotEmpty()) Line(subtitle, t.text("analysis.subtitleSize", "color.text.muted"), maxLines = 2)
    }
}

/**
 * When the list may follow the song again: [touchedAt] is the last time a finger
 * moved it (plain, read when needed); [releases] counts the times one let go, so
 * following waits from then.
 */
private class Follow {
    var touchedAt = 0L
    var releases by mutableIntStateOf(0)
    /** False until the list has been put at the line once: that first time it jumps, after that it glides. */
    var placed = false
}

@Composable
private fun LyricLines(lyrics: Lyrics, track: Track, nowPlaying: NowPlaying, seek: (Long) -> Unit, modifier: Modifier) {
    val t = LocalAppTheme.current
    val density = LocalDensity.current

    // The line being sung, read every `tickMs` while playing (not every frame: nothing
    // else moves here); paused, from the held place (it is state, so a seek shows).
    val sameSong = nowPlaying.shownTrack?.filePath == track.filePath
    val ticking = nowPlaying.live && nowPlaying.isPlaying && lyrics.synced && sameSong
    var ticked by remember(lyrics) { mutableIntStateOf(lyrics.lineAt(nowPlaying.positionMs())) }
    val tick = t.number("lyrics.tickMs").toLong()
    LaunchedEffect(nowPlaying, lyrics, ticking) {
        if (ticking) while (true) {
            ticked = lyrics.lineAt(nowPlaying.positionMs())
            delay(tick)
        }
    }
    val current = when {
        !sameSong -> -1
        ticking -> ticked
        else -> lyrics.lineAt(nowPlaying.positionMs())
    }

    // The screen stays on while timed lyrics play (my call), as a lyrics screen is read, not touched.
    val view = LocalView.current
    val keepOn = ticking
    DisposableEffect(view, keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    val main = rememberCoverColor(track)
    val accent = remember(t, main) { main?.let { t.coverAccent(it) } }
    val lit = accent?.toCompose() ?: t.color("color.accent.playback")
    val sung = t.color("color.text.muted")
    val coming = t.color("color.text.secondary")
    val untimed = t.color("color.text.primary")
    val change = t.number("lyrics.changeMs").toInt()
    val grown = t.number("lyrics.currentScale").toFloat()

    val list = rememberLazyListState()
    val follow = remember(lyrics) { Follow() }
    val connection = remember(follow) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) follow.touchedAt = SystemClock.uptimeMillis()
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                follow.touchedAt = SystemClock.uptimeMillis()
                follow.releases++
                return Velocity.Zero
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val height = constraints.maxHeight
        val anchor = (height * t.number("lyrics.anchor")).roundToInt()
        if (lyrics.synced) {
            val resume = t.number("lyrics.followAgainMs").toLong()
            LaunchedEffect(lyrics, current, follow.releases, anchor) {
                // Wait while a finger has the list, and `followAgainMs` after it lets go.
                while (true) {
                    val wait = follow.touchedAt + resume - SystemClock.uptimeMillis()
                    if (follow.touchedAt == 0L || wait <= 0) break
                    delay(wait)
                }
                list.bringToAnchor(current.coerceAtLeast(0), anchor, glide = follow.placed, change)
                follow.placed = true
            }
        }

        LazyColumn(
            Modifier.fillMaxSize().nestedScroll(connection),
            state = list,
            // Room under the last line, so it too can come up to the anchor.
            contentPadding = PaddingValues(
                top = t.dp("lyrics.paddingTop"),
                bottom = with(density) { (height - anchor).coerceAtLeast(0).toDp() },
            ),
        ) {
            itemsIndexed(lyrics.lines) { i, line ->
                val color = when {
                    !lyrics.synced -> untimed
                    i == current -> lit
                    i < current -> sung
                    else -> coming
                }
                LyricRow(line, color, if (i == current) grown else 1f, grown, change, if (lyrics.synced) ({ seek(line.startMs) }) else null)
            }
            item {
                Line(
                    LyricsRows.source(lyrics),
                    t.text("lyrics.sourceSize", "color.text.muted"),
                    Modifier.padding(horizontal = t.dp("lyrics.paddingX")).padding(top = t.dp("lyrics.sourceTop")),
                    maxLines = 2,
                )
            }
        }
    }
}

/**
 * One line, `lyrics.lineSize` at `lineWeight`, in [color]; the line being sung
 * drawn [scale] times larger from its left edge. Every line is laid out narrow
 * enough to grow to [maxScale] inside the padding, so none rewraps when it is lit.
 * An empty line is a break, `breakHeight` tall.
 */
@Composable
private fun LyricRow(line: LyricLine, color: Color, scale: Float, maxScale: Float, changeMs: Int, onTap: (() -> Unit)?) {
    val t = LocalAppTheme.current
    if (line.text.isEmpty()) {
        Spacer(Modifier.height(t.dp("lyrics.breakHeight")))
        return
    }
    val shown by animateColorAsState(color, tween(changeMs), label = "lyric colour")
    val size by animateFloatAsState(scale, tween(changeMs), label = "lyric size")
    BasicText(
        line.text,
        Modifier
            .fillMaxWidth()
            .tappable(RoundedCornerShape(t.radius("radius.row")), onTap)
            .padding(horizontal = t.dp("lyrics.paddingX"), vertical = t.dp("lyrics.linePaddingY"))
            .fillMaxWidth(1f / maxScale)
            .graphicsLayer {
                scaleX = size
                scaleY = size
                transformOrigin = TransformOrigin(0f, 0.5f)
            },
        t.text("lyrics.lineSize", "color.text.primary", weight = t.number("lyrics.lineWeight").toInt(), lineHeight = t.number("lyrics.lineHeight")),
        color = { shown },
    )
}

/**
 * Puts item [index] `anchor` px down the list: gliding there over [ms] when it is
 * on screen and [glide], else at once (the first time, and after a seek far off).
 * At the top the list can't go further, so the first lines sit higher.
 */
private suspend fun LazyListState.bringToAnchor(index: Int, anchor: Int, glide: Boolean, ms: Int) {
    val shown = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (shown == null || !glide) {
        scrollToItem(index)
        scrollBy(-anchor.toFloat())
        return
    }
    animateScrollBy((shown.offset - anchor).toFloat(), tween(ms))
}
