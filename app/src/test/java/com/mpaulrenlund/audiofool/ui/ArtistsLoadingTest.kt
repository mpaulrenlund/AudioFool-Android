package com.mpaulrenlund.audiofool.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mpaulrenlund.audiofool.core.library.LibraryScanner
import com.mpaulrenlund.audiofool.core.library.MusicLibrary
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
 * Artists before the library is read (the UI/UX review of 2026-11-22, finding 8): at a cold
 * start it said "No music yet" until the cache was read. Now it says it is loading, and
 * "No music yet" only of a library that has been read and is empty.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = ThemeLabTest.M21)
class ArtistsLoadingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val tokens by lazy { AppTokens.get(ApplicationProvider.getApplicationContext<android.app.Application>().assets) }

    private var browse by mutableStateOf(Browse(MusicLibrary.Empty, emptyList(), NowPlaying(), loaded = false))

    private fun show() {
        compose.setContent {
            AudioFoolTheme(AppTheme(true, tokens)) {
                CompositionLocalProvider(LocalBarInsets provides WindowInsets(top = 28.dp, bottom = 20.dp)) {
                    ArtistsScreen(browse, AppActions())
                }
            }
        }
    }

    @Test fun before_the_library_is_read_it_says_loading_then_lists_the_artists() {
        show()
        compose.onNodeWithText(LIBRARY_LOADING).assertExists()
        compose.onAllNodesWithText("No music yet").assertCountEquals(0)

        val library = LibraryScanner.build(ThemeLabFixtures.tracks.take(20))
        browse = Browse(library, emptyList(), NowPlaying())
        compose.waitForIdle()
        compose.onAllNodesWithText(LIBRARY_LOADING).assertCountEquals(0)
        compose.onNodeWithText(library.artists.first().name).assertExists()
    }

    @Test fun a_library_read_and_empty_still_says_no_music_yet() {
        browse = Browse(MusicLibrary.Empty, emptyList(), NowPlaying())
        show()
        compose.onNodeWithText("No music yet").assertExists()
        compose.onAllNodesWithText(LIBRARY_LOADING).assertCountEquals(0)
    }
}
