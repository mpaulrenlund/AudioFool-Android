package com.mpaulrenlund.audiofool.theme

import com.mpaulrenlund.audiofool.core.library.LibraryScanner
import com.mpaulrenlund.audiofool.core.library.LibraryStatistics
import com.mpaulrenlund.audiofool.core.library.MusicLibrary
import com.mpaulrenlund.audiofool.core.model.Track

/**
 * The mockups' sample library (design/mockup), so ThemeLab's pictures can be laid
 * beside design/screens: the same artists and album counts, Aphelion Drive's three
 * albums, Second Sight's ten songs with their times, and the Polygon songs Search
 * finds. Albums no mockup opens get a few placeholder songs.
 */
object ThemeLabFixtures {
    const val INTERNAL = "/storage/emulated/0/Music"

    /** Statistics (spec 4.6) the size of the desktop's library, as last seen there. */
    val fullLibraryStats = LibraryStatistics(
        trackCount = 26_795,
        albumCount = 2_376,
        artistCount = 499,
        totalDurationMs = 7_421_000_000,
        totalBytes = 743_000_000_000,
        fileTypes = listOf(
            LibraryStatistics.Slice("FLAC", 19_204, 19_204 / 26_795.0, 611_000_000_000),
            LibraryStatistics.Slice("MP3", 6_102, 6_102 / 26_795.0, 58_000_000_000),
            LibraryStatistics.Slice("DSD", 812, 812 / 26_795.0, 61_000_000_000),
            LibraryStatistics.Slice("ALAC", 498, 498 / 26_795.0, 11_000_000_000),
            LibraryStatistics.Slice("AAC", 176, 176 / 26_795.0, 1_400_000_000),
            LibraryStatistics.Slice("WAV", 3, 3 / 26_795.0, 120_000_000),
        ),
    )
    const val SD_CARD = "/storage/1A2B-3C4D/Music"

    private fun ms(time: String): Long = time.split(":").let { (m, s) -> (m.toLong() * 60 + s.toLong()) * 1000 }

    private fun track(root: String, artist: String, album: String, year: Int, n: Int, title: String, time: String) = Track(
        filePath = "$root/$artist/$album/%02d %s.flac".format(n, title),
        title = title,
        artist = artist,
        albumArtist = artist,
        album = album,
        trackNumber = n,
        year = year,
        durationMs = ms(time),
        kind = "FLAC",
        sampleRate = 44100,
        bitDepth = 16,
    )

    private fun album(root: String, artist: String, album: String, year: Int, songs: List<Pair<String, String>>) =
        songs.mapIndexed { i, (title, time) -> track(root, artist, album, year, i + 1, title, time) }

    private fun filler(root: String, artist: String, album: String, year: Int, count: Int) =
        album(root, artist, album, year, (1..count).map { "Track $it" to "3:${(10 + it * 7) % 60}".padEnd(4, '0') })

    val secondSight = listOf(
        "Sunlight Through Static" to "4:31", "Memory Card" to "3:34", "Polygon Weather" to "5:32",
        "Low Poly Sunrise" to "4:49", "Analogue Stick" to "3:18", "Wireframe Hymn" to "6:41",
        "Disc Read Error" to "2:56", "Grey Plastic" to "4:03", "Startup Chime" to "1:28", "Second Sight" to "6:06",
    )

