package com.mpaulrenlund.audiofool.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.mpaulrenlund.audiofool.core.library.LibraryRelocator
import com.mpaulrenlund.audiofool.core.library.LibrarySearch
import com.mpaulrenlund.audiofool.core.library.MusicLibrary
import com.mpaulrenlund.audiofool.core.model.Album
import com.mpaulrenlund.audiofool.core.model.ArtistGroup
import com.mpaulrenlund.audiofool.core.model.Track
import com.mpaulrenlund.audiofool.playback.NowPlaying
import com.mpaulrenlund.audiofool.theme.LocalAppTheme
import java.util.Collections
import java.util.IdentityHashMap

/**
 * What the library screens show: the library, which of its folders can't be
 * reached right now, and what is playing. Rebuilt when the library changes;
 * nothing in it is held past that.
 */
@Stable
class Browse(
    val library: MusicLibrary,
    val unavailableFolders: List<String>,
    val nowPlaying: NowPlaying,
    /** Whether any folder is ticked (the empty Artists list says which is wrong). */
    val anyFolderIncluded: Boolean = true,
) {
    private val unavailable: MutableMap<Any, Boolean> = Collections.synchronizedMap(IdentityHashMap())

    /**
     * Whether a track sits on a folder that can't be reached (a removed SD card,
     * spec 1). Its row stays, drawn as unavailable, and won't play.
     */
    fun isUnavailable(track: Track): Boolean =
        unavailableFolders.isNotEmpty() && unavailableFolders.any { LibraryRelocator.isUnder(track.filePath, it) }

    /** An album whose every track is unavailable. */
    fun isUnavailable(album: Album): Boolean =
        unavailableFolders.isNotEmpty() && unavailable.getOrPut(album) { album.tracks.all(::isUnavailable) }

    fun isUnavailable(artist: ArtistGroup): Boolean =
        unavailableFolders.isNotEmpty() && unavailable.getOrPut(artist) { artist.albums.all(::isUnavailable) }

    fun artist(name: String): ArtistGroup? = library.artists.firstOrNull { it.name == name }

    fun album(artist: String, title: String): Album? = artist(artist)?.albums?.firstOrNull { it.title == title }

    fun isPlaying(track: Track): Boolean = nowPlaying.track?.filePath == track.filePath

    /** The search index, folded once per library (LibrarySearch.Index); built off the main thread by Search. */
    @Volatile var searchIndex: LibrarySearch.Index? = null
}

/** What the screens can ask for. */
class AppActions(
    val open: (Route) -> Unit = {},
    val back: () -> Unit = {},
    val toggleTheme: () -> Unit = {},
    /** A tap on a song (spec 2): it plays, and Now Playing opens. */
    val play: (Track) -> Unit = {},
    val playPause: () -> Unit = {},
    val previous: () -> Unit = {},
    val next: () -> Unit = {},
    /** A tap on the mini player: Now Playing, which session 3 builds. */
    val openNowPlaying: () -> Unit = {},
)

/**
 * The Artists panel when there is nothing to list, worded as the desktop's
 * `EmptyStateText` is, with the phone's way to the folders (Settings, from the
 * logo menu) in place of the desktop's Libraries menu.
 */
@Composable
fun EmptyNote(browse: Browse, scanning: Boolean, modifier: Modifier = Modifier) {
    val t = LocalAppTheme.current
    val (title, detail) = when {
        browse.library.allTracks.isEmpty() && scanning -> "Scanning the library" to "Music appears here as it's found."
        !browse.anyFolderIncluded -> "Every folder is unticked" to "Tick one in Settings, from the logo menu."
        else -> "No music yet" to "Add a folder in Settings, from the logo menu."
    }
    Column(
        modifier.fillMaxWidth().padding(t.dp("settings.rowPaddingX")),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(t.dp("artists.lineGap")),
    ) {
        Line(title, t.text("artists.nameSize", "color.text.primary").copy(textAlign = TextAlign.Center))
        Line(detail, t.text("artists.countSize", "color.text.muted").copy(textAlign = TextAlign.Center), maxLines = 2)
    }
}
