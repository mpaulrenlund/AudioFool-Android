package com.mpaulrenlund.audiofool.core.playback

import com.mpaulrenlund.audiofool.core.model.Track
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The time spent listening (spec 4.6), recorded by the app from [sinceUtc] on.
 * New for the phone: the desktop has no such figure. Saved as a small JSON file
 * with [com.mpaulrenlund.audiofool.core.library.SafeFile].
 */
@Serializable
data class ListeningTime(val totalMs: Long = 0, val sinceUtc: Long = 0) {
    fun encode(): ByteArray = json.encodeToString(serializer(), this).toByteArray(Charsets.UTF_8)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(bytes: ByteArray): ListeningTime? =
            json.decodeFromString(serializer(), bytes.toString(Charsets.UTF_8)).takeIf { it.totalMs >= 0 }
    }
}

/**
 * Turns the heard position, reported every 250 ms while playing, into time
 * listened, by the rule [com.mpaulrenlund.audiofool.core.scrobbling.PlayTracker]
 * counts a play with: only small forward steps of the same track count. A seek
 * forward is not listening, and a pause adds nothing.
 */
class ListeningCounter {
    private var track: Track? = null
    private var lastPositionMs = 0L

    /** The milliseconds listened since the last report: 0 for a seek, a new track or a pause. */
    fun heard(track: Track, positionMs: Long): Long {
        val sameTrack = track === this.track
        val step = positionMs - lastPositionMs
        this.track = track
        lastPositionMs = positionMs
        return if (sameTrack && step > 0 && step <= MAX_STEP_MS) step else 0
    }

    /** Playback stopped: the next report starts afresh. */
    fun reset() {
        track = null
    }

    private companion object {
        /** As PlayTracker.MAX_STEP_MS: room for a busy main thread, while a short skip still reads as a seek. */
        const val MAX_STEP_MS = 3_000L
    }
}
