package com.mpaulrenlund.audiofool.updates

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mpaulrenlund.audiofool.core.scrobbling.HttpAnswer
import com.mpaulrenlund.audiofool.core.updates.ApkDownload
import com.mpaulrenlund.audiofool.core.updates.GitHubReleases
import com.mpaulrenlund.audiofool.core.updates.ReleasesTransport
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The menus' Version row's check (spec 9; 2026-11-12, the user's call): a newer build is
 * offered, else a note says there's none or that GitHub couldn't be read. Cancel doesn't
 * stop the same build being offered at the next tap.
 */
@RunWith(AndroidJUnit4::class)
class UpdateCheckTest {
    private fun release(version: String) = """
        {"tag_name": "v$version", "draft": false, "prerelease": true,
         "assets": [{"name": "AudioFool-$version.apk", "state": "uploaded", "size": 27123325,
           "browser_download_url": "https://example.invalid/AudioFool-$version.apk"}]}
    """.trimIndent()

    private fun updates(current: String, transport: ReleasesTransport) =
        Updates(ApplicationProvider.getApplicationContext(), GitHubReleases(transport = transport), ApkDownload(), current)

    private fun github(vararg versions: String) = ReleasesTransport { HttpAnswer(200, versions.joinToString(",", "[", "]") { release(it) }) }

    /** Runs the main thread until [done], as GitHub is read on another thread. */
    private fun waitFor(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!done()) {
            check(System.currentTimeMillis() < deadline) { "timed out" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
    }

    @Test
    fun `a newer build is offered, and offered again after Cancel`() {
        val u = updates("0.20.0", github("0.19.1", "0.20.1", "0.20.0"))
        u.checkNow()
        assertEquals(Updates.Note.Checking, u.note)
        waitFor { u.prompt != null }
        assertEquals("AudioFool 0.20.1 is available. You have 0.20.0.", Updates.message(u.prompt!!))
        assertNull(u.note)

        u.cancel()
        assertNull(u.prompt)
        u.checkNow()
        waitFor { u.prompt != null }
        assertEquals("0.20.1", u.prompt!!.release.version.text)
    }

    @Test
    fun `nothing newer says so, and the note goes when the screen is done with it`() {
        val u = updates("0.20.0", github("0.19.1", "0.20.0"))
        u.checkNow()
        waitFor { u.note != Updates.Note.Checking }
        assertEquals(Updates.Note.UpToDate, u.note)
        assertNull(u.prompt)
        assertEquals("No new version available", Updates.noteText(u.note!!))
        u.noteGone(Updates.Note.UpToDate)
        assertNull(u.note)
    }

    @Test
    fun `GitHub out of reach says the check failed`() {
        val u = updates("0.20.0") { throw IOException("no route") }
        u.checkNow()
        waitFor { u.note != Updates.Note.Checking }
        val failed = assertIs<Updates.Note.CheckFailed>(u.note)
        assertEquals("Couldn't reach GitHub: no route", failed.reason)
        assertEquals("Couldn't check for updates", Updates.noteText(failed))
    }

    @Test
    fun `an old note doesn't take a newer one away`() {
        val u = updates("0.20.0", github("0.20.0"))
        u.checkNow()
        waitFor { u.note == Updates.Note.UpToDate }
        u.checkNow()
        assertEquals(Updates.Note.Checking, u.note)
        u.noteGone(Updates.Note.UpToDate)
        assertEquals(Updates.Note.Checking, u.note)
        waitFor { u.note == Updates.Note.UpToDate }
    }
}
