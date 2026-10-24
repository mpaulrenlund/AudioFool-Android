package com.mpaulrenlund.audiofool.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.mpaulrenlund.audiofool.settings.MusicFolder
import com.mpaulrenlund.audiofool.theme.LocalAppTheme

/** What Settings can ask for. */
class SettingsActions(
    val toggleTheme: () -> Unit = {},
    val rescan: () -> Unit = {},
    val addFolder: () -> Unit = {},
    val setIncluded: (String, Boolean) -> Unit = { _, _ -> },
)

/** A music folder as Settings lists it: "SD card · Music", its path, and whether it can be reached. */
class FolderLine(val folder: MusicFolder, val name: String, val unavailable: Boolean)

/**
 * Settings (spec 4.5): Appearance, Playback, Music folders and Last.fm.
 * Bit-Perfect and Last.fm are shown in their off state; sessions 4 and 5 wire
 * them up.
 */
@Composable
fun SettingsScreen(folders: List<FolderLine>, actions: SettingsActions) {
    val t = LocalAppTheme.current
    TitledScreen("Settings") {
        Panel("Appearance") {
            SettingRow("Theme", t.name, onClick = actions.toggleTheme) {
                Chip(width = t.dp("settings.themeChipWidth"), onClick = actions.toggleTheme) {
                    Icon(if (t.isDark) AppIcons.MOON else AppIcons.SUN, t.dp("settings.chipIcon"), "color.text.status")
                }
            }
        }
        Panel("Playback") {
            // The output readout (spec 6.3) comes with the output stage in session 3.
            SettingRow("Bit-Perfect", "Shared") {
                Chip {
                    StatusDot("color.status.bitPerfectOff")
                    ChipText("Bit-Perfect")
                }
            }
        }
        Panel("Music folders", list = true, headerAction = {
            IconButtonSmall(AppIcons.REFRESH, actions.rescan)
        }) {
            for (line in folders) FolderRow(line) { actions.setIncluded(line.folder.path, !line.folder.included) }
            ChipButton(
                "Add Folder…",
                Modifier.padding(
                    start = t.dp("settings.folderRowPaddingX"),
                    top = t.dp("settings.folderRowPaddingY"),
                    bottom = t.dp("panel.listPadding"),
                ),
                onClick = actions.addFolder,
            )
        }
        Panel("Last.fm") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = t.dp("settings.rowPaddingX"), vertical = t.dp("settings.rowPaddingY"))
                    .heightIn(min = t.dp("settings.rowMinHeight")),
                horizontalArrangement = Arrangement.spacedBy(t.dp("settings.rowGap")),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(t.dp("settings.dotGap")), verticalAlignment = Alignment.CenterVertically) {
                    StatusDot("color.status.lastFmOff")
                    Line("Last.fm: not connected", t.text("settings.statusTextSize", "color.text.primary"), maxLines = 2)
                }
                ChipButton("Connect", onClick = null)
            }
        }
    }
}

/**
 * A folder (spec 4.5): the checkbox, its name and its path. One that can't be
 * reached (a removed SD card) is drawn at `disabledOpacity`, and its path line
 * says so; its tracks are kept.
 */
@Composable
private fun FolderRow(line: FolderLine, onToggle: () -> Unit) {
    val t = LocalAppTheme.current
    Row(
        Modifier
            .fillMaxWidth()
            .tappable(RoundedCornerShape(t.radius("radius.row")), onToggle)
            .padding(horizontal = t.dp("settings.folderRowPaddingX"), vertical = t.dp("settings.folderRowPaddingY"))
            .heightIn(min = t.dp("settings.folderRowMinHeight"))
            .let { if (line.unavailable) it.alpha(t.number("disabledOpacity").toFloat()) else it },
        horizontalArrangement = Arrangement.spacedBy(t.dp("settings.rowGap")),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FolderCheckbox(line.folder.included)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(t.dp("settings.lineGap"))) {
            Line(line.name, t.text("settings.folderNameSize", "color.text.primary"))
            Line(
                if (line.unavailable) "Not available · ${line.folder.path}" else line.folder.path,
                t.text("settings.folderPathSize", "color.text.muted"),
            )
        }
    }
}

/** The panel header's small button (Music folders' refresh): 32 dp, a 16 dp icon in `text.muted`. */
@Composable
fun IconButtonSmall(icon: AppIcon, onClick: () -> Unit) {
    val t = LocalAppTheme.current
    androidx.compose.foundation.layout.Box(
        Modifier.size(t.dp("settings.headerActionButton")).tappable(CircleShape, onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, t.dp("settings.headerActionIcon"), "color.text.muted") }
}

/**
 * A screen with a title in its top bar (Settings, Statistics; 18/600,
 * `text.heading`) and panels `settings.sectionGap` apart that scroll.
 */
@Composable
fun TitledScreen(title: String, content: @Composable ColumnScope.() -> Unit) {
    val t = LocalAppTheme.current
    ScreenFrame(
        topBar = {
            TopBar(t.dp("topBar.paddingStart"), t.dp("topBar.paddingEnd"), t.dp("topBar.gap")) {
                Line(title, t.text("topBar.titleSize", "color.text.heading", weight = t.number("topBar.titleWeight").toInt()))
            }
        },
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(t.dp("settings.sectionGap") - t.dp("panel.marginBottom")),
        ) {
            content()
            Spacer(Modifier)
        }
    }
}
