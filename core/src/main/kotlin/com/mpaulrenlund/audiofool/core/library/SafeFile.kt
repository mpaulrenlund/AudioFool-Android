package com.mpaulrenlund.audiofool.core.library

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * A file saved so that a crash or power cut mid-save leaves a good copy: written
 * beside the old one, flushed to the disk, and only then swapped in, the old one
 * kept as `.bak`. [read] falls back to that backup when the file is missing or
 * damaged. Ported from the desktop's `AudioFool.Core/Library/SafeJsonFile.cs`,
 * for any format (the library cache here is binary).
 */
object SafeFile {
    enum class Load {
        /** The file itself was read. */
        Read,

        /** No file and no backup: a first run. */
        Missing,

        /** The file was missing or damaged, and the backup from the save before it was read. */
        FromBackup,

        /** The file was damaged and moved aside, with no backup to read. */
        Damaged,
    }

    /** The copy of the save before the last. */
    fun backupPathFor(path: String): String = "$path.bak"

    /**
     * Writes the file through [write] to a working file, flushed to the disk, then
     * swaps it in, the old file becoming the backup. Throws as file calls do; the
     * file on disk is then still the last good one.
     */
    fun write(path: String, write: (OutputStream) -> Unit) {
        val target = File(path)
        val temp = File("$path.tmp")
        try {
            target.parentFile?.mkdirs()
            FileOutputStream(temp).use { stream ->
                val buffered = stream.buffered(1 shl 16)
                write(buffered)
                buffered.flush()
                stream.fd.sync()
            }
            if (target.exists())
                Files.move(target.toPath(), File(backupPathFor(path)).toPath(), StandardCopyOption.REPLACE_EXISTING)
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            temp.delete()
            throw e
        }
    }

    /**
     * Never throws. The file, else its backup; a damaged file ([parse] throws or
     * returns null) is moved aside as `name.unreadable-yyyyMMddHHmmss` rather than
     * saved over, so it can be looked at.
     */
    fun <T : Any> read(path: String, nowUtc: Long, parse: (ByteArray) -> T?): Pair<T?, Load> {
        val (value, state) = readOne(path, parse)
        if (state == State.Read) return value to Load.Read

        if (state == State.Broken) moveAside(path, nowUtc)

        val (backup, backupState) = readOne(backupPathFor(path), parse)
        return when {
            backupState == State.Read -> backup to Load.FromBackup
            state == State.Broken -> null to Load.Damaged
            else -> null to Load.Missing
        }
    }

    private enum class State { Missing, Read, Broken }

    private fun <T : Any> readOne(path: String, parse: (ByteArray) -> T?): Pair<T?, State> {
        val file = File(path)
        if (!file.exists()) return null to State.Missing
        return try {
            val value = parse(file.readBytes())
            if (value != null) value to State.Read else null to State.Broken
        } catch (e: IOException) {
            null to State.Broken
        } catch (e: RuntimeException) {
            null to State.Broken
        }
    }

    private fun moveAside(path: String, nowUtc: Long) {
        val stamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.ROOT)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(nowUtc))
        File(path).renameTo(File("$path.unreadable-$stamp"))
    }
}
