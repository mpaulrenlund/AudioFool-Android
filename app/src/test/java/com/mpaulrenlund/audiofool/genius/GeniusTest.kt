package com.mpaulrenlund.audiofool.genius

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mpaulrenlund.audiofool.core.genius.GeniusClient
import com.mpaulrenlund.audiofool.core.genius.GeniusComment
import com.mpaulrenlund.audiofool.core.genius.GeniusException
import com.mpaulrenlund.audiofool.core.genius.GeniusHit
import com.mpaulrenlund.audiofool.core.genius.GeniusLookup
import com.mpaulrenlund.audiofool.core.genius.GeniusTransport
import com.mpaulrenlund.audiofool.core.model.Track
import com.mpaulrenlund.audiofool.core.scrobbling.HttpAnswer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.IOException
import java.net.URI
import java.time.Instant
import java.time.ZoneOffset
import java.util.Collections
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The Genius screen on the phone (spec 5.2): the desktop's GeniusViewModel wording,
// its LinkedText, and the model's lookups against a made-up genius.com.

@RunWith(AndroidJUnit4::class)
class GeniusTest {
    // ------------------------------------------------------------ wording

    @Test
    fun `a comment says its date and votes as the desktop does`() {
        val zone = ZoneOffset.UTC
        fun figure(votes: Int, at: Instant? = Instant.parse("2017-03-07T12:00:00Z")) =
            GeniusRows.comment(GeniusComment("a", at, votes, "x"), zone).figure

        assertEquals("Mar 7, 2017 · 297 votes", figure(297))
        assertEquals("Mar 7, 2017 · 1 vote", figure(1))
        assertEquals("Mar 7, 2017 · -1 vote", figure(-1))
        assertEquals("Mar 7, 2017 · 1,234 votes", figure(1234))
        assertEquals("-6 votes", figure(-6, at = null))
    }

    @Test
    fun `a connection names five songs, then how many more`() {
        val songs = (1..7).map { GeniusHit(it.toLong(), "Song $it", if (it == 2) "" else "Band $it", "") }

        assertEquals("Song 1 · Band 1\nSong 2\nSong 3 · Band 3\nSong 4 · Band 4\nSong 5 · Band 5\nand 2 more", GeniusRows.connectionText(songs))
        assertEquals("Song 1 · Band 1", GeniusRows.connectionText(songs.take(1)))
    }

    @Test
    fun `show more counts what is left, up to a page`() {
        assertEquals("Show 50 more", GeniusRows.moreText(2, 257, 50))
        assertEquals("Show 7 more", GeniusRows.moreText(2, 57, 50))
        assertEquals("Show 50 more", GeniusRows.moreText(2, 0, 50))
        assertNull(GeniusRows.moreText(null, 57, 57))
        assertEquals("1 comment", GeniusRows.commentCount(1))
        assertEquals("1,257 comments", GeniusRows.commentCount(1257))
    }

    @Test
    fun `missing parts are named in a sentence`() {
        assertEquals("comments", GeniusRows.joinNames(listOf("comments")))
        assertEquals("annotations or the comments", GeniusRows.joinNames(listOf("annotations", "comments")))
        assertEquals(
            "questions and answers, the annotations or the comments",
            GeniusRows.joinNames(listOf("questions and answers", "annotations", "comments")),
        )
    }

    @Test
    fun `a failure says why and offers another try`() {
        val note = GeniusRows.failed(GeniusException("Genius answered HTTP 403 without a page AudioFool can read."))

        assertEquals("Couldn't load the Genius page.", note.text)
        assertEquals("Genius answered HTTP 403 without a page AudioFool can read. Tap to try again.", note.detail)
        assertTrue(note.retry)
    }

    @Test
    fun `the header line is the album and year from the tags`() {
        assertEquals("Second Sight · 1997", GeniusRows.detailLine(Track(filePath = "/a.flac", album = "Second Sight", year = 1997)))
        assertEquals("1997", GeniusRows.detailLine(Track(filePath = "/a.flac", year = 1997)))
        assertNull(GeniusRows.detailLine(Track(filePath = "/a.flac")))
    }

    // ------------------------------------------------------------ links

    @Test
    fun `web addresses become links and their trailing stop stays text`() {
        assertEquals(
            listOf("See " to false, "https://example.com/a?b=1" to true, ". Or " to false, "http://x.org/y" to true, ")" to false),
            LinkedText.split("See https://example.com/a?b=1. Or http://x.org/y)"),
        )
        assertEquals(listOf("https://youtu.be/abc" to true), LinkedText.split("https://youtu.be/abc"))
        assertEquals(listOf("no link here\nnor here" to false), LinkedText.split("no link here\nnor here"))
        assertEquals(listOf("just https:// alone" to false), LinkedText.split("just https:// alone"))
    }

    // ------------------------------------------------------------ the model

    private fun ok(response: String) = """{"meta":{"status":200},"response":$response}"""

    private fun search(vararg hits: Triple<Long, String, String>) = ok(
        """{"sections":[{"type":"song","hits":[""" +
            hits.joinToString(",") { (id, artist, title) ->
                """{"type":"song","result":{"_type":"song","id":$id,"title":"$title","primary_artist":{"name":"$artist"},"url":"https://genius.com/$id"}}"""
            } + "]}]}",
    )

