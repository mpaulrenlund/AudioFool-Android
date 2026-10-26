package com.mpaulrenlund.audiofool.core.playback

import com.mpaulrenlund.audiofool.core.format.Display

/**
 * The output readout under Now Playing's controls and on the Settings row
 * (spec 6.3): "Shared · 48 kHz / 32-bit (resampled)" or "Exclusive · 44.1 kHz".
 * Ported from the desktop's `AudioFool.Core/Playback/OutputReadout.cs`; session 4
 * puts the route (USB, Bluetooth) in front of it ([describe] with a route).
 */
object OutputReadout {
    /**
     * @param mode The mode the output actually opened in.
     * @param deviceRate The output's sample rate, in Hz.
     * @param deviceBitDepth The output's bit depth, or 0 when unknown.
     * @param sourceRate The playing track's decoded rate, or 0 when unknown.
     * @param isDop Whether the track is passed through as DSD over PCM.
     */
    fun describe(mode: OutputMode, deviceRate: Int, deviceBitDepth: Int, sourceRate: Int, isDop: Boolean): String {
        val rate = rate(deviceRate)

        if (mode == OutputMode.Exclusive) {
            // DoP looks like ordinary high-rate PCM from the device's side, so say
            // which it is rather than leaving it ambiguous.
            return if (isDop) "Exclusive · $rate · DSD over PCM" else "Exclusive · $rate"
        }

        val depth = if (deviceBitDepth > 0) " / $deviceBitDepth-bit" else ""
        val resampled = if (sourceRate > 0 && sourceRate != deviceRate) " (resampled)" else ""
        return "Shared · $rate$depth$resampled"
    }

    /**
     * The phone's readout (spec 6.3): the output's name, the rate, the depth and a
     * note, a dot between each. "Shanling UA2 Plus · 96 kHz · 24-Bit · Exclusive",
     * "Shanling UA2 Plus · 96 kHz · 24-Bit · Rate Matched", "4.4mm · 96 kHz ·
     * 24-Bit · Exclusive", "Speaker · 48 kHz · Resampled". The note never says
     * exclusive unless the path is bit-perfect: a rate matched mixer still mixes,
     * and the M21's way is only called exclusive when the system output is seen
     * running at the track's rate.
     *
     * 0.2.2 (the user's call): no "Shared" (an output that isn't bit-perfect or rate
     * matched is shared, so it said nothing), and the words capitalised, as the
     * Now Playing quality line's "24-Bit". 0.2.5 (the user's call): the device's
     * own name in place of the route ([OutputNames]), "Exclusive" for
     * "(Bit-Perfect)" (the desktop's word), and dots in place of the brackets and
     * the "/". The desktop's [describe] by mode is unchanged.
     *
     * @param name What the output is called ([OutputNames]).
     * @param path How the output was opened.
     * @param outputRate The rate the output runs at, in Hz: the mixer entry's,
     *   or for [OutputPath.Shared] and [OutputPath.SourceRate] the system
     *   output's (`AudioTrack.getNativeOutputSampleRate`). 0 when unknown.
     * @param bits The bit depth sent, or 0 when it isn't known.
     * @param sourceRate The track's decoded rate, or 0 when unknown.
     * @param isDop DSD passed through as DSD over PCM.
     */
    fun describe(name: String, path: OutputPath, outputRate: Int, bits: Int, sourceRate: Int, isDop: Boolean): String {
        if (outputRate <= 0) return name
        val rate = rate(outputRate)
        val depth = if (bits > 0) " · $bits-Bit" else ""
        val matches = sourceRate <= 0 || sourceRate == outputRate
        return when (path) {
            OutputPath.BitPerfect ->
                if (isDop) "$name · $rate · DSD over PCM · Exclusive" else "$name · $rate$depth · Exclusive"
            OutputPath.RateMatched -> "$name · $rate$depth · Rate Matched"
            OutputPath.SourceRate ->
                if (matches) "$name · $rate$depth · Exclusive" else "$name · $rate · Resampled"
            // The shared output's depth isn't known (Android mixes in its own), as the desktop leaves it out.
            OutputPath.Shared -> if (matches) "$name · $rate" else "$name · $rate · Resampled"
        }
    }

    /**
     * The rate the M21's readout goes by (0.1.5, the user's call): the rate FiiO
     * itself reports its DAC running at (`fiio.intent.action.SAMPLE_RATE_CHANGED`'s
     * `rate`), once it has reported one for the output open now; until then, the
     * system output's (`AudioTrack.getNativeOutputSampleRate`), as before.
     *
     * @param reportedRate FiiO's rate, or 0 when it hasn't reported one.
     */
    fun hardwareRate(systemRate: Int, reportedRate: Int): Int = if (reportedRate > 0) reportedRate else systemRate

    /** "44.1 kHz", "96 kHz". */
    fun rate(hz: Int): String = Display.sampleRate(hz)
}

/** How the output stage opened the output for a track (session 4). */
enum class OutputPath {
    /** The normal shared output: Android's mixer at its own rate. */
    Shared,

    /** A USB device's bit-perfect mixer at the track's rate (`MIXER_BEHAVIOR_BIT_PERFECT`). */
    BitPerfect,

    /** A USB device's ordinary mixer, set to the track's rate: no resampling, but still mixed. */
    RateMatched,

    /** Opened at the track's own rate for the system to pass on (the M21's SRC bypass). */
    SourceRate,
}
