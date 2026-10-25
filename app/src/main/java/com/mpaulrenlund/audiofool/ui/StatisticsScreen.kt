package com.mpaulrenlund.audiofool.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpaulrenlund.audiofool.core.library.LibraryStatistics
import com.mpaulrenlund.audiofool.core.playback.ListeningTime
import com.mpaulrenlund.audiofool.theme.tokenSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import com.mpaulrenlund.audiofool.StartTimes
import com.mpaulrenlund.audiofool.core.format.Display
import com.mpaulrenlund.audiofool.library.CoverArt
import com.mpaulrenlund.audiofool.library.LibraryState
import com.mpaulrenlund.audiofool.playback.NowPlaying
import com.mpaulrenlund.audiofool.theme.LocalAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The numbers the debug panel shows that don't come from the library itself. */
class DebugNumbers(
    val startTimes: StartTimes,
    /** Covers the background fill has done, of how many ("812 of 2,376"). */
    val thumbnailsDone: () -> Pair<Int, Int> = { 0 to 0 },
    /** How "Unavailable: SD card · Music" names a folder. */
    val describeFolder: (String) -> String = { it },
    val playback: PlaybackNumbers? = null,
)

/** What the user reports back about playback: BASS, the formats it plays, and how each track's output opened. */
class PlaybackNumbers(
    val nowPlaying: NowPlaying,
    val bass: () -> String,
    val formats: () -> String,
    val missing: () -> String,
    /** Gapless joins, reopens and underruns since the app started. */
    val joins: () -> String = { "–" },
    /** Session 4: the route, Bit-Perfect, the USB mixer entries and what the system reports. */
    val output: () -> List<Pair<String, String>> = { emptyList() },
    /** The emulator's pretend route (it has no USB DAC or headphones), and a tap to cycle it. */
    val pretendRoute: () -> String = { "" },
    val cyclePretendRoute: () -> Unit = {},
)

class DebugActions(
    val showTokenSheet: () -> Unit = {},
    val forgetCache: () -> Unit = {},
)

/**
 * Statistics, from the logo menu (spec 4.6): the library's totals, its formats
 * and the time listened, built as the desktop's Statistics window is (figures in
 * `type.statValue`, labelled bars), in two columns for a phone. Below them, the
 * numbers the user reports back from the phones (the user's call, session 2):
 * playback, start and scan times, and the debug switches.
 */
@Composable
fun StatisticsScreen(
    state: LibraryState,
    numbers: DebugNumbers,
    actions: DebugActions,
    stats: LibraryStatistics = LibraryStatistics.Empty,
    listening: ListeningTime? = null,
) {
    TitledScreen("Statistics") {
        Panel("Library") {
            if (state.library == null) {
                SettingRow("Reading the library…")
            } else {
                Figures(
                    listOf(
                        Display.count(stats.trackCount) to "Tracks",
                        Display.count(stats.albumCount) to "Albums",
                        Display.count(stats.artistCount) to "Artists",
                    ),
                    listOf(
                        Display.longDuration(stats.totalDurationMs) to "Play time",
                        Display.size(stats.totalBytes) to "On disk",
                    ),
                )
            }
            SettingRow(label = "Scan", value = libraryLine(state, numbers))
        }
        if (stats.fileTypes.isNotEmpty()) {
            Panel("Formats") {
                Column(Modifier.padding(vertical = LocalAppTheme.current.dp("statistics.listPaddingY"))) {
                    for (s in stats.fileTypes) {
                        BarRow(s.label, "${Display.count(s.count)} · ${Display.percent(s.share)} · ${Display.size(s.bytes)}", s.share)
                    }
                }
            }
        }
        if (listening != null) {
            Panel("Listening") {
                Figures(listOf(Display.longDuration(listening.totalMs) to "Listened since ${date(listening.sinceUtc)}"))
            }
        }
        numbers.playback?.let { PlaybackPanel(it) }
        DebugPanel(state, numbers, actions)
    }
}

