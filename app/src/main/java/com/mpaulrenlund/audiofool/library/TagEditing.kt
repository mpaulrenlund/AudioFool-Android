package com.mpaulrenlund.audiofool.library

import com.mpaulrenlund.audiofool.core.library.AudioShape
import com.mpaulrenlund.audiofool.core.library.NumberTexts
import com.mpaulrenlund.audiofool.core.library.Rehearsal
import com.mpaulrenlund.audiofool.core.library.TagDetails
import com.mpaulrenlund.audiofool.core.library.TagFile
import com.mpaulrenlund.audiofool.core.library.TagReader
import com.mpaulrenlund.audiofool.core.library.TagSaver
import com.mpaulrenlund.audiofool.core.library.TrackTagEdit
import com.mpaulrenlund.audiofool.core.library.ReleaseDate
import com.mpaulrenlund.audiofool.core.model.Track

/**
 * Edit Tags (spec 5.4) through TagLib (tagedit_jni.cpp): reading what the library
 * doesn't keep, and saving an edit the desktop's safe way ([TagSaver]). Call off
 * the main thread.
 */
object TagEditing : TagFile {
    init {
        // The same library as the scan's reader.
        TagLibNative.hashCode()
    }

    // Slots of the edit array; tagedit_jni.cpp names the same ones.
    private const val E_TITLE = 0
    private const val E_ARTIST = 1
    private const val E_ALBUM_ARTIST = 2
    private const val E_ALBUM = 3
    private const val E_DATE = 4
    private const val E_TRACK = 5
    private const val E_TRACK_TOTAL = 6
    private const val E_DISC = 7
    private const val E_DISC_TOTAL = 8
    private const val E_PUBLISHER = 9
    private const val E_COMPOSER = 10
    private const val E_CONDUCTOR = 11
    private const val E_GENRE = 12
    private const val E_COMMENT = 13
    private const val EDIT_SLOTS = 14

    private external fun details(pathUtf8: ByteArray): Array<ByteArray?>
    private external fun rehearse(pathUtf8: ByteArray, edit: Array<ByteArray?>, limit: Long): Array<ByteArray?>
    private external fun saveAs(copyUtf8: ByteArray, songUtf8: ByteArray, edit: Array<ByteArray?>): ByteArray?
    private external fun audio(pathUtf8: ByteArray, songUtf8: ByteArray): LongArray?

    private fun utf8(s: String) = s.toByteArray(Charsets.UTF_8)

    /**
     * What the file holds of the fields the library doesn't keep, with the full date
     * and the numbers as spelled; null when it can't be read (the desktop's
     * `TagReader.ReadDetails`: null, not empty, so nothing is taken for cleared).
     */
    fun readDetails(path: String): TagDetails? {
        val slots = try {
            details(utf8(path))
        } catch (e: Exception) {
            return null
        }
        fun s(i: Int) = slots.getOrNull(i)?.toString(Charsets.UTF_8)
        if (s(0) != "read") return null
        fun values(i: Int) = s(i)?.split('\u0000')
        return TagDetails(
            publisher = s(1)?.trim() ?: "",
            composer = TagDetails.join(values(2)),
            conductor = s(3)?.trim() ?: "",
            genre = TagDetails.join(values(4)),
            comment = s(5)?.trim() ?: "",
            date = ReleaseDate.fromTag(s(6))?.takeIf { ReleaseDate.hasMonth(it) } ?: "",
            numbers = NumberTexts.read(s(7), s(8), s(9), s(10)),
        )
    }

    /**
     * Saves [edit] into [track]'s file. The song as the library should now hold it
     * (read again, as a scan would, keeping when it was added: a save made on a copy
     * gives the file a new creation date), or why it wasn't saved.
     */
    fun save(track: Track, edit: TrackTagEdit): Result<Track> {
        TagSaver.save(track.filePath, edit, this)?.let { return Result.failure(SaveRefused(it)) }
        val read = TagReader(TagLibNative).read(track.filePath, folderArt = track.folderArtPath).track
        return Result.success(read.copy(addedUtc = track.addedUtc ?: read.addedUtc))
    }

    /** A save that didn't happen; [message] says why, in the desktop's words ("the file no longer exists"). */
    class SaveRefused(message: String) : Exception(message)

    // ---- TagFile, for TagSaver ----

    override fun rehearse(path: String, edit: TrackTagEdit, limit: Long): Rehearsal {
        val out = rehearse(utf8(path), slots(edit), limit)
        fun s(i: Int) = out.getOrNull(i)?.toString(Charsets.UTF_8)
        return when (s(0)) {
            "inplace" -> Rehearsal.InPlace((2 until out.size step 2).map { i -> s(i)!!.toLong() to (out[i + 1] ?: ByteArray(0)) })
            "moves" -> Rehearsal.Moves
            else -> Rehearsal.Failed(s(1) ?: "the tags couldn't be saved")
        }
    }

    override fun saveAs(copy: String, song: String, edit: TrackTagEdit): String? =
        saveAs(utf8(copy), utf8(song), slots(edit))?.toString(Charsets.UTF_8)

    override fun audio(path: String, song: String): AudioShape? =
        audio(utf8(path), utf8(song))?.let { AudioShape(it[0], it[1].toInt(), it[2].toInt()) }

    /** The edit as the C++ side takes it: null leaves a field alone, empty clears it; several values '\0'-separated. */
    private fun slots(edit: TrackTagEdit): Array<ByteArray?> {
        val out = arrayOfNulls<ByteArray>(EDIT_SLOTS)
        fun set(slot: Int, value: String?) {
            out[slot] = value?.let(::utf8)
        }
        fun number(n: Int?) = n?.toString() ?: ""
        set(E_TITLE, edit.title)
        set(E_ARTIST, edit.artist)
        set(E_ALBUM_ARTIST, edit.albumArtist)
        set(E_ALBUM, edit.album)
        set(E_DATE, edit.date ?: edit.year?.toString() ?: "")
        set(E_TRACK, number(edit.trackNumber))
        set(E_TRACK_TOTAL, number(edit.trackCount))
        set(E_DISC, number(edit.discNumber))
        set(E_DISC_TOTAL, number(edit.discCount))
        val d = edit.details
        set(E_PUBLISHER, d.publisher)
        set(E_COMPOSER, d.composer?.let { TagDetails.split(it).joinToString("\u0000") })
        set(E_CONDUCTOR, d.conductor)
        set(E_GENRE, d.genre?.let { TagDetails.split(it).joinToString("\u0000") })
        set(E_COMMENT, d.comment)
        return out
    }
}
