package com.mpaulrenlund.audiofool.core.library

import com.mpaulrenlund.audiofool.core.model.Track

/**
 * A single-track tag edit (Edit Tags, spec 5.4). Ported from the desktop's
 * `AudioFool.Core/Library/TagEdit.cs` (`TrackTagEdit`). The positional fields
 * are always written, from the boxes as they stand; [artist] and [albumArtist]
 * only where the box no longer reads the file's first value (the writer's rule,
 * so fixing a title keeps the "B" of "A; B"); [details] only where changed.
 * [date] is the full date when one naming a month was typed ("2026-10-02");
 * [year] is its year. Both null clears the year.
 */
data class TrackTagEdit(
    val title: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val year: Int?,
    val trackNumber: Int?,
    val trackCount: Int?,
    val discNumber: Int?,
    val discCount: Int?,
    val details: TagDetailsEdit = TagDetailsEdit.None,
    val date: String? = null,
)

/**
 * Changes to the tags the library doesn't keep (shown only in Edit Tags, so
 * read from the file when it opens). Each is null to leave the file's value
 * alone, "" to clear it, or the new text. Composer and Genre hold several values
 * separated by semicolons. The desktop's `TagDetailsEdit`.
 */
data class TagDetailsEdit(
    val publisher: String? = null,
    val composer: String? = null,
    val conductor: String? = null,
    val genre: String? = null,
    val comment: String? = null,
) {
    companion object {
        val None = TagDetailsEdit()
    }
}

/**
 * What a file holds for the [TagDetailsEdit] fields, Composer's and Genre's
 * values joined by [SEPARATOR]; with the release date when it names more than
 * the year ("" otherwise: the library keeps only the year) and the numbers as
 * the file spells them. The desktop's `TagDetails`.
 */
data class TagDetails(
    val publisher: String = "",
    val composer: String = "",
    val conductor: String = "",
    val genre: String = "",
    val comment: String = "",
    val date: String = "",
    val numbers: NumberTexts = NumberTexts.Empty,
) {
    companion object {
        /** Joins several composers or genres for the box, and splits them on save. */
        const val SEPARATOR = "; "

        fun join(values: Iterable<String>?): String =
            values?.map { it.trim() }?.filter { it.isNotEmpty() }?.joinToString(SEPARATOR) ?: ""

        fun split(value: String): List<String> =
            value.split(';').map { it.trim() }.filter { it.isNotEmpty() }
    }
}

/**
 * A file's track and disc numbers and totals as text, leading zeros kept, so the
 * window shows "01" where the number reads 1: a leading zero is something the
 * user wants to see and fix. "" for one the file lacks. The desktop's `NumberTexts`.
 */
data class NumberTexts(val trackNumber: String, val trackCount: String, val discNumber: String, val discCount: String) {
    companion object {
        val Empty = NumberTexts("", "", "", "")

        /** The track's own numbers, when the file can't be read: no spellings to go by. */
        fun of(track: Track) = NumberTexts(
            track.trackNumber?.toString() ?: "",
            track.trackCount?.toString() ?: "",
            track.discNumber?.toString() ?: "",
            track.discCount?.toString() ?: "",
        )

        /**
         * From the fields as the file holds them: [track] ("01/12" or "01"), [trackTotal]
         * (Xiph's own TRACKTOTAL, or TOTALTRACKS), and the same for the disc. The number is
         * read as the scan reads it ([TagReader]); a spelling is kept only when it is plain
         * digits naming that number, else the number is written plainly (the desktop's
         * `TagReader.ReadNumbers`).
         */
        fun read(track: String?, trackTotal: String?, disc: String?, discTotal: String?): NumberTexts {
            val (trackNumber, trackCountFromNumber) = TagReader.numberPair(track)
            val (discNumber, discCountFromNumber) = TagReader.numberPair(disc)
            return NumberTexts(
                asWritten(trackNumber, listOf(part(track, 0))),
                asWritten(trackCountFromNumber ?: leading(trackTotal), listOf(trackTotal, part(track, 1))),
                asWritten(discNumber, listOf(part(disc, 0))),
                asWritten(discCountFromNumber ?: leading(discTotal), listOf(discTotal, part(disc, 1))),
            )
        }

        // "01/12" -> "01" or "12".
        private fun part(text: String?, index: Int): String? = text?.split('/')?.getOrNull(index)?.trim()

        private fun leading(text: String?): Int? =
            text?.trim()?.takeWhile { it in '0'..'9' }?.take(9)?.toIntOrNull()?.takeIf { it > 0 }

        private fun asWritten(value: Int?, spellings: List<String?>): String {
            if (value == null) return ""
            for (text in spellings) {
                if (!text.isNullOrEmpty() && text.all { it in '0'..'9' } && text.length <= 9 && text.toInt() == value) return text
            }
            return value.toString()
        }
    }
}

/** The boxes of the Edit Tags screen, in the desktop window's order. */
enum class TagField(val label: String) {
    Title("Title"),
    Artist("Artist"),
    AlbumArtist("Album Artist"),
    Album("Album"),
    Year("Year"),
    TrackNumber("Track #"),
    TrackCount("Total tracks"),
    DiscNumber("Disc #"),
    DiscCount("Total discs"),
    Publisher("Publisher"),
    Composer("Composer"),
    Conductor("Conductor"),
    Genre("Genre"),
    Comment("Comments"),
    ;

    /** The five the desktop gives a clear button: the library doesn't keep them. */
    val isDetail: Boolean get() = this in DETAILS

