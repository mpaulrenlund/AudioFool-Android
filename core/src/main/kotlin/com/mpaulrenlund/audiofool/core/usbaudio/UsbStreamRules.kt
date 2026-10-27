package com.mpaulrenlund.audiofool.core.usbaudio

// New (0.4.0, true Bit-Perfect on the S24's USB DAC; design/usb-bit-perfect.md): the rules the
// app's own USB Audio driver plays by. The feedback and packet-size rules are Linux's, from its
// USB audio driver (sound/usb/endpoint.c, snd_usb_handle_sync_urb and next_packet_size);
// the native driver (app/src/main/cpp/usbaudio_driver.cpp) does the same sums and says so.

/**
 * Which of the DAC's streaming settings a track plays through: one in PCM with the track's
 * channels, at a rate the DAC offers, in the narrowest slot that holds the track's samples
 * exactly (the same order as [com.mpaulrenlund.audiofool.core.playback.MixerChoice]): a 16-bit
 * track in 16-bit where it can, 24-bit in 24, and never narrower than its own. DoP goes in a
 * 24- or 32-bit slot.
 */
object UsbFormatChoice {
    /**
     * @param clockRates The rates the setting's clock offers (UAC2's RANGE reply); a UAC1
     *   setting lists its own.
     * @param sourceBits 16, 24 (also 32-bit sources, which float decodes to 24 bits), or 0
     *   for a decoder that gives float (lossy files, DSD converted to PCM).
     */
    fun pick(d: UsbAudioDescriptors, clockRates: List<Int>, sampleRate: Int, channels: Int, sourceBits: Int, dop: Boolean): StreamingSetting? {
        val fit = d.playback.filter {
            isPcm(d.uacVersion, it.formats) && it.channels == channels && it.dataEndpoint != null && offers(d.uacVersion, it, clockRates, sampleRate)
        }
        val slots: List<(StreamingSetting) -> Boolean> = when {
            dop -> listOf(slot(3, 24), slot(4, 24))
            sourceBits in 1..16 -> listOf(slot(2, 16), slot(3, 24), slot(4, 24))
            sourceBits > 16 -> listOf(slot(3, 24), slot(4, 24))
            // A float decoder: the widest whole-number slot.
            else -> listOf(slot(4, 24), slot(3, 24), slot(2, 16))
        }
        for (wanted in slots) fit.firstOrNull(wanted)?.let { return it }
        return null
    }

    private fun slot(bytes: Int, minBits: Int): (StreamingSetting) -> Boolean = { it.subslotBytes == bytes && it.bits >= minBits }

    fun isPcm(uac: Int, formats: Long): Boolean = if (uac == 2) formats and 0x1L != 0L else formats == 1L

    private fun offers(uac: Int, s: StreamingSetting, clockRates: List<Int>, rate: Int): Boolean =
        if (uac == 2) rate in clockRates else rate in s.rates || s.rateRange?.contains(rate) == true
}

/**
 * The DAC's own clock as its feedback endpoint reports it, in Q16.16 frames per (micro)frame
 * (Linux's `freqm`). The value's format differs between DACs (16.16, 10.14, sometimes shifted
 * further), so the first value is shifted until it is near the nominal rate, and that shift
 * kept, as Linux does; a value far off resets it. Until a value arrives, the nominal rate.
 */
class UsbFeedback(sampleRate: Int, highSpeed: Boolean) {
    /** The nominal rate in Q16.16 frames per microframe (high speed) or per frame (full speed). */
    val nominal: Int = nominal(sampleRate, highSpeed)

    /** The rate to send at now. */
    var current: Int = nominal
        private set

    /** The shift found from the first value, or null until then (or after a value far off). */
    var shift: Int? = null
        private set

    /** Most frames per (micro)frame the DAC may ask for: half again the nominal. */
    private val most = nominal + nominal / 2

