package com.mpaulrenlund.audiofool.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.mpaulrenlund.audiofool.theme.AppTheme
import com.mpaulrenlund.audiofool.theme.LocalAppTheme
import com.mpaulrenlund.audiofool.theme.tokenSurface

// Session 1's versions of the shared parts in spec 2, enough for the folder list
// and the debug screen. Session 2 builds them out (rows, round buttons, glow).

/**
 * A panel (spec 2): `color.panel.bg`, a 1 dp `color.panel.border`,
 * `radius.surface`, `shadow.panelHighlight`, and a header row with a divider.
 */
@Composable
fun Panel(
    label: String,
    modifier: Modifier = Modifier,
    headerAction: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalAppTheme.current
    val shape = RoundedCornerShape(t.radius("radius.surface"))
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = t.dp("panel.marginX"))
            .tokenSurface(
                shape = shape,
                background = t.color("color.panel.bg"),
                shadows = t.shadow("shadow.panelHighlight"),
                border = t.color("color.panel.border"),
                borderWidth = t.dp("borderWidth"),
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(t.dp("panel.headerHeight"))
                .padding(horizontal = t.dp("panel.headerPaddingX")),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                label.uppercase(),
                style = t.text("panel.headerTextSize", "color.text.muted", weight = 600,
                    letterSpacingEm = t.number("panel.headerLetterSpacingEm")),
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            headerAction?.invoke(this)
        }
        Divider(t)
        Column(Modifier.padding(vertical = t.dp("panel.listPadding")), content = content)
    }
}

@Composable
fun Divider(t: AppTheme) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(t.dp("borderWidth"))
            .drawBehind { drawRect(t.color("color.panel.divider")) },
    )
}

/** A settings-style row (spec 4.5): a label with an optional line under it, and something on the right. */
@Composable
fun SettingRow(
    label: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val t = LocalAppTheme.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = t.dp("settings.rowMinHeight"))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = t.dp("settings.rowPaddingX"), vertical = t.dp("settings.rowPaddingY")),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(label, style = t.text("settings.labelSize", "color.text.primary"), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (value != null) {
                BasicText(value, style = t.text("settings.valueSize", "color.text.secondary"), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
}

/** A chip (spec 2): `settings.chipHeight`, `color.status.chipBg`, a 1 dp `chipBorder`, `radius.chip`. */
@Composable
fun Chip(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val t = LocalAppTheme.current
    val shape = RoundedCornerShape(t.radius("radius.chip"))
    Row(
        modifier
            .height(t.dp("settings.chipHeight"))
            .let { if (width != null) it.width(width) else it }
            .tokenSurface(shape, t.color("color.status.chipBg"), border = t.color("color.status.chipBorder"), borderWidth = t.dp("borderWidth"))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = t.dp("settings.chipPaddingX")),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content,
    )
}

@Composable
fun ChipText(text: String) {
    val t = LocalAppTheme.current
    BasicText(text, style = t.text("settings.chipTextSize", "color.text.status"), maxLines = 1)
}

/** An icon from [AppIcons], tinted with a colour token. */
@Composable
fun Icon(pathData: String, size: Dp, colorPath: String, strokePath: String = "icon.strokeOn24Grid") {
    val t = LocalAppTheme.current
    val stroke = t.number(strokePath)
    val vector = remember(pathData, stroke) { AppIcons.stroked(pathData, stroke) }
    Image(vector, contentDescription = null, modifier = Modifier.size(size), colorFilter = ColorFilter.tint(t.color(colorPath)))
}

/**
 * The folder checkbox (spec 4.5): filled `accent.playback` with the check in
 * `panel.bg` when ticked; an empty box with the panel's border when not.
 */
@Composable
fun FolderCheckbox(checked: Boolean) {
    val t = LocalAppTheme.current
    val shape = RoundedCornerShape(t.radius("radius.row"))
    Box(
        Modifier
            .size(t.dp("settings.checkbox"))
            .tokenSurface(
                shape,
                background = if (checked) t.color("color.accent.playback") else t.color("color.control.face"),
                border = if (checked) t.color("color.accent.playback") else t.color("color.control.faceBorder"),
                borderWidth = t.dp("borderWidth"),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(AppIcons.CHECK, t.dp("settings.checkIcon"), "color.panel.bg", "settings.checkStrokeOn24Grid")
    }
}

/** A text button in a chip, as "Add Folder…" and "Connect" are drawn (spec 4.5). */
@Composable
fun ChipButton(text: String, onClick: () -> Unit) {
    val t = LocalAppTheme.current
    Chip(onClick = onClick) {
        BasicText(text, style = t.text("settings.buttonTextSize", "color.text.status", weight = 600), maxLines = 1)
    }
}

@Composable
fun VerticalGap(height: Dp) = Spacer(Modifier.height(height))
