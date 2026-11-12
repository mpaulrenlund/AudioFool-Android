package com.mpaulrenlund.audiofool.playlists

import com.mpaulrenlund.audiofool.core.library.LibraryScanner
import com.mpaulrenlund.audiofool.core.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The heart and the Playlists tab's model (2026-11-12): liking is kept on disk, and rows and songs come from the library. */
class PlaylistsTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val now = 1_794_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun t(album: String, n: Int, added: Long? = null) = Track(
        filePath = "/music/Loam/$album/$n.flac", artist = "Loam", albumArtist = "Loam", album = album,
        title = "$album $n", trackNumber = n, durationMs = 60_000, addedUtc = added,
    )

    private val old1 = t("Old", 1, now - 200 * day)
    private val old2 = t("Old", 2, now - 200 * day)
    private val new1 = t("New", 1, now - 2 * day)
    private val library = LibraryScanner.build(listOf(old1, old2, new1))

    private fun path() = File(Files.createTempDirectory("AudioFoolTests").toFile(), "playlists.json").path

    @Test fun a_like_shows_at_once_is_saved_and_survives_a_restart() = runBlocking {
        val path = path()
        val playlists = Playlists(path, scope) { now }
        playlists.toggleLike(old2)
        assertTrue(playlists.isLiked(old2), "the heart fills at once")
        playlists.toggleLike(new1)

        // Read on the same thread as the writes, so after them.
        val liked = playlists.shown(playlists.rows(library).first().id, library)!!
        assertEquals(listOf(old2, new1), liked.tracks)
        assertEquals("2 tracks", liked.trackCount)

        val again = Playlists(path, scope) { now }
        assertEquals(listOf("Liked", "Recently Added"), again.rows(library).map { it.name })
        assertTrue(again.isLiked(old2))
        assertFalse(again.isLiked(old1))
    }

    @Test fun a_second_tap_unlikes() = runBlocking {
        val playlists = Playlists(path(), scope) { now }
        playlists.toggleLike(old1)
        playlists.toggleLike(old1)
        assertFalse(playlists.isLiked(old1))
        val liked = playlists.shown(playlists.rows(library).first().id, library)!!
        assertTrue(liked.tracks.isEmpty())
        assertEquals("No liked songs yet", liked.empty?.first)
    }

    @Test fun recently_added_holds_the_last_30_days_by_track_number() = runBlocking {
        val playlists = Playlists(path(), scope) { now }
        val rows = playlists.rows(library)
        assertEquals("1 track", rows[1].trackCount)
        val recent = playlists.shown(Playlists.RECENT_ID, library)!!
        assertEquals(listOf(new1), recent.tracks)
        assertEquals("Last 30 days", recent.line)
        assertFalse(recent.numbered)
    }
}
