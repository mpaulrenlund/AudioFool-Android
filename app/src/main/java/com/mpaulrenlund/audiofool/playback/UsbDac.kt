package com.mpaulrenlund.audiofool.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mpaulrenlund.audiofool.core.usbaudio.UacRanges
import com.mpaulrenlund.audiofool.core.usbaudio.UsbAudioDescriptors
import com.mpaulrenlund.audiofool.core.usbaudio.UsbAudioReport
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * The look-only build of true Bit-Perfect on the S24 (0.3.0, design/usb-bit-perfect.md): asks
 * for the USB DAC, reads what it offers, and tries the one step the driver depends on, taking
 * the DAC from Android's own driver and giving it back. Playback never goes through here yet.
 * Statistics > Debug's **USB DAC** panel drives it; the report is saved and shared as text.
 * 0.3.1 tries two ways of giving the DAC back, since 0.3.0's left it silent until a replug.
 */
@Stable
class UsbDac(private val context: Context) {
    private val usb = context.getSystemService(UsbManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)

    /** What the panel shows under "Read the DAC": the descriptors in words, or where it got to. */
    var described: List<String> by mutableStateOf(emptyList())
        private set

    /** What the panel shows under each way of giving it back, once tried. */
    var probed: Map<GiveBack, List<String>> by mutableStateOf(emptyMap())
        private set

    var busy: Boolean by mutableStateOf(false)
        private set

    /** The whole report, ready to share; empty until the DAC has been read. */
    var report: String by mutableStateOf("")
        private set

    private var raw: ByteArray? = null
    private var waiting: ((UsbDevice) -> Unit)? = null

    /** The first USB device with an audio interface, as Android lists it now. */
    fun device(): UsbDevice? = usb.deviceList.values.firstOrNull { d ->
        (0 until d.interfaceCount).any { d.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_AUDIO }
    }

    fun summary(): String = device()?.let { "${name(it)} · %04X:%04X".format(it.vendorId, it.productId) } ?: "none plugged in"

    /** Reads the descriptors: no change to the DAC or to playback. */
    fun read() = withDevice { device ->
        val connection = usb.openDevice(device) ?: return@withDevice done(described = listOf("Android wouldn't open it"))
        val bytes = try { connection.rawDescriptors } finally { connection.close() }
        if (bytes == null) return@withDevice done(described = listOf("no descriptors"))
        raw = bytes
        val d = UsbAudioDescriptors.parse(bytes)
        done(described = UsbAudioReport.describe(d))
    }

    /**
     * The two ways of giving the DAC back that 0.3.1 tries, after 0.3.0's (reattaching Android's
     * driver straight away) left it silent until a replug. Each is a test of its own, so the
     * one that brings the sound back is known.
     */
    enum class GiveBack(val title: String) {
        /** Reattach, then reset the DAC over USB, the nearest an app gets to a replug. */
        Reset("with a USB reset"),

        /**
         * Wait first for Android to close its output to the DAC: if it still has the sound card
         * open when the DAC is taken, the card that comes back gets a new number that Android
         * never learns, which would explain the silence.
         */
        AfterWait("after a wait"),
    }

