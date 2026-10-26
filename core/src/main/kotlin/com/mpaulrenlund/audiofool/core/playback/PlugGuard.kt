package com.mpaulrenlund.audiofool.core.playback

/**
 * Plugging in a USB DAC or adapter never starts the music (0.2.3, the user's
 * call). The app itself never plays at a plug; what does is a play request from
 * outside it at that moment: a USB-C adapter often "presses" its play button as
 * it connects, or the system passes a play on. New here; the desktop has no plug.
 *
 * A play from outside the app (a headset or Bluetooth key, the lock screen, the
 * notification) within [windowMs] after a USB or wired device came or went is
 * ignored. One that came first, up to [windowMs] before the plug (the key can
 * beat Android's notice of the device), is undone: [plugged] says to pause. The
 * app's own buttons never pass through here.
 *
 * Times are any one clock in milliseconds (`SystemClock.elapsedRealtime` on the phone).
 */
class PlugGuard(private val windowMs: Long = WINDOW_MS) {
    private var plugAt: Long? = null
    private var outsideStartAt: Long? = null

    /** Plays ignored and undone so far, for the debug screen. */
    var ignored = 0
        private set
    var undone = 0
        private set

    /** A USB or wired device came or went at [now]. True when a play from outside just started the music: pause it. */
    fun plugged(now: Long): Boolean {
        plugAt = now
        val started = outsideStartAt
        outsideStartAt = null
        if (started == null || now - started > windowMs) return false
        undone++
        return true
    }

    /** A play from outside the app at [now]: false to ignore it. */
    fun mayStart(now: Long): Boolean {
        val plug = plugAt
        if (plug != null && now - plug in 0..windowMs) {
            ignored++
            return false
        }
        outsideStartAt = now
        return true
    }

    /** The music was paused or started from the app: a later plug leaves it alone. */
    fun settled() {
        outsideStartAt = null
    }

    /** How long ago the last plug was, at [now], or null if none yet. */
    fun sincePlug(now: Long): Long? = plugAt?.let { now - it }

    companion object {
        /** Long enough for an adapter to settle and Android to name it; short enough not to get in the way. */
        const val WINDOW_MS = 3_000L
    }
}
