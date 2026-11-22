package com.mpaulrenlund.audiofool.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mpaulrenlund.audiofool.lastfm.LastFm
import com.mpaulrenlund.audiofool.theme.AppTheme
import com.mpaulrenlund.audiofool.theme.AppTokens
import com.mpaulrenlund.audiofool.theme.AudioFoolTheme
import com.mpaulrenlund.audiofool.theme.ThemeLabTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Last.fm's Connect (spec 4.5; the UI/UX review of 2026-11-22, finding 7): it can't be pressed,
 * and is drawn faded (the pictures), until the username and password are both filled in,
 * and while the sign-in is being sent.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = ThemeLabTest.M21)
class LastFmConnectButtonTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val tokens by lazy { AppTokens.get(ApplicationProvider.getApplicationContext<android.app.Application>().assets) }

    private var sent: Pair<String, String>? = null

    private fun show(status: LastFm.Status) {
        compose.setContent {
            AudioFoolTheme(AppTheme(true, tokens)) {
                CompositionLocalProvider(LocalBarInsets provides WindowInsets(top = 28.dp, bottom = 20.dp)) {
                    SettingsScreen(
                        folders = emptyList(),
                        actions = SettingsActions(),
                        lastFm = status,
                        lastFmActions = LastFmActions(connect = { _, _, user, password -> sent = user to password }),
                    )
                }
            }
        }
    }

    @Test fun connect_can_be_pressed_once_both_fields_are_filled() {
        show(LastFm.Status(signingIn = true))
        val connect = compose.onNodeWithText("Connect")
        connect.assertHasNoClickAction()

        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("questinnn")
        connect.assertHasNoClickAction()
        fields[1].performTextInput("hunter2")
        // The form ends below the M21's screen: scrolled to, as a finger would.
        connect.assertHasClickAction().performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("questinnn" to "hunter2", sent)
    }

    @Test fun connect_cannot_be_pressed_while_signing_in() {
        show(LastFm.Status(signingIn = true, sending = true, userName = "questinnn"))
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("hunter2")
        compose.onNodeWithText("Connect").assertHasNoClickAction()
        assertNull(sent)
    }
}
