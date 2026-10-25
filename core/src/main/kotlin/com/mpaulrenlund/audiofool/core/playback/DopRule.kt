package com.mpaulrenlund.audiofool.core.playback

/**
 * When DSD goes out as DSD over PCM (DoP): the DAC reads markers hidden in the
 * samples, so they must reach it untouched, which only a bit-perfect output at
 * the DoP rate does. Anything else (Android's mixer resampling or changing the
 * volume) turns them into hiss, and the file is converted to PCM instead.
 *
 * Ported from the desktop's `AudioFool.Core/Playback/DopRule.cs`. "Exclusive"
 * there is Android's bit-perfect mixer here ([OutputMode.Exclusive]); a
 * rate-matched mixer still mixes, so it counts as shared.
 */
object DopRule {
    /**
     * Whether a DSD file is opened as DoP: Bit-Perfect is on, the device can be
     * clocked at the DoP rate bit-perfectly, and setting it hasn't been refused.
     */
    fun mayOpen(requested: OutputMode, dopRate: Int, exclusiveRates: Collection<Int>, refusedRates: Collection<Int>): Boolean =
        requested == OutputMode.Exclusive && dopRate > 0 && dopRate in exclusiveRates && dopRate !in refusedRates

    /** Whether a DoP stream at [dopRate] may play on this output: bit-perfect, at its rate. */
    fun mayPlayOn(outputMode: OutputMode, outputRate: Int, dopRate: Int): Boolean =
        outputMode == OutputMode.Exclusive && outputRate == dopRate
}
