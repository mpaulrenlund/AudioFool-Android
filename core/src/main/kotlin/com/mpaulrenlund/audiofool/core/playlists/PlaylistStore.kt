package com.mpaulrenlund.audiofool.core.playlists

import com.mpaulrenlund.audiofool.core.library.Paths
import com.mpaulrenlund.audiofool.core.library.SafeFile
import com.mpaulrenlund.audiofool.core.model.Track
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale

/**
 * Every playlist, Liked included, in `playlists.json` beside the library cache.
 * Ported from the desktop's `AudioFool.Core/Playlists/PlaylistStore.cs`, in the
 * desktop's file format (its property names, ISO dates). Each change is saved
 * straight away, flushed and swapped in with the save before kept as
 * `playlists.json.bak` ([SafeFile]). Nothing is written to the music files.
 *
 * A song whose file can't be found is never dropped from here: an SD card that is
 * out isn't a deleted song. Only removing it, or unliking it, takes it out.
 *
 * Left out for now (the phone has no screens for them yet, the user's call,
 * 2026-11-12: "We can flesh out further playlist creation later"): pictures. The
 * desktop's "Locked" load (a file held open by another program) has no
 * counterpart on the phone.
 */
class PlaylistStore private constructor(private val path: String, private val list: MutableList<Playlist>) {
    val playlists: List<Playlist> get() = list

    val liked: Playlist get() = list.first { it.isLiked }

    /** Whether the file itself was read, or its backup, or neither ([load]). */
    var loadedHow: SafeFile.Load = SafeFile.Load.Missing
        private set

    /** Most recently modified first (the user's call on the desktop), then by name. */
    fun byRecent(): List<Playlist> =
        list.sortedWith(compareByDescending<Playlist> { it.modifiedUtc }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    /**
     * Why [name] can't be used, or null when it can: it needs a name, and one no
     * other playlist has (Liked included).
     */
    fun nameProblem(name: String?, renaming: Playlist? = null): String? {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty()) return "Give the playlist a name."
        return if (list.any { it !== renaming && it.name.trim().equals(trimmed, ignoreCase = true) })
            "There's already a playlist called \"$trimmed\"."
        else null
    }

    fun create(name: String, nowUtc: Long): Playlist {
        nameProblem(name)?.let { throw IllegalArgumentException(it) }
        val playlist = Playlist(name = name.trim(), modifiedUtc = nowUtc)
        list.add(playlist)
        save()
        return playlist
    }

    fun rename(playlist: Playlist, name: String, nowUtc: Long) {
        check(!playlist.isLiked) { "Liked can't be renamed." }
        nameProblem(name, playlist)?.let { throw IllegalArgumentException(it) }
        if (playlist.name == name.trim()) return
        playlist.name = name.trim()
        touch(playlist, nowUtc)
    }

    /** Deletes the playlist. Liked can't be deleted. */
    fun delete(playlist: Playlist): Boolean {
        if (playlist.isLiked || !list.remove(playlist)) return false
        save()
        return true
    }

    /**
     * Adds the songs to the end, in order, skipping any already in it. Returns how
     * many were added; nothing changes, not even the date, when that is none.
     */
    fun add(playlist: Playlist, tracks: Iterable<Track>, nowUtc: Long): Int {
        var added = 0
        for (track in tracks) {
            if (playlist.contains(track.filePath)) continue
            playlist.entries.add(PlaylistEntry.of(track))
            added++
        }
        if (added > 0) touch(playlist, nowUtc)
        return added
    }

    /** Removes the songs at these paths. Returns how many went. */
    fun remove(playlist: Playlist, paths: Iterable<String>, nowUtc: Long): Int {
        val keys = paths.mapTo(HashSet()) { Paths.key(it) }
        val before = playlist.entries.size
        playlist.entries.removeAll { Paths.key(it.filePath) in keys }
        val removed = before - playlist.entries.size
        if (removed > 0) touch(playlist, nowUtc)
        return removed
    }

