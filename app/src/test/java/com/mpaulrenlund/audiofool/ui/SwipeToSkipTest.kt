package com.mpaulrenlund.audiofool.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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

/**
 * Now Playing's swipe to skip (0.2.0): whatever the swipe, the art and title end
 * up back in place. 0.2.0 left them stuck part-way off the screen on the S24,
 * whose touch screen sends a move every 8 ms or so; the emulator's injected
 * swipes are too slow to show it, so the swipes here are a phone's.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = ThemeLabTest.S24)
class SwipeToSkipTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val tokens by lazy { AppTokens.get(ApplicationProvider.getApplicationContext<android.app.Application>().assets) }
    private val second = ThemeLabFixtures.tracks.first { it.title == "Grey Plastic" }
    private val nowPlaying = NowPlaying().apply { showForPictures(ThemeLabFixtures.nowPlayingTrack, index = 2, size = 10) }
    private var nexts = 0
    private var previouses = 0

    private fun show() {
        val actions = NowPlayingActions(
            next = {
                nexts++
                nowPlaying.showForPictures(second, index = 3, size = 10)
            },
            previous = { previouses++ },
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

    /** The artist line, inside the swiped block, and the same on both songs. */
    private fun artistLeft() = compose.onNodeWithText("Aphelion Drive").getBoundsInRoot().left

    /**
     * A phone's swipe: a move of [step] px every 8 ms, [moves] times, then the last
     * few moves and the lift in the same frame, as a phone's touch screen batches them.
     */
    private fun swipe(step: Float, moves: Int) {
        compose.onNodeWithText("Aphelion Drive").performTouchInput {
            down(center)
            repeat(moves) { moveBy(Offset(step, 0f), delayMillis = 8) }
            repeat(3) { moveBy(Offset(step, 0f), delayMillis = 0) }
            up()
        }
    }

    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(3000)
        compose.waitForIdle()
    }

    @Test fun a_quick_swipe_left_skips_and_comes_back() {
        show()
        val home = artistLeft()
        swipe(-30f, 20)
        settle()
        assertEquals(1, nexts)
        assertEquals(home, artistLeft())
    }

    /** 0.2.1: a skip involves no animation, so nothing can leave the art off the screen. */
    @Test fun a_skip_is_back_in_place_at_once() {
        show()
        val home = artistLeft()
        compose.mainClock.autoAdvance = false
        swipe(-30f, 20)
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1, nexts)
        assertEquals(home, artistLeft())
        compose.mainClock.autoAdvance = true
    }

    @Test fun a_quick_swipe_right_goes_back_and_comes_back() {
        show()
        val home = artistLeft()
        swipe(30f, 20)
        settle()
        assertEquals(1, previouses)
        assertEquals(home, artistLeft())
    }

    @Test fun a_short_swipe_springs_back_without_skipping() {
        show()
        val home = artistLeft()
        compose.onNodeWithText("Aphelion Drive").performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(-4f, 0f), delayMillis = 40) }
            up()
        }
        settle()
        assertEquals(0, nexts)
        assertEquals(home, artistLeft())
    }

    @Test fun a_swipe_inside_one_frame_comes_back() {
        show()
        val home = artistLeft()
        // No frame runs during the swipe: every move is still queued when the finger lifts.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Aphelion Drive").performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(-30f, 0f), delayMillis = 0) }
            up()
        }
        compose.mainClock.autoAdvance = true
        settle()
        assertEquals(1, nexts)
        assertEquals(home, artistLeft())
    }

    /** Android takes a swipe from the screen's edge for its back gesture: the app's touch is cancelled part-way. */
    @Test fun a_swipe_taken_by_the_system_comes_back() {
        show()
        val home = artistLeft()
        compose.onNodeWithText("Aphelion Drive").performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(30f, 0f), delayMillis = 8) }
            cancel()
        }
        settle()
        assertEquals(0, previouses)
        assertEquals(home, artistLeft())
    }

    @Test fun swipes_one_after_another_all_come_back() {
        show()
        val home = artistLeft()
        repeat(4) { swipe(if (it % 2 == 0) -30f else 30f, 15) }
        settle()
        assertEquals(home, artistLeft())
    }
}
