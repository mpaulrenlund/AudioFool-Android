package com.mpaulrenlund.audiofool.core.lyrics

import com.mpaulrenlund.audiofool.core.scrobbling.HttpAnswer
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The Lyrics screen's own logic (spec 5.5; the phone's: the desktop has no lyrics).
// The lyrics below are made up; LRCLIB's answers are made up in its shape.

class LyricsTest {
    private fun parse(text: String) = Lrc.parse(text, LyricsSource.LrcFile)

    // ---------------------------------------------------------------- LRC

    @Test
    fun `timed lines are read with their times in milliseconds`() {
        val lyrics = parse("[00:01.00]First line\n[00:04.5]Second line\n[01:02.345]Third line\n[2:03]Fourth")!!
        assertTrue(lyrics.synced)
        assertEquals(
            listOf(LyricLine(1000, "First line"), LyricLine(4500, "Second line"), LyricLine(62345, "Third line"), LyricLine(123000, "Fourth")),
            lyrics.lines,
        )
    }

    @Test
    fun `a fraction after a colon is read as one after a point`() {
        assertEquals(LyricLine(61250, "Line"), parse("[01:01:25]Line")!!.lines.single())
    }

    @Test
    fun `a line with several times is sung at each, in time order`() {
        val lyrics = parse("[00:10.00][01:30.00]Chorus\n[00:20.00]Verse")!!
        assertEquals(listOf(LyricLine(10000, "Chorus"), LyricLine(20000, "Verse"), LyricLine(90000, "Chorus")), lyrics.lines)
    }

    @Test
    fun `information lines are left out and brackets that are lyrics are kept`() {
        val text = """
            [ar:Some Artist]
            [ti:Some Title]
            [al:Some Album]
            [by:someone]
            [length: 03:21]
            [#:a comment]
            [00:01.00][Chorus]
            [00:02.00]Words
        """.trimIndent()
        assertEquals(listOf(LyricLine(1000, "[Chorus]"), LyricLine(2000, "Words")), parse(text)!!.lines)
    }

    @Test
    fun `the offset moves every line, plus being sooner`() {
        assertEquals(listOf(4500L, 9500L), parse("[offset:+500]\n[00:05.00]A\n[00:10.00]B")!!.lines.map { it.startMs })
        assertEquals(listOf(5250L), parse("[offset:-250]\n[00:05.00]A")!!.lines.map { it.startMs })
        assertEquals(listOf(0L), parse("[offset:2000]\n[00:01.00]A")!!.lines.map { it.startMs })
    }

    @Test
    fun `word times inside a line are left out`() {
        assertEquals("Hello there world", parse("[00:01.00]<00:01.00>Hello <00:01.50>there <00:02.00>world")!!.lines.single().text)
    }

    @Test
    fun `timed pauses stay, but never two together, at the start, or untimed lines in timed lyrics`() {
        val text = "[00:00.00]\n[00:01.00]One\n[00:02.00]\n[00:03.00]\nCredit line\n[00:04.00]Two\n[00:09.00]"
        assertEquals(
            listOf(LyricLine(1000, "One"), LyricLine(2000, ""), LyricLine(4000, "Two"), LyricLine(9000, "")),
            parse(text)!!.lines,
        )
    }

    @Test
    fun `text with no times is plain lyrics, verses kept apart once`() {
        val lyrics = parse("﻿\r\nFirst line\r\nSecond line\r\n\r\n\r\nNext verse\r\n\r\n")!!
        assertFalse(lyrics.synced)
        assertEquals(listOf("First line", "Second line", "", "Next verse"), lyrics.lines.map { it.text })
        assertTrue(lyrics.lines.all { it.startMs == -1L })
    }

    @Test
    fun `no words is no lyrics`() {
        assertNull(parse(""))
        assertNull(parse("[ar:Artist]\n[ti:Title]\n\n"))
        assertNull(parse("[00:01.00]\n[00:02.00]"))
    }

    @Test
    fun `the line being sung is the last one started`() {
        val lyrics = parse("[00:01.00]A\n[00:05.00]B\n[00:09.00]C")!!
        assertEquals(-1, lyrics.lineAt(0))
        assertEquals(-1, lyrics.lineAt(999))
        assertEquals(0, lyrics.lineAt(1000))
        assertEquals(0, lyrics.lineAt(4999))
        assertEquals(1, lyrics.lineAt(5000))
        assertEquals(2, lyrics.lineAt(600_000))
        assertEquals(-1, parse("A\nB")!!.lineAt(5000))
    }

    @Test
    fun `timed lyrics are picked over plain ones, then the first`() {
        val plain = "Just words"
        val timed = "[00:01.00]Timed words"
        assertEquals(LyricsSource.Tags, Lrc.pick(listOf(plain to LyricsSource.LrcFile, timed to LyricsSource.Tags))!!.source)
        assertEquals(LyricsSource.LrcFile, Lrc.pick(listOf(plain to LyricsSource.LrcFile, "Other" to LyricsSource.Tags))!!.source)
        assertEquals(LyricsSource.Tags, Lrc.pick(listOf("" to LyricsSource.LrcFile, plain to LyricsSource.Tags))!!.source)
        assertNull(Lrc.pick(emptyList()))
    }

