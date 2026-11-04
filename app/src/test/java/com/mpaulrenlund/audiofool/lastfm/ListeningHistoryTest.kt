package com.mpaulrenlund.audiofool.lastfm

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mpaulrenlund.audiofool.core.library.LibraryScanner
import com.mpaulrenlund.audiofool.core.model.Track
import com.mpaulrenlund.audiofool.core.scrobbling.HttpAnswer
import com.mpaulrenlund.audiofool.core.scrobbling.LastFmApi
import com.mpaulrenlund.audiofool.core.scrobbling.LastFmTransport
import com.mpaulrenlund.audiofool.core.scrobbling.ListenerProfile
import com.mpaulrenlund.audiofool.core.scrobbling.ListeningMatch
import com.mpaulrenlund.audiofool.core.scrobbling.ListeningPeriod
import com.mpaulrenlund.audiofool.core.scrobbling.RecentPlay
import com.mpaulrenlund.audiofool.core.scrobbling.TopEntry
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.net.URI
import java.net.URLDecoder
import java.time.Instant
import java.time.ZoneOffset
import java.util.Collections
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

// Listening History on the phone (spec 4.7): Last.fm's answers as rows (the desktop's
// ListeningHistoryViewModel, ported), where a row leads, and the model's fetching.

@RunWith(AndroidJUnit4::class)
class ListeningHistoryTest {
    private fun track(title: String, artist: String, album: String, albumArtist: String = artist) = Track(
        filePath = "/sdcard/Music/$albumArtist/$album/$title.flac", title = title, artist = artist, albumArtist = albumArtist, album = album,
    )

    private val limelight = track("Limelight", "Rush", "Moving Pictures")
    private val yyz = track("YYZ", "Rush", "Moving Pictures")
    private val guest = track("Closer to the Heart", "Rush", "Prog Hits", albumArtist = "Various Artists")
    private val library = LibraryScanner.build(listOf(limelight, yyz, guest))
    private val now = Instant.parse("2026-11-04T15:00:00Z")

    // ------------------------------------------------------------ rows

    @Test
    fun `tiles name the totals and drop a count Last fm leaves out`() {
        val tiles = HistoryRows.tiles(ListenerProfile("q", 12263, Instant.parse("2025-12-03T10:00:00Z"), 618, null, 3938), ZoneOffset.UTC)

        assertEquals(listOf("12,263" to "Scrobbles", "Dec 2025" to "Scrobbling since", "618" to "Artists", "3,938" to "Tracks"), tiles)
    }

    @Test
    fun `top rows are measured against the first and say their plays`() {
        val match = ListeningMatch(library.allTracks)
        val rows = HistoryRows.top(listOf(TopEntry("Moving Pictures", "Rush", 866), TopEntry("Lateralus", "Tool", 1))) {
            HistoryKind.Album to match.album(it.artist ?: "", it.name)
        }

        assertEquals(listOf("866 plays", "1 play"), rows.map { it.value })
        assertEquals(1.0, rows[0].fill)
        assertEquals(1.0 / 866, rows[1].fill)
        assertEquals("Rush", rows[0].detail)
        assertTrue(rows[0].target in listOf(limelight, yyz))
        assertNull(rows[1].target, "nothing in the library: the row can't be tapped")
    }

    @Test
    fun `recent rows say when and where from, without a bar`() {
        val match = ListeningMatch(library.allTracks)
        val playing = HistoryRows.recent(RecentPlay("YYZ", "Rush", "Moving Pictures", null, true), match, now, ZoneOffset.UTC)
        val noAlbum = HistoryRows.recent(RecentPlay("Schism", "Tool", "", now.minusSeconds(7200), false), match, now, ZoneOffset.UTC)

        assertEquals("Now playing", playing.value)
        assertEquals("Rush · Moving Pictures", playing.detail)
        assertNull(playing.fill)
        assertSame(yyz, playing.target)
        assertEquals("2 hours ago", noAlbum.value)
        assertEquals("Tool", noAlbum.detail)
        assertNull(noAlbum.target)
    }

    @Test
    fun `a period with nothing in it says so as the desktop does`() {
        assertEquals("Nothing played in the last 7 days.", HistoryRows.emptyText(ListeningPeriod.Week))
        assertEquals("Nothing played in the last month.", HistoryRows.emptyText(ListeningPeriod.Month))
        assertEquals("Nothing played in the last 12 months.", HistoryRows.emptyText(ListeningPeriod.Year))
        assertEquals("Nothing played yet.", HistoryRows.emptyText(ListeningPeriod.AllTime))
    }

    @Test
    fun `a row leads to its artist's albums, its album, or its album with the song chosen`() {
        val artist = HistoryRow("Rush", "3 plays", null, 1.0, HistoryKind.Artist, limelight)
        val album = HistoryRow("Moving Pictures", "3 plays", "Rush", 1.0, HistoryKind.Album, yyz)
        val song = HistoryRow("YYZ", "Now playing", "Rush · Moving Pictures", null, HistoryKind.Song, yyz)
        val onCompilation = HistoryRow("Closer to the Heart", "1 play", "Rush", 1.0, HistoryKind.Song, guest)

        assertEquals(HistoryDestination("Rush", null, null), historyTarget(artist, library))
        assertEquals(HistoryDestination("Rush", "Moving Pictures", null), historyTarget(album, library))
        assertEquals(HistoryDestination("Rush", "Moving Pictures", yyz.filePath), historyTarget(song, library))
        assertEquals(HistoryDestination("Various Artists", "Prog Hits", guest.filePath), historyTarget(onCompilation, library))
        assertNull(historyTarget(HistoryRow("Schism", "1 play", "Tool", 1.0, HistoryKind.Song, null), library))
        assertNull(historyTarget(song, null))
    }

