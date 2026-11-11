package com.mpaulrenlund.audiofool.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mpaulrenlund.audiofool.theme.AppTheme
import com.mpaulrenlund.audiofool.theme.AppTokens
import com.mpaulrenlund.audiofool.theme.AudioFoolTheme
import com.mpaulrenlund.audiofool.theme.ThemeLabFixtures
import com.mpaulrenlund.audiofool.theme.ThemeLabTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The cover full screen's touches (spec 5.3). A square cover fitted to the S24's width
 * leaves the window's background above and below it; zoomed in, the cover covers that.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = ThemeLabTest.S24)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoverViewerTouchTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val tokens by lazy { AppTokens.get(ApplicationProvider.getApplicationContext<android.app.Application>().assets) }
    private val theme by lazy { AppTheme(true, tokens) }

    private fun show() {
        compose.setContent {
            AudioFoolTheme(theme) {
                CompositionLocalProvider(LocalBarInsets provides WindowInsets(top = 28.dp, bottom = 20.dp)) {
                    CoverViewerScreen(ThemeLabFixtures.nowPlayingTrack, ThemeLabFixtures.coverLandscape())
                }
            }
        }
        compose.waitForIdle()
    }

    /** The colour 60 px under the top of the cover's box, in the middle across. */
    private fun nearTheTop(): Int {
        val cover = compose.onNodeWithContentDescription("Cover").fetchSemanticsNode().boundsInRoot
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        return image.getPixel(cover.center.x.toInt(), cover.top.toInt() + 60)
    }

    private val background get() = theme.color("color.window.bg").toArgb()

    @Test fun fitted_the_background_shows_above_the_cover() {
        show()
        assertEquals(background, nearTheTop())
    }

    @Test fun a_double_tap_zooms_in_and_another_zooms_out() {
        show()
        val cover = compose.onNodeWithContentDescription("Cover")
        cover.performTouchInput { doubleClick(center) }
        compose.waitForIdle()
        assertNotEquals(background, nearTheTop(), "zoomed in, the cover reaches the top")
        cover.performTouchInput { doubleClick(center) }
        compose.waitForIdle()
        assertEquals(background, nearTheTop(), "back to the fitted size")
    }

    @Test fun a_pinch_zooms_in() {
        show()
        compose.onNodeWithContentDescription("Cover").performTouchInput {
            pinch(center - Offset(20f, 0f), center - Offset(400f, 0f), center + Offset(20f, 0f), center + Offset(400f, 0f), durationMillis = 400)
        }
        compose.waitForIdle()
        assertNotEquals(background, nearTheTop(), "zoomed in, the cover reaches the top")
    }
}
