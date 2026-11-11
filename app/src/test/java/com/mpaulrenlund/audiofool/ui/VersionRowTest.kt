package com.mpaulrenlund.audiofool.ui

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The menus' Version row (spec 4.1, 2026-11-10, the user's call): a tap opens
 * AudioFool's releases page in the default browser, and the menu closes.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = ThemeLabTest.M21)
class VersionRowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val tokens by lazy { AppTokens.get(ApplicationProvider.getApplicationContext<android.app.Application>().assets) }

    @Test fun a_tap_on_version_opens_the_releases_and_closes_the_menu() {
        var opened = 0
        compose.setContent {
            AudioFoolTheme(AppTheme(true, tokens)) {
                CompositionLocalProvider(LocalBarInsets provides WindowInsets(top = 28.dp, bottom = 20.dp)) {
                    ArtistsScreen(Browse(ThemeLabFixtures.library, emptyList(), NowPlaying()), AppActions(openReleases = { opened++ }), version = "0.14.1")
                }
            }
        }
        compose.onNodeWithContentDescription("Menu").performClick()
        // The logo menu keeps every row (2026-11-10: the cover menu lost four of them).
        for (row in listOf("Library Statistics", "Listening History", "Rescan Library", "Settings", "Theme")) compose.onNodeWithText(row).assertExists()
        compose.onNodeWithText("Version").performClick()
        compose.waitForIdle()
        assertEquals(1, opened)
        compose.onNodeWithText("Version").assertDoesNotExist()
    }

    private fun addApp(pkg: String, host: String? = null, default: Boolean = true) {
        val packages = shadowOf(compose.activity.packageManager)
        val component = ComponentName(pkg, "$pkg.Main")
        packages.addActivityIfNotPresent(component)
        packages.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                if (default) addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme("https")
                host?.let { addDataAuthority(it, null) }
            },
        )
    }

    @Test fun the_page_goes_to_the_default_browser_not_an_app_that_claims_github() {
        addApp("com.example.browser")
        addApp("com.github.android", host = "github.com")
        openInBrowser(compose.activity, RELEASES_URL)
        val started = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(Uri.parse("https://github.com/mpaulrenlund/AudioFool-Android/releases"), started.data)
        assertEquals("com.example.browser", started.`package`)
    }

    @Test fun with_no_browser_known_android_chooses() {
        openInBrowser(compose.activity, RELEASES_URL)
        val started = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Uri.parse(RELEASES_URL), started.data)
        assertNull(started.`package`)
    }
}