    /** The artists, album counts and order of the Artists mockup. Grey Chassis and The Polygon Set live on the SD card. */
    val tracks: List<Track> = buildList {
        addAll(filler(INTERNAL, "Aeon Static", "Static Bloom", 2004, 9))
        addAll(filler(INTERNAL, "Aphelion Drive", "First Light", 1995, 6))
        addAll(album(INTERNAL, "Aphelion Drive", "Second Sight", 1997, secondSight))
        addAll(filler(INTERNAL, "Aphelion Drive", "Third Person", 2001, 8))
        addAll(filler(INTERNAL, "Bitcrush Quartet", "Eight Bit", 2009, 7))
        addAll(filler(INTERNAL, "Bitcrush Quartet", "Sixteen Bit", 2012, 9))
        addAll(filler(INTERNAL, "Dual Shock Ensemble", "Rumble", 1998, 10))
        addAll(filler(INTERNAL, "Emotion Engine", "Two Hundred Ninety-Four Megahertz", 2000, 11))
        addAll(filler(INTERNAL, "Fifth Generation", "Thirty-Two Bit", 1996, 8))
        add(track(SD_CARD, "Grey Chassis", "Idle Animation", 2006, 4, "Low Polygon Lullaby", "4:12"))
        addAll(filler(INTERNAL, "Hyperblast Choir", "One", 2002, 5))
        addAll(filler(INTERNAL, "Hyperblast Choir", "Two", 2004, 5))
        addAll(filler(INTERNAL, "Hyperblast Choir", "Three", 2007, 5))
        addAll(filler(INTERNAL, "Hyperblast Choir", "Four", 2011, 5))
        addAll(filler(INTERNAL, "Memory Card Club", "Fifteen Blocks", 1999, 6))
        addAll(filler(INTERNAL, "Memory Card Club", "Save Point", 2003, 6))
        addAll(filler(SD_CARD, "The Polygon Set", "Wireframes", 1999, 9))
        add(track(SD_CARD, "The Polygon Set", "Polygon Count", 2003, 1, "Polygon Count", "3:47"))
        addAll(filler(SD_CARD, "The Polygon Set", "Polygon Count", 2003, 11).drop(1))
        addAll(filler(SD_CARD, "The Polygon Set", "Texture Pop", 2008, 10))
        addAll(filler(INTERNAL, "Redbook Audio", "Seventy-Four Minutes", 1997, 12))
        addAll(filler(INTERNAL, "Scanline", "Interlaced", 2001, 8))
        addAll(filler(INTERNAL, "Scanline", "Progressive", 2005, 8))
        addAll(filler(INTERNAL, "Vibration Function", "Pulse", 2010, 7))
    }

    val library: MusicLibrary = LibraryScanner.build(tracks)

    /** "Polygon Weather" from Second Sight: what the mockups show playing. */
    val playing: Track = tracks.first { it.title == "Polygon Weather" }

    /** Now Playing's song as its mockup shows it: a 24/96 FLAC at 1,140 kbps, third of ten. */
    val nowPlayingTrack: Track = playing.copy(sampleRate = 96000, bitDepth = 24, bitrate = 1140)

    /** The Now Playing mockup's position: 2:18 of 5:32. */
    const val NOW_PLAYING_POSITION_MS = 138_000L

    // ---- edge cases (session 5): very long names and a 120-song album ----

    const val LONG_ARTIST = "The Extraordinarily Long-Named Philharmonic Orchestra and Chorus of Greater Somewhere"
    const val LONG_ALBUM = "Complete Symphonies, Overtures, Incidental Music and Assorted Fragments (Remastered Deluxe Anniversary Edition)"
    const val BIG_ALBUM = "The Complete Sessions"

    val edgeTracks: List<Track> = buildList {
        add(track(INTERNAL, LONG_ARTIST, LONG_ALBUM, 2019, 1,
            "Symphony No. 9 in D Minor, Op. 125 \"Choral\": IV. Presto – Allegro assai – Andante maestoso – Allegro energico, sempre ben marcato", "24:51"))
        addAll(filler(INTERNAL, LONG_ARTIST, LONG_ALBUM, 2019, 7).drop(1))
        addAll(filler(INTERNAL, "Aphelion Drive", BIG_ALBUM, 2024, 120))
        addAll(tracks)
    }

    val edgeLibrary: MusicLibrary = LibraryScanner.build(edgeTracks)

    /** Every entry the letter strip can hold (0.2.0): "#", A to Z and "•", two artists each. */
    val alphabetLibrary: MusicLibrary = LibraryScanner.build(buildList {
        val names = listOf("2 Player Mode", "8-Bit Choir") +
            ('A'..'Z').flatMap { listOf("${it}x Ensemble", "${it}y Quartet") } +
            listOf("Кино", "坂本龍一")
        names.forEachIndexed { i, name -> addAll(filler(INTERNAL, name, "Album $i", 2000 + i % 20, 2)) }
    })

    // ---- Albums' year strip (0.11.0) ----

    const val YEARS_ARTIST = "Discography Unit"
    const val CAREER_ARTIST = "Sixty Year Septet"

