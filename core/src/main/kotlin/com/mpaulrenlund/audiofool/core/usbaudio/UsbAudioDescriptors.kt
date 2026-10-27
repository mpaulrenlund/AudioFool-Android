package com.mpaulrenlund.audiofool.core.usbaudio

// A USB DAC's descriptors as the USB Audio Class 1.0 and 2.0 specs lay them out: what the
// app's own driver needs to know to play to it (design/usb-bit-perfect.md). New: the
// desktop leaves USB to Windows' driver, so there is nothing to port. 0.3.0 only reads and
// shows them (the look-only build); the driver is built on top later.

/** How an isochronous endpoint keeps time with the DAC (bmAttributes bits 2–3). */
enum class SyncType { None, Async, Adaptive, Sync }

/** What an isochronous endpoint carries (bmAttributes bits 4–5). */
enum class EndpointUsage { Data, Feedback, ImplicitFeedback }

class UsbEndpoint(
    val address: Int,
    val attributes: Int,
    /** wMaxPacketSize with the extra transactions per microframe (bits 11–12) applied. */
    val maxPacketBytes: Int,
    val interval: Int,
) {
    val isIn: Boolean get() = address and 0x80 != 0
    val isIsochronous: Boolean get() = attributes and 0x03 == 1
    val sync: SyncType get() = SyncType.entries[(attributes shr 2) and 0x03]
    val usage: EndpointUsage get() = when ((attributes shr 4) and 0x03) {
        1 -> EndpointUsage.Feedback
        2 -> EndpointUsage.ImplicitFeedback
        else -> EndpointUsage.Data
    }
}

/** A UAC2 clock source (the DAC's own clock, whose rates are asked for in a RANGE request). */
class ClockSource(val id: Int, val attributes: Int, val controls: Int) {
    /** bmAttributes bits 0–1: external, internal fixed, internal variable, internal programmable. */
    val kind: String get() = when (attributes and 0x03) {
        0 -> "external"
        1 -> "internal, fixed"
        2 -> "internal, variable"
        else -> "internal, programmable"
    }

    /** The frequency control (bmControls bits 0–1) is host programmable: the app can set the rate. */
    val rateSettable: Boolean get() = controls and 0x03 == 0x03
}

class ClockSelector(val id: Int, val inputs: List<Int>)

/** A feature unit's master (channel 0) mute and volume, read-only or settable. */
class FeatureUnit(val id: Int, val sourceId: Int, val masterMute: Boolean, val masterVolume: Boolean, val channelVolume: Boolean)

class Terminal(val id: Int, val type: Int, val input: Boolean, val clockId: Int, val sourceId: Int)

/** One alternate setting of an audio streaming interface: one sample format the DAC takes. */
class StreamingSetting(
    val interfaceNumber: Int,
    val alternate: Int,
    val terminalLink: Int,
    val channels: Int,
    /** Bytes per sample in the stream (bSubslotSize, UAC1's bSubframeSize). */
    val subslotBytes: Int,
    /** Bits of each sample the DAC uses (bBitResolution). */
    val bits: Int,
    /** UAC2's bmFormats, or UAC1's wFormatTag. */
    val formats: Long,
    /** UAC1 only: the rates the format descriptor lists (empty on UAC2: ask the clock). */
    val rates: List<Int>,
    /** UAC1 only: a continuous range instead of a list. */
    val rateRange: IntRange?,
    val endpoints: List<UsbEndpoint>,
) {
    val dataEndpoint: UsbEndpoint? get() = endpoints.firstOrNull { it.isIsochronous && it.usage != EndpointUsage.Feedback }
    val feedbackEndpoint: UsbEndpoint? get() = endpoints.firstOrNull { it.isIsochronous && it.usage == EndpointUsage.Feedback }
}

