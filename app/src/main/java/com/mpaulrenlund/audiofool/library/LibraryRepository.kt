package com.mpaulrenlund.audiofool.library

import android.os.SystemClock
import com.mpaulrenlund.audiofool.core.library.LibraryCache
import com.mpaulrenlund.audiofool.core.library.LibraryRelocator
import com.mpaulrenlund.audiofool.core.library.LibraryScanner
import com.mpaulrenlund.audiofool.core.library.MusicLibrary
import com.mpaulrenlund.audiofool.core.library.Paths
import com.mpaulrenlund.audiofool.core.library.SafeFile
import com.mpaulrenlund.audiofool.core.library.ScanProgress
import com.mpaulrenlund.audiofool.core.library.ScanSummary
import com.mpaulrenlund.audiofool.core.library.TagReader
import com.mpaulrenlund.audiofool.core.model.Track
import com.mpaulrenlund.audiofool.settings.MusicFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** What the screens show of the library. */
data class LibraryState(
    /** Null until the cache (or the first scan) has given a library. */
    val library: MusicLibrary? = null,
    /** Included folders the last scan couldn't reach; their tracks are kept, shown as unavailable. */
    val unavailableFolders: List<String> = emptyList(),
    val scanning: Boolean = false,
    val progress: ScanProgress? = null,
    val stats: LibraryStats = LibraryStats(),
) {
    /** Whether a track sits on a folder that can't be reached right now (a removed SD card). */
    fun isUnavailable(track: Track): Boolean = unavailableFolders.any { LibraryRelocator.isUnder(track.filePath, it) }
}

/** The numbers the debug screen shows, for the user to report back from the phone. */
data class LibraryStats(
    /** Reading the cache and building the library from it (ms); null without a cache. */
    val cacheLoadMs: Long? = null,
    val cacheTracks: Int = 0,
    val cacheBytes: Long = 0,
    val lastScanMs: Long? = null,
    val lastScanSummary: ScanSummary? = null,
    val scans: Int = 0,
)

/**
 * Loads the library from the cache at once, then rescans in the background and
 * saves the cache when something changed: the desktop's start-up, from its
 * `LibraryCache` and `LibraryScanner`. A folder that can't be reached keeps its
 * tracks, carried over from the cache; a folder left out of the list keeps them
 * too, hidden, so ticking it again needs no scan.
 */
class LibraryRepository(
    private val cachePath: String,
    private val scope: CoroutineScope,
    private val folders: () -> List<MusicFolder>,
) {
    private val scanner = LibraryScanner(TagReader(TagLibNative), parallelism = 4)
    private val scanLock = Mutex()
    private var scanJob: Job? = null

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    /** Every track the library knows, shown or not, for the next scan to reuse. */
    @Volatile private var known: List<Track> = emptyList()

    private fun included() = folders().filter { it.included }.map { it.path }

    /** Reads the cache (on the caller's thread, so call it off the main one), then starts a rescan. */
    fun start() {
        val began = SystemClock.uptimeMillis()
        val cache = LibraryCache.load(cachePath)
        if (cache != null) {
            known = cache.tracks
            val library = LibraryScanner.build(shown(cache.tracks))
            _state.update {
                it.copy(
                    library = library,
                    stats = it.stats.copy(
                        cacheLoadMs = SystemClock.uptimeMillis() - began,
                        cacheTracks = cache.tracks.size,
                        cacheBytes = File(cachePath).length(),
                    ),
                )
            }
        }
        rescan(rereadTags = cache?.needsReread == true)
    }

    /** Scans the included folders again; tags are reused for files whose size and date haven't changed. */
    fun rescan(rereadTags: Boolean = false) {
        scanJob?.cancel()
        scanJob = scope.launch {
            scanLock.withLock {
                val included = included()
                val all = folders().map { it.path }
                _state.update { it.copy(scanning = true, progress = null) }
                val began = SystemClock.uptimeMillis()
                try {
                    val result = scanner.scan(
                        rootFolders = included,
                        known = known.associateBy { Paths.key(it.filePath) },
                        progress = { p -> _state.update { it.copy(progress = p) } },
                        rereadTags = rereadTags,
                    )
                    // Tracks of folders that are listed but left out stay known; tracks
                    // of folders no longer listed at all are dropped (MusicFolderList).
                    val leftOut = known.filter { t ->
                        included.none { LibraryRelocator.isUnder(t.filePath, it) } &&
                            all.any { LibraryRelocator.isUnder(t.filePath, it) }
                    }
                    val before = known.size
                    known = result.library.allTracks + leftOut
                    if (com.mpaulrenlund.audiofool.BuildConfig.DEBUG && result.library.allTracks.size <= 200) {
                        // For checking tags on the emulator: one line per track in logcat.
                        for (t in result.library.allTracks) android.util.Log.d("AudioFoolScan", t.toCheckLine())
                    }
                    if (result.summary.anyChanges || known.size != before || !File(cachePath).exists()) {
                        LibraryCache.from(all, known, System.currentTimeMillis()).save(cachePath)
                    }
                    _state.update {
                        it.copy(
                            library = result.library,
                            unavailableFolders = result.unavailableFolders,
                            stats = it.stats.copy(
                                lastScanMs = SystemClock.uptimeMillis() - began,
                                lastScanSummary = result.summary,
                                scans = it.stats.scans + 1,
                                cacheBytes = File(cachePath).length(),
                            ),
                        )
                    }
                } finally {
                    _state.update { it.copy(scanning = false, progress = null) }
                }
            }
        }
    }

    /**
     * The folder list changed: show what is already known for the new list at
     * once, then scan for the rest.
     */
    fun foldersChanged() {
        _state.update { it.copy(library = LibraryScanner.build(shown(known))) }
        rescan()
    }

    /** Forgets the cache, so the next start reads every tag (the debug screen's way to time a full scan). */
    fun deleteCache() {
        File(cachePath).delete()
        File(SafeFile.backupPathFor(cachePath)).delete()
        known = emptyList()
    }

    private fun shown(tracks: List<Track>): List<Track> {
        val included = included()
        return tracks.filter { t -> included.any { LibraryRelocator.isUnder(t.filePath, it) } }
    }
}

/** Every field a scan read, on one line, for the emulator check. */
private fun Track.toCheckLine(): String =
    "${Paths.fileName(filePath)} | kind=$kind | title=$title | artist=$artist | albumArtist=$albumArtist | album=$album | " +
        "track=$trackNumber/$trackCount | disc=$discNumber/$discCount | year=$year | date=$releaseDate | ms=$durationMs | " +
        "rate=$sampleRate | bits=$bitDepth | kbps=$bitrate | folderArt=${folderArtPath?.let(Paths::fileName)}"
