package com.mpaulrenlund.audiofool.core.scrobbling

import com.mpaulrenlund.audiofool.core.model.Track

/**
 * Decides when a play has earned a scrobble, by Last.fm's rule: the track is
 * longer than 30 seconds and has been played for half its length or for four
 * minutes, whichever comes first. Ported from the desktop's
 * `AudioFool.Core/Scrobbling/PlayTracker.cs`.
 *
 * Listening time is counted from the audio position, not the clock. The caller
 * reports the position a few times a second while playing, and only small
 * forward steps count. So a seek forward is not listening, a pause or the phone
 * sleeping adds nothing, and a jump back to the first few seconds is a new play,
 * which is how a Repeat-One loop and a Previous that restarts the track are
 * recognised, since the player raises no event for either.
 *
 * Pure and clock-free: every time comes in as an argument, in milliseconds.
 */
class PlayTracker {
    private var track: Track? = null
    private var durationMs = 0L
    private var playedMs = 0L
    private var lastPositionMs = 0L
    private var startedAtMs = 0L
    private var scrobbled = false

    val current: Track? get() = track
    val played: Long get() = playedMs

    /**
     * A track began playing from the start. Returns true when this is a new
     * play, which is when "now playing" should be sent.
     *
     * The same file starting again before it has scrobbled continues the play
     * rather than starting over: switching Bit-Perfect reopens the current track,
     * and that should not throw away the minute already heard.
     */
    fun start(track: Track, durationMs: Long, nowMs: Long): Boolean {
        val sameUnfinished = this.track != null && !scrobbled &&
            this.track!!.filePath.equals(track.filePath, ignoreCase = true)

        this.track = track
        this.durationMs = durationMs
        lastPositionMs = 0

        if (sameUnfinished) return false

        playedMs = 0
        startedAtMs = nowMs
        scrobbled = false
        return true
    }

    /**
     * The tracked file's tags were saved mid-play. Swaps in the new copy so the
     * scrobble carries what the file says now; the play itself (time heard, start
     * time, whether it has scrobbled) is untouched. Returns false when [track] is
     * not the file being tracked.
     */
    fun retag(track: Track): Boolean {
        val current = this.track ?: return false
        if (!current.filePath.equals(track.filePath, ignoreCase = true)) return false
        this.track = track
        return true
    }

    /** Playback stopped: the play is over, scrobbled or not. */
    fun stop() {
        track = null
    }

    /**
     * Reports the current position. Returns [AdvanceResult.restarted] when the
     * track jumped back to its start, and the scrobble (exactly once per play)
     * when this step crossed the threshold.
     */
    fun advance(positionMs: Long, nowMs: Long): AdvanceResult {
        val track = this.track ?: return AdvanceResult()

        val step = positionMs - lastPositionMs
        var restarted = false

        if (step < 0) {
            if (positionMs < RESTART_WINDOW_MS && lastPositionMs >= RESTART_WINDOW_MS) {
                playedMs = positionMs
                startedAtMs = nowMs - positionMs
                scrobbled = false
                restarted = true
            }
        } else if (step <= MAX_STEP_MS) {
            playedMs += step
        }

        lastPositionMs = positionMs

        var due: ScrobbleEntry? = null
        val threshold = threshold(durationMs)
        if (!scrobbled && threshold != null && playedMs >= threshold) {
            scrobbled = true
            due = ScrobbleEntry.from(track, durationMs, startedAtMs)
        }

        return AdvanceResult(restarted, due)
    }

    /** The play as "now playing" would describe it. */
    fun nowPlaying(): ScrobbleEntry? = track?.let { ScrobbleEntry.from(it, durationMs, startedAtMs) }

    companion object {
        const val MINIMUM_LENGTH_MS = 30_000L
        const val MAXIMUM_THRESHOLD_MS = 4 * 60_000L

        /**
         * The largest position step counted as listening. Ticks come every 250 ms;
         * this leaves room for a busy main thread while still treating a short skip
         * forward as a seek.
         */
        const val MAX_STEP_MS = 3_000L

        /** A jump back to before this point starts a new play. */
        const val RESTART_WINDOW_MS = 3_000L

        /**
         * How long a track must be played to scrobble, or null if it is too short
         * ever to scrobble (or its length is unknown).
         */
        fun threshold(durationMs: Long): Long? = when {
            durationMs <= MINIMUM_LENGTH_MS -> null
            durationMs / 2 < MAXIMUM_THRESHOLD_MS -> durationMs / 2
            else -> MAXIMUM_THRESHOLD_MS
        }
    }
}

data class AdvanceResult(val restarted: Boolean = false, val due: ScrobbleEntry? = null)
