package com.mpaulrenlund.audiofool.art

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.LruCache
import com.mpaulrenlund.audiofool.core.art.CoverColor
import com.mpaulrenlund.audiofool.core.model.Track
import com.mpaulrenlund.audiofool.library.CoverArt
import com.mpaulrenlund.audiofool.playback.SystemControls

/**
 * Each song's cover colour (core [CoverColor.main]), which Now Playing's controls
 * and the mini player's take (spec 3 and 5, Cover colour). Worked out once a song,
 * from its cover decoded no smaller than [DECODE_PX] a side (the lock screen's
 * size), so it is the same colour on both screens and on both phones; up to
 * 2026-11-10 Now Playing worked it out from the cover at its own size. The last
 * [capacity] songs' are kept, so a screen opening has its song's at once.
 */
class CoverColors(capacity: Int = 64) {
    /** A song's colour once worked out: [color] null when there is no cover (a black and white one gives a grey, since 2026-11-11). */
    class Known(val color: Int?)

    private val known = LruCache<String, Known>(capacity)

    /** The song's colour if it has been worked out, else null (look again after [load]). */
    fun peek(track: Track): Known? = known.get(keyOf(track))

    /**
     * The song's colour, worked out from its cover if it hasn't been. Reads and
     * decodes, so off the main thread. [read] gives the cover's bytes: Now Playing
     * passes the ones it has read already, so the file isn't read twice.
     */
    fun load(track: Track, read: () -> ByteArray? = { CoverArt.read(track)?.bytes }): Known {
        peek(track)?.let { return it }
        val bitmap = read()?.let { SystemControls.decode(it, DECODE_PX) }
        val result = Known(bitmap?.let { main(it).also { _ -> it.recycle() } })
        known.put(keyOf(track), result)
        return result
    }

    /** ThemeLab's, which can't read a cover: [track]'s colour as if worked out from [cover]. */
    fun putForPictures(track: Track, cover: Bitmap) {
        known.put(keyOf(track), Known(main(cover)))
    }

    /** A file changed since (a new cover in its tags) is a new key; its old colour drops out in time. */
    private fun keyOf(track: Track) = "${track.filePath}|${track.modifiedUtc}|${track.fileSize}"

    companion object {
        const val DECODE_PX = 512

        /**
         * The main colour of what the screens show of a cover: its middle square (the
         * art is cropped to one), scaled down to `CoverColor.SAMPLE_SIDE` pixels a side.
         * Moved here from Now Playing (2026-11-10).
         */
        fun main(bitmap: Bitmap): Int? {
            val n = CoverColor.SAMPLE_SIDE
            val side = minOf(bitmap.width, bitmap.height)
            val left = (bitmap.width - side) / 2
            val top = (bitmap.height - side) / 2
            val small = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
            Canvas(small).drawBitmap(bitmap, Rect(left, top, left + side, top + side), Rect(0, 0, n, n), Paint(Paint.FILTER_BITMAP_FLAG))
            val pixels = IntArray(n * n)
            small.getPixels(pixels, 0, n, 0, 0, n, n)
            small.recycle()
            return CoverColor.main(pixels)
        }
    }
}
