package com.mpaulrenlund.audiofool.core.library

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException

/** TagLib's part of a save: on the phone, the JNI calls in `TagEditing`; a stand-in in the tests. */
interface TagFile {
    /** The save made against a stand-in for [path], which is only read ([TagSaver.MAX_IN_PLACE_BYTES] is [limit]). */
    fun rehearse(path: String, edit: TrackTagEdit, limit: Long): Rehearsal

    /** The save made on [copy], a working copy of [song] (whose name gives the format). Null when saved, else why not. */
    fun saveAs(copy: String, song: String, edit: TrackTagEdit): String?

    /** How TagLib reads the audio of [path] (named [song] for its format); null when it can't. */
    fun audio(path: String, song: String): AudioShape?
}

/** What a save would do (the desktop's `SavePlan`). */
sealed interface Rehearsal {
    /** It changes only these bytes, the file keeping its length: they can be written into the file as they are. */
    class InPlace(val changes: List<Pair<Long, ByteArray>>) : Rehearsal

    /** It moves the audio along the file: it is made on a copy instead. */
    data object Moves : Rehearsal

    /** It can't be made, and why. */
    data class Failed(val why: String) : Rehearsal
}

/** The audio as TagLib reads it: the same before and after a tag save, or the save is not used. */
data class AudioShape(val lengthMs: Long, val sampleRate: Int, val channels: Int)

/**
 * Saves a tag edit so that nothing that stops it partway (the app closed, the
 * phone off, the SD card pulled) can leave the song damaged. Ported from the
 * desktop's `TagWriter.SaveTags`:
 *
 * - The save is first made against a stand-in for the file, which says what it
 *   would write. Most edits fit the room the tag has and change a few kilobytes
 *   at the start (or end) of the file; those bytes are written into the file as
 *   they are. Stopped partway, only the tag can suffer; the music's bytes are
 *   never written, and a stream playing the file hears nothing.
 * - An edit that doesn't fit moves the music along the file, which TagLib does in
 *   place, rewriting the whole song. That save is made on a copy beside the song,
 *   checked to hold the same music ([sameAudio]), and swapped in with one rename
 *   ([FileSwap]). A stream already playing the song keeps the file it opened.
 *
 * A file that changed between the two steps is not saved. Never throws: a failure
 * is the reason, in the desktop's words.
 */
object TagSaver {
    /**
     * The most a save may write and still be taken for one that keeps the music
     * where it is: far more than any tag, cover included; far less than a song.
     */
    const val MAX_IN_PLACE_BYTES = 32L shl 20

    /** Null when [edit] was saved into [path]; otherwise why it wasn't, the song untouched. */
    fun save(path: String, edit: TrackTagEdit, tags: TagFile, nowMs: () -> Long = System::currentTimeMillis): String? {
        if (!File(path).isFile) return "the file no longer exists"
        val stamp = FileStamp.forPath(path)
        val plan = try {
            tags.rehearse(path, edit, MAX_IN_PLACE_BYTES)
        } catch (e: Exception) {
            return describe(path, e)
        }
        if (plan is Rehearsal.Failed) return plan.why
        if (FileStamp.forPath(path) != stamp) return "the file changed while it was being saved - try again"
        return try {
            when (plan) {
                is Rehearsal.InPlace -> writeInPlace(path, plan.changes)
                Rehearsal.Moves -> saveOnCopy(path, edit, tags, nowMs())
                is Rehearsal.Failed -> Unit
            }
            null
        } catch (e: Exception) {
            describe(path, e)
        }
    }

    /** Writes the bytes a save that keeps the length changed, and makes sure they are on the disk. */
    private fun writeInPlace(path: String, changes: List<Pair<Long, ByteArray>>) {
        RandomAccessFile(path, "rw").use { file ->
            for ((at, bytes) in changes) {
                file.seek(at)
                file.write(bytes)
            }
            file.fd.sync()
        }
    }

    /**
     * Makes the save on a copy beside the song (the same volume, so the swap is one
     * rename), checks the copy plays the same music, and swaps it in. Throws on any
     * failure, with the song untouched and the copy gone. Working copies a crash left
     * in the folder go first.
     */
    private fun saveOnCopy(path: String, edit: TrackTagEdit, tags: TagFile, nowMs: Long) {
        File(path).parentFile?.listFiles()?.let { files -> FileSwap.deleteLeftovers(files.map { it.path }, nowMs) }
        val copy = FileSwap.workingCopyFor(path)
        try {
            FileSwap.copy(path, copy)
            tags.saveAs(copy, path, edit)?.let { throw SaveFailed(it) }
            RandomAccessFile(copy, "rw").use { it.fd.sync() }
            sameAudio(path, copy, tags)?.let { throw SaveFailed(it) }
            FileSwap.replace(copy, path)
        } finally {
            File(copy).delete()
        }
    }

    /**
     * Null when [copy], a tag save made on a copy of [original], plays the same
     * music; otherwise why not. TagLib must read the same length, rate and
     * channels, and for the layouts [TagLayout] knows (FLAC, MP3, DSF) the music
     * must match byte for byte. The tags are what the save changed, so they aren't
     * compared. The desktop's `CoverCleaner.SameAudio`.
     */
    fun sameAudio(original: String, copy: String, tags: TagFile): String? {
        val before = tags.audio(original, original)
        val after = tags.audio(copy, original)
        if (before == null || after == null || before != after) return "the saved copy didn't read as the same audio, so it wasn't used"
        val layoutBefore = TagLayout.read(original)
        val layoutAfter = TagLayout.read(copy)
        if (layoutBefore != null && layoutAfter != null &&
            !TagLayout.audioDigest(original, layoutBefore).contentEquals(TagLayout.audioDigest(copy, layoutAfter))
        ) return "the saved copy's audio didn't come out identical, so it wasn't used"
        return null
    }

    /** A save that couldn't be made, in words already fit to show. */
    private class SaveFailed(message: String) : IOException(message)

    /** The desktop's `TagWriter.Describe`, for what goes wrong on a phone. */
    private fun describe(path: String, e: Exception): String = when {
        !File(path).exists() -> "the file no longer exists"
        e is SaveFailed -> e.message ?: "the tags couldn't be saved"
        e is AccessDeniedException || e is SecurityException ||
            (e is FileNotFoundException && e.message?.contains("EACCES") == true) ||
            (e is FileSystemException && e.reason?.contains("denied", ignoreCase = true) == true) ->
            "permission denied - check the file isn't read-only"
        e is IOException -> "the file couldn't be written (${e.message ?: e.javaClass.simpleName})"
        else -> "Unexpected error: ${e.message ?: e.javaClass.simpleName}"
    }
}
