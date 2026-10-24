package com.mpaulrenlund.audiofool.core.art

import com.mpaulrenlund.audiofool.core.model.Album
import com.mpaulrenlund.audiofool.core.model.Track
import org.junit.After
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Ported from the desktop's tests/AudioFool.Core.Tests/ArtDiskCacheTests.cs: names
// that survive a move and change with the files, the "no cover" record, pruning.
// (Its tag-reading tests belong to TagLib, which is checked on the emulator.)

class ArtDiskCacheTest {
    private val folder: File = Files.createTempDirectory("AudioFool-art").toFile().resolve("art")
    private val now = 1_791_806_400_000L // 2026-10-12 12:00 UTC
    private val day = 24L * 60 * 60 * 1000

    @After fun cleanUp() {
        folder.parentFile.deleteRecursively()
    }

    private fun albumAt(root: String, size: Long = 1000, tracks: Int = 3, modified: Long = 1_767_225_600_000L) = Album(
        title = "Moving Pictures",
        artistName = "Rush",
        tracks = (1..tracks).map { i ->
            Track(filePath = "$root/Rush/Moving Pictures/%02d.flac".format(i), fileSize = size + i, modifiedUtc = modified)
        },
    )

    @Test fun same_album_on_another_volume_has_the_same_name() =
        assertEquals(ArtDiskCache.keyFor(albumAt("/storage/1A2B-3C4D"), 96), ArtDiskCache.keyFor(albumAt("/storage/emulated/0"), 96))

    @Test fun a_changed_file_or_another_size_gives_another_name() {
        val key = ArtDiskCache.keyFor(albumAt("/sd"), 96)
        assertNotEquals(key, ArtDiskCache.keyFor(albumAt("/sd", modified = now), 96))
        assertNotEquals(key, ArtDiskCache.keyFor(albumAt("/sd", size = 2000), 96))
        assertNotEquals(key, ArtDiskCache.keyFor(albumAt("/sd"), 320))
    }

    @Test fun only_the_first_five_tracks_count() {
        // The cover is looked for in the first five tracks only.
        val album = albumAt("/sd", tracks = 12)
        val sixthChanged = Album(
            title = album.title,
            artistName = album.artistName,
            tracks = album.tracks.mapIndexed { i, t -> if (i < 5) t else t.copy(fileSize = 1) },
        )
        assertEquals(ArtDiskCache.keyFor(album, 96), ArtDiskCache.keyFor(sixthChanged, 96))
    }

    @Test fun a_copy_reads_back_and_an_empty_one_means_no_cover() {
        val cache = ArtDiskCache(folder.path)
        assertNull(cache.tryRead("a", now))

        cache.write("a", byteArrayOf(1, 2, 3))
        cache.write("b", null)

        assertContentEquals(byteArrayOf(1, 2, 3), assertNotNull(cache.tryRead("a", now)).image)
        assertNull(assertNotNull(cache.tryRead("b", now)).image)
        assertTrue(cache.contains("b"))
    }

    @Test fun remove_deletes_a_copy() {
        val cache = ArtDiskCache(folder.path)
        cache.write("a", byteArrayOf(1))
        cache.remove("a")
        cache.remove("never written")
        assertFalse(cache.contains("a"))
    }

    @Test fun prune_deletes_copies_unused_for_90_days_and_old_temp_files() {
        val cache = ArtDiskCache(folder.path)
        cache.write("old", byteArrayOf(1))
        cache.write("recent", byteArrayOf(2))
        File(cache.pathFor("old")).setLastModified(now - 91 * day)
        File(cache.pathFor("recent")).setLastModified(now - 89 * day)
        val temp = File(folder, "x.art.123.tmp").apply { writeBytes(byteArrayOf(0)); setLastModified(now - 2 * day) }
        val other = File(folder, "notes.txt").apply { writeText("not ours"); setLastModified(now - 400 * day) }

        assertEquals(2, cache.prune(now))
        assertFalse(cache.contains("old"))
        assertTrue(cache.contains("recent"))
        assertFalse(temp.exists())
        assertTrue(other.exists())
    }

    @Test fun using_an_old_copy_keeps_it_from_being_pruned() {
        val cache = ArtDiskCache(folder.path)
        cache.write("a", byteArrayOf(1))
        File(cache.pathFor("a")).setLastModified(now - 60 * day)

        assertNotNull(cache.tryRead("a", now))

        assertEquals(0, cache.prune(now + 45 * day))
        assertTrue(cache.contains("a"))
    }

    @Test fun prune_with_no_folder_yet_does_nothing() = assertEquals(0, ArtDiskCache(folder.path).prune(now))
}
