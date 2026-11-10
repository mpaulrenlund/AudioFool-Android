package com.mpaulrenlund.audiofool.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mpaulrenlund.audiofool.theme.LocalAppTheme
import com.mpaulrenlund.audiofool.theme.tokenSurface
import com.mpaulrenlund.audiofool.theme.toCompose

/**
 * The mini player (spec 3), under the panel on every library screen: the cover,
 * title (600, `text.primary`), artist (600, `accent.blue`), and Previous,
 * Play/Pause and Next. A tap anywhere else opens Now Playing. Hidden until
 * something has been played.
 *
 * Its three buttons follow Now Playing's (2026-11-10, the user's call): the icons,
 * and Play/Pause's ring and glow, take the cover's colour ([rememberCoverColor],
 * the same colour Now Playing shows for the song), and the glow shows only while
 * playing. A cover with no colour of its own, or no cover, leaves them the theme's:
 * `accent.playback` for Play/Pause, `control.iconNeutral` for Previous and Next.
 */
@Composable
fun MiniPlayerBar(browse: Browse, actions: AppActions) {
    val track = browse.nowPlaying.track ?: return
    val t = LocalAppTheme.current
    val shape = RoundedCornerShape(t.radius("radius.surface"))
    val main = rememberCoverColor(track)
    // Fitted to the theme as it is now, not kept from the last one: a theme switch gives the other theme's.
    val accent = remember(t, main) { main?.let { t.coverAccent(it) } }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = t.dp("miniPlayer.marginX"))
            .padding(bottom = t.dp("miniPlayer.marginBottom"))
            .height(t.dp("miniPlayer.height"))
            .tokenSurface(shape, t.color("color.panel.bg"), t.shadow("shadow.panelHighlight"), t.color("color.panel.border"), t.dp("borderWidth"))
            .tappable(shape, actions.openNowPlaying)
            .padding(horizontal = t.dp("miniPlayer.paddingX")),
        horizontalArrangement = Arrangement.spacedBy(t.dp("miniPlayer.gap")),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArt(browse.library.albumHolding(track.filePath), t.dp("miniPlayer.thumb"), "color.art.border", t.shadow("shadow.nowPlayingArt"))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(t.dp("miniPlayer.lineGap"))) {
            Line(track.displayTitle, t.text("miniPlayer.titleSize", "color.text.primary", weight = t.number("miniPlayer.titleWeight").toInt()))
            Line(track.artist.ifBlank { track.groupingArtist }, t.text("miniPlayer.artistSize", "color.accent.blue", weight = t.number("miniPlayer.artistWeight").toInt()))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(t.dp("miniPlayer.buttonGroupGap")), verticalAlignment = Alignment.CenterVertically) {
            val skipW = t.dp("miniPlayer.skipTarget.width")
            val skipH = t.dp("miniPlayer.skipTarget.height")
            val skipColor = accent?.toCompose() ?: t.color("color.control.iconNeutral")
            RoundButton(t.dp("miniPlayer.skipButton"), skipW, skipH, "Previous", actions.previous) {
                Icon(AppIcons.PREVIOUS, t.dp("miniPlayer.skipIcon"), skipColor)
            }
            val playing = browse.nowPlaying.isPlaying
            RoundButton(
                t.dp("miniPlayer.playButton"), t.dp("miniPlayer.playTarget"), t.dp("miniPlayer.playTarget"),
                if (playing) "Pause" else "Play", actions.playPause, play = true, playTint = accent, playGlow = playing,
            ) {
                Icon(if (playing) AppIcons.PAUSE else AppIcons.PLAY, t.dp("miniPlayer.playIcon"), accent?.toCompose() ?: t.color("color.accent.playback"))
            }
            RoundButton(t.dp("miniPlayer.skipButton"), skipW, skipH, "Next", actions.next) {
                Icon(AppIcons.NEXT, t.dp("miniPlayer.skipIcon"), skipColor)
            }
        }
    }
}
