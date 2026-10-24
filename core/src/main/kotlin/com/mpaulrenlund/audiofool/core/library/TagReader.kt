package com.mpaulrenlund.audiofool.core.library

import com.mpaulrenlund.audiofool.core.model.Track

/**
 * What a tag library found in one file, before the desktop's rules turn it into
 * a [Track]. The app fills it with TagLib (through the NDK); tests use fakes.
 */
data class RawTags(
    val status: Status,
    val title: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val album: String? = null,
    /** As written: "3", "03" or "3/12". */
    val trackNumber: String? = null,
    val trackTotal: String? = null,
    val discNumber: String? = null,
    val discTotal: String? = null,
    val year: Int? = null,
    /** The format's own date field, already left out where it can't be trusted (ID3v2.3). */
    val date: String? = null,
    val durationMs: Long = 0,
    val sampleRate: Int? = null,
    val bitrate: Int? = null,
    val bitsPerSample: Int? = null,
    val isAppleLossless: Boolean = false,
) {
    enum class Status {
        Read,

        /** Opens, but the tag library can't make sense of it. Reads the same next time. */
        Unparsed,

        /** Couldn't be opened at all: its card gone, or no access. */
        Unopened,
    }
}

/** Reads one file's tags. Must be safe to call from several threads at once. */
fun interface TagSource {
    fun read(path: String): RawTags
}

/**
 * Turns a file on disk into a [Track]. Never throws for a bad file: a file we
 * can't parse still shows up in the library using whatever its path gives, so
 * nothing silently disappears. Ported from the desktop's
 * `AudioFool.Core/Library/TagReader.cs` (the reading half; tag writing isn't in
 * the Android app).
 */
class TagReader(private val source: TagSource) {
    data class Result(val track: Track, val couldNotOpen: Boolean)

    /**
     * Reads one file. [stamp] is the stat the scan already made; [folderArt] the
     * cover file found beside it. A file that couldn't be opened has only what its
     * path gives, and its size and date are left unknown, so a cache never takes
     * it as up to date and the next scan reads it again. A file that opens but
     * can't be parsed is different: it reads the same next time, so it keeps its
     * real stamp and isn't retried at every scan.
     */
    fun read(path: String, stamp: FileStamp? = null, folderArt: String? = null): Result {
        var kind = AudioFormats.kindFor(path)
        var fileStamp = stamp ?: FileStamp.forPath(path)

        val raw = try {
            source.read(path)
        } catch (e: Exception) {
            RawTags(RawTags.Status.Unparsed)
        }

        val couldNotOpen = raw.status == RawTags.Status.Unopened
        if (couldNotOpen) fileStamp = FileStamp.Unknown.copy(createdUtc = fileStamp.createdUtc)
        val tags = if (raw.status == RawTags.Status.Read) raw else null

        val isAlac = tags?.isAppleLossless == true
        if (isAlac && kind == "AAC") kind = "ALAC"

        val lossy = AudioFormats.isLossy(path) && !isAlac

        var durationMs = tags?.durationMs ?: 0
        var sampleRate = positive(tags?.sampleRate)
        var bitrate = positive(tags?.bitrate)
        var bitDepth = if (lossy) null else positive(tags?.bitsPerSample)

        // DSD: fill any gaps the tag library left from the DSF header itself.
        if (kind == "DSD" && !couldNotOpen && (sampleRate == null || durationMs == 0L)) {
            DsfHeaderReader.tryRead(path)?.let { d ->
                sampleRate = sampleRate ?: d.sampleRate
                bitDepth = bitDepth ?: d.bitDepth
                bitrate = bitrate ?: d.bitrateKbps
                if (durationMs == 0L) durationMs = d.durationMs
            }
        }

        val (track, trackCountFromNumber) = numberPair(tags?.trackNumber)
        val (disc, discCountFromNumber) = numberPair(tags?.discNumber)

        return Result(
            Track(
                filePath = path,
                fileSize = fileStamp.length,
                modifiedUtc = fileStamp.modifiedUtc,
                addedUtc = fileStamp.createdUtc.takeIf { it != 0L },
                title = clean(tags?.title),
                artist = clean(tags?.artist ?: tags?.albumArtist),
                albumArtist = clean(tags?.albumArtist),
                album = clean(tags?.album),
                trackNumber = track,
                trackCount = trackCountFromNumber ?: leadingNumber(tags?.trackTotal),
                discNumber = disc,
                discCount = discCountFromNumber ?: leadingNumber(tags?.discTotal),
                // TagLib's APE tags (WavPack, APE, Musepack) give no year for a full
                // date like "2026-10-02"; TagLibSharp reads its first four digits.
                year = validYear(tags?.year) ?: ReleaseDate.fromTag(tags?.date)?.take(4)?.toInt(),
                releaseDate = ReleaseDate.fromTag(tags?.date)?.takeIf { ReleaseDate.hasMonth(it) },
                durationMs = durationMs,
                kind = kind,
                bitrate = bitrate,
                bitDepth = bitDepth,
                sampleRate = sampleRate,
                folderArtPath = folderArt,
            ),
            couldNotOpen,
        )
    }

    companion object {
        /** "3/12" -> (3, 12); "03" -> (3, null). Zero and garbage are null, as TagLibSharp gives 0. */
        fun numberPair(text: String?): Pair<Int?, Int?> {
            if (text.isNullOrBlank()) return null to null
            val parts = text.split('/')
            return leadingNumber(parts[0]) to parts.getOrNull(1)?.let(::leadingNumber)
        }

        private fun leadingNumber(text: String?): Int? =
            text?.trim()?.takeWhile { it in '0'..'9' }?.take(9)?.toIntOrNull()?.takeIf { it > 0 }

        private fun clean(value: String?): String = value?.trim() ?: ""

        private fun positive(value: Int?): Int? = value?.takeIf { it > 0 }

        /** Rejects the 0 and obvious-garbage years that tags are full of. */
        private fun validYear(year: Int?): Int? = year?.takeIf { it in 1001..2199 }
    }
}
