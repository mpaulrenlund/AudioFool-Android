package com.mpaulrenlund.audiofool.updates

import com.mpaulrenlund.audiofool.core.updates.AppRelease
import com.mpaulrenlund.audiofool.core.updates.AppVersion
import org.junit.Test
import kotlin.test.assertEquals

// The update pop-up (spec 9): what it says at each step.

class UpdatePromptTextTest {
    private val release = AppRelease(AppVersion.parse("v0.19.0")!!, "https://example.invalid/a.apk", 27_123_325, null)

    @Test
    fun `the offer names the new version and this one`() {
        assertEquals("AudioFool 0.19.0 is available. You have 0.18.1.", Updates.message(Updates.Prompt.Offer(release, "0.18.1")))
    }

    @Test
    fun `the download says how far it has got`() {
        assertEquals("Downloading AudioFool 0.19.0… 12 of 27 MB", Updates.message(Updates.Prompt.Downloading(release, 12_400_000)))
    }

    @Test
    fun `a failure gives its reason as it came`() {
        assertEquals("Couldn't reach GitHub: timeout", Updates.message(Updates.Prompt.Failed(release, "Couldn't reach GitHub: timeout")))
    }
}