/** Where the scan is: "Up to date", the tags being read, or the folders that can't be reached. */
private fun libraryLine(state: LibraryState, numbers: DebugNumbers): String = when {
    state.scanning -> state.progress?.let { p ->
        if (p.filesFound == 0) "Checking for changes…" else "Reading tags: ${Display.count(p.filesRead)} of ${Display.count(p.filesFound)}"
    } ?: "Checking for changes…"
    state.unavailableFolders.isNotEmpty() ->
        "Unavailable: " + state.unavailableFolders.joinToString { numbers.describeFolder(it) } +
            " (${Display.count(state.library?.allTracks?.count(state::isUnavailable) ?: 0)} tracks kept)"
    else -> "Up to date"
}

/** "24 Oct 2026". */
private fun date(utcMs: Long): String = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(Date(utcMs))

/**
 * Rows of figures, the desktop's tiles: each a `type.statValue` number with its
 * label under it, sharing the row's width.
 */
@Composable
private fun Figures(vararg rows: List<Pair<String, String>>) {
    val t = LocalAppTheme.current
    val value = t.text("statistics.tileLabelSize", "color.text.heading", weight = t.desktopNumber("type.statValue.weight").toInt())
        .copy(fontSize = t.desktopNumber("type.statValue.size").sp)
    val label = t.text("statistics.tileLabelSize", "color.text.muted")
    Column(
        Modifier.padding(horizontal = t.dp("statistics.tilePaddingX"), vertical = t.dp("statistics.tilePaddingY")),
        verticalArrangement = Arrangement.spacedBy(t.dp("statistics.tilePaddingY")),
    ) {
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(t.dp("statistics.tileGap"))) {
                for ((figure, name) in row) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(t.dp("statistics.tileLineGap"))) {
                        Line(figure, value)
                        Line(name, label, maxLines = 2)
                    }
                }
            }
        }
    }
}

/**
 * The desktop's BarRow: the label and its value on a line, and under them a bar
 * `playbackBar.trackHeight` high, filled [fill] of the way in `slider.volumeFill`
 * on `slider.track` (the playback colour is kept for playback).
 */
@Composable
private fun BarRow(label: String, value: String, fill: Double) {
    val t = LocalAppTheme.current
    val shape = RoundedCornerShape(t.radius("radius.sliderTrack"))
    Column(
        Modifier.padding(horizontal = t.dp("statistics.rowPaddingX"), vertical = t.dp("statistics.rowPaddingY")),
        verticalArrangement = Arrangement.spacedBy(t.dp("statistics.barGap")),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(t.dp("settings.rowGap")), verticalAlignment = Alignment.CenterVertically) {
            Line(label, t.text("statistics.rowLabelSize", "color.text.primary"), Modifier.weight(1f))
            Line(value, t.text("statistics.rowValueSize", "color.text.muted"))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(t.desktopNumber("playbackBar.trackHeight").dp)
                .tokenSurface(shape, t.color("color.slider.track"), t.shadow("shadow.sliderTrackInset")),
        ) {
            val f = fill.coerceIn(0.0, 1.0).toFloat()
            if (f > 0f) Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(shape).background(t.color("color.slider.volumeFill")))
        }
    }
}

/**
 * Playback's numbers (session 3): BASS and its add-ons, what is heard, and for
 * each track the rate and format its decoder asked for and the output took.
 * Session 4 adds the route, Bit-Perfect and the mixer, for the user to report.
 */
