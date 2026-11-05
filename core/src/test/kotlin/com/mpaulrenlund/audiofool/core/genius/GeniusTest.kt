package com.mpaulrenlund.audiofool.core.genius

import com.mpaulrenlund.audiofool.core.scrobbling.HttpAnswer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Test
import java.io.IOException
import java.net.URI
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

// Ported from the desktop's tests/AudioFool.Core.Tests/GeniusTests.cs (session 75):
// matching a library song to a page, reading genius.com's answers, and the lookup
// that ties them together. Each of the desktop's [Theory] rows is a case in a loop
// here. The answers below are made up in Genius's shape; none of Genius's own text
// is copied.

class GeniusTest {
    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    // --------------------------------------------------------- title cleaning

    @Test
    fun `cleanTitle drops trailing extras only`() {
        val cases = listOf(
            "Paranoid Android (Remastered)" to "Paranoid Android",
            "Paranoid Android - 2011 Remaster" to "Paranoid Android",
            "Paranoid Android - Remastered 2009" to "Paranoid Android",
            "Song [Live at Wembley]" to "Song",
            "Song (feat. Somebody)" to "Song",
            "Song feat. Somebody" to "Song",
            "Song ft. Somebody Else" to "Song",
            "Song (Radio Edit) [2014 Remaster]" to "Song",
            "Song - Live at X - 2001" to "Song",
            "Song (Mono Version)" to "Song",
            "Song (1997)" to "Song",
            "Seishun Line (from Ookiku Furikabutte)" to "Seishun Line",
            "Let It Go - From \"Frozen\"" to "Let It Go",
            "Song (Part 2)" to "Song (Part 2)",
            "(Don't Fear) The Reaper" to "(Don't Fear) The Reaper",
            "Track 1 - Intro" to "Track 1 - Intro",
            "Re-Arranged" to "Re-Arranged",
            "(Live)" to "(Live)",
            "  Plain  " to "Plain",
        )
        for ((title, expected) in cases) assertEquals(expected, GeniusMatch.cleanTitle(title), title)
    }

    @Test
    fun `searchArtist is the first artist before any featuring`() {
        val cases = listOf(
            "Artist feat. Guest" to "Artist",
            "Artist ft. Guest" to "Artist",
            "Simon & Garfunkel" to "Simon & Garfunkel",
            "  Radiohead " to "Radiohead",
        )
        for ((artist, expected) in cases) assertEquals(expected, GeniusMatch.searchArtist(artist), artist)
    }

    // ---------------------------------------------------------------- picking

    private fun hit(id: Long, artist: String, title: String) = GeniusHit(id, title, artist, "https://genius.com/$id")

    @Test
    fun `pick takes the song and skips Genius translation pages`() {
        val hits = listOf(
            hit(1, "Genius Türkçe Çeviriler", "Radiohead - Paranoid Android (Türkçe Çeviri)"),
            hit(2, "Radiohead", "Paranoid Android"),
        )

        assertEquals(2, GeniusMatch.pick(hits, "Radiohead", "Radiohead", "Paranoid Android (Remastered)")?.id)
    }

    @Test
    fun `pick needs both the artist and the title`() {
        val hits = listOf(hit(1, "Radiohead", "Karma Police"), hit(2, "Some Cover Band", "Paranoid Android"))

        assertNull(GeniusMatch.pick(hits, "Radiohead", "", "Paranoid Android"))
    }

    @Test
    fun `pick compares artists loosely`() {
        val cases = listOf(
            "Beatles" to "The Beatles",
            "The Beatles" to "Beatles",
            "Beyoncé & Jay-Z" to "Beyonce and JAY-Z",
            "Artist feat. Guest" to "Artist",
            "Artist" to "Artist & Partner",
            "Artist x Partner" to "Partner",
        )
        for ((ours, theirs) in cases) {
            assertEquals(7, GeniusMatch.pick(listOf(hit(7, theirs, "Some Song")), ours, "", "Some Song")?.id, "$ours / $theirs")
        }
    }

    @Test
    fun `pick compares titles without extras on either side`() {
        val hits = listOf(hit(3, "Artist", "Some Song (Live)"))

        assertEquals(3, GeniusMatch.pick(hits, "Artist", "", "Some Song - 2011 Remaster")?.id)
    }