    // ------------------------------------------------------------- .lrc file

    @Test
    fun `the lrc file sits beside the song with the same name`() {
        assertEquals(
            listOf("/storage/0000-0000/Music/A/03 Song.lrc", "/storage/0000-0000/Music/A/03 Song.LRC"),
            LrcFile.candidates("/storage/0000-0000/Music/A/03 Song.flac"),
        )
        assertEquals("/m/v1.0/Song.lrc", LrcFile.candidates("/m/v1.0/Song")[0])
    }

    @Test
    fun `the lrc file is read beside the song, and none is null`() {
        val dir = Files.createTempDirectory("lyrics").toFile()
        try {
            val song = File(dir, "01 Song.flac").apply { writeBytes(ByteArray(4)) }
            assertNull(LrcFile.read(song.path.replace('\\', '/')))
            File(dir, "01 Song.lrc").writeText("[00:01.00]Ça va", Charsets.UTF_8)
            assertEquals("[00:01.00]Ça va", LrcFile.read(song.path.replace('\\', '/')))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `lrc text is decoded as UTF-8, UTF-16 with its mark, or the Western code page`() {
        assertEquals("Café", LrcFile.decode("Café".toByteArray(Charsets.UTF_8)))
        assertEquals("Café", LrcFile.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Café".toByteArray(Charsets.UTF_8)))
        assertEquals("Café", LrcFile.decode(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Café".toByteArray(Charsets.UTF_16LE)))
        assertEquals("Café", LrcFile.decode(byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + "Café".toByteArray(Charsets.UTF_16BE)))
        assertEquals("Café", LrcFile.decode("Café".toByteArray(charset("windows-1252"))))
    }

    // --------------------------------------------------------------- LRCLIB

    private fun record(
        id: Long,
        artist: String,
        title: String,
        duration: Double = 200.0,
        synced: String? = null,
        plain: String? = null,
        instrumental: Boolean = false,
        album: String = "Album",
    ) = LrcLibRecord(id, title, artist, album, duration, instrumental, plain, synced)

    @Test
    fun `a match needs the artist, the title and about the length`() {
        val records = listOf(
            record(1, "Someone Else", "Song", synced = "[00:01.00]x"),
            record(2, "The Band", "Song (Live)", duration = 260.0, synced = "[00:01.00]x"),
            record(3, "The Band", "Song", plain = "words"),
            record(4, "Band", "Song - 2011 Remaster", duration = 201.5, synced = "[00:01.00]x"),
        )
        // Timed over plain; "The" and "(Remastered)" don't matter; the live version is too long.
        assertEquals(4L, LrcLibMatch.pick(records, "The Band", null, "Song (Remastered)", 200_000)?.id)
        assertEquals(3L, LrcLibMatch.pick(records.take(3), "The Band", null, "Song", 200_000)?.id)
        assertNull(LrcLibMatch.pick(records, "Nobody", null, "Song", 200_000))
        assertNull(LrcLibMatch.pick(records, "", "", "Song", 200_000))
        // The album artist counts, as on the Genius screen.
        assertEquals(4L, LrcLibMatch.pick(records, "", "Band", "Song", 200_000)?.id)
    }

    @Test
    fun `LRCLIB's answers are read loosely`() {
        val json = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"id":7,"trackName":"Song","artistName":"Band","albumName":"Album","duration":201.0,"instrumental":false,
                "plainLyrics":"Words","syncedLyrics":null,"extra":1}""",
        )
        assertEquals(record(7, "Band", "Song", duration = 201.0, plain = "Words"), LrcLibJson.record(json))
        assertNull(LrcLibJson.record(kotlinx.serialization.json.Json.parseToJsonElement("""{"code":404}""")))
        assertEquals(emptyList(), LrcLibJson.records(kotlinx.serialization.json.Json.parseToJsonElement("[]")))
    }

    /** LRCLIB as a map of path (decoded, without the base) to answer; counts every request. */
    private class FakeLrcLib(private val answer: (String) -> Pair<Int, String>) : LrcLibTransport {
        val asked = ArrayList<String>()

        override fun get(url: String): HttpAnswer {
            val path = URLDecoder.decode(URI(url).rawPath.removePrefix("/api/") + "?" + URI(url).rawQuery, Charsets.UTF_8)
            asked.add(path)
            val (status, body) = answer(path)
            return HttpAnswer(status, body)
        }
    }

    private fun json(id: Long, artist: String, title: String, duration: Double, synced: String?, plain: String?, instrumental: Boolean = false): String {
        fun q(s: String?) = if (s == null) "null" else "\"" + s.replace("\n", "\\n") + "\""
        return """{"id":$id,"trackName":${q(title)},"artistName":${q(artist)},"albumName":"Album","duration":$duration,
            "instrumental":$instrumental,"plainLyrics":${q(plain)},"syncedLyrics":${q(synced)}}"""
    }

    private val notFound = 404 to """{"code":404,"name":"TrackNotFound","message":"Failed to find specified track"}"""

    @Test
    fun `the song under its exact names is taken when it is timed, and kept for the run`() = runBlocking {
        val fake = FakeLrcLib { path ->
            if (path.startsWith("get?")) 200 to json(1, "Band", "Song", 200.0, "[00:01.00]One\n[00:02.00]Two", "One\nTwo") else 200 to "[]"
        }
        val online = OnlineLyrics(LrcLibClient(fake))
        val found = online.find("Band", "Band", "Song", "Album", 200_400)
        assertTrue(found.lyrics!!.synced)
        assertEquals(LyricsSource.LrcLib, found.lyrics!!.source)
        assertEquals(listOf("One", "Two"), found.lyrics!!.lines.map { it.text })
        assertEquals(listOf("get?track_name=Song&artist_name=Band&album_name=Album&duration=200"), fake.asked)

        online.find("Band", "Band", "Song", "Album", 200_400)
        assertEquals(1, fake.asked.size)
    }

    @Test
    fun `with no song under its exact names, LRCLIB's search finds it`() = runBlocking {
        val fake = FakeLrcLib { path ->
            when {
                path.startsWith("get?") -> notFound
                else -> 200 to "[" + json(5, "Other", "Song", 200.0, "[00:01.00]No", null) + "," +
                    json(6, "Band", "Song", 199.0, "[00:01.00]Yes", null) + "]"
            }
        }
        val found = OnlineLyrics(LrcLibClient(fake)).find("Band", null, "Song (Remastered)", "Album", 200_000)
        assertEquals("Yes", found.lyrics!!.lines.single().text)
        assertEquals("search?track_name=Song&artist_name=Band", fake.asked[1])
    }

    @Test
    fun `plain lyrics under the exact names are kept when the search has nothing timed`() = runBlocking {
        val fake = FakeLrcLib { path ->
            if (path.startsWith("get?")) 200 to json(1, "Band", "Song", 200.0, null, "Just words") else 200 to "[]"
        }
        val found = OnlineLyrics(LrcLibClient(fake)).find("Band", null, "Song", "Album", 200_000)
        assertFalse(found.lyrics!!.synced)
        assertEquals(2, fake.asked.size)
    }

    @Test
    fun `an instrumental says so, and nothing found is kept as nothing`() = runBlocking {
        val instrumental = FakeLrcLib { path -> if (path.startsWith("get?")) 200 to json(1, "Band", "Song", 200.0, null, null, instrumental = true) else 200 to "[]" }
        val found = OnlineLyrics(LrcLibClient(instrumental)).find("Band", null, "Song", "Album", 200_000)
        assertNull(found.lyrics)
        assertTrue(found.instrumental)

        val nothing = FakeLrcLib { path -> if (path.startsWith("get?")) notFound else 200 to "[]" }
        val online = OnlineLyrics(LrcLibClient(nothing))
        assertEquals(OnlineFind.None, online.find("Band", null, "Song", "Album", 200_000))
        online.find("Band", null, "Song", "Album", 200_000)
        assertEquals(2, nothing.asked.size)
    }

    @Test
    fun `no album or length skips the exact ask, and no artist or title asks nothing`() = runBlocking {
        val fake = FakeLrcLib { 200 to "[]" }
        val online = OnlineLyrics(LrcLibClient(fake))
        online.find("Band", null, "Song", "", 200_000)
        online.find("Band", null, "Song", "Album", 0)
        assertTrue(fake.asked.all { it.startsWith("search?") })
        assertEquals(OnlineFind.None, online.find("", "", "Song", "Album", 200_000))
        assertEquals(OnlineFind.None, online.find("Band", null, "  ", "Album", 200_000))
        assertEquals(2, fake.asked.size)
    }

    @Test
    fun `failures say why and are not kept`() = runBlocking {
        var fail = true
        val fake = object : LrcLibTransport {
            override fun get(url: String): HttpAnswer {
                if (fail) throw IOException("Unable to resolve host \"lrclib.net\"")
                return HttpAnswer(200, json(1, "Band", "Song", 200.0, "[00:01.00]One", null))
            }
        }
        val online = OnlineLyrics(LrcLibClient(fake))
        val e = assertFailsWith<LyricsException> { online.find("Band", null, "Song", "Album", 200_000) }
        assertEquals("Couldn't reach LRCLIB: Unable to resolve host \"lrclib.net\"", e.message)
        fail = false
        assertTrue(online.find("Band", null, "Song", "Album", 200_000).lyrics!!.synced)

        val server = LrcLibClient { HttpAnswer(500, """{"message":"Something broke."}""") }
        assertEquals("LRCLIB answered HTTP 500: Something broke", assertFailsWith<LyricsException> { server.search("Band", "Song") }.message)
        val page = LrcLibClient { HttpAnswer(200, "<html>blocked</html>") }
        assertEquals("LRCLIB answered in a way AudioFool doesn't recognise.", assertFailsWith<LyricsException> { page.search("Band", "Song") }.message)
    }
}
