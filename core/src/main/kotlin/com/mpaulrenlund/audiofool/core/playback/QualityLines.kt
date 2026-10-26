package com.mpaulrenlund.audiofool.core.playback

import com.mpaulrenlund.audiofool.core.format.Display
import com.mpaulrenlund.audiofool.core.library.AudioFormats
import com.mpaulrenlund.audiofool.core.model.Track
import java.util.Locale

/**
 * Now Playing's quality line (spec 5), right-aligned over the seekbar: format,
 * bitrate, bit depth, sample rate, "FLAC · 1,901 kbps · 24-Bit · 48 kHz" (the
 * user's order, 0.2.2; until then two lines, "FLAC · 96 kHz" over "24 bit ·
 * 1,140 kbps"). Lossy files have no bit depth ("MP3 · 320 kbps · 44.1 kHz"); DSD
 * gives its rate in MHz ("DSD · 5,645 kbps · 1-Bit · 2.8 MHz" for DSD64). Whatever isn't known
 * is left out. New for the phone: the desktop shows these as table columns.
 */
object QualityLines {
    fun of(track: Track): String {
        val rate = track.sampleRate ?: 0
        val bitrate = Display.bitrate(track.bitrate)
        if (AudioFormats.isDsd(track.filePath)) {
            val mhz = if (rate > 0) "%.1f MHz".format(Locale.ROOT, rate / 1_000_000.0) else ""
            return join("DSD", bitrate, depth(track.bitDepth ?: 1), mhz)
        }
        return join(track.kind, bitrate, depth(track.bitDepth), Display.sampleRate(rate))
    }

    /** "24-Bit"; nothing for a lossy file, which has none. */
    private fun depth(bits: Int?): String = bits?.takeIf { it > 0 }?.let { "$it-Bit" } ?: ""

    private fun join(vararg parts: String): String = parts.filter { it.isNotEmpty() }.joinToString(" · ")
}
