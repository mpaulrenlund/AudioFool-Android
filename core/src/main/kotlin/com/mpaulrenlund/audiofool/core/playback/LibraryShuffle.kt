package com.mpaulrenlund.audiofool.core.playback

import com.mpaulrenlund.audiofool.core.library.MusicLibrary
import com.mpaulrenlund.audiofool.core.model.Track
import kotlin.random.Random

/**
 * Artists' shuffle button (0.9.0, the user's request): the whole library as one
 * queue, played shuffled from a random song. New for the phone; the desktop has no
 * such button.
 *
 * The queue is the library in its own order, as the screens list it (artists, each
 * artist's albums, each album's songs), so [PlayOrder] does the shuffling as it does
 * for an album: Shuffle turned off later carries on in library order from the song
 * that plays.
 */
object LibraryShuffle {
    /** Every song in library order, without those [unavailable] says can't be reached (a removed SD card). */
    fun queue(library: MusicLibrary, unavailable: (Track) -> Boolean = { false }): List<Track> {
        val tracks = ArrayList<Track>(library.allTracks.size)
        for (artist in library.artists)
            for (album in artist.albums)
                for (track in album.tracks)
                    if (!unavailable(track)) tracks.add(track)
        return tracks
    }

    /** The song to start from: any of [count], or -1 when there are none. */
    fun start(count: Int, rng: Random = Random.Default): Int = if (count <= 0) -1 else rng.nextInt(count)
}
