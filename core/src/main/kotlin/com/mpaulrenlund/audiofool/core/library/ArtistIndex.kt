package com.mpaulrenlund.audiofool.core.library

import com.mpaulrenlund.audiofool.core.model.ArtistGroup
import java.text.Normalizer

/**
 * The Artists screen's letter strip (new for the phone, 0.2.0; the desktop has
 * none): the letters the list holds, each with the first artist filed under it.
 *
 * An artist files under the first letter of its sort key, so "The Beatles" is
 * under B, as the list sorts it ([SortRules.artistSortKey]). Accents are dropped
 * ("Åsa" under A), as the root collation sorts them with their letter. Names that
 * start with a digit or a symbol are under "#"; names in another script under "•".
 * Only letters with artists are listed, in the order they first appear, so every
 * jump goes down the list.
 */
class ArtistIndex private constructor(val sections: List<Section>) {
    /** A letter and the position of its first artist in the list. */
    data class Section(val label: String, val firstIndex: Int)

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
        const val DIGITS_AND_SYMBOLS = "#"
        const val OTHER_SCRIPTS = "•"

        val Empty = ArtistIndex(emptyList())

        /** The index of [artists], in the order the list shows them. */
        fun of(artists: List<ArtistGroup>): ArtistIndex {
            val seen = HashSet<String>()
            val sections = ArrayList<Section>()
            artists.forEachIndexed { i, artist ->
                val label = labelFor(artist.sortKey)
                if (seen.add(label)) sections += Section(label, i)
            }
            return ArtistIndex(sections)
        }

        /** The letter a sort key files under. */
        fun labelFor(sortKey: String): String {
            val key = sortKey.trim()
            if (key.isEmpty()) return DIGITS_AND_SYMBOLS
            val first = key.substring(0, Character.charCount(key.codePointAt(0)))
            // NFD splits "Å" into "A" and its ring; letters that don't split are mapped by hand.
            val base = Normalizer.normalize(first, Normalizer.Form.NFD)[0]
            val letter = (LATIN_WITHOUT_DECOMPOSITION[base] ?: base).uppercaseChar()
            return when {
                letter in 'A'..'Z' -> letter.toString()
                Character.isLetter(first.codePointAt(0)) -> OTHER_SCRIPTS
                else -> DIGITS_AND_SYMBOLS
            }
        }

        private val LATIN_WITHOUT_DECOMPOSITION = mapOf(
            'Ø' to 'O', 'ø' to 'o', 'Æ' to 'A', 'æ' to 'a', 'Œ' to 'O', 'œ' to 'o',
            'Ð' to 'D', 'ð' to 'd', 'Đ' to 'D', 'đ' to 'd', 'Þ' to 'T', 'þ' to 't',
            'Ł' to 'L', 'ł' to 'l', 'ß' to 'S', 'ı' to 'i', 'Ħ' to 'H', 'ħ' to 'h',
        )
    }
}
