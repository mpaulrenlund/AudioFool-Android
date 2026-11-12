package com.mpaulrenlund.audiofool.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import com.mpaulrenlund.audiofool.core.library.TagDetails
import com.mpaulrenlund.audiofool.core.library.TagEditRules
import com.mpaulrenlund.audiofool.core.library.TagField
import com.mpaulrenlund.audiofool.core.library.TrackTagEdit
import com.mpaulrenlund.audiofool.core.model.Track
import com.mpaulrenlund.audiofool.theme.LocalAppTheme
import com.mpaulrenlund.audiofool.theme.tokenSurface

/**
 * The Edit Tags screen's boxes (spec 5.4), for one song: what they started with,
 * what they hold now, which details were cleared with their button, and whether
 * the file is still being read or saved. The rules are the desktop window's
 * ([TagEditRules]).
 */
@Stable
class EditTagsState(val track: Track) {
    /** What the boxes started with: the library's song until the file is read ([read]), then with its details. */
    var initial: Map<TagField, String> by mutableStateOf(TagEditRules.initial(track, null))
        private set

    val values = mutableStateMapOf<TagField, String>().apply { putAll(initial) }

    var cleared: Set<TagField> by mutableStateOf(emptySet())
        private set

    /** The file is being read: the boxes can't be typed in and Save waits (the desktop's `IsReadingDetails`). */
    var reading: Boolean by mutableStateOf(true)
        private set

    var saving: Boolean by mutableStateOf(false)

    /** Why the last save didn't happen, until the boxes change. */
    var failure: String? by mutableStateOf(null)

    /** The file's details, read off the main thread; null when it couldn't be read (the library's numbers then, and empty details). */
    fun read(details: TagDetails?) {
        initial = TagEditRules.initial(track, details)
        values.clear()
        values.putAll(initial)
        reading = false
    }

    fun type(field: TagField, text: String) {
        values[field] = text
        failure = null
    }

    /** A detail's clear button: the box empties and says it will be cleared; typing afterwards writes what is typed. */
    fun clear(field: TagField) {
        values[field] = ""
        cleared = cleared + field
        failure = null
    }

    val problem: String? get() = TagEditRules.problem(values)
    val canSave: Boolean get() = !reading && !saving && problem == null
    val hasChanges: Boolean get() = TagEditRules.hasChanges(initial, values, cleared)
    fun edit(): TrackTagEdit = TagEditRules.edit(initial, values, cleared)

    /** The footer's line: saving, why a save failed, or why the boxes can't be saved. */
    val footer: String
        get() = when {
            saving -> "Saving…"
            failure != null -> failure!!
            else -> problem ?: ""
        }
}

class EditTagsActions(
    /** Saves the boxes ([EditTagsState.canSave] first); back to Now Playing once saved. */
    val save: () -> Unit = {},
    /** Leaves without saving. */
    val leave: () -> Unit = {},
)

/**
 * Edit Tags (spec 5.4; 2026-11-11, the user's request): the desktop's Edit Track
 * Tags window for the song playing, from the cover menu. The desktop's boxes in
 * its order, labels on the left: Title, Artist, Album Artist, Album, Year, Track
 * "of", Disc "of", then Publisher, Composer, Conductor, Genre and Comments, each
 * with its clear button. Under the panel the footer: why the boxes can't be
 * saved, and Save. No Close button (spec 1: no on-screen back buttons): system
 * back leaves, and asks first when something was typed (the desktop's Escape).
 * [promptForPictures] shows that question, for ThemeLab.
 */
