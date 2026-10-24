package com.mpaulrenlund.audiofool.art

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.mpaulrenlund.audiofool.core.art.ArtDiskCache
import com.mpaulrenlund.audiofool.core.art.BestCover
import com.mpaulrenlund.audiofool.core.art.CutOffImage
import com.mpaulrenlund.audiofool.core.art.EmbeddedPicture
import com.mpaulrenlund.audiofool.core.model.Album
import com.mpaulrenlund.audiofool.library.TagLibNative
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Album covers at the size they are shown (spec 8), from the desktop's
 * `AlbumArtService`: a cover is looked for in the album's first five tracks, then
 * its folder's cover file; whole pictures before cut-off ones. Each size keeps a
 * small copy on disk ([ArtDiskCache]), so after a restart the lists fill from the
 * app's own storage rather than the SD card, and decoded copies stay in memory,
 * bounded by bytes and dropped least-recently-used first.
 *
 * [widthPx] is the shorter side a copy is shrunk to; the picture keeps its shape
 * and is cropped square where it is shown.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Thumbnails(folder: File, val widthPx: Int, private val scope: CoroutineScope) {
    /** A cover, or [NONE] for an album known to have none. */
    class Entry(val image: ImageBitmap?)

    private val disk = ArtDiskCache(folder.path)
    private val memory = object : LruCache<String, Entry>(memoryBytes()) {
        override fun sizeOf(key: String, value: Entry): Int = value.image?.let { it.width * it.height * 4 } ?: 64
    }
    private val loading = ConcurrentHashMap<String, Deferred<Entry?>>()

    // Reading covers off an SD card: a few at a time, so the list's own work isn't starved.
    private val readers = Dispatchers.IO.limitedParallelism(3)

    /** How many albums the background fill has made a copy for, or found one for (the debug screen). */
    var prefilled by mutableIntStateOf(0)
        private set
    var prefillTotal by mutableIntStateOf(0)
        private set
    private var prefillJob: Job? = null

    fun keyFor(album: Album): String = ArtDiskCache.keyFor(album, widthPx)

    /** What memory holds for [key] already, so a row scrolled back into view draws its cover at once. */
    fun peek(key: String): Entry? = memory.get(key)

    /** The cover for [album]: from memory, the disk copy, or the music files (then saved). Null when it couldn't be read. */
    suspend fun load(album: Album, key: String = keyFor(album)): Entry? {
        memory.get(key)?.let { return it }
        val job = loading.computeIfAbsent(key) {
            scope.async(readers) {
                try {
                    loadUncached(album, key)?.also { memory.put(key, it) }
                } finally {
                    loading.remove(key)
                }
            }
        }
        return job.await()
    }

    private fun loadUncached(album: Album, key: String): Entry? {
        disk.tryRead(key)?.let { saved ->
            val bytes = saved.image ?: return NONE // looked before: no cover
            decode(bytes, 0)?.let { return Entry(it.asImageBitmap()) }
        }
        return fromSource(album, key, save = true)
    }

    /**
     * Reads the cover from the tracks or the folder and keeps a copy. "No cover"
     * is kept only when every file looked in was read: a removed SD card or a file
     * another app holds must not be remembered as an album without art.
     */
    private fun fromSource(album: Album, key: String, save: Boolean): Entry? {
        var certain = album.tracks.isNotEmpty()
        val pictures = sequence {
            for (track in album.tracks.take(ArtDiskCache.TRACKS_LOOKED_IN)) {
                val read = TagLibNative.embeddedPictures(track.filePath)
                if (read == null) certain = false
                yield(read?.map { EmbeddedPicture(it.type, it.data) }?.let(BestCover::pick)?.data)
            }
            yield(album.folderArtPath?.let(::readFile))
        }
        val bitmap = CutOffImage.wholeFirst(pictures).firstNotNullOfOrNull { decode(it, widthPx) }
        if (bitmap == null && album.folderArtPath != null) certain = false

        if (bitmap != null) {
            if (save) encode(bitmap)?.let { disk.write(key, it) }
            return Entry(bitmap.asImageBitmap())
        }
        if (!certain) return null
        if (save) disk.write(key, null)
        return NONE
    }

    /**
     * Makes the saved copies of albums that have none yet, one at a time in the
     * background, newest albums first (what the Artists list shows), as the
     * desktop's background prefill does. Leaves the memory cache alone, so filling
     * in a whole library doesn't push out what is on screen.
     */
    fun prefill(albums: List<Album>) {
        prefillJob?.cancel()
        prefillJob = scope.launch(Dispatchers.IO.limitedParallelism(1)) {
            prefillTotal = albums.size
            prefilled = 0
            for (album in albums) {
                val key = keyFor(album)
                if (!disk.contains(key) && loading[key] == null) fromSource(album, key, save = true)
                prefilled++
            }
        }
    }

    /** Deletes copies untouched for 90 days (ArtDiskCache.prune). */
    suspend fun prune(): Int = withContext(Dispatchers.IO) { disk.prune(System.currentTimeMillis()) }

    /** Decodes [bytes] with its shorter side brought down to about [width] (0: as saved). */
    private fun decode(bytes: ByteArray, width: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val shorter = minOf(bounds.outWidth, bounds.outHeight)
        if (shorter <= 0) return null
        val options = BitmapFactory.Options().apply {
            if (width > 0) inSampleSize = Integer.highestOneBit(max(1, shorter / width))
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        if (width <= 0) return decoded
        val scale = width.toFloat() / minOf(decoded.width, decoded.height)
        if (scale >= 1f) return decoded
        return Bitmap.createScaledBitmap(decoded, (decoded.width * scale).roundToInt(), (decoded.height * scale).roundToInt(), true)
            .also { if (it !== decoded) decoded.recycle() }
    }

    /** JPEG at quality 90; PNG when the picture has transparency, which JPEG would lose (the desktop's rule). */
    private fun encode(bitmap: Bitmap): ByteArray? = try {
        ByteArrayOutputStream().use { out ->
            val format = if (bitmap.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            if (bitmap.compress(format, 90, out)) out.toByteArray() else null
        }
    } catch (e: IllegalStateException) {
        null
    }

    private fun readFile(path: String): ByteArray? = try {
        File(path).readBytes()
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }

    companion object {
        val NONE = Entry(null)

        /** An eighth of the app's heap. */
        private fun memoryBytes(): Int = (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
