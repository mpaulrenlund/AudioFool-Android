package com.mpaulrenlund.audiofool.core.playback

/** What the engine does when the playing track's audio runs out ([TrackEnd.decide]). */
enum class TrackEndAction {
    /** Repeat One: the same track again from its start, gapless. */
    RepeatOne,

    /** The next track, already open, joined on in the same output: gapless. */
    Splice,

    /** The next track is open but can't join this output (another rate or format): the output is reopened for it once the last track is heard. */
    Reopen,

    /** The next track's file is out for a save. The desktop only; kept so the rule reads as it does there. */
    WaitForSave,

    /** There is a next track, but it wasn't opened ahead (it wouldn't open): opened now, skipping any that won't, once the last track is heard. */
    OpenNext,

    /** The end of the queue: the output is let go once the last track is heard. */
    Finish,
}

/**
 * The decision made when a track runs out, kept apart from the engine so it can
 * be tested without a sound device.
 * Ported from the desktop's `AudioFool.Core/Playback/TrackEnd.cs`.
 *
 * On the phone the output stage opens its `AudioTrack` at the track's own rate
 * and format, as the desktop's exclusive output does, so the engine passes
 * `sharedOutput = false`: only a track at the same rate and format joins.
 */
object TrackEnd {
    /**
     * @param repeatOne Repeat is on One.
     * @param hasOutput An output is open.
     * @param nextOpened The next track's stream was opened ahead of time.
     * @param sharedOutput The output resamples every track to one rate (the desktop's shared WASAPI mixer).
     * @param nextAtOutputRate The next track's rate and format are the output's.
     * @param nextIsDop The next track was opened as DSD over PCM, which only exclusive output keeps intact.
     * @param nextMidSave The next track's file is being saved. Always false on the phone.
     * @param hasNext The play order has a track after this one.
     */
    fun decide(
        repeatOne: Boolean,
        hasOutput: Boolean,
        nextOpened: Boolean,
        sharedOutput: Boolean,
        nextAtOutputRate: Boolean,
        nextIsDop: Boolean,
        nextMidSave: Boolean,
        hasNext: Boolean,
    ): TrackEndAction {
        if (repeatOne && hasOutput) return TrackEndAction.RepeatOne

        if (nextOpened) {
            val joins = hasOutput && (if (sharedOutput) !nextIsDop else nextAtOutputRate)
            return if (joins) TrackEndAction.Splice else TrackEndAction.Reopen
        }

        if (nextMidSave) return TrackEndAction.WaitForSave

        return if (hasNext) TrackEndAction.OpenNext else TrackEndAction.Finish
    }
}
