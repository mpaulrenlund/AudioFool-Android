package com.mpaulrenlund.audiofool.core.library

import com.mpaulrenlund.audiofool.core.model.Album

/**
 * The Albums screen's year strip (new for the phone, 0.11.0, the user's call; the
 * desktop has none): Artists' letter strip, but an artist's albums are in release
 * order ([SortRules.sortAlbums]), so it holds the years they came out in. The strip
 * shows "’97", the bubble "1997". Albums with no year sink to the bottom of the
 * list and file under "?".
 */
object AlbumYearIndex {
    const val NO_YEAR = "?"

    /** The index of [albums], in the order the list shows them. */
    fun of(albums: List<Album>): ScrollIndex = ScrollIndex.of(albums, { labelFor(it.year) }, { bubbleFor(it.year) })

    /** "’97" for 1997, "’05" for 2005, "?" for no year. */
    fun labelFor(year: Int?): String = if (year == null) NO_YEAR else "’%02d".format(Math.floorMod(year, 100))

    /** The whole year, or "?". */
    fun bubbleFor(year: Int?): String = year?.toString() ?: NO_YEAR
}
