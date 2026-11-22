package com.mpaulrenlund.audiofool.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mpaulrenlund.audiofool.core.library.LibraryScanner
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

/**
 * Search (spec 4.4; the UI/UX review of 2026-11-22, finding 6): the desktop's "No matches"
 * when nothing is found, the keyboard's Search key putting the keyboard away, and the ×
 * clearing the field with the next search ready to type.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = ThemeLabTest.M21)
class SearchNoMatchesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val tokens by lazy { AppTokens.get(ApplicationProvider.getApplicationContext<android.app.Application>().assets) }
    private val library = LibraryScanner.build(ThemeLabFixtures.tracks)

    private fun show(initialQuery: String = "", loaded: Boolean = true) {
        compose.setContent {
            AudioFoolTheme(AppTheme(true, tokens)) {
                CompositionLocalProvider(LocalBarInsets provides WindowInsets(top = 28.dp, bottom = 20.dp)) {
                    SearchScreen(Browse(library, emptyList(), NowPlaying(), loaded = loaded), AppActions(), initialQuery)
                }
            }
        }
        compose.waitForIdle()
    }

    private val field get() = compose.onNode(hasSetTextAction())

    @Test fun nothing_found_says_no_matches() {
        show()
        compose.onAllNodesWithText("No matches").assertCountEquals(0)

        field.performTextInput("zzqxzzqx")
        compose.waitForIdle()
        compose.onNodeWithText("No matches").assertExists()
        compose.onNodeWithText("Nothing in the library matches.").assertExists()
    }

    @Test fun a_search_that_finds_something_shows_no_note() {
        show(initialQuery = library.artists.first().name)
        compose.onAllNodesWithText("No matches").assertCountEquals(0)
        compose.onNodeWithText("Artists", ignoreCase = true).assertExists()
    }

    @Test fun no_note_before_the_library_is_read() {
        show(initialQuery = "zzqxzzqx", loaded = false)
        compose.onAllNodesWithText("No matches").assertCountEquals(0)
    }

    @Test fun the_search_key_puts_the_keyboard_away_and_the_clear_button_brings_it_back() {
        show()
        field.assertIsFocused()
        field.performTextInput("polygon")
        field.performImeAction()
        compose.waitForIdle()
        field.assertIsNotFocused()

        compose.onNodeWithContentDescription("Clear").performClick()
        compose.waitForIdle()
        field.assertIsFocused()
        // Empty, the field shows its "Search" placeholder: what's typed is checked, not what's shown.
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }
}