    /**
     * [YEARS_ARTIST]: 26 albums over 1985 to 2024, some years twice, and one with no
     * year, too many to fit. [CAREER_ARTIST]: one album a year for sixty years, more
     * years than the strip can write on either phone.
     */
    val yearsLibrary: MusicLibrary = LibraryScanner.build(buildList {
        val years = listOf(1985, 1987, 1988, 1990, 1991, 1991, 1993, 1995, 1996, 1997, 1999, 1999, 2001, 2003,
            2004, 2006, 2008, 2009, 2011, 2013, 2015, 2016, 2018, 2020, 2022, 2024)
        years.forEachIndexed { i, year -> addAll(filler(INTERNAL, YEARS_ARTIST, "Release ${i + 1}", year, 8)) }
        addAll(filler(INTERNAL, YEARS_ARTIST, "Untitled Tape", 2000, 4).map { it.copy(year = null) })
        (1965..2024).forEach { year -> addAll(filler(INTERNAL, CAREER_ARTIST, "Live in $year", year, 3)) }
    })

    /** The long-named song, playing. */
    val longPlaying: Track = edgeTracks.first()

    /**
     * The Now Playing mockup's made-up waveform (its `renderVals`, 96 values) as levels of 0
     * to 1, spread over the real 600 columns (0.2.4) with the same outline and a finer texture.
     */
    val mockupWaveform: FloatArray = FloatArray(600) { i ->
        val x = i * 96.0 / 600
        val env = 0.55 + 0.35 * kotlin.math.sin(x / 9.0) + 0.1 * kotlin.math.sin(x / 2.3)
        val fade = minOf(1.0, x / 6.0, (96 - x) / 8.0)
        // Uneven detail, as a real song's: no single sine, which lines up into a comb at 600 columns.
        val detail = 0.8 + 0.1 * kotlin.math.sin(i * 0.37) + 0.06 * kotlin.math.sin(i * 1.13 + 1.0) + 0.04 * kotlin.math.sin(i * 2.71 + 2.0)
        kotlin.math.abs(env * fade * detail).coerceAtMost(1.0).toFloat()
    }

    /**
     * Analyze's picture (0.6.0): a made-up spectrum shaped like a song made from a
     * lossy file. The music falls 2 dB a kilohertz, swells and dips over the song
     * with uneven texture, and stops dead at [cutoffHz] (decoder silence above).
     * Built directly, not decoded: ThemeLab has no BASS.
     */
    fun spectrum(rate: Int = 96000, cutoffHz: Double = 16000.0, seconds: Double = 332.0): com.mpaulrenlund.audiofool.core.analysis.SpectrumAnalysis {
        val size = com.mpaulrenlund.audiofool.core.analysis.SpectrumAccumulator.fftSizeFor(rate)
        val bins = size / 2 + 1
        val nyquist = rate / 2.0
        fun music(hz: Double) = if (hz < cutoffHz) -40 - 2 * (hz - 1000).coerceAtLeast(0.0) / 1000 else -140.0
        val average = DoubleArray(bins) { k -> music(k.toDouble() * rate / size) }
        val columns = 800
        val rows = 512
        val random = java.util.Random(7)
        val picture = FloatArray(columns * rows) { i ->
            val column = i / rows
            val hz = (i % rows + 0.5) / rows * nyquist
            val at = column / columns.toDouble()
            // Swells, a quiet bridge two thirds in, and a fade at the end.
            val swell = 6 * kotlin.math.sin(at * 17.0) + 4 * kotlin.math.sin(at * 41.0 + 1.0) - (if (at in 0.62..0.68) 18.0 else 0.0) - (if (at > 0.95) (at - 0.95) * 400 else 0.0)
            val level = music(hz)
            if (level <= -140) -140f else (level + swell - 6 + random.nextGaussian() * 3).toFloat()
        }
        return com.mpaulrenlund.audiofool.core.analysis.SpectrumAnalysis(
            sampleRate = rate, channels = 2, durationSeconds = seconds, fftSize = size,
            average = average, peak = average, columns = columns, rows = rows, picture = picture,
            peakSampleDb = -0.3, usedBits = 24, extraBitsShare = 0.99,
        )
    }

    // ---- Listening History (spec 4.7): made-up Last.fm answers over the sample library ----

    /** When the pictures are "taken": the recent plays' times are worded from it. */
    val historyNow: java.time.Instant = java.time.Instant.parse("2026-11-04T15:00:00Z")
    val historyZone: java.time.ZoneId = java.time.ZoneOffset.UTC

