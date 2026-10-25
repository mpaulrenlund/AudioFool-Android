package com.mpaulrenlund.audiofool.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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
)

class DebugActions(
    val showTokenSheet: () -> Unit = {},
    val forgetCache: () -> Unit = {},
)

/**
 * Statistics, from the logo menu (spec 4.6). Session 5 builds the statistics
 * themselves; until then it holds the numbers the user reports back from the
 * phones (the user's call, session 2): the library's counts and state, start and
 * scan times, and the debug switches. The Debug panel stays at the bottom once
 * the statistics are added above it.
 */
@Composable
fun StatisticsScreen(state: LibraryState, numbers: DebugNumbers, actions: DebugActions) {
    TitledScreen("Statistics") {
        Panel("Library") {
            val library = state.library
            SettingRow(
                label = if (library == null) "Reading the library…" else
                    "${Display.count(library.artists.size)} artists · ${Display.count(library.albumCount)} albums · ${Display.count(library.allTracks.size)} tracks",
                value = when {
                    state.scanning -> state.progress?.let { p ->
                        if (p.filesFound == 0) "Checking for changes…" else "Reading tags: ${Display.count(p.filesRead)} of ${Display.count(p.filesFound)}"
                    } ?: "Checking for changes…"
                    state.unavailableFolders.isNotEmpty() ->
                        "Unavailable: " + state.unavailableFolders.joinToString { numbers.describeFolder(it) } +
                            " (${Display.count(library?.allTracks?.count(state::isUnavailable) ?: 0)} tracks kept)"
                    else -> "Up to date"
                },
            )
            ArtCheck(state)
        }
        numbers.playback?.let { PlaybackPanel(it) }
        DebugPanel(state, numbers, actions)
    }
}

/**
 * Playback's numbers (session 3): BASS and its add-ons, what is heard, and for
 * each track the rate and format its decoder asked for and the output took.
 */
@Composable
private fun PlaybackPanel(numbers: PlaybackNumbers) {
    val np = numbers.nowPlaying
    Panel("Playback") {
        SettingRow("Decoder", numbers.bass())
        SettingRow("Formats", numbers.formats().ifEmpty { "–" })
        numbers.missing().takeIf { it.isNotEmpty() }?.let { SettingRow("Missing add-ons", it) }
        SettingRow("Output", np.readout.ifEmpty { "–" })
        SettingRow("Joins", numbers.joins())
        SettingRow("Opened", if (np.openedLog.isEmpty()) "nothing played yet" else np.openedLog.joinToString("\n"))
        if (np.skippedLog.isNotEmpty()) SettingRow("Skipped", np.skippedLog.joinToString("\n"))
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
    LaunchedEffect(track.filePath) {
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
