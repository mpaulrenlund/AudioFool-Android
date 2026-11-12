package com.mpaulrenlund.audiofool.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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

/** Now Playing's touches (spec 5): the small buttons' touch areas, the buttons' names and places, the cover menu, and the artist and album lines. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = ThemeLabTest.S24)
class NowPlayingTouchTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val tokens by lazy { AppTokens.get(ApplicationProvider.getApplicationContext<android.app.Application>().assets) }
    private val song = ThemeLabFixtures.nowPlayingTrack
    private val nowPlaying = NowPlaying().apply { showForPictures(song, index = 2, size = 10) }
    private val pressed = ArrayList<String>()
    private var liked by mutableStateOf(false)
    private var seeks = ArrayList<Long>()

    private fun show(cover: android.graphics.Bitmap? = null) {
        val actions = NowPlayingActions(
            playPause = { pressed += "play" },
            previous = { pressed += "previous" },
            next = { pressed += "next" },
            seek = { seeks += it },
            toggleLike = { pressed += "like ${it.title}" },
            isLiked = { liked },
            toggleShuffle = { pressed += "shuffle" },
            toggleBitPerfect = { pressed += "bp" },
            openArtist = { pressed += "artist ${it.artist}" },
            openAlbum = { pressed += "album ${it.album}" },
            openCover = { pressed += "cover ${it.title}" },
            lyrics = { pressed += "lyrics" },
        )
        compose.setContent {
            AudioFoolTheme(AppTheme(true, tokens)) {
                CompositionLocalProvider(LocalBarInsets provides WindowInsets(top = 28.dp, bottom = 20.dp)) {
                    NowPlayingScreen(nowPlaying, actions, coverForPictures = cover)
                }
            }
        }
        compose.waitForIdle()
    }

    /** A tap [outside] dp straight above a button's face, or under it ([below]). */
    private fun tapOutside(button: String, outside: Int, below: Boolean = false) {
        compose.onNodeWithContentDescription(button).performTouchInput {
            val by = outside.dp.toPx()
            down(Offset(centerX, if (below) bottom + by else top - by))
            up()
        }
        compose.waitForIdle()
    }

    private fun bounds(name: String) = compose.onNodeWithContentDescription(name).fetchSemanticsNode().boundsInRoot

    private fun dp(px: Float) = with(compose.density) { px.toDp().value }

    /**
     * The heart and Shuffle are under the 48 dp a finger wants (44 dp on the S24, 35 on the
     * M21). Compose widens a small touch area to 48 dp itself, so a tap just outside
     * the face still presses the button and nothing on screen had to grow; this pins
     * that. Both are tried from above and from below.
     */
    @Test fun a_tap_just_outside_a_small_button_still_presses_it() {
        show()
        tapOutside("Like", 1)
        tapOutside("Like", 1, below = true)
        tapOutside("Shuffle", 1)
        tapOutside("Shuffle", 1, below = true)
        assertEquals(listOf("like ${song.title}", "like ${song.title}", "shuffle", "shuffle"), pressed)
    }

    @Test @Config(qualifiers = ThemeLabTest.M21)
    fun a_tap_just_outside_a_small_button_still_presses_it_on_the_m21() {
        show()
        tapOutside("Like", 5)
        tapOutside("Shuffle", 5, below = true)
        assertEquals(listOf("like ${song.title}", "shuffle"), pressed)
    }

    @Test fun a_tap_well_outside_a_small_button_does_nothing() {
        show()
        tapOutside("Like", 10)
        tapOutside("Shuffle", 10, below = true)
        assertEquals(emptyList(), pressed)
    }

    @Test fun every_button_has_a_name_for_a_screen_reader() {
        show()
        for (name in listOf("Previous", "Pause", "Next", "Like", "Shuffle")) compose.onNodeWithContentDescription(name).performClick()
        assertEquals(listOf("previous", "play", "next", "like ${song.title}", "shuffle"), pressed)
        compose.onNodeWithContentDescription("Bit-Perfect").assertDoesNotExist()
        compose.onNodeWithContentDescription("Repeat").assertDoesNotExist()
    }

    /** The heart (2026-11-12): says whether the song is liked, and a tap likes or unlikes the song playing. */
    @Test fun the_heart_says_whether_the_song_is_liked() {
        show()
        compose.onNodeWithContentDescription("Like").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not liked"))
        liked = true
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Like").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Liked")).performClick()
        assertEquals(listOf("like ${song.title}"), pressed)
    }

    // ---- the controls row (2026-11-10): the heart (Repeat until 2026-11-12) at the left, the transport centred, Shuffle at the right ----

    private fun checkControlsRow(modeSize: Float) {
        show()
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val heart = bounds("Like")
        val shuffle = bounds("Shuffle")
        val play = bounds("Pause")
        val previous = bounds("Previous")
        val next = bounds("Next")
        assertEquals(screen.center.x, play.center.x, 1f, "Play/Pause in the screen's middle")
        assertEquals(dp(play.left - previous.right), dp(next.left - play.right), 0.5f, "Previous and Next the same way from it")
        assertEquals(modeSize, dp(heart.width), 0.5f)
        assertEquals(modeSize, dp(shuffle.width), 0.5f)
        assertEquals(20f, dp(heart.left), 0.5f, "The heart level with the cover's and the seekbar's left")
        assertEquals(20f, dp(screen.right - shuffle.right), 0.5f, "Shuffle level with their right")
        assertEquals(play.center.y, heart.center.y, 1f, "The heart level with Play/Pause's middle")
        assertEquals(play.center.y, shuffle.center.y, 1f, "Shuffle level with Play/Pause's middle")
        assertTrue(dp(previous.left - heart.right) >= 11.5f, "at least 12 dp between the heart and Previous")
        assertTrue(dp(shuffle.left - next.right) >= 11.5f, "at least 12 dp between Next and Shuffle")
    }

    @Test fun the_transport_is_centred_between_the_heart_and_shuffle() = checkControlsRow(44f)

    @Test @Config(qualifiers = ThemeLabTest.M21)
    fun the_transport_is_centred_between_the_heart_and_shuffle_on_the_m21() = checkControlsRow(35f)

    // ---- the cover menu (2026-11-10): a tap opens it, and Bit-Perfect is a row in it ----

    @Test fun a_tap_on_the_cover_opens_its_menu() {
        show()
        compose.onNodeWithText("Analyze").assertDoesNotExist()
        compose.onNodeWithContentDescription("Cover").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Analyze").assertExists()
        compose.onNodeWithText("Bit-Perfect").assertExists()
    }

    @Test fun the_cover_menu_keeps_theme_and_version_but_not_the_logo_menus_other_rows() {
        // 2026-11-10, the user's calls: Listening History, Rescan Library, Settings and Statistics are the logo menu's only.
        show()
        compose.onNodeWithContentDescription("Cover").performClick()
        compose.waitForIdle()
        for (row in listOf("Resolution", "Analyze", "Bit-Perfect", "Genius", "Lyrics", "Theme", "Version")) compose.onNodeWithText(row).assertExists()
        for (row in listOf("Library Statistics", "Listening History", "Rescan Library", "Settings")) compose.onNodeWithText(row).assertDoesNotExist()
    }

    /** 2026-11-11, the user's request: the menu's Lyrics row opens the Lyrics screen (spec 5.5), and the menu closes. */
    @Test fun the_lyrics_row_opens_the_lyrics_screen() {
        show()
        compose.onNodeWithContentDescription("Cover").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Lyrics").performClick()
        compose.waitForIdle()
        assertEquals(listOf("lyrics"), pressed)
        compose.onNodeWithText("Analyze").assertDoesNotExist()
    }

    /** 2026-11-10, the user's call: the resolution opens the cover full screen (spec 5.3); with no cover it is information only, as before. */
    @Test fun the_resolution_opens_the_cover_full_screen() {
        show(cover = ThemeLabFixtures.coverYellow())
        compose.onNodeWithContentDescription("Cover").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Resolution").performClick()
        compose.waitForIdle()
        assertEquals(listOf("cover ${song.title}"), pressed)
        compose.onNodeWithText("Analyze").assertDoesNotExist()
    }

    @Test fun without_a_cover_the_resolution_does_nothing() {
        show()
        compose.onNodeWithContentDescription("Cover").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Resolution").performClick()
        compose.waitForIdle()
        assertEquals(emptyList(), pressed)
        compose.onNodeWithText("Analyze").assertExists()
    }

    @Test fun a_long_press_on_the_cover_still_opens_it() {
        show()
        compose.onNodeWithContentDescription("Cover").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Bit-Perfect").assertExists()
    }

    @Test fun bit_perfect_is_unchecked_while_off_and_a_tap_switches_it_keeping_the_menu_open() {
        show()
        compose.onNodeWithContentDescription("Cover").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Bit-Perfect").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Off")).performClick()
        compose.waitForIdle()
        assertEquals(listOf("bp"), pressed)
        compose.onNodeWithText("Bit-Perfect").assertExists()
    }

    @Test fun bit_perfect_is_checked_while_on() {
        nowPlaying.bitPerfect = true
        show()
        compose.onNodeWithContentDescription("Cover").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Bit-Perfect").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "On"))
    }

    @Test fun bit_perfect_cannot_be_switched_where_it_cannot_work() {
        nowPlaying.showForPictures(song, index = 2, size = 10, bitPerfectGreyed = true)
        show()
        compose.onNodeWithContentDescription("Cover").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Bit-Perfect").assertIsNotEnabled().performClick()
        compose.waitForIdle()
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
