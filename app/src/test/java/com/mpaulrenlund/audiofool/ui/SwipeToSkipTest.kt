package com.mpaulrenlund.audiofool.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import kotlin.test.assertTrue

/**
 * Now Playing's swipe to skip (0.2.0): whatever the swipe, the cover ends up back
 * in place (the title block moved with it until 2026-11-20; now only the cover does). 0.2.0 left them stuck part-way off the screen on the S24,
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
    private var stepBacks = 0

    private fun show() {
        val actions = NowPlayingActions(
            next = {
                nexts++
                nowPlaying.showForPictures(second, index = 3, size = 10)
            },
            previous = { previouses++ },
            stepBack = { stepBacks++ },
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

    /** The cover, the only part that moves with the finger (2026-11-20; the title block moved too until then). */
    private fun coverLeft() = compose.onNodeWithContentDescription("Cover").getBoundsInRoot().left

    /** The artist line, where the swipes start: it stays put. */
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
        val home = coverLeft()
        swipe(-30f, 20)
        settle()
        assertEquals(1, nexts)
        assertEquals(home, coverLeft())
    }

    /** 0.2.1: a skip involves no animation, so nothing can leave the art off the screen. */
    @Test fun a_skip_is_back_in_place_at_once() {
        show()
        val home = coverLeft()
        compose.mainClock.autoAdvance = false
        swipe(-30f, 20)
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1, nexts)
        assertEquals(home, coverLeft())
        compose.mainClock.autoAdvance = true
    }

    @Test fun a_quick_swipe_right_goes_back_and_comes_back() {
        show()
        val home = coverLeft()
        swipe(30f, 20)
        settle()
        assertEquals(1, stepBacks)
        assertEquals(home, coverLeft())
    }

    /** 2026-11-11: right is the song before however far in, so not the button's Previous (which restarts after 3 s). */
    @Test fun a_swipe_right_steps_back_not_previous() {
        show()
        swipe(30f, 20)
        settle()
        assertEquals(1, stepBacks)
        assertEquals(0, previouses)
    }

    /**
     * 2026-11-11, the swipe made easier: a slow drag of 90 dp, no flick, skips. The drag
     * counts from past the touch slop (16 dp here, about 8 on a phone), so this is 74 dp
     * against `swipeSkipDistance`; it took 96 before.
     */
    @Test fun a_slow_short_drag_skips() {
        show()
        val px = with(compose.density) { 90.dp.toPx() }
        compose.onNodeWithText("Aphelion Drive").performTouchInput {
            down(center)
            // Slow: 90 dp over 32 moves 50 ms apart, about 55 dp/s, far below any flick.
            repeat(32) { moveBy(Offset(-px / 32, 0f), delayMillis = 50) }
            repeat(10) { moveBy(Offset(0f, 0f), delayMillis = 50) }
            up()
        }
        settle()
        assertEquals(1, nexts)
    }

    /** A moderate flick of 40 dp in about 60 ms skips (500 dp/s is enough now; it took 1200 before). */
    @Test fun a_moderate_flick_skips() {
        show()
        val px = with(compose.density) { 40.dp.toPx() }
        compose.onNodeWithText("Aphelion Drive").performTouchInput {
            down(center)
            repeat(8) { moveBy(Offset(px / 8, 0f), delayMillis = 8) }
            up()
        }
        settle()
        assertEquals(1, stepBacks)
    }

    @Test fun a_short_swipe_springs_back_without_skipping() {
        show()
        val home = coverLeft()
        compose.onNodeWithText("Aphelion Drive").performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(-4f, 0f), delayMillis = 40) }
            up()
        }
        settle()
        assertEquals(0, nexts)
        assertEquals(home, coverLeft())
    }

    @Test fun a_swipe_inside_one_frame_comes_back() {
        show()
        val home = coverLeft()
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
        assertEquals(home, coverLeft())
    }

    /** Android takes a swipe from the screen's edge for its back gesture: the app's touch is cancelled part-way. */
    @Test fun a_swipe_taken_by_the_system_comes_back() {
        show()
        val home = coverLeft()
        compose.onNodeWithText("Aphelion Drive").performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(30f, 0f), delayMillis = 8) }
            cancel()
        }
        settle()
        assertEquals(0, previouses)
        assertEquals(home, coverLeft())
    }

    /**
     * 2026-11-20, the user's call: a drag started on the names moves and fades the cover only;
     * the names (and Analyze, Genius and Lyrics beside them) stay where they are.
     */
    @Test fun only_the_cover_moves_with_the_finger() {
        show()
        val cover = coverLeft()
        val artist = artistLeft()
        val analyze = compose.onNodeWithContentDescription("Analyze").getBoundsInRoot().left
        compose.onNodeWithText("Aphelion Drive").performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(-8f, 0f), delayMillis = 40) }
        }
        compose.waitForIdle()
        assertTrue(coverLeft() < cover, "the cover follows the finger")
        assertEquals(artist, artistLeft())
        assertEquals(analyze, compose.onNodeWithContentDescription("Analyze").getBoundsInRoot().left)
        compose.onNodeWithText("Aphelion Drive").performTouchInput { up() }
        settle()
        assertEquals(cover, coverLeft())
    }

    /** A swipe that starts on the cover skips too. */
    @Test fun a_swipe_on_the_cover_skips() {
        show()
        compose.onNodeWithContentDescription("Cover").performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(-30f, 0f), delayMillis = 8) }
            up()
        }
        settle()
        assertEquals(1, nexts)
    }

    @Test fun swipes_one_after_another_all_come_back() {
        show()
        val home = coverLeft()
        repeat(4) { swipe(if (it % 2 == 0) -30f else 30f, 15) }
        settle()
        assertEquals(home, coverLeft())
    }
}