    private val historyProfile = com.mpaulrenlund.audiofool.core.scrobbling.ListenerProfile(
        "questinnn", 12_263, java.time.Instant.parse("2025-12-03T10:00:00Z"), 618, 1_219, 3_938,
    )

    /** Mostly the library's names; one artist, album and song it doesn't hold, which can't be tapped. */
    private val historyLists = com.mpaulrenlund.audiofool.lastfm.TopLists(
        artists = listOf(
            "Aphelion Drive" to 866, "The Polygon Set" to 602, "Hyperblast Choir" to 484, "Scanline" to 424,
            "Bitcrush Quartet" to 423, "Memory Card Club" to 409, "Northlane" to 325, "Emotion Engine" to 247,
            "Redbook Audio" to 234, "Vibration Function" to 212,
        ).map { (name, plays) -> com.mpaulrenlund.audiofool.core.scrobbling.TopEntry(name, null, plays.toLong()) },
        albums = listOf(
            Triple("Second Sight", "Aphelion Drive", 656), Triple("Polygon Count", "The Polygon Set", 512),
            Triple("Interlaced", "Scanline", 240), Triple("Rumble", "Dual Shock Ensemble", 195),
            Triple("Sixteen Bit", "Bitcrush Quartet", 170), Triple("Save Point", "Memory Card Club", 169),
            Triple("Two Hundred Ninety-Four Megahertz", "Emotion Engine", 144), Triple("Seventy-Four Minutes", "Redbook Audio", 125),
            Triple("Pulse", "Vibration Function", 120), Triple("Live At The Wireless", "Northlane", 102),
        ).map { (name, artist, plays) -> com.mpaulrenlund.audiofool.core.scrobbling.TopEntry(name, artist, plays.toLong()) },
        tracks = listOf(
            Triple("Polygon Weather", "Aphelion Drive", 123), Triple("Memory Card", "Aphelion Drive", 113),
            Triple("Low Poly Sunrise", "Aphelion Drive", 72), Triple("Wireframe Hymn", "Aphelion Drive", 65),
            Triple("Polygon Count", "The Polygon Set", 63), Triple("Echo Chamber", "Northlane", 63),
            Triple("Startup Chime", "Aphelion Drive", 60), Triple("Disc Read Error", "Aphelion Drive", 59),
            Triple("Low Polygon Lullaby", "Grey Chassis", 57), Triple("Grey Plastic", "Aphelion Drive", 55),
        ).map { (name, artist, plays) -> com.mpaulrenlund.audiofool.core.scrobbling.TopEntry(name, artist, plays.toLong()) },
    )

    /** The last 20 plays, newest first, the first still playing. */
    private val historyRecent: List<com.mpaulrenlund.audiofool.core.scrobbling.RecentPlay> = run {
        val plays = listOf(
            Triple("Polygon Weather", "Aphelion Drive", "Second Sight"), Triple("Low Poly Sunrise", "Aphelion Drive", "Second Sight"),
            Triple("Memory Card", "Aphelion Drive", "Second Sight"), Triple("Sunlight Through Static", "Aphelion Drive", "Second Sight"),
            Triple("Echo Chamber", "Northlane", "Live At The Wireless"), Triple("Plenty", "Northlane", "Live At The Wireless"),
            Triple("Polygon Count", "The Polygon Set", "Polygon Count"), Triple("Track 2", "The Polygon Set", "Polygon Count"),
            Triple("Track 3", "The Polygon Set", "Polygon Count"), Triple("Low Polygon Lullaby", "Grey Chassis", "Idle Animation"),
            Triple("Track 1", "Scanline", "Interlaced"), Triple("Track 2", "Scanline", "Interlaced"),
            Triple("Track 1", "Redbook Audio", "Seventy-Four Minutes"), Triple("Track 2", "Redbook Audio", "Seventy-Four Minutes"),
            Triple("Track 1", "Bitcrush Quartet", "Sixteen Bit"), Triple("Track 2", "Bitcrush Quartet", "Sixteen Bit"),
            Triple("Track 1", "Memory Card Club", "Save Point"), Triple("Track 2", "Memory Card Club", "Save Point"),
            Triple("Track 1", "Vibration Function", "Pulse"), Triple("Track 2", "Vibration Function", "Pulse"),
        )
        // Minutes before historyNow: playing, then minutes, hours, yesterday, days.
        val minutesAgo = listOf(0L, 4, 9, 14, 52, 125, 130, 136, 141, 410, 430, 455, 900, 920, 1500, 1510, 3000, 3010, 10000, 10010)
        plays.mapIndexed { i, (track, artist, album) ->
            com.mpaulrenlund.audiofool.core.scrobbling.RecentPlay(
                track, artist, album,
                if (i == 0) null else historyNow.minusSeconds(minutesAgo[i] * 60),
                nowPlaying = i == 0,
            )
        }
    }

