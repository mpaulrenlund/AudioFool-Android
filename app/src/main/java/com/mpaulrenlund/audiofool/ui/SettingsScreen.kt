package com.mpaulrenlund.audiofool.ui

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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.mpaulrenlund.audiofool.core.scrobbling.ScrobblerState
import com.mpaulrenlund.audiofool.lastfm.LastFm
import com.mpaulrenlund.audiofool.settings.MusicFolder
import com.mpaulrenlund.audiofool.theme.LocalAppTheme
import com.mpaulrenlund.audiofool.theme.tokenSurface

/** What Settings can ask for. */
class SettingsActions(
    val toggleTheme: () -> Unit = {},
    val rescan: () -> Unit = {},
    val addFolder: () -> Unit = {},
    val setIncluded: (String, Boolean) -> Unit = { _, _ -> },
    val toggleBitPerfect: () -> Unit = {},
)

/** A music folder as Settings lists it: "SD card · Music", its path, and whether it can be reached. */
class FolderLine(val folder: MusicFolder, val name: String, val unavailable: Boolean)

/**
 * Settings (spec 4.5): Appearance, Playback, Music folders and Last.fm.
 * [readout] is the output readout (spec 6.3); Bit-Perfect's row is the BP
 * button's setting ([bitPerfect]), greyed out in the same cases
 * ([bitPerfectGreyed]). [lastFm] is the connection (spec 7). [scanText] is what
 * the Music folders header says of a scan, left of its refresh button (0.7.0).
 */
@Composable
fun SettingsScreen(
    folders: List<FolderLine>,
    actions: SettingsActions,
    readout: String = "",
    bitPerfect: Boolean = false,
    bitPerfectGreyed: Boolean = false,
    lastFm: LastFm.Status = LastFm.Status(),
    lastFmActions: LastFmActions = LastFmActions(),
    scanText: String? = null,
    /** ThemeLab sets it to see the end of the Last.fm form. */
    scroll: ScrollState = rememberScrollState(),
) {
    val t = LocalAppTheme.current
    TitledScreen("Settings", scroll) {
        Panel("Appearance") {
            SettingRow("Theme", t.name, onClick = actions.toggleTheme) {
                Chip(width = t.dp("settings.themeChipWidth"), onClick = actions.toggleTheme) {
                    Icon(if (t.isDark) AppIcons.MOON else AppIcons.SUN, t.dp("settings.chipIcon"), "color.text.status")
                }
            }
        }
        Panel("Playback") {
            val toggle = actions.toggleBitPerfect.takeUnless { bitPerfectGreyed }
            SettingRow("Bit-Perfect", readout.ifEmpty { "Shared" }, onClick = toggle) {
                Chip(
                    Modifier.alpha(if (bitPerfectGreyed) t.number("disabledOpacity").toFloat() else 1f),
                    onClick = toggle,
                ) {
                    StatusDot(if (bitPerfect) "color.status.bitPerfectOn" else "color.status.bitPerfectOff")
                    ChipText("Bit-Perfect")
                }
            }
        }
        Panel("Music folders", list = true, headerAction = {
            // The button keeps its room; a long note ellipsizes before it.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (scanText != null) ScanNote(scanText, Modifier.weight(1f, fill = false))
                IconButtonSmall(AppIcons.REFRESH, actions.rescan)
            }
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
        Panel("Last.fm") { LastFmPanel(lastFm, lastFmActions) }
    }
}

/** What the Last.fm panel can ask for (spec 7). */
class LastFmActions(
    val startConnecting: () -> Unit = {},
    val connect: (userName: String, password: String) -> Unit = { _, _ -> },
    val cancel: () -> Unit = {},
    val disconnect: () -> Unit = {},
)

/**
 * Last.fm (spec 4.5, 7): the dot and "Last.fm: not connected" with Connect, or
 * "connected as <user>" with Disconnect. Connect opens a form in the panel for the
 * Last.fm username and password (the user's call, after 0.8.0). A failing send or
 * an error adds a line under the status.
 */
@Composable
private fun LastFmPanel(status: LastFm.Status, actions: LastFmActions) {
    val t = LocalAppTheme.current
    val connected = status.state != ScrobblerState.Disconnected && status.state != ScrobblerState.NeedsReconnect
    val busy = status.signingIn
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = t.dp("settings.rowPaddingX"), vertical = t.dp("settings.rowPaddingY"))
            .heightIn(min = t.dp("settings.rowMinHeight")),
        horizontalArrangement = Arrangement.spacedBy(t.dp("settings.rowGap")),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(t.dp("settings.lineGap"))) {
            Row(horizontalArrangement = Arrangement.spacedBy(t.dp("settings.dotGap")), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(if (status.state == ScrobblerState.Scrobbling) "color.status.lastFmDot" else "color.status.lastFmOff")
                val text = when (status.state) {
                    ScrobblerState.Disconnected -> "Last.fm: not connected"
                    ScrobblerState.NeedsReconnect -> "Last.fm: connect again"
                    else -> "Last.fm: connected as ${status.userName}"
                }
                Line(text, t.text("settings.statusTextSize", "color.text.primary"), maxLines = 2)
            }
            val note = status.error ?: status.queue.takeIf { status.state == ScrobblerState.Failing }
            if (note != null && !busy) Line(note, t.text("settings.valueSize", "color.text.muted"), maxLines = 4)
        }
        when {
            busy -> {}
            connected -> ChipButton("Disconnect", onClick = actions.disconnect)
            else -> ChipButton("Connect", onClick = actions.startConnecting)
        }
    }
    if (status.signingIn) ConnectForm(status, actions)
}

