package com.mpaulrenlund.audiofool.core.art

import com.mpaulrenlund.audiofool.core.library.DsfHeaderReader
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

// Ported from the desktop's ImageInfoTests.cs, CutOffImageTests.cs (the
// in-memory cases) and BestCoverTests.cs, with the same fixtures.

fun fixture(name: String): ByteArray =
    checkNotNull(object {}.javaClass.getResourceAsStream("/testdata/$name")) { "no fixture $name" }.use { it.readBytes() }

/** A PNG's signature and IHDR chunk, which is all the header reader looks at. */
fun png(width: Int, height: Int, filler: Int = 0): ByteArray = byteArrayOf(
    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    0, 0, 0, 13, 'I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte(),
    (width shr 24).toByte(), (width shr 16).toByte(), (width shr 8).toByte(), width.toByte(),
    (height shr 24).toByte(), (height shr 16).toByte(), (height shr 8).toByte(), height.toByte(),
) + ByteArray(filler)

/** A JPEG up to its frame header; the APP0 segment's padding is the filler. */
fun jpeg(width: Int, height: Int, filler: Int = 0): ByteArray {
    val app0Length = filler + 2
    return byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), (app0Length shr 8).toByte(), app0Length.toByte()) +
        ByteArray(filler) +
        byteArrayOf(
            0xFF.toByte(), 0xC0.toByte(), 0x00, 0x11, 0x08,
            (height shr 8).toByte(), height.toByte(), (width shr 8).toByte(), width.toByte(), 0x03,
        )
}

class ImageInfoTest {
    @Test fun reads_real_jpeg_fixture() =
        assertEquals(ImageInfo(ImageFormat.Jpeg, 8, 8), ImageInfo.read(fixture("cover.jpg")))

    @Test fun reads_png_size_from_ihdr() =
        assertEquals(ImageInfo(ImageFormat.Png, 3000, 2000), ImageInfo.read(png(3000, 2000) + byteArrayOf(8, 6, 0, 0, 0)))

    @Test fun truncated_png_is_not_read() = assertNull(ImageInfo.read(png(1400, 1400).copyOf(20)))

    @Test fun png_with_zero_size_is_not_read() = assertNull(ImageInfo.read(png(0, 1400)))

    @Test fun other_formats_are_not_read() {
        assertNull(ImageInfo.read(byteArrayOf()))
        assertNull(ImageInfo.read(byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x10, 0x00, 0x10, 0x00))) // GIF89a
        assertNull(ImageInfo.read(byteArrayOf(0x42, 0x4D, 0x00, 0x00)))                                     // BMP
    }
}

class CutOffImageTest {
    private val pictures = listOf("cover.jpg", "noise-64.jpg", "noise-64.png")

    @Test fun a_whole_picture_is_not_cut_off() {
        for (name in pictures) assertFalse(CutOffImage.isCutOff(fixture(name)), name)
    }

    @Test fun a_picture_cut_anywhere_is_cut_off() {
        for (name in pictures) {
            val whole = fixture(name)
            // A PNG that lacks only IEND's checksum has all of its picture, so it counts as whole.
            val end = if (name.endsWith(".png")) whole.size - 4 else whole.size
            for (length in 8 until end)
                assertTrue(CutOffImage.isCutOff(whole.copyOf(length)), "$name cut to $length of ${whole.size} bytes")
        }
    }

    @Test fun bytes_after_the_end_are_fine() {
        for (name in pictures) assertFalse(CutOffImage.isCutOff(fixture(name) + ByteArray(500) + byteArrayOf(0x12, 0x34)), name)
    }

    @Test fun a_cut_off_picture_padded_with_zeros_is_still_cut_off() {
        val whole = fixture("noise-64.jpg")
        assertTrue(CutOffImage.isCutOff(whole.copyOf(whole.size / 3) + ByteArray(whole.size)))
    }

    @Test fun a_thumbnail_inside_the_jpeg_is_not_taken_for_its_end() {
        // An APP1 block, as EXIF has, holding a whole JPEG with its own end marker,
        // put after SOI; then the big picture is cut off.
        val thumb = fixture("cover.jpg")
        val big = fixture("noise-64.jpg")
        val app1Length = thumb.size + 2
        val withThumb = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), (app1Length shr 8).toByte(), app1Length.toByte()) +
            thumb + big.copyOfRange(2, big.size)

        assertFalse(CutOffImage.isCutOff(withThumb))
        assertTrue(CutOffImage.isCutOff(withThumb.copyOf(withThumb.size - big.size / 2)))
    }

    @Test fun a_progressive_jpeg_needs_the_end_after_its_last_scan() {
        fun segment(marker: Int, payload: Int) =
            byteArrayOf(0xFF.toByte(), marker.toByte(), ((payload + 2) shr 8).toByte(), (payload + 2).toByte()) + ByteArray(payload)
        val scanData = byteArrayOf(0x12, 0xFF.toByte(), 0x00, 0x34, 0xFF.toByte(), 0xD3.toByte(), 0x56, 0xFF.toByte(), 0xFF.toByte(), 0x00, 0x78)
        val sof2 = byteArrayOf(0xFF.toByte(), 0xC2.toByte(), 0x00, 0x11, 0x08, 0x00, 0x40, 0x00, 0x40, 0x03) + ByteArray(9)

        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) +
            segment(0xDB, 65) + sof2 + segment(0xC4, 30) +
            segment(0xDA, 10) + scanData +
            segment(0xC4, 30) +
            segment(0xDA, 8) + scanData +
            segment(0xDA, 8) + scanData +
            byteArrayOf(0xFF.toByte(), 0xD9.toByte())

        assertFalse(CutOffImage.isCutOff(jpeg))
        assertEquals(ImageFormat.Jpeg, ImageInfo.read(jpeg)!!.format)
        for (length in 4 until jpeg.size) assertTrue(CutOffImage.isCutOff(jpeg.copyOf(length)), "cut to $length of ${jpeg.size}")
    }

    @Test fun other_formats_are_not_called_cut_off() {
        assertFalse(CutOffImage.isCutOff(byteArrayOf()))
        assertFalse(CutOffImage.isCutOff(byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x10, 0x00)))
        assertFalse(CutOffImage.isCutOff(byteArrayOf(0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00)))
    }

    @Test fun a_jpeg_laid_out_wrongly_is_not_called_cut_off() =
        assertFalse(CutOffImage.isCutOff(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x12, 0x34, 0x56, 0x78)))
}

