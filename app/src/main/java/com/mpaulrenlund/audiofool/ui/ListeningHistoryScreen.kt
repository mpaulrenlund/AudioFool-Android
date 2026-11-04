package com.mpaulrenlund.audiofool.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.mpaulrenlund.audiofool.core.scrobbling.ListeningPeriod
import com.mpaulrenlund.audiofool.lastfm.HistoryRow
import com.mpaulrenlund.audiofool.lastfm.HistoryState
import com.mpaulrenlund.audiofool.theme.LocalAppTheme

/** What Listening History can ask for. */
class HistoryActions(
    val choose: (ListeningPeriod) -> Unit = {},
    /** A row the library holds: its artist's albums, its album, or its album with the song chosen. */
    val go: (HistoryRow) -> Unit = {},
    val retry: () -> Unit = {},
    /** Not connected: Settings, where Last.fm is connected. */
    val openSettings: () -> Unit = {},
)

/**
 * Listening History (spec 4.7), from the logo menu and the cover menu: the desktop's
 * Listening History window (session 74) as a screen, built from Statistics' parts in
 * one column. The totals, the period switch, then TOP ARTISTS, TOP ALBUMS and TOP
 * TRACKS for that period, and RECENTLY PLAYED: the desktop's left column, then its
 * right. A row the library holds can be tapped; one it doesn't looks the same and does
 * nothing, as on the desktop.
 */
@Composable
fun ListeningHistoryScreen(state: HistoryState, actions: HistoryActions, scroll: ScrollState = rememberScrollState()) {
    TitledScreen(if (state.user == null) "Listening History" else "Listening History · ${state.user}", scroll) {
        when (state.phase) {
            HistoryState.Phase.NotConnected -> Panel("Last.fm") {
                SettingRow("Not connected to Last.fm", "Connect in Settings to see your listening history.", onClick = actions.openSettings)
            }
            HistoryState.Phase.Loading -> Panel("Last.fm") {
                SettingRow("Loading from Last.fm…")
            }
            HistoryState.Phase.Failed -> Panel("Last.fm") {
                SettingRow("Couldn't load your listening history", "${state.error ?: ""} Tap to try again.".trim(), onClick = actions.retry)
            }
            HistoryState.Phase.Loaded -> {
                Panel("Totals") {
                    // Statistics' two rows: the scrobbles and since when, then the three counts.
                    Figures(*state.tiles.chunkedForTiles().toTypedArray())
                }
                PeriodSwitch(state.period, actions.choose)
                HistoryPanel("Top Artists", state.topArtists, state.topEmptyText, actions.go)
                HistoryPanel("Top Albums", state.topAlbums, state.topEmptyText, actions.go)
                HistoryPanel("Top Tracks", state.topTracks, state.topEmptyText, actions.go)
                HistoryPanel("Recently Played", state.recent, "Nothing played yet.", actions.go)
            }
        }
    }
}

/** The first two tiles on a row (Scrobbles, Scrobbling since), the rest (the counts) on the next. */
private fun List<Pair<String, String>>.chunkedForTiles(): List<List<Pair<String, String>>> =
    if (size <= 2) listOf(this) else listOf(take(2), drop(2))

/**
 * The period the three top lists cover: the phone's six short words (the user's call)
 * in equal slots, so the row doesn't shift when the choice moves. As the desktop's
 * switch: the chosen one in `text.heading` at the dialogs' emphasis weight, the rest
 * `text.muted`.
 */
@Composable
private fun PeriodSwitch(chosen: ListeningPeriod, choose: (ListeningPeriod) -> Unit) {
    val t = LocalAppTheme.current
    val shape = RoundedCornerShape(t.radius("radius.row"))
    Row(Modifier.fillMaxWidth().padding(horizontal = t.dp("panel.marginX"))) {
        for (period in ListeningPeriod.entries) {
            val on = period == chosen
            Box(
                Modifier
                    .weight(1f)
                    .height(t.dp("listeningHistory.periodHeight"))
                    .tappable(shape, if (on) null else ({ choose(period) })),
                contentAlignment = Alignment.Center,
            ) {
                Line(
                    period.shortLabel,
                    t.text(
                        "listeningHistory.periodTextSize",
                        if (on) "color.text.heading" else "color.text.muted",
                        weight = if (on) t.desktopNumber("dialog.emphasisWeight").toInt() else 400,
                    ).copy(textAlign = TextAlign.Center),
                )
            }
        }
    }
}

/** A panel of rows, or the line it says when it has none. */
@Composable
private fun HistoryPanel(label: String, rows: List<HistoryRow>, empty: String, go: (HistoryRow) -> Unit) {
    val t = LocalAppTheme.current
    Panel(label) {
        if (rows.isEmpty()) {
            SettingRow(empty)
        } else {
            Column(Modifier.padding(horizontal = t.dp("panel.listPadding"), vertical = t.dp("statistics.listPaddingY"))) {
                for (row in rows) HistoryLine(row, if (row.target == null) null else ({ go(row) }))
            }
        }
    }
}

/**
 * One row, as the desktop's: the label and its figure ("866 plays", "2 hours ago") on a
 * line, the bar for a top list, then the artist (or "Artist · Album") in grey. Its
 * text sits where Statistics' rows put theirs; the press shows inside the list padding.
 */
@Composable
private fun HistoryLine(row: HistoryRow, onClick: (() -> Unit)?) {
    val t = LocalAppTheme.current
    Column(
        Modifier
            .fillMaxWidth()
            .tappable(RoundedCornerShape(t.radius("radius.row")), onClick)
            .padding(horizontal = t.dp("statistics.rowPaddingX") - t.dp("panel.listPadding"), vertical = t.dp("statistics.rowPaddingY")),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(t.dp("settings.rowGap")), verticalAlignment = Alignment.CenterVertically) {
            Line(row.label, t.text("statistics.rowLabelSize", "color.text.primary"), Modifier.weight(1f))
            Line(row.value, t.text("statistics.rowValueSize", "color.text.muted"))
        }
        row.fill?.let {
            Spacer(Modifier.height(t.dp("statistics.barGap")))
            StatBar(it)
        }
        row.detail?.let {
            Spacer(Modifier.height(t.dp("listeningHistory.detailGap")))
            Line(it, t.text("listeningHistory.detailSize", "color.text.muted"))
        }
    }
}
