package com.mpaulrenlund.audiofool.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mpaulrenlund.audiofool.core.model.Track
import com.mpaulrenlund.audiofool.playback.NowPlaying
import com.mpaulrenlund.audiofool.playlists.Playlists
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

/** Artists' PLAYLISTS tab and a playlist's screen (2026-11-12, the user's request). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = ThemeLabTest.M21)
class PlaylistsTabTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val tokens by lazy { AppTokens.get(ApplicationProvider.getApplicationContext<android.app.Application>().assets) }
    private val opened = ArrayList<Route>()
    private val played = ArrayList<Pair<Track, List<Track>>>()
    private var shuffles = 0
    private val actions = AppActions(open = { opened += it }, playIn = { t, list -> played += t to list }, shuffleLibrary = { shuffles++ })

    private fun theme(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            AudioFoolTheme(AppTheme(true, tokens)) {
                CompositionLocalProvider(LocalBarInsets provides WindowInsets(top = 28.dp, bottom = 20.dp)) { content() }
            }
        }
        compose.waitForIdle()
    }

    private fun showArtists() = theme {
        ArtistsScreen(Browse(ThemeLabFixtures.library, emptyList(), NowPlaying()), actions, playlists = ThemeLabFixtures.playlistRows())
    }

    @Test fun the_tabs_switch_between_artists_and_playlists() {
        showArtists()
        compose.onNodeWithText("ARTISTS").assertIsSelected()
        compose.onNodeWithText("PLAYLISTS").assertIsNotSelected().performClick()
        compose.onNodeWithText("PLAYLISTS").assertIsSelected()
        compose.onNodeWithText("Liked").assertIsDisplayed()
        compose.onNodeWithText("Recently Added").assertIsDisplayed()
        // The shuffle button is the Artists tab's.
        compose.onNodeWithContentDescription("Shuffle library").assertDoesNotExist()

        compose.onNodeWithText("ARTISTS").performClick()
        compose.onNodeWithText("Liked").assertDoesNotExist()
        compose.onNodeWithContentDescription("Shuffle library").assertIsDisplayed()
    }

    @Test fun system_back_on_playlists_goes_back_to_artists() {
        showArtists()
        compose.onNodeWithText("PLAYLISTS").performClick()
        compose.onNodeWithText("Liked").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("ARTISTS").assertIsSelected()
        compose.onNodeWithText("Liked").assertDoesNotExist()
    }

    @Test fun a_playlist_row_opens_it() {
        showArtists()
        compose.onNodeWithText("PLAYLISTS").performClick()
        compose.onNodeWithText("Recently Added").performClick()
        assertEquals(listOf<Route>(Route.Playlist(Playlists.RECENT_ID)), opened)
    }

    @Test fun a_song_plays_the_playlist_from_it() {
        val liked = ThemeLabFixtures.likedPlaylist()
        theme { PlaylistScreen(liked, Browse(ThemeLabFixtures.library, emptyList(), NowPlaying()), actions) }
        compose.onNodeWithText("Memory Card").performClick()
        assertEquals(1, played.size)
        assertEquals("Memory Card", played[0].first.title)
        assertEquals(liked.tracks, played[0].second)
    }

    @Test fun an_empty_liked_says_how_to_fill_it() {
        theme { PlaylistScreen(ThemeLabFixtures.emptyLikedPlaylist(), Browse(ThemeLabFixtures.library, emptyList(), NowPlaying()), actions) }
        compose.onNodeWithText("No liked songs yet").assertIsDisplayed()
        compose.onNodeWithText("Tap the heart on Now Playing to add a song.").assertIsDisplayed()
    }
}
