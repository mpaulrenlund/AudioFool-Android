package com.mpaulrenlund.audiofool.playback

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mpaulrenlund.audiofool.core.model.Track
import com.mpaulrenlund.audiofool.core.playback.RepeatMode

/**
 * What is playing, as the screens show it: the mini player (spec 3), the
 * now-playing rows (spec 2) and Now Playing (spec 5). [Player] keeps it up to
 * date, on the main thread; ThemeLab fills it in by hand ([showForPictures]).
 *
 * The track is the one being **heard**, which across a gapless join is the old
 * one until its last frame has come out of the speakers.
 */
@Stable
class NowPlaying {
    var track: Track? by mutableStateOf(null)
        internal set
    var isPlaying: Boolean by mutableStateOf(false)
        internal set

    /** Where [track] sits in the queue, from 0, and the queue's length: Now Playing's "3 / 10". */
    var queueIndex: Int by mutableIntStateOf(0)
        internal set
    var queueSize: Int by mutableIntStateOf(0)
        internal set

    var repeat: RepeatMode by mutableStateOf(RepeatMode.Off)
        internal set
    var shuffle: Boolean by mutableStateOf(false)
        internal set

    /** The output readout (spec 6.3). */
    var readout: String by mutableStateOf("")
        internal set

    /** The Bit-Perfect setting (spec 6.2, 6.4): the BP button's label and the Settings chip's dot. */
    var bitPerfect: Boolean by mutableStateOf(false)
        internal set

    /** Bit-Perfect can't work on this route (Bluetooth, the speaker): BP is greyed out and can't be tapped. */
    var bitPerfectGreyed: Boolean by mutableStateOf(true)
        internal set

    /** The track's waveform levels (0 to 1), or null until worked out (spec 5: a flat bar meanwhile). */
    var waveform: FloatArray? by mutableStateOf(null)
        internal set

    /** Where playback is held when nothing is moving it (paused after a restore, a seek pending). */
    var heldPositionMs: Long by mutableLongStateOf(0)
        internal set

    /** Where in [track] the speakers are, in milliseconds; read as often as the screen draws. */
    internal var positionSource: () -> Long = { heldPositionMs }

    fun positionMs(): Long = positionSource()

    /** False for ThemeLab's still pictures: the seekbar reads the position once instead of every frame. */
    internal var live: Boolean = true

    /** The track's length as Now Playing shows it. */
    val durationMs: Long get() = track?.durationMs ?: 0

    /** For the debug panel: how each track's output was opened, newest first. */
    var openedLog: List<String> by mutableStateOf(emptyList())
        internal set

    /** For the debug panel: songs passed over because they wouldn't open. */
    var skippedLog: List<String> by mutableStateOf(emptyList())
        internal set

    /** For ThemeLab: a song shown as playing, as in the mockups. */
    fun showForPictures(
        track: Track,
        playing: Boolean = true,
        index: Int = 0,
        size: Int = 1,
        positionMs: Long = 0,
        waveform: FloatArray? = null,
        readout: String = "",
        bitPerfect: Boolean = false,
        bitPerfectGreyed: Boolean = false,
    ) {
        this.bitPerfect = bitPerfect
        this.bitPerfectGreyed = bitPerfectGreyed
        this.track = track
        isPlaying = playing
        queueIndex = index
        queueSize = size
        heldPositionMs = positionMs
        positionSource = { heldPositionMs }
        live = false
        this.waveform = waveform
        this.readout = readout
    }
}
