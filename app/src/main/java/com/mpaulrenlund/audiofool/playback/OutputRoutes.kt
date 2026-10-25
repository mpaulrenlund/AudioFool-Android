package com.mpaulrenlund.audiofool.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import com.mpaulrenlund.audiofool.core.format.Display
import com.mpaulrenlund.audiofool.core.playback.BitPerfectRule
import com.mpaulrenlund.audiofool.core.playback.BitPerfectWay
import com.mpaulrenlund.audiofool.core.playback.DopRule
import com.mpaulrenlund.audiofool.core.playback.MixerChoice
import com.mpaulrenlund.audiofool.core.playback.MixerEntry
import com.mpaulrenlund.audiofool.core.playback.OutputMode
import com.mpaulrenlund.audiofool.core.playback.OutputRoute
import com.mpaulrenlund.audiofool.core.playback.SampleKind
import com.mpaulrenlund.audiofool.core.playback.SpeakerlessPlayers
import java.util.concurrent.CopyOnWriteArraySet

/** How the output stage opens the `AudioTrack` for a track (session 4). */
sealed interface OutputPlan {
    /** The normal shared output, as session 3 opened it. */
    data object Shared : OutputPlan

    /**
     * The USB device's mixer has been set to [format] (bit-perfect or rate
     * matched): the `AudioTrack` must use exactly this format to be attached to it.
     */
    class Mixer(val format: AudioFormat, val bitPerfect: Boolean) : OutputPlan

    /** The track's own rate in whole-number samples at full volume, for the system to pass on (the M21). */
    data object SourceRate : OutputPlan
}

/**
 * Where the sound goes and what Bit-Perfect can do there (spec 6.2): follows
 * USB DACs, headphones and Bluetooth coming and going, lists a USB device's
 * mixer entries, and sets the one a track plays through
 * (`setPreferredMixerAttributes`, Android 14+).
 *
 * The output stage calls [prepare], [mayOpenDop], [refuse] and [clearMixer] on
 * its thread; the rest is the main thread's. AudioManager's calls are safe from
 * either.
 */
