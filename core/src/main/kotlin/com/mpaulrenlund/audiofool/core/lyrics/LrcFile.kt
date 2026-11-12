package com.mpaulrenlund.audiofool.core.lyrics

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * A song's own .lrc file: beside it, with the same name ("03 Song.flac" →
 * "03 Song.lrc"), as LRCGET, foobar2000 and most players keep them. Read only;
 * nothing here writes one.
 */
object LrcFile {
    /** Larger than any lyrics; anything bigger isn't one. */
    const val MAX_BYTES = 1_000_000L

    /** Where the song's .lrc file would be: the song's path with its extension swapped, in both cases. */
    fun candidates(songPath: String): List<String> {
        val slash = songPath.lastIndexOf('/')
        val dot = songPath.lastIndexOf('.')
        val base = if (dot > slash) songPath.substring(0, dot) else songPath
        return listOf("$base.lrc", "$base.LRC")
    }

    /** The text of the song's .lrc file, or null when it has none (or it can't be read: the SD card out). */
    fun read(songPath: String): String? {
        for (path in candidates(songPath)) {
            val file = File(path)
            try {
                if (!file.isFile || file.length() > MAX_BYTES) continue
                return decode(file.readBytes())
            } catch (e: IOException) {
                continue
            } catch (e: SecurityException) {
                continue
            }
        }
        return null
    }

    /**
     * The file's text: UTF-8 (with or without its mark), UTF-16 with its mark, and
     * otherwise Windows' Western code page, which older Windows tools wrote.
     */
    fun decode(bytes: ByteArray): String {
        fun startsWith(vararg b: Int) = bytes.size >= b.size && b.indices.all { bytes[it] == b[it].toByte() }
        return when {
            startsWith(0xEF, 0xBB, 0xBF) -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            startsWith(0xFF, 0xFE) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            startsWith(0xFE, 0xFF) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            else -> try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            } catch (e: CharacterCodingException) {
                String(bytes, Charset.forName("windows-1252"))
            }
        }
    }
}
