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

    /** The long-named song, playing. */
    val longPlaying: Track = edgeTracks.first()

    /** The Now Playing mockup's made-up waveform (its `renderVals`), as levels of 0 to 1. */
    val mockupWaveform: FloatArray = FloatArray(96) { i ->
        val env = 0.55 + 0.35 * kotlin.math.sin(i / 9.0) + 0.1 * kotlin.math.sin(i / 2.3)
        val fade = minOf(1.0, i / 6.0, (96 - i) / 8.0)
        kotlin.math.abs(env * fade * (0.75 + 0.25 * kotlin.math.sin(i * 1.7))).coerceAtMost(1.0).toFloat()
    }
}
