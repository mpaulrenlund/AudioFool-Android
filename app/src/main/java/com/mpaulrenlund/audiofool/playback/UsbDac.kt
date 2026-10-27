package com.mpaulrenlund.audiofool.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mpaulrenlund.audiofool.core.usbaudio.UsbAudioDescriptors
import com.mpaulrenlund.audiofool.core.usbaudio.UsbAudioReport
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * The USB DAC as Statistics > Debug's **USB DAC** panel shows it: its descriptors (0.3.0),
 * what the app's own driver is doing with it (0.4.0, [UsbDriver]), and a report with both
 * and the raw bytes, saved and shared as text. 0.3.0 and 0.3.1's tests of taking the DAC and
 * giving it back are gone: the driver does both now, and the tests would take the DAC from it.
 */
@Stable
class UsbDac(private val context: Context, private val driverStatus: () -> List<String>) {
    private val usb = context.getSystemService(UsbManager::class.java)

    /** What the panel shows under "Read the DAC": the descriptors in words, or where it got to. */
    var described: List<String> by mutableStateOf(emptyList())
        private set

    var busy: Boolean by mutableStateOf(false)
        private set

    /** The whole report as last built (when the DAC was read, or sent). */
    var report: String by mutableStateOf("")
        private set

    private var raw: ByteArray? = null
    private var waiting: ((UsbDevice) -> Unit)? = null

    /** The first USB device with an audio interface, as Android lists it now. */
    fun device(): UsbDevice? = usb.deviceList.values.firstOrNull { d ->
        (0 until d.interfaceCount).any { d.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_AUDIO }
    }

    fun summary(): String = device()?.let { "${name(it)} · %04X:%04X".format(it.vendorId, it.productId) } ?: "none plugged in"

    /** What the driver is doing, a line each. */
    fun driver(): List<String> = driverStatus()

    /** Reads the descriptors: no change to the DAC or to playback (a second handle beside the driver's). */
    fun read() = withDevice { device ->
        val connection = usb.openDevice(device) ?: return@withDevice done(listOf("Android wouldn't open it"))
        val bytes = try { connection.rawDescriptors } finally { connection.close() }
        if (bytes == null) return@withDevice done(listOf("no descriptors"))
        raw = bytes
        done(UsbAudioReport.describe(UsbAudioDescriptors.parse(bytes)))
    }

    /** Saves the report to Download and opens Android's share sheet with it. */
    fun share() {
        report = buildReport()
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
        val run = { d: UsbDevice -> thread(name = "AudioFoolUsb") { runCatching { action(d) }.onFailure { done(listOf("failed: ${it.message}")) } } }
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
                // 0.3.1: an answer nothing is waiting for (a second one for the same ask) used to
                // overwrite what had been read with "not allowed", as 0.3.0's report showed.
                val next = waiting.also { waiting = null } ?: return
                val device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                if (device != null && intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) next(device)
                else done(listOf("not allowed: tap again and choose Allow"))
            }
        }
        context.registerReceiver(receiver, IntentFilter(ACTION_PERMISSION), Context.RECEIVER_NOT_EXPORTED)
    }

    private fun done(described: List<String>) {
        main.post {
            this.described = described
            report = buildReport()
            busy = false
            for (line in described) Log.i(TAG, line)
        }
    }

    /** The report: the driver's lines are worth sending even before the DAC has been read. */
    private fun buildReport(): String {
        val bytes = raw
        val device = device()
        return buildString {
            appendLine("AudioFool USB DAC report, ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(Date())}")
            appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("DAC: ${device?.let { "${name(it)}, %04X:%04X".format(it.vendorId, it.productId) } ?: "unplugged since"}")
            appendLine()
            appendLine("Descriptors:")
            described.ifEmpty { listOf("not read (tap Read the DAC)") }.forEach { appendLine("  $it") }
            appendLine()
            appendLine("Driver:")
            driver().forEach { appendLine("  $it") }
            if (bytes != null) {
                appendLine()
                appendLine("Raw (${bytes.size} bytes):")
                UsbAudioReport.hex(bytes).forEach { appendLine(it) }
            }
        }
    }

    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    companion object {
        private const val TAG = "AudioFoolUsb"
        private const val ACTION_PERMISSION = "com.mpaulrenlund.audiofool.USB_PERMISSION"

        private fun name(device: UsbDevice): String = device.productName?.trim()?.takeIf { it.isNotEmpty() } ?: device.deviceName
    }
}
