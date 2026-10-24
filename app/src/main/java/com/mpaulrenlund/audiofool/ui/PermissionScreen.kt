package com.mpaulrenlund.audiofool.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mpaulrenlund.audiofool.theme.LocalAppTheme
import com.mpaulrenlund.audiofool.theme.WindowBackground

/**
 * Asks for all-files access (spec 1), which the system grants on its own page.
 * The app comes back here and carries on once it is on.
 */
@Composable
fun PermissionScreen(onAllow: () -> Unit) {
    val t = LocalAppTheme.current
    WindowBackground(t, Modifier.fillMaxSize()) {
        Column(Modifier.statusBarsPadding(), verticalArrangement = Arrangement.spacedBy(t.dp("settings.sectionGap"))) {
            BasicText(
                "AudioFool",
                style = t.text("topBar.titleSize", "color.text.heading", weight = 600),
                modifier = Modifier.padding(horizontal = t.dp("topBar.paddingStart"), vertical = t.dp("panel.listPadding")),
            )
            Panel("Music files") {
                SettingRow("Allow access to all files", "AudioFool reads your music folders on the phone and the SD card by their paths.")
                Row(Modifier.padding(horizontal = t.dp("settings.rowPaddingX"), vertical = t.dp("settings.rowPaddingY"))) {
                    ChipButton("Allow…", onAllow)
                }
            }
        }
    }
}
