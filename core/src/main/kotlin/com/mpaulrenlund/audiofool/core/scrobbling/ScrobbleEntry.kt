package com.mpaulrenlund.audiofool.core.scrobbling

import com.mpaulrenlund.audiofool.core.model.Track
import kotlinx.serialization.Serializable

/**
 * One play as Last.fm is told about it. [timestamp] is when the play started, in
 * Unix seconds UTC, which is what Last.fm asks for. Also the format of the
 * offline queue file, so renaming a member loses queued scrobbles. Ported from
 * the desktop's `AudioFool.Core/Scrobbling/ScrobbleEntry.cs`.
 *
 * Matched by identity (`===`) in [ScrobbleQueue.remove], as the desktop's
 * reference equality: two plays of the same song can be equal as data.
 */
@Serializable
data class ScrobbleEntry(
    val artist: String,
    val track: String,
    val album: String? = null,
    val albumArtist: String? = null,
    val trackNumber: Int? = null,
    val durationSeconds: Int? = null,
    val timestamp: Long = 0,
) {
    companion object {
        /**
         * The track as Last.fm should see it, or null when it has no title or no
         * artist tag. The file name is deliberately not used as a title: "01 -
         * Untitled" on a profile is worse than no scrobble. Artist is the track
         * artist, falling back to the album artist; the album artist goes in its
         * own field, which is how Last.fm files compilations and features.
         */
        fun from(track: Track, durationMs: Long, startedAtMs: Long): ScrobbleEntry? {
            val title = track.title.trim()
            val artist = if (track.artist.isBlank()) track.albumArtist.trim() else track.artist.trim()
            if (title.isEmpty() || artist.isEmpty()) return null

            return ScrobbleEntry(
                artist = artist,
                track = title,
                album = nullIfBlank(track.album),
                albumArtist = nullIfBlank(track.albumArtist),
                trackNumber = track.trackNumber?.takeIf { it > 0 },
                // Math.rint rounds half to even, as the desktop's Math.Round.
                durationSeconds = if (durationMs > 0) Math.rint(durationMs / 1000.0).toInt() else null,
                timestamp = Math.floorDiv(startedAtMs, 1000L),
            )
        }

        private fun nullIfBlank(value: String): String? = if (value.isBlank()) null else value.trim()
    }
}
