package com.mpaulrenlund.audiofool.library

import com.mpaulrenlund.audiofool.core.art.BestCover
import com.mpaulrenlund.audiofool.core.art.EmbeddedPicture
import com.mpaulrenlund.audiofool.core.model.Track
import java.io.File
import java.io.IOException

/**
 * A track's cover: the best embedded picture (the desktop's BestCover rules),
 * else the cover file beside it. Session 2 adds the thumbnail cache on top.
 */
object CoverArt {
    enum class Source { Embedded, Folder }

    class Cover(val bytes: ByteArray, val source: Source)

    fun read(track: Track): Cover? {
        TagLibNative.embeddedPictures(track.filePath)
            ?.map { EmbeddedPicture(it.type, it.data) }
            ?.let(BestCover::pick)
            ?.let { return Cover(it.data, Source.Embedded) }

        val folderArt = track.folderArtPath ?: return null
        return try {
            Cover(File(folderArt).readBytes(), Source.Folder)
        } catch (e: IOException) {
            null
        }
    }
}
