package com.mpaulrenlund.audiofool.core.scrobbling

import com.mpaulrenlund.audiofool.core.model.Track
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.net.URI
import java.net.URLDecoder
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

// Ported from the desktop's tests/AudioFool.Core.Tests/ListeningHistoryTests.cs (session 74),
// plus the phone's short period words.

class ListeningHistoryTest {
    private fun json(text: String) = LastFmApi.parse(text)

    // ------------------------------------------------------------ parsing

    @Test
    fun `Profile reads numbers sent as strings and the join date`() {
        val p = ListeningJson.profile(
            json(
                """
                {"user":{"name":"marcus","playcount":"48210","artist_count":"1734","album_count":"4120",
                         "track_count":"21007","registered":{"unixtime":"1394668800","#text":1394668800}}}
                """,
            ),
        )

        assertEquals("marcus", p.userName)
        assertEquals(48210, p.scrobbles)
        assertEquals(1734, p.artists)
        assertEquals(4120, p.albums)
        assertEquals(21007, p.tracks)
        assertEquals(Instant.ofEpochSecond(1394668800), p.registered)
    }

    @Test
    fun `Profile without the newer counts leaves them null`() {
        val p = ListeningJson.profile(json("""{"user":{"name":"rj","playcount":12,"registered":{"#text":1037793040}}}"""))

        assertEquals(12, p.scrobbles)
        assertNull(p.artists)
        assertNull(p.albums)
        assertNull(p.tracks)
        assertEquals(Instant.ofEpochSecond(1037793040), p.registered)
    }

    @Test
    fun `Profile missing is a last fm error`() {
        assertFailsWith<LastFmException> { ListeningJson.profile(json("{}")) }
    }

    @Test
    fun `Top albums read name artist and plays`() {
        val top = ListeningJson.top(
            json(
                """
                {"topalbums":{"album":[
                    {"name":"Moving Pictures","playcount":"312","artist":{"name":"Rush","mbid":""},"@attr":{"rank":"1"}},
                    {"name":"Lateralus","playcount":"98","artist":{"name":"Tool"},"@attr":{"rank":"2"}}],
                  "@attr":{"user":"marcus","total":"2"}}}
                """,
            ),
            "topalbums", "album", 10,
        )

        assertEquals(listOf(TopEntry("Moving Pictures", "Rush", 312), TopEntry("Lateralus", "Tool", 98)), top)
    }

    @Test
    fun `Top artists have no artist and a list of one is an object`() {
        val top = ListeningJson.top(json("""{"topartists":{"artist":{"name":"Rush","playcount":"7"}}}"""), "topartists", "artist", 10)

        assertEquals(listOf(TopEntry("Rush", null, 7)), top)
    }

    @Test
    fun `Top is cut to the limit and an empty list is empty`() {
        val many = (1..12).joinToString(",") { i -> """{"name":"Song $i","playcount":"${20 - i}","artist":{"name":"A"}}""" }
        assertEquals(10, ListeningJson.top(json("""{"toptracks":{"track":[$many]}}"""), "toptracks", "track", 10).size)

        assertTrue(ListeningJson.top(json("""{"toptracks":{"track":[],"@attr":{"total":"0"}}}"""), "toptracks", "track", 10).isEmpty())
        assertTrue(ListeningJson.top(json("""{"toptracks":""}"""), "toptracks", "track", 10).isEmpty())
    }

    @Test
    fun `Recent puts the playing song first without a time and keeps to the limit`() {
        val recent = ListeningJson.recent(
            json(
                """
                {"recenttracks":{"track":[
                    {"artist":{"#text":"Rush"},"name":"Limelight","album":{"#text":"Moving Pictures"},"@attr":{"nowplaying":"true"}},
                    {"artist":{"#text":"Rush"},"name":"Red Barchetta","album":{"#text":"Moving Pictures"},"date":{"uts":"1793000000","#text":"x"}},
                    {"artist":{"#text":"Tool"},"name":"Schism","album":{"#text":""},"date":{"uts":"1792990000"}}]}}
                """,
            ),
            2,
        )

        assertEquals(2, recent.size)
        assertEquals(RecentPlay("Limelight", "Rush", "Moving Pictures", null, true), recent[0])
        assertEquals(RecentPlay("Red Barchetta", "Rush", "Moving Pictures", Instant.ofEpochSecond(1793000000), false), recent[1])
    }

    @Test
    fun `Recent reads the extended artist shape too`() {
        val recent = ListeningJson.recent(
            json("""{"recenttracks":{"track":{"artist":{"name":"Rush","url":"x"},"name":"YYZ","album":{"#text":"Moving Pictures"},"date":{"uts":"1"}}}}"""),
            20,
        )

        assertEquals("Rush", recent.single().artist)
    }

    // ------------------------------------------------------------- times

    private val zone = ZoneOffset.ofHours(-5)
    private val now = OffsetDateTime.of(2026, 11, 2, 15, 0, 0, 0, zone).toInstant()

    private fun ago(before: Duration) = HistoryTime.ago(RecentPlay("t", "a", "b", now - before, false), now, zone)

    @Test
    fun `Times read as minutes hours yesterday then a date`() {
        assertEquals("Now playing", HistoryTime.ago(RecentPlay("t", "a", "b", null, true), now, zone))
        assertEquals("Just now", ago(Duration.ofSeconds(40)))
        assertEquals("12 min ago", ago(Duration.ofMinutes(12)))
        assertEquals("1 hour ago", ago(Duration.ofMinutes(75)))
        assertEquals("14 hours ago", ago(Duration.ofMinutes(14 * 60 + 30)))
        assertEquals("Yesterday", ago(Duration.ofHours(16)))
        assertEquals("Oct 30", ago(Duration.ofDays(3)))
        assertEquals("Dec 30, 2025", ago(Duration.ofDays(307)))
    }