class UsbAudioDescriptors(
    val vendorId: Int,
    val productId: Int,
    /** 1 or 2 (from the audio control interface's protocol and header); 0 when there's no audio interface. */
    val uacVersion: Int,
    val controlInterface: Int,
    val clockSources: List<ClockSource>,
    val clockSelectors: List<ClockSelector>,
    val featureUnits: List<FeatureUnit>,
    val terminals: List<Terminal>,
    /** Every alternate setting that plays to the DAC (an OUT data endpoint), in descriptor order. */
    val playback: List<StreamingSetting>,
) {
    val isAudio: Boolean get() = uacVersion != 0

    /** The clock that feeds a streaming setting's terminal (UAC2): through the input terminal it links to. */
    fun clockFor(setting: StreamingSetting): Int? =
        terminals.firstOrNull { it.id == setting.terminalLink }?.clockId?.takeIf { it != 0 }

    /**
     * The feature unit on the way from a streaming setting's terminal to the output: where a
     * hardware volume would be. Units this parser doesn't read (mixers, selectors) break the
     * chain; then the first feature unit with a volume is taken.
     */
    fun volumeUnitFor(setting: StreamingSetting): FeatureUnit? {
        var id = setting.terminalLink
        repeat(16) {
            val unit = featureUnits.firstOrNull { it.sourceId == id } ?: return featureUnits.firstOrNull { it.masterVolume || it.channelVolume }
            if (unit.masterVolume || unit.channelVolume) return unit
            id = unit.id
        }
        return null
    }

    companion object {
        private const val INTERFACE = 4
        private const val ENDPOINT = 5
        private const val CS_INTERFACE = 0x24
        private const val AUDIO = 1
        private const val AUDIOCONTROL = 1
        private const val AUDIOSTREAMING = 2

        /** Parses `UsbDeviceConnection.getRawDescriptors()`: the device descriptor, then the active configuration. */
        fun parse(raw: ByteArray): UsbAudioDescriptors {
            val bytes = Bytes(raw)
            var vendor = 0
            var product = 0
            var uac = 0
            var controlIf = -1
            val clocks = mutableListOf<ClockSource>()
            val selectors = mutableListOf<ClockSelector>()
            val features = mutableListOf<FeatureUnit>()
            val terminals = mutableListOf<Terminal>()
            val playback = mutableListOf<StreamingSetting>()

            // The interface whose descriptors follow, and what its alternate setting has said so far.
            var inClass = 0
            var subclass = 0
            var ifNumber = 0
            var alternate = 0
            var terminalLink = -1
            var channels = 0
            var subslot = 0
            var bits = 0
            var formats = 0L
            var rates = mutableListOf<Int>()
            var range: IntRange? = null
            var endpoints = mutableListOf<UsbEndpoint>()

            fun finishSetting() {
                if (inClass == AUDIO && subclass == AUDIOSTREAMING && terminalLink >= 0 && subslot > 0 &&
                    endpoints.any { !it.isIn && it.isIsochronous }
                ) {
                    playback += StreamingSetting(ifNumber, alternate, terminalLink, channels, subslot, bits, formats, rates, range, endpoints)
                }
                terminalLink = -1; channels = 0; subslot = 0; bits = 0
                formats = 0; rates = mutableListOf(); range = null; endpoints = mutableListOf()
            }

            var at = 0
            while (at + 2 <= raw.size) {
                val length = bytes.u8(at)
                if (length < 2 || at + length > raw.size) break
                val type = bytes.u8(at + 1)
                when {
                    at == 0 && type == 1 && length >= 12 -> {
                        vendor = bytes.u16(at + 8)
                        product = bytes.u16(at + 10)
                    }
                    type == INTERFACE && length >= 9 -> {
                        finishSetting()
                        ifNumber = bytes.u8(at + 2)
                        alternate = bytes.u8(at + 3)
                        inClass = bytes.u8(at + 5)
                        subclass = bytes.u8(at + 6)
                        if (inClass == AUDIO && subclass == AUDIOCONTROL && controlIf < 0) {
                            controlIf = ifNumber
                            uac = if (bytes.u8(at + 7) == 0x20) 2 else 1
                        }
                    }
                    type == ENDPOINT && length >= 7 && inClass == AUDIO && subclass == AUDIOSTREAMING -> {
                        val size = bytes.u16(at + 4)
                        val packet = (size and 0x7FF) * (1 + ((size shr 11) and 0x03))
                        endpoints += UsbEndpoint(bytes.u8(at + 2), bytes.u8(at + 3), packet, bytes.u8(at + 6))
                    }
                    type == CS_INTERFACE && length >= 3 && inClass == AUDIO && subclass == AUDIOCONTROL && ifNumber == controlIf ->
                        readControl(bytes, at, length, uac, clocks, selectors, features, terminals)
                    type == CS_INTERFACE && length >= 3 && inClass == AUDIO && subclass == AUDIOSTREAMING -> {
                        val sub = bytes.u8(at + 2)
                        if (uac == 2) {
                            // UAC2 keeps bNrChannels in AS_GENERAL and the slot size in FORMAT_TYPE I.
                            if (sub == 1 && length >= 16) {
                                terminalLink = bytes.u8(at + 3)
                                formats = bytes.u32(at + 6)
                                channels = bytes.u8(at + 10)
                            } else if (sub == 2 && length >= 6 && bytes.u8(at + 3) == 1) {
                                subslot = bytes.u8(at + 4)
                                bits = bytes.u8(at + 5)
                            }
                        } else {
                            if (sub == 1 && length >= 7) {
                                terminalLink = bytes.u8(at + 3)
                                formats = bytes.u16(at + 5).toLong()
                            } else if (sub == 2 && length >= 8 && bytes.u8(at + 3) == 1) {
                                channels = bytes.u8(at + 4)
                                subslot = bytes.u8(at + 5)
                                bits = bytes.u8(at + 6)
                                val count = bytes.u8(at + 7)
                                if (count == 0 && length >= 14) range = bytes.u24(at + 8)..bytes.u24(at + 11)
                                else for (i in 0 until count) if (at + 8 + i * 3 + 3 <= at + length) rates += bytes.u24(at + 8 + i * 3)
                            }
                        }
                    }
                }
                at += length
            }
            finishSetting()
            return UsbAudioDescriptors(vendor, product, if (controlIf < 0) 0 else uac, controlIf, clocks, selectors, features, terminals, playback)
        }

        private fun readControl(
            bytes: Bytes, at: Int, length: Int, uac: Int,
            clocks: MutableList<ClockSource>, selectors: MutableList<ClockSelector>,
            features: MutableList<FeatureUnit>, terminals: MutableList<Terminal>,
        ) {
            val sub = bytes.u8(at + 2)
            if (uac == 2) when (sub) {
                0x02 -> if (length >= 17) terminals += Terminal(bytes.u8(at + 3), bytes.u16(at + 4), input = true, clockId = bytes.u8(at + 7), sourceId = 0)
                0x03 -> if (length >= 12) terminals += Terminal(bytes.u8(at + 3), bytes.u16(at + 4), input = false, clockId = bytes.u8(at + 8), sourceId = bytes.u8(at + 7))
                0x06 -> if (length >= 10) {
                    // bmaControls: four bytes per channel, master first; mute bits 0–1, volume bits 2–3.
                    val channels = (length - 6) / 4
                    val master = bytes.u32(at + 5)
                    val anyChannel = (1 until channels).any { (bytes.u32(at + 5 + it * 4) shr 2) and 0x03 != 0L }
                    features += FeatureUnit(bytes.u8(at + 3), bytes.u8(at + 4), master and 0x03 != 0L, (master shr 2) and 0x03 != 0L, anyChannel)
                }
                0x0A -> if (length >= 8) clocks += ClockSource(bytes.u8(at + 3), bytes.u8(at + 4), bytes.u8(at + 5))
                0x0B -> if (length >= 5) {
                    val count = bytes.u8(at + 4)
                    selectors += ClockSelector(bytes.u8(at + 3), (0 until count).filter { at + 5 + it < at + length }.map { bytes.u8(at + 5 + it) })
                }
            } else when (sub) {
                0x02 -> if (length >= 12) terminals += Terminal(bytes.u8(at + 3), bytes.u16(at + 4), input = true, clockId = 0, sourceId = 0)
                0x03 -> if (length >= 9) terminals += Terminal(bytes.u8(at + 3), bytes.u16(at + 4), input = false, clockId = 0, sourceId = bytes.u8(at + 7))
                0x06 -> if (length >= 7) {
                    // bmaControls of bControlSize bytes, master first; mute bit 0, volume bit 1.
                    val size = bytes.u8(at + 5).coerceAtLeast(1)
                    val channels = (length - 7) / size
                    fun controls(ch: Int): Int = if (at + 6 + ch * size < at + length) bytes.u8(at + 6 + ch * size) else 0
                    val anyChannel = (1 until channels).any { controls(it) and 0x02 != 0 }
                    features += FeatureUnit(bytes.u8(at + 3), bytes.u8(at + 4), controls(0) and 0x01 != 0, controls(0) and 0x02 != 0, anyChannel)
                }
            }
        }
    }
}

