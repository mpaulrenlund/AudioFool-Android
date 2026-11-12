package com.mpaulrenlund.audiofool.core.library

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Puts a rewritten copy of a song in the original's place, beside which it was
 * made, and names the working copies a save leaves beside a song. Ported from the
 * desktop's `AudioFool.Core/Library/FileSwap.cs`.
 *
 * The desktop's swap (Windows' `File.Replace`) can break off partway, so it keeps
 * the original under a backup name until the copy is in. Here the swap is one
 * rename on the same volume, which either happens whole or not at all, so there is
 * no backup to keep.
 */
object FileSwap {
    /** Every working file AudioFool leaves beside a song starts with this; a scan never takes one for a song. */
    const val PREFIX = ".audiofool-"

    /** A song's own bytes, kept by the desktop while it swaps: never deleted here. */
    const val BACKUP_PREFIX = PREFIX + "backup-"

    /** A working copy is named this, a random id and ".part"; one left by a crash is safe to delete. */
    const val WORKING_SUFFIX = ".part"

    /**
     * How long a working copy goes unwritten before it is taken for one a crash
     * left: a save can be under way meanwhile, and its copy is written to throughout.
     */
    const val LEFTOVER_AGE_MS = 10 * 60 * 1000L

    /** A new working copy's path, beside [song] (the same volume, so the swap is one rename). */
    fun workingCopyFor(song: String): String {
        val folder = File(song).parentFile ?: File(".")
        return File(folder, "${PREFIX}save-${UUID.randomUUID().toString().replace("-", "")}$WORKING_SUFFIX").path
    }

    /** Replaces [original] with [copy] in one step. Throws on failure, with the original untouched. */
    fun replace(copy: String, original: String) {
        Files.move(File(copy).toPath(), File(original).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    /** A working copy a save may have left beside a song: safe to delete once it is old enough. */
    fun isWorkingCopy(path: String): Boolean {
        val name = File(path).name
        return name.startsWith(PREFIX, ignoreCase = true) &&
            !name.startsWith(BACKUP_PREFIX, ignoreCase = true) &&
            name.endsWith(WORKING_SUFFIX, ignoreCase = true)
    }

    /**
     * Deletes the working copies among [paths] not written for [LEFTOVER_AGE_MS];
     * the number deleted. Never touches a backup or anything else. The desktop runs
     * it over each scan's files; the phone over the folder of each save made on a copy.
     */
    fun deleteLeftovers(paths: Iterable<String>, nowMs: Long): Int {
        var deleted = 0
        for (path in paths) {
            if (!isWorkingCopy(path)) continue
            try {
                val file = File(path)
                if (nowMs - file.lastModified() < LEFTOVER_AGE_MS) continue
                if (file.delete()) deleted++
            } catch (e: SecurityException) {
            }
        }
        return deleted
    }

    /** Copies [song] to [copy], which must not exist yet. */
    internal fun copy(song: String, copy: String) {
        Files.copy(File(song).toPath(), File(copy).toPath())
    }
}