    @Test
    fun `The join month is short month and year`() {
        assertEquals("Mar 2014", HistoryTime.month(OffsetDateTime.of(2014, 3, 13, 12, 0, 0, 0, ZoneOffset.UTC).toInstant(), zone))
    }

    @Test
    fun `Periods have last fm names and labels`() {
        assertEquals(listOf("7day", "1month", "3month", "6month", "12month", "overall"), ListeningPeriod.entries.map { it.apiName })
        assertEquals(listOf("7 Days", "1 Month", "3 Months", "6 Months", "12 Months", "All Time"), ListeningPeriod.entries.map { it.label })
    }

    @Test
    fun `The phone's period words are short`() {
        assertEquals(listOf("7D", "1M", "3M", "6M", "12M", "All"), ListeningPeriod.entries.map { it.shortLabel })
    }

    // ----------------------------------------------------------- matching

    private fun t(title: String, artist: String, album: String, albumArtist: String = "", path: String? = null) = Track(
        filePath = path ?: "/sdcard/Music/$artist/$album/$title.flac",
        title = title,
        artist = artist,
        albumArtist = albumArtist,
        album = album,
    )

    @Test
    fun `Names match without case accents punctuation or ampersands`() {
        val song = t("Déjà Vu", "Crosby, Stills, Nash & Young", "Déjà Vu")
        val match = ListeningMatch(listOf(song))

        assertSame(song, match.artist("crosby stills nash and young"))
        assertSame(song, match.album("Crosby, Stills, Nash and Young", "Deja vu"))
        assertSame(song, match.song("CROSBY STILLS NASH & YOUNG", "deja-vu"))
    }

    @Test
    fun `A trailing bracketed edition is tried without`() {
        val song = t("Tom Sawyer (2011 Remaster)", "Rush", "Moving Pictures [Deluxe Edition]")
        val match = ListeningMatch(listOf(song))

        assertSame(song, match.album("Rush", "Moving Pictures"))
        assertSame(song, match.song("Rush", "Tom Sawyer"))
        assertSame(song, match.album("Rush", "Moving Pictures (Remastered)"))
    }

    @Test
    fun `An artist goes to their own album before a compilation they are on`() {
        val guest = t("Closer to the Heart", "Rush", "Now That's What I Call Prog", "Various Artists", "/sdcard/Music/VA/01.flac")
        val own = t("Limelight", "Rush", "Moving Pictures", "Rush", "/sdcard/Music/Rush/02.flac")
        val match = ListeningMatch(listOf(guest, own))

        assertSame(own, match.artist("Rush"))
        assertSame(guest, match.album("Rush", "Now That's What I Call Prog"))
        assertSame(guest, match.album("Various Artists", "Now That's What I Call Prog"))
    }

    @Test
    fun `Nothing is matched on a different artist or an empty name`() {
        val match = ListeningMatch(listOf(t("Schism", "Tool", "Lateralus")))

        assertNull(match.song("Rush", "Schism"))
        assertNull(match.album("Tool", "Aenima"))
        assertNull(match.artist(""))
        assertNull(match.song("", "Schism"))
        assertNull(ListeningMatch.Empty.artist("Tool"))
    }

    // ---------------------------------------------------------------- API

    private class RecordingTransport(private val json: String) : LastFmTransport {
        val gets = ArrayList<String>()
        var posts = 0

        override fun post(url: String, form: String): HttpAnswer {
            posts++
            return HttpAnswer(200, json)
        }

        override fun get(url: String): HttpAnswer {
            gets.add(url)
            return HttpAnswer(200, json)
        }
    }

    @Test
    fun `Reads are unsigned gets with the user and period`() = runBlocking {
        val transport = RecordingTransport("""{"topartists":{"artist":[{"name":"Rush","playcount":"3"}]}}""")
        val api = LastFmApi("key1", "", transport)

        val top = api.getTopArtists("marc us", ListeningPeriod.ThreeMonths, 10)

        assertEquals("Rush", top.single().name)
        assertEquals(0, transport.posts)
        val uri = URI(transport.gets.single())
        assertEquals("https://ws.audioscrobbler.com/2.0/", uri.scheme + "://" + uri.host + uri.path)
        val query = uri.rawQuery.split('&').associate {
            val (name, value) = it.split('=', limit = 2)
            name to URLDecoder.decode(value, Charsets.UTF_8)
        }
        assertEquals("user.getTopArtists", query["method"])
        assertEquals("marc us", query["user"])
        assertTrue("%20" in uri.rawQuery, "a space is sent as %20, as the desktop's Uri.EscapeDataString does")
        assertEquals("3month", query["period"])
        assertEquals("10", query["limit"])
        assertEquals("key1", query["api_key"])
        assertEquals("json", query["format"])
        assertFalse("api_sig" in query)
        assertFalse("sk" in query)
    }

    @Test
    fun `A read error from last fm is its own code`() = runBlocking {
        val api = LastFmApi("key1", "", RecordingTransport("""{"error":6,"message":"User not found"}"""))

        val e = assertFailsWith<LastFmException> { api.getProfile("nobody") }
        assertEquals(6, e.code)
        assertEquals("User not found", e.message)
    }
}
