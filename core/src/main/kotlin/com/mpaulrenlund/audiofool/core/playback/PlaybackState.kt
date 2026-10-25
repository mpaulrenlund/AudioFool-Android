package com.mpaulrenlund.audiofool.core.playback

import com.mpaulrenlund.audiofool.core.model.Track

// Ported from the desktop's `AudioFool.Core/Playback/PlaybackState.cs` and `HeardPoint.cs`.

enum class PlaybackState { Stopped, Playing, Paused }

enum class RepeatMode { Off, All, One }

/** How audio reaches the output. */
enum class OutputMode {
    /**
     * The system mixer, shared with every other app, which resamples to its own
     * rate (on a phone usually 48 kHz). The desktop's shared WASAPI.
     */
    Shared,

    /**
     * The output opened at the track's own rate with nothing in between: what
     * Bit-Perfect turns on (session 4). The desktop's exclusive WASAPI.
     */
    Exclusive,
}

/**
 * What is coming out of the speakers: the track (null for silence, nothing
 * playing) and where in it, in milliseconds.
 */
data class HeardPoint(val track: Track?, val positionMs: Long)
