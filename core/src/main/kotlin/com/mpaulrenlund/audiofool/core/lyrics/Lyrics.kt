package com.mpaulrenlund.audiofool.core.lyrics

/**
 * One line of a song's lyrics (spec 5.5). [startMs] is when it is sung, in timed
 * lyrics; -1 in lyrics with no timing. An empty [text] is a break: between verses,
 * or a timed pause (an instrumental passage), where no line is lit.
 */
data class LyricLine(val startMs: Long, val text: String)

/** Where a song's lyrics came from, named under them on the Lyrics screen. */
enum class LyricsSource { LrcFile, Tags, LrcLib }

/**
 * A song's lyrics: [synced] when each line carries the time it is sung (LRC, an
 * ID3v2 SYLT frame), the lines then in time order; otherwise the lines as written.
 * The phone's own: the desktop has no lyrics (2026-11-11, the user's request).
 */
data class Lyrics(val lines: List<LyricLine>, val synced: Boolean, val source: LyricsSource) {
    /**
     * The line being sung at [positionMs]: the last one whose time has come, or -1
     * before the first (and always in lyrics with no timing). Lines share a time
     * when an LRC line names several; the last of them is the one lit.
     */
    fun lineAt(positionMs: Long): Int {
        if (!synced) return -1
        var lo = 0
        var hi = lines.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].startMs <= positionMs) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }
}

/**
 * Reads lyrics as LRC files, LRCLIB and most tag editors write them: each line
 * after one or more times, `[mm:ss.xx]`, and a few `[key:value]` lines about the
 * file. Text with no times at all is plain lyrics, kept as written.
 *
 * - Times: `[m:ss]`, `[mm:ss.x]`, `[mm:ss.xx]`, `[mm:ss.xxx]`, and `[mm:ss:xx]` as
 *   some tools write the fraction; minutes may pass 59. A line with several times
 *   is sung at each.
 * - `[offset:+500]` moves every line 500 ms earlier (LRC's sign: + is sooner).
 * - Word times inside a line (`<mm:ss.xx>`, "enhanced" LRC) are left out: lines
 *   are lit whole.
 * - `[ar:…]`, `[ti:…]`, `[al:…]`, `[by:…]`, `[length:…]` and the like are left out;
 *   anything else in brackets ("[Chorus]") is a line of the lyrics.
 * - A timed empty line is a pause; a line with no time in timed lyrics (a credit)
 *   is left out.
 */
object Lrc {
    private val time = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val wordTime = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")
    private val offset = Regex("""^\[offset:\s*([+-]?\d+)\s*]$""", RegexOption.IGNORE_CASE)
    private val info = Regex("""^\[(?:ar|al|ti|au|by|length|re|ve|tool|la|lang|id|#)\s*:.*]$""", RegexOption.IGNORE_CASE)

    /** The lyrics in [text], or null when it holds no words. */
    fun parse(text: String, source: LyricsSource): Lyrics? {
        val timed = ArrayList<LyricLine>()
        val plain = ArrayList<String>()
        var offsetMs = 0L

        for (raw in text.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            val line = raw.trim()
            offset.matchEntire(line)?.let { m ->
                offsetMs = m.groupValues[1].toLongOrNull() ?: 0
                continue
            }
            if (info.matches(line)) continue

            val times = ArrayList<Long>()
            var at = 0
            while (true) {
                while (at < line.length && line[at].isWhitespace()) at++
                val m = time.find(line, at)?.takeIf { it.range.first == at } ?: break
                times.add(millis(m))
                at = m.range.last + 1
            }
            if (times.isEmpty()) {
                plain.add(line)
            } else {
                val words = wordTime.replace(line.substring(at), "").trim().replace(Regex("""\s{2,}"""), " ")
                for (t in times) timed.add(LyricLine(t, words))
            }
        }

        if (timed.any { it.text.isNotEmpty() }) {
            val sorted = timed.map { it.copy(startMs = maxOf(0, it.startMs - offsetMs)) }.sortedBy { it.startMs }
            return Lyrics(tidy(sorted), synced = true, source)
        }
        val lines = tidy(plain.map { LyricLine(-1, it) })
        return if (lines.isEmpty()) null else Lyrics(lines, synced = false, source)
    }

    /**
     * The first of [texts] (each with where it came from) that is timed, or the
     * first with any words when none is: a song's .lrc file, then its tags.
     */
    fun pick(texts: List<Pair<String, LyricsSource>>): Lyrics? {
        val parsed = texts.mapNotNull { (text, source) -> parse(text, source) }
        return parsed.firstOrNull { it.synced } ?: parsed.firstOrNull()
    }

    /** "[01:02.5]" → 62,500 ms. */
    private fun millis(m: MatchResult): Long {
        val minutes = m.groupValues[1].toLong()
        val seconds = m.groupValues[2].toLong()
        val fraction = m.groupValues[3]
        val ms = if (fraction.isEmpty()) 0L else fraction.padEnd(3, '0').toLong()
        return (minutes * 60 + seconds) * 1000 + ms
    }

    /** No break at the start or end, and never two together. */
    private fun tidy(lines: List<LyricLine>): List<LyricLine> {
        val out = ArrayList<LyricLine>(lines.size)
        for (line in lines) {
            if (line.text.isEmpty() && (out.isEmpty() || out.last().text.isEmpty())) continue
            out.add(line)
        }
        // A timed break at the end stays: it ends the last line, so nothing is lit through the outro.
        while (out.isNotEmpty() && out.last().text.isEmpty() && out.last().startMs < 0) out.removeAt(out.size - 1)
        return out
    }
}
