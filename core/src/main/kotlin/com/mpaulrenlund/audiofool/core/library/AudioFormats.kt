package com.mpaulrenlund.audiofool.core.library

import java.util.Locale

/**
 * The single place that knows which files we consider music, what to call each
 * format, and which of them have a meaningful bit depth.
 * Ported unchanged from the desktop's `AudioFool.Core/Library/AudioFormats.cs`.
 */
object AudioFormats {
    /** Extension (lower case) to format name. */
    val kindByExtension: Map<String, String> = mapOf(
        // Core four - what this player is built around.
        ".mp3" to "MP3",
        ".flac" to "FLAC",
        ".ogg" to "Ogg Vorbis",
        ".oga" to "Ogg Vorbis",
        ".dsf" to "DSD",
        ".dff" to "DSD",

        // Lossy
        ".m4a" to "AAC",          // refined to ALAC at read time when detected
        ".m4b" to "AAC",
        ".mp4" to "AAC",
        ".aac" to "AAC",
        ".opus" to "Opus",
        ".wma" to "WMA",
        ".mpc" to "Musepack",
        ".mp+" to "Musepack",
        ".mpp" to "Musepack",
        ".ac3" to "AC-3",
        ".dts" to "DTS",

        // Lossless / uncompressed
        ".wav" to "WAV",
        ".wave" to "WAV",
        ".aiff" to "AIFF",
        ".aif" to "AIFF",
        ".aifc" to "AIFF",
        ".ape" to "APE",
        ".wv" to "WavPack",
        ".tak" to "TAK",
        ".alac" to "ALAC",

        // Tracker / module formats
        ".mod" to "MOD",
        ".s3m" to "S3M",
        ".xm" to "XM",
        ".it" to "IT",
        ".mtm" to "MTM",
        ".umx" to "UMX",
        ".mo3" to "MO3",
    )

    /**
     * Formats that decode to float and therefore have no source bit depth.
     * The bit depth stays blank for these rather than inventing a 16.
     */
    private val lossyExtensions = setOf(
        ".mp3", ".ogg", ".oga", ".opus", ".m4a", ".m4b", ".mp4",
        ".aac", ".wma", ".mpc", ".mp+", ".mpp", ".ac3", ".dts",
    )

    /** Tracker formats. BASS loads these through MusicLoad rather than CreateStream. */
    private val moduleExtensions = setOf(".mod", ".s3m", ".xm", ".it", ".mtm", ".umx", ".mo3")

    private val dsdExtensions = setOf(".dsf", ".dff", ".dsd")

    private fun ext(path: String) = Paths.extension(path).lowercase(Locale.ROOT)

    fun isSupported(path: String): Boolean = ext(path) in kindByExtension

    fun isLossy(path: String): Boolean = ext(path) in lossyExtensions

    fun isModule(path: String): Boolean = ext(path) in moduleExtensions

    /**
     * DSD containers. These get their own playback path: either converted to PCM,
     * or passed straight through as DSD-over-PCM to a DAC that understands it.
     */
    fun isDsd(path: String): Boolean = ext(path) in dsdExtensions

    fun kindFor(path: String): String =
        kindByExtension[ext(path)] ?: Paths.extension(path).trimStart('.').uppercase(Locale.ROOT)

    /** Cover art file names looked for beside the audio, in priority order. */
    val folderArtNames: List<String> = listOf(
        "cover.jpg", "cover.jpeg", "cover.png",
        "folder.jpg", "folder.jpeg", "folder.png",
        "front.jpg", "front.jpeg", "front.png",
        "album.jpg", "album.jpeg", "album.png",
        "albumart.jpg", "artwork.jpg", "thumb.jpg",
    )

    /**
     * The folder art among [namesInFolder] (the folder's file names, as listed),
     * by the priority of [folderArtNames], ignoring case. The desktop's
     * `TagReader.FindFolderArt` asks the file system for each name in turn; the
     * scan here already has the folder's listing, so it asks that instead.
     */
    fun pickFolderArt(namesInFolder: Collection<String>): String? {
        if (namesInFolder.isEmpty()) return null
        val byLower = namesInFolder.associateBy { it.lowercase(Locale.ROOT) }
        return folderArtNames.firstNotNullOfOrNull { byLower[it] }
    }
}
