package com.mpaulrenlund.audiofool.core.library

import com.mpaulrenlund.audiofool.core.model.Track
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Ported from the desktop's LibraryCacheTests.cs (FileStampMatchingTests and
// LibraryCacheTests). The format is binary here, not JSON; the behaviour is the same.

class FileStampMatchingTest {
    private val time = 1_772_563_000_000L
    private fun cached(size: Long, modified: Long) = Track(filePath = "/music/a.flac", fileSize = size, modifiedUtc = modified)

    @Test fun same_size_and_time_is_a_match() = assertTrue(cached(1000, time).matchesFile(1000, time))
    @Test fun a_different_size_is_not_a_match() = assertFalse(cached(1000, time).matchesFile(1001, time))
    @Test fun a_different_write_time_is_not_a_match() = assertFalse(cached(1000, time).matchesFile(1000, time + 1000))

    @Test fun an_edited_tag_is_caught_even_at_identical_size() =
        // Rewriting a tag in place can leave the file exactly the same length.
        assertFalse(cached(5_000_000, time).matchesFile(5_000_000, time + 60_000))

    @Test fun a_track_with_no_stamp_never_matches_a_real_file() =
        assertFalse(Track(filePath = "/music/a.flac").matchesFile(1000, time))
}

class LibraryCacheTest {
    @get:Rule val temp = TemporaryFolder()

    private fun sample(path: String) = Track(
        filePath = path,
        fileSize = 12_345,
        modifiedUtc = 1_767_323_045_000,
        addedUtc = 1_767_000_000_000,
        title = "Shredders of the Universe",
        artist = "100 Onces",
        albumArtist = "100 Onces",
        album = "100 Onces",
        trackNumber = 1,
        trackCount = 11,
        discNumber = 2,
        discCount = 3,
        year = 2014,
        releaseDate = "2014-05-01",
        durationMs = 294_000,
        kind = "FLAC",
        bitrate = 906,
        bitDepth = 16,
        sampleRate = 44100,
        folderArtPath = "/storage/1A2B-3C4D/Music/cover.jpg",
    )

    private fun roundTrip(cache: LibraryCache): LibraryCache {
        val path = File(temp.root, "library.bin").path
        assertTrue(cache.save(path))
        return assertNotNull(LibraryCache.load(path))
    }

    @Test fun survives_a_round_trip_with_every_field_intact() {
        val original = LibraryCache.from(listOf("/storage/1A2B-3C4D/Music"), listOf(sample("/storage/1A2B-3C4D/Music/a.flac")), 42)
        val restored = roundTrip(original)

        assertEquals(original.tracks, restored.tracks)
        assertEquals(original.folders, restored.folders)
        assertEquals(42, restored.savedUtc)
    }

    @Test fun round_trip_preserves_nulls() {
        // A lossy track has no bit depth; an untagged one has no numbers, date or art.
        val lossy = Track(filePath = "/music/b.mp3", kind = "MP3", fileSize = 3, modifiedUtc = 4)
        assertEquals(lossy, roundTrip(LibraryCache.from(listOf("/music"), listOf(lossy), 0)).tracks.single())
    }

    @Test fun names_outside_the_basic_plane_survive() {
        val track = sample("/music/🎵 Ünïcödé/𝄞.flac").copy(title = "日本語 🎶", artist = "Sigur Rós")
        assertEquals(track, roundTrip(LibraryCache.from(listOf("/music"), listOf(track), 0)).tracks.single())
    }

    @Test fun folder_comparison_ignores_order_and_case() {
        val cache = LibraryCache.from(listOf("/storage/emulated/0/Music", "/storage/1A2B-3C4D/More"), emptyList(), 0)

        assertTrue(cache.coversSameFolders(listOf("/storage/1a2b-3c4d/more", "/storage/emulated/0/music")))
        assertFalse(cache.coversSameFolders(listOf("/storage/emulated/0/Music")))
        assertFalse(cache.coversSameFolders(listOf("/storage/emulated/0/Music", "/storage/1A2B-3C4D/More", "/x")))
    }

    @Test fun by_path_indexes_case_insensitively() {
        val map = LibraryCache.from(listOf("/music"), listOf(sample("/Music/A.flac")), 0).byPath()
        assertTrue(Paths.key("/MUSIC/a.FLAC") in map)
    }

    @Test fun a_cache_from_a_different_version_is_rejected() {
        val path = File(temp.root, "library.bin").path
        LibraryCache(version = 999, tracks = listOf(sample("/music/a.flac"))).save(path)
        assertNull(LibraryCache.load(path))
    }

    @Test fun an_older_cache_is_shown_but_flagged_for_a_reread() {
        assertFalse(LibraryCache.from(listOf("/music"), emptyList(), 0).needsReread)
        assertTrue(LibraryCache.from(listOf("/music"), emptyList(), 0, version = 0).needsReread)
    }

    @Test fun an_empty_cache_is_no_cache() {
        val path = File(temp.root, "library.bin").path
        LibraryCache.from(listOf("/music"), emptyList(), 0).save(path)
        assertNull(LibraryCache.load(path))
    }

    @Test fun a_save_keeps_the_one_before_as_a_backup() {
        val path = File(temp.root, "library.bin").path
        LibraryCache.from(listOf("/music"), listOf(sample("/music/a.flac")), 0).save(path)
        LibraryCache.from(listOf("/music"), listOf(sample("/music/a.flac"), sample("/music/b.flac")), 0).save(path)

        assertEquals(2, LibraryCache.load(path)!!.tracks.size)
        assertTrue(File(SafeFile.backupPathFor(path)).exists())
        assertFalse(File("$path.tmp").exists())
    }

    @Test fun a_damaged_or_missing_cache_is_read_from_the_save_before() {
        // With the SD card out, no cache would mean an empty library.
        for (what in listOf("damaged", "truncated", "missing")) {
            val path = File(temp.newFolder(what), "library.bin").path
            LibraryCache.from(listOf("/music"), listOf(sample("/music/a.flac")), 0).save(path)
            LibraryCache.from(listOf("/music"), listOf(sample("/music/a.flac"), sample("/music/b.flac")), 0).save(path)

            when (what) {
                "damaged" -> File(path).writeText("""{"Version":4,"Folders":["C:/mu""")
                "truncated" -> File(path).let { it.writeBytes(it.readBytes().copyOf(it.length().toInt() / 2)) }
                else -> File(path).delete()
            }

            val loaded = assertNotNull(LibraryCache.load(path), what)
            assertEquals("/music/a.flac", loaded.tracks.single().filePath, what)
        }
    }

    @Test fun a_damaged_cache_is_moved_aside_not_saved_over() {
        val path = File(temp.root, "library.bin").path
        File(path).writeText("garbage")
        assertNull(LibraryCache.load(path, nowUtc = 0))
        assertTrue(File("$path.unreadable-19700101000000").exists())
    }
}
