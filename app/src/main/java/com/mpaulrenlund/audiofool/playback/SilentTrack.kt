package com.mpaulrenlund.audiofool.playback

import android.media.AudioFormat
import android.media.AudioTrack

/**
 * An `AudioTrack` that plays nothing but zeros while the USB driver plays (0.5.1), so Android
 * counts the app as playing audio. With Bit-Perfect on the music goes to the DAC through the
 * driver and no `AudioTrack` plays, and at a switch to another app or home the whole app was
 * stopped for a second (0.5.0's Debug on the S24: the app stopped 1005 ms of a 1006 ms gap,
 * a send to the DAC 7 ms, samples 1 ms). With BP off, playing through an `AudioTrack`, it
 * never stuttered.
 *
 * Only zeros ever go in, so it can't be heard wherever Android routes it. It is a static
 * track looping a tenth of a second, so it costs the app no work while it plays.
 */
class SilentTrack {
    private var track: AudioTrack? = null

    @Volatile var playing = false
        private set

    /** Why it couldn't be made, for the debug screen; null when fine. */
    @Volatile var failed: String? = null
        private set

    @Synchronized fun play() {
        if (playing) return
        val t = track ?: build()?.also { track = it } ?: return
        runCatching { t.play() }
            .onSuccess { playing = true }
            .onFailure { failed = "wouldn't play: ${it.message}" }
    }

    @Synchronized fun pause() {
        if (!playing) return
        runCatching { track?.pause() }
        playing = false
    }

    private fun build(): AudioTrack? = try {
        val bytes = FRAMES * BYTES_PER_FRAME
        val t = AudioTrack.Builder()
            .setAudioAttributes(OutputRoutes.MEDIA)
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build(),
            )
            .setBufferSizeInBytes(bytes)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        if (t.write(ByteArray(bytes), 0, bytes) == bytes && t.setLoopPoints(0, FRAMES, -1) == AudioTrack.SUCCESS) {
            failed = null
            t
        } else {
            t.release()
            failed = "wouldn't take its zeros"
            null
        }
    } catch (e: Exception) {
        failed = "wouldn't open: ${e.message}"
        null
    }

    private companion object {
        const val RATE = 48000
        const val FRAMES = RATE / 10
        const val BYTES_PER_FRAME = 4
    }
}
