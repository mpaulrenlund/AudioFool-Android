package com.mpaulrenlund.audiofool.core.library

import com.mpaulrenlund.audiofool.core.model.Track
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Ported from the desktop's PortableDriveTests.cs, DriveLostMidScanTests.cs,
// LibraryCacheTests.cs (ScanSummary) and SortRulesTests.cs (DateAdded): the
// scan's handling of folders that can't be reached. A missing SD card is never
// a deleted library.

/** Tags as the JVM tests see them: no tag library, so files open but don't parse. */
class FakeTagSource(private val unopened: Set<String> = emptySet()) : TagSource {
    override fun read(path: String): RawTags = when {
        path in unopened || !File(path).canRead() -> RawTags(RawTags.Status.Unopened)
        else -> RawTags(RawTags.Status.Unparsed)
    }
}

fun scanner(source: TagSource = FakeTagSource()) = LibraryScanner(TagReader(source), parallelism = 2)

fun known(vararg tracks: Track): Map<String, Track> = tracks.associateBy { Paths.key(it.filePath) }

class PathContainmentTest {
    @Test fun containment_is_recognised() {
        listOf(
            Triple("/storage/1A2B-3C4D/Music/a.flac", "/storage/1A2B-3C4D/Music", true),
            Triple("/storage/1A2B-3C4D/Music/Sub/a.flac", "/storage/1A2B-3C4D/Music", true),
            Triple("/storage/1a2b-3c4d/music/a.flac", "/storage/1A2B-3C4D/Music", true),
            Triple("/storage/1A2B-3C4D/Music", "/storage/1A2B-3C4D/Music", true),
            Triple("/storage/1A2B-3C4D/Music/a.flac", "/storage/1A2B-3C4D/Music/", true),
            Triple("/storage/emulated/0/Music/a.flac", "/storage/1A2B-3C4D/Music", false),
            Triple("/storage/1A2B-3C4D/Other/a.flac", "/storage/1A2B-3C4D/Music", false),
            // The desktop's own cases, which still hold.
            Triple("""E:\Music\a.flac""", """E:\Music""", true),
            Triple("""F:\Music\a.flac""", """E:\Music""", false),
        ).forEach { (path, folder, expected) -> assertEquals(expected, LibraryRelocator.isUnder(path, folder), "$path in $folder") }
    }

    @Test fun a_sibling_with_a_shared_prefix_is_not_inside() {
        // Plain startsWith would wrongly say "MusicOld" is inside "Music".
        assertFalse(LibraryRelocator.isUnder("/storage/x/MusicOld/a.flac", "/storage/x/Music"))
        assertFalse(LibraryRelocator.isUnder("/storage/x/Musical/a.flac", "/storage/x/Music"))
    }
}

class RebaseTest {
    @Test fun paths_move_to_the_new_root() {
        assertEquals("/storage/BBBB-2222/Music/Artist/a.flac",
            LibraryRelocator.rebase("/storage/AAAA-1111/Music/Artist/a.flac", "/storage/AAAA-1111/Music", "/storage/BBBB-2222/Music"))
        assertEquals("/storage/emulated/0/Media/Music/a.flac",
            LibraryRelocator.rebase("/storage/AAAA-1111/Music/a.flac", "/storage/AAAA-1111/Music", "/storage/emulated/0/Media/Music"))
    }

    @Test fun paths_outside_the_old_root_are_left_alone() {
        val other = "/storage/emulated/0/Elsewhere/a.flac"
        assertEquals(other, LibraryRelocator.rebase(other, "/storage/AAAA-1111/Music", "/storage/BBBB-2222/Music"))
    }

    @Test fun rebasing_a_library_moves_both_the_file_and_its_folder_art() {
        val tracks = listOf(
            Track(filePath = "/sd/Music/Artist/Album/01.flac", folderArtPath = "/sd/Music/Artist/Album/cover.jpg"),
            Track(filePath = "/other/keep.flac"),
        )
        val moved = LibraryRelocator.rebase(tracks, "/sd/Music", "/sd2/Music")

        assertEquals("/sd2/Music/Artist/Album/01.flac", moved[0].filePath)
        assertEquals("/sd2/Music/Artist/Album/cover.jpg", moved[0].folderArtPath)
        assertEquals("/other/keep.flac", moved[1].filePath)
    }

