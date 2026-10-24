package com.mpaulrenlund.audiofool.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.mpaulrenlund.audiofool.art.Thumbnails
import com.mpaulrenlund.audiofool.core.model.Album
import com.mpaulrenlund.audiofool.core.theme.ShadowLayer
import com.mpaulrenlund.audiofool.theme.AppTheme
import com.mpaulrenlund.audiofool.theme.LocalAppTheme
import com.mpaulrenlund.audiofool.theme.WindowBackground
import com.mpaulrenlund.audiofool.theme.tokenSurface

// The shared parts of spec 2: the screen frame, panel, panel header, rows, art,
// chip, round buttons, top bars and the search field. Everything is sized and
// coloured from tokens, read from the theme while composing.

/**
 * The system bars' (and keyboard's) insets the screens keep clear of. On the
 * phone they come from the window; ThemeLab provides fixed ones, the mockups'
 * 28 dp status row and 20 dp handle, so its pictures line up with design/screens.
 */
val LocalBarInsets = staticCompositionLocalOf<WindowInsets?> { null }

@Composable
fun barInsets(): WindowInsets = LocalBarInsets.current ?: WindowInsets.systemBars.union(WindowInsets.ime)

/** The cover thumbnails (64 dp lists, 72 dp album header); null draws placeholders (ThemeLab). */
class ArtSources(val list: Thumbnails, val header: Thumbnails)

val LocalArt = staticCompositionLocalOf<ArtSources?> { null }

/**
 * A whole screen: the window background with the Dark title glow behind the
 * status bar, the top bar, the content (a panel, given the remaining height), and
 * under it [bottom] (the mini player on library screens).
 */
@Composable
fun ScreenFrame(
    topBar: @Composable () -> Unit,
    bottom: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalAppTheme.current
    WindowBackground(t, Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(barInsets().only(WindowInsetsSides.Vertical))) {
            topBar()
            Column(Modifier.weight(1f).fillMaxWidth(), content = content)
            bottom?.invoke()
        }
    }
}

/**
 * A top bar row, `topBar.height` tall. Each screen gives its own padding and gap
 * (the mockups differ: the logo bar, the title bars, Search's bar).
 */
@Composable
fun TopBar(paddingStart: Dp, paddingEnd: Dp, gap: Dp, content: @Composable RowScope.() -> Unit) {
    val t = LocalAppTheme.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(t.dp("topBar.height"))
            .padding(start = paddingStart, end = paddingEnd),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A 48 dp touch area holding an icon, as the top bars' search button. */
@Composable
fun IconButton(icon: AppIcon, iconSize: Dp, colorPath: String, onClick: () -> Unit, strokePath: String = "icon.strokeOn24Grid") {
    val t = LocalAppTheme.current
    Box(
        Modifier.size(t.dp("topBar.iconButton")).tappable(CircleShape, onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, iconSize, colorPath, strokePath) }
}

/**
 * Shows a press: `state.hoverOverlay` over the [shape] while a finger is down, the
 * desktop's hover colour standing in for touch. Nothing when [onClick] is null.
 */
@Composable
fun Modifier.tappable(shape: Shape, onClick: (() -> Unit)?): Modifier {
    if (onClick == null) return this
    val t = LocalAppTheme.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val overlay = t.color("color.state.hoverOverlay")
    return this
        .clickable(interactionSource = source, indication = null, onClick = onClick)
        .drawWithContent {
            drawContent()
            if (pressed) drawOutline(shape.createOutline(size, layoutDirection, this), overlay)
        }
}

/** One line of text with "…", or [maxLines] lines (album titles: two). */
@Composable
fun Line(text: String, style: TextStyle, modifier: Modifier = Modifier, maxLines: Int = 1) {
    BasicText(text, modifier, style, overflow = TextOverflow.Ellipsis, maxLines = maxLines, softWrap = maxLines > 1)
}

/**
 * A panel (spec 2): `color.panel.bg`, a 1 dp `color.panel.border`,
 * `radius.surface` and `shadow.panelHighlight`, inset `panel.marginX` each side
 * and `panel.marginBottom` below. Its content is clipped to the corners.
 */
@Composable
fun PanelSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val t = LocalAppTheme.current
    val shape = RoundedCornerShape(t.radius("radius.surface"))
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = t.dp("panel.marginX"))
            .padding(bottom = t.dp("panel.marginBottom"))
            .tokenSurface(
                shape = shape,
                background = t.color("color.panel.bg"),
                shadows = t.shadow("shadow.panelHighlight"),
                border = t.color("color.panel.border"),
                borderWidth = t.dp("borderWidth"),
            )
            .clip(shape),
        content = content,
    )
}

/**
 * A panel header (spec 2): `panel.headerHeight`, a 1 dp `color.panel.divider`
 * under it (and over it, where a header follows a group, as Search's do), the
 * label uppercase, 600, letter-spaced, `color.text.muted`.
 */
