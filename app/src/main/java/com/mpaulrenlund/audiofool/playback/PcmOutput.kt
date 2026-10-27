package com.mpaulrenlund.audiofool.playback

import android.media.AudioTrack
import android.media.VolumeShaper
import android.os.SystemClock
import com.mpaulrenlund.audiofool.core.playback.SeekFade
import java.nio.ByteBuffer

/**
 * What the output stage writes samples to (0.4.0): an `AudioTrack`, as before, or the app's
 * own USB Audio driver holding the DAC ([UsbOutput]). Both take samples in the format the
 * output was opened in, play and pause, and say how many frames have been heard, so gapless
 * joins, fades and the heard position work the same through either.
 */
interface PcmOutput {
    /** Writes up to [size] bytes from [buffer] without waiting: how many went in (the position moves on), 0 when full, below 0 when the output is gone. */
    fun write(buffer: ByteBuffer, size: Int): Int

    fun play()
    fun pause()
    fun flush()
    fun release()

    val isPlaying: Boolean

    /** Frames heard since the output opened, as an unsigned count that may wrap at 32 bits. */
    fun headFrames(): Long

    /** Times the output ran dry while fed, since it opened. */
    val underrunCount: Int

    /** Audio focus's ducking; a bit-perfect output is never turned down. */
    fun setVolume(level: Float)

    /**
     * Starts [SeekFade]'s 5 ms fade-out on what is still to be heard and waits for it to be
     * heard; the result is closed once the output has been paused and emptied. Null if
     * there is nothing to fade.
     */
    fun fadeOut(): AutoCloseable?
}

/** An `AudioTrack` as the output (spec 6.1, sessions 3 and 4). */
class AudioTrackOutput(val track: AudioTrack) : PcmOutput {
    override fun write(buffer: ByteBuffer, size: Int): Int = track.write(buffer, size, AudioTrack.WRITE_NON_BLOCKING)
    override fun play() = track.play()
    override fun pause() = track.pause()
    override fun flush() = track.flush()
    override fun release() = track.release()
    override val isPlaying: Boolean get() = track.playState == AudioTrack.PLAYSTATE_PLAYING
    override fun headFrames(): Long = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
    override val underrunCount: Int get() = track.underrunCount
    override fun setVolume(level: Float) {
        track.setVolume(level)
    }

    /**
     * What was handed over can't be changed any more, so the fade is a `VolumeShaper` on the
     * track, given time to reach the speakers before the pause.
     */
    override fun fadeOut(): AutoCloseable? = runCatching {
        track.createVolumeShaper(FADE_OUT).also {
            it.apply(VolumeShaper.Operation.PLAY)
            SystemClock.sleep(FADE_OUT_WAIT_MS)
        }
    }.getOrNull()

    private companion object {
        /** [SeekFade]'s 5 ms, falling as a raised cosine does (the cubic curve is the nearest). */
        val FADE_OUT: VolumeShaper.Configuration = VolumeShaper.Configuration.Builder()
            .setDuration((SeekFade.SECONDS * 1000).toLong())
            .setCurve(floatArrayOf(0f, 1f), floatArrayOf(1f, 0f))
            .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_CUBIC)
            .build()

        /**
         * How long the fade is given before the pause: Android applies a shaper at
         * its next mix and ramps over that mix, so the fade plus a mix or two.
         */
        const val FADE_OUT_WAIT_MS = 40L
    }
}

/**
 * The DAC held by the app's own USB driver as the output (0.4.0): the samples go to the DAC
 * as they are. [onRelease] is the driver's: the stream stops, but the DAC stays the app's
 * until the driver gives it back.
 */
class UsbOutput(private val stream: UsbStream, private val sampleRate: Int, private val onRelease: () -> Unit) : PcmOutput {
    @Volatile private var playing = false

    override fun write(buffer: ByteBuffer, size: Int): Int = stream.write(buffer, size)

    override fun play() {
        playing = true
        stream.setPlaying(true)
    }

    override fun pause() {
        playing = false
        stream.setPlaying(false)
    }

    override fun flush() = stream.flush()
    override fun release() = onRelease()
    override val isPlaying: Boolean get() = playing
    override fun headFrames(): Long = stream.head() and 0xFFFFFFFFL
    override val underrunCount: Int get() = stream.counts().underruns

    override fun setVolume(level: Float) = Unit

    /** The driver fades the samples as it sends them; the transfers in flight (16 ms) go first. */
    override fun fadeOut(): AutoCloseable? {
        stream.fadeOut(SeekFade.frames(sampleRate))
        SystemClock.sleep(FADE_OUT_WAIT_MS)
        return null
    }

    private companion object {
        /** The 16 ms in flight, then the 5 ms fade, with room to spare. */
        const val FADE_OUT_WAIT_MS = 40L
    }
}