    @Test fun relocating_a_track_carries_every_other_field_across() {
        val original = Track(
            filePath = "/sd/Music/a.flac", folderArtPath = "/sd/Music/cover.jpg", fileSize = 4242,
            modifiedUtc = 1_778_000_000_000, addedUtc = 1_775_000_000_000, trackNumber = 3, trackCount = 9,
            title = "Title", artist = "Artist", albumArtist = "Album Artist", album = "Album", durationMs = 123_000,
            discNumber = 2, discCount = 2, year = 1999, releaseDate = "1999-03", kind = "FLAC", bitrate = 900,
            bitDepth = 24, sampleRate = 96000,
        )
        val moved = original.relocated("/sd2/Music/a.flac", "/sd2/Music/cover.jpg")

        // A data class copy: every field but the two paths is the original's.
        assertEquals(original, moved.copy(filePath = original.filePath, folderArtPath = original.folderArtPath))
        assertEquals("/sd2/Music/a.flac", moved.filePath)
        assertEquals("/sd2/Music/cover.jpg", moved.folderArtPath)
    }
}

class UnavailableFolderTest {
    /** A path that can't be mounted, standing in for a removed SD card. */
    private val missingCard = "/storage/0000-DEAD/Music"

    private fun onMissingCard(name: String) = Track(
        filePath = "$missingCard/Artist/$name.flac",
        title = name, artist = "Artist", album = "Album",
        fileSize = 1000, modifiedUtc = 1_767_225_600_000,
    )

    @get:Rule val temp = TemporaryFolder()

    @Test fun a_removed_card_does_not_look_like_a_deleted_library() = runBlocking {
        // Opening the app with the card out must not report every track as removed,
        // because the cache is then overwritten with nothing.
        val result = scanner().scan(listOf(missingCard), known(onMissingCard("one"), onMissingCard("two"), onMissingCard("three")))

        assertEquals(0, result.summary.removed)
        assertEquals(3, result.library.allTracks.size)
        assertTrue(result.anyFolderUnavailable)
        assertEquals(listOf(missingCard), result.unavailableFolders)
    }

    @Test fun tracks_on_a_removed_card_stay_browsable() = runBlocking {
        val result = scanner().scan(listOf(missingCard), known(onMissingCard("one"), onMissingCard("two")))

        val artist = result.library.artists.single()
        assertEquals("Artist", artist.name)
        assertEquals(2, artist.trackCount)
    }

    @Test fun an_empty_but_present_folder_really_does_mean_removed() = runBlocking {
        // The counterpart: a folder that IS reachable and now has nothing in it
        // genuinely means the files are gone.
        val folder = temp.newFolder("empty").path
        val gone = Paths.combine(folder, "gone.flac")

        val result = scanner().scan(listOf(folder), known(Track(filePath = gone)))

        assertFalse(result.anyFolderUnavailable)
        assertEquals(1, result.summary.removed)
        assertTrue(result.library.allTracks.isEmpty())
    }
}

class RemovedCountTest {
    @get:Rule val temp = TemporaryFolder()

    private fun write(folder: File, name: String, content: String) = File(folder, name).apply { writeText(content) }

    @Test fun a_changed_file_is_not_also_counted_as_removed() = runBlocking {
        // A file whose timestamp moved is "changed", never "removed".
        val folder = temp.newFolder("changed")
        val kept = write(folder, "kept.mp3", "aaaa")
        val touched = write(folder, "touched.mp3", "bbbb")

        val first = scanner().scan(listOf(folder.path))
        val cached = first.library.allTracks.associateBy { Paths.key(it.filePath) }
        assertEquals(2, cached.size)

        // Move one file's write time, exactly as a non-preserving copy would.
        touched.setLastModified(touched.lastModified() + 3 * 3_600_000)

        val second = scanner().scan(listOf(folder.path), cached)

        assertEquals(1, second.summary.read)     // the touched one
        assertEquals(1, second.summary.reused)   // the untouched one
        assertEquals(0, second.summary.removed)  // nothing was deleted
        assertEquals(2, second.library.allTracks.size)
        assertTrue(kept.exists())
    }

