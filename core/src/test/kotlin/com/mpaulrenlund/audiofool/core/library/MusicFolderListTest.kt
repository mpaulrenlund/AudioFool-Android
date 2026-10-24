package com.mpaulrenlund.audiofool.core.library

import com.mpaulrenlund.audiofool.core.model.Track
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

// Ported from the desktop's MusicFolderListTests.cs and DriveChangeTests.cs
// (with MovedCopyTests), on Android paths.

class MusicFolderListTest {
    private fun t(path: String) = Track(filePath = path)

    @Test fun normalize_drops_a_trailing_separator_but_keeps_the_root() {
        assertEquals("/storage/1A2B-3C4D/Music", MusicFolderList.normalize("/storage/1A2B-3C4D/Music"))
        assertEquals("/storage/1A2B-3C4D/Music", MusicFolderList.normalize("/storage/1A2B-3C4D/Music/"))
        assertEquals("/storage/1A2B-3C4D/Music", MusicFolderList.normalize(" /storage/1A2B-3C4D/Music// "))
        assertEquals("/", MusicFolderList.normalize("/"))
    }

    @Test fun same_folder_ignores_case_and_a_trailing_separator() {
        assertTrue(MusicFolderList.sameFolder("/sd/Music", "/SD/music/"))
        assertFalse(MusicFolderList.sameFolder("/sd/Music", "/sd/MusicOld"))
        assertFalse(MusicFolderList.sameFolder("/sd/Music", "/storage/emulated/0/Music"))
    }

    @Test fun distinct_merges_repeats_and_keeps_the_first_in_order() = assertEquals(
        listOf("/storage/emulated/0/Music", "/sd/Music"),
        MusicFolderList.distinct(listOf("/storage/emulated/0/Music", "/sd/Music", "/sd/Music", "/SD/music/", "", "  ")),
    )

    @Test fun removing_a_folder_takes_only_its_tracks() {
        val tracks = listOf(t("/sd/Music/A/1.flac"), t("/sd/MusicOld/B/2.flac"), t("/storage/emulated/0/Music/3.mp3"))

        val left = MusicFolderList.withoutFolder(tracks, "/sd/Music/", listOf("/storage/emulated/0/Music"))

        assertEquals(listOf("/sd/MusicOld/B/2.flac", "/storage/emulated/0/Music/3.mp3"), left.map { it.filePath })
    }

    @Test fun removing_a_folder_keeps_tracks_another_folder_still_covers() {
        val tracks = listOf(t("/sd/Music/A/1.flac"), t("/sd/Music/B/2.flac"))

        // /sd is still watched, so nothing under /sd/Music leaves the library.
        assertEquals(2, MusicFolderList.withoutFolder(tracks, "/sd/Music", listOf("/sd")).size)

        // /sd/Music/A is still watched, so only B goes.
        assertEquals(listOf("/sd/Music/A/1.flac"),
            MusicFolderList.withoutFolder(tracks, "/sd/Music", listOf("/sd/Music/A")).map { it.filePath })
    }
}

class DriveChangeTest {
    private fun present(vararg folders: String): (String) -> Boolean =
        { folder -> folders.any { MusicFolderList.sameFolder(it, folder) } }

    private val card = "/storage/1A2B-3C4D/Music"

    @Test fun storage_that_holds_no_music_folder_changes_nothing() {
        // A USB stick plugged in with the library all there.
        assertFalse(DriveChange.worthRefresh(listOf(card), emptyList(), arrived = true, present(card)))
        assertFalse(DriveChange.worthRefresh(listOf(card), emptyList(), arrived = false, present(card)))
    }

    @Test fun a_folder_back_again_refreshes() =
        assertTrue(DriveChange.worthRefresh(listOf(card), listOf(card), arrived = true, present(card)))

    @Test fun a_folder_gone_since_the_last_scan_refreshes() =
        assertTrue(DriveChange.worthRefresh(listOf(card), emptyList(), arrived = false, present()))

    @Test fun a_folder_already_known_gone_refreshes_only_when_storage_arrives() {
        assertTrue(DriveChange.worthRefresh(listOf(card), listOf(card), arrived = true, present()))
        assertFalse(DriveChange.worthRefresh(listOf(card), listOf(card), arrived = false, present()))
    }

    @Test fun folders_are_matched_however_they_are_written() =
        assertFalse(DriveChange.worthRefresh(listOf(card), listOf("/storage/1a2b-3c4d/music/"), arrived = false, present()))

    @Test fun one_changed_folder_among_several_is_enough() = assertTrue(
        DriveChange.worthRefresh(listOf("/storage/emulated/0/Music", card), emptyList(), arrived = false, present("/storage/emulated/0/Music")),
    )

    @Test fun no_music_folders_never_refresh() =
        assertFalse(DriveChange.worthRefresh(emptyList(), emptyList(), arrived = true, present()))
}

class MovedCopyTest {
    private val moves = listOf(LibraryRelocator.Relocation("/storage/AAAA-1111/Music", "/storage/BBBB-2222/Music"))

    @Test fun a_moved_track_becomes_the_librarys_copy_at_the_new_path() {
        val old = Track(filePath = "/storage/AAAA-1111/Music/A/01.flac", title = "Old")
        val copy = Track(filePath = "/storage/BBBB-2222/Music/A/01.flac", title = "Library")

        assertSame(copy, LibraryRelocator.movedCopy(old, moves, mapOf(Paths.key(copy.filePath) to copy)))
    }

    @Test fun a_moved_track_the_library_lacks_is_re_pointed_itself() {
        val old = Track(
            filePath = "/storage/AAAA-1111/Music/A/01.flac",
            folderArtPath = "/storage/AAAA-1111/Music/A/cover.jpg",
            title = "Kept",
        )

        val moved = assertNotNull(LibraryRelocator.movedCopy(old, moves, emptyMap()))
        assertEquals("/storage/BBBB-2222/Music/A/01.flac", moved.filePath)
        assertEquals("/storage/BBBB-2222/Music/A/cover.jpg", moved.folderArtPath)
        assertEquals("Kept", moved.title)
    }

    @Test fun a_track_outside_the_moved_folders_stays() =
        assertNull(LibraryRelocator.movedCopy(Track(filePath = "/storage/emulated/0/Download/song.flac"), moves, emptyMap()))
}
