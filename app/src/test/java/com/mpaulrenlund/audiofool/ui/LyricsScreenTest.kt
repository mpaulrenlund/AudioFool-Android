package com.mpaulrenlund.audiofool.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mpaulrenlund.audiofool.lyrics.LyricsState
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
import kotlin.test.assertTrue

/** The Lyrics screen's touches and following (spec 5.5): a tap on a timed line seeks there, and the list keeps the line being sung in view. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = ThemeLabTest.S24)
class LyricsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val tokens by lazy { AppTokens.get(ApplicationProvider.getApplicationContext<android.app.Application>().assets) }
    private val nowPlaying = NowPlaying().apply { showForPictures(ThemeLabFixtures.nowPlayingTrack, positionMs = ThemeLabFixtures.NOW_PLAYING_POSITION_MS) }
    private val seeks = ArrayList<Long>()

    private fun show(state: LyricsState) {
        compose.setContent {
            AudioFoolTheme(AppTheme(true, tokens)) {
                CompositionLocalProvider(LocalBarInsets provides WindowInsets(top = 28.dp, bottom = 20.dp)) {
                    LyricsScreen(state, nowPlaying, LyricsActions(seek = { seeks += it }))
                }
            }
        }
        compose.waitForIdle()
    }

    private fun topOf(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    private val height get() = compose.onRoot().fetchSemanticsNode().boundsInRoot.height

    @Test fun a_tap_on_a_timed_line_seeks_to_it() {
        show(ThemeLabFixtures.lyrics())
        compose.onNodeWithText("Smooth the corners of the world").performClick()
        assertEquals(listOf(141_000L), seeks)
    }

    @Test fun plain_lyrics_have_no_time_to_seek_to() {
        show(ThemeLabFixtures.lyrics(synced = false))
        compose.onNodeWithText("Static on the window, numbers in the rain").performClick()
        assertTrue(seeks.isEmpty())
    }

    /** Under the lyrics, where they came from (the pictures stop short of the end of a whole song). */
    @Test fun the_source_is_named_under_the_lyrics() {
        show(ThemeLabFixtures.lyrics(synced = false))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Lyrics from the song's tags, without timing"))
        compose.onNodeWithText("Lyrics from the song's tags, without timing").assertIsDisplayed()
    }

    @Test fun timed_lyrics_say_they_are_timed() {
        show(ThemeLabFixtures.lyrics())
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Timed lyrics from LRCLIB"))
        compose.onNodeWithText("Timed lyrics from LRCLIB").assertIsDisplayed()
    }

    @Test fun the_line_being_sung_is_kept_in_view_and_follows_a_seek() {
        show(ThemeLabFixtures.lyrics())
        // 2:18 is late in the song: the list has scrolled to put its line near the anchor, not at the bottom.
        val sung = topOf("Let the gradients bleed")
        assertTrue(sung > height * 0.2f && sung < height * 0.5f, "the line at 2:18 is at $sung of $height")

        // Moved to 0:31 (paused, a seek): the list comes back up to that line.
        compose.runOnIdle { nowPlaying.heldPositionMs = 31_500 }
        compose.waitForIdle()
        val early = topOf("Now the forecast's rendering a colder kind of light")
        assertTrue(early > 0f && early < height * 0.5f, "the line at 0:31 is at $early of $height")
    }
}
