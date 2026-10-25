package com.mpaulrenlund.audiofool.core.playback

import kotlin.math.PI
import kotlin.math.cos

/**
 * The fade either side of a jump in the sound: a seek, a skip, a song tapped
 * while another plays. Ported from the desktop's `AudioFool.Core/Playback/
 * OutputChain.cs` (`SeekFadeSeconds`, `Fade`): "A seek joins two unrelated
 * points of the waveform, and that step is an audible click ... 5 ms out and
 * 5 ms in brings it under a tenth." The M21 clicked at every restart and tap
 * (0.1.3) until the phone did the same.
 *
 * DoP is never faded (the desktop's `Seek`): scaling its samples breaks the
 * markers the DAC reads them by.
 */
object SeekFade {
    const val SECONDS = 0.005

    /** How many frames the fade lasts at [sampleRate]: never less than one. */
    fun frames(sampleRate: Int): Int = maxOf(1, (sampleRate * SECONDS).toInt())

    /**
     * The gain for frame [frame] of a [count]-frame raised cosine: rising from 0
     * (the first frame is silent) or falling to 0 (the last frame is silent).
     */
    fun gain(frame: Int, count: Int, rising: Boolean): Float {
        val t = if (rising) frame.toDouble() / count else (frame + 1).toDouble() / count
        return (if (rising) 0.5 - 0.5 * cos(PI * t) else 0.5 + 0.5 * cos(PI * t)).toFloat()
    }

    /**
     * Fades the start of [samples] in: [channels] interleaved, the first
     * `min(fadeFrames, frames)` frames scaled by the rising curve.
     *
     * @param get Reads sample [i]. @param set Writes it back.
     */
    inline fun fadeIn(frames: Int, channels: Int, fadeFrames: Int, get: (Int) -> Float, set: (Int, Float) -> Unit) {
        for (f in 0 until minOf(fadeFrames, frames)) {
            val g = gain(f, fadeFrames, rising = true)
            for (c in 0 until channels) {
                val i = f * channels + c
                set(i, get(i) * g)
            }
        }
    }
}