    // ------------------------------------------------------------ fetching

    /** Answers each read by its method, and counts them. */
    private class FakeLastFm(private val failing: Boolean = false) : LastFmTransport {
        val methods: MutableList<String> = Collections.synchronizedList(ArrayList())

        override fun post(url: String, form: String) = HttpAnswer(500, "")

        override fun get(url: String): HttpAnswer {
            val query = URI(url).rawQuery.split('&').associate {
                val (name, value) = it.split('=', limit = 2)
                name to URLDecoder.decode(value, Charsets.UTF_8)
            }
            val method = query.getValue("method")
            methods.add(if ("period" in query) "$method ${query["period"]}" else method)
            if (failing) return HttpAnswer(404, """{"error":6,"message":"User not found"}""")
            return HttpAnswer(200, when (method) {
                "user.getInfo" -> """{"user":{"name":"q","playcount":"5","registered":{"unixtime":"1764756000"}}}"""
                "user.getTopArtists" -> """{"topartists":{"artist":{"name":"Rush","playcount":"${if (query["period"] == "7day") 2 else 5}"}}}"""
                "user.getTopAlbums" -> """{"topalbums":{"album":{"name":"Moving Pictures","playcount":"5","artist":{"name":"Rush"}}}}"""
                "user.getTopTracks" -> """{"toptracks":{"track":[]}}"""
                else -> """{"recenttracks":{"track":{"name":"YYZ","artist":{"#text":"Rush"},"album":{"#text":"Moving Pictures"},"@attr":{"nowplaying":"true"}}}}"""
            })
        }
    }

    private fun history(fake: FakeLastFm?) = ListeningHistory(
        reader = { fake?.let { HistoryReader(LastFmApi("key", "", it), "q") } },
        library = { library },
        clock = { now },
        zone = { ZoneOffset.UTC },
    )

    /** Runs the main thread until [done], as the reads answer on other threads. */
    private fun waitFor(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!done()) {
            check(System.currentTimeMillis() < deadline) { "timed out" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
    }

    @Test
    fun `it loads the totals, 12 months and the recent plays, then each period once`() {
        val fake = FakeLastFm()
        val h = history(fake)
        h.open()
        waitFor { h.state.phase == HistoryState.Phase.Loaded }

        assertEquals("q", h.state.user)
        assertEquals(ListeningPeriod.Year, h.state.period)
        assertEquals(listOf("5" to "Scrobbles", "Dec 2025" to "Scrobbling since"), h.state.tiles)
        assertEquals(listOf("Rush"), h.state.topArtists.map { it.label })
        assertSame(limelight, h.state.topArtists.single().target)
        assertTrue(h.state.topTracks.isEmpty())
        assertEquals("Nothing played in the last 12 months.", h.state.topEmptyText)
        assertEquals(listOf("Now playing"), h.state.recent.map { it.value })
        assertEquals(
            listOf("user.getInfo", "user.getRecentTracks", "user.getTopAlbums 12month", "user.getTopArtists 12month", "user.getTopTracks 12month"),
            fake.methods.sorted(),
        )

        h.choose(ListeningPeriod.Week)
        assertEquals("Loading…", h.state.topEmptyText)
        waitFor { h.state.topArtists.isNotEmpty() }
        assertEquals("2 plays", h.state.topArtists.single().value)

        fake.methods.clear()
        h.choose(ListeningPeriod.Year)
        assertEquals("5 plays", h.state.topArtists.single().value, "already fetched: shown at once")
        h.choose(ListeningPeriod.Week)
        assertEquals("2 plays", h.state.topArtists.single().value)
        assertTrue(fake.methods.isEmpty(), "nothing asked again within one opening")

        // Opened again from a menu: asked afresh.
        h.open()
        waitFor { h.state.phase == HistoryState.Phase.Loaded }
        assertEquals(ListeningPeriod.Year, h.state.period)
        assertEquals(5, fake.methods.size)
    }

    @Test
    fun `not connected, it says so without asking Last fm`() {
        val h = history(null)
        h.showing()
        waitFor { h.state.phase != HistoryState.Phase.Loading }

        assertEquals(HistoryState.Phase.NotConnected, h.state.phase)
        assertNull(h.state.user)
    }

    @Test
    fun `a failed read says why, and showing again doesn't ask again`() {
        val fake = FakeLastFm(failing = true)
        val h = history(fake)
        h.showing()
        waitFor { h.state.phase == HistoryState.Phase.Failed }

        assertEquals("User not found.", h.state.error)
        val asked = fake.methods.size
        h.showing()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(asked, fake.methods.size)
    }
}
