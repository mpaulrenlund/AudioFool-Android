package com.mpaulrenlund.audiofool.core.library

import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.Paths as NioPaths

/**
 * A file's size and last-write time: the two cheap facts that decide whether
 * cached tags are still valid. [createdUtc] comes free with the same stat and
 * means "added"; it takes no part in deciding whether tags are stale.
 * Ported from the desktop's `AudioFool.Core/Library/FileStamp.cs`.
 */
data class FileStamp(val length: Long, val modifiedUtc: Long, val createdUtc: Long = 0) {
    companion object {
        val Unknown = FileStamp(0, 0)

        fun of(attributes: BasicFileAttributes) = FileStamp(
            length = attributes.size(),
            modifiedUtc = attributes.lastModifiedTime().toMillis(),
            createdUtc = attributes.creationTime().toMillis(),
        )

        fun forPath(path: String): FileStamp = try {
            val attributes = Files.readAttributes(NioPaths.get(path), BasicFileAttributes::class.java)
            if (attributes.isRegularFile) of(attributes) else Unknown
        } catch (e: IOException) {
            Unknown
        } catch (e: SecurityException) {
            Unknown
        } catch (e: java.nio.file.InvalidPathException) {
            Unknown
        }
    }
}
