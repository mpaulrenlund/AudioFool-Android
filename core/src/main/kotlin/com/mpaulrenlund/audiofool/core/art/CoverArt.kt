package com.mpaulrenlund.audiofool.core.art

// Ported from the desktop's AudioFool.Core/Art: ImageInfo.cs, JpegSize.cs,
// CutOffImage.cs (the in-memory half) and TagReader.BestCover, with their tests.

enum class ImageFormat { Jpeg, Png }

/**
 * The format and pixel size of a cover, read from its header bytes. The format
 * is judged by the bytes, never by a name: an embedded picture has none, and a
 * MIME type in a tag can be wrong.
 */
data class ImageInfo(val format: ImageFormat, val width: Int, val height: Int) {
    companion object {
        private val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

        /** Null for anything that is not a readable JPEG or PNG. */
        fun read(data: ByteArray): ImageInfo? {
            JpegSize.tryRead(data)?.let { (w, h) -> return ImageInfo(ImageFormat.Jpeg, w, h) }

            // PNG: the signature, then IHDR first, whose data starts with width and
            // height as big-endian 32-bit integers.
            if (data.size >= 24 && startsWith(data, pngSignature)
                && data[12] == 'I'.code.toByte() && data[13] == 'H'.code.toByte()
                && data[14] == 'D'.code.toByte() && data[15] == 'R'.code.toByte()
            ) {
                val width = int32(data, 16)
                val height = int32(data, 20)
                if (width > 0 && height > 0) return ImageInfo(ImageFormat.Png, width, height)
            }
            return null
        }

        internal fun startsWith(data: ByteArray, prefix: ByteArray) =
            data.size >= prefix.size && prefix.indices.all { data[it] == prefix[it] }

        internal fun isPng(data: ByteArray) = startsWith(data, pngSignature)

        private fun int32(d: ByteArray, i: Int) =
            (u(d[i]) shl 24) or (u(d[i + 1]) shl 16) or (u(d[i + 2]) shl 8) or u(d[i + 3])
    }
}

internal fun u(b: Byte): Int = b.toInt() and 0xFF

/** Reads a JPEG's pixel size from its frame header. */
object JpegSize {
    enum class Probe { NotJpeg, NeedMore, Found }

    /** (width, height), or null when it isn't a whole enough JPEG to say. */
    fun tryRead(data: ByteArray): Pair<Int, Int>? = probe(data).let { (p, size) -> if (p == Probe.Found) size else null }

    fun probe(data: ByteArray): Pair<Probe, Pair<Int, Int>?> {
        if (data.size < 2) return Probe.NeedMore to null
        if (u(data[0]) != 0xFF || u(data[1]) != 0xD8) return Probe.NotJpeg to null

        var i = 2
        while (true) {
            if (i + 1 >= data.size) return Probe.NeedMore to null
            if (u(data[i]) != 0xFF) return Probe.NotJpeg to null

            val marker = u(data[i + 1])
            if (marker == 0xFF) { i++; continue } // fill byte

            i += 2

            // Markers that carry no length field.
            if (marker == 0x01 || marker == 0xD8 || marker in 0xD0..0xD7) continue

            // Scan data or end of image before any frame header: not a usable JPEG.
            if (marker == 0xDA || marker == 0xD9) return Probe.NotJpeg to null

            if (i + 2 > data.size) return Probe.NeedMore to null

            val length = (u(data[i]) shl 8) or u(data[i + 1])
            if (length < 2) return Probe.NotJpeg to null

            // SOF0-SOF15, except DHT (C4), JPG (C8) and DAC (CC), which share the range.
            if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                if (i + 7 > data.size) return Probe.NeedMore to null
                val height = (u(data[i + 3]) shl 8) or u(data[i + 4])
                val width = (u(data[i + 5]) shl 8) or u(data[i + 6])
                return if (width > 0 && height > 0) Probe.Found to (width to height) else Probe.NotJpeg to null
            }

            i += length
        }
    }
}

/**
 * Tells a JPEG or PNG whose data stops before its end from a whole one. A
 * decoder can draw a cut-off JPEG without an error (the rows it has, then flat
 * grey), and the header still gives the full size, so nothing else notices.
 * Anything that isn't a JPEG or PNG, or is laid out in a way the walk doesn't
 * follow, is not called cut off: only data that runs out before the end is.
 */