@Composable
fun PanelHeader(label: String, dividerAbove: Boolean = false, action: (@Composable RowScope.() -> Unit)? = null) {
    val t = LocalAppTheme.current
    Column {
        if (dividerAbove) Divider(t)
        Row(
            Modifier
                .fillMaxWidth()
                .height(t.dp("panel.headerHeight") - t.dp("borderWidth") * (if (dividerAbove) 2 else 1))
                .padding(start = t.dp("panel.headerPaddingX"), end = t.dp(if (action != null) "panel.headerActionPaddingEnd" else "panel.headerPaddingX")),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Line(
                label.uppercase(),
                t.text("panel.headerTextSize", "color.text.muted", weight = 600, letterSpacingEm = t.number("panel.headerLetterSpacingEm")),
                Modifier.weight(1f),
            )
            action?.invoke(this)
        }
        Divider(t)
    }
}

/**
 * A panel with a header and a column of rows, for Settings and Statistics; a
 * [list] of rows is padded `panel.listPadding` (Music folders). The library lists
 * use [PanelSurface] with their own lazy list.
 */
@Composable
fun Panel(
    label: String,
    modifier: Modifier = Modifier,
    list: Boolean = false,
    headerAction: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalAppTheme.current
    PanelSurface(modifier) {
        PanelHeader(label, action = headerAction)
        Column(if (list) Modifier.padding(t.dp("panel.listPadding")) else Modifier, content = content)
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

/** How a list row is drawn (spec 2). */
enum class RowLook { Normal, Selected, NowPlaying }

/**
 * A list row (spec 2): `radius.row` corners; Selected on `state.selectionBg`,
 * Now playing on `state.nowPlayingBg`. A row whose music can't be reached (a
 * removed SD card) is drawn at `disabledOpacity`.
 */
@Composable
fun ListRow(
    height: Dp,
    paddingX: Dp,
    look: RowLook = RowLook.Normal,
    unavailable: Boolean = false,
    gap: Dp? = null,
    onClick: (() -> Unit)?,
    content: @Composable RowScope.() -> Unit,
) {
    val t = LocalAppTheme.current
    val shape = RoundedCornerShape(t.radius("radius.row"))
    val background = when (look) {
        RowLook.Normal -> Color.Transparent
        RowLook.Selected -> t.color("color.state.selectionBg")
        RowLook.NowPlaying -> t.color("color.state.nowPlayingBg")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind { if (background.alpha > 0f) drawOutline(shape.createOutline(size, layoutDirection, this), background) }
            .tappable(shape, onClick)
            .padding(horizontal = paddingX)
            .let { if (unavailable) it.alpha(t.number("disabledOpacity").toFloat()) else it },
        horizontalArrangement = if (gap != null) Arrangement.spacedBy(gap) else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * An album's cover in a square frame: a 1 dp [borderPath] border, the panel's
 * colour under it, and the desktop's placeholder note (`albums.placeholderIconSize`,
 * `text.muted`) when there is no cover. [header] picks the album header's larger
 * copy. Loaded off the main thread; a cover already in memory shows at once.
 */
@Composable
fun AlbumArt(
    album: Album?,
    size: Dp,
    borderPath: String,
    shadows: List<ShadowLayer> = emptyList(),
    header: Boolean = false,
) {
    val t = LocalAppTheme.current
    val sources = LocalArt.current
    val thumbnails = if (header) sources?.header else sources?.list
    val key = remember(album, thumbnails) { if (album != null && thumbnails != null) thumbnails.keyFor(album) else null }
    var entry by remember(key) { mutableStateOf(key?.let { thumbnails?.peek(it) }) }
    if (entry == null && key != null && album != null && thumbnails != null) {
        LaunchedEffect(key) { entry = thumbnails.load(album, key) }
    }
    val border = t.dp("borderWidth")
    Box(
        Modifier
            .size(size)
            .tokenSurface(RectangleShape, t.color("color.panel.bg"), shadows, t.color(borderPath), border),
        contentAlignment = Alignment.Center,
    ) {
        val image = entry?.image
        if (image != null) {
            Image(
                image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(border),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Medium,
            )
        } else {
            Icon(AppIcons.MUSIC_NOTE, t.desktopNumber("albums.placeholderIconSize").let { Dp(it.toFloat()) }, "color.text.muted")
        }
    }
}

/**
 * A round button (spec 2): a [face] dp circle in `color.control.face` with a 1 dp
 * `faceBorder` and `shadow.controlButton`, centred in a touch area. [play] is
 * Play/Pause: `shadow.playButton`, a 1 dp `control.playRing` ring and
 * `shadow.playGlow` (both invisible in Light).
 */
@Composable
fun RoundButton(
    face: Dp,
    targetWidth: Dp,
    targetHeight: Dp,
    onClick: () -> Unit,
    play: Boolean = false,
    content: @Composable () -> Unit,
) {
    val t = LocalAppTheme.current
    val shadows = if (play) {
        val ring = ShadowLayer(inset = false, offsetX = 0.0, offsetY = 0.0, blur = 0.0, spread = t.number("borderWidth"), color = t.themeColor("color.control.playRing"))
        t.shadow("shadow.playButton") + ring + t.shadow("shadow.playGlow")
    } else {
        t.shadow("shadow.controlButton")
    }
    Box(
        Modifier
            .size(targetWidth, targetHeight)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(face)
                .tokenSurface(CircleShape, t.color("color.control.face"), shadows, t.color("color.control.faceBorder"), t.dp("borderWidth")),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

/**
 * The search field (spec 4.1, 4.4): `panel.bg` with a 1 dp `panel.border`,
 * `radius.surface` and `shadow.searchInset`; the icon in `text.muted` and
 * "Search" in `text.placeholder`. On Artists it is a button that opens Search;
 * Search draws its focused form itself ([SearchBox]).
 */
@Composable
fun SearchFieldButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val t = LocalAppTheme.current
    val shape = RoundedCornerShape(t.radius("radius.surface"))
    Row(
        modifier
            .height(t.dp("searchField.height"))
            .tokenSurface(shape, t.color("color.panel.bg"), t.shadow("shadow.searchInset"), t.color("color.panel.border"), t.dp("borderWidth"))
            .tappable(shape, onClick)
            .padding(horizontal = t.dp("searchField.paddingStart")),
        horizontalArrangement = Arrangement.spacedBy(t.dp("searchField.iconGap")),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.SEARCH, t.dp("searchField.icon"), "color.text.muted", "icon.searchStrokeOn24Grid")
        Line("Search", t.text("searchField.textSize", "color.text.placeholder"))
    }
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
    // The mockups' min-height is the content's, inside the padding.
    Row(
        Modifier
            .fillMaxWidth()
            .tappable(RoundedCornerShape(t.radius("radius.row")), onClick)
            .padding(horizontal = t.dp("settings.rowPaddingX"), vertical = t.dp("settings.rowPaddingY"))
            .heightIn(min = t.dp("settings.rowMinHeight")),
        horizontalArrangement = Arrangement.spacedBy(t.dp("settings.rowGap")),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(t.dp("settings.lineGap"))) {
            BasicText(label, style = t.text("settings.labelSize", "color.text.primary"))
            if (value != null) {
                BasicText(value, style = t.text("settings.valueSize", "color.text.muted"))
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
    paddingX: Dp? = null,
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
            .tappable(shape, onClick)
            .padding(horizontal = if (width != null) Dp(0f) else paddingX ?: t.dp("settings.chipPaddingX")),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(t.dp("settings.dotGap"), Alignment.CenterHorizontally),
        content = content,
    )
}

@Composable
fun ChipText(text: String) {
    val t = LocalAppTheme.current
    Line(text, t.text("settings.chipTextSize", "color.text.status"))
}

/** A status dot (spec 4.5): `settings.dot` across in [colorPath], ringed 1 dp in `status.dotRing`. */
@Composable
fun StatusDot(colorPath: String) {
    val t = LocalAppTheme.current
    Box(
        Modifier
            .size(t.dp("settings.dot"))
            .tokenSurface(CircleShape, t.color(colorPath), border = t.color("color.status.dotRing"), borderWidth = t.dp("borderWidth")),
    )
}

/** An icon from [AppIcons], tinted with a colour token. */
@Composable
fun Icon(icon: AppIcon, size: Dp, colorPath: String, strokePath: String = "icon.strokeOn24Grid") {
    val t = LocalAppTheme.current
    val stroke = t.number(strokePath)
    val vector = remember(icon, stroke) { icon.vector(stroke) }
    Image(vector, contentDescription = null, modifier = Modifier.size(size), colorFilter = ColorFilter.tint(t.color(colorPath)))
}

/** An icon sized by its own grid (the Album table's 8 x 10 triangle). */
@Composable
fun Icon(icon: AppIcon, width: Dp, height: Dp, colorPath: String, strokePath: String) {
    val t = LocalAppTheme.current
    val stroke = t.number(strokePath)
    val vector = remember(icon, stroke) { icon.vector(stroke) }
    Image(vector, contentDescription = null, modifier = Modifier.size(width, height), colorFilter = ColorFilter.tint(t.color(colorPath)))
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
fun ChipButton(text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)?) {
    val t = LocalAppTheme.current
    Chip(modifier, paddingX = t.dp("settings.buttonPaddingX"), onClick = onClick) {
        Line(text, t.text("settings.buttonTextSize", "color.text.status"))
    }
}

@Composable
fun VerticalGap(height: Dp) = Spacer(Modifier.height(height))
