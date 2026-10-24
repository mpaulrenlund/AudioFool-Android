package com.mpaulrenlund.audiofool.theme

import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.mpaulrenlund.audiofool.ui.SheetPage
import com.mpaulrenlund.audiofool.ui.TokenSheet
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ThemeLab: renders composables in both themes at both device sizes, the
 * S24 Ultra (384 x 832 dp) and the M21 (375 x 667 dp). `recordRoborazziDebug`
 * writes the pictures to app/screenshots/; `verifyRoborazziDebug` compares
 * against them. Starts with the token sheet; session 2 adds every screen.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeLabTest {
    private val tokens by lazy { AppTokens.get(ApplicationProvider.getApplicationContext<android.app.Application>().assets) }

    private fun shoot(device: String, name: String, content: @Composable () -> Unit) {
        for (dark in listOf(true, false)) {
            val themeName = if (dark) "dark" else "light"
            captureRoboImage("screenshots/$device-$themeName-$name.png") {
                AudioFoolTheme(AppTheme(dark, tokens)) { content() }
            }
        }
    }

    private fun tokenSheet(device: String) {
        for (page in SheetPage.entries) shoot(device, "tokens-${page.name.lowercase()}") { TokenSheet(page) }
    }

    @Test
    @Config(qualifiers = S24)
    fun token_sheet_s24() = tokenSheet("s24")

    @Test
    @Config(qualifiers = M21)
    fun token_sheet_m21() = tokenSheet("m21")

    companion object {
        /** Galaxy S24 Ultra: 1440 x 3120 px, shown at 384 x 832 dp. */
        const val S24 = "w384dp-h832dp-port-xxhdpi"

        /** FiiO M21: 750 x 1334 px, shown at 375 x 667 dp. */
        const val M21 = "w375dp-h667dp-port-xhdpi"
    }
}
