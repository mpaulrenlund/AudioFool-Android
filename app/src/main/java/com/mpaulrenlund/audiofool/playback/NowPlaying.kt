package com.mpaulrenlund.audiofool.playback

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mpaulrenlund.audiofool.core.model.Track

/**
 * What is playing, as the screens show it: the mini player (spec 3) and the
 * now-playing rows (spec 2). Session 3 puts the queue and BASS behind it.
 *
 * Until then it has only a **preview**, turned on from the debug panel: with it
 * on, a tap on a song marks that song as playing (silently) and the mini player
 * shows it, so both can be checked. Off, nothing plays and the mini player stays
 * hidden, as spec 3 says it is until something has been played.
 */
@Stable
class NowPlaying {
    var track: Track? by mutableStateOf(null)
        private set
    var isPlaying: Boolean by mutableStateOf(false)
        private set
    var preview: Boolean by mutableStateOf(false)
        private set

    /** A tap on a song (spec 2: it plays, with the rest of its album after it). Only the preview, for now. */
    fun play(track: Track) {
        if (!preview) return
        this.track = track
        isPlaying = true
    }

    fun playPause() {
        if (track != null) isPlaying = !isPlaying
    }

    fun setPreview(on: Boolean, firstTrack: Track?) {
        preview = on
        if (on) {
            if (track == null) {
                track = firstTrack
                isPlaying = firstTrack != null
            }
        } else {
            track = null
            isPlaying = false
        }
    }

    /** For ThemeLab: a song shown as playing, as in the mockups. */
    fun showForPictures(track: Track, playing: Boolean = true) {
        preview = true
        this.track = track
        isPlaying = playing
    }
}
