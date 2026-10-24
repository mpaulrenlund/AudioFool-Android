package com.mpaulrenlund.audiofool.core.library

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal reader for the DSF (.dsf) header, to fill in rate, depth, bitrate and
 * length when the tag library leaves gaps. Layout per Sony's "DSF File Format
 * Specification" v1.01. Ported from the desktop's `DsfHeaderReader.cs`.
 */
object DsfHeaderReader {
    data class DsfProperties(
        val sampleRate: Int,
        val bitDepth: Int,
        val channels: Int,
        val durationMs: Long,
        val bitrateKbps: Int,
    )

    fun tryRead(path: String): DsfProperties? = try {
        RandomAccessFile(File(path), "r").use { file ->
            // "DSD " chunk: id(4) size(8) fileSize(8) metadataPointer(8) = 28 bytes, then "fmt ".
            val header = ByteArray(28 + 4 + 8 + 4 * 6 + 8)
            file.readFully(header)
            val b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            if (String(header, 0, 4, Charsets.US_ASCII) != "DSD ") return null
            if (String(header, 28, 4, Charsets.US_ASCII) != "fmt ") return null

            // chunk size(8), version(4), format id(4), channel type(4)
            val channels = b.getInt(32 + 8 + 12)
            val sampleRate = b.getInt(32 + 8 + 16)
            val bitDepth = b.getInt(32 + 8 + 20)       // 1 for DSD
            val sampleCount = b.getLong(32 + 8 + 24)

            if (sampleRate <= 0 || channels <= 0) return null

            DsfProperties(
                sampleRate = sampleRate,
                bitDepth = bitDepth,
                channels = channels,
                durationMs = sampleCount * 1000 / sampleRate,
                bitrateKbps = (sampleRate.toLong() * channels * bitDepth / 1000).toInt(),
            )
        }
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }
}