@Composable
fun EditTagsScreen(
    state: EditTagsState,
    actions: EditTagsActions,
    scroll: ScrollState = rememberScrollState(),
    promptForPictures: Boolean = false,
) {
    val t = LocalAppTheme.current
    var asking by remember { mutableStateOf(promptForPictures) }
    // While saving, back waits; with changes, it asks. Otherwise predictive back as on every screen.
    BackHandler(enabled = state.saving || state.hasChanges) {
        if (!state.saving) asking = true
    }

    ScreenFrame(
        topBar = {
            TopBar(t.dp("topBar.paddingStart"), t.dp("topBar.paddingEnd"), t.dp("topBar.gap")) {
                Line("Edit Tags", t.text("topBar.titleSize", "color.text.heading", weight = t.number("topBar.titleWeight").toInt()))
            }
        },
        bottom = { Footer(state, actions.save) },
    ) {
        Column(Modifier.weight(1f).verticalScroll(scroll)) {
            Panel("Tags") {
                Column(
                    Modifier.padding(horizontal = t.dp("tagEdit.paddingX"), vertical = t.dp("tagEdit.paddingY")),
                    verticalArrangement = Arrangement.spacedBy(t.dp("tagEdit.rowGap")),
                ) {
                    for (field in listOf(TagField.Title, TagField.Artist, TagField.AlbumArtist, TagField.Album)) {
                        FieldRow(field.label) { TagBox(state, field, Modifier.weight(1f)) }
                    }
                    FieldRow(TagField.Year.label) {
                        TagBox(state, TagField.Year, Modifier.width(t.dp("tagEdit.yearWidth")), KeyboardType.Number)
                    }
                    FieldRow("Track") { NumberPair(state, TagField.TrackNumber, TagField.TrackCount) }
                    FieldRow("Disc") { NumberPair(state, TagField.DiscNumber, TagField.DiscCount) }
                    for (field in listOf(TagField.Publisher, TagField.Composer, TagField.Conductor, TagField.Genre)) {
                        FieldRow(field.label) {
                            TagBox(state, field, Modifier.weight(1f))
                            ClearButton(state, field)
                        }
                    }
                    FieldRow(TagField.Comment.label) {
                        TagBox(state, TagField.Comment, Modifier.weight(1f), multiline = true)
                        ClearButton(state, TagField.Comment)
                    }
                }
            }
        }
    }

    if (asking) {
        UnsavedPrompt(
            save = {
                asking = false
                // As the desktop: Save only where the boxes can be saved; otherwise the footer says why.
                if (state.canSave) actions.save()
            },
            discard = {
                asking = false
                actions.leave()
            },
            cancel = { asking = false },
        )
    }
}

/** A label in its column, level with the top of its box, then the box(es). */
@Composable
private fun FieldRow(label: String, content: @Composable RowScope.() -> Unit) {
    val t = LocalAppTheme.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(t.dp("tagEdit.labelWidth")).heightIn(min = t.dp("tagEdit.fieldHeight")), contentAlignment = Alignment.CenterStart) {
            Line(label, t.text("tagEdit.labelSize", "color.text.primary"), maxLines = 2)
        }
        Spacer(Modifier.width(t.dp("tagEdit.labelGap")))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Top, content = content)
    }
}

/** A track or disc number and its total, "of" between, as the desktop's. */
@Composable
private fun RowScope.NumberPair(state: EditTagsState, number: TagField, total: TagField) {
    val t = LocalAppTheme.current
    TagBox(state, number, Modifier.width(t.dp("tagEdit.numberWidth")), KeyboardType.Number)
    Box(Modifier.height(t.dp("tagEdit.fieldHeight")).padding(horizontal = t.dp("tagEdit.ofGap")), contentAlignment = Alignment.Center) {
        Line("of", t.text("tagEdit.labelSize", "color.text.muted"))
    }
    TagBox(state, total, Modifier.width(t.dp("tagEdit.numberWidth")), KeyboardType.Number)
}

/**
 * A box, drawn as the search field and Last.fm's fields are (`panel.bg`,
 * `panel.border`, `shadow.searchInset`). A cleared detail's empty box says
 * "Will be cleared", as the desktop's placeholder does.
 */
