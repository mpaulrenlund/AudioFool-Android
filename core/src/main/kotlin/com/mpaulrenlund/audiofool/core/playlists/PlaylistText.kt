package com.mpaulrenlund.audiofool.core.playlists

import com.mpaulrenlund.audiofool.core.format.Display
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The words the Playlists list and the playlist header use. Ported from the
 * desktop's `AudioFool.Core/Playlists/PlaylistText.cs`; the empty notes are the
 * phone's own wording, since a heart there is on Now Playing.
 */
object PlaylistText {
    private val isoDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** "Modified 2026-10-07", in local time: the ISO date format the desktop uses for release dates. */
    fun modified(modifiedUtc: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        "Modified " + isoDate.format(Instant.ofEpochMilli(modifiedUtc).atZone(zone))

    /** "1 track" / "12 tracks", for the Playlists list's rows. */
    fun trackCount(count: Int): String = if (count == 1) "1 track" else "${Display.count(count)} tracks"

    /**
     * The header's track line: the count, then how many the library doesn't have,
     * when any ("12 tracks · 2 not found"), so a short list says why.
     */
    fun headerTrackCount(count: Int, missing: Int): String =
        if (missing == 0) trackCount(count) else "${trackCount(count)} · ${Display.count(missing)} not found"

    /** What a playlist with nothing to show says: a title and a detail. */
    fun empty(isLiked: Boolean, missing: Int): Pair<String, String> {
        if (missing > 0) {
            return "None of these songs were found" to
                if (missing == 1) "Its song isn't in the library any more."
                else "Its ${Display.count(missing)} songs aren't in the library any more."
        }
        return if (isLiked) "No liked songs yet" to "Tap the heart on Now Playing to add a song."
        else "This playlist is empty" to "Songs added to it show here."
    }

    /** What Recently Added says when nothing arrived in the window. */
    val recentEmpty: Pair<String, String>
        get() = "Nothing added in the last ${RecentlyAdded.DAYS} days" to
            "Songs copied into the library show here for ${RecentlyAdded.DAYS} days."
}
