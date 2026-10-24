package com.mpaulrenlund.audiofool.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.mpaulrenlund.audiofool.theme.AppTheme
import com.mpaulrenlund.audiofool.theme.LocalAppTheme
import com.mpaulrenlund.audiofool.theme.WindowBackground
import com.mpaulrenlund.audiofool.theme.tokenSurface

/**
 * The ThemeLab token sheet: every colour token as a swatch, every mobile text
 * size, and the shared surfaces with their shadows. The screenshot tests render
 * each page in both themes at both device sizes; the debug screen shows it on
 * the phone. Sizes come from the tokens, like any screen.
 */
enum class SheetPage { Colours1, Colours2, Colours3, Surfaces }

/** Swatches per page: fifteen rows of two fit the M21's 667 dp. */
private const val SWATCHES_PER_PAGE = 30

@Composable
fun TokenSheet(page: SheetPage, modifier: Modifier = Modifier) {
    val t = LocalAppTheme.current
    WindowBackground(t, modifier.fillMaxSize()) {
        Column(Modifier.statusBarsPadding()) {
            Title("ThemeLab · ${t.name} · ${page.name}")
            when (page) {
                SheetPage.Surfaces -> Surfaces(t)
                else -> Swatches((desktopColours(t) + mobileColours(t)).chunked(SWATCHES_PER_PAGE).getOrElse(page.ordinal) { emptyList() })
            }
        }
    }
}

private fun desktopColours(t: AppTheme) = (if (t.isDark) t.tokens.dark else t.tokens.light).colors.keys.toList()
private fun mobileColours(t: AppTheme) = t.tokens.mobile.colors.keys.map { it.substringBeforeLast('.') }.distinct().map { "mobile:$it" }

@Composable
private fun Title(text: String) {
    val t = LocalAppTheme.current
    BasicText(
        text,
        style = t.text("topBar.titleSize", "color.text.heading", weight = 600),
        modifier = Modifier.padding(horizontal = t.dp("topBar.paddingStart"), vertical = t.dp("panel.listPadding")),
        maxLines = 1,
    )
}

/** Two columns of swatches: the colour, then its path. */
@Composable
private fun Swatches(keys: List<String>) {
    val t = LocalAppTheme.current
    val rows = keys.chunked(2)
    Column(Modifier.padding(horizontal = t.dp("panel.marginX"))) {
        for (row in rows) {
            Row(Modifier.fillMaxWidth()) {
                for (key in row) Swatch(key, Modifier.weight(1f))
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Swatch(key: String, modifier: Modifier) {
    val t = LocalAppTheme.current
    val color = if (key.startsWith("mobile:")) t.mobileColor(key.removePrefix("mobile:")) else t.color(key)
    Row(modifier.height(t.dp("settings.chipHeight")), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(t.dp("settings.checkbox"))
                .tokenSurface(RoundedCornerShape(t.radius("radius.row")), color, border = t.color("color.panel.border"), borderWidth = t.dp("borderWidth")),
        )
        Spacer(Modifier.width(t.dp("settings.dotGap")))
        BasicText(
            key.removePrefix("color.").removePrefix("mobile:color."),
            style = t.text("settings.folderPathSize", "color.text.secondary"),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Surfaces(t: AppTheme) {
    Column(verticalArrangement = Arrangement.spacedBy(t.dp("settings.sectionGap"))) {
        Panel("Panel · type sizes") {
            val sizes = t.tokens.mobile.numbers.keys.filter { it.endsWith("TextSize") || it.endsWith("titleSize") }
                .filter { it.startsWith("settings.") || it.startsWith("topBar.") || it.startsWith("panel.") || it.startsWith("searchField.") }
            for (size in sizes.take(8)) {
                BasicText(
                    "${size.substringAfter('.')} · Björk 0123456789 1:11",
                    style = t.text(size, "color.text.primary"),
                    modifier = Modifier.padding(horizontal = t.dp("settings.rowPaddingX")),
                    maxLines = 1,
                )
            }
        }
        Panel("Rows and chips") {
            SettingRow("Theme", t.name) {
                Chip(width = t.dp("settings.themeChipWidth")) {
                    Icon(if (t.isDark) AppIcons.MOON else AppIcons.SUN, t.dp("settings.chipIcon"), "color.text.status")
                }
            }
            Row(
                Modifier.padding(horizontal = t.dp("settings.rowPaddingX")),
                horizontalArrangement = Arrangement.spacedBy(t.dp("settings.dotGap")),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FolderCheckbox(true)
                FolderCheckbox(false)
                ChipButton("Add Folder…") {}
                Chip { ChipText("Bit-Perfect") }
            }
        }
        Panel("Buttons and art") {
            Row(
                Modifier.padding(horizontal = t.dp("settings.rowPaddingX"), vertical = t.dp("panel.listPadding")),
                horizontalArrangement = Arrangement.spacedBy(t.dp("nowPlaying.transportGap.min")),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // A round button: control.face, control.faceBorder, shadow.controlButton.
                Box(
                    Modifier.size(t.dp("miniPlayer.skipButton")).tokenSurface(
                        CircleShape, t.color("color.control.face"), t.shadow("shadow.controlButton"),
                        t.color("color.control.faceBorder"), t.dp("borderWidth"),
                    ),
                )
                // Play/Pause: shadow.playButton and shadow.playGlow, with the playRing border.
                Box(
                    Modifier.size(t.dp("miniPlayer.playButton")).tokenSurface(
                        CircleShape, t.color("color.control.face"), t.shadow("shadow.playGlow") + t.shadow("shadow.playButton"),
                        t.color("color.control.playRing"), t.dp("borderWidth"),
                    ),
                )
                // Album art: art.border and shadow.headerArt.
                Box(
                    Modifier.size(t.dp("album.art")).tokenSurface(
                        RoundedCornerShape(t.radius("radius.art")), t.color("color.state.nowPlayingBg"),
                        t.shadow("shadow.headerArt"), t.color("color.art.border"), t.dp("borderWidth"),
                    ),
                )
                // The search field: state.searchFocusBg with shadow.searchInset.
                Box(
                    Modifier.weight(1f).height(t.dp("searchField.height")).tokenSurface(
                        RoundedCornerShape(t.radius("radius.surface")), t.color("color.state.searchFocusBg"),
                        t.shadow("shadow.searchInset"), t.color("color.state.searchFocusRing"), t.dp("searchField.focusRingWidth"),
                    ),
                )
            }
        }
    }
}
