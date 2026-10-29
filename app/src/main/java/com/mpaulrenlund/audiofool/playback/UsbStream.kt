package com.mpaulrenlund.audiofool.playback

import java.nio.ByteBuffer

/**
 * The native USB Audio driver (`usbaudio_driver.cpp`, 0.4.0) for one DAC, opened from the
 * file descriptor Android's `UsbManager` gave the app. Every call is serialised: the output
 * stage's thread streams, the main thread may give the DAC back.
 */
class UsbStream private constructor(private var handle: Long) {
    val highSpeed: Boolean = nativeHighSpeed(handle)

    @Synchronized fun claim(interfaces: IntArray): Int = ifOpen(LIBUSB_ERROR_NO_DEVICE) { nativeClaim(it, interfaces) }

    /** 1 if Android's driver has [interfaceNumber], 0 if no driver has it, else libusb's error (0.4.7). */
    @Synchronized fun kernelDriverActive(interfaceNumber: Int): Int =
        ifOpen(LIBUSB_ERROR_NO_DEVICE) { nativeKernelDriverActive(it, interfaceNumber) }

    @Synchronized fun setAlternate(interfaceNumber: Int, alternate: Int): Int =
        ifOpen(LIBUSB_ERROR_NO_DEVICE) { nativeSetAlternate(it, interfaceNumber, alternate) }

    @Synchronized fun start(
        interfaceNumber: Int, dataEndpoint: Int, maxPacketBytes: Int, dataInterval: Int,
        feedbackEndpoint: Int, feedbackPacketBytes: Int, sampleRate: Int, bytesPerFrame: Int, slotBytes: Int, bufferBytes: Int,
    ): Int = ifOpen(LIBUSB_ERROR_NO_DEVICE) {
        nativeStart(it, interfaceNumber, dataEndpoint, maxPacketBytes, dataInterval, highSpeed, feedbackEndpoint, feedbackPacketBytes, sampleRate, bytesPerFrame, slotBytes, bufferBytes)
    }

    @Synchronized fun stop() = ifOpen(Unit) { nativeStop(it) }

    /** Bytes taken from [buffer] (its position moves on by as many), or -1 when the DAC is gone. */
    @Synchronized fun write(buffer: ByteBuffer, size: Int): Int {
        val n = ifOpen(-1) { nativeWrite(it, buffer, buffer.position(), size) }
        if (n > 0) buffer.position(buffer.position() + n)
        return n
    }

    @Synchronized fun setPlaying(playing: Boolean) = ifOpen(Unit) { nativeSetPlaying(it, playing) }
    @Synchronized fun flush() = ifOpen(Unit) { nativeFlush(it) }
    @Synchronized fun fadeOut(frames: Int) = ifOpen(Unit) { nativeFadeOut(it, frames) }
    @Synchronized fun head(): Long = ifOpen(0L) { nativeHead(it) }

    /** What the stream counts: see [Counts]. */
    @Synchronized fun counts(): Counts = Counts(ifOpen(IntArray(15)) { nativeCounts(it) })

    /** Gives the DAC back to Android ([UsbStream.nativeGiveBack]); the attach results, then the reset's. */
    @Synchronized fun giveBack(interfaces: IntArray, reset: Boolean): IntArray = ifOpen(IntArray(0)) { nativeGiveBack(it, interfaces, reset) }

    @Synchronized fun close() {
        if (handle == 0L) return
        nativeClose(handle)
        handle = 0
    }

    private inline fun <T> ifOpen(closed: T, action: (Long) -> T): T = if (handle == 0L) closed else action(handle)

    class Counts(values: IntArray) {
        val underruns = values[0]
        val feedbackValues = values[1]
        val errors = values[2]

        /** The rate the DAC's clock asks for, and its nominal, in Q16.16 frames per (micro)frame. */
        val rate = values[3]
        val nominal = values[4]
        val lost = values[5] != 0

        /** The longest the refills were apart, and how often the DAC played all that was in flight first (0.4.2). */
        val longestGapMs = values[6]
        val lateRefills = values[7]

        /** How much is in flight to the DAC: what a cut waits for before its fade is heard. */
        val queuedMs = values[8]

        /** The libusb event thread's nice value: -19 is what it asks for; 99 not started. */
        val eventNice = values[9]

        /** Packets the host reported failed inside completed transfers, and the last one's libusb status (0.4.5). */
        val packetErrors = values[10]
        val lastPacketStatus = values[11]

        /**
         * Where the longest gap went (0.4.10), in ms: the refills' wait for the ring's lock, their
         * longest resubmit to the DAC, and the longest a thread that only sleeps went without
         * running (as long as the gap: the whole app was stopped).
         */
        val longestLockWaitMs = values[12]
        val longestSubmitMs = values[13]
        val longestHeartbeatGapMs = values[14]
    }

    companion object {
        const val LIBUSB_ERROR_NO_DEVICE = -4
        const val LIBUSB_ERROR_NOT_FOUND = -5

        init { System.loadLibrary("audiofoolusb") }

        /** Opens the DAC from [fd], or null when libusb can't. */
        fun open(fd: Int): UsbStream? = nativeOpen(fd).takeIf { it != 0L }?.let { UsbStream(it) }

        fun errorName(code: Int): String = if (code == 0) "ok" else nativeErrorName(code)

        @JvmStatic private external fun nativeOpen(fd: Int): Long
        @JvmStatic private external fun nativeHighSpeed(handle: Long): Boolean
        @JvmStatic private external fun nativeClaim(handle: Long, interfaces: IntArray): Int
        @JvmStatic private external fun nativeKernelDriverActive(handle: Long, interfaceNumber: Int): Int
        @JvmStatic private external fun nativeSetAlternate(handle: Long, interfaceNumber: Int, alternate: Int): Int
        @JvmStatic private external fun nativeStart(
            handle: Long, interfaceNumber: Int, dataEndpoint: Int, maxPacketBytes: Int, dataInterval: Int, highSpeed: Boolean,
            feedbackEndpoint: Int, feedbackPacketBytes: Int, sampleRate: Int, bytesPerFrame: Int, slotBytes: Int, bufferBytes: Int,
        ): Int
        @JvmStatic private external fun nativeStop(handle: Long)
        @JvmStatic private external fun nativeWrite(handle: Long, buffer: ByteBuffer, offset: Int, size: Int): Int
        @JvmStatic private external fun nativeSetPlaying(handle: Long, playing: Boolean)
        @JvmStatic private external fun nativeFlush(handle: Long)
        @JvmStatic private external fun nativeFadeOut(handle: Long, frames: Int)
        @JvmStatic private external fun nativeHead(handle: Long): Long
        @JvmStatic private external fun nativeCounts(handle: Long): IntArray
        @JvmStatic private external fun nativeGiveBack(handle: Long, interfaces: IntArray, reset: Boolean): IntArray
        @JvmStatic private external fun nativeClose(handle: Long)
        @JvmStatic private external fun nativeErrorName(code: Int): String
    }
}