object CutOffImage {
    fun isCutOff(data: ByteArray): Boolean = when {
        data.size >= 2 && u(data[0]) == 0xFF && u(data[1]) == 0xD8 -> jpegRunsOut(data)
        ImageInfo.isPng(data) -> pngRunsOut(data)
        else -> false
    }

    private fun jpegRunsOut(data: ByteArray): Boolean {
        var i = 2
        while (true) {
            if (i + 1 >= data.size) return true
            if (u(data[i]) != 0xFF) return false // not laid out as expected: not ours to judge

            val marker = u(data[i + 1])
            if (marker == 0xFF) { i++; continue } // fill byte

            i += 2
            if (marker == 0xD9) return false
            if (marker == 0x01 || marker == 0xD8 || marker in 0xD0..0xD7) continue

            if (i + 2 > data.size) return true
            val length = (u(data[i]) shl 8) or u(data[i + 1])
            if (length < 2) return false
            if (i + length > data.size) return true
            i += length

            if (marker != 0xDA) continue

            // Scan data: runs to the next marker. An FF in it is followed by 00
            // (a stuffed byte) or a restart number; anything else is a marker.
            while (true) {
                var next = i
                while (next < data.size && u(data[next]) != 0xFF) next++
                if (next >= data.size) return true
                i = next
                if (i + 1 >= data.size) return true

                val after = u(data[i + 1])
                when {
                    after == 0x00 || after in 0xD0..0xD7 -> i += 2
                    after == 0xFF -> i++
                    else -> break
                }
            }
        }
    }

    private fun pngRunsOut(data: ByteArray): Boolean {
        // Chunks: a 4-byte length, a 4-byte type, the data, a 4-byte CRC.
        var i = 8L
        while (true) {
            if (i + 8 > data.size) return true
            val at = i.toInt()
            val length = (u(data[at]).toLong() shl 24) or (u(data[at + 1]).toLong() shl 16) or
                (u(data[at + 2]).toLong() shl 8) or u(data[at + 3]).toLong()
            if (data[at + 4] == 'I'.code.toByte() && data[at + 5] == 'E'.code.toByte() &&
                data[at + 6] == 'N'.code.toByte() && data[at + 7] == 'D'.code.toByte()
            ) return false

            i += 12 + length
            if (i > data.size) return true
        }
    }
}

/** An embedded picture: its type as TagLib names it ("Front Cover", ...) and its bytes. */
class EmbeddedPicture(val type: String, val data: ByteArray) {
    val isFrontCover: Boolean get() = type.equals("Front Cover", ignoreCase = true)
}

object BestCover {
    /**
     * The picture to show when a file carries several. Many files hold two or
     * three copies of the cover, and the order says nothing about which is
     * better. A whole picture beats a cut-off one; then front covers win if there
     * are any; among them the most pixels, then a JPEG over a PNG (the user's
     * call: a PNG of the same picture is several times the size and looks no
     * different), then the most bytes, then the first. A picture whose size can't
     * be read counts as 0 pixels. Null when none has any data.
     */
    fun pick(pictures: List<EmbeddedPicture>): EmbeddedPicture? {
        var withData = pictures.filter { it.data.isNotEmpty() }
        val whole = withData.filter { ImageInfo.read(it.data) != null && !CutOffImage.isCutOff(it.data) }
        if (whole.isNotEmpty()) withData = whole

        val fronts = withData.filter { it.isFrontCover }
        val candidates = fronts.ifEmpty { withData }

        var best: EmbeddedPicture? = null
        var bestRank = Rank(-1, false, -1)
        for (picture in candidates) {
            val info = ImageInfo.read(picture.data)
            val rank = Rank(
                pixels = if (info == null) 0L else info.width.toLong() * info.height,
                jpeg = info?.format == ImageFormat.Jpeg,
                bytes = picture.data.size,
            )
            if (rank.beats(bestRank)) {
                best = picture
                bestRank = rank
            }
        }
        return best
    }

    private data class Rank(val pixels: Long, val jpeg: Boolean, val bytes: Int) {
        fun beats(b: Rank) = when {
            pixels != b.pixels -> pixels > b.pixels
            jpeg != b.jpeg -> jpeg
            else -> bytes > b.bytes
        }
    }
}
