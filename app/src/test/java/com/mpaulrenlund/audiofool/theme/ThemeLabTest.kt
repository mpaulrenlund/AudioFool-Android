package com.mpaulrenlund.audiofool.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.mpaulrenlund.audiofool.StartTimes
import com.mpaulrenlund.audiofool.core.library.LibraryScanner
import com.mpaulrenlund.audiofool.core.library.MusicLibrary
import com.mpaulrenlund.audiofool.core.library.ScanProgress
import com.mpaulrenlund.audiofool.library.LibraryState
import com.mpaulrenlund.audiofool.playback.NowPlaying
import com.mpaulrenlund.audiofool.settings.MusicFolder
import com.mpaulrenlund.audiofool.ui.AlbumScreen
import com.mpaulrenlund.audiofool.ui.AlbumsScreen
import com.mpaulrenlund.audiofool.ui.AppActions
import com.mpaulrenlund.audiofool.ui.ArtistsScreen
import com.mpaulrenlund.audiofool.ui.Browse
import com.mpaulrenlund.audiofool.ui.DebugActions
import com.mpaulrenlund.audiofool.ui.DebugNumbers
import com.mpaulrenlund.audiofool.ui.FolderLine
import com.mpaulrenlund.audiofool.ui.IndexPreview
import com.mpaulrenlund.audiofool.ui.LocalBarInsets
import com.mpaulrenlund.audiofool.ui.SearchScreen
import com.mpaulrenlund.audiofool.ui.SettingsActions
import com.mpaulrenlund.audiofool.ui.SettingsScreen
import com.mpaulrenlund.audiofool.ui.SheetPage
import com.mpaulrenlund.audiofool.ui.StatisticsScreen
import com.mpaulrenlund.audiofool.ui.TokenSheet
import com.mpaulrenlund.audiofool.ui.NowPlayingActions
import com.mpaulrenlund.audiofool.ui.NowPlayingScreen
import com.mpaulrenlund.audiofool.core.playback.OutputMode
import com.mpaulrenlund.audiofool.core.playback.OutputPath
import com.mpaulrenlund.audiofool.core.playback.OutputReadout
import com.mpaulrenlund.audiofool.core.playback.OutputRoute
import com.mpaulrenlund.audiofool.core.playback.RepeatMode
import com.mpaulrenlund.audiofool.core.playback.ListeningTime
import com.mpaulrenlund.audiofool.core.scrobbling.ScrobblerState
import com.mpaulrenlund.audiofool.lastfm.LastFm
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ThemeLab: renders composables in both themes at both device sizes, the
 * S24 Ultra (384 x 832 dp) and the M21 (375 x 667 dp). `recordRoborazziDebug`
 * writes the pictures to app/screenshots/; `verifyRoborazziDebug` compares
 * against them. The screens use the mockups' sample library and the mockups'
 * 28 dp status row and 20 dp handle as system bars, so each picture can be laid
 * beside its design/screens PNG.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeLabTest {
    private val tokens by lazy { AppTokens.get(ApplicationProvider.getApplicationContext<android.app.Application>().assets) }

    /** The mockups' bars: a 28 dp status row and a 20 dp gesture handle. */
    private val mockupBars = WindowInsets(top = 28.dp, bottom = 20.dp)

    internal fun browse(playing: Boolean = true, unavailable: List<String> = emptyList()) = Browse(
        library = ThemeLabFixtures.library,
        unavailableFolders = unavailable,
        nowPlaying = NowPlaying().apply { if (playing) showForPictures(ThemeLabFixtures.playing) },
    )

    private fun shoot(device: String, name: String, content: @Composable () -> Unit) {
        for (dark in listOf(true, false)) {
            val themeName = if (dark) "dark" else "light"
            captureRoboImage("screenshots/$device-$themeName-$name.png") {
                AudioFoolTheme(AppTheme(dark, tokens)) {
                    CompositionLocalProvider(LocalBarInsets provides mockupBars) { content() }
                }
            }
        }
    }

    private fun tokenSheet(device: String) {
        for (page in SheetPage.entries) shoot(device, "tokens-${page.name.lowercase()}") { TokenSheet(page) }
    }

    private fun screens(device: String) {
        val actions = AppActions()
        shoot(device, "artists") { ArtistsScreen(browse(), actions, initialSelected = "Aphelion Drive") }
        shoot(device, "albums") { AlbumsScreen("Aphelion Drive", browse(), actions, initialSelected = "Second Sight") }
        shoot(device, "album") { AlbumScreen("Aphelion Drive", "Second Sight", browse(), actions) }
        shoot(device, "search") { SearchScreen(browse(), actions, initialQuery = "polygon") }
        shoot(device, "settings") {
            SettingsScreen(
                folders = listOf(
                    FolderLine(MusicFolder(ThemeLabFixtures.INTERNAL), "Internal storage · Music", unavailable = false),
                    FolderLine(MusicFolder(ThemeLabFixtures.SD_CARD), "SD card · Music", unavailable = false),
                ),
                actions = SettingsActions(),
            )
        }
        // Not in the mockups: nothing played yet, and the SD card taken out.
        shoot(device, "artists-nothing-played") { ArtistsScreen(browse(playing = false), actions) }
        // A first scan (no cache): the artists read so far, and the count in the header.
        shoot(device, "artists-first-scan") {
            val partial = Browse(LibraryScanner.build(ThemeLabFixtures.tracks.take(60)), emptyList(), NowPlaying())
            ArtistsScreen(partial, actions, scanning = true, progress = ScanProgress(filesFound = 27115, filesRead = 12000))
        }
        shoot(device, "artists-first-scan-starting") {
            ArtistsScreen(Browse(MusicLibrary.Empty, emptyList(), NowPlaying()), actions, scanning = true, progress = ScanProgress(filesFound = 27115, filesRead = 0))
        }
        // The letter strip (0.2.0, no mockup): as it shows while the list scrolls, and with a finger on H.
        shoot(device, "artists-index") { ArtistsScreen(browse(), actions, initialSelected = "Aphelion Drive", indexPreview = IndexPreview()) }
        shoot(device, "artists-index-pressed") { ArtistsScreen(browse(), actions, initialSelected = "Aphelion Drive", indexPreview = IndexPreview("H")) }
        shoot(device, "artists-sd-card-out") { ArtistsScreen(browse(unavailable = listOf(ThemeLabFixtures.SD_CARD)), actions) }
        shoot(device, "settings-sd-card-out") {
            SettingsScreen(
                folders = listOf(
                    FolderLine(MusicFolder(ThemeLabFixtures.INTERNAL), "Internal storage · Music", unavailable = false),
                    FolderLine(MusicFolder(ThemeLabFixtures.SD_CARD), "SD card · Music", unavailable = true),
                ),
                actions = SettingsActions(),
            )
        }
        // No mockup (spec 4.6): figures the size of the M21's library, to see the widest numbers fit.
        shoot(device, "statistics") {
            StatisticsScreen(
                LibraryState(library = ThemeLabFixtures.library),
                DebugNumbers(StartTimes()),
                actions = DebugActions(),
                stats = ThemeLabFixtures.fullLibraryStats,
                listening = ListeningTime(totalMs = 51_720_000, sinceUtc = 1_792_800_000_000),
            )
        }
        shoot(device, "statistics-empty") {
            StatisticsScreen(LibraryState(library = MusicLibrary.Empty), DebugNumbers(StartTimes()), actions = DebugActions(), listening = ListeningTime(sinceUtc = 1_792_800_000_000))
        }
        // As the mockup: playing, Repeat on (All), Shuffle off.
        shoot(device, "now-playing") { NowPlayingScreen(nowPlaying().apply { repeat = RepeatMode.All }, NowPlayingActions()) }
        // Not in the mockups: Repeat One and Shuffle on, paused, the waveform not worked out yet.
        shoot(device, "now-playing-modes") {
            val np = nowPlaying(playing = false, waveform = null).apply {
                repeat = RepeatMode.One
                shuffle = true
            }
            NowPlayingScreen(np, NowPlayingActions())
        }
        // BP's other two states (spec 6.4; off is the mockup's): on through a USB DAC's
        // bit-perfect mixer, and greyed out on the phone speaker.
        shoot(device, "now-playing-bp-on") {
            val np = nowPlaying(readout = OutputReadout.describe(OutputRoute.Usb, OutputPath.BitPerfect, 96000, 24, 96000, false)).apply {
                bitPerfect = true
            }
            NowPlayingScreen(np, NowPlayingActions())
        }
        shoot(device, "now-playing-bp-greyed") {
            val np = nowPlaying(readout = OutputReadout.describe(OutputRoute.Speaker, OutputPath.Shared, 48000, 0, 96000, false)).apply {
                bitPerfectGreyed = true
            }
            NowPlayingScreen(np, NowPlayingActions())
        }
        shoot(device, "settings-bp-on") {
            SettingsScreen(
                folders = listOf(FolderLine(MusicFolder(ThemeLabFixtures.INTERNAL), "Internal storage · Music", unavailable = false)),
                actions = SettingsActions(),
                readout = OutputReadout.describe(OutputRoute.Usb, OutputPath.RateMatched, 96000, 24, 96000, false),
                bitPerfect = true,
            )
        }
        shoot(device, "settings-bp-greyed") {
            SettingsScreen(
                folders = listOf(FolderLine(MusicFolder(ThemeLabFixtures.INTERNAL), "Internal storage · Music", unavailable = false)),
                actions = SettingsActions(),
                readout = OutputReadout.describe(OutputRoute.Bluetooth, OutputPath.Shared, 48000, 0, 44100, false),
                bitPerfect = true,
                bitPerfectGreyed = true,
            )
        }
        // Last.fm (spec 7): connected, and Connect tapped with the desktop's key filled in.
        shoot(device, "settings-lastfm-connected") {
            SettingsScreen(
                folders = listOf(FolderLine(MusicFolder(ThemeLabFixtures.INTERNAL), "Internal storage · Music", unavailable = false)),
                actions = SettingsActions(),
                lastFm = LastFm.Status(state = ScrobblerState.Scrobbling, userName = "mpaulrenlund"),
            )
        }
        shoot(device, "settings-lastfm-connect") {
            SettingsScreen(
                folders = listOf(FolderLine(MusicFolder(ThemeLabFixtures.INTERNAL), "Internal storage · Music", unavailable = false)),
                actions = SettingsActions(),
                lastFm = LastFm.Status(enteringKeys = true, savedKey = "0123456789abcdef0123456789abcdef", savedSecret = ""),
            )
        }
    }

    /**
     * Edge cases (session 5): very long names, a 120-song album, an empty
     * library, and the main screens at a font scale of 1.3.
     */
    private fun edgeCases(device: String) {
        val actions = AppActions()
        val long = ThemeLabFixtures.LONG_ARTIST
        fun edge(track: com.mpaulrenlund.audiofool.core.model.Track = ThemeLabFixtures.longPlaying) = Browse(
            library = ThemeLabFixtures.edgeLibrary,
            unavailableFolders = emptyList(),
            nowPlaying = NowPlaying().apply { showForPictures(track) },
        )
        shoot(device, "edge-artists-long") { ArtistsScreen(edge(), actions, initialSelected = long) }
        shoot(device, "edge-albums-long") { AlbumsScreen(long, edge(), actions, initialSelected = ThemeLabFixtures.LONG_ALBUM) }
        shoot(device, "edge-album-long") { AlbumScreen(long, ThemeLabFixtures.LONG_ALBUM, edge(), actions) }
        shoot(device, "edge-album-120") {
            val big = ThemeLabFixtures.edgeLibrary.allTracks.first { it.album == ThemeLabFixtures.BIG_ALBUM && it.trackNumber == 3 }
            AlbumScreen("Aphelion Drive", ThemeLabFixtures.BIG_ALBUM, edge(big), actions)
        }
        shoot(device, "edge-search-long") { SearchScreen(edge(), actions, initialQuery = "symphon") }
        shoot(device, "edge-now-playing-long") {
            NowPlayingScreen(
                NowPlaying().apply {
                    showForPictures(ThemeLabFixtures.longPlaying, index = 0, size = 7, positionMs = 754_000, waveform = ThemeLabFixtures.mockupWaveform,
                        readout = OutputReadout.describe(OutputMode.Shared, 48000, 0, 44100, false))
                },
                NowPlayingActions(),
            )
        }
        // Every entry the strip can hold, to see 28 letters fit the M21's list.
        shoot(device, "edge-artists-index-full") {
            ArtistsScreen(Browse(ThemeLabFixtures.alphabetLibrary, emptyList(), NowPlaying()), actions, indexPreview = IndexPreview("M"))
        }
        shoot(device, "edge-artists-empty") { ArtistsScreen(Browse(MusicLibrary.Empty, emptyList(), NowPlaying()), actions) }
        shoot(device, "edge-search-empty") { SearchScreen(Browse(MusicLibrary.Empty, emptyList(), NowPlaying()), actions, initialQuery = "polygon") }
    }

    /** The main screens with Android's font size at 1.3. */
    private fun fontScale(device: String) {
        RuntimeEnvironment.setFontScale(1.3f)
        val actions = AppActions()
        shoot(device, "font130-artists") { ArtistsScreen(browse(), actions, initialSelected = "Aphelion Drive") }
        shoot(device, "font130-albums") { AlbumsScreen("Aphelion Drive", browse(), actions, initialSelected = "Second Sight") }
        shoot(device, "font130-album") { AlbumScreen("Aphelion Drive", "Second Sight", browse(), actions) }
        shoot(device, "font130-search") { SearchScreen(browse(), actions, initialQuery = "polygon") }
        shoot(device, "font130-now-playing") { NowPlayingScreen(nowPlaying().apply { repeat = RepeatMode.All }, NowPlayingActions()) }
        shoot(device, "font130-settings") {
            SettingsScreen(
                folders = listOf(
                    FolderLine(MusicFolder(ThemeLabFixtures.INTERNAL), "Internal storage · Music", unavailable = false),
                    FolderLine(MusicFolder(ThemeLabFixtures.SD_CARD), "SD card · Music", unavailable = false),
                ),
                actions = SettingsActions(),
                lastFm = LastFm.Status(state = ScrobblerState.Scrobbling, userName = "mpaulrenlund"),
            )
        }
        shoot(device, "font130-statistics") {
            StatisticsScreen(LibraryState(library = ThemeLabFixtures.library), DebugNumbers(StartTimes()), actions = DebugActions(),
                stats = ThemeLabFixtures.fullLibraryStats, listening = ListeningTime(totalMs = 51_720_000, sinceUtc = 1_792_800_000_000))
        }
    }

    /** Now Playing's mockup state: third of ten, 2:18 in, its waveform and the readout the shared output gives. */
    private fun nowPlaying(
        playing: Boolean = true,
        waveform: FloatArray? = ThemeLabFixtures.mockupWaveform,
        readout: String = OutputReadout.describe(OutputMode.Shared, 48000, 0, 96000, false),
    ) = NowPlaying().apply {
        showForPictures(
            ThemeLabFixtures.nowPlayingTrack,
            playing = playing,
            index = 2,
            size = 10,
            positionMs = ThemeLabFixtures.NOW_PLAYING_POSITION_MS,
            waveform = waveform,
            readout = readout,
        )
    }


    @Test
    @Config(qualifiers = S24)
    fun token_sheet_s24() = tokenSheet("s24")

    @Test
    @Config(qualifiers = M21)
    fun token_sheet_m21() = tokenSheet("m21")

    @Test
    @Config(qualifiers = S24)
    fun screens_s24() = screens("s24")

    @Test
    @Config(qualifiers = M21)
    fun screens_m21() = screens("m21")

    @Test
    @Config(qualifiers = S24)
    fun edge_cases_s24() = edgeCases("s24")

    @Test
    @Config(qualifiers = M21)
    fun edge_cases_m21() = edgeCases("m21")

    @Test
    @Config(qualifiers = S24)
    fun font_scale_s24() = fontScale("s24")

    @Test
    @Config(qualifiers = M21)
    fun font_scale_m21() = fontScale("m21")

    companion object {
        /** Galaxy S24 Ultra: 1440 x 3120 px, shown at 384 x 832 dp. */
        const val S24 = "w384dp-h832dp-port-xxhdpi"

        /** FiiO M21: 750 x 1334 px, shown at 375 x 667 dp. */
        const val M21 = "w375dp-h667dp-port-xhdpi"
    }
}
