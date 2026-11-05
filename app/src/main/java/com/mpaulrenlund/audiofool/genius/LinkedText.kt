package com.mpaulrenlund.audiofool.genius

/**
 * Text with its web addresses picked out, so the Genius screen can make them links
 * that open in the browser. Genius's write-ups, answers and comments come as plain
 * text, and an answer is sometimes only a link ("Is there a live performance?" and a
 * YouTube address). Line breaks in the text are kept.
 *
 * Ported from the desktop's `Formatting/LinkedText.cs` (session 75).
 */
object LinkedText {
    private val url = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
    private val trailing = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '”', '’', '\'')

    /** The text cut into plain parts and addresses (true), in order; an address's trailing full stop or bracket stays text. */
    fun split(text: String): List<Pair<String, Boolean>> {
        val parts = ArrayList<Pair<String, Boolean>>()
        var at = 0
        for (m in url.findAll(text)) {
            val link = m.value.trimEnd(*trailing)
            if (link.length <= "https://".length) continue

            if (m.range.first > at) parts.add(text.substring(at, m.range.first) to false)
            parts.add(link to true)
            at = m.range.first + link.length
        }
        if (at < text.length) parts.add(text.substring(at) to false)
        return parts
    }
}
