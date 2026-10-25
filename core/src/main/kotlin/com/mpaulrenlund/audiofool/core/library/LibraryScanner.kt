package com.mpaulrenlund.audiofool.core.library

import com.mpaulrenlund.audiofool.core.model.Album
import com.mpaulrenlund.audiofool.core.model.ArtistGroup
import com.mpaulrenlund.audiofool.core.model.Track
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext
import java.nio.file.Paths as NioPaths

/**
 * Walks folders, reads tags in parallel, and assembles the Artist -> Album -> Song
 * tree with [SortRules] applied at every level. Ported from the desktop's
 * `AudioFool.Core/Library/LibraryScanner.cs`, with its tests.
 */
class LibraryScanner(
    private val tagReader: TagReader,
    /** How many files are read at once. The desktop uses every core; an SD card prefers fewer. */
    private val parallelism: Int = 4,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /** A file found by the walk, with the stat that came with it and its folder's cover. */
    data class FoundFile(val path: String, val stamp: FileStamp, val folderArt: String?)

    /**
     * Scans the given folders. When [known] (keyed by [Paths.key]) is supplied,
     * any file whose size and write time still match its cached entry keeps the
     * tags it already had instead of being re-read.
     *
     * Folders that aren't reachable (a removed SD card, most often) are reported
     * rather than reconciled. Their tracks are carried over untouched, so a
     * missing card can never be mistaken for a deleted library. A folder that
     * goes during the scan is treated the same way, and so is a file that can't
     * be opened: it keeps the tags it had rather than a blank stand-in.
     *
     * [rereadTags] re-reads every reachable file even when it matches the cache,
     * for a cache from an older version that lacks a field.
     *
     * [onRead] is given each track as its tags are read, from the reading threads,
     * so a first scan can show the library as it is found (session 2 on the M21:
     * 27,000 files take minutes). The result is still only what [scan] returns.
     */
    suspend fun scan(
        rootFolders: List<String>,
        known: Map<String, Track>? = null,
        progress: ((ScanProgress) -> Unit)? = null,
        rereadTags: Boolean = false,
        onRead: ((Track) -> Unit)? = null,
    ): ScanResult = withContext(dispatcher) {
        val reachable = mutableListOf<String>()
        val unavailable = mutableListOf<String>()

        for (folder in rootFolders) {
            if (folder.isBlank()) continue
            if (directoryReachable(folder)) reachable.add(folder) else unavailable.add(folder)
        }

        val brokenOff = mutableListOf<String>()
        val files = enumerateAudioFiles(reachable, brokenOff)

        scanFiles(files, reachable, unavailable, brokenOff, known, progress, rereadTags, onRead)
    }

    private suspend fun scanFiles(
        files: List<FoundFile>,
        reachableFolders: List<String>,
        unavailableFolders: List<String>,
        brokenOffFolders: List<String>,
        known: Map<String, Track>?,
        progress: ((ScanProgress) -> Unit)?,
        rereadTags: Boolean,
        onRead: ((Track) -> Unit)?,
    ): ScanResult {
        // Decide reuse-or-read for every file we can see. The stat came with the walk.
        val reusable = ArrayList<Track>()
        val toRead = ArrayList<FoundFile>()
        var backfilled = 0

        for (file in files) {
            coroutineContext.ensureActive()
            var cached = known?.get(Paths.key(file.path))

            if (cached != null && !rereadTags && cached.matchesFile(file.stamp.length, file.stamp.modifiedUtc)) {
                // The date added comes with the stat, so an older cache gets it
                // here rather than needing every tag read again.
                if (cached.addedUtc == null && file.stamp.createdUtc != 0L) {
                    cached = cached.copy(addedUtc = file.stamp.createdUtc)
                    backfilled++
                }
                reusable.add(cached)
            } else {
                toRead.add(file)
            }
        }

        // Progress counts only the files actually being read.
        progress?.invoke(ScanProgress(toRead.size, 0))

        val freshlyRead = ArrayList<Track>(toRead.size)
        var unopened = 0
        if (toRead.isNotEmpty()) {
            for ((track, couldNotOpen) in readTags(toRead, progress, onRead)) {
                // A file that couldn't be opened (its card gone mid-scan) keeps the
                // tags it had: a blank stand-in would replace them in the cache.
                val had = if (couldNotOpen) known?.get(Paths.key(track.filePath)) else null
                if (had != null) {
                    reusable.add(had)
                    unopened++
                } else {
                    freshlyRead.add(track)
                }
            }
        }

        // A folder whose listing broke off, or that is gone now the tags are read,
        // was unplugged mid-scan. What was seen of it is only part of it, so it is
        // treated as unplugged from the start: its tracks come from the cache, and
        // the files it no longer showed are not taken as deleted.
        val lost = reachableFolders.filter { folder ->
            brokenOffFolders.any { Paths.same(it, folder) } || !directoryReachable(folder)
        }

        var unavailable = unavailableFolders
        if (lost.isNotEmpty()) {
            val onLost = { track: Track -> lost.any { LibraryRelocator.isUnder(track.filePath, it) } }
            reusable.removeAll(onLost)
            freshlyRead.removeAll(onLost)
            unavailable = unavailableFolders + lost
        }

        // Tracks on an unreachable folder are kept exactly as they were. Without
        // this, removing the SD card would report the whole library as removed and
        // the cache would be overwritten with nothing.
        val carriedOver = ArrayList<Track>()
        if (known != null && unavailable.isNotEmpty()) {
            for (track in known.values) {
                if (unavailable.any { LibraryRelocator.isUnder(track.filePath, it) }) carriedOver.add(track)
            }
        }

        val all = ArrayList<Track>(reusable.size + freshlyRead.size + carriedOver.size)
        all.addAll(reusable)
        all.addAll(freshlyRead)
        all.addAll(carriedOver)

        // Removed means a cached entry we looked for and could not find - not one
        // that merely changed, which is still here under the same path.
        var removed = 0
        if (known != null) {
            val kept = all.mapTo(HashSet()) { Paths.key(it.filePath) }
            removed = known.keys.count { it !in kept }
        }

        return ScanResult(
            library = build(all),
            summary = ScanSummary(
                reused = reusable.size + carriedOver.size,
                read = freshlyRead.size,
                removed = removed,
                backfilled = backfilled,
                unopened = unopened,
            ),
            unavailableFolders = unavailable,
        )
    }

    private suspend fun readTags(
        files: List<FoundFile>,
        progress: ((ScanProgress) -> Unit)?,
        onRead: ((Track) -> Unit)?,
    ): List<TagReader.Result> = coroutineScope {
        val read = AtomicInteger()
        val step = maxOf(1, files.size / 100)
        val chunk = maxOf(1, (files.size + parallelism - 1) / parallelism)

        files.chunked(chunk).map { part ->
            async(dispatcher) {
                part.map { file ->
                    ensureActive()
                    val result = tagReader.read(file.path, file.stamp, file.folderArt)
                    onRead?.invoke(result.track)

                    // Report roughly every 1%, so a big scan doesn't drown the UI.
                    val done = read.incrementAndGet()
                    if (done % step == 0 || done == files.size) progress?.invoke(ScanProgress(files.size, done))
                    result
                }
            }
        }.awaitAll().flatten()
    }

    companion object {
        const val UNKNOWN_ARTIST = "Unknown Artist"
        const val UNKNOWN_ALBUM = "Unknown Album"

        /**
         * Whether a folder can be read right now. A removed SD card reports as
         * simply absent, which is what we need to distinguish.
         */
        fun directoryReachable(folder: String): Boolean = try {
            File(folder).isDirectory
        } catch (e: SecurityException) {
            false
        }

        /** Recursive walk of every root, skipping hidden (dot) files and folders. */
        fun enumerateAudioFiles(rootFolders: List<String>): List<FoundFile> =
            enumerateAudioFiles(rootFolders, mutableListOf())

        /**
         * As above, adding to [brokenOff] each root whose listing stopped partway
         * (its card pulled, most likely), so only part of it is listed.
         */
        fun enumerateAudioFiles(rootFolders: List<String>, brokenOff: MutableList<String>): List<FoundFile> {
            val results = ArrayList<FoundFile>()
            val seen = HashSet<String>()

            for (root in rootFolders) {
                if (root.isBlank()) continue

                // Reachable a moment ago, so gone since: nothing of it was listed.
                if (!directoryReachable(root)) {
                    brokenOff.add(root)
                    continue
                }

                try {
                    walk(root, results, seen)
                } catch (e: IOException) {
                    // A root that vanished mid-walk shouldn't kill the whole scan,
                    // and what was listed of it is only part of it.
                    brokenOff.add(root)
                }
            }
            return results
        }

        private fun walk(root: String, results: MutableList<FoundFile>, seen: MutableSet<String>) {
            // Per folder: the audio files and every name, so the folder's cover is
            // found from the listing rather than by asking for fifteen names per song.
            val pending = ArrayDeque<MutableList<Pair<String, FileStamp>>>()
            val names = ArrayDeque<MutableList<String>>()

            Files.walkFileTree(NioPaths.get(root), object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (dir.toString() != root && isHidden(dir)) return FileVisitResult.SKIP_SUBTREE
                    pending.addLast(mutableListOf())
                    names.addLast(mutableListOf())
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val name = file.fileName?.toString() ?: return FileVisitResult.CONTINUE
                    if (name.startsWith('.')) return FileVisitResult.CONTINUE
                    names.last().add(name)
                    val path = file.toString()
                    if (attrs.isRegularFile && AudioFormats.isSupported(path) && seen.add(Paths.key(path)))
                        pending.last().add(path to FileStamp.of(attrs))
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = when (exc) {
                    // Inaccessible folders are skipped, as the desktop's IgnoreInaccessible does,
                    // and so is something deleted while being listed.
                    is AccessDeniedException, is NoSuchFileException ->
                        if (file.toString() == root) throw exc else FileVisitResult.CONTINUE
                    else -> throw exc
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    if (exc != null) throw exc
                    val art = AudioFormats.pickFolderArt(names.removeLast())
                        ?.let { Paths.combine(dir.toString(), it) }
                    for ((path, stamp) in pending.removeLast()) results.add(FoundFile(path, stamp, art))
                    return FileVisitResult.CONTINUE
                }
            })
        }

        private fun isHidden(path: Path) = path.fileName?.toString()?.startsWith('.') == true

        /** Groups flat tracks into the sorted artist/album tree. */
        fun build(tracks: Iterable<Track>): MusicLibrary {
            val allTracks = tracks.toList()

            val artists = allTracks
                .groupBy { SortRules.nameKey(displayName(it.groupingArtist, UNKNOWN_ARTIST)) }
                .values
                .map(::buildArtist)

            return MusicLibrary(artists = SortRules.sortArtists(artists), allTracks = allTracks)
        }

        private fun buildArtist(group: List<Track>): ArtistGroup {
            // Use the most common spelling as the display name; tags are inconsistent
            // about casing and the list shouldn't flip-flop between scans.
            val name = group
                .groupingBy { displayName(it.groupingArtist, UNKNOWN_ARTIST) }
                .eachCount()
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .first().key

            val albums = group
                .groupBy { SortRules.nameKey(displayName(it.album, UNKNOWN_ALBUM)) }
                .values
                .map { buildAlbum(name, it) }

            return ArtistGroup(name = name, sortKey = SortRules.artistSortKey(name), albums = SortRules.sortAlbums(albums))
        }

        private fun buildAlbum(artistName: String, group: List<Track>): Album {
            val tracks = SortRules.sortTracks(group)

            return Album(
                // The desktop's GroupBy keeps the first spelling it met as the key.
                title = displayName(group.first().album, UNKNOWN_ALBUM),
                artistName = artistName,
                // Earliest tagged year wins: reissue tags on a few tracks shouldn't
                // drag a 1969 album down to the bottom of the list.
                year = tracks.mapNotNull { it.year }.minOrNull(),
                sortDate = tracks.mapNotNull { it.sortDate }.minOrNull(),
                tracks = tracks,
                folderArtPath = tracks.firstNotNullOfOrNull { it.folderArtPath },
            )
        }

        private fun displayName(value: String?, fallback: String): String =
            if (value.isNullOrBlank()) fallback else value.trim()
    }
}
