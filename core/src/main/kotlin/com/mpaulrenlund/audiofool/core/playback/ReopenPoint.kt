package com.mpaulrenlund.audiofool.core.playback

import com.mpaulrenlund.audiofool.core.model.Track

/**
 * Where a track that is closed and opened again carries on from: the app
 * reopened with the queue restored (spec 6.1, Resume), or the output reopened
 * mid-track. The decoder runs ahead of what has been heard by what the output
 * holds, so its place would skip that much.
 * Ported from the desktop's `AudioFool.Core/Playback/ReopenPoint.cs`.
 */
object ReopenPoint {
    /**
     * @param current The track being reopened.
     * @param heard What the speakers were playing (held still while paused).
     * @param decodedMs Where the decoder was in [current].
     */
    fun choose(current: Track, heard: HeardPoint, decodedMs: Long): Long {
        // Silence, or the output unable to say: the decoder's place.
        val heardTrack = heard.track ?: return decodedMs

        // The track before it is still playing out of the output (a gapless join
        // not yet heard): none of this one has been heard.
        if (heardTrack !== current) return 0

        return heard.positionMs.coerceIn(0, maxOf(0, decodedMs))
    }
}
