package com.mpaulrenlund.audiofool.core.library

import java.io.EOFException
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Where a file's tags end and its music begins, for FLAC, MP3 and DSF, read from
 * the bytes alone: what a tag save may change and what it must leave exactly as
 * it was. Ported from the desktop's `TagPadding.Read` (its layout parsing; the
 * phone doesn't rewrite padding) and `CoverCleaner.AudioHash`, to check a save
 * made on a copy before it is swapped in ([TagSaver]).
 *
 * [audioStart] is the first byte after the tags at the front (in a DSF, after its
 * 28-byte header, whose file size changes with the tag); [audioEnd] is where a
 * DSF's tag begins, after the music, and null for FLAC and MP3, whose music runs
 * to the end bar an ID3v1 or APE tag there.
 *
 * Only layouts understood whole are read: ID3v2 tags (one or several) before the
 * audio or before a FLAC's "fLaC", then the FLAC's metadata blocks; or a DSF whose
 * header points at its one ID3v2 tag. An ID3v2 tag that is unsynchronised, has an
 * extended header or a footer, or whose frames don't walk cleanly to zeros, gives
 * null, and so does anything else.
 */
data class TagLayout(val audioStart: Long, val audioEnd: Long? = null) {
    companion object {
        fun read(path: String): TagLayout? = try {
            RandomAccessFile(path, "r").use { read(it) }
        } catch (e: IOException) {
            null
        }

        fun read(file: RandomAccessFile): TagLayout? {
            if (isDsf(file)) return dsf(file)
            var position = 0L
            var tags = 0
            val head = ByteArray(10)
            while (true) {
                file.seek(position)
                if (readUpTo(file, head, 0, 4) < 4) return null
                if (head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte()) {
                    if (readUpTo(file, head, 4, 6) < 6) return null
                    val end = id3v2End(file, position, head) ?: return null
                    tags++
                    position = end
                    continue
                }
                if (head[0] == 'f'.code.toByte() && head[1] == 'L'.code.toByte() && head[2] == 'a'.code.toByte() && head[3] == 'C'.code.toByte()) {
                    return flacAudioStart(file, position + 4)?.let { TagLayout(it) }
                }
                // Anything else after the tags is audio; with no tag at all, not a layout this knows.
                return if (tags > 0) TagLayout(position) else null
            }
        }

        private fun isDsf(file: RandomAccessFile): Boolean {
            val id = ByteArray(4)
            file.seek(0)
            return readUpTo(file, id, 0, 4) == 4 && String(id, Charsets.US_ASCII) == "DSD "
        }

        /**
         * A DSF's header: "DSD ", a chunk size of 28, the file size, then where the
         * ID3v2 tag starts. The file size must be where the tag ends, or one more,
         * which is what TagLib writes. Null when it doesn't check out or there's no tag.
         */
        private fun dsf(file: RandomAccessFile): TagLayout? {
            val header = ByteArray(28)
            file.seek(0)
            if (readUpTo(file, header, 0, 28) < 28) return null
            val chunkSize = le64(header, 4)
            val fileSize = le64(header, 12)
            val pointer = le64(header, 20)
            if (chunkSize != 28L || pointer < 28 || pointer + 10 > file.length()) return null
            val head = ByteArray(10)
            file.seek(pointer)
            file.readFully(head)
            if (!(head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte())) return null
            val end = id3v2End(file, pointer, head) ?: return null
            if (fileSize != end && fileSize != end + 1) return null
            return TagLayout(28, pointer)
        }

        /** Where the metadata blocks from [start] end; null if they run past the end or there's no STREAMINFO first. */
        private fun flacAudioStart(file: RandomAccessFile, start: Long): Long? {
            val header = ByteArray(4)
            var position = start
            var blocks = 0
            while (true) {
                file.seek(position)
                if (readUpTo(file, header, 0, 4) < 4) return null
                val type = header[0].toInt() and 0x7F
                val length = ((header[1].toInt() and 0xFF) shl 16) or ((header[2].toInt() and 0xFF) shl 8) or (header[3].toInt() and 0xFF)
                if (type == 127 || (blocks == 0 && type != 0) || position + 4 + length > file.length()) return null
                blocks++
                position += 4 + length
                if (header[0].toInt() and 0x80 != 0) return position
            }
        }

        /**
         * Walks an ID3v2.2–2.4 tag's frames to where the padding starts and checks the
         * padding is all zeros; the tag's end, or null. [head] is the 10-byte header at [start].
         */
        private fun id3v2End(file: RandomAccessFile, start: Long, head: ByteArray): Long? {
            val version = head[3].toInt() and 0xFF
            val flags = head[5].toInt() and 0xFF
            // Unsynchronisation, an extended header, a footer: not walked here.
            if (version < 2 || version > 4 || flags and 0xF0 != 0) return null
            if ((head[6].toInt() or head[7].toInt() or head[8].toInt() or head[9].toInt()) and 0x80 != 0) return null
            val size = ((head[6].toInt() shl 21) or (head[7].toInt() shl 14) or (head[8].toInt() shl 7) or head[9].toInt()).toLong()
            val end = start + 10 + size
            if (end > file.length()) return null

            val tag = ByteArray(size.toInt())
            file.seek(start + 10)
            file.readFully(tag)

            val idLength = if (version == 2) 3 else 4
            val headerLength = if (version == 2) 6 else 10
            var pos = 0
            while (pos + headerLength <= size && tag[pos].toInt() != 0) {
                for (i in 0 until idLength) {
                    val c = tag[pos + i].toInt().toChar()
                    if (!(c in 'A'..'Z' || c in '0'..'9')) return null
                }
                fun b(i: Int) = tag[pos + i].toLong() and 0xFF
                val frameSize = when (version) {
                    2 -> (b(3) shl 16) or (b(4) shl 8) or b(5)
                    3 -> (b(4) shl 24) or (b(5) shl 16) or (b(6) shl 8) or b(7)
                    else -> if ((b(4) or b(5) or b(6) or b(7)) and 0x80 != 0L) -1 else (b(4) shl 21) or (b(5) shl 14) or (b(6) shl 7) or b(7)
                }
                if (frameSize <= 0 || pos + headerLength + frameSize > size) return null
                pos += headerLength + frameSize.toInt()
            }
            for (i in pos until size.toInt()) if (tag[i].toInt() != 0) return null
            return end
        }

        /**
         * SHA-256 of the music: from [layout]'s start to its end, or for FLAC and MP3 to
         * the end of the file less an ID3v1 tag and an APE tag there (those are tags,
         * which the save may change).
         */
        fun audioDigest(path: String, layout: TagLayout): ByteArray = RandomAccessFile(path, "r").use { file ->
            val start = layout.audioStart
            var end = layout.audioEnd ?: file.length()
            if (layout.audioEnd == null && end - start >= 128) {
                val tag = ByteArray(3)
                file.seek(end - 128)
                file.readFully(tag)
                if (String(tag, Charsets.US_ASCII) == "TAG") end -= 128
            }
            // APEv2: a 32-byte footer whose size covers the items and the footer, plus a 32-byte header if flagged.
            if (layout.audioEnd == null && end - start >= 32) {
                val footer = ByteArray(32)
                file.seek(end - 32)
                file.readFully(footer)
                if (String(footer, 0, 8, Charsets.US_ASCII) == "APETAGEX") {
                    val size = le32(footer, 12)
                    val hasHeader = footer[23].toInt() and 0x80 != 0
                    val tagStart = end - size - if (hasHeader) 32 else 0
                    if (tagStart >= start) end = tagStart
                }
            }
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(1 shl 20)
            file.seek(start)
            var left = end - start
            while (left > 0) {
                val read = file.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                if (read <= 0) throw EOFException()
                digest.update(buffer, 0, read)
                left -= read
            }
            digest.digest()
        }

        private fun readUpTo(file: RandomAccessFile, into: ByteArray, offset: Int, count: Int): Int {
            var total = 0
            while (total < count) {
                val n = file.read(into, offset + total, count - total)
                if (n <= 0) break
                total += n
            }
            return total
        }

        private fun le64(b: ByteArray, at: Int): Long {
            var v = 0L
            for (i in 7 downTo 0) v = (v shl 8) or (b[at + i].toLong() and 0xFF)
            return v
        }

        private fun le32(b: ByteArray, at: Int): Long {
            var v = 0L
            for (i in 3 downTo 0) v = (v shl 8) or (b[at + i].toLong() and 0xFF)
            return v
        }
    }
}
