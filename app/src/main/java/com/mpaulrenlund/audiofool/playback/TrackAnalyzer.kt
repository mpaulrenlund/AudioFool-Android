package com.mpaulrenlund.audiofool.playback

import com.mpaulrenlund.audiofool.core.analysis.SpectrumAccumulator
import com.mpaulrenlund.audiofool.core.analysis.SpectrumAnalysis
import com.mpaulrenlund.audiofool.core.library.AudioFormats
import com.mpaulrenlund.audiofool.core.library.LibraryRelocator
import com.mpaulrenlund.audiofool.core.library.Paths
import com.un4seen.bass.BASS
import com.un4seen.bass.BASSDSD
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.coroutines.coroutineContext

/** Why a file couldn't be analysed, in a sentence for the screen. */
class AnalysisException(message: String) : Exception(message)

/**
 * Analyze (0.6.0): decodes a whole file and runs it through a
 * [SpectrumAccumulator]. Ported from the desktop's
 * `AudioFool.Core/Analysis/TrackAnalyzer.cs` (the whole-file read; the sampled
 * read is the library-wide Quality Check's, which the phone doesn't have).
 *
 * Opens its own decode-only stream, as [WaveformReader] does, so it never touches
 * the output stage and runs beside playback. Cancelling the coroutine stops it
 * at the next buffer.
 */
object TrackAnalyzer {
    /**
     * The rate DSD is converted to for analysis: DSD64 ÷ 16, DSD128 ÷ 32. High
     * enough to show the noise DSD pushes above the audio band, which is where a
     * DSD file shows what it was made from.
     */
    const val DSD_ANALYSIS_RATE = 176400

    private const val BUFFER_FRAMES = 16384

    private class Reading(val path: String) {
        @Volatile var letGo = false
    }

    private val readings = CopyOnWriteArraySet<Reading>()

    /**
     * A volume is being ejected: Android ends any process still holding a file
     * open on it a moment later, so a read from it stops at its next buffer and
     * closes the file (HANDOFF's first trap).
     */
    fun letGoOf(root: String) {
        for (reading in readings) if (LibraryRelocator.isUnder(reading.path, root)) reading.letGo = true
    }

    /** Reads all of [path]; [progress] hears 0 to 1, once per whole percent, on the reading thread. */
    suspend fun analyze(path: String, progress: (Double) -> Unit = {}): SpectrumAnalysis {
        if (!File(path).isFile) throw AnalysisException("The file isn't there. Is the SD card in?")
        if (!BassRuntime.initialise()) throw AnalysisException(BassRuntime.initError ?: "The decoder couldn't start.")

        val module = AudioFormats.isModule(path)
        val stream = open(path, module)
        if (stream == 0) throw AnalysisException("The file couldn't be decoded (error ${BASS.BASS_ErrorGetCode()}).")

        val reading = Reading(path)
        readings += reading
        try {
            val info = BASS.BASS_CHANNELINFO()
            if (!BASS.BASS_ChannelGetInfo(stream, info) || info.freq <= 0 || info.chans <= 0) {
                throw AnalysisException("The file's format couldn't be read (error ${BASS.BASS_ErrorGetCode()}).")
            }

            val bytes = BASS.BASS_ChannelGetLength(stream, BASS.BASS_POS_BYTE)
            val frames = if (bytes > 0) bytes / (4 * info.chans) else 0

            // Only a lossless PCM source has bits worth counting; a lossy decoder
            // invents the low bits, and DSD has one. The low 16 bits of origres are the resolution.
            val checkBits = !AudioFormats.isLossy(path) && !AudioFormats.isDsd(path) && !module && (info.origres and 0xFFFF) > 16
            val accumulator = SpectrumAccumulator(info.freq, info.chans, frames, checkBits = checkBits)

            val buffer = ByteBuffer.allocateDirect(BUFFER_FRAMES * info.chans * 4).order(ByteOrder.nativeOrder())
            val samples = FloatArray(BUFFER_FRAMES * info.chans)
            var read = 0L
            var reported = -1

            while (true) {
                coroutineContext.ensureActive()
                if (reading.letGo) throw AnalysisException("The SD card was removed while the file was read.")
                buffer.clear()
                val got = BASS.BASS_ChannelGetData(stream, buffer, buffer.capacity() or BASS.BASS_DATA_FLOAT)
                if (got <= 0) break
                val count = got / 4
                buffer.asFloatBuffer().get(samples, 0, count)
                accumulator.add(samples, 0, count)
                read += count / info.chans

                if (frames > 0) {
                    val percent = minOf(100, (read * 100 / frames).toInt())
                    if (percent != reported) {
                        reported = percent
                        progress(percent / 100.0)
                    }
                }
            }

            return accumulator.finish()
        } finally {
            readings -= reading
            if (module) BASS.BASS_MusicFree(stream) else BASS.BASS_StreamFree(stream)
        }
    }

    private fun open(path: String, module: Boolean): Int {
        val flags = BASS.BASS_STREAM_DECODE or BASS.BASS_SAMPLE_FLOAT
        return when {
            module -> BASS.BASS_MusicLoad(path, 0, 0, flags or BASS.BASS_MUSIC_PRESCAN, 0)
            AudioFormats.isDsd(path) -> BASSDSD.BASS_DSD_StreamCreateFile(path, 0, 0, flags, DSD_ANALYSIS_RATE)
            // Prescan gives a VBR MP3 an exact length, which the progress needs.
            Paths.extension(path).equals(".mp3", ignoreCase = true) ->
                BASS.BASS_StreamCreateFile(path, 0, 0, flags or BASS.BASS_STREAM_PRESCAN)
            else -> BASS.BASS_StreamCreateFile(path, 0, 0, flags)
        }
    }
}