    @Test
    fun `pick keeps parts apart`() {
        val hits = listOf(hit(1, "Artist", "Suite (Part 1)"), hit(2, "Artist", "Suite (Part 2)"))

        assertEquals(2, GeniusMatch.pick(hits, "Artist", "", "Suite (Part 2)")?.id)
    }

    @Test
    fun `pick falls back on the album artist`() {
        val hits = listOf(hit(4, "Band", "Song"))

        assertEquals(4, GeniusMatch.pick(hits, "Band's Singer", "Band", "Song")?.id)
    }

    @Test
    fun `pick without a title or artist finds nothing`() {
        val hits = listOf(hit(1, "Artist", "Song"))

        assertNull(GeniusMatch.pick(hits, "Artist", "", ""))
        assertNull(GeniusMatch.pick(hits, "", "", "Song"))
    }

    @Test
    fun `suggestions leave out Genius pages and repeats and stop at five`() {
        val hits = listOf(
            hit(1, "A", "One"), hit(2, "Genius English Translations", "x"), hit(1, "A", "One"),
            hit(3, "B", "Two"), hit(4, "C", "Three"), hit(5, "D", "Four"), hit(6, "E", "Five"), hit(7, "F", "Six"),
        )

        assertEquals(listOf(1L, 3, 4, 5, 6), GeniusMatch.suggestions(hits).map { it.id })
    }

    // ---------------------------------------------------------------- parsing

    @Test
    fun `searchHits reads songs with their primary artist`() {
        val hits = GeniusJson.searchHits(
            json(
                """
                {"sections":[{"type":"song","hits":[
                  {"type":"song","result":{"_type":"song","id":11,"title":"First","artist_names":"Band (Ft. Guest)",
                    "primary_artist":{"name":"Band"},"url":"https://genius.com/Band-first-lyrics"}},
                  {"type":"song","result":{"_type":"song","id":12,"title":"Second","artist_names":"Solo","url":"u"}},
                  {"type":"song","result":{"_type":"song","title":"No id"}}
                ]}]}
                """,
            ),
        )

        assertEquals(2, hits.size)
        assertEquals(GeniusHit(11, "First", "Band", "https://genius.com/Band-first-lyrics"), hits[0])
        assertEquals("Solo", hits[1].artist)
    }

    private val songJson = """
        {"song":{"id":42,"title":"Made-Up Song","url":"https://genius.com/made-up",
          "primary_artist":{"name":"Made-Up Band"},
          "description":{"plain":"First paragraph.\n\nSecond paragraph."},
          "album":{"name":"Made-Up Album"},
          "release_date_for_display":"May 1, 2001",
          "recording_location":"A Barn",
          "writer_artists":[{"name":"Writer One"},{"name":"Writer Two"}],
          "producer_artists":[{"name":"Producer"}],
          "custom_performances":[{"label":"Mastered At","artists":[{"name":"Studio"}]},{"label":"Empty","artists":[]}],
          "song_relationships":[
            {"relationship_type":"samples","songs":[{"id":1,"title":"Old Song","primary_artist":{"name":"Old Band"},"url":"o"}]},
            {"relationship_type":"sampled_in","songs":[]},
            {"relationship_type":"covered_by","songs":[{"id":2,"title":"Made-Up Song","primary_artist":{"name":"Cover Band"},"url":"c"}]},
            {"relationship_type":"translations","songs":[{"id":3,"title":"T","primary_artist":{"name":"Genius Translations"},"url":"t"}]},
            {"relationship_type":"brand_new_kind","songs":[{"id":4,"title":"N","primary_artist":{"name":"New"},"url":"n"}]}
          ]}}
        """

    @Test
    fun `song reads the write-up, album and credits in order`() {
        val s = GeniusJson.song(json(songJson))

        assertEquals(42, s.id)
        assertEquals("Made-Up Song", s.title)
        assertEquals("Made-Up Band", s.artist)
        assertEquals("First paragraph.\n\nSecond paragraph.", s.about)
        assertEquals("Made-Up Album", s.album)
        assertEquals("May 1, 2001", s.releaseDate)
        assertEquals(listOf("Written by", "Produced by", "Released", "Recorded at", "Mastered at"), s.credits.map { it.label })
        assertEquals(listOf("Writer One", "Writer Two"), s.credits[0].names)
    }

