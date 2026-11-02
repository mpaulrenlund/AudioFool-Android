package com.mpaulrenlund.audiofool.theme

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.mpaulrenlund.audiofool.core.playback.OutputPath
import com.mpaulrenlund.audiofool.core.playback.OutputReadout
import com.mpaulrenlund.audiofool.core.playback.OutputRoute
import com.mpaulrenlund.audiofool.playback.NowPlaying
import com.mpaulrenlund.audiofool.ui.AppActions
import com.mpaulrenlund.audiofool.ui.ArtistsScreen
import com.mpaulrenlund.audiofool.ui.Browse
import com.mpaulrenlund.audiofool.ui.LocalBarInsets
import com.mpaulrenlund.audiofool.ui.NowPlayingActions
import com.mpaulrenlund.audiofool.ui.NowPlayingScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ThemeLab's picture of the logo menu (spec 4.1). The menu is a popup, a window of
 * its own, so the whole screen is captured, once per test (a test sets its content once).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeLabMenuTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val tokens by lazy { AppTokens.get(ApplicationProvider.getApplicationContext<android.app.Application>().assets) }

    private fun menu(device: String, dark: Boolean) {
        val browse = Browse(ThemeLabFixtures.library, emptyList(), NowPlaying().apply { showForPictures(ThemeLabFixtures.playing) })
        compose.setContent {
            AudioFoolTheme(AppTheme(dark, tokens)) {
                CompositionLocalProvider(LocalBarInsets provides WindowInsets(top = 28.dp, bottom = 20.dp)) {
                    ArtistsScreen(browse, AppActions(), initialMenuOpen = true, version = "0.7.0")
                }
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("screenshots/$device-${if (dark) "dark" else "light"}-menu.png")
    }

    @Test @Config(qualifiers = ThemeLabTest.S24) fun menu_s24_dark() = menu("s24", dark = true)
    @Test @Config(qualifiers = ThemeLabTest.S24) fun menu_s24_light() = menu("s24", dark = false)
    @Test @Config(qualifiers = ThemeLabTest.M21) fun menu_m21_dark() = menu("m21", dark = true)
    @Test @Config(qualifiers = ThemeLabTest.M21) fun menu_m21_light() = menu("m21", dark = false)

    /** Now Playing's cover menu (0.6.0), opened in the art's middle on the mockup's song. */
    private fun artMenu(device: String, dark: Boolean) {
        val nowPlaying = NowPlaying().apply {
            showForPictures(ThemeLabFixtures.nowPlayingTrack, playing = true, index = 2, size = 10, positionMs = ThemeLabFixtures.NOW_PLAYING_POSITION_MS, waveform = ThemeLabFixtures.mockupWaveform,
                readout = OutputReadout.describe(OutputRoute.Speaker.label, OutputPath.Shared, 48000, 0, 96000, false))
        }
        compose.setContent {
            AudioFoolTheme(AppTheme(dark, tokens)) {
                CompositionLocalProvider(LocalBarInsets provides WindowInsets(top = 28.dp, bottom = 20.dp)) {
                    NowPlayingScreen(nowPlaying, NowPlayingActions(), artMenuForPictures = "1200 × 1200")
                }
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("screenshots/$device-${if (dark) "dark" else "light"}-art-menu.png")
    }

    @Test @Config(qualifiers = ThemeLabTest.S24) fun art_menu_s24_dark() = artMenu("s24", dark = true)
    @Test @Config(qualifiers = ThemeLabTest.S24) fun art_menu_s24_light() = artMenu("s24", dark = false)
    @Test @Config(qualifiers = ThemeLabTest.M21) fun art_menu_m21_dark() = artMenu("m21", dark = true)
    @Test @Config(qualifiers = ThemeLabTest.M21) fun art_menu_m21_light() = artMenu("m21", dark = false)
}
