package com.mpaulrenlund.audiofool.playback

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.AudioEffect

/**
 * One audio session for the shared output, announced to equalizer apps (2026-11-23, the
 * user's call: Wavelet). An EQ app attaches its effects to the session a player announces,
 * as it does for Spotify or Poweramp; without the announcement Wavelet reached AudioFool
 * only through its experimental session detection or its legacy mode.
 *
 * [id] stays the same for the app's life, so the effects stay attached when the output is
 * reopened for a song in another format. Only the shared output plays in it
 * ([OutputStage]): the Bit-Perfect ways (the USB mixer, the M21's source rate, the driver)
 * never do, so an EQ never touches them.
 *
 * Announced when playing starts, and stays announced through a pause (so the EQ doesn't
 * drop and come back at every Play); closed when the queue ends or the service goes.
 * Main thread only.
 */
class EffectsSession(private val context: Context, val id: Int) {
    /** True between an open and a close. */
    var announced = false
        private set

    /** Playing has started: tells EQ apps about [id] (once, until [close]). */
    fun open() {
        if (id <= 0 || announced) return
        send(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
        announced = true
        android.util.Log.i(TAG, "effects session $id announced")
    }

    /** The play has ended: EQ apps let go of [id]. */
    fun close() {
        if (!announced) return
        send(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        announced = false
        android.util.Log.i(TAG, "effects session $id closed")
    }

    private fun send(action: String) = context.sendBroadcast(
        Intent(action)
            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, id)
            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC),
    )

    companion object {
        private const val TAG = "AudioFoolPlay"

        /** A new session from Android; one that couldn't be had (`AudioManager.ERROR`) is never announced or used. */
        fun create(context: Context): EffectsSession =
            EffectsSession(context, context.getSystemService(AudioManager::class.java).generateAudioSessionId())
    }
}