/** UAC2 RANGE replies (layout 3 for the clock's rates, layout 2 for the volume), little-endian. */
object UacRanges {
    /** A clock's rates: each sub-range MIN..MAX in steps of RES (a list of single rates has MIN = MAX). */
    fun rates(reply: ByteArray): List<Int> {
        val b = Bytes(reply)
        if (reply.size < 2) return emptyList()
        val count = b.u16(0)
        val out = mutableListOf<Int>()
        for (i in 0 until count) {
            val at = 2 + i * 12
            if (at + 12 > reply.size) break
            val min = b.u32(at).toInt()
            val max = b.u32(at + 4).toInt()
            val res = b.u32(at + 8).toInt()
            if (min == max || res <= 0) out += min
            else {
                // A continuous range: list the usual audio rates inside it, not every step.
                out += COMMON.filter { it in min..max && (it - min) % res == 0 }.ifEmpty { listOf(min, max) }
            }
        }
        return out.distinct().sorted()
    }

    /** A volume control's range in 1/256 dB: (min, max, step) of the first sub-range. */
    fun volume(reply: ByteArray): Triple<Int, Int, Int>? {
        val b = Bytes(reply)
        if (reply.size < 8 || b.u16(0) == 0) return null
        return Triple(b.s16(2), b.s16(4), b.s16(6))
    }

