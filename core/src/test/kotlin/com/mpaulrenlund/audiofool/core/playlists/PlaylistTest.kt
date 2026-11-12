package com.mpaulrenlund.audiofool.core.playlists

import com.mpaulrenlund.audiofool.core.library.SafeFile
import com.mpaulrenlund.audiofool.core.model.Track
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Playlists and Liked: what is saved, the order they're listed in, finding the
 * songs again after the music moved, and never losing a song whose file can't be
 * found. Ported from the desktop's `PlaylistTests.cs`, without its pictures and
 * its locked file (the phone has neither).
 */
class PlaylistTest {
    private val monday = LocalDateTime.of(2026, 10, 5, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val hour = 60L * 60 * 1000
    private val tuesday = monday + 24 * hour
    private val wednesday = monday + 48 * hour

    private fun newPath(): String = File(Files.createTempDirectory("AudioFoolTests").toFile(), "playlists.json").path

    private fun t(path: String, artist: String = "Jingoro", album: String = "Back", seconds: Int = 60) = Track(
        filePath = path,
        artist = artist,
        albumArtist = artist,
        album = album,
        title = path.substringAfterLast('/').substringBeforeLast('.'),
        durationMs = seconds * 1000L,
    )

    @Test fun a_new_store_has_Liked_and_saves_it() {
        val path = newPath()
        val store = PlaylistStore.load(path, monday)

        val liked = store.playlists.single()
        assertTrue(liked.isLiked)
        assertEquals("Liked", liked.name)
        assertSame(liked, store.liked)
        assertTrue(File(path).exists())
    }

    @Test fun liking_adds_to_the_end_and_a_round_trip_keeps_everything() {
        val path = newPath()
        val store = PlaylistStore.load(path, monday)

        assertEquals(1, store.add(store.liked, listOf(t("/music/a.flac")), tuesday))
        assertEquals(1, store.add(store.liked, listOf(t("/music/b.flac")), tuesday))
        val mix = store.create("Road trip", wednesday)
        store.add(mix, listOf(t("/music/b.flac"), t("/music/a.flac")), wednesday)

        val again = PlaylistStore.load(path, wednesday)

        assertEquals(listOf("/music/a.flac", "/music/b.flac"), again.liked.entries.map { it.filePath })
        assertEquals(tuesday, again.liked.modifiedUtc)
        val loaded = again.playlists.single { !it.isLiked }
        assertEquals("Road trip", loaded.name)
        assertEquals(mix.id, loaded.id)
        assertEquals(listOf("/music/b.flac", "/music/a.flac"), loaded.entries.map { it.filePath })
        assertEquals(PlaylistEntry("/music/b.flac", "Jingoro", "Back"), loaded.entries[0])
    }

    @Test fun the_file_is_the_desktops_format() {
        val path = newPath()
        val store = PlaylistStore.load(path, monday)
        store.add(store.liked, listOf(t("/music/a.flac")), tuesday)

        val text = File(path).readText()
        assertTrue("\"IsLiked\": true" in text, text)
        assertTrue("\"ModifiedUtc\": \"2026-10-06T12:00:00Z\"" in text, text)
        assertTrue("\"FilePath\": \"/music/a.flac\"" in text, text)
        assertTrue("\"Picture\": null" in text, text)
    }

    @Test fun a_file_the_desktop_wrote_is_read() {
        val path = newPath()
        File(path).writeText(
            """
            [
              {
                "Id": "7D3C1F0E-1111-2222-3333-444455556666",
                "Name": "Liked",
                "IsLiked": true,
                "ModifiedUtc": "2026-10-07T21:14:05.1234567Z",
                "Picture": null,
                "Entries": [ { "FilePath": "D:\\Music\\a.flac", "Artist": "Jingoro", "Album": "Back" } ]
              },
              { "Id": "8d3c1f0e-1111-2222-3333-444455556666", "Name": "Mix", "IsLiked": false,
                "ModifiedUtc": "2026-10-06T08:00:00Z", "Picture": "x.png", "Entries": [] }
            ]
            """.trimIndent(),
        )

        val store = PlaylistStore.load(path, monday)

        assertEquals(SafeFile.Load.Read, store.loadedHow)
        assertEquals(listOf("D:\\Music\\a.flac"), store.liked.entries.map { it.filePath })
        assertEquals(LocalDateTime.of(2026, 10, 7, 21, 14, 5, 123_000_000).toInstant(ZoneOffset.UTC).toEpochMilli(), store.liked.modifiedUtc)
        val mix = store.playlists.single { it.name == "Mix" }
        assertEquals("x.png", mix.picture)
        assertEquals("8d3c1f0e-1111-2222-3333-444455556666", mix.id)
    }

    @Test fun a_song_already_there_is_not_added_twice_and_the_date_stays() {
        val store = PlaylistStore.load(newPath(), monday)
        store.add(store.liked, listOf(t("/music/a.flac")), tuesday)

        assertEquals(0, store.add(store.liked, listOf(t("/MUSIC/A.FLAC")), wednesday))

        assertEquals(1, store.liked.entries.size)
        assertEquals(tuesday, store.liked.modifiedUtc)
    }

    @Test fun unliking_removes_the_song_and_stamps_the_date() {
        val store = PlaylistStore.load(newPath(), monday)
        store.add(store.liked, listOf(t("/music/a.flac"), t("/music/b.flac")), tuesday)

        assertEquals(1, store.remove(store.liked, listOf("/MUSIC/a.flac"), wednesday))

        assertEquals(listOf("/music/b.flac"), store.liked.entries.map { it.filePath })
        assertEquals(wednesday, store.liked.modifiedUtc)
        assertEquals(0, store.remove(store.liked, listOf("/music/zzz.flac"), wednesday + 24 * hour))
        assertEquals(wednesday, store.liked.modifiedUtc)
    }

    @Test fun playlists_are_listed_most_recently_modified_first() {
        val store = PlaylistStore.load(newPath(), monday)
        val older = store.create("Older", tuesday)
        val newer = store.create("Newer", wednesday)

        assertEquals(listOf(newer, older, store.liked), store.byRecent())

        store.add(store.liked, listOf(t("/music/a.flac")), wednesday + hour)
        assertEquals(listOf(store.liked, newer, older), store.byRecent())

        store.rename(older, "Oldest", wednesday + 2 * hour)
        assertEquals(older, store.byRecent()[0])
    }

    @Test fun names_must_be_given_and_unique_and_Liked_cannot_be_renamed_or_deleted() {
        val store = PlaylistStore.load(newPath(), monday)
        val mix = store.create("Mix", monday)

        assertNotNull(store.nameProblem("   "))
        assertNotNull(store.nameProblem("liked"))
        assertNotNull(store.nameProblem(" MIX "))
        assertNull(store.nameProblem("Mix", renaming = mix))
        assertNull(store.nameProblem("Other"))
        assertFailsWith<IllegalArgumentException> { store.create("mix", tuesday) }

        assertFailsWith<IllegalStateException> { store.rename(store.liked, "Loved", tuesday) }
        assertFalse(store.delete(store.liked))
        assertTrue(store.delete(mix))
        assertEquals(1, store.playlists.size)
    }

    @Test fun an_unreadable_file_is_moved_aside_rather_than_saved_over() {
        val path = newPath()
        File(path).writeText("{ not json")

        val store = PlaylistStore.load(path, monday)

        assertEquals(1, store.playlists.size)
        assertEquals(SafeFile.Load.Damaged, store.loadedHow)
        val aside = File(path).parentFile!!.listFiles()!!.filter { it.name.startsWith("playlists.json.unreadable-") }
        assertEquals("{ not json", aside.single().readText())
    }

    /** Two likes saved: the file holds both, its backup the first. */
    private fun twoLikes(): String {
        val path = newPath()
        val store = PlaylistStore.load(path, monday)
        store.add(store.liked, listOf(t("/music/a.flac")), tuesday)
        store.add(store.liked, listOf(t("/music/b.flac")), wednesday)
        return path
    }

    private fun likedIn(path: String): List<String> =
        PlaylistStore.load(path, wednesday).liked.entries.map { it.filePath.substringAfterLast('/') }

    @Test fun a_save_keeps_the_one_before_as_the_backup() {
        val path = twoLikes()

        assertEquals(listOf("a.flac", "b.flac"), likedIn(path))
        File(SafeFile.backupPathFor(path)).copyTo(File(path), overwrite = true)
        assertEquals(listOf("a.flac"), likedIn(path))
        assertFalse(File("$path.tmp").exists())
    }

    @Test fun a_damaged_file_is_read_from_its_backup_and_kept_aside() {
        val path = twoLikes()
        File(path).writeText("\u0000\u0000\u0000\u0000")

        val store = PlaylistStore.load(path, wednesday)

        assertEquals(SafeFile.Load.FromBackup, store.loadedHow)
        assertEquals(listOf("/music/a.flac"), store.liked.entries.map { it.filePath })
        // Written again from the backup, so the next start reads the file itself.
        assertEquals(SafeFile.Load.Read, PlaylistStore.load(path, wednesday).loadedHow)
        assertEquals(listOf("a.flac"), likedIn(path))
    }

    @Test fun a_missing_file_with_a_backup_is_read_from_the_backup() {
        val path = twoLikes()
        File(path).delete()

        val store = PlaylistStore.load(path, wednesday)

        assertEquals(SafeFile.Load.FromBackup, store.loadedHow)
        assertEquals(listOf("/music/a.flac"), store.liked.entries.map { it.filePath })
    }

    @Test fun a_first_run_is_missing_not_recovered() {
        assertEquals(SafeFile.Load.Missing, PlaylistStore.load(newPath(), monday).loadedHow)
    }

    @Test fun songs_are_found_by_path_in_playlist_order() {
        val a = t("/music/Jingoro/Back/01.flac", seconds = 100)
        val b = t("/music/Jingoro/Back/02.flac", seconds = 50)
        val store = PlaylistStore.load(newPath(), monday)
        store.add(store.liked, listOf(b, a), monday)

        val resolved = PlaylistResolver(listOf(a, b)).resolve(store.liked)

        assertEquals(listOf(b, a), resolved.tracks)
        assertEquals(0, resolved.missing)
        assertFalse(resolved.changed)
        assertEquals(150_000L, resolved.durationMs)
    }

    @Test fun after_the_music_moved_songs_are_found_and_re_pointed() {
        val store = PlaylistStore.load(newPath(), monday)
        store.add(store.liked, listOf(t("/storage/AAAA-1111/Music/Jingoro/Back/01.flac"), t("/storage/AAAA-1111/Music/Other/X/01.flac", "Other", "X")), monday)

        val moved = listOf(
            t("/storage/BBBB-2222/Music/Jingoro/Back/01.flac"),
            t("/storage/BBBB-2222/Music/Other/X/01.flac", "Other", "X"),
        )
        val resolved = PlaylistResolver(moved).resolve(store.liked)

        assertEquals(moved, resolved.tracks)
        assertTrue(resolved.changed)
        assertEquals(
            listOf("/storage/BBBB-2222/Music/Jingoro/Back/01.flac", "/storage/BBBB-2222/Music/Other/X/01.flac"),
            store.liked.entries.map { it.filePath },
        )
        assertEquals(monday, store.liked.modifiedUtc)
    }

    @Test fun the_same_file_name_on_another_album_is_not_taken_for_the_song() {
        val store = PlaylistStore.load(newPath(), monday)
        store.add(store.liked, listOf(t("/a/Music/Jingoro/Back/01.flac")), monday)

        val resolved = PlaylistResolver(listOf(t("/b/Music/Jingoro/Forward/01.flac", album = "Forward"))).resolve(store.liked)

        assertTrue(resolved.tracks.isEmpty())
        assertEquals(1, resolved.missing)
    }

    @Test fun a_song_not_in_the_library_stays_in_the_playlist() {
        val path = newPath()
        val store = PlaylistStore.load(path, monday)
        val kept = t("/music/a.flac")
        val gone = t("/music/gone.flac")
        store.add(store.liked, listOf(kept, gone), monday)

        val resolved = PlaylistResolver(listOf(kept)).resolve(store.liked)
        store.save()

        assertEquals(listOf(kept), resolved.tracks)
        assertEquals(1, resolved.missing)
        assertEquals(2, PlaylistStore.load(path, tuesday).liked.entries.size)
    }

    @Test fun a_retagged_song_is_still_found_and_its_entry_takes_the_new_names() {
        val store = PlaylistStore.load(newPath(), monday)
        store.add(store.liked, listOf(t("/music/a.flac", album = "Old title")), monday)

        val retagged = t("/music/a.flac", album = "New title")
        val resolved = PlaylistResolver(listOf(retagged)).resolve(store.liked)

        assertEquals(listOf(retagged), resolved.tracks)
        assertTrue(resolved.changed)
        assertEquals("New title", store.liked.entries[0].album)
    }

    /** A playlist of a.flac, b.flac, … in that order, made on Monday. */
    private fun letters(letters: String): Triple<PlaylistStore, Playlist, String> {
        val path = newPath()
        val store = PlaylistStore.load(path, monday)
        val mix = store.create("Mix", monday)
        store.add(mix, letters.map { t("/music/$it.flac") }, monday)
        return Triple(store, mix, path)
    }

    private fun order(playlist: Playlist): String =
        playlist.entries.joinToString("") { it.filePath.substringAfterLast('/').substringBefore('.') }

    private fun p(c: Char): String = "/music/$c.flac"

    @Test fun songs_move_as_a_block_to_just_before_the_target() {
        // start, moving, before, expected (the desktop's theory rows)
        val cases = listOf(
            listOf("abcde", "d", "b", "adbce"), // up
            listOf("abcde", "b", "e", "acdbe"), // down, before e
            listOf("abcde", "b", null, "acdeb"), // to the end
            listOf("abcde", "a", "a", "abcde"), // onto itself
            listOf("abcde", "eb", "a", "beacd"), // a block keeps playlist order, not selection order
            listOf("abcde", "bd", "c", "abdce"), // dropped on one of its own: before the next one left
            listOf("abcde", "bd", "d", "acbde"),
            listOf("abcde", "ae", "c", "baecd"),
        )
        for ((start, moving, before, expected) in cases) {
            val (store, mix, path) = letters(start!!)
            val moved = store.move(mix, moving!!.map(::p), before?.let { p(it[0]) }, tuesday)

            assertEquals(expected, order(mix), "$start $moving $before")
            assertEquals(expected != start, moved)
            assertEquals(expected, order(PlaylistStore.load(path, wednesday).playlists.single { it.name == "Mix" }))
        }
    }

    @Test fun a_move_stamps_the_date_and_one_that_changes_nothing_does_not() {
        val (store, mix, _) = letters("abc")

        assertFalse(store.move(mix, listOf(p('b')), p('c'), tuesday))
        assertFalse(store.move(mix, listOf("/music/zzz.flac"), p('a'), tuesday))
        assertEquals(monday, mix.modifiedUtc)

        assertTrue(store.move(mix, listOf("/MUSIC/C.FLAC"), p('a'), wednesday))
        assertEquals("cab", order(mix))
        assertEquals(wednesday, mix.modifiedUtc)
    }

    @Test fun songs_not_found_keep_their_place_among_the_rest() {
        // m isn't in the library, so the list shows a, b, c, d.
        val (store, mix, _) = letters("abmcd")

        store.move(mix, listOf(p('d')), p('c'), tuesday)
        assertEquals("abmdc", order(mix))

        store.move(mix, listOf(p('a')), null, tuesday)
        assertEquals("bmdca", order(mix))
        assertEquals(5, mix.entries.size)
    }

    @Test fun header_text() {
        assertEquals("1 track", PlaylistText.trackCount(1))
        assertEquals("1,204 tracks", PlaylistText.trackCount(1204))
        assertEquals("12 tracks", PlaylistText.headerTrackCount(12, 0))
        assertEquals("12 tracks · 2 not found", PlaylistText.headerTrackCount(12, 2))

        val zone = ZoneId.of("America/Los_Angeles")
        val lateEvening = LocalDateTime.of(2026, 10, 7, 23, 30).atZone(zone).toInstant().toEpochMilli()
        assertEquals("Modified 2026-10-07", PlaylistText.modified(lateEvening, zone))

        assertEquals("No liked songs yet", PlaylistText.empty(isLiked = true, missing = 0).first)
        assertEquals("This playlist is empty", PlaylistText.empty(isLiked = false, missing = 0).first)
        assertTrue("3 songs" in PlaylistText.empty(isLiked = false, missing = 3).second)
    }
}