    private val song = ok(
        """{"song":{"id":42,"title":"Song","url":"https://genius.com/42","primary_artist":{"name":"Band"},
            "description":{"plain":"About it."},"writer_artists":[{"name":"Writer"}]}}""",
    )

    private fun comments(page: Int) = ok(
        """{"comments":[{"author":{"login":"p$page"},"votes_total":3,"body":{"plain":"Comment on page $page."}}],
            "total_count":3,"next_page":${if (page < 3) page + 1 else "null"}}""",
    )

    /** genius.com made up, answering each path with [answer]; [offline] makes it unreachable. */
    private class FakeGenius(var offline: Boolean = false, val answer: (String) -> String) : GeniusTransport {
        val asked: MutableList<String> = Collections.synchronizedList(ArrayList())

        override fun get(url: String): HttpAnswer {
            val uri = URI(url)
            val path = uri.rawPath + (uri.rawQuery?.let { "?$it" } ?: "")
            asked.add(path)
            if (offline) throw IOException("Unable to resolve host \"genius.com\"")
            return HttpAnswer(200, answer(path))
        }
    }

    private fun genius(searchFor: (String) -> String): Pair<Genius, FakeGenius> {
        val fake = FakeGenius { path ->
            when {
                path.startsWith("/api/search") -> searchFor(path)
                path.startsWith("/api/songs/42/comments") -> comments(Regex("[?&]page=(\\d+)").find(path)!!.groupValues[1].toInt())
                path.startsWith("/api/songs/42") -> song
                path.startsWith("/api/questions") -> ok("""{"questions":[]}""")
                else -> ok("""{"referents":[]}""")
            }
        }
        return Genius(GeniusLookup(GeniusClient(fake)), zone = { ZoneOffset.UTC }) to fake
    }

    private val track = Track(filePath = "/sdcard/Music/Band/Album/Song (Remastered).flac", title = "Song (Remastered)", artist = "Band", album = "Album")

    /** Runs the main thread until [done], as the requests answer on other threads. */
    private fun waitFor(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!done()) {
            check(System.currentTimeMillis() < deadline) { "timed out" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
    }

    @Test
    fun `it finds the song's page and shows it under the library's song`() {
        val (g, _) = genius { search(Triple(42, "Band", "Song")) }

        g.show(track)
        assertEquals(GeniusRows.LOOKING_UP, g.state.note?.text)
        waitFor { g.state.pageUrl != null }

        assertEquals(track, g.state.track)
        assertNull(g.state.note)
        assertEquals("About it.", g.state.about)
        assertEquals(listOf(GeniusCreditRow("Written by", "Writer")), g.state.credits)
        assertEquals(listOf("p1"), g.state.comments.map { it.heading })
        assertEquals("3 comments", g.state.commentCount)
        assertEquals("Show 2 more", g.state.moreComments)
    }

    @Test
    fun `show more adds the next page of comments under the ones shown`() {
        val (g, _) = genius { search(Triple(42, "Band", "Song")) }
        g.show(track)
        waitFor { g.state.pageUrl != null }

        g.showMoreComments()
        assertEquals(GeniusRows.LOADING_MORE, g.state.moreComments)
        waitFor { g.state.comments.size == 2 }
        assertEquals("Show 1 more", g.state.moreComments)

        g.showMoreComments()
        waitFor { g.state.comments.size == 3 }
        assertEquals(listOf("p1", "p2", "p3"), g.state.comments.map { it.heading })
        assertNull(g.state.moreComments)
    }

    @Test
    fun `with no match it offers the nearest results, and one chosen loads under the same header`() {
        val (g, _) = genius { search(Triple(42, "Someone Else", "Another Song"), Triple(7, "Genius Romanizations", "Song")) }

        g.show(track)
        waitFor { g.state.note?.text != GeniusRows.LOOKING_UP }
        assertEquals(GeniusRows.NO_MATCH, g.state.note?.text)
        assertEquals(listOf(42L), g.state.suggestions.map { it.id })

        g.choose(g.state.suggestions[0])
        waitFor { g.state.pageUrl != null }
        assertEquals(track, g.state.track)
        assertTrue(g.state.suggestions.isEmpty())
        assertEquals("About it.", g.state.about)
    }

    @Test
    fun `a failure says why, and a tap asks again`() {
        val (g, fake) = genius { search(Triple(42, "Band", "Song")) }
        fake.offline = true

        g.show(track)
        waitFor { g.state.note?.retry == true }
        assertEquals("Couldn't load the Genius page.", g.state.note?.text)
        assertEquals("Couldn't reach Genius: Unable to resolve host \"genius.com\". Tap to try again.", g.state.note?.detail)

        fake.offline = false
        g.retry()
        waitFor { g.state.pageUrl != null }
        assertNull(g.state.note)
    }

    @Test
    fun `the same song again asks Genius nothing, and a song without a title asks nothing at all`() {
        val (g, fake) = genius { search(Triple(42, "Band", "Song")) }
        g.show(track)
        waitFor { g.state.pageUrl != null }
        val asked = fake.asked.size

        g.show(track)
        waitFor { g.state.pageUrl != null }
        assertEquals(asked, fake.asked.size)

        g.show(Track(filePath = "/sdcard/Music/untitled.flac"))
        assertEquals(GeniusRows.NO_TITLE, g.state.note?.text)
        assertEquals(asked, fake.asked.size)
    }
}
