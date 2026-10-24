package com.mpaulrenlund.audiofool.theme

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mpaulrenlund.audiofool.settings.AppSettings
import com.mpaulrenlund.audiofool.settings.MusicFolder
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Spec 2: Dark when nothing is saved; a switch is saved and applies at once. Spec 4.5: the folder list. */
@RunWith(AndroidJUnit4::class)
class ThemeSettingTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun clear() {
        context.getSharedPreferences(AppSettings.FILE, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun dark_when_nothing_is_saved() = assertTrue(AppSettings(context).darkTheme.value)

    @Test
    fun a_switch_applies_at_once_and_is_saved() {
        val settings = AppSettings(context)
        settings.setDarkTheme(false)
        assertFalse(settings.darkTheme.value)
        assertFalse(AppSettings(context).darkTheme.value, "read back by the next start")
        settings.setDarkTheme(true)
        assertTrue(AppSettings(context).darkTheme.value)
    }

    @Test
    fun the_theme_reads_each_files_colours() {
        val tokens = AppTokens.get(context.assets)
        val dark = AppTheme(true, tokens)
        val light = dark.withDark(false)
        assertEquals(tokens.dark.color("color.window.bg").toCompose(), dark.color("color.window.bg"))
        assertEquals(tokens.light.color("color.window.bg").toCompose(), light.color("color.window.bg"))
        assertNotEquals(dark.color("color.accent.playback"), light.color("color.accent.playback"))
        // Mobile-only colours pick their own theme's value.
        assertEquals(tokens.mobile.color("color.bpLabel.off.light").toCompose(), light.mobileColor("color.bpLabel.off"))
        assertEquals(tokens.mobile.color("color.bpLabel.off.dark").toCompose(), dark.mobileColor("color.bpLabel.off"))
        assertEquals("PS2 Dark", dark.name)
        assertEquals("PS1 Light", light.name)
    }

    @Test
    fun folders_are_saved_once_each_with_their_tick() {
        val settings = AppSettings(context)
        assertFalse(settings.foldersChosen)
        settings.setFolders(listOf(MusicFolder("/storage/emulated/0/Music"), MusicFolder("/storage/1A2B-3C4D/Music/")))
        settings.addFolder("/STORAGE/1a2b-3c4d/music") // the same folder again
        settings.setIncluded("/storage/1A2B-3C4D/Music", false)

        val again = AppSettings(context)
        assertTrue(again.foldersChosen)
        assertEquals(
            listOf(MusicFolder("/storage/emulated/0/Music"), MusicFolder("/storage/1A2B-3C4D/Music", included = false)),
            again.folders.value,
        )
    }
}
