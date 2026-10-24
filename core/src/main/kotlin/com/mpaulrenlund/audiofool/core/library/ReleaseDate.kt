package com.mpaulrenlund.audiofool.core.library

/**
 * Release dates: a year ("2026"), a month ("2026-10") or a full date
 * ("2026-10-02"), always in that ISO order, the one every tag format's date
 * field uses. Ported from the desktop's `AudioFool.Core/Library/ReleaseDate.cs`.
 */
object ReleaseDate {
    data class Parsed(val normalized: String, val year: Int)

    // [0-9], not \d: \d would match other scripts' digits, which the desktop found
    // made a scan fail. Kotlin's \d is ASCII-only, but the pattern says so plainly.
    private val typed = Regex("^(?<y>[0-9]{4})(-(?<m>[0-9]{1,2})(-(?<d>[0-9]{1,2}))?)?$")
    private val stored = Regex("^(?<y>[0-9]{4})(-(?<m>[0-9]{2})(-(?<d>[0-9]{2}))?)?([T ].*)?$")

    /**
     * Parses what was typed, padding single-digit months and days
     * ("2026-1-2" becomes "2026-01-02"). Null when it isn't a date.
     */
    fun tryParse(text: String?): Parsed? =
        typed.matchEntire(text?.trim() ?: "")?.let(::build)

    /**
     * Reads a date as a tag stores it, which can carry a time as well:
     * "2014-05-01T07:00:00Z" in MP4, "2014-05-01T12:30" in ID3v2.4. Only the
     * date is kept. Null when the tag holds nothing usable.
     */
    fun fromTag(raw: String?): String? =
        stored.matchEntire(raw?.trim() ?: "")?.let(::build)?.normalized

    /** True when the date names more than the year. */
    fun hasMonth(normalized: String): Boolean = normalized.length > 4

    private fun build(match: MatchResult): Parsed? {
        val year = match.groups["y"]!!.value.toInt()

        // The same range TagReader accepts as a year.
        if (year <= 1000 || year >= 2200) return null

        val m = match.groups["m"] ?: return Parsed(year.toString().padStart(4, '0'), year)
        val month = m.value.toInt()
        if (month !in 1..12) return null

        val d = match.groups["d"] ?: return Parsed("${pad4(year)}-${pad2(month)}", year)
        val day = d.value.toInt()
        if (day < 1 || day > java.time.YearMonth.of(year, month).lengthOfMonth()) return null

        return Parsed("${pad4(year)}-${pad2(month)}-${pad2(day)}", year)
    }

    private fun pad4(n: Int) = n.toString().padStart(4, '0')
    private fun pad2(n: Int) = n.toString().padStart(2, '0')
}