    /** Listening History loaded, on [period] (the lists are the same for each: they're made up). */
    fun history(period: com.mpaulrenlund.audiofool.core.scrobbling.ListeningPeriod = com.mpaulrenlund.audiofool.lastfm.ListeningHistory.FIRST_PERIOD) =
        com.mpaulrenlund.audiofool.lastfm.HistoryState.loaded(
            "questinnn", historyProfile, period, historyLists, historyRecent,
            com.mpaulrenlund.audiofool.core.scrobbling.ListeningMatch(tracks), historyNow, historyZone,
        )

    /** A Second Sight song's path, for the Album screen with it chosen. */
    fun secondSightPath(title: String): String = tracks.first { it.album == "Second Sight" && it.title == title }.filePath

    // ---- the Genius screen (spec 5.2): a made-up page for the song playing, in Genius's shape ----

    private fun geniusHit(id: Long, title: String, artist: String) =
        com.mpaulrenlund.audiofool.core.genius.GeniusHit(id, title, artist, "https://genius.com/songs/$id")

    private val geniusSong = com.mpaulrenlund.audiofool.core.genius.GeniusSong(
        id = 4242,
        title = "Polygon Weather",
        artist = "Aphelion Drive",
        url = "https://genius.com/Aphelion-drive-polygon-weather-lyrics",
        about = "The centrepiece of Second Sight, written in one night on a borrowed four-track while the band were snowed in.\n\n" +
            "The drummer has said the rain at the end is the studio roof, recorded through an open window. A demo version is at https://example.com/polygon-demo.",
        album = "Second Sight",
        releaseDate = "March 3, 1997",
        credits = listOf(
            com.mpaulrenlund.audiofool.core.genius.GeniusCredit("Written by", listOf("Mara Lindqvist", "Tom Okafor")),
            com.mpaulrenlund.audiofool.core.genius.GeniusCredit("Produced by", listOf("Tom Okafor")),
            com.mpaulrenlund.audiofool.core.genius.GeniusCredit("Released", listOf("March 3, 1997")),
            com.mpaulrenlund.audiofool.core.genius.GeniusCredit("Recorded at", listOf("Northlight Studio, Gothenburg")),
            com.mpaulrenlund.audiofool.core.genius.GeniusCredit("Mastered at", listOf("Grey Room Mastering")),
            com.mpaulrenlund.audiofool.core.genius.GeniusCredit("Background Vocals", listOf("Mara Lindqvist", "Jun Sato", "Elise Marchetti")),
        ),
        connections = listOf(
            com.mpaulrenlund.audiofool.core.genius.GeniusConnection("Samples", listOf(geniusHit(11, "Startup Chime", "Aphelion Drive"))),
            com.mpaulrenlund.audiofool.core.genius.GeniusConnection(
                "Covered by",
                listOf(
                    geniusHit(21, "Polygon Weather", "Scanline"), geniusHit(22, "Polygon Weather", "Memory Card Club"),
                    geniusHit(23, "Polygon Weather (Acoustic)", "Grey Chassis"), geniusHit(24, "Polygon Weather", "Redbook Audio"),
                    geniusHit(25, "Polygon Weather", "Dual Shock Ensemble"), geniusHit(26, "Polygon Weather", "Bitcrush Quartet"),
                    geniusHit(27, "Polygon Weather", "Emotion Engine"),
                ),
            ),
        ),
    )