    @Test
    fun `creditLabel lowers only the small words after the first`() {
        val cases = listOf(
            "Mastered At" to "Mastered at",
            "Overdubbed At" to "Overdubbed at",
            "Video Director" to "Video Director",
            "A&R" to "A&R",
            "At Desk For Strings" to "At Desk for Strings",
        )
        for ((label, expected) in cases) assertEquals(expected, GeniusJson.creditLabel(label), label)
    }

    @Test
    fun `song connections leave out empty kinds and translations`() {
        val s = GeniusJson.song(json(songJson))

        assertEquals(listOf("Samples", "Covered by", "Brand new kind"), s.connections.map { it.label })
        assertEquals("Old Band", s.connections[0].songs[0].artist)
    }

    @Test
    fun `song without a write-up has no About`() {
        for (description in listOf("""{"plain":"?"}""", """{"plain":""}""", "null")) {
            val s = GeniusJson.song(json("""{"song":{"id":1,"title":"T","primary_artist":{"name":"A"},"description":$description}}"""))

            assertNull(s.about, description)
            assertTrue(s.credits.isEmpty())
            assertTrue(s.connections.isEmpty())
        }
    }

    @Test
    fun `annotations skip the write-up and unreviewed ones`() {
        val notes = GeniusJson.annotations(
            json(
                """
                {"referents":[
                  {"fragment":"Made-Up Song","is_description":true,"classification":"accepted","annotations":[{"body":{"plain":"About text"}}]},
                  {"fragment":"a first line","classification":"accepted","annotations":[{"body":{"plain":"What it means."},"verified":false}]},
                  {"fragment":"a guess","classification":"unreviewed","annotations":[{"body":{"plain":"Maybe."}}]},
                  {"fragment":"the artist's line","classification":"verified","annotations":[{"body":{"plain":"I meant this."},"verified":true}]},
                  {"fragment":"no body","classification":"accepted","annotations":[{"body":{"plain":""}}]}
                ]}
                """,
            ),
        )

        assertEquals(2, notes.size)
        assertEquals(GeniusAnnotation("a first line", "What it means.", false), notes[0])
        assertTrue(notes[1].verified)
    }

    @Test
    fun `answers keep answered questions only`() {
        val answers = GeniusJson.answers(
            json(
                """
                {"questions":[
                  {"state":"pinned","body":{"plain":"Who wrote it?"},"answer":{"body":{"plain":"The band."}}},
                  {"state":"unanswered","body":{"plain":"Why?"},"answer":null},
                  {"state":"answered","body":{"plain":"When?"},"answer":{"body":{"plain":""}}}
                ]}
                """,
            ),
        )

        assertEquals(listOf(GeniusAnswer("Who wrote it?", "The band.")), answers)
    }

    @Test
    fun `answers take a question sent as a bare string`() {
        // genius.com sends the question's text bare and only the answer wrapped.
        val answers = GeniusJson.answers(
            json("""{"questions":[{"state":"pinned","body":"What did the band say about it?","answer":{"body":{"plain":"That it was quick."}}}]}"""),
        )

        assertEquals(listOf(GeniusAnswer("What did the band say about it?", "That it was quick.")), answers)
    }

    @Test
    fun `comments read author, date, votes and the next page`() {
        val page = GeniusJson.comments(
            json(
                """
                {"comments":[
                  {"author":{"login":"listener"},"created_at":1488906011,"votes_total":297,"body":{"plain":"Great song."}},
                  {"author":null,"anonymous_author":{"name":"Someone"},"votes_total":-6,"body":{"plain":"Not for me."}},
                  {"author":{"login":"blank"},"votes_total":1,"body":{"plain":""}}
                ],"total_count":134,"next_page":2}
                """,
            ),
        )

        assertEquals(2, page.comments.size)
        assertEquals(GeniusComment("listener", Instant.ofEpochSecond(1488906011), 297, "Great song."), page.comments[0])
        assertEquals(GeniusComment("Someone", null, -6, "Not for me."), page.comments[1])
        assertEquals(134, page.total)
        assertEquals(2, page.nextPage)
    }