    /**
     * Takes the DAC from Android, asks its clock for its rates and its volume control for its
     * range, gives it back the [way] asked, and reports what Android and the kernel show before
     * and after. [pause] stops the music first, since the DAC goes away under it. Afterwards the
     * user plays a song to hear whether it worked.
     */
    fun takeAndGiveBack(way: GiveBack, pause: () -> Unit) = withDevice { device ->
        pause()
        val lines = mutableListOf<String>()
        lines += "Android's USB output: ${androidOutputs()}"
        if (way == GiveBack.AfterWait) {
            SystemClock.sleep(WAIT_MS)
            lines += "waited ${WAIT_MS / 1000} s for Android to close its output"
        }
        lines += "sound cards: ${soundCards()}"
        val connection = usb.openDevice(device) ?: return@withDevice done(way, lines + "Android wouldn't open it")
        try {
            val bytes = connection.rawDescriptors ?: return@withDevice done(way, lines + "no descriptors")
            raw = bytes
            val d = UsbAudioDescriptors.parse(bytes)
            if (!d.isAudio) return@withDevice done(way, lines + "no USB Audio interface")
            lines += probe(device, connection, d, way)
        } catch (e: Exception) {
            lines += "failed: ${e.javaClass.simpleName}: ${e.message}"
        } finally {
            connection.close()
        }
        // A reset that Android takes for a replug drops the DAC from its outputs and adds it again.
        val dropped = waitFor(present = false, 3000)
        if (dropped >= 0) {
            val back = waitFor(present = true, 5000)
            lines += "Android dropped the DAC after $dropped ms" +
                if (back >= 0) " and set it up again $back ms later" else " and didn't get it back in 5 s"
        } else {
            lines += "Android never dropped the DAC"
        }
        lines += "Android's USB output now: ${androidOutputs()}"
        lines += "sound cards now: ${soundCards()}"
        lines += "now play a song: is it heard? If not, unplug the DAC and plug it in again"
        done(way, lines, described = UsbAudioReport.describe(UsbAudioDescriptors.parse(raw!!)))
    }

    private fun probe(device: UsbDevice, connection: UsbDeviceConnection, d: UsbAudioDescriptors, way: GiveBack): List<String> {
        val lines = mutableListOf<String>()
        val numbers = (listOf(d.controlInterface) + d.playback.map { it.interfaceNumber }).distinct()
        val claimed = numbers.filter { n -> interfaceOf(device, n)?.let { connection.claimInterface(it, true) } == true }
        lines += "claimed interfaces ${claimed.joinToString(", ").ifEmpty { "none" }} of ${numbers.joinToString(", ")}"
        if (d.controlInterface !in claimed) return lines + giveBack(device, connection, claimed, way)
        lines += "while held, sound cards: ${soundCards()}"

        if (d.uacVersion == 2) {
            for (clock in d.clockSources) {
                val rates = rangeRequest(connection, CS_SAM_FREQ shl 8, (clock.id shl 8) or d.controlInterface, width = 4)
                val now = cur(connection, CS_SAM_FREQ shl 8, (clock.id shl 8) or d.controlInterface, 4)
                lines += "clock ${clock.id} rates: " + (rates?.let { UacRanges.rates(it).joinToString(", ") } ?: "no reply") +
                    (now?.let { " · now ${le(it)} Hz" } ?: "")
            }
        }
        for (unit in d.featureUnits.filter { it.masterVolume || it.channelVolume }) {
            val channel = if (unit.masterVolume) 0 else 1
            val value = (FU_VOLUME shl 8) or channel
            val index = (unit.id shl 8) or d.controlInterface
            val range = if (d.uacVersion == 2) {
                rangeRequest(connection, value, index, width = 2)?.let { UacRanges.volume(it) }
            } else {
                // UAC1 asks for each end apart: GET_MIN, GET_MAX, GET_RES.
                val min = get(connection, 0x82, value, index, 2)
                val max = get(connection, 0x83, value, index, 2)
                val res = get(connection, 0x84, value, index, 2)
                if (min != null && max != null && res != null) Triple(le(min).toShort().toInt(), le(max).toShort().toInt(), le(res).toShort().toInt()) else null
            }
            val now = (if (d.uacVersion == 2) cur(connection, value, index, 2) else get(connection, 0x81, value, index, 2))?.let { le(it).toShort().toInt() }
            lines += "volume (unit ${unit.id}, ${if (channel == 0) "master" else "channel 1"}): " +
                (range?.let { (lo, hi, step) -> "${db(lo)} to ${db(hi)} dB in ${db(step)} dB steps" } ?: "no reply") +
                (now?.let { " · now ${db(it)} dB" } ?: "")
        }
        return lines + giveBack(device, connection, claimed, way)
    }