@Composable
private fun TagBox(
    state: EditTagsState,
    field: TagField,
    modifier: Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    multiline: Boolean = false,
) {
    val t = LocalAppTheme.current
    val shape = RoundedCornerShape(t.radius("radius.surface"))
    val value = state.values[field] ?: ""
    val placeholder = if (value.isEmpty() && field in state.cleared) "Will be cleared" else null
    val size = if (multiline) {
        Modifier.heightIn(min = t.dp("tagEdit.commentMinHeight"), max = t.dp("tagEdit.commentMaxHeight"))
    } else {
        Modifier.height(t.dp("tagEdit.fieldHeight"))
    }
    BasicTextField(
        value = value,
        onValueChange = { state.type(field, it) },
        modifier = modifier.then(size).semantics { contentDescription = field.label },
        readOnly = state.reading || state.saving,
        singleLine = !multiline,
        textStyle = t.text("tagEdit.fieldTextSize", "color.text.primary"),
        cursorBrush = SolidColor(t.color("color.accent.playback")),
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = keyboardType,
            imeAction = if (multiline) ImeAction.Default else ImeAction.Next,
        ),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(if (multiline) Modifier.heightIn(min = t.dp("tagEdit.commentMinHeight")) else Modifier.height(t.dp("tagEdit.fieldHeight")))
                    .tokenSurface(shape, t.color("color.panel.bg"), t.shadow("shadow.searchInset"), t.color("color.panel.border"), t.dp("borderWidth"))
                    .padding(horizontal = t.dp("tagEdit.fieldPaddingX"), vertical = if (multiline) t.dp("tagEdit.fieldPaddingY") else Dp(0f)),
                contentAlignment = if (multiline) Alignment.TopStart else Alignment.CenterStart,
            ) {
                if (placeholder != null) Line(placeholder, t.text("tagEdit.fieldTextSize", "color.text.muted"))
                inner()
            }
        },
    )
}

/** A detail's clear button (×), `tagEdit.clearButtonGap` right of its box: the chips' look. */
@Composable
private fun ClearButton(state: EditTagsState, field: TagField) {
    val t = LocalAppTheme.current
    val shape = RoundedCornerShape(t.radius("radius.chip"))
    val enabled = !state.reading && !state.saving
    Spacer(Modifier.width(t.dp("tagEdit.clearButtonGap")))
    Box(
        Modifier
            .size(t.dp("tagEdit.clearButton"))
            .let { if (enabled) it else it.alpha(t.number("disabledOpacity").toFloat()) }
            .tokenSurface(shape, t.color("color.status.chipBg"), border = t.color("color.status.chipBorder"), borderWidth = t.dp("borderWidth"))
            .tappable(shape, if (enabled) ({ state.clear(field) }) else null)
            .semantics { contentDescription = "Clear ${field.label}" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.CLEAR, t.dp("tagEdit.clearIcon"), "color.text.status")
    }
}

/**
 * Under the panel: the line saying why the boxes can't be saved (or that they are
 * being saved, or why a save failed) in `text.heading` at the dialogs' emphasis,
 * no colour, as the desktop's footer; and Save, greyed while it can't be pressed.
 */
@Composable
private fun Footer(state: EditTagsState, save: () -> Unit) {
    val t = LocalAppTheme.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = t.dp("panel.marginX") + t.dp("tagEdit.paddingX"), vertical = t.dp("tagEdit.footerPaddingY")),
        horizontalArrangement = Arrangement.spacedBy(t.dp("tagEdit.footerGap")),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            state.footer,
            Modifier.weight(1f),
            t.text("tagEdit.footerTextSize", "color.text.heading", weight = t.desktopNumber("dialog.emphasisWeight").toInt()),
        )
        ChipButton(
            "Save",
            Modifier.let { if (state.canSave) it else it.alpha(t.number("disabledOpacity").toFloat()) },
            onClick = if (state.canSave) save else null,
        )
    }
}

/** The desktop's "Unsaved Changes" question, at system back with something typed: Save first, then Don't Save, then Cancel. */
@Composable
private fun UnsavedPrompt(save: () -> Unit, discard: () -> Unit, cancel: () -> Unit) {
    val t = LocalAppTheme.current
    val shape = RoundedCornerShape(t.radius("radius.surface"))
    Dialog(onDismissRequest = cancel) {
        Column(
            Modifier
                .width(t.dp("tagEdit.promptWidth"))
                .tokenSurface(shape, t.color("color.panel.bg"), t.shadow("shadow.headerArt") + t.shadow("shadow.panelHighlight"), t.color("color.panel.border"), t.dp("borderWidth"))
                .padding(t.dp("tagEdit.promptPadding")),
            verticalArrangement = Arrangement.spacedBy(t.dp("tagEdit.promptGap")),
        ) {
            Line("Unsaved Changes", t.text("tagEdit.promptTitleSize", "color.text.heading", weight = 600))
            BasicText("Save your changes before closing?", style = t.text("tagEdit.promptTextSize", "color.text.secondary"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(t.dp("lastFm.buttonGap"), Alignment.End)) {
                ChipButton("Save", onClick = save)
                ChipButton("Don't Save", onClick = discard)
                ChipButton("Cancel", onClick = cancel)
            }
        }
    }
}
