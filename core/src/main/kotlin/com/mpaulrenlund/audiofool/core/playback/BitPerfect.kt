package com.mpaulrenlund.audiofool.core.playback

/** Where the sound goes, as the readout names it (spec 6.3). */
enum class OutputRoute(val label: String) {
    Usb("USB"),
    Bluetooth("Bluetooth"),
    Speaker("Speaker"),

    /** A headphone jack or line out (the M21's 3.5 mm and 4.4 mm). */
    Headphones("Headphones"),

    /** Anything else (HDMI, the earpiece, a car): treated like the speaker. */
    Other("Output"),
}

/**
 * Players with no speaker, whose headphone outs Android lists as the built-in
 * speaker: the FiiO M21 lists only "speaker", earpiece and telephony outputs,
 * with its 3.5 mm and 4.4 mm jacks behind the "speaker" (the user's report on
 * 0.1.1). There the speaker route is the headphone out.
 *
 * 0.1.2 looked for a maker or brand of exactly "FiiO" and the M21 wasn't caught;
 * its speaker calls itself "FiiO M21", so the model and that name count too, and
 * "FiiO" may be anywhere in any of them.
 */
object SpeakerlessPlayers {
    /**
     * @param names The phone's `Build.MANUFACTURER`, `Build.BRAND`, `Build.MODEL`
     *   and the speaker's own product name: any that says FiiO.
     */
    fun speakerIsHeadphones(vararg names: String?): Boolean =
        names.any { it != null && it.contains("FiiO", ignoreCase = true) }

    /** The route as the readout and Bit-Perfect should take it on this player. */
    fun route(route: OutputRoute, speakerIsHeadphones: Boolean): OutputRoute =
        if (speakerIsHeadphones && route == OutputRoute.Speaker) OutputRoute.Headphones else route
}

/** What Bit-Perfect does on a route (spec 6.2). */
enum class BitPerfectWay {
    /** It can't do anything here: BP is greyed out (Bluetooth, the speaker). */
    None,

    /**
     * Android 14+'s mixer attributes on a USB device: the bit-perfect mixer at
     * the track's rate where the phone offers one, else an ordinary mixer entry
     * at the track's rate ("rate matched", the user's call in session 4).
     */
    UsbMixer,

    /**
     * Open the output at the track's own rate, in whole-number samples, at full
     * volume, and leave the rest to the system (FiiO's SRC bypass on the M21).
     * Whether it arrived at that rate is checked afterwards, not assumed.
     */
    SourceRate,
}

/**
 * Which way Bit-Perfect works on each route (spec 6.2, session 4's research):
 * USB on Android 14+ asks for the mixer; USB without that API (the M21 is
 * Android 13) and a headphone out rely on the system leaving the rate alone;
 * Bluetooth and the speaker always go through the mixer.
 */
object BitPerfectRule {
    /**
     * @param mixerApi Android 14 or later (`setPreferredMixerAttributes`).
     * @param usbMixerEntries How many mixer entries the USB device lists; a USB
     *   device that lists none can't be configured, so BP is greyed there.
     */
    fun way(route: OutputRoute, mixerApi: Boolean, usbMixerEntries: Int): BitPerfectWay = when (route) {
        OutputRoute.Usb -> when {
            !mixerApi -> BitPerfectWay.SourceRate
            usbMixerEntries > 0 -> BitPerfectWay.UsbMixer
            else -> BitPerfectWay.None
        }
        OutputRoute.Headphones -> BitPerfectWay.SourceRate
        OutputRoute.Bluetooth, OutputRoute.Speaker, OutputRoute.Other -> BitPerfectWay.None
    }
}

/** A PCM sample format as a mixer lists it. */
enum class SampleKind(val bits: Int) {
    Int16(16),
    Int24(24),
    Int32(32),
    Float(32),
}

/** One entry of `AudioManager.getSupportedMixerAttributes`: a rate, channels and sample format, and whether it is the bit-perfect mixer. */
data class MixerEntry(val sampleRate: Int, val channels: Int, val kind: SampleKind?, val bitPerfect: Boolean)

/**
 * Which mixer entry a track plays through on a USB device (session 4):
 * the bit-perfect mixer at the track's own rate and channels if there is one,
 * else an ordinary mixer at that rate ("rate matched"), else none (the shared
 * output). Within each, the sample format that holds the track's samples
 * exactly, narrowest first: a 16-bit track goes out as 16-bit where it can,
 * 24-bit as 24-bit, and never into a narrower format than its own.
 */
object MixerChoice {
    /**
     * @param sourceBits The track's own bit depth: 16, 24 (also for 32-bit
     *   sources, which float decodes to 24 bits), or 0 for a decoder that gives
     *   float (lossy files, DSD converted to PCM).
     * @param dop The track goes out as DSD over PCM: only the bit-perfect mixer,
     *   in a 24- or 32-bit container.
     * @return The index of the chosen entry in [entries], or -1.
     */
    fun pick(entries: List<MixerEntry>, sampleRate: Int, channels: Int, sourceBits: Int, dop: Boolean): Int {
        val kinds = when {
            dop -> listOf(SampleKind.Int24, SampleKind.Int32)
            sourceBits in 1..16 -> listOf(SampleKind.Int16, SampleKind.Int24, SampleKind.Int32)
            sourceBits > 16 -> listOf(SampleKind.Int24, SampleKind.Int32)
            // A float decoder: the widest whole-number format, then float itself.
            else -> listOf(SampleKind.Int32, SampleKind.Int24, SampleKind.Float, SampleKind.Int16)
        }
        val behaviours = if (dop) listOf(true) else listOf(true, false)
        for (bitPerfect in behaviours) {
            for (kind in kinds) {
                val i = entries.indexOfFirst {
                    it.bitPerfect == bitPerfect && it.kind == kind && it.sampleRate == sampleRate && it.channels == channels
                }
                if (i >= 0) return i
            }
        }
        return -1
    }

    /** The rates the bit-perfect mixer offers for DoP in [channels] (DopRule's "exclusive rates"). */
    fun dopRates(entries: List<MixerEntry>, channels: Int): Set<Int> =
        entries.filter { it.bitPerfect && it.channels == channels && (it.kind == SampleKind.Int24 || it.kind == SampleKind.Int32) }
            .map { it.sampleRate }.toSet()

    /**
     * For the debug screen: the entries grouped by behaviour, e.g.
     * "bit-perfect: 44.1, 48, 96 kHz · 16, 24, 32-bit · 2 ch".
     */
    fun summary(entries: List<MixerEntry>): String {
        if (entries.isEmpty()) return "none"
        return listOf(true, false).mapNotNull { bitPerfect ->
            val some = entries.filter { it.bitPerfect == bitPerfect }
            if (some.isEmpty()) return@mapNotNull null
            val rates = some.map { it.sampleRate }.distinct().sorted().joinToString(", ") { OutputReadout.rate(it).removeSuffix(" kHz") }
            val kinds = some.mapNotNull { it.kind }.distinct().sorted().joinToString(", ") { if (it == SampleKind.Float) "float" else "${it.bits}" }
            val channels = some.map { it.channels }.distinct().sorted().joinToString(", ")
            val unknown = if (some.any { it.kind == null }) " (+ other formats)" else ""
            "${if (bitPerfect) "bit-perfect" else "default"}: $rates kHz · $kinds-bit$unknown · $channels ch"
        }.joinToString("\n")
    }
}
