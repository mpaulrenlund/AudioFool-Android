package com.mpaulrenlund.audiofool.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import com.mpaulrenlund.audiofool.StartTimes
import com.mpaulrenlund.audiofool.library.CoverArt
import com.mpaulrenlund.audiofool.library.LibraryState
import com.mpaulrenlund.audiofool.settings.MusicFolder
import com.mpaulrenlund.audiofool.storage.Storage
import com.mpaulrenlund.audiofool.theme.LocalAppTheme
import com.mpaulrenlund.audiofool.theme.WindowBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the session 1 screen can ask for. Session 2 replaces the screen with Artists and Settings. */
class HomeActions(
    val toggleTheme: () -> Unit,
    val rescan: () -> Unit,
    val addFolder: () -> Unit,
    val setIncluded: (String, Boolean) -> Unit,
    val showTokenSheet: () -> Unit,
    val forgetCache: () -> Unit,
)

/**
 * Session 1's screen: the library's counts, the music folders, the theme, and
 * the debug numbers the user reports back from the phone (counts, start times,
 * scan times).
 */
@Composable
fun LibraryHome(
    state: LibraryState,
    folders: List<MusicFolder>,
    startTimes: StartTimes,
    actions: HomeActions,
) {
    val t = LocalAppTheme.current
    val context = LocalContext.current
    WindowBackground(t, Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(t.dp("settings.sectionGap")),
        ) {
            BasicText(
                "AudioFool",
                style = t.text("topBar.titleSize", "color.text.heading", weight = 600),
                modifier = Modifier.padding(horizontal = t.dp("topBar.paddingStart"), vertical = t.dp("panel.listPadding")),
            )

            Panel("Library") {
                val library = state.library
                SettingRow(
                    label = if (library == null) "Reading the library…" else
                        "%,d artists · %,d albums · %,d tracks".format(library.artists.size, library.albumCount, library.allTracks.size),
                    value = when {
                        state.scanning -> state.progress?.let { p ->
                            if (p.filesFound == 0) "Checking for changes…" else "Reading tags: %,d of %,d".format(p.filesRead, p.filesFound)
                        } ?: "Checking for changes…"
                        state.unavailableFolders.isNotEmpty() ->
                            "Unavailable: " + state.unavailableFolders.joinToString { Storage.describe(context, it) } +
                                " (%,d tracks kept)".format(library?.allTracks?.count(state::isUnavailable) ?: 0)
                        else -> "Up to date"
                    },
                )
                ArtCheck(state)
            }

            Panel("Music folders", headerAction = {
                Box(
                    Modifier.size(t.dp("settings.headerActionButton")).clickable(onClick = actions.rescan),
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.REFRESH, t.dp("settings.headerActionIcon"), "color.control.iconNeutral") }
            }) {
                for (folder in folders) FolderRow(folder, Storage.describe(context, folder.path)) { actions.setIncluded(folder.path, !folder.included) }
                Row(Modifier.padding(horizontal = t.dp("settings.folderRowPaddingX"), vertical = t.dp("settings.folderRowPaddingY"))) {
                    ChipButton("Add Folder…", actions.addFolder)
                }
            }

            Panel("Appearance") {
                SettingRow("Theme", t.name, onClick = actions.toggleTheme) {
                    Chip(width = t.dp("settings.themeChipWidth"), onClick = actions.toggleTheme) {
                        Icon(if (t.isDark) AppIcons.MOON else AppIcons.SUN, t.dp("settings.chipIcon"), "color.text.status")
                    }
                }
            }

            DebugPanel(state, startTimes, actions)
            Spacer(Modifier.padding(bottom = t.dp("panel.marginBottom")))
        }
    }
}

@Composable
private fun FolderRow(folder: MusicFolder, name: String, onToggle: () -> Unit) {
    val t = LocalAppTheme.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = t.dp("settings.folderRowMinHeight"))
            .clickable(onClick = onToggle)
            .padding(horizontal = t.dp("settings.folderRowPaddingX"), vertical = t.dp("settings.folderRowPaddingY")),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FolderCheckbox(folder.included)
        Spacer(Modifier.width(t.dp("settings.folderRowPaddingX")))
        Column(Modifier.weight(1f)) {
            BasicText(name, style = t.text("settings.folderNameSize", "color.text.primary"), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(folder.path, style = t.text("settings.folderPathSize", "color.text.muted"), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
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
private fun DebugPanel(state: LibraryState, startTimes: StartTimes, actions: HomeActions) {
    val s = state.stats
    fun ms(v: Long?) = v?.let { "%,d ms".format(it) } ?: "–"
    Panel("Debug") {
        SettingRow("Cold start", "process start to the library on screen: ${ms(startTimes.coldStartMs)}")
        SettingRow("Warm start", "screen created to the library on screen: ${ms(startTimes.warmStartMs)} (${startTimes.warmStarts} so far)")
        SettingRow("Cache", "read and built in ${ms(s.cacheLoadMs)} · %,d tracks · %,d KB".format(s.cacheTracks, s.cacheBytes / 1024))
        val sum = s.lastScanSummary
        SettingRow(
            "Last scan",
            if (sum == null) "–" else "${ms(s.lastScanMs)} · read %,d · reused %,d · removed %,d · unopened %,d".format(sum.read, sum.reused, sum.removed, sum.unopened),
        )
        state.library?.let { library ->
            SettingRow("Formats", library.allTracks.groupingBy { it.kind }.eachCount().entries.sortedByDescending { it.value }
                .joinToString(" · ") { "${it.key} %,d".format(it.value) })
        }
        SettingRow("Token sheet", "every colour, size and surface", onClick = actions.showTokenSheet)
        SettingRow("Forget the cache", "the next start reads every tag, to time a full scan", onClick = actions.forgetCache)
    }
}