    companion object {
        val DETAILS = setOf(Publisher, Composer, Conductor, Genre, Comment)
    }
}

/**
 * The single-track Edit Track Tags window's rules, from the desktop's
 * `TagEditViewModel` (its single-track constructor, `Revalidate`, `IfChanged`,
 * `BuildTrackEdit`, `HasChanges`). Boxes are held as text by [TagField].
 */
object TagEditRules {
    /** Every box filled from the library's [track], and from the file's [details] where it could be read. */
    fun initial(track: Track, details: TagDetails?): Map<TagField, String> {
        val numbers = details?.numbers ?: NumberTexts.of(track)
        return mapOf(
            TagField.Title to track.title,
            TagField.Artist to track.artist,
            TagField.AlbumArtist to track.albumArtist,
            TagField.Album to track.album,
            // The library keeps only the year; the file may hold the whole date.
            TagField.Year to (details?.date?.takeIf { it.isNotEmpty() } ?: track.year?.toString() ?: ""),
            TagField.TrackNumber to numbers.trackNumber,
            TagField.TrackCount to numbers.trackCount,
            TagField.DiscNumber to numbers.discNumber,
            TagField.DiscCount to numbers.discCount,
            TagField.Publisher to (details?.publisher ?: ""),
            TagField.Composer to (details?.composer ?: ""),
            TagField.Conductor to (details?.conductor ?: ""),
            TagField.Genre to (details?.genre ?: ""),
            TagField.Comment to (details?.comment ?: ""),
        )
    }

    private val numberFields = listOf(TagField.TrackNumber, TagField.TrackCount, TagField.DiscNumber, TagField.DiscCount)

    /**
     * Why the boxes can't be saved as they are, the first reason only; null when
     * they can. The desktop's `Revalidate`, with the grid's stricter numbers
     * (`InlineTagEdit`): digits only and at least 1. The window's own
     * `int.TryParse` took "-2" and wrote it into the file as 4294967294.
     */
    fun problem(values: Map<TagField, String>): String? {
        if (text(values, TagField.Album).isBlank()) return "Album can't be empty."
        val year = text(values, TagField.Year)
        if (year.isNotBlank() && ReleaseDate.tryParse(year) == null) return "Year must be a year (2026) or a date (2026-10-02)."
        for (field in numberFields) {
            val value = text(values, field).trim()
            if (value.isNotEmpty() && wholeNumber(value) == null) return "${field.label} must be a whole number."
        }
        return null
    }

    /** "12" and "012" are 12; "", "0", "-2", "+3" and "1.5" are not whole numbers from 1 up. */
    fun wholeNumber(text: String): Int? {
        val t = text.trim()
        if (t.isEmpty() || !t.all { it in '0'..'9' }) return null
        return t.toIntOrNull()?.takeIf { it >= 1 }
    }

    /**
     * The edit the boxes come to, for boxes [problem] accepts. A detail box is
     * written when its text changed, or when it was [cleared] with its button
     * and left empty (an empty box the user cleared that way writes "").
     */
    fun edit(initial: Map<TagField, String>, values: Map<TagField, String>, cleared: Set<TagField>): TrackTagEdit {
        val parsed = ReleaseDate.tryParse(text(values, TagField.Year))
        return TrackTagEdit(
            title = text(values, TagField.Title).trim(),
            artist = text(values, TagField.Artist).trim(),
            albumArtist = text(values, TagField.AlbumArtist).trim(),
            album = text(values, TagField.Album).trim(),
            year = parsed?.year,
            trackNumber = wholeNumber(text(values, TagField.TrackNumber)),
            trackCount = wholeNumber(text(values, TagField.TrackCount)),
            discNumber = wholeNumber(text(values, TagField.DiscNumber)),
            discCount = wholeNumber(text(values, TagField.DiscCount)),
            details = details(initial, values, cleared),
            date = parsed?.normalized?.takeIf { ReleaseDate.hasMonth(it) },
        )
    }

    /** Whether anything differs from what the boxes started with: typed text, or a cleared detail. */
    fun hasChanges(initial: Map<TagField, String>, values: Map<TagField, String>, cleared: Set<TagField>): Boolean =
        TagField.entries.filterNot { it.isDetail }.any { typed(initial, values, it) } ||
            details(initial, values, cleared) != TagDetailsEdit.None

    private fun details(initial: Map<TagField, String>, values: Map<TagField, String>, cleared: Set<TagField>) = TagDetailsEdit(
        publisher = ifChanged(initial, values, cleared, TagField.Publisher),
        composer = ifChanged(initial, values, cleared, TagField.Composer),
        conductor = ifChanged(initial, values, cleared, TagField.Conductor),
        genre = ifChanged(initial, values, cleared, TagField.Genre),
        comment = ifChanged(initial, values, cleared, TagField.Comment),
    )

    /** The box's text when the user changed it or cleared it with the button, otherwise null for "leave alone". */
    private fun ifChanged(initial: Map<TagField, String>, values: Map<TagField, String>, cleared: Set<TagField>, field: TagField): String? {
        val now = text(values, field).trim()
        if (now.isEmpty() && field in cleared) return ""
        return if (now == text(initial, field).trim()) null else now
    }

    private fun typed(initial: Map<TagField, String>, values: Map<TagField, String>, field: TagField) =
        text(values, field).trim() != text(initial, field).trim()

    private fun text(values: Map<TagField, String>, field: TagField) = values[field] ?: ""
}
