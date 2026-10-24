package com.mpaulrenlund.audiofool.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mpaulrenlund.audiofool.theme.LocalAppTheme
import com.mpaulrenlund.audiofool.theme.tokenSurface

/**
 * The mini player (spec 3), under the panel on every library screen: the cover,
 * title (600, `text.primary`), artist (600, `accent.blue`), and Previous,
 * Play/Pause and Next. A tap anywhere else opens Now Playing. Hidden until
 * something has been played.
 */
@Composable
fun MiniPlayerBar(browse: Browse, actions: AppActions) {
    val track = browse.nowPlaying.track ?: return
    val t = LocalAppTheme.current
    val shape = RoundedCornerShape(t.radius("radius.surface"))
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
            RoundButton(t.dp("miniPlayer.skipButton"), skipW, skipH, actions.previous) {
                Icon(AppIcons.PREVIOUS, t.dp("miniPlayer.skipIcon"), "color.control.iconNeutral")
            }
            RoundButton(t.dp("miniPlayer.playButton"), t.dp("miniPlayer.playTarget"), t.dp("miniPlayer.playTarget"), actions.playPause, play = true) {
                Icon(if (browse.nowPlaying.isPlaying) AppIcons.PAUSE else AppIcons.PLAY, t.dp("miniPlayer.playIcon"), "color.accent.playback")
            }
            RoundButton(t.dp("miniPlayer.skipButton"), skipW, skipH, actions.next) {
                Icon(AppIcons.NEXT, t.dp("miniPlayer.skipIcon"), "color.control.iconNeutral")
            }
        }
    }
}