    /** UAC2's bLength of a RANGE reply with `count` sub-ranges of `width`-byte numbers. */
    fun replyLength(count: Int, width: Int): Int = 2 + count * 3 * width

    val COMMON = listOf(
        8000, 11025, 16000, 22050, 32000, 44100, 48000, 88200, 96000, 176400, 192000,
        352800, 384000, 705600, 768000, 1411200, 1536000,
    )
}

/** What the look-only build reports: the descriptors in words, one line each. */
object UsbAudioReport {
    fun describe(d: UsbAudioDescriptors): List<String> {
        if (!d.isAudio) return listOf("no USB Audio interface")
        val lines = mutableListOf<String>()
        lines += "USB Audio Class ${d.uacVersion}.0 · %04X:%04X · control interface ${d.controlInterface}".format(d.vendorId, d.productId)
        for (c in d.clockSources) lines += "clock ${c.id}: ${c.kind}, rate ${if (c.rateSettable) "settable" else "read-only"}"
        for (s in d.clockSelectors) lines += "clock selector ${s.id}: from ${s.inputs.joinToString(", ")}"
        for (f in d.featureUnits) {
            val what = listOfNotNull("volume".takeIf { f.masterVolume }, "mute".takeIf { f.masterMute }, "channel volumes".takeIf { f.channelVolume })
            lines += "feature unit ${f.id}: ${what.joinToString(", ").ifEmpty { "no volume or mute" }}"
        }
        if (d.playback.isEmpty()) lines += "no playback setting"
        for (s in d.playback) lines += setting(d, s)
        return lines
    }

    fun setting(d: UsbAudioDescriptors, s: StreamingSetting): String {
        val kind = formatName(d.uacVersion, s.formats)
        val rates = when {
            s.rateRange != null -> " · ${s.rateRange.first}–${s.rateRange.last} Hz"
            s.rates.isNotEmpty() -> " · " + s.rates.joinToString(", ")
            else -> d.clockFor(s)?.let { " · clock $it" } ?: ""
        }
        val data = s.dataEndpoint
        val sync = data?.let { " · ${it.sync.name.lowercase()}" } ?: ""
        val feedback = s.feedbackEndpoint?.let { " · feedback 0x%02X".format(it.address) }
            ?: if (data?.usage == EndpointUsage.ImplicitFeedback) " · implicit feedback" else ""
        val packet = data?.let { " · ${it.maxPacketBytes} B every ${1 shl ((it.interval - 1).coerceIn(0, 15))} µframe" } ?: ""
        return "interface ${s.interfaceNumber} alt ${s.alternate}: $kind ${s.bits}-bit in ${s.subslotBytes} bytes, ${s.channels} ch$rates$sync$feedback$packet"
    }

    fun formatName(uac: Int, formats: Long): String = if (uac == 2) {
        val names = listOfNotNull(
            "PCM".takeIf { formats and 0x1L != 0L },
            "PCM8".takeIf { formats and 0x2L != 0L },
            "float".takeIf { formats and 0x4L != 0L },
            "raw (native DSD)".takeIf { formats and 0x80000000L != 0L },
        )
        names.joinToString("+").ifEmpty { "format 0x%X".format(formats) }
    } else when (formats) {
        1L -> "PCM"
        2L -> "PCM8"
        3L -> "float"
        else -> "format 0x%X".format(formats)
    }

    /** The raw bytes as hex, 32 to a line, so a test can be built from the user's own DAC. */
    fun hex(raw: ByteArray): List<String> =
        raw.toList().chunked(32).map { line -> line.joinToString(" ") { "%02X".format(it) } }
}

/** Little-endian reads of unsigned USB fields. */
private class Bytes(private val b: ByteArray) {
    fun u8(at: Int): Int = b[at].toInt() and 0xFF
    fun u16(at: Int): Int = u8(at) or (u8(at + 1) shl 8)
    fun s16(at: Int): Int = u16(at).toShort().toInt()
    fun u24(at: Int): Int = u16(at) or (u8(at + 2) shl 16)
    fun u32(at: Int): Long = (u16(at).toLong()) or (u16(at + 2).toLong() shl 16)
}