    /**
     * Takes one feedback packet's [value] (little-endian, 3 or 4 bytes as read, already
     * masked to 28 bits). False if it was 0 or far off, and the rate stayed as it was.
     */
    fun take(value: Int): Boolean {
        if (value == 0) return false
        var f = value.toLong()
        val known = shift
        if (known == null) {
            // Assumes the DAC is within -25% and +50% of nominal, as Linux does.
            var s = 0
            while (f < nominal - nominal / 4) { f = f shl 1; s++ }
            while (f > nominal + nominal / 2) { f = f shr 1; s-- }
            shift = s
        } else {
            f = if (known >= 0) f shl known else f shr -known
        }
        if (f >= nominal - nominal / 8 && f <= most) {
            current = f.toInt()
            return true
        }
        shift = null
        return false
    }

    companion object {
        /** Linux's get_usb_high_speed_rate and get_usb_full_speed_rate. */
        fun nominal(sampleRate: Int, highSpeed: Boolean): Int =
            if (highSpeed) (((sampleRate.toLong() shl 10) + 62) / 125).toInt() else (((sampleRate.toLong() shl 13) + 62) / 125).toInt()

        /** A feedback packet's bytes as a value: 3 bytes (full speed's 10.14) or 4 (16.16), masked as Linux does. */
        fun valueOf(bytes: ByteArray, size: Int): Int {
            var v = 0L
            for (i in 0 until minOf(size, 4)) v = v or ((bytes[i].toLong() and 0xFF) shl (8 * i))
            return (v and 0x0FFFFFFF).toInt()
        }
    }
}

/**
 * How many frames each data packet carries: the rate in Q16.16 frames per (micro)frame,
 * times the (micro)frames a packet spans (`2^dataInterval`), its whole part sent and the
 * fraction carried on, never more than the packet holds (Linux's next_packet_size).
 */
class PacketSizer(private val dataInterval: Int, private val maxFrames: Int) {
    private var phase = 0L

    fun next(rate: Int): Int {
        phase = (phase and 0xFFFF) + (rate.toLong() shl dataInterval)
        return minOf((phase shr 16).toInt(), maxFrames)
    }
}

/**
 * The DAC's hardware volume (its feature unit) in the steps the volume keys move: [STEPS]
 * steps of 1 dB below the DAC's loudest, 0 being mute, each snapped to the DAC's own
 * resolution. The DAC's values are in 1/256 dB (UAC's units).
 */
class UsbVolume(val min: Int, val max: Int, val resolution: Int) {
    /** The value for [step] (0 is the quietest the DAC goes, and muted too). */
    fun value(step: Int): Int {
        val s = step.coerceIn(0, STEPS)
        if (s == 0) return min
        val wanted = (max - (STEPS - s) * DB).coerceIn(min, max)
        val res = resolution.coerceAtLeast(1)
        return (min + Math.round((wanted - min).toDouble() / res).toInt() * res).coerceIn(min, max)
    }

    fun muted(step: Int): Boolean = step <= 0

    /** The step nearest a value the DAC is at. */
    fun stepNear(value: Int): Int = (STEPS - Math.round((max - value).toDouble() / DB).toInt()).coerceIn(1, STEPS)

    companion object {
        const val STEPS = 60
        private const val DB = 256

        /**
         * Where to start when the app takes the DAC, from Android's media volume: its share
         * of the top, as 1 dB a step of 60 below the DAC's loudest. Android's own curve falls
         * less steeply, so this starts no louder than Android was, usually a little quieter.
         */
        fun stepFor(androidIndex: Int, androidMax: Int): Int =
            if (androidMax <= 0) STEPS else Math.round(STEPS.toDouble() * androidIndex.coerceIn(0, androidMax) / androidMax).toInt()

        /** "−12.5 dB". */
        fun db(value: Int): String = "%.1f dB".format(java.util.Locale.ENGLISH, value / 256.0).replace('-', '−')
    }
}
