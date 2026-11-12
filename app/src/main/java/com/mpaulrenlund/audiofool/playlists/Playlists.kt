package com.mpaulrenlund.audiofool.playlists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mpaulrenlund.audiofool.core.library.MusicLibrary
import com.mpaulrenlund.audiofool.core.library.Paths
import com.mpaulrenlund.audiofool.core.model.Album
import com.mpaulrenlund.audiofool.core.model.Track
import com.mpaulrenlund.audiofool.core.playlists.Playlist
import com.mpaulrenlund.audiofool.core.playlists.PlaylistResolver
import com.mpaulrenlund.audiofool.core.playlists.PlaylistStore
import com.mpaulrenlund.audiofool.core.playlists.PlaylistText
import com.mpaulrenlund.audiofool.core.playlists.RecentlyAdded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Playlists and Liked on the phone (2026-11-12, the user's request: the desktop's
 * Liked and Recently Added, reached from the Playlists tab in Artists' header; the
 * heart on Now Playing fills Liked). The store is the desktop's
 * ([PlaylistStore], `no_backup/playlists.json`); this is what the screens read.
 *
 * Every read and write of the store runs on one background thread ([serial]), so
 * nothing touches the disk on the main thread and the entries are never changed
 * from two threads. The screens read Compose state: [likedKeys] for the heart and
 * [version], which moves on every change, so a list showing a playlist reads it again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Playlists(private val path: String, private val scope: CoroutineScope, private val clock: () -> Long = System::currentTimeMillis) {
    private val serial = Dispatchers.IO.limitedParallelism(1)

    private var loaded: PlaylistStore? = null
    private var resolver: PlaylistResolver? = null
    private var resolverLibrary: MusicLibrary? = null

    /** Liked's songs, as [Paths.key]s, for the heart. Set at once on a tap, then kept in step with the store. */
    var likedKeys: Set<String> by mutableStateOf(emptySet())
        private set

    /** Moves whenever a playlist changes, or the songs are found again in a new library. */
    var version: Int by mutableIntStateOf(0)
        private set

    init {
        scope.launch(serial) { publish(store()) }
    }

    private fun store(): PlaylistStore = loaded ?: PlaylistStore.load(path, clock()).also { loaded = it }

    private fun resolverFor(library: MusicLibrary): PlaylistResolver =
        resolver?.takeIf { resolverLibrary === library } ?: PlaylistResolver(library.allTracks).also {
            resolver = it
            resolverLibrary = library
        }

    private fun publish(store: PlaylistStore) {
        likedKeys = store.liked.entries.mapTo(HashSet()) { Paths.key(it.filePath) }
        version++
    }

    fun isLiked(track: Track?): Boolean = track != null && Paths.key(track.filePath) in likedKeys

    /** The heart: likes the song (to the end of Liked) or unlikes it, as the desktop's does. */
    fun toggleLike(track: Track) {
        val key = Paths.key(track.filePath)
        val like = key !in likedKeys
        likedKeys = if (like) likedKeys + key else likedKeys - key
        scope.launch(serial) {
            val store = store()
            if (like) store.add(store.liked, listOf(track), clock())
            else store.remove(store.liked, listOf(track.filePath), clock())
            publish(store)
        }
    }

    /**
     * A new library (a scan, a tag saved): finds every playlist's songs in it, so a
     * song whose card came back under another name is re-pointed and its heart
     * shows. Saved only when something was re-pointed, which isn't a modification.
     * An empty library (before the cache is read) finds nothing and changes nothing.
     */
    fun libraryChanged(library: MusicLibrary) {
        if (library.allTracks.isEmpty()) return
        scope.launch(serial) {
            val store = store()
            val resolver = resolverFor(library)
            var changed = false
            for (playlist in store.playlists) changed = resolver.resolve(playlist).changed or changed
            if (changed) store.save()
            publish(store)
        }
    }

    /** The Playlists tab's rows: Liked, then Recently Added (both pinned, as on the desktop), then the rest most recently modified first. */
    suspend fun rows(library: MusicLibrary): List<PlaylistRow> = withContext(serial) {
        val store = store()
        val resolver = resolverFor(library)
        val liked = store.liked
        val recent = RecentlyAdded.albums(library, clock())
        buildList {
            add(row(liked, resolver.resolve(liked).tracks, library))
            add(PlaylistRow(RECENT_ID, RecentlyAdded.NAME, PlaylistText.trackCount(recent.sumOf { it.tracks.size }), recent.firstOrNull()))
            for (p in store.byRecent()) if (p !== liked) add(row(p, resolver.resolve(p).tracks, library))
        }
    }

    private fun row(playlist: Playlist, tracks: List<Track>, library: MusicLibrary) =
        PlaylistRow(playlist.id, playlist.name, PlaylistText.trackCount(tracks.size), tracks.firstOrNull()?.let { library.albumHolding(it.filePath) })

    /** One playlist's songs, in its order, for its screen; null when there is no such playlist (deleted). */
    suspend fun shown(id: String, library: MusicLibrary): ShownPlaylist? = withContext(serial) {
        if (id == RECENT_ID) {
            val albums = RecentlyAdded.albums(library, clock())
            val tracks = RecentlyAdded.tracks(albums)
            return@withContext ShownPlaylist(
                id, RecentlyAdded.NAME, RecentlyAdded.window, PlaylistText.headerTrackCount(tracks.size, 0),
                tracks.sumOf { it.durationMs }, tracks, albums.firstOrNull(), numbered = false,
                empty = if (tracks.isEmpty()) PlaylistText.recentEmpty else null,
            )
        }
        val store = store()
        val playlist = store.playlists.firstOrNull { it.id == id } ?: return@withContext null
        val resolved = resolverFor(library).resolve(playlist)
        if (resolved.changed) store.save()
        ShownPlaylist(
            id, playlist.name, PlaylistText.modified(playlist.modifiedUtc), PlaylistText.headerTrackCount(resolved.tracks.size, resolved.missing),
            resolved.durationMs, resolved.tracks, resolved.tracks.firstOrNull()?.let { library.albumHolding(it.filePath) }, numbered = true,
            empty = if (resolved.tracks.isEmpty()) PlaylistText.empty(playlist.isLiked, resolved.missing) else null,
        )
    }

    companion object {
        /** Recently Added's id: worked out from the library, it has no entry in the store. */
        const val RECENT_ID = "recently-added"
    }
}

/** A row of the Playlists tab: the name, "12 tracks", and the album whose cover stands for it (the first song's). */
class PlaylistRow(val id: String, val name: String, val trackCount: String, val coverAlbum: Album?)

/**
 * A playlist as its screen shows it. [line] is "Modified 2026-11-12" or Recently
 * Added's "Last 30 days"; [trackCount] "12 tracks · 2 not found". [numbered]: the #
 * column shows the place in the playlist; else (Recently Added, whose songs run
 * album by album) the track number, as the desktop's does. [empty] is the note
 * shown in place of the songs when there are none.
 */
class ShownPlaylist(
    val id: String,
    val name: String,
    val line: String,
    val trackCount: String,
    val durationMs: Long,
    val tracks: List<Track>,
    val coverAlbum: Album?,
    val numbered: Boolean,
    val empty: Pair<String, String>? = null,
)