    @Test fun a_genuinely_deleted_file_still_counts_as_removed() = runBlocking {
        val folder = temp.newFolder("deleted")
        write(folder, "stays.mp3", "aaaa")
        val doomed = write(folder, "goes.mp3", "bbbb")

        val first = scanner().scan(listOf(folder.path))
        doomed.delete()

        val second = scanner().scan(listOf(folder.path), first.library.allTracks.associateBy { Paths.key(it.filePath) })

        assertEquals(1, second.summary.removed)
        assertEquals(0, second.summary.read)
        assertEquals(1, second.library.allTracks.size)
    }
}

/**
 * The card going partway through a scan. The desktop's scan used to store a
 * blank "Unknown Artist" track for every file it couldn't open, and took every
 * file it hadn't listed yet as deleted.
 */
class DriveLostMidScanTest {
    @get:Rule val temp = TemporaryFolder()

    private val folder by lazy { temp.newFolder("lost") }

    private fun write(name: String) = File(folder, name).apply { writeText("not really audio") }.path

    /** What the cache held for a file before it last changed, so the scan has to read it. */
    private fun cached(path: String, title: String) = Track(
        filePath = path, title = title, artist = "Artist", album = "Album",
        fileSize = 1, modifiedUtc = 1_577_836_800_000,
    )

    @Test fun a_known_file_that_cant_be_opened_keeps_the_tags_it_had() = runBlocking {
        val path = write("locked.mp3")

        val result = scanner(FakeTagSource(unopened = setOf(path))).scan(listOf(folder.path), known(cached(path, "Kept")))

        val track = result.library.allTracks.single()
        assertEquals("Kept", track.title)
        assertEquals("Artist", track.artist)
        assertEquals(0, result.summary.read)
        assertEquals(1, result.summary.unopened)
        assertEquals(0, result.summary.removed)
    }

    @Test fun a_new_file_that_cant_be_opened_is_listed_and_read_again_next_time() = runBlocking {
        val path = write("locked.mp3")

        val first = scanner(FakeTagSource(unopened = setOf(path))).scan(listOf(folder.path))

        // Listed, so nothing silently disappears, but not trusted as read.
        assertEquals(0, first.library.allTracks.single().fileSize)

        val second = scanner().scan(listOf(folder.path), known(*first.library.allTracks.toTypedArray()))

        assertEquals(1, second.summary.read)
        assertEquals(File(path).length(), second.library.allTracks.single().fileSize)
    }

    @Test fun a_locked_file_is_told_apart_from_one_that_opens_but_cant_be_parsed() {
        val locked = write("locked.mp3")
        val damaged = write("damaged.mp3")
        val reader = TagReader(FakeTagSource(unopened = setOf(locked)))

        assertTrue(reader.read(locked).couldNotOpen)

        // Reads the same next time, so it keeps its real stamp and isn't retried.
        val result = reader.read(damaged)
        assertFalse(result.couldNotOpen)
        assertEquals(File(damaged).length(), result.track.fileSize)
    }

    @Test fun a_folder_that_goes_mid_scan_is_unplugged_not_deleted() = runBlocking {
        val one = write("one.mp3")
        val two = write("two.mp3")
        write("new.mp3")

        // The folder goes after the files are listed and before their tags are
        // read: the card pulled partway through.
        var done = false
        val result = scanner().scan(listOf(folder.path), known(cached(one, "One"), cached(two, "Two")), progress = {
            if (!done) { done = true; folder.deleteRecursively() }
        })

        assertEquals(listOf(folder.path), result.unavailableFolders)
        assertEquals(0, result.summary.removed)
        assertEquals(listOf("One", "Two"), result.library.allTracks.map { it.title }.sorted())
    }

    @Test fun a_folder_that_is_still_there_mid_scan_is_scanned_as_usual() = runBlocking {
        val kept = write("kept.mp3")
        val gone = Paths.combine(folder.path, "gone.mp3")

        val result = scanner().scan(listOf(folder.path), known(cached(kept, "Kept"), cached(gone, "Gone")))

        assertTrue(result.unavailableFolders.isEmpty())
        assertEquals(1, result.summary.removed)
        assertEquals(1, result.summary.read)
    }
}

class DateAddedTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun a_cache_without_dates_gets_them_from_the_stat_and_counts_as_a_change() = runBlocking {
        val folder = temp.newFolder("added")
        val file = File(folder, "one.flac").apply { writeText("x") }
        val stamp = FileStamp.forPath(file.path)