/** Files often carry the cover two or three times; the one shown must be the best. */
class BestCoverTest {
    private fun picture(data: ByteArray, type: String = "Front Cover") = EmbeddedPicture(type, data)
    private fun cutOff(whole: ByteArray) = whole.copyOf(whole.size / 2)

    @Test fun the_largest_cover_wins_wherever_it_is_stored() {
        val small = picture(png(500, 500))
        val large = picture(png(2000, 2000))
        assertSame(large, BestCover.pick(listOf(small, large)))
        assertSame(large, BestCover.pick(listOf(large, small)))
    }

    @Test fun of_two_covers_the_same_size_the_bigger_file_wins() {
        val lean = picture(png(800, 800, filler = 100))
        val rich = picture(png(800, 800, filler = 900))
        assertSame(rich, BestCover.pick(listOf(lean, rich, lean)))
    }

    @Test fun of_two_covers_the_same_size_a_jpeg_beats_a_bigger_png() {
        val png = picture(png(800, 800, filler = 9000))
        val jpeg = picture(jpeg(800, 800, filler = 1000))
        assertSame(jpeg, BestCover.pick(listOf(png, jpeg)))
        assertSame(jpeg, BestCover.pick(listOf(jpeg, png)))
    }

    @Test fun a_larger_png_still_beats_a_smaller_jpeg() {
        val jpeg = picture(jpeg(800, 800))
        val png = picture(png(1200, 1200))
        assertSame(png, BestCover.pick(listOf(jpeg, png)))
    }

    @Test fun of_two_jpegs_the_same_size_the_bigger_file_wins() {
        val lean = picture(jpeg(500, 500, filler = 100))
        val rich = picture(jpeg(500, 500, filler = 400))
        assertSame(rich, BestCover.pick(listOf(lean, rich)))
    }

    @Test fun identical_copies_give_the_first() {
        val first = picture(png(1200, 1200))
        val copy = picture(png(1200, 1200))
        assertSame(first, BestCover.pick(listOf(first, copy)))
    }

    @Test fun a_front_cover_beats_a_larger_picture_of_another_kind() {
        val front = picture(png(500, 500))
        val back = picture(png(3000, 3000), "Back Cover")
        assertSame(front, BestCover.pick(listOf(back, front)))
    }

    @Test fun without_a_front_cover_the_largest_picture_wins() {
        val other = picture(png(500, 500), "Other")
        val back = picture(png(1000, 1000), "Back Cover")
        assertSame(back, BestCover.pick(listOf(other, back)))
    }

    @Test fun a_readable_cover_beats_a_bigger_one_that_cannot_be_measured() {
        val unreadable = picture(ByteArray(50_000))
        val readable = picture(png(600, 600))
        assertSame(readable, BestCover.pick(listOf(unreadable, readable)))
    }

    @Test fun a_whole_cover_beats_a_bigger_one_that_is_cut_off() {
        val whole = picture(fixture("cover.jpg"))             // 8 x 8
        val cut = picture(cutOff(fixture("noise-64.jpg")))    // 64 x 64, half of it
        assertSame(whole, BestCover.pick(listOf(cut, whole)))
        assertSame(whole, BestCover.pick(listOf(whole, cut)))
    }

    @Test fun a_whole_picture_of_another_kind_beats_a_cut_off_front_cover() {
        val front = picture(cutOff(fixture("noise-64.jpg")))
        val back = picture(fixture("cover.jpg"), "Back Cover")
        assertSame(back, BestCover.pick(listOf(front, back)))
    }

    @Test fun with_only_cut_off_pictures_the_usual_rules_pick_one() {
        val small = picture(cutOff(fixture("cover.jpg")))
        val large = picture(cutOff(fixture("noise-64.jpg")))
        assertSame(large, BestCover.pick(listOf(small, large)))
    }

    @Test fun no_pictures_or_only_empty_ones_give_null() {
        assertNull(BestCover.pick(emptyList()))
        assertNull(BestCover.pick(listOf(picture(byteArrayOf()))))
    }
}

class DsfHeaderReaderTest {
    @Test fun reads_the_desktops_sample_dsf() {
        val file = File.createTempFile("sample", ".dsf").apply { deleteOnExit(); writeBytes(fixture("sample.dsf")) }
        val dsf = assertNotNull(DsfHeaderReader.tryRead(file.path))
        assertEquals(2_822_400, dsf.sampleRate)   // DSD64
        assertEquals(1, dsf.bitDepth)
        assertEquals(2, dsf.channels)
        assertEquals(2_822_400 * 2 / 1000, dsf.bitrateKbps)
    }

    @Test fun anything_else_is_not_read() {
        val file = File.createTempFile("not", ".dsf").apply { deleteOnExit(); writeText("not a dsf at all, but long enough to try reading") }
        assertNull(DsfHeaderReader.tryRead(file.path))
        assertNull(DsfHeaderReader.tryRead(file.path + ".missing"))
    }
}
