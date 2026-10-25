package com.mpaulrenlund.audiofool.playback

import android.media.AudioFormat
import com.mpaulrenlund.audiofool.core.library.AudioFormats
import com.mpaulrenlund.audiofool.core.model.Track
import com.un4seen.bass.BASS
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The format the output stage opens for a track (spec 6.1: the track's own
 * sample rate and bit depth wherever the route allows). Two tracks with the same
 * format play through the same `AudioTrack`, which is what makes them gapless.
 */
data class PcmFormat(val sampleRate: Int, val channels: Int, val encoding: Int) {
    val bytesPerSample: Int get() = when (encoding) {
        AudioFormat.ENCODING_PCM_16BIT -> 2
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
        else -> 4
    }
    val bytesPerFrame: Int get() = bytesPerSample * channels

    /** "24-bit", "16-bit", "32-bit float". */
    val depthName: String get() = when (encoding) {
        AudioFormat.ENCODING_PCM_16BIT -> "16-bit"
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> "24-bit"
        AudioFormat.ENCODING_PCM_32BIT -> "32-bit"
        else -> "32-bit float"
    }

    /** The bit depth the readout gives (spec 6.3). */
    val bits: Int get() = when (encoding) {
        AudioFormat.ENCODING_PCM_16BIT -> 16
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
        else -> 32
    }

    fun withEncoding(encoding: Int) = copy(encoding = encoding)
}

/**
 * One track opened for decoding: a BASS decode-only stream giving 32-bit float
 * samples, which [read] converts to the output's encoding. Float holds a 24-bit
 * source exactly (and a 16-bit one), so the conversion gives back the file's own
 * samples. DSD is converted to PCM by BASSDSD at its default rate (88.2 kHz for
 * DSD64); DoP comes in session 4.
 *
 * Not thread-safe: the output stage's thread owns it.
 */
class Decoder private constructor(
    val track: Track,
    private val handle: Int,
    private val isModule: Boolean,
    /** The format the source decodes to: its rate and channels, and the encoding its bit depth asks for. */
    val format: PcmFormat,
    startMs: Long,
) {
    private val floatBuffer: ByteBuffer = ByteBuffer.allocateDirect(CHUNK_FRAMES * format.channels * 4).order(ByteOrder.nativeOrder())

    /** Frames decoded since the stream was opened or last moved, and where that was. */
    private var framesDecoded = 0L
    private var startSeconds = startMs / 1000.0

    var ended = false
        private set

    /** Where the next frame read comes from, in seconds into the track. */
    val positionSeconds: Double get() = startSeconds + framesDecoded.toDouble() / format.sampleRate

    /** The track's length as the decoder sees it, in seconds; 0 when unknown. */
    val lengthSeconds: Double = run {
        val bytes = BASS.BASS_ChannelGetLength(handle, BASS.BASS_POS_BYTE)
        if (bytes > 0) BASS.BASS_ChannelBytes2Seconds(handle, bytes) else 0.0
    }

    /**
     * Decodes up to one chunk into [out] (cleared first), converted to
     * [encoding]: whole frames, in the output's byte order. False at the end of
     * the track or on a read error.
     */
    fun read(out: ByteBuffer, encoding: Int): Boolean {
        out.clear()
        if (ended) return false
        floatBuffer.clear()
        val got = BASS.BASS_ChannelGetData(handle, floatBuffer, floatBuffer.capacity() or BASS.BASS_DATA_FLOAT)
        if (got <= 0) {
            ended = true
            return false
        }
        val samples = got / 4
        val frames = samples / format.channels
        val floats = floatBuffer.asFloatBuffer()
        when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> for (i in 0 until samples) out.putFloat(floats.get(i))
            AudioFormat.ENCODING_PCM_16BIT -> for (i in 0 until samples) out.putShort(toInt(floats.get(i), 32768.0, 32767).toShort())
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> for (i in 0 until samples) {
                val v = toInt(floats.get(i), 8388608.0, 8388607)
                out.put(v.toByte())
                out.put((v shr 8).toByte())
                out.put((v shr 16).toByte())
            }
            else -> for (i in 0 until samples) out.putInt(toInt(floats.get(i), 2147483648.0, Int.MAX_VALUE))
        }
        out.flip()
        framesDecoded += frames
        return true
    }

    /** Moves to [ms] into the track. False when BASS can't seek there. */
    fun seek(ms: Long): Boolean {
        val bytes = BASS.BASS_ChannelSeconds2Bytes(handle, maxOf(0L, ms) / 1000.0)
        if (bytes < 0 || !BASS.BASS_ChannelSetPosition(handle, bytes, BASS.BASS_POS_BYTE)) return false
        startSeconds = BASS.BASS_ChannelBytes2Seconds(handle, bytes)
        framesDecoded = 0
        ended = false
        return true
    }

    fun close() {
        if (isModule) BASS.BASS_MusicFree(handle) else BASS.BASS_StreamFree(handle)
    }

    companion object {
        /** About 85 ms at 48 kHz: small enough to hand over often, big enough to cost little. */
        const val CHUNK_FRAMES = 4096

        /**
         * Opens [track] at [startMs], or null when the file isn't there (a removed
         * SD card) or BASS can't decode it. [lastError] says which.
         */
        fun open(track: Track, startMs: Long = 0): Decoder? {
            val path = track.filePath
            if (!File(path).isFile) {
                lastError = "not found"
                return null
            }
            val flags = BASS.BASS_STREAM_DECODE or BASS.BASS_SAMPLE_FLOAT
            val module = AudioFormats.isModule(path)
            val handle = if (module) {
                BASS.BASS_MusicLoad(path, 0, 0, flags or BASS.BASS_MUSIC_PRESCAN, 0)
            } else {
                // MP3s are prescanned so seeking (and resuming) lands where it should.
                BASS.BASS_StreamCreateFile(path, 0, 0, flags or if (path.endsWith(".mp3", ignoreCase = true)) BASS.BASS_STREAM_PRESCAN else 0)
            }
            if (handle == 0) {
                lastError = "BASS error ${BASS.BASS_ErrorGetCode()}"
                return null
            }

            val info = BASS.BASS_CHANNELINFO()
            if (!BASS.BASS_ChannelGetInfo(handle, info) || info.freq <= 0 || info.chans <= 0) {
                if (module) BASS.BASS_MusicFree(handle) else BASS.BASS_StreamFree(handle)
                lastError = "no stream info"
                return null
            }

            val decoder = Decoder(track, handle, module, PcmFormat(info.freq, info.chans, encodingFor(track)), 0)
            if (startMs > 0) decoder.seek(startMs)
            return decoder
        }

        @Volatile var lastError: String = ""
            private set

        /**
         * The encoding a track's own bit depth asks for: 16-bit sources as 16-bit,
         * 24- and 32-bit as 24-bit (what float carries exactly); lossy files and
         * DSD converted to PCM have no bit depth of their own, so float.
         */
        private fun encodingFor(track: Track): Int {
            if (AudioFormats.isDsd(track.filePath)) return AudioFormat.ENCODING_PCM_FLOAT
            val bits = track.bitDepth ?: return AudioFormat.ENCODING_PCM_FLOAT
            return if (bits <= 16) AudioFormat.ENCODING_PCM_16BIT else AudioFormat.ENCODING_PCM_24BIT_PACKED
        }

        private fun toInt(sample: Float, scale: Double, max: Int): Int {
            val v = Math.round(sample * scale)
            return v.coerceIn(-max - 1L, max.toLong()).toInt()
        }
    }
}