    @Test
    fun `comments' last page has no next page`() {
        val page = GeniusJson.comments(json("""{"comments":[],"total_count":0,"next_page":null}"""))

        assertTrue(page.comments.isEmpty())
        assertNull(page.nextPage)
    }

    // ----------------------------------------------------------- the client

    /** Answers each request from a function of its path and query; records what was asked. Status 0: unreachable. */
    private class FakeGenius(private val answer: (String) -> Pair<Int, String>) : GeniusTransport {
        val asked = ArrayList<String>()

        override fun get(url: String): HttpAnswer {
            val uri = URI(url)
            val path = uri.rawPath + (uri.rawQuery?.let { "?$it" } ?: "")
            synchronized(asked) { asked.add(path) }
            val (status, body) = answer(path)
            if (status == 0) throw IOException("No such host is known.")
            return HttpAnswer(status, body)
        }
    }

    private fun ok(response: String) = """{"meta":{"status":200},"response":$response}"""

    @Test
    fun `client turns a block page into a GeniusException`() = runBlocking {
        val client = GeniusClient(FakeGenius { 403 to "<html>blocked</html>" })

        val e = assertFailsWith<GeniusException> { client.getSong(1) }
        assertEquals("Genius answered HTTP 403 without a page AudioFool can read.", e.message)
    }

    @Test
    fun `client reports Genius's own error message`() = runBlocking {
        val client = GeniusClient(FakeGenius { 404 to """{"meta":{"status":404,"message":"Not found."}}""" })

        val e = assertFailsWith<GeniusException> { client.getSong(1) }
        assertEquals("Genius answered HTTP 404: Not found", e.message)
    }

    @Test
    fun `client says when Genius can't be reached`() = runBlocking {
        val client = GeniusClient(FakeGenius { 0 to "" })

        val e = assertFailsWith<GeniusException> { client.search("x", 5) }
        assertTrue(e.message!!.startsWith("Couldn't reach Genius"))
    }

    @Test
    fun `client asks the web API in plain text`() = runBlocking {
        val fake = FakeGenius { 200 to ok("""{"comments":[],"total_count":0}""") }

        GeniusClient(fake).getComments(78831, 2)

        assertEquals(listOf("/api/songs/78831/comments?per_page=50&page=2&text_format=plain"), fake.asked)
    }

    // ----------------------------------------------------------- the lookup

    private fun searchAnswer(vararg hits: Triple<Long, String, String>): String = ok(
        buildJsonObject {
            putJsonArray("sections") {
                addJsonObject {
                    put("type", "song")
                    putJsonArray("hits") {
                        for ((id, artist, title) in hits) {
                            addJsonObject {
                                put("type", "song")
                                putJsonObject("result") {
                                    put("_type", "song")
                                    put("id", id)
                                    put("title", title)
                                    putJsonObject("primary_artist") { put("name", artist) }
                                    put("url", "u$id")
                                }
                            }
                        }
                    }
                }
            }
        }.toString(),
    )

    @Test
    fun `find searches artist and clean title first and keeps the answer`() = runBlocking {
        val fake = FakeGenius {
            200 to searchAnswer(Triple(5L, "Genius Farsi Translations", "Radiohead - Paranoid Android"), Triple(6L, "Radiohead", "Paranoid Android"))
        }
        val lookup = GeniusLookup(GeniusClient(fake))

        val find = lookup.find("Radiohead", "Radiohead", "Paranoid Android (Remastered)")
        val again = lookup.find("Radiohead", "Radiohead", "Paranoid Android (Remastered)")

        assertEquals(6, find.match?.id)
        assertSame(find, again)
        assertEquals(listOf("/api/search/song?per_page=10&q=Radiohead%20Paranoid%20Android"), fake.asked)
    }

