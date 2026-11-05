package com.mpaulrenlund.audiofool.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.mpaulrenlund.audiofool.core.genius.GeniusHit
import com.mpaulrenlund.audiofool.core.model.Album
import com.mpaulrenlund.audiofool.core.model.Track
import com.mpaulrenlund.audiofool.genius.GeniusCreditRow
import com.mpaulrenlund.audiofool.genius.GeniusEntry
import com.mpaulrenlund.audiofool.genius.GeniusRows
import com.mpaulrenlund.audiofool.genius.GeniusState
import com.mpaulrenlund.audiofool.genius.LinkedText
import com.mpaulrenlund.audiofool.theme.AppTheme
import com.mpaulrenlund.audiofool.theme.LocalAppTheme

/** What the Genius screen can ask for. */
class GeniusActions(
    /** One of the nearest results, when nothing matched: its page under the same header. */
    val choose: (GeniusHit) -> Unit = {},
    /** A note that offers it ("Tap to try again."): whatever failed, asked again. */
    val retry: () -> Unit = {},
    val showMoreComments: () -> Unit = {},
    /** "Open on Genius", or a web address in the text: the browser. */
    val openUrl: (String) -> Unit = {},
)

/**
 * The Genius screen (spec 5.2), from Now Playing's cover menu: the desktop's Genius
 * window (session 75) as a screen, in its one column. The library's own song at the
 * top, drawn as the Album screen's header ([album] gives the cover), with "Open on
 * Genius" once a page is loaded; then, each only when Genius has something for it,
 * ABOUT, QUESTIONS & ANSWERS, ANNOTATIONS, CREDITS, CONNECTIONS and COMMENTS. With no
 * match, CLOSEST ON GENIUS offers the nearest results. Looking it up, a failure and
 * the parts Genius didn't send are a GENIUS panel's row, as Listening History's are.
 * Web addresses in the text are links. Built from Statistics' and the album header's
 * parts; no new colours.
 */
@Composable
fun GeniusScreen(state: GeniusState, album: Album?, actions: GeniusActions, scroll: ScrollState = rememberScrollState()) {
    TitledScreen("Genius", scroll) {
        state.track?.let { SongHeader(it, album, state.pageUrl, actions.openUrl) }
        state.note?.let { note ->
            Panel("Genius") { SettingRow(note.text, note.detail, onClick = if (note.retry) actions.retry else null) }
        }
        if (state.suggestions.isNotEmpty()) {
            Panel("Closest on Genius") { Choices(state.suggestions, actions.choose) }
        }
        state.about?.let { about ->
            Panel("About") { Section { LinkedBody(about, actions.openUrl) } }
        }
        if (state.answers.isNotEmpty()) {
            Panel("Questions & Answers") { Section { for (e in state.answers) Entry(e, actions.openUrl) } }
        }
        if (state.annotations.isNotEmpty()) {
            Panel("Annotations") { Section { for (e in state.annotations) Entry(e, actions.openUrl) } }
        }
        if (state.credits.isNotEmpty()) {
            Panel("Credits") { Credits(state.credits) }
        }
        if (state.connections.isNotEmpty()) {
            Panel("Connections") { Credits(state.connections) }
        }
        if (state.comments.isNotEmpty()) {
            val t = LocalAppTheme.current
            Panel(
                "Comments",
                headerAction = {
                    // A note, not a button: it keeps the label's own end padding.
                    Line(
                        state.commentCount,
                        t.text("genius.figureSize", "color.text.muted"),
                        Modifier.padding(end = t.dp("panel.headerPaddingX") - t.dp("panel.headerActionPaddingEnd")),
                    )
                },
            ) {
                Section {
                    for (e in state.comments) Entry(e, actions.openUrl)
                    state.moreComments?.let { more ->
                        if (more == GeniusRows.LOADING_MORE) {
                            Line(more, t.text("genius.bodySize", "color.text.muted"))
                        } else {
                            Line(more, linkStyle(t, "genius.bodySize"), Modifier.tappable(RoundedCornerShape(t.radius("radius.row")), actions.showMoreComments))
                        }
                    }
                }
            }
        }
    }
}

/**
 * The library's song, as the Album screen's header draws an album (spec 4.3): its
 * cover, the title, the artist in the artist blue and "Album · Year" from its tags,
 * then "Open on Genius" once a page is loaded. It stays the library's song when one of
 * the nearest results is chosen; the page under it is Genius's.
 */
@Composable
private fun SongHeader(track: Track, album: Album?, pageUrl: String?, open: (String) -> Unit) {
    val t = LocalAppTheme.current
    PanelSurface {
        Row(
            Modifier.fillMaxWidth().padding(t.dp("album.headerPadding")),
            horizontalArrangement = Arrangement.spacedBy(t.dp("album.artGap")),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlbumArt(album, t.dp("album.art"), "color.art.border", t.shadow("shadow.headerArt"), header = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(t.dp("album.lineGap"))) {
                Line(
                    track.displayTitle,
                    t.text("album.titleSize", "color.text.heading", weight = 600, lineHeight = t.number("album.titleLineHeight")),
                    maxLines = t.number("album.titleMaxLines").toInt(),
                )
                Line(track.artist.ifBlank { track.groupingArtist }, t.text("album.artistSize", "color.accent.blue", weight = 600))
                GeniusRows.detailLine(track)?.let { Line(it, t.text("album.detailsSize", "color.text.secondary")) }
                if (pageUrl != null) {
                    Line(
                        "Open on Genius",
                        linkStyle(t, "album.detailsSize"),
                        Modifier
                            .padding(top = t.dp("genius.linkGap") - t.dp("album.lineGap"))
                            .tappable(RoundedCornerShape(t.radius("radius.row"))) { open(pageUrl) },
                    )
                }
            }
        }
    }
}

