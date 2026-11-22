package com.mpaulrenlund.audiofool.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mpaulrenlund.audiofool.theme.AppTheme
import com.mpaulrenlund.audiofool.theme.AppTokens
import com.mpaulrenlund.audiofool.theme.AudioFoolTheme
import com.mpaulrenlund.audiofool.theme.ThemeLabTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Settings' Bit-Perfect row (spec 4.5; the UI/UX review of 2026-11-22, finding 1): the chip
 * says "On" or "Off", and a screen reader finds one switch that says the same.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = ThemeLabTest.M21)
class SettingsBitPerfectTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val tokens by lazy { AppTokens.get(ApplicationProvider.getApplicationContext<android.app.Application>().assets) }

    private var bitPerfect by mutableStateOf(false)
    private var toggled = 0

    private fun show(greyed: Boolean = false) {
        compose.setContent {
            AudioFoolTheme(AppTheme(true, tokens)) {
                CompositionLocalProvider(LocalBarInsets provides WindowInsets(top = 28.dp, bottom = 20.dp)) {
                    SettingsScreen(
                        folders = emptyList(),
                        actions = SettingsActions(toggleBitPerfect = { toggled++; bitPerfect = !bitPerfect }),
                        readout = "Shanling UA2 Plus · 24-Bit · 96 kHz · Rate Matched",
                        bitPerfect = bitPerfect,
                        bitPerfectGreyed = greyed,
                    )
                }
            }
        }
    }

    private fun isSwitch(on: Boolean) =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch) and
            SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState(on)) and
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, if (on) "On" else "Off")

    @Test fun the_row_is_one_switch_that_says_off_then_on() {
        show()
        val row = compose.onNodeWithText("Bit-Perfect")
        row.assert(isSwitch(on = false)).assertIsEnabled()
        // The chip's word is the row's state, not a second thing to read.
        compose.onAllNodesWithText("Off").assertCountEquals(0)

        row.performClick()
        compose.waitForIdle()
        assertEquals(1, toggled)
        row.assert(isSwitch(on = true))
        compose.onAllNodesWithText("On").assertCountEquals(0)
    }

    @Test fun greyed_out_it_still_says_how_it_is_set_and_cannot_be_switched() {
        bitPerfect = true
        show(greyed = true)
        compose.onNodeWithText("Bit-Perfect").assert(isSwitch(on = true)).assertIsNotEnabled()
    }
}
