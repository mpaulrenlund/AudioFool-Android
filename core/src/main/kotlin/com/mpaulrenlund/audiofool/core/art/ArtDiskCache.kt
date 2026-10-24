package com.mpaulrenlund.audiofool.core.art

import com.mpaulrenlund.audiofool.core.library.Paths
import com.mpaulrenlund.audiofool.core.model.Album
import com.mpaulrenlund.audiofool.core.model.Track
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/**
 * Small copies of album covers, already shrunk to the size they are shown at,
 * kept in the app's own storage. Ported from the desktop's
 * `AudioFool.Core/Art/ArtDiskCache.cs` (its session 47). Showing a cover otherwise
 * means opening an audio file on the music storage and shrinking a picture of
 * typically 300 KB, sometimes 12 MB; on the M21's SD card that makes the lists
 * slow to fill.
 *
 * A copy is named by a hash of the width and the first five tracks' file names,
 * sizes and write times (the tracks the cover is looked for in), so it is found
 * again after the files move, and a retag or a new cover gives a new name rather
 * than a stale picture. A zero-length copy records "no cover". Not noticed: a
 * cover file in the folder changed by another program while no track changed.
 *
 * Best effort throughout: any file error reads as "no copy" or is skipped.
 */
class ArtDiskCache(val folder: String) {
    /** The result of [tryRead]: no copy at all, or a copy that is either a picture or "no cover" (null). */
    class Saved(val image: ByteArray?)

    fun pathFor(key: String): String = File(folder, sha1Hex(key) + EXTENSION).path

    fun contains(key: String): Boolean = File(pathFor(key)).exists()

    /**
     * Null when there is no copy. A [Saved] with a null image when the album was
     * found to have no cover, or with the copy's bytes.
     */
    fun tryRead(key: String, nowUtc: Long = System.currentTimeMillis()): Saved? {
        val file = File(pathFor(key))
        return try {
            if (!file.exists()) return null
            val bytes = file.readBytes()
            if (nowUtc - file.lastModified() > TOUCH_AFTER_MS) file.setLastModified(nowUtc)
            Saved(if (bytes.isEmpty()) null else bytes)
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    /**
     * Saves a copy; null records "no cover". Written to a temporary file and moved
     * into place, so a copy is never half there.
     */
    fun write(key: String, image: ByteArray?) {
        val path = File(pathFor(key))
        val temp = File(path.path + "." + UUID.randomUUID().toString().replace("-", "") + ".tmp")
        try {
            File(folder).mkdirs()
            temp.writeBytes(image ?: ByteArray(0))
            if (!temp.renameTo(path)) {
                path.delete()
                if (!temp.renameTo(path)) temp.delete()
            }
        } catch (e: IOException) {
            temp.delete()
        } catch (e: SecurityException) {
            temp.delete()
        }
    }

    fun remove(key: String) {
        File(pathFor(key)).delete()
    }

    /**
     * Deletes copies not used for [KEEP_FOR_MS], and temporary files a closed app
     * left behind. Returns how many went.
     */
    fun prune(nowUtc: Long): Int {
        val files = File(folder).listFiles() ?: return 0
        var removed = 0
        for (file in files) {
            val age = nowUtc - file.lastModified()
            val stale = when {
                file.name.endsWith(".tmp", ignoreCase = true) -> age > DAY_MS
                file.name.endsWith(EXTENSION, ignoreCase = true) -> age > KEEP_FOR_MS
                else -> false
            }
            if (stale && file.delete()) removed++
        }
        return removed
    }

    companion object {
        private const val EXTENSION = ".art"
        private const val DAY_MS = 24L * 60 * 60 * 1000

        /** Copies used within this long are left alone by [prune]. */
        const val KEEP_FOR_MS = 90 * DAY_MS

        /** A copy older than this is re-stamped when used, so pruning sees it as in use. */
        private const val TOUCH_AFTER_MS = 30 * DAY_MS

        /** How many of an album's tracks the cover is looked for in, and the name is made from. */
        const val TRACKS_LOOKED_IN = 5

        /** "144|Song.flac|31457280|1767225600000|..." - the same album wherever its files are. */
        fun keyFor(album: Album, width: Int): String =
            "$width|" + album.tracks.take(TRACKS_LOOKED_IN).joinToString("|") { keyOf(it) }

        /** A file by its name, size and write time (the desktop's QualityCache.KeyOf). */
        fun keyOf(track: Track): String = "${Paths.fileName(track.filePath)}|${track.fileSize}|${track.modifiedUtc}"

        private fun sha1Hex(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}
