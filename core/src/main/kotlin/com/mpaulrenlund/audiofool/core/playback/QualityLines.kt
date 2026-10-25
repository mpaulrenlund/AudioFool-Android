package com.mpaulrenlund.audiofool.core.playback

import com.mpaulrenlund.audiofool.core.format.Display
import com.mpaulrenlund.audiofool.core.library.AudioFormats
import com.mpaulrenlund.audiofool.core.model.Track
import java.util.Locale

/**
 * Now Playing's two right-aligned quality lines (spec 5): format and sample
 * rate ("FLAC · 96 kHz"), then bit depth and bitrate ("24 bit · 1,140 kbps").
 * Lossy files have no bit depth, so only the bitrate ("320 kbps"). DSD reads
 * "DSD · 2.8 MHz" (DSD64), then "1 bit". New for the phone: the desktop shows
 * these as table columns.
 */
object QualityLines {
    fun of(track: Track): Pair<String, String> {
        val rate = track.sampleRate ?: 0
        if (AudioFormats.isDsd(track.filePath)) {
            val mhz = if (rate > 0) " · %.1f MHz".format(Locale.ROOT, rate / 1_000_000.0) else ""
            return "DSD$mhz" to Display.bitDepth(track.bitDepth ?: 1)
        }

        val first = listOf(track.kind, Display.sampleRate(rate)).filter { it.isNotEmpty() }.joinToString(" · ")
        val depth = track.bitDepth?.takeIf { it > 0 }?.let(Display::bitDepth)
        val second = listOfNotNull(depth, Display.bitrate(track.bitrate).ifEmpty { null }).joinToString(" · ")
        return first to second
    }
}
