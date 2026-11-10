package com.mpaulrenlund.audiofool.art

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mpaulrenlund.audiofool.core.model.Track
import com.mpaulrenlund.audiofool.theme.ThemeLabFixtures
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Each song's cover colour, shared by Now Playing and the mini player (2026-11-10):
 * worked out once, kept, and the same whichever screen asked first.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoverColorsTest {
    private val song = Track(filePath = "/storage/emulated/0/Music/Aphelion Drive/01 Polygon Weather.flac", modifiedUtc = 1_000, fileSize = 2_000)

    private fun png(bitmap: Bitmap) = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    @Test
    fun worked_out_once_then_kept() {
        val colors = CoverColors()
        assertNull(colors.peek(song))
        var reads = 0
        val first = colors.load(song) { reads++; png(ThemeLabFixtures.coverDeepRed()) }
        assertNotNull(first.color)
        val again = colors.load(song) { reads++; error("read again") }
        assertSame(first, again)
        assertSame(first, colors.peek(song))
        assertEquals(1, reads)
    }

    @Test
    fun the_screens_agree_on_a_cover() {
        // ThemeLab's colour (from the picture) and the phone's (decoded from the file) are one rule.
        val cover = ThemeLabFixtures.coverYellow()
        val fromFile = CoverColors().load(song) { png(cover) }.color
        val forPictures = CoverColors().apply { putForPictures(song, cover) }.peek(song)?.color
        assertNotNull(fromFile)
        assertEquals(fromFile, forPictures)
    }

    @Test
    fun no_cover_or_a_grey_one_has_no_colour_and_is_kept_too() {
        val colors = CoverColors()
        assertNull(colors.load(song) { null }.color)
        assertNotNull(colors.peek(song))
        val grey = song.copy(filePath = song.filePath.replace("01", "02"))
        assertNull(colors.load(grey) { png(ThemeLabFixtures.coverBlackAndWhite()) }.color)
    }

    @Test
    fun a_changed_file_is_worked_out_afresh() {
        val colors = CoverColors()
        colors.load(song) { png(ThemeLabFixtures.coverDeepRed()) }
        assertNull(colors.peek(song.copy(modifiedUtc = 5_000)))
        assertNull(colors.peek(song.copy(fileSize = 3_000)))
    }
}
