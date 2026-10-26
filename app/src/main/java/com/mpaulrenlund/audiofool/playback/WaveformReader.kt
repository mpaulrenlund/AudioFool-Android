package com.mpaulrenlund.audiofool.playback

import com.mpaulrenlund.audiofool.core.analysis.WaveformLevels
import com.mpaulrenlund.audiofool.core.library.AudioFormats
import com.mpaulrenlund.audiofool.core.library.Paths
import com.mpaulrenlund.audiofool.core.model.Track
import com.un4seen.bass.BASS
import com.un4seen.bass.BASSDSD
import kotlinx.coroutines.ensureActive
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * The seekbar's waveform (spec 5): the whole file decoded on its own
 * decode-only stream, beside playback, into [WaveformLevels]. Ported from the
 * desktop's `AudioFool.Core/Analysis/Waveform.cs` (`Waveform.Read`).
 *
 * Kept per file in [folder] (the file's path, size and date make the name), so
 * each song is decoded once; a changed file gets a new one.
 */
class WaveformReader(private val folder: File, private val columns: Int = WaveformLevels.COLUMNS) {
    /** The levels for [track], from the cache or decoded now. Null when the file can't be read. */
    suspend fun levels(track: Track): FloatArray? {
        val cached = cacheFile(track)
        load(cached)?.let { return it }
        val levels = read(track.filePath) ?: return null
        save(cached, levels)
        return levels
    }

    /** Just the cache, for a waveform shown at once when it is there. */
    fun cached(track: Track): FloatArray? = load(cacheFile(track))

    private suspend fun read(path: String): FloatArray? {
        if (!File(path).isFile || !BassRuntime.initialise()) return null
        val flags = BASS.BASS_STREAM_DECODE or BASS.BASS_SAMPLE_FLOAT
        val module = AudioFormats.isModule(path)
        val stream = when {
            module -> BASS.BASS_MusicLoad(path, 0, 0, flags or BASS.BASS_MUSIC_PRESCAN, 0)
            // Converted at a low rate: plenty for loudness, and much quicker.
            AudioFormats.isDsd(path) -> BASSDSD.BASS_DSD_StreamCreateFile(path, 0, 0, flags, DSD_RATE)
            // No prescan: a VBR MP3's estimated length only decides the block size,
            // and the blocks are counted, not trusted, at the end.
            else -> BASS.BASS_StreamCreateFile(path, 0, 0, flags)
        }
        if (stream == 0) return null

        try {
            val info = BASS.BASS_CHANNELINFO()
            if (!BASS.BASS_ChannelGetInfo(stream, info) || info.freq <= 0 || info.chans <= 0) return null

            val bytes = BASS.BASS_ChannelGetLength(stream, BASS.BASS_POS_BYTE)
            val frames = if (bytes > 0) bytes / (4 * info.chans) else 0
            val levels = WaveformLevels(info.chans, frames, columns)
            val buffer = ByteBuffer.allocateDirect(16384 * info.chans * 4).order(ByteOrder.nativeOrder())
            val samples = FloatArray(16384 * info.chans)

            while (true) {
                coroutineContext.ensureActive()
                buffer.clear()
                val got = BASS.BASS_ChannelGetData(stream, buffer, buffer.capacity() or BASS.BASS_DATA_FLOAT)
                if (got <= 0) break
                val count = got / 4
                buffer.asFloatBuffer().get(samples, 0, count)
                levels.add(samples, 0, count)
            }
            return levels.finish()
        } finally {
            if (module) BASS.BASS_MusicFree(stream) else BASS.BASS_StreamFree(stream)
        }
    }

    private fun cacheFile(track: Track): File {
        val key = "${Paths.key(track.filePath)}|${track.fileSize}|${track.modifiedUtc}|$columns"
        val digest = MessageDigest.getInstance("SHA-1").digest(key.toByteArray(Charsets.UTF_8))
        return File(folder, digest.joinToString("") { "%02x".format(it) } + ".wave")
    }

    private fun load(file: File): FloatArray? = try {
        DataInputStream(file.inputStream().buffered()).use { input ->
            FloatArray(input.readInt()) { input.readFloat() }
        }
    } catch (e: IOException) {
        null
    }

    private fun save(file: File, levels: FloatArray) {
        try {
            folder.mkdirs()
            val temp = File(file.path + ".tmp")
            DataOutputStream(temp.outputStream().buffered()).use { out ->
                out.writeInt(levels.size)
                levels.forEach(out::writeFloat)
            }
            temp.renameTo(file)
        } catch (e: IOException) {
            // Only a cache: worked out again next time.
        }
    }

    private companion object {
        const val DSD_RATE = 44100
    }
}
