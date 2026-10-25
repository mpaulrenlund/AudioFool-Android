package com.mpaulrenlund.audiofool.playback

import com.mpaulrenlund.audiofool.core.model.Track

/**
 * Told what is being heard while playing, by [Player] on the main thread: Last.fm
 * (spec 7) and the listening time (spec 4.6).
 */
interface PlayWatcher {
    /** [track] is now the one heard while playing (a new song, or the first tick after Play). */
    fun started(track: Track)

    /** Every 250 ms while playing: where in [track] the speakers are. */
    fun position(track: Track, positionMs: Long)

    /** The queue ran out: the play is over. Pausing is not stopping. */
    fun stopped()
}
