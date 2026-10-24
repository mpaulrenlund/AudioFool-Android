package com.mpaulrenlund.audiofool.core.format

import java.util.Locale
import kotlin.math.floor
import kotlin.math.max

/**
 * How times, numbers and sizes are written, kept in one place so every screen
 * agrees. Ported from the desktop's `AudioFool/Formatting/Display.cs`. Numbers
 * use the desktop's invariant formatting: a comma between thousands.
 */
object Display {
    /** "4:07", or "1:02:33" once past an hour. */
    fun time(ms: Long): String {
        val totalSeconds = max(0L, ms) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds / 60) % 60
        val seconds = totalSeconds % 60
        return if (hours >= 1) "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
        else "%d:%02d".format(Locale.ROOT, minutes, seconds)
    }

    /** "320 kbps". Blank when unknown. */
    fun bitrate(kbps: Int?): String = if (kbps != null && kbps > 0) "%,d kbps".format(Locale.ROOT, kbps) else ""

    /**
     * "16 bit", or an em dash for lossy formats where the concept doesn't apply.
     * DSD reports 1 bit, which is correct and worth showing.
     */
    fun bitDepth(bits: Int?): String = if (bits != null && bits > 0) "$bits bit" else "—"

    /**
     * "44.1 kHz", "192 kHz", "2822.4 kHz" for DSD64. Trailing ".0" is dropped
     * so the column doesn't read "48.0 kHz".
     */
    fun sampleRate(hz: Int?): String {
        if (hz == null || hz <= 0) return ""
        val khz = hz / 1000.0
        return if (khz == floor(khz)) "${khz.toLong()} kHz" else "${oneDecimal(khz)} kHz"
    }

    /** Blank rather than "0" for untagged track and disc numbers. */
    fun number(value: Int?): String = if (value != null && value > 0) value.toString() else ""

    /** "8,127": a whole number with thousands separated, as the desktop's N0. */
    fun count(value: Int): String = "%,d".format(Locale.ROOT, value)

    /**
     * "62.4%", "100%", or "<0.1%" for a share too small to round to anything -
     * a lone WMA file in 26,000 tracks should not read as "0%".
     */
    fun percent(fraction: Double): String {
        if (fraction <= 0) return "0%"
        val percent = fraction * 100
        return if (percent < 0.1) "<0.1%" else "${oneDecimal(percent)}%"
    }

    /**
     * "692 GB", "1.4 TB", "83 MB". Binary units under decimal names, the way
     * Explorer reports them, so the figure matches what the drive's Properties
     * dialog says.
     */
    fun size(bytes: Long): String {
        val units = arrayOf("bytes", "KB", "MB", "GB", "TB")
        var value = max(0L, bytes).toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.size - 1) {
            value /= 1024
            unit++
        }
        return when {
            unit == 0 -> "%,d %s".format(Locale.ROOT, value.toLong(), units[0])
            value >= 100 -> "%,d %s".format(Locale.ROOT, Math.round(value), units[unit])
            else -> "${oneDecimal(value)} ${units[unit]}"
        }
    }

    /**
     * A span too long for a timecode: "74 d 3 h", "14 h 20 m", "52 m".
     * Only the two largest units - nobody needs the seconds of a library.
     */
    fun longDuration(ms: Long): String {
        val minutesTotal = max(0L, ms) / 60_000
        val hoursTotal = minutesTotal / 60
        val days = hoursTotal / 24
        return when {
            days >= 1 -> "%,d d %d h".format(Locale.ROOT, days, hoursTotal % 24)
            hoursTotal >= 1 -> "$hoursTotal h ${minutesTotal % 60} m"
            else -> "$minutesTotal m"
        }
    }

    /** .NET's "0.#": one decimal at most, none when it rounds to a whole number. */
    private fun oneDecimal(value: Double): String {
        val rounded = Math.round(value * 10) / 10.0
        return if (rounded == floor(rounded)) rounded.toLong().toString() else "%.1f".format(Locale.ROOT, rounded)
    }
}
