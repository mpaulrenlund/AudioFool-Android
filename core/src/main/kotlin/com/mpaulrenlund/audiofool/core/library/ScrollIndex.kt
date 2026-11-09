package com.mpaulrenlund.audiofool.core.library

/**
 * What a list's index strip holds (new for the phone; the desktop has none): its
 * labels in list order, each with the position of its first row. Artists' letters
 * ([ArtistIndex], 0.2.0) and an artist's album years ([AlbumYearIndex], 0.11.0).
 */
class ScrollIndex(val sections: List<Section>) {
    /**
     * A label on the strip, the position of its first row, and what the bubble
     * shows while a finger is on it ("’97" on the strip is "1997" in the bubble).
     */
    data class Section(val label: String, val firstIndex: Int, val bubble: String = label)

    val isEmpty: Boolean get() = sections.isEmpty()

    /** The section a list position falls in: the last one starting at or before it. */
    fun sectionAt(listIndex: Int): Int {
        if (sections.isEmpty()) return -1
        var found = 0
        for (i in sections.indices) {
            if (sections[i].firstIndex <= listIndex) found = i else break
        }
        return found
    }

    companion object {
        val Empty = ScrollIndex(emptyList())

        /**
         * The index of [rows], one section per label in the order it first appears,
         * so every jump goes down the list. Two labels alike with different bubbles
         * (’28 for 1928 and 2028) are two sections.
         */
        fun <T> of(rows: List<T>, label: (T) -> String, bubble: (T) -> String = label): ScrollIndex {
            val seen = HashSet<Pair<String, String>>()
            val sections = ArrayList<Section>()
            rows.forEachIndexed { i, row ->
                val l = label(row)
                val b = bubble(row)
                if (seen.add(l to b)) sections += Section(l, i, b)
            }
            return ScrollIndex(sections)
        }
    }
}