        // As a cache written before addedUtc was known: it matches the file, so
        // its tags are reused, not read again.
        val old = Track(filePath = file.path, fileSize = stamp.length, modifiedUtc = stamp.modifiedUtc, title = "Cached")

        val result = scanner().scan(listOf(folder.path), known(old))

        val track = result.library.allTracks.single()
        assertEquals("Cached", track.title)
        assertEquals(stamp.createdUtc, track.addedUtc)
        assertEquals(0, result.summary.read)
        assertEquals(1, result.summary.backfilled)
        assertTrue(result.summary.anyChanges)

        // The next scan finds nothing to do.
        val again = scanner().scan(listOf(folder.path), result.library.allTracks.associateBy { Paths.key(it.filePath) })
        assertFalse(again.summary.anyChanges)
    }

    @Test fun reading_a_file_records_when_it_was_created() {
        val file = temp.newFile("two.flac").apply { writeText("x") }
        val stamp = FileStamp.forPath(file.path)
        assertEquals(stamp.createdUtc, TagReader(FakeTagSource()).read(file.path).track.addedUtc)
    }
}

class FolderArtTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun folder_art_is_found_by_priority_ignoring_case() {
        assertEquals("Cover.JPG", AudioFormats.pickFolderArt(listOf("01.flac", "folder.jpg", "Cover.JPG")))
        assertEquals("folder.png", AudioFormats.pickFolderArt(listOf("01.flac", "back.jpg", "folder.png")))
        assertNull(AudioFormats.pickFolderArt(listOf("01.flac", "scan.jpg")))
    }

    @Test fun a_scan_gives_each_track_its_folders_cover() = runBlocking {
        val album = File(temp.root, "Artist/Album").apply { mkdirs() }
        File(album, "01.flac").writeText("x")
        File(album, "folder.jpg").writeText("jpeg")
        val bare = File(temp.root, "Artist/Bare").apply { mkdirs() }
        File(bare, "01.flac").writeText("x")
        File(temp.root, ".hidden").mkdir()
        File(temp.root, ".hidden/02.flac").writeText("x")

        val tracks = scanner().scan(listOf(temp.root.path)).library.allTracks.associateBy { Paths.fileName(Paths.directoryName(it.filePath)!!) }

        assertEquals(2, tracks.size) // the hidden folder is skipped
        assertEquals(File(album, "folder.jpg").path, tracks.getValue("Album").folderArtPath)
        assertNull(tracks.getValue("Bare").folderArtPath)
    }
}

class ScanSummaryTest {
    @Test fun no_reads_and_no_removals_means_nothing_changed() {
        val summary = ScanSummary(reused = 500, read = 0, removed = 0)
        assertFalse(summary.anyChanges)
        assertTrue(summary.entirelyCached)
    }

    @Test fun any_read_or_removal_counts_as_a_change() {
        for ((reused, read, removed) in listOf(Triple(0, 1, 0), Triple(0, 0, 1), Triple(10, 2, 3))) {
            val summary = ScanSummary(reused, read, removed)
            assertTrue(summary.anyChanges)
            assertFalse(summary.entirelyCached)
        }
    }
}

// Not from the desktop: the first scan shows the library as it is found (session 2).
class ReadAsFoundTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun every_file_read_is_handed_over_as_it_is_read() = runBlocking {
        val folder = temp.newFolder("music")
        for (i in 1..9) File(folder, "song $i.mp3").writeText("x".repeat(i))
        val seen = java.util.concurrent.ConcurrentLinkedQueue<Track>()

        val result = scanner().scan(listOf(folder.path), onRead = { seen.add(it) })

        assertEquals(result.library.allTracks.map { it.filePath }.sorted(), seen.map { it.filePath }.sorted())
    }

    @Test fun files_reused_from_the_cache_are_not_handed_over() = runBlocking {
        val folder = temp.newFolder("music")
        File(folder, "old.mp3").writeText("aaaa")
        val first = scanner().scan(listOf(folder.path))
        File(folder, "new.mp3").writeText("bbbb")
        val seen = java.util.concurrent.ConcurrentLinkedQueue<Track>()

        scanner().scan(listOf(folder.path), first.library.allTracks.associateBy { Paths.key(it.filePath) }, onRead = { seen.add(it) })

        assertEquals(listOf("new.mp3"), seen.map { Paths.fileName(it.filePath) })
    }
}
