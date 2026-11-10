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
 * Albums' shuffle button (spec 4.2, 2026-11-10): at the right of the ALBUMS header, as
 * Artists' is, for that artist. And Artists' bar since the same day: the search field
 * first, the logo at the right.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = ThemeLabTest.M21)
class AlbumsShuffleTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val tokens by lazy { AppTokens.get(ApplicationProvider.getApplicationContext<android.app.Application>().assets) }
    private val shuffled = ArrayList<String>()
    private val actions = AppActions(shuffleArtist = { shuffled += it })

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            AudioFoolTheme(AppTheme(true, tokens)) {
                CompositionLocalProvider(LocalBarInsets provides WindowInsets(top = 28.dp, bottom = 20.dp)) { content() }
            }
        }
        compose.waitForIdle()
    }

    private val browse get() = Browse(ThemeLabFixtures.library, emptyList(), NowPlaying())

    private fun dp(px: Float) = with(compose.density) { px.toDp().value }

    @Test fun a_tap_shuffles_that_artist() {
        show { AlbumsScreen("Aphelion Drive", browse, actions) }
        compose.onNodeWithContentDescription("Shuffle artist").assertIsDisplayed().performClick()
        assertEquals(listOf("Aphelion Drive"), shuffled)
    }

    @Test fun the_button_sits_at_the_far_right_of_the_header() {
        show { AlbumsScreen("Aphelion Drive", browse, actions) }
        val button = compose.onNodeWithContentDescription("Shuffle artist").fetchSemanticsNode().boundsInRoot
        val label = compose.onNodeWithText("ALBUMS").fetchSemanticsNode().boundsInRoot
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(button.center.y in label.top..label.bottom, "level with the label")
        // The panel's margin and the header's action padding (8 + 8 dp), as on Artists.
        assertEquals(16f, dp(screen.right - button.right), 1.5f)
    }

    @Test fun an_artist_the_library_does_not_hold_has_no_button() {
        show { AlbumsScreen("Nobody", browse, actions) }
        compose.onNodeWithContentDescription("Shuffle artist").assertDoesNotExist()
    }

    @Test fun the_artists_bar_has_the_search_field_first_and_the_logo_at_the_right() {
        show { ArtistsScreen(browse, actions) }
        val field = compose.onNodeWithText("Search").fetchSemanticsNode().boundsInRoot
        val logo = compose.onNodeWithContentDescription("Menu").fetchSemanticsNode().boundsInRoot
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(12f, dp(field.left), 0.5f, "the field from the left, as Search's")
        assertEquals(6f, dp(logo.left - field.right), 0.5f)
        assertEquals(4f, dp(screen.right - logo.right), 0.5f, "the logo's 48 dp touch area at the right")
    }
}