    /**
     * Releases what was claimed and asks the kernel to bind Android's driver again, control
     * interface first; then, for [GiveBack.Reset], resets the DAC.
     */
    private fun giveBack(device: UsbDevice, connection: UsbDeviceConnection, claimed: List<Int>, way: GiveBack): List<String> {
        for (n in claimed) interfaceOf(device, n)?.let { connection.releaseInterface(it) }
        val results = claimed.map { n -> n to runCatching { reconnect(connection.fileDescriptor, n) }.getOrElse { -1 } }
        // EBUSY (16): Android's driver already took that interface along with the control interface.
        val lines = mutableListOf("gave back: " + results.joinToString(", ") { (n, e) -> "$n " + if (e == 0) "ok" else if (e == 16) "already bound" else "error $e" })
        if (way == GiveBack.Reset) {
            val e = runCatching { reset(connection.fileDescriptor) }.getOrElse { -1 }
            lines += "USB reset: " + if (e == 0) "ok" else "error $e"
        }
        return lines
    }

    /**
     * The USB outputs Android lists, with the sound card each one plays to: Android gives a USB
     * device's address as "card=1;device=0".
     */
    private fun androidOutputs(): String = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .filter { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
        .joinToString(", ") { it.address.ifEmpty { "no address" } }
        .ifEmpty { "none" }

    /** The kernel's own list of sound cards, if Android lets the app read it. */
    private fun soundCards(): String = runCatching {
        File("/proc/asound/cards").readLines()
            .mapNotNull { Regex("""^\s*(\d+)\s+\[[^]]*]:\s*(.*)$""").find(it)?.destructured?.let { (n, name) -> "$n ${name.trim()}" } }
            .joinToString(", ").ifEmpty { "none" }
    }.getOrElse { "not readable (${it.javaClass.simpleName})" }

    private fun rangeRequest(connection: UsbDeviceConnection, value: Int, index: Int, width: Int): ByteArray? {
        val head = ByteArray(2)
        if (connection.controlTransfer(CLASS_IN, RANGE, value, index, head, 2, TIMEOUT_MS) < 2) return null
        val count = le(head)
        if (count <= 0) return null
        val full = ByteArray(UacRanges.replyLength(count, width))
        val got = connection.controlTransfer(CLASS_IN, RANGE, value, index, full, full.size, TIMEOUT_MS)
        return if (got < full.size) null else full
    }

    private fun cur(connection: UsbDeviceConnection, value: Int, index: Int, size: Int): ByteArray? = get(connection, CUR, value, index, size)

    private fun get(connection: UsbDeviceConnection, request: Int, value: Int, index: Int, size: Int): ByteArray? {
        val buffer = ByteArray(size)
        return if (connection.controlTransfer(CLASS_IN, request, value, index, buffer, size, TIMEOUT_MS) == size) buffer else null
    }

    /** Waits until Android lists a USB output ([present]) or none; the ms it took, or -1. */
    private fun waitFor(present: Boolean, timeoutMs: Long): Long {
        val start = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - start < timeoutMs) {
            if (androidHasUsbOutput() == present) return SystemClock.elapsedRealtime() - start
            SystemClock.sleep(50)
        }
        return -1
    }

