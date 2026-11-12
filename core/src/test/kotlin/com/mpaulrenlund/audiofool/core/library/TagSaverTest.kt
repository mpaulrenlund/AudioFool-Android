package com.mpaulrenlund.audiofool.core.library

import org.junit.After
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The save's own logic (Edit Tags, spec 5.4), with a stand-in for TagLib: the
// desktop's SafeTagSaveTests and LeftoverTests where they don't need TagLib
// itself, and TagPadding's layouts for the check of a copy. TagLib's own writing
// is checked on the emulator with real files.

class TagSaverTest {
    private val folder = Files.createTempDirectory("audiofool-tagsave").toFile()

    @After fun cleanUp() {
        folder.deleteRecursively()
    }

    private val edit = TrackTagEdit("T", "A", "A", "Al", 2026, 1, 2, 1, 1)
    private val shape = AudioShape(240_000, 44100, 2)

    /** A made-up TagLib: rehearses as told, saves by replacing [from] with [to] in the copy. */
    private inner class StandIn(
        val plan: (String) -> Rehearsal,
        val from: ByteArray = byteArrayOf(),
        val to: ByteArray = byteArrayOf(),
        val copyShape: AudioShape? = shape,
        val beforeCommit: (String) -> Unit = {},
    ) : TagFile {
        val copies = mutableListOf<String>()

        override fun rehearse(path: String, edit: TrackTagEdit, limit: Long): Rehearsal = plan(path).also { beforeCommit(path) }

        override fun saveAs(copy: String, song: String, edit: TrackTagEdit): String? {
            copies += copy
            val bytes = File(copy).readBytes()
            val at = indexOf(bytes, from)
            if (at < 0) return "unsupported file format"
            File(copy).writeBytes(bytes.copyOfRange(0, at) + to + bytes.copyOfRange(at + from.size, bytes.size))
            return null
        }

        override fun audio(path: String, song: String): AudioShape? = if (path == song) shape else copyShape
    }

    private fun song(name: String, bytes: ByteArray) = File(folder, name).apply { writeBytes(bytes) }.path

    // ---- the layouts ----

    private fun syncsafe(n: Int) = byteArrayOf((n shr 21 and 0x7F).toByte(), (n shr 14 and 0x7F).toByte(), (n shr 7 and 0x7F).toByte(), (n and 0x7F).toByte())

    /** An ID3v2.4 tag holding one TIT2 frame of [title], padded with [padding] zeros. */
    private fun id3(title: String, padding: Int): ByteArray {
        val body = byteArrayOf(3) + title.toByteArray()
        val frame = "TIT2".toByteArray() + syncsafe(body.size) + byteArrayOf(0, 0) + body
        val size = frame.size + padding
        return "ID3".toByteArray() + byteArrayOf(4, 0, 0) + syncsafe(size) + frame + ByteArray(padding)
    }

    private fun flac(blocks: List<Pair<Int, ByteArray>>, audio: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("fLaC".toByteArray())
        blocks.forEachIndexed { i, (type, data) ->
            out.write((type or if (i == blocks.lastIndex) 0x80 else 0))
            out.write(data.size shr 16); out.write(data.size shr 8 and 0xFF); out.write(data.size and 0xFF)
            out.write(data)
        }
        out.write(audio)
        return out.toByteArray()
    }

    private fun le64(v: Long) = ByteArray(8) { (v shr (8 * it) and 0xFF).toByte() }

    private fun dsf(audio: ByteArray, tag: ByteArray): ByteArray {
        val pointer = 28L + audio.size
        val size = pointer + tag.size
        return "DSD ".toByteArray() + le64(28) + le64(size) + le64(pointer) + audio + tag
    }

    private val music = ByteArray(5000) { (it * 7).toByte() }

    @Test fun flac_audio_starts_after_its_last_block_and_an_id3_tag_in_front_is_walked() {
        val bytes = id3("x", 10) + flac(listOf(0 to ByteArray(34), 4 to ByteArray(20), 1 to ByteArray(100)), music)
        val layout = TagLayout.read(song("a.flac", bytes))
        assertEquals(TagLayout(bytes.size - music.size.toLong()), layout)
    }

    @Test fun mp3_audio_runs_to_the_end_less_id3v1_and_ape_tags() {
        val apeItems = ByteArray(40)
        val footer = "APETAGEX".toByteArray() + byteArrayOf(-48, 7, 0, 0) + byteArrayOf((apeItems.size + 32).toByte(), 0, 0, 0) + ByteArray(16)
        val id3v1 = "TAG".toByteArray() + ByteArray(125)
        val a = song("a.mp3", id3("Old", 50) + music + apeItems + footer + id3v1)
        val b = song("b.mp3", id3("A much longer title", 500) + music + id3v1)
        val digestA = TagLayout.audioDigest(a, TagLayout.read(a)!!)
        val digestB = TagLayout.audioDigest(b, TagLayout.read(b)!!)
        assertContentEquals(digestA, digestB)
    }

    @Test fun dsf_audio_is_between_the_header_and_the_tag() {
        val path = song("a.dsf", dsf(music, id3("x", 20)))
        assertEquals(TagLayout(28, 28L + music.size), TagLayout.read(path))
    }

    @Test fun layouts_not_understood_give_null() {
        assertNull(TagLayout.read(song("plain.mp3", music)))
        val dirty = id3("x", 10).also { it[it.size - 1] = 1 }
        assertNull(TagLayout.read(song("dirty.mp3", dirty + music)))
        assertNull(TagLayout.read(song("short.flac", "fLaC".toByteArray() + byteArrayOf(0, 0, 0, 34))))
    }

    // ---- the save ----