@Composable
private fun PlaybackPanel(numbers: PlaybackNumbers) {
    val np = numbers.nowPlaying
    Panel("Playback") {
        SettingRow("Decoder", numbers.bass())
        SettingRow("Formats", numbers.formats().ifEmpty { "–" })
        numbers.missing().takeIf { it.isNotEmpty() }?.let { SettingRow("Missing add-ons", it) }
        SettingRow("Output", np.readout.ifEmpty { "–" })
        for ((label, value) in numbers.output()) SettingRow(label, value)
        SettingRow("Joins", numbers.joins())
        SettingRow("Opened", if (np.openedLog.isEmpty()) "nothing played yet" else np.openedLog.joinToString("\n"))
        if (np.skippedLog.isNotEmpty()) SettingRow("Skipped", np.skippedLog.joinToString("\n"))
        SettingRow("Pretend route (emulator test)", numbers.pretendRoute(), onClick = numbers.cyclePretendRoute)
    }
}

/**
 * One album's cover, read the way the library will: embedded first (the best
 * of several), else the folder's file. Shows that both kinds are read.
 */
@Composable
private fun ArtCheck(state: LibraryState) {
    val t = LocalAppTheme.current
    val album = state.library?.artists?.firstOrNull()?.newestAlbum ?: return
    val track = album.tracks.firstOrNull() ?: return
    val sizePx = with(LocalDensity.current) { t.dp("artists.thumb").roundToPx() }
    var cover by remember(track.filePath) { mutableStateOf<Pair<ImageBitmap?, String>?>(null) }
    // Not in ThemeLab (no LocalArt there, as Now Playing's art): TagLib can't load in a
    // JVM test, and the failed read went uncaught into the next test.
    if (LocalArt.current != null) LaunchedEffect(track.filePath) {
        cover = withContext(Dispatchers.IO) {
            val read = CoverArt.read(track)
            val bitmap = read?.let { c ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(c.bytes, 0, c.bytes.size, bounds)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = Integer.highestOneBit(maxOf(1, minOf(bounds.outWidth, bounds.outHeight) / sizePx))
                }
                BitmapFactory.decodeByteArray(c.bytes, 0, c.bytes.size, options)?.asImageBitmap()
            }
            bitmap to (read?.source?.name ?: "none")
        }
    }
    SettingRow("Newest album of ${album.artistName}", "${album.title} · cover: ${cover?.second ?: "…"}") {
        cover?.first?.let { Image(it, contentDescription = null, modifier = Modifier.size(t.dp("artists.thumb"))) }
    }
}

@Composable
private fun DebugPanel(state: LibraryState, numbers: DebugNumbers, actions: DebugActions) {
    val s = state.stats
    val startTimes = numbers.startTimes
    fun ms(v: Long?) = v?.let { "${Display.count(it.toInt())} ms" } ?: "–"
    Panel("Debug") {
        ArtCheck(state)
        SettingRow("Cold start", "process start to the library on screen: ${ms(startTimes.coldStartMs)}")
        SettingRow("Warm start", "screen created to the library on screen: ${ms(startTimes.warmStartMs)} (${startTimes.warmStarts} so far)")
        SettingRow("Cache", "read and built in ${ms(s.cacheLoadMs)} · ${Display.count(s.cacheTracks)} tracks · ${Display.count((s.cacheBytes / 1024).toInt())} KB")
        val sum = s.lastScanSummary
        SettingRow(
            "Last scan",
            if (sum == null) "–" else "${ms(s.lastScanMs)} · read ${Display.count(sum.read)} · reused ${Display.count(sum.reused)} · " +
                "removed ${Display.count(sum.removed)} · unopened ${Display.count(sum.unopened)}",
        )
        state.library?.let { library ->
            SettingRow("Formats", library.allTracks.groupingBy { it.kind }.eachCount().entries.sortedByDescending { it.value }
                .joinToString(" · ") { "${it.key} ${Display.count(it.value)}" })
        }
        val (done, total) = numbers.thumbnailsDone()
        SettingRow("Covers", "saved small copies checked: ${Display.count(done)} of ${Display.count(total)} albums")
        SettingRow("Token sheet", "every colour, size and surface", onClick = actions.showTokenSheet)
        SettingRow("Forget the cache", "the next start reads every tag, to time a full scan", onClick = actions.forgetCache)
    }
}