/** Inside a panel: the header's padding across, `genius.sectionPaddingY` down, entries `genius.entryGap` apart. */
@Composable
private fun Section(content: @Composable ColumnScope.() -> Unit) {
    val t = LocalAppTheme.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = t.dp("statistics.rowPaddingX"), vertical = t.dp("genius.sectionPaddingY")),
        verticalArrangement = Arrangement.spacedBy(t.dp("genius.entryGap")),
        content = content,
    )
}

/**
 * A question and its answer, an explanation under its lyric line, or a comment under
 * its author: the first line at the dialogs' emphasis weight (a lyric line in the
 * muted grey), the right-hand note ("Verified", "Mar 7, 2017 · 297 votes") on its
 * baseline, then the text.
 */
@Composable
private fun Entry(e: GeniusEntry, open: (String) -> Unit) {
    val t = LocalAppTheme.current
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(t.dp("genius.columnGap"))) {
            BasicText(
                e.heading,
                Modifier.weight(1f).alignByBaseline(),
                t.text(
                    "genius.headingSize",
                    if (e.isQuote) "color.text.muted" else "color.text.primary",
                    weight = t.desktopNumber("dialog.emphasisWeight").toInt(),
                    lineHeight = t.number("genius.bodyLineHeight"),
                ),
            )
            e.figure?.let { Line(it, t.text("genius.figureSize", "color.text.muted"), Modifier.alignByBaseline()) }
        }
        Spacer(Modifier.height(t.dp("genius.bodyGap")))
        LinkedBody(e.body, open)
    }
}

/** The credits or the connections: the label in grey in a fixed column, then the names (several lines for connections). */
@Composable
private fun Credits(rows: List<GeniusCreditRow>) {
    val t = LocalAppTheme.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = t.dp("statistics.rowPaddingX"), vertical = t.dp("genius.sectionPaddingY")),
        verticalArrangement = Arrangement.spacedBy(t.dp("genius.creditGap")),
    ) {
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(t.dp("genius.columnGap"))) {
                BasicText(row.label, Modifier.width(t.dp("genius.creditLabelWidth")), bodyStyle(t, "color.text.muted"))
                BasicText(row.value, Modifier.weight(1f), bodyStyle(t, "color.text.secondary"))
            }
        }
    }
}

/** The nearest results: Statistics' rows, the title over the artist, each a tap to load. */
@Composable
private fun Choices(hits: List<GeniusHit>, choose: (GeniusHit) -> Unit) {
    val t = LocalAppTheme.current
    Column(Modifier.padding(horizontal = t.dp("panel.listPadding"), vertical = t.dp("statistics.listPaddingY"))) {
        for (hit in hits) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .tappable(RoundedCornerShape(t.radius("radius.row"))) { choose(hit) }
                    .padding(horizontal = t.dp("statistics.rowPaddingX") - t.dp("panel.listPadding"), vertical = t.dp("statistics.rowPaddingY")),
            ) {
                Line(hit.title, t.text("statistics.rowLabelSize", "color.text.primary"))
                if (hit.artist.isNotEmpty()) {
                    Spacer(Modifier.height(t.dp("genius.detailGap")))
                    Line(hit.artist, t.text("genius.detailSize", "color.text.muted"))
                }
            }
        }
    }
}

/** Genius's text, its web addresses made links that open in the browser (the desktop's `LinkedText`). */
@Composable
private fun LinkedBody(text: String, open: (String) -> Unit) {
    val t = LocalAppTheme.current
    val link = linkStyle(t, "genius.bodySize").toSpanStyle()
    val annotated = remember(text, link, open) {
        buildAnnotatedString {
            for ((part, isLink) in LinkedText.split(text)) {
                if (isLink) {
                    withLink(LinkAnnotation.Url(part, TextLinkStyles(link)) { open(part) }) { append(part) }
                } else {
                    append(part)
                }
            }
        }
    }
    BasicText(annotated, Modifier.fillMaxWidth(), bodyStyle(t, "color.text.secondary"))
}

private fun bodyStyle(t: AppTheme, color: String): TextStyle =
    t.text("genius.bodySize", color, lineHeight = t.number("genius.bodyLineHeight"))

/**
 * A link in the text, as the desktop's `theme.link`: blue is only for artist names,
 * so a link is told apart by weight and a lasting underline instead of colour.
 */
private fun linkStyle(t: AppTheme, size: String): TextStyle =
    t.text(size, "color.text.primary", weight = t.desktopNumber("dialog.emphasisWeight").toInt())
        .copy(textDecoration = TextDecoration.Underline)