    /**
     * Moves the songs at these paths, as one block in their playlist order, to just
     * before the song at [beforePath], or to the end when that is null. Every other
     * entry keeps its place among the rest, songs whose file isn't found included.
     * Returns whether the order changed; nothing is saved, not even the date, when
     * it didn't.
     */
    fun move(playlist: Playlist, paths: Iterable<String>, beforePath: String?, nowUtc: Long): Boolean {
        val keys = paths.mapTo(HashSet()) { Paths.key(it) }
        fun moving(e: PlaylistEntry) = Paths.key(e.filePath) in keys
        val block = playlist.entries.filter(::moving)
        if (block.isEmpty()) return false

        val rest = playlist.entries.filterNot(::moving).toMutableList()

        // Dropped on one of the moving songs: before the first one left after it.
        var at = rest.size
        if (beforePath != null) {
            val target = playlist.entries.indexOfFirst { Paths.same(it.filePath, beforePath) }
            if (target >= 0) {
                val anchor = playlist.entries.drop(target).firstOrNull { !moving(it) }
                at = if (anchor == null) rest.size else rest.indexOfFirst { it === anchor }
            }
        }

        rest.addAll(at, block)
        if (rest.size == playlist.entries.size && rest.indices.all { rest[it] === playlist.entries[it] }) return false

        playlist.entries.clear()
        playlist.entries.addAll(rest)
        touch(playlist, nowUtc)
        return true
    }

    private fun touch(playlist: Playlist, nowUtc: Long) {
        playlist.modifiedUtc = nowUtc
        save()
    }

    /** Writes the file. Also called after re-pointing songs, which isn't a modification. */
    fun save() {
        try {
            SafeFile.write(path) { it.write(encode(list)) }
        } catch (e: IOException) {
            // Still held in memory, and written with the next change.
        }
    }

    companion object {
        const val LIKED_NAME = "Liked"

        /**
         * Never throws. A missing file is no playlists but Liked. A damaged or missing
         * one falls back to the backup of the save before ([SafeFile]); a damaged one
         * is moved aside rather than overwritten by the next save, so it can be
         * recovered. [loadedHow] says which.
         */
        fun load(path: String, nowUtc: Long): PlaylistStore {
            val (playlists, how) = SafeFile.read(path, nowUtc, ::decode)
            val store = PlaylistStore(path, playlists.orEmpty().toMutableList())
            store.loadedHow = how

            val addLiked = store.list.none { it.isLiked }
            if (addLiked) store.list.add(Playlist(name = LIKED_NAME, isLiked = true, modifiedUtc = nowUtc))

            // From the backup: a file of its own again, so the next start reads it.
            if (addLiked || how == SafeFile.Load.FromBackup) store.save()
            return store
        }

        // ------------------------------------------------------------ the file

        // The desktop's System.Text.Json names, so one format serves both.
        @Serializable
        private class EntryJson(
            @SerialName("FilePath") val filePath: String = "",
            @SerialName("Artist") val artist: String = "",
            @SerialName("Album") val album: String = "",
        )

        @Serializable
        private class PlaylistJson(
            @SerialName("Id") val id: String = "",
            @SerialName("Name") val name: String = "",
            @SerialName("IsLiked") val isLiked: Boolean = false,
            @SerialName("ModifiedUtc") val modifiedUtc: String = "",
            @SerialName("Picture") val picture: String? = null,
            @SerialName("Entries") val entries: List<EntryJson> = emptyList(),
        )

        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            explicitNulls = true
            encodeDefaults = true
        }

        private val listSerializer = ListSerializer(PlaylistJson.serializer())

        internal fun encode(playlists: List<Playlist>): ByteArray =
            json.encodeToString(
                listSerializer,
                playlists.map { p ->
                    PlaylistJson(
                        id = p.id,
                        name = p.name,
                        isLiked = p.isLiked,
                        modifiedUtc = Instant.ofEpochMilli(p.modifiedUtc).toString(),
                        picture = p.picture,
                        entries = p.entries.map { EntryJson(it.filePath, it.artist, it.album) },
                    )
                },
            ).toByteArray(Charsets.UTF_8)

        /** Null for a file that isn't a playlist list, so [SafeFile] treats it as damaged. */
        internal fun decode(bytes: ByteArray): List<Playlist>? {
            val text = bytes.toString(Charsets.UTF_8)
            if (text.isBlank()) return null
            return json.decodeFromString(listSerializer, text).map { p ->
                Playlist(
                    id = p.id.ifBlank { java.util.UUID.randomUUID().toString() }.lowercase(Locale.ROOT),
                    name = p.name,
                    isLiked = p.isLiked,
                    modifiedUtc = parseTime(p.modifiedUtc),
                    picture = p.picture,
                    entries = p.entries.mapTo(ArrayList()) { PlaylistEntry(it.filePath, it.artist, it.album) },
                )
            }
        }

        /** The desktop writes "2026-10-07T12:00:00Z", with up to seven decimals; an offset is read too. */
        private fun parseTime(text: String): Long =
            try {
                Instant.parse(text).toEpochMilli()
            } catch (e: RuntimeException) {
                try {
                    OffsetDateTime.parse(text).toInstant().toEpochMilli()
                } catch (e: RuntimeException) {
                    0L
                }
            }
    }
}
