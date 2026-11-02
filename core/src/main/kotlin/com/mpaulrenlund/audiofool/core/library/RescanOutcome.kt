package com.mpaulrenlund.audiofool.core.library

import com.mpaulrenlund.audiofool.core.format.Display
import com.mpaulrenlund.audiofool.core.model.Track

// New for the phone (0.7.0): the desktop shows no end to a rescan.

/**
 * What a rescan the user asked for (Rescan Library, Settings' refresh) found,
 * shown in the panel headers for a few seconds after it ends.
 */
data class RescanOutcome(val added: Int, val updated: Int, val removed: Int, val unavailableFolders: Int) {
    /** "Up to date · 3 new, 1 removed", "Up to date · no changes", "… · 1 folder unavailable". */
    val text: String
        get() {
            val changes = listOf(added to "new", updated to "updated", removed to "removed")
                .filter { it.first > 0 }
                .joinToString(", ") { "${Display.count(it.first)} ${it.second}" }
                .ifEmpty { "no changes" }
            val unavailable = when (unavailableFolders) {
                0 -> ""
                1 -> " · 1 folder unavailable"
                else -> " · $unavailableFolders folders unavailable"
            }
            return "Up to date · $changes$unavailable"
        }

    companion object {
        /**
         * From the tracks shown [before] the scan and [after] it, and the number of
         * files it [read]: a read file that was shown before was updated, one that
         * wasn't is new. Tracks of folders left out are in neither, so unticking a
         * folder doesn't count as removing its tracks.
         */
        fun of(before: Collection<Track>, after: Collection<Track>, read: Int, unavailableFolders: Int): RescanOutcome {
            val was = before.mapTo(HashSet()) { Paths.key(it.filePath) }
            val now = after.mapTo(HashSet()) { Paths.key(it.filePath) }
            val added = now.count { it !in was }
            return RescanOutcome(
                added = added,
                updated = maxOf(0, read - added),
                removed = was.count { it !in now },
                unavailableFolders = unavailableFolders,
            )
        }
    }
}
