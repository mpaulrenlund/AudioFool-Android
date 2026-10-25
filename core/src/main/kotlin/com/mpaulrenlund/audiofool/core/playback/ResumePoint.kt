package com.mpaulrenlund.audiofool.core.playback

import com.mpaulrenlund.audiofool.core.library.Paths
import com.mpaulrenlund.audiofool.core.model.Track
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the app reopens to (spec 6.1, Resume): the queue, the track, the place
 * in it, and shuffle and repeat, restored paused. Saved as a small JSON file
 * with [com.mpaulrenlund.audiofool.core.library.SafeFile].
 *
 * New for the phone: the desktop reopens on the last song at 0:00 (its
 * `LastPlayed` setting). The place is the heard one ([ReopenPoint]), not the
 * decoder's.
 */
@Serializable
data class ResumePoint(
    /** The queue, in album order, as plain paths. */
    val paths: List<String>,
    /** The queue index that was playing. */
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.Off,
    /** The play order ([PlayOrder.order]) when shuffled, so Next carries on as it would have. */
    val order: List<Int> = emptyList(),
) {
    fun encode(): ByteArray = json.encodeToString(serializer(), this).toByteArray(Charsets.UTF_8)

    /**
     * The saved queue as the library's tracks now. Songs no longer in the library
     * drop out (a folder taken off the list); a song on a missing SD card is still
     * in the library, so it stays. Null when the song that was playing is gone.
     */
    fun resolve(lookup: (String) -> Track?): Restored? {
        val tracks = ArrayList<Track>()
        val newIndexOf = IntArray(paths.size) { -1 }
        paths.forEachIndexed { i, path ->
            lookup(path)?.let {
                newIndexOf[i] = tracks.size
                tracks.add(it)
            }
        }
        val current = newIndexOf.getOrElse(index) { -1 }
        if (current < 0) return null

        // A shuffled order survives only if nothing dropped out of the queue.
        val keptOrder = if (shuffle && tracks.size == paths.size && isPermutation(order, tracks.size)) order else emptyList()
        return Restored(tracks, current, positionMs.coerceIn(0, maxOf(0, tracks[current].durationMs)), shuffle, repeat, keptOrder)
    }

    class Restored(
        val queue: List<Track>,
        val index: Int,
        val positionMs: Long,
        val shuffle: Boolean,
        val repeat: RepeatMode,
        /** The saved play order, or empty to shuffle again. */
        val order: List<Int>,
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(bytes: ByteArray): ResumePoint? {
            val point = json.decodeFromString(serializer(), bytes.toString(Charsets.UTF_8))
            return point.takeIf { it.index in it.paths.indices }
        }

        /** A lookup by path that ignores case, as paths compare on the phone ([Paths.key]). */
        fun lookupIn(tracks: Iterable<Track>): (String) -> Track? {
            val byKey = tracks.associateBy { Paths.key(it.filePath) }
            return { byKey[Paths.key(it)] }
        }

        private fun isPermutation(order: List<Int>, count: Int): Boolean =
            order.size == count && order.toSet().size == count && order.all { it in 0 until count }
    }
}