    private val geniusComments = com.mpaulrenlund.audiofool.core.genius.GeniusCommentPage(
        listOf(
            com.mpaulrenlund.audiofool.core.genius.GeniusComment("lowpolyfan", java.time.Instant.parse("2017-03-07T12:00:00Z"), 297, "Still the best thing they ever put out. The last minute gets me every time."),
            com.mpaulrenlund.audiofool.core.genius.GeniusComment("discreaderror", java.time.Instant.parse("2019-11-21T08:30:00Z"), 41, "Anyone else hear the startup chime hidden at 3:12?"),
            com.mpaulrenlund.audiofool.core.genius.GeniusComment("Anonymous", null, 1, "Saw them play this live in 1998. Louder than the record."),
        ),
        total = 57,
        nextPage = 2,
    )

    private val geniusPage = com.mpaulrenlund.audiofool.core.genius.GeniusPage(
        song = geniusSong,
        answers = listOf(
            com.mpaulrenlund.audiofool.core.genius.GeniusAnswer("What has the band said about the song?", "In a 1997 interview the band called it \"the weather report for a town that only exists in a video game\"."),
            com.mpaulrenlund.audiofool.core.genius.GeniusAnswer("Is there a live recording?", "https://example.com/polygon-weather-live"),
        ),
        annotations = listOf(
            com.mpaulrenlund.audiofool.core.genius.GeniusAnnotation("Clouds rendered in grey plastic", "The sky in the band's favourite racing game, drawn with just a few flat shapes.", verified = false),
            com.mpaulrenlund.audiofool.core.genius.GeniusAnnotation("Forecast: low poly, clearing by dawn", "We wanted a weather forecast you could only hear inside a game. — Mara", verified = true),
        ),
        comments = geniusComments,
    )

    /** The Genius screen with the whole made-up page. */
    fun genius(missing: List<String> = emptyList()): com.mpaulrenlund.audiofool.genius.GeniusState =
        com.mpaulrenlund.audiofool.genius.GeniusRows.page(
            nowPlayingTrack,
            com.mpaulrenlund.audiofool.core.genius.GeniusPageResult(
                if (missing.isEmpty()) geniusPage else geniusPage.copy(comments = com.mpaulrenlund.audiofool.core.genius.GeniusCommentPage.Empty),
                missing,
            ),
            historyZone,
        )

    /** Genius found no page that is the song: the nearest results. */
    val geniusSuggestions = listOf(
        geniusHit(31, "Polygon Weather (Live at the Roundhouse)", "Aphelion Drive & Friends"),
        geniusHit(32, "Polygon Weather Report", "The Low Poly Club"),
        geniusHit(33, "Weather", "Polygon"),
        geniusHit(34, "Polygon", "Weather Systems"),
        geniusHit(35, "Polygon Weather", "Second Sight Tribute Band"),
    )

    /**
     * Made-up lyrics for "Polygon Weather" (the Lyrics screen, spec 5.5), timed as an
     * LRC file is: at [NOW_PLAYING_POSITION_MS] (2:18) "Let the gradients bleed" is sung.
     */
    private const val POLYGON_WEATHER_LRC = """[ar:Aphelion Drive]
[ti:Polygon Weather]
[00:14.20]Static on the window, numbers in the rain
[00:19.80]Every cloud a polygon, folding into frame
[00:25.40]I drew the sky in vectors when you said goodbye
[00:31.00]Now the forecast's rendering a colder kind of light
[00:36.60]
[00:42.10]Oh, polygon weather
[00:46.30]Sharp edges on the breeze
[00:50.90]Oh, polygon weather
[00:55.20]You're falling in degrees
[01:01.00]
[01:20.50]The city hums in wireframe, the streetlights flicker blue
[01:26.10]I count the shapes between us, and every one is you
[01:31.70]The pressure's dropping slowly, the satellites agree
[01:37.30]There's a storm in low resolution coming after me
[01:43.00]
[01:48.40]Oh, polygon weather
[01:52.60]Sharp edges on the breeze
[01:57.20]Oh, polygon weather
[02:01.50]You're falling in degrees
[02:07.80]
[02:12.00]Turn the angles soft again
[02:16.40]Let the gradients bleed
[02:21.00]Smooth the corners of the world
[02:25.60]Till it's only you and me
[02:31.00]
[02:36.20]Oh, polygon weather
[02:40.40]Sharp edges on the breeze
[02:45.00]Oh, polygon weather
[02:49.30]You're falling in degrees
[02:55.00]"""

