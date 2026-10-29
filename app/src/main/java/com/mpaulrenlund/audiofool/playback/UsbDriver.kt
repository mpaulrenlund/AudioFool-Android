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
 * - **Volume**: the DAC's own hardware volume ([UsbVolume]), moved by the volume keys through
 *   the media session. The samples are never touched. The level comes from the user only
 *   (0.4.4): the quietest step heard at the first take, then where the keys left it; the
 *   DAC's channel controls are opened so the master alone says how loud. (Samsung turns a USB
 *   DAC down in the DAC itself and says its media volume takes 0 dB off: 0.4.2 and 0.4.3,
 *   which went by Android's volume, came in at full volume twice.)
 * - **Giving it back** (Bit-Perfect off, the player stopping, a minute paused): released,
 *   reattached to Android's driver and reset, which Android takes as a replug and sets the DAC
 *   up afresh (0.3.1 on the S24: reattaching alone left it silent until a replug). One left
 *   held when Android ended the app is given back at the next start ([repairIfLeftHeld], 0.4.7).
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
    private class Volume(
        val unit: FeatureUnit,
        /** Where the level goes: the master (0), or each channel if there is no master. */
        val levelOn: List<Int>,
        /** Channel controls held open (at 0 dB, or their top) while the master carries the level. */
        val open: List<Pair<Int, Int>>,
        val range: UsbVolume,
        /** What the DAC's controls were at when taken, for the notes. */
        val found: String,
        var step: Int,
    )

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

        // Muted while the DAC changes rate and stream, and until it has settled (0.4.6: it popped).
        hush(h)
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
            // A second (0.4.9; half a second before): room to open the next track meanwhile, and for
            // the output thread to fall behind while Android switches apps. A cut flushes it.
            bufferBytes = bytesPerFrame * rate,
        )
        if (r != 0) return failed("streaming wouldn't start: ${UsbStream.errorName(r)}")
        // The stream starts paused, so silence goes out while the DAC locks to it; then the level.
        SystemClock.sleep(SETTLE_MS)
        unhush(h)

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

    /** "0 failed packets", or how many and the last one's status (0.4.5). */
    private fun packetErrors(c: UsbStream.Counts): String =
        if (c.packetErrors == 0) "0 failed packets" else "${c.packetErrors} failed packets (last: ${transferStatus(c.lastPacketStatus)})"

    /** libusb's transfer statuses (`libusb_transfer_status`). */
    private fun transferStatus(status: Int): String = when (status) {
        0 -> "completed"
        1 -> "error"
        2 -> "timed out"
        3 -> "cancelled"
        4 -> "stall"
        5 -> "no device"
        6 -> "overflow"
        else -> "status $status"
    }

    /**
     * What the DAC says its volume controls and mute are, just after they were set (0.4.5: a
     * stream played a minute in silence; this shows whether the DAC took the volume).
     */
    private fun readBack(h: Held): String {
        val v = h.volume ?: return "no volume"
        val d = h.descriptors
        val index = (v.unit.id shl 8) or d.controlInterface
        val levels = (v.levelOn + v.open.map { it.first }).distinct().sorted().mapNotNull { ch ->
            currentVolume(h.connection, d, ch, index)?.let { (if (ch == 0) "master " else "channel $ch ") + UsbVolume.db(it) }
        }
        val mute = if (v.unit.masterMute) get(h.connection, CLASS_IN, if (d.uacVersion == 2) CUR else UAC1_GET_CUR, FU_MUTE shl 8, index, 1)
            ?.let { if (it[0].toInt() != 0) "muted" else "not muted" } ?: "mute not read" else "no mute"
        return (levels + mute).joinToString(", ")
    }

    /** How the stream went, as it stops (0.4.2): what a report needs to tell a gap's cause. */
    private fun summarise(h: Held) {
        val what = h.streaming ?: return
        val c = h.stream.counts()
        note = "stopped $what: ${c.underruns} underruns · ${c.lateRefills} late refills (longest gap ${c.longestGapMs} ms, ${c.queuedMs} ms in flight, priority ${c.eventNice}; app stopped ${c.longestHeartbeatGapMs} ms, send ${c.longestSubmitMs} ms, samples ${c.longestLockWaitMs} ms) · ${c.errors} errors · " +
            "${packetErrors(c)} · clock %.4f of %.4f frames, %d feedback values".format(java.util.Locale.ENGLISH, c.rate / 65536.0, c.nominal / 65536.0, c.feedbackValues)
    }

    /**
     * Gives the DAC back to Android, if the app holds it: released, reattached and reset; then
     * waits (up to [waitMs]) for Android to set it up again, so an output opened next goes to
     * the DAC and not the speaker.
     *
     * The volume (0.4.5): Samsung never touches the DAC's own controls; it turns a USB DAC down in
     * its own software, by its media volume (0.4.4's report). So the DAC goes back to 0 dB, as
     * Android expects it (0.4.4 left it at the BP level, and Android couldn't make it loud
     * enough), and first Android's media volume comes down to [ANDROID_SAFE_INDEX] if it is
     * higher, so whatever plays next through Android starts quiet (0.4.2 put the DAC back to
     * 0 dB with Android at 9 of 15, and it was far too loud). Bit-Perfect off also pauses (the
     * player's), and the user turns it up and plays on when ready.
     */
    fun giveBack(why: String, waitMs: Long = GIVE_BACK_WAIT_MS) {
        val h = synchronized(this) { held?.also { held = null } } ?: return
        handBack(h, why, waitMs)
    }

    /**
     * At the app's start (0.4.7), on a background thread: a DAC the driver was holding when
     * Android ended the app is given back as [giveBack] does, or it stays silent until a
     * replug. Ending the app lets go of the interfaces but leaves them with no driver at all
     * (Linux doesn't bind Android's again), and Android goes on writing to the sound card it
     * had. So a DAC whose audio interfaces have no driver is one left held: it is taken,
     * turned back to 0 dB and unmuted (it may have been muted or turned down), and reset.
     * Nothing happens without the permission ("Always open AudioFool" keeps it) or on a DAC
     * Android's driver has.
     */
    fun repairIfLeftHeld() {
        val h = synchronized(this) { if (held == null) leftHeld()?.also { repairing = true } else null } ?: return
        try {
            handBack(h, "left held when Android ended the app")
        } finally {
            repairing = false
        }
    }

    /** A DAC left held is being given back: not to be taken meanwhile. */
    @Volatile private var repairing = false

    private fun leftHeld(): Held? {
        val device = dacDevice() ?: return null
        if (!usb.hasPermission(device)) return null
        val connection = usb.openDevice(device) ?: return null
        val d = connection.rawDescriptors?.let { UsbAudioDescriptors.parse(it) }
        // A library that won't load is no reason to stop the app's start.
        val stream = if (d == null || d.playback.isEmpty()) null else try {
            UsbStream.open(connection.fileDescriptor)
        } catch (e: LinkageError) {
            null
        }
        if (d == null || stream == null) {
            connection.close()
            return null
        }
        val interfaces = (listOf(d.controlInterface) + d.playback.map { it.interfaceNumber }).distinct().toIntArray()
        val free = interfaces.filter { stream.kernelDriverActive(it) == 0 }
        if (free.isEmpty()) {
            stream.close()
            connection.close()
            return null
        }
        // Claimed, so its volume and mute can be set; a reset alone if it won't be.
        val claimed = stream.claim(interfaces) == 0
        val volume = if (claimed) readVolume(connection, d) else null
        note = "found left held (interface ${free.joinToString(", ")} with no driver" + (if (claimed) "" else ", wouldn't be claimed") +
            "; the DAC at ${volume?.found ?: "not read"})"
        Log.i(TAG, note)
        return Held(device, connection, stream, d, interfaces, emptyMap(), volume)
    }

    private fun handBack(h: Held, why: String, waitMs: Long = GIVE_BACK_WAIT_MS) {
        handingBackUntil = SystemClock.elapsedRealtime() + HANDING_BACK_MS
        summarise(h)
        val lowered = lowerAndroidVolume()
        h.volume?.let { v -> setVolumeValue(h, v, v.range.atZeroDb(), mute = false) }
        val results = h.stream.giveBack(h.interfaces, reset = true)
        h.stream.close()
        h.connection.close()
        note = "given back ($why): " + results.joinToString(", ") { UsbStream.errorName(it) } +
            (h.volume?.let { " · the DAC back at ${UsbVolume.db(it.range.atZeroDb())}" } ?: "") + " · $lowered"
        Log.i(TAG, note)
        main.post { onChange() }
        if (waitMs > 0) {
            val back = waitForAndroid(waitMs)
            // Android may put its volume back for the DAC it sets up again: down again if so.
            val again = lowerAndroidVolume()
            note = if (back >= 0) "Android has the DAC again after $back ms: $again"
            else "Android hadn't set the DAC up again after ${waitMs / 1000} s (unplug it and plug it in again): $again"
        }
    }

    /** Android's media volume down to [ANDROID_SAFE_INDEX] if it is higher; what it was and is, for the notes. */
    private fun lowerAndroidVolume(): String {
        val before = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (before > ANDROID_SAFE_INDEX) runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, ANDROID_SAFE_INDEX, 0) }
        val after = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (after != before) "Android's media volume lowered from $before to $after of $max" else "Android's media volume $after of $max"
    }

    /**
     * Android drops the DAC at the reset and sets it up again about a second later (0.3.1 on
     * the S24); the ms that took, or -1 if it hadn't in [waitMs] (0.4.3: once it hadn't in 4 s,
     * and the music went to the speaker).
     */
    private fun waitForAndroid(waitMs: Long): Long {
        val start = SystemClock.elapsedRealtime()
        var dropped = false
        while (SystemClock.elapsedRealtime() - start < waitMs) {
            val listed = androidListsUsbOutput()
            if (!listed) dropped = true
            if (dropped && listed) return SystemClock.elapsedRealtime() - start
            SystemClock.sleep(50)
        }
        return -1
    }

    private fun androidListsUsbOutput(): Boolean = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
        it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
    }

    // ---- taking the DAC ----

    private fun take(): Held? {
        val device = dacDevice() ?: return null
        if (device.deviceName == refused || repairing) return null
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
        // First of all: the DAC comes from Android at 0 dB, and popped loudly as it changed over (0.4.5).
        muteAtOnce(connection, d)
        val clockRates = if (d.uacVersion == 2) d.clockSources.associate { c -> c.id to clockRates(connection, d, c.id) } else emptyMap()
        val h = Held(device, connection, stream, d, interfaces, clockRates, readVolume(connection, d))
        held = h
        // The level is set, but stays muted until the stream has settled ([open]).
        hush(h)
        note = "taken: " + (if (d.uacVersion == 2) "rates " + clockRates.values.flatten().distinct().sorted().joinToString(", ") { Display.sampleRate(it) } else "UAC1") +
            (h.volume?.let { " · volume ${UsbVolume.db(it.range.value(it.step))}, step ${it.step} (the DAC was at ${it.found}; ${androidVolume()}; reads back ${readBack(h)})" } ?: " · no hardware volume")
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
        lastStep = v.step
        setVolumeValue(h, v, v.range.value(v.step), v.range.muted(v.step))
    }

    /** The step the keys last left the DAC at, while the app runs; null until the first take ([UsbVolume.startStep]). */
    @Volatile private var lastStep: Int? = null

    /**
     * The DAC's volume controls, and where to start (0.4.4): the level goes on the master
     * control (on each channel's, if there is no master); the channels' own controls are opened
     * to 0 dB, so the master alone says how loud. Samsung turns a USB DAC down in controls like
     * those, which 0.4.3 left alone: what the driver set was then not what was heard.
     */
    private fun readVolume(c: UsbDeviceConnection, d: UsbAudioDescriptors): Volume? {
        val setting = d.playback.firstOrNull() ?: return null
        val unit = d.volumeUnitFor(setting) ?: return null
        val index = (unit.id shl 8) or d.controlInterface
        val channels = (1..setting.channels).toList()
        val levelOn = if (unit.masterVolume) listOf(0) else channels
        val range = volumeRange(c, d, levelOn.first(), index) ?: return null
        val open = if (unit.masterVolume && unit.channelVolume) {
            channels.mapNotNull { ch -> volumeRange(c, d, ch, index)?.let { (lo, hi, _) -> ch to (if (0 in lo..hi) 0 else hi) } }
        } else {
            emptyList()
        }
        val found = (listOf(0) + channels).mapNotNull { ch ->
            currentVolume(c, d, ch, index)?.let { (if (ch == 0) "master " else "channel $ch ") + UsbVolume.db(it) }
        }.joinToString(", ").ifEmpty { "not read" }
        val start = UsbVolume.startStep(lastStep)
        lastStep = start
        return Volume(unit, levelOn, open, UsbVolume(range.first, range.second, range.third), found, start)
    }

    /** A volume control's range (min, max, resolution, in 1/256 dB): UAC2's RANGE, UAC1's GET_MIN, MAX and RES. */
    private fun volumeRange(c: UsbDeviceConnection, d: UsbAudioDescriptors, channel: Int, index: Int): Triple<Int, Int, Int>? {
        val value = (FU_VOLUME shl 8) or channel
        return if (d.uacVersion == 2) {
            rangeRequest(c, value, index, width = 2)?.let { UacRanges.volume(it) }
        } else {
            val min = get(c, CLASS_IN, UAC1_GET_MIN, value, index, 2)
            val max = get(c, CLASS_IN, UAC1_GET_MAX, value, index, 2)
            val res = get(c, CLASS_IN, UAC1_GET_RES, value, index, 2)
            if (min != null && max != null && res != null) Triple(le(min).toShort().toInt(), le(max).toShort().toInt(), le(res).toShort().toInt()) else null
        }
    }

    private fun currentVolume(c: UsbDeviceConnection, d: UsbAudioDescriptors, channel: Int, index: Int): Int? =
        get(c, CLASS_IN, if (d.uacVersion == 2) CUR else UAC1_GET_CUR, (FU_VOLUME shl 8) or channel, index, 2)?.let { le(it).toShort().toInt() }

    /** "Android's media volume 7 of 15", for the notes. */
    private fun androidVolume(): String =
        "Android's media volume ${audio.getStreamVolume(AudioManager.STREAM_MUSIC)} of ${audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}"

    /**
     * Mutes the DAC straight after it is taken, before its controls are even read (0.4.6): its
     * mute if it has one, else its master volume at the bottom of its range.
     */
    private fun muteAtOnce(c: UsbDeviceConnection, d: UsbAudioDescriptors) {
        val unit = d.playback.firstOrNull()?.let { d.volumeUnitFor(it) } ?: return
        val index = (unit.id shl 8) or d.controlInterface
        if (unit.masterMute) {
            c.controlTransfer(CLASS_OUT, CUR, FU_MUTE shl 8, index, byteArrayOf(1), 1, TIMEOUT_MS)
        } else if (unit.masterVolume) {
            volumeRange(c, d, 0, index)?.let { (lo, _, _) -> c.controlTransfer(CLASS_OUT, CUR, FU_VOLUME shl 8, index, le(lo, 2), 2, TIMEOUT_MS) }
        }
    }

    /** Silent while the stream is set up (the mute, or the bottom of the range if there is none), at the user's level otherwise. */
    private fun hush(h: Held) {
        val v = h.volume ?: return
        if (v.unit.masterMute) setVolumeValue(h, v, v.range.value(v.step), mute = true) else setVolumeValue(h, v, v.range.min, mute = false)
    }

    private fun unhush(h: Held) {
        val v = h.volume ?: return
        setVolumeValue(h, v, v.range.value(v.step), v.range.muted(v.step))
    }

    private fun setVolumeValue(h: Held, v: Volume, value: Int, mute: Boolean) {
        val index = (v.unit.id shl 8) or h.descriptors.controlInterface
        for ((channel, open) in v.open) h.connection.controlTransfer(CLASS_OUT, CUR, (FU_VOLUME shl 8) or channel, index, le(open, 2), 2, TIMEOUT_MS)
        for (channel in v.levelOn) h.connection.controlTransfer(CLASS_OUT, CUR, (FU_VOLUME shl 8) or channel, index, le(value, 2), 2, TIMEOUT_MS)
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
            // 0.4.9: the stutter question in one line; the details are in the next.
            lines += "gaps since this stream started: ${c.lateRefills} from USB refills late (${c.queuedMs} ms sent ahead), ${c.underruns} from samples late"
            // 0.5.0: a 1288 ms gap at priority -19; which of the three it was.
            lines += "longest gap ${c.longestGapMs} ms: the app stopped ${c.longestHeartbeatGapMs} ms, a send to the DAC ${c.longestSubmitMs} ms, waiting for samples ${c.longestLockWaitMs} ms"
            lines += "clock:%.4f frames a %s (nominal %.4f) · %d feedback values · %d underruns · %d errors".format(
                java.util.Locale.ENGLISH, c.rate / 65536.0, if (h.stream.highSpeed) "microframe" else "frame", c.nominal / 65536.0,
                c.feedbackValues, c.underruns, c.errors,
            ) + " · refills: longest gap ${c.longestGapMs} ms, ${c.lateRefills} late (${c.queuedMs} ms in flight) · priority ${c.eventNice} · ${packetErrors(c)}" + if (c.lost) " · lost" else ""
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

        /** How long the DAC stays muted after its stream starts, while it locks to the new rate (0.4.6). */
        private const val SETTLE_MS = 150L

        /** Android's media volume at most, once the DAC is given back (0.4.5): the quietest step that is heard. */
        private const val ANDROID_SAFE_INDEX = 1

        /** How long a give-back waits for Android to set the DAC up again (0.3.1: about 1.3 s; 0.4.3 once not in 4 s). */
        const val GIVE_BACK_WAIT_MS = 8_000L

        /** How long the DAC dropping out and returning is taken as the app's own reset. */
        private const val HANDING_BACK_MS = 12_000L

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
