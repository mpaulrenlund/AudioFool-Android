package com.mpaulrenlund.audiofool.core.playback

import com.mpaulrenlund.audiofool.core.format.Display

/**
 * The output readout under Now Playing's controls and on the Settings row
 * (spec 6.3): "Shared · 48 kHz / 32-bit (resampled)" or "Exclusive · 44.1 kHz".
 * Ported from the desktop's `AudioFool.Core/Playback/OutputReadout.cs`; session 4
 * puts the route (USB, Bluetooth) in front of it.
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

    /** "44.1 kHz", "96 kHz". */
    fun rate(hz: Int): String = Display.sampleRate(hz)
}
