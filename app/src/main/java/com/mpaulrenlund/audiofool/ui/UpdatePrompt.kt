package com.mpaulrenlund.audiofool.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import com.mpaulrenlund.audiofool.theme.LocalAppTheme
import com.mpaulrenlund.audiofool.theme.tokenSurface
import com.mpaulrenlund.audiofool.updates.Updates

/** What the update pop-up can ask for (spec 9). */
class UpdateActions(
    val update: () -> Unit = {},
    val cancel: () -> Unit = {},
    val openSettings: () -> Unit = {},
)

/** The update pop-up (spec 9) over whatever screen is showing; system back is Cancel. */
@Composable
fun UpdatePrompt(prompt: Updates.Prompt, actions: UpdateActions) {
    Dialog(onDismissRequest = actions.cancel) { UpdateBox(prompt, actions) }
}

/**
 * The pop-up's box, drawn as Edit Tags' "Unsaved Changes" question is: the title, the
 * text, then the buttons at the right, the first one the step forward. Update and Cancel
 * on the offer; Cancel alone while downloading or installing; Open Settings and Cancel
 * while Android needs the user's OK; Try Again and Cancel after a failure.
 */
@Composable
fun UpdateBox(prompt: Updates.Prompt, actions: UpdateActions) {
    val t = LocalAppTheme.current
    val shape = RoundedCornerShape(t.radius("radius.surface"))
    Column(
        Modifier
            .width(t.dp("update.promptWidth"))
            .tokenSurface(shape, t.color("color.panel.bg"), t.shadow("shadow.headerArt") + t.shadow("shadow.panelHighlight"), t.color("color.panel.border"), t.dp("borderWidth"))
            .padding(t.dp("update.promptPadding")),
        verticalArrangement = Arrangement.spacedBy(t.dp("update.promptGap")),
    ) {
        Line(Updates.TITLE, t.text("update.titleSize", "color.text.heading", weight = t.desktopNumber("dialog.emphasisWeight").toInt()))
        BasicText(Updates.message(prompt), style = t.text("update.textSize", "color.text.secondary"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(t.dp("update.buttonGap"), Alignment.End)) {
            when (prompt) {
                is Updates.Prompt.Offer -> ChipButton("Update", onClick = actions.update)
                is Updates.Prompt.NeedsPermission -> ChipButton("Open Settings", onClick = actions.openSettings)
                is Updates.Prompt.Failed -> ChipButton("Try Again", onClick = actions.update)
                is Updates.Prompt.Downloading, is Updates.Prompt.Installing -> {}
            }
            ChipButton("Cancel", onClick = actions.cancel)
        }
    }
}
