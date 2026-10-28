package com.mpaulrenlund.audiofool.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.mpaulrenlund.audiofool.core.format.Display
import com.mpaulrenlund.audiofool.core.usbaudio.FeatureUnit
import com.mpaulrenlund.audiofool.core.usbaudio.StreamingSetting
import com.mpaulrenlund.audiofool.core.usbaudio.UacRanges
import com.mpaulrenlund.audiofool.core.usbaudio.UsbAudioDescriptors
import com.mpaulrenlund.audiofool.core.usbaudio.UsbFormatChoice
import com.mpaulrenlund.audiofool.core.usbaudio.UsbVolume
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * True Bit-Perfect on a USB DAC through the app's own driver (0.4.0, design/usb-bit-perfect.md):
 * where Android offers no bit-perfect mixer (the S24 has none, and no 44.1 kHz), Bit-Perfect
 * takes the DAC from Android and plays to it directly, at each track's own rate and in a slot
 * that holds its samples exactly.
 *
 * - **Taking the DAC**: Android's USB permission (asked once; the manifest's attach filter lets
 *   the user say "always"), then its interfaces are claimed from Android's own driver. Android
 *   goes on listing the DAC as an output meanwhile (0.3.0 on the S24), and other sounds have
 *   nowhere to go.
 * - **Volume**: the DAC's own hardware volume ([UsbVolume]), started from Android's media
 *   volume, moved by the volume keys through the media session. The samples are never touched.
 * - **Giving it back** (Bit-Perfect off, the player stopping, a minute paused): released,
 *   reattached to Android's driver and reset, which Android takes as a replug and sets the DAC
 *   up afresh (0.3.1 on the S24: reattaching alone left it silent until a replug). The DAC's
 *   volume is put back as it was first.
 * - **Unplugged**: the stream reports the DAC gone and the output stage pauses.
 *
 * The output stage calls [settingFor], [open] and [giveBack] on its thread; the volume and
 * the permission are the main thread's. Everything that touches the DAC is synchronised.
 */
