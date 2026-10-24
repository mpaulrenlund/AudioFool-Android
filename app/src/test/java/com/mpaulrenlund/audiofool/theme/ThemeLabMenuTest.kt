package com.mpaulrenlund.audiofool.theme

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.mpaulrenlund.audiofool.playback.NowPlaying
import com.mpaulrenlund.audiofool.ui.AppActions
import com.mpaulrenlund.audiofool.ui.ArtistsScreen
import com.mpaulrenlund.audiofool.ui.Browse
import com.mpaulrenlund.audiofool.ui.LocalBarInsets
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
                    ArtistsScreen(browse, AppActions(), initialMenuOpen = true)
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
}
