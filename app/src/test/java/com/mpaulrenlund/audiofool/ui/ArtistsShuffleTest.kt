package com.mpaulrenlund.audiofool.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mpaulrenlund.audiofool.core.library.MusicLibrary
import com.mpaulrenlund.audiofool.core.library.RescanOutcome
import com.mpaulrenlund.audiofool.library.LibraryState
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

/** Artists' shuffle button (spec 4.1, 0.9.0): at the right of the ARTISTS header. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = ThemeLabTest.M21)
class ArtistsShuffleTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val tokens by lazy { AppTokens.get(ApplicationProvider.getApplicationContext<android.app.Application>().assets) }
    private var shuffles = 0

    private fun show(library: MusicLibrary = ThemeLabFixtures.library, scanText: String? = null) {
        val actions = AppActions(shuffleLibrary = { shuffles++ })
        compose.setContent {
            AudioFoolTheme(AppTheme(true, tokens)) {
                CompositionLocalProvider(LocalBarInsets provides WindowInsets(top = 28.dp, bottom = 20.dp)) {
                    ArtistsScreen(Browse(library, emptyList(), NowPlaying()), actions, scanText = scanText)
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun a_tap_shuffles_the_library() {
        show()
        compose.onNodeWithContentDescription("Shuffle library").assertIsDisplayed().performClick()
        assertEquals(1, shuffles)
    }

    @Test fun the_button_sits_at_the_far_right_of_the_header() {
        show()
        val button = compose.onNodeWithContentDescription("Shuffle library").fetchSemanticsNode().boundsInRoot
        val label = compose.onNodeWithText("ARTISTS").fetchSemanticsNode().boundsInRoot
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(button.center.y in label.top..label.bottom, "level with the label")
        // From the screen's edge: the panel's margin and the header's action padding (8 + 8 dp), as Settings' refresh button.
        val fromEdge = with(compose.density) { (screen.right - button.right).toDp().value }
        assertEquals(16f, fromEdge, 1.5f)
    }

    @Test fun a_long_scan_note_keeps_the_button() {
        show(scanText = RescanOutcome(1234, 56, 7, 1).text)
        compose.onNodeWithContentDescription("Shuffle library").assertIsDisplayed()
        val button = compose.onNodeWithContentDescription("Shuffle library").fetchSemanticsNode().boundsInRoot
        val note = compose.onNodeWithText(RescanOutcome(1234, 56, 7, 1).text).fetchSemanticsNode().boundsInRoot
        assertTrue(note.right <= button.left, "the note ends before the button")
    }

    @Test fun nothing_to_shuffle_no_button() {
        show(library = MusicLibrary.Empty)
        compose.onNodeWithContentDescription("Shuffle library").assertDoesNotExist()
    }
}