/**
 * The Last.fm username (or email) and password, sent once to Last.fm's sign-in for
 * phone apps; AudioFool keeps only the session. The name starts as the last one
 * connected ("connect again"). The password is held only while the form shows (not
 * in saved state), drawn as dots, and offered to the phone's password manager.
 */
@Composable
private fun ConnectForm(status: LastFm.Status, actions: LastFmActions) {
    val t = LocalAppTheme.current
    var userName by rememberSaveable(status.userName) { mutableStateOf(status.userName.orEmpty()) }
    var password by remember { mutableStateOf("") }
    val canSend = userName.isNotBlank() && password.isNotEmpty() && !status.sending
    val send = { actions.connect(userName, password) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = t.dp("settings.rowPaddingX"), end = t.dp("settings.rowPaddingX"), bottom = t.dp("lastFm.formPaddingBottom")),
        verticalArrangement = Arrangement.spacedBy(t.dp("lastFm.formGap")),
    ) {
        val message = t.text("lastFm.messageSize", "color.text.secondary")
        Line("Sign in with your Last.fm username and password. AudioFool keeps the session Last.fm gives back, never the password.", message, maxLines = 4)
        if (status.error != null) Line(status.error, t.text("lastFm.messageSize", "color.text.primary"), maxLines = 4)
        FormField("Username", userName, ContentType.Username, KeyboardType.Email, ImeAction.Next) { userName = it.trim() }
        FormField("Password", password, ContentType.Password, KeyboardType.Password, ImeAction.Done, hidden = true, onDone = send.takeIf { canSend }) { password = it }
        ButtonRow {
            if (status.sending) Line("Signing in…", message, Modifier.weight(1f))
            ChipButton("Cancel", onClick = actions.cancel)
            ChipButton("Connect", onClick = send.takeIf { canSend })
        }
    }
}

@Composable
private fun ButtonRow(content: @Composable RowScope.() -> Unit) {
    val t = LocalAppTheme.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(t.dp("lastFm.buttonGap"), Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * A labelled one-line field, drawn as the search field is (`panel.bg`, `panel.border`,
 * `shadow.searchInset`). [contentType] lets the phone's password manager fill it;
 * a [hidden] one shows dots.
 */
@Composable
private fun FormField(
    label: String,
    value: String,
    contentType: ContentType,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    hidden: Boolean = false,
    onDone: (() -> Unit)? = null,
    onValue: (String) -> Unit,
) {
    val t = LocalAppTheme.current
    val shape = RoundedCornerShape(t.radius("radius.surface"))
    Column(verticalArrangement = Arrangement.spacedBy(t.dp("lastFm.fieldLabelGap"))) {
        Line(label, t.text("lastFm.fieldLabelSize", "color.text.muted"))
        BasicTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.fillMaxWidth().height(t.dp("lastFm.fieldHeight")).semantics { this.contentType = contentType },
            singleLine = true,
            textStyle = t.text("lastFm.fieldTextSize", "color.text.primary"),
            cursorBrush = SolidColor(t.color("color.accent.playback")),
            visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            decorationBox = { field ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .tokenSurface(shape, t.color("color.panel.bg"), t.shadow("shadow.searchInset"), t.color("color.panel.border"), t.dp("borderWidth"))
                        .padding(horizontal = t.dp("lastFm.fieldPaddingX")),
                    contentAlignment = Alignment.CenterStart,
                ) { field() }
            },
        )
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

/**
 * The panel header's small button (Music folders' refresh, Artists' shuffle): 32 dp, a
 * 16 dp icon in `text.muted`. [label] is what TalkBack (and a test) calls it.
 */
@Composable
fun IconButtonSmall(icon: AppIcon, onClick: () -> Unit, label: String? = null) {
    val t = LocalAppTheme.current
    androidx.compose.foundation.layout.Box(
        Modifier
            .size(t.dp("settings.headerActionButton"))
            .tappable(CircleShape, onClick)
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, t.dp("settings.headerActionIcon"), "color.text.muted") }
}

/**
 * A screen with a title in its top bar (Settings, Statistics; 18/600,
 * `text.heading`) and panels `settings.sectionGap` apart that scroll.
 */
@Composable
fun TitledScreen(title: String, scroll: ScrollState = rememberScrollState(), content: @Composable ColumnScope.() -> Unit) {
    val t = LocalAppTheme.current
    ScreenFrame(
        topBar = {
            TopBar(t.dp("topBar.paddingStart"), t.dp("topBar.paddingEnd"), t.dp("topBar.gap")) {
                Line(title, t.text("topBar.titleSize", "color.text.heading", weight = t.number("topBar.titleWeight").toInt()))
            }
        },
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(t.dp("settings.sectionGap") - t.dp("panel.marginBottom")),
        ) {
            content()
            Spacer(Modifier)
        }
    }
}