    private fun androidHasUsbOutput(): Boolean = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
        it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
    }

    /** Saves the report to Download and opens Android's share sheet with it. */
    fun share() {
        val text = report.ifEmpty { return }
        runCatching {
            @Suppress("DEPRECATION")
            val folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            File(folder, "AudioFool-USB-DAC.txt").writeText(text)
        }.onFailure { Log.w(TAG, "couldn't save the report", it) }
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "AudioFool USB DAC report")
            .putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, "Send the USB DAC report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Runs [action] off the main thread once Android has given the app the DAC (asking first if needed). */
    private fun withDevice(action: (UsbDevice) -> Unit) {
        if (busy) return
        val device = device() ?: run { described = listOf("no USB DAC plugged in"); return }
        busy = true
        val run = { d: UsbDevice -> thread(name = "AudioFoolUsb") { runCatching { action(d) }.onFailure { done(described = listOf("failed: ${it.message}")) } } }
        if (usb.hasPermission(device)) { run(device); return }
        described = listOf("asking for the DAC…")
        waiting = { run(it) }
        listen()
        val intent = Intent(ACTION_PERMISSION).setPackage(context.packageName)
        usb.requestPermission(device, PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
    }

    private var listening = false

    private fun listen() {
        if (listening) return
        listening = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                // 0.3.1: an answer nothing is waiting for (a second one for the same ask) used to
                // overwrite what had been read with "not allowed", as 0.3.0's report showed.
                val next = waiting.also { waiting = null } ?: return
                if (device != null && intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) next(device)
                else done(described = listOf("not allowed: tap again and choose Allow"))
            }
        }
        context.registerReceiver(receiver, IntentFilter(ACTION_PERMISSION), Context.RECEIVER_NOT_EXPORTED)
    }

    private fun done(described: List<String>) = done(null, emptyList(), described)

    /** Shows the results ([lines] under [way], if one was tried) and builds the report again. */
    private fun done(way: GiveBack?, lines: List<String>, described: List<String> = this.described) {
        main.post {
            this.described = described
            if (way != null) probed = probed + (way to lines)
            report = buildReport()
            busy = false
            for (line in described + lines) Log.i(TAG, line)
        }
    }

    private fun buildReport(): String {
        val bytes = raw ?: return ""
        val device = device()
        return buildString {
            appendLine("AudioFool USB DAC report, ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(Date())}")
            appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("DAC: ${device?.let { "${name(it)}, %04X:%04X".format(it.vendorId, it.productId) } ?: "unplugged since"}")
            appendLine()
            appendLine("Descriptors:")
            described.forEach { appendLine("  $it") }
            for ((way, lines) in probed) {
                appendLine()
                appendLine("Give it back ${way.title}:")
                lines.forEach { appendLine("  $it") }
            }
            appendLine()
            appendLine("Raw (${bytes.size} bytes):")
            UsbAudioReport.hex(bytes).forEach { appendLine(it) }
        }
    }

    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    companion object {
        private const val TAG = "AudioFoolUsb"
        private const val ACTION_PERMISSION = "com.mpaulrenlund.audiofool.USB_PERMISSION"
        private const val TIMEOUT_MS = 1000

        /** Longer than Android's usual 3 s before an idle output closes its sound card. */
        private const val WAIT_MS = 5000L

        // UAC class requests to an interface, device to host; UAC2's CUR and RANGE.
        private const val CLASS_IN = 0xA1
        private const val CUR = 0x01
        private const val RANGE = 0x02
        private const val CS_SAM_FREQ = 0x01
        private const val FU_VOLUME = 0x02

        init { System.loadLibrary("audiofoolusb") }

        /** USBDEVFS_CONNECT on one interface: lets the kernel bind its own driver again. 0, or the errno. */
        @JvmStatic private external fun reconnect(fd: Int, interfaceNumber: Int): Int

        /** USBDEVFS_RESET: resets the DAC on the bus (0.3.1). 0, or the errno. */
        @JvmStatic private external fun reset(fd: Int): Int

        private fun interfaceOf(device: UsbDevice, number: Int): UsbInterface? =
            (0 until device.interfaceCount).map { device.getInterface(it) }.firstOrNull { it.id == number && it.alternateSetting == 0 }

        private fun name(device: UsbDevice): String = device.productName?.trim()?.takeIf { it.isNotEmpty() } ?: device.deviceName

        private fun le(b: ByteArray): Int = b.foldIndexed(0) { i, acc, v -> acc or ((v.toInt() and 0xFF) shl (8 * i)) }

        private fun db(v: Int): String = "%.1f".format(Locale.ENGLISH, v / 256.0)
    }
}
