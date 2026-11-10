package com.mpaulrenlund.audiofool.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mpaulrenlund.audiofool.core.model.Track
import com.mpaulrenlund.audiofool.playback.NowPlaying
import com.mpaulrenlund.audiofool.theme.AppTheme
import com.mpaulrenlund.audiofool.theme.AppTokens
import com.mpaulrenlund.audiofool.theme.AudioFoolTheme
import com.mpaulrenlund.audiofool.theme.ThemeLabFixtures
import com.mpaulrenlund.audiofool.theme.ThemeLabTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Now Playing's touches (spec 5): the small buttons' touch areas, the buttons' names, and the artist and album lines. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = ThemeLabTest.S24)
class NowPlayingTouchTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val tokens by lazy { AppTokens.get(ApplicationProvider.getApplicationContext<android.app.Application>().assets) }
    private val song = ThemeLabFixtures.nowPlayingTrack
    private val nowPlaying = NowPlaying().apply { showForPictures(song, index = 2, size = 10) }
    private val pressed = ArrayList<String>()
    private var seeks = ArrayList<Long>()

    private fun show() {
        val actions = NowPlayingActions(
            playPause = { pressed += "play" },
            previous = { pressed += "previous" },
            next = { pressed += "next" },
            seek = { seeks += it },
            cycleRepeat = { pressed += "repeat" },
            toggleShuffle = { pressed += "shuffle" },
            toggleBitPerfect = { pressed += "bp" },
            openArtist = { pressed += "artist ${it.artist}" },
            openAlbum = { pressed += "album ${it.album}" },
        )
        compose.setContent {
            AudioFoolTheme(AppTheme(true, tokens)) {
                CompositionLocalProvider(LocalBarInsets provides WindowInsets(top = 28.dp, bottom = 20.dp)) {
                    NowPlayingScreen(nowPlaying, actions)
                }
            }
        }
        compose.waitForIdle()
    }

    /** A tap [above] the middle of a 34 dp button; its face ends 17 dp from there. */
    private fun tapAbove(button: String, above: Int) {
        compose.onNodeWithContentDescription(button).performTouchInput {
            down(Offset(centerX, centerY - above.dp.toPx()))
            up()
        }
        compose.waitForIdle()
    }

    /**
     * Repeat, Shuffle and BP are 34 dp across, under the 48 dp a finger wants. Compose
     * widens a small touch area to 48 dp itself, so a tap 5 dp outside the face still
     * presses the button and nothing on screen had to grow; this pins that.
     */
    @Test fun a_tap_just_outside_a_small_button_still_presses_it() {
        show()
        tapAbove("Repeat", 22)
        tapAbove("Shuffle", 22)
        tapAbove("Bit-Perfect", 22)
        assertEquals(listOf("repeat", "shuffle", "bp"), pressed)
    }

    @Test fun a_tap_well_outside_a_small_button_does_nothing() {
        show()
        tapAbove("Bit-Perfect", 32)
        assertEquals(emptyList(), pressed)
    }

    @Test fun every_button_has_a_name_for_a_screen_reader() {
        show()
        for (name in listOf("Previous", "Pause", "Next", "Repeat", "Shuffle", "Bit-Perfect")) compose.onNodeWithContentDescription(name).performClick()
        assertEquals(listOf("previous", "play", "next", "repeat", "shuffle", "bp"), pressed)
    }

    @Test fun bp_cannot_be_pressed_where_it_cannot_work() {
        nowPlaying.showForPictures(song, index = 2, size = 10, bitPerfectGreyed = true)
        show()
        compose.onNodeWithContentDescription("Bit-Perfect").assertIsNotEnabled()
        tapAbove("Bit-Perfect", 0)
        assertEquals(emptyList(), pressed)
    }

    @Test fun the_artists_and_the_albums_names_open_them() {
        show()
        compose.onNodeWithText("Aphelion Drive").performClick()
        compose.onNodeWithText("Second Sight").performClick()
        assertEquals(listOf("artist Aphelion Drive", "album Second Sight"), pressed)
    }

    /** The finger ends just past the middle of the bar: 2:46 and a bit of 5:32. The time shows twice, in the bubble and as the elapsed time. */
    @Test fun dragging_the_seekbar_shows_the_time_under_the_finger_and_seeks_on_letting_go() {
        nowPlaying.showForPictures(song, playing = false, index = 2, size = 10)
        show()
        compose.onNodeWithText("0:00").assertExists()
        val bar = compose.onNodeWithContentDescription("Seek")
        bar.performTouchInput {
            down(Offset(width * 0.25f, centerY))
            repeat(10) { moveBy(Offset(width * 0.02515f, 0f), delayMillis = 16) }
        }
        compose.waitForIdle()
        assertEquals(2, compose.onAllNodes(hasText("2:46")).fetchSemanticsNodes().size, "the bubble and the elapsed time")
        assertEquals(emptyList(), seeks, "no seek while the finger is down")
        bar.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(1, seeks.size)
        assertTrue(seeks.single() in 166_000L..166_999L, "sought to ${seeks.single()}")
        // This player doesn't move when asked to seek, so the elapsed time is back at 0:00 too.
        assertEquals(0, compose.onAllNodes(hasText("2:46")).fetchSemanticsNodes().size, "the bubble is gone")
    }

    // ---- where the lines lead ----

    private val library = ThemeLabFixtures.library

    @Test fun the_artist_line_leads_to_that_artists_albums() =
        assertEquals(Route.Albums("Aphelion Drive"), artistRouteFor(song, library))

    /** A guest on someone else's album isn't in the artists list: the album's artist is. */
    @Test fun a_guest_artist_leads_to_the_albums_artist() {
        val guest = song.copy(artist = "Somebody Else")
        assertEquals(Route.Albums("Aphelion Drive"), artistRouteFor(guest, library))
    }

    /** A song filed under another artist's album, by an artist the library lists too: that artist's own albums. */
    @Test fun an_artist_the_library_lists_leads_to_their_own_albums() {
        val guest = song.copy(artist = "Scanline")
        assertEquals(Route.Albums("Scanline"), artistRouteFor(guest, library))
    }

    @Test fun the_album_line_leads_to_the_album_with_the_song_chosen() =
        assertEquals(Route.Album("Aphelion Drive", "Second Sight", song.filePath), albumRouteFor(song, library))

    @Test fun a_song_the_library_does_not_hold_leads_nowhere() {
        val stray = Track(filePath = "/storage/emulated/0/Download/stray.flac", artist = "Aphelion Drive", album = "Second Sight")
        assertNull(artistRouteFor(stray, library))
        assertNull(albumRouteFor(stray, library))
        assertNull(artistRouteFor(song, null))
    }
}