    @Test fun an_in_place_save_writes_only_its_bytes() {
        val path = song("a.mp3", "HEADER-old-tag-MUSIC".toByteArray())
        val tags = StandIn({ Rehearsal.InPlace(listOf(7L to "new".toByteArray())) })
        assertNull(TagSaver.save(path, edit, tags))
        assertEquals("HEADER-new-tag-MUSIC", File(path).readText())
        assertTrue(tags.copies.isEmpty())
    }

    @Test fun a_save_that_moves_the_audio_is_made_on_a_copy_and_swapped_in() {
        val path = song("a.flac", "fLaC-short-MUSIC".toByteArray())
        val tags = StandIn({ Rehearsal.Moves }, "short".toByteArray(), "a much longer tag".toByteArray())
        assertNull(TagSaver.save(path, edit, tags))
        assertEquals("fLaC-a much longer tag-MUSIC", File(path).readText())
        assertEquals(1, tags.copies.size)
        assertTrue(FileSwap.isWorkingCopy(tags.copies[0]))
        assertEquals(listOf("a.flac"), folder.list()!!.toList())
    }

    @Test fun a_copy_that_reads_as_other_audio_is_not_used() {
        val path = song("a.flac", "fLaC-short-MUSIC".toByteArray())
        val tags = StandIn({ Rehearsal.Moves }, "short".toByteArray(), "long".toByteArray(), copyShape = shape.copy(lengthMs = 1))
        assertEquals("the saved copy didn't read as the same audio, so it wasn't used", TagSaver.save(path, edit, tags))
        assertEquals("fLaC-short-MUSIC", File(path).readText())
        assertEquals(listOf("a.flac"), folder.list()!!.toList())
    }

    @Test fun a_copy_whose_music_changed_is_not_used() {
        val bytes = id3("Old", 10) + music
        val path = song("a.mp3", bytes)
        // The made-up save damages a byte of the music.
        val tags = StandIn({ Rehearsal.Moves }, music.copyOfRange(100, 104), byteArrayOf(9, 9, 9, 9))
        assertEquals("the saved copy's audio didn't come out identical, so it wasn't used", TagSaver.save(path, edit, tags))
        assertContentEquals(bytes, File(path).readBytes())
    }

    @Test fun taglibs_own_failure_leaves_the_song_and_no_copy() {
        val path = song("a.flac", "fLaC-MUSIC".toByteArray())
        val tags = StandIn({ Rehearsal.Moves }, "not there".toByteArray(), byteArrayOf())
        assertEquals("unsupported file format", TagSaver.save(path, edit, tags))
        assertEquals(listOf("a.flac"), folder.list()!!.toList())
    }

    @Test fun a_rehearsal_that_fails_says_why() {
        val path = song("a.mod", "MOD".toByteArray())
        assertEquals("unsupported file format", TagSaver.save(path, edit, StandIn({ Rehearsal.Failed("unsupported file format") })))
    }

    @Test fun a_file_changed_between_rehearsal_and_save_is_not_saved() {
        val path = song("a.mp3", "HEADER-old-tag-MUSIC".toByteArray())
        val tags = StandIn({ Rehearsal.InPlace(listOf(7L to "new".toByteArray())) }, beforeCommit = { File(it).appendText("!") })
        assertEquals("the file changed while it was being saved - try again", TagSaver.save(path, edit, tags))
        assertEquals("HEADER-old-tag-MUSIC!", File(path).readText())
    }

    @Test fun a_missing_file_is_said_to_be_gone() {
        assertEquals("the file no longer exists", TagSaver.save(File(folder, "gone.flac").path, edit, StandIn({ Rehearsal.Moves })))
    }

    @Test fun old_working_copies_go_and_backups_and_fresh_copies_stay() {
        val now = System.currentTimeMillis()
        fun touch(name: String, ageMs: Long) = File(folder, name).apply { writeText("x"); setLastModified(now - ageMs) }.path
        val old = touch("${FileSwap.PREFIX}save-1${FileSwap.WORKING_SUFFIX}", 2 * 3600_000L)
        val oldCover = touch("${FileSwap.PREFIX}cover-2.shrunk${FileSwap.WORKING_SUFFIX}", 2 * 3600_000L)
        val fresh = touch("${FileSwap.PREFIX}save-3${FileSwap.WORKING_SUFFIX}", 60_000L)
        val backup = touch("${FileSwap.BACKUP_PREFIX}4-song.flac.bak", 3 * 86_400_000L)

        assertEquals(2, FileSwap.deleteLeftovers(listOf(old, oldCover, fresh, backup), now))
        assertFalse(File(old).exists())
        assertFalse(File(oldCover).exists())
        assertTrue(File(fresh).exists())
        assertTrue(File(backup).exists())
    }

    @Test fun a_save_on_a_copy_clears_old_leftovers_beside_the_song() {
        val path = song("a.flac", "fLaC-short-MUSIC".toByteArray())
        val left = File(folder, "${FileSwap.PREFIX}save-old${FileSwap.WORKING_SUFFIX}").apply { writeText("x"); setLastModified(0) }
        assertNull(TagSaver.save(path, edit, StandIn({ Rehearsal.Moves }, "short".toByteArray(), "long".toByteArray())))
        assertFalse(left.exists())
        assertNotNull(File(path).takeIf { it.readText() == "fLaC-long-MUSIC" })
    }

    private fun indexOf(bytes: ByteArray, part: ByteArray): Int {
        if (part.isEmpty()) return -1
        outer@ for (i in 0..bytes.size - part.size) {
            for (j in part.indices) if (bytes[i + j] != part[j]) continue@outer
            return i
        }
        return -1
    }
}
