package com.mpaulrenlund.audiofool

import android.app.Activity
import android.os.Bundle

/**
 * Opened by Android when a USB audio device is plugged in (0.4.0, the manifest's attach filter):
 * the user's "Always open AudioFool" answer is what lets the app's own USB driver use the DAC
 * at every plug without asking, including after the driver gives the DAC back with a reset,
 * which Android takes for a replug. Shows nothing and closes; the driver hears of the plug itself.
 */
class UsbAttachActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
