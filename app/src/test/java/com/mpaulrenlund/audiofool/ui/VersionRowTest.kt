package com.mpaulrenlund.audiofool.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
import com.mpaulrenlund.audiofool.updates.Updates
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * The menus' Version row (spec 4.1, 9; 2026-11-12, the user's call): a tap looks for a
 * newer build, and the menu closes. (It opened the releases page from 2026-11-10.)
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = ThemeLabTest.M21)
class VersionRowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val tokens by lazy { AppTokens.get(ApplicationProvider.getApplicationContext<android.app.Application>().assets) }

    @Test fun a_tap_on_version_checks_for_updates_and_closes_the_menu() {
        var checked = 0
        compose.setContent {
            AudioFoolTheme(AppTheme(true, tokens)) {
                CompositionLocalProvider(LocalBarInsets provides WindowInsets(top = 28.dp, bottom = 20.dp)) {
                    ArtistsScreen(Browse(ThemeLabFixtures.library, emptyList(), NowPlaying()), AppActions(checkForUpdates = { checked++ }), version = "0.14.1")
                }
            }
        }
        compose.onNodeWithContentDescription("Menu").performClick()
        // The logo menu keeps every row (2026-11-10: the cover menu lost four of them).
        for (row in listOf("Library Statistics", "Listening History", "Rescan Library", "Settings", "Theme")) compose.onNodeWithText(row).assertExists()
        compose.onNodeWithText("Version").performClick()
        compose.waitForIdle()
        assertEquals(1, checked)
        compose.onNodeWithText("Version").assertDoesNotExist()
    }

    // Spec 9: the note doesn't take the screen's taps (in a popup window, as first built, it did).
    @Test fun a_tap_on_the_note_goes_through_to_the_screen() {
        var tapped = 0
        compose.setContent {
            AudioFoolTheme(AppTheme(false, tokens)) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().clickable { tapped++ })
                    UpdateNote(Updates.Note.UpToDate, onGone = {}, Modifier.align(Alignment.Center))
                }
            }
        }
        compose.onNodeWithText("No new version available").assertExists()
        compose.onRoot().performTouchInput { click(center) }
        compose.waitForIdle()
        assertEquals(1, tapped)
    }
}
