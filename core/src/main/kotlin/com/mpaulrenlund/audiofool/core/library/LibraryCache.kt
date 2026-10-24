package com.mpaulrenlund.audiofool.core.library

import com.mpaulrenlund.audiofool.core.model.Track
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * The scanned library, persisted so a start doesn't have to re-read every tag.
 * Ported from the desktop's `AudioFool.Core/Library/LibraryCache.cs`.
 *
 * The desktop stores JSON (15 MB for 26,000 tracks, a third of a second to load
 * on a PC). This is the same content in a compact binary form, so a phone can
 * load it at once: strings are stored once in a table (an artist's name, an
 * album's folder) and tracks refer to them by number.
 */
class LibraryCache(
    val version: Int = CURRENT_VERSION,
    /** Folders this cache was built from, to spot a changed library root. */
    val folders: List<String> = emptyList(),
    val savedUtc: Long = 0,
    val tracks: List<Track> = emptyList(),
) {
    /**
     * True for an older cache: its tracks are shown, but every tag is re-read in
     * the next scan, because an older cache lacks fields this version needs.
     */
    val needsReread: Boolean get() = version < CURRENT_VERSION

    /** Whether the cache was built from the same set of folders, ignoring order and case. */
    fun coversSameFolders(folders: Iterable<String>): Boolean =
        this.folders.mapTo(HashSet(), Paths::key) == folders.mapTo(HashSet(), Paths::key)

    /** Cached tracks keyed by [Paths.key], for the incremental scan to look up. */
    fun byPath(): Map<String, Track> {
        val map = HashMap<String, Track>(tracks.size * 2)
        for (track in tracks) map[Paths.key(track.filePath)] = track
        return map
    }

    /**
     * Saved beside the old one and swapped in, the old one kept as a backup
     * ([SafeFile]), so a crash mid-save leaves a good cache. A cache that fails to
     * save just means the next start is slow.
     */
    fun save(path: String): Boolean = try {
        SafeFile.write(path) { out -> write(DataOutputStream(out)) }
        true
    } catch (e: IOException) {
        false
    }

    private fun write(out: DataOutputStream) {
        val strings = StringTable()
        // Paths are split into folder and name: an album's songs share a folder.
        fun dir(path: String) = Paths.directoryName(path) ?: ""
        for (t in tracks) {
            strings.add(dir(t.filePath)); strings.add(Paths.fileName(t.filePath))
            strings.add(t.title); strings.add(t.artist); strings.add(t.albumArtist); strings.add(t.album)
            strings.add(t.kind); t.releaseDate?.let(strings::add)
            t.folderArtPath?.let { strings.add(dir(it)); strings.add(Paths.fileName(it)) }
        }
        folders.forEach(strings::add)

        out.writeInt(MAGIC)
        out.writeInt(version)
        out.writeLong(savedUtc)
        strings.write(out)
        out.writeInt(folders.size)
        folders.forEach { out.writeInt(strings[it]) }
        out.writeInt(tracks.size)
        for (t in tracks) {
            out.writeInt(strings[dir(t.filePath)]); out.writeInt(strings[Paths.fileName(t.filePath)])
            out.writeLong(t.fileSize)
            out.writeLong(t.modifiedUtc)
            out.writeLong(t.addedUtc ?: NO_TIME)
            out.writeInt(t.trackNumber ?: 0); out.writeInt(t.trackCount ?: 0)
            out.writeInt(strings[t.title]); out.writeInt(strings[t.artist])
            out.writeInt(strings[t.albumArtist]); out.writeInt(strings[t.album])
            out.writeLong(t.durationMs)
            out.writeInt(t.discNumber ?: 0); out.writeInt(t.discCount ?: 0)
            out.writeInt(t.year ?: 0)
            out.writeInt(t.releaseDate?.let(strings::get) ?: -1)
            out.writeInt(strings[t.kind])
            out.writeInt(t.bitrate ?: 0); out.writeInt(t.bitDepth ?: 0); out.writeInt(t.sampleRate ?: 0)
            val art = t.folderArtPath
            out.writeInt(art?.let { strings[dir(it)] } ?: -1)
            out.writeInt(art?.let { strings[Paths.fileName(it)] } ?: -1)
        }
        out.writeInt(MAGIC)
        out.flush()
    }

    companion object {
        /**
         * Bump when [Track] changes in a way that makes old files wrong. A version
         * this build can't read is treated as "no cache", not an error.
         */
        const val CURRENT_VERSION = 1

        /** The oldest version still worth showing while [needsReread] refreshes it. */
        const val OLDEST_SHOWN_VERSION = 1

        private const val MAGIC = 0x41464C43 // "AFLC"
        private const val NO_TIME = Long.MIN_VALUE

        fun from(folders: Iterable<String>, tracks: Iterable<Track>, nowUtc: Long, version: Int = CURRENT_VERSION) =
            LibraryCache(version = version, folders = folders.toList(), savedUtc = nowUtc, tracks = tracks.toList())

        /**
         * The cache, or null when there isn't a usable one. Never throws: a corrupt
         * or half-written cache should cost a slow start, not a crash. A missing or
         * damaged one is read from the save before: with the SD card out, no cache
         * would mean no library until it is back.
         */
        fun load(path: String, nowUtc: Long = System.currentTimeMillis()): LibraryCache? {
            val (cache, _) = SafeFile.read(path, nowUtc, ::parse)
            if (cache == null || cache.version !in OLDEST_SHOWN_VERSION..CURRENT_VERSION || cache.tracks.isEmpty())
                return null
            return cache
        }

        /** Reads the bytes [save] wrote; null when they aren't a cache. */
        fun parse(bytes: ByteArray): LibraryCache? {
            val input = DataInputStream(ByteArrayInputStream(bytes))
            if (bytes.size < 8 || input.readInt() != MAGIC) return null
            val version = input.readInt()
            if (version !in OLDEST_SHOWN_VERSION..CURRENT_VERSION) return LibraryCache(version = version)
            val savedUtc = input.readLong()
            val strings = StringTable.read(input, bytes.size)
            fun s(i: Int) = strings[i]
            fun path(dir: Int, name: Int) = s(dir).let { d -> if (d.isEmpty()) s(name) else Paths.combine(d, s(name)) }

            val folders = List(checked(input.readInt(), bytes.size)) { s(input.readInt()) }
            val count = checked(input.readInt(), bytes.size)
            val tracks = ArrayList<Track>(count)
            repeat(count) {
                val filePath = path(input.readInt(), input.readInt())
                val fileSize = input.readLong()
                val modified = input.readLong()
                val added = input.readLong().takeIf { it != NO_TIME }
                val trackNumber = input.readInt().orNull()
                val trackCount = input.readInt().orNull()
                val title = s(input.readInt())
                val artist = s(input.readInt())
                val albumArtist = s(input.readInt())
                val album = s(input.readInt())
                val duration = input.readLong()
                val disc = input.readInt().orNull()
                val discCount = input.readInt().orNull()
                val year = input.readInt().orNull()
                val releaseDate = input.readInt().takeIf { it >= 0 }?.let(::s)
                val kind = s(input.readInt())
                val bitrate = input.readInt().orNull()
                val bitDepth = input.readInt().orNull()
                val sampleRate = input.readInt().orNull()
                val artDir = input.readInt()
                val artName = input.readInt()
                tracks.add(
                    Track(
                        filePath = filePath, fileSize = fileSize, modifiedUtc = modified, addedUtc = added,
                        trackNumber = trackNumber, trackCount = trackCount, title = title, artist = artist,
                        albumArtist = albumArtist, album = album, durationMs = duration, discNumber = disc,
                        discCount = discCount, year = year, releaseDate = releaseDate, kind = kind,
                        bitrate = bitrate, bitDepth = bitDepth, sampleRate = sampleRate,
                        folderArtPath = if (artDir >= 0 && artName >= 0) path(artDir, artName) else null,
                    )
                )
            }
            if (input.readInt() != MAGIC) return null
            return LibraryCache(version, folders, savedUtc, tracks)
        }

        private fun Int.orNull(): Int? = takeIf { it != 0 }

        /** A count or length read from the file, refused when the file is too short to hold it. */
        private fun checked(n: Int, limit: Int): Int = if (n in 0..limit) n else throw IOException("bad length $n")
    }

    /** Each distinct string once, as UTF-8; tracks refer to them by index. */
    private class StringTable {
        private val index = LinkedHashMap<String, Int>()
        fun add(s: String) { index.getOrPut(s) { index.size } }
        operator fun get(s: String): Int = index.getValue(s)

        fun write(out: DataOutputStream) {
            out.writeInt(index.size)
            for (s in index.keys) {
                val bytes = s.toByteArray(Charsets.UTF_8)
                out.writeInt(bytes.size)
                out.write(bytes)
            }
        }

        companion object {
            fun read(input: DataInputStream, limit: Int): Array<String> = Array(checked(input.readInt(), limit)) {
                val bytes = ByteArray(checked(input.readInt(), limit))
                input.readFully(bytes)
                String(bytes, Charsets.UTF_8)
            }
        }
    }
}
