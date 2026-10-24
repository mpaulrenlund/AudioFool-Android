package com.mpaulrenlund.audiofool.library

import com.mpaulrenlund.audiofool.core.library.RawTags
import com.mpaulrenlund.audiofool.core.library.TagSource

/**
 * Tags through TagLib 2.x (third_party/taglib, built with the NDK): the C++
 * library the desktop's TagLibSharp is ported from, so the same fields come out.
 * It reads every format in AudioFormats that has tags, DSF and DFF included.
 */
object TagLibNative : TagSource {
    init {
        System.loadLibrary("audiofooltags")
    }

    // Slots of the array read() returns; tagreader_jni.cpp names the same ones.
    private const val STATUS = 0
    private const val TITLE = 1
    private const val ARTIST = 2
    private const val ALBUM_ARTIST = 3
    private const val ALBUM = 4
    private const val TRACK = 5
    private const val TRACK_TOTAL = 6
    private const val DISC = 7
    private const val DISC_TOTAL = 8
    private const val YEAR = 9
    private const val DATE = 10
    private const val LENGTH_MS = 11
    private const val SAMPLE_RATE = 12
    private const val BITRATE = 13
    private const val BITS_PER_SAMPLE = 14
    private const val IS_ALAC = 15

    /** UTF-8 byte arrays in, UTF-8 byte arrays out: JNI's own strings can't hold every character. */
    private external fun read(pathUtf8: ByteArray): Array<ByteArray?>
    private external fun pictures(pathUtf8: ByteArray): Array<ByteArray?>?

    override fun read(path: String): RawTags {
        val slots = read(path.toByteArray(Charsets.UTF_8))
        fun s(i: Int) = slots[i]?.toString(Charsets.UTF_8)
        fun n(i: Int) = s(i)?.toLongOrNull()

        val status = when (s(STATUS)) {
            "read" -> RawTags.Status.Read
            "unopened" -> RawTags.Status.Unopened
            else -> RawTags.Status.Unparsed
        }
        if (status != RawTags.Status.Read) return RawTags(status)

        return RawTags(
            status = status,
            title = s(TITLE),
            artist = s(ARTIST),
            albumArtist = s(ALBUM_ARTIST),
            album = s(ALBUM),
            trackNumber = s(TRACK),
            trackTotal = s(TRACK_TOTAL),
            discNumber = s(DISC),
            discTotal = s(DISC_TOTAL),
            year = n(YEAR)?.toInt(),
            date = s(DATE),
            durationMs = n(LENGTH_MS) ?: 0,
            sampleRate = n(SAMPLE_RATE)?.toInt(),
            bitrate = n(BITRATE)?.toInt(),
            bitsPerSample = n(BITS_PER_SAMPLE)?.toInt(),
            isAppleLossless = s(IS_ALAC) == "1",
        )
    }

    /** One embedded picture: TagLib's type name ("Front Cover", ...) and the image bytes. */
    class Picture(val type: String, val data: ByteArray)

    /** Every embedded picture, or null when the file couldn't be read. */
    fun embeddedPictures(path: String): List<Picture>? {
        val flat = pictures(path.toByteArray(Charsets.UTF_8)) ?: return null
        return (flat.indices step 2).mapNotNull { i ->
            val type = flat[i]?.toString(Charsets.UTF_8) ?: ""
            flat.getOrNull(i + 1)?.let { Picture(type, it) }
        }
    }
}