class UsbDriver(private val context: Context) {
    private val usb = context.getSystemService(UsbManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    /** Called on the main thread when the DAC is taken, given back, plugged in or out (what the screens and the volume keys show). */
    var onChange: () -> Unit = {}

    /** Called on the main thread when Android allows the app the DAC: Bit-Perfect can now go through the driver. */
    var onAllowed: () -> Unit = {}

    /** The DAC while the app holds it. */
    private class Held(
        val device: UsbDevice,
        val connection: UsbDeviceConnection,
        val stream: UsbStream,
        val descriptors: UsbAudioDescriptors,
        val interfaces: IntArray,
        /** The rates each clock offers (UAC2), by clock id. */
        val clockRates: Map<Int, List<Int>>,
        val volume: Volume?,
    ) {
        /** What is streaming now, for the debug screen. */
        var streaming: String? = null
    }

    /** The DAC's volume control: its unit, the channels it is set on, its range, and how it was when taken. */
    private class Volume(val unit: FeatureUnit, val channels: List<Int>, val range: UsbVolume, val original: Int, var step: Int)

    @Volatile private var held: Held? = null

    /** The device nothing more is tried on (not a playable DAC, or it wouldn't be claimed), until it is plugged in again. */
    @Volatile private var refused: String? = null
    @Volatile private var asked: String? = null

    /** What happened last, for the debug screen; the last few are kept with their times ([status]). */
    @Volatile var note: String = "not used yet"
        private set(value) {
            field = value
            synchronized(history) {
                history.addFirst("${SimpleDateFormat("HH:mm:ss", Locale.ENGLISH).format(Date())} $value")
                while (history.size > HISTORY) history.removeLast()
            }
        }

    /** 0.4.1: one note hid what came before it (the S24's report showed only the last give-back). */
    private val history = ArrayDeque<String>()

    /** Until when the DAC dropping out and coming back is the app's own doing (a give-back's reset). */
    @Volatile private var handingBackUntil = 0L

    val holding: Boolean get() = held != null

    /** The DAC Android lists, if any: the first USB device with an audio interface. */
    fun dacDevice(): UsbDevice? = usb.deviceList.values.firstOrNull { d ->
        (0 until d.interfaceCount).any { d.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_AUDIO }
    }

    /** A DAC is plugged in that this driver may play to (permission aside). */
    val available: Boolean get() = held != null || dacDevice()?.let { it.deviceName != refused } == true

    /** A drop and return of the DAC now is the app's own give-back, not an unplug. */
    fun handingBack(): Boolean = SystemClock.elapsedRealtime() < handingBackUntil

    init {
        val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED).apply { addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED) }
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java) ?: return
                if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) unplugged(device) else plugged(device)
            }
        }, filter, Context.RECEIVER_NOT_EXPORTED)
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                note = if (granted) "allowed" else "not allowed: Bit-Perfect stays rate matched"
                if (granted) onAllowed()
            }
        }, IntentFilter(ACTION_PERMISSION), Context.RECEIVER_NOT_EXPORTED)
    }

    // ---- for the output stage's thread ----

    /**
     * The DAC's setting for a track that decodes to [asked], taking the DAC first if the app
     * doesn't hold it yet; null when it can't play here (no permission yet, a rate or a channel
     * count the DAC doesn't offer, or the DAC won't be taken).
     */
    @Synchronized fun settingFor(asked: PcmFormat): StreamingSetting? {
        val h = held ?: take() ?: return null
        return pick(h, asked)
    }

    /** Whether a DSD file may open as DoP at [dopRate]: the DAC takes 24-bit PCM at that rate. */
    fun mayOpenDop(dopRate: Int): Boolean = settingFor(PcmFormat(dopRate, 2, AudioFormat.ENCODING_PCM_24BIT_PACKED, dop = true)) != null

    /**
     * Starts the DAC on [asked]'s setting: the output, and the format it takes (the samples in
     * the DAC's slot size). Null if it wouldn't start; the DAC stays held.
     */
    @Synchronized fun open(asked: PcmFormat): Pair<PcmOutput, PcmFormat>? {
        val h = held ?: return null
        val s = pick(h, asked) ?: return null
        val d = h.descriptors
        val data = s.dataEndpoint ?: return null
        val feedback = s.feedbackEndpoint
        val rate = asked.sampleRate
        val bytesPerFrame = s.subslotBytes * s.channels

        // Linux's order: UAC2 sets the rate with no bandwidth taken and then the setting; UAC1 the other way round.
        h.stream.setAlternate(s.interfaceNumber, 0)
        if (d.uacVersion == 1) h.stream.setAlternate(s.interfaceNumber, s.alternate)
        val set = setRate(h, s, rate)
        if (d.uacVersion != 1) h.stream.setAlternate(s.interfaceNumber, s.alternate).let { r ->
            if (r != 0) return failed("setting ${s.alternate} refused: ${UsbStream.errorName(r)}")
        }
        if (!set) return failed("the DAC didn't take ${Display.sampleRate(rate)}")

        val dataInterval = if (h.stream.highSpeed) (data.interval - 1).coerceIn(0, 3) else 0
        val r = h.stream.start(
            interfaceNumber = s.interfaceNumber,
            dataEndpoint = data.address,
            maxPacketBytes = data.maxPacketBytes,
            dataInterval = dataInterval,
            feedbackEndpoint = feedback?.address ?: 0,
            feedbackPacketBytes = feedback?.maxPacketBytes ?: 0,
            sampleRate = rate,
            bytesPerFrame = bytesPerFrame,
            slotBytes = s.subslotBytes,
            // About half a second, as the AudioTrack has: room to open the next track meanwhile.
            bufferBytes = bytesPerFrame * rate / 2,
        )
        if (r != 0) return failed("streaming wouldn't start: ${UsbStream.errorName(r)}")

        val encoding = when (s.subslotBytes) {
            2 -> AudioFormat.ENCODING_PCM_16BIT
            3 -> AudioFormat.ENCODING_PCM_24BIT_PACKED
            else -> AudioFormat.ENCODING_PCM_32BIT
        }
        h.streaming = "${Display.sampleRate(rate)}, ${s.bits}-bit in ${s.subslotBytes} bytes (setting ${s.alternate})" + if (asked.dop) ", DoP" else ""
        note = "playing ${h.streaming}"
        Log.i(TAG, note)
        return UsbOutput(h.stream, rate) { stop(h) } to PcmFormat(rate, s.channels, encoding, asked.dop)
    }

    private fun failed(why: String): Pair<PcmOutput, PcmFormat>? {
        note = why
        Log.w(TAG, why)
        return null
    }

    private fun stop(h: Held) {
        summarise(h)
        h.stream.stop()
        h.streaming = null
    }

    /** How the stream went, as it stops (0.4.2): what a report needs to tell a gap's cause. */
    private fun summarise(h: Held) {
        val what = h.streaming ?: return
        val c = h.stream.counts()
        note = "stopped $what: ${c.underruns} underruns · ${c.lateRefills} late refills (longest gap ${c.longestGapMs} ms, ${c.queuedMs} ms in flight, priority ${c.eventNice}) · ${c.errors} errors"
    }

    /**
     * Gives the DAC back to Android, if the app holds it: its volume as it was, released,
     * reattached and reset; then waits (up to [waitMs]) for Android to set it up again, so an
     * output opened next goes to the DAC and not the speaker.
     */
    fun giveBack(why: String, waitMs: Long = GIVE_BACK_WAIT_MS) {
        val h = synchronized(this) { held?.also { held = null } } ?: return
        handingBackUntil = SystemClock.elapsedRealtime() + HANDING_BACK_MS
        summarise(h)
        h.volume?.let { v -> setVolumeValue(h, v, v.original, mute = false) }
        val results = h.stream.giveBack(h.interfaces, reset = true)
        h.stream.close()
        h.connection.close()
        note = "given back ($why): " + results.joinToString(", ") { UsbStream.errorName(it) }
        Log.i(TAG, note)
        main.post { onChange() }
        if (waitMs > 0) waitForAndroid(waitMs)
    }

    /** Android drops the DAC at the reset and sets it up again about a second later (0.3.1 on the S24). */
    private fun waitForAndroid(waitMs: Long) {
        val start = SystemClock.elapsedRealtime()
        var dropped = false
        while (SystemClock.elapsedRealtime() - start < waitMs) {
            val listed = androidListsUsbOutput()
            if (!listed) dropped = true
            if (dropped && listed) return
            SystemClock.sleep(50)
        }
    }

    private fun androidListsUsbOutput(): Boolean = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
        it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
    }

    // ---- taking the DAC ----

    private fun take(): Held? {
        val device = dacDevice() ?: return null
        if (device.deviceName == refused) return null
        if (!usb.hasPermission(device)) {
            askOnce(device)
            return null
        }
        val connection = usb.openDevice(device) ?: return refuse(device, "Android wouldn't open it", null)
        val raw = connection.rawDescriptors ?: return refuse(device, "no descriptors", connection)
        val d = UsbAudioDescriptors.parse(raw)
        if (d.playback.none { UsbFormatChoice.isPcm(d.uacVersion, it.formats) }) return refuse(device, "no PCM setting to play to", connection)
        // A library that won't load must not take the output thread down with it: the mixer plays instead.
        val stream = try {
            UsbStream.open(connection.fileDescriptor)
        } catch (e: LinkageError) {
            return refuse(device, "the driver's library wouldn't load: ${e.message}", connection)
        } ?: return refuse(device, "libusb couldn't open it", connection)
        val interfaces = (listOf(d.controlInterface) + d.playback.map { it.interfaceNumber }).distinct().toIntArray()
        val claimed = stream.claim(interfaces)
        if (claimed != 0) {
            // Android's driver gets back whatever was taken before the refusal.
            stream.giveBack(interfaces, reset = false)
            stream.close()
            return refuse(device, "wouldn't be taken from Android: ${UsbStream.errorName(claimed)}", connection)
        }
        val clockRates = if (d.uacVersion == 2) d.clockSources.associate { c -> c.id to clockRates(connection, d, c.id) } else emptyMap()
        val h = Held(device, connection, stream, d, interfaces, clockRates, readVolume(connection, d))
        held = h
        h.volume?.let { v -> setVolumeValue(h, v, v.range.value(v.step), mute = v.range.muted(v.step)) }
        note = "taken: " + (if (d.uacVersion == 2) "rates " + clockRates.values.flatten().distinct().sorted().joinToString(", ") { Display.sampleRate(it) } else "UAC1") +
            (h.volume?.let { " · volume ${UsbVolume.db(it.range.value(it.step))} (was ${UsbVolume.db(it.original)})" } ?: " · no hardware volume")
        Log.i(TAG, note)
        main.post { onChange() }
        return h
    }

    private fun refuse(device: UsbDevice, why: String, connection: UsbDeviceConnection?): Held? {
        connection?.close()
        refused = device.deviceName
        note = "not used: $why"
        Log.w(TAG, note)
        main.post { onChange() }
        return null
    }

    /** Asks for the DAC once per plug; the answer comes to the permission receiver. */
    private fun askOnce(device: UsbDevice) {
        if (asked == device.deviceName) return
        asked = device.deviceName
        note = "asking for the DAC"
        val intent = Intent(ACTION_PERMISSION).setPackage(context.packageName)
        main.post { usb.requestPermission(device, PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)) }
    }

    private fun pick(h: Held, asked: PcmFormat): StreamingSetting? {
        val d = h.descriptors
        val clock = d.playback.firstNotNullOfOrNull { d.clockFor(it) }
        val rates = clock?.let { h.clockRates[it] } ?: h.clockRates.values.flatten()
        return UsbFormatChoice.pick(d, rates, asked.sampleRate, asked.channels, OutputRoutes.sourceBits(asked), asked.dop)
    }

    private fun plugged(device: UsbDevice) {
        if (refused == device.deviceName) refused = null
        if (asked == device.deviceName) asked = null
        main.post { onChange() }
    }

    /** The DAC was pulled out: what the app held is let go (the stream already reports it gone). */
    private fun unplugged(device: UsbDevice) {
        refused = null
        asked = null
        val h = synchronized(this) { held?.takeIf { it.device.deviceName == device.deviceName }?.also { held = null } } ?: return
        summarise(h)
        h.stream.close()
        h.connection.close()
        note = "unplugged while held"
        Log.i(TAG, note)
        onChange()
    }

    // ---- the DAC's clock ----

    private fun setRate(h: Held, s: StreamingSetting, rate: Int): Boolean {
        val c = h.connection
        val d = h.descriptors
        return if (d.uacVersion == 2) {
            val clock = d.clockFor(s) ?: return false
            val index = (clock shl 8) or d.controlInterface
            c.controlTransfer(CLASS_OUT, CUR, CS_SAM_FREQ shl 8, index, le(rate, 4), 4, TIMEOUT_MS)
            get(c, CLASS_IN, CUR, CS_SAM_FREQ shl 8, index, 4)?.let { le(it) } == rate
        } else {
            val endpoint = s.dataEndpoint?.address ?: return false
            c.controlTransfer(ENDPOINT_OUT, CUR, SAMPLING_FREQ shl 8, endpoint, le(rate, 3), 3, TIMEOUT_MS)
            // Some UAC1 DACs can't say their rate; one that answers must answer this one.
            get(c, ENDPOINT_IN, UAC1_GET_CUR, SAMPLING_FREQ shl 8, endpoint, 3)?.let { le(it) == rate } ?: true
        }
    }

    private fun clockRates(c: UsbDeviceConnection, d: UsbAudioDescriptors, clock: Int): List<Int> =
        rangeRequest(c, CS_SAM_FREQ shl 8, (clock shl 8) or d.controlInterface, width = 4)?.let { UacRanges.rates(it) } ?: emptyList()

    // ---- the DAC's volume ----

    /** The volume keys' steps while the app holds a DAC with a volume control, or null. */
    val volumeSteps: Int? get() = held?.volume?.let { UsbVolume.STEPS }

    val volumeStep: Int get() = held?.volume?.step ?: 0

    /** A volume key, or the volume slider (main thread). */
    @Synchronized fun setVolumeStep(step: Int) {
        val h = held ?: return
        val v = h.volume ?: return
        v.step = step.coerceIn(0, UsbVolume.STEPS)
        setVolumeValue(h, v, v.range.value(v.step), v.range.muted(v.step))
        keyStep = v.step
        mirrorToAndroid(v.step)
    }

    private fun readVolume(c: UsbDeviceConnection, d: UsbAudioDescriptors): Volume? {
        val setting = d.playback.firstOrNull() ?: return null
        val unit = d.volumeUnitFor(setting) ?: return null
        val channels = if (unit.masterVolume) listOf(0) else (1..setting.channels).toList()
        val value = (FU_VOLUME shl 8) or channels.first()
        val index = (unit.id shl 8) or d.controlInterface
        val range = if (d.uacVersion == 2) {
            rangeRequest(c, value, index, width = 2)?.let { UacRanges.volume(it) }
        } else {
            val min = get(c, CLASS_IN, UAC1_GET_MIN, value, index, 2)
            val max = get(c, CLASS_IN, UAC1_GET_MAX, value, index, 2)
            val res = get(c, CLASS_IN, UAC1_GET_RES, value, index, 2)
            if (min != null && max != null && res != null) Triple(le(min).toShort().toInt(), le(max).toShort().toInt(), le(res).toShort().toInt()) else null
        } ?: return null
        val now = get(c, CLASS_IN, if (d.uacVersion == 2) CUR else UAC1_GET_CUR, value, index, 2)?.let { le(it).toShort().toInt() } ?: range.second
        val volume = UsbVolume(range.first, range.second, range.third)
        // 0.4.2: the step the keys last set, unless Android's volume has been moved since
        // (0.4.1 started from Android's every time, and after a hand-back came back louder).
        val android = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val start = keyStep?.takeIf { android == mirroredIndex } ?: UsbVolume.stepFor(android, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
        keyStep = start
        mirroredIndex = android
        return Volume(unit, channels, volume, now, start)
    }

    /** The step the volume keys left the DAC at, kept across hand-backs; and Android's media volume set to match it. */
    @Volatile private var keyStep: Int? = null
    @Volatile private var mirroredIndex: Int? = null

    /**
     * Android's media volume follows the keys while the driver holds the DAC (nothing hears it
     * then), so once the DAC is Android's again it plays about as loud, and Bit-Perfect off
     * doesn't jump. Android's curve isn't the driver's 1 dB steps, so "about".
     */
    private fun mirrorToAndroid(step: Int) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val index = Math.round(step.toDouble() * max / UsbVolume.STEPS).toInt()
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0) }
        // What Android took (it may hold back a loud step for hearing safety).
        mirroredIndex = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    private fun setVolumeValue(h: Held, v: Volume, value: Int, mute: Boolean) {
        val index = (v.unit.id shl 8) or h.descriptors.controlInterface
        for (channel in v.channels) h.connection.controlTransfer(CLASS_OUT, CUR, (FU_VOLUME shl 8) or channel, index, le(value, 2), 2, TIMEOUT_MS)
        if (v.unit.masterMute) h.connection.controlTransfer(CLASS_OUT, CUR, FU_MUTE shl 8, index, byteArrayOf(if (mute) 1 else 0), 1, TIMEOUT_MS)
    }

    // ---- class requests ----

    private fun rangeRequest(c: UsbDeviceConnection, value: Int, index: Int, width: Int): ByteArray? {
        val head = ByteArray(2)
        if (c.controlTransfer(CLASS_IN, RANGE, value, index, head, 2, TIMEOUT_MS) < 2) return null
        val count = le(head)
        if (count <= 0) return null
        val full = ByteArray(UacRanges.replyLength(count, width))
        return if (c.controlTransfer(CLASS_IN, RANGE, value, index, full, full.size, TIMEOUT_MS) < full.size) null else full
    }

    private fun get(c: UsbDeviceConnection, type: Int, request: Int, value: Int, index: Int, size: Int): ByteArray? {
        val buffer = ByteArray(size)
        return if (c.controlTransfer(type, request, value, index, buffer, size, TIMEOUT_MS) == size) buffer else null
    }

    // ---- the debug screen ----

    /** What the driver is doing, a line each: its state now, then what happened lately, newest first. */
    fun status(): List<String> {
        val h = held
        val lines = mutableListOf<String>()
        val lately = synchronized(history) { history.toList() }.ifEmpty { listOf(note) }
        if (h == null) {
            lines += dacDevice()?.let { if (usb.hasPermission(it)) "not held · allowed" else "not held · not allowed yet" } ?: "no DAC plugged in"
            return lines + lately
        }
        lines += "held: ${h.device.productName?.trim() ?: h.device.deviceName} · interfaces ${h.interfaces.joinToString(", ")}"
        lines += "streaming: ${h.streaming ?: "no"}"
        val c = h.stream.counts()
        if (h.streaming != null) {
            lines += "clock: %.4f frames a %s (nominal %.4f) · %d feedback values · %d underruns · %d errors".format(
                java.util.Locale.ENGLISH, c.rate / 65536.0, if (h.stream.highSpeed) "microframe" else "frame", c.nominal / 65536.0,
                c.feedbackValues, c.underruns, c.errors,
            ) + " · refills: longest gap ${c.longestGapMs} ms, ${c.lateRefills} late (${c.queuedMs} ms in flight) · priority ${c.eventNice}" + if (c.lost) " · lost" else ""
        }
        lines += h.volume?.let { "volume: step ${it.step} of ${UsbVolume.STEPS}, ${UsbVolume.db(it.range.value(it.step))}" + if (it.range.muted(it.step)) " (muted)" else "" } ?: "volume: the DAC has none"
        return lines + lately
    }

    companion object {
        private const val TAG = "AudioFoolUsb"
        private const val ACTION_PERMISSION = "com.mpaulrenlund.audiofool.USB_DRIVER_PERMISSION"
        private const val TIMEOUT_MS = 1000

        /** Notes kept for the debug screen and the report. */
        private const val HISTORY = 12

        /** How long a give-back waits for Android to set the DAC up again (0.3.1: about 1.3 s). */
        const val GIVE_BACK_WAIT_MS = 4_000L

        /** How long the DAC dropping out and returning is taken as the app's own reset. */
        private const val HANDING_BACK_MS = 8_000L

        // Class requests to an interface (UAC2's CUR and RANGE; UAC1's GET_*), and to an endpoint (UAC1's rate).
        private const val CLASS_IN = 0xA1
        private const val CLASS_OUT = 0x21
        private const val ENDPOINT_IN = 0xA2
        private const val ENDPOINT_OUT = 0x22
        private const val CUR = 0x01
        private const val RANGE = 0x02
        private const val UAC1_GET_CUR = 0x81
        private const val UAC1_GET_MIN = 0x82
        private const val UAC1_GET_MAX = 0x83
        private const val UAC1_GET_RES = 0x84
        private const val CS_SAM_FREQ = 0x01
        private const val SAMPLING_FREQ = 0x01
        private const val FU_MUTE = 0x01
        private const val FU_VOLUME = 0x02

        private fun le(b: ByteArray): Int = b.foldIndexed(0) { i, acc, v -> acc or ((v.toInt() and 0xFF) shl (8 * i)) }
        private fun le(value: Int, size: Int) = ByteArray(size) { (value shr (8 * it)).toByte() }
    }
}