class OutputRoutes(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    /** The setting (saved in AppSettings); the output stage reads it when it opens an output. */
    @Volatile var bitPerfectOn: Boolean = false

    /** The route media plays to now. */
    @Volatile var route: OutputRoute = OutputRoute.Speaker
        private set

    /** The device media plays to now, or null when Android doesn't say. */
    @Volatile private var device: AudioDeviceInfo? = null

    /** The USB device's mixer entries (Android 14+), and the platform's objects for them. */
    @Volatile private var usbMixers: UsbMixers = UsbMixers.None

    /** DoP rates setting the mixer was refused at, per device (the desktop's "refused rates"). */
    private val refusedRates = CopyOnWriteArraySet<Int>()

    /** The mixer entry set last, for the debug screen; null when none is set. */
    @Volatile var mixerSet: String? = null
        private set

    /** What happened the last time a mixer was asked for, for the debug screen. */
    @Volatile var lastMixerNote: String = ""
        private set

    /** Called on the main thread when the route (or the USB device) changes. */
    var onChange: () -> Unit = {}

    val way: BitPerfectWay get() = BitPerfectRule.way(route, MIXER_API, usbMixers.entries.size)

    /** BP is greyed out and can't be tapped (spec 6.2). */
    val greyed: Boolean get() = way == BitPerfectWay.None

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = devicesChanged()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = devicesChanged()
    }

    /** Android may move media to a new device a moment after saying it came: look now, and again shortly. */
    private fun devicesChanged() {
        refresh()
        main.removeCallbacks(refreshAgain)
        main.postDelayed(refreshAgain, SETTLE_MS)
    }

    private val refreshAgain = Runnable { refresh() }

    fun start() {
        audioManager.registerAudioDeviceCallback(callback, main)
        refresh()
    }

    /** Works out the route again (devices came or went, or an `AudioTrack` was rerouted). Main thread. */
    fun refresh() {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val said = audioManager.getAudioDevicesForAttributes(MEDIA).firstOrNull { it.isSink }
        // Media always leaves the speaker for headphones plugged in; if Android
        // still names the speaker (or nothing), the headphones it is. (Not line
        // outs: some phones list one that is always there.)
        val now = said?.takeUnless { routeOf(it.type) == OutputRoute.Speaker }
            ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES }
            ?: said
            ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        val pretending = pretend
        val newRoute = pretending ?: SpeakerlessPlayers.route(now?.let { routeOf(it.type) } ?: OutputRoute.Speaker, SPEAKER_IS_HEADPHONES)
        val changed = newRoute != route || now?.id != device?.id
        // The old device's mixer goes with it (unplugging clears it anyway).
        if (changed) clearMixer()
        device = now
        route = newRoute
        if (changed) {
            refusedRates.clear()
            usbMixers = when {
                pretending == OutputRoute.Usb -> UsbMixers.pretend()
                newRoute == OutputRoute.Usb && now != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> UsbMixers.read(audioManager, now)
                else -> UsbMixers.None
            }
            onChange()
        }
    }

    /**
     * For the emulator only, which has no USB DAC or headphone jack: treat the
     * output as [OutputRoute.Headphones] (the M21's way) or [OutputRoute.Usb]
     * with a made-up list of bit-perfect mixer entries that is never sent to
     * Android, so the Bit-Perfect paths (and DoP) can be played through. Not
     * saved; the debug screen sets it and says so.
     */
    @Volatile var pretend: OutputRoute? = null
        private set

    fun setPretend(route: OutputRoute?) {
        pretend = route
        refresh()
    }

    // ---- for the output stage's thread ----

    /**
     * Gets the route ready for a track that decodes to [asked] and says how to
     * open the `AudioTrack`. With Bit-Perfect on a USB device this sets the mixer
     * entry [MixerChoice] picks; with nothing to pick, the shared output.
     */
    fun prepare(asked: PcmFormat): OutputPlan {
        if (!bitPerfectOn) {
            clearMixer()
            return OutputPlan.Shared
        }
        return when (way) {
            BitPerfectWay.None -> {
                clearMixer()
                OutputPlan.Shared
            }
            BitPerfectWay.SourceRate -> {
                clearMixer()
                OutputPlan.SourceRate
            }
            BitPerfectWay.UsbMixer -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) prepareMixer(asked) else OutputPlan.Shared
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun prepareMixer(asked: PcmFormat): OutputPlan {
        val mixers = usbMixers
        val usb = device
        val i = MixerChoice.pick(mixers.entries, asked.sampleRate, asked.channels, sourceBits(asked), asked.dop)
        if (usb == null || i < 0) {
            clearMixer()
            lastMixerNote = "no mixer entry for ${describe(asked)}: shared"
            return OutputPlan.Shared
        }
        val entry = mixers.entries[i]
        val name = "${Display.sampleRate(entry.sampleRate)} / ${entry.kind?.let { if (it == SampleKind.Float) "float" else "${it.bits}-bit" }}, ${entry.channels} ch, " +
            if (entry.bitPerfect) "bit-perfect" else "default"
        val made = mixers.platform[i]
        if (made is AudioFormat) {
            // Pretending (the emulator): nothing is asked of Android.
            lastMixerNote = "pretend: $name"
            return OutputPlan.Mixer(made, entry.bitPerfect)
        }
        val attributes = made as AudioMixerAttributes
        val ok = runCatching { audioManager.setPreferredMixerAttributes(MEDIA, usb, attributes) }.getOrDefault(false)
        if (!ok) {
            if (asked.dop) refusedRates += asked.sampleRate
            clearMixer()
            lastMixerNote = "refused: $name"
            return OutputPlan.Shared
        }
        mixerSet = name
        lastMixerNote = "set: $name"
        return OutputPlan.Mixer(attributes.format, entry.bitPerfect)
    }

    /** The `AudioTrack` wouldn't open in the entry [prepare] set: back to shared, and the debug screen says so. */
    fun mixerTrackFailed() {
        lastMixerNote = "track not accepted (${lastMixerNote}): shared"
        clearMixer()
    }

    /** Back to the normal mixer on the USB device (Bit-Perfect off, nothing playing, another route). */
    fun clearMixer() {
        if (mixerSet == null) return
        val usb = device
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && usb != null) runCatching { audioManager.clearPreferredMixerAttributes(MEDIA, usb) }
        mixerSet = null
    }

    /** Whether a DSD file at [dopRate] may open as DoP here ([DopRule], from the desktop). */
    fun mayOpenDop(dopRate: Int): Boolean {
        val exclusive = bitPerfectOn && way == BitPerfectWay.UsbMixer
        return DopRule.mayOpen(
            if (exclusive) OutputMode.Exclusive else OutputMode.Shared,
            dopRate,
            MixerChoice.dopRates(usbMixers.entries, 2),
            refusedRates,
        )
    }

    /** DoP at [dopRate] didn't get a bit-perfect output: don't try it again on this device. */
    fun refuse(dopRate: Int) {
        refusedRates += dopRate
    }

    // ---- what the readout and the debug screen show ----

    /** The rate the system's media output runs at now (what the M21's check compares with the track's). */
    fun systemOutputRate(): Int = runCatching { AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC) }.getOrDefault(0)

    /** Every output Android lists now, for the debug screen: which one the route was taken from. */
    fun outputsSummary(): String =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).joinToString(", ") { "${typeName(it.type)} \"${it.productName}\"" }.ifEmpty { "none" }

    /** The USB device's mixer entries, grouped, for the debug screen. */
    fun mixerSummary(): String = if (route != OutputRoute.Usb) "–" else if (!MIXER_API) "Android 13: no mixer API" else MixerChoice.summary(usbMixers.entries)

    /** The device media plays to: its kind, name and the rates and encodings it lists. */
    fun deviceSummary(): String {
        val d = device ?: return "–"
        val rates = d.sampleRates.sorted().joinToString(", ") { Display.sampleRate(it).removeSuffix(" kHz") }.let { if (it.isEmpty()) "any" else "$it kHz" }
        val encodings = d.encodings.toSet().sorted().joinToString(", ") { encodingName(it) }.ifEmpty { "any" }
        val pretending = pretend?.let { " · pretending ${it.label} (debug)" } ?: ""
        val speakerless = if (SPEAKER_IS_HEADPHONES) " · ${Build.MANUFACTURER} ${Build.MODEL}: its speaker is the headphone out" else ""
        return "${typeName(d.type)} \"${d.productName}\" · rates: $rates · formats: $encodings$pretending$speakerless"
    }

    /**
     * Whether Android says it would play [format] untouched (direct), for the
     * debug screen: on the M21 a hint at whether FiiO's bypass is in the path.
     */
    fun directSupport(format: PcmFormat): String {
        val support = runCatching {
            val f = AudioFormat.Builder().setSampleRate(format.sampleRate).setEncoding(format.encoding).setChannelMask(maskOf(format.channels)).build()
            AudioManager.getDirectPlaybackSupport(f, MEDIA)
        }.getOrDefault(-1)
        if (support < 0) return "unknown"
        if (support == AudioManager.DIRECT_PLAYBACK_NOT_SUPPORTED) return "not direct"
        return listOfNotNull(
            "offload".takeIf { support and AudioManager.DIRECT_PLAYBACK_OFFLOAD_SUPPORTED != 0 },
            "bitstream".takeIf { support and AudioManager.DIRECT_PLAYBACK_BITSTREAM_SUPPORTED != 0 },
        ).joinToString(" + ").ifEmpty { "direct ($support)" }
    }

    /** What the USB mixer reads back as set for media, for the debug screen. */
    fun preferredMixer(): String {
        val usb = device ?: return "–"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || route != OutputRoute.Usb) return "–"
        val set = runCatching { audioManager.getPreferredMixerAttributes(MEDIA, usb) }.getOrNull() ?: return "none"
        val f = set.format
        return "${Display.sampleRate(f.sampleRate)} / ${encodingName(f.encoding)}, ${f.channelCount} ch, " +
            if (set.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT) "bit-perfect" else "default"
    }

    /** A USB device's mixer entries, in core's terms and the platform's (same order). */
    private class UsbMixers(val entries: List<MixerEntry>, val platform: List<Any>) {
        companion object {
            val None = UsbMixers(emptyList(), emptyList())

            /** [pretend]'s USB DAC: bit-perfect at the usual rates in 16, 24 and 32-bit stereo, plain `AudioFormat`s. */
            fun pretend(): UsbMixers {
                val rates = listOf(44100, 48000, 88200, 96000, 176400, 192000, 352800, 384000)
                val kinds = listOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_24BIT_PACKED, AudioFormat.ENCODING_PCM_32BIT)
                // Android's own limit on rates (192 kHz on the emulator) leaves out the ones above it.
                val formats = rates.flatMap { rate ->
                    kinds.mapNotNull {
                        runCatching { AudioFormat.Builder().setSampleRate(rate).setEncoding(it).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build() }.getOrNull()
                    }
                }
                return UsbMixers(formats.map { MixerEntry(it.sampleRate, 2, kindOf(it.encoding), bitPerfect = true) }, formats)
            }

            @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            fun read(audioManager: AudioManager, device: AudioDeviceInfo): UsbMixers {
                val list = runCatching { audioManager.getSupportedMixerAttributes(device) }.getOrDefault(emptyList())
                return UsbMixers(
                    list.map { m ->
                        MixerEntry(
                            sampleRate = m.format.sampleRate,
                            channels = m.format.channelCount,
                            kind = kindOf(m.format.encoding),
                            bitPerfect = m.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT,
                        )
                    },
                    list,
                )
            }
        }
    }

    companion object {
        /** `setPreferredMixerAttributes` and the bit-perfect mixer: Android 14 and later (the S24; not the M21). */
        val MIXER_API: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

        /** This player has no speaker: what Android calls its speaker is the headphone out (the M21). */
        val SPEAKER_IS_HEADPHONES: Boolean = SpeakerlessPlayers.speakerIsHeadphones(Build.MANUFACTURER ?: "", Build.BRAND ?: "")

        /** How long after a device comes or goes the route is looked at again. */
        private const val SETTLE_MS = 1_000L

        /** Media: the only usage mixer attributes apply to, and what every `AudioTrack` here plays as. */
        val MEDIA: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        fun routeOf(type: Int): OutputRoute = when (type) {
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> OutputRoute.Usb
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL, AudioDeviceInfo.TYPE_AUX_LINE -> OutputRoute.Headphones
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_BLE_BROADCAST, AudioDeviceInfo.TYPE_HEARING_AID -> OutputRoute.Bluetooth
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> OutputRoute.Speaker
            else -> OutputRoute.Other
        }

        fun kindOf(encoding: Int): SampleKind? = when (encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> SampleKind.Int16
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> SampleKind.Int24
            AudioFormat.ENCODING_PCM_32BIT -> SampleKind.Int32
            AudioFormat.ENCODING_PCM_FLOAT -> SampleKind.Float
            else -> null
        }

        /** The bit depth [MixerChoice] goes by: the track's own, or 0 for a float decoder. */
        fun sourceBits(format: PcmFormat): Int = when (format.encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> 16
            AudioFormat.ENCODING_PCM_24BIT_PACKED, AudioFormat.ENCODING_PCM_32BIT -> 24
            else -> 0
        }

        fun maskOf(channels: Int): Int = when (channels) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            4 -> AudioFormat.CHANNEL_OUT_QUAD
            6 -> AudioFormat.CHANNEL_OUT_5POINT1
            8 -> AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
            else -> AudioFormat.CHANNEL_INVALID
        }

        fun describe(f: PcmFormat) = "${Display.sampleRate(f.sampleRate)} / ${f.depthName}, ${f.channels} ch" + if (f.dop) " (DoP)" else ""

        fun encodingName(encoding: Int): String = when (encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> "16-bit"
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> "24-bit"
            AudioFormat.ENCODING_PCM_32BIT -> "32-bit"
            AudioFormat.ENCODING_PCM_FLOAT -> "float"
            AudioFormat.ENCODING_PCM_8BIT -> "8-bit"
            AudioFormat.ENCODING_DSD -> "DSD"
            else -> "#$encoding"
        }

        fun typeName(type: Int): String = when (type) {
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB device"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB accessory"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired headset"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired headphones"
            AudioDeviceInfo.TYPE_LINE_ANALOG -> "line out"
            AudioDeviceInfo.TYPE_LINE_DIGITAL -> "digital line out"
            AudioDeviceInfo.TYPE_AUX_LINE -> "aux line"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE headset"
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth LE speaker"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
            else -> "type $type"
        }
    }
}