    @Test
    fun `find tries the title alone when the artist search misses`() = runBlocking {
        val fake = FakeGenius { path ->
            200 to if ("q=Band%20" in path) searchAnswer(Triple(1L, "Other", "Elsewhere")) else searchAnswer(Triple(2L, "Band", "Song"))
        }
        val lookup = GeniusLookup(GeniusClient(fake))

        val find = lookup.find("Band", "", "Song")

        assertEquals(2, find.match?.id)
        assertEquals(2, fake.asked.size)
    }

    @Test
    fun `find with no match offers the nearest results`() = runBlocking {
        val fake = FakeGenius { path ->
            200 to if ("q=Band%20" in path) {
                searchAnswer(Triple(1L, "Other", "Song"), Triple(2L, "Genius Romanizations", "Song"))
            } else {
                searchAnswer(Triple(1L, "Other", "Song"), Triple(3L, "Third", "Song"))
            }
        }
        val lookup = GeniusLookup(GeniusClient(fake))

        val find = lookup.find("Band", "", "Song")

        assertNull(find.match)
        assertEquals(listOf(1L, 3), find.suggestions.map { it.id })
    }

    @Test
    fun `find without a title asks nothing`() = runBlocking {
        val fake = FakeGenius { 200 to searchAnswer() }
        val lookup = GeniusLookup(GeniusClient(fake))

        val find = lookup.find("Band", "", " ")

        assertNull(find.match)
        assertTrue(find.suggestions.isEmpty())
        assertTrue(fake.asked.isEmpty())
    }

    @Test
    fun `find that fails is not kept`() = runBlocking {
        var offline = true
        val fake = FakeGenius { if (offline) 0 to "" else 200 to searchAnswer(Triple(1L, "Band", "Song")) }
        val lookup = GeniusLookup(GeniusClient(fake))

        assertFailsWith<GeniusException> { lookup.find("Band", "", "Song") }
        offline = false
        val find = lookup.find("Band", "", "Song")

        assertEquals(1, find.match?.id)
    }

    private fun pageAnswer(path: String, commentsFail: Boolean = false): Pair<Int, String> = when {
        path.startsWith("/api/songs/42/comments") ->
            if (commentsFail) 500 to "oops" else 200 to ok("""{"comments":[{"author":{"login":"a"},"votes_total":3,"body":{"plain":"Nice."}}],"total_count":1}""")
        path.startsWith("/api/songs/42") -> 200 to ok(songJson)
        path.startsWith("/api/questions") -> 200 to ok("""{"questions":[{"body":{"plain":"Q?"},"answer":{"body":{"plain":"A."}}}]}""")
        else -> 200 to ok("""{"referents":[{"fragment":"line","annotations":[{"body":{"plain":"Means."}}]}]}""")
    }

    @Test
    fun `load fetches all four and keeps a whole page`() = runBlocking {
        val fake = FakeGenius { pageAnswer(it) }
        val lookup = GeniusLookup(GeniusClient(fake))

        val first = lookup.load(42)
        val again = lookup.load(42)

        assertTrue(first.missing.isEmpty())
        assertEquals("Made-Up Song", first.page.song.title)
        assertEquals(1, first.page.answers.size)
        assertEquals(1, first.page.annotations.size)
        assertEquals(1, first.page.comments.comments.size)
        assertSame(first.page, again.page)
        assertEquals(4, fake.asked.size)
    }

    @Test
    fun `load without comments names them and asks again next time`() = runBlocking {
        val fake = FakeGenius { pageAnswer(it, commentsFail = true) }
        val lookup = GeniusLookup(GeniusClient(fake))

        val first = lookup.load(42)
        lookup.load(42)

        assertEquals(listOf("comments"), first.missing)
        assertTrue(first.page.comments.comments.isEmpty())
        assertEquals(1, first.page.answers.size)
        assertEquals(8, fake.asked.size)
    }

    @Test
    fun `load fails when the song itself fails`() = runBlocking {
        val fake = FakeGenius { p -> if (p.startsWith("/api/songs/42?")) 404 to """{"meta":{"status":404,"message":"Not found"}}""" else pageAnswer(p) }
        val lookup = GeniusLookup(GeniusClient(fake))

        assertFailsWith<GeniusException> { lookup.load(42) }
        Unit
    }
}
