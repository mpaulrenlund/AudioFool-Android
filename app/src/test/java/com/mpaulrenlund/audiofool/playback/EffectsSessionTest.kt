package com.mpaulrenlund.audiofool.playback

import android.app.Application
import android.media.audiofx.AudioEffect
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** EQ apps such as Wavelet hear of the shared output's session (2026-11-23): once per play, with what they read. */
@RunWith(AndroidJUnit4::class)
class EffectsSessionTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private fun sent() = shadowOf(app).broadcastIntents.filter { it.action?.contains("AUDIO_EFFECT_CONTROL_SESSION") == true }

    @Test
    fun open_tells_eq_apps_the_session_the_package_and_music() {
        val session = EffectsSession(app, 4321)
        session.open()
        val intent = sent().single()
        assertEquals(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, intent.action)
        assertEquals(4321, intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1))
        assertEquals(app.packageName, intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME))
        assertEquals(AudioEffect.CONTENT_TYPE_MUSIC, intent.getIntExtra(AudioEffect.EXTRA_CONTENT_TYPE, -1))
        assertTrue(session.announced)
    }

    @Test
    fun a_second_play_and_a_pause_send_nothing_more() {
        val session = EffectsSession(app, 4321)
        session.open()
        session.open()
        assertEquals(1, sent().size, "open is sent once until the play ends")
    }

    @Test
    fun close_lets_go_once_and_the_next_play_announces_again() {
        val session = EffectsSession(app, 4321)
        session.close()
        assertEquals(0, sent().size, "nothing to close before an open")
        session.open()
        session.close()
        session.close()
        session.open()
        assertEquals(
            listOf(
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION,
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION,
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION,
            ),
            sent().map { it.action },
        )
        assertEquals(4321, sent()[1].getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1), "the close names the same session")
    }

    @Test
    fun a_session_android_wouldnt_give_is_never_announced() {
        val session = EffectsSession(app, -1)
        session.open()
        assertEquals(0, sent().size)
        assertFalse(session.announced)
    }
}