    /** The Lyrics screen's state for the song playing: its made-up lyrics, timed (from LRCLIB) or plain (from its tags). */
    fun lyrics(synced: Boolean = true): com.mpaulrenlund.audiofool.lyrics.LyricsState {
        val text = if (synced) POLYGON_WEATHER_LRC else POLYGON_WEATHER_LRC.lines().joinToString("\n") { it.substringAfterLast(']') }
        val source = if (synced) com.mpaulrenlund.audiofool.core.lyrics.LyricsSource.LrcLib else com.mpaulrenlund.audiofool.core.lyrics.LyricsSource.Tags
        return com.mpaulrenlund.audiofool.lyrics.LyricsState(nowPlayingTrack, com.mpaulrenlund.audiofool.core.lyrics.Lrc.parse(text, source))
    }

    /** The Album screen's album for the song playing, for the Genius screen's header. */
    val nowPlayingAlbum get() = library.albumHolding(nowPlayingTrack.filePath)

    /** Edit Tags (spec 5.4) on the song playing, with made-up details as if read from its file. */
    fun editTags() = com.mpaulrenlund.audiofool.ui.EditTagsState(nowPlayingTrack).apply {
        read(
            com.mpaulrenlund.audiofool.core.library.TagDetails(
                publisher = "Hollow Signal Records",
                composer = "M. Okafor; J. Lindqvist",
                genre = "Synthwave; Electronic",
                comment = "Ripped from the 2024 vinyl. Side B starts here.",
                numbers = com.mpaulrenlund.audiofool.core.library.NumberTexts("03", "10", "1", "1"),
            ),
        )
    }

    // ---- Now Playing's cover colour (2026-11-09): made-up covers, since ThemeLab can't read one ----

    private const val COVER_SIDE = 600f

    /** A square cover: [ground] all over, then whatever [draw] puts on it. Made afresh for each picture. */
    private fun cover(ground: Int, draw: android.graphics.Canvas.(android.graphics.Paint) -> Unit): android.graphics.Bitmap {
        val bitmap = android.graphics.Bitmap.createBitmap(COVER_SIDE.toInt(), COVER_SIDE.toInt(), android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(ground)
        canvas.draw(android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG))
        return bitmap
    }

    private fun android.graphics.Paint.of(rgb: Int) = apply { color = 0xFF000000.toInt() or rgb }

    /** A deep red cover, darker than either theme's playback colour: a band of near-black and an off-white disc on burgundy. */
    fun coverDeepRed() = cover(0xFF5A0E12.toInt()) { paint ->
        drawRect(0f, COVER_SIDE * 0.7f, COVER_SIDE, COVER_SIDE, paint.of(0x1C0506))
        drawCircle(COVER_SIDE * 0.5f, COVER_SIDE * 0.4f, COVER_SIDE * 0.16f, paint.of(0xF0E0D0))
    }

    /** A bright yellow cover, lighter than either theme's playback colour: black bars and a white disc on yellow. */
    fun coverYellow() = cover(0xFFF2C500.toInt()) { paint ->
        for (i in 0..2) drawRect(COVER_SIDE * 0.12f, COVER_SIDE * (0.58f + i * 0.1f), COVER_SIDE * 0.88f, COVER_SIDE * (0.62f + i * 0.1f), paint.of(0x000000))
        drawCircle(COVER_SIDE * 0.5f, COVER_SIDE * 0.3f, COVER_SIDE * 0.14f, paint.of(0xFFFFFF))
    }

    /** Several colours: a blue sky over green grass, with an orange sun. The sky is most of it. */
    fun coverLandscape() = cover(0xFF4A90D9.toInt()) { paint ->
        drawRect(0f, COVER_SIDE * 0.68f, COVER_SIDE, COVER_SIDE, paint.of(0x5B8C3A))
        drawCircle(COVER_SIDE * 0.72f, COVER_SIDE * 0.26f, COVER_SIDE * 0.11f, paint.of(0xF39C12))
    }

    /** Black and white: no colour to take, so the controls keep the theme's. */
    fun coverBlackAndWhite() = cover(0xFF161616.toInt()) { paint ->
        drawCircle(COVER_SIDE * 0.5f, COVER_SIDE * 0.5f, COVER_SIDE * 0.34f, paint.of(0x8A8A8A))
        drawCircle(COVER_SIDE * 0.5f, COVER_SIDE * 0.5f, COVER_SIDE * 0.2f, paint.of(0xF2F2F2))
    }
}
